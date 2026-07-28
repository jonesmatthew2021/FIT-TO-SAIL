package au.crewcomp.people

import au.crewcomp.platform.persistence.CreatedEntity
import au.crewcomp.reference.Requirement
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDate

/**
 * A crew member's own statement about one of their requirements (§7.5, MOB-5).
 *
 * "I have booked the course." "I need help arranging it." Both are *answers*, not decisions: the
 * compliance verdict stays the engine's (AUTH-1), and nothing in `au.crewcomp.engine` reads this
 * table. What a statement does is tell the office where a person is, so that the chasing can stop
 * and a coordinator can act.
 *
 * Write-once, hence [CreatedEntity]. Changing your mind is a new statement, not an edit — the
 * office needs to see that the answer moved, and when.
 */
@Entity
@Table(name = "crew_statement")
class CrewStatement : CreatedEntity() {

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
     * The expiry this statement was about, as the holding stood when it was made. Null when there
     * was no expiring holding — a statement about a gap suppresses nothing, because a gap raises
     * no expiry warning to suppress.
     */
    @Column(name = "about_expiry")
    var aboutExpiry: LocalDate? = null

    /** §10.3 sync cursor, trigger-assigned — see [Person.updatedSeq]. */
    @Column(name = "updated_seq", insertable = false, updatable = false)
    var updatedSeq: Long = 0

    var kind: CrewStatementKind
        get() = CrewStatementKind.fromWire(kindValue)
        set(value) {
            kindValue = value.wire
        }
}

/**
 * The two answers the crew app can post today.
 *
 * The wire values are a compatibility surface: they are half of `IntentKind` in
 * `mobile/lib/src/domain/intents.dart`, and the operation names on the sync queue are derived from
 * them. Renaming one silently changes what a device that has been offline for a fortnight is
 * allowed to say.
 */
enum class CrewStatementKind(val wire: String) {
    /** MOB-5: the crew member has a course booked. Suppresses expiry chasing for that expiry. */
    COURSE_BOOKED("course_booked"),

    /**
     * MOB-5 / MOB-0: the crew member wants help arranging it.
     *
     * Suppresses nothing, deliberately. The screen promises "nothing is recorded against you for
     * asking for help", and that promise is about *consequence* — no compliance state moves, the
     * engine never sees this, and no scan changes behaviour. It is not a promise that the request
     * evaporates: a request nobody can find is a request nobody can act on, which would make the
     * button a placebo.
     */
    HELP_REQUESTED("help_requested");

    companion object {
        fun fromWire(wire: String): CrewStatementKind =
            entries.firstOrNull { it.wire == wire }
                ?: throw IllegalArgumentException("Unknown crew statement kind: $wire")
    }
}

@ApplicationScoped
class CrewStatementRepository : PanacheRepositoryBase<CrewStatement, Long> {

    /**
     * The row a replayed operation already created, or null.
     *
     * Unscoped on purpose, and safe: `op_id` is a client-minted UUID, so this cannot be used to
     * discover anyone else's statement without already holding their opaque id — and the one
     * caller checks the person on what it finds before returning it.
     */
    fun byOpId(opId: String): CrewStatement? = find("opId", opId).firstResult()

    /**
     * True when this person has said a course is booked against exactly this expiry date.
     *
     * The expiry is part of the question rather than a detail of the answer: it is what lets the
     * expiry scan go quiet for the fact the crew member answered and start again for the next one
     * (§9's "the key includes the value, not just the subject").
     */
    fun hasCourseBookedForUnscoped(personId: Long, requirementId: Long, expiry: LocalDate): Boolean =
        count(
            "person.id = ?1 and requirement.id = ?2 and kindValue = ?3 and aboutExpiry = ?4",
            personId,
            requirementId,
            CrewStatementKind.COURSE_BOOKED.wire,
            expiry,
        ) > 0

    /**
     * Every `course_booked` statement, as `person → requirement → the expiry it was about`.
     *
     * One query for the whole expiry scan. The per-row alternative is a query per alert, on a job
     * that already walks every expiring holding in the fleet.
     */
    fun courseBookedIndexUnscoped(): Set<Triple<Long, Long, LocalDate>> =
        getEntityManager()
            .createQuery(
                "select s.person.id, s.requirement.id, s.aboutExpiry from CrewStatement s " +
                    "where s.kindValue = :kind and s.aboutExpiry is not null",
                Array<Any>::class.java,
            )
            .setParameter("kind", CrewStatementKind.COURSE_BOOKED.wire)
            .resultList
            .map { Triple((it[0] as Number).toLong(), (it[1] as Number).toLong(), it[2] as LocalDate) }
            .toSet()

    fun forPersonUnscoped(personId: Long): List<CrewStatement> =
        find(
            "from CrewStatement s join fetch s.requirement where s.person.id = ?1 " +
                "order by s.createdAt desc",
            personId,
        ).list()
}
