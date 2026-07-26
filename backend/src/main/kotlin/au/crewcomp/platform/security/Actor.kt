package au.crewcomp.platform.security

import jakarta.enterprise.context.RequestScoped

/**
 * The authenticated actor behind the current unit of work.
 *
 * Every business mutation records this (AUTH-3), and [kind] is what lets the audit trail
 * distinguish human from AI-proposed-human-approved from AI-automatic (AIA-3).
 */
data class Actor(
    /** `null` only for [ActorKind.SYSTEM] actors such as scheduled jobs and migrations. */
    val userAccountId: Long?,
    /** Set for crew members; `null` for back-office users with no Person record (§4.3). */
    val personId: Long?,
    val roles: Set<Role>,
    val kind: ActorKind,
    /**
     * Human-readable identification, denormalised into every audit event so that the event
     * stays readable after the account changes (§4.4 self-containment).
     */
    val label: String,
    /** Partnerships a Vessel Master is scoped to (§3). Empty for every other role. */
    val partnershipIds: Set<Long> = emptySet(),
    /** AIA-3: recorded on AI-touched events. */
    val aiModel: String? = null,
    val aiConfigVersion: String? = null,
) {
    fun hasRole(role: Role): Boolean = role in roles
    fun hasAnyRole(vararg candidates: Role): Boolean = candidates.any { it in roles }

    companion object {
        /**
         * The actor for scheduled jobs, Flyway-adjacent data fix-ups and other unattended work.
         * Never used for anything a person or an AI initiated.
         */
        fun system(label: String): Actor = Actor(
            userAccountId = null,
            personId = null,
            roles = emptySet(),
            kind = ActorKind.SYSTEM,
            label = label,
        )
    }
}

/**
 * Request-scoped holder for the current [Actor], populated once by the authentication layer
 * (OIDC for API callers, the token-authenticated MCP transport for agent callers) and read
 * everywhere else.
 */
@RequestScoped
class ActorContext {
    private var current: Actor? = null

    fun set(actor: Actor) {
        current = actor
    }

    fun currentOrNull(): Actor? = current

    fun require(): Actor = current ?: throw NotAuthenticatedException()

    /** Runs [block] as [actor]. Used by jobs and tests; never reachable from a request path. */
    fun <T> runAs(actor: Actor, block: () -> T): T {
        val previous = current
        current = actor
        try {
            return block()
        } finally {
            current = previous
        }
    }
}

class NotAuthenticatedException(message: String = "No authenticated actor") : RuntimeException(message)

class AccessDeniedException(message: String) : RuntimeException(message)
