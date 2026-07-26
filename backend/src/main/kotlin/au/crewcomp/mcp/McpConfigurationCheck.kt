package au.crewcomp.mcp

import io.quarkus.runtime.StartupEvent
import io.quarkus.runtime.configuration.ConfigUtils
import jakarta.enterprise.event.Observes
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.ConfigProvider
import org.jboss.logging.Logger

/**
 * Fails start-up rather than serving an unsafe MCP configuration.
 *
 * MCP-4 calls an unauthenticated MCP endpoint with write tools "remote-code-execution-equivalent
 * and prohibited in every environment". A misconfiguration that leaves it open is therefore not
 * something to warn about in a log nobody reads — the application refuses to start.
 */
@ApplicationScoped
class McpConfigurationCheck {

    private val log = Logger.getLogger(McpConfigurationCheck::class.java)

    fun onStart(@Observes event: StartupEvent) {
        val config = ConfigProvider.getConfig()
        val enabled = config.getOptionalValue("crewcomp.mcp.enabled", Boolean::class.java).orElse(false)
        if (!enabled) return

        val production = ConfigUtils.getProfiles().contains("prod")
        val writesAllowed = config.getOptionalValue("crewcomp.mcp.allow-writes", Boolean::class.java).orElse(false)

        // MCP-3: disabled in production by default; any production enablement is explicit,
        // audited, time-boxed, and read-only diagnostics at most (posture pending O-18).
        check(!(production && writesAllowed)) {
            "MCP write tools may not be enabled in production (MCP-3)."
        }

        // MCP-4: authenticated in every environment. McpHttpAuthentication enforces the bearer
        // token; without a configured token it would reject everything, so fail loudly instead
        // of leaving a developer to debug 401s.
        val token = config.getOptionalValue("crewcomp.mcp.token", String::class.java).orElse(null)
        check(!token.isNullOrBlank()) {
            "The MCP server is enabled but crewcomp.mcp.token is not set. MCP-4 requires token " +
                "authentication in every environment, including development."
        }
        check(token.length >= MINIMUM_TOKEN_LENGTH) {
            "crewcomp.mcp.token must be at least $MINIMUM_TOKEN_LENGTH characters."
        }

        val host = config.getOptionalValue("quarkus.http.host", String::class.java).orElse("0.0.0.0")
        if (host == "0.0.0.0" && !production) {
            log.warnf(
                "The MCP server is enabled while HTTP is bound to %s. MCP-4 expects a " +
                    "localhost/private-network binding or an authenticated tunnel.",
                host,
            )
        }

        log.infof(
            "MCP server enabled (writes=%s, profile=%s)",
            writesAllowed,
            ConfigUtils.getProfiles().joinToString(","),
        )
    }

    private companion object {
        const val MINIMUM_TOKEN_LENGTH = 32
    }
}
