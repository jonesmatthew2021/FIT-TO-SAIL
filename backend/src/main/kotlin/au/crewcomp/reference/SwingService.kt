package au.crewcomp.reference

import au.crewcomp.engine.PersonStatus
import au.crewcomp.people.AssignmentRepository
import au.crewcomp.people.AssignmentService
import au.crewcomp.people.Person
import au.crewcomp.people.PersonRepository
import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.Role
import au.crewcomp.platform.time.BusinessClock
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The swing pattern — the Coolibah portal's four-week A/B rotation, as a service per ship.
 *
 * The engine evaluates a swing given its roster (§5). What the portal adds, and this brings across,
 * is what *produces* the swings and the rosters: a pattern (anchor day out, cycle, which crew flies
 * on the anchor) that gives every swing a date, an A/B rotation on each person that gives every
 * swing a crew, and the office's typed dates over the pattern's when a changeover slips. The rules
 * are the portal's, kept exactly because its users know them:
 *
 *  - Swing k flies out on anchor + k × cycle and home on anchor + (k + 1) × cycle; the crew is
 *    onboard from the day out to the day before the day home, which is the window the engine
 *    reads certificates against (a ticket expiring three days before fly-out is a blocker for
 *    that swing, one expiring onboard is a watch).
 *  - The crew alternates each swing, starting from the crew named on the anchor.
 *  - A date the office types is kept against the swing's number, so a date given three swings
 *    ahead is still that swing's when it comes round; "use the pattern" puts the pattern's back.
 *
 * Rostering from the rotation is the one place this service writes assignments, and it does so
 * through [AssignmentService] — the same door the planner uses, with the same clash check and
 * the same audit event — so a crew member already committed elsewhere is *reported* as unrostered
 * rather than double-booked (§5.4).
 */
@ApplicationScoped
class SwingService(
    private val partnerships: PartnershipRepository,
    private val crewChanges: CrewChangeRepository,
    private val people: PersonRepository,
    private val slots: PositionSlotRepository,
    private val assignments: AssignmentRepository,
    private val assignmentService: AssignmentService,
    private val clock: BusinessClock,
    private val policy: AccessPolicy,
    private val audit: AuditWriter,
) {

    /** One swing as the pattern reads it. `from`/`to` is the onboard window the engine evaluates. */
    data class PatternSwing(val k: Int, val crew: String, val flyOut: LocalDate, val flyHome: LocalDate) {
        val from: LocalDate get() = flyOut
        val to: LocalDate get() = flyHome.minusDays(1)
    }

    data class Pattern(val anchor: LocalDate, val cycleDays: Int, val anchorCrew: String) {
        fun at(k: Int): PatternSwing = PatternSwing(
            k = k,
            crew = if (k % 2 == 0) anchorCrew else other(anchorCrew),
            flyOut = anchor.plusDays(k.toLong() * cycleDays),
            flyHome = anchor.plusDays((k + 1).toLong() * cycleDays),
        )

        /** Which swing a date falls in — the one flying out on or before it. */
        fun indexOf(date: LocalDate): Int =
            Math.floorDiv(ChronoUnit.DAYS.between(anchor, date), cycleDays.toLong()).toInt()
    }

    /** Strings, not entities: read after the transaction that produced them has closed. */
    data class Unrostered(val ccId: String, val personId: Long, val name: String, val position: String, val reason: String)

    /** What [createMissing] found and made: the swings wanted, and which of them are new. */
    data class Created(val swings: List<CrewChange>, val createdCcIds: List<String>)

    fun patternOf(partnership: Partnership): Pattern? {
        val anchor = partnership.rosterAnchor ?: return null
        val cycle = partnership.rosterCycleDays ?: return null
        val crew = partnership.rosterAnchorCrew ?: return null
        return Pattern(anchor, cycle, crew)
    }

    @Transactional
    fun partnership(abbrev: String): Partnership =
        partnerships.byAbbrev(abbrev) ?: throw EntityNotFoundException("No partnership $abbrev")

    @Transactional
    fun setPattern(abbrev: String, anchor: LocalDate?, cycleDays: Int?, anchorCrew: String?): Partnership {
        policy.require(Role.CREW_COORDINATOR, Role.COMPLIANCE_LEAD, Role.SYSTEM_ADMINISTRATOR)
        policy.assertNotReadOnlyActor()
        val partnership = partnership(abbrev)
        val clearing = anchor == null && cycleDays == null && anchorCrew == null
        if (!clearing) {
            requireNotNull(anchor) { "A pattern needs the day its first swing flies out" }
            require(cycleDays != null && cycleDays > 0) { "A pattern needs a cycle in days (the portal's is 28)" }
            require(anchorCrew in CREWS) { "The crew flying out on the anchor is A or B" }
        }
        val before = mapOf("anchor" to partnership.rosterAnchor?.toString(), "cycleDays" to partnership.rosterCycleDays, "anchorCrew" to partnership.rosterAnchorCrew)
        partnership.rosterAnchor = anchor
        partnership.rosterCycleDays = cycleDays
        partnership.rosterAnchorCrew = anchorCrew
        partnership.stampUpdated(policy.actor().label)
        audit.record(
            entityType = "Partnership",
            event = "partnership.pattern_set",
            entityId = partnership.id,
            businessKey = partnership.abbrev,
            before = before,
            after = mapOf("anchor" to anchor?.toString(), "cycleDays" to cycleDays, "anchorCrew" to anchorCrew),
        )
        return partnership
    }

    /**
     * The swing onboard now and the [lookahead] after it, created from the pattern where they do
     * not exist yet. Idempotent: a swing already on the calendar for a pattern number is left
     * exactly as it is. Rostering the new ones is the caller's next step ([rosterFromRotation],
     * one transaction per swing), so a roster that cannot be completed never undoes the calendar.
     */
    @Transactional
    fun createMissing(abbrev: String, lookahead: Int = LOOKAHEAD): Created {
        policy.require(Role.CREW_COORDINATOR, Role.COMPLIANCE_LEAD, Role.SYSTEM_ADMINISTRATOR)
        val partnership = partnership(abbrev)
        val pattern = patternOf(partnership)
            ?: return Created(crewChanges.forPartnership(partnership.requiredId), emptyList())

        // Mutable, and grown as swings are made: the next number is read off this list, so a run
        // that makes three swings must see the first two before naming the third.
        val existing = crewChanges.forPartnership(partnership.requiredId).toMutableList()
        val k0 = pattern.indexOf(clock.today())
        val created = mutableListOf<String>()
        val wanted = (k0..k0 + lookahead).map { k ->
            existing.firstOrNull { it.patternK == k } ?: create(partnership, pattern.at(k), existing).also {
                existing += it
                created += it.ccId
            }
        }
        return Created(wanted, created)
    }

    private fun create(partnership: Partnership, swing: PatternSwing, existing: List<CrewChange>): CrewChange {
        val next = (existing.mapNotNull { CC_NUMBER.find(it.ccId)?.groupValues?.get(1)?.toInt() }.maxOrNull() ?: 0) + 1
        val crewChange = CrewChange().apply {
            ccId = "CC%02d".format(next)
            this.partnership = partnership
            fromDate = swing.from
            toDate = swing.to
            cutoffDate = swing.from.minusDays(CUTOFF_DAYS)
            rotation = swing.crew
            patternK = swing.k
            stampCreated(policy.actor().label)
        }
        crewChanges.persist(crewChange)
        crewChanges.flush()
        audit.record(
            entityType = "CrewChange",
            event = "crew_change.created",
            entityId = crewChange.id,
            businessKey = "${partnership.abbrev}/${crewChange.ccId}",
            after = mapOf("from" to swing.from.toString(), "to" to swing.to.toString(), "crew" to swing.crew, "patternK" to swing.k, "source" to "pattern"),
        )
        return crewChange
    }

    /** The office's dates over the pattern's. Whole-swing assignments move with the window. */
    @Transactional
    fun setDates(abbrev: String, ccId: String, from: LocalDate, to: LocalDate): CrewChange {
        policy.require(Role.CREW_COORDINATOR, Role.COMPLIANCE_LEAD, Role.SYSTEM_ADMINISTRATOR)
        require(!to.isBefore(from)) { "The day home is on or before the day out" }
        return moveWindow(crewChange(abbrev, ccId), from, to, "crew_change.dates_set")
    }

    /** The pattern's dates back — what the office typed is dropped. */
    @Transactional
    fun usePattern(abbrev: String, ccId: String): CrewChange {
        policy.require(Role.CREW_COORDINATOR, Role.COMPLIANCE_LEAD, Role.SYSTEM_ADMINISTRATOR)
        val crewChange = crewChange(abbrev, ccId)
        val k = crewChange.patternK ?: throw IllegalArgumentException("$ccId was not made from the pattern, so there is no pattern date to go back to")
        val pattern = patternOf(crewChange.partnership) ?: throw IllegalArgumentException("${crewChange.partnership.abbrev} has no pattern")
        val swing = pattern.at(k)
        return moveWindow(crewChange, swing.from, swing.to, "crew_change.pattern_restored")
    }

    private fun moveWindow(crewChange: CrewChange, from: LocalDate, to: LocalDate, event: String): CrewChange {
        val before = mapOf("from" to crewChange.fromDate.toString(), "to" to crewChange.toDate.toString(), "cutoff" to crewChange.cutoffDate.toString())
        val oldFrom = crewChange.fromDate
        val oldTo = crewChange.toDate
        crewChange.fromDate = from
        crewChange.toDate = to
        crewChange.cutoffDate = from.minusDays(CUTOFF_DAYS)
        crewChange.stampUpdated(policy.actor().label)
        // An assignment that covered the whole old window covers the whole new one; a handover leg
        // (§4.3) keeps its own dates — it was somebody's deliberate split.
        assignments.forCrewChangeUnscoped(crewChange.requiredId)
            .filter { it.fromDate == oldFrom && it.toDate == oldTo }
            .forEach {
                it.fromDate = from
                it.toDate = to
                it.stampUpdated(policy.actor().label)
            }
        audit.record(
            entityType = "CrewChange",
            event = event,
            entityId = crewChange.id,
            businessKey = "${crewChange.partnership.abbrev}/${crewChange.ccId}",
            before = before,
            after = mapOf("from" to from.toString(), "to" to to.toString(), "cutoff" to crewChange.cutoffDate.toString()),
        )
        return crewChange
    }

    /**
     * Puts the swing's crew into free slots that take their position, and reports who could not
     * be. Every check that could refuse an assignment is made *before* [AssignmentService.assign]
     * is called — a clash, a slot that takes another position, an inactive person — because an
     * exception out of that call would mark this transaction rollback-only and undo the rest.
     */
    @Transactional
    fun rosterFromRotation(abbrev: String, ccId: String): List<Unrostered> {
        policy.require(Role.CREW_COORDINATOR, Role.SYSTEM_ADMINISTRATOR)
        val crewChange = crewChange(abbrev, ccId)
        val partnership = crewChange.partnership
        val crew = crewChange.rotation ?: return emptyList()
        val already = assignments.forCrewChangeUnscoped(crewChange.requiredId)
        val assignedPeople = already.map { it.person.requiredId }.toSet()
        val taken = already.map { it.slotRef }.toMutableSet()
        val free = slots.allOrdered().filter { it.ref !in taken }.toMutableList()

        val unrostered = mutableListOf<Unrostered>()
        people.byPartnershipUnscoped(partnership.requiredId)
            .filter { it.status == PersonStatus.ACTIVE && it.rotation == crew && it.requiredId !in assignedPeople }
            .sortedBy { it.name }
            .forEach { person ->
                val slot = free.firstOrNull { s -> s.allowedPositions.any { it.requiredId == person.position.requiredId } }
                if (slot == null) {
                    unrostered += unrostered(crewChange, person, "no free slot takes a ${person.position.name}")
                    return@forEach
                }
                val clashes = assignmentService.clashesFor(person, crewChange.fromDate, crewChange.toDate)
                if (clashes.isNotEmpty()) {
                    unrostered += unrostered(crewChange, person, clashes.joinToString("; "))
                    return@forEach
                }
                assignmentService.assign(partnership.abbrev, crewChange.ccId, slot.ref, person.requiredId)
                free.remove(slot)
            }
        return unrostered
    }

    private fun unrostered(crewChange: CrewChange, person: Person, reason: String) =
        Unrostered(crewChange.ccId, person.requiredId, person.name, person.position.name, reason)

    private fun crewChange(abbrev: String, ccId: String): CrewChange =
        crewChanges.byBusinessKey(ccId, abbrev) ?: throw EntityNotFoundException("No swing $ccId on $abbrev")

    companion object {
        /** The portal's look-ahead: the swing on now and the three after it — what a renewal takes. */
        const val LOOKAHEAD = 3
        const val CUTOFF_DAYS = 7L
        val CREWS = setOf("A", "B")
        private val CC_NUMBER = Regex("^CC(\\d+)$")

        fun other(crew: String): String = if (crew == "A") "B" else "A"
    }
}
