package au.crewcomp.platform.adapters.local

import au.crewcomp.platform.adapters.JobRun
import au.crewcomp.platform.adapters.ObjectStorage
import au.crewcomp.platform.adapters.ScheduledJob
import au.crewcomp.platform.adapters.SecretsProvider
import au.crewcomp.platform.adapters.StoredObject
import au.crewcomp.platform.adapters.TaskScheduler
import io.quarkus.arc.DefaultBean
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.ConfigProvider
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Development and test implementations of the cloud adapters.
 *
 * Marked [DefaultBean] so that adding a real AWS or GCP implementation in the spike displaces
 * these without any edit here — and so that a missing platform implementation shows up as
 * "files landed on local disk" rather than as an injection failure at start-up.
 *
 * These are not fallbacks for production: `LocalObjectStorage` refuses to start under the `prod`
 * profile, because silently storing crew evidence on a container filesystem would be a
 * data-loss and SEC-5 problem at once.
 */

@ApplicationScoped
@DefaultBean
class LocalObjectStorage : ObjectStorage {

    private val root: Path by lazy {
        require(!isProduction()) {
            "LocalObjectStorage must never be active in production — evidence originals require " +
                "managed, encrypted, versioned object storage (SEC-5, NFR-4)."
        }
        val configured = ConfigProvider.getConfig()
            .getOptionalValue("crewcomp.storage.local-path", String::class.java)
            .orElse(System.getProperty("java.io.tmpdir") + "/crewcomp-objects")
        Path.of(configured).also { Files.createDirectories(it) }
    }

    override fun put(key: String, bytes: ByteArray, contentType: String): StoredObject {
        val path = resolve(key)
        Files.createDirectories(path.parent)
        Files.write(path, bytes)
        return StoredObject(key, bytes.size.toLong(), contentType, Instant.now())
    }

    override fun get(key: String): ByteArray? =
        resolve(key).takeIf { Files.exists(it) }?.let { Files.readAllBytes(it) }

    override fun exists(key: String): Boolean = Files.exists(resolve(key))

    override fun signedReadUrl(key: String, ttl: Duration): URI = resolve(key).toUri()

    override fun signedWriteUrl(key: String, contentType: String, ttl: Duration): URI = resolve(key).toUri()

    /** Rejects traversal: a key is a relative path under [root] and nothing else. */
    private fun resolve(key: String): Path {
        val resolved = root.resolve(key).normalize()
        require(resolved.startsWith(root)) { "Object key escapes the storage root: $key" }
        return resolved
    }
}

/**
 * Reads secrets from MicroProfile Config, which in dev means `application.properties` and
 * environment variables. Refuses to serve secrets in production: SEC-9 requires a managed store.
 */
@ApplicationScoped
@DefaultBean
class ConfigSecretsProvider : SecretsProvider {

    override fun secret(name: String): String =
        secretOrNull(name) ?: throw IllegalStateException("Secret '$name' is not configured")

    override fun secretOrNull(name: String): String? {
        check(!isProduction()) {
            "ConfigSecretsProvider must never be active in production — secrets live in a managed " +
                "secret store (SEC-9)."
        }
        return ConfigProvider.getConfig()
            .getOptionalValue("crewcomp.secrets.$name", String::class.java)
            .orElse(null)
    }
}

/**
 * An in-memory registry of scheduled jobs.
 *
 * It holds what is scheduled, runs a job on demand, and remembers the outcome of each job's last run
 * — which is what the MCP `run_job` tool and the ADM-10 health view need. `JobRegistry` drives the
 * cadence with `quarkus-scheduler` and routes *both* scheduled and manual runs through [runNow], so
 * the recorded history covers both.
 *
 * The last-run history is in memory and therefore per-instance and lost on restart. That is stated
 * out loud on `JobHealth` rather than hidden, because a durable job history is the platform's
 * monitoring concern (NFR-6) and a table that looked authoritative without being it would be worse
 * than an honest gap.
 */
@ApplicationScoped
@DefaultBean
class InMemoryTaskScheduler : TaskScheduler {

    private val jobs = ConcurrentHashMap<String, ScheduledJob>()
    private val runnables = ConcurrentHashMap<String, () -> String>()
    private val lastRuns = ConcurrentHashMap<String, JobRun>()

    fun register(job: ScheduledJob, body: () -> String) {
        jobs[job.name] = job
        runnables[job.name] = body
    }

    /** The outcome of each job's most recent run in this process. Empty until something has run. */
    fun lastRuns(): Map<String, JobRun> = lastRuns.toMap()

    override fun schedule(job: ScheduledJob) {
        jobs[job.name] = job
    }

    override fun cancel(name: String) {
        jobs.remove(name)
        runnables.remove(name)
    }

    override fun scheduled(): List<ScheduledJob> = jobs.values.sortedBy { it.name }

    override fun runNow(name: String): JobRun {
        val body = runnables[name]
            // Not recorded: "no such job" is a fact about the caller, not about a job's health, and
            // storing it under that name would invent a job in the history.
            ?: return JobRun(name, Instant.now(), Instant.now(), "not_found", "No such job")
        val startedAt = Instant.now()
        val run = try {
            JobRun(name, startedAt, Instant.now(), "succeeded", body())
        } catch (e: Exception) {
            JobRun(name, startedAt, Instant.now(), "failed", e.message ?: e.javaClass.simpleName)
        }
        lastRuns[name] = run
        return run
    }
}

private fun isProduction(): Boolean = io.quarkus.runtime.configuration.ConfigUtils.getProfiles().contains("prod")
