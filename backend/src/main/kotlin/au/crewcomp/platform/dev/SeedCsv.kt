package au.crewcomp.platform.dev

/**
 * A strict RFC 4180 reader for the POC's seed extracts (§11).
 *
 * Hand-rolled rather than a dependency because the need is one shape of file: comma-separated,
 * double-quote quoting, `""` escaping, and — the part a naive `split(',')` gets wrong — quoted
 * fields that contain commas and newlines, which the register's free-text note columns do.
 */
object SeedCsv {

    /** Parses [text] into rows of fields. Blank lines are dropped; CRLF and LF both end a row. */
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0

        fun endField() {
            row.add(field.toString())
            field.clear()
        }

        fun endRow() {
            endField()
            rows.add(row)
            row = mutableListOf()
        }

        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        field.append('"')
                        i++
                    }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    ',' -> endField()
                    '\r' -> Unit // the paired \n ends the row
                    '\n' -> endRow()
                    else -> field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()

        require(!inQuotes) { "Unterminated quoted field at end of input" }
        return rows.filterNot { it.size == 1 && it[0].isBlank() }
    }

    /** Parses [text] and keys each data row by the header row's column names. */
    fun parseWithHeader(text: String): List<Map<String, String>> {
        val rows = parse(text)
        if (rows.isEmpty()) return emptyList()
        val header = rows.first()
        return rows.drop(1).map { row ->
            header.indices.associate { i -> header[i] to (row.getOrNull(i) ?: "") }
        }
    }
}
