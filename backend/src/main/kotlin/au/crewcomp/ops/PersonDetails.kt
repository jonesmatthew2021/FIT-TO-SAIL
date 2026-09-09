package au.crewcomp.ops

import au.crewcomp.people.Person
import au.crewcomp.people.PersonRepository
import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.persistence.AuditedEntity
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.Role
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
import jakarta.ws.rs.GET
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.openapi.annotations.Operation
import java.time.LocalDate

/**
 * A crew member's papers and contacts (OPS): passport, visa, seafarer's book, next of kin,
 * emergency contact, PPE sizes, dietary needs. Personal data — read by those who may see the
 * person, written by the office and the company's management, never sent anywhere else.
 */
@Entity
@Table(name = "person_detail")
class PersonDetail : AuditedEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    lateinit var person: Person

    @Column(name = "passport_number") var passportNumber: String? = null
    @Column(name = "passport_expiry") var passportExpiry: LocalDate? = null
    @Column(name = "visa_detail") var visaDetail: String? = null
    @Column(name = "visa_expiry") var visaExpiry: LocalDate? = null
    @Column(name = "seafarer_book") var seafarerBook: String? = null
    @Column(name = "seafarer_book_expiry") var seafarerBookExpiry: LocalDate? = null
    @Column(name = "next_of_kin_name") var nextOfKinName: String? = null
    @Column(name = "next_of_kin_relation") var nextOfKinRelation: String? = null
    @Column(name = "next_of_kin_phone") var nextOfKinPhone: String? = null
    @Column(name = "emergency_contact") var emergencyContact: String? = null
    @Column(name = "ppe_sizes") var ppeSizes: String? = null
    @Column(name = "dietary") var dietary: String? = null
    @Column(name = "notes") var notes: String? = null
}

@ApplicationScoped
class PersonDetailRepository : PanacheRepositoryBase<PersonDetail, Long> {
    fun forPerson(personId: Long): PersonDetail? = find("person.id = ?1", personId).firstResult()
}

data class PersonDetailDto(
    val personId: Long,
    val passportNumber: String?,
    val passportExpiry: LocalDate?,
    val visaDetail: String?,
    val visaExpiry: LocalDate?,
    val seafarerBook: String?,
    val seafarerBookExpiry: LocalDate?,
    val nextOfKinName: String?,
    val nextOfKinRelation: String?,
    val nextOfKinPhone: String?,
    val emergencyContact: String?,
    val ppeSizes: String?,
    val dietary: String?,
    val notes: String?,
)

data class SavePersonDetailRequest(
    val passportNumber: String? = null,
    val passportExpiry: LocalDate? = null,
    val visaDetail: String? = null,
    val visaExpiry: LocalDate? = null,
    val seafarerBook: String? = null,
    val seafarerBookExpiry: LocalDate? = null,
    val nextOfKinName: String? = null,
    val nextOfKinRelation: String? = null,
    val nextOfKinPhone: String? = null,
    val emergencyContact: String? = null,
    val ppeSizes: String? = null,
    val dietary: String? = null,
    val notes: String? = null,
)

@ApplicationScoped
class PersonDetailService(
    private val details: PersonDetailRepository,
    private val people: PersonRepository,
    private val policy: AccessPolicy,
    private val audit: AuditWriter,
) {

    @Transactional
    fun get(personId: Long): PersonDetailDto {
        val person = people.findById(personId) ?: throw EntityNotFoundException("No person $personId")
        policy.assertCanSeePerson(person.requiredId, person.partnership.requiredId)
        return details.forPerson(personId)?.toDto() ?: PersonDetailDto(personId, null, null, null, null, null, null, null, null, null, null, null, null, null)
    }

    @Transactional
    fun set(personId: Long, request: SavePersonDetailRequest): PersonDetailDto {
        policy.require(Role.CREW_COORDINATOR, Role.COMPLIANCE_LEAD, Role.DATA_STEWARD, Role.SYSTEM_ADMINISTRATOR)
        policy.assertNotReadOnlyActor()
        val person = people.findById(personId) ?: throw EntityNotFoundException("No person $personId")
        policy.assertCanSeePerson(person.requiredId, person.partnership.requiredId)
        val existing = details.forPerson(personId)
        val detail = existing ?: PersonDetail().apply {
            this.person = person
            stampCreated(policy.actor().label)
        }
        val before = existing?.let { snapshot(it) }
        val clean = { s: String? -> s?.trim()?.ifEmpty { null } }
        detail.passportNumber = clean(request.passportNumber)
        detail.passportExpiry = request.passportExpiry
        detail.visaDetail = clean(request.visaDetail)
        detail.visaExpiry = request.visaExpiry
        detail.seafarerBook = clean(request.seafarerBook)
        detail.seafarerBookExpiry = request.seafarerBookExpiry
        detail.nextOfKinName = clean(request.nextOfKinName)
        detail.nextOfKinRelation = clean(request.nextOfKinRelation)
        detail.nextOfKinPhone = clean(request.nextOfKinPhone)
        detail.emergencyContact = clean(request.emergencyContact)
        detail.ppeSizes = clean(request.ppeSizes)
        detail.dietary = clean(request.dietary)
        detail.notes = clean(request.notes)
        detail.stampUpdated(policy.actor().label)
        if (existing == null) details.persist(detail)
        details.flush()
        // The audit event names the fields that changed, not their values: these are personal data.
        val after = snapshot(detail)
        val changed = after.keys.filter { before?.get(it) != after[it] }
        audit.record(entityType = "PersonDetail", event = "person_detail.updated", entityId = detail.id, businessKey = person.sam, after = mapOf("changed" to changed))
        return detail.toDto()
    }

    private fun snapshot(d: PersonDetail): Map<String, String?> = mapOf(
        "passportNumber" to d.passportNumber, "passportExpiry" to d.passportExpiry?.toString(), "visaDetail" to d.visaDetail,
        "visaExpiry" to d.visaExpiry?.toString(), "seafarerBook" to d.seafarerBook, "seafarerBookExpiry" to d.seafarerBookExpiry?.toString(),
        "nextOfKinName" to d.nextOfKinName, "nextOfKinRelation" to d.nextOfKinRelation, "nextOfKinPhone" to d.nextOfKinPhone,
        "emergencyContact" to d.emergencyContact, "ppeSizes" to d.ppeSizes, "dietary" to d.dietary, "notes" to d.notes,
    )

    private fun PersonDetail.toDto() = PersonDetailDto(
        personId = person.requiredId,
        passportNumber = passportNumber, passportExpiry = passportExpiry, visaDetail = visaDetail, visaExpiry = visaExpiry,
        seafarerBook = seafarerBook, seafarerBookExpiry = seafarerBookExpiry, nextOfKinName = nextOfKinName,
        nextOfKinRelation = nextOfKinRelation, nextOfKinPhone = nextOfKinPhone, emergencyContact = emergencyContact,
        ppeSizes = ppeSizes, dietary = dietary, notes = notes,
    )
}

@Path("/api/v1/ops/person-details")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
class PersonDetailResource(private val details: PersonDetailService) {

    @GET
    @Path("/{personId}")
    @Operation(summary = "A crew member's papers and contacts")
    fun get(@PathParam("personId") personId: Long): PersonDetailDto = details.get(personId)

    @PUT
    @Path("/{personId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Set a crew member's papers and contacts — audited by field name")
    fun set(@PathParam("personId") personId: Long, request: SavePersonDetailRequest): PersonDetailDto = details.set(personId, request)
}
