package au.crewcomp.compliance

import au.crewcomp.engine.AssignmentId
import au.crewcomp.engine.AssignmentView
import au.crewcomp.engine.ConditionalRuleView
import au.crewcomp.engine.CrewChangeId
import au.crewcomp.engine.DependentRuleView
import au.crewcomp.engine.HoldingView
import au.crewcomp.engine.OneOfRuleView
import au.crewcomp.engine.PartnershipId
import au.crewcomp.engine.PartnershipView
import au.crewcomp.engine.PersonId
import au.crewcomp.engine.PersonView
import au.crewcomp.engine.PositionId
import au.crewcomp.engine.QuotaRuleView
import au.crewcomp.engine.RegisterRecordView
import au.crewcomp.engine.RequirementId
import au.crewcomp.engine.RequirementRuleView
import au.crewcomp.engine.SlotView
import au.crewcomp.engine.SwingWindow
import au.crewcomp.people.Assignment
import au.crewcomp.people.Person
import au.crewcomp.people.QualificationHolding
import au.crewcomp.reference.CrewChange
import au.crewcomp.reference.Partnership
import au.crewcomp.reference.PositionSlot
import au.crewcomp.rules.ConditionalKind
import au.crewcomp.rules.ConditionalMemberRole
import au.crewcomp.rules.ConditionalRule
import au.crewcomp.rules.QuotaRule
import au.crewcomp.rules.RequirementRule
import au.crewcomp.workflow.RegisterRecord

/**
 * Entity → engine value-type mapping.
 *
 * The §5 engine is a pure function of plain values, which is what lets its test suite state the
 * normative semantics without a database. This file is the only place that knows both worlds:
 * everything else works with entities on one side or engine views on the other.
 */

fun Partnership.toView() = PartnershipView(
    id = PartnershipId(requiredId),
    abbrev = abbrev,
    vesselClass = vesselClass,
)

fun Person.toView() = PersonView(
    id = PersonId(requiredId),
    sam = sam,
    name = name,
    positionId = PositionId(position.requiredId),
    tier = tier,
    homePartnershipId = PartnershipId(partnership.requiredId),
    status = status,
)

fun QualificationHolding.toView() = HoldingView(
    requirementId = RequirementId(requirement.requiredId),
    status = status,
    expiry = expiryDate,
)

fun PositionSlot.toView() = SlotView(
    ref = ref,
    shift = shift,
    allowedPositionIds = allowedPositions.map { PositionId(it.requiredId) }.toSet(),
    notes = notes,
)

fun Assignment.toView() = AssignmentView(
    id = AssignmentId(requiredId),
    personId = PersonId(person.requiredId),
    crewChangeId = CrewChangeId(crewChange.requiredId),
    partnershipId = PartnershipId(partnership.requiredId),
    slotRef = slotRef,
    from = fromDate,
    to = toDate,
)

fun CrewChange.toWindow() = SwingWindow(
    crewChangeId = CrewChangeId(requiredId),
    ccId = ccId,
    partnershipId = PartnershipId(partnership.requiredId),
    from = fromDate,
    to = toDate,
    cutoff = cutoffDate,
)

fun RequirementRule.toView() = RequirementRuleView(
    partnershipId = partnership?.let { PartnershipId(it.requiredId) },
    positionId = PositionId(position.requiredId),
    requirementId = RequirementId(requirement.requiredId),
    level = level,
)

fun QuotaRule.toView() = QuotaRuleView(
    footnote = footnote,
    requirementId = RequirementId(requirement.requiredId),
    min = minCount,
    scope = scope,
    positionIds = positions.takeIf { it.isNotEmpty() }?.map { PositionId(it.requiredId) }?.toSet(),
)

fun ConditionalRule.toView(): ConditionalRuleView = when (kind) {
    ConditionalKind.ONE_OF -> OneOfRuleView(
        positionId = PositionId(position.requiredId),
        requirementIds = requirementsWithRole(ConditionalMemberRole.MEMBER)
            .map { RequirementId(it.requiredId) },
        label = label,
    )

    ConditionalKind.DEPENDENT -> DependentRuleView(
        positionId = PositionId(position.requiredId),
        requirementId = RequirementId(
            checkNotNull(requirement) { "dependent conditional rule $id has no target requirement" }.requiredId,
        ),
        requiredIfHolds = requirementsWithRole(ConditionalMemberRole.REQUIRED_IF_HOLDS)
            .map { RequirementId(it.requiredId) },
        unlessHolds = requirementsWithRole(ConditionalMemberRole.UNLESS_HOLDS)
            .map { RequirementId(it.requiredId) },
    )
}

fun RegisterRecord.toView() = RegisterRecordView(
    recordId = recordId,
    requirementId = requirement?.let { RequirementId(it.requiredId) },
    outcome = outcome,
    approvalFrom = approvalFrom,
    approvalTo = approvalTo,
    open = isOpen,
)

/**
 * Indexes register records for the exemption overlay, keyed by (person, requirement).
 *
 * Records with no mapped requirement are skipped: `req_raw` preserves the legacy title for
 * display (§4.4), but an unmapped title cannot identify a cell, and guessing one would be
 * exactly the silent clean-up §11 forbids.
 *
 * Where several records exist for one cell in one swing, an approved record wins over an open
 * one, and an open one over a closed-but-not-approved one — the overlay should reflect the most
 * favourable *effective* position, and a closed unsuccessful record leaves the gap standing
 * anyway.
 */
fun List<RegisterRecord>.indexForOverlay(): Map<PersonId, Map<RequirementId, RegisterRecordView>> =
    asSequence()
        .filter { it.person != null && it.requirement != null }
        .groupBy { PersonId(it.person!!.requiredId) }
        .mapValues { (_, records) ->
            records
                .groupBy { RequirementId(it.requirement!!.requiredId) }
                .mapValues { (_, forRequirement) ->
                    forRequirement
                        .map { it.toView() }
                        .sortedWith(
                            compareByDescending<RegisterRecordView> { it.grantsExemption }
                                .thenByDescending { it.open },
                        )
                        .first()
                }
        }
