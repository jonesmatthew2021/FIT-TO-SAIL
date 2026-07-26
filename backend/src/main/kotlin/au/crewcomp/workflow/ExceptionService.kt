package au.crewcomp.workflow

import au.crewcomp.people.QualificationHolding
import au.crewcomp.notify.NotificationKind
import au.crewcomp.notify.NotificationService
import au.crewcomp.people.QualificationHoldingRepository
import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.Role
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.Instant

/**
 * ADM-7 — the data-quality worklist (§4.4, §11).
 *
 * The system's standing position on dirty data is that anomalies are **preserved and flagged,
 * never silently cleaned** (§1, §11): a duplicated Sam #, a register title that never mapped to a
 * catalogue code, a holding nobody ever established. Each becomes an [ExceptionItem] rather than
 * a correction nobody can audit. This service is where they are read, resolved and — for
 * something a steward spots that no migration produced — raised.
 *
 * The "unknown holdings" chase list (Q11) is here too, and is deliberately *not* stored as
 * exception rows: it is a live query over holdings whose status was never established, so it
 * shrinks by itself the moment someone records the answer. Materialising it would mean resolving
 * a row and updating a holding as two separate acts, and the pair would drift.
 */
@ApplicationScoped
class ExceptionService(
    private val exceptions: ExceptionItemRepository,
    private val holdings: QualificationHoldingRepository,
    private val policy: AccessPolicy,
    private val audit: AuditWriter,
    private val notifications: NotificationService,
) {

    /** @param state `open`, `resolved`, or null for everything. */
    @Transactional
    fun list(state: String? = null): List<ExceptionItem> {
        policy.require(*READERS)
        require(state == null || state == OPEN || state == RESOLVED) {
            "An exception item is either open or resolved, not '$state'"
        }
        return exceptions.byState(state)
    }

    /**
     * Q11's standing chase list: every holding whose status is `unknown`.
     *
     * Unscoped, like the expiry scan — it is a back-office worklist over the whole fleet, and the
     * role check above is what gates it.
     */
    @Transactional
    fun unknownHoldings(): List<QualificationHolding> {
        policy.require(*READERS)
        return holdings.unknownUnscoped()
    }

    @Transactional
    fun raise(
        area: String,
        description: String,
        linkedEntityType: String? = null,
        linkedEntityId: Long? = null,
    ): ExceptionItem {
        policy.require(*RESOLVERS)

        val cleanArea = area.trim()
        val cleanDescription = description.trim()
        require(cleanArea.isNotEmpty()) { "An exception item needs an area" }
        require(cleanDescription.isNotEmpty()) { "An exception item needs a description" }

        val item = ExceptionItem().apply {
            this.area = cleanArea
            this.description = cleanDescription
            state = OPEN
            this.linkedEntityType = linkedEntityType?.ifBlank { null }
            this.linkedEntityId = linkedEntityId
            stampCreated(policy.actor().label)
        }
        exceptions.persist(item)

        audit.record(
            entityType = "ExceptionItem",
            event = "exception.raised",
            entityId = item.id,
            businessKey = cleanArea,
            after = snapshot(item),
        )

        // §9: "new exceptions -> Data Steward". Empty until back-office accounts exist (ADR 0003),
        // and deliberately not fatal when it is — see `RegisterService.notifyWorkflow`.
        notifications.raiseForRoles(
            roles = listOf(Role.DATA_STEWARD),
            kind = NotificationKind.EXCEPTION_RAISED,
            title = "A data-quality item has been raised",
            body = "$cleanArea: $cleanDescription",
            deepLink = "/exceptions",
        )
        return item
    }

    /**
     * Closes an item with the note that says what was done.
     *
     * The note is **required**. An exception resolved with no explanation is indistinguishable
     * from one dismissed to clear the list, and §11's whole argument is that every fix-up is
     * visible afterwards.
     */
    @Transactional
    fun resolve(exceptionItemId: Long, note: String): ExceptionItem {
        policy.require(*RESOLVERS)

        val clean = note.trim()
        require(clean.isNotEmpty()) { "Resolving an exception needs a note saying what was done" }

        val item = requireItem(exceptionItemId)
        require(item.isOpen) { "Exception ${item.requiredId} is already resolved" }
        val before = snapshot(item)

        val actor = policy.actor()
        item.state = RESOLVED
        item.resolutionNote = clean
        item.resolvedAt = Instant.now()
        item.resolvedBy = actor.label
        item.stampUpdated(actor.label)

        audit.record(
            entityType = "ExceptionItem",
            event = "exception.resolved",
            entityId = item.id,
            businessKey = item.area,
            before = before,
            after = snapshot(item),
        )
        return item
    }

    /**
     * Reopens an item that was resolved wrongly. The original resolution note is kept in the
     * audit event's `before` state, so "we thought we had fixed this" stays on the record.
     */
    @Transactional
    fun reopen(exceptionItemId: Long): ExceptionItem {
        policy.require(*RESOLVERS)

        val item = requireItem(exceptionItemId)
        require(!item.isOpen) { "Exception ${item.requiredId} is already open" }
        val before = snapshot(item)

        item.state = OPEN
        item.resolutionNote = null
        item.resolvedAt = null
        item.resolvedBy = null
        item.stampUpdated(policy.actor().label)

        audit.record(
            entityType = "ExceptionItem",
            event = "exception.reopened",
            entityId = item.id,
            businessKey = item.area,
            before = before,
            after = snapshot(item),
        )
        return item
    }

    private fun requireItem(exceptionItemId: Long): ExceptionItem =
        exceptions.findById(exceptionItemId)
            ?: throw EntityNotFoundException("No exception item $exceptionItemId")

    private fun snapshot(item: ExceptionItem): Map<String, Any?> = mapOf(
        "area" to item.area,
        "description" to item.description,
        "state" to item.state,
        "linkedEntityType" to item.linkedEntityType,
        "linkedEntityId" to item.linkedEntityId,
        "resolutionNote" to item.resolutionNote,
        "resolvedBy" to item.resolvedBy,
    )

    companion object {
        const val OPEN = "open"
        const val RESOLVED = "resolved"

        /** The worklist is visible to every back-office role; a crew member has no business here. */
        private val READERS = arrayOf(
            Role.DATA_STEWARD, Role.COMPLIANCE_LEAD, Role.CREW_COORDINATOR,
            Role.WORKFLOW_MANAGER, Role.SYSTEM_ADMINISTRATOR,
        )

        /** Data quality is the Data Steward's job; the Compliance Lead owns the catalogue half. */
        private val RESOLVERS = arrayOf(
            Role.DATA_STEWARD, Role.COMPLIANCE_LEAD, Role.SYSTEM_ADMINISTRATOR,
        )
    }
}
