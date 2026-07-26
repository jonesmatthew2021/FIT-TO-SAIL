package au.crewcomp.platform.security

import jakarta.enterprise.context.ApplicationScoped

/**
 * AUTH-2 — the one component that turns a [DataScope] into an actual restriction.
 *
 * It offers two forms of the same rule, and callers reading person-scoped data are expected to
 * use both:
 *
 *  1. [clause] pushes the restriction into the query, so restricted actors never load rows they
 *     may not see.
 *  2. [assertVisible] / [retain] check what came back, so a query that forgets the clause — or
 *     an entity loaded by primary key, which no query predicate can reach — still fails closed.
 *
 * Everything person-scoped funnels through here. No endpoint derives its own scope.
 */
@ApplicationScoped
class ScopeGuard(private val policy: AccessPolicy) {

    /**
     * An HQL fragment restricting a query to the current actor's scope, plus its parameters.
     *
     * @param personPath HQL path to the owning person's id, e.g. `"h.person.id"`.
     * @param partnershipPath HQL path to the owning partnership's id, where the entity has one.
     *   Required for a [DataScope.Partnerships] actor; its absence makes the query deny-all.
     */
    fun clause(personPath: String, partnershipPath: String? = null): ScopeClause =
        when (val scope = policy.scope()) {
            is DataScope.All -> ScopeClause.PERMIT_ALL

            is DataScope.OwnPersonOnly -> ScopeClause(
                hql = "$personPath = :scopePersonId",
                params = mapOf("scopePersonId" to scope.personId),
            )

            is DataScope.Partnerships ->
                if (partnershipPath == null) {
                    ScopeClause.DENY_ALL
                } else {
                    ScopeClause(
                        hql = "$partnershipPath in (:scopePartnershipIds)",
                        params = mapOf("scopePartnershipIds" to scope.partnershipIds),
                    )
                }

            is DataScope.None -> ScopeClause.DENY_ALL
        }

    /** Fails closed if the current actor may not see data owned by this person/partnership. */
    fun assertVisible(personId: Long?, partnershipId: Long? = null) {
        when (val scope = policy.scope()) {
            is DataScope.All -> return
            is DataScope.OwnPersonOnly ->
                if (personId != scope.personId) {
                    throw AccessDeniedException("Out of scope: person $personId")
                }
            is DataScope.Partnerships ->
                if (partnershipId == null || partnershipId !in scope.partnershipIds) {
                    throw AccessDeniedException("Out of scope: partnership $partnershipId")
                }
            is DataScope.None -> throw AccessDeniedException("No readable scope for this actor")
        }
    }

    /** Drops out-of-scope items from an already-loaded collection. */
    fun <T> retain(
        items: Iterable<T>,
        personId: (T) -> Long?,
        partnershipId: (T) -> Long? = { null },
    ): List<T> = when (val scope = policy.scope()) {
        is DataScope.All -> items.toList()
        is DataScope.OwnPersonOnly -> items.filter { personId(it) == scope.personId }
        is DataScope.Partnerships -> items.filter { partnershipId(it) in scope.partnershipIds }
        is DataScope.None -> emptyList()
    }
}

/**
 * An HQL restriction and its bind parameters. [PERMIT_ALL] and [DENY_ALL] are constant
 * predicates so that callers can concatenate unconditionally rather than branching.
 */
data class ScopeClause(val hql: String, val params: Map<String, Any>) {

    /** Appends [condition] to this clause with `and`. */
    fun and(condition: String, params: Map<String, Any> = emptyMap()): ScopeClause =
        ScopeClause("($hql) and ($condition)", this.params + params)

    companion object {
        val PERMIT_ALL = ScopeClause("1 = 1", emptyMap())
        val DENY_ALL = ScopeClause("1 = 0", emptyMap())
    }
}
