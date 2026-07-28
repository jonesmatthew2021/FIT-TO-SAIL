package au.crewcomp.sync

import au.crewcomp.evidence.EvidenceDocument
import au.crewcomp.notify.Notification
import au.crewcomp.people.Assignment
import au.crewcomp.people.CrewStatement
import au.crewcomp.people.LeaveRecord
import au.crewcomp.people.Person
import au.crewcomp.people.QualificationHolding
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager

/**
 * §10.3 delta reads.
 *
 * Every method here takes the owning `personId` as a **required** parameter and every query
 * filters on it. That is deliberate and slightly redundant: the caller
 * ([SyncService]) has already established that the person is the authenticated crew member, but
 * a sync query that could be called with someone else's id is one refactor away from being the
 * whole-fleet leak. There is no unscoped variant to reach for.
 *
 * As elsewhere in this codebase, the queries fetch-join whatever the DTO mapping touches — the
 * mapping runs after the transaction closes.
 */
@ApplicationScoped
class SyncDeltaRepository(private val em: EntityManager) {

    fun person(personId: Long, since: Long): Person? = em.createQuery(
        "select p from Person p join fetch p.position join fetch p.partnership " +
            "where p.id = :personId and p.updatedSeq > :since",
        Person::class.java,
    ).setParameter("personId", personId).setParameter("since", since).resultList.firstOrNull()

    fun holdings(personId: Long, since: Long): List<QualificationHolding> = em.createQuery(
        "select h from QualificationHolding h join fetch h.requirement join fetch h.person " +
            "where h.person.id = :personId and h.updatedSeq > :since order by h.updatedSeq",
        QualificationHolding::class.java,
    ).setParameter("personId", personId).setParameter("since", since).resultList

    fun assignments(personId: Long, since: Long): List<Assignment> = em.createQuery(
        "select a from Assignment a join fetch a.crewChange join fetch a.partnership " +
            "join fetch a.person where a.person.id = :personId and a.updatedSeq > :since " +
            "order by a.updatedSeq",
        Assignment::class.java,
    ).setParameter("personId", personId).setParameter("since", since).resultList

    fun leave(personId: Long, since: Long): List<LeaveRecord> = em.createQuery(
        "select l from LeaveRecord l join fetch l.person " +
            "where l.person.id = :personId and l.updatedSeq > :since order by l.updatedSeq",
        LeaveRecord::class.java,
    ).setParameter("personId", personId).setParameter("since", since).resultList

    /** Notifications are keyed by user account, so the person is reached through the linkage. */
    fun notifications(personId: Long, since: Long): List<Notification> = em.createQuery(
        "select n from Notification n join fetch n.recipient r " +
            "where r.person.id = :personId and n.updatedSeq > :since order by n.updatedSeq",
        Notification::class.java,
    ).setParameter("personId", personId).setParameter("since", since).resultList

    fun submissions(personId: Long, since: Long): List<EvidenceDocument> = em.createQuery(
        // `requirementHint` is left-joined because the DTO mapping reads it after this
        // transaction closes, and most submissions carry none.
        "select d from EvidenceDocument d join fetch d.person left join fetch d.requirementHint " +
            "where d.person.id = :personId and d.updatedSeq > :since order by d.updatedSeq",
        EvidenceDocument::class.java,
    ).setParameter("personId", personId).setParameter("since", since).resultList

    fun crewStatements(personId: Long, since: Long): List<CrewStatement> = em.createQuery(
        "select s from CrewStatement s join fetch s.requirement " +
            "where s.person.id = :personId and s.updatedSeq > :since order by s.updatedSeq",
        CrewStatement::class.java,
    ).setParameter("personId", personId).setParameter("since", since).resultList

    fun tombstones(personId: Long, since: Long): List<SyncTombstone> = em.createQuery(
        "select t from SyncTombstone t " +
            "where t.personId = :personId and t.seq > :since order by t.seq",
        SyncTombstone::class.java,
    ).setParameter("personId", personId).setParameter("since", since).resultList

    /**
     * The current value of the global sequence, read **without consuming one**.
     *
     * This is the cursor a client is handed, and it must be taken from the same transaction and
     * snapshot as the rows above. `last_value` is per-session and meaningless before the session
     * has called nextval, so the cursor is the highest sequence actually visible in this
     * snapshot: the max over the tracked tables. A value the client has already seen is
     * harmless (it re-receives a row); a value ahead of what it received would silently skip
     * rows, so the max of what was visible is the only safe answer.
     */
    fun currentCursor(): Long = (
        em.createNativeQuery(
            """
            select coalesce(greatest(
                (select max(updated_seq) from person),
                (select max(updated_seq) from qualification_holding),
                (select max(updated_seq) from assignment),
                (select max(updated_seq) from leave_record),
                (select max(updated_seq) from notification),
                (select max(updated_seq) from evidence_document),
                -- Every person-scoped table streamed in a delta must be here. A table left out
                -- keeps its rows above the cursor the client is handed, so the same rows arrive in
                -- every delta forever — harmless, invisible, and permanent.
                (select max(updated_seq) from crew_statement),
                (select max(seq) from sync_tombstone)
            ), 0)
            """.trimIndent(),
        ).singleResult as Number
        ).toLong()

    /**
     * The reference-data cursor.
     *
     * Reference rows are not streamed row-by-row (see V2__sync_change_tracking.sql): a matrix
     * publication changes most of a client's cached rules at once, and a partial application
     * would render a coherent-looking view assembled from two matrix versions. The client
     * compares this scalar and re-snapshots when it moves.
     */
    fun referenceCursor(): Long = (
        em.createNativeQuery(
            """
            select coalesce(greatest(
                (select max(updated_seq) from partnership),
                (select max(updated_seq) from crew_position),
                (select max(updated_seq) from crew_change),
                (select max(updated_seq) from requirement),
                (select max(updated_seq) from matrix_version),
                (select max(updated_seq) from requirement_rule),
                (select max(updated_seq) from conditional_rule),
                (select max(updated_seq) from quota_rule),
                (select max(updated_seq) from position_slot)
            ), 0)
            """.trimIndent(),
        ).singleResult as Number
        ).toLong()
}

@ApplicationScoped
class SyncTombstoneRepository : PanacheRepositoryBase<SyncTombstone, Long>
