package au.crewcomp.api

import au.crewcomp.engine.MatrixDiffResult
import au.crewcomp.engine.QuotaDiffEntry
import au.crewcomp.engine.RuleDiffEntry
import au.crewcomp.rules.ConditionalRule
import au.crewcomp.rules.MatrixVersion
import au.crewcomp.rules.MatrixVersionDetail
import au.crewcomp.rules.MatrixVersionSummary
import au.crewcomp.rules.QuotaRule
import au.crewcomp.rules.RequirementRule
import java.time.Instant
import java.time.LocalDate

/**
 * ADM-3 / §5.5 matrix versioning representations.
 *
 * These carry **ids, not labels**, for requirements, positions and partnerships — the same choice
 * [CellDto] makes. The SPA has the catalogue, the position list and the partnership list loaded
 * before it can render any of these screens, so denormalising a code into every one of a few
 * thousand rules would inflate the payload to say something the client already knows.
 */

data class MatrixVersionDto(
    val id: Long,
    val label: String,
    val status: String,
    val effectiveFrom: LocalDate?,
    val publishedBy: String?,
    val publishedAt: Instant?,
    val notes: String?,
    /** The footnote label *this version* uses for the tier review rule (§4.2), or null. */
    val tierFootnote: String?,
    val editable: Boolean,
)

data class MatrixVersionSummaryDto(
    val version: MatrixVersionDto,
    val requirementRuleCount: Long,
    val conditionalRuleCount: Long,
    val quotaRuleCount: Long,
)

/** One cell of the matrix: a level for (partnership-or-base, position, requirement). */
data class MatrixRuleDto(
    val id: Long,
    /** `null` = the base (`'*'`) rule that applies to every partnership. */
    val partnershipId: Long?,
    val positionId: Long,
    val requirementId: Long,
    val level: String,
)

data class MatrixConditionalRuleDto(
    val id: Long,
    val kind: String,
    val positionId: Long,
    /** The target for `dependent` rules; null for `one_of`. */
    val requirementId: Long?,
    val label: String?,
    val members: List<MatrixConditionalMemberDto>,
)

data class MatrixConditionalMemberDto(val requirementId: Long, val role: String, val ordinal: Int)

data class MatrixQuotaRuleDto(
    val id: Long,
    val footnote: String,
    val requirementId: Long,
    val minCount: Int,
    val scope: String,
    /** Empty = the quota counts people in any position (§4.2). */
    val positionIds: List<Long>,
)

data class MatrixVersionDetailDto(
    val version: MatrixVersionDto,
    val rules: List<MatrixRuleDto>,
    val conditionals: List<MatrixConditionalRuleDto>,
    val quotas: List<MatrixQuotaRuleDto>,
)

data class CreateMatrixDraftRequest(
    val label: String,
    /** Any version, superseded ones included — reviving a past matrix is a normal thing to want. */
    val copyFromVersionId: Long? = null,
    val notes: String? = null,
)

data class UpdateMatrixDraftRequest(
    val label: String,
    val notes: String? = null,
    val tierFootnote: String? = null,
)

/**
 * One cell edit. [partnershipId] `null` addresses the base rule; a partnership id addresses that
 * partnership's override, and an empty [level] on an override is the positive statement "this
 * partnership does not require it" — which is why clearing an override is a DELETE, not this.
 */
data class SetMatrixCellRequest(
    val partnershipId: Long? = null,
    val positionId: Long,
    val requirementId: Long,
    val level: String,
)

data class PublishMatrixRequest(
    /** Defaults to the server's business date — a matrix takes effect when someone decides it does. */
    val effectiveFrom: LocalDate? = null,
)

data class PublicationResultDto(
    val published: MatrixVersionDto,
    val superseded: MatrixVersionDto?,
)

// --- §5.5 diff -------------------------------------------------------------

data class RuleDiffEntryDto(
    val partnershipId: Long?,
    val positionId: Long,
    val requirementId: Long,
    val from: String?,
    val to: String?,
    /** `added` · `removed` · `level_changed`. */
    val kind: String,
)

data class QuotaDiffEntryDto(
    val footnote: String,
    val requirementId: Long,
    val scope: String,
    val fromMin: Int?,
    val toMin: Int?,
    val kind: String,
)

data class MatrixDiffDto(
    val fromVersionId: Long,
    val toVersionId: Long,
    val rules: List<RuleDiffEntryDto>,
    val quotas: List<QuotaDiffEntryDto>,
    val empty: Boolean,
)

// ---------------------------------------------------------------------------
// Mapping
// ---------------------------------------------------------------------------

fun MatrixVersion.toDto() = MatrixVersionDto(
    id = requiredId,
    label = label,
    status = statusValue,
    effectiveFrom = effectiveFrom,
    publishedBy = publishedBy,
    publishedAt = publishedAt,
    notes = notes,
    tierFootnote = tierFootnote,
    editable = isEditable,
)

fun MatrixVersionSummary.toDto() = MatrixVersionSummaryDto(
    version = version.toDto(),
    requirementRuleCount = requirementRuleCount,
    conditionalRuleCount = conditionalRuleCount,
    quotaRuleCount = quotaRuleCount,
)

fun RequirementRule.toDto() = MatrixRuleDto(
    id = requiredId,
    partnershipId = partnership?.requiredId,
    positionId = position.requiredId,
    requirementId = requirement.requiredId,
    level = levelValue,
)

fun ConditionalRule.toDto() = MatrixConditionalRuleDto(
    id = requiredId,
    kind = kindValue,
    positionId = position.requiredId,
    requirementId = requirement?.requiredId,
    label = label,
    members = members
        .sortedBy { it.ordinal }
        .map { MatrixConditionalMemberDto(it.requirement.requiredId, it.roleValue, it.ordinal) },
)

fun QuotaRule.toDto() = MatrixQuotaRuleDto(
    id = requiredId,
    footnote = footnote,
    requirementId = requirement.requiredId,
    minCount = minCount,
    scope = scopeValue,
    positionIds = positions.map { it.requiredId }.sorted(),
)

fun MatrixVersionDetail.toDto() = MatrixVersionDetailDto(
    version = version.toDto(),
    rules = rules.map { it.toDto() },
    conditionals = conditionals.map { it.toDto() },
    quotas = quotas.map { it.toDto() },
)

private fun RuleDiffEntry.toDto() = RuleDiffEntryDto(
    partnershipId = key.partnershipId?.value,
    positionId = key.positionId.value,
    requirementId = key.requirementId.value,
    from = from?.label,
    to = to?.label,
    kind = kind.name.lowercase(),
)

private fun QuotaDiffEntry.toDto() = QuotaDiffEntryDto(
    footnote = key.footnote,
    requirementId = key.requirementId.value,
    scope = key.scope.wire,
    fromMin = from?.min,
    toMin = to?.min,
    kind = kind.name.lowercase(),
)

fun MatrixDiffResult.toDto() = MatrixDiffDto(
    fromVersionId = fromVersionId.value,
    toVersionId = toVersionId.value,
    rules = rules.map { it.toDto() },
    quotas = quotas.map { it.toDto() },
    empty = isEmpty,
)
