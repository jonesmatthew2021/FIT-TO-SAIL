package au.crewcomp.reference

import au.crewcomp.platform.adapters.ObjectStorage
import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.persistence.AuditedEntity
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.Role
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.transaction.Transactional
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat
import java.util.UUID

/**
 * A ship's document (V15): one of the sheets the operation is run from — the training matrix,
 * the skills matrix, the validity periods matrix, the shift-allocation guideline, the OPMS
 * export, the crew certificates sheet. The Coolibah portal's "required documents", per ship.
 *
 * One is current per category; a replaced one stays as history with [supersededAt] set, and its
 * bytes stay in object storage — what a checker read last month must still be readable.
 */
@Entity
@Table(name = "ship_document")
class ShipDocument : AuditedEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partnership_id", nullable = false)
    lateinit var partnership: Partnership

    @Column(name = "category", nullable = false)
    lateinit var category: String

    @Column(name = "file_name", nullable = false)
    lateinit var fileName: String

    @Column(name = "content_type", nullable = false)
    lateinit var contentType: String

    @Column(name = "byte_size", nullable = false)
    var byteSize: Long = 0

    @Column(name = "object_key", nullable = false)
    lateinit var objectKey: String

    @Column(name = "sha256")
    var sha256: String? = null

    @Column(name = "current", nullable = false)
    var current: Boolean = true

    @Column(name = "filed_by", nullable = false)
    lateinit var filedBy: String

    @Column(name = "filed_at", nullable = false)
    var filedAt: Instant = Instant.now()

    @Column(name = "superseded_at")
    var supersededAt: Instant? = null
}

@ApplicationScoped
class ShipDocumentRepository : PanacheRepositoryBase<ShipDocument, Long> {
    fun currentFor(partnershipId: Long): List<ShipDocument> =
        find("from ShipDocument d join fetch d.partnership where d.partnership.id = ?1 and d.current = true order by d.category", partnershipId).list()

    fun currentIn(partnershipId: Long, category: String): ShipDocument? =
        find("partnership.id = ?1 and category = ?2 and current = true", partnershipId, category).firstResult()

    fun byIdWithPartnership(id: Long): ShipDocument? =
        find("from ShipDocument d join fetch d.partnership where d.id = ?1", id).firstResult()
}

data class ShipDocumentContent(val bytes: ByteArray, val contentType: String, val fileName: String)

/**
 * Filing, replacing, withdrawing and reading a ship's documents. Writes are the Compliance
 * Lead's, the Data Steward's and the administrator's — the people who file the office's sheets —
 * and every one is audited. Nothing in the engine reads these; the Admin tabs do.
 */
@ApplicationScoped
class ShipDocumentService(
    private val documents: ShipDocumentRepository,
    private val partnerships: PartnershipRepository,
    private val storage: ObjectStorage,
    private val policy: AccessPolicy,
    private val audit: AuditWriter,
) {

    @Transactional
    fun current(abbrev: String): List<ShipDocument> {
        policy.actor()
        return documents.currentFor(partnership(abbrev).requiredId)
    }

    /** Files a document under a category, replacing the current one — which stays as history. */
    @Transactional
    fun file(abbrev: String, category: String, fileName: String, contentType: String, bytes: ByteArray): ShipDocument {
        policy.require(Role.COMPLIANCE_LEAD, Role.DATA_STEWARD, Role.SYSTEM_ADMINISTRATOR)
        policy.assertNotReadOnlyActor()
        val partnership = partnership(abbrev)
        val cleanCategory = category.trim().lowercase()
        require(cleanCategory.matches(Regex("^[a-z][a-z0-9-]{1,40}$"))) { "A document category is a short lowercase key" }
        require(bytes.isNotEmpty()) { "The upload carried no bytes" }
        require(bytes.size <= MAX_BYTES) { "The file exceeds the $MAX_BYTES byte limit" }
        val cleanName = fileName.trim().ifEmpty { "document" }

        val actor = policy.actor()
        val previous = documents.currentIn(partnership.requiredId, cleanCategory)
        if (previous != null) {
            previous.current = false
            previous.supersededAt = Instant.now()
            previous.stampUpdated(actor.label)
            documents.flush()
        }

        val key = "ship-documents/${partnership.abbrev}/${UUID.randomUUID()}/original"
        storage.put(key, bytes, contentType)
        val document = ShipDocument().apply {
            this.partnership = partnership
            this.category = cleanCategory
            this.fileName = cleanName
            this.contentType = contentType
            byteSize = bytes.size.toLong()
            objectKey = key
            sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
            current = true
            filedBy = actor.label
            stampCreated(actor.label)
        }
        documents.persist(document)
        documents.flush()

        audit.record(
            entityType = "ShipDocument",
            event = if (previous == null) "ship_document.filed" else "ship_document.replaced",
            entityId = document.id,
            businessKey = "${partnership.abbrev}/$cleanCategory",
            before = previous?.let { mapOf("fileName" to it.fileName, "filedAt" to it.filedAt.toString()) },
            after = mapOf("fileName" to cleanName, "byteSize" to bytes.size, "sha256" to document.sha256),
        )
        return document
    }

    /** Takes the current document off the file. Nothing is destroyed; it becomes history. */
    @Transactional
    fun withdraw(documentId: Long, reason: String) {
        policy.require(Role.COMPLIANCE_LEAD, Role.DATA_STEWARD, Role.SYSTEM_ADMINISTRATOR)
        policy.assertNotReadOnlyActor()
        val cleanReason = reason.trim()
        require(cleanReason.isNotEmpty()) { "Withdrawing a document needs a reason" }
        val document = documents.byIdWithPartnership(documentId) ?: throw EntityNotFoundException("No ship document $documentId")
        require(document.current) { "${document.fileName} is already history" }
        document.current = false
        document.supersededAt = Instant.now()
        document.stampUpdated(policy.actor().label)
        audit.record(
            entityType = "ShipDocument",
            event = "ship_document.withdrawn",
            entityId = document.id,
            businessKey = "${document.partnership.abbrev}/${document.category}",
            before = mapOf("fileName" to document.fileName),
            after = mapOf("reason" to cleanReason),
        )
    }

    @Transactional
    fun content(documentId: Long): ShipDocumentContent {
        policy.actor()
        val document = documents.byIdWithPartnership(documentId) ?: throw EntityNotFoundException("No ship document $documentId")
        val bytes = storage.get(document.objectKey) ?: throw EntityNotFoundException("The bytes of ${document.fileName} are missing from storage")
        return ShipDocumentContent(bytes, document.contentType, document.fileName)
    }

    private fun partnership(abbrev: String): Partnership =
        partnerships.byAbbrev(abbrev) ?: throw EntityNotFoundException("No partnership $abbrev")

    companion object {
        const val MAX_BYTES = 64 * 1024 * 1024
    }
}
