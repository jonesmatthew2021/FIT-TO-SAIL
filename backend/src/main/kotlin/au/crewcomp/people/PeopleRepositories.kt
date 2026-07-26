package au.crewcomp.people

import au.crewcomp.platform.security.ScopeGuard
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import io.quarkus.panache.common.Sort
import jakarta.enterprise.context.ApplicationScoped
import java.time.LocalDate

/**
 * People-layer repositories.
 *
 * Everything here is person-scoped, so the read methods take their restriction from
 * [ScopeGuard] rather than from their caller (AUTH-2). A method that returns person data
 * without a scope clause is a defect; the ones that legitimately need the unrestricted set —
 * the expiry scan, the suggestion candidate pool — say so in their name and are only reachable
 * from system-actor code paths.
 *
 * **Enumerated columns are queried by their `*Value` field**, never by the typed Kotlin property
 * beside it: `statusValue`, not `status`. Only the field is a JPA attribute, so HQL naming the
 * property compiles cleanly and fails at query time with "could not interpret path expression".
 *
 * The scoped reads **fetch-join whatever the API mapping reads**. Mapping to a DTO happens after
 * the service transaction has closed, so a lazy association reached at that point throws
 * LazyInitializationException — a failure no unit test sees, because it needs a real session.
 */

@ApplicationScoped
class PersonRepository(private val scopeGuard: ScopeGuard) : PanacheRepositoryBase<Person, Long> {

    /** Person is its own scope subject, so the clause applies to the row's own id. */
    fun findScoped(id: Long): Person? {
        val clause = scopeGuard.clause("p.id", "p.partnership.id")
        return find(
            "from Person p join fetch p.position join fetch p.partnership " +
                "where (${clause.hql}) and p.id = :personId",
            clause.params + mapOf("personId" to id),
        ).firstResult()
    }

    fun listScoped(): List<Person> {
        val clause = scopeGuard.clause("p.id", "p.partnership.id")
        return find(
            "from Person p join fetch p.position join fetch p.partnership " +
                "where ${clause.hql} order by p.name",
            clause.params,
        ).list()
    }

    fun bySam(sam: String): List<Person> = list("sam", sam)

    /**
     * Every active person, unscoped. Used by the expiry scan (§5.4) and the suggestion
     * candidate pool (§5.4), both of which run as a system or back-office actor.
     */
    fun allActiveUnscoped(): List<Person> =
        list("statusValue = ?1", Sort.by("name"), au.crewcomp.engine.PersonStatus.ACTIVE.wire)

    fun byPartnershipUnscoped(partnershipId: Long): List<Person> =
        list("partnership.id = ?1 order by name", partnershipId)
}

@ApplicationScoped
class QualificationHoldingRepository(
    private val scopeGuard: ScopeGuard,
) : PanacheRepositoryBase<QualificationHolding, Long> {

    fun forPersonScoped(personId: Long): List<QualificationHolding> {
        scopeGuard.assertVisible(personId)
        return find("from QualificationHolding h join fetch h.requirement where h.person.id = ?1", personId)
            .list()
    }

    fun forPeopleUnscoped(personIds: Collection<Long>): List<QualificationHolding> =
        if (personIds.isEmpty()) emptyList() else list("person.id in ?1", personIds)

    fun find(personId: Long, requirementId: Long): QualificationHolding? =
        find("person.id = ?1 and requirement.id = ?2", personId, requirementId).firstResult()

    /** Feeds the expiry-alert scan (§5.4). Runs as a system actor. */
    fun expiringByUnscoped(horizon: LocalDate): List<QualificationHolding> =
        list(
            "statusValue = ?1 and expiryDate <= ?2",
            Sort.by("expiryDate"),
            au.crewcomp.engine.HoldingStatus.HELD_EXPIRY.wire,
            horizon,
        )

    /** The standing "unknown holdings" chase list (§4.3, Q11 / ADM-7). */
    fun unknownUnscoped(): List<QualificationHolding> =
        find(
            "from QualificationHolding h join fetch h.person join fetch h.requirement " +
                "where h.statusValue = ?1 order by h.person.name",
            au.crewcomp.engine.HoldingStatus.UNKNOWN.wire,
        ).list()

    /**
     * `requirement_id → how many people hold a record for it` — ADM-6's usage count.
     *
     * Unscoped, and deliberately: the number answers "is this catalogue entry in use", which is a
     * property of the catalogue rather than of any person. It exposes no one's holdings.
     */
    fun countByRequirementUnscoped(): Map<Long, Long> =
        getEntityManager()
            .createQuery(
                "select h.requirement.id, count(h) from QualificationHolding h group by h.requirement.id",
                Array<Any>::class.java,
            )
            .resultList
            .associate { (it[0] as Number).toLong() to (it[1] as Number).toLong() }
}

@ApplicationScoped
class AssignmentRepository(private val scopeGuard: ScopeGuard) : PanacheRepositoryBase<Assignment, Long> {

    fun forCrewChangeUnscoped(crewChangeId: Long): List<Assignment> =
        list("crewChange.id = ?1 order by slotRef, fromDate", crewChangeId)

    fun forPersonScoped(personId: Long): List<Assignment> {
        scopeGuard.assertVisible(personId)
        return find(
            "from Assignment a join fetch a.crewChange join fetch a.partnership " +
                "where a.person.id = ?1 order by a.fromDate desc",
            personId,
        ).list()
    }

    /**
     * Assignments overlapping `[from, to]` outside [excludingCrewChangeId] — the clash input for
     * suggestion ranking (§5.4).
     */
    fun overlappingUnscoped(from: LocalDate, to: LocalDate, excludingCrewChangeId: Long): List<Assignment> =
        list(
            "fromDate <= ?1 and toDate >= ?2 and crewChange.id <> ?3",
            to, from, excludingCrewChangeId,
        )

    fun upcomingUnscoped(onOrAfter: LocalDate): List<Assignment> =
        list("toDate >= ?1 order by fromDate", onOrAfter)

    /**
     * Everything this person is already committed to across `[from, to]` — the clash check the
     * assignment write path runs (ADM-2).
     *
     * Unlike [overlappingUnscoped] this does **not** exclude a crew change, because being in two
     * slots of the *same* swing is exactly as much of a clash as being in two swings. It fetches
     * the crew change and partnership because the caller renders them into the clash message
     * after the transaction has closed.
     */
    fun overlappingForPersonUnscoped(personId: Long, from: LocalDate, to: LocalDate): List<Assignment> =
        find(
            "from Assignment a join fetch a.crewChange join fetch a.partnership join fetch a.person " +
                "where a.person.id = ?1 and a.fromDate <= ?2 and a.toDate >= ?3 order by a.fromDate",
            personId, to, from,
        ).list()
}

@ApplicationScoped
class UserAccountRepository : PanacheRepositoryBase<UserAccount, Long> {

    /** SEC-1: identity is keyed on (issuer, subject) — never on email (ADR 0003). */
    fun byExternalIdentity(issuer: String, subject: String): UserAccount? =
        find("issuer = ?1 and subject = ?2", issuer, subject).firstResult()

    fun forPerson(personId: Long): UserAccount? = find("person.id", personId).firstResult()

    /** SEC-1b: local/test accounts must stay enumerable so their removal can be verified. */
    fun localTestAccounts(): List<UserAccount> = list("kindValue", UserAccountKind.LOCAL_TEST.wire)
}

@ApplicationScoped
class IdentityProviderRepository : PanacheRepositoryBase<IdentityProvider, Long> {

    /** SEC-1a: the allow-list lookup performed on **every** token, not just at first login. */
    fun enabledFor(issuer: String, tenantOrDomain: String): IdentityProvider? =
        find(
            "issuer = ?1 and tenantOrDomain = ?2 and enabled = true",
            issuer, tenantOrDomain,
        ).firstResult()

    fun enabled(): List<IdentityProvider> = list("enabled", true)
}

@ApplicationScoped
class LeaveRecordRepository(private val scopeGuard: ScopeGuard) : PanacheRepositoryBase<LeaveRecord, Long> {

    fun forPersonScoped(personId: Long): List<LeaveRecord> {
        scopeGuard.assertVisible(personId)
        return list("person.id = ?1 order by fromDate desc", personId)
    }

    /**
     * Leave overlapping `[from, to]` that still stands — the second half of ADM-2's clash check.
     * Declined and cancelled leave is not a clash: the person is available.
     */
    fun overlappingForPersonUnscoped(personId: Long, from: LocalDate, to: LocalDate): List<LeaveRecord> =
        list(
            "person.id = ?1 and fromDate <= ?2 and toDate >= ?3 and status in ?4 order by fromDate",
            personId, to, from, listOf("recorded", "requested", "approved"),
        )
}
