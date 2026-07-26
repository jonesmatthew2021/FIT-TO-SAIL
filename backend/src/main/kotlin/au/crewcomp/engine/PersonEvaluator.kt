package au.crewcomp.engine

/**
 * §5.2 — person-level evaluation: every effective rule for the person's position, then the
 * conditional-rule post-passes. **Normative.**
 */
object PersonEvaluator {

    /**
     * Evaluates every requirement that applies to [person] for [swing].
     *
     * @param holdings the person's holdings, keyed by requirement.
     * @param registerRecords the person's register records for this swing, keyed by requirement.
     */
    fun evaluate(
        person: PersonView,
        partnership: PartnershipView,
        swing: SwingWindow,
        matrix: MatrixSnapshot,
        holdings: Map<RequirementId, HoldingView>,
        registerRecords: Map<RequirementId, RegisterRecordView> = emptyMap(),
    ): PersonEvaluation {
        val effective = matrix.effectiveRulesForPosition(swing.partnershipId, person.positionId)

        val cells = effective.map { (requirementId, level) ->
            CellEvaluator.evaluate(
                requirementId = requirementId,
                level = level,
                holding = holdings[requirementId],
                swing = swing,
                matrix = matrix,
                person = person,
                partnership = partnership,
                registerRecord = registerRecords[requirementId],
            )
        }

        val afterOneOf = applyOneOf(cells, matrix.conditionalsForPosition(person.positionId), holdings)
        val afterDependent = applyDependent(afterOneOf, matrix.conditionalsForPosition(person.positionId), holdings)

        return PersonEvaluation(person.id, afterDependent)
    }

    /**
     * `one_of` post-pass (§5.2): if any member of the set is `ok`, the other members become `na`;
     * if none is satisfied, members remain gaps — softened to `unknown` if any member is unknown,
     * because the gap may not be real.
     */
    internal fun applyOneOf(
        cells: List<CellEvaluation>,
        conditionals: List<ConditionalRuleView>,
        holdings: Map<RequirementId, HoldingView>,
    ): List<CellEvaluation> {
        var result = cells

        for (rule in conditionals.filterIsInstance<OneOfRuleView>()) {
            val members = rule.requirementIds.toSet()
            val memberCells = result.filter { it.requirementId in members }
            if (memberCells.isEmpty()) continue

            val satisfiedBy = memberCells.firstOrNull { it.state == CellState.OK }
            val label = rule.label?.let { " ($it)" } ?: ""

            result = if (satisfiedBy != null) {
                result.map { cell ->
                    when {
                        cell.requirementId !in members -> cell
                        cell.requirementId == satisfiedBy.requirementId -> cell
                        else -> cell.withState(
                            CellState.NA,
                            "Satisfied by another member of the one-of set$label.",
                        )
                    }
                }
            } else {
                // Not satisfied. The set's gaps stand — unless any member is unknown, in which
                // case the person may in fact hold one and the whole set softens to unknown.
                val anyUnknown = memberCells.any { it.state == CellState.UNKNOWN } ||
                    members.any { holdings[it]?.status == HoldingStatus.UNKNOWN }

                result.map { cell ->
                    when {
                        cell.requirementId !in members -> cell
                        anyUnknown && cell.state == CellState.GAP -> cell.withState(
                            CellState.UNKNOWN,
                            "One-of set$label unsatisfied, but a member holding is unknown — the gap may not be real.",
                        )
                        cell.state == CellState.GAP -> cell.withState(
                            CellState.GAP,
                            "No member of the one-of set$label is held.",
                        )
                        else -> cell
                    }
                }
            }
        }

        return result
    }

    /**
     * `dependent` post-pass (§5.2): if the trigger condition does not hold — the person holds none
     * of `required_if_holds`, or holds something in `unless_holds` — the target becomes `na`.
     * If triggered and missing, it stays a gap with an explanatory note.
     */
    internal fun applyDependent(
        cells: List<CellEvaluation>,
        conditionals: List<ConditionalRuleView>,
        holdings: Map<RequirementId, HoldingView>,
    ): List<CellEvaluation> {
        var result = cells

        for (rule in conditionals.filterIsInstance<DependentRuleView>()) {
            val holdsTrigger = rule.requiredIfHolds.any { holdings[it]?.status?.isHeld == true }
            val holdsExclusion = rule.unlessHolds.any { holdings[it]?.status?.isHeld == true }
            val triggered = holdsTrigger && !holdsExclusion

            result = result.map { cell ->
                if (cell.requirementId != rule.requirementId) {
                    cell
                } else if (!triggered) {
                    cell.withState(CellState.NA, "Not required: the dependency condition does not apply.")
                } else if (cell.state == CellState.GAP) {
                    cell.withState(CellState.GAP, "Required because of a dependent qualification held.")
                } else {
                    cell
                }
            }
        }

        return result
    }
}
