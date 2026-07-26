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
        list("statusValue = ?1", au.crewcomp.engine.HoldingStatus.UNKNOWN.wire)
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
}
