package au.crewcomp.api

import au.crewcomp.reference.ReferenceService
import io.quarkus.security.Authenticated
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.tags.Tag

/**
 * §4.1 reference-layer reads (§10.2 resource groups).
 *
 * Read-only for now: the catalogue and matrix write paths are ADM-3/ADM-6, which need the
 * versioning and diff services that do not exist yet. Everything here is a thin adapter over
 * [ReferenceService], which is where the authorisation lives.
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
}
