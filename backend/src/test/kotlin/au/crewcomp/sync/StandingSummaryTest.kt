package au.crewcomp.sync

import au.crewcomp.engine.CellEvaluation
import au.crewcomp.engine.CellState
import au.crewcomp.engine.PersonEvaluation
import au.crewcomp.engine.PersonId
import au.crewcomp.engine.RequirementId
import au.crewcomp.engine.RuleLevel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * MOB-0's headline and readiness — the sentence and the ring the crew app renders as received.
 *
 * The load-bearing cases are the ones the 31 Jul review caught the device getting wrong:
 * `pending` must never read as "you can sail" (an unanswered request is not a granted one), and
 * `expiring` must not reassure above a subhead saying a certificate lapses mid-swing.
 */
@DisplayName("Standing summary (MOB-0 headline and readiness)")
class StandingSummaryTest {

    private fun cell(state: CellState, id: Long = 1) =
        CellEvaluation(RequirementId(id), RuleLevel.MANDATORY, state)

    private fun evaluation(vararg states: CellState) = PersonEvaluation(
        personId = PersonId(1),
        cells = states.mapIndexed { index, state -> cell(state, index + 1L) },
    )

    @Test
    fun `pending is never 'you can sail'`() {
        val headline = StandingSummary.headline(evaluation(CellState.OK, CellState.PENDING))
        assertThat(headline).doesNotContain("can sail")
        assertThat(headline).contains("not yet granted")
    }

    @Test
    fun `expiring names the lapse rather than reassuring`() {
        assertThat(StandingSummary.headline(evaluation(CellState.OK, CellState.EXPIRING)))
            .isEqualTo("A certificate lapses before this swing ends.")
        assertThat(StandingSummary.headline(evaluation(CellState.EXPIRING, CellState.EXPIRING)))
            .isEqualTo("2 certificates lapse before this swing ends.")
    }

    @Test
    fun `the confirm count is the actual count, not a constant`() {
        assertThat(StandingSummary.headline(evaluation(CellState.OK, CellState.UNKNOWN)))
            .isEqualTo("Almost — one thing to confirm.")
        assertThat(
            StandingSummary.headline(
                evaluation(CellState.UNKNOWN, CellState.UNKNOWN, CellState.REVIEW),
            ),
        ).isEqualTo("Almost — 3 things to confirm.")
    }

    @Test
    fun `ok and a granted exemption both permit sailing, and the exemption says so`() {
        assertThat(StandingSummary.headline(evaluation(CellState.OK)))
            .isEqualTo("You can sail this swing.")
        assertThat(StandingSummary.headline(evaluation(CellState.OK, CellState.EXEMPT)))
            .isEqualTo("You can sail this swing — an exemption covers you.")
    }

    @Test
    fun `readiness excludes na from both halves and pending from ready`() {
        val readiness = StandingSummary.readiness(
            evaluation(CellState.OK, CellState.EXEMPT, CellState.PENDING, CellState.GAP, CellState.NA),
        )
        assertThat(readiness).isEqualTo(Readiness(ready = 2, total = 4))
    }

    @Test
    fun `an empty evaluation is zero over zero, not full`() {
        assertThat(StandingSummary.readiness(evaluation())).isEqualTo(Readiness(ready = 0, total = 0))
        assertThat(StandingSummary.headline(evaluation())).isEqualTo("You can sail this swing.")
    }
}
