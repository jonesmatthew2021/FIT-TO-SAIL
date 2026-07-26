package au.crewcomp.rules

import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped

/**
 * `requirement_id → row count`, in one grouped query rather than one query per requirement.
 *
 * Shared by the repositories that answer ADM-6's usage counts. Every entity it is used for maps
 * the association as `requirement`, which is what makes one string parameter enough.
 */
private fun PanacheRepositoryBase<*, *>.countGroupedByRequirement(entity: String): Map<Long, Long> =
    getEntityManager()
        .createQuery(
            "select e.requirement.id, count(e) from $entity e group by e.requirement.id",
            Array<Any>::class.java,
        )
        .resultList
        .associate { (it[0] as Number).toLong() to (it[1] as Number).toLong() }

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

    /**
     * How many rules name each requirement, across **every** matrix version — ADM-6's usage
     * count. Historic versions count deliberately: the point of the number is "is this
     * catalogue entry load-bearing anywhere", and a retired version is still audit evidence.
     */
    fun countByRequirement(): Map<Long, Long> = countGroupedByRequirement("RequirementRule")

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

    /**
     * ADM-6 usage: a requirement counts as used by a conditional rule whether it is the rule's
     * target (`dependent`) or one of its members (`one_of`, `required_if_holds`, `unless_holds`).
     *
     * Two queries returning `(requirement, rule)` pairs rather than one clever grouped one: a
     * requirement can be both the target *and* a member of the same rule, and counting the pairs
     * distinctly in Kotlin is the readable way to make that one use rather than two.
     */
    fun countByRequirement(): Map<Long, Long> {
        val pairs = mutableSetOf<Pair<Long, Long>>()
        listOf(
            "select c.requirement.id, c.id from ConditionalRule c where c.requirement is not null",
            "select m.requirement.id, m.conditionalRule.id from ConditionalRuleMember m",
        ).forEach { query ->
            getEntityManager().createQuery(query, Array<Any>::class.java).resultList.forEach {
                pairs += (it[0] as Number).toLong() to (it[1] as Number).toLong()
            }
        }
        return pairs.groupingBy { it.first }.eachCount().mapValues { it.value.toLong() }
    }
}

@ApplicationScoped
class QuotaRuleRepository : PanacheRepositoryBase<QuotaRule, Long> {

    fun countByRequirement(): Map<Long, Long> = countGroupedByRequirement("QuotaRule")

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
