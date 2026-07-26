package au.crewcomp.reference

import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessPolicy
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

/**
 * Read access to the §4.1 reference layer: the partnerships, the swing calendar, the slot model
 * and the requirement catalogue.
 *
 * These are the lookups both frontends need before they can render anything — a cell state is a
 * requirement *code*, not a requirement id — so they are cacheable, mostly static, and read by
 * every authenticated role. The one exception is the partnership list, which a Vessel Master
 * sees narrowed to their own (§3).
 *
 * Catalogue **writes** (ADM-6, restricted to the Compliance Lead) are not implemented yet; when
 * they are, they belong here alongside the reads, with the usual role check → validate → mutate
 * → audit shape.
 */
@ApplicationScoped
class ReferenceService(
    private val partnershipRepository: PartnershipRepository,
    private val crewChangeRepository: CrewChangeRepository,
    private val requirementRepository: RequirementRepository,
    private val positionRepository: CrewPositionRepository,
    private val slotRepository: PositionSlotRepository,
    private val policy: AccessPolicy,
) {

    /** Partnerships the actor may see. A crew member sees none: their app is §7, not ADM-2. */
    @Transactional
    fun listPartnerships(): List<Partnership> =
        partnershipRepository.allOrdered().filter { policy.canSeePartnership(it.requiredId) }

    @Transactional
    fun partnershipByAbbrev(abbrev: String): Partnership {
        val partnership = partnershipRepository.byAbbrev(abbrev)
            ?: throw EntityNotFoundException("No partnership $abbrev")
        policy.assertCanSeePartnership(partnership.requiredId)
        return partnership
    }

    /**
     * The partnership's swing calendar, oldest first. ADM-2 scopes its crew-change selector to
     * the selected partnership's calendar rather than offering every CC id in the system.
     */
    @Transactional
    fun listCrewChanges(partnershipAbbrev: String): List<CrewChange> {
        val partnership = partnershipByAbbrev(partnershipAbbrev)
        return crewChangeRepository.forPartnership(partnership.requiredId)
    }

    /**
     * The whole requirement catalogue, retired entries included. ADM-6 filters by status in the
     * UI, and register history from before a requirement was retired still has to render its
     * code — so filtering retired rows out here would break the older screens.
     */
    @Transactional
    fun listRequirements(): List<Requirement> {
        policy.actor()
        return requirementRepository.listAll(io.quarkus.panache.common.Sort.by("code"))
    }

    @Transactional
    fun listPositions(): List<CrewPosition> {
        policy.actor()
        return positionRepository.allOrdered()
    }

    @Transactional
    fun listSlots(): List<PositionSlot> {
        policy.actor()
        return slotRepository.allOrdered()
    }
}
