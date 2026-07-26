package au.crewcomp.api

import au.crewcomp.evidence.EvidenceDocument
import au.crewcomp.evidence.ExtractionSchema
import au.crewcomp.notify.Notification
import au.crewcomp.notify.NotificationKind
import au.crewcomp.people.IdentityProvider
import au.crewcomp.people.UserAccount
import au.crewcomp.platform.adapters.JobRun
import au.crewcomp.platform.adapters.ScheduledJob
import au.crewcomp.platform.config.ConfigSetting
import java.time.Instant
import java.time.LocalDate

/**
 * Representations for ADM-8 (notifications), ADM-9 (evidence verification) and ADM-10
 * (administration).
 *
 * Like the rest of the API these carry ids for anything the client already has a lookup table for,
 * and denormalised labels only where a client would otherwise need a second call it cannot make —
 * a person's name on an evidence document, for instance, because the queue is not scoped to one
 * person and the SPA has no reason to have loaded them all.
 */

// ---------------------------------------------------------------------------
// ADM-8 — notifications
// ---------------------------------------------------------------------------

data class AdminNotificationDto(
    val id: Long,
    val kind: String,
    /** `crew` · `back_office`, or null for a kind this revision does not know — see below. */
    val audience: String?,
    val title: String,
    val body: String?,
    val deepLink: String?,
    val createdAt: Instant,
    val readAt: Instant?,
    val read: Boolean,
    /** Who the row was addressed to. A per-role fan-out is several rows, and this is which. */
    val recipient: String,
)

data class NotificationSummaryDto(val total: Int, val unread: Long)

// ---------------------------------------------------------------------------
// ADM-9 — evidence verification queue
// ---------------------------------------------------------------------------

/** One extracted field with the confidence the model reported for it (§8 stage 2). */
data class ExtractedFieldDto(val name: String, val value: String?, val confidence: Double)

data class EvidenceDocumentDto(
    val publicId: String,
    val personId: Long,
    val sam: String,
    val personName: String,
    val partnershipAbbrev: String,
    val source: String,
    val submittedBy: String,
    val submittedAt: Instant,
    val verificationStatus: String,
    val contentType: String?,
    val byteSize: Long?,
    val uploadComplete: Boolean,
    /** True once bytes exist to show in the side-by-side view. */
    val hasContent: Boolean,
    /** The crew member's tag for what this evidences (§7.5) — an input to matching, possibly wrong. */
    val requirementHintId: Long?,
    /** What §8 stage 3 resolved. Null means nothing could be resolved; the reason says why. */
    val matchedRequirementId: Long?,
    val extractionModel: String?,
    val extraction: List<ExtractedFieldDto>,
    /** Why stage 4 sent this to a human rather than accepting it. */
    val reviewReason: String?,
    val rejectionReason: String?,
    val linkedHoldingId: Long?,
)

data class AcceptEvidenceRequest(
    /** What the **reviewer** decided, not what was extracted — accept and correct are one operation. */
    val requirementId: Long,
    val status: String,
    val expiry: LocalDate? = null,
    val issueDate: LocalDate? = null,
    val note: String? = null,
)

data class RejectEvidenceRequest(val reason: String)

// ---------------------------------------------------------------------------
// ADM-10 — administration
// ---------------------------------------------------------------------------

data class ConfigSettingDto(
    val key: String,
    val kind: String,
    val description: String,
    /** The effective value: the stored override if there is one, otherwise the code default. */
    val value: Any?,
    val defaultValue: Any?,
    val overridden: Boolean,
    val updatedAt: Instant?,
    val updatedBy: String?,
)

data class SetConfigRequest(
    /** Null clears the override, restoring the code default. */
    val value: Any? = null,
)

data class ScheduledJobDto(
    val name: String,
    /** The effective cron or interval, resolved from configuration. */
    val schedule: String,
    val description: String,
    val lastRun: JobRunDto?,
)

data class JobRunDto(
    val startedAt: Instant,
    val finishedAt: Instant?,
    val outcome: String,
    val detail: String?,
)

data class UserAccountDto(
    val id: Long,
    val displayName: String,
    val email: String?,
    /** `corporate` · `local_test`. A `local_test` account is a flagged SEC-1b exception. */
    val kind: String,
    val status: String,
    val roles: List<String>,
    val scopedPartnershipIds: List<Long>,
    val personId: Long?,
    val lastLoginAt: Instant?,
    /** True once a corporate identity has been bound — i.e. the person has signed in at least once. */
    val identityLinked: Boolean,
)

data class CreateTransitionalAccountRequest(
    val displayName: String,
    val email: String? = null,
    val roles: List<String>,
    val personId: Long? = null,
)

data class SetAccountStatusRequest(val status: String)

data class SetScopesRequest(val partnershipIds: List<Long>)

data class IdentityProviderDto(
    val id: Long,
    val provider: String,
    val issuer: String,
    val tenantOrDomain: String,
    val displayName: String,
    val enabled: Boolean,
)

data class CreateIdentityProviderRequest(
    val provider: String,
    val issuer: String,
    val tenantOrDomain: String,
    val displayName: String,
)

data class SetEnabledRequest(val enabled: Boolean)

// ---------------------------------------------------------------------------
// Mapping
// ---------------------------------------------------------------------------

/**
 * `audience` is looked up tolerantly.
 *
 * A notification row written by a newer application revision can carry a kind this one has never
 * heard of, and during an expand/contract deploy both revisions are live at once. Failing to render
 * the whole list because of one unrecognised string would be the wrong trade: the row still has a
 * title, a body and a deep link, all of which are useful without knowing its audience.
 */
fun Notification.toAdminDto() = AdminNotificationDto(
    id = requiredId,
    kind = kind,
    audience = NotificationKind.fromWireOrNull(kind)?.audience?.name?.lowercase(),
    title = title,
    body = body,
    deepLink = deepLink,
    createdAt = createdAt,
    readAt = readAt,
    read = isRead,
    recipient = recipient.displayName,
)

fun EvidenceDocument.toReviewDto() = EvidenceDocumentDto(
    publicId = publicId.toString(),
    personId = person.requiredId,
    sam = person.sam,
    personName = person.name,
    partnershipAbbrev = person.partnership.abbrev,
    source = sourceValue,
    submittedBy = submittedBy,
    submittedAt = submittedAt,
    verificationStatus = verificationStatusValue,
    contentType = contentType,
    byteSize = byteSize,
    uploadComplete = uploadComplete,
    hasContent = objectKey != null,
    requirementHintId = requirementHint?.requiredId,
    matchedRequirementId = matchedRequirement?.requiredId,
    extractionModel = extractionModel,
    // Every field the schema defines, present or not: a queue form that showed only the fields the
    // model happened to fill would give the reviewer nowhere to type the ones it missed.
    extraction = ExtractionSchema.FIELDS.map { name ->
        val field = ExtractionSchema.fieldOf(extraction, name)
        ExtractedFieldDto(name, field.value, field.confidence)
    },
    reviewReason = reviewReason,
    rejectionReason = rejectionReason,
    linkedHoldingId = linkedHolding?.requiredId,
)

fun ConfigSetting.toDto() = ConfigSettingDto(
    key = key.key,
    kind = key.kind.name.lowercase(),
    description = key.description,
    value = value,
    defaultValue = key.defaultValue,
    overridden = overridden,
    updatedAt = updatedAt,
    updatedBy = updatedBy,
)

fun JobRun.toDto() = JobRunDto(
    startedAt = startedAt,
    finishedAt = finishedAt,
    outcome = outcome,
    detail = detail,
)

fun ScheduledJob.toDto(lastRun: JobRun?) = ScheduledJobDto(
    name = name,
    schedule = cron,
    description = description,
    lastRun = lastRun?.toDto(),
)

fun UserAccount.toDto() = UserAccountDto(
    id = requiredId,
    displayName = displayName,
    email = email,
    kind = kindValue,
    status = status,
    roles = roles.map { it.wire }.sorted(),
    scopedPartnershipIds = scopedPartnershipIds.sorted(),
    personId = person?.id,
    lastLoginAt = lastLoginAt,
    // SEC-1: identity is (issuer, subject). Neither is exposed — an issuer URL and an opaque
    // subject on an admin screen are a fingerprinting surface and tell nobody anything useful.
    // Whether the binding exists is the operational question, and that is a boolean.
    identityLinked = issuer != null && subject != null,
)

fun IdentityProvider.toDto() = IdentityProviderDto(
    id = requiredId,
    provider = provider,
    issuer = issuer,
    tenantOrDomain = tenantOrDomain,
    displayName = displayName,
    enabled = enabled,
)
