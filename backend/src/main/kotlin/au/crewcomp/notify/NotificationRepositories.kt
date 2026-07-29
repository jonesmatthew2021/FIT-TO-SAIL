package au.crewcomp.notify

import au.crewcomp.platform.security.Role
import au.crewcomp.platform.security.ScopeGuard
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import java.time.Instant

/**
 * §9 notification reads.
 *
 * Notifications are addressed to a [au.crewcomp.people.UserAccount], never to a Person and never to
 * a role: §9 says "Notification rows in the database (**per recipient**)", and per-role *routing* is
 * implemented as fan-out at raise time rather than as a shared row. That choice is what keeps read
 * state per-user (§6 ADM-8: "per-user in production; the POC was global") without a second table —
 * `read_at` on the row is already per-recipient because the row is.
 *
 * The crew-facing reads resolve their scope through the account's person linkage. An account with no
 * person — a back-office user — is out of scope for every crew-scoped read here by construction.
 */
@ApplicationScoped
class NotificationRepository(private val scopeGuard: ScopeGuard) :
    PanacheRepositoryBase<Notification, Long> {

    fun forPersonScoped(personId: Long): List<Notification> {
        scopeGuard.assertVisible(personId)
        return find(
            "from Notification n join fetch n.recipient r where r.person.id = ?1 " +
                "order by n.createdAt desc",
            personId,
        ).list()
    }

    /**
     * Loads one notification for the given person. Returning null for "belongs to someone else"
     * rather than throwing is deliberate: absent and invisible must be indistinguishable, or the
     * status code enumerates other people's notification ids.
     */
    fun findForPerson(id: Long, personId: Long): Notification? = find(
        "from Notification n join fetch n.recipient r where n.id = ?1 and r.person.id = ?2",
        id,
        personId,
    ).firstResult()

    fun unreadCountForPerson(personId: Long): Long =
        count("recipient.person.id = ?1 and readAt is null", personId)

    // -----------------------------------------------------------------------
    // ADM-8 — the notifications centre
    // -----------------------------------------------------------------------

    /** One user's notifications, newest first. The ordinary ADM-8 read once an actor has an account. */
    fun forAccount(userAccountId: Long): List<Notification> = find(
        "$SELECT where n.recipient.id = ?1 order by n.createdAt desc",
        userAccountId,
    ).list()

    fun unreadCountForAccount(userAccountId: Long): Long =
        count("recipient.id = ?1 and readAt is null", userAccountId)

    fun findForAccount(id: Long, userAccountId: Long): Notification? =
        find("$SELECT where n.id = ?1 and n.recipient.id = ?2", id, userAccountId).firstResult()

    /**
     * Notifications addressed to any account holding one of [roles] — the read for a back-office
     * actor who does not yet have an account of their own.
     *
     * This branch is **unreachable in production**: `ActorResolver` refuses a request whose
     * corporate identity has no linked `UserAccount`, so a production actor always has one and takes
     * [forAccount] instead. It exists for the development shim, which authenticates a set of roles
     * rather than a person, and it disappears when the identity spike (ADR 0003) gives back-office
     * users accounts. `NotificationService.forActor` is the only caller and says the same thing.
     */
    fun forRoles(roles: Collection<Role>): List<Notification> =
        if (roles.isEmpty()) {
            emptyList()
        } else {
            find(
                // `distinct`: an account holding two of the named roles would otherwise match twice.
                "select distinct n from Notification n join fetch n.recipient r " +
                    "join r.roleAssignments ra where ra.role in ?1 order by n.createdAt desc",
                roles.map { it.wire },
            ).list()
        }

    /**
     * When each of [userAccountIds] was last sent a notification of [kind] — MOB-11's `nudgedAt`.
     *
     * Read from the notifications themselves rather than from a column on the person, because the
     * notification **is** the nudge: there is no second record of one, and a `last_nudged_at` that
     * could be set without a notification arriving is precisely the untraceable nudge this feature
     * refuses to allow.
     */
    fun latestByKind(userAccountIds: Collection<Long>, kind: NotificationKind): Map<Long, Instant> {
        if (userAccountIds.isEmpty()) return emptyMap()
        return getEntityManager()
            .createQuery(
                "select n.recipient.id, max(n.createdAt) from Notification n " +
                    "where n.recipient.id in :ids and n.kind = :kind group by n.recipient.id",
                Array<Any>::class.java,
            )
            .setParameter("ids", userAccountIds)
            .setParameter("kind", kind.wire)
            .resultList
            .associate { (it[0] as Number).toLong() to it[1] as Instant }
    }

    /** The idempotency lookup for a scheduled scan (V4) — see [Notification.dedupeKey]. */
    fun byDedupeKey(userAccountId: Long, dedupeKey: String): Notification? =
        find("recipient.id = ?1 and dedupeKey = ?2", userAccountId, dedupeKey).firstResult()

    private companion object {
        /**
         * The fetch set every ADM-8 read shares. The recipient is joined because the DTO names who a
         * notification went to — a fan-out means one fact becomes several rows, and a list that
         * cannot say which is which is confusing rather than informative.
         */
        const val SELECT = "select n from Notification n join fetch n.recipient r"
    }
}
