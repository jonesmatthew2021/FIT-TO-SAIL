package au.crewcomp.api

import au.crewcomp.courses.CourseOffer
import au.crewcomp.evidence.EvidenceDocument
import au.crewcomp.notify.Notification
import au.crewcomp.people.Attestation
import au.crewcomp.people.CrewStatement
import au.crewcomp.sync.SyncTombstone
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    /** The crew member's own one-tap answers, and what the office did about each (MOB-5, ADM-11). */
    val crewStatements: List<CrewStatementSyncDto>,
    /** MOB-9's pre-sail declarations, as signed. */
    val attestations: List<AttestationSyncDto>,
    /** MOB-8's course dates, already filtered to this crew member — see [CourseOfferDto]. */
    val courseOptions: List<CourseOfferDto>,
    val reference: SyncReferenceDto,
    /** Null when the crew member has no assignment to evaluate against — see [SyncStandingDto]. */
    val standing: SyncStandingDto?,
)

/**
 * One of the crew member's own statements, coming back with the office's answer on it.
 *
 * This closes the loop the outbox opens. The device already records what it *sent* — that is the
 * `crew_intents` row, keyed on [opId] — but a device-local record is not durable across a reinstall
 * and can say nothing about what happened next. This row is server-owned, so it survives, and it
 * carries the decision.
 *
 * [opId] is the join. It is the device's own queue-entry id, echoed back, which lets the app match
 * a server statement to the tap that produced it without inventing a second identifier.
 *
 * Note what is **not** here: no cell state, no roll-up, no holding. A statement is not a compliance
 * answer and the app must not read one as though it were (AUTH-1). The person's standing arrives, as
 * it always has, in [SyncSnapshotDto.standing].
 */
data class CrewStatementSyncDto(
    val id: Long,
    /** The device's queue-entry id — how the app matches this to its own outbox record. */
    val opId: String,
    /**
     * The **operation name** the device posted — `requirement.progress` | `requirement.help` — not
     * the domain's `course_booked` / `help_requested`.
     *
     * Deliberate: this payload is read by one client, whose whole vocabulary for these is the
     * operation type it queued. Sending the domain word would make the app translate between two
     * enumerations to recognise its own tap. See [au.crewcomp.people.CrewStatementKind].
     */
    val kind: String,
    val requirementId: Long,
    /** `open` | `actioned` | `dismissed`. */
    val status: String,
    /** The expiry the statement was about, or null when there was no expiring holding. */
    val aboutExpiry: LocalDate?,
    val raisedAt: Instant,
    /**
     * The coordinator's answer, **written knowing the crew member reads it** — the ADM-11 form says
     * so above the field. That is what makes it worth sending: "we could not find your booking, can
     * you forward the confirmation" is an answer, and a bare "dismissed" is a door closing.
     */
    val decisionNote: String?,
    val decidedAt: Instant?,
)

/**
 * One course date MOB-8 may offer this crew member.
 *
 * **Already filtered, and that is the contract.** The screen lists only dates that resolve the
 * requirement before it lapses and that the person is not at sea for; it is never a generic
 * catalogue the crew member has to sift. Both of those need the roster, so both are the server's —
 * see [au.crewcomp.courses.CourseOffers] for the rules and why "on leave" is labelled rather than
 * excluded.
 *
 * [seats] is what the provider last told us, not an inventory this system controls. Nothing
 * decrements it and [id] does not reserve anything: choosing a date raises a request a coordinator
 * actions (ADM-11), because this system has no contract with the provider.
 */
data class CourseOfferDto(
    /** The catalogue's business key — what the device sends back as `subjectRef`. */
    val id: String,
    val requirementId: Long,
    val starts: LocalDate,
    val finishes: LocalDate,
    val provider: String,
    val location: String,
    /** The provider's own phrasing. Empty rather than derived when they did not give one. */
    val durationLabel: String,
    val seats: Int,
    /** "Clear of your leave · 11 days before expiry" — composed server-side, roster in hand. */
    val note: String,
    /** At most one per requirement: the soonest date with a seat that is clear of their leave. */
    val recommended: Boolean,
    val waitlistOnly: Boolean,
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
    val crewStatements: List<CrewStatementSyncDto>,
    val attestations: List<AttestationSyncDto>,
    /**
     * Recomputed in full on every delta, never diffed — like [standing] and for the same reason.
     *
     * A course offer is a derived answer rather than a row: it changes when the catalogue changes,
     * when the person's roster changes, when a certificate is renewed, and when a day passes. A
     * client applying row-level deltas would show a stale set indefinitely after any of those, and
     * there is no tombstone that could tell it a date is no longer worth offering.
     */
    val courseOptions: List<CourseOfferDto>,
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
 * append-only with client-generated ids, read-marks are monotonic, and a crew statement is keyed
 * on the `opId` that carried it.
 */
data class SyncQueueRequest(val operations: List<SyncOperationDto>)

data class SyncOperationDto(
    /**
     * Client-generated id for this queue entry. Echoed back in the result so the device knows
     * exactly which entries to drop, even when the batch partially succeeded.
     *
     * It is also the **idempotency key** for every operation that creates something: a device
     * re-posts the same id after a dropped connection, and the server must treat the second
     * delivery as a no-op rather than as a second answer.
     */
    val opId: String,
    /**
     * `notification.read` | `evidence.submit` | `requirement.progress` | `requirement.help` — the
     * operations a client may originate today. An unrecognised type is `rejected` per operation,
     * never per batch: an old server and a new app must not deadlock each other's queues.
     */
    val type: String,
    val notificationId: Long? = null,
    val readAt: Instant? = null,
    val submission: EvidenceSubmitDto? = null,
    /**
     * `requirement.progress` | `requirement.help` | `register.exemption_request`: what the crew
     * member is answering or asking about.
     */
    val requirementId: Long? = null,
    /**
     * `course.seat_request` | `course.waitlist`: the course option the crew member picked.
     *
     * A catalogue key and opaque to the queue — resolved through [au.crewcomp.courses.CourseCatalogue]
     * so that the office's record of the ask carries a sentence naming the date, not just a key
     * that may not resolve in six months.
     */
    val subjectRef: String? = null,
    /** `register.exemption_request`: `no_seat` | `medical_personal` | `with_authority`. */
    val reason: String? = null,
    /** `register.exemption_request`: the crew member's own words, optional. */
    val note: String? = null,
    /**
     * `register.exemption_request`: the swing to raise it against.
     *
     * Checked against the person's own roster rather than trusted — it is the one field on this
     * operation that names something outside the crew member, and a device that could choose any
     * swing could file a request against one it has nothing to do with.
     */
    val ccId: String? = null,
    /**
     * `register.exemption_request`: the `opId`s of what the crew member already tried.
     *
     * The device is what knows they happened, so the device is what attaches them. Those the server
     * can resolve to a crew statement become a sentence on the record's note; those it cannot are
     * dropped, because an `opId` with no server record is an operation that was never accepted.
     */
    val attachedOpIds: List<String>? = null,
    /** `attestation.sign_off`: the assignment being signed for — checked against their roster. */
    val assignmentId: Long? = null,
    /** `attestation.sign_off`: the declaration ids the crew member confirmed. */
    val declarations: List<String>? = null,
    /**
     * `team.nudge`: whom to nudge, by Sam #.
     *
     * The only field on this queue that names another person, and it is checked against the
     * supervisor's own watch rather than trusted. A Sam # rather than a person id because that is
     * what MOB-11's payload carries — [TeamMemberDto] deliberately has no internal id on it.
     */
    val targetSam: String? = null,
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
 * A signed pre-sail declaration, as the server holds it (MOB-9).
 *
 * The device sends this and then reads it back rather than trusting its own copy, for one reason
 * that matters and one that is merely useful.
 *
 * The one that matters: [signedAt] is the **server's** time, and the app must show that or nothing.
 * A legal declaration timestamped from the phone of the person who made it is worth nothing as
 * evidence, so the client has no timestamp of its own to fall back on.
 *
 * The one that is useful: [declarations] is what was actually confirmed. A screen that re-derived
 * the ticks from "which lines were already satisfied" would show a *different* set from the one
 * signed — every line the crew member ticked by hand would come back empty.
 */
data class AttestationSyncDto(
    val id: Long,
    /** The device's queue-entry id, echoed back — how the app matches this to its own record. */
    val opId: String,
    val assignmentId: Long,
    val ccId: String,
    /** The ids confirmed. May be a subset: signing with a line outstanding is a real thing to do. */
    val declarations: List<String>,
    val signedAt: Instant,
    /**
     * The signature line, already written, in the **vessel's** timezone.
     *
     * Formatted here rather than on the device for the same reason `serverToday` is sent rather
     * than computed: a phone knows neither the operating timezone (O-11) nor the admin date
     * override, and this string appears on a legal record. "28 Jul 2026, 07:05 AWST" rendered from
     * a device set to UTC would be eight hours wrong, for some viewers only, silently.
     */
    val signedAtDisplay: String,
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

/**
 * Named `toSyncDto` rather than `toDto` because [CrewStatement] already has one, for ADM-11's queue.
 *
 * The two are deliberately different shapes and neither should grow into the other: the console's
 * row carries the person and their position, because a coordinator is triaging a list of people;
 * the device's row carries none of that, because it is already the crew member's own phone.
 */
fun Attestation.toSyncDto(zone: ZoneId) = AttestationSyncDto(
    id = requiredId,
    opId = opId,
    assignmentId = assignment.requiredId,
    ccId = crewChange.ccId,
    declarations = declarations.sorted(),
    signedAt = signedAt,
    signedAtDisplay = ATTESTATION_SIGNED_AT.format(signedAt.atZone(zone)),
)

/** `28 Jul 2026, 07:05 AWST` — the design's own format for MOB-9's signature block. */
private val ATTESTATION_SIGNED_AT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm zzz", Locale.ENGLISH)

fun CourseOffer.toDto() = CourseOfferDto(
    id = option.ref,
    requirementId = option.requirementId,
    starts = option.starts,
    finishes = option.finishes,
    provider = option.provider,
    location = option.location,
    // Empty rather than invented. A duration derived from the date range is wrong as often as it
    // is right — a two-day course can span a weekend — and the screen omits a blank line happily.
    durationLabel = option.durationLabel.orEmpty(),
    seats = option.seats,
    note = note,
    recommended = recommended,
    waitlistOnly = waitlistOnly,
)

fun CrewStatement.toSyncDto() = CrewStatementSyncDto(
    id = requiredId,
    opId = opId,
    kind = kind.operation,
    requirementId = requirement.requiredId,
    status = status.wire,
    aboutExpiry = aboutExpiry,
    raisedAt = createdAt,
    decisionNote = decisionNote,
    decidedAt = decidedAt,
)
