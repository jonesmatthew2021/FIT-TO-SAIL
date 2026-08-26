package au.crewcomp.it

import au.crewcomp.platform.dev.PortalSeedLoader
import au.crewcomp.platform.time.BusinessClock
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import java.nio.file.Path

/**
 * Runs [PortalSeedLoader] for [PortalSeedIT] — same shape as [ExtractFixtureRunner], and for the
 * same reason: the loader needs a transaction and a `@Transactional` helper on the test class
 * would be self-invoked. It is driven only against the fictional fixture in
 * `src/test/resources/portal-fixture`, never against a real snapshot.
 */
@ApplicationScoped
class PortalFixtureRunner(
    private val em: EntityManager,
    private val clock: BusinessClock,
) {
    @Transactional
    fun load(root: Path) {
        PortalSeedLoader(em, clock, root).load()
    }

    @Transactional
    fun <T> query(jpql: String, type: Class<T>): List<T> = em.createQuery(jpql, type).resultList

    fun count(jpql: String): Long = query(jpql, java.lang.Long::class.java).single().toLong()
}
