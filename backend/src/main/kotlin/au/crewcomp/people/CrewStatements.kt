package au.crewcomp.people

import au.crewcomp.platform.persistence.AuditedEntity
import au.crewcomp.platform.security.ScopeGuard
import au.crewcomp.reference.Requirement
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

/**
 * A crew member's own statement about one of their requirements (§7.5, MOB-5), and the office's
 * disposition of it (ADM-11).
 *
 * "I have booked the course." "I need help arranging it." Both are *answers*, not decisions: the
 * compliance verdict stays the engine's (AUTH-1), and nothing in `au.crewcomp.engine` reads this
 * table. What a statement does is tell the office where a person is, so that the chasing can stop
 * and a coordinator can act.
 *
 * ### One row, two authors
 *
 * The statement half — [kind], [requirement], [aboutExpiry] — is **write-once**. Changing your mind
 * is a new statement, not an edit: the office needs to see that the answer moved, and when. The
 * queue half — [status], [decisionNote], [decidedAt] — belongs to whoever worked it, and is the
 * only part that ever changes. Hence [AuditedEntity]: `created_*` is the crew member,
 * `updated_*` is the coordinator, and the audit events (`crew_statement.recorded` versus
 * `.actioned` / `.dismissed`) keep the two apart in the trail.
 */
@Entity
@Table(name = "crew_statement")
class CrewStatement : AuditedEntity() {

    /**
     * The device's queue-entry id, and therefore the idempotency key (§7.6).
     *
     * Unique in the schema, which is what makes a replayed operation a no-op rather than a second
     * answer — the constraint does the work, not a read-then-write that two concurrent deliveries
     * could both pass.
     */
    @Column(name = "op_id", nullable = false)
    lateinit var opId: String

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    lateinit var person: Person

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requirement_id", nullable = false)
    lateinit var requirement: Requirement

    @Column(name = "kind", nullable = false)
    var kindValue: String = CrewStatementKind.COURSE_BOOKED.wire

    /**
     * MOB-8: the course option this is about, as the catalogue adapter names it.
     *
     * Opaque here, and deliberately so — it is a key into something that may not be a table in
     * this database for long (see `CourseCatalogue`). Null for the two kinds that name nothing
     * beyond the requirement.
     */
    @Column(name = "subject_ref")
    var subjectRef: String? = null

    /**
     * The same option as a sentence, rendered by the **server** when the statement was recorded.
     *
     * Denormalised on purpose. A coordinator working this queue in three weeks needs to know which
     * course was asked for even if the option has since been withdrawn, and a dangling
     * [subjectRef] tells them nothing. It is the server's rendering rather than the device's
     * summary for the same reason the attestation's signature line is: the office's record of what
     * a crew member asked for should not be composed on the crew member's phone.
     */
    @Column(name = "subject_label")
    var subjectLabel: String? = null

    /**
     * The expiry this statement was about, as the holding stood when it was made. Null when there
     * was no expiring holding — a statement about a gap suppresses nothing, because a gap raises
     * no expiry warning to suppress.
     */
    @Column(name = "about_expiry")
    var aboutExpiry: LocalDate? = null

    /** §10.3 sync cursor, trigger-assigned — see [Person.updatedSeq]. */
    @Column(name = "updated_seq", insertable = false, updatable = false)
    var updatedSeq: Long = 0

    // -----------------------------------------------------------------------
    // ADM-11 — the office's disposition
    // -----------------------------------------------------------------------

    @Column(name = "status", nullable = false)
    var statusValue: String = CrewStatementStatus.OPEN.wire

    /** What the coordinator did, in their words. Required on a decision. */
    @Column(name = "decision_note")
    var decisionNote: String? = null

    @Column(name = "decided_at")
    var decidedAt: Instant? = null

    @Column(name = "decided_by")
    var decidedBy: String? = null

    var kind: CrewStatementKind
        get() = CrewStatementKind.fromWire(kindValue)
        set(value) {
            kindValue = value.wire
        }

    var status: CrewStatementStatus
        get() = CrewStatementStatus.fromWire(statusValue)
        set(value) {
            statusValue = value.wire
        }

    val isOpen: Boolean get() = status == CrewStatementStatus.OPEN
}

/**
 * Where a crew request has got to (ADM-11).
 *
 * Three states, and **both non-open states are terminal**. There is no reopen, for the same reason
 * the register has none: a crew member who is still waiting asks again, and that second statement
 * is a better signal than a reopened first one — it carries its own date, and two rows say
 * "we did not fix this" in a way one bouncing row does not.
 */
enum class CrewStatementStatus(val wire: String) {
    /** Nobody has looked at it. */
    OPEN("open"),

    /** A coordinator did the thing: confirmed the booking, arranged the help. */
    ACTIONED("actioned"),

    /**
     * Nothing to do, or the office cannot confirm it.
     *
     * For a `course_booked` statement this is load-bearing rather than tidy: dismissing it
     * **resumes the expiry chasing** the statement had silenced. "We have no record of that
     * booking" has to put the warning back, or a mis-tap goes unchased until the certificate lapses.
     */
    DISMISSED("dismissed");

    companion object {
        fun fromWire(wire: String): CrewStatementStatus =
            entries.firstOrNull { it.wire == wire }
                ?: throw IllegalArgumentException("Unknown crew statement status: $wire")
    }
}

/**
 * When a statement stops the expiry scan chasing the crew member about the expiry it names.
 *
 * Suppression is the only consequence a client-originated statement is trusted enough to cause, so
 * what earns it is worth stating rather than leaving as an `if` in a query. Three answers, and the
 * middle one is the whole design:
 *
 *  * [NEVER] — the statement is a message, and a message changes nothing. Asking for help is not
 *    having the certificate.
 *  * [ON_WORD] — the crew member's own assertion is enough, **until the office contradicts it**.
 *    "I have booked the course" is a fact only they know, so the system believes them; dismissing
 *    the request in ADM-11 puts the chasing back. That safety valve is why a dismissal exists.
 *  * [ON_ACTION] — only the office's answer counts. A seat *request* is not a seat: nothing is
 *    booked, and a crew member who asked and was never answered should still be chased, because
 *    their certificate is still lapsing. When a coordinator actions it they have booked the seat,
 *    and at that point it is as good as [ON_WORD]'s claim and better evidenced.
 */
enum class Suppression { NEVER, ON_WORD, ON_ACTION }

/**
 * The four answers the crew app can post today.
 *
 * **Two names each, and they are not interchangeable.** [wire] is the domain's word, and it is what
 * the console and the database store. [operation] is the sync-queue operation the device posted to
 * raise it, and it is what travels back in `CrewStatementSyncDto.kind` — because the device's whole
 * vocabulary for these is `IntentKind` in `mobile/lib/src/domain/intents.dart`, and a payload that
 * spoke the other language would make the app match a statement to the tap that produced it through
 * a translation table nobody would keep in step.
 *
 * That is not hypothetical: the first version sent [wire], the app matched on [operation], the join
 * silently found nothing, and a dismissed request rendered as no answer at all — on a simulator,
 * with every unit test green, because the fixtures encoded the assumption rather than the wire.
 *
 * Both are a compatibility surface. Renaming either changes what a device that has been offline for
 * a fortnight can say, or what it can understand when it is told.
 */
enum class CrewStatementKind(
    val wire: String,
    val operation: String,
    val suppression: Suppression,
) {
    /** MOB-5: the crew member has a course booked. Suppresses expiry chasing for that expiry. */
    COURSE_BOOKED("course_booked", "requirement.progress", Suppression.ON_WORD),

    /**
     * MOB-5 / MOB-0: the crew member wants help arranging it.
     *
     * Suppresses nothing, deliberately. The screen promises "nothing is recorded against you for
     * asking for help", and that promise is about *consequence* — no compliance state moves, the
     * engine never sees this, and no scan changes behaviour. It is not a promise that the request
     * evaporates: a request nobody can find is a request nobody can act on, which would make the
     * button a placebo.
     */
    HELP_REQUESTED("help_requested", "requirement.help", Suppression.NEVER),

    /**
     * MOB-8: a seat on one specific course date, named by [CrewStatement.subjectRef].
     *
     * An *ask*, and the office's answer is what makes it real. Nothing here holds the seat: this
     * system has no contract with the provider and must not behave as though it did, so the
     * catalogue's seat count is what the provider last said and this row is what the crew member
     * would like. A coordinator books it and actions the request.
     */
    SEAT_REQUESTED("seat_requested", "course.seat_request", Suppression.ON_ACTION),

    /**
     * MOB-8: the same ask against a date with no seats left.
     *
     * A separate kind rather than a flag, because the office's job is different — a waitlist has to
     * be *chased* with the provider, where a seat request is booked once. The queue says which one
     * it is, so a coordinator can tell a "no" that is final from one that may still turn.
     */
    WAITLISTED("waitlisted", "course.waitlist", Suppression.ON_ACTION);

    companion object {
        fun fromWire(wire: String): CrewStatementKind =
            entries.firstOrNull { it.wire == wire }
                ?: throw IllegalArgumentException("Unknown crew statement kind: $wire")

        /** Kinds whose suppression the crew member's word alone earns. */
        val SUPPRESS_ON_WORD: List<String> =
            entries.filter { it.suppression == Suppression.ON_WORD }.map { it.wire }

        /** Kinds that suppress only once the office has actioned them. */
        val SUPPRESS_ON_ACTION: List<String> =
            entries.filter { it.suppression == Suppression.ON_ACTION }.map { it.wire }

        /** The kinds MOB-8 raises — the ones that carry a course option. */
        val COURSE_KINDS: Set<CrewStatementKind> = setOf(SEAT_REQUESTED, WAITLISTED)
    }
}

@ApplicationScoped
class CrewStatementRepository(
    private val scopeGuard: ScopeGuard,
) : PanacheRepositoryBase<CrewStatement, Long> {

    /**
     * The row a replayed operation already created, or null.
     *
     * Unscoped on purpose, and safe: `op_id` is a client-minted UUID, so this cannot be used to
     * discover anyone else's statement without already holding their opaque id — and the one
     * caller checks the person on what it finds before returning it.
     */
    fun byOpId(opId: String): CrewStatement? = find("opId", opId).firstResult()

    /**
     * Every **standing** statement that has earned its silence, as `person → requirement → the
     * expiry it was about`. The expiry scan skips a crew warning for anything in this set.
     *
     * Three filters, and each is the point:
     *
     *  * `about_expiry is not null` — the expiry is part of the question rather than a detail of the
     *    answer. A statement silences the warning about *that* date; renew the certificate and the
     *    date moves, nothing matches, and the chasing resumes on its own (§9's "the key includes the
     *    value, not just the subject").
     *  * **[Suppression.ON_WORD] excludes `dismissed`.** A coordinator who answers "we have no
     *    record of that booking" puts the warning back. Without this the queue could only ever
     *    agree with the crew member, and a mis-tap would go unchased until the certificate lapsed.
     *  * **[Suppression.ON_ACTION] requires `actioned`.** A seat *request* is not a seat. Someone
     *    who asked and was never answered is still lapsing and should still be chased; once a
     *    coordinator has booked it and said so, they should not be.
     *
     * One query for the whole scan. The per-row alternative is a query per alert, on a job that
     * already walks every expiring holding in the fleet.
     */
    fun suppressedExpiriesUnscoped(): Set<Triple<Long, Long, LocalDate>> =
        getEntityManager()
            .createQuery(
                "select s.person.id, s.requirement.id, s.aboutExpiry from CrewStatement s " +
                    "where s.aboutExpiry is not null and (" +
                    "  (s.kindValue in :onWord and s.statusValue <> :dismissed)" +
                    "  or (s.kindValue in :onAction and s.statusValue = :actioned)" +
                    ")",
                Array<Any>::class.java,
            )
            .setParameter("onWord", CrewStatementKind.SUPPRESS_ON_WORD)
            .setParameter("onAction", CrewStatementKind.SUPPRESS_ON_ACTION)
            .setParameter("dismissed", CrewStatementStatus.DISMISSED.wire)
            .setParameter("actioned", CrewStatementStatus.ACTIONED.wire)
            .resultList
            .map { Triple((it[0] as Number).toLong(), (it[1] as Number).toLong(), it[2] as LocalDate) }
            .toSet()

    /**
     * Whether anything the crew member said about this requirement is still standing — MOB-11's
     * `inHand`, and the reason a supervisor's watch does not chase somebody who has already acted.
     *
     * Deliberately wider than [suppressedExpiriesUnscoped]: *asking for help* is not a reason to
     * stop the expiry reminders, but it is very much a reason not to nudge the person again.
     * Unscoped and taking a set of people, because the caller has already established which crew
     * it may see and one query beats one per member.
     */
    fun openOrActionedByPersonUnscoped(personIds: Collection<Long>): Set<Long> {
        if (personIds.isEmpty()) return emptySet()
        return getEntityManager()
            .createQuery(
                "select distinct s.person.id from CrewStatement s " +
                    "where s.person.id in :personIds and s.statusValue <> :dismissed",
                Long::class.javaObjectType,
            )
            .setParameter("personIds", personIds)
            .setParameter("dismissed", CrewStatementStatus.DISMISSED.wire)
            .resultList
            .toSet()
    }

    /**
     * One crew member's own statements, for their §10.3 snapshot.
     *
     * Scoped, like every other read the snapshot makes: `SyncService` has already established that
     * the person is the authenticated crew member, and this is the second lock on that door.
     * There is deliberately no unscoped variant to reach for — the back-office read is
     * [queueUnscoped], which says what it is in its name and is gated by a role check in the
     * service.
     */
    fun forPersonScoped(personId: Long): List<CrewStatement> {
        scopeGuard.assertVisible(personId)
        return find(
            "from CrewStatement s join fetch s.requirement where s.person.id = ?1 " +
                "order by s.createdAt desc",
            personId,
        ).list()
    }

    /**
     * ADM-11's worklist, oldest first — the oldest unanswered request is the one somebody is still
     * waiting on.
     *
     * Unscoped, and the role check in the service is what gates it: this is a back-office queue over
     * the whole fleet, in the same position as ADM-7's worklist and the expiry scan.
     *
     * Fetch-joins person, their position and the requirement because the DTO reads all three after
     * the service transaction has closed — the `LazyInitializationException` this codebase has paid
     * for twice already.
     */
    fun queueUnscoped(status: CrewStatementStatus?): List<CrewStatement> {
        val filter = if (status == null) "" else "where s.statusValue = :status "
        val query = find(
            "from CrewStatement s " +
                "join fetch s.person p join fetch p.position join fetch p.partnership " +
                "join fetch s.requirement " +
                filter +
                "order by s.statusValue, s.createdAt",
            if (status == null) emptyMap() else mapOf("status" to status.wire),
        )
        return query.list()
    }

    fun openCountUnscoped(): Long = count("statusValue", CrewStatementStatus.OPEN.wire)

    /**
     * One row with everything [au.crewcomp.api.CrewRequestDto] reads, for the write path.
     *
     * `findById` would do for the update itself and then 500 on the way out: mapping happens after
     * the service transaction closes, so `person.position.name` reached there is a
     * `LazyInitializationException` on a screen and in no unit test. This repository's list query
     * and its by-id query therefore carry the **same** fetch set — the pair that has to move
     * together, per the note in `backend/CLAUDE.md`.
     */
    fun findWithContextUnscoped(id: Long): CrewStatement? = find(
        "from CrewStatement s " +
            "join fetch s.person p join fetch p.position join fetch p.partnership " +
            "join fetch s.requirement where s.id = ?1",
        id,
    ).firstResult()
}
