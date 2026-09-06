package au.crewcomp.evidence

import au.crewcomp.engine.HoldingStatus
import au.crewcomp.notify.NotificationKind
import au.crewcomp.notify.NotificationService
import au.crewcomp.people.HoldingService
import au.crewcomp.people.UserAccountRepository
import au.crewcomp.platform.adapters.ObjectStorage
import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.Role
import au.crewcomp.reference.RequirementRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.LocalDate
import java.util.UUID

/**
 * §8 stage 5 and ADM-9 — the human verification queue.
 *
 * This is where AIA-2's "AI proposes, an authorised human disposes" is actually implemented. The
 * pipeline has read the document and, at most, written its reading into `evidence_document`; a Data
 * Steward decides what the record says. Three shapes of decision, and the distinction between the
 * first two is the whole reason the screen exists:
 *
 *  * **Accept** — the extracted values are right, so write them.
 *  * **Correct** — the extracted values are wrong in some part, so write different ones. Not a
 *    separate operation: [accept] takes the values to write, and the audit event carries both what
 *    was extracted and what was accepted, so a correction is visible as a correction. Measuring the
 *    gap between the two is what LLM-2 needs before auto-acceptance can be turned on.
 *  * **Reject** — the document does not evidence what it claims, or is illegible. The submitter is
 *    told, because a crew member who photographed a certificate on a vessel and heard nothing back
 *    will simply photograph it again.
 *
 * A holding is only ever written through [HoldingService], the same door the admin API and the
 * pipeline use (LLM-1, AUTH-1).
 */
@ApplicationScoped
class EvidenceReviewService(
    private val documents: EvidenceDocumentRepository,
    private val requirements: RequirementRepository,
    private val holdingService: HoldingService,
    private val accounts: UserAccountRepository,
    private val notifications: NotificationService,
    private val storage: ObjectStorage,
    private val policy: AccessPolicy,
    private val audit: AuditWriter,
) {

    /**
     * The queue, filtered by status. Defaults to everything a reviewer has to act on *plus* the
     * auto-accepted spot-check list (§8 stage 4).
     *
     * Readable by the roles that work it and by those who need to see the backlog exists;
     * **deciding** is narrower. A crew member never reads this — their own documents come back
     * through the sync path, scoped to them.
     */
    @Transactional
    fun queue(statuses: Collection<VerificationStatus> = DEFAULT_QUEUE_STATUSES): List<EvidenceDocument> {
        policy.require(
            Role.DATA_STEWARD, Role.CREW_COORDINATOR, Role.COMPLIANCE_LEAD,
            Role.WORKFLOW_MANAGER, Role.SYSTEM_ADMINISTRATOR,
        )
        return documents.queueUnscoped(statuses.map { it.wire })
    }

    @Transactional
    fun detail(publicId: UUID): EvidenceDocument = authorisedRead(publicId)

    /**
     * The document's bytes, for the side-by-side view ADM-9 is specified as (§6).
     *
     * Streamed through the application rather than served from a signed URL, which is **not** where
     * SEC-7 wants to end up: the requirement is signed URLs with non-executable dispositions,
     * served by the platform. It cannot be met yet — `LocalObjectStorage.signedReadUrl` returns a
     * `file:` URI, which a browser will not fetch — so this is the interim, and the ceiling on it is
     * the ingest allow-list: only PDFs and the four image types `EvidenceService.ACCEPTED_CONTENT_TYPES`
     * permits were ever stored, and the resource re-asserts the stored content type rather than
     * trusting a client's `Accept`. Replaced by a signed URL in the platform spike.
     */
    @Transactional
    fun content(publicId: UUID): EvidenceContent {
        // `authorisedRead`, not `detail`: calling a sibling `@Transactional` method would be a
        // self-invocation that bypasses the interceptor. It happens to work here because this
        // method already opened the transaction, which is exactly what makes the pattern a trap.
        val document = authorisedRead(publicId)
        val key = document.objectKey
            ?: throw EntityNotFoundException("Evidence document $publicId has no stored object yet")
        val bytes = storage.get(key)
            ?: throw EntityNotFoundException("Evidence object for $publicId is missing from storage")
        return EvidenceContent(
            bytes = bytes,
            contentType = document.contentType ?: "application/octet-stream",
            filename = "evidence-$publicId",
        )
    }

    /**
     * Accepts the document and writes the holding it evidences.
     *
     * [requirementId], [status], [expiry] and [issueDate] are what the **reviewer** decided, not
     * what was extracted. That is the point: passing the values in is what makes "accept" and
     * "correct" one operation with one audit trail, and it is why the reviewer can accept a document
     * whose extraction produced nothing at all — which is every document until §14.5 picks a
     * provider.
     */
    @Transactional
    fun accept(
        publicId: UUID,
        requirementId: Long,
        status: HoldingStatus,
        expiry: LocalDate? = null,
        issueDate: LocalDate? = null,
        note: String? = null,
    ): EvidenceDocument {
        policy.require(Role.DATA_STEWARD, Role.SYSTEM_ADMINISTRATOR)

        val document = openDocument(publicId)
        val requirement = requirements.findById(requirementId)
            ?: throw EntityNotFoundException("No requirement $requirementId")

        val extracted = extractedSummary(document)
        val holding = holdingService.setHolding(
            personId = document.person.requiredId,
            requirementId = requirementId,
            status = status,
            expiry = expiry,
            issueDate = issueDate,
            note = note ?: "Verified from evidence $publicId (§8 stage 5)",
            evidenceDocumentId = document.id,
        )

        val previousStatus = document.verificationStatus
        document.verificationStatus = VerificationStatus.VERIFIED
        document.matchedRequirement = requirement
        document.linkedHolding = holding
        document.rejectionReason = null
        document.reviewReason = null
        document.stampUpdated(policy.actor().label)

        val accepted = mapOf(
            "requirement" to requirement.code,
            "status" to status.wire,
            "expiry" to expiry?.toString(),
            "issueDate" to issueDate?.toString(),
        )
        audit.record(
            entityType = "EvidenceDocument",
            // A correction gets its own event name: "the human changed what the model read" is the
            // single most useful thing in this trail, and it must not be buried inside a generic
            // acceptance nobody would think to search for.
            event = if (isCorrection(extracted, accepted)) "evidence.corrected" else "evidence.verified",
            entityId = document.id,
            businessKey = publicId.toString(),
            before = mapOf("status" to previousStatus.wire, "extracted" to extracted),
            after = mapOf("status" to VerificationStatus.VERIFIED.wire, "accepted" to accepted),
        )

        notifySubmitter(
            document = document,
            kind = NotificationKind.EVIDENCE_VERIFIED,
            title = "A document you submitted has been accepted",
            body = "Your ${requirement.code} record has been updated from the document you submitted.",
        )
        return document
    }

    /**
     * Rejects the document with a reason, and tells the submitter.
     *
     * The reason is mandatory and is shown to the crew member. "Rejected" with no explanation makes
     * the mobile app's queue a dead end — the person cannot tell whether to re-photograph the same
     * certificate or find a different one.
     */
    @Transactional
    fun reject(publicId: UUID, reason: String): EvidenceDocument {
        policy.require(Role.DATA_STEWARD, Role.SYSTEM_ADMINISTRATOR)

        val cleanReason = reason.trim()
        require(cleanReason.isNotEmpty()) {
            "A rejection needs a reason — it is shown to the person who submitted the document"
        }

        val document = openDocument(publicId)
        val previousStatus = document.verificationStatus
        document.verificationStatus = VerificationStatus.REJECTED
        document.rejectionReason = cleanReason
        document.reviewReason = null
        document.stampUpdated(policy.actor().label)

        audit.record(
            entityType = "EvidenceDocument",
            event = "evidence.rejected",
            entityId = document.id,
            businessKey = publicId.toString(),
            before = mapOf("status" to previousStatus.wire, "extracted" to extractedSummary(document)),
            after = mapOf("status" to VerificationStatus.REJECTED.wire, "reason" to cleanReason),
        )

        notifySubmitter(
            document = document,
            kind = NotificationKind.EVIDENCE_REJECTED,
            title = "A document you submitted needs attention",
            body = cleanReason,
        )
        return document
    }

    /** One person's documents, newest first — the certificates-on-file card. */
    @Transactional
    fun forPerson(personId: Long): List<EvidenceDocument> {
        policy.require(
            Role.DATA_STEWARD, Role.CREW_COORDINATOR, Role.COMPLIANCE_LEAD,
            Role.WORKFLOW_MANAGER, Role.SYSTEM_ADMINISTRATOR,
        )
        return documents.forPersonForReview(personId)
    }

    /**
     * Corrects a **filed** document — the certificates-on-file "Edit".
     *
     * [accept] decides an open document and [openDocument] refuses a decided one, on purpose: a
     * decision's history is not rewritten. This is the other operation: the document stays decided,
     * the holding it evidences is written again through [HoldingService] with the corrected values,
     * the document is re-linked, and the event says "amended" so an auditor can find every
     * after-the-fact correction in one search.
     */
    @Transactional
    fun amend(
        publicId: UUID,
        requirementId: Long,
        status: HoldingStatus,
        expiry: LocalDate? = null,
        issueDate: LocalDate? = null,
        note: String? = null,
    ): EvidenceDocument {
        policy.require(Role.DATA_STEWARD, Role.SYSTEM_ADMINISTRATOR)

        val document = documents.byPublicIdForReview(publicId)
            ?: throw EntityNotFoundException("No evidence document $publicId")
        require(document.verificationStatus in FILED_STATUSES) {
            "Only a filed (verified or auto-accepted) document can be amended; this one is " +
                "${document.verificationStatus.wire} — decide it on the evidence queue instead"
        }
        val requirement = requirements.findById(requirementId)
            ?: throw EntityNotFoundException("No requirement $requirementId")

        val before = mapOf(
            "requirement" to document.matchedRequirement?.code,
            "holdingId" to document.linkedHolding?.requiredId,
        )
        val holding = holdingService.setHolding(
            personId = document.person.requiredId,
            requirementId = requirementId,
            status = status,
            expiry = expiry,
            issueDate = issueDate,
            note = note ?: "Amended from evidence $publicId",
            evidenceDocumentId = document.id,
        )
        document.matchedRequirement = requirement
        document.linkedHolding = holding
        document.stampUpdated(policy.actor().label)

        audit.record(
            entityType = "EvidenceDocument",
            event = "evidence.amended",
            entityId = document.id,
            businessKey = publicId.toString(),
            before = before,
            after = mapOf(
                "requirement" to requirement.code,
                "status" to status.wire,
                "expiry" to expiry?.toString(),
                "issueDate" to issueDate?.toString(),
                "holdingId" to holding.requiredId,
            ),
        )
        return document
    }

    /**
     * Takes a document off the file — the certificates-on-file "Delete", which is a withdrawal and
     * not a deletion: the bytes stay, the audit trail stays, and **the holding is untouched**. A
     * document that was wrong about a date is corrected with [amend]; a holding that should not
     * exist is corrected on the person's holdings. Removing a scan changes what is on file, not
     * what is true.
     */
    @Transactional
    fun remove(publicId: UUID, reason: String): EvidenceDocument {
        policy.require(Role.DATA_STEWARD, Role.SYSTEM_ADMINISTRATOR)
        val cleanReason = reason.trim()
        require(cleanReason.isNotEmpty()) { "Removing a document needs a reason — it is what the file will say in its place" }

        val document = documents.byPublicIdForReview(publicId)
            ?: throw EntityNotFoundException("No evidence document $publicId")
        val previousStatus = document.verificationStatus
        document.verificationStatus = VerificationStatus.REJECTED
        document.rejectionReason = "Removed from file: $cleanReason"
        document.reviewReason = null
        document.stampUpdated(policy.actor().label)

        audit.record(
            entityType = "EvidenceDocument",
            event = "evidence.removed",
            entityId = document.id,
            businessKey = publicId.toString(),
            before = mapOf("status" to previousStatus.wire, "fileName" to document.fileName),
            after = mapOf("status" to VerificationStatus.REJECTED.wire, "reason" to cleanReason),
        )
        return document
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private fun authorisedRead(publicId: UUID): EvidenceDocument {
        policy.require(
            Role.DATA_STEWARD, Role.CREW_COORDINATOR, Role.COMPLIANCE_LEAD,
            Role.WORKFLOW_MANAGER, Role.SYSTEM_ADMINISTRATOR,
        )
        return documents.byPublicIdForReview(publicId)
            ?: throw EntityNotFoundException("No evidence document $publicId")
    }

    /**
     * Loads a document that can still be decided.
     *
     * `verified` and `rejected` are terminal. A wrongly-accepted holding is corrected on the
     * person's holdings (ADM-5), where the change is audited as what it is — a holding edit — rather
     * than by reopening a document and rewriting the history of a decision that was made.
     * `auto_accepted` **is** decidable, and deliberately: it is what the spot-check list is for.
     */
    private fun openDocument(publicId: UUID): EvidenceDocument {
        val document = documents.byPublicIdForReview(publicId)
            ?: throw EntityNotFoundException("No evidence document $publicId")
        if (document.verificationStatus in TERMINAL_STATUSES) {
            throw EvidenceAlreadyDecidedException(publicId, document.verificationStatus)
        }
        return document
    }

    /** What the model read, flattened to `field → value` for the audit event's `before`. */
    private fun extractedSummary(document: EvidenceDocument): Map<String, Any?> = mapOf(
        "requirement" to document.matchedRequirement?.code,
        "expiry" to ExtractionSchema.fieldOf(document.extraction, ExtractionSchema.EXPIRY_DATE).value,
        "issueDate" to ExtractionSchema.fieldOf(document.extraction, ExtractionSchema.ISSUE_DATE).value,
        "model" to document.extractionModel,
    )

    /**
     * Whether the reviewer changed anything the model read.
     *
     * Compares only the fields both sides carry, and treats "the model read nothing" as *not* a
     * correction: with no provider configured every acceptance would otherwise be logged as a
     * correction, which would make the one genuinely interesting event name useless.
     */
    private fun isCorrection(extracted: Map<String, Any?>, accepted: Map<String, Any?>): Boolean {
        val comparable = listOf("requirement", "expiry", "issueDate")
        if (comparable.all { extracted[it] == null }) return false
        return comparable.any { field ->
            val was = extracted[field]
            was != null && was != accepted[field]
        }
    }

    private fun notifySubmitter(
        document: EvidenceDocument,
        kind: NotificationKind,
        title: String,
        body: String,
    ) {
        // No account means nobody to notify — see the same case in `ExtractionStages.notify`.
        val account = accounts.forPerson(document.person.requiredId) ?: return
        notifications.raise(
            recipientUserAccountId = account.requiredId,
            kind = kind,
            title = title,
            body = body,
            deepLink = "crewcomp://certifications",
        )
    }

    companion object {
        /** What a reviewer sees by default: the work, plus the auto-accepted spot-check list. */
        val DEFAULT_QUEUE_STATUSES: List<VerificationStatus> = listOf(
            VerificationStatus.PENDING_REVIEW,
            VerificationStatus.PENDING_EXTRACTION,
            VerificationStatus.AUTO_ACCEPTED,
        )

        private val TERMINAL_STATUSES = setOf(VerificationStatus.VERIFIED, VerificationStatus.REJECTED)

        /** A document that has written a holding — the only kind [amend] applies to. */
        private val FILED_STATUSES = setOf(VerificationStatus.VERIFIED, VerificationStatus.AUTO_ACCEPTED)
    }
}

data class EvidenceContent(val bytes: ByteArray, val contentType: String, val filename: String) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is EvidenceContent && contentType == other.contentType &&
                    filename == other.filename && bytes.contentEquals(other.bytes)
                )

    override fun hashCode(): Int = 31 * (31 * contentType.hashCode() + filename.hashCode()) + bytes.contentHashCode()
}

/**
 * A decision was attempted on a document that already has one.
 *
 * 409, like the matrix's immutability conflict: the request is well formed and the caller is
 * permitted, but the document has moved on. Two reviewers opening the same queue is the ordinary way
 * this happens, and the useful response is "someone already did this", not a validation error.
 */
class EvidenceAlreadyDecidedException(val publicId: UUID, val status: VerificationStatus) :
    RuntimeException(
        "Evidence document $publicId is already ${status.wire}. A holding recorded in error is " +
            "corrected on the person's holdings, where the correction is audited as one — not by " +
            "rewriting the decision.",
    )
