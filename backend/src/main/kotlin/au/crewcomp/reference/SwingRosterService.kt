package au.crewcomp.reference

import au.crewcomp.engine.Shift
import au.crewcomp.people.Assignment
import au.crewcomp.people.AssignmentClashException
import au.crewcomp.people.AssignmentRepository
import au.crewcomp.people.AssignmentService
import au.crewcomp.people.Person
import au.crewcomp.people.PersonRepository
import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.Role
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

/**
 * The swing roster board — the Coolibah portal's "Onboard and off swing" page, per swing.
 *
 * The portal's board had three moves: bring somebody onboard, send somebody ashore, and set the
 * watch they keep while they are here (days, nights, or none yet). Here every one of them is an
 * assignment write through [AssignmentService] — the same door the planner uses, with the same
 * clash check and audit event — so the board and the slot planner are two views of one roster:
 *
 *  - **Onboard** puts the person into a free slot that takes their position, preferring one with
 *    no watch attached; if no slot is free a new one is made for them, because the slot model is
 *    a description of who is on the vessel, not a cap on it (the portal seed made its slots the
 *    same way, one per person on the crew list).
 *  - **Ashore** removes their assignment(s) on the swing.
 *  - **Watch** moves them to a slot on that shift — day is Shift 1, night is Shift 2, as the seed
 *    assumed — keeping the dates of every leg they held. Quotas are evaluated per shift (§5.3),
 *    so the watch a person keeps is a fact the engine reads, not a label.
 *
 * Switching the swings — the crew change — flips which crew the swing carries: the old crew's
 * assignments go, fill-ins stay, and the caller rosters the other crew from the rotation in a
 * transaction of its own, so a roster that cannot be completed never undoes the switch.
 */
@ApplicationScoped
class SwingRosterService(
    private val crewChanges: CrewChangeRepository,
    private val people: PersonRepository,
    private val slots: PositionSlotRepository,
    private val assignments: AssignmentRepository,
    private val assignmentService: AssignmentService,
    private val policy: AccessPolicy,
    private val audit: AuditWriter,
) {

    enum class Watch(val wire: String, val shift: Shift) {
        DAY("day", Shift.SHIFT_1),
        NIGHT("night", Shift.SHIFT_2),
        NONE("none", Shift.NOT_APPLICABLE);

        companion object {
            fun fromWire(wire: String): Watch =
                entries.firstOrNull { it.wire == wire.trim().lowercase() }
                    ?: throw IllegalArgumentException("A watch is day, night or none — not \"$wire\"")
        }
    }

    @Transactional
    fun bringOnboard(abbrev: String, ccId: String, personId: Long, acknowledgeClash: Boolean) {
        policy.require(Role.CREW_COORDINATOR, Role.SYSTEM_ADMINISTRATOR)
        val crewChange = crewChange(abbrev, ccId)
        val person = people.findById(personId) ?: throw EntityNotFoundException("No person $personId")
        val existing = assignments.forCrewChangeUnscoped(crewChange.requiredId)
        require(existing.none { it.person.requiredId == personId }) { "${person.name} is already on $ccId" }

        // Asked before anything is written: an exception out of assign() would mark the
        // transaction rollback-only, and this one is meant to reach the mapper as a 409.
        val clashes = assignmentService.clashesFor(person, crewChange.fromDate, crewChange.toDate)
        if (clashes.isNotEmpty() && !acknowledgeClash) throw AssignmentClashException(clashes)

        val slot = freeSlot(existing, person, Shift.NOT_APPLICABLE)
            ?: freeSlot(existing, person, null)
            ?: makeSlot(person, Shift.NOT_APPLICABLE)
        assignmentService.assign(abbrev, ccId, slot.ref, personId, acknowledgeClash = true)
    }

    @Transactional
    fun sendAshore(abbrev: String, ccId: String, personId: Long) {
        policy.require(Role.CREW_COORDINATOR, Role.SYSTEM_ADMINISTRATOR)
        val crewChange = crewChange(abbrev, ccId)
        val mine = assignments.forCrewChangeUnscoped(crewChange.requiredId).filter { it.person.requiredId == personId }
        require(mine.isNotEmpty()) { "Person $personId is not on $ccId" }
        mine.forEach { assignmentService.unassign(it.requiredId) }
    }

    @Transactional
    fun setWatch(abbrev: String, ccId: String, personId: Long, watch: String) {
        policy.require(Role.CREW_COORDINATOR, Role.SYSTEM_ADMINISTRATOR)
        val wanted = Watch.fromWire(watch)
        val crewChange = crewChange(abbrev, ccId)
        val existing = assignments.forCrewChangeUnscoped(crewChange.requiredId)
        val mine = existing.filter { it.person.requiredId == personId }
        require(mine.isNotEmpty()) { "Person $personId is not on $ccId, so there is no watch to set" }
        val person = mine.first().person
        if (mine.all { slots.byRef(it.slotRef)?.shift == wanted.shift }) return

        val target = freeSlot(existing - mine.toSet(), person, wanted.shift) ?: makeSlot(person, wanted.shift)
        // Every leg they held, moved as it was: a handover keeps its dates on the new slot.
        val legs = mine.map { Triple(it.requiredId, it.fromDate, it.toDate) }
        legs.forEach { assignmentService.unassign(it.first) }
        assignments.flush()
        legs.forEach { (_, from, to) ->
            assignmentService.assign(abbrev, ccId, target.ref, personId, from, to, acknowledgeClash = true)
        }
    }

    /**
     * The crew change: the swing now carries the other crew. The old crew's assignments go; a
     * fill-in — no rotation, or already the incoming crew — stays. Rostering the incoming crew is
     * the caller's next step, in its own transaction.
     */
    @Transactional
    fun switchCrew(abbrev: String, ccId: String): CrewChange {
        policy.require(Role.CREW_COORDINATOR, Role.SYSTEM_ADMINISTRATOR)
        val crewChange = crewChange(abbrev, ccId)
        val was = crewChange.rotation
            ?: throw IllegalArgumentException("$ccId is not a crew A or B swing, so there is no other crew to switch to")
        val now = SwingService.other(was)
        val ashore = assignments.forCrewChangeUnscoped(crewChange.requiredId).filter { it.person.rotation == was }
        ashore.forEach { assignmentService.unassign(it.requiredId) }
        crewChange.rotation = now
        crewChange.stampUpdated(policy.actor().label)
        audit.record(
            entityType = "CrewChange",
            event = "crew_change.crew_switched",
            entityId = crewChange.id,
            businessKey = "$abbrev/$ccId",
            before = mapOf("crew" to was, "ashore" to ashore.size),
            after = mapOf("crew" to now),
        )
        return crewChange
    }

    // -----------------------------------------------------------------------

    private fun crewChange(abbrev: String, ccId: String): CrewChange {
        val crewChange = crewChanges.byBusinessKey(ccId, abbrev)
            ?: throw EntityNotFoundException("No swing $ccId on $abbrev")
        policy.assertCanSeePartnership(crewChange.partnership.requiredId)
        return crewChange
    }

    /** A slot nobody on the swing holds, on [shift] (any shift when null), that takes the person's position. */
    private fun freeSlot(taken: Collection<Assignment>, person: Person, shift: Shift?): PositionSlot? {
        val takenRefs = taken.map { it.slotRef }.toSet()
        return slots.allOrdered().firstOrNull { slot ->
            slot.ref !in takenRefs &&
                (shift == null || slot.shift == shift) &&
                slot.allowedPositions.any { it.requiredId == person.position.requiredId }
        }
    }

    private fun makeSlot(person: Person, shift: Shift): PositionSlot {
        val next = (slots.allOrdered().maxOfOrNull { it.ref } ?: 0) + 1
        val slot = PositionSlot().apply {
            ref = next
            this.shift = shift
            notes = "Made by the swing roster for a ${person.position.name}"
            allowedPositions = mutableSetOf(person.position)
            stampCreated(policy.actor().label)
        }
        slots.persist(slot)
        slots.flush()
        audit.record(
            entityType = "PositionSlot",
            event = "position_slot.created",
            entityId = slot.id,
            businessKey = "slot-$next",
            after = mapOf("ref" to next, "shift" to shift.wire, "positions" to listOf(person.position.name), "source" to "swing roster"),
        )
        return slot
    }
}
