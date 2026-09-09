package au.crewcomp.ops

import au.crewcomp.engine.HoldingStatus
import au.crewcomp.people.Person
import au.crewcomp.people.PersonRepository
import au.crewcomp.people.QualificationHoldingRepository
import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.persistence.AuditedEntity
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.Role
import au.crewcomp.platform.time.BusinessClock
import au.crewcomp.reference.PartnershipRepository
import au.crewcomp.reference.Requirement
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
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.openapi.annotations.Operation
import java.time.Instant
import java.time.LocalDate

/**
 * Reminders that leave the building (OPS): one per person, certificate and band — 90, 60, 30
 * days before it runs out, and on the day. Made from the holdings; sent by whatever carries
 * them (email, text, a phone call), which is recorded here so nobody is chased twice. Until
 * an email service is wired in, the office sends from the queue and marks it sent.
 */
@Entity
@Table(name = "reminder")
class Reminder : AuditedEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    lateinit var person: Person

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requirement_id", nullable = false)
    lateinit var requirement: Requirement

    @Column(name = "expires_on", nullable = false)
    lateinit var expiresOn: LocalDate

    @Column(name = "days_before", nullable = false)
    var daysBefore: Int = 0

    @Column(name = "sent_at")
    var sentAt: Instant? = null

    @Column(name = "sent_by")
    var sentBy: String? = null

    @Column(name = "channel")
    var channel: String? = null
}

@ApplicationScoped
class ReminderRepository : PanacheRepositoryBase<Reminder, Long> {
    fun forPartnership(partnershipId: Long): List<Reminder> =
        find("from Reminder r join fetch r.person join fetch r.requirement where r.person.partnership.id = ?1 order by r.sentAt nulls first, r.expiresOn, r.person.name", partnershipId).list()

    fun exists(personId: Long, requirementId: Long, expiresOn: LocalDate, daysBefore: Int): Boolean =
        count("person.id = ?1 and requirement.id = ?2 and expiresOn = ?3 and daysBefore = ?4", personId, requirementId, expiresOn, daysBefore) > 0

    fun byIdLoaded(id: Long): Reminder? =
        find("from Reminder r join fetch r.person p join fetch p.partnership join fetch r.requirement where r.id = ?1", id).firstResult()
}

data class ReminderDto(
    val id: Long,
    val personId: Long,
    val personName: String,
    val personEmail: String?,
    val position: String,
    val requirementId: Long,
    val code: String,
    val title: String,
    val expiresOn: LocalDate,
    val daysBefore: Int,
    val sentAt: Instant?,
    val sentBy: String?,
    val channel: String?,
)

data class GeneratedDto(val made: Int, val pending: Int)

data class MarkSentRequest(val channel: String)

@ApplicationScoped
class ReminderService(
    private val reminders: ReminderRepository,
    private val holdings: QualificationHoldingRepository,
    private val people: PersonRepository,
    private val partnerships: PartnershipRepository,
    private val clock: BusinessClock,
    private val policy: AccessPolicy,
    private val audit: AuditWriter,
) {

    @Transactional
    fun list(abbrev: String, pendingOnly: Boolean): List<ReminderDto> {
        val partnership = partnerships.byAbbrev(abbrev) ?: throw EntityNotFoundException("No partnership $abbrev")
        policy.assertCanSeePartnership(partnership.requiredId)
        return reminders.forPartnership(partnership.requiredId).filter { !pendingOnly || it.sentAt == null }.map { it.toDto() }
    }

    /** The queue, brought up to date: a reminder for every band a certificate has reached and nobody has been told about. */
    @Transactional
    fun generate(abbrev: String): GeneratedDto {
        policy.require(Role.CREW_COORDINATOR, Role.COMPLIANCE_LEAD, Role.DATA_STEWARD, Role.SYSTEM_ADMINISTRATOR)
        val partnership = partnerships.byAbbrev(abbrev) ?: throw EntityNotFoundException("No partnership $abbrev")
        policy.assertCanSeePartnership(partnership.requiredId)
        val today = clock.today()
        val crew = people.byPartnershipUnscoped(partnership.requiredId).filter { it.statusValue == "active" }
        val crewIds = crew.map { it.requiredId }.toSet()
        var made = 0
        holdings.expiringByUnscoped(today.plusDays(BANDS.max().toLong()))
            .filter { it.person.requiredId in crewIds && it.status == HoldingStatus.HELD_EXPIRY && it.expiryDate != null }
            .forEach { holding ->
                val expiresOn = holding.expiryDate as LocalDate
                BANDS.filter { band -> !today.isBefore(expiresOn.minusDays(band.toLong())) }.forEach { band ->
                    if (!reminders.exists(holding.person.requiredId, holding.requirement.requiredId, expiresOn, band)) {
                        reminders.persist(
                            Reminder().apply {
                                person = holding.person
                                requirement = holding.requirement
                                this.expiresOn = expiresOn
                                daysBefore = band
                                stampCreated(policy.actor().label)
                            },
                        )
                        made++
                    }
                }
            }
        reminders.flush()
        if (made > 0) audit.record(entityType = "Reminder", event = "reminders.generated", businessKey = partnership.abbrev, after = mapOf("made" to made, "today" to today.toString()))
        return GeneratedDto(made = made, pending = reminders.forPartnership(partnership.requiredId).count { it.sentAt == null })
    }

    @Transactional
    fun markSent(id: Long, channel: String): ReminderDto {
        policy.require(Role.CREW_COORDINATOR, Role.COMPLIANCE_LEAD, Role.DATA_STEWARD, Role.SYSTEM_ADMINISTRATOR)
        val reminder = reminders.byIdLoaded(id) ?: throw EntityNotFoundException("No reminder $id")
        policy.assertCanSeePartnership(reminder.person.partnership.requiredId)
        require(channel.isNotBlank()) { "Say how it went: email, text, phone, in person" }
        reminder.sentAt = Instant.now()
        reminder.sentBy = policy.actor().label
        reminder.channel = channel.trim().lowercase()
        reminder.stampUpdated(policy.actor().label)
        audit.record(entityType = "Reminder", event = "reminder.sent", entityId = reminder.id, businessKey = "${reminder.person.sam}/${reminder.requirement.code}/${reminder.daysBefore}", after = mapOf("channel" to reminder.channel, "expiresOn" to reminder.expiresOn.toString()))
        return reminder.toDto()
    }

    private fun Reminder.toDto() = ReminderDto(
        id = requiredId,
        personId = person.requiredId,
        personName = person.name,
        personEmail = person.email,
        position = person.position.name,
        requirementId = requirement.requiredId,
        code = requirement.code,
        title = requirement.title,
        expiresOn = expiresOn,
        daysBefore = daysBefore,
        sentAt = sentAt,
        sentBy = sentBy,
        channel = channel,
    )

    companion object {
        /** Days before expiry at which a reminder is owed. */
        val BANDS = listOf(90, 60, 30, 0)
    }
}

@Path("/api/v1/ops/reminders")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
class ReminderResource(private val reminders: ReminderService) {

    @GET
    @Path("/{partnership}")
    @Operation(summary = "The reminder queue for a ship's crew — pending first")
    fun list(@PathParam("partnership") partnership: String, @QueryParam("pending") pending: Boolean?): List<ReminderDto> =
        reminders.list(partnership, pending ?: false)

    @POST
    @Path("/{partnership}/generate")
    @Operation(summary = "Bring the queue up to date from the holdings — audited")
    fun generate(@PathParam("partnership") partnership: String): GeneratedDto = reminders.generate(partnership)

    @PUT
    @Path("/{id}/sent")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Mark a reminder sent, and how — audited")
    fun markSent(@PathParam("id") id: Long, request: MarkSentRequest): ReminderDto = reminders.markSent(id, request.channel)
}
