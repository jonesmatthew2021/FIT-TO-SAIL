package au.crewcomp.platform.security.oidc

import au.crewcomp.platform.security.AccessDeniedException
import au.crewcomp.platform.security.Actor
import au.crewcomp.platform.security.ActorContext
import au.crewcomp.platform.security.dev.DevAuth
import io.quarkus.security.identity.SecurityIdentity
import jakarta.annotation.Priority
import jakarta.ws.rs.Priorities
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.ext.Provider
import org.eclipse.microprofile.jwt.JsonWebToken

/**
 * Populates the request's [ActorContext] from the verified OIDC token.
 *
 * Runs after Quarkus has authenticated the request, and does nothing for anonymous ones: public
 * endpoints (health, OpenAPI) simply have no actor, and any protected code path then fails with
 * `NotAuthenticatedException` rather than with a half-populated identity.
 *
 * The token is read from [SecurityIdentity] rather than injected as a `JsonWebToken` bean. That
 * bean exists only while the OIDC extension is enabled, so injecting it directly makes the whole
 * application fail to start under `quarkus.oidc.enabled=false` — which is every test profile, and
 * local development before a corporate IdP is configured.
 */
@Provider
@Priority(Priorities.AUTHENTICATION + 100)
class ActorResolutionFilter(
    private val identity: SecurityIdentity,
    private val resolver: ActorResolver,
    private val actorContext: ActorContext,
) : ContainerRequestFilter {

    override fun filter(requestContext: ContainerRequestContext) {
        if (identity.isAnonymous) return

        // The local-development shim resolves its own actor. The attribute can only have been
        // set by a bean that a production build does not contain (see DevAuth), so honouring it
        // here cannot weaken the corporate path below.
        identity.getAttribute<Actor>(DevAuth.ACTOR_ATTRIBUTE)?.let { devActor ->
            actorContext.set(devActor)
            return
        }

        // SEC-1: corporate SSO is the sole authentication path, so an authenticated request that
        // arrived without an ID token is a misconfiguration. Fail closed and name the problem,
        // rather than proceeding with no actor and a confusing downstream 401.
        val token = identity.principal as? JsonWebToken
            ?: throw AccessDeniedException(
                "Authenticated without a corporate ID token (principal: " +
                    "${identity.principal?.javaClass?.simpleName}). Corporate SSO is the only " +
                    "supported authentication path (SEC-1).",
            )

        actorContext.set(resolver.resolve(token))
    }
}
