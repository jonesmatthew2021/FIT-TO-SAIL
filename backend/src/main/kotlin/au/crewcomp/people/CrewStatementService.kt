package au.crewcomp.people

import au.crewcomp.courses.CourseCatalogue
import au.crewcomp.engine.HoldingStatus
import au.crewcomp.notify.NotificationKind
import au.crewcomp.notify.NotificationService
import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.Role
import au.crewcomp.reference.RequirementRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.Instant
import java.time.LocalDate

/**
 * The write path for the crew app's one-tap answers (MOB-5, §7.5).
 *
 * ### What this is not
 *
 * It is not a way for a device to write compliance data. Nothing recorded here is read by the §5
 * engine, no cell state moves, and no holding is created — AUTH-1 keeps the answer the engine's and
 * §7.5 lists evidence submission and read-marks as the only client-originated writes to the system
 * of record. A statement is a *message*: the crew member telling the office where they are.
 *
 * Two things follow from that, and both are deliberate:
 *
 *  * **A statement never clears a gap.** A person who says "course booked" against a lapsed medical
 *    still evaluates as a gap, on their phone and on the planner, because they still do not hold it.
 *  * **The one consequence it does have is negative** — [CrewStatementKind.COURSE_BOOKED] stops the
 *    expiry scan chasing that one expiry (see `NotificationScans.expiryScan`). Silence is the only
 *    thing a client-originated statement is trusted enough to cause.
 *
 * ### Idempotency
 *
 * The device's `opId` is the key, and it is unique in the schema. A retry after a dropped
 * connection re-posts the same id and gets the same statement back, rather than a second one —
 * which matters because the app's outbox will re-post an operation it never saw a verdict for.
 */
@ApplicationScoped
class CrewStatementService(
    private val statements: CrewStatementRepository,
    private val people: PersonRepository,
    private val accounts: UserAccountRepository,
    private val holdings: QualificationHoldingRepository,
    private val requirements: RequirementRepository,
    private val notifications: NotificationService,
    private val courses: CourseCatalogue,
    private val policy: AccessPolicy,
    private val audit: AuditWriter,
) {

    /**
     * Records one statement and tells the Crew Coordinators about it.
     *
     * @param opId the device's queue-entry id — the idempotency key (§7.6).
     * @param personId whose statement it is. The caller has already resolved this from the
     *   authenticated actor (`SyncService` takes no person id from a client), and
     *   [AccessPolicy.assertCanSeePerson] re-checks it here rather than trusting that: for a crew
     *   actor the scope is their own person and nothing else, so a caller that ever passed
     *   somebody else's id fails at this line instead of writing the row.
     * @param subjectRef MOB-8 only: the course option the crew member picked. Required for the two
     *   course kinds, because a seat request that does not say which seat is not actionable — and
     *   refused for the other two, which name nothing beyond the requirement.
     */
    @Transactional
    fun record(
        opId: String,
        personId: Long,
        requirementId: Long,
        kind: CrewStatementKind,
        subjectRef: String? = null,
    ): CrewStatement {
        policy.assertCanSeePerson(personId)
        if (kind in CrewStatementKind.COURSE_KINDS) {
            require(!subjectRef.isNullOrBlank()) {
                "${kind.operation} requires a subjectRef naming the course option"
            }
        } else {
            require(subjectRef == null) { "${kind.operation} does not name a course option" }
        }

        statements.byOpId(opId)?.let { existing ->
            // A replay. Return what was already recorded rather than recording it again — and
            // check the owner, because an opId arriving for someone else's statement is not a
            // replay, it is a device presenting an id it should not have.
            require(existing.person.id == personId) { "Operation '$opId' is not yours to replay" }
            return existing
        }

        val person = people.findById(personId)
            ?: throw IllegalArgumentException("No person $personId")
        val requirement = requirements.findById(requirementId)
            ?: throw IllegalArgumentException("No requirement $requirementId")

        // Resolved server-side, and stored beside the key rather than instead of it. The key is
        // what a coordinator would use to find the course; the label is what tells them which
        // course it was after the option has been withdrawn from the catalogue. A device's own
        // summary text is neither — see [CrewStatement.subjectLabel].
        val option = subjectRef?.let { courses.byRef(it) }

        val statement = CrewStatement().apply {
            this.opId = opId
            this.person = person
            this.requirement = requirement
            this.kind = kind
            this.subjectRef = subjectRef
            this.subjectLabel = option?.label
            this.aboutExpiry = expiringHoldingDate(personId, requirementId)
            stampCreated(policy.actor().label)
        }
        statements.persist(statement)

        audit.record(
            entityType = "CrewStatement",
            event = "crew_statement.recorded",
            entityId = statement.id,
            businessKey = "${person.sam}/${requirement.code}",
            after = mapOf(
                "kind" to kind.wire,
                "opId" to opId,
                "aboutExpiry" to statement.aboutExpiry?.toString(),
                "subjectRef" to subjectRef,
                "subjectLabel" to statement.subjectLabel,
            ),
        )

        notifyCoordinators(statement, person, requirement.code)
        return statement
    }

    // -----------------------------------------------------------------------
    // ADM-11 — the queue
    // -----------------------------------------------------------------------

    /** The worklist. @param status open, actioned, dismissed, or null for everything. */
    @Transactional
    fun queue(status: CrewStatementStatus? = null): List<CrewStatement> {
        policy.require(*READERS)
        return statements.queueUnscoped(status)
    }

    /** How many requests nobody has dealt with — the nav badge, and the only number ADM-11 needs. */
    @Transactional
    fun openCount(): Long {
        policy.require(*READERS)
        return statements.openCountUnscoped()
    }

    /**
     * The coordinator did the thing: confirmed the booking, arranged the help.
     *
     * Terminal. There is no reopen, for the same reason the register has none — a crew member who
     * is still waiting asks again, and the second statement carries its own date.
     */
    @Transactional
    fun action(statementId: Long, note: String): CrewStatement = decide(
        statementId = statementId,
        to = CrewStatementStatus.ACTIONED,
        note = note,
        event = "crew_statement.actioned",
    )

    /**
     * Nothing to do, or the office cannot confirm it.
     *
     * For a `course_booked` statement this **resumes the expiry chasing** the statement had
     * silenced, because the suppression query excludes dismissed rows. That is the whole safety
     * valve: without it the queue could only ever agree with the crew member.
     */
    @Transactional
    fun dismiss(statementId: Long, note: String): CrewStatement = decide(
        statementId = statementId,
        to = CrewStatementStatus.DISMISSED,
        note = note,
        event = "crew_statement.dismissed",
    )

    /**
     * The one place a request leaves the queue.
     *
     * The note is **required**, in both directions. A queue emptied with no explanation is
     * indistinguishable from one emptied to clear the badge — the same argument ADM-7 makes for its
     * resolution note, and it matters more here because the person on the other end of a dismissal
     * is a crew member who asked for something.
     */
    private fun decide(
        statementId: Long,
        to: CrewStatementStatus,
        note: String,
        event: String,
    ): CrewStatement {
        policy.require(*DECIDERS)

        val clean = note.trim()
        require(clean.isNotEmpty()) { "Deciding a crew request needs a note saying what was done" }

        // Fetch-joined rather than loaded by id: this method returns the entity, and the DTO maps
        // it after the transaction has closed.
        val statement = statements.findWithContextUnscoped(statementId)
            ?: throw EntityNotFoundException("No crew request $statementId")
        require(statement.isOpen) {
            "Crew request ${statement.requiredId} was already ${statement.status.wire}"
        }

        val before = snapshot(statement)
        val actor = policy.actor()
        statement.status = to
        statement.decisionNote = clean
        statement.decidedAt = Instant.now()
        statement.decidedBy = actor.label
        statement.stampUpdated(actor.label)

        audit.record(
            entityType = "CrewStatement",
            event = event,
            entityId = statement.id,
            businessKey = "${statement.person.sam}/${statement.requirement.code}",
            before = before,
            after = snapshot(statement),
        )

        notifyCrewMember(statement, clean)
        return statement
    }

    /**
     * Tells the crew member what the office decided.
     *
     * The loop the outbox opens is only closed here. Without it a crew member taps "Course booked",
     * the row says "Sent to the office", and nothing ever happens on their phone again — which is a
     * button that talks to a void, and worse than no button. The dismissal half matters most: it is
     * the office saying "we could not find that, over to you", and it changes what the person has to
     * do next.
     *
     * The decision note travels in the body, and ADM-11's form says so above the field. Two reasons
     * that is the right way round: a bare "dismissed" is a door closing rather than an answer, and a
     * coordinator who knows the crew member reads it writes "can you forward the confirmation email"
     * instead of "no record".
     *
     * SEC-13: the title carries neither the qualification nor the verdict, because the title is the
     * only field a push payload may carry and a lock screen is read over someone's shoulder.
     *
     * The statement row itself also reaches the device, through the sync payload — this is the
     * *prompt*, not the record. A crew member who never opens the notification still sees the
     * decision on the requirement.
     */
    private fun notifyCrewMember(statement: CrewStatement, note: String) {
        val account = accounts.forPerson(statement.person.requiredId) ?: return

        val actioned = statement.status == CrewStatementStatus.ACTIONED
        // The **title**, not the code. A coordinator lives in `MS-02`; a crew member knows it as
        // Sea Survival, and every screen in the crew app names it that way.
        val name = statement.requirement.title
        notifications.raise(
            recipientUserAccountId = account.requiredId,
            kind = if (actioned) {
                NotificationKind.CREW_REQUEST_ACTIONED
            } else {
                NotificationKind.CREW_REQUEST_DISMISSED
            },
            title = "The office answered your request",
            body = if (actioned) {
                "$name — the office has it in hand. $note"
            } else {
                "$name — the office could not act on this. $note"
            },
            deepLink = "crewcomp://certifications/${statement.requirement.requiredId}",
            // Terminal, so this fires once per statement — but the key costs nothing and makes a
            // replayed decision (a retried request, a future reopen) impossible to double-send.
            dedupeKey = "crew-statement-decided:${statement.opId}",
        )
    }

    private fun snapshot(statement: CrewStatement): Map<String, Any?> = mapOf(
        "kind" to statement.kind.wire,
        "status" to statement.status.wire,
        "decisionNote" to statement.decisionNote,
        "decidedBy" to statement.decidedBy,
    )

    /**
     * The expiry this statement is about, or null.
     *
     * Only a `held_expiry` holding has one. A gap, a perpetual holding and an unknown all give
     * null, which is correct: there is no expiry warning about any of them, so there is nothing for
     * the statement to be about and nothing for it to suppress.
     */
    private fun expiringHoldingDate(personId: Long, requirementId: Long): LocalDate? =
        holdings.find(personId, requirementId)
            ?.takeIf { it.status == HoldingStatus.HELD_EXPIRY }
            ?.expiryDate

    /**
     * §9 routing: a crew member's answer is a Crew Coordinator's business.
     *
     * The fan-out may be **empty**, and that is not a failure — back-office accounts do not exist
     * until the identity spike creates them (ADR 0003), exactly as for every other write path here.
     * It is also the reason the statement is a row: with no recipient, the row is the only place
     * the answer survives.
     *
     * SEC-13: the title names neither the person nor the qualification, because the title is the
     * only field a push payload carries.
     */
    private fun notifyCoordinators(
        statement: CrewStatement,
        person: Person,
        requirementCode: String,
    ) {
        val (kind, title, body) = when (statement.kind) {
            CrewStatementKind.COURSE_BOOKED -> Triple(
                NotificationKind.CREW_PROGRESS_REPORTED,
                "A crew member has reported progress",
                "${person.name} (${person.sam}) says a course is booked for $requirementCode" +
                    (statement.aboutExpiry?.let { ", which expires $it" } ?: "") + ".",
            )

            CrewStatementKind.HELP_REQUESTED -> Triple(
                NotificationKind.CREW_HELP_REQUESTED,
                "A crew member has asked for help",
                "${person.name} (${person.sam}) needs help arranging $requirementCode.",
            )

            // MOB-8. Both name a course date, and the body says which — a coordinator who has to
            // ring a provider needs the date in the message, not one click away. The two are
            // deliberately separate sentences: booking a seat and chasing a waitlist are different
            // jobs, and a coordinator triaging their morning should be able to tell them apart
            // before opening anything.
            CrewStatementKind.SEAT_REQUESTED -> Triple(
                NotificationKind.CREW_COURSE_REQUESTED,
                "A crew member has asked for a course seat",
                "${person.name} (${person.sam}) wants a seat on $requirementCode" +
                    (statement.subjectLabel?.let { ": $it" } ?: "") + ".",
            )

            CrewStatementKind.WAITLISTED -> Triple(
                NotificationKind.CREW_COURSE_REQUESTED,
                "A crew member has joined a course waitlist",
                "${person.name} (${person.sam}) is waitlisted for $requirementCode" +
                    (statement.subjectLabel?.let { ": $it" } ?: "") +
                    " — no seats were showing when they asked.",
            )
        }

        notifications.raiseForRoles(
            roles = listOf(Role.CREW_COORDINATOR),
            kind = kind,
            title = title,
            body = body,
            // ADM-11's queue, not the person page: the notification says something arrived, and
            // the thing that can be *done* about it is in the queue. The person is one click on
            // from there.
            deepLink = "/crew-requests",
            // Keyed on the statement's own opId, so a replay that got past the row check — a
            // concurrent duplicate delivery, say — still cannot produce a second notification.
            dedupeKey = "crew-statement:${statement.opId}",
        )
    }

    private companion object {
        /**
         * Who may read the queue. The wider set, because a crew request is context for anyone
         * planning a swing or working the register, not just for whoever will action it.
         */
        val READERS = arrayOf(
            Role.CREW_COORDINATOR, Role.WORKFLOW_MANAGER, Role.COMPLIANCE_LEAD,
            Role.DATA_STEWARD, Role.SYSTEM_ADMINISTRATOR,
        )

        /**
         * Who may decide one. Narrower: arranging a course and confirming a booking is the Crew
         * Coordinator's job (§9 routes these to them), and a dismissal restarts the chasing of a
         * named crew member — which is not something a Data Steward should be able to do while
         * tidying data.
         */
        val DECIDERS = arrayOf(
            Role.CREW_COORDINATOR, Role.WORKFLOW_MANAGER, Role.SYSTEM_ADMINISTRATOR,
        )
    }
}
