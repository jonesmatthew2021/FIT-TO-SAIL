package au.crewcomp.engine

/**
 * Appendix A enumerations (normative reference) and the ordering rules of spec §5.
 *
 * This file is part of the pure-domain engine: no CDI, no JPA, no framework types.
 * The persistence and API layers map onto these; the engine never depends on them.
 */

/** Appendix A — cell states. */
enum class CellState(val wire: String) {
    OK("ok"),
    EXPIRING("expiring"),
    GAP("gap"),
    EXEMPT("exempt"),
    PENDING("pending"),
    UNKNOWN("unknown"),
    REVIEW("review"),
    QUOTA_ONLY("quota_only"),
    NA("na"),
    RECOMMENDED("recommended");

    companion object {
        fun fromWire(wire: String): CellState =
            entries.firstOrNull { it.wire == wire }
                ?: throw IllegalArgumentException("Unknown cell state: $wire")
    }
}

/**
 * Per-person roll-up severity (§5.2): `gap > expiring > pending > unknown > review > exempt > ok`.
 *
 * The three states absent from the spec's ordering are informational rather than
 * person-level problems, so they rank below `ok`:
 *  - `quota_only` is evaluated at swing level, never against the individual;
 *  - `recommended` is by definition never a gap (§5.1 rule 1);
 *  - `na` means the requirement does not apply to this person at all.
 */
val CellState.rollUpSeverity: Int
    get() = when (this) {
        CellState.GAP -> 70
        CellState.EXPIRING -> 60
        CellState.PENDING -> 50
        CellState.UNKNOWN -> 40
        CellState.REVIEW -> 30
        CellState.EXEMPT -> 20
        CellState.OK -> 10
        CellState.QUOTA_ONLY -> 5
        CellState.RECOMMENDED -> 4
        CellState.NA -> 0
    }

/**
 * Gap-report ordering (§5.4): `gap → expiring → unknown → review → pending → exempt`.
 *
 * Note this is deliberately *not* [rollUpSeverity] — the spec swaps `pending` and `unknown`
 * between the two orderings (a pending request is a worse personal state than an unknown
 * holding, but is further down the worklist because someone is already actioning it).
 *
 * `quota_only` and `recommended` sort last: the gap report shows "every non-`ok`/`na` cell",
 * so they are surfaced rather than hidden, but they never pre-fill an exemption request.
 */
val CellState.gapReportOrder: Int
    get() = when (this) {
        CellState.GAP -> 0
        CellState.EXPIRING -> 1
        CellState.UNKNOWN -> 2
        CellState.REVIEW -> 3
        CellState.PENDING -> 4
        CellState.EXEMPT -> 5
        CellState.QUOTA_ONLY -> 6
        CellState.RECOMMENDED -> 7
        CellState.OK -> 8
        CellState.NA -> 9
    }

/** True for the states the gap report and worklists exclude (§5.4). */
val CellState.isClear: Boolean
    get() = this == CellState.OK || this == CellState.NA

/** Appendix A — holding status. */
enum class HoldingStatus(val wire: String) {
    HELD_EXPIRY("held_expiry"),
    HELD_PERPETUAL("held_perpetual"),
    NOT_HELD("not_held"),
    UNKNOWN("unknown");

    /** Whether the person possesses the qualification at all, ignoring dates. */
    val isHeld: Boolean get() = this == HELD_EXPIRY || this == HELD_PERPETUAL

    companion object {
        fun fromWire(wire: String): HoldingStatus =
            entries.firstOrNull { it.wire == wire }
                ?: throw IllegalArgumentException("Unknown holding status: $wire")
    }
}

/** Appendix A — expiry impact classification (§5.4). */
enum class ExpiryImpact(val wire: String) {
    EXPIRED_BEFORE_SWING("expired_before_swing"),
    MID_SWING("mid_swing"),
    NONE("none");
}

/** Appendix A — register outcomes (§4.4). */
enum class RegisterOutcome(val wire: String) {
    APPROVED("Approved"),
    NOT_APPROVED("Not Approved"),
    INFO_REQUIRED("Info Required"),
    ADMIN_ACTION("Admin Action"),
    NOT_REQUIRED("Not Required");

    companion object {
        fun fromWire(wire: String): RegisterOutcome =
            entries.firstOrNull { it.wire == wire }
                ?: throw IllegalArgumentException("Unknown register outcome: $wire")
    }
}

/** §4.1 PositionSlot shift. `N/A` means aboard the whole swing and counts toward *both* shifts. */
enum class Shift(val wire: String) {
    SHIFT_1("Shift 1"),
    SHIFT_2("Shift 2"),
    NOT_APPLICABLE("N/A");

    /** The shifts this slot contributes to when evaluating shift-scoped quotas (§5.3). */
    fun contributesTo(): Set<Shift> = when (this) {
        NOT_APPLICABLE -> setOf(SHIFT_1, SHIFT_2)
        else -> setOf(this)
    }

    companion object {
        val QUOTA_SHIFTS = listOf(SHIFT_1, SHIFT_2)

        fun fromWire(wire: String): Shift =
            entries.firstOrNull { it.wire == wire }
                ?: throw IllegalArgumentException("Unknown shift: $wire")
    }
}

/** §4.2 QuotaRule scope. */
enum class QuotaScope(val wire: String) {
    SWING("swing"),
    SHIFT("shift");
}

/** §4.3 Person status. */
enum class PersonStatus(val wire: String) {
    ACTIVE("active"),
    LOOKUP_ONLY("lookup_only"),
    INACTIVE("inactive");
}

/**
 * §4.2 RequirementRule level: `M`, a footnote label (`Mn`), `R`, or empty.
 *
 * Footnote labels are **data, not code** (§4.2): the same label means different things in
 * different matrix versions, so nothing here interprets the text. A level is a footnote iff
 * it is neither plain `M`, plain `R`, nor blank; *which* footnote behaviour applies is
 * decided by looking the label up in the matrix version's [MatrixSnapshot.quotaFootnotes]
 * and [MatrixSnapshot.tierFootnote] records.
 */
@JvmInline
value class RuleLevel(val raw: String) {
    val label: String get() = raw.trim()
    val isBlank: Boolean get() = label.isEmpty()
    val isRecommended: Boolean get() = label.equals("R", ignoreCase = true)
    val isPlainMandatory: Boolean get() = label.equals("M", ignoreCase = true)
    val isFootnote: Boolean get() = !isBlank && !isRecommended && !isPlainMandatory

    /** Everything that is not `R` and not blank imposes some form of obligation. */
    val isMandatoryish: Boolean get() = !isBlank && !isRecommended

    override fun toString(): String = label

    companion object {
        val MANDATORY = RuleLevel("M")
        val RECOMMENDED = RuleLevel("R")
        val NONE = RuleLevel("")
    }
}
