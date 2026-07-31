package au.crewcomp.engine

import java.time.LocalDate

/**
 * §5.4 — crew suggestions, gap report and expiry alerts. **Normative.**
 *
 * Each of these is a clean function of engine inputs rather than view-bound logic, because
 * §17.1 makes an AI assistant just another consumer of them ("bites now" for P1).
 */
object Planning {

    // -----------------------------------------------------------------------
    // Crew suggestions
    // -----------------------------------------------------------------------

    /**
     * Ranked suggestions for an open slot: active people whose position fits the slot's allowed
     * positions and who are not already assigned to this swing, ranked **ascending by penalty
     * score**. Reference weights come from the POC; they are configuration ([SuggestionWeights]).
     *
     * A clash — an overlapping assignment elsewhere, or standing leave — is scored, not hidden
     * (POC behaviour). Leave is the same two-tier reading as [au.crewcomp.courses.CourseOffers]:
     * the person *could* take the slot (leave gets cancelled), so it ranks them last-ish and says
     * why, where hiding them would make the write path's 409 unreachable knowledge.
     */
    fun suggestCrew(
        slot: SlotView,
        inputs: SwingEvaluationInputs,
        candidates: Collection<PersonView>,
        /** Assignments outside this swing, used to detect overlaps. */
        otherAssignments: Collection<AssignmentView> = emptyList(),
        /** Standing leave only — the caller applies the status filter. */
        leave: Collection<LeaveView> = emptyList(),
        weights: SuggestionWeights = SuggestionWeights(),
        limit: Int = 20,
    ): List<Suggestion> {
        val alreadyAssigned = inputs.assignments.map { it.personId }.toSet()

        return candidates
            .asSequence()
            .filter { it.status == PersonStatus.ACTIVE }
            .filter { it.positionId in slot.allowedPositionIds }
            .filterNot { it.id in alreadyAssigned }
            .map { person -> score(person, inputs, otherAssignments, leave, weights) }
            .sortedWith(compareBy({ it.score }, { it.person.name }))
            .take(limit)
            .toList()
    }

    private fun score(
        person: PersonView,
        inputs: SwingEvaluationInputs,
        otherAssignments: Collection<AssignmentView>,
        leave: Collection<LeaveView>,
        weights: SuggestionWeights,
    ): Suggestion {
        val evaluation = PersonEvaluator.evaluate(
            person = person,
            partnership = inputs.partnership,
            swing = inputs.swing,
            matrix = inputs.matrix,
            holdings = inputs.holdings[person.id].orEmpty(),
            registerRecords = inputs.registerRecords[person.id].orEmpty(),
        )

        val gaps = evaluation.cells.count { it.state == CellState.GAP }
        val unknowns = evaluation.cells.count { it.state == CellState.UNKNOWN }
        val expiring = evaluation.cells.count { it.state == CellState.EXPIRING }
        val crossPartnership = person.homePartnershipId != inputs.swing.partnershipId
        val clashes = otherAssignments.filter {
            it.personId == person.id &&
                it.crewChangeId != inputs.swing.crewChangeId &&
                overlaps(it.from, it.to, inputs.swing.from, inputs.swing.to)
        }
        val leaveClashes = leave.filter {
            it.personId == person.id &&
                overlaps(it.from, it.to, inputs.swing.from, inputs.swing.to)
        }

        val reasons = buildList {
            if (gaps > 0) add("$gaps gap${plural(gaps)}")
            if (unknowns > 0) add("$unknowns unknown holding${plural(unknowns)}")
            if (expiring > 0) add("$expiring expiring mid-swing")
            if (crossPartnership) add("cross-partnership")
            if (clashes.isNotEmpty()) add("clash: already assigned elsewhere in this window")
            leaveClashes.forEach { add("on ${it.kind.replace('_', ' ')} ${it.from}..${it.to}") }
        }

        val score = gaps * weights.gap +
            unknowns * weights.unknown +
            expiring * weights.expiring +
            (if (crossPartnership) weights.crossPartnership else 0) +
            clashes.size * weights.overlappingAssignment +
            leaveClashes.size * weights.onLeave

        return Suggestion(
            person = person,
            score = score,
            gapCount = gaps,
            unknownCount = unknowns,
            expiringCount = expiring,
            crossPartnership = crossPartnership,
            clashingAssignmentIds = clashes.map { it.id },
            leaveClashes = leaveClashes.map { "${it.kind.replace('_', ' ')} ${it.from}..${it.to}" },
            reasons = reasons,
            evaluation = evaluation,
        )
    }

    // -----------------------------------------------------------------------
    // Gap report
    // -----------------------------------------------------------------------

    /**
     * Every non-`ok`/`na` cell in the swing, ordered
     * `gap → expiring → unknown → review → pending → exempt` (§5.4). Each row carries enough
     * context to pre-fill a new exemption request in one click (POC behaviour, kept).
     */
    fun gapReport(evaluation: SwingEvaluation): List<GapReportRow> =
        evaluation.assignments
            .flatMap { assignmentEvaluation ->
                assignmentEvaluation.evaluation.cells
                    .filterNot { it.state.isClear }
                    .map { cell ->
                        GapReportRow(
                            person = assignmentEvaluation.person,
                            assignmentId = assignmentEvaluation.assignment.id,
                            slotRef = assignmentEvaluation.assignment.slotRef,
                            requirementId = cell.requirementId,
                            level = cell.level,
                            state = cell.state,
                            expiry = cell.expiry,
                            registerRecordId = cell.registerRecordId,
                            notes = cell.notes,
                        )
                    }
            }
            .sortedWith(
                compareBy(
                    { it.state.gapReportOrder },
                    { it.person.name },
                    { it.requirementId.value },
                ),
            )

    // -----------------------------------------------------------------------
    // Expiry alerts
    // -----------------------------------------------------------------------

    /**
     * Holdings with `held_expiry` falling within [leadDays] of [asOf], for active people,
     * classified by impact on the person's next assignment (§5.4). Feeds notifications (§9)
     * and the mobile app (MOB-1).
     *
     * Already-expired holdings are included: an expiry in the past is the most urgent case,
     * not an absent one.
     */
    fun expiryAlerts(
        people: Collection<PersonView>,
        holdings: Map<PersonId, Map<RequirementId, HoldingView>>,
        assignments: Collection<AssignmentView>,
        asOf: LocalDate,
        leadDays: Long = DEFAULT_EXPIRY_LEAD_DAYS,
    ): List<ExpiryAlert> {
        val horizon = asOf.plusDays(leadDays)
        val nextAssignments = assignments
            .filterNot { it.to.isBefore(asOf) }
            .groupBy { it.personId }
            .mapValues { (_, list) -> list.minByOrNull { it.from }!! }

        return people
            .filter { it.status == PersonStatus.ACTIVE }
            .flatMap { person ->
                holdings[person.id].orEmpty().values
                    .filter { it.status == HoldingStatus.HELD_EXPIRY }
                    .filter { !it.expiry!!.isAfter(horizon) }
                    .map { holding ->
                        val next = nextAssignments[person.id]
                        ExpiryAlert(
                            person = person,
                            requirementId = holding.requirementId,
                            expiry = holding.expiry!!,
                            daysRemaining = CellEvaluator.daysUntil(holding.expiry, asOf),
                            impact = classifyImpact(holding.expiry, next),
                            nextAssignmentId = next?.id,
                        )
                    }
            }
            .sortedWith(compareBy({ it.expiry }, { it.person.name }))
    }

    private fun classifyImpact(expiry: LocalDate, next: AssignmentView?): ExpiryImpact = when {
        next == null -> ExpiryImpact.NONE
        expiry.isBefore(next.from) -> ExpiryImpact.EXPIRED_BEFORE_SWING
        expiry.isBefore(next.to) -> ExpiryImpact.MID_SWING
        else -> ExpiryImpact.NONE
    }

    const val DEFAULT_EXPIRY_LEAD_DAYS: Long = 90

    private fun overlaps(aFrom: LocalDate, aTo: LocalDate, bFrom: LocalDate, bTo: LocalDate): Boolean =
        !aFrom.isAfter(bTo) && !bFrom.isAfter(aTo)

    private fun plural(n: Int) = if (n == 1) "" else "s"
}

/**
 * Suggestion penalty weights (§5.4). POC reference values; configurable via ADM-10.
 * An overlapping assignment is weighted so heavily that a clashing candidate always sorts last
 * while still being shown. Standing leave weighs slightly less than a hard clash: leave can be
 * cancelled by agreement, another assignment cannot, so of two clashing candidates the one on
 * leave is the better ask.
 */
data class SuggestionWeights(
    val gap: Int = 100,
    val unknown: Int = 10,
    val expiring: Int = 5,
    val crossPartnership: Int = 3,
    val overlappingAssignment: Int = 1000,
    val onLeave: Int = 800,
)

data class Suggestion(
    val person: PersonView,
    val score: Int,
    val gapCount: Int,
    val unknownCount: Int,
    val expiringCount: Int,
    val crossPartnership: Boolean,
    val clashingAssignmentIds: List<AssignmentId>,
    /** Human-readable standing-leave overlaps ("annual leave 2026-08-03..2026-08-10"). */
    val leaveClashes: List<String> = emptyList(),
    val reasons: List<String>,
    val evaluation: PersonEvaluation,
) {
    val hasClash: Boolean get() = clashingAssignmentIds.isNotEmpty()
    val onLeave: Boolean get() = leaveClashes.isNotEmpty()
}

data class GapReportRow(
    val person: PersonView,
    val assignmentId: AssignmentId,
    val slotRef: Int,
    val requirementId: RequirementId,
    val level: RuleLevel,
    val state: CellState,
    val expiry: LocalDate?,
    val registerRecordId: String?,
    val notes: List<String>,
)

data class ExpiryAlert(
    val person: PersonView,
    val requirementId: RequirementId,
    val expiry: LocalDate,
    val daysRemaining: Long,
    val impact: ExpiryImpact,
    val nextAssignmentId: AssignmentId?,
)
