package au.crewcomp.platform.time

import io.quarkus.runtime.configuration.ConfigUtils
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference

/**
 * "Today" for the business, and the operating timezone.
 *
 * Spec NFR-5: all business dates are calendar dates in the operating timezone (AWST assumed —
 * confirm O-11), stored as dates rather than timestamps. Everything that needs the current date
 * asks this, so there is exactly one place a date-override can take effect.
 *
 * The POC pinned "today" to 2026-07-25. Production runs on the real clock but keeps an
 * **admin-only** date override for audit reconstruction and testing (§1). The override is
 * refused in production unless explicitly permitted by configuration, because a wrong "today"
 * silently changes every compliance answer the system gives.
 */
@ApplicationScoped
class BusinessClock(
    @ConfigProperty(name = "crewcomp.operating-timezone", defaultValue = "Australia/Perth")
    private val timezone: String,
    @ConfigProperty(name = "crewcomp.date-override.allowed-in-production", defaultValue = "false")
    private val overrideAllowedInProduction: Boolean,
) {
    private val override = AtomicReference<LocalDate?>(null)

    val zone: ZoneId by lazy { ZoneId.of(timezone) }

    /** The current business date, honouring an active date override. */
    fun today(): LocalDate = override.get() ?: LocalDate.now(Clock.system(zone))

    /** The real current date, ignoring any override — for audit timestamps and job scheduling. */
    fun realToday(): LocalDate = LocalDate.now(Clock.system(zone))

    fun overrideValue(): LocalDate? = override.get()

    val isOverridden: Boolean get() = override.get() != null

    /**
     * Sets the date override. Callers must have already checked that the actor is a System
     * Administrator — this class deliberately knows nothing about roles.
     */
    fun overrideToday(date: LocalDate?) {
        if (date != null && isProduction() && !overrideAllowedInProduction) {
            throw IllegalStateException(
                "The date override is disabled in production. Enable " +
                    "crewcomp.date-override.allowed-in-production explicitly and audit the act.",
            )
        }
        override.set(date)
    }

    fun clearOverride() = override.set(null)

    private fun isProduction(): Boolean = ConfigUtils.getProfiles().contains("prod")
}
