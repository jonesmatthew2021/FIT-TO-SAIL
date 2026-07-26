package au.crewcomp.engine

/**
 * §5.5 — diff between any two matrix versions: added / removed / level-changed.
 *
 * The diff is taken over the **raw rule records**, not over resolved rules for one partnership,
 * so that a change to a base (`'*'`) rule reads as one entry rather than five.
 */
object MatrixDiff {

    fun diff(from: MatrixSnapshot, to: MatrixSnapshot): MatrixDiffResult {
        val before = from.rules.associate { it.diffKey() to it.level }
        val after = to.rules.associate { it.diffKey() to it.level }

        val rules = (before.keys + after.keys).sortedWith(
            compareBy({ it.partnershipId?.value ?: -1L }, { it.positionId.value }, { it.requirementId.value }),
        ).mapNotNull { key ->
            val old = before[key]
            val new = after[key]
            when {
                old == null && new != null -> RuleDiffEntry(key, null, new, DiffKind.ADDED)
                old != null && new == null -> RuleDiffEntry(key, old, null, DiffKind.REMOVED)
                old != null && new != null && old != new -> RuleDiffEntry(key, old, new, DiffKind.LEVEL_CHANGED)
                else -> null
            }
        }

        val quotasBefore = from.quotas.associateBy { QuotaDiffKey(it.footnote, it.requirementId, it.scope) }
        val quotasAfter = to.quotas.associateBy { QuotaDiffKey(it.footnote, it.requirementId, it.scope) }

        val quotas = (quotasBefore.keys + quotasAfter.keys).sortedWith(
            compareBy({ it.footnote }, { it.requirementId.value }),
        ).mapNotNull { key ->
            val old = quotasBefore[key]
            val new = quotasAfter[key]
            when {
                old == null && new != null -> QuotaDiffEntry(key, null, new, DiffKind.ADDED)
                old != null && new == null -> QuotaDiffEntry(key, old, null, DiffKind.REMOVED)
                old != null && new != null && old != new -> QuotaDiffEntry(key, old, new, DiffKind.LEVEL_CHANGED)
                else -> null
            }
        }

        return MatrixDiffResult(from.versionId, to.versionId, rules, quotas)
    }

    private fun RequirementRuleView.diffKey() = RuleDiffKey(partnershipId, positionId, requirementId)
}

enum class DiffKind { ADDED, REMOVED, LEVEL_CHANGED }

data class RuleDiffKey(
    /** `null` = the base (`'*'`) rule. */
    val partnershipId: PartnershipId?,
    val positionId: PositionId,
    val requirementId: RequirementId,
)

data class RuleDiffEntry(
    val key: RuleDiffKey,
    val from: RuleLevel?,
    val to: RuleLevel?,
    val kind: DiffKind,
)

data class QuotaDiffKey(
    val footnote: String,
    val requirementId: RequirementId,
    val scope: QuotaScope,
)

data class QuotaDiffEntry(
    val key: QuotaDiffKey,
    val from: QuotaRuleView?,
    val to: QuotaRuleView?,
    val kind: DiffKind,
)

data class MatrixDiffResult(
    val fromVersionId: MatrixVersionId,
    val toVersionId: MatrixVersionId,
    val rules: List<RuleDiffEntry>,
    val quotas: List<QuotaDiffEntry>,
) {
    val isEmpty: Boolean get() = rules.isEmpty() && quotas.isEmpty()
}
