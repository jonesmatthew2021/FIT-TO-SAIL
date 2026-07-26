package au.crewcomp.evidence

import au.crewcomp.platform.security.ScopeGuard
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class EvidenceDocumentRepository(private val scopeGuard: ScopeGuard) :
    PanacheRepositoryBase<EvidenceDocument, Long> {

    fun forPersonScoped(personId: Long): List<EvidenceDocument> {
        scopeGuard.assertVisible(personId)
        return find(
            "from EvidenceDocument d join fetch d.person left join fetch d.requirementHint " +
                "where d.person.id = ?1 order by d.submittedAt desc",
            personId,
        ).list()
    }

    /**
     * The idempotency lookup for MOB-4: a device replaying a submission after an offline gap
     * presents the same client-generated [publicId], and must get the existing document back
     * rather than create a second one.
     */
    fun byPublicId(publicId: UUID): EvidenceDocument? =
        find("from EvidenceDocument d join fetch d.person where d.publicId = ?1", publicId).firstResult()

    /** The §8 review queue (ADM-9). Back-office only; not reachable from a crew-scoped path. */
    fun awaitingReviewUnscoped(): List<EvidenceDocument> = find(
        "$QUEUE_SELECT where d.verificationStatusValue = ?1 order by d.submittedAt",
        VerificationStatus.PENDING_REVIEW.wire,
    ).list()

    /**
     * ADM-9's queue, filtered by status.
     *
     * `auto_accepted` belongs in this list even though nothing is asked of the reviewer: §8 stage 4
     * requires auto-acceptances to surface "for retrospective spot-checking", and a decision nobody
     * ever sees is a decision nobody can audit.
     */
    fun queueUnscoped(statuses: Collection<String>): List<EvidenceDocument> =
        if (statuses.isEmpty()) {
            emptyList()
        } else {
            find("$QUEUE_SELECT where d.verificationStatusValue in ?1 order by d.submittedAt", statuses).list()
        }

    /**
     * Documents whose bytes have arrived and which have not been extracted yet — the §8 stage 2
     * work list.
     *
     * `uploadComplete` is the load-bearing predicate: a submission is recorded before its bytes
     * finish uploading (MOB-5a), so extracting on submission alone would run the model over a
     * partial file. Unscoped, and only reachable from the scheduled sweep, which runs as a system
     * actor.
     */
    fun awaitingExtractionUnscoped(limit: Int): List<EvidenceDocument> = find(
        "from EvidenceDocument d where d.verificationStatusValue = ?1 and d.uploadComplete = true " +
            "order by d.submittedAt",
        VerificationStatus.PENDING_EXTRACTION.wire,
    ).page(0, limit).list()

    /**
     * One document with everything ADM-9's review panel renders.
     *
     * The fetch set has to cover the **whole** DTO: mapping happens after the service transaction
     * closes, so a lazy association reached there throws `LazyInitializationException` — in
     * production, on a screen, and never in a test with no session. This is the fetch set for both
     * the queue list and the detail, for the same reason.
     */
    fun byPublicIdForReview(publicId: UUID): EvidenceDocument? =
        find("$QUEUE_SELECT where d.publicId = ?1", publicId).firstResult()

    private companion object {
        /**
         * Shared so the list query and the detail query cannot drift apart — the register module
         * paid for exactly that, with a detail endpoint that worked and a list that 500'd on the
         * same mapping function.
         */
        const val QUEUE_SELECT =
            "select d from EvidenceDocument d " +
                "join fetch d.person p " +
                "join fetch p.partnership " +
                "left join fetch d.requirementHint " +
                "left join fetch d.matchedRequirement " +
                "left join fetch d.linkedHolding"
    }
}
