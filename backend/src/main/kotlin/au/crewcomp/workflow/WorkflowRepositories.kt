package au.crewcomp.workflow

import au.crewcomp.platform.security.ScopeGuard
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class RegisterRecordRepository(private val scopeGuard: ScopeGuard) : PanacheRepositoryBase<RegisterRecord, Long> {

    fun byRecordId(recordId: String): RegisterRecord? = find("recordId", recordId).firstResult()

    /**
     * The register records that overlay a swing's evaluation (§5.1 step 4). Runs on behalf of
     * whoever is evaluating the swing, so it is scoped like everything else person-shaped.
     */
    fun forCrewChangeScoped(crewChangeId: Long): List<RegisterRecord> {
        val clause = scopeGuard.clause("r.person.id", "r.partnership.id")
        return find(
            "from RegisterRecord r where (${clause.hql}) and r.crewChange.id = :crewChangeId",
            clause.params + mapOf("crewChangeId" to crewChangeId),
        ).list()
    }

    fun forPersonScoped(personId: Long): List<RegisterRecord> {
        scopeGuard.assertVisible(personId)
        return list("person.id = ?1 order by raisedDate desc", personId)
    }

    fun openRecords(): List<RegisterRecord> =
        list(
            "statusValue in ?1 order by raisedDate",
            RegisterStatus.entries.filter { it.isOpen }.map { it.wire },
        )

    /**
     * The highest sequence already issued for a `{PT}{CCnn}-` prefix, for generating the next
     * business key. Monotonic per prefix (§4.4) — the service allocates under a lock.
     */
    fun highestSequenceForPrefix(prefix: String): Int =
        find("recordId like ?1", "$prefix%")
            .list()
            .mapNotNull { it.recordId.substringAfterLast('-').toIntOrNull() }
            .maxOrNull() ?: 0
}

@ApplicationScoped
class ExceptionItemRepository : PanacheRepositoryBase<ExceptionItem, Long> {

    fun open(): List<ExceptionItem> = list("state = 'open' order by area, id")

    fun forEntity(entityType: String, entityId: Long): List<ExceptionItem> =
        list("linkedEntityType = ?1 and linkedEntityId = ?2", entityType, entityId)
}
