package au.crewcomp.api

import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.time.BusinessClock
import io.quarkus.security.Authenticated
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.tags.Tag

/**
 * Who the caller is, and what "today" is.
 *
 * The admin SPA calls this once on load: it is how the app learns its roles (for defence-in-depth
 * UI gating, AUTH-1) and the operating-timezone business date (NFR-5). A 401 here is the SPA's
 * signal to send the user to sign in.
 *
 * There is no login or logout endpoint yet. Under ADR 0003 those belong to the BFF session layer
 * — the server-side code flow, the opaque `HttpOnly` cookie, the token store — which is the
 * identity spike's deliverable. This endpoint is the half of the contract that does not depend
 * on how the session was established, so it is stable across that work.
 */
@Path("/api/v1/session")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Session", description = "The authenticated actor and the business date")
class SessionResource(
    private val policy: AccessPolicy,
    private val clock: BusinessClock,
    private val accounts: au.crewcomp.people.UserAccountRepository,
    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "crewcomp.dev-auth.enabled", defaultValue = "false")
    private val devAuth: Boolean,
) {

    @GET
    @Operation(summary = "The current actor, their roles, and the server's business date")
    fun current(@jakarta.ws.rs.core.Context headers: jakarta.ws.rs.core.HttpHeaders): SessionDto {
        val actor = policy.actor()
        // The company the account works for (BUS-2). A signed-in account carries it; under the
        // development shim, which has no account, the X-Dev-Customer header stands in. A display
        // hint only — what the caller may read is decided by the partnership scope, never by this.
        val customerId = actor.userAccountId?.let { accounts.findById(it)?.customerId }
            ?: headers.getHeaderString("X-Dev-Customer")?.toLongOrNull()?.takeIf { devAuth }
        return actor.toSessionDto(clock.today(), clock.isOverridden, customerId)
    }
}
