package au.crewcomp.api

import au.crewcomp.platform.time.BusinessClock
import au.crewcomp.reference.SwingService
import io.quarkus.security.Authenticated
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DefaultValue
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
import java.time.LocalDate

/**
 * The swing pattern (the Coolibah portal's Swing Compliance page, server half). Rooted at
 * `/api/v1` beside [ComplianceResource] and [AssignmentResource], whose `/swings/…` paths these
 * sit among.
 */
@Path("/api/v1")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Swings", description = "The swing pattern: rotation, upcoming swings, the office's dates")
class SwingResource(private val swings: SwingService, private val clock: BusinessClock) {

    @GET
    @Path("/swings/{partnership}/pattern")
    @Operation(summary = "The ship's pattern and which swing today falls in")
    fun pattern(@PathParam("partnership") partnership: String): PatternDto {
        val p = swings.partnership(partnership)
        val pattern = swings.patternOf(p)
        return PatternDto(
            rosterAnchor = pattern?.anchor,
            rosterCycleDays = pattern?.cycleDays,
            rosterAnchorCrew = pattern?.anchorCrew,
            currentK = pattern?.indexOf(clock.today()),
            today = clock.today(),
        )
    }

    @PUT
    @Path("/swings/{partnership}/pattern")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Set the ship's pattern — audited")
    fun setPattern(@PathParam("partnership") partnership: String, request: SetPatternRequest): PartnershipDto =
        swings.setPattern(partnership, request.rosterAnchor, request.rosterCycleDays, request.rosterAnchorCrew).toDto()

    @POST
    @Path("/swings/{partnership}/ensure-upcoming")
    @Operation(summary = "The swing on now and the next few, made from the pattern and rostered where missing")
    fun ensureUpcoming(
        @PathParam("partnership") partnership: String,
        @QueryParam("lookahead") @DefaultValue("3") lookahead: Int,
    ): UpcomingSwingsDto {
        // Two steps, two transactions: the calendar first, committed; then each new swing's roster
        // on its own, so a roster that cannot be completed never undoes the calendar or another
        // swing's roster.
        val created = swings.createMissing(partnership, lookahead.coerceIn(0, 12))
        val unrostered = created.createdCcIds.flatMap { swings.rosterFromRotation(partnership, it) }
        return UpcomingSwingsDto(
            swings = created.swings.map { it.toDto() },
            unrostered = unrostered.map { it.toDto() },
        )
    }

    @PUT
    @Path("/swings/{partnership}/{cc}/dates")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "The office's dates for a swing, typed over the pattern's — audited")
    fun setDates(
        @PathParam("partnership") partnership: String,
        @PathParam("cc") cc: String,
        request: SetSwingDatesRequest,
    ): CrewChangeDto = swings.setDates(partnership, cc, request.from, request.to).toDto()

    @POST
    @Path("/swings/{partnership}/{cc}/use-pattern")
    @Operation(summary = "Put the pattern's dates back on a swing — audited")
    fun usePattern(@PathParam("partnership") partnership: String, @PathParam("cc") cc: String): CrewChangeDto =
        swings.usePattern(partnership, cc).toDto()

    @POST
    @Path("/swings/{partnership}/{cc}/roster-from-rotation")
    @Operation(summary = "Put the swing's crew into free slots; reports who could not be rostered")
    fun rosterFromRotation(@PathParam("partnership") partnership: String, @PathParam("cc") cc: String): UpcomingSwingsDto =
        UpcomingSwingsDto(
            swings = emptyList(),
            unrostered = swings.rosterFromRotation(partnership, cc).map { it.toDto() },
        )
}

data class PatternDto(
    val rosterAnchor: LocalDate?,
    val rosterCycleDays: Int?,
    val rosterAnchorCrew: String?,
    /** Which pattern number today falls in; null without a pattern. */
    val currentK: Int?,
    val today: LocalDate,
)

fun SwingService.Unrostered.toDto() = UnrosteredDto(
    ccId = ccId,
    personId = personId,
    name = name,
    position = position,
    reason = reason,
)
