package au.crewcomp.notify

import au.crewcomp.platform.security.ScopeGuard
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped

/**
 * §9 notification reads.
 *
 * Notifications are addressed to a [au.crewcomp.people.UserAccount], not to a Person, so the
 * scope check resolves through the account's person linkage. An account with no person — a
 * back-office user — is out of scope for every crew-scoped read here by construction.
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
}
