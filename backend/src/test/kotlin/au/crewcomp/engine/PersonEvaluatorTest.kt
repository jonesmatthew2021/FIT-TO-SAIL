package au.crewcomp.engine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** Spec §5.2 — person evaluation post-passes and roll-up. */
@DisplayName("§5.2 person evaluation")
class PersonEvaluatorTest {

    private val chiefOfficer = Fx.person(position = Fx.CHIEF_OFFICER, name = "C. Officer")

    @Nested
    @DisplayName("one_of sets")
    inner class OneOf {

        private val matrix = Fx.matrix(
            rules = listOf(
                Fx.rule(Fx.CHIEF_OFFICER, Fx.QL02, "M⁸"),
                Fx.rule(Fx.CHIEF_OFFICER, Fx.QL03, "M⁸"),
            ),
            conditionals = listOf(OneOfRuleView(Fx.CHIEF_OFFICER, listOf(Fx.QL02, Fx.QL03), label = "M⁸")),
        )

        @Test
        fun `holding one member makes the others na`() {
            val evaluation = PersonEvaluator.evaluate(
                chiefOfficer, Fx.NOR, Fx.SWING, matrix,
                Fx.holdings(Fx.perpetual(Fx.QL02), Fx.notHeld(Fx.QL03)),
            )

            assertThat(evaluation.cell(Fx.QL02)!!.state).isEqualTo(CellState.OK)
            assertThat(evaluation.cell(Fx.QL03)!!.state).isEqualTo(CellState.NA)
            assertThat(evaluation.rollUp).isEqualTo(CellState.OK)
        }

        @Test
        fun `holding none leaves the members as gaps`() {
            val evaluation = PersonEvaluator.evaluate(
                chiefOfficer, Fx.NOR, Fx.SWING, matrix,
                Fx.holdings(Fx.notHeld(Fx.QL02), Fx.notHeld(Fx.QL03)),
            )

            assertThat(evaluation.cells.map { it.state }).containsOnly(CellState.GAP)
            assertThat(evaluation.rollUp).isEqualTo(CellState.GAP)
        }

        @Test
        fun `an unknown member softens the whole unsatisfied set to unknown - the gap may not be real`() {
            val evaluation = PersonEvaluator.evaluate(
                chiefOfficer, Fx.NOR, Fx.SWING, matrix,
                Fx.holdings(Fx.notHeld(Fx.QL02), Fx.unknown(Fx.QL03)),
            )

            assertThat(evaluation.cell(Fx.QL02)!!.state).isEqualTo(CellState.UNKNOWN)
            assertThat(evaluation.cell(Fx.QL03)!!.state).isEqualTo(CellState.UNKNOWN)
            assertThat(evaluation.rollUp).isEqualTo(CellState.UNKNOWN)
        }

        @Test
        fun `a missing holding record counts as unknown for softening`() {
            val evaluation = PersonEvaluator.evaluate(
                chiefOfficer, Fx.NOR, Fx.SWING, matrix,
                Fx.holdings(Fx.notHeld(Fx.QL02)),
            )

            assertThat(evaluation.cell(Fx.QL02)!!.state).isEqualTo(CellState.UNKNOWN)
        }

        @Test
        fun `an expiring member does not satisfy the set`() {
            val evaluation = PersonEvaluator.evaluate(
                chiefOfficer, Fx.NOR, Fx.SWING, matrix,
                Fx.holdings(Fx.held(Fx.QL02, Fx.SWING.to.minusDays(3)), Fx.notHeld(Fx.QL03)),
            )

            assertThat(evaluation.cell(Fx.QL02)!!.state).isEqualTo(CellState.EXPIRING)
            assertThat(evaluation.cell(Fx.QL03)!!.state).isEqualTo(CellState.GAP)
        }

        @Test
        fun `the satisfying member keeps its own state and explanation notes name the set`() {
            val evaluation = PersonEvaluator.evaluate(
                chiefOfficer, Fx.NOR, Fx.SWING, matrix,
                Fx.holdings(Fx.perpetual(Fx.QL02), Fx.notHeld(Fx.QL03)),
            )

            assertThat(evaluation.cell(Fx.QL03)!!.notes.single()).contains("M⁸")
        }
    }

    @Nested
    @DisplayName("dependent rules")
    inner class Dependent {

        private val matrix = Fx.matrix(
            rules = listOf(Fx.rule(Fx.MASTER, Fx.COST, "M")),
            conditionals = listOf(
                DependentRuleView(
                    positionId = Fx.MASTER,
                    requirementId = Fx.COST,
                    requiredIfHolds = listOf(Fx.CoR),
                    unlessHolds = listOf(Fx.STCW),
                ),
            ),
        )

        private val master = Fx.person(position = Fx.MASTER)

        @Test
        fun `not triggered - the target becomes na`() {
            val evaluation = PersonEvaluator.evaluate(
                master, Fx.NOR, Fx.SWING, matrix, Fx.holdings(Fx.notHeld(Fx.COST)),
            )

            assertThat(evaluation.cell(Fx.COST)!!.state).isEqualTo(CellState.NA)
            assertThat(evaluation.rollUp).isEqualTo(CellState.OK)
        }

        @Test
        fun `triggered and missing - stays a gap with an explanation`() {
            val evaluation = PersonEvaluator.evaluate(
                master, Fx.NOR, Fx.SWING, matrix,
                Fx.holdings(Fx.perpetual(Fx.CoR), Fx.notHeld(Fx.COST)),
            )

            val cell = evaluation.cell(Fx.COST)!!
            assertThat(cell.state).isEqualTo(CellState.GAP)
            assertThat(cell.notes).isNotEmpty()
        }

        @Test
        fun `triggered and held - ok`() {
            val evaluation = PersonEvaluator.evaluate(
                master, Fx.NOR, Fx.SWING, matrix,
                Fx.holdings(Fx.perpetual(Fx.CoR), Fx.perpetual(Fx.COST)),
            )

            assertThat(evaluation.cell(Fx.COST)!!.state).isEqualTo(CellState.OK)
        }

        @Test
        fun `an unless_holds qualification suppresses the trigger`() {
            val evaluation = PersonEvaluator.evaluate(
                master, Fx.NOR, Fx.SWING, matrix,
                Fx.holdings(Fx.perpetual(Fx.CoR), Fx.perpetual(Fx.STCW), Fx.notHeld(Fx.COST)),
            )

            assertThat(evaluation.cell(Fx.COST)!!.state).isEqualTo(CellState.NA)
        }

        @Test
        fun `an unknown trigger holding does not trigger the dependency`() {
            // `unknown` is not `held`; the dependency stays inapplicable and the unknown holding
            // is chased through the exceptions worklist instead (§4.3, Q11).
            val evaluation = PersonEvaluator.evaluate(
                master, Fx.NOR, Fx.SWING, matrix,
                Fx.holdings(Fx.unknown(Fx.CoR), Fx.notHeld(Fx.COST)),
            )

            assertThat(evaluation.cell(Fx.COST)!!.state).isEqualTo(CellState.NA)
        }
    }

    @Nested
    @DisplayName("roll-up severity")
    inner class RollUp {

        @Test
        fun `severity order is gap greater than expiring greater than pending greater than unknown greater than review greater than exempt greater than ok`() {
            val ordered = listOf(
                CellState.GAP, CellState.EXPIRING, CellState.PENDING, CellState.UNKNOWN,
                CellState.REVIEW, CellState.EXEMPT, CellState.OK,
            )
            assertThat(ordered.map { it.rollUpSeverity }).isSortedAccordingTo(reverseOrder())
        }

        @Test
        fun `the worst cell wins`() {
            val evaluation = PersonEvaluation(
                PersonId(1),
                listOf(
                    CellEvaluation(Fx.MED, RuleLevel.MANDATORY, CellState.OK),
                    CellEvaluation(Fx.FRC, RuleLevel.MANDATORY, CellState.EXEMPT),
                    CellEvaluation(Fx.WAH, RuleLevel.MANDATORY, CellState.EXPIRING),
                ),
            )
            assertThat(evaluation.rollUp).isEqualTo(CellState.EXPIRING)
        }

        @Test
        fun `a person whose every cell is na rolls up to ok`() {
            val evaluation = PersonEvaluation(
                PersonId(1),
                listOf(CellEvaluation(Fx.MED, RuleLevel.MANDATORY, CellState.NA)),
            )
            assertThat(evaluation.rollUp).isEqualTo(CellState.OK)
        }

        @Test
        fun `a person with no applicable rules rolls up to ok`() {
            val evaluation = PersonEvaluator.evaluate(
                chiefOfficer, Fx.NOR, Fx.SWING, Fx.matrix(), emptyMap(),
            )
            assertThat(evaluation.cells).isEmpty()
            assertThat(evaluation.rollUp).isEqualTo(CellState.OK)
        }
    }

    @Test
    fun `only the rules of the person's own position are evaluated`() {
        val matrix = Fx.matrix(
            rules = listOf(
                Fx.rule(Fx.MASTER, Fx.MED, "M"),
                Fx.rule(Fx.GPH, Fx.WAH, "M"),
            ),
        )

        val evaluation = PersonEvaluator.evaluate(
            Fx.person(position = Fx.GPH), Fx.NOR, Fx.SWING, matrix, emptyMap(),
        )

        assertThat(evaluation.cells.map { it.requirementId }).containsExactly(Fx.WAH)
    }

    @Test
    fun `partnership overrides are resolved against the swing's partnership, not the person's home`() {
        val matrix = Fx.matrix(
            rules = listOf(
                Fx.rule(Fx.MASTER, Fx.MED, "M"),
                Fx.rule(Fx.MASTER, Fx.MED, "", partnership = Fx.UNI.id),
            ),
        )
        val uniSwing = Fx.SWING.copy(partnershipId = Fx.UNI.id)
        val norPerson = Fx.person(position = Fx.MASTER, home = Fx.NOR.id)

        val evaluation = PersonEvaluator.evaluate(norPerson, Fx.UNI, uniSwing, matrix, emptyMap())

        assertThat(evaluation.cells).isEmpty()
    }
}
