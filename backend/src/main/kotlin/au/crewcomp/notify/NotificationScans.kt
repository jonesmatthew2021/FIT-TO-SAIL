package au.crewcomp.notify

import au.crewcomp.compliance.ComplianceService
import au.crewcomp.engine.ExpiryImpact
import au.crewcomp.people.CrewStatementRepository
import au.crewcomp.people.UserAccountRepository
import au.crewcomp.platform.config.ConfigService
import au.crewcomp.platform.security.Role
import au.crewcomp.platform.time.BusinessClock
import au.crewcomp.reference.CrewChangeRepository
import au.crewcomp.reference.PartnershipRepository
import au.crewcomp.reference.RequirementRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * §9's **scheduled scans** — the half of notifications that no domain event produces.
 *
 * A domain event knows something happened. A scan knows something is *about to*, and that is a
 * different kind of statement: nothing happens when a certificate drifts to within 90 days of
 * expiry, or when a swing's submission cutoff moves to next Tuesday. Somebody has to go and look.
 *
 * Every scan here obeys three rules, and all three exist because these run daily and unattended:
 *
 *  1. **Idempotent by dedupe key.** Every notification raised carries a key naming the *fact* it is
 *     about — this person, this requirement, this expiry date — so a scan that runs 90 mornings
 *     running produces one notification, not 90. Without that, the one new warning is invisible
 *     among the repeats, and the whole feature becomes noise people learn to dismiss.
 *  2. **The key includes the value, not just the subject.** `expiry:{person}:{requirement}:{date}`
 *     rather than `expiry:{person}:{requirement}`: if the expiry date changes because a renewal was
 *     recorded, that is a new fact and deserves a new notification.
 *  3. **A scan reports what it did.** The returned summary is the job log and ADM-10's health view.
 *     A scan that silently does nothing is indistinguishable from a scan that is not running.
 *
 * These read compliance answers from [ComplianceService] rather than deriving anything: a
 * notification that disagreed with the screen would be worse than no notification (AUTH-1).
 */
@ApplicationScoped
class NotificationScans(
    private val compliance: ComplianceService,
    private val notifications: NotificationService,
    private val accounts: UserAccountRepository,
    private val crewStatements: CrewStatementRepository,
    private val partnerships: PartnershipRepository,
    private val crewChanges: CrewChangeRepository,
    private val requirements: RequirementRepository,
    private val config: ConfigService,
    private val clock: BusinessClock,
) {

    /**
     * §9's expiry lead-time job.
     *
     * Two audiences from one pass, because they care about different things:
     *
     *  * **The crew member** is told their qualification is expiring, whatever it affects. It is
     *    their certificate and their responsibility to renew it (MOB-1, MOB-3).
     *  * **Coordinators** are told only when the expiry actually lands on or inside an upcoming
     *    assignment — `impact != none`. That is §9's "roster gaps → Coordinators": an expiry with no
     *    assignment behind it is the crew member's admin, not a planning problem, and routing every
     *    one of them to a coordinator is how the list stops being read.
     *
     * ### Answering stops the chasing
     *
     * A crew member who has tapped "Course booked" (MOB-5) has answered, and repeating the question
     * every morning is how a warning system teaches people to ignore it. So a statement against
     * **this expiry date** suppresses the crew-facing warning — and only that one:
     *
     *  * The **coordinator's** notice still fires. A booked course is not a held certificate, and a
     *    planner deciding whether to crew a swing needs the risk, not the reassurance.
     *  * Renewing the certificate moves the expiry, so no statement matches the new one and the
     *    chasing resumes by itself. That is the same "the key includes the value" rule as the dedupe
     *    key beside it, and it is why the statement records the date it was about.
     *
     * **Which statements earn it is [au.crewcomp.people.Suppression]'s answer, not this scan's.**
     * "Course booked" is the crew member's own word and is believed until ADM-11 contradicts it; a
     * MOB-8 seat *request* earns nothing until a coordinator actions it, because asking for a seat
     * is not having one and the certificate is lapsing either way.
     */
    @Transactional
    fun expiryScan(): String {
        val leadDays = config.expiryLeadDays()
        val alerts = compliance.expiryAlerts(leadDays)
        if (alerts.isEmpty()) return "No holdings expiring within $leadDays days"

        val codes = requirementCodes()
        // One query for the whole scan rather than one per alert; this job walks the fleet.
        val answered = crewStatements.suppressedExpiriesUnscoped()
        var toCrew = 0
        var unreachable = 0
        var answeredAlready = 0
        var toCoordinators = 0

        alerts.forEach { alert ->
            val code = codes[alert.requirementId.value] ?: "a qualification"
            val key = "expiry:${alert.person.id.value}:${alert.requirementId.value}:${alert.expiry}"
            val hasAnswered = Triple(
                alert.person.id.value,
                alert.requirementId.value,
                alert.expiry,
            ) in answered

            val account = accounts.forPerson(alert.person.id.value)
            if (hasAnswered) {
                answeredAlready++
            } else if (account == null) {
                // A Person with no account cannot be notified. Counted rather than logged per row:
                // a growing number here means onboarding is behind, which is worth seeing.
                unreachable++
            } else {
                notifications.raise(
                    recipientUserAccountId = account.requiredId,
                    kind = NotificationKind.EXPIRY_WARNING,
                    // SEC-13: the title is the only field a push payload carries. It names no
                    // qualification, so a lock-screen preview cannot leak a medical condition.
                    title = "A qualification is expiring soon",
                    body = "Your $code expires on ${alert.expiry} — ${alert.daysRemaining} days away.",
                    deepLink = "crewcomp://certifications",
                    dedupeKey = key,
                )
                toCrew++
            }

            if (alert.impact != ExpiryImpact.NONE) {
                toCoordinators += notifications.raiseForRoles(
                    roles = listOf(Role.CREW_COORDINATOR),
                    kind = NotificationKind.EXPIRY_AFFECTS_ROSTER,
                    title = "An expiry affects an upcoming swing",
                    body = "${alert.person.name} (${alert.person.sam}): $code expires ${alert.expiry}, " +
                        "${impactWording(alert.impact)}.",
                    deepLink = "/people/${alert.person.id.value}",
                    dedupeKey = "roster-expiry:$key",
                ).size
            }
        }

        return "${alerts.size} expiries within $leadDays days: $toCrew addressed to crew " +
            "($unreachable with no account, $answeredAlready already answered), " +
            "$toCoordinators to coordinators"
    }

    /**
     * §9's cutoff-approaching job, for coordinators.
     *
     * Only swings whose cutoff is **still ahead** are warned about: once the cutoff has passed the
     * warning is not actionable, and a late submission is already handled where it belongs — the
     * register's Q17 acknowledgement, at the moment someone lodges one.
     */
    @Transactional
    fun cutoffScan(): String {
        val leadDays = config.cutoffLeadDays()
        val today = clock.today()
        val horizon = today.plusDays(leadDays)

        val approaching = partnerships.allOrdered()
            .flatMap { crewChanges.forPartnership(it.requiredId) }
            .filter { !it.cutoffDate.isBefore(today) && !it.cutoffDate.isAfter(horizon) }

        if (approaching.isEmpty()) return "No submission cutoffs within $leadDays days"

        var raised = 0
        approaching.forEach { crewChange ->
            val days = ChronoUnit.DAYS.between(today, crewChange.cutoffDate)
            raised += notifications.raiseForRoles(
                roles = listOf(Role.CREW_COORDINATOR, Role.WORKFLOW_MANAGER),
                kind = NotificationKind.CUTOFF_APPROACHING,
                title = "A submission cutoff is approaching",
                body = "${crewChange.partnership.abbrev} ${crewChange.ccId} closes for submissions " +
                    "on ${crewChange.cutoffDate}" + if (days == 0L) " — today." else " — in $days days.",
                deepLink = "/planner?partnership=${crewChange.partnership.abbrev}&cc=${crewChange.ccId}",
                dedupeKey = "cutoff:${crewChange.partnership.abbrev}:${crewChange.ccId}",
            ).size
        }
        return "${approaching.size} cutoffs within $leadDays days; $raised notifications raised"
    }

    /**
     * §9's "quota shortfalls and roster gaps → Coordinators".
     *
     * Scoped to swings that are current or upcoming: a shortfall on a swing that has already sailed
     * is history, and history belongs in the register rather than in someone's notification list.
     *
     * The dedupe key carries the shortfall *size* for a quota and the slot ref for a gap, so the
     * list changes when the situation does — filling one of two missing GPH is progress, and a
     * notification that stayed silent about it would be reporting a stale number.
     */
    @Transactional
    fun rosterScan(): String {
        val today = clock.today()
        // Hoisted: this is a catalogue read, and issuing it inside the loop would be one query per
        // swing for a table that does not change during a scan.
        val codes = requirementCodes()
        var quotaNotices = 0
        var gapNotices = 0
        var swings = 0

        partnerships.allOrdered().forEach { partnership ->
            crewChanges.forPartnership(partnership.requiredId)
                .filter { !it.toDate.isBefore(today) }
                .take(SWINGS_AHEAD)
                .forEach { crewChange ->
                    swings++
                    val evaluation = compliance.evaluateSwing(partnership.abbrev, crewChange.ccId)

                    evaluation.quotas.filterNot { it.satisfied }.forEach { quota ->
                        val code = codes[quota.requirementId.value] ?: "a requirement"
                        quotaNotices += notifications.raiseForRoles(
                            roles = listOf(Role.CREW_COORDINATOR),
                            kind = NotificationKind.QUOTA_SHORTFALL,
                            title = "A quota is short on an upcoming swing",
                            body = "${partnership.abbrev} ${crewChange.ccId}: " +
                                "${quota.footnote} needs ${quota.min} with $code" +
                                (quota.shift?.let { " on ${it.wire}" } ?: "") +
                                " and has ${quota.actual}.",
                            deepLink = "/planner?partnership=${partnership.abbrev}&cc=${crewChange.ccId}",
                            dedupeKey = "quota:${partnership.abbrev}:${crewChange.ccId}:" +
                                "${quota.footnote}:${quota.requirementId.value}:" +
                                "${quota.shift?.wire ?: "swing"}:${quota.shortfall}",
                        ).size
                    }

                    if (evaluation.openSlots.isNotEmpty()) {
                        gapNotices += notifications.raiseForRoles(
                            roles = listOf(Role.CREW_COORDINATOR),
                            kind = NotificationKind.ROSTER_GAP,
                            title = "An upcoming swing has unfilled slots",
                            body = "${partnership.abbrev} ${crewChange.ccId} has " +
                                "${evaluation.openSlots.size} unfilled " +
                                (if (evaluation.openSlots.size == 1) "slot" else "slots") +
                                " (${evaluation.openSlots.joinToString { it.ref.toString() }}).",
                            deepLink = "/planner?partnership=${partnership.abbrev}&cc=${crewChange.ccId}",
                            dedupeKey = "roster-gap:${partnership.abbrev}:${crewChange.ccId}:" +
                                evaluation.openSlots.joinToString("-") { it.ref.toString() },
                        ).size
                    }
                }
        }

        return "$swings upcoming swings scanned: $quotaNotices quota notices, $gapNotices roster-gap notices"
    }

    private fun requirementCodes(): Map<Long, String> =
        requirements.listAll().associate { it.requiredId to it.code }

    private fun impactWording(impact: ExpiryImpact): String = when (impact) {
        ExpiryImpact.EXPIRED_BEFORE_SWING -> "before their next assignment starts"
        ExpiryImpact.MID_SWING -> "part-way through their next assignment"
        ExpiryImpact.NONE -> "with no assignment affected"
    }

    private companion object {
        /**
         * How many swings ahead the roster scan looks, per partnership. Two: the current one and the
         * next. A shortfall three swings out is not yet actionable — nobody has been assigned — and
         * warning about it would fill the list with problems that resolve themselves during planning.
         */
        const val SWINGS_AHEAD = 2
    }
}
