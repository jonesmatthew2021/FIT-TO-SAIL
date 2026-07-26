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
        "from EvidenceDocument d join fetch d.person where d.verificationStatusValue = ?1 " +
            "order by d.submittedAt",
        VerificationStatus.PENDING_REVIEW.wire,
    ).list()
}
