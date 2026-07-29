package au.crewcomp.notify

import au.crewcomp.people.UserAccount
import au.crewcomp.platform.persistence.CreatedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

/**
 * §9 / MOB-3 notifications.
 *
 * The in-app record is the **source of truth**; push and email are delivery channels for it, not
 * separate messages. That is why this is a [CreatedEntity] with a single mutable field: a
 * notification is never edited, only read.
 *
 * SEC-13: [title] and [deepLink] are the only fields a push payload may carry. [body] holds the
 * detail and is fetched in-app, so a lock-screen preview cannot leak that a named crew member's
 * medical is about to expire.
 */
@Entity
@Table(name = "notification")
class Notification : CreatedEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_user_id", nullable = false)
    lateinit var recipient: UserAccount

    @Column(name = "kind", nullable = false)
    lateinit var kind: String

    /** Safe for a push payload (SEC-13). */
    @Column(name = "title", nullable = false)
    lateinit var title: String

    /** Not safe for a push payload — fetched in-app. */
    @Column(name = "body")
    var body: String? = null

    @Column(name = "deep_link")
    var deepLink: String? = null

    /**
     * Stable identity for a notification a scheduled scan raises, unique per recipient (V4).
     *
     * Null for a domain event: raising a notification because something just happened needs no
     * key, because it happens once. A daily scan recomputes what is due on every run, so without
     * this the same warning would arrive every morning until the certificate expired.
     */
    @Column(name = "dedupe_key")
    var dedupeKey: String? = null

    /**
     * The one mutable field, and monotonic by construction: a read-mark only ever moves from
     * null to a timestamp. §7.6 leans on that — it is what makes the mobile outbound queue's
     * read-marks safely replayable without conflict resolution.
     */
    @Column(name = "read_at")
    var readAt: Instant? = null

    /** §10.3 sync cursor, trigger-assigned — see [au.crewcomp.people.Person.updatedSeq]. */
    @Column(name = "updated_seq", insertable = false, updatable = false)
    var updatedSeq: Long = 0

    val isRead: Boolean get() = readAt != null
}

/**
 * The §9 notification kinds.
 *
 * The wire values are what the mobile app switches on to choose an icon and a deep-link target
 * (MOB-3) and what ADM-8 groups by, so they are a compatibility surface.
 *
 * [audience] is not access control — the recipient on the row is what decides who sees a
 * notification. It is routing metadata: it says which kinds a scan is entitled to address to a
 * crew member and which belong to the back office, and it lets ADM-8 and the mobile list filter
 * without either of them hard-coding a list of names.
 */
enum class NotificationKind(val wire: String, val audience: NotificationAudience) {
    // Crew-facing (MOB-3).
    EXPIRY_WARNING("expiry_warning", NotificationAudience.CREW),
    ASSIGNMENT_ADDED("assignment_added", NotificationAudience.CREW),
    ASSIGNMENT_REMOVED("assignment_removed", NotificationAudience.CREW),
    ASSIGNMENT_CHANGED("assignment_changed", NotificationAudience.CREW),
    REQUIREMENT_ADDED("requirement_added", NotificationAudience.CREW),
    EVIDENCE_RECEIVED("evidence_received", NotificationAudience.CREW),
    EVIDENCE_VERIFIED("evidence_verified", NotificationAudience.CREW),
    EVIDENCE_REJECTED("evidence_rejected", NotificationAudience.CREW),

    // ADM-11 answering back. A crew member who taps "Course booked" and hears nothing has been
    // given a button that talks to a void; these are the office's reply, and the second one matters
    // most — a dismissal changes what the person has to do next.
    CREW_REQUEST_ACTIONED("crew_request_actioned", NotificationAudience.CREW),
    CREW_REQUEST_DISMISSED("crew_request_dismissed", NotificationAudience.CREW),

    // Back-office, per §9's routing: register events → Workflow Manager; quota shortfalls and
    // roster gaps → Coordinators; new exceptions → Data Steward.
    REGISTER_EVENT("register_event", NotificationAudience.BACK_OFFICE),
    CUTOFF_APPROACHING("cutoff_approaching", NotificationAudience.BACK_OFFICE),
    QUOTA_SHORTFALL("quota_shortfall", NotificationAudience.BACK_OFFICE),
    ROSTER_GAP("roster_gap", NotificationAudience.BACK_OFFICE),
    EXPIRY_AFFECTS_ROSTER("expiry_affects_roster", NotificationAudience.BACK_OFFICE),
    MATRIX_PUBLISHED("matrix_published", NotificationAudience.BACK_OFFICE),
    EXCEPTION_RAISED("exception_raised", NotificationAudience.BACK_OFFICE),
    EVIDENCE_AWAITING_REVIEW("evidence_awaiting_review", NotificationAudience.BACK_OFFICE),

    // The crew app's one-tap answers (MOB-5), routed to Coordinators. Back-office rather than
    // crew: the person who tapped already knows what they said, and their own copy of it is the
    // queue entry on their device.
    CREW_PROGRESS_REPORTED("crew_progress_reported", NotificationAudience.BACK_OFFICE),
    CREW_HELP_REQUESTED("crew_help_requested", NotificationAudience.BACK_OFFICE),

    /** MOB-8: a seat request or a waitlist entry. One kind, because one coordinator works both. */
    CREW_COURSE_REQUESTED("crew_course_requested", NotificationAudience.BACK_OFFICE),

    /**
     * MOB-11: a supervisor has nudged a member of their watch.
     *
     * Crew-facing, and that is the whole point of it existing. The nudge itself is one row in
     * ADM-11's world — a supervisor saying "please deal with this" — and a nudge the recipient
     * cannot see, or cannot see the origin of, is a way to harass someone quietly. So the person
     * nudged gets a notification naming who sent it, and there is no configuration that turns
     * that off.
     */
    CREW_NUDGED("crew_nudged", NotificationAudience.CREW);

    companion object {
        fun fromWire(wire: String): NotificationKind =
            entries.firstOrNull { it.wire == wire }
                ?: throw IllegalArgumentException("Unknown notification kind: $wire")

        /** Tolerant lookup for a row written by a newer revision — see `NotificationDto`. */
        fun fromWireOrNull(wire: String): NotificationKind? = entries.firstOrNull { it.wire == wire }
    }
}

enum class NotificationAudience { CREW, BACK_OFFICE }
