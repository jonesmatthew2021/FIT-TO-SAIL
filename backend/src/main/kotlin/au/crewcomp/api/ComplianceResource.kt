package au.crewcomp.api

import au.crewcomp.compliance.ComplianceService
import io.quarkus.security.Authenticated
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.tags.Tag

/**
 * The swing-level derived reads that mirror the engine (§10.2).
 *
 * Every method here is a thin adapter: authorise, delegate to a service, map to a DTO. No
 * business logic lives in a resource — that is what lets the MCP tools in
 * [au.crewcomp.mcp.ComplianceTools] call the same services and get identical behaviour (MCP-2).
 *
 * Everything rooted at `/api/v1/people` — including a person's §5.2 evaluation — lives in
 * [PeopleResource] instead. That is not a style choice: JAX-RS selects **one** root resource
 * class by path prefix and then matches sub-paths only within it, so a `/people/…` method on a
 * class rooted at `/api/v1` is unreachable once a class rooted at `/api/v1/people` exists. It
 * answers 404, silently, with no start-up warning.
 */
@Path("/api/v1")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Compliance", description = "Engine-derived evaluations (spec §5)")
class ComplianceResource(private val compliance: ComplianceService) {

    @GET
    @Path("/swings/{partnership}/{cc}/evaluation")
    @Operation(summary = "Whole-swing compliance evaluation (§5.3)")
    fun swingEvaluation(
        @PathParam("partnership") partnership: String,
        @PathParam("cc") cc: String,
        @QueryParam("matrixVersionId") matrixVersionId: Long?,
    ): SwingEvaluationDto = compliance.evaluateSwing(partnership, cc, matrixVersionId).toDto()

    @GET
    @Path("/swings/{partnership}/{cc}/quotas")
    @Operation(summary = "Quota rule evaluation for a swing, per shift where scoped (§5.3)")
    fun quotas(
        @PathParam("partnership") partnership: String,
        @PathParam("cc") cc: String,
        @QueryParam("matrixVersionId") matrixVersionId: Long?,
    ): List<QuotaDto> = compliance.quotas(partnership, cc, matrixVersionId).map { it.toDto() }

    @GET
    @Path("/swings/{partnership}/{cc}/gaps")
    @Operation(summary = "Gap report: every non-ok/na cell, ordered as a worklist (§5.4)")
    fun gaps(
        @PathParam("partnership") partnership: String,
        @PathParam("cc") cc: String,
        @QueryParam("matrixVersionId") matrixVersionId: Long?,
    ): List<GapReportRowDto> = compliance.gapReport(partnership, cc, matrixVersionId).map { it.toDto() }

    @GET
    @Path("/swings/{partnership}/{cc}/suggestions")
    @Operation(summary = "Ranked crew suggestions for an open slot (§5.4)")
    fun suggestions(
        @PathParam("partnership") partnership: String,
        @PathParam("cc") cc: String,
        @QueryParam("slotRef") slotRef: Int,
        @QueryParam("limit") @DefaultValue("20") limit: Int,
    ): List<SuggestionDto> = compliance.suggestions(partnership, cc, slotRef, limit = limit).map { it.toDto() }

    @GET
    @Path("/expiry-alerts")
    @Operation(summary = "Holdings expiring within the lead window, by impact on next assignment (§5.4)")
    fun expiryAlerts(
        @QueryParam("leadDays") @DefaultValue("90") leadDays: Long,
    ): List<ExpiryAlertDto> = compliance.expiryAlerts(leadDays).map { it.toDto() }
}
