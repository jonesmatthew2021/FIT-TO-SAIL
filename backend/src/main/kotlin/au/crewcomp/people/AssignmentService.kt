package au.crewcomp.people

import au.crewcomp.engine.PersonStatus
import au.crewcomp.notify.NotificationKind
import au.crewcomp.notify.NotificationService
import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.Role
import au.crewcomp.reference.CrewChange
import au.crewcomp.reference.CrewChangeRepository
import au.crewcomp.reference.PositionSlot
import au.crewcomp.reference.PositionSlotRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.LocalDate

/**
 * Raised when the person would be in two places at once (or on leave) for part of the window.
 *
 * §5.4's rule for the *suggestion* list is that a clash is shown and scored, never filtered out.
 * The write path takes the same position one step further: it does not refuse the assignment, it
 * refuses to make it *silently*. The coordinator sees what the clash is and assigns anyway if
 * they mean to — and the acknowledgement lands in the audit event, the same shape as Q17's late
 * submission.
 */
class AssignmentClashException(val clashes: List<String>) :
    RuntimeException(clashes.joinToString("; "))

/**
 * The validated write path for roster assignments — ADM-2's missing half (§4.3, §5.3).
 *
 * The planner could already rank candidates for an open slot; this is what lets it fill one. As
 * with [HoldingService] there is exactly one door, because both AUTH-1 ("the backend re-validates
 * every write") and the audit guarantee rest on that.
 *
 * The validations here are the slot model's invariants, not advice:
 *
 *  - the person's position must be one the slot accepts (slots 15/16 take AE **or** GPH, §4.1);
 *  - the window must sit inside the swing — an assignment is a sub-range of it, never a superset;
 *  - two people may share a slot only *sequentially*, which is what a mid-swing handover is. An
 *    overlap is a double-booking and is rejected outright, since no coordinator means it.
 *
 * A clash with the person's own commitments elsewhere is different in kind: it may be exactly
 * what the coordinator intends (a cross-partnership loan, leave about to be cancelled), so it is
 * surfaced and acknowledged rather than blocked. See [AssignmentClashException].
 */
@ApplicationScoped
class AssignmentService(
    private val assignments: AssignmentRepository,
    private val people: PersonRepository,
    private val leaveRecords: LeaveRecordRepository,
    private val crewChanges: CrewChangeRepository,
    private val slots: PositionSlotRepository,
    private val accounts: UserAccountRepository,
    private val notifications: NotificationService,
    private val policy: AccessPolicy,
    private val audit: AuditWriter,
) {

    /**
     * Assigns [personId] to [slotRef] of a swing, for the whole swing unless [from]/[to] narrow
     * it to a handover leg.
     *
     * @param acknowledgeClash proceed despite an overlapping assignment or leave record. The
     *   acknowledgement and the clashes it covered are both recorded in the audit event.
     */
    @Transactional
    fun assign(
        partnershipAbbrev: String,
        ccId: String,
        slotRef: Int,
        personId: Long,
        from: LocalDate? = null,
        to: LocalDate? = null,
        acknowledgeClash: Boolean = false,
    ): Assignment {
        policy.require(Role.CREW_COORDINATOR, Role.SYSTEM_ADMINISTRATOR)

        val crewChange = requireCrewChange(partnershipAbbrev, ccId)
        val slot = slots.byRef(slotRef)
            ?: throw EntityNotFoundException("No position slot with ref $slotRef")
        val person = people.findById(personId)
            ?: throw EntityNotFoundException("No person $personId")

        val fromDate = from ?: crewChange.fromDate
        val toDate = to ?: crewChange.toDate

        // Before the overlap read, not after: two concurrent assigns to one slot must serialise
        // so the second sees the first's row. The V11 exclusion constraint is the backstop.
        assignments.lockSlot(crewChange.requiredId, slotRef)
        validate(person, slot, crewChange, fromDate, toDate)

        val clashes = clashesFor(person, fromDate, toDate)
        if (clashes.isNotEmpty() && !acknowledgeClash) throw AssignmentClashException(clashes)

        val actor = policy.actor()
        val assignment = Assignment().apply {
            this.person = person
            this.crewChange = crewChange
            this.partnership = crewChange.partnership
            this.slotRef = slotRef
            this.fromDate = fromDate
            this.toDate = toDate
            stampCreated(actor.label)
        }
        assignments.persist(assignment)

        audit.record(
            entityType = "Assignment",
            event = "assignment.created",
            entityId = assignment.id,
            businessKey = businessKey(crewChange, slotRef, person),
            after = snapshot(assignment) + mapOf(
                "clashAcknowledged" to clashes.isNotEmpty(),
                "clashes" to clashes,
            ),
        )

        notifyPerson(
            person = person,
            kind = NotificationKind.ASSIGNMENT_ADDED,
            title = "You have a new assignment",
            body = "${crewChange.partnership.abbrev} ${crewChange.ccId}, slot $slotRef, " +
                "$fromDate to $toDate.",
            crewChange = crewChange,
        )

        return assignment
    }

    /**
     * Removes an assignment. The row is deleted rather than flagged: the swing evaluation is a
     * function of the assignments that exist, and a soft-deleted one would have to be filtered
     * out of every query that touches it — the kind of filter a later query forgets. The audit
     * event carries the whole `before` state, so nothing is lost, and the `AFTER DELETE` trigger
     * writes the tombstone that removes it from the crew member's device (ADR 0009 §2).
     */
    @Transactional
    fun unassign(assignmentId: Long) {
        policy.require(Role.CREW_COORDINATOR, Role.SYSTEM_ADMINISTRATOR)

        val assignment = assignments.findById(assignmentId)
            ?: throw EntityNotFoundException("No assignment $assignmentId")
        policy.assertCanSeePartnership(assignment.partnership.requiredId)

        // Read everything the audit event and the notification need *before* the delete: after it
        // the instance is removed from the persistence context and its lazy sides are gone.
        val person = assignment.person
        val crewChange = assignment.crewChange
        val slotRef = assignment.slotRef
        val before = snapshot(assignment)

        assignments.delete(assignment)

        audit.record(
            entityType = "Assignment",
            event = "assignment.removed",
            entityId = assignmentId,
            businessKey = businessKey(crewChange, slotRef, person),
            before = before,
        )

        notifyPerson(
            person = person,
            kind = NotificationKind.ASSIGNMENT_REMOVED,
            title = "An assignment has been removed",
            body = "${crewChange.partnership.abbrev} ${crewChange.ccId}, slot $slotRef, " +
                "${before["from"]} to ${before["to"]}.",
            crewChange = crewChange,
        )
    }

    // -----------------------------------------------------------------------
    // Validation
    // -----------------------------------------------------------------------

    private fun requireCrewChange(partnershipAbbrev: String, ccId: String): CrewChange {
        val crewChange = crewChanges.byBusinessKey(ccId, partnershipAbbrev)
            ?: throw EntityNotFoundException("No crew change $ccId for partnership $partnershipAbbrev")
        policy.assertCanSeePartnership(crewChange.partnership.requiredId)
        return crewChange
    }

    private fun validate(
        person: Person,
        slot: PositionSlot,
        crewChange: CrewChange,
        fromDate: LocalDate,
        toDate: LocalDate,
    ) {
        require(person.status == PersonStatus.ACTIVE) {
            "${person.name} is ${person.status.wire} and cannot be assigned"
        }
        require(slot.allowedPositions.any { it.requiredId == person.position.requiredId }) {
            "Slot ${slot.ref} takes ${slot.allowedPositions.joinToString(" or ") { it.name }}, " +
                "and ${person.name} is ${person.position.name}"
        }
        require(!toDate.isBefore(fromDate)) {
            "An assignment cannot end ($toDate) before it starts ($fromDate)"
        }
        require(!fromDate.isBefore(crewChange.fromDate) && !toDate.isAfter(crewChange.toDate)) {
            "An assignment must sit inside the swing ${crewChange.fromDate}..${crewChange.toDate}, " +
                "and $fromDate..$toDate does not"
        }

        val overlapping = assignments.forCrewChangeUnscoped(crewChange.requiredId)
            .filter { it.slotRef == slot.ref && overlaps(it.fromDate, it.toDate, fromDate, toDate) }
        require(overlapping.isEmpty()) {
            "Slot ${slot.ref} is already covered ${overlapping.joinToString(", ") {
                "${it.fromDate}..${it.toDate} by ${it.person.name}"
            }}. Two people share a slot only in sequence — shorten one of the two windows."
        }
    }

    /**
     * The person's own commitments overlapping the window: assignments anywhere (including
     * another slot of this same swing) and leave that has not been declined or cancelled.
     *
     * Public so a caller rostering many people at once (the swing pattern) can *ask* before
     * assigning: an exception out of [assign] marks the shared transaction rollback-only, and one
     * clash would otherwise undo everyone else's assignment in the same run.
     */
    fun clashesFor(person: Person, fromDate: LocalDate, toDate: LocalDate): List<String> {
        val personId = person.requiredId

        val assignmentClashes = assignments
            .overlappingForPersonUnscoped(personId, fromDate, toDate)
            .map {
                "already assigned to ${it.partnership.abbrev} ${it.crewChange.ccId} slot " +
                    "${it.slotRef}, ${it.fromDate}..${it.toDate}"
            }

        val leaveClashes = leaveRecords
            .overlappingForPersonUnscoped(personId, fromDate, toDate)
            .map { "on ${it.kind.replace('_', ' ')} ${it.fromDate}..${it.toDate}" }

        return assignmentClashes + leaveClashes
    }

    private fun overlaps(aFrom: LocalDate, aTo: LocalDate, bFrom: LocalDate, bTo: LocalDate): Boolean =
        !aFrom.isAfter(bTo) && !aTo.isBefore(bFrom)

    // -----------------------------------------------------------------------
    // Audit and notification
    // -----------------------------------------------------------------------

    private fun businessKey(crewChange: CrewChange, slotRef: Int, person: Person): String =
        "${crewChange.partnership.abbrev}/${crewChange.ccId}/slot-$slotRef/${person.sam}"

    private fun snapshot(assignment: Assignment): Map<String, Any?> = mapOf(
        "personId" to assignment.person.requiredId,
        "sam" to assignment.person.sam,
        "crewChangeId" to assignment.crewChange.requiredId,
        "ccId" to assignment.crewChange.ccId,
        "partnership" to assignment.partnership.abbrev,
        "slotRef" to assignment.slotRef,
        "from" to assignment.fromDate.toString(),
        "to" to assignment.toDate.toString(),
    )

    /**
     * §9: an assignment change is a domain event the crew member is told about. Silent when they
     * have no account yet — most of the fleet does not, until the identity spike lands, and a
     * roster change must not fail because of that.
     *
     * SEC-13: the title carries no detail, because the title is all a push payload may hold.
     */
    private fun notifyPerson(
        person: Person,
        kind: NotificationKind,
        title: String,
        body: String,
        crewChange: CrewChange,
    ) {
        val account = accounts.forPerson(person.requiredId) ?: return
        notifications.raise(
            recipientUserAccountId = account.requiredId,
            kind = kind,
            title = title,
            body = body,
            deepLink = "crewcomp://roster/${crewChange.requiredId}",
        )
    }
}
