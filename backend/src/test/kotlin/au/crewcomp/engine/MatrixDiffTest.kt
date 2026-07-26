package au.crewcomp.engine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/** Spec §5.5 — matrix version diff. */
@DisplayName("§5.5 matrix diff")
class MatrixDiffTest {

    @Test
    fun `added, removed and level-changed rules are reported`() {
        val from = Fx.matrix(
            id = 1,
            rules = listOf(
                Fx.rule(Fx.MASTER, Fx.MED, "M"),
                Fx.rule(Fx.MASTER, Fx.FRC, "R"),
            ),
        )
        val to = Fx.matrix(
            id = 2,
            rules = listOf(
                Fx.rule(Fx.MASTER, Fx.MED, "M"),      // unchanged
                Fx.rule(Fx.MASTER, Fx.FRC, "M"),      // level changed
                Fx.rule(Fx.MASTER, Fx.WAH, "M"),      // added
            ),
        )

        val diff = MatrixDiff.diff(from, to)

        assertThat(diff.rules).hasSize(2)
        assertThat(diff.rules.first { it.key.requirementId == Fx.FRC }.kind).isEqualTo(DiffKind.LEVEL_CHANGED)
        assertThat(diff.rules.first { it.key.requirementId == Fx.WAH }.kind).isEqualTo(DiffKind.ADDED)
    }

    @Test
    fun `a removed rule is reported`() {
        val from = Fx.matrix(id = 1, rules = listOf(Fx.rule(Fx.MASTER, Fx.MED, "M")))
        val to = Fx.matrix(id = 2)

        val entry = MatrixDiff.diff(from, to).rules.single()

        assertThat(entry.kind).isEqualTo(DiffKind.REMOVED)
        assertThat(entry.from).isEqualTo(RuleLevel("M"))
        assertThat(entry.to).isNull()
    }

    @Test
    fun `base and partnership-specific rules diff independently`() {
        val from = Fx.matrix(id = 1, rules = listOf(Fx.rule(Fx.MASTER, Fx.MED, "M")))
        val to = Fx.matrix(
            id = 2,
            rules = listOf(
                Fx.rule(Fx.MASTER, Fx.MED, "M"),
                Fx.rule(Fx.MASTER, Fx.MED, "", partnership = Fx.UNI.id),
            ),
        )

        val entry = MatrixDiff.diff(from, to).rules.single()

        assertThat(entry.key.partnershipId).isEqualTo(Fx.UNI.id)
        assertThat(entry.kind).isEqualTo(DiffKind.ADDED)
    }

    @Test
    fun `quota rule changes are reported`() {
        val from = Fx.matrix(id = 1, quotas = listOf(QuotaRuleView("M7", Fx.FRC, 4, QuotaScope.SWING)))
        val to = Fx.matrix(id = 2, quotas = listOf(QuotaRuleView("M7", Fx.FRC, 5, QuotaScope.SWING)))

        val entry = MatrixDiff.diff(from, to).quotas.single()

        assertThat(entry.kind).isEqualTo(DiffKind.LEVEL_CHANGED)
        assertThat(entry.from!!.min).isEqualTo(4)
        assertThat(entry.to!!.min).isEqualTo(5)
    }

    @Test
    fun `identical versions diff to nothing`() {
        val rules = listOf(Fx.rule(Fx.MASTER, Fx.MED, "M"))
        val quotas = listOf(QuotaRuleView("M7", Fx.FRC, 4, QuotaScope.SWING))

        assertThat(MatrixDiff.diff(Fx.matrix(id = 1, rules = rules, quotas = quotas), Fx.matrix(id = 2, rules = rules, quotas = quotas)).isEmpty)
            .isTrue()
    }
}
