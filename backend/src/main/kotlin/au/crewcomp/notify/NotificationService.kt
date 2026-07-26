package au.crewcomp.notify

import au.crewcomp.people.UserAccountRepository
import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.security.AccessPolicy
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.Instant

/**
 * §9 notification service.
 *
 * Only two operations exist so far and they sit at opposite ends of the trust model:
 *
 *  * [raise] is called by the system (the expiry scan, assignment changes, pipeline outcomes) —
 *    never by a client.
 *  * [markRead] is one of the two writes a crew member's device may queue offline (§7.6). It is
 *    **monotonic**: a read-mark only ever moves from unset to set, and re-marking an
 *    already-read notification is a no-op. That is what lets the mobile outbound queue replay it
 *    freely without conflict resolution.
 */
@ApplicationScoped
class NotificationService(
    private val notifications: NotificationRepository,
    private val accounts: UserAccountRepository,
    private val policy: AccessPolicy,
    private val audit: AuditWriter,
) {

    /**
     * Records an in-app notification — the source of truth; push and email are channels for it.
     *
     * SEC-13: [title] must stay free of sensitive detail, because it is the only field a push
     * payload carries. The detail belongs in [body], which is fetched in-app.
     */
    @Transactional
    fun raise(
        recipientUserAccountId: Long,
        kind: NotificationKind,
        title: String,
        body: String? = null,
        deepLink: String? = null,
    ): Notification {
        val recipient = accounts.findById(recipientUserAccountId)
            ?: throw IllegalArgumentException("No user account $recipientUserAccountId")

        val notification = Notification().apply {
            this.recipient = recipient
            this.kind = kind.wire
            this.title = title
            this.body = body
            this.deepLink = deepLink
            stampCreated(policy.actor().label)
        }
        notifications.persist(notification)

        audit.record(
            entityType = "Notification",
            event = "notification.raised",
            entityId = notification.id,
            businessKey = kind.wire,
            after = mapOf("recipientUserAccountId" to recipientUserAccountId, "title" to title),
        )
        return notification
    }

    /**
     * Marks one of [personId]'s notifications read. Returns false when there is no such
     * notification *for that person* — absent and invisible answer alike, so a caller cannot
     * enumerate ids belonging to someone else.
     *
     * Deliberately unaudited. A read-mark is not a business mutation: it changes no compliance
     * answer, and a device syncing after a week offline would otherwise write a burst of audit
     * events that bury the changes AUTH-3 exists to make findable.
     */
    @Transactional
    fun markRead(notificationId: Long, personId: Long, at: Instant = Instant.now()): Boolean {
        val notification = notifications.findForPerson(notificationId, personId) ?: return false
        if (notification.readAt == null) {
            notification.readAt = at
        }
        return true
    }
}
