package au.crewcomp.compliance

import au.crewcomp.engine.MatrixSnapshot
import au.crewcomp.engine.MatrixVersionId
import au.crewcomp.rules.ConditionalRuleRepository
import au.crewcomp.rules.MatrixStatus
import au.crewcomp.rules.MatrixVersion
import au.crewcomp.rules.MatrixVersionRepository
import au.crewcomp.rules.QuotaRuleRepository
import au.crewcomp.rules.RequirementRuleRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads a [MatrixSnapshot] — the immutable, self-contained value the §5 engine evaluates against.
 *
 * Published and superseded versions are immutable by definition (§4.2), so their snapshots are
 * cached indefinitely; drafts are rebuilt every time because they are being edited. That is the
 * whole of the caching policy, and it is why NFR-3's sub-500 ms budget is comfortable without
 * materialising any evaluation.
 */
@ApplicationScoped
class MatrixSnapshotService(
    private val matrixVersions: MatrixVersionRepository,
    private val requirementRules: RequirementRuleRepository,
    private val conditionalRules: ConditionalRuleRepository,
    private val quotaRules: QuotaRuleRepository,
) {
    private val immutableSnapshots = ConcurrentHashMap<Long, MatrixSnapshot>()

    /** The currently published version (§4.2: latest `effective_from` wins). */
    @Transactional
    fun current(): MatrixSnapshot {
        val version = matrixVersions.currentPublished()
            ?: throw NoPublishedMatrixException()
        return snapshotOf(version)
    }

    @Transactional
    fun forVersion(matrixVersionId: Long): MatrixSnapshot {
        val version = matrixVersions.findById(matrixVersionId)
            ?: throw IllegalArgumentException("No matrix version $matrixVersionId")
        return snapshotOf(version)
    }

    /**
     * The snapshot to evaluate a swing against: the pinned version if one is given (historical
     * reconstruction, §5.5), otherwise the currently published one.
     */
    @Transactional
    fun forEvaluation(pinnedVersionId: Long?): MatrixSnapshot =
        if (pinnedVersionId == null) current() else forVersion(pinnedVersionId)

    /** Drops cached snapshots for a version — called when a draft is edited or published. */
    fun invalidate(matrixVersionId: Long) {
        immutableSnapshots.remove(matrixVersionId)
    }

    fun invalidateAll() = immutableSnapshots.clear()

    private fun snapshotOf(version: MatrixVersion): MatrixSnapshot {
        val id = version.requiredId
        if (version.status == MatrixStatus.DRAFT) return build(version)
        return immutableSnapshots.computeIfAbsent(id) { build(version) }
    }

    private fun build(version: MatrixVersion): MatrixSnapshot {
        val id = version.requiredId
        return MatrixSnapshot(
            versionId = MatrixVersionId(id),
            label = version.label,
            rules = requirementRules.forVersion(id).map { it.toView() },
            conditionals = conditionalRules.forVersion(id).map { it.toView() },
            quotas = quotaRules.forVersion(id).map { it.toView() },
            tierFootnote = version.tierFootnote,
            tierPolicy = version.tierPolicies
                .groupBy { it.vesselClass }
                .mapValues { (_, policies) -> policies.map { it.acceptedTier }.toSet() },
        )
    }
}

/**
 * Raised when evaluation is requested and no matrix version has been published. This is a
 * configuration state, not a server fault: an empty system before the first publication is
 * expected, and the API reports it as such.
 */
class NoPublishedMatrixException :
    RuntimeException("No published matrix version — publish one before evaluating compliance (§4.2)")
