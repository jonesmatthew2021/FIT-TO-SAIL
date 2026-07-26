package au.crewcomp.reference

import au.crewcomp.people.QualificationHoldingRepository
import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.Role
import au.crewcomp.rules.ConditionalRuleRepository
import au.crewcomp.rules.QuotaRuleRepository
import au.crewcomp.rules.RequirementRuleRepository
import au.crewcomp.workflow.RegisterRecordRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

/**
 * How many places a catalogue entry is load-bearing (ADM-6).
 *
 * Counted across every matrix version rather than only the published one: the question the
 * number answers is "may I retire this", and a superseded version is still the audit evidence
 * for how a past swing was evaluated.
 */
data class RequirementUsage(
    val requirementId: Long,
    val holdings: Long,
    val requirementRules: Long,
    val quotaRules: Long,
    val conditionalRules: Long,
    val registerRecords: Long,
) {
    val total: Long get() = holdings + requirementRules + quotaRules + conditionalRules + registerRecords
}

/**
 * Read access to the §4.1 reference layer: the partnerships, the swing calendar, the slot model
 * and the requirement catalogue.
 *
 * These are the lookups both frontends need before they can render anything — a cell state is a
 * requirement *code*, not a requirement id — so they are cacheable, mostly static, and read by
 * every authenticated role. The one exception is the partnership list, which a Vessel Master
 * sees narrowed to their own (§3).
 *
 * Catalogue **writes** (ADM-6) live here too, alongside the reads, with the usual role check →
 * validate → mutate → audit shape. They are restricted to the Compliance Lead: the catalogue is
 * the vocabulary every matrix rule, holding and register record is written in, so renaming an
 * entry silently changes what a hundred other rows mean.
 */
@ApplicationScoped
class ReferenceService(
    private val partnershipRepository: PartnershipRepository,
    private val crewChangeRepository: CrewChangeRepository,
    private val requirementRepository: RequirementRepository,
    private val positionRepository: CrewPositionRepository,
    private val slotRepository: PositionSlotRepository,
    private val holdingRepository: QualificationHoldingRepository,
    private val requirementRuleRepository: RequirementRuleRepository,
    private val quotaRuleRepository: QuotaRuleRepository,
    private val conditionalRuleRepository: ConditionalRuleRepository,
    private val registerRecordRepository: RegisterRecordRepository,
    private val policy: AccessPolicy,
    private val audit: AuditWriter,
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

    @Transactional
    fun requirement(requirementId: Long): Requirement {
        policy.actor()
        return requireWithAliases(requirementId)
    }

    /** The catalogue with aliases attached — ADM-6's list, and the shape safe to map after commit. */
    @Transactional
    fun listRequirementsWithAliases(): List<Requirement> {
        policy.actor()
        return requirementRepository.allWithAliases()
    }

    // -----------------------------------------------------------------------
    // Catalogue writes — ADM-6, Compliance Lead only
    // -----------------------------------------------------------------------

    /**
     * Usage across the whole system, in five grouped queries rather than five per requirement.
     * Returns an entry for every catalogue row, zeros included: "used nowhere" is the answer the
     * screen most needs, and an absent key would make it look like a lookup failure.
     */
    @Transactional
    fun requirementUsage(): List<RequirementUsage> {
        policy.actor()
        val holdings = holdingRepository.countByRequirementUnscoped()
        val requirementRules = requirementRuleRepository.countByRequirement()
        val quotaRules = quotaRuleRepository.countByRequirement()
        val conditionalRules = conditionalRuleRepository.countByRequirement()
        val registerRecords = registerRecordRepository.countByRequirement()

        return requirementRepository.listAll().map { requirement ->
            val id = requirement.requiredId
            RequirementUsage(
                requirementId = id,
                holdings = holdings[id] ?: 0,
                requirementRules = requirementRules[id] ?: 0,
                quotaRules = quotaRules[id] ?: 0,
                conditionalRules = conditionalRules[id] ?: 0,
                registerRecords = registerRecords[id] ?: 0,
            )
        }
    }

    @Transactional
    fun createRequirement(
        code: String,
        category: String,
        title: String,
        issuingAuthority: String? = null,
        notes: String? = null,
    ): Requirement {
        policy.require(Role.COMPLIANCE_LEAD, Role.SYSTEM_ADMINISTRATOR)

        val cleanCode = validateCode(code)
        validateCategory(category)
        val cleanTitle = validateTitle(title)
        require(requirementRepository.byCode(cleanCode) == null) {
            "Requirement code $cleanCode is already in the catalogue"
        }

        val requirement = Requirement().apply {
            this.code = cleanCode
            this.category = category
            this.title = cleanTitle
            this.issuingAuthority = issuingAuthority?.ifBlank { null }
            this.notes = notes?.ifBlank { null }
            status = "active"
            stampCreated(policy.actor().label)
        }
        requirementRepository.persist(requirement)

        audit.record(
            entityType = "Requirement",
            event = "requirement.created",
            entityId = requirement.id,
            businessKey = cleanCode,
            after = snapshot(requirement),
        )
        return requirement
    }

    /**
     * Edits a catalogue entry. The **code is immutable**: it is the business key every matrix
     * rule, CSV export and register record is written against, and renaming it in place would
     * silently rewrite history rather than record a change. A miscoded entry is retired and
     * replaced, which is what leaves a trail.
     */
    @Transactional
    fun updateRequirement(
        requirementId: Long,
        category: String,
        title: String,
        issuingAuthority: String?,
        notes: String?,
        status: String,
    ): Requirement {
        policy.require(Role.COMPLIANCE_LEAD, Role.SYSTEM_ADMINISTRATOR)

        validateCategory(category)
        val cleanTitle = validateTitle(title)
        require(status == "active" || status == "retired") {
            "A requirement is either active or retired, not '$status'"
        }

        val requirement = requireWithAliases(requirementId)
        val before = snapshot(requirement)
        val statusChanged = requirement.status != status

        requirement.category = category
        requirement.title = cleanTitle
        requirement.issuingAuthority = issuingAuthority?.ifBlank { null }
        requirement.notes = notes?.ifBlank { null }
        requirement.status = status
        requirement.stampUpdated(policy.actor().label)

        audit.record(
            entityType = "Requirement",
            // Retirement is the consequential act, so it gets its own event name rather than
            // hiding inside a generic update that nobody would think to search for.
            event = when {
                !statusChanged -> "requirement.updated"
                status == "retired" -> "requirement.retired"
                else -> "requirement.reactivated"
            },
            entityId = requirement.id,
            businessKey = requirement.code,
            before = before,
            after = snapshot(requirement),
        )
        return requirement
    }

    /**
     * Adds a legacy free-text title that maps to this requirement (§4.1).
     *
     * Aliases are how the register's 445 rows of historic free text join the catalogue, and how
     * the evidence pipeline matches a document naming a certificate by its old name (§8 stage 3).
     * They are added, never edited: an alias is a historical fact about what something used to be
     * called.
     */
    @Transactional
    fun addRequirementAlias(requirementId: Long, alias: String): Requirement {
        policy.require(Role.COMPLIANCE_LEAD, Role.SYSTEM_ADMINISTRATOR)

        val clean = alias.trim()
        require(clean.isNotEmpty()) { "An alias cannot be blank" }

        val requirement = requireWithAliases(requirementId)
        require(requirement.aliases.none { it.alias.equals(clean, ignoreCase = true) }) {
            "${requirement.code} already has the alias '$clean'"
        }
        // Matching is case-insensitive across the whole catalogue (RequirementRepository.matching),
        // so an alias that already points elsewhere would make a document match two entries and
        // land in review forever.
        val elsewhere = requirementRepository.matching(clean).firstOrNull { it.requiredId != requirementId }
        require(elsewhere == null) {
            "'$clean' already matches ${elsewhere?.code}, and an alias that matches two entries " +
                "makes every document naming it ambiguous"
        }

        requirement.aliases.add(
            RequirementAlias().apply {
                this.requirement = requirement
                this.alias = clean
                stampCreated(policy.actor().label)
            },
        )
        requirement.stampUpdated(policy.actor().label)

        audit.record(
            entityType = "Requirement",
            event = "requirement.alias_added",
            entityId = requirement.id,
            businessKey = requirement.code,
            after = mapOf("alias" to clean),
        )
        return requirement
    }

    @Transactional
    fun removeRequirementAlias(requirementId: Long, aliasId: Long): Requirement {
        policy.require(Role.COMPLIANCE_LEAD, Role.SYSTEM_ADMINISTRATOR)

        val requirement = requireWithAliases(requirementId)
        val alias = requirement.aliases.firstOrNull { it.id == aliasId }
            ?: throw EntityNotFoundException("No alias $aliasId on ${requirement.code}")

        requirement.aliases.remove(alias)
        requirement.stampUpdated(policy.actor().label)

        audit.record(
            entityType = "Requirement",
            event = "requirement.alias_removed",
            entityId = requirement.id,
            businessKey = requirement.code,
            before = mapOf("alias" to alias.alias),
        )
        return requirement
    }

    // -----------------------------------------------------------------------
    // Validation
    // -----------------------------------------------------------------------

    private fun requireWithAliases(requirementId: Long): Requirement =
        requirementRepository.withAliases(requirementId)
            ?: throw EntityNotFoundException("No requirement $requirementId")

    private fun validateCode(code: String): String {
        val clean = code.trim().uppercase()
        require(clean.isNotEmpty()) { "A requirement code cannot be blank" }
        // Mirrors the shape the catalogue already uses (QL-01, PS-04). Enforced so a typo does
        // not create a second vocabulary nobody can search.
        require(CODE_SHAPE.matches(clean)) {
            "A requirement code looks like 'PS-04' — a category prefix, a hyphen and digits — not '$clean'"
        }
        return clean
    }

    private fun validateCategory(category: String) {
        require(category in CATEGORIES) {
            "'$category' is not an Appendix A category; the categories are ${CATEGORIES.joinToString(", ")}"
        }
    }

    private fun validateTitle(title: String): String {
        val clean = title.trim()
        require(clean.isNotEmpty()) { "A requirement needs a title" }
        return clean
    }

    private fun snapshot(requirement: Requirement): Map<String, Any?> = mapOf(
        "code" to requirement.code,
        "category" to requirement.category,
        "title" to requirement.title,
        "status" to requirement.status,
        "issuingAuthority" to requirement.issuingAuthority,
        "notes" to requirement.notes,
    )

    companion object {
        /**
         * Appendix A, verbatim. Duplicated from the database CHECK constraint deliberately: the
         * constraint is the backstop, and this is what produces a usable message instead of a
         * 500 from a constraint violation.
         *
         * What these expand to is not recorded anywhere and is a question for the client — see
         * the ADM-6 follow-up in `admin-web/CLAUDE.md`. Inventing expansions would put a wrong
         * label in front of people who know the right one.
         */
        val CATEGORIES = listOf("QL", "VS", "PS", "MS", "CS", "HR", "PT", "VI", "PI")

        private val CODE_SHAPE = Regex("^[A-Z]{2}-[0-9]{1,3}$")
    }
}
