package au.crewcomp.mcp

import au.crewcomp.platform.security.Actor
import au.crewcomp.platform.security.ActorContext
import au.crewcomp.platform.security.ActorKind
import au.crewcomp.platform.security.Role
import io.quarkus.runtime.configuration.ConfigUtils
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty

/**
 * MCP-3 / MCP-4 — the gate every MCP tool passes through before doing anything.
 *
 * Two separate concerns, both enforced here rather than in each tool:
 *
 *  1. **Environment gating (MCP-3).** The MCP surface is enabled in development and staging and
 *     **disabled in production by default**. Enabling it in production is an explicit,
 *     configured act; until O-18 settles the posture, this refuses write tools there outright.
 *
 *  2. **Actor identity (MCP-2 / AIA-3).** An MCP caller is an *agent*, and its mutations must be
 *     attributable as such in the audit trail. Every tool call runs under an `ai_automatic`
 *     actor, so an agent-driven change is never indistinguishable from a human one.
 *
 * Transport authentication (MCP-4) is enforced by the HTTP layer — the MCP endpoint sits behind
 * a required-authentication permission in `application.properties` and is bound to a private
 * interface. An unauthenticated MCP endpoint with write tools is remote-code-execution-equivalent
 * and is prohibited in every environment, so both halves are asserted at start-up by
 * [McpConfigurationCheck].
 */
@ApplicationScoped
class McpGuard(
    private val actorContext: ActorContext,
    @ConfigProperty(name = "crewcomp.mcp.enabled", defaultValue = "false")
    private val enabled: Boolean,
    @ConfigProperty(name = "crewcomp.mcp.agent-label", defaultValue = "MCP agent")
    private val agentLabel: String,
    @ConfigProperty(name = "crewcomp.mcp.allow-writes", defaultValue = "false")
    private val allowWrites: Boolean,
) {

    /** Establishes the agent actor for a read-only tool call. */
    fun <T> read(block: () -> T): T {
        assertEnabled()
        return actorContext.runAs(agentActor(), block)
    }

    /**
     * Establishes the agent actor for a tool call that mutates state. Refused in production,
     * and refused anywhere `crewcomp.mcp.allow-writes` is not explicitly on.
     */
    fun <T> write(block: () -> T): T {
        assertEnabled()
        if (isProduction()) {
            throw IllegalStateException(
                "MCP write tools are disabled in production (MCP-3). Production enablement is " +
                    "read-only diagnostics at most, and is an explicit, audited, time-boxed act.",
            )
        }
        if (!allowWrites) {
            throw IllegalStateException(
                "MCP write tools are disabled. Set crewcomp.mcp.allow-writes=true to enable them " +
                    "in a development or staging environment.",
            )
        }
        return actorContext.runAs(agentActor(), block)
    }

    private fun assertEnabled() {
        if (!enabled) {
            throw IllegalStateException("The MCP server is disabled (crewcomp.mcp.enabled=false)")
        }
        if (isProduction() && !enabled) {
            throw IllegalStateException("The MCP server is disabled in production by default (MCP-3)")
        }
    }

    /**
     * The agent's identity. It is given the roles it needs to exercise real code paths — the
     * point of MCP-2 is that agent-driven mutations go through the same validation and
     * authorisation as everyone else's, so the agent is a privileged *user*, not a bypass.
     */
    private fun agentActor(): Actor = Actor(
        userAccountId = null,
        personId = null,
        roles = setOf(
            Role.SYSTEM_ADMINISTRATOR, Role.CREW_COORDINATOR, Role.DATA_STEWARD,
            Role.COMPLIANCE_LEAD, Role.WORKFLOW_MANAGER,
        ),
        kind = ActorKind.AI_AUTOMATIC,
        label = agentLabel,
    )

    private fun isProduction(): Boolean = ConfigUtils.getProfiles().contains("prod")
}
