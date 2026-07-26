package au.crewcomp.mcp

import au.crewcomp.evidence.EvidencePipeline
import au.crewcomp.evidence.EvidenceReviewService
import au.crewcomp.platform.config.ConfigKey
import au.crewcomp.platform.config.ConfigService
import au.crewcomp.platform.jobs.JobHealth
import au.crewcomp.rules.MatrixService
import com.fasterxml.jackson.databind.ObjectMapper
import io.quarkiverse.mcp.server.Tool
import io.quarkiverse.mcp.server.ToolArg
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/**
 * The MCP tools MCP-1 names beyond the §5 engine: **configuration, background-job status, job
 * triggering, and the matrix**.
 *
 * MCP-2 holds exactly as it does in [ComplianceTools] — every tool below calls the same service the
 * REST API calls, so an agent setting a threshold or publishing a matrix exercises the real
 * validation, the real role checks and the real audit writes. Nothing here touches a repository.
 *
 * These are the tools that make a scenario *settable*. The §8 pipeline's behaviour depends on a
 * configuration value and on a scheduled sweep having run; without [setConfig] and [runJob] an agent
 * could only observe the pipeline's default posture and would have to wait two minutes to see
 * anything happen at all.
 */
@ApplicationScoped
class OperationsTools(
    private val config: ConfigService,
    private val jobHealth: JobHealth,
    private val matrix: MatrixService,
    private val pipeline: EvidencePipeline,
    private val evidenceReview: EvidenceReviewService,
    private val guard: McpGuard,
    private val json: ObjectMapper,
) {

    // -----------------------------------------------------------------------
    // Configuration and jobs
    // -----------------------------------------------------------------------

    @Tool(
        description = "List the ADM-10 configuration settings with their effective values, defaults " +
            "and whether each has been overridden (spec §6 ADM-10).",
    )
    fun listConfig(): String = guard.read {
        json.writeValueAsString(
            config.list().map {
                mapOf(
                    "key" to it.key.key,
                    "kind" to it.key.kind.name.lowercase(),
                    "value" to it.value,
                    "default" to it.key.defaultValue,
                    "overridden" to it.overridden,
                    "description" to it.key.description,
                )
            },
        )
    }

    @Tool(
        description = "Set one configuration setting, or clear the override to restore its default. " +
            "Goes through the same validated, audited service as the API (MCP-2). Values are JSON: a " +
            "number for expiry.lead-days, a 0-1 confidence for evidence.auto-accept-threshold, an " +
            "object of named weights for suggestion.weights.",
    )
    fun setConfig(
        @ToolArg(description = "Setting key, e.g. expiry.lead-days") key: String,
        @ToolArg(
            description = "JSON value, or omit to clear the override and restore the default",
            required = false,
        ) value: String?,
    ): String = guard.write {
        val configKey = ConfigKey.entries.firstOrNull { it.key == key }
            ?: throw IllegalArgumentException(
                "'$key' is not a configurable setting; the settings are " +
                    ConfigKey.entries.joinToString { it.key },
            )
        // Parsed as JSON rather than taken as a string, so that a threshold arrives as a number and
        // the weights arrive as an object. `validate` on the key is what rejects the wrong shape.
        val parsed = value?.let { json.readValue(it, Any::class.java) }
        val setting = config.set(configKey, parsed)
        json.writeValueAsString(
            mapOf("key" to setting.key.key, "value" to setting.value, "overridden" to setting.overridden),
        )
    }

    @Tool(
        description = "List the scheduled jobs (§9 scans, §8 extraction sweep) with their cadence and " +
            "the outcome of their last run in this process.",
    )
    fun listJobs(): String = guard.read {
        val lastRuns = jobHealth.lastRuns()
        json.writeValueAsString(
            jobHealth.scheduled().map { job ->
                val run = lastRuns[job.name]
                mapOf(
                    "name" to job.name,
                    "schedule" to job.cron,
                    "description" to job.description,
                    "lastOutcome" to run?.outcome,
                    "lastDetail" to run?.detail,
                    "lastStartedAt" to run?.startedAt?.toString(),
                )
            },
        )
    }

    @Tool(
        description = "Run a scheduled job now and return what it did. Every job is an idempotent " +
            "scan, so running one is safe and repeating it raises no duplicate notifications.",
    )
    fun runJob(
        @ToolArg(description = "Job name: evidence-extraction | expiry-scan | cutoff-scan | roster-scan") name: String,
    ): String = guard.write {
        val run = jobHealth.runNow(name)
        json.writeValueAsString(
            mapOf("job" to run.jobName, "outcome" to run.outcome, "detail" to run.detail),
        )
    }

    // -----------------------------------------------------------------------
    // Matrix (ADM-3, §5.5)
    // -----------------------------------------------------------------------

    @Tool(description = "List the matrix versions with their status, effective date and rule counts (§5.5).")
    fun listMatrixVersions(): String = guard.read {
        json.writeValueAsString(
            matrix.list().map {
                mapOf(
                    "id" to it.version.requiredId,
                    "label" to it.version.label,
                    "status" to it.version.statusValue,
                    "effectiveFrom" to it.version.effectiveFrom?.toString(),
                    "requirementRules" to it.requirementRuleCount,
                    "quotaRules" to it.quotaRuleCount,
                    "conditionalRules" to it.conditionalRuleCount,
                    "editable" to it.version.isEditable,
                )
            },
        )
    }

    @Tool(
        description = "Create a matrix draft, optionally as a deep copy of any existing version. " +
            "A published version is immutable, so editing means drafting from it (§5.5).",
    )
    fun createMatrixDraft(
        @ToolArg(description = "Label for the new version; must be unique") label: String,
        @ToolArg(description = "Version id to copy rules from, or omit for an empty draft", required = false)
        copyFromVersionId: Long?,
    ): String = guard.write {
        val draft = matrix.createDraft(label = label, copyFromVersionId = copyFromVersionId)
        json.writeValueAsString(
            mapOf("id" to draft.requiredId, "label" to draft.label, "status" to draft.statusValue),
        )
    }

    @Tool(
        description = "Set one cell of a matrix draft: the level for (position, requirement), " +
            "optionally as a partnership override. Level is 'M', 'R', a footnote label, or empty " +
            "for an override that removes the requirement for that partnership.",
    )
    fun setMatrixCell(
        @ToolArg(description = "Draft matrix version id") matrixVersionId: Long,
        @ToolArg(description = "Crew position id") positionId: Long,
        @ToolArg(description = "Requirement id") requirementId: Long,
        @ToolArg(description = "Level: M | R | a footnote label | empty (partnership override only)") level: String,
        @ToolArg(description = "Partnership id for an override; omit for the base rule", required = false)
        partnershipId: Long?,
    ): String = guard.write {
        val rule = matrix.setCell(
            matrixVersionId = matrixVersionId,
            partnershipId = partnershipId,
            positionId = positionId,
            requirementId = requirementId,
            level = level,
        )
        json.writeValueAsString(
            mapOf("id" to rule.requiredId, "level" to rule.levelValue, "partnershipId" to partnershipId),
        )
    }

    @Tool(
        description = "Publish a matrix draft, superseding the currently published version. " +
            "Compliance Lead authority; changes every compliance answer in the system (§5.5).",
    )
    fun publishMatrixVersion(
        @ToolArg(description = "Draft matrix version id") matrixVersionId: Long,
        @ToolArg(description = "Effective date (YYYY-MM-DD); defaults to the business date", required = false)
        effectiveFrom: String?,
    ): String = guard.write {
        val result = matrix.publish(
            matrixVersionId,
            effectiveFrom?.let { java.time.LocalDate.parse(it) },
        )
        json.writeValueAsString(
            mapOf(
                "published" to result.published.label,
                "effectiveFrom" to result.published.effectiveFrom?.toString(),
                "superseded" to result.superseded?.label,
            ),
        )
    }

    // -----------------------------------------------------------------------
    // Evidence pipeline (§8, ADM-9)
    // -----------------------------------------------------------------------

    @Tool(
        description = "List the §8 evidence verification queue: documents awaiting review, plus the " +
            "auto-accepted ones surfaced for spot-checking, with the reason each is there.",
    )
    fun evidenceQueue(): String = guard.read {
        json.writeValueAsString(
            evidenceReview.queue().map { document ->
                mapOf(
                    "publicId" to document.publicId.toString(),
                    "sam" to document.person.sam,
                    "person" to document.person.name,
                    "status" to document.verificationStatusValue,
                    "matchedRequirement" to document.matchedRequirement?.code,
                    "reviewReason" to document.reviewReason,
                    "extractionModel" to document.extractionModel,
                )
            },
        )
    }

    @Tool(
        description = "Run §8 stages 2-4 (extract, match, decide) for one evidence document now, " +
            "rather than waiting for the periodic sweep. Idempotent: a document already decided is " +
            "left alone.",
    )
    fun extractEvidence(
        @ToolArg(description = "The document's public id (the device-generated UUID)") publicId: String,
    ): String = guard.write {
        val id = UUID.fromString(publicId)
        val status = pipeline.process(id)
        val document = evidenceReview.detail(id)
        json.writeValueAsString(
            mapOf(
                "publicId" to publicId,
                "status" to status.wire,
                "matchedRequirement" to document.matchedRequirement?.code,
                "reviewReason" to document.reviewReason,
            ),
        )
    }
}
