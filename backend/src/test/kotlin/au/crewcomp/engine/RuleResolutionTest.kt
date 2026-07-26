package au.crewcomp.engine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/** Spec §4.2 — rule resolution semantics (normative, as implemented in the POC `effectiveRules`). */
@DisplayName("§4.2 rule resolution")
class RuleResolutionTest {

    private val key = RuleKey(Fx.MASTER, Fx.MED)

    @Test
    fun `base rules apply to every partnership`() {
        val matrix = Fx.matrix(rules = listOf(Fx.rule(Fx.MASTER, Fx.MED, "M")))

        assertThat(matrix.effectiveRules(Fx.NOR.id)[key]).isEqualTo(RuleLevel("M"))
        assertThat(matrix.effectiveRules(Fx.UNI.id)[key]).isEqualTo(RuleLevel("M"))
    }

    @Test
    fun `a partnership-specific row overrides the base rule`() {
        val matrix = Fx.matrix(
            rules = listOf(
                Fx.rule(Fx.MASTER, Fx.MED, "M"),
                Fx.rule(Fx.MASTER, Fx.MED, "R", partnership = Fx.UNI.id),
            ),
        )

        assertThat(matrix.effectiveRules(Fx.NOR.id)[key]).isEqualTo(RuleLevel("M"))
        assertThat(matrix.effectiveRules(Fx.UNI.id)[key]).isEqualTo(RuleLevel("R"))
    }

    @Test
    fun `an override with an empty level removes the requirement for that partnership`() {
        val matrix = Fx.matrix(
            rules = listOf(
                Fx.rule(Fx.MASTER, Fx.MED, "M"),
                Fx.rule(Fx.MASTER, Fx.MED, "", partnership = Fx.UNI.id),
            ),
        )

        assertThat(matrix.effectiveRules(Fx.NOR.id)).containsKey(key)
        assertThat(matrix.effectiveRules(Fx.UNI.id)).doesNotContainKey(key)
    }

    @Test
    fun `a blank base rule contributes nothing`() {
        val matrix = Fx.matrix(rules = listOf(Fx.rule(Fx.MASTER, Fx.MED, "  ")))
        assertThat(matrix.effectiveRules(Fx.NOR.id)).isEmpty()
    }

    @Test
    fun `another partnership's override does not leak`() {
        val matrix = Fx.matrix(
            rules = listOf(
                Fx.rule(Fx.MASTER, Fx.MED, "M", partnership = Fx.UNI.id),
            ),
        )

        assertThat(matrix.effectiveRules(Fx.NOR.id)).isEmpty()
        assertThat(matrix.effectiveRules(Fx.UNI.id)).containsKey(key)
    }

    @Test
    fun `resolution is scoped per position`() {
        val matrix = Fx.matrix(
            rules = listOf(
                Fx.rule(Fx.MASTER, Fx.MED, "M"),
                Fx.rule(Fx.GPH, Fx.WAH, "M3"),
            ),
        )

        assertThat(matrix.effectiveRulesForPosition(Fx.NOR.id, Fx.MASTER).keys).containsExactly(Fx.MED)
        assertThat(matrix.effectiveRulesForPosition(Fx.NOR.id, Fx.GPH).keys).containsExactly(Fx.WAH)
    }

    @Test
    fun `rule levels classify correctly without interpreting footnote text`() {
        assertThat(RuleLevel("M").isPlainMandatory).isTrue()
        assertThat(RuleLevel("M").isFootnote).isFalse()
        assertThat(RuleLevel("r").isRecommended).isTrue()
        assertThat(RuleLevel("").isBlank).isTrue()
        assertThat(RuleLevel(" M⁸ ").isFootnote).isTrue()
        assertThat(RuleLevel(" M⁸ ").label).isEqualTo("M⁸")
        assertThat(RuleLevel("M7").isMandatoryish).isTrue()
        assertThat(RuleLevel("R").isMandatoryish).isFalse()
    }
}
