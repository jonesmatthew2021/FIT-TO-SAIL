package au.crewcomp.evidence

import au.crewcomp.people.PersonRepository
import au.crewcomp.platform.adapters.ObjectStorage
import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.reference.RequirementRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID

/**
 * §8 stage 1 (ingest) and MOB-4/MOB-5a (submission + resumable upload).
 *
 * This is the **only** client-originated write path a crew member has (§7.6), which is why it is
 * the one service that does not call `assertNotReadOnlyActor()`. Everything about it is shaped
 * by the fact that the client is on a vessel with intermittent connectivity:
 *
 *  * **Idempotent by client-generated id.** [submit] with a `publicId` the server already knows
 *    returns the existing document unchanged. A device that uploaded successfully and then lost
 *    the response replays safely.
 *  * **Resume from a server-held offset.** [appendChunk] accepts a chunk only at the current
 *    offset. Replaying an already-applied chunk is a no-op that reports the current offset, so a
 *    client that crashed mid-`PATCH` recovers by asking where it got to.
 *  * **The document is never trusted.** LLM-1/LLM-3: this stage stores bytes and records
 *    metadata. It does not extract, does not match, and above all does not touch a holding.
 *
 * Chunks are stored as separate objects and concatenated on completion — [ObjectStorage] offers
 * put/get and deliberately not append (ADR 0005).
 */
@ApplicationScoped
class EvidenceService(
    private val documents: EvidenceDocumentRepository,
    private val people: PersonRepository,
    private val requirements: RequirementRepository,
    private val storage: ObjectStorage,
    private val policy: AccessPolicy,
    private val audit: AuditWriter,
) {

    /**
     * Registers a submission and returns it, or returns the existing one for a known [publicId].
     *
     * @param personId whose evidence this is. A crew member may only submit for themselves; the
     *   scope check below is what enforces that, and it is the same check every other read and
     *   write in the system goes through.
     */
    @Transactional
    fun submit(
        publicId: UUID,
        personId: Long,
        source: EvidenceSource,
        contentType: String?,
        declaredSize: Long?,
        declaredSha256: String?,
        requirementHintId: Long? = null,
    ): EvidenceDocument {
        val existing = documents.byPublicId(publicId)
        if (existing != null) {
            // Idempotent replay. The scope check still runs: a guessed UUID must not become a
            // read of someone else's submission.
            policy.assertCanSeePerson(existing.person.requiredId, existing.person.partnership.requiredId)
            return existing
        }

        val person = people.findById(personId) ?: throw EntityNotFoundException("No person $personId")
        policy.assertCanSeePerson(personId, person.partnership.requiredId)

        require(declaredSize == null || declaredSize > 0) { "Declared size must be positive" }
        require(declaredSize == null || declaredSize <= MAX_SUBMISSION_BYTES) {
            "Declared size $declaredSize exceeds the $MAX_SUBMISSION_BYTES byte limit"
        }
        if (contentType != null) {
            require(contentType in ACCEPTED_CONTENT_TYPES) {
                "Unsupported content type '$contentType' — accepted: ${ACCEPTED_CONTENT_TYPES.joinToString()}"
            }
        }

        val actor = policy.actor()
        val document = EvidenceDocument().apply {
            this.publicId = publicId
            this.person = person
            this.source = source
            this.contentType = contentType
            this.declaredSize = declaredSize
            this.declaredSha256 = declaredSha256?.lowercase()
            this.requirementHint = requirementHintId?.let {
                requirements.findById(it) ?: throw EntityNotFoundException("No requirement $it")
            }
            this.submittedBy = actor.label
            this.verificationStatus = VerificationStatus.PENDING_EXTRACTION
            stampCreated(actor.label)
        }
        documents.persist(document)

        audit.record(
            entityType = "EvidenceDocument",
            event = "evidence.submitted",
            entityId = document.id,
            businessKey = publicId.toString(),
            after = mapOf(
                "personSam" to person.sam,
                "source" to source.wire,
                "contentType" to contentType,
                "declaredSize" to declaredSize,
                "requirementHintId" to requirementHintId,
            ),
        )
        return document
    }

    /** Where a resuming client should continue from. */
    @Transactional
    fun uploadState(publicId: UUID): UploadState {
        val document = documents.byPublicId(publicId)
            ?: throw EntityNotFoundException("No evidence document $publicId")
        policy.assertCanSeePerson(document.person.requiredId, document.person.partnership.requiredId)
        return UploadState(document.uploadOffset, document.uploadComplete, document.declaredSize)
    }

    /**
     * Appends one chunk at [offset].
     *
     * The offset must equal the bytes already received. An offset *behind* that is a retry of an
     * applied chunk and succeeds as a no-op; an offset *ahead* would leave a hole and is
     * refused, because a file with a hole in it is worse than a failed upload.
     */
    @Transactional
    fun appendChunk(publicId: UUID, offset: Long, bytes: ByteArray): UploadState {
        val document = documents.byPublicId(publicId)
            ?: throw EntityNotFoundException("No evidence document $publicId")
        policy.assertCanSeePerson(document.person.requiredId, document.person.partnership.requiredId)

        if (document.uploadComplete) {
            return UploadState(document.uploadOffset, true, document.declaredSize)
        }
        if (offset < document.uploadOffset) {
            // Already applied — a duplicate retry, not an error.
            return UploadState(document.uploadOffset, document.uploadComplete, document.declaredSize)
        }
        if (offset > document.uploadOffset) {
            throw ChunkOutOfOrderException(expected = document.uploadOffset, received = offset)
        }
        require(bytes.isNotEmpty()) { "An empty chunk carries no bytes to append" }

        val newOffset = document.uploadOffset + bytes.size
        val declared = document.declaredSize
        if (declared != null && newOffset > declared) {
            throw IllegalArgumentException(
                "Chunk would take the upload to $newOffset bytes, past the declared $declared",
            )
        }
        require(newOffset <= MAX_SUBMISSION_BYTES) {
            "Upload would exceed the $MAX_SUBMISSION_BYTES byte limit"
        }

        storage.put(
            key = partKey(document.publicId, document.uploadPartCount),
            bytes = bytes,
            contentType = "application/octet-stream",
        )
        document.uploadPartCount += 1
        document.uploadOffset = newOffset
        document.stampUpdated(policy.actor().label)

        if (declared != null && newOffset == declared) {
            finalise(document)
        }
        return UploadState(document.uploadOffset, document.uploadComplete, declared)
    }

    /**
     * Concatenates the stored parts into the immutable original and verifies the client's
     * digest. A mismatch fails the document rather than handing corrupt bytes to extraction.
     */
    private fun finalise(document: EvidenceDocument) {
        val assembled = ByteArray(document.uploadOffset.toInt())
        var cursor = 0
        for (part in 0 until document.uploadPartCount) {
            val chunk = storage.get(partKey(document.publicId, part))
                ?: throw IllegalStateException(
                    "Upload part $part of ${document.publicId} is missing from object storage",
                )
            chunk.copyInto(assembled, cursor)
            cursor += chunk.size
        }

        val digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(assembled))
        val declaredDigest = document.declaredSha256
        if (declaredDigest != null && declaredDigest != digest) {
            document.verificationStatus = VerificationStatus.REJECTED
            document.rejectionReason =
                "Uploaded bytes do not match the digest declared by the device"
            audit.record(
                entityType = "EvidenceDocument",
                event = "evidence.upload_digest_mismatch",
                entityId = document.id,
                businessKey = document.publicId.toString(),
                after = mapOf("declared" to declaredDigest, "computed" to digest),
            )
            return
        }

        val key = originalKey(document.publicId)
        storage.put(key, assembled, document.contentType ?: "application/octet-stream")
        document.objectKey = key
        document.byteSize = assembled.size.toLong()
        document.uploadComplete = true

        audit.record(
            entityType = "EvidenceDocument",
            event = "evidence.upload_completed",
            entityId = document.id,
            businessKey = document.publicId.toString(),
            after = mapOf("objectKey" to key, "byteSize" to assembled.size, "sha256" to digest),
        )
    }

    private fun partKey(publicId: UUID, part: Int): String =
        "evidence/$publicId/part-%06d".format(part)

    private fun originalKey(publicId: UUID): String = "evidence/$publicId/original"

    companion object {
        /**
         * §7.5 allows multi-page photographs and PDFs. The ceiling exists so a malformed or
         * hostile client cannot fill the object store; it is generous against the research's
         * 20 MB gauntlet target.
         */
        const val MAX_SUBMISSION_BYTES: Long = 64L * 1024 * 1024

        /**
         * SEC-7: evidence is stored and served with non-executable dispositions, and the accepted
         * set is an allow-list rather than a deny-list. Extraction (§8 stage 2) handles images
         * and PDFs and nothing else.
         */
        val ACCEPTED_CONTENT_TYPES: Set<String> = setOf(
            "application/pdf", "image/jpeg", "image/png", "image/heic", "image/heif",
        )
    }
}

data class UploadState(val offset: Long, val complete: Boolean, val declaredSize: Long?)

/**
 * A chunk arrived somewhere other than the resume point. Carries the expected offset so the
 * client can seek rather than restart — the whole point of MOB-5a.
 */
class ChunkOutOfOrderException(val expected: Long, val received: Long) :
    RuntimeException("Expected the next chunk at byte $expected but it began at $received")
