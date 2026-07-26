package au.crewcomp.rules

import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class MatrixVersionRepository : PanacheRepositoryBase<MatrixVersion, Long> {

    /**
     * The currently published version: latest `effective_from` wins (§4.2). There is exactly one
     * at a time, but the query orders defensively rather than assuming it — a second published
     * row is a bug the evaluation should survive deterministically, not crash on.
     */
    fun currentPublished(): MatrixVersion? =
        find(
            "statusValue = ?1 order by effectiveFrom desc, id desc",
            MatrixStatus.PUBLISHED.wire,
        ).firstResult()

    fun byLabel(label: String): MatrixVersion? = find("label", label).firstResult()

    fun drafts(): List<MatrixVersion> = list("statusValue", MatrixStatus.DRAFT.wire)

    fun allOrdered(): List<MatrixVersion> = list("from MatrixVersion order by effectiveFrom desc nulls first, id desc")
}

@ApplicationScoped
class RequirementRuleRepository : PanacheRepositoryBase<RequirementRule, Long> {

    fun forVersion(matrixVersionId: Long): List<RequirementRule> =
        find(
            """
            select r from RequirementRule r
            join fetch r.position
            join fetch r.requirement
            left join fetch r.partnership
            where r.matrixVersion.id = ?1
            """.trimIndent(),
            matrixVersionId,
        ).list()

    fun find(matrixVersionId: Long, partnershipId: Long?, positionId: Long, requirementId: Long): RequirementRule? =
        if (partnershipId == null) {
            find(
                "matrixVersion.id = ?1 and partnership is null and position.id = ?2 and requirement.id = ?3",
                matrixVersionId, positionId, requirementId,
            ).firstResult()
        } else {
            find(
                "matrixVersion.id = ?1 and partnership.id = ?2 and position.id = ?3 and requirement.id = ?4",
                matrixVersionId, partnershipId, positionId, requirementId,
            ).firstResult()
        }
}

@ApplicationScoped
class ConditionalRuleRepository : PanacheRepositoryBase<ConditionalRule, Long> {

    fun forVersion(matrixVersionId: Long): List<ConditionalRule> =
        find(
            """
            select distinct c from ConditionalRule c
            join fetch c.position
            left join fetch c.requirement
            left join fetch c.members m
            left join fetch m.requirement
            where c.matrixVersion.id = ?1
            """.trimIndent(),
            matrixVersionId,
        ).list()
}

@ApplicationScoped
class QuotaRuleRepository : PanacheRepositoryBase<QuotaRule, Long> {

    fun forVersion(matrixVersionId: Long): List<QuotaRule> =
        find(
            """
            select distinct q from QuotaRule q
            join fetch q.requirement
            left join fetch q.positions
            where q.matrixVersion.id = ?1
            """.trimIndent(),
            matrixVersionId,
        ).list()
}
