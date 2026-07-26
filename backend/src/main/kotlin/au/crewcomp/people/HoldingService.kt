package au.crewcomp.people

import au.crewcomp.engine.HoldingStatus
import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.Role
import au.crewcomp.reference.RequirementRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.LocalDate

/**
 * The validated write path for qualification holdings — this system is the system of record for
 * them (§4.3, Q9).
 *
 * Everything that changes a holding comes through here: the admin API, the MCP tools, and later
 * the evidence pipeline's decide/verify stages (§8, LLM-1). There is deliberately no second
 * route, because AUTH-1's "the backend re-validates every write" and AIA-2's "AI proposes, an
 * authorised human disposes" both rest on there being exactly one door.
 */
@ApplicationScoped
class HoldingService(
    private val holdings: QualificationHoldingRepository,
    private val people: PersonRepository,
    private val requirements: RequirementRepository,
    private val policy: AccessPolicy,
    private val audit: AuditWriter,
) {

    /**
     * Sets a person's holding for a requirement, creating it if absent.
     *
     * @param evidenceDocumentId links the change to the evidence that justified it (§8); every
     *   pipeline-driven write carries one, and it lands in the audit event.
     */
    @Transactional
    fun setHolding(
        personId: Long,
        requirementId: Long,
        status: HoldingStatus,
        expiry: LocalDate? = null,
        issueDate: LocalDate? = null,
        note: String? = null,
        evidenceDocumentId: Long? = null,
    ): QualificationHolding {
        policy.require(Role.DATA_STEWARD, Role.CREW_COORDINATOR, Role.SYSTEM_ADMINISTRATOR)

        validate(status, expiry, issueDate)

        val person = people.findById(personId)
            ?: throw IllegalArgumentException("No person $personId")
        val requirement = requirements.findById(requirementId)
            ?: throw IllegalArgumentException("No requirement $requirementId")

        val actor = policy.actor()
        val existing = holdings.find(personId, requirementId)
        val before = existing?.let { snapshot(it) }

        val holding = existing ?: QualificationHolding().apply {
            this.person = person
            this.requirement = requirement
            stampCreated(actor.label)
        }

        holding.status = status
        holding.expiryDate = if (status == HoldingStatus.HELD_EXPIRY) expiry else null
        holding.issueDate = issueDate
        holding.note = note
        holding.stampUpdated(actor.label)

        if (existing == null) holdings.persist(holding)

        audit.record(
            entityType = "QualificationHolding",
            event = if (existing == null) "holding.created" else "holding.updated",
            entityId = holding.id,
            businessKey = "${person.sam}/${requirement.code}",
            before = before,
            after = snapshot(holding) + mapOfNotNull("evidenceDocumentId" to evidenceDocumentId),
        )

        return holding
    }

    /**
     * Validates the invariants the schema also enforces, so that a bad request fails as a
     * validation error with a useful message rather than as a constraint violation.
     */
    private fun validate(status: HoldingStatus, expiry: LocalDate?, issueDate: LocalDate?) {
        if (status == HoldingStatus.HELD_EXPIRY) {
            requireNotNull(expiry) { "An expiring holding must carry an expiry date" }
        } else {
            require(expiry == null) { "Only a 'held_expiry' holding may carry an expiry date" }
        }
        if (issueDate != null && expiry != null) {
            require(!issueDate.isAfter(expiry)) { "Issue date $issueDate is after expiry date $expiry" }
        }
    }

    private fun snapshot(holding: QualificationHolding): Map<String, Any?> = mapOf(
        "status" to holding.status.wire,
        "expiry" to holding.expiryDate?.toString(),
        "issueDate" to holding.issueDate?.toString(),
        "note" to holding.note,
    )

    private fun mapOfNotNull(vararg pairs: Pair<String, Any?>): Map<String, Any?> =
        pairs.filter { it.second != null }.toMap()
}
