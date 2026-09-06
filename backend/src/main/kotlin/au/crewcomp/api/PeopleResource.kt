package au.crewcomp.api

import au.crewcomp.compliance.ComplianceService
import au.crewcomp.engine.HoldingStatus
import au.crewcomp.evidence.EvidenceReviewService
import au.crewcomp.people.HoldingService
import au.crewcomp.people.PersonDirectoryService
import io.quarkus.security.Authenticated
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.tags.Tag

/**
 * §4.3 people-layer reads plus the holdings write path — ADM-5.
 *
 * The reads are row-scoped centrally (AUTH-2): nothing here passes a person filter, because the
 * scope is [PersonDirectoryService]'s to apply, not a caller's to remember. The write goes
 * through [HoldingService], the single validated door for holdings (AUTH-1), which audits it.
 *
 * The §5.2 person evaluation lives here rather than with the other engine reads in
 * [ComplianceResource] because JAX-RS resolves a request to one root resource class by path
 * prefix: anything under `/api/v1/people` must be a method of this class or it is unreachable.
 */
@Path("/api/v1/people")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "People", description = "Crew directory, holdings, assignments and leave")
class PeopleResource(
    private val directory: PersonDirectoryService,
    private val holdings: HoldingService,
    private val compliance: ComplianceService,
    private val evidence: EvidenceReviewService,
) {

    @GET
    @Operation(summary = "People visible to the caller (§3 scoping)")
    fun list(): List<PersonDto> = directory.list().map { it.toDto() }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Add a crew member — audited; refuses a re-used employee id")
    fun create(request: CreatePersonRequest): PersonDto = directory.create(
        name = request.name,
        sam = request.sam,
        positionId = request.positionId,
        partnershipId = request.partnershipId,
        email = request.email,
    ).toDto()

    @GET
    @Path("/{personId}/evidence")
    @Operation(summary = "A person's evidence documents — the certificates on file, newest first")
    fun evidence(@PathParam("personId") personId: Long): List<EvidenceDocumentDto> =
        evidence.forPerson(personId).map { it.toReviewDto() }

    @GET
    @Path("/{personId}")
    @Operation(summary = "One person")
    fun get(@PathParam("personId") personId: Long): PersonDto = directory.get(personId).toDto()

    @GET
    @Path("/{personId}/holdings")
    @Operation(summary = "A person's qualification holdings, by category then requirement code")
    fun holdings(@PathParam("personId") personId: Long): List<HoldingDto> =
        directory.holdingsFor(personId).map { it.toDto() }

    @GET
    @Path("/{personId}/assignments")
    @Operation(summary = "A person's assignment history, most recent first")
    fun assignments(@PathParam("personId") personId: Long): List<AssignmentDto> =
        directory.assignmentsFor(personId).map { it.toDto() }

    @GET
    @Path("/{personId}/leave")
    @Operation(summary = "A person's leave records, most recent first")
    fun leave(@PathParam("personId") personId: Long): List<LeaveRecordDto> =
        directory.leaveFor(personId).map { it.toDto() }

    @GET
    @Path("/{personId}/evaluation")
    @Operation(summary = "One person's evaluation against a swing (§5.2)")
    fun evaluation(
        @PathParam("personId") personId: Long,
        @QueryParam("partnership") partnership: String,
        @QueryParam("cc") cc: String,
        @QueryParam("matrixVersionId") matrixVersionId: Long?,
    ): PersonEvaluationDto = compliance.evaluatePerson(personId, partnership, cc, matrixVersionId).toDto()

    @PUT
    @Path("/{personId}/holdings/{requirementId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Set a person's holding for a requirement — audited (§4.3, AUTH-3)")
    fun setHolding(
        @PathParam("personId") personId: Long,
        @PathParam("requirementId") requirementId: Long,
        request: SetHoldingRequest,
    ): HoldingDto = holdings.setHolding(
        personId = personId,
        requirementId = requirementId,
        status = HoldingStatus.fromWire(request.status),
        expiry = request.expiry,
        issueDate = request.issueDate,
        note = request.note,
    ).toDto()
}
