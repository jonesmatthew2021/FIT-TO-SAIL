package au.crewcomp.api

import au.crewcomp.people.TeamMemberView
import au.crewcomp.people.TeamService
import au.crewcomp.people.TeamView
import io.quarkus.security.Authenticated
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import java.time.Instant
import java.time.LocalDate

/**
 * MOB-11 — a supervisor's watch.
 *
 * A "me" endpoint in the same sense as sync: it takes no person id and there is no parameter
 * through which a caller could name somebody else's team. The watch is derived from the
 * authenticated supervisor's own roster (see [TeamService]).
 *
 * The nudge is **not** here. It arrives through the sync queue like every other client-originated
 * write, so that a supervisor who taps it out of coverage gets the same durable outbox everything
 * else on that phone gets, rather than a button that fails silently at sea.
 */
@Path("/api/v1")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Team", description = "MOB-11 — a supervisor's watch over the crew on their swing")
class TeamResource(private val team: TeamService) {

    @GET
    @Path("/me/team")
    @Operation(summary = "The crew on this supervisor's current or next swing, status only")
    fun myTeam(): TeamDto = team.myTeam().toDto()
}

/**
 * A supervisor's watch.
 *
 * [ccId] being null is a different answer from [members] being empty, and the app must render them
 * differently: "you are not on a swing" versus "everyone on your watch is fine". Collapsing the two
 * would show a reassuring blank screen to a supervisor whose team is simply not loaded.
 */
data class TeamDto(
    val ccId: String?,
    val partnershipAbbrev: String?,
    val from: LocalDate?,
    val to: LocalDate?,
    val members: List<TeamMemberDto>,
)

/**
 * One member of the watch — **status and one line, and deliberately nothing else**.
 *
 * The privacy rule is the shape of this class, not the discretion of the screen that draws it. A
 * supervisor is entitled to know that somebody is not ready and roughly why; they are not entitled
 * to the document, the medical detail, or the reason a certificate lapsed. A device that received
 * that and chose not to draw it would leak it to anyone who read the local database, which is
 * precisely what SEC-12 encrypts against — so there is nowhere here to put it.
 */
data class TeamMemberDto(
    val sam: String,
    val name: String,
    /** A §5.1 cell state — the engine's own roll-up, never recomputed here. */
    val worstState: String,
    /** "Work at Heights not held". One line, naming a requirement code and a state. */
    val reason: String?,
    /**
     * Something is already moving — they have asked the office for something and it has not been
     * dismissed. The app suppresses the nudge, because chasing someone who has already acted is
     * how a supervisor's tool gets resented.
     */
    val inHand: Boolean,
    /** When this person was last nudged, by anyone. Read from the notification, which *is* the nudge. */
    val nudgedAt: Instant?,
)

fun TeamView.toDto() = TeamDto(
    ccId = swing?.ccId,
    partnershipAbbrev = swing?.partnership?.abbrev,
    from = swing?.fromDate,
    to = swing?.toDate,
    members = members.map { it.toDto() },
)

fun TeamMemberView.toDto() = TeamMemberDto(
    sam = person.sam,
    name = person.name,
    worstState = worstState,
    reason = reason,
    inHand = inHand,
    nudgedAt = nudgedAt,
)
