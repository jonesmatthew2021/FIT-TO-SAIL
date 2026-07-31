package au.crewcomp.api

import au.crewcomp.engine.CellEvaluation
import au.crewcomp.engine.ExpiryAlert
import au.crewcomp.engine.GapReportRow
import au.crewcomp.engine.PersonEvaluation
import au.crewcomp.engine.QuotaEvaluation
import au.crewcomp.engine.Suggestion
import au.crewcomp.engine.SwingEvaluation
import java.time.LocalDate

/**
 * API representations.
 *
 * Separate from the engine's value types on purpose: the engine's types are shaped for
 * evaluation (inline value classes, ids everywhere), whereas the wire wants stable field names,
 * Appendix A wire strings, and enough denormalised labelling that a client can render a row
 * without a second call. The OpenAPI schema generated from these is what produces the admin
 * SPA's and the mobile app's types (DEV-2), so their shape is a compatibility surface.
 */

data class CellDto(
    val requirementId: Long,
    val level: String,
    val state: String,
    val expiry: LocalDate?,
    val notes: List<String>,
    val registerRecordId: String?,
)

data class PersonEvaluationDto(
    val personId: Long,
    val rollUp: String,
    val cells: List<CellDto>,
)

data class AssignmentEvaluationDto(
    val assignmentId: Long,
    val personId: Long,
    val sam: String,
    val name: String,
    val slotRef: Int,
    val from: LocalDate,
    val to: LocalDate,
    val evaluation: PersonEvaluationDto,
)

data class OpenSlotDto(val ref: Int, val shift: String, val allowedPositionIds: List<Long>)

data class QuotaDto(
    val footnote: String,
    val requirementId: Long,
    val scope: String,
    val shift: String?,
    val min: Int,
    val actual: Int,
    val satisfied: Boolean,
    val shortfall: Int,
)

data class SwingEvaluationDto(
    val ccId: String,
    val partnershipId: Long,
    val from: LocalDate,
    val to: LocalDate,
    val cutoff: LocalDate,
    val assignments: List<AssignmentEvaluationDto>,
    val openSlots: List<OpenSlotDto>,
    val partiallyCoveredSlots: List<OpenSlotDto>,
    /** Cell counts keyed by Appendix A cell state. */
    val stateCounts: Map<String, Int>,
    val quotas: List<QuotaDto>,
)

data class GapReportRowDto(
    val personId: Long,
    val sam: String,
    val name: String,
    val slotRef: Int,
    val assignmentId: Long,
    val requirementId: Long,
    val level: String,
    val state: String,
    val expiry: LocalDate?,
    val registerRecordId: String?,
    val notes: List<String>,
)

data class SuggestionDto(
    val personId: Long,
    val sam: String,
    val name: String,
    val score: Int,
    val gapCount: Int,
    val unknownCount: Int,
    val expiringCount: Int,
    val crossPartnership: Boolean,
    /** Shown, never hidden (§5.4). */
    val clash: Boolean,
    /** Standing leave overlapping the swing — scored and labelled, same rule as [clash]. */
    val onLeave: Boolean,
    val reasons: List<String>,
)

data class ExpiryAlertDto(
    val personId: Long,
    val sam: String,
    val name: String,
    val requirementId: Long,
    val expiry: LocalDate,
    val daysRemaining: Long,
    val impact: String,
)

data class SetHoldingRequest(
    val status: String,
    val expiry: LocalDate? = null,
    val issueDate: LocalDate? = null,
    val note: String? = null,
)

data class HoldingDto(
    val id: Long,
    val personId: Long,
    val requirementId: Long,
    val status: String,
    val expiry: LocalDate?,
    val issueDate: LocalDate?,
    val note: String?,
)

data class ErrorDto(val error: String, val detail: String? = null)

// ---------------------------------------------------------------------------
// Mapping
// ---------------------------------------------------------------------------

fun CellEvaluation.toDto() = CellDto(
    requirementId = requirementId.value,
    level = level.label,
    state = state.wire,
    expiry = expiry,
    notes = notes,
    registerRecordId = registerRecordId,
)

fun PersonEvaluation.toDto() = PersonEvaluationDto(
    personId = personId.value,
    rollUp = rollUp.wire,
    cells = cells.map { it.toDto() },
)

fun QuotaEvaluation.toDto() = QuotaDto(
    footnote = footnote,
    requirementId = requirementId.value,
    scope = scope.wire,
    shift = shift?.wire,
    min = min,
    actual = actual,
    satisfied = satisfied,
    shortfall = shortfall,
)

fun SwingEvaluation.toDto() = SwingEvaluationDto(
    ccId = swing.ccId,
    partnershipId = swing.partnershipId.value,
    from = swing.from,
    to = swing.to,
    cutoff = swing.cutoff,
    assignments = assignments.map { evaluation ->
        AssignmentEvaluationDto(
            assignmentId = evaluation.assignment.id.value,
            personId = evaluation.person.id.value,
            sam = evaluation.person.sam,
            name = evaluation.person.name,
            slotRef = evaluation.assignment.slotRef,
            from = evaluation.assignment.from,
            to = evaluation.assignment.to,
            evaluation = evaluation.evaluation.toDto(),
        )
    },
    openSlots = openSlots.map { slot ->
        OpenSlotDto(slot.ref, slot.shift.wire, slot.allowedPositionIds.map { it.value })
    },
    partiallyCoveredSlots = partiallyCoveredSlots.map { slot ->
        OpenSlotDto(slot.ref, slot.shift.wire, slot.allowedPositionIds.map { it.value })
    },
    stateCounts = stateCounts.entries.associate { (state, count) -> state.wire to count },
    quotas = quotas.map { it.toDto() },
)

fun GapReportRow.toDto() = GapReportRowDto(
    personId = person.id.value,
    sam = person.sam,
    name = person.name,
    slotRef = slotRef,
    assignmentId = assignmentId.value,
    requirementId = requirementId.value,
    level = level.label,
    state = state.wire,
    expiry = expiry,
    registerRecordId = registerRecordId,
    notes = notes,
)

fun Suggestion.toDto() = SuggestionDto(
    personId = person.id.value,
    sam = person.sam,
    name = person.name,
    score = score,
    gapCount = gapCount,
    unknownCount = unknownCount,
    expiringCount = expiringCount,
    crossPartnership = crossPartnership,
    clash = hasClash,
    onLeave = onLeave,
    reasons = reasons,
)

fun ExpiryAlert.toDto() = ExpiryAlertDto(
    personId = person.id.value,
    sam = person.sam,
    name = person.name,
    requirementId = requirementId.value,
    expiry = expiry,
    daysRemaining = daysRemaining,
    impact = impact.wire,
)
