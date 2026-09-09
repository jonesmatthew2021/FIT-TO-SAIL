package au.crewcomp.ops

import au.crewcomp.people.AssignmentRepository
import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.persistence.AuditedEntity
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.Role
import au.crewcomp.reference.CrewChangeRepository
import au.crewcomp.reference.CrewPosition
import au.crewcomp.reference.CrewPositionRepository
import au.crewcomp.reference.Partnership
import au.crewcomp.reference.PartnershipRepository
import au.crewcomp.reference.PositionSlotRepository
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

/**
 * Minimum safe manning (OPS): how many of each position the ship must carry — on the swing,
 * or on a shift of it — from its manning document. Checked against a swing's roster the way
 * the shift rules are, but by position rather than by certificate.
 */
@Entity
@Table(name = "manning_requirement")
class ManningRequirement : AuditedEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partnership_id", nullable = false)
    lateinit var partnership: Partnership

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "position_id", nullable = false)
    lateinit var position: CrewPosition

    /** `Shift 1` · `Shift 2` · null for the whole swing. */
    @Column(name = "shift")
    var shift: String? = null

    @Column(name = "required", nullable = false)
    var required: Int = 0
}

@ApplicationScoped
class ManningRequirementRepository : PanacheRepositoryBase<ManningRequirement, Long> {
    fun forPartnership(partnershipId: Long): List<ManningRequirement> =
        find("from ManningRequirement m join fetch m.position where m.partnership.id = ?1 order by m.position.name, m.shift nulls first", partnershipId).list()
}

data class ManningRequirementDto(val positionId: Long, val positionName: String, val shift: String?, val required: Int)

data class ManningCheckLine(
    val positionId: Long,
    val positionName: String,
    val shift: String?,
    val required: Int,
    val standing: Int,
    val names: List<String>,
    val satisfied: Boolean,
)

data class ManningCheckDto(val ccId: String, val lines: List<ManningCheckLine>, val short: Int)

data class SetManningRequest(val requirements: List<ManningRequirementDto>)

@ApplicationScoped
class ManningService(
    private val manning: ManningRequirementRepository,
    private val partnerships: PartnershipRepository,
    private val positions: CrewPositionRepository,
    private val crewChanges: CrewChangeRepository,
    private val assignments: AssignmentRepository,
    private val slots: PositionSlotRepository,
    private val policy: AccessPolicy,
    private val audit: AuditWriter,
) {

    @Transactional
    fun list(abbrev: String): List<ManningRequirementDto> {
        val partnership = partnership(abbrev)
        policy.assertCanSeePartnership(partnership.requiredId)
        return manning.forPartnership(partnership.requiredId).map { it.toDto() }
    }

    /** Replaces the ship's manning table with the one given — audited as one change. */
    @Transactional
    fun replace(abbrev: String, requirements: List<ManningRequirementDto>): List<ManningRequirementDto> {
        policy.require(Role.COMPLIANCE_LEAD, Role.SYSTEM_ADMINISTRATOR)
        policy.assertNotReadOnlyActor()
        val partnership = partnership(abbrev)
        policy.assertCanSeePartnership(partnership.requiredId)
        val before = manning.forPartnership(partnership.requiredId).map { it.toDto() }
        manning.delete("partnership.id = ?1", partnership.requiredId)
        val label = policy.actor().label
        val distinct = requirements.distinctBy { "${it.positionId}/${it.shift ?: ""}" }
        distinct.forEach { line ->
            require(line.required >= 0) { "A manning count cannot be negative" }
            require(line.shift == null || line.shift in SHIFTS) { "A shift is Shift 1, Shift 2, or the whole swing" }
            val position = positions.findById(line.positionId) ?: throw EntityNotFoundException("No position ${line.positionId}")
            manning.persist(
                ManningRequirement().apply {
                    this.partnership = partnership
                    this.position = position
                    shift = line.shift
                    required = line.required
                    stampCreated(label)
                },
            )
        }
        manning.flush()
        val after = manning.forPartnership(partnership.requiredId).map { it.toDto() }
        audit.record(entityType = "ManningRequirement", event = "manning.replaced", businessKey = partnership.abbrev, before = before, after = after)
        return after
    }

    /** The swing's roster against the manning table: who is standing in each position, and whether that is enough. */
    @Transactional
    fun check(abbrev: String, ccId: String): ManningCheckDto {
        val partnership = partnership(abbrev)
        policy.assertCanSeePartnership(partnership.requiredId)
        val crewChange = crewChanges.byBusinessKey(ccId, abbrev) ?: throw EntityNotFoundException("No swing $ccId on $abbrev")
        val roster = assignments.forCrewChangeUnscoped(crewChange.requiredId)
        val shiftOfSlot = slots.allOrdered().associate { it.ref to it.shift.wire }
        val lines = manning.forPartnership(partnership.requiredId).map { rule ->
            val standing = roster
                .filter { it.person.position.requiredId == rule.position.requiredId }
                .filter { rule.shift == null || shiftOfSlot[it.slotRef] == rule.shift || shiftOfSlot[it.slotRef] == "N/A" }
                .map { it.person.name }
                .distinct()
            ManningCheckLine(
                positionId = rule.position.requiredId,
                positionName = rule.position.name,
                shift = rule.shift,
                required = rule.required,
                standing = standing.size,
                names = standing,
                satisfied = standing.size >= rule.required,
            )
        }
        return ManningCheckDto(ccId = ccId, lines = lines, short = lines.count { !it.satisfied })
    }

    private fun partnership(abbrev: String): Partnership =
        partnerships.byAbbrev(abbrev) ?: throw EntityNotFoundException("No partnership $abbrev")

    private fun ManningRequirement.toDto() = ManningRequirementDto(position.requiredId, position.name, shift, required)

    companion object {
        val SHIFTS = setOf("Shift 1", "Shift 2")
    }
}

@Path("/api/v1/ops/manning")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
class ManningResource(private val manning: ManningService) {

    @GET
    @Path("/{partnership}")
    @Operation(summary = "The ship's minimum safe manning table")
    fun list(@PathParam("partnership") partnership: String): List<ManningRequirementDto> = manning.list(partnership)

    @PUT
    @Path("/{partnership}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Replace the ship's manning table — audited")
    fun replace(@PathParam("partnership") partnership: String, request: SetManningRequest): List<ManningRequirementDto> =
        manning.replace(partnership, request.requirements)

    @GET
    @Path("/{partnership}/{cc}/check")
    @Operation(summary = "A swing's roster against the manning table")
    fun check(@PathParam("partnership") partnership: String, @PathParam("cc") cc: String): ManningCheckDto = manning.check(partnership, cc)
}
