package au.crewcomp.platform.adapters.anthropic

import au.crewcomp.platform.adapters.ExtractedField
import au.crewcomp.platform.adapters.ExtractionRequest
import au.crewcomp.platform.adapters.ExtractionResult
import au.crewcomp.platform.adapters.LlmClient
import au.crewcomp.platform.adapters.ModelDescriptor
import au.crewcomp.platform.adapters.SecretsProvider
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.Base64PdfSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.DocumentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.TextBlockParam
import com.fasterxml.jackson.databind.ObjectMapper
import io.quarkus.arc.properties.IfBuildProperty
import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Alternative
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.util.Base64

/**
 * The [LlmClient] backed by Claude (Anthropic's Messages API) — §14.5's provider, chosen here for
 * the office-side certificate intake and the §8 pipeline it shares.
 *
 * Present in the artefact only when `crewcomp.llm.provider=anthropic` is set **at build time**:
 * the same mechanism the auth shim and the dev extractor use, so a runtime override cannot switch
 * a build that was never given a provider onto one. When present it takes precedence over the
 * dev extractor and the unconfigured default (`@Alternative @Priority`); when absent, those stand
 * exactly as before.
 *
 * What the adapter does and, more to the point, does not do — every line of it under LLM-3:
 *
 *  - The document is passed as a **document/image content block** and the instruction as a
 *    separate text block. Nothing read out of the document is ever concatenated into the
 *    instruction, and the instruction is [ExtractionRequest.instruction] verbatim — a constant
 *    the caller owns.
 *  - The output is constrained to the caller's **fixed JSON schema** through the API's structured
 *    output mode. The model fills a form; it has no field to put a request in.
 *  - The model has **no tools** and no network beyond the one call. It returns values; the pipeline
 *    decides what to do with them (LLM-1), and the only path to a holding is still `HoldingService`.
 *  - A **refusal** (the model's safety layer declining) is reported as an empty extraction with the
 *    refusal recorded in the raw response, never as an error the sweep would retry forever.
 *
 * The API key comes from the [SecretsProvider] (`crewcomp.secrets.anthropic-api-key`, which in dev
 * is `ANTHROPIC_API_KEY`) and is checked at start-up: a provider that is configured and cannot
 * authenticate should fail the boot, not the first upload three days later (same posture as MCP-4).
 */
@ApplicationScoped
@Alternative
@Priority(1)
@IfBuildProperty(name = "crewcomp.llm.provider", stringValue = "anthropic")
class AnthropicLlmClient(
    secrets: SecretsProvider,
    private val objectMapper: ObjectMapper,
    @ConfigProperty(name = "crewcomp.llm.anthropic.model", defaultValue = DEFAULT_MODEL)
    private val model: String,
) : LlmClient {

    private val log = Logger.getLogger(AnthropicLlmClient::class.java)

    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(
            secrets.secretOrNull(API_KEY_SECRET) ?: throw IllegalStateException(
                "crewcomp.llm.provider is 'anthropic' but no API key is configured. Set " +
                    "ANTHROPIC_API_KEY (crewcomp.secrets.$API_KEY_SECRET) or unset the provider.",
            ),
        )
        .build()

    override fun extract(request: ExtractionRequest): ExtractionResult {
        val blocks = ArrayList<ContentBlockParam>()
        request.documents.forEach { part -> blocks.add(documentBlock(part.contentType, part.bytes)) }
        // The instruction is its own block, after the document, and is the caller's constant.
        blocks.add(ContentBlockParam.ofText(TextBlockParam.builder().text(request.instruction).build()))

        @Suppress("UNCHECKED_CAST")
        val schema = objectMapper.readValue(request.outputSchema, Map::class.java) as Map<String, Any?>

        val params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(MAX_TOKENS)
            .outputConfig(
                OutputConfig.builder()
                    .format(JsonOutputFormat.builder().schema(JsonValue.from(schema)).build())
                    .build(),
            )
            .addUserMessageOfBlockParams(blocks)
            .build()

        val response = client.messages().create(params)
        val stopReason = response.stopReason().map { it.toString() }.orElse("")
        if (stopReason == "refusal") {
            val detail = response.stopDetails().map { it.toString() }.orElse("no detail")
            log.warnf("The model declined to read a document: %s", detail)
            return ExtractionResult(
                fields = emptyMap(),
                rawResponse = """{"extracted":false,"reason":"refusal","detail":${quote(detail)}}""",
                model = descriptor(),
            )
        }

        val text = response.content().stream()
            .flatMap { block -> block.text().stream() }
            .map { it.text() }
            .reduce("") { a, b -> a + b }

        return ExtractionResult(fields = parse(text), rawResponse = text, model = descriptor())
    }

    override fun descriptor(): ModelDescriptor = ModelDescriptor(model = model, configVersion = PROMPT_VERSION)

    private fun documentBlock(contentType: String, bytes: ByteArray): ContentBlockParam {
        val data = Base64.getEncoder().encodeToString(bytes)
        return when (contentType.lowercase()) {
            "application/pdf" -> ContentBlockParam.ofDocument(
                DocumentBlockParam.builder()
                    .source(Base64PdfSource.builder().data(data).build())
                    .build(),
            )
            "image/jpeg", "image/png", "image/gif", "image/webp" -> ContentBlockParam.ofImage(
                ImageBlockParam.builder()
                    .source(
                        Base64ImageSource.builder()
                            .mediaType(Base64ImageSource.MediaType.of(contentType.lowercase()))
                            .data(data)
                            .build(),
                    )
                    .build(),
            )
            // HEIC/HEIF are accepted at ingest (§7.5) but not by the model; a document in one stays
            // in `pending_extraction` with this reason in the log rather than being guessed at.
            else -> throw IllegalArgumentException(
                "The model cannot read '$contentType'; convert it to PDF, JPEG or PNG and re-submit",
            )
        }
    }

    /**
     * `{field: {value, confidence}}` → the port's fields. Tolerant of a fenced or padded body, and
     * of a field the model left out (absent = not read = zero confidence), but never of a field it
     * invented: only the schema's names are read back.
     */
    private fun parse(text: String): Map<String, ExtractedField> {
        val body = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val root = try {
            objectMapper.readTree(body)
        } catch (e: Exception) {
            log.warnf("The model's response was not JSON (%s); treating as nothing read", e.message)
            return emptyMap()
        }
        val fields = LinkedHashMap<String, ExtractedField>()
        root.fields().forEach { (name, node) ->
            if (!node.isObject) return@forEach
            val value = node.path("value").takeUnless { it.isNull || it.isMissingNode }?.asText()?.trim()
            val confidence = node.path("confidence").asDouble(0.0).coerceIn(0.0, 1.0)
            fields[name] = ExtractedField(value?.ifEmpty { null }, if (value.isNullOrEmpty()) 0.0 else confidence)
        }
        return fields
    }

    private fun quote(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    companion object {
        const val API_KEY_SECRET = "anthropic-api-key"
        const val DEFAULT_MODEL = "claude-opus-5"

        /** Bumped whenever the instruction, schema or request shape changes — it is the AIA-3 config version. */
        const val PROMPT_VERSION = "anthropic-extract-1"

        /** Room for the model's reasoning plus a seven-field form; the form itself is a few hundred tokens. */
        const val MAX_TOKENS = 16_000L
    }
}
