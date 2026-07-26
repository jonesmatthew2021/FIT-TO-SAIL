package au.crewcomp.api

import au.crewcomp.compliance.ComplianceService
import au.crewcomp.rules.MatrixService
import io.quarkus.security.Authenticated
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.tags.Tag

/**
 * ADM-3 — matrix versions: list, draft, edit, diff, publish (§5.5, §10.2).
 *
 * Rooted at `/api/v1` rather than `/api/v1/matrix-versions` for the JAX-RS reason documented on
 * [ComplianceResource]: a longer-prefixed root resource captures every request beneath it, so
 * anything later added under `/matrix-versions` from another class would 404 silently.
 *
 * There is no endpoint here for "the generated matrix view per swing" that §6 also asks for. That
 * view is the §5.3 swing evaluation pivoted from crew × cells to crew × requirements, and it is
 * already served by `/swings/{pt}/{cc}/evaluation`. Adding a second endpoint that re-derived the
 * same cell states would be a second implementation of §5.1 in all but name (AUTH-1).
 */
@Path("/api/v1")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Matrix", description = "Matrix versions: draft, edit, diff, publish (§5.5)")
class MatrixResource(
    private val matrix: MatrixService,
    private val compliance: ComplianceService,
) {

    @GET
    @Path("/matrix-versions")
    @Operation(summary = "Every matrix version with its rule counts (ADM-3)")
    fun list(): List<MatrixVersionSummaryDto> = matrix.list().map { it.toDto() }

    @GET
    @Path("/matrix-versions/{versionId}")
    @Operation(summary = "One version's rules, for the cell editor")
    fun detail(@PathParam("versionId") versionId: Long): MatrixVersionDetailDto =
        matrix.detail(versionId).toDto()

    @GET
    @Path("/matrix-versions/{from}/diff/{to}")
    @Operation(summary = "§5.5 diff: added / removed / level-changed rules and quotas")
    fun diff(
        @PathParam("from") from: Long,
        @PathParam("to") to: Long,
    ): MatrixDiffDto = compliance.matrixDiff(from, to).toDto()

    @POST
    @Path("/matrix-versions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create a draft, optionally copied from any version — Compliance Lead, audited")
    fun createDraft(request: CreateMatrixDraftRequest): MatrixVersionDto =
        matrix.createDraft(
            label = request.label,
            copyFromVersionId = request.copyFromVersionId,
            notes = request.notes,
        ).toDto()

    @PUT
    @Path("/matrix-versions/{versionId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Rename a draft or edit its notes and tier footnote")
    fun updateDraft(
        @PathParam("versionId") versionId: Long,
        request: UpdateMatrixDraftRequest,
    ): MatrixVersionDto =
        matrix.updateDraft(versionId, request.label, request.notes, request.tierFootnote).toDto()

    @DELETE
    @Path("/matrix-versions/{versionId}")
    @Operation(summary = "Discard a draft and everything in it — drafts only")
    fun discardDraft(@PathParam("versionId") versionId: Long): Response {
        matrix.discardDraft(versionId)
        return Response.noContent().build()
    }

    @PUT
    @Path("/matrix-versions/{versionId}/cells")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Set one cell's level — base rule or partnership override")
    fun setCell(
        @PathParam("versionId") versionId: Long,
        request: SetMatrixCellRequest,
    ): MatrixRuleDto = matrix.setCell(
        matrixVersionId = versionId,
        partnershipId = request.partnershipId,
        positionId = request.positionId,
        requirementId = request.requirementId,
        level = request.level,
    ).toDto()

    /**
     * Clears a cell. `DELETE` with the cell's coordinates as query parameters rather than in a
     * body: a DELETE body is permitted but widely mishandled by proxies and clients, and the
     * coordinates are three scalars.
     */
    @DELETE
    @Path("/matrix-versions/{versionId}/cells")
    @Operation(summary = "Clear a cell — removes the base rule, or drops a partnership override")
    fun clearCell(
        @PathParam("versionId") versionId: Long,
        @QueryParam("partnershipId") partnershipId: Long?,
        @QueryParam("positionId") positionId: Long,
        @QueryParam("requirementId") requirementId: Long,
    ): Response {
        matrix.clearCell(versionId, partnershipId, positionId, requirementId)
        return Response.noContent().build()
    }

    @POST
    @Path("/matrix-versions/{versionId}/publish")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Publish a draft, superseding the current version — Compliance Lead only (§5.5)")
    fun publish(
        @PathParam("versionId") versionId: Long,
        request: PublishMatrixRequest?,
    ): PublicationResultDto {
        val result = matrix.publish(versionId, request?.effectiveFrom)
        return PublicationResultDto(
            published = result.published.toDto(),
            superseded = result.superseded?.toDto(),
        )
    }
}
