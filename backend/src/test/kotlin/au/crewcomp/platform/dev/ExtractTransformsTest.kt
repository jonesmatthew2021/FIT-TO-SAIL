package au.crewcomp.platform.dev

import au.crewcomp.engine.RegisterOutcome
import au.crewcomp.platform.dev.ExtractTransforms.MatrixRuleRow
import au.crewcomp.workflow.RegisterStatus
import au.crewcomp.workflow.RegisterType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * §11 fix-up rules, ported from the POC's `build_real_seed.py`. Each case here is an anomaly the
 * source workbooks actually contain (see the profile in the loader's history); the expected
 * values are the POC's own behaviour, which is the behavioural reference.
 */
@DisplayName("§11 extract fix-up rules")
class ExtractTransformsTest {

    // ------------------------------------------------------------------ dates

    @Test
    fun `ISO dates parse and rubbish is null, never a guess`() {
        assertThat(ExtractTransforms.isoOrNull("2026-01-23")).isEqualTo(LocalDate.of(2026, 1, 23))
        assertThat(ExtractTransforms.isoOrNull("23/01/2026")).isNull()
        assertThat(ExtractTransforms.isoOrNull("TBC")).isNull()
        assertThat(ExtractTransforms.isoOrNull("")).isNull()
        assertThat(ExtractTransforms.isoOrNull(null)).isNull()
    }

    @Test
    fun `the register's long-form date artifact parses`() {
        assertThat(ExtractTransforms.isoOrNull("Friday, 23 January 2026 11:37 AM"))
            .isEqualTo(LocalDate.of(2026, 1, 23))
    }

    // ------------------------------------------------------------------ people

    @Test
    fun `a tiered position splits into base position and tier`() {
        assertThat(ExtractTransforms.splitTier("Chief Officer - Unlimited"))
            .isEqualTo("Chief Officer" to "Unlimited")
        assertThat(ExtractTransforms.splitTier("Chief Officer - 100m"))
            .isEqualTo("Chief Officer" to "100m")
        assertThat(ExtractTransforms.splitTier("GPH")).isEqualTo("GPH" to null)
        // Only the two known tiers split; an unknown suffix is part of the position name.
        assertThat(ExtractTransforms.splitTier("Master - Coastal")).isEqualTo("Master - Coastal" to null)
    }

    @Test
    fun `the Sam format accepts the real shape and rejects extraction artifacts`() {
        assertThat(ExtractTransforms.SAM_FORMAT.matches("j12345J")).isTrue()
        assertThat(ExtractTransforms.SAM_FORMAT.matches("Shift 2")).isFalse()
        assertThat(ExtractTransforms.SAM_FORMAT.matches("1")).isFalse()
        assertThat(ExtractTransforms.SAM_FORMAT.matches("J12345J")).isFalse()
    }

    // ------------------------------------------------------------------ titles

    @Test
    fun `title normalisation folds case, dash and apostrophe variants`() {
        assertThat(ExtractTransforms.normTitle("  ECDIS—STCW  Reg II/1 & II/2 "))
            .isEqualTo("ecdis-stcw reg ii/1 & ii/2")
        assertThat(ExtractTransforms.TITLE_ALIASES[ExtractTransforms.normTitle("ECDIS–STCW Reg II/1 & II/2")])
            .isEqualTo("QL-13")
    }

    // ------------------------------------------------------------------ slot collapse

    private val order = mapOf("QL-01" to 0, "QL-02" to 1, "QL-03" to 2, "MS-01" to 3, "PT-03" to 4)

    @Test
    fun `agreeing slots collapse to one rule per position with no flag`() {
        val collapsed = ExtractTransforms.collapseSlots(
            listOf(
                MatrixRuleRow(1, "Master", "MS-01", "M"),
                MatrixRuleRow(2, "Master", "MS-01", "M"),
            ),
            order,
        )
        assertThat(collapsed.levels).containsExactly(java.util.Map.entry("Master" to "MS-01", "M"))
        assertThat(collapsed.disagreements).isEmpty()
    }

    @Test
    fun `disagreeing slots take the strictest level and are flagged, including a blank slot`() {
        val collapsed = ExtractTransforms.collapseSlots(
            listOf(
                MatrixRuleRow(11, "GPH", "PT-03", "M7"),
                MatrixRuleRow(12, "GPH", "PT-03", "R"),
                MatrixRuleRow(13, "GPH", "MS-01", "M"),
                MatrixRuleRow(14, "GPH", "MS-01", "M"),
                // Slots 13/14 carry no PT-03 cell at all: still a divergence worth flagging.
            ),
            order,
        )
        assertThat(collapsed.levels[("GPH" to "PT-03")]).isEqualTo("M7")
        assertThat(collapsed.levels[("GPH" to "MS-01")]).isEqualTo("M")
        // Two divergences, not one: MS-01 being *absent* from slots 11/12 is a divergence too
        // (POC rule — a blank cell in one slot of a position is flagged, not papered over).
        assertThat(collapsed.disagreements)
            .hasSize(2)
            .anySatisfy { assertThat(it).startsWith("GPH PT-03:") }
            .anySatisfy { assertThat(it).startsWith("GPH MS-01:") }
    }

    @Test
    fun `the Chief Officer CoC cells become one-of set M8 whatever the slots said`() {
        val collapsed = ExtractTransforms.collapseSlots(
            listOf(
                MatrixRuleRow(3, "Chief Officer", "QL-02", "M"),
                MatrixRuleRow(4, "Chief Officer", "QL-03", "M"),
            ),
            order,
        )
        assertThat(collapsed.levels[("Chief Officer" to "QL-01")]).isEqualTo("M8")
        assertThat(collapsed.levels[("Chief Officer" to "QL-02")]).isEqualTo("M8")
        assertThat(collapsed.levels[("Chief Officer" to "QL-03")]).isEqualTo("M8")
    }

    // ------------------------------------------------------------------ register header derivation

    @Test
    fun `the partnership acting as operator is relabelled OPS`() {
        val h = ExtractTransforms.deriveHeader("Open UNI", "Exemption Request - UNI", null, hasCloseout = false)
        assertThat(h.status).isEqualTo(RegisterStatus.OPEN_OPS)
        assertThat(h.type).isEqualTo(RegisterType.EXEMPTION_REQUEST_OPS)
        assertThat(h.statusDerived).isFalse()
        assertThat(h.typeDerived).isFalse()
    }

    @Test
    fun `a missing status derives from the outcome`() {
        val h = ExtractTransforms.deriveHeader("", "MRL Query", RegisterOutcome.INFO_REQUIRED, hasCloseout = false)
        assertThat(h.status).isEqualTo(RegisterStatus.CLOSED_INFO_REQUIRED)
        assertThat(h.statusDerived).isTrue()
    }

    @Test
    fun `a missing status with only a closeout date is closed without an outcome`() {
        val h = ExtractTransforms.deriveHeader("", "PW Query", null, hasCloseout = true)
        assertThat(h.status).isEqualTo(RegisterStatus.CLOSED_ADMIN_ACTION)
        assertThat(h.closedWithoutOutcome).isTrue()
    }

    @Test
    fun `a missing status with nothing else stays open with the type's party`() {
        assertThat(ExtractTransforms.deriveHeader("", "PW Query", null, false).status)
            .isEqualTo(RegisterStatus.OPEN_PW)
        assertThat(ExtractTransforms.deriveHeader("", "MRL Query", null, false).status)
            .isEqualTo(RegisterStatus.OPEN_MRL)
        assertThat(ExtractTransforms.deriveHeader("", "Exemption Request - UNI", null, false).status)
            .isEqualTo(RegisterStatus.OPEN_OPS)
    }

    @Test
    fun `closed-by-party combines with the outcome column when there is one`() {
        val h = ExtractTransforms.deriveHeader("Closed - PW", "Exemption Request - PW", RegisterOutcome.APPROVED, true)
        assertThat(h.status).isEqualTo(RegisterStatus.CLOSED_APPROVED)
        assertThat(h.closedWithoutOutcome).isFalse()
    }

    @Test
    fun `closed-by-party with no outcome becomes Admin Action and is flagged`() {
        val h = ExtractTransforms.deriveHeader("Closed - MRL", "MRL Query", null, true)
        assertThat(h.status).isEqualTo(RegisterStatus.CLOSED_ADMIN_ACTION)
        assertThat(h.closedWithoutOutcome).isTrue()
    }

    @Test
    fun `a missing type becomes the weaker claim - a query for PW and MRL, a request for OPS`() {
        assertThat(ExtractTransforms.deriveHeader("Open - PW", "", null, false).type)
            .isEqualTo(RegisterType.PW_QUERY)
        assertThat(ExtractTransforms.deriveHeader("Open - MRL", "", null, false).type)
            .isEqualTo(RegisterType.MRL_QUERY)
        assertThat(ExtractTransforms.deriveHeader("Open UNI", "", null, false).type)
            .isEqualTo(RegisterType.EXEMPTION_REQUEST_OPS)
        assertThat(ExtractTransforms.deriveHeader("Open - PW", "", null, false).typeDerived).isTrue()
    }
}
