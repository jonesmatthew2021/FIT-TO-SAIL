package au.crewcomp.engine

/**
 * §5.3 — whole-swing evaluation for one (crew change, partnership). **Normative.**
 */
object SwingEvaluator {

    fun evaluate(inputs: SwingEvaluationInputs): SwingEvaluation {
        val swing = inputs.swing

        // Slots may hold sequential assignments (mid-swing handovers) — evaluate each.
        val assignmentEvaluations = inputs.assignments
            .sortedWith(compareBy({ it.slotRef }, { it.from }))
            .mapNotNull { assignment ->
                val person = inputs.people[assignment.personId] ?: return@mapNotNull null
                AssignmentEvaluation(
                    assignment = assignment,
                    person = person,
                    evaluation = PersonEvaluator.evaluate(
                        person = person,
                        partnership = inputs.partnership,
                        swing = swing,
                        matrix = inputs.matrix,
                        holdings = inputs.holdings[person.id].orEmpty(),
                        registerRecords = inputs.registerRecords[person.id].orEmpty(),
                    ),
                )
            }

        val assignedSlotRefs = inputs.assignments.map { it.slotRef }.toSet()
        val openSlots = inputs.slots.filter { it.ref !in assignedSlotRefs }

        // Slots that are assigned but not for the whole swing. Not an "open slot" in the POC's
        // sense, but it is the input the per-day quota evaluation of ENG-1 (P4) will need, and
        // it is a real planning signal today.
        val partiallyCoveredSlots = inputs.slots
            .filter { it.ref in assignedSlotRefs }
            .filterNot { slot -> coversWholeSwing(inputs.assignments.filter { it.slotRef == slot.ref }, swing) }

        val stateCounts = assignmentEvaluations
            .flatMap { it.evaluation.cells }
            .groupingBy { it.state }
            .eachCount()

        return SwingEvaluation(
            swing = swing,
            assignments = assignmentEvaluations,
            openSlots = openSlots,
            partiallyCoveredSlots = partiallyCoveredSlots,
            stateCounts = stateCounts,
            quotas = evaluateQuotas(inputs),
        )
    }

    /**
     * §5.3 quota evaluation. Scope `swing` counts assigned people whose holding is valid through
     * swing end; scope `shift` evaluates per shift, with `N/A`-shift slots counting toward both.
     * Shortfalls are warnings, surfaced per shift (Q8).
     */
    fun evaluateQuotas(inputs: SwingEvaluationInputs): List<QuotaEvaluation> {
        val slotsByRef = inputs.slots.associateBy { it.ref }

        return inputs.matrix.quotas.flatMap { rule ->
            when (rule.scope) {
                QuotaScope.SWING -> listOf(
                    countAgainst(rule, inputs, shift = null, assignments = inputs.assignments),
                )

                QuotaScope.SHIFT -> Shift.QUOTA_SHIFTS.map { shift ->
                    val forShift = inputs.assignments.filter { assignment ->
                        val slotShift = slotsByRef[assignment.slotRef]?.shift ?: Shift.NOT_APPLICABLE
                        shift in slotShift.contributesTo()
                    }
                    countAgainst(rule, inputs, shift = shift, assignments = forShift)
                }
            }
        }
    }

    private fun countAgainst(
        rule: QuotaRuleView,
        inputs: SwingEvaluationInputs,
        shift: Shift?,
        assignments: List<AssignmentView>,
    ): QuotaEvaluation {
        val contributing = assignments
            .map { it.personId }
            .distinct()
            .mapNotNull { inputs.people[it] }
            .filter { person -> rule.positionIds == null || person.positionId in rule.positionIds }
            .filter { person ->
                CellEvaluator.validThroughSwing(
                    inputs.holdings[person.id]?.get(rule.requirementId),
                    inputs.swing,
                )
            }
            .map { it.id }

        return QuotaEvaluation(
            footnote = rule.footnote,
            requirementId = rule.requirementId,
            scope = rule.scope,
            shift = shift,
            min = rule.min,
            actual = contributing.size,
            contributingPersonIds = contributing,
        )
    }

    private fun coversWholeSwing(assignments: List<AssignmentView>, swing: SwingWindow): Boolean {
        if (assignments.isEmpty()) return false
        val ordered = assignments.sortedBy { it.from }
        if (ordered.first().from.isAfter(swing.from)) return false

        var covered = ordered.first().to
        for (assignment in ordered.drop(1)) {
            // A gap of more than one day between consecutive assignments leaves the slot uncovered.
            if (assignment.from.isAfter(covered.plusDays(1))) return false
            if (assignment.to.isAfter(covered)) covered = assignment.to
        }
        return !covered.isBefore(swing.to)
    }
}

// ---------------------------------------------------------------------------
// Inputs and outputs
// ---------------------------------------------------------------------------

/**
 * Everything §5.3 needs for one swing. Assembled by the service layer from the repositories;
 * the engine itself performs no I/O.
 */
data class SwingEvaluationInputs(
    val swing: SwingWindow,
    val partnership: PartnershipView,
    val matrix: MatrixSnapshot,
    val slots: List<SlotView>,
    val assignments: List<AssignmentView>,
    val people: Map<PersonId, PersonView>,
    val holdings: Map<PersonId, Map<RequirementId, HoldingView>>,
    val registerRecords: Map<PersonId, Map<RequirementId, RegisterRecordView>> = emptyMap(),
)

data class AssignmentEvaluation(
    val assignment: AssignmentView,
    val person: PersonView,
    val evaluation: PersonEvaluation,
)

data class QuotaEvaluation(
    val footnote: String,
    val requirementId: RequirementId,
    val scope: QuotaScope,
    /** `null` for swing-scoped rules. */
    val shift: Shift?,
    val min: Int,
    val actual: Int,
    val contributingPersonIds: List<PersonId> = emptyList(),
) {
    val satisfied: Boolean get() = actual >= min
    val shortfall: Int get() = (min - actual).coerceAtLeast(0)
}

data class SwingEvaluation(
    val swing: SwingWindow,
    val assignments: List<AssignmentEvaluation>,
    val openSlots: List<SlotView>,
    val partiallyCoveredSlots: List<SlotView>,
    val stateCounts: Map<CellState, Int>,
    val quotas: List<QuotaEvaluation>,
) {
    /** Quota shortfalls are warnings, not blocks (Q8). */
    val quotaShortfalls: List<QuotaEvaluation> get() = quotas.filterNot { it.satisfied }

    fun countOf(state: CellState): Int = stateCounts[state] ?: 0
}
