package au.crewcomp.people

import au.crewcomp.compliance.ComplianceService
import au.crewcomp.engine.CellEvaluation
import au.crewcomp.engine.CellState
import au.crewcomp.engine.rollUpSeverity
import au.crewcomp.notify.NotificationKind
import au.crewcomp.notify.NotificationRepository
import au.crewcomp.notify.NotificationService
import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.security.AccessDeniedException
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.Role
import au.crewcomp.platform.time.BusinessClock
import au.crewcomp.reference.CrewChange
import au.crewcomp.reference.RequirementRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * MOB-11 — a supervisor's watch, and the nudge they can send.
 *
 * ### Who supervises whom
 *
 * Nobody maintains an org chart here, and nobody should have to. A supervisor's team is **the
 * people rostered onto a swing they are also rostered onto**, derived from assignments that
 * already exist. Change the roster and the watch changes with it, which is the correct behaviour
 * on a vessel where the team *is* the swing — and it means there is no second structure to drift
 * out of step with the first.
 *
 * The role is `vessel_master`, which §3 already defines as a restricted, read-only role. It did not
 * need inventing; what it needed was a scope narrower than the one [AccessPolicy] gives it. A
 * Vessel Master reads their whole partnership, and a partnership is far more people than the crew
 * standing on their deck. So this class does not use the actor's ambient scope at all:
 * [supervisedCrew] answers only for co-assignment, and it is the **single definition** both the
 * list and [nudge] go through. That is the AUTH-2 rule — a supervisor who could nudge anyone in
 * their partnership would have a wider write than read, which is exactly backwards.
 *
 * ### It never takes a person id it will act on
 *
 * Like `SyncService`, the entry points resolve the supervisor from the authenticated actor. [nudge]
 * does take a target, and it is checked against the computed team rather than trusted — the one
 * field a client chooses that names somebody else.
 *
 * ### What travels
 *
 * A status and one line of reason. Never a document, never a medical detail, never why a
 * certificate lapsed. The privacy rule is the **shape of the payload**, not the discretion of the
 * screen: a device sent the detail and told not to draw it would leak it to anyone who read the
 * local database, which is what SEC-12 encrypts against.
 */
@ApplicationScoped
class TeamService(
    private val assignments: AssignmentRepository,
    private val people: PersonRepository,
    private val accounts: UserAccountRepository,
    private val statements: CrewStatementRepository,
    private val requirements: RequirementRepository,
    private val compliance: ComplianceService,
    private val notifications: NotificationService,
    private val notificationRows: NotificationRepository,
    private val policy: AccessPolicy,
    private val audit: AuditWriter,
    private val clock: BusinessClock,
) {

    /**
     * The authenticated supervisor's watch: everyone sharing their current or next swing.
     *
     * Empty is a real and common answer — a Vessel Master ashore between swings supervises nobody
     * — and it is not the same thing as an error. The app must say "no swing under way", never
     * draw an empty list a supervisor reads as *everyone is fine*.
     */
    @Transactional
    fun myTeam(): TeamView {
        policy.require(Role.VESSEL_MASTER)
        val supervisorId = policy.actor().personId
            ?: throw AccessDeniedException(
                "A supervisor's watch is their own roster; ${policy.actor().label} has no linked " +
                    "Person record",
            )

        val swing = currentOrNextSwing(supervisorId) ?: return TeamView(null, emptyList())
        val crew = supervisedCrew(supervisorId, swing)
        if (crew.isEmpty()) return TeamView(swing, emptyList())

        // One swing evaluation for the whole watch rather than one per member. This is also the
        // partnership check: `evaluateSwing` asserts the actor may see the swing's partnership, so
        // a Vessel Master scoped to somebody else's partnership gets a 403 here rather than a
        // watch. Co-assignment narrows *within* that scope; it does not replace it.
        val evaluation = compliance.evaluateSwing(swing.partnership.abbrev, swing.ccId)
        val byPerson = evaluation.assignments.associateBy { it.evaluation.personId.value }
        val codes = requirements.listAll().associate { it.requiredId to it.code }
        val inHand = statements.openOrActionedByPersonUnscoped(crew.map { it.requiredId })
        val accountByPerson = crew.mapNotNull { person ->
            accounts.forPerson(person.requiredId)?.let { person.requiredId to it.requiredId }
        }.toMap()
        val nudges = notificationRows.latestByKind(accountByPerson.values, NotificationKind.CREW_NUDGED)

        return TeamView(
            swing = swing,
            members = crew.map { person ->
                val cells = byPerson[person.requiredId]?.evaluation?.cells.orEmpty()
                TeamMemberView(
                    person = person,
                    // The roll-up the engine computed, not one recomputed here — a supervisor's
                    // badge and the crew member's own home screen must agree, and they only can
                    // if there is one implementation of "worst".
                    worstState = (byPerson[person.requiredId]?.evaluation?.rollUp ?: CellState.OK).wire,
                    reason = reasonFor(cells, codes),
                    inHand = person.requiredId in inHand,
                    nudgedAt = accountByPerson[person.requiredId]?.let { nudges[it] },
                )
            }.sortedBy { it.person.name },
        )
    }

    /**
     * MOB-11's nudge: "please deal with this".
     *
     * Three properties, and the third is the one worth arguing about.
     *
     *  * **It writes nothing to the system of record.** A nudge is a message, not a compliance
     *    fact. Nothing moves a cell, nothing suppresses a scan.
     *  * **It is authorised by the same co-assignment as the read.** [supervisedCrew] is the only
     *    definition, so a supervisor can never nudge somebody they cannot see.
     *  * **The person nudged is told, and told who sent it.** There is no configuration that turns
     *    that off. A nudge nobody can trace is a way to harass someone quietly, and the delivery
     *    channel is not the point — the in-app record is the source of truth (MOB-3) and remains so
     *    whether or not a push ever lands.
     */
    @Transactional
    fun nudge(sam: String, note: String?) {
        policy.require(Role.VESSEL_MASTER)
        val actor = policy.actor()
        val supervisorId = actor.personId
            ?: throw AccessDeniedException("Only a rostered supervisor may nudge")

        val swing = currentOrNextSwing(supervisorId)
            ?: throw IllegalArgumentException("You have no current swing, so there is nobody to nudge")
        // Matched against the computed watch, not looked up and then checked. A person who is not
        // on the swing simply is not in this list, so there is no ordering in which the lookup
        // succeeds and the authorisation is forgotten.
        val target = supervisedCrew(supervisorId, swing).firstOrNull { it.sam == sam }
            ?: throw AccessDeniedException("$sam is not on your watch")

        // Named after the *sender, the swing and the day*, not the moment. Two taps on the same day
        // are one nudge; tomorrow is a new one. Without this a supervisor's stuck finger is a crew
        // member's notification list — and worse, a device replaying its outbox would look like
        // being chased twice.
        val dedupeKey = "nudge:${swing.ccId}:$supervisorId:${target.requiredId}:${clock.today()}"

        val account = accounts.forPerson(target.requiredId)
        if (account != null && notificationRows.byDedupeKey(account.requiredId, dedupeKey) != null) {
            // Already sent today. Returning quietly rather than auditing again keeps the trail
            // reading as one nudge, which is what happened.
            return
        }

        if (account != null) {
            notifications.raise(
                recipientUserAccountId = account.requiredId,
                kind = NotificationKind.CREW_NUDGED,
                // SEC-13: the title is the only field a push payload may carry, and it names
                // neither the qualification nor the sender's message.
                title = "A message from your supervisor",
                body = buildString {
                    append("${supervisorName(supervisorId)} asked you to look at your certificates ")
                    // "before CC24 starts" is wrong once it has, and a crew member already aboard
                    // reading it would reasonably conclude the message was stale.
                    append(
                        if (clock.today().isBefore(swing.fromDate)) {
                            "before ${swing.ccId} starts."
                        } else {
                            "for ${swing.ccId}."
                        },
                    )
                    note?.trim()?.takeIf { it.isNotEmpty() }?.let { append(" \"$it\"") }
                },
                deepLink = "crewcomp://certifications",
                dedupeKey = dedupeKey,
            )
        }

        // Audited whether or not there was an account to deliver to. That a supervisor chased a
        // named crew member is the traceability, and it must not wait on the identity spike —
        // `delivered` records which of the two happened.
        audit.record(
            entityType = "Person",
            event = "team.nudged",
            entityId = target.requiredId,
            businessKey = target.sam,
            after = mapOf(
                "ccId" to swing.ccId,
                "supervisorPersonId" to supervisorId,
                "delivered" to (account != null),
                "note" to note,
            ),
        )
    }

    /**
     * **The one definition of a supervisor's watch**, and the reason both entry points route
     * through it.
     *
     * Everyone else assigned to [swing], minus the supervisor. Unscoped at the repository, and
     * deliberately so: the co-assignment *is* the restriction, and running it through the actor's
     * ambient [au.crewcomp.platform.security.DataScope] would either widen it to the whole
     * partnership or — for a supervisor whose scope is their own person — deny their whole team.
     */
    private fun supervisedCrew(supervisorId: Long, swing: CrewChange): List<Person> =
        assignments.forCrewChangeUnscoped(swing.requiredId)
            .map { it.person }
            .distinctBy { it.requiredId }
            .filter { it.requiredId != supervisorId }

    /**
     * The swing the supervisor is on now, or the next one they are rostered to.
     *
     * The same "current or next" rule the sync payload's standing uses, and for the same reason: a
     * supervisor preparing for a swing that starts on Monday needs to see that watch today, which
     * is precisely when a nudge is still worth sending.
     */
    private fun currentOrNextSwing(supervisorId: Long): CrewChange? {
        val today = clock.today()
        val mine = assignments.forPersonScoped(supervisorId)
        val inProgress = mine.firstOrNull {
            !today.isBefore(it.crewChange.fromDate) && !today.isAfter(it.crewChange.toDate)
        }
        if (inProgress != null) return inProgress.crewChange
        return mine
            .filter { it.crewChange.fromDate.isAfter(today) }
            .minByOrNull { it.crewChange.fromDate }
            ?.crewChange
    }

    /**
     * One line, and only one, describing the worst thing on this person's plate.
     *
     * "PS-04 not held" — a requirement code and a state, which is the most a supervisor is
     * entitled to. Never the holding, never a document, never *why* a certificate is missing: a
     * lapsed medical and a lapsed forklift ticket read identically here, which is the point.
     *
     * Derived from the same worst cell the roll-up came from, so the badge and the line cannot
     * disagree — and present for **every** non-clear state rather than only the alarming ones. A
     * row reading `pending` with no explanation is a supervisor wondering what is pending.
     */
    private fun reasonFor(cells: List<CellEvaluation>, codes: Map<Long, String>): String? {
        val worst = cells.maxByOrNull { it.state.rollUpSeverity } ?: return null
        val code = codes[worst.requirementId.value] ?: "A requirement"
        return when (worst.state) {
            CellState.GAP -> "$code not held"
            // Formatted, never ISO. `08-16` and `16-08` are two different days to two people in
            // the same crew room, and a supervisor is crew.
            CellState.EXPIRING ->
                "$code expires ${worst.expiry?.let { CREW_DATE.format(it) } ?: "during this swing"}"
            CellState.UNKNOWN -> "$code not confirmed"
            CellState.REVIEW -> "$code needs a look"
            CellState.PENDING -> "$code — a request is with the office"
            CellState.EXEMPT -> "$code — exempted for this swing"
            else -> null
        }
    }

    private fun supervisorName(personId: Long): String =
        people.findById(personId)?.name ?: "Your supervisor"

    private companion object {
        /** `16 Aug 2026`. A crew member is never shown an ISO date, and a supervisor is crew. */
        val CREW_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH)
    }
}

/**
 * A supervisor's watch, and the swing it is a watch over.
 *
 * [swing] is null when they are not rostered anywhere, and the distinction from an empty
 * [members] is what stops the app drawing a reassuring blank: "no swing under way" and "everyone
 * on your watch is fine" are opposite messages.
 */
data class TeamView(val swing: CrewChange?, val members: List<TeamMemberView>)

data class TeamMemberView(
    val person: Person,
    /** A §5.1 cell state, the worst across everything asked of them. */
    val worstState: String,
    val reason: String?,
    /** Something is already moving, so chasing them would be chasing someone who has acted. */
    val inHand: Boolean,
    val nudgedAt: Instant?,
)
