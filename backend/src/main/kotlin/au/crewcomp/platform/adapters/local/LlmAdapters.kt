package au.crewcomp.platform.adapters.local

import au.crewcomp.platform.adapters.ExtractedField
import au.crewcomp.platform.adapters.ExtractionRequest
import au.crewcomp.platform.adapters.ExtractionResult
import au.crewcomp.platform.adapters.LlmClient
import au.crewcomp.platform.adapters.ModelDescriptor
import io.quarkus.arc.DefaultBean
import jakarta.enterprise.context.ApplicationScoped

/**
 * The [LlmClient] for a system with **no provider selected yet**.
 *
 * §14.5 has not chosen an LLM provider and ADR 0005 forbids a cloud SDK outside `infra/`, so there
 * is no real extractor to ship. What matters is that the pipeline around it is complete and
 * honest: a document still gets ingested, still moves through matching and the decide stage, and
 * still lands in ADM-9's queue for a human. Extraction is the one stage that returns nothing.
 *
 * That is not a degraded mode — it is exactly LLM-2's launch posture ("auto-accept off at launch,
 * threshold = always review"). The queue works with empty extractions; a Data Steward types the
 * fields. When a provider is chosen, the only new thing is pre-filled values and a confidence
 * number beside each.
 *
 * The development counterpart lives beside the domain that defines the schema, in
 * `au.crewcomp.evidence.DevTextPatternLlmClient` — an extractor has to know the field names, and a
 * platform adapter that imported the evidence package would be pointing the wrong way.
 */

/**
 * The production default: extraction is a no-op that says so.
 *
 * Zero confidence on every field, which means stage 4 can never auto-accept regardless of how the
 * threshold is configured — the threshold is a floor above zero by construction
 * ([au.crewcomp.platform.config.ConfigKey.EVIDENCE_AUTO_ACCEPT_THRESHOLD] refuses 0). So there is
 * no configuration of this system that lets an unconfigured extractor write a holding.
 */
@ApplicationScoped
@DefaultBean
class UnconfiguredLlmClient : LlmClient {

    override fun extract(request: ExtractionRequest): ExtractionResult = ExtractionResult(
        fields = emptyMap(),
        rawResponse = RAW_RESPONSE,
        model = descriptor(),
    )

    override fun descriptor(): ModelDescriptor = ModelDescriptor(model = "none", configVersion = "unconfigured")

    companion object {
        /**
         * Retained verbatim in `extraction_raw` like any other model response, so an auditor
         * reading a document's history sees why nothing was extracted rather than a null.
         */
        const val RAW_RESPONSE =
            """{"extracted":false,"reason":"no LLM provider is configured (spec §14.5)"}"""
    }
}
