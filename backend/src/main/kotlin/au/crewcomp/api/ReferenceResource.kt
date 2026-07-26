package au.crewcomp.api

import au.crewcomp.reference.ReferenceService
import io.quarkus.security.Authenticated
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.tags.Tag

/**
 * §4.1 reference-layer reads, plus the ADM-6 catalogue write path (§10.2 resource groups).
 *
 * Everything here is a thin adapter over [ReferenceService], which is where the authorisation
 * lives. The matrix write path (ADM-3) is not here: it belongs to the versioning and diff
 * services, which are a module of their own.
 *
 * Rooted at `/api/v1` rather than at `/api/v1/requirements` for the JAX-RS reason documented on
 * [ComplianceResource]: a longer-prefixed root resource captures every request beneath it.
 */
@Path("/api/v1")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Reference", description = "Partnerships, swing calendar, slots, requirement catalogue")
class ReferenceResource(private val reference: ReferenceService) {

    @GET
    @Path("/partnerships")
    @Operation(summary = "Partnerships visible to the caller (§3 scoping)")
    fun partnerships(): List<PartnershipDto> = reference.listPartnerships().map { it.toDto() }

    @GET
    @Path("/partnerships/{abbrev}/crew-changes")
    @Operation(summary = "A partnership's swing calendar, oldest first")
    fun crewChanges(@PathParam("abbrev") abbrev: String): List<CrewChangeDto> =
        reference.listCrewChanges(abbrev).map { it.toDto() }

    @GET
    @Path("/requirements")
    @Operation(summary = "The requirement catalogue, retired entries included")
    fun requirements(): List<RequirementDto> = reference.listRequirements().map { it.toDto() }

    @GET
    @Path("/positions")
    @Operation(summary = "Crew positions")
    fun positions(): List<PositionDto> = reference.listPositions().map { it.toDto() }

    @GET
    @Path("/slots")
    @Operation(summary = "The position slot model, by slot reference")
    fun slots(): List<SlotDto> = reference.listSlots().map { it.toDto() }

    // -----------------------------------------------------------------------
    // Catalogue — ADM-6
    // -----------------------------------------------------------------------

    @GET
    @Path("/requirements/catalogue")
    @Operation(summary = "The catalogue with aliases and usage counts (ADM-6)")
    fun catalogue(): List<RequirementDetailDto> {
        val usage = reference.requirementUsage().associateBy { it.requirementId }
        return reference.listRequirementsWithAliases().map { it.toDetailDto(usage[it.requiredId]) }
    }

    @POST
    @Path("/requirements")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Add a catalogue entry — Compliance Lead only, audited")
    fun createRequirement(request: SaveRequirementRequest): RequirementDetailDto = detail(
        reference.createRequirement(
            code = requireNotNull(request.code) { "A new requirement needs a code" },
            category = request.category,
            title = request.title,
            issuingAuthority = request.issuingAuthority,
            notes = request.notes,
        ),
    )

    @PUT
    @Path("/requirements/{requirementId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Edit a catalogue entry or retire it — Compliance Lead only, audited")
    fun updateRequirement(
        @PathParam("requirementId") requirementId: Long,
        request: SaveRequirementRequest,
    ): RequirementDetailDto = detail(
        reference.updateRequirement(
            requirementId = requirementId,
            category = request.category,
            title = request.title,
            issuingAuthority = request.issuingAuthority,
            notes = request.notes,
            status = request.status,
        ),
    )

    @POST
    @Path("/requirements/{requirementId}/aliases")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Record a legacy title that maps to this requirement (§4.1)")
    fun addAlias(
        @PathParam("requirementId") requirementId: Long,
        request: AddAliasRequest,
    ): RequirementDetailDto = detail(reference.addRequirementAlias(requirementId, request.alias))

    @DELETE
    @Path("/requirements/{requirementId}/aliases/{aliasId}")
    @Operation(summary = "Remove a legacy title mapping")
    fun removeAlias(
        @PathParam("requirementId") requirementId: Long,
        @PathParam("aliasId") aliasId: Long,
    ): RequirementDetailDto = detail(reference.removeRequirementAlias(requirementId, aliasId))

    /**
     * Re-reads the usage counts for a mutation response. Five grouped queries on a catalogue of a
     * few hundred rows, on an operation a Compliance Lead performs a handful of times a year —
     * paid so the client never has to reason about a sometimes-absent field.
     */
    private fun detail(requirement: au.crewcomp.reference.Requirement): RequirementDetailDto =
        requirement.toDetailDto(
            reference.requirementUsage().firstOrNull { it.requirementId == requirement.requiredId },
        )
}
