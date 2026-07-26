package au.crewcomp.api

import au.crewcomp.engine.RegisterOutcome
import au.crewcomp.workflow.ConditionType
import au.crewcomp.workflow.RegisterService
import au.crewcomp.workflow.RegisterStatus
import au.crewcomp.workflow.RegisterType
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
 * ADM-4 — the exemption and query register (§4.4).
 *
 * Records are addressed by their **business key** (`UNICC24-3`), not by surrogate id: it is what
 * appears on a compliance cell, in a CSV export and in every conversation about a request, so it
 * is what a URL should carry. The surrogate key stays where it belongs — in foreign keys.
 *
 * Rooted at `/api/v1` for the JAX-RS reason documented on [ComplianceResource].
 */
@Path("/api/v1")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Register", description = "Exemption requests and queries, and their workflow")
class RegisterResource(private val register: RegisterService) {

    @GET
    @Path("/register")
    @Operation(summary = "The register, filtered. `state` is open, closed, or omitted for all")
    fun search(
        @QueryParam("partnership") partnership: String?,
        @QueryParam("cc") cc: String?,
        @QueryParam("type") type: String?,
        @QueryParam("state") state: String?,
    ): List<RegisterRecordDto> =
        register.search(partnership, cc, type, state?.ifBlank { null }).map { it.toDto() }

    @GET
    @Path("/register/next-id")
    @Operation(summary = "The next business key for a swing — the create form's preview (§6)")
    fun nextId(
        @QueryParam("partnership") partnership: String,
        @QueryParam("cc") cc: String,
    ): NextRecordIdDto = NextRecordIdDto(register.nextRecordId(partnership, cc))

    @GET
    @Path("/register/{recordId}")
    @Operation(summary = "One record with its conditions, party notes and human-readable trail")
    fun get(@PathParam("recordId") recordId: String): RegisterRecordDetailDto =
        register.get(recordId).toDetailDto()

    @POST
    @Path("/register")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Raise a request or query — auto-ID, window and cutoff validated, audited")
    fun create(request: CreateRegisterRecordRequest): RegisterRecordDetailDto = register.create(
        type = RegisterType.fromWire(request.type),
        partnershipAbbrev = request.partnership,
        ccId = request.cc,
        personId = request.personId,
        requirementId = request.requirementId,
        reqRaw = request.reqRaw,
        effectiveFrom = request.effectiveFrom,
        effectiveTo = request.effectiveTo,
        status = request.status?.let { RegisterStatus.fromWire(it) },
        acknowledgeLateSubmission = request.acknowledgeLateSubmission,
    ).toDetailDto()

    @POST
    @Path("/register/{recordId}/transition")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Move an open record between the open statuses — Workflow Manager only")
    fun transition(
        @PathParam("recordId") recordId: String,
        request: TransitionRegisterRecordRequest,
    ): RegisterRecordDetailDto =
        register.transition(recordId, RegisterStatus.fromWire(request.status)).toDetailDto()

    @POST
    @Path("/register/{recordId}/close")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Close with an outcome; an approval must carry its window — audited")
    fun close(
        @PathParam("recordId") recordId: String,
        request: CloseRegisterRecordRequest,
    ): RegisterRecordDetailDto = register.close(
        recordId = recordId,
        outcome = RegisterOutcome.fromWire(request.outcome),
        approvalFrom = request.approvalFrom,
        approvalTo = request.approvalTo,
        conditions = request.conditions.map { ConditionType.fromWire(it.type) to it.body },
        note = request.note,
    ).toDetailDto()

    @POST
    @Path("/register/{recordId}/notes")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Add a party-attributed note (§4.4). Append-only")
    fun addNote(
        @PathParam("recordId") recordId: String,
        request: AddNoteRequest,
    ): RegisterRecordDetailDto = register.addNote(recordId, request.party, request.body).toDetailDto()

    @POST
    @Path("/register/{recordId}/conditions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Attach a structured approval condition (Q16) — Workflow Manager only")
    fun addCondition(
        @PathParam("recordId") recordId: String,
        request: AddConditionRequest,
    ): RegisterRecordDetailDto =
        register.addCondition(recordId, ConditionType.fromWire(request.type), request.body).toDetailDto()
}
