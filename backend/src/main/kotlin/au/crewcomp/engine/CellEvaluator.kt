package au.crewcomp.engine

import java.time.LocalDate

/**
 * §5.1 — evaluation of one (person, requirement rule, swing) cell. **Normative.**
 *
 * The base classification is against the **whole swing window** (Q13), then the four precedence
 * rules of §5.1 are applied in order.
 */
object CellEvaluator {

    /**
     * Base classification (§5.1 table): holding × swing window → state, before any rule-level,
     * quota, tier or exemption handling.
     */
    fun baseState(holding: HoldingView?, swing: SwingWindow): CellState = when {
        holding == null -> CellState.UNKNOWN
        holding.status == HoldingStatus.UNKNOWN -> CellState.UNKNOWN
        holding.status == HoldingStatus.NOT_HELD -> CellState.GAP
        holding.status == HoldingStatus.HELD_PERPETUAL -> CellState.OK
        else -> {
            val expiry = requireNotNull(holding.expiry)
            when {
                !expiry.isBefore(swing.to) -> CellState.OK              // expiry >= swing.to
                !expiry.isBefore(swing.from) -> CellState.EXPIRING      // swing.from <= expiry < swing.to
                else -> CellState.GAP                                   // expiry < swing.from
            }
        }
    }

    /**
     * Evaluates one cell.
     *
     * @param registerRecord the register record for (person, requirement, this swing), if one exists.
     */
    fun evaluate(
        requirementId: RequirementId,
        level: RuleLevel,
        holding: HoldingView?,
        swing: SwingWindow,
        matrix: MatrixSnapshot,
        person: PersonView,
        partnership: PartnershipView,
        registerRecord: RegisterRecordView? = null,
    ): CellEvaluation {
        val base = baseState(holding, swing)
        val cell = CellEvaluation(
            requirementId = requirementId,
            level = level,
            state = base,
            expiry = holding?.expiry,
        )

        // 1. Recommended (`R`) is never a gap.
        if (level.isRecommended) {
            return if (base == CellState.OK) cell else cell.withState(CellState.RECOMMENDED)
        }

        // 2. Quota footnote levels: the cell is not individually mandatory. A gap becomes
        //    `quota_only` and is counted only at swing level (§5.3); `unknown` stays `unknown`
        //    and held stays `ok`.
        var state = base
        if (matrix.isQuotaFootnote(level) && state == CellState.GAP) {
            state = CellState.QUOTA_ONLY
        }

        // 3. Tier footnote (only where this matrix version defines one): a tier that conflicts
        //    with the partnership's vessel class goes to human review and is never auto-resolved
        //    (Q6). Precedence puts this ahead of the exemption overlay, so a tier conflict is
        //    surfaced even when a register record exists for the same cell.
        if (matrix.isTierFootnote(level)) {
            tierConflictNote(person, partnership, matrix)?.let { note ->
                return cell.withState(CellState.REVIEW, note)
            }
        }

        // 4. Exemption overlay.
        if (state in OVERLAY_STATES && registerRecord != null) {
            return when {
                registerRecord.grantsExemption -> cell.copy(
                    state = CellState.EXEMPT,
                    registerRecordId = registerRecord.recordId,
                    notes = cell.notes + exemptionNote(registerRecord),
                )
                registerRecord.open -> cell.copy(
                    state = CellState.PENDING,
                    registerRecordId = registerRecord.recordId,
                    notes = cell.notes + "Register record ${registerRecord.recordId} is open.",
                )
                else -> cell.withState(state)
            }
        }

        return cell.withState(state)
    }

    private val OVERLAY_STATES = setOf(CellState.GAP, CellState.UNKNOWN, CellState.EXPIRING)

    private fun exemptionNote(record: RegisterRecordView): String =
        "Exempt under ${record.recordId} (approved ${record.approvalFrom} to ${record.approvalTo})."

    /**
     * The tier review rule. Semantics are pending O-3, so the default is conservative: a
     * tier-footnote cell goes to review unless [MatrixSnapshot.tierPolicy] explicitly clears
     * the person's tier for the partnership's vessel class. The rule cannot fire at all where
     * the partnership has no vessel class — there is nothing for the tier to conflict with.
     *
     * @return an explanatory note when the cell must go to review, or `null` when it need not.
     */
    private fun tierConflictNote(
        person: PersonView,
        partnership: PartnershipView,
        matrix: MatrixSnapshot,
    ): String? {
        val vesselClass = partnership.vesselClass ?: return null
        val accepted = matrix.tierPolicy[vesselClass]

        if (accepted == null) {
            return "Tier review (${matrix.tierFootnote}): matrix version '${matrix.label}' defines no " +
                "accepted tiers for vessel class '$vesselClass' — needs human review (O-3)."
        }
        val tier = person.tier
            ?: return "Tier review (${matrix.tierFootnote}): ${person.name} has no recorded position tier, " +
                "and vessel class '$vesselClass' accepts ${accepted.sorted().joinToString(", ")}."

        if (tier in accepted) return null

        return "Tier review (${matrix.tierFootnote}): tier '$tier' is not accepted on vessel class " +
            "'$vesselClass' (accepted: ${accepted.sorted().joinToString(", ")})."
    }

    /** Whether the holding is valid through swing end — the test quota rules apply (§5.3). */
    fun validThroughSwing(holding: HoldingView?, swing: SwingWindow): Boolean = when {
        holding == null -> false
        holding.status == HoldingStatus.HELD_PERPETUAL -> true
        holding.status == HoldingStatus.HELD_EXPIRY -> !holding.expiry!!.isBefore(swing.to)
        else -> false
    }

    /** Days remaining until [expiry] from [asOf]; negative once expired. */
    fun daysUntil(expiry: LocalDate, asOf: LocalDate): Long =
        java.time.temporal.ChronoUnit.DAYS.between(asOf, expiry)
}
