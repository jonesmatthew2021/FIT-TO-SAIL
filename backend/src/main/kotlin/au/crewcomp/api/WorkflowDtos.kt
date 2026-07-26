package au.crewcomp.api

import au.crewcomp.people.QualificationHolding
import au.crewcomp.workflow.ExceptionItem
import au.crewcomp.workflow.RegisterRecord
import java.time.Instant
import java.time.LocalDate

/**
 * §4.4 workflow-layer representations: the ADM-7 data-quality worklist.
 *
 * Same rules as the other DTO files — enough denormalised labelling that a row renders without a
 * second call, and no entity ever reaches the wire.
 */

data class ExceptionItemDto(
    val id: Long,
    val area: String,
    val description: String,
    val state: String,
    /**
     * What the item is about, when it is about something in particular — `Person`, `Requirement`,
     * `RegisterRecord`. The screen turns the pair into a link; an item with neither is a general
     * observation, which is legitimate.
     */
    val linkedEntityType: String?,
    val linkedEntityId: Long?,
    val resolutionNote: String?,
    val resolvedAt: Instant?,
    val resolvedBy: String?,
    val createdAt: Instant,
    val createdBy: String,
)

data class RaiseExceptionRequest(
    val area: String,
    val description: String,
    val linkedEntityType: String? = null,
    val linkedEntityId: Long? = null,
)

data class ResolveExceptionRequest(val note: String)

/**
 * One row of Q11's standing chase list — a holding whose status was never established.
 *
 * Not an [ExceptionItemDto]: this is a live query over holdings, not a stored worklist row, so it
 * shrinks the moment someone records the answer rather than needing a second act to resolve.
 */
data class UnknownHoldingDto(
    val personId: Long,
    val sam: String,
    val name: String,
    val requirementId: Long,
    val code: String,
    val title: String,
)

// ---------------------------------------------------------------------------
// Register — ADM-4
// ---------------------------------------------------------------------------

/**
 * A register row as the list renders it.
 *
 * Denormalised on purpose: the register list is the module's main screen and shows a person's
 * name, a requirement code and a CC id on every row. Sending ids alone would make it three
 * lookups per row against caches a Vessel Master may not even be allowed to hold.
 */
data class RegisterRecordDto(
    val recordId: String,
    val type: String,
    val status: String,
    val outcome: String?,
    val open: Boolean,
    val personId: Long?,
    val sam: String?,
    val personName: String?,
    val positionName: String?,
    val requirementId: Long?,
    val requirementCode: String?,
    /** The legacy free-text title, where it never mapped to a catalogue code (§4.4). Badged. */
    val reqRaw: String?,
    val partnershipAbbrev: String,
    val ccId: String?,
    val effectiveFrom: LocalDate?,
    val effectiveTo: LocalDate?,
    val approvalFrom: LocalDate?,
    val approvalTo: LocalDate?,
    val raisedDate: LocalDate,
    val lateSubmissionAcknowledged: Boolean,
)

/** The detail view: the row plus the three things only it shows. */
data class RegisterRecordDetailDto(
    val record: RegisterRecordDto,
    val conditions: List<ApprovalConditionDto>,
    val notes: List<RegisterNoteDto>,
    /**
     * The human-readable trail the POC showed on the record. Distinct from `audit_event`, which
     * is the machine-readable, export-ready chain (§17.4) and is not exposed here.
     */
    val trail: List<RegisterTrailEntryDto>,
)

data class ApprovalConditionDto(val id: Long, val type: String, val body: String, val createdAt: Instant)

data class RegisterNoteDto(
    val id: Long,
    val party: String,
    val body: String,
    val createdAt: Instant,
    val createdBy: String,
)

data class RegisterTrailEntryDto(
    val ordinal: Int,
    val body: String,
    val actor: String,
    val occurredAt: Instant,
)

data class CreateRegisterRecordRequest(
    val type: String,
    val partnership: String,
    val cc: String,
    val personId: Long? = null,
    val requirementId: Long? = null,
    val reqRaw: String? = null,
    val effectiveFrom: LocalDate? = null,
    val effectiveTo: LocalDate? = null,
    /** Omitted for the default opening status of the type — see `RegisterService`. */
    val status: String? = null,
    /** Q17: required to lodge after the swing's cutoff, and recorded on the record. */
    val acknowledgeLateSubmission: Boolean = false,
)

data class TransitionRegisterRecordRequest(val status: String)

data class CloseRegisterRecordRequest(
    val outcome: String,
    val approvalFrom: LocalDate? = null,
    val approvalTo: LocalDate? = null,
    val conditions: List<AddConditionRequest> = emptyList(),
    val note: String? = null,
)

data class AddConditionRequest(val type: String, val body: String)

data class AddNoteRequest(val party: String, val body: String)

/** The auto-ID preview (§6). Advisory — the real key is allocated under a lock at create time. */
data class NextRecordIdDto(val recordId: String)

fun RegisterRecord.toDto() = RegisterRecordDto(
    recordId = recordId,
    type = type.wire,
    status = status.wire,
    outcome = outcome?.wire,
    open = isOpen,
    personId = person?.id,
    sam = person?.sam,
    personName = person?.name,
    positionName = position?.name,
    requirementId = requirement?.id,
    requirementCode = requirement?.code,
    reqRaw = reqRaw,
    partnershipAbbrev = partnership.abbrev,
    ccId = crewChange?.ccId,
    effectiveFrom = effectiveFrom,
    effectiveTo = effectiveTo,
    approvalFrom = approvalFrom,
    approvalTo = approvalTo,
    raisedDate = raisedDate,
    lateSubmissionAcknowledged = lateSubmissionAcknowledged,
)

fun RegisterRecord.toDetailDto() = RegisterRecordDetailDto(
    record = toDto(),
    conditions = conditions
        .sortedBy { it.createdAt }
        .map { ApprovalConditionDto(it.requiredId, it.type.wire, it.body, it.createdAt) },
    notes = notes
        .sortedBy { it.createdAt }
        .map { RegisterNoteDto(it.requiredId, it.party, it.body, it.createdAt, it.createdBy) },
    trail = auditEntries
        .sortedBy { it.ordinal }
        .map { RegisterTrailEntryDto(it.ordinal, it.body, it.actor, it.occurredAt) },
)

fun ExceptionItem.toDto() = ExceptionItemDto(
    id = requiredId,
    area = area,
    description = description,
    state = state,
    linkedEntityType = linkedEntityType,
    linkedEntityId = linkedEntityId,
    resolutionNote = resolutionNote,
    resolvedAt = resolvedAt,
    resolvedBy = resolvedBy,
    createdAt = createdAt,
    createdBy = createdBy,
)

fun QualificationHolding.toUnknownDto() = UnknownHoldingDto(
    personId = person.requiredId,
    sam = person.sam,
    name = person.name,
    requirementId = requirement.requiredId,
    code = requirement.code,
    title = requirement.title,
)
