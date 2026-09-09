package au.crewcomp.ops

import au.crewcomp.people.Person
import au.crewcomp.people.PersonRepository
import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.persistence.AuditedEntity
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.Role
import au.crewcomp.reference.Customer
import au.crewcomp.reference.CustomerRepository
import au.crewcomp.reference.Partnership
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
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.Operation
import java.time.Instant

/**
 * Notices (OPS): a safety alert, a changed procedure, a message to the crew — posted to a
 * customer or one of its ships, read-and-acknowledged by name. Who has and has not acknowledged
 * is the point: a notice nobody can be shown to have read is a notice that was not given.
 */
@Entity
@Table(name = "notice")
class Notice : AuditedEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    var customer: Customer? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partnership_id")
    var partnership: Partnership? = null

    @Column(name = "title", nullable = false)
    lateinit var title: String

    @Column(name = "body", nullable = false, columnDefinition = "text")
    lateinit var body: String

    @Column(name = "requires_ack", nullable = false)
    var requiresAck: Boolean = true

    @Column(name = "posted_at", nullable = false)
    var postedAt: Instant = Instant.now()

    @Column(name = "withdrawn_at")
    var withdrawnAt: Instant? = null
}

@Entity
@Table(name = "notice_ack")
class NoticeAck {
    @jakarta.persistence.Id
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notice_id", nullable = false)
    lateinit var notice: Notice

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    lateinit var person: Person

    @Column(name = "acked_at", nullable = false)
    var ackedAt: Instant = Instant.now()

    @Column(name = "acked_by", nullable = false)
    lateinit var ackedBy: String
}

@ApplicationScoped
class NoticeRepository : PanacheRepositoryBase<Notice, Long> {
    fun current(): List<Notice> =
        find("from Notice n left join fetch n.customer left join fetch n.partnership where n.withdrawnAt is null order by n.postedAt desc").list()

    fun byIdLoaded(id: Long): Notice? =
        find("from Notice n left join fetch n.customer left join fetch n.partnership where n.id = ?1", id).firstResult()
}

@ApplicationScoped
class NoticeAckRepository : PanacheRepositoryBase<NoticeAck, Long> {
    fun forNotice(noticeId: Long): List<NoticeAck> =
        find("from NoticeAck a join fetch a.person where a.notice.id = ?1 order by a.ackedAt", noticeId).list()

    fun find(noticeId: Long, personId: Long): NoticeAck? = find("notice.id = ?1 and person.id = ?2", noticeId, personId).firstResult()
}

data class NoticeDto(
    val id: Long,
    val customerId: Long?,
    val customerName: String?,
    val partnershipId: Long?,
    val partnershipAbbrev: String?,
    val title: String,
    val body: String,
    val requiresAck: Boolean,
    val postedAt: Instant,
    val postedBy: String,
    /** How many of the people it reaches have acknowledged, and how many it reaches. */
    val acknowledged: Int,
    val reaches: Int,
)

data class NoticeAckDto(val personId: Long, val personName: String, val ackedAt: Instant, val ackedBy: String)

data class PostNoticeRequest(val customerId: Long? = null, val partnershipId: Long? = null, val title: String, val body: String, val requiresAck: Boolean = true)

@ApplicationScoped
class NoticeService(
    private val notices: NoticeRepository,
    private val acks: NoticeAckRepository,
    private val customers: CustomerRepository,
    private val partnerships: PartnershipRepository,
    private val people: PersonRepository,
    private val policy: AccessPolicy,
    private val audit: AuditWriter,
) {

    /** The notices the caller may see: those aimed at a ship they may see, or at everyone. */
    @Transactional
    fun list(partnershipId: Long?): List<NoticeDto> {
        policy.actor()
        return notices.current()
            .filter { reachable(it) }
            .filter { partnershipId == null || it.partnership == null && it.customer == null || it.partnership?.requiredId == partnershipId || partnerships.forCustomer(it.customer?.requiredId ?: -1).any { p -> p.requiredId == partnershipId } }
            .map { it.toDto() }
    }

    @Transactional
    fun post(request: PostNoticeRequest): NoticeDto {
        policy.require(Role.COMPLIANCE_LEAD, Role.CREW_COORDINATOR, Role.WORKFLOW_MANAGER, Role.SYSTEM_ADMINISTRATOR)
        policy.assertNotReadOnlyActor()
        require(request.title.isNotBlank()) { "A notice needs a title" }
        require(request.body.isNotBlank()) { "A notice needs something to say" }
        val notice = Notice().apply {
            title = request.title.trim()
            body = request.body.trim()
            requiresAck = request.requiresAck
            customer = request.customerId?.let { customers.findById(it) ?: throw EntityNotFoundException("No customer $it") }
            partnership = request.partnershipId?.let { partnerships.findById(it) ?: throw EntityNotFoundException("No partnership $it") }
            stampCreated(policy.actor().label)
        }
        notice.partnership?.let { policy.assertCanSeePartnership(it.requiredId) }
        notices.persist(notice)
        notices.flush()
        audit.record(entityType = "Notice", event = "notice.posted", entityId = notice.id, businessKey = notice.title, after = mapOf("customer" to notice.customer?.name, "partnership" to notice.partnership?.abbrev, "requiresAck" to notice.requiresAck))
        return notice.toDto()
    }

    @Transactional
    fun withdraw(id: Long) {
        policy.require(Role.COMPLIANCE_LEAD, Role.CREW_COORDINATOR, Role.WORKFLOW_MANAGER, Role.SYSTEM_ADMINISTRATOR)
        val notice = notices.byIdLoaded(id) ?: throw EntityNotFoundException("No notice $id")
        notice.withdrawnAt = Instant.now()
        notice.stampUpdated(policy.actor().label)
        audit.record(entityType = "Notice", event = "notice.withdrawn", entityId = notice.id, businessKey = notice.title)
    }

    /** Acknowledged — by the person from the crew app, or by the office on their word. */
    @Transactional
    fun acknowledge(id: Long, personId: Long): NoticeAckDto {
        val notice = notices.byIdLoaded(id) ?: throw EntityNotFoundException("No notice $id")
        val person = people.findById(personId) ?: throw EntityNotFoundException("No person $personId")
        policy.assertCanSeePerson(person.requiredId, person.partnership.requiredId)
        val existing = acks.find(id, personId)
        if (existing != null) return existing.toDto()
        val ack = NoticeAck().apply {
            this.notice = notice
            this.person = person
            ackedBy = policy.actor().label
        }
        acks.persist(ack)
        acks.flush()
        audit.record(entityType = "Notice", event = "notice.acknowledged", entityId = notice.id, businessKey = notice.title, after = mapOf("person" to person.name, "by" to ack.ackedBy))
        return ack.toDto()
    }

    @Transactional
    fun acknowledgements(id: Long): List<NoticeAckDto> {
        val notice = notices.byIdLoaded(id) ?: throw EntityNotFoundException("No notice $id")
        require(reachable(notice)) { "Not a notice you may see" }
        return acks.forNotice(id).map { it.toDto() }
    }

    private fun reachable(notice: Notice): Boolean {
        val partnership = notice.partnership
        if (partnership != null) return policy.canSeePartnership(partnership.requiredId)
        val customer = notice.customer
        if (customer != null) return partnerships.forCustomer(customer.requiredId).any { policy.canSeePartnership(it.requiredId) }
        return true
    }

    private fun reaches(notice: Notice): List<Person> {
        val partnership = notice.partnership
        if (partnership != null) return people.byPartnershipUnscoped(partnership.requiredId)
        val customer = notice.customer
        if (customer != null) return partnerships.forCustomer(customer.requiredId).flatMap { people.byPartnershipUnscoped(it.requiredId) }
        return people.allActiveUnscoped()
    }

    private fun Notice.toDto(): NoticeDto {
        val reached = reaches(this).filter { it.statusValue == "active" }
        return NoticeDto(
            id = requiredId,
            customerId = customer?.requiredId,
            customerName = customer?.name,
            partnershipId = partnership?.requiredId,
            partnershipAbbrev = partnership?.abbrev,
            title = title,
            body = body,
            requiresAck = requiresAck,
            postedAt = postedAt,
            postedBy = createdBy,
            acknowledged = acks.forNotice(requiredId).size,
            reaches = reached.size,
        )
    }

    private fun NoticeAck.toDto() = NoticeAckDto(person.requiredId, person.name, ackedAt, ackedBy)
}

@Path("/api/v1/ops/notices")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
class NoticeResource(private val notices: NoticeService) {

    @GET
    @Operation(summary = "Current notices — for one ship when given, else every one the caller may see")
    fun list(@QueryParam("partnershipId") partnershipId: Long?): List<NoticeDto> = notices.list(partnershipId)

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Post a notice to a customer, a ship, or everyone — audited")
    fun post(request: PostNoticeRequest): NoticeDto = notices.post(request)

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Withdraw a notice — audited")
    fun withdraw(@PathParam("id") id: Long): Response {
        notices.withdraw(id)
        return Response.noContent().build()
    }

    @GET
    @Path("/{id}/acknowledgements")
    @Operation(summary = "Who has acknowledged a notice, and when")
    fun acknowledgements(@PathParam("id") id: Long): List<NoticeAckDto> = notices.acknowledgements(id)

    @POST
    @Path("/{id}/acknowledge/{personId}")
    @Operation(summary = "Record a person's acknowledgement — from the crew app, or the office on their word")
    fun acknowledge(@PathParam("id") id: Long, @PathParam("personId") personId: Long): NoticeAckDto = notices.acknowledge(id, personId)
}
