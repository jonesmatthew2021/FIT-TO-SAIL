package au.crewcomp.platform.security.dev

import au.crewcomp.platform.security.Actor
import au.crewcomp.platform.security.ActorKind
import au.crewcomp.platform.security.Role
import io.quarkus.arc.properties.IfBuildProperty
import io.quarkus.runtime.StartupEvent
import io.quarkus.runtime.configuration.ConfigUtils
import io.quarkus.security.identity.IdentityProviderManager
import io.quarkus.security.identity.SecurityIdentity
import io.quarkus.security.identity.request.AuthenticationRequest
import io.quarkus.security.runtime.QuarkusPrincipal
import io.quarkus.security.runtime.QuarkusSecurityIdentity
import io.quarkus.vertx.http.runtime.security.ChallengeData
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism
import io.smallrye.mutiny.Uni
import io.vertx.ext.web.RoutingContext
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.util.Optional

/**
 * The local-development authentication shim.
 *
 * ADR 0003 gives the admin SPA a BFF session: the OIDC code flow runs server-side, tokens never
 * reach the browser, and the browser holds an opaque `HttpOnly` cookie. That layer is the
 * identity spike's deliverable and needs a real corporate IdP to build against. Until it exists
 * there is no way to authenticate a request locally at all — `quarkus.oidc.enabled=false` in dev
 * and test — so every `@Authenticated` endpoint answers 401 and the SPA cannot be developed.
 *
 * This fills that hole, and is deliberately built so it cannot survive into production:
 *
 *  - [IfBuildProperty] removes the bean at **build** time. `crewcomp.dev-auth.enabled` is true
 *    only under the `dev` and `test` profiles, so a production artefact — JVM or native — does
 *    not contain this mechanism at all. It is absent, not disabled.
 *  - [DevAuthConfigurationCheck] fails start-up if it is somehow enabled under `prod`.
 *  - It asks for no password. A shim with a fake credential invites being mistaken for
 *    authentication; one that plainly trusts a request header cannot be.
 *
 * The actor is carried to [au.crewcomp.platform.security.oidc.ActorResolutionFilter] as a
 * [SecurityIdentity] attribute, so the normal request path stays single-source: exactly one
 * place sets the [au.crewcomp.platform.security.ActorContext], whichever mechanism authenticated.
 */
object DevAuth {
    /** [SecurityIdentity] attribute under which the shim attaches a ready-made [Actor]. */
    const val ACTOR_ATTRIBUTE = "crewcomp.dev-actor"

    const val USER_HEADER = "X-Dev-User"
    const val ROLES_HEADER = "X-Dev-Roles"
    const val PERSON_HEADER = "X-Dev-Person-Id"
    const val PARTNERSHIPS_HEADER = "X-Dev-Partnerships"
}

@ApplicationScoped
@IfBuildProperty(name = "crewcomp.dev-auth.enabled", stringValue = "true")
class DevAuthenticationMechanism(
    /**
     * Optional rather than a defaulted String: SmallRye's String converter treats an explicitly
     * empty value as null and fails start-up, and "no default roles, so an unheadered request is
     * anonymous" is exactly what the test profile wants to say.
     */
    @ConfigProperty(name = "crewcomp.dev-auth.default-roles")
    private val defaultRoles: Optional<String>,
) : HttpAuthenticationMechanism {

    override fun authenticate(
        context: RoutingContext,
        identityProviderManager: IdentityProviderManager,
    ): Uni<SecurityIdentity> {
        val headers = context.request().headers()
        val roleList = (headers.get(DevAuth.ROLES_HEADER) ?: defaultRoles.orElse(""))
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // No roles named and no default configured means "not signed in": the request stays
        // anonymous and protected endpoints answer 401, exactly as they will in production.
        if (roleList.isEmpty()) return Uni.createFrom().nullItem()

        val roles = roleList.map { Role.fromWire(it) }.toSet()
        val label = headers.get(DevAuth.USER_HEADER)?.takeIf { it.isNotBlank() } ?: "Dev User"

        val actor = Actor(
            userAccountId = null,
            personId = headers.get(DevAuth.PERSON_HEADER)?.toLongOrNull(),
            roles = roles,
            kind = ActorKind.HUMAN,
            label = "$label <dev>",
            partnershipIds = (headers.get(DevAuth.PARTNERSHIPS_HEADER) ?: "")
                .split(',')
                .mapNotNull { it.trim().toLongOrNull() }
                .toSet(),
        )

        val identity = QuarkusSecurityIdentity.builder()
            .setPrincipal(QuarkusPrincipal(actor.label))
            .addRoles(roles.map { it.wire }.toSet())
            .addAttribute(DevAuth.ACTOR_ATTRIBUTE, actor)
            .build()

        return Uni.createFrom().item(identity)
    }

    override fun getChallenge(context: RoutingContext): Uni<ChallengeData> =
        Uni.createFrom().item(
            ChallengeData(401, "WWW-Authenticate", "X-Dev-Roles realm=\"crewcomp-dev\""),
        )

    override fun getCredentialTypes(): Set<Class<out AuthenticationRequest>> = emptySet()
}

/**
 * Belt and braces for the build-time removal above: if the shim is ever enabled under the
 * production profile, refuse to start rather than serve requests that anyone can authenticate.
 */
@ApplicationScoped
class DevAuthConfigurationCheck(
    @ConfigProperty(name = "crewcomp.dev-auth.enabled", defaultValue = "false")
    private val enabled: Boolean,
) {
    private val log = Logger.getLogger(DevAuthConfigurationCheck::class.java)

    fun onStart(@Observes event: StartupEvent) {
        if (!enabled) return
        if (ConfigUtils.getProfiles().contains("prod")) {
            throw IllegalStateException(
                "crewcomp.dev-auth.enabled is true under the production profile. The development " +
                    "authentication shim trusts request headers and must never run in production " +
                    "(SEC-1).",
            )
        }
        log.warn(
            "Development authentication shim is ACTIVE: any request naming roles in X-Dev-Roles " +
                "is authenticated. Local development only — replaced by the BFF session (ADR 0003).",
        )
    }
}
