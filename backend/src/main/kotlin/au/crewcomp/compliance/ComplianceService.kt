package au.crewcomp.compliance

import au.crewcomp.engine.ExpiryAlert
import au.crewcomp.engine.GapReportRow
import au.crewcomp.engine.PersonEvaluation
import au.crewcomp.engine.PersonEvaluator
import au.crewcomp.engine.PersonId
import au.crewcomp.engine.Planning
import au.crewcomp.engine.QuotaEvaluation
import au.crewcomp.engine.RequirementId
import au.crewcomp.engine.Suggestion
import au.crewcomp.engine.SuggestionWeights
import au.crewcomp.engine.SwingEvaluation
import au.crewcomp.engine.SwingEvaluationInputs
import au.crewcomp.engine.SwingEvaluator
import au.crewcomp.people.AssignmentRepository
import au.crewcomp.people.PersonRepository
import au.crewcomp.people.QualificationHoldingRepository
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.Role
import au.crewcomp.platform.security.ScopeGuard
import au.crewcomp.platform.time.BusinessClock
import au.crewcomp.reference.CrewChange
import au.crewcomp.reference.CrewChangeRepository
import au.crewcomp.reference.PositionSlotRepository
import au.crewcomp.workflow.RegisterRecordRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

/**
 * The application service around the §5 engine — the single entry point for compliance answers.
 *
 * §17.1's "bites now" requires exactly this: the engine, suggestion ranking and gap report are
 * clean service APIs rather than view-bound logic, so that the admin API, the MCP tools and a
 * later AI swing-planning assistant are all just consumers. Nothing below re-implements
 * semantics; it assembles inputs, calls the engine, and applies authorisation.
 */
@ApplicationScoped
class ComplianceService(
    private val crewChanges: CrewChangeRepository,
    private val slots: PositionSlotRepository,
    private val assignments: AssignmentRepository,
    private val people: PersonRepository,
    private val holdings: QualificationHoldingRepository,
    private val registerRecords: RegisterRecordRepository,
    private val matrixSnapshots: MatrixSnapshotService,
    private val policy: AccessPolicy,
    private val scopeGuard: ScopeGuard,
    private val clock: BusinessClock,
) {

    // -----------------------------------------------------------------------
    // Swing evaluation (§5.3)
    // -----------------------------------------------------------------------

    /**
     * Whole-swing evaluation for one (partnership, crew change).
     *
     * Restricted to the roles that plan and oversee swings: a crew member's view of their own
     * compliance is [evaluatePerson], which is row-scoped. A Vessel Master may evaluate swings
     * for their own partnerships only.
     */
    @Transactional
    fun evaluateSwing(
        partnershipAbbrev: String,
        ccId: String,
        pinnedMatrixVersionId: Long? = null,
    ): SwingEvaluation {
        policy.require(
            Role.CREW_COORDINATOR, Role.WORKFLOW_MANAGER, Role.COMPLIANCE_LEAD,
            Role.DATA_STEWARD, Role.VESSEL_MASTER, Role.SYSTEM_ADMINISTRATOR,
        )
        val crewChange = requireCrewChange(partnershipAbbrev, ccId)
        policy.assertCanSeePartnership(crewChange.partnership.requiredId)

        return SwingEvaluator.evaluate(inputsFor(crewChange, pinnedMatrixVersionId))
    }

    /** §5.3 quota evaluation on its own, for the planner's quota panel. */
    @Transactional
    fun quotas(
        partnershipAbbrev: String,
        ccId: String,
        pinnedMatrixVersionId: Long? = null,
    ): List<QuotaEvaluation> = evaluateSwing(partnershipAbbrev, ccId, pinnedMatrixVersionId).quotas

    /** §5.4 gap report: every non-`ok`/`na` cell, ordered as the worklist. */
    @Transactional
    fun gapReport(
        partnershipAbbrev: String,
        ccId: String,
        pinnedMatrixVersionId: Long? = null,
    ): List<GapReportRow> = Planning.gapReport(evaluateSwing(partnershipAbbrev, ccId, pinnedMatrixVersionId))

    // -----------------------------------------------------------------------
    // Person evaluation (§5.2)
    // -----------------------------------------------------------------------

    /**
     * One person's evaluation against a swing. Row-scoped: a crew member may call this for
     * themselves, which is what backs MOB-1's "what I need" view.
     */
    @Transactional
    fun evaluatePerson(
        personId: Long,
        partnershipAbbrev: String,
        ccId: String,
        pinnedMatrixVersionId: Long? = null,
    ): PersonEvaluation {
        val person = people.findById(personId)
            ?: throw IllegalArgumentException("No person $personId")
        // A load by primary key bypasses any query predicate, so the scope check is explicit.
        scopeGuard.assertVisible(personId, person.partnership.requiredId)

        val crewChange = requireCrewChange(partnershipAbbrev, ccId)
        val matrix = matrixSnapshots.forEvaluation(pinnedMatrixVersionId)

        return PersonEvaluator.evaluate(
            person = person.toView(),
            partnership = crewChange.partnership.toView(),
            swing = crewChange.toWindow(),
            matrix = matrix,
            holdings = holdings.forPersonScoped(personId).associate {
                RequirementId(it.requirement.requiredId) to it.toView()
            },
            registerRecords = registerRecords.forPersonScoped(personId)
                .filter { it.crewChange?.id == crewChange.id }
                .indexForOverlay()[PersonId(personId)]
                .orEmpty(),
        )
    }

    // -----------------------------------------------------------------------
    // Suggestions and alerts (§5.4)
    // -----------------------------------------------------------------------

    /** Ranked crew suggestions for an open slot. Planning is a coordinator activity. */
    @Transactional
    fun suggestions(
        partnershipAbbrev: String,
        ccId: String,
        slotRef: Int,
        weights: SuggestionWeights = SuggestionWeights(),
        limit: Int = 20,
    ): List<Suggestion> {
        policy.require(Role.CREW_COORDINATOR, Role.SYSTEM_ADMINISTRATOR)

        val crewChange = requireCrewChange(partnershipAbbrev, ccId)
        val slot = slots.byRef(slotRef)
            ?: throw IllegalArgumentException("No position slot with ref $slotRef")
        val inputs = inputsFor(crewChange, pinnedMatrixVersionId = null)

        val candidates = people.allActiveUnscoped()
        val candidateHoldings = holdings
            .forPeopleUnscoped(candidates.mapNotNull { it.id })
            .groupBy { PersonId(it.person.requiredId) }
            .mapValues { (_, list) -> list.associate { RequirementId(it.requirement.requiredId) to it.toView() } }

        return Planning.suggestCrew(
            slot = slot.toView(),
            // Candidates are not assigned to this swing, so their holdings are not in `inputs`.
            inputs = inputs.copy(
                people = inputs.people + candidates.associate { PersonId(it.requiredId) to it.toView() },
                holdings = inputs.holdings + candidateHoldings,
            ),
            candidates = candidates.map { it.toView() },
            otherAssignments = assignments
                .overlappingUnscoped(crewChange.fromDate, crewChange.toDate, crewChange.requiredId)
                .map { it.toView() },
            weights = weights,
            limit = limit,
        )
    }

    /**
     * §5.4 expiry alerts across all active people. Feeds the notification scan (§9) and ADM-1.
     * Crew members get their own alerts from [evaluatePerson] and the mobile sync path instead.
     */
    @Transactional
    fun expiryAlerts(leadDays: Long = Planning.DEFAULT_EXPIRY_LEAD_DAYS): List<ExpiryAlert> {
        policy.require(
            Role.CREW_COORDINATOR, Role.WORKFLOW_MANAGER, Role.COMPLIANCE_LEAD,
            Role.DATA_STEWARD, Role.SYSTEM_ADMINISTRATOR,
        )
        val today = clock.today()
        val activePeople = people.allActiveUnscoped()
        val personIds = activePeople.mapNotNull { it.id }

        return Planning.expiryAlerts(
            people = activePeople.map { it.toView() },
            holdings = holdings.forPeopleUnscoped(personIds)
                .groupBy { PersonId(it.person.requiredId) }
                .mapValues { (_, list) -> list.associate { RequirementId(it.requirement.requiredId) to it.toView() } },
            assignments = assignments.upcomingUnscoped(today).map { it.toView() },
            asOf = today,
            leadDays = leadDays,
        )
    }

    // -----------------------------------------------------------------------
    // Input assembly
    // -----------------------------------------------------------------------

    private fun requireCrewChange(partnershipAbbrev: String, ccId: String): CrewChange =
        crewChanges.byBusinessKey(ccId, partnershipAbbrev)
            ?: throw IllegalArgumentException("No crew change $ccId for partnership $partnershipAbbrev")

    private fun inputsFor(crewChange: CrewChange, pinnedMatrixVersionId: Long?): SwingEvaluationInputs {
        val swingAssignments = assignments.forCrewChangeUnscoped(crewChange.requiredId)
        val assignedPeople = swingAssignments.map { it.person }.distinctBy { it.requiredId }
        val personIds = assignedPeople.map { it.requiredId }

        return SwingEvaluationInputs(
            swing = crewChange.toWindow(),
            partnership = crewChange.partnership.toView(),
            matrix = matrixSnapshots.forEvaluation(pinnedMatrixVersionId),
            slots = slots.allOrdered().map { it.toView() },
            assignments = swingAssignments.map { it.toView() },
            people = assignedPeople.associate { PersonId(it.requiredId) to it.toView() },
            holdings = holdings.forPeopleUnscoped(personIds)
                .groupBy { PersonId(it.person.requiredId) }
                .mapValues { (_, list) -> list.associate { RequirementId(it.requirement.requiredId) to it.toView() } },
            registerRecords = registerRecords.forCrewChangeScoped(crewChange.requiredId).indexForOverlay(),
        )
    }
}
