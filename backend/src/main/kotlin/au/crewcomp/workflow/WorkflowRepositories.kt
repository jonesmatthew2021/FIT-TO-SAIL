package au.crewcomp.workflow

import au.crewcomp.platform.security.ScopeGuard
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class RegisterRecordRepository(private val scopeGuard: ScopeGuard) : PanacheRepositoryBase<RegisterRecord, Long> {

    fun byRecordId(recordId: String): RegisterRecord? = find("recordId", recordId).firstResult()

    /**
     * The record a replayed MOB-10 request already created, or null.
     *
     * Unscoped and safe for the same reason `CrewStatementRepository.byOpId` is: `crew_op_id` is a
     * client-minted UUID, so this discovers nothing without already holding the opaque id — and the
     * caller checks the owner on what it finds.
     */
    fun byCrewOpId(opId: String): RegisterRecord? = find("crewOpId", opId).firstResult()

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

    /**
     * The record with everything the detail view renders, fetched in one go — conditions, notes
     * and the human-readable trail are all lazy collections, and the DTO mapping happens after
     * the service transaction has closed.
     *
     * Three separate queries rather than one with three fetch joins: joining two collections in a
     * single query is a cartesian product Hibernate will happily build and then de-duplicate in
     * memory, which turns 3 notes × 2 conditions × 5 audit rows into thirty rows off the wire.
     */
    fun detailByRecordId(recordId: String): RegisterRecord? {
        val record = find(
            """
            select r from RegisterRecord r
            left join fetch r.person
            left join fetch r.position
            left join fetch r.requirement
            join fetch r.partnership
            left join fetch r.crewChange
            where r.recordId = ?1
            """.trimIndent(),
            recordId,
        ).firstResult() ?: return null

        val id = record.requiredId
        getEntityManager().createQuery(
            "select c from ApprovalCondition c where c.registerRecord.id = ?1",
            ApprovalCondition::class.java,
        ).setParameter(1, id).resultList
        getEntityManager().createQuery(
            "select n from RegisterNote n where n.registerRecord.id = ?1",
            RegisterNote::class.java,
        ).setParameter(1, id).resultList
        getEntityManager().createQuery(
            "select a from RegisterAuditEntry a where a.registerRecord.id = ?1",
            RegisterAuditEntry::class.java,
        ).setParameter(1, id).resultList

        // Loading the children into the persistence context is not enough on its own: the
        // collections still have to be *touched* to be marked initialised before the transaction
        // ends. They resolve from the context rather than from another round trip.
        record.conditions.size
        record.notes.size
        record.auditEntries.size
        return record
    }

    /**
     * ADM-4's filtered list, row-scoped like everything person-shaped (AUTH-2).
     *
     * `openOnly` is tri-state on purpose: the register's default view is the open work, but the
     * 445-row history is the reason the module exists and has to be reachable.
     */
    fun searchScoped(
        partnershipAbbrev: String?,
        ccId: String?,
        type: String?,
        openOnly: Boolean?,
    ): List<RegisterRecord> {
        var clause = scopeGuard.clause("r.person.id", "r.partnership.id")
        if (partnershipAbbrev != null) {
            clause = clause.and("r.partnership.abbrev = :abbrev", mapOf("abbrev" to partnershipAbbrev))
        }
        if (ccId != null) {
            clause = clause.and("r.crewChange.ccId = :ccId", mapOf("ccId" to ccId))
        }
        if (type != null) {
            clause = clause.and("r.typeValue = :type", mapOf("type" to type))
        }
        if (openOnly != null) {
            val open = RegisterStatus.entries.filter { it.isOpen }.map { it.wire }
            clause = if (openOnly) {
                clause.and("r.statusValue in (:openStatuses)", mapOf("openStatuses" to open))
            } else {
                clause.and("r.statusValue not in (:openStatuses)", mapOf("openStatuses" to open))
            }
        }

        return find(
            """
            select r from RegisterRecord r
            left join fetch r.person
            left join fetch r.position
            left join fetch r.requirement
            join fetch r.partnership
            left join fetch r.crewChange
            where ${clause.hql}
            order by r.raisedDate desc, r.recordId desc
            """.trimIndent(),
            clause.params,
        ).list()
    }

    /**
     * Serialises business-key allocation for one `{PT}{CCnn}-` prefix.
     *
     * `record_id` is unique and monotonic per prefix (§4.4), and "read the highest, add one" is a
     * race between two coordinators raising a request for the same swing at the same moment. The
     * lock is transaction-scoped, so it releases on commit with no unlock to forget — the same
     * reasoning as the audit sequence's.
     */
    fun lockPrefix(prefix: String) {
        getEntityManager()
            .createNativeQuery("select pg_advisory_xact_lock(:key)")
            .setParameter("key", PREFIX_LOCK_NAMESPACE + prefix.hashCode())
            .singleResult
    }

    fun openRecords(): List<RegisterRecord> =
        list(
            "statusValue in ?1 order by raisedDate",
            RegisterStatus.entries.filter { it.isOpen }.map { it.wire },
        )

    /**
     * `requirement_id → how many register records name it` — ADM-6's usage count. Records whose
     * legacy title never mapped to a code carry `req_raw` instead and are not counted, which is
     * the honest answer: nothing links them to a catalogue entry.
     */
    fun countByRequirement(): Map<Long, Long> =
        getEntityManager()
            .createQuery(
                "select r.requirement.id, count(r) from RegisterRecord r " +
                    "where r.requirement is not null group by r.requirement.id",
                Array<Any>::class.java,
            )
            .resultList
            .associate { (it[0] as Number).toLong() to (it[1] as Number).toLong() }

    /**
     * The highest sequence already issued for a `{PT}{CCnn}-` prefix, for generating the next
     * business key. Monotonic per prefix (§4.4) — the service allocates under a lock.
     */
    fun highestSequenceForPrefix(prefix: String): Int =
        find("recordId like ?1", "$prefix%")
            .list()
            .mapNotNull { it.recordId.substringAfterLast('-').toIntOrNull() }
            .maxOrNull() ?: 0

    private companion object {
        /** Arbitrary but fixed, and distinct from the audit writer's own lock namespace. */
        const val PREFIX_LOCK_NAMESPACE = 0x5245_4749_0000_0000L
    }
}

@ApplicationScoped
class ExceptionItemRepository : PanacheRepositoryBase<ExceptionItem, Long> {

    fun open(): List<ExceptionItem> = list("state = 'open' order by area, id")

    /**
     * The worklist, filtered by state — ADM-7. Open items first within the "all" view, because
     * the resolved ones are history and the open ones are the job.
     */
    fun byState(state: String?): List<ExceptionItem> =
        if (state == null) list("from ExceptionItem order by state, area, id")
        else list("state = ?1 order by area, id", state)

    fun forEntity(entityType: String, entityId: Long): List<ExceptionItem> =
        list("linkedEntityType = ?1 and linkedEntityId = ?2", entityType, entityId)
}
