package au.crewcomp.ops

import au.crewcomp.people.Person
import au.crewcomp.people.PersonRepository
import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.persistence.AuditedEntity
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.Role
import au.crewcomp.reference.PartnershipRepository
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.transaction.Transactional
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.Operation
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Hours of rest (OPS): one figure per person per day, checked against the rule every
 * inspection asks about — at least 10 hours' rest in any 24, at least 77 in any 7 days.
 * A day with no record is not a breach; it is a day nobody has written down.
 */
@Entity
@Table(name = "rest_record")
class RestRecord : AuditedEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    lateinit var person: Person

    @Column(name = "day", nullable = false)
    lateinit var day: LocalDate

    @Column(name = "rest_hours", nullable = false)
    lateinit var restHours: BigDecimal

    @Column(name = "note")
    var note: String? = null
}

@ApplicationScoped
class RestRecordRepository : PanacheRepositoryBase<RestRecord, Long> {
    fun forPartnership(partnershipId: Long, from: LocalDate, to: LocalDate): List<RestRecord> =
        find("from RestRecord r join fetch r.person where r.person.partnership.id = ?1 and r.day between ?2 and ?3 order by r.person.name, r.day", partnershipId, from, to).list()

    fun find(personId: Long, day: LocalDate): RestRecord? = find("person.id = ?1 and day = ?2", personId, day).firstResult()
}

data class RestRecordDto(val personId: Long, val personName: String, val day: LocalDate, val restHours: BigDecimal, val note: String?)

data class RestBreach(val personId: Long, val personName: String, val day: LocalDate, val rule: String, val hours: BigDecimal)

data class RestSummaryDto(val from: LocalDate, val to: LocalDate, val records: List<RestRecordDto>, val breaches: List<RestBreach>)

data class SetRestRequest(val restHours: BigDecimal, val note: String? = null)

@ApplicationScoped
class RestService(
    private val rest: RestRecordRepository,
    private val people: PersonRepository,
    private val partnerships: PartnershipRepository,
    private val policy: AccessPolicy,
    private val audit: AuditWriter,
) {

    @Transactional
    fun summary(abbrev: String, from: LocalDate, to: LocalDate): RestSummaryDto {
        val partnership = partnerships.byAbbrev(abbrev) ?: throw EntityNotFoundException("No partnership $abbrev")
        policy.assertCanSeePartnership(partnership.requiredId)
        require(!to.isBefore(from)) { "The range ends before it starts" }
        // The seven days before the range count toward its first week, so they are read too.
        val records = rest.forPartnership(partnership.requiredId, from.minusDays(6), to)
        val breaches = mutableListOf<RestBreach>()
        records.groupBy { it.person.requiredId }.forEach { (_, theirs) ->
            val byDay = theirs.associateBy { it.day }
            val name = theirs.first().person.name
            val personId = theirs.first().person.requiredId
            var day = from
            while (!day.isAfter(to)) {
                val today = byDay[day]
                if (today != null && today.restHours < DAILY_MIN) breaches += RestBreach(personId, name, day, "under 10 hours in the day", today.restHours)
                // Seven days ending today, when every one of them has a figure.
                val week = (0L..6L).map { byDay[day.minusDays(it)] }
                if (week.all { it != null }) {
                    val total = week.fold(BigDecimal.ZERO) { sum, r -> sum + (r as RestRecord).restHours }
                    if (total < WEEKLY_MIN) breaches += RestBreach(personId, name, day, "under 77 hours in the seven days", total)
                }
                day = day.plusDays(1)
            }
        }
        return RestSummaryDto(
            from = from,
            to = to,
            records = records.filter { !it.day.isBefore(from) }.map { it.toDto() },
            breaches = breaches.sortedWith(compareBy({ it.day }, { it.personName })),
        )
    }

    @Transactional
    fun set(personId: Long, day: LocalDate, restHours: BigDecimal, note: String?): RestRecordDto {
        policy.require(Role.CREW_COORDINATOR, Role.COMPLIANCE_LEAD, Role.DATA_STEWARD, Role.VESSEL_MASTER, Role.SYSTEM_ADMINISTRATOR)
        require(restHours >= BigDecimal.ZERO && restHours <= BigDecimal(24)) { "Rest is between 0 and 24 hours" }
        val person = people.findById(personId) ?: throw EntityNotFoundException("No person $personId")
        policy.assertCanSeePerson(person.requiredId, person.partnership.requiredId)
        val existing = rest.find(personId, day)
        val record = existing ?: RestRecord().apply {
            this.person = person
            this.day = day
            stampCreated(policy.actor().label)
        }
        val before = existing?.restHours?.toPlainString()
        record.restHours = restHours.setScale(1, java.math.RoundingMode.HALF_UP)
        record.note = note?.trim()?.ifEmpty { null }
        record.stampUpdated(policy.actor().label)
        if (existing == null) rest.persist(record)
        rest.flush()
        audit.record(entityType = "RestRecord", event = "rest.recorded", entityId = record.id, businessKey = "${person.sam}/$day", before = mapOf("hours" to before), after = mapOf("hours" to record.restHours.toPlainString(), "note" to record.note))
        return record.toDto()
    }

    @Transactional
    fun clear(personId: Long, day: LocalDate) {
        policy.require(Role.CREW_COORDINATOR, Role.COMPLIANCE_LEAD, Role.DATA_STEWARD, Role.SYSTEM_ADMINISTRATOR)
        val record = rest.find(personId, day) ?: return
        policy.assertCanSeePerson(record.person.requiredId, record.person.partnership.requiredId)
        val key = "${record.person.sam}/$day"
        val before = mapOf("hours" to record.restHours.toPlainString())
        rest.delete(record)
        audit.record(entityType = "RestRecord", event = "rest.cleared", businessKey = key, before = before)
    }

    private fun RestRecord.toDto() = RestRecordDto(person.requiredId, person.name, day, restHours, note)

    companion object {
        val DAILY_MIN: BigDecimal = BigDecimal(10)
        val WEEKLY_MIN: BigDecimal = BigDecimal(77)
    }
}

@Path("/api/v1/ops/rest")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
class RestResource(private val rest: RestService) {

    @GET
    @Path("/{partnership}")
    @Operation(summary = "Hours of rest for a ship's crew over a range, with the breaches of 10-in-24 and 77-in-7")
    fun summary(@PathParam("partnership") partnership: String, @QueryParam("from") from: String, @QueryParam("to") to: String): RestSummaryDto =
        rest.summary(partnership, LocalDate.parse(from), LocalDate.parse(to))

    @PUT
    @Path("/{personId}/{day}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Record a person's rest for a day — audited")
    fun set(@PathParam("personId") personId: Long, @PathParam("day") day: String, request: SetRestRequest): RestRecordDto =
        rest.set(personId, LocalDate.parse(day), request.restHours, request.note)

    @DELETE
    @Path("/{personId}/{day}")
    @Operation(summary = "Clear a day's record — audited")
    fun clear(@PathParam("personId") personId: Long, @PathParam("day") day: String): Response {
        rest.clear(personId, LocalDate.parse(day))
        return Response.noContent().build()
    }
}
