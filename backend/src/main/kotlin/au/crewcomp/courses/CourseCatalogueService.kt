package au.crewcomp.courses

import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.Role
import au.crewcomp.reference.RequirementRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.LocalDate

/**
 * The write path for the course catalogue — maintaining the dates MOB-8 offers.
 *
 * ### Why there is no ADM screen for this yet
 *
 * There deliberately is not one. Whether this catalogue is ours to maintain at all is the open
 * question [CourseCatalogue] exists to keep open, and a console screen is the most expensive thing
 * to build against an answer that may change. What a coordinator needs today is a way to *put dates
 * in*, and MCP-1 already provides one: an agent calling this service exercises the same role
 * checks, the same validation and the same audit writes the API would (MCP-2), because it is the
 * same service.
 *
 * If the answer settles on "ours", this is what an ADM screen calls. If it settles on a provider
 * feed, this is what the importer calls. Either way the seam holds.
 *
 * ### Withdrawn, never deleted
 *
 * [withdraw] deactivates. A crew statement points at an option by its ref, and a coordinator
 * reading "asked for a seat on 12–13 Aug at Fremantle" three weeks later needs that row to still
 * resolve — a deleted course turns a request into a key nobody can read.
 */
@ApplicationScoped
class CourseCatalogueService(
    private val options: CourseOptionRepository,
    private val requirements: RequirementRepository,
    private val policy: AccessPolicy,
    private val audit: AuditWriter,
) {

    /** Everything in the catalogue, including withdrawn rows — this is the maintenance view. */
    @Transactional
    fun list(): List<CatalogueOption> {
        policy.require(*READERS)
        return options.allOrdered().map { it.toCatalogueOption() }
    }

    /**
     * Creates or replaces one option, keyed on its business ref.
     *
     * Upsert rather than create-then-edit because the realistic write is a re-import: the same
     * dates arrive again with a new seat count, and a call that failed on the second delivery
     * would make maintaining this a manual reconciliation.
     */
    @Transactional
    fun upsert(
        optionRef: String,
        requirementCode: String,
        starts: LocalDate,
        finishes: LocalDate,
        provider: String,
        location: String,
        durationLabel: String?,
        seats: Int,
    ): CatalogueOption {
        policy.require(*WRITERS)
        require(optionRef.isNotBlank()) { "A course option needs a ref" }
        require(!finishes.isBefore(starts)) { "A course cannot finish before it starts" }
        require(seats >= 0) { "Seats cannot be negative" }

        val requirement = requirements.byCode(requirementCode)
            ?: throw EntityNotFoundException("No requirement $requirementCode")

        val existing = options.byRef(optionRef)
        val before = existing?.toCatalogueOption()?.let(::snapshot)

        val entity = (existing ?: CourseOptionEntity().apply { this.optionRef = optionRef }).apply {
            this.requirement = requirement
            this.starts = starts
            this.finishes = finishes
            this.provider = provider
            this.location = location
            this.durationLabel = durationLabel
            this.seats = seats
            // A re-imported option is a live one again. Withdrawal is an explicit act.
            this.active = true
            if (existing == null) stampCreated(policy.actor().label) else stampUpdated(policy.actor().label)
        }
        if (existing == null) options.persist(entity)

        val after = entity.toCatalogueOption()
        audit.record(
            entityType = "CourseOption",
            event = if (existing == null) "course_option.created" else "course_option.updated",
            entityId = entity.id,
            businessKey = optionRef,
            before = before,
            after = snapshot(after),
        )
        return after
    }

    /** Takes an option off the offer list without losing what it was. */
    @Transactional
    fun withdraw(optionRef: String): CatalogueOption {
        policy.require(*WRITERS)
        val entity = options.byRef(optionRef)
            ?: throw EntityNotFoundException("No course option $optionRef")
        val before = snapshot(entity.toCatalogueOption())

        entity.active = false
        entity.stampUpdated(policy.actor().label)

        val after = entity.toCatalogueOption()
        audit.record(
            entityType = "CourseOption",
            event = "course_option.withdrawn",
            entityId = entity.id,
            businessKey = optionRef,
            before = before,
            after = snapshot(after),
        )
        return after
    }

    private fun snapshot(option: CatalogueOption): Map<String, Any?> = mapOf(
        "starts" to option.starts.toString(),
        "finishes" to option.finishes.toString(),
        "provider" to option.provider,
        "location" to option.location,
        "seats" to option.seats,
        "active" to option.active,
    )

    private companion object {
        val READERS = arrayOf(
            Role.CREW_COORDINATOR, Role.WORKFLOW_MANAGER, Role.COMPLIANCE_LEAD,
            Role.DATA_STEWARD, Role.SYSTEM_ADMINISTRATOR,
        )

        /**
         * Arranging training is the Crew Coordinator's job — they are the ones who ring the
         * provider, and they are who a seat request is routed to in ADM-11.
         */
        val WRITERS = arrayOf(Role.CREW_COORDINATOR, Role.SYSTEM_ADMINISTRATOR)
    }
}
