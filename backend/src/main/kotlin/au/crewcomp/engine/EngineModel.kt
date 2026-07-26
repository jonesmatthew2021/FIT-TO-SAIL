package au.crewcomp.engine

import java.time.LocalDate

/**
 * Engine-facing value types — the inputs and outputs of spec §5.
 *
 * These are deliberately *not* the JPA entities. The engine is a pure function of its inputs so
 * that it can be unit-tested against the POC's validated behaviour without a database (DEV-2),
 * and so that later consumers (the API, the MCP tools, an AI swing-planning assistant per §17.1)
 * all call the same semantics rather than re-deriving them.
 *
 * Identifiers are surrogate keys (spec §4: business keys are never primary keys). Business keys
 * that users actually see — Sam #, requirement code, register record ID, CC ID — travel alongside
 * for display and export, never as the join key.
 */

@JvmInline value class PersonId(val value: Long)
@JvmInline value class PositionId(val value: Long)
@JvmInline value class RequirementId(val value: Long)
@JvmInline value class PartnershipId(val value: Long)
@JvmInline value class CrewChangeId(val value: Long)
@JvmInline value class AssignmentId(val value: Long)
@JvmInline value class MatrixVersionId(val value: Long)

// ---------------------------------------------------------------------------
// Reference / people inputs
// ---------------------------------------------------------------------------

data class PersonView(
    val id: PersonId,
    val sam: String,
    val name: String,
    val positionId: PositionId,
    /** Nullable — PositionTier applies only to some positions (§4.1, semantics pending O-3). */
    val tier: String?,
    val homePartnershipId: PartnershipId,
    val status: PersonStatus,
)

data class PartnershipView(
    val id: PartnershipId,
    val abbrev: String,
    /** Nullable; drives the tier review rule (§4.1). */
    val vesselClass: String?,
)

/** §4.3 QualificationHolding, reduced to what evaluation needs. */
data class HoldingView(
    val requirementId: RequirementId,
    val status: HoldingStatus,
    /** Required iff [status] is [HoldingStatus.HELD_EXPIRY]; a calendar date, never a timestamp (NFR-5). */
    val expiry: LocalDate?,
) {
    init {
        require(status != HoldingStatus.HELD_EXPIRY || expiry != null) {
            "held_expiry holding for requirement ${requirementId.value} must carry an expiry date"
        }
    }
}

/** §4.1 PositionSlot. */
data class SlotView(
    val ref: Int,
    val shift: Shift,
    /** Slots 15/16 accept AE **or** GPH, hence a set (§4.1). */
    val allowedPositionIds: Set<PositionId>,
    val notes: String? = null,
)

/** §4.3 Assignment. `from`/`to` may be a sub-range of the swing (mid-swing handovers). */
data class AssignmentView(
    val id: AssignmentId,
    val personId: PersonId,
    val crewChangeId: CrewChangeId,
    val partnershipId: PartnershipId,
    val slotRef: Int,
    val from: LocalDate,
    val to: LocalDate,
)

/** §4.1 CrewChange (Swing) — the evaluation window. */
data class SwingWindow(
    val crewChangeId: CrewChangeId,
    val ccId: String,
    val partnershipId: PartnershipId,
    val from: LocalDate,
    val to: LocalDate,
    /** Stored, not derived, so exceptions to the `from − 7 days` rule can exist (§4.1, Q17). */
    val cutoff: LocalDate,
) {
    init { require(!to.isBefore(from)) { "swing $ccId: to ($to) precedes from ($from)" } }

    fun covers(date: LocalDate): Boolean = !date.isBefore(from) && !date.isAfter(to)
}

// ---------------------------------------------------------------------------
// Rules inputs (§4.2)
// ---------------------------------------------------------------------------

data class RequirementRuleView(
    /** `null` = the base (`'*'`) rule that applies to every partnership. */
    val partnershipId: PartnershipId?,
    val positionId: PositionId,
    val requirementId: RequirementId,
    val level: RuleLevel,
)

sealed interface ConditionalRuleView {
    val positionId: PositionId
}

/** `one_of` — the person must hold at least one member of the set (§4.2). */
data class OneOfRuleView(
    override val positionId: PositionId,
    val requirementIds: List<RequirementId>,
    /** Display label, e.g. the CoC set's `M⁸`. Never interpreted (§4.2). */
    val label: String? = null,
) : ConditionalRuleView

/** `dependent` — required only when the person holds something in [requiredIfHolds] (§4.2). */
data class DependentRuleView(
    override val positionId: PositionId,
    val requirementId: RequirementId,
    val requiredIfHolds: List<RequirementId>,
    val unlessHolds: List<RequirementId> = emptyList(),
) : ConditionalRuleView

/** §4.2 QuotaRule, e.g. "≥4 crew with FRC per swing", "≥1 GPH with Work at Heights per shift". */
data class QuotaRuleView(
    /** Display label only — behaviour is keyed off this record, never off the text (§4.2). */
    val footnote: String,
    val requirementId: RequirementId,
    val min: Int,
    val scope: QuotaScope,
    /** `null` = any position counts. */
    val positionIds: Set<PositionId>? = null,
)

/**
 * A resolved, immutable snapshot of one MatrixVersion — everything §5 needs to evaluate against it.
 *
 * Evaluating a historical swing pins a historical version; the default is the current published
 * one (§5.5). Because a snapshot is a plain value, an evaluation is fully reproducible from it.
 */
data class MatrixSnapshot(
    val versionId: MatrixVersionId,
    val label: String,
    val rules: List<RequirementRuleView>,
    val conditionals: List<ConditionalRuleView> = emptyList(),
    val quotas: List<QuotaRuleView> = emptyList(),
    /**
     * The footnote label this version uses for the tier review rule (Q6/§5.1 step 3), or `null`
     * if this version defines none. Set per version, because the same label means different
     * things in different versions.
     */
    val tierFootnote: String? = null,
    /**
     * Vessel class → tiers accepted on that class, for the tier review rule. Semantics are
     * pending O-3, so the default is deliberately conservative: a tier-footnote cell is sent
     * to human review unless this mapping explicitly clears the person's tier
     * ("Mˣ → human review, never auto-resolved", Q6).
     */
    val tierPolicy: Map<String, Set<String>> = emptyMap(),
) {
    /** Footnote labels that this version treats as quota-only (§5.1 step 2). */
    val quotaFootnotes: Set<String> = quotas.map { it.footnote }.toSet()

    fun isQuotaFootnote(level: RuleLevel): Boolean =
        level.isFootnote && level.label in quotaFootnotes

    fun isTierFootnote(level: RuleLevel): Boolean =
        tierFootnote != null && level.isFootnote && level.label == tierFootnote
}

// ---------------------------------------------------------------------------
// Workflow input (§4.4) — the exemption overlay
// ---------------------------------------------------------------------------

/**
 * A register record as the engine sees it. Records are matched to a cell by
 * (person, requirement, swing) — see [EvaluationInputs.registerRecords].
 */
data class RegisterRecordView(
    val recordId: String,
    val requirementId: RequirementId?,
    val outcome: RegisterOutcome?,
    val approvalFrom: LocalDate?,
    val approvalTo: LocalDate?,
    /** True while the record sits in any `Open - *` status (§4.4). */
    val open: Boolean,
) {
    /** §5.1 step 4: "outcome Approved with an approval window → exempt". */
    val grantsExemption: Boolean
        get() = outcome == RegisterOutcome.APPROVED && approvalFrom != null && approvalTo != null
}

// ---------------------------------------------------------------------------
// Outputs
// ---------------------------------------------------------------------------

data class CellEvaluation(
    val requirementId: RequirementId,
    val level: RuleLevel,
    val state: CellState,
    val expiry: LocalDate? = null,
    /** Human-readable explanations — shown in the UI and consumed by AI assists (§17.1). */
    val notes: List<String> = emptyList(),
    /** The register record that produced an `exempt`/`pending` state, if any. */
    val registerRecordId: String? = null,
) {
    fun withState(state: CellState, note: String? = null): CellEvaluation =
        copy(state = state, notes = if (note == null) notes else notes + note)
}

data class PersonEvaluation(
    val personId: PersonId,
    val cells: List<CellEvaluation>,
) {
    /**
     * §5.2 per-person roll-up: the worst cell state by [rollUpSeverity].
     *
     * A person with no cells, or whose every cell is `na` (nothing in the matrix applies to
     * them), rolls up to `ok` — "not applicable" is not a person-level status.
     */
    val rollUp: CellState =
        cells.maxByOrNull { it.state.rollUpSeverity }
            ?.state
            ?.takeUnless { it == CellState.NA }
            ?: CellState.OK

    fun cell(requirementId: RequirementId): CellEvaluation? =
        cells.firstOrNull { it.requirementId == requirementId }
}
