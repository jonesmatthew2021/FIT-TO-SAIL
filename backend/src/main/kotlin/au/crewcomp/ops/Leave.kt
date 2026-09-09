package au.crewcomp.ops

import au.crewcomp.people.LeaveRecord
import au.crewcomp.people.LeaveRecordRepository
import au.crewcomp.people.PersonRepository
import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.Role
import au.crewcomp.reference.PartnershipRepository
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import jakarta.ws.rs.Consumes
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
 * Leave and availability (OPS): who is away, and when. The engine already reads leave — a
 * standing record is a clash on the planner and keeps the pattern from rostering the person
 * (§5.4) — but until now nothing let the office enter it. This does.
 */
data class LeaveDto(
    val id: Long,
    val personId: Long,
    val personName: String,
    val position: String,
    val kind: String,
    val from: LocalDate,
    val to: LocalDate,
    /** `recorded` · `requested` · `approved` · `declined` · `cancelled`. The first three stand. */
    val status: String,
)

data class RecordLeaveRequest(val personId: Long, val kind: String, val from: LocalDate, val to: LocalDate)

data class SetLeaveStatusRequest(val status: String)

@ApplicationScoped
class LeaveService(
    private val leave: LeaveRecordRepository,
    private val people: PersonRepository,
    private val partnerships: PartnershipRepository,
    private val policy: AccessPolicy,
    private val audit: AuditWriter,
) {

    @Transactional
    fun forPartnership(abbrev: String, from: LocalDate): List<LeaveDto> {
        val partnership = partnerships.byAbbrev(abbrev) ?: throw EntityNotFoundException("No partnership $abbrev")
        policy.assertCanSeePartnership(partnership.requiredId)
        return leave.list("person.partnership.id = ?1 and toDate >= ?2 order by fromDate", partnership.requiredId, from).map { it.toDto() }
    }

    @Transactional
    fun record(personId: Long, kind: String, from: LocalDate, to: LocalDate): LeaveDto {
        policy.require(Role.CREW_COORDINATOR, Role.COMPLIANCE_LEAD, Role.SYSTEM_ADMINISTRATOR)
        policy.assertNotReadOnlyActor()
        val person = people.findById(personId) ?: throw EntityNotFoundException("No person $personId")
        policy.assertCanSeePerson(person.requiredId, person.partnership.requiredId)
        val cleanKind = kind.trim().lowercase().replace(' ', '_')
        require(cleanKind.matches(Regex("^[a-z_]{2,40}$"))) { "A kind of leave is a short word — annual, sick, unpaid, training" }
        require(!to.isBefore(from)) { "Leave cannot end before it starts" }
        val record = LeaveRecord().apply {
            this.person = person
            this.kind = cleanKind
            fromDate = from
            toDate = to
            status = "recorded"
            stampCreated(policy.actor().label)
        }
        leave.persist(record)
        leave.flush()
        audit.record(entityType = "LeaveRecord", event = "leave.recorded", entityId = record.id, businessKey = "${person.sam}/$from", after = snapshot(record))
        return record.toDto()
    }

    @Transactional
    fun setStatus(leaveId: Long, status: String): LeaveDto {
        policy.require(Role.CREW_COORDINATOR, Role.COMPLIANCE_LEAD, Role.SYSTEM_ADMINISTRATOR)
        policy.assertNotReadOnlyActor()
        require(status in STATUSES) { "Status must be one of ${STATUSES.joinToString()}" }
        val record = leave.findById(leaveId) ?: throw EntityNotFoundException("No leave record $leaveId")
        policy.assertCanSeePerson(record.person.requiredId, record.person.partnership.requiredId)
        val before = snapshot(record)
        record.status = status
        record.stampUpdated(policy.actor().label)
        audit.record(entityType = "LeaveRecord", event = "leave.status_set", entityId = record.id, businessKey = "${record.person.sam}/${record.fromDate}", before = before, after = snapshot(record))
        return record.toDto()
    }

    private fun snapshot(record: LeaveRecord) = mapOf(
        "person" to record.person.name,
        "kind" to record.kind,
        "from" to record.fromDate.toString(),
        "to" to record.toDate.toString(),
        "status" to record.status,
    )

    private fun LeaveRecord.toDto() = LeaveDto(
        id = requiredId,
        personId = person.requiredId,
        personName = person.name,
        position = person.position.name,
        kind = kind,
        from = fromDate,
        to = toDate,
        status = status,
    )

    companion object {
        val STATUSES = setOf("recorded", "requested", "approved", "declined", "cancelled")
    }
}

@Path("/api/v1/ops/leave")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Operations", description = "Leave, renewals, vessel papers, manning, rest, travel, notices, reminders")
class LeaveResource(private val leave: LeaveService) {

    @GET
    @Path("/{partnership}")
    @Operation(summary = "Leave on a ship's crew that ends on or after a day (today when omitted)")
    fun list(@PathParam("partnership") partnership: String, @QueryParam("from") from: String?): List<LeaveDto> =
        leave.forPartnership(partnership, from?.takeIf { it.isNotBlank() }?.let(LocalDate::parse) ?: LocalDate.now())

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Record leave — audited; a standing record is a clash on the planner")
    fun record(request: RecordLeaveRequest): LeaveDto = leave.record(request.personId, request.kind, request.from, request.to)

    @PUT
    @Path("/{leaveId}/status")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Approve, decline or cancel leave — audited")
    fun setStatus(@PathParam("leaveId") leaveId: Long, request: SetLeaveStatusRequest): LeaveDto = leave.setStatus(leaveId, request.status)
}
