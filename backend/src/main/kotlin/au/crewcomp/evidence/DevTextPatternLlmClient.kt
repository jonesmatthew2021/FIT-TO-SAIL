package au.crewcomp.evidence

import au.crewcomp.platform.adapters.ExtractedField
import au.crewcomp.platform.adapters.ExtractionRequest
import au.crewcomp.platform.adapters.ExtractionResult
import au.crewcomp.platform.adapters.LlmClient
import au.crewcomp.platform.adapters.ModelDescriptor
import io.quarkus.arc.properties.IfBuildProperty
import jakarta.enterprise.context.ApplicationScoped
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * A development and test extractor that reads labelled ASCII out of the uploaded bytes.
 *
 * **Absent from a production artefact.** `@IfBuildProperty` removes the bean at build time — the
 * same mechanism the auth shim and the data fixture use — so this cannot be switched on by a
 * runtime config override. With it gone, `UnconfiguredLlmClient` is the only candidate.
 *
 * It exists so that §8 stages 3, 4 and 5 are exercisable end to end without a provider, a bill or
 * a network call, which is the difference between a pipeline that is written and one that is known
 * to work. It is a genuine deterministic extractor rather than a fabricator: it finds
 * `Expiry date: 2027-03-01` in the bytes or it reports nothing at all. It never invents a value,
 * and it never reports a confidence for a value it did not read.
 *
 * Ambiguous dates are dropped rather than guessed — `03/04/2027` is either April or March
 * depending on the country the certificate was printed in, and a pipeline that picks one is how a
 * medical silently expires eleven months early.
 */
@ApplicationScoped
@IfBuildProperty(name = "crewcomp.llm.dev-extractor.enabled", stringValue = "true")
class DevTextPatternLlmClient : LlmClient {

    override fun extract(request: ExtractionRequest): ExtractionResult {
        // Latin-1 rather than UTF-8: it never throws on arbitrary bytes, and every label below is
        // ASCII. A photograph scanned this way simply yields no matches, which is the right answer.
        val text = request.documents.joinToString("\n") { String(it.bytes, Charsets.ISO_8859_1) }

        val fields = LABELS.mapNotNull { (field, labels) ->
            val raw = labels.firstNotNullOfOrNull { label -> valueAfter(text, label) }
                ?: return@mapNotNull null
            val value = if (field in DATE_FIELDS) {
                normaliseDate(raw) ?: return@mapNotNull null
            } else {
                raw
            }
            field to ExtractedField(value, LABELLED_CONFIDENCE)
        }.toMap()

        return ExtractionResult(
            fields = fields,
            // Same contract as a real provider's raw response: the verbatim thing the extractor
            // said, retained for audit (§8 stage 2).
            rawResponse = fields.entries
                .sortedBy { it.key }
                .joinToString(",", "{", "}") { """"${it.key}":${quote(it.value.value)}""" },
            model = descriptor(),
        )
    }

    override fun descriptor(): ModelDescriptor =
        ModelDescriptor(model = "dev-text-pattern", configVersion = "1")

    private fun valueAfter(text: String, label: String): String? {
        val index = text.indexOf(label, ignoreCase = true)
        if (index < 0) return null
        return text.substring(index + label.length)
            .takeWhile { it != '\n' && it != '\r' }
            .trim()
            .trimStart(':')
            .trim()
            .ifEmpty { null }
    }

    private fun normaliseDate(raw: String): String? = try {
        LocalDate.parse(raw.take(10)).toString()
    } catch (_: DateTimeParseException) {
        null
    }

    private fun quote(value: String?): String =
        if (value == null) {
            "null"
        } else {
            "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        }

    companion object {
        /**
         * High but not 1.0. The label was found and the value parsed, which is genuinely strong
         * evidence — but a threshold of exactly 1.0 must still be expressible as "never accept
         * automatically", and a stub reporting perfect confidence would take that away.
         */
        const val LABELLED_CONFIDENCE = 0.95

        private val DATE_FIELDS = setOf(ExtractionSchema.ISSUE_DATE, ExtractionSchema.EXPIRY_DATE)

        /**
         * Label synonyms per field, **longest first within each list**: `valueAfter` matches on a
         * substring, so "Expiry" would otherwise consume the "date: …" of an "Expiry date:" line.
         */
        private val LABELS: Map<String, List<String>> = mapOf(
            ExtractionSchema.DOCUMENT_TYPE to listOf("Document type", "Certificate type"),
            ExtractionSchema.HOLDER_NAME to listOf("Holder name", "Holder", "Name"),
            ExtractionSchema.IDENTIFYING_NUMBER to listOf("Certificate number", "Number"),
            ExtractionSchema.ISSUING_AUTHORITY to listOf("Issuing authority", "Issued by"),
            ExtractionSchema.ISSUE_DATE to listOf("Issue date", "Issued"),
            ExtractionSchema.EXPIRY_DATE to listOf("Expiry date", "Expires", "Expiry"),
            ExtractionSchema.QUALIFICATION_TITLE to listOf("Qualification", "Course", "Title"),
        )
    }
}
