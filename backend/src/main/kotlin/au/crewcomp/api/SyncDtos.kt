package au.crewcomp.api

import au.crewcomp.evidence.EvidenceDocument
import au.crewcomp.notify.Notification
import au.crewcomp.sync.SyncTombstone
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * §10.3 mobile sync representations.
 *
 * The contract the spec fixes is `snapshot` / `delta?cursor=` / `queue`, and these are its
 * payloads. Three properties matter more than the field lists:
 *
 *  * **A cursor is one scalar.** A client holds a single `cursor` across every entity type,
 *    because a per-type cursor set is a per-type opportunity to get one wrong.
 *  * **The client is never asked who it is.** There is no `personId` anywhere in a request. The
 *    server answers for the authenticated crew member and nobody else (AUTH-2).
 *  * **The engine's answers arrive pre-computed.** [SyncSnapshotDto.standing] carries the §5.2
 *    evaluation the server calculated, because AUTH-1 forbids a client deciding what a cell
 *    state is. The app renders it and, offline, counts days against it — which §7.6 explicitly
 *    permits ("expiry countdowns … are safe to show stale").
 */

data class SyncSnapshotDto(
    /** High-water mark to send back as `?cursor=`. */
    val cursor: Long,
    /** Compared against the value in a later delta to decide whether to re-snapshot. */
    val referenceCursor: Long,
    /** NFR-5 / O-11 — the operating timezone's date, which a device cannot compute. */
    val serverToday: LocalDate,
    val person: PersonDto,
    val holdings: List<HoldingDto>,
    val assignments: List<AssignmentDto>,
    val leave: List<LeaveRecordDto>,
    val notifications: List<NotificationDto>,
    val submissions: List<EvidenceSubmissionDto>,
    val reference: SyncReferenceDto,
    /** Null when the crew member has no assignment to evaluate against — see [SyncStandingDto]. */
    val standing: SyncStandingDto?,
)

/**
 * The catalogue and matrix subset a device needs to render "what I need" (MOB-1).
 *
 * Sent whole rather than as row-level deltas: a matrix publication changes most of it at once,
 * and applying half of one would show crew a coherent-looking view assembled from two matrix
 * versions.
 */
data class SyncReferenceDto(
    val cursor: Long,
    val matrixVersionId: Long?,
    val matrixVersionLabel: String?,
    val requirements: List<RequirementDto>,
    val positions: List<PositionDto>,
    val partnerships: List<PartnershipDto>,
    val crewChanges: List<CrewChangeDto>,
)

/**
 * The server's §5.2 evaluation of this crew member, and the swing it was computed against.
 *
 * A roll-up is only meaningful against a swing, so the swing is named here rather than left for
 * the client to assume. When the crew member has no current or upcoming assignment there is
 * nothing to evaluate against and this is absent — which the app must show as "no upcoming
 * swing", not as "compliant".
 */
data class SyncStandingDto(
    val ccId: String,
    val partnershipAbbrev: String,
    val from: LocalDate,
    val to: LocalDate,
    /** True when this is the swing currently in progress rather than the next one. */
    val current: Boolean,
    val evaluation: PersonEvaluationDto,
)

data class NotificationDto(
    val id: Long,
    val kind: String,
    /** SEC-13: the only field a push payload may carry, alongside [deepLink]. */
    val title: String,
    val body: String?,
    val deepLink: String?,
    val createdAt: Instant,
    val readAt: Instant?,
)

/** MOB-4's visible queue state for a submission the device made. */
data class EvidenceSubmissionDto(
    /** The device-generated id — the idempotency key, and the client's join key. */
    val publicId: UUID,
    val requirementHintId: Long?,
    val source: String,
    val contentType: String?,
    val declaredSize: Long?,
    /** Bytes the server has; where a resuming upload continues from (MOB-5a). */
    val uploadOffset: Long,
    val uploadComplete: Boolean,
    val verificationStatus: String,
    val rejectionReason: String?,
    val submittedAt: Instant,
)

data class SyncTombstoneDto(val entityType: String, val entityId: Long, val seq: Long)

data class SyncDeltaDto(
    val cursor: Long,
    val referenceCursor: Long,
    /**
     * True when reference data has moved since the cursor the client sent. The client re-fetches
     * the snapshot; the person-scoped rows in this response are still valid and still applied.
     */
    val referenceStale: Boolean,
    val serverToday: LocalDate,
    /** Present only when the person row itself changed. */
    val person: PersonDto?,
    val holdings: List<HoldingDto>,
    val assignments: List<AssignmentDto>,
    val leave: List<LeaveRecordDto>,
    val notifications: List<NotificationDto>,
    val submissions: List<EvidenceSubmissionDto>,
    val tombstones: List<SyncTombstoneDto>,
    /** Recomputed on every delta: a holding change silently changes the roll-up. */
    val standing: SyncStandingDto?,
)

// ---------------------------------------------------------------------------
// Outbound queue (§7.6 write model)
// ---------------------------------------------------------------------------

/**
 * A batch of client-originated writes.
 *
 * §7.6 restricts these to operations that are "commutative or idempotent by construction", which
 * is what removes general conflict resolution from this system: evidence submissions are
 * append-only with client-generated ids, and read-marks are monotonic.
 */
data class SyncQueueRequest(val operations: List<SyncOperationDto>)

data class SyncOperationDto(
    /**
     * Client-generated id for this queue entry. Echoed back in the result so the device knows
     * exactly which entries to drop, even when the batch partially succeeded.
     */
    val opId: String,
    /** `notification.read` | `evidence.submit` — the only two a client may originate. */
    val type: String,
    val notificationId: Long? = null,
    val readAt: Instant? = null,
    val submission: EvidenceSubmitDto? = null,
)

data class EvidenceSubmitDto(
    val publicId: UUID,
    /** `mobile_camera` | `mobile_file`. An `admin_upload` from a device is refused. */
    val source: String,
    val contentType: String? = null,
    val declaredSize: Long? = null,
    val declaredSha256: String? = null,
    /** The crew member's tag for what this evidences — a hint, never binding (§7.5). */
    val requirementHintId: Long? = null,
)

/**
 * Per-operation outcomes.
 *
 * Deliberately not all-or-nothing. A durable queue that fails a whole batch on one bad entry
 * retries that batch forever: the poison entry blocks every good one behind it, offline, on a
 * vessel. Each operation gets its own transaction and its own verdict, and a `rejected` verdict
 * tells the device to stop retrying rather than to back off.
 */
data class SyncQueueResultDto(val cursor: Long, val results: List<SyncOperationResultDto>)

data class SyncOperationResultDto(
    val opId: String,
    /** `applied` — done. `rejected` — will never succeed, drop it. `failed` — retry later. */
    val status: String,
    val detail: String? = null,
    /** For `evidence.submit`: where to resume the upload from (MOB-5a). */
    val uploadOffset: Long? = null,
)

data class UploadStateDto(val offset: Long, val complete: Boolean, val declaredSize: Long?)

// ---------------------------------------------------------------------------
// Mapping
// ---------------------------------------------------------------------------

fun Notification.toDto() = NotificationDto(
    id = requiredId,
    kind = kind,
    title = title,
    body = body,
    deepLink = deepLink,
    createdAt = createdAt,
    readAt = readAt,
)

fun EvidenceDocument.toDto() = EvidenceSubmissionDto(
    publicId = publicId,
    requirementHintId = requirementHint?.requiredId,
    source = source.wire,
    contentType = contentType,
    declaredSize = declaredSize,
    uploadOffset = uploadOffset,
    uploadComplete = uploadComplete,
    verificationStatus = verificationStatus.wire,
    rejectionReason = rejectionReason,
    submittedAt = submittedAt,
)

fun SyncTombstone.toDto() = SyncTombstoneDto(
    entityType = entityType,
    entityId = entityId,
    seq = seq,
)
