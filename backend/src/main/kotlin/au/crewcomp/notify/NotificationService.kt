package au.crewcomp.notify

import au.crewcomp.people.UserAccountRepository
import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.Role
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.Instant

/**
 * §9 notification service.
 *
 * ### Two addressing shapes, one storage model
 *
 * §9 says notification rows are **per recipient**, and §9 also says back-office notifications are
 * routed per role. Those are not in tension: [raiseForRoles] resolves the role to the accounts
 * holding it and writes one row each. Routing happens once, at raise time; storage stays
 * per-recipient. The consequence worth knowing is that read state needs no second table — `read_at`
 * on the row is already per-user because the row is, which is what §6's "per-user in production (the
 * POC was global)" asks for.
 *
 * ### The trust model, at both ends
 *
 *  * [raise] and [raiseForRoles] are called by the system — the scheduled scans, domain write paths,
 *    pipeline outcomes — and never by a client.
 *  * [markRead] is one of the two writes a crew member's device may queue offline (§7.6). It is
 *    **monotonic**: a read-mark only ever moves from unset to set, and re-marking is a no-op. That
 *    is what lets the mobile outbound queue replay it freely with no conflict resolution.
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
     *
     * [dedupeKey], when given, makes the call idempotent for this recipient: a scan that runs daily
     * and recomputes what is due raises the same key every run and creates one row.
     */
    @Transactional
    fun raise(
        recipientUserAccountId: Long,
        kind: NotificationKind,
        title: String,
        body: String? = null,
        deepLink: String? = null,
        dedupeKey: String? = null,
    ): Notification = raiseTo(recipientUserAccountId, kind, title, body, deepLink, dedupeKey)

    /**
     * §9's back-office routing: one notification per account holding any of [roles].
     *
     * Returns the rows created, which may be **empty** — and that is not a failure. Back-office
     * users have no accounts until the identity spike creates them (ADR 0003), so today a register
     * transition raises nothing and the caller carries on. The alternative, throwing, would make
     * every write path in the system depend on an identity layer that does not exist yet.
     *
     * The fan-out is deliberately not deduplicated across roles: an actor holding both Coordinator
     * and Workflow Manager gets one row, because the query resolves accounts and an account appears
     * once.
     */
    @Transactional
    fun raiseForRoles(
        roles: Collection<Role>,
        kind: NotificationKind,
        title: String,
        body: String? = null,
        deepLink: String? = null,
        dedupeKey: String? = null,
    ): List<Notification> {
        val recipients = accounts.holdingAnyRole(roles)
        if (recipients.isEmpty()) return emptyList()
        return recipients.map { account ->
            raiseTo(account.requiredId, kind, title, body, deepLink, dedupeKey)
        }
    }

    /**
     * The single-recipient write, which both public raise methods delegate to.
     *
     * Private rather than a second `@Transactional` method, so neither caller self-invokes one: a
     * `@Transactional` method called from inside the same class bypasses the CDI interceptor. It
     * would work here — both callers already opened a transaction — which is exactly what makes the
     * pattern a trap worth not laying.
     */
    private fun raiseTo(
        recipientUserAccountId: Long,
        kind: NotificationKind,
        title: String,
        body: String?,
        deepLink: String?,
        dedupeKey: String?,
    ): Notification {
        val recipient = accounts.findById(recipientUserAccountId)
            ?: throw IllegalArgumentException("No user account $recipientUserAccountId")
        if (dedupeKey != null) {
            notifications.byDedupeKey(recipientUserAccountId, dedupeKey)?.let { return it }
        }

        val notification = Notification().apply {
            this.recipient = recipient
            this.kind = kind.wire
            this.title = title
            this.body = body
            this.deepLink = deepLink
            this.dedupeKey = dedupeKey
            stampCreated(policy.actor().label)
        }
        notifications.persist(notification)

        audit.record(
            entityType = "Notification",
            event = "notification.raised",
            entityId = notification.id,
            businessKey = kind.wire,
            after = mapOf(
                "recipientUserAccountId" to recipientUserAccountId,
                "title" to title,
                "dedupeKey" to dedupeKey,
            ),
        )
        return notification
    }

    // -----------------------------------------------------------------------
    // ADM-8 — reads and read-marks for the signed-in actor
    // -----------------------------------------------------------------------

    /**
     * The signed-in actor's notifications, newest first.
     *
     * Two paths, and the second one is a scaffold with a fixed removal date:
     *
     *  * An actor **with** an account reads the notifications addressed to it. That is the whole of
     *    the production path — `ActorResolver` refuses a corporate identity that has no linked
     *    account, so every production actor has one.
     *  * An actor **without** an account — which today means the development shim, since it
     *    authenticates a set of roles rather than a person — reads the notifications addressed to
     *    the accounts holding their roles. It is a role proxy, it is unreachable in production, and
     *    it exists so this module is finishable and demonstrable before the identity spike rather
     *    than after it.
     */
    @Transactional
    fun forActor(): List<Notification> = mine()

    @Transactional
    fun unreadCountForActor(): Long =
        myAccountId()
            ?.let { notifications.unreadCountForAccount(it) }
            ?: mine().count { !it.isRead }.toLong()

    /**
     * Marks one of the actor's own notifications read. Returns false when there is no such
     * notification *for this actor* — absent and invisible answer alike, so a caller cannot
     * enumerate ids belonging to someone else.
     *
     * Unaudited, for the same reason as [markRead].
     */
    @Transactional
    fun markReadForActor(notificationId: Long, at: Instant = Instant.now()): Boolean {
        val notification = mine().firstOrNull { it.id == notificationId } ?: return false
        if (notification.readAt == null) notification.readAt = at
        return true
    }

    /** Marks everything the actor can see as read — the "clear all" ADM-8 needs to stay usable. */
    @Transactional
    fun markAllReadForActor(at: Instant = Instant.now()): Int {
        val unread = mine().filter { !it.isRead }
        unread.forEach { it.readAt = at }
        return unread.size
    }

    /**
     * The actor's own account, or null when they have none.
     *
     * `userAccountId` is set by `ActorResolver` for every corporate identity; the `personId` fallback
     * covers a path that authenticated a crew member without resolving their account.
     */
    private fun myAccountId(): Long? {
        val actor = policy.actor()
        return actor.userAccountId ?: actor.personId?.let { accounts.forPerson(it)?.id }
    }

    /**
     * The rows the actor may read — the one place the two addressing paths are chosen between.
     *
     * The role-proxy fallback is gated on the actor being **back-office**, and that gate is
     * load-bearing rather than tidy. Without it, a crew member whose Person record has no account
     * yet would fall through to "notifications addressed to accounts holding my roles" — and their
     * role is Crew Member, so they would read every other crew member's notifications. An IT caught
     * exactly that.
     *
     * So: an actor who is a crew member, or who is tied to a Person, reads their own account's rows
     * or nothing at all. Only an actor with no person and no crew role — a back-office user under the
     * development shim, which `ActorResolver` makes impossible in production — gets the proxy.
     */
    private fun mine(): List<Notification> {
        myAccountId()?.let { return notifications.forAccount(it) }

        val actor = policy.actor()
        val isCrewShaped = actor.personId != null || actor.hasRole(Role.CREW_MEMBER)
        if (isCrewShaped) return emptyList()

        return notifications.forRoles(actor.roles - Role.CREW_MEMBER)
    }

    // -----------------------------------------------------------------------
    // MOB-3 — the crew member's own list
    // -----------------------------------------------------------------------

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
