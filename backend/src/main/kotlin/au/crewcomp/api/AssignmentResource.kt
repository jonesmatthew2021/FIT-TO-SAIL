package au.crewcomp.api

import au.crewcomp.people.AssignmentService
import io.quarkus.security.Authenticated
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.tags.Tag

/**
 * ADM-2's write half: filling and clearing a slot.
 *
 * Rooted at `/api/v1` alongside [ComplianceResource] and [ReferenceResource] rather than at
 * `/api/v1/swings`. A root resource with the longer prefix would capture every `/swings/…`
 * request and make the evaluation endpoints on the shorter one unreachable — the same JAX-RS
 * resolution rule that put the person evaluation inside [PeopleResource].
 */
@Path("/api/v1")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Assignments", description = "Roster assignment write path (ADM-2)")
class AssignmentResource(private val assignments: AssignmentService) {

    @POST
    @Path("/swings/{partnership}/{cc}/assignments")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Assign a person to a slot of a swing — audited (§4.3, AUTH-3)")
    fun assign(
        @PathParam("partnership") partnership: String,
        @PathParam("cc") cc: String,
        request: AssignRequest,
    ): AssignmentDto = assignments.assign(
        partnershipAbbrev = partnership,
        ccId = cc,
        slotRef = request.slotRef,
        personId = request.personId,
        from = request.from,
        to = request.to,
        acknowledgeClash = request.acknowledgeClash,
    ).toDto()

    @DELETE
    @Path("/assignments/{assignmentId}")
    @Operation(summary = "Remove an assignment — audited, and tombstoned to the crew device")
    fun unassign(@PathParam("assignmentId") assignmentId: Long): Response {
        assignments.unassign(assignmentId)
        return Response.noContent().build()
    }
}
