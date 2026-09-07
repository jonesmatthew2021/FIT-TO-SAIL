package au.crewcomp.people

import au.crewcomp.engine.PersonStatus
import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.Role
import au.crewcomp.platform.time.BusinessClock
import au.crewcomp.reference.CrewPositionRepository
import au.crewcomp.reference.PartnershipRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

/**
 * Read access to the §4.3 people layer — ADM-5's person list and person detail, and the same
 * data the mobile app reads about its own user.
 *
 * Every method here is **row-scoped by construction** (AUTH-2): the repositories take their
 * restriction from [au.crewcomp.platform.security.ScopeGuard], not from the caller, so a crew
 * member calling `list()` gets a one-row list and a Vessel Master gets their partnerships. There
 * is deliberately no `personId` filter parameter on `list()` — a parameter is something a caller
 * can forget, and AUTH-2 says the scope is not the caller's to decide.
 */
@ApplicationScoped
class PersonDirectoryService(
    private val people: PersonRepository,
    private val holdings: QualificationHoldingRepository,
    private val assignments: AssignmentRepository,
    private val leave: LeaveRecordRepository,
    private val positions: CrewPositionRepository,
    private val partnerships: PartnershipRepository,
    private val assignmentService: AssignmentService,
    private val clock: BusinessClock,
    private val policy: AccessPolicy,
    private val audit: AuditWriter,
) {

    @Transactional
    fun list(): List<Person> {
        policy.actor()
        return people.listScoped()
    }

    /**
     * Creates a person — the office's "add a new crew member", reached from the certificate
     * intake when a document names someone the roster does not carry.
     *
     * The one thing it refuses is a **re-used employee id**. `Person.sam` is deliberately not
     * unique in the schema (the source data carries one historical duplicate, preserved as an
     * exception item), but a *new* row reusing a number is how the source data got that duplicate
     * in the first place, and the person adding a crew member is the right person to notice.
     */
    /** Which crew the person sails with — 'A', 'B', or null to take them off the rotation. Audited. */
    @Transactional
    fun setRotation(personId: Long, rotation: String?): Person {
        policy.require(Role.CREW_COORDINATOR, Role.DATA_STEWARD, Role.SYSTEM_ADMINISTRATOR)
        policy.assertNotReadOnlyActor()
        val clean = rotation?.trim()?.uppercase()?.ifEmpty { null }
        require(clean == null || clean == "A" || clean == "B") { "A rotation is A or B" }
        val person = get(personId)
        val before = person.rotation
        person.rotation = clean
        person.stampUpdated(policy.actor().label)
        audit.record(
            entityType = "Person",
            event = "person.rotation_set",
            entityId = person.id,
            businessKey = person.sam,
            before = mapOf("rotation" to before),
            after = mapOf("rotation" to clean),
        )
        return person
    }

    /**
     * On or off the crew. Off (`inactive`) is the portal's "remove from the roster": the person and
     * everything recorded about them stay — holdings, scans, history — and they are taken off every
     * swing still to sail, through the same door the planner uses, so the crew app is told. Back on
     * puts them where they were on the list; the swings are re-rostered by hand or by the pattern.
     */
    @Transactional
    fun setActive(personId: Long, active: Boolean): Person {
        policy.require(Role.CREW_COORDINATOR, Role.DATA_STEWARD, Role.SYSTEM_ADMINISTRATOR)
        policy.assertNotReadOnlyActor()
        val person = get(personId)
        val before = person.status
        val after = if (active) PersonStatus.ACTIVE else PersonStatus.INACTIVE
        if (before == after) return person
        val removed = if (active) emptyList() else assignments.upcomingUnscoped(clock.today()).filter { it.person.requiredId == personId }
        removed.forEach { assignmentService.unassign(it.requiredId) }
        person.status = after
        person.stampUpdated(policy.actor().label)
        audit.record(
            entityType = "Person",
            event = if (active) "person.reinstated" else "person.removed",
            entityId = person.id,
            businessKey = person.sam,
            before = mapOf("status" to before.wire),
            after = mapOf("status" to after.wire, "assignmentsRemoved" to removed.size),
        )
        return person
    }

    @Transactional
    fun create(name: String, sam: String, positionId: Long, partnershipId: Long, email: String?, rotation: String? = null): Person {
        policy.require(Role.CREW_COORDINATOR, Role.DATA_STEWARD, Role.SYSTEM_ADMINISTRATOR)
        policy.assertNotReadOnlyActor()

        val cleanName = name.trim()
        val cleanSam = sam.trim()
        val cleanRotation = rotation?.trim()?.uppercase()?.ifEmpty { null }
        require(cleanRotation == null || cleanRotation == "A" || cleanRotation == "B") { "A rotation is A or B" }
        require(cleanName.contains(',')) { "Name must be 'SURNAME, Given names' — the form every other crew record uses" }
        require(cleanSam.isNotEmpty()) { "An employee id (Sam #) is required" }
        people.bySam(cleanSam).firstOrNull()?.let {
            throw IllegalArgumentException("Employee id $cleanSam already belongs to ${it.name} — check the number")
        }
        val position = positions.findById(positionId) ?: throw EntityNotFoundException("No position $positionId")
        val partnership = partnerships.findById(partnershipId)
            ?: throw EntityNotFoundException("No partnership $partnershipId")
        policy.assertCanSeePartnership(partnershipId)

        val actor = policy.actor()
        val person = Person().apply {
            this.sam = cleanSam
            this.name = cleanName
            this.position = position
            this.partnership = partnership
            this.email = email?.trim()?.ifEmpty { null }
            this.rotation = cleanRotation
            status = PersonStatus.ACTIVE
            stampCreated(actor.label)
        }
        people.persist(person)
        people.flush()

        audit.record(
            entityType = "Person",
            event = "person.created",
            entityId = person.id,
            businessKey = person.sam,
            after = mapOf(
                "name" to person.name,
                "position" to position.name,
                "partnership" to partnership.abbrev,
                "email" to person.email,
                "rotation" to person.rotation,
            ),
        )
        return person
    }

    /**
     * One person. Invisible and absent are the same answer — see [EntityNotFoundException] for
     * why the scoped read must not distinguish them.
     */
    @Transactional
    fun get(personId: Long): Person = people.findScoped(personId)
        ?: throw EntityNotFoundException("No person $personId")

    @Transactional
    fun holdingsFor(personId: Long): List<QualificationHolding> {
        get(personId)
        return holdings.forPersonScoped(personId).sortedWith(
            compareBy({ it.requirement.category }, { it.requirement.code }),
        )
    }

    @Transactional
    fun assignmentsFor(personId: Long): List<Assignment> {
        get(personId)
        return assignments.forPersonScoped(personId)
    }

    @Transactional
    fun leaveFor(personId: Long): List<LeaveRecord> {
        get(personId)
        return leave.forPersonScoped(personId)
    }
}
