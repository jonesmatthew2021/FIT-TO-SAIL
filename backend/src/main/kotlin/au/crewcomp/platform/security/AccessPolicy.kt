package au.crewcomp.platform.security

import jakarta.enterprise.context.ApplicationScoped

/**
 * AUTH-1/AUTH-2/AUTH-3 — the single place that answers "what may this actor see and do?".
 *
 * Rules of the road:
 *  - Endpoints and MCP tools call [require] for authorisation and never re-derive a scope.
 *  - Reads of person-scoped data go through [scope]; the Hibernate `crewScope` filter
 *    (see `CrewScopeFilter`) applies it to every query as defence in depth.
 *  - A load by primary key is **not** covered by the Hibernate filter, so anything fetched that
 *    way must be checked with [assertCanSeePerson] / [assertCanSeePartnership].
 */
@ApplicationScoped
class AccessPolicy(private val actorContext: ActorContext) {

    fun actor(): Actor = actorContext.require()

    /**
     * The current actor's read scope. Back-office roles read everything; a Vessel Master is
     * limited to their partnerships; a crew member sees only their own person record.
     *
     * A user holding both a back-office role and Crew Member gets the wider scope — that is the
     * intended reading of "roles are assignable per user; one user may hold several" (§3).
     */
    fun scope(): DataScope {
        val actor = actorContext.currentOrNull() ?: return DataScope.None

        if (actor.kind == ActorKind.SYSTEM) return DataScope.All
        if (actor.roles.any { it in Role.UNRESTRICTED_READERS }) return DataScope.All

        if (actor.hasRole(Role.VESSEL_MASTER) && actor.partnershipIds.isNotEmpty()) {
            // Their own person record travels with the scope — see [DataScope.Partnerships].
            // A Vessel Master is a crew member who supervises, so the same account holds both
            // roles and both readings have to be true at once.
            return DataScope.Partnerships(actor.partnershipIds, actor.personId)
        }
        if (actor.hasRole(Role.CREW_MEMBER) && actor.personId != null) {
            return DataScope.OwnPersonOnly(actor.personId)
        }
        return DataScope.None
    }

    /** AUTH-1: server-side role check. Throws rather than returning a boolean, so that a
     *  forgotten `if` cannot silently permit the call. */
    fun require(vararg allowed: Role) {
        val actor = actor()
        if (actor.kind == ActorKind.SYSTEM) return
        if (allowed.none { it in actor.roles }) {
            throw AccessDeniedException(
                "${actor.label} holds ${actor.roles.map { it.wire }} but this action requires " +
                    "one of ${allowed.map { it.wire }}",
            )
        }
    }

    /** True when the actor may read data belonging to [personId]. */
    fun canSeePerson(personId: Long, personPartnershipId: Long? = null): Boolean =
        when (val scope = scope()) {
            is DataScope.All -> true
            is DataScope.OwnPersonOnly -> scope.personId == personId
            is DataScope.Partnerships -> personId == scope.ownPersonId ||
                (personPartnershipId != null && personPartnershipId in scope.partnershipIds)
            is DataScope.None -> false
        }

    fun assertCanSeePerson(personId: Long, personPartnershipId: Long? = null) {
        if (!canSeePerson(personId, personPartnershipId)) {
            throw AccessDeniedException("Not permitted to access person $personId")
        }
    }

    fun canSeePartnership(partnershipId: Long): Boolean = when (val scope = scope()) {
        is DataScope.All -> true
        is DataScope.Partnerships -> partnershipId in scope.partnershipIds
        // A crew member's partnership visibility follows their own person record, which the
        // caller resolves; a bare partnership query is not something they may run.
        is DataScope.OwnPersonOnly -> false
        is DataScope.None -> false
    }

    fun assertCanSeePartnership(partnershipId: Long) {
        if (!canSeePartnership(partnershipId)) {
            throw AccessDeniedException("Not permitted to access partnership $partnershipId")
        }
    }

    /**
     * Crew members may never write business data directly — evidence submission is the only
     * client-originated write, and it goes through the pipeline (§7.5, MOB-4).
     */
    fun assertNotReadOnlyActor() {
        val actor = actor()
        if (actor.kind == ActorKind.SYSTEM) return
        if (actor.hasRole(Role.VESSEL_MASTER) && actor.roles.size == 1) {
            throw AccessDeniedException("Vessel Master is a read-only role")
        }
        if (actor.hasRole(Role.CREW_MEMBER) && actor.roles.size == 1) {
            throw AccessDeniedException("Crew members may not write business data directly")
        }
    }
}
