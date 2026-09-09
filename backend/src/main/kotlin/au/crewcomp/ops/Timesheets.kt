package au.crewcomp.ops

import au.crewcomp.people.AssignmentRepository
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.reference.CrewChangeRepository
import au.crewcomp.reference.PartnershipRepository
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.Operation
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Timesheets (OPS): days onboard per person over a range, read off the roster's assignments —
 * the figure payroll wants, clipped to the range so a swing straddling a month end is split
 * the way the pay period is. Nothing is stored; the roster is the record.
 */
data class TimesheetLeg(val ccId: String, val from: LocalDate, val to: LocalDate, val days: Long)

data class TimesheetLine(val personId: Long, val sam: String, val name: String, val position: String, val rotation: String?, val days: Long, val legs: List<TimesheetLeg>)

data class TimesheetDto(val partnership: String, val from: LocalDate, val to: LocalDate, val lines: List<TimesheetLine>, val totalDays: Long)

@ApplicationScoped
class TimesheetService(
    private val partnerships: PartnershipRepository,
    private val assignments: AssignmentRepository,
    private val crewChanges: CrewChangeRepository,
    private val policy: AccessPolicy,
) {

    @Transactional
    fun build(abbrev: String, from: LocalDate, to: LocalDate): TimesheetDto {
        val partnership = partnerships.byAbbrev(abbrev) ?: throw EntityNotFoundException("No partnership $abbrev")
        policy.assertCanSeePartnership(partnership.requiredId)
        require(!to.isBefore(from)) { "The range ends before it starts" }
        val rows = assignments.list("partnership.id = ?1 and fromDate <= ?2 and toDate >= ?3 order by person.name, fromDate", partnership.requiredId, to, from)
        val lines = rows.groupBy { it.person.requiredId }.map { (_, theirs) ->
            val person = theirs.first().person
            val legs = theirs.map { a ->
                val start = maxOf(a.fromDate, from)
                val end = minOf(a.toDate, to)
                TimesheetLeg(a.crewChange.ccId, start, end, ChronoUnit.DAYS.between(start, end) + 1)
            }
            TimesheetLine(person.requiredId, person.sam, person.name, person.position.name, person.rotation, legs.sumOf { it.days }, legs)
        }.sortedBy { it.name }
        return TimesheetDto(partnership.abbrev, from, to, lines, lines.sumOf { it.days })
    }

    /** The swing calendar as iCalendar text, for Outlook, Google and phones. */
    @Transactional
    fun calendar(abbrev: String): String {
        val partnership = partnerships.byAbbrev(abbrev) ?: throw EntityNotFoundException("No partnership $abbrev")
        policy.assertCanSeePartnership(partnership.requiredId)
        val lines = mutableListOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//FIT TO SAIL//Swings//EN", "X-WR-CALNAME:${partnership.abbrev} swings")
        crewChanges.forPartnership(partnership.requiredId).forEach { cc ->
            val crew = cc.rotation?.let { "Crew $it" } ?: cc.ccId
            lines += "BEGIN:VEVENT"
            lines += "UID:${partnership.abbrev}-${cc.ccId}@fit-to-sail"
            lines += "DTSTART;VALUE=DATE:${cc.fromDate.toString().replace("-", "")}"
            lines += "DTEND;VALUE=DATE:${cc.toDate.plusDays(1).toString().replace("-", "")}"
            lines += "SUMMARY:${partnership.abbrev} swing $crew (${cc.ccId})"
            lines += "DESCRIPTION:Onboard ${cc.fromDate} to ${cc.toDate}\\, cutoff ${cc.cutoffDate}"
            lines += "END:VEVENT"
        }
        lines += "END:VCALENDAR"
        return lines.joinToString("\r\n") + "\r\n"
    }
}

@Path("/api/v1/ops")
@Authenticated
class TimesheetResource(private val timesheets: TimesheetService) {

    @GET
    @Path("/timesheets/{partnership}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Days onboard per person over a range, from the roster")
    fun timesheet(@PathParam("partnership") partnership: String, @QueryParam("from") from: String, @QueryParam("to") to: String): TimesheetDto =
        timesheets.build(partnership, LocalDate.parse(from), LocalDate.parse(to))

    @GET
    @Path("/calendar/{partnership}.ics")
    @Produces("text/calendar")
    @Operation(summary = "The swing calendar as an iCalendar feed")
    fun calendar(@PathParam("partnership") partnership: String): Response =
        Response.ok(timesheets.calendar(partnership))
            .type("text/calendar; charset=utf-8")
            .header("Content-Disposition", "inline; filename=\"$partnership-swings.ics\"")
            .build()
}
