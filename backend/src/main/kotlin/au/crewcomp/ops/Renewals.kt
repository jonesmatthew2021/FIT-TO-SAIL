package au.crewcomp.ops

import au.crewcomp.people.Person
import au.crewcomp.people.PersonRepository
import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.persistence.AuditedEntity
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.Role
import au.crewcomp.reference.PartnershipRepository
import au.crewcomp.reference.Requirement
import au.crewcomp.reference.RequirementRepository
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
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.Operation
import java.time.LocalDate

/**
 * A renewal being worked (OPS): the Coolibah portal's Booked / Chased / Evidence-in marks
 * against an expiring certificate, with the provider and the course date. A mark is what the
 * office is doing about it; the certificate's state stays the engine's.
 */
@Entity
@Table(name = "renewal")
class Renewal : AuditedEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    lateinit var person: Person

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requirement_id", nullable = false)
    lateinit var requirement: Requirement

    @Column(name = "status", nullable = false)
    lateinit var status: String

    @Column(name = "provider")
    var provider: String? = null

    @Column(name = "course_date")
    var courseDate: LocalDate? = null

    @Column(name = "note")
    var note: String? = null
}

@ApplicationScoped
class RenewalRepository : PanacheRepositoryBase<Renewal, Long> {
    fun forPartnership(partnershipId: Long): List<Renewal> =
        find("from Renewal r join fetch r.person join fetch r.requirement where r.person.partnership.id = ?1 order by r.updatedAt desc", partnershipId).list()

    fun find(personId: Long, requirementId: Long): Renewal? =
        find("person.id = ?1 and requirement.id = ?2", personId, requirementId).firstResult()
}

data class RenewalDto(
    val personId: Long,
    val personName: String,
    val requirementId: Long,
    val code: String,
    /** `booked` · `chased` · `evidence_in`. */
    val status: String,
    val provider: String?,
    val courseDate: LocalDate?,
    val note: String?,
    val updatedBy: String,
    val updatedOn: LocalDate,
)

data class SetRenewalRequest(val status: String, val provider: String? = null, val courseDate: LocalDate? = null, val note: String? = null)

@ApplicationScoped
class RenewalService(
    private val renewals: RenewalRepository,
    private val people: PersonRepository,
    private val requirements: RequirementRepository,
    private val partnerships: PartnershipRepository,
    private val policy: AccessPolicy,
    private val audit: AuditWriter,
) {

    @Transactional
    fun forPartnership(abbrev: String): List<RenewalDto> {
        val partnership = partnerships.byAbbrev(abbrev) ?: throw EntityNotFoundException("No partnership $abbrev")
        policy.assertCanSeePartnership(partnership.requiredId)
        return renewals.forPartnership(partnership.requiredId).map { it.toDto() }
    }

    @Transactional
    fun set(personId: Long, requirementId: Long, status: String, provider: String?, courseDate: LocalDate?, note: String?): RenewalDto {
        policy.require(Role.CREW_COORDINATOR, Role.COMPLIANCE_LEAD, Role.DATA_STEWARD, Role.SYSTEM_ADMINISTRATOR)
        policy.assertNotReadOnlyActor()
        require(status in STATUSES) { "A renewal is booked, chased or evidence_in" }
        val person = people.findById(personId) ?: throw EntityNotFoundException("No person $personId")
        policy.assertCanSeePerson(person.requiredId, person.partnership.requiredId)
        val requirement = requirements.findById(requirementId) ?: throw EntityNotFoundException("No requirement $requirementId")
        val existing = renewals.find(personId, requirementId)
        val before = existing?.let { snapshot(it) }
        val renewal = existing ?: Renewal().apply {
            this.person = person
            this.requirement = requirement
            stampCreated(policy.actor().label)
        }
        renewal.status = status
        renewal.provider = provider?.trim()?.ifEmpty { null }
        renewal.courseDate = courseDate
        renewal.note = note?.trim()?.ifEmpty { null }
        renewal.stampUpdated(policy.actor().label)
        if (existing == null) renewals.persist(renewal)
        renewals.flush()
        audit.record(
            entityType = "Renewal",
            event = if (existing == null) "renewal.marked" else "renewal.updated",
            entityId = renewal.id,
            businessKey = "${person.sam}/${requirement.code}",
            before = before,
            after = snapshot(renewal),
        )
        return renewal.toDto()
    }

    @Transactional
    fun clear(personId: Long, requirementId: Long) {
        policy.require(Role.CREW_COORDINATOR, Role.COMPLIANCE_LEAD, Role.DATA_STEWARD, Role.SYSTEM_ADMINISTRATOR)
        policy.assertNotReadOnlyActor()
        val renewal = renewals.find(personId, requirementId) ?: return
        val before = snapshot(renewal)
        val key = "${renewal.person.sam}/${renewal.requirement.code}"
        renewals.delete(renewal)
        audit.record(entityType = "Renewal", event = "renewal.cleared", businessKey = key, before = before)
    }

    private fun snapshot(renewal: Renewal) = mapOf(
        "status" to renewal.status,
        "provider" to renewal.provider,
        "courseDate" to renewal.courseDate?.toString(),
        "note" to renewal.note,
    )

    private fun Renewal.toDto() = RenewalDto(
        personId = person.requiredId,
        personName = person.name,
        requirementId = requirement.requiredId,
        code = requirement.code,
        status = status,
        provider = provider,
        courseDate = courseDate,
        note = note,
        updatedBy = updatedBy,
        updatedOn = LocalDate.ofInstant(updatedAt, java.time.ZoneId.of("Australia/Perth")),
    )

    companion object {
        val STATUSES = setOf("booked", "chased", "evidence_in")
    }
}

@Path("/api/v1/ops/renewals")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
class RenewalResource(private val renewals: RenewalService) {

    @GET
    @Path("/{partnership}")
    @Operation(summary = "Every renewal mark on a ship's crew")
    fun list(@PathParam("partnership") partnership: String): List<RenewalDto> = renewals.forPartnership(partnership)

    @PUT
    @Path("/{personId}/{requirementId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Mark what is being done about a certificate: booked, chased, evidence in — audited")
    fun set(@PathParam("personId") personId: Long, @PathParam("requirementId") requirementId: Long, request: SetRenewalRequest): RenewalDto =
        renewals.set(personId, requirementId, request.status, request.provider, request.courseDate, request.note)

    @DELETE
    @Path("/{personId}/{requirementId}")
    @Operation(summary = "Clear the mark — audited")
    fun clear(@PathParam("personId") personId: Long, @PathParam("requirementId") requirementId: Long): Response {
        renewals.clear(personId, requirementId)
        return Response.noContent().build()
    }
}
