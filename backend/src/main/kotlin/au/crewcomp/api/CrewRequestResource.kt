package au.crewcomp.api

import au.crewcomp.people.CrewStatementService
import au.crewcomp.people.CrewStatementStatus
import io.quarkus.security.Authenticated
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.tags.Tag

/**
 * ADM-11 — the crew request queue.
 *
 * What the crew app's one-tap answers land in, so that "I have booked the course" reaches somebody
 * whose job it is to do something about it rather than only a notification that one person marks
 * read. §6 enumerates ADM-1 to ADM-10 and stops; this module post-dates the spec and the mobile
 * design handoff is what asks for it — see `docs/handoff/mobile-crew-app-backend.md` §1.1.
 *
 * Rooted at `/api/v1` for the JAX-RS reason documented on [ComplianceResource]: a root resource at
 * `/api/v1/crew-requests` would capture everything beneath that prefix.
 */
@Path("/api/v1")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Crew requests", description = "ADM-11 — what the crew app's one-tap answers land in")
class CrewRequestResource(private val statements: CrewStatementService) {

    @GET
    @Path("/crew-requests")
    @Operation(summary = "The queue. `status` is open, actioned, dismissed, or omitted for all")
    fun list(@QueryParam("status") status: String?): List<CrewRequestDto> = statements
        .queue(status?.ifBlank { null }?.let { CrewStatementStatus.fromWire(it) })
        .map { it.toDto() }

    @GET
    @Path("/crew-requests/open-count")
    @Operation(summary = "How many requests nobody has dealt with — the nav badge")
    fun openCount(): CrewRequestSummaryDto = CrewRequestSummaryDto(statements.openCount())

    @POST
    @Path("/crew-requests/{crewRequestId}/action")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Confirmed / arranged, with the note saying what was done — audited")
    fun action(
        @PathParam("crewRequestId") crewRequestId: Long,
        request: DecideCrewRequestRequest,
    ): CrewRequestDto = statements.action(crewRequestId, request.note).toDto()

    @POST
    @Path("/crew-requests/{crewRequestId}/dismiss")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Nothing to do, or unconfirmable — audited. Resumes expiry chasing for a booking",
    )
    fun dismiss(
        @PathParam("crewRequestId") crewRequestId: Long,
        request: DecideCrewRequestRequest,
    ): CrewRequestDto = statements.dismiss(crewRequestId, request.note).toDto()
}
