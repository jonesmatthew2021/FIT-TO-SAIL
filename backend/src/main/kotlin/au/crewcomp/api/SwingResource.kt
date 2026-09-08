package au.crewcomp.api

import au.crewcomp.platform.time.BusinessClock
import au.crewcomp.reference.SwingRosterService
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
class SwingResource(
    private val swings: SwingService,
    private val roster: SwingRosterService,
    private val clock: BusinessClock,
) {

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

    // ------------------------------------------------------------ the roster board

    @POST
    @Path("/swings/{partnership}/{cc}/roster/{personId}/onboard")
    @Operation(summary = "Bring a person onto the swing — the whole of it, or from/to for part of it; a free slot for their position, or a new one; a clash is a 409 unless acknowledged")
    fun bringOnboard(
        @PathParam("partnership") partnership: String,
        @PathParam("cc") cc: String,
        @PathParam("personId") personId: Long,
        @QueryParam("acknowledgeClash") @DefaultValue("false") acknowledgeClash: Boolean,
        @QueryParam("from") from: String?,
        @QueryParam("to") to: String?,
    ) = roster.bringOnboard(
        partnership, cc, personId, acknowledgeClash,
        from?.takeIf { it.isNotBlank() }?.let(LocalDate::parse),
        to?.takeIf { it.isNotBlank() }?.let(LocalDate::parse),
    )

    @PUT
    @Path("/swings/{partnership}/{cc}/roster/{personId}/window")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "The days a person is on the swing — home early, out late, or the whole swing again")
    fun setWindow(
        @PathParam("partnership") partnership: String,
        @PathParam("cc") cc: String,
        @PathParam("personId") personId: Long,
        request: SetSwingDatesRequest,
    ) = roster.setWindow(partnership, cc, personId, request.from, request.to)

    @POST
    @Path("/swings/{partnership}/{cc}/roster/{personId}/ashore")
    @Operation(summary = "Send a person off the swing — their assignments on it are removed")
    fun sendAshore(
        @PathParam("partnership") partnership: String,
        @PathParam("cc") cc: String,
        @PathParam("personId") personId: Long,
    ) = roster.sendAshore(partnership, cc, personId)

    @PUT
    @Path("/swings/{partnership}/{cc}/roster/{personId}/watch")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "The watch a person keeps on the swing: day (Shift 1), night (Shift 2) or none")
    fun setWatch(
        @PathParam("partnership") partnership: String,
        @PathParam("cc") cc: String,
        @PathParam("personId") personId: Long,
        request: SetWatchRequest,
    ) = roster.setWatch(partnership, cc, personId, request.watch)

    @GET
    @Path("/swings/{partnership}/{cc}/shift-balance")
    @Operation(summary = "Day against night by rank — the same people and the same ranks on each, cooks excepted")
    fun shiftBalance(@PathParam("partnership") partnership: String, @PathParam("cc") cc: String): ShiftBalanceDto {
        val balance = roster.shiftBalance(partnership, cc)
        return ShiftBalanceDto(
            balanced = balance.balanced,
            dayCount = balance.dayCount,
            nightCount = balance.nightCount,
            ranks = balance.ranks.map { RankBalanceDto(it.position, it.day, it.night) },
            excluded = balance.excluded,
            unwatched = balance.unwatched,
        )
    }

    @POST
    @Path("/swings/{partnership}/{cc}/switch-crew")
    @Operation(summary = "The crew change: the swing carries the other crew, rostered from the rotation")
    fun switchCrew(@PathParam("partnership") partnership: String, @PathParam("cc") cc: String): UpcomingSwingsDto {
        // Two transactions, as ensure-upcoming: the switch commits, then the incoming crew is
        // rostered, so a roster that cannot be completed never undoes the switch.
        val switched = roster.switchCrew(partnership, cc)
        return UpcomingSwingsDto(
            swings = listOf(switched.toDto()),
            unrostered = swings.rosterFromRotation(partnership, cc).map { it.toDto() },
        )
    }
}

data class RankBalanceDto(val position: String, val day: Int, val night: Int)

data class ShiftBalanceDto(
    val balanced: Boolean,
    val dayCount: Int,
    val nightCount: Int,
    val ranks: List<RankBalanceDto>,
    val excluded: List<String>,
    val unwatched: List<String>,
)

data class SetWatchRequest(
    /** `day`, `night` or `none`. */
    val watch: String,
)

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
