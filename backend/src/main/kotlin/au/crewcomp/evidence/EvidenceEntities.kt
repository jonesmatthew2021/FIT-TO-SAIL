package au.crewcomp.evidence

import au.crewcomp.people.Person
import au.crewcomp.people.QualificationHolding
import au.crewcomp.platform.persistence.AuditedEntity
import au.crewcomp.reference.Requirement
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * §8 / MOB-4 evidence submission.
 *
 * Two things about this entity carry the offline contract:
 *
 *  * [publicId] is generated **on the device**, before the upload is attempted, and is the
 *    idempotency key for the whole submission (§7.6). A crew member who captures a document in a
 *    dead spot, force-quits, and reopens the app a day later replays the same id; the server
 *    recognises it and does not create a second document.
 *  * [uploadOffset] is the number of contiguous bytes received. It is the resume point for
 *    MOB-5a, and it is server-held rather than client-held because the client is the party that
 *    just crashed.
 *
 * LLM-1/LLM-3: nothing here mutates a holding. [linkedHolding] is set by the pipeline only after
 * a human verifies (or the auto-accept threshold clears) the extraction, and [extractionRaw] is
 * retained verbatim as data — never replayed as instructions.
 */
@Entity
@Table(name = "evidence_document")
class EvidenceDocument : AuditedEntity() {

    /** Client-generated, so mobile submissions are idempotent across offline retries (§7.6). */
    @Column(name = "public_id", nullable = false, unique = true)
    lateinit var publicId: UUID

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    lateinit var person: Person

    /** Opaque key into the object-storage adapter; no cloud types in the schema (ADR 0005). */
    @Column(name = "object_key")
    var objectKey: String? = null

    @Column(name = "content_type")
    var contentType: String? = null

    @Column(name = "byte_size")
    var byteSize: Long? = null

    @Column(name = "source", nullable = false)
    var sourceValue: String = EvidenceSource.MOBILE_CAMERA.wire

    @Column(name = "submitted_by", nullable = false)
    lateinit var submittedBy: String

    @Column(name = "submitted_at", nullable = false)
    var submittedAt: Instant = Instant.now()

    @Column(name = "verification_status", nullable = false)
    var verificationStatusValue: String = VerificationStatus.PENDING_EXTRACTION.wire

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_holding_id")
    var linkedHolding: QualificationHolding? = null

    /**
     * The crew member's tag for what this evidences (§7.5). A **hint** that seeds §8 stage 3
     * matching — extraction may correct it — which is why it is separate from [linkedHolding].
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requirement_hint_id")
    var requirementHint: Requirement? = null

    @Column(name = "extraction_model")
    var extractionModel: String? = null

    /** Verbatim model response, retained for audit (§8 stage 2). Data, never instructions. */
    @Column(name = "extraction_raw")
    var extractionRaw: String? = null

    @Column(name = "rejection_reason")
    var rejectionReason: String? = null

    // --- MOB-5a resumable upload state -------------------------------------

    /** Contiguous bytes received from zero. The resume point handed back to the device. */
    @Column(name = "upload_offset", nullable = false)
    var uploadOffset: Long = 0

    @Column(name = "upload_complete", nullable = false)
    var uploadComplete: Boolean = false

    /** Parts stored so far; names the next part object without needing a listing capability. */
    @Column(name = "upload_part_count", nullable = false)
    var uploadPartCount: Int = 0

    /** Client-declared total, so the server can recognise completion and reject an overrun. */
    @Column(name = "declared_size")
    var declaredSize: Long? = null

    /** Client-declared digest, verified on completion. A mismatch fails the document. */
    @Column(name = "declared_sha256")
    var declaredSha256: String? = null

    /** §10.3 sync cursor, trigger-assigned — see [au.crewcomp.people.Person.updatedSeq]. */
    @Column(name = "updated_seq", insertable = false, updatable = false)
    var updatedSeq: Long = 0

    var source: EvidenceSource
        get() = EvidenceSource.fromWire(sourceValue)
        set(value) {
            sourceValue = value.wire
        }

    var verificationStatus: VerificationStatus
        get() = VerificationStatus.fromWire(verificationStatusValue)
        set(value) {
            verificationStatusValue = value.wire
        }
}

/** Appendix A: `evidence_document.source`. */
enum class EvidenceSource(val wire: String) {
    MOBILE_CAMERA("mobile_camera"),
    MOBILE_FILE("mobile_file"),
    ADMIN_UPLOAD("admin_upload");

    val isMobile: Boolean get() = this != ADMIN_UPLOAD

    companion object {
        fun fromWire(wire: String): EvidenceSource =
            entries.firstOrNull { it.wire == wire }
                ?: throw IllegalArgumentException("Unknown evidence source: $wire")
    }
}

/**
 * Appendix A: `evidence_document.verification_status`. This is the queue state MOB-4 shows the
 * crew member, so the wire values are a compatibility surface.
 */
enum class VerificationStatus(val wire: String) {
    PENDING_EXTRACTION("pending_extraction"),
    PENDING_REVIEW("pending_review"),
    AUTO_ACCEPTED("auto_accepted"),
    VERIFIED("verified"),
    REJECTED("rejected");

    companion object {
        fun fromWire(wire: String): VerificationStatus =
            entries.firstOrNull { it.wire == wire }
                ?: throw IllegalArgumentException("Unknown verification status: $wire")
    }
}
