package au.crewcomp.mcp

import io.quarkus.vertx.http.runtime.filters.Filters
import io.vertx.core.http.HttpServerRequest
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.security.MessageDigest
import java.util.Optional

/**
 * MCP-4 — token authentication on the MCP endpoint, in **every** environment.
 *
 * "An unauthenticated MCP endpoint with write tools is a remote-code-execution-equivalent hole
 * and is prohibited in every environment." That includes a developer laptop, so this is
 * implemented in code rather than left to per-profile HTTP permission configuration, where a
 * single `%dev` override would quietly disable it.
 *
 * The check is a constant-time comparison against a token supplied by the environment or the
 * managed secret store (SEC-9); [McpConfigurationCheck] refuses to start if MCP is enabled
 * without one.
 */
@ApplicationScoped
class McpHttpAuthentication(
    @ConfigProperty(name = "crewcomp.mcp.enabled", defaultValue = "false")
    private val enabled: Boolean,
    @ConfigProperty(name = "crewcomp.mcp.token")
    private val token: Optional<String>,
) {

    fun register(@Observes filters: Filters) {
        filters.register(
            { context ->
                if (!isMcpPath(context.request())) {
                    context.next()
                } else if (!enabled) {
                    context.response().setStatusCode(404).end()
                } else if (!isAuthorised(context.request())) {
                    context.response()
                        .setStatusCode(401)
                        .putHeader("WWW-Authenticate", "Bearer realm=\"crewcomp-mcp\"")
                        .end()
                } else {
                    context.next()
                }
            },
            FILTER_PRIORITY,
        )
    }

    private fun isMcpPath(request: HttpServerRequest): Boolean {
        val path = request.path() ?: return false
        return path == MCP_ROOT || path.startsWith("$MCP_ROOT/")
    }

    private fun isAuthorised(request: HttpServerRequest): Boolean {
        val expected = token.orElse(null) ?: return false
        val header = request.getHeader("Authorization") ?: return false
        if (!header.startsWith(BEARER_PREFIX, ignoreCase = true)) return false

        val presented = header.substring(BEARER_PREFIX.length).trim()
        return MessageDigest.isEqual(
            presented.toByteArray(Charsets.UTF_8),
            expected.toByteArray(Charsets.UTF_8),
        )
    }

    companion object {
        const val MCP_ROOT = "/mcp"
        private const val BEARER_PREFIX = "Bearer "

        /** Ahead of the application's routes, so an unauthenticated request never reaches a tool. */
        private const val FILTER_PRIORITY = 500
    }
}
