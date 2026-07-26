package au.crewcomp.engine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** Spec §5.3 — whole-swing evaluation and quota rules. */
@DisplayName("§5.3 swing evaluation")
class SwingEvaluatorTest {

    private val swing = Fx.SWING

    private val slots = listOf(
        Fx.slot(1, Shift.SHIFT_1, Fx.MASTER),
        Fx.slot(2, Shift.SHIFT_2, Fx.MASTER),
        Fx.slot(15, Shift.SHIFT_1, Fx.AE, Fx.GPH),
        Fx.slot(16, Shift.SHIFT_2, Fx.AE, Fx.GPH),
        Fx.slot(17, Shift.NOT_APPLICABLE, Fx.COOK),
    )

    private fun inputs(
        matrix: MatrixSnapshot = Fx.matrix(),
        assignments: List<AssignmentView> = emptyList(),
        people: List<PersonView> = emptyList(),
        holdings: Map<PersonId, Map<RequirementId, HoldingView>> = emptyMap(),
        registerRecords: Map<PersonId, Map<RequirementId, RegisterRecordView>> = emptyMap(),
    ) = SwingEvaluationInputs(
        swing = swing,
        partnership = Fx.NOR,
        matrix = matrix,
        slots = slots,
        assignments = assignments,
        people = people.associateBy { it.id },
        holdings = holdings,
        registerRecords = registerRecords,
    )

    @Nested
    @DisplayName("slots and assignments")
    inner class Slots {

        @Test
        fun `unassigned slots are reported as open`() {
            val master = Fx.person(1, position = Fx.MASTER)
            val evaluation = SwingEvaluator.evaluate(
                inputs(assignments = listOf(Fx.assignment(1, master.id, slotRef = 1)), people = listOf(master)),
            )

            assertThat(evaluation.openSlots.map { it.ref }).containsExactly(2, 15, 16, 17)
        }

        @Test
        fun `sequential assignments in one slot are each evaluated`() {
            val first = Fx.person(1, name = "First", position = Fx.MASTER)
            val second = Fx.person(2, name = "Second", position = Fx.MASTER)
            val mid = swing.from.plusDays(13)

            val evaluation = SwingEvaluator.evaluate(
                inputs(
                    matrix = Fx.matrix(rules = listOf(Fx.rule(Fx.MASTER, Fx.MED, "M"))),
                    assignments = listOf(
                        Fx.assignment(1, first.id, 1, from = swing.from, to = mid),
                        Fx.assignment(2, second.id, 1, from = mid.plusDays(1), to = swing.to),
                    ),
                    people = listOf(first, second),
                ),
            )

            assertThat(evaluation.assignments).hasSize(2)
            assertThat(evaluation.assignments.map { it.person.name }).containsExactly("First", "Second")
            assertThat(evaluation.openSlots.map { it.ref }).doesNotContain(1)
        }

        @Test
        fun `a handover pair that spans the whole swing is not flagged as partially covered`() {
            val first = Fx.person(1, position = Fx.MASTER)
            val second = Fx.person(2, position = Fx.MASTER)
            val mid = swing.from.plusDays(13)

            val evaluation = SwingEvaluator.evaluate(
                inputs(
                    assignments = listOf(
                        Fx.assignment(1, first.id, 1, from = swing.from, to = mid),
                        Fx.assignment(2, second.id, 1, from = mid.plusDays(1), to = swing.to),
                    ),
                    people = listOf(first, second),
                ),
            )

            assertThat(evaluation.partiallyCoveredSlots).isEmpty()
        }

        @Test
        fun `a slot assigned for only part of the swing is flagged as partially covered`() {
            val master = Fx.person(1, position = Fx.MASTER)
            val evaluation = SwingEvaluator.evaluate(
                inputs(
                    assignments = listOf(
                        Fx.assignment(1, master.id, 1, from = swing.from, to = swing.to.minusDays(5)),
                    ),
                    people = listOf(master),
                ),
            )

            assertThat(evaluation.partiallyCoveredSlots.map { it.ref }).containsExactly(1)
        }

        @Test
        fun `cell states are aggregated across all assignments`() {
            val a = Fx.person(1, name = "A", position = Fx.MASTER)
            val b = Fx.person(2, name = "B", position = Fx.MASTER)

            val evaluation = SwingEvaluator.evaluate(
                inputs(
                    matrix = Fx.matrix(rules = listOf(Fx.rule(Fx.MASTER, Fx.MED, "M"))),
                    assignments = listOf(Fx.assignment(1, a.id, 1), Fx.assignment(2, b.id, 2)),
                    people = listOf(a, b),
                    holdings = mapOf(
                        a.id to Fx.holdings(Fx.perpetual(Fx.MED)),
                        b.id to Fx.holdings(Fx.notHeld(Fx.MED)),
                    ),
                ),
            )

            assertThat(evaluation.countOf(CellState.OK)).isEqualTo(1)
            assertThat(evaluation.countOf(CellState.GAP)).isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("quota rules")
    inner class Quotas {

        private val frcQuota = QuotaRuleView("M7", Fx.FRC, min = 4, scope = QuotaScope.SWING)

        @Test
        fun `swing-scoped quota counts people whose holding is valid through swing end`() {
            val crew = (1L..4L).map { Fx.person(it, name = "Crew $it", position = Fx.MASTER) }
            val evaluation = SwingEvaluator.evaluate(
                inputs(
                    matrix = Fx.matrix(quotas = listOf(frcQuota)),
                    assignments = crew.mapIndexed { i, p -> Fx.assignment(i + 1L, p.id, slotRef = i + 1) },
                    people = crew,
                    holdings = crew.associate { it.id to Fx.holdings(Fx.perpetual(Fx.FRC)) },
                ),
            )

            val quota = evaluation.quotas.single()
            assertThat(quota.actual).isEqualTo(4)
            assertThat(quota.satisfied).isTrue()
            assertThat(quota.shortfall).isZero()
        }

        @Test
        fun `a holding expiring mid-swing does not count toward a quota`() {
            val crew = (1L..4L).map { Fx.person(it, name = "Crew $it", position = Fx.MASTER) }
            val evaluation = SwingEvaluator.evaluate(
                inputs(
                    matrix = Fx.matrix(quotas = listOf(frcQuota)),
                    assignments = crew.mapIndexed { i, p -> Fx.assignment(i + 1L, p.id, slotRef = i + 1) },
                    people = crew,
                    holdings = crew.associate { person ->
                        val holding = if (person.id.value == 4L) {
                            Fx.held(Fx.FRC, swing.to.minusDays(1))
                        } else {
                            Fx.perpetual(Fx.FRC)
                        }
                        person.id to Fx.holdings(holding)
                    },
                ),
            )

            val quota = evaluation.quotas.single()
            assertThat(quota.actual).isEqualTo(3)
            assertThat(quota.shortfall).isEqualTo(1)
            assertThat(evaluation.quotaShortfalls).hasSize(1)
        }

        @Test
        fun `a position-filtered quota only counts people in those positions`() {
            val gph = Fx.person(1, position = Fx.GPH)
            val ae = Fx.person(2, position = Fx.AE)
            val rule = QuotaRuleView("M9", Fx.WAH, min = 1, scope = QuotaScope.SWING, positionIds = setOf(Fx.GPH))

            val evaluation = SwingEvaluator.evaluate(
                inputs(
                    matrix = Fx.matrix(quotas = listOf(rule)),
                    assignments = listOf(Fx.assignment(1, gph.id, 15), Fx.assignment(2, ae.id, 16)),
                    people = listOf(gph, ae),
                    holdings = mapOf(
                        gph.id to Fx.holdings(Fx.perpetual(Fx.WAH)),
                        ae.id to Fx.holdings(Fx.perpetual(Fx.WAH)),
                    ),
                ),
            )

            assertThat(evaluation.quotas.single().actual).isEqualTo(1)
        }

        @Test
        fun `shift-scoped quotas are evaluated once per shift`() {
            val rule = QuotaRuleView("M9", Fx.WAH, min = 1, scope = QuotaScope.SHIFT, positionIds = setOf(Fx.GPH))
            val gph = Fx.person(1, position = Fx.GPH)

            val evaluation = SwingEvaluator.evaluate(
                inputs(
                    matrix = Fx.matrix(quotas = listOf(rule)),
                    assignments = listOf(Fx.assignment(1, gph.id, slotRef = 15)),  // Shift 1
                    people = listOf(gph),
                    holdings = mapOf(gph.id to Fx.holdings(Fx.perpetual(Fx.WAH))),
                ),
            )

            assertThat(evaluation.quotas).hasSize(2)
            assertThat(evaluation.quotas.map { it.shift }).containsExactly(Shift.SHIFT_1, Shift.SHIFT_2)
            assertThat(evaluation.quotas.first { it.shift == Shift.SHIFT_1 }.satisfied).isTrue()
            assertThat(evaluation.quotas.first { it.shift == Shift.SHIFT_2 }.satisfied).isFalse()
        }

        @Test
        fun `an N-A shift slot counts toward both shifts`() {
            val rule = QuotaRuleView("M7", Fx.FRC, min = 1, scope = QuotaScope.SHIFT)
            val cook = Fx.person(1, position = Fx.COOK)

            val evaluation = SwingEvaluator.evaluate(
                inputs(
                    matrix = Fx.matrix(quotas = listOf(rule)),
                    assignments = listOf(Fx.assignment(1, cook.id, slotRef = 17)),  // N/A shift
                    people = listOf(cook),
                    holdings = mapOf(cook.id to Fx.holdings(Fx.perpetual(Fx.FRC))),
                ),
            )

            assertThat(evaluation.quotas.map { it.satisfied }).containsExactly(true, true)
        }

        @Test
        fun `a person assigned twice in the same swing counts once`() {
            val rule = QuotaRuleView("M7", Fx.FRC, min = 2, scope = QuotaScope.SWING)
            val person = Fx.person(1, position = Fx.MASTER)
            val mid = swing.from.plusDays(13)

            val evaluation = SwingEvaluator.evaluate(
                inputs(
                    matrix = Fx.matrix(quotas = listOf(rule)),
                    assignments = listOf(
                        Fx.assignment(1, person.id, 1, from = swing.from, to = mid),
                        Fx.assignment(2, person.id, 2, from = mid.plusDays(1), to = swing.to),
                    ),
                    people = listOf(person),
                    holdings = mapOf(person.id to Fx.holdings(Fx.perpetual(Fx.FRC))),
                ),
            )

            assertThat(evaluation.quotas.single().actual).isEqualTo(1)
        }

        @Test
        fun `an exemption does not satisfy a quota - the quota asks about real holdings`() {
            val rule = QuotaRuleView("M7", Fx.FRC, min = 1, scope = QuotaScope.SWING)
            val person = Fx.person(1, position = Fx.MASTER)

            val evaluation = SwingEvaluator.evaluate(
                inputs(
                    matrix = Fx.matrix(quotas = listOf(rule)),
                    assignments = listOf(Fx.assignment(1, person.id, 1)),
                    people = listOf(person),
                    holdings = mapOf(person.id to Fx.holdings(Fx.notHeld(Fx.FRC))),
                    registerRecords = mapOf(person.id to mapOf(Fx.FRC to Fx.approved())),
                ),
            )

            assertThat(evaluation.quotas.single().satisfied).isFalse()
        }
    }

    /**
     * §11 acceptance test shape: the known real quota shortfall in the validated dataset —
     * UNI CC24, 0 of 1 required GPH with Work at Heights on Shift 1.
     *
     * Reconstructed here from synthetic data with the same structure. The migration acceptance
     * test (P1) re-runs this against the real extract and diffs against the POC rendering.
     */
    @Test
    @DisplayName("known shortfall: UNI CC24 — 0/1 GPH with Work at Heights on Shift 1")
    fun knownShortfall() {
        val uniSwing = swing.copy(partnershipId = Fx.UNI.id, ccId = "CC24")
        val shift1Gph = Fx.person(1, name = "Shift-1 GPH", position = Fx.GPH)
        val shift2Gph = Fx.person(2, name = "Shift-2 GPH", position = Fx.GPH)

        val evaluation = SwingEvaluator.evaluate(
            SwingEvaluationInputs(
                swing = uniSwing,
                partnership = Fx.UNI,
                matrix = Fx.matrix(
                    rules = listOf(Fx.rule(Fx.GPH, Fx.WAH, "M9")),
                    quotas = listOf(
                        QuotaRuleView("M9", Fx.WAH, min = 1, scope = QuotaScope.SHIFT, positionIds = setOf(Fx.GPH)),
                    ),
                ),
                slots = slots,
                assignments = listOf(
                    Fx.assignment(1, shift1Gph.id, slotRef = 15, swing = uniSwing),
                    Fx.assignment(2, shift2Gph.id, slotRef = 16, swing = uniSwing),
                ),
                people = listOf(shift1Gph, shift2Gph).associateBy { it.id },
                holdings = mapOf(
                    shift1Gph.id to Fx.holdings(Fx.notHeld(Fx.WAH)),
                    shift2Gph.id to Fx.holdings(Fx.perpetual(Fx.WAH)),
                ),
            ),
        )

        val shortfall = evaluation.quotaShortfalls.single()
        assertThat(shortfall.shift).isEqualTo(Shift.SHIFT_1)
        assertThat(shortfall.actual).isZero()
        assertThat(shortfall.min).isEqualTo(1)

        // The individual cell is quota-only, not an individual gap — the shortfall lives at
        // swing level, exactly as the client reasons about it.
        val cell = evaluation.assignments.first { it.person.id == shift1Gph.id }.evaluation.cell(Fx.WAH)!!
        assertThat(cell.state).isEqualTo(CellState.QUOTA_ONLY)
    }
}
