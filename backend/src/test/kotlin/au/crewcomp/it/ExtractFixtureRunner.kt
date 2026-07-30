package au.crewcomp.it

import au.crewcomp.platform.dev.ExtractedSeedLoader
import au.crewcomp.platform.time.BusinessClock
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import java.nio.file.Path

/**
 * Runs [ExtractedSeedLoader] for [ExtractedSeedIT]. A separate injected bean because the loader
 * needs a transaction and a `@Transactional` helper on the test class would be self-invoked
 * (see backend/CLAUDE.md, "Traps").
 *
 * The loader itself is a plain class — only [au.crewcomp.platform.dev.DevDataSeeder] is the
 * build-gated bean, and it is absent under `%test` — so the IT drives it against the fictional
 * fixture in `src/test/resources/extract-fixture`, never against real extracts.
 */
@ApplicationScoped
class ExtractFixtureRunner(
    private val em: EntityManager,
    private val clock: BusinessClock,
) {
    @Transactional
    fun load(root: Path) {
        ExtractedSeedLoader(em, clock, root).load()
    }

    @Transactional
    fun <T> query(jpql: String, type: Class<T>): List<T> = em.createQuery(jpql, type).resultList

    fun count(jpql: String): Long = query(jpql, java.lang.Long::class.java).single().toLong()
}
