package au.crewcomp.platform.config

import au.crewcomp.engine.Planning
import au.crewcomp.engine.SuggestionWeights
import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.Role
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

/**
 * ADM-10 configuration — the tunables §6 names: expiry lead days, suggestion weights, the LLM
 * auto-accept threshold, and the notification lead times.
 *
 * ### What is here, and what deliberately is not
 *
 * This store holds **policy**: numbers a Compliance Lead or System Administrator decides and can
 * change without a deploy. It does **not** hold *schedules*. A cron expression lives in
 * `application.properties` because that is what the runtime reads to build the schedule at
 * start-up, and a cron in a database that the scheduler cannot see would be a setting that looks
 * editable and does nothing. ADM-10 shows the schedules read-only, beside a "run now" button —
 * which is the operation an operator actually wants (§9, MCP-1).
 *
 * ### Every key has a default, and the default is in code
 *
 * A missing row means "use the default", never "the feature is broken". That is what lets the
 * table start empty — which it does, because the §11 migration seeds data, not settings — and it
 * is why [ConfigKey] carries the default rather than a migration inserting one. `DEFAULT` values
 * that already exist elsewhere in the domain are referenced, not repeated:
 * [Planning.DEFAULT_EXPIRY_LEAD_DAYS] and [SuggestionWeights]`()` are the engine's own defaults,
 * so a change there does not leave a stale copy here.
 */
@ApplicationScoped
class ConfigService(
    private val entries: AppConfigRepository,
    private val policy: AccessPolicy,
    private val audit: AuditWriter,
) {

    /** Every setting with its effective value, its default, and whether it has been overridden. */
    @Transactional
    fun list(): List<ConfigSetting> {
        policy.require(
            Role.COMPLIANCE_LEAD, Role.SYSTEM_ADMINISTRATOR, Role.CREW_COORDINATOR,
            Role.WORKFLOW_MANAGER, Role.DATA_STEWARD,
        )
        val stored = entries.allOrdered().associateBy { it.key }
        return ConfigKey.entries.map { key ->
            val row = stored[key.key]
            ConfigSetting(
                key = key,
                value = row?.value?.get(VALUE_MEMBER) ?: key.defaultValue,
                overridden = row != null,
                updatedAt = row?.updatedAt,
                updatedBy = row?.updatedBy,
            )
        }
    }

    /**
     * Sets one value, or clears the override when [value] is null.
     *
     * Validation is per-key ([ConfigKey.validate]) and happens before anything is written, so a
     * nonsense threshold is a 400 rather than a setting that silently disables auto-acceptance —
     * or, far worse, silently enables it.
     */
    @Transactional
    fun set(key: ConfigKey, value: Any?): ConfigSetting {
        policy.require(Role.SYSTEM_ADMINISTRATOR, Role.COMPLIANCE_LEAD)

        val actor = policy.actor()
        val existing = entries.findById(key.key)
        val before = existing?.value?.get(VALUE_MEMBER)

        if (value == null) {
            existing?.let { entries.delete(it) }
            audit.record(
                entityType = "AppConfig",
                event = "config.cleared",
                businessKey = key.key,
                before = mapOf("value" to before),
                after = mapOf("value" to key.defaultValue, "source" to "default"),
            )
            return ConfigSetting(key, key.defaultValue, overridden = false, null, null)
        }

        val normalised = key.validate(value)
        val entry = existing ?: AppConfigEntry().apply { this.key = key.key }
        entry.value = mutableMapOf(VALUE_MEMBER to normalised)
        entry.updatedAt = java.time.Instant.now()
        entry.updatedBy = actor.label
        if (existing == null) entries.persist(entry)

        audit.record(
            entityType = "AppConfig",
            event = "config.set",
            businessKey = key.key,
            before = mapOf("value" to before),
            after = mapOf("value" to normalised),
        )
        return ConfigSetting(key, normalised, overridden = true, entry.updatedAt, entry.updatedBy)
    }

    // -----------------------------------------------------------------------
    // Typed reads — the callers inside the application
    // -----------------------------------------------------------------------

    /**
     * These bypass the role check on purpose: they are read by the engine's callers and by
     * scheduled jobs, not by a user asking to see the settings. [list] is the authorised read.
     */
    @Transactional
    fun expiryLeadDays(): Long = numberOrDefault(ConfigKey.EXPIRY_LEAD_DAYS).toLong()

    @Transactional
    fun cutoffLeadDays(): Long = numberOrDefault(ConfigKey.CUTOFF_LEAD_DAYS).toLong()

    @Transactional
    fun suggestionWeights(): SuggestionWeights {
        val stored = entries.findById(ConfigKey.SUGGESTION_WEIGHTS.key)?.value?.get(VALUE_MEMBER)
        val defaults = SuggestionWeights()
        if (stored !is Map<*, *>) return defaults
        fun weight(name: String, fallback: Int) = (stored[name] as? Number)?.toInt() ?: fallback
        return SuggestionWeights(
            gap = weight("gap", defaults.gap),
            unknown = weight("unknown", defaults.unknown),
            expiring = weight("expiring", defaults.expiring),
            crossPartnership = weight("crossPartnership", defaults.crossPartnership),
            overlappingAssignment = weight("overlappingAssignment", defaults.overlappingAssignment),
        )
    }

    /**
     * The §8 stage-4 auto-accept threshold, or `null` for "always review".
     *
     * `null` is the launch posture and the default (LLM-2): auto-acceptance is off until precision
     * has been measured against the review queue. The absence of a row is what expresses that, so
     * nothing has to be turned off — it was never on.
     */
    @Transactional
    fun autoAcceptThreshold(): Double? {
        val stored = entries.findById(ConfigKey.EVIDENCE_AUTO_ACCEPT_THRESHOLD.key)
            ?.value?.get(VALUE_MEMBER)
        return (stored as? Number)?.toDouble()
    }

    private fun numberOrDefault(key: ConfigKey): Number {
        val stored = entries.findById(key.key)?.value?.get(VALUE_MEMBER)
        return (stored as? Number) ?: (key.defaultValue as Number)
    }

    companion object {
        /** The single member every `app_config.value` object carries — see [AppConfigEntry]. */
        const val VALUE_MEMBER = "value"
    }
}

/**
 * The settings ADM-10 exposes. Adding one means adding it here, with its default and its
 * validation; there is no free-text key, because a typo'd key would be a setting that silently
 * never takes effect.
 */
enum class ConfigKey(
    val key: String,
    val kind: ConfigKind,
    val description: String,
    val defaultValue: Any?,
) {
    EXPIRY_LEAD_DAYS(
        key = "expiry.lead-days",
        kind = ConfigKind.NUMBER,
        description = "How far ahead an expiry is reported as an alert and notified (§5.4, §9).",
        defaultValue = Planning.DEFAULT_EXPIRY_LEAD_DAYS,
    ),
    CUTOFF_LEAD_DAYS(
        key = "notifications.cutoff-lead-days",
        kind = ConfigKind.NUMBER,
        description = "How many days before a swing's submission cutoff coordinators are warned (§9).",
        defaultValue = 10L,
    ),
    SUGGESTION_WEIGHTS(
        key = "suggestion.weights",
        kind = ConfigKind.WEIGHTS,
        description = "Penalties the §5.4 ranking applies: gap, unknown, expiring, crossPartnership, overlappingAssignment.",
        defaultValue = SuggestionWeights().let {
            mapOf(
                "gap" to it.gap,
                "unknown" to it.unknown,
                "expiring" to it.expiring,
                "crossPartnership" to it.crossPartnership,
                "overlappingAssignment" to it.overlappingAssignment,
            )
        },
    ),
    EVIDENCE_AUTO_ACCEPT_THRESHOLD(
        key = "evidence.auto-accept-threshold",
        kind = ConfigKind.THRESHOLD,
        description = "Minimum per-field confidence for §8 stage 4 to accept without review. " +
            "Unset means always review, which is the launch posture (LLM-2).",
        defaultValue = null,
    ),
    ;

    /** Returns the value to store, coerced to the type the key expects, or throws. */
    fun validate(value: Any?): Any = when (kind) {
        ConfigKind.NUMBER -> {
            val number = (value as? Number)?.toLong()
                ?: throw IllegalArgumentException("$key is a whole number of days, not '$value'")
            require(number in 0..3650) { "$key must be between 0 and 3650 days, not $number" }
            number
        }

        ConfigKind.THRESHOLD -> {
            val number = (value as? Number)?.toDouble()
                ?: throw IllegalArgumentException("$key is a confidence between 0 and 1, not '$value'")
            // Deliberately excludes 0: a threshold of zero would accept every extraction
            // unconditionally, which is not "auto-accept enabled" but "review disabled". If that
            // is genuinely wanted it should be a separate, named, loudly audited setting.
            require(number > 0.0 && number <= 1.0) {
                "$key must be greater than 0 and at most 1 — clear it to mean 'always review' " +
                    "rather than setting it to 0, which would accept everything"
            }
            number
        }

        ConfigKind.WEIGHTS -> {
            val map = value as? Map<*, *>
                ?: throw IllegalArgumentException("$key is an object of named weights, not '$value'")
            val allowed = setOf("gap", "unknown", "expiring", "crossPartnership", "overlappingAssignment")
            val unknownNames = map.keys.map { it.toString() } - allowed
            require(unknownNames.isEmpty()) {
                "$key has no weight named ${unknownNames.joinToString()}; the weights are ${allowed.joinToString()}"
            }
            map.entries.associate { (name, weight) ->
                val number = (weight as? Number)?.toInt()
                    ?: throw IllegalArgumentException("Weight '$name' must be a whole number, not '$weight'")
                require(number >= 0) { "Weight '$name' cannot be negative" }
                name.toString() to number
            }
        }
    }
}

enum class ConfigKind { NUMBER, THRESHOLD, WEIGHTS }

data class ConfigSetting(
    val key: ConfigKey,
    val value: Any?,
    /** False when the effective value is the code default and no row exists. */
    val overridden: Boolean,
    val updatedAt: java.time.Instant?,
    val updatedBy: String?,
)
