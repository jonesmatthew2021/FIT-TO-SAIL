package au.crewcomp.platform.dev

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The seed extracts' one demanding shape is the register: quoted free-text note columns carrying
 * commas, doubled quotes and embedded newlines. A parser that gets those wrong silently shifts
 * every later column of the row — which is exactly how a Sam # ends up in a date field.
 */
@DisplayName("Seed CSV parsing")
class SeedCsvTest {

    @Test
    fun `plain rows split on commas`() {
        assertThat(SeedCsv.parse("a,b,c\nd,e,f"))
            .containsExactly(listOf("a", "b", "c"), listOf("d", "e", "f"))
    }

    @Test
    fun `quoted fields keep commas and doubled quotes`() {
        assertThat(SeedCsv.parse("""x,"a, ""quoted"" b",z"""))
            .containsExactly(listOf("x", """a, "quoted" b""", "z"))
    }

    @Test
    fun `a quoted field spans newlines`() {
        val rows = SeedCsv.parse("id,note\n1,\"line one\nline two\"\n2,plain")
        assertThat(rows).containsExactly(
            listOf("id", "note"),
            listOf("1", "line one\nline two"),
            listOf("2", "plain"),
        )
    }

    @Test
    fun `CRLF ends a row like LF, and blank lines are dropped`() {
        assertThat(SeedCsv.parse("a,b\r\nc,d\r\n\r\n"))
            .containsExactly(listOf("a", "b"), listOf("c", "d"))
    }

    @Test
    fun `trailing empty fields survive`() {
        assertThat(SeedCsv.parse("a,,\nb,,")).containsExactly(listOf("a", "", ""), listOf("b", "", ""))
    }

    @Test
    fun `the final row needs no trailing newline`() {
        assertThat(SeedCsv.parse("a,b")).containsExactly(listOf("a", "b"))
    }

    @Test
    fun `an unterminated quote is an error, not a guess`() {
        assertThatIllegalArgumentException().isThrownBy { SeedCsv.parse("a,\"open") }
    }

    @Test
    fun `header keying pads short rows with empty strings`() {
        val rows = SeedCsv.parseWithHeader("a,b,c\n1,2")
        assertThat(rows).containsExactly(mapOf("a" to "1", "b" to "2", "c" to ""))
    }
}
