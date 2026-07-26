package au.crewcomp.engine

import java.time.LocalDate

/**
 * Shared fixtures for the §5 engine suite.
 *
 * Deliberately small and hand-built: these tests are the executable statement of the normative
 * semantics (DEV-2), so every input a test depends on should be visible in the test itself.
 */
object Fx {

    // Positions
    val MASTER = PositionId(1)
    val CHIEF_OFFICER = PositionId(2)
    val GPH = PositionId(3)
    val AE = PositionId(4)
    val COOK = PositionId(5)

    // Requirements
    val QL01 = RequirementId(101)   // CoC — Master Unlimited
    val QL02 = RequirementId(102)   // CoC — Chief Officer Unlimited
    val QL03 = RequirementId(103)   // CoC — Chief Officer 100m
    val QL08 = RequirementId(108)   // GPH qualification A
    val QL09 = RequirementId(109)   // GPH qualification B
    val COST = RequirementId(120)   // Certificate of Recognition support training
    val CoR = RequirementId(121)    // Certificate of Recognition (non-STCW)
    val STCW = RequirementId(122)   // STCW certificate
    val FRC = RequirementId(130)    // Fast Rescue Craft
    val WAH = RequirementId(131)    // Work at Heights
    val MED = RequirementId(140)    // Medical

    // Partnerships
    val NOR = PartnershipView(PartnershipId(1), "NOR", vesselClass = null)
    val UNI = PartnershipView(PartnershipId(2), "UNI", vesselClass = "100m")

    val SWING: SwingWindow = SwingWindow(
        crewChangeId = CrewChangeId(24),
        ccId = "CC24",
        partnershipId = NOR.id,
        from = LocalDate.of(2026, 8, 1),
        to = LocalDate.of(2026, 8, 28),
        cutoff = LocalDate.of(2026, 7, 25),
    )

    fun person(
        id: Long = 1,
        name: String = "Test Person",
        position: PositionId = MASTER,
        tier: String? = null,
        home: PartnershipId = NOR.id,
        status: PersonStatus = PersonStatus.ACTIVE,
    ) = PersonView(PersonId(id), sam = "SAM$id", name = name, positionId = position, tier = tier, homePartnershipId = home, status = status)

    fun held(requirement: RequirementId, expiry: LocalDate) =
        HoldingView(requirement, HoldingStatus.HELD_EXPIRY, expiry)

    fun perpetual(requirement: RequirementId) =
        HoldingView(requirement, HoldingStatus.HELD_PERPETUAL, null)

    fun notHeld(requirement: RequirementId) =
        HoldingView(requirement, HoldingStatus.NOT_HELD, null)

    fun unknown(requirement: RequirementId) =
        HoldingView(requirement, HoldingStatus.UNKNOWN, null)

    fun holdings(vararg holdings: HoldingView): Map<RequirementId, HoldingView> =
        holdings.associateBy { it.requirementId }

    fun rule(position: PositionId, requirement: RequirementId, level: String, partnership: PartnershipId? = null) =
        RequirementRuleView(partnership, position, requirement, RuleLevel(level))

    fun matrix(
        rules: List<RequirementRuleView> = emptyList(),
        conditionals: List<ConditionalRuleView> = emptyList(),
        quotas: List<QuotaRuleView> = emptyList(),
        tierFootnote: String? = null,
        tierPolicy: Map<String, Set<String>> = emptyMap(),
        label: String = "v1",
        id: Long = 1,
    ) = MatrixSnapshot(MatrixVersionId(id), label, rules, conditionals, quotas, tierFootnote, tierPolicy)

    fun approved(recordId: String = "NORCC24-001", from: LocalDate = SWING.from, to: LocalDate = SWING.to) =
        RegisterRecordView(recordId, null, RegisterOutcome.APPROVED, from, to, open = false)

    fun open(recordId: String = "NORCC24-002") =
        RegisterRecordView(recordId, null, null, null, null, open = true)

    fun assignment(
        id: Long,
        person: PersonId,
        slotRef: Int,
        swing: SwingWindow = SWING,
        from: LocalDate = swing.from,
        to: LocalDate = swing.to,
    ) = AssignmentView(AssignmentId(id), person, swing.crewChangeId, swing.partnershipId, slotRef, from, to)

    // Overloads rather than a vararg: Kotlin prohibits vararg parameters of an inline value class.
    fun slot(ref: Int, shift: Shift, positions: Collection<PositionId>) =
        SlotView(ref, shift, positions.toSet())

    fun slot(ref: Int, shift: Shift, position: PositionId) =
        slot(ref, shift, listOf(position))

    fun slot(ref: Int, shift: Shift, first: PositionId, second: PositionId) =
        slot(ref, shift, listOf(first, second))
}
