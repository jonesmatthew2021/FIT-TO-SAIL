package au.crewcomp.engine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Spec §5.1 — cell evaluation. These tests *are* the normative statement of the semantics;
 * changing one means changing the spec.
 */
@DisplayName("§5.1 cell evaluation")
class CellEvaluatorTest {

    private val swing = Fx.SWING
    private val master = Fx.person(position = Fx.MASTER)

    private fun evaluate(
        level: String = "M",
        holding: HoldingView? = null,
        matrix: MatrixSnapshot = Fx.matrix(),
        person: PersonView = master,
        partnership: PartnershipView = Fx.NOR,
        register: RegisterRecordView? = null,
    ) = CellEvaluator.evaluate(
        requirementId = Fx.MED,
        level = RuleLevel(level),
        holding = holding,
        swing = swing,
        matrix = matrix,
        person = person,
        partnership = partnership,
        registerRecord = register,
    )

    @Nested
    @DisplayName("base classification against the whole swing window (Q13)")
    inner class BaseClassification {

        @Test
        fun `held_perpetual is ok`() {
            assertThat(evaluate(holding = Fx.perpetual(Fx.MED)).state).isEqualTo(CellState.OK)
        }

        @Test
        fun `expiry after swing end is ok`() {
            val holding = Fx.held(Fx.MED, swing.to.plusDays(1))
            assertThat(evaluate(holding = holding).state).isEqualTo(CellState.OK)
        }

        @Test
        fun `expiry exactly on swing end is ok - the boundary is inclusive`() {
            val holding = Fx.held(Fx.MED, swing.to)
            assertThat(evaluate(holding = holding).state).isEqualTo(CellState.OK)
        }

        @Test
        fun `expiry inside the swing is expiring - needs rebooking`() {
            val holding = Fx.held(Fx.MED, swing.to.minusDays(1))
            assertThat(evaluate(holding = holding).state).isEqualTo(CellState.EXPIRING)
        }

        @Test
        fun `expiry exactly on swing start is expiring`() {
            val holding = Fx.held(Fx.MED, swing.from)
            assertThat(evaluate(holding = holding).state).isEqualTo(CellState.EXPIRING)
        }

        @Test
        fun `expiry before swing start is a gap`() {
            val holding = Fx.held(Fx.MED, swing.from.minusDays(1))
            assertThat(evaluate(holding = holding).state).isEqualTo(CellState.GAP)
        }

        @Test
        fun `not_held is a gap`() {
            assertThat(evaluate(holding = Fx.notHeld(Fx.MED)).state).isEqualTo(CellState.GAP)
        }

        @Test
        fun `unknown holding is unknown`() {
            assertThat(evaluate(holding = Fx.unknown(Fx.MED)).state).isEqualTo(CellState.UNKNOWN)
        }

        @Test
        fun `no holding record at all is unknown`() {
            assertThat(evaluate(holding = null).state).isEqualTo(CellState.UNKNOWN)
        }
    }

    @Nested
    @DisplayName("precedence 1 — recommended (R) is never a gap")
    inner class Recommended {

        @Test
        fun `held recommended requirement is ok`() {
            assertThat(evaluate(level = "R", holding = Fx.perpetual(Fx.MED)).state).isEqualTo(CellState.OK)
        }

        @Test
        fun `missing recommended requirement is recommended, not gap`() {
            assertThat(evaluate(level = "R", holding = Fx.notHeld(Fx.MED)).state).isEqualTo(CellState.RECOMMENDED)
        }

        @Test
        fun `unknown recommended requirement is recommended, not unknown`() {
            assertThat(evaluate(level = "R", holding = null).state).isEqualTo(CellState.RECOMMENDED)
        }

        @Test
        fun `an exemption never overlays a recommended cell`() {
            val cell = evaluate(level = "R", holding = Fx.notHeld(Fx.MED), register = Fx.approved())
            assertThat(cell.state).isEqualTo(CellState.RECOMMENDED)
            assertThat(cell.registerRecordId).isNull()
        }
    }

    @Nested
    @DisplayName("precedence 2 — quota footnote levels are not individually mandatory")
    inner class QuotaFootnote {

        private val matrix = Fx.matrix(
            quotas = listOf(QuotaRuleView("M7", Fx.MED, min = 4, scope = QuotaScope.SWING)),
        )

        @Test
        fun `a gap becomes quota_only`() {
            assertThat(evaluate(level = "M7", holding = Fx.notHeld(Fx.MED), matrix = matrix).state)
                .isEqualTo(CellState.QUOTA_ONLY)
        }

        @Test
        fun `unknown stays unknown`() {
            assertThat(evaluate(level = "M7", holding = Fx.unknown(Fx.MED), matrix = matrix).state)
                .isEqualTo(CellState.UNKNOWN)
        }

        @Test
        fun `held stays ok`() {
            assertThat(evaluate(level = "M7", holding = Fx.perpetual(Fx.MED), matrix = matrix).state)
                .isEqualTo(CellState.OK)
        }

        @Test
        fun `a quota_only cell is not overlaid by an exemption - it was never individually required`() {
            val cell = evaluate(
                level = "M7",
                holding = Fx.notHeld(Fx.MED),
                matrix = matrix,
                register = Fx.approved(),
            )
            assertThat(cell.state).isEqualTo(CellState.QUOTA_ONLY)
            assertThat(cell.registerRecordId).isNull()
        }

        @Test
        fun `the same label in a version that declares no such quota stays mandatory`() {
            // §4.2: footnote labels are data, not code — M7 means different things in different
            // versions, so behaviour is keyed off the QuotaRule records, never the label text.
            assertThat(evaluate(level = "M7", holding = Fx.notHeld(Fx.MED), matrix = Fx.matrix()).state)
                .isEqualTo(CellState.GAP)
        }
    }

    @Nested
    @DisplayName("precedence 3 — tier footnote goes to human review (Q6)")
    inner class TierFootnote {

        private val matrix = Fx.matrix(
            tierFootnote = "Mx",
            tierPolicy = mapOf("100m" to setOf("100m")),
        )

        @Test
        fun `a tier that conflicts with the partnership vessel class goes to review`() {
            val person = Fx.person(position = Fx.CHIEF_OFFICER, tier = "Unlimited")
            val cell = evaluate(level = "Mx", holding = Fx.perpetual(Fx.MED), matrix = matrix, person = person, partnership = Fx.UNI)
            assertThat(cell.state).isEqualTo(CellState.REVIEW)
            assertThat(cell.notes.single()).contains("Unlimited", "100m")
        }

        @Test
        fun `a tier the policy accepts falls through to normal evaluation`() {
            val person = Fx.person(position = Fx.CHIEF_OFFICER, tier = "100m")
            val cell = evaluate(level = "Mx", holding = Fx.notHeld(Fx.MED), matrix = matrix, person = person, partnership = Fx.UNI)
            assertThat(cell.state).isEqualTo(CellState.GAP)
        }

        @Test
        fun `the rule cannot fire where the partnership has no vessel class`() {
            val person = Fx.person(position = Fx.CHIEF_OFFICER, tier = "Unlimited")
            val cell = evaluate(level = "Mx", holding = Fx.perpetual(Fx.MED), matrix = matrix, person = person, partnership = Fx.NOR)
            assertThat(cell.state).isEqualTo(CellState.OK)
        }

        @Test
        fun `an unmapped vessel class goes to review rather than being auto-resolved (O-3)`() {
            val person = Fx.person(position = Fx.CHIEF_OFFICER, tier = "Unlimited")
            val unmapped = Fx.matrix(tierFootnote = "Mx")
            val cell = evaluate(level = "Mx", holding = Fx.perpetual(Fx.MED), matrix = unmapped, person = person, partnership = Fx.UNI)
            assertThat(cell.state).isEqualTo(CellState.REVIEW)
        }

        @Test
        fun `a person with no recorded tier goes to review`() {
            val person = Fx.person(position = Fx.CHIEF_OFFICER, tier = null)
            val cell = evaluate(level = "Mx", holding = Fx.perpetual(Fx.MED), matrix = matrix, person = person, partnership = Fx.UNI)
            assertThat(cell.state).isEqualTo(CellState.REVIEW)
        }

        @Test
        fun `review outranks the exemption overlay - a tier conflict is never auto-resolved`() {
            val person = Fx.person(position = Fx.CHIEF_OFFICER, tier = "Unlimited")
            val cell = evaluate(
                level = "Mx",
                holding = Fx.notHeld(Fx.MED),
                matrix = matrix,
                person = person,
                partnership = Fx.UNI,
                register = Fx.approved(),
            )
            assertThat(cell.state).isEqualTo(CellState.REVIEW)
        }

        @Test
        fun `a version that defines no tier footnote treats the label as an ordinary footnote`() {
            val person = Fx.person(position = Fx.CHIEF_OFFICER, tier = "Unlimited")
            val cell = evaluate(level = "Mx", holding = Fx.notHeld(Fx.MED), matrix = Fx.matrix(), person = person, partnership = Fx.UNI)
            assertThat(cell.state).isEqualTo(CellState.GAP)
        }
    }

    @Nested
    @DisplayName("precedence 4 — exemption overlay")
    inner class ExemptionOverlay {

        @Test
        fun `an approved record with an approval window makes a gap exempt`() {
            val cell = evaluate(holding = Fx.notHeld(Fx.MED), register = Fx.approved("NORCC24-007"))
            assertThat(cell.state).isEqualTo(CellState.EXEMPT)
            assertThat(cell.registerRecordId).isEqualTo("NORCC24-007")
        }

        @Test
        fun `an open record makes a gap pending`() {
            val cell = evaluate(holding = Fx.notHeld(Fx.MED), register = Fx.open("NORCC24-008"))
            assertThat(cell.state).isEqualTo(CellState.PENDING)
            assertThat(cell.registerRecordId).isEqualTo("NORCC24-008")
        }

        @Test
        fun `the overlay applies to unknown and expiring too`() {
            assertThat(evaluate(holding = Fx.unknown(Fx.MED), register = Fx.approved()).state)
                .isEqualTo(CellState.EXEMPT)
            assertThat(evaluate(holding = Fx.held(Fx.MED, swing.to.minusDays(2)), register = Fx.approved()).state)
                .isEqualTo(CellState.EXEMPT)
        }

        @Test
        fun `the overlay never touches an ok cell`() {
            val cell = evaluate(holding = Fx.perpetual(Fx.MED), register = Fx.approved())
            assertThat(cell.state).isEqualTo(CellState.OK)
            assertThat(cell.registerRecordId).isNull()
        }

        @Test
        fun `approved without an approval window does not grant an exemption`() {
            val record = RegisterRecordView("NORCC24-009", null, RegisterOutcome.APPROVED, null, null, open = false)
            assertThat(evaluate(holding = Fx.notHeld(Fx.MED), register = record).state).isEqualTo(CellState.GAP)
        }

        @Test
        fun `a closed, not-approved record leaves the gap standing`() {
            val record = RegisterRecordView("NORCC24-010", null, RegisterOutcome.NOT_APPROVED, null, null, open = false)
            assertThat(evaluate(holding = Fx.notHeld(Fx.MED), register = record).state).isEqualTo(CellState.GAP)
        }
    }

    @Nested
    @DisplayName("helpers")
    inner class Helpers {

        @Test
        fun `validThroughSwing accepts perpetual and expiry on or after swing end`() {
            assertThat(CellEvaluator.validThroughSwing(Fx.perpetual(Fx.FRC), swing)).isTrue()
            assertThat(CellEvaluator.validThroughSwing(Fx.held(Fx.FRC, swing.to), swing)).isTrue()
            assertThat(CellEvaluator.validThroughSwing(Fx.held(Fx.FRC, swing.to.minusDays(1)), swing)).isFalse()
            assertThat(CellEvaluator.validThroughSwing(Fx.unknown(Fx.FRC), swing)).isFalse()
            assertThat(CellEvaluator.validThroughSwing(null, swing)).isFalse()
        }

        @Test
        fun `daysUntil is negative once expired`() {
            val today = LocalDate.of(2026, 7, 26)
            assertThat(CellEvaluator.daysUntil(today.plusDays(90), today)).isEqualTo(90)
            assertThat(CellEvaluator.daysUntil(today.minusDays(3), today)).isEqualTo(-3)
        }

        @Test
        fun `a held_expiry holding without an expiry date is rejected at construction`() {
            org.assertj.core.api.Assertions
                .assertThatThrownBy { HoldingView(Fx.MED, HoldingStatus.HELD_EXPIRY, null) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }
}
