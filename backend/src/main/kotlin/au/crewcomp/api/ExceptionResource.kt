package au.crewcomp.api

import au.crewcomp.workflow.ExceptionService
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
 * ADM-7 — the data-quality worklist (§4.4, §11).
 *
 * Rooted at `/api/v1` for the JAX-RS reason documented on [ComplianceResource]: a root resource
 * at `/api/v1/exceptions` would capture everything beneath that prefix and nothing else could
 * serve a sub-path of it.
 */
@Path("/api/v1")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Exceptions", description = "Data-quality worklist and the unknown-holdings chase list")
class ExceptionResource(private val exceptions: ExceptionService) {

    @GET
    @Path("/exceptions")
    @Operation(summary = "The worklist. `state` is open, resolved, or omitted for both")
    fun list(@QueryParam("state") state: String?): List<ExceptionItemDto> =
        exceptions.list(state?.ifBlank { null }).map { it.toDto() }

    @GET
    @Path("/exceptions/unknown-holdings")
    @Operation(summary = "Q11's standing chase list: holdings whose status was never established")
    fun unknownHoldings(): List<UnknownHoldingDto> =
        exceptions.unknownHoldings().map { it.toUnknownDto() }

    @POST
    @Path("/exceptions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Raise a data-quality item — audited")
    fun raise(request: RaiseExceptionRequest): ExceptionItemDto = exceptions.raise(
        area = request.area,
        description = request.description,
        linkedEntityType = request.linkedEntityType,
        linkedEntityId = request.linkedEntityId,
    ).toDto()

    @POST
    @Path("/exceptions/{exceptionItemId}/resolve")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Close an item with the note saying what was done — audited")
    fun resolve(
        @PathParam("exceptionItemId") exceptionItemId: Long,
        request: ResolveExceptionRequest,
    ): ExceptionItemDto = exceptions.resolve(exceptionItemId, request.note).toDto()

    @POST
    @Path("/exceptions/{exceptionItemId}/reopen")
    @Operation(summary = "Reopen an item resolved in error — audited")
    fun reopen(@PathParam("exceptionItemId") exceptionItemId: Long): ExceptionItemDto =
        exceptions.reopen(exceptionItemId).toDto()
}
