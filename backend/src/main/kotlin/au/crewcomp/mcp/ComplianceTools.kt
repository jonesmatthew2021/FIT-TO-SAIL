package au.crewcomp.mcp

import au.crewcomp.api.toDto
import au.crewcomp.compliance.ComplianceService
import au.crewcomp.engine.HoldingStatus
import au.crewcomp.people.HoldingService
import au.crewcomp.platform.time.BusinessClock
import com.fasterxml.jackson.databind.ObjectMapper
import io.quarkiverse.mcp.server.Tool
import io.quarkiverse.mcp.server.ToolArg
import jakarta.enterprise.context.ApplicationScoped
import java.time.LocalDate

/**
 * The embedded MCP server's tool surface (spec §13.1) — a module of the monolith, not a service
 * (MCP-5).
 *
 * **MCP-2 is the whole design constraint:** every tool below calls the *same* [ComplianceService]
 * and [HoldingService] the REST API calls. Nothing here touches a repository, let alone the
 * database. That is precisely the testing value — an agent driving these tools exercises the
 * real validation, authorisation and audit paths — and it means agent-driven mutations land in
 * the audit trail under an `ai_automatic` actor (AIA-3).
 *
 * A tool that reached past the service layer would be a defect, not an optimisation.
 */
@ApplicationScoped
class ComplianceTools(
    private val compliance: ComplianceService,
    private val holdings: HoldingService,
    private val clock: BusinessClock,
    private val guard: McpGuard,
    private val json: ObjectMapper,
) {

    @Tool(description = "Evaluate a whole swing's compliance (spec §5.3): per-assignment cell states, open slots, quota results.")
    fun evaluateSwing(
        @ToolArg(description = "Partnership abbreviation, e.g. NOR, SIN, OPT, UNI, SMG") partnership: String,
        @ToolArg(description = "Crew change id, e.g. CC24") cc: String,
        @ToolArg(description = "Optional matrix version id to pin; defaults to the current published version", required = false) matrixVersionId: Long?,
    ): String = guard.read {
        json.writeValueAsString(compliance.evaluateSwing(partnership, cc, matrixVersionId).toDto())
    }

    @Tool(description = "Gap report for a swing (spec §5.4): every non-ok/na cell, ordered gap → expiring → unknown → review → pending → exempt.")
    fun swingGaps(
        @ToolArg(description = "Partnership abbreviation") partnership: String,
        @ToolArg(description = "Crew change id, e.g. CC24") cc: String,
    ): String = guard.read {
        json.writeValueAsString(compliance.gapReport(partnership, cc).map { it.toDto() })
    }

    @Tool(description = "Quota rule results for a swing (spec §5.3), evaluated per shift where the rule is shift-scoped.")
    fun swingQuotas(
        @ToolArg(description = "Partnership abbreviation") partnership: String,
        @ToolArg(description = "Crew change id, e.g. CC24") cc: String,
    ): String = guard.read {
        json.writeValueAsString(compliance.quotas(partnership, cc).map { it.toDto() })
    }

    @Tool(description = "Ranked crew suggestions for an open slot (spec §5.4). Clashing candidates are included and scored, not hidden.")
    fun suggestCrew(
        @ToolArg(description = "Partnership abbreviation") partnership: String,
        @ToolArg(description = "Crew change id, e.g. CC24") cc: String,
        @ToolArg(description = "Position slot ref, 1–17") slotRef: Int,
        @ToolArg(description = "Maximum suggestions to return", required = false) limit: Int?,
    ): String = guard.read {
        json.writeValueAsString(
            compliance.suggestions(partnership, cc, slotRef, limit = limit ?: 20).map { it.toDto() },
        )
    }

    @Tool(description = "One person's compliance evaluation against a swing (spec §5.2).")
    fun evaluatePerson(
        @ToolArg(description = "Person id") personId: Long,
        @ToolArg(description = "Partnership abbreviation") partnership: String,
        @ToolArg(description = "Crew change id, e.g. CC24") cc: String,
    ): String = guard.read {
        json.writeValueAsString(compliance.evaluatePerson(personId, partnership, cc).toDto())
    }

    @Tool(description = "Holdings expiring within a lead window, classified by impact on the person's next assignment (spec §5.4).")
    fun expiryAlerts(
        @ToolArg(description = "Lead window in days; defaults to 90", required = false) leadDays: Long?,
    ): String = guard.read {
        json.writeValueAsString(compliance.expiryAlerts(leadDays ?: 90).map { it.toDto() })
    }

    @Tool(description = "The current business date, and whether an admin date override is active (spec §1).")
    fun businessDate(): String = guard.read {
        json.writeValueAsString(
            mapOf(
                "today" to clock.today().toString(),
                "realToday" to clock.realToday().toString(),
                "timezone" to clock.zone.id,
                "overridden" to clock.isOverridden,
            ),
        )
    }

    // -----------------------------------------------------------------------
    // Write tools — MCP-1's "set up a scenario, exercise it, inspect the outcome".
    // Disabled in production, and off unless explicitly enabled (MCP-3).
    // -----------------------------------------------------------------------

    @Tool(description = "Set a person's qualification holding. Goes through the same validated, audited service as the API (MCP-2); the audit event records an AI-automatic actor.")
    fun setHolding(
        @ToolArg(description = "Person id") personId: Long,
        @ToolArg(description = "Requirement id") requirementId: Long,
        @ToolArg(description = "Holding status: held_expiry | held_perpetual | not_held | unknown") status: String,
        @ToolArg(description = "Expiry date (YYYY-MM-DD); required for held_expiry", required = false) expiry: String?,
        @ToolArg(description = "Issue date (YYYY-MM-DD)", required = false) issueDate: String?,
        @ToolArg(description = "Free-text note", required = false) note: String?,
    ): String = guard.write {
        val holding = holdings.setHolding(
            personId = personId,
            requirementId = requirementId,
            status = HoldingStatus.fromWire(status),
            expiry = expiry?.let { LocalDate.parse(it) },
            issueDate = issueDate?.let { LocalDate.parse(it) },
            note = note,
        )
        json.writeValueAsString(
            mapOf(
                "id" to holding.requiredId,
                "status" to holding.status.wire,
                "expiry" to holding.expiryDate?.toString(),
            ),
        )
    }

    @Tool(description = "Set or clear the admin date override, so a scenario can be exercised against a chosen 'today' (spec §1). Refused in production.")
    fun setBusinessDate(
        @ToolArg(description = "Date (YYYY-MM-DD), or omit to clear the override", required = false) date: String?,
    ): String = guard.write {
        clock.overrideToday(date?.let { LocalDate.parse(it) })
        json.writeValueAsString(
            mapOf("today" to clock.today().toString(), "overridden" to clock.isOverridden),
        )
    }
}
