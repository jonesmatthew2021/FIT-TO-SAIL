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
 * The §9 notification kinds a crew member can receive (MOB-3). The wire values are what the
 * mobile app switches on to choose an icon and a deep-link target, so they are a compatibility
 * surface.
 */
enum class NotificationKind(val wire: String) {
    EXPIRY_WARNING("expiry_warning"),
    ASSIGNMENT_ADDED("assignment_added"),
    ASSIGNMENT_REMOVED("assignment_removed"),
    ASSIGNMENT_CHANGED("assignment_changed"),
    REQUIREMENT_ADDED("requirement_added"),
    EVIDENCE_RECEIVED("evidence_received"),
    EVIDENCE_VERIFIED("evidence_verified"),
    EVIDENCE_REJECTED("evidence_rejected");

    companion object {
        fun fromWire(wire: String): NotificationKind =
            entries.firstOrNull { it.wire == wire }
                ?: throw IllegalArgumentException("Unknown notification kind: $wire")
    }
}
