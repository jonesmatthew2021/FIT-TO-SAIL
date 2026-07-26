package au.crewcomp.platform.adapters

import java.net.URI
import java.time.Duration
import java.time.Instant

/**
 * Thin, cloud-neutral adapter interfaces.
 *
 * ADR 0005 defers the AWS-vs-GCP decision to the backend spike, and until it resolves **no code
 * outside `infra/` may use a cloud SDK**. Everything the platform provides — object storage,
 * secrets, scheduling, LLM inference — sits behind these interfaces, so the spike swaps
 * implementations rather than editing call sites, and the losing platform's implementation is
 * deleted rather than untangled.
 *
 * Keep these interfaces at the level of what the application needs, not what a provider offers:
 * a narrow interface is what makes the second implementation cheap.
 */

/**
 * Evidence originals (§8 stage 1) and exports. Objects are immutable once written; versioning
 * and retention are the platform's job (NFR-4, AUD-1).
 */
interface ObjectStorage {

    /** Stores [bytes] under [key] and returns the stored object's identity. */
    fun put(key: String, bytes: ByteArray, contentType: String): StoredObject

    fun get(key: String): ByteArray?

    fun exists(key: String): Boolean

    /**
     * A time-limited URL for direct read access. SEC-7 requires objects be served with
     * non-executable dispositions via signed URLs rather than proxied through the application.
     */
    fun signedReadUrl(key: String, ttl: Duration): URI

    /** A time-limited URL a client may upload to directly (MOB-5a resumable uploads). */
    fun signedWriteUrl(key: String, contentType: String, ttl: Duration): URI
}

data class StoredObject(
    val key: String,
    val sizeBytes: Long,
    val contentType: String,
    val storedAt: Instant,
    /** Provider-specific version/generation id where the platform offers one (NFR-4). */
    val versionId: String? = null,
)

/**
 * SEC-9 — secrets come from a managed secret store, never from code or config files.
 * Implementations cache; callers should not.
 */
interface SecretsProvider {
    fun secret(name: String): String
    fun secretOrNull(name: String): String?
}

/**
 * Scheduled work (§9 expiry scan, notification fan-out; §8 pipeline stages).
 *
 * Deliberately not a queue abstraction: at NFR-1's scale the jobs are periodic and idempotent,
 * and modelling them as "run this named job on this cadence" keeps both a platform scheduler
 * (EventBridge / Cloud Scheduler) and an in-process scheduler viable implementations.
 */
interface TaskScheduler {
    fun schedule(job: ScheduledJob)
    fun cancel(name: String)
    fun scheduled(): List<ScheduledJob>
    /** Runs a registered job immediately — used by MCP tooling and the ADM-10 health view. */
    fun runNow(name: String): JobRun
}

data class ScheduledJob(
    val name: String,
    val cron: String,
    val description: String,
)

data class JobRun(
    val jobName: String,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val outcome: String,
    val detail: String? = null,
)

/**
 * LLM inference for evidence extraction (§8) and, later, the §17.1 assists.
 *
 * The interface takes a **fixed output schema** and returns structured fields with confidence,
 * because LLM-3 requires extraction to run with structured output, no agentic capabilities and
 * no network access beyond the model call. Document content is passed as data; there is
 * deliberately no way for a caller to hand the model instructions taken from a document.
 */
interface LlmClient {

    fun extract(request: ExtractionRequest): ExtractionResult

    /** Identifies the model and prompt/config version for AIA-3 audit records. */
    fun descriptor(): ModelDescriptor
}

data class ExtractionRequest(
    /** Page images or PDF bytes; multi-page aware (§8 stage 2). */
    val documents: List<DocumentPart>,
    /** JSON Schema the model must fill. Fixed by the caller, never derived from the document. */
    val outputSchema: String,
    val instruction: String,
)

data class DocumentPart(val contentType: String, val bytes: ByteArray) {
    // ByteArray gives identity equals/hashCode by default, which is wrong for a value type.
    override fun equals(other: Any?): Boolean =
        this === other || (other is DocumentPart && contentType == other.contentType && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = 31 * contentType.hashCode() + bytes.contentHashCode()
}

data class ExtractionResult(
    val fields: Map<String, ExtractedField>,
    /** Retained for audit (§8 stage 2). */
    val rawResponse: String,
    val model: ModelDescriptor,
)

data class ExtractedField(val value: String?, val confidence: Double)

data class ModelDescriptor(val model: String, val configVersion: String)
