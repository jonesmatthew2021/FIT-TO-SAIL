package au.crewcomp.reference

import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped

/**
 * Reference-layer repositories.
 *
 * Reference data is not person-scoped, so these carry no [au.crewcomp.platform.security.ScopeGuard]
 * plumbing — every authenticated actor may read the catalogue, the calendar and the slot model.
 * Writing them is role-gated in the service layer (AUTH-1).
 */

@ApplicationScoped
class PartnershipRepository : PanacheRepositoryBase<Partnership, Long> {
    fun byAbbrev(abbrev: String): Partnership? = find("abbrev", abbrev).firstResult()
    fun allOrdered(): List<Partnership> = listAll(io.quarkus.panache.common.Sort.by("abbrev"))
    fun forCustomer(customerId: Long): List<Partnership> =
        list("customer.id = ?1 order by abbrev", customerId)
}

/** COM-1 — the client companies. Reference data like the rest of the file; writes are role-gated in CustomerService. */
@ApplicationScoped
class CustomerRepository : PanacheRepositoryBase<Customer, Long> {
    fun byName(name: String): Customer? = find("lower(name) = lower(?1)", name).firstResult()
    fun allOrdered(): List<Customer> = listAll(io.quarkus.panache.common.Sort.by("name"))
}

@ApplicationScoped
class VesselRepository : PanacheRepositoryBase<Vessel, Long> {
    fun forPartnership(partnershipId: Long): List<Vessel> = list("partnership.id", partnershipId)
}

@ApplicationScoped
class CrewPositionRepository : PanacheRepositoryBase<CrewPosition, Long> {
    fun byName(name: String): CrewPosition? = find("name", name).firstResult()
    fun allOrdered(): List<CrewPosition> = listAll(io.quarkus.panache.common.Sort.by("name"))
}

@ApplicationScoped
class PositionSlotRepository : PanacheRepositoryBase<PositionSlot, Long> {
    fun allOrdered(): List<PositionSlot> = listAll(io.quarkus.panache.common.Sort.by("ref"))
    fun byRef(ref: Int): PositionSlot? = find("ref", ref).firstResult()
}

@ApplicationScoped
class CrewChangeRepository : PanacheRepositoryBase<CrewChange, Long> {

    /** The swing identified by its business key — `(cc_id, partnership)` is unique (§4.1). */
    fun byBusinessKey(ccId: String, partnershipAbbrev: String): CrewChange? =
        find("ccId = ?1 and partnership.abbrev = ?2", ccId, partnershipAbbrev).firstResult()

    fun forPartnership(partnershipId: Long): List<CrewChange> =
        list("partnership.id = ?1 order by fromDate", partnershipId)
}

@ApplicationScoped
class RequirementRepository : PanacheRepositoryBase<Requirement, Long> {
    fun byCode(code: String): Requirement? = find("code", code).firstResult()
    fun active(): List<Requirement> = list("status = 'active' order by code")

    /**
     * The catalogue with its legacy aliases attached — ADM-6's screen and the only shape safe to
     * map outside the transaction. Aliases are a lazy collection, and the DTO mapping happens
     * after the service transaction closes.
     */
    fun allWithAliases(): List<Requirement> =
        find("select distinct r from Requirement r left join fetch r.aliases order by r.code").list()

    fun withAliases(id: Long): Requirement? =
        find("select r from Requirement r left join fetch r.aliases where r.id = ?1", id).firstResult()

    /**
     * Catalogue matching for the evidence pipeline (§8 stage 3): code, then title, then legacy
     * alias. Returns every candidate rather than picking one — an ambiguous match goes to
     * `pending_review` and is never guessed silently.
     */
    fun matching(text: String): List<Requirement> {
        val needle = text.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        return find(
            """
            select distinct r from Requirement r
            left join r.aliases a
            where lower(r.code) = :needle
               or lower(r.title) = :needle
               or lower(a.alias) = :needle
            """.trimIndent(),
            mapOf("needle" to needle),
        ).list()
    }
}
