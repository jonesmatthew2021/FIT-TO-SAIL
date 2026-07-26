package au.crewcomp.platform.jobs

import au.crewcomp.evidence.EvidencePipeline
import au.crewcomp.notify.NotificationScans
import au.crewcomp.platform.adapters.JobRun
import au.crewcomp.platform.adapters.ScheduledJob
import au.crewcomp.platform.adapters.local.InMemoryTaskScheduler
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.Actor
import au.crewcomp.platform.security.ActorContext
import au.crewcomp.platform.security.Role
import io.quarkus.runtime.StartupEvent
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import org.eclipse.microprofile.config.ConfigProvider
import org.jboss.logging.Logger

/**
 * The scheduled work of §8 and §9, and the one place that says what runs when.
 *
 * ### Every job is idempotent, and that is what makes this scheduler enough
 *
 * There is no job store, no queue and no retry ledger, because nothing here needs one: each job is a
 * scan that recomputes what is due and dedupes what it raises. A missed run is caught by the next
 * one; a run interrupted halfway leaves the half it did, and the next run finishes the rest. That
 * property is why the build takes `quarkus-scheduler` over JobRunr and its tables — and it is the
 * property to check before adding a job here. **A job that is not idempotent does not belong in this
 * class.**
 *
 * ### Why the cadence is in `application.properties` and not in `app_config`
 *
 * `@Scheduled` resolves its expression from MicroProfile Config at start-up. A cron stored in the
 * database would be a setting the scheduler never reads — editable on screen and completely inert. So
 * the cadence is deployment configuration, ADM-10 shows it read-only, and what ADM-10 gives an
 * operator instead is the button that matters in practice: run it now. `ConfigService` documents the
 * same split from the other side.
 *
 * ### One path in, whether a clock or a person started it
 *
 * Both the `@Scheduled` methods and ADM-10's "run now" go through
 * [InMemoryTaskScheduler.runNow], which invokes the body registered at start-up and records the
 * outcome. So a manual run and a scheduled run are the same code, are recorded the same way, and the
 * health view cannot show one and miss the other.
 *
 * Jobs run as [Actor.system], which puts `actor_kind = 'system'` on every audit event they write
 * (AIA-3) — that is what distinguishes "the nightly scan noticed this" from "a person did this".
 */
@ApplicationScoped
class JobRegistry(
    private val scans: NotificationScans,
    private val pipeline: EvidencePipeline,
    private val scheduler: InMemoryTaskScheduler,
    private val actorContext: ActorContext,
) {
    private val log = Logger.getLogger(JobRegistry::class.java)

    /**
     * Registers each job's body with the [au.crewcomp.platform.adapters.TaskScheduler] adapter, so
     * ADM-10 and the MCP tooling can list and trigger them (MCP-1) without knowing this class exists.
     */
    fun onStart(@Observes event: StartupEvent) {
        Job.entries.forEach { job ->
            scheduler.register(ScheduledJob(job.jobName, cronOf(job), job.description)) {
                actorContext.runAs(Actor.system(job.actorLabel)) { execute(job) }
            }
        }
        log.infof(
            "Registered %d scheduled jobs: %s",
            Job.entries.size,
            Job.entries.joinToString { "${it.jobName} (${cronOf(it)})" },
        )
    }

    // -----------------------------------------------------------------------
    // The schedules
    // -----------------------------------------------------------------------

    /**
     * §8 stage 2, LLM-5: "extraction is asynchronous (seconds–minutes acceptable)".
     *
     * Frequent and small. `SKIP` on concurrent execution rather than queueing: two sweeps at once
     * would both claim the same documents, and while the pipeline is idempotent enough to survive
     * that, paying a provider twice for the same extraction is not something to be relaxed about.
     */
    @Scheduled(
        every = "{crewcomp.jobs.evidence-extraction.every}",
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
    )
    fun evidenceExtraction() = fire(Job.EVIDENCE_EXTRACTION)

    /** §9's daily expiry lead-time job. */
    @Scheduled(cron = "{crewcomp.jobs.expiry-scan.cron}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    fun expiryScan() = fire(Job.EXPIRY_SCAN)

    /** §9's cutoff-approaching job, for coordinators. */
    @Scheduled(cron = "{crewcomp.jobs.cutoff-scan.cron}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    fun cutoffScan() = fire(Job.CUTOFF_SCAN)

    /** §9's quota-shortfall and roster-gap notices, for coordinators. */
    @Scheduled(cron = "{crewcomp.jobs.roster-scan.cron}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    fun rosterScan() = fire(Job.ROSTER_SCAN)

    // -----------------------------------------------------------------------
    // Running
    // -----------------------------------------------------------------------

    /**
     * Runs a job through the scheduler so the outcome is recorded, and logs it.
     *
     * Nothing is rethrown: `runNow` already turns a failure into a recorded `failed` [JobRun], and an
     * exception escaping a `@Scheduled` method buys nothing but a second log line.
     */
    private fun fire(job: Job) {
        val run = scheduler.runNow(job.jobName)
        if (run.outcome == "succeeded") {
            log.infof("Job %s: %s", job.jobName, run.detail)
        } else {
            log.errorf("Job %s %s: %s", job.jobName, run.outcome, run.detail)
        }
    }

    private fun execute(job: Job): String = when (job) {
        Job.EVIDENCE_EXTRACTION -> pipeline.sweep()
        Job.EXPIRY_SCAN -> scans.expiryScan()
        Job.CUTOFF_SCAN -> scans.cutoffScan()
        Job.ROSTER_SCAN -> scans.rosterScan()
    }

    /**
     * The job's effective schedule expression.
     *
     * Read from config rather than taken from the `@Scheduled` annotation: an annotation value is not
     * readable at runtime without reflection, and the health view wants the resolved expression
     * rather than the `{placeholder}`.
     */
    private fun cronOf(job: Job): String =
        ConfigProvider.getConfig()
            .getOptionalValue(job.scheduleProperty, String::class.java)
            .orElse("unconfigured")

    enum class Job(val jobName: String, val scheduleProperty: String, val description: String) {
        EVIDENCE_EXTRACTION(
            "evidence-extraction",
            "crewcomp.jobs.evidence-extraction.every",
            "§8 stages 2–4: extract, match and decide for uploaded evidence documents",
        ),
        EXPIRY_SCAN(
            "expiry-scan",
            "crewcomp.jobs.expiry-scan.cron",
            "§9: notify crew of expiring qualifications, and coordinators where a roster is affected",
        ),
        CUTOFF_SCAN(
            "cutoff-scan",
            "crewcomp.jobs.cutoff-scan.cron",
            "§9: warn coordinators of approaching swing submission cutoffs",
        ),
        ROSTER_SCAN(
            "roster-scan",
            "crewcomp.jobs.roster-scan.cron",
            "§9: notify coordinators of quota shortfalls and unfilled slots on upcoming swings",
        ),
        ;

        val actorLabel: String get() = "job:$jobName"
    }
}

/**
 * ADM-10's integration health view (§6): what is scheduled, and what happened when it last ran.
 *
 * Deliberately thin, and deliberately honest about what it does not know. Last-run state is held in
 * memory, so it is empty after a restart and is per-instance rather than per-deployment. That is
 * enough for a single-instance deployment answering "is the expiry scan running?", and it is not
 * enough for anything else — a durable job history belongs to the platform's monitoring, which is the
 * spike's (NFR-6). Saying so here is better than a table that looks authoritative and is not.
 */
@ApplicationScoped
class JobHealth(
    private val scheduler: InMemoryTaskScheduler,
    private val policy: AccessPolicy,
) {

    fun scheduled(): List<ScheduledJob> {
        requireOperator()
        return scheduler.scheduled()
    }

    fun lastRuns(): Map<String, JobRun> {
        requireOperator()
        return scheduler.lastRuns()
    }

    /**
     * Runs a job now.
     *
     * System Administrator only. Every job is idempotent, so the blast radius of a mistaken click is
     * a duplicate scan rather than duplicate notifications — but triggering unattended work on demand
     * is an administrative act nonetheless, and it is audited by the job's own writes.
     */
    fun runNow(name: String): JobRun {
        policy.require(Role.SYSTEM_ADMINISTRATOR)
        return scheduler.runNow(name)
    }

    /** Reading the schedule is not privileged: "is the scan running?" is an operational question. */
    private fun requireOperator() = policy.require(
        Role.SYSTEM_ADMINISTRATOR, Role.COMPLIANCE_LEAD, Role.CREW_COORDINATOR,
        Role.WORKFLOW_MANAGER, Role.DATA_STEWARD,
    )
}
