package au.crewcomp.reference

import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.Role
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.transaction.Transactional
import java.time.Instant

/**
 * A sentence on a page, as the office rewrote it (V16). The code carries the original wording
 * under a key; a row here replaces it for everyone until the row is removed. Plain text only —
 * an edit is a sentence, not markup.
 */
@Entity
@Table(name = "page_copy")
class PageCopy {

    @Id
    @Column(name = "copy_key", nullable = false)
    lateinit var key: String

    @Column(name = "text", nullable = false, columnDefinition = "text")
    lateinit var text: String

    @Column(name = "updated_by", nullable = false)
    lateinit var updatedBy: String

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
}

@ApplicationScoped
class PageCopyRepository : PanacheRepositoryBase<PageCopy, String> {
    fun allOrdered(): List<PageCopy> = listAll(io.quarkus.panache.common.Sort.by("key"))
}

@ApplicationScoped
class PageCopyService(
    private val copy: PageCopyRepository,
    private val policy: AccessPolicy,
    private val audit: AuditWriter,
) {

    @Transactional
    fun list(): List<PageCopy> {
        policy.actor()
        return copy.allOrdered()
    }

    @Transactional
    fun set(key: String, text: String): PageCopy {
        policy.require(Role.COMPLIANCE_LEAD, Role.SYSTEM_ADMINISTRATOR)
        policy.assertNotReadOnlyActor()
        require(KEY.matches(key)) { "A text key is lower-case letters, digits, dots and dashes" }
        val clean = text.trim()
        require(clean.isNotEmpty()) { "The text cannot be empty — use \"Back to the original\" to remove a rewrite" }
        require(clean.length <= MAX_LENGTH) { "The text is longer than $MAX_LENGTH characters" }
        val actor = policy.actor().label
        val existing = copy.findById(key)
        val before = existing?.text
        val row = existing ?: PageCopy().apply { this.key = key }
        row.text = clean
        row.updatedBy = actor
        row.updatedAt = Instant.now()
        if (existing == null) copy.persist(row)
        audit.record(
            entityType = "PageCopy",
            event = "page_copy.set",
            businessKey = key,
            before = mapOf("text" to before),
            after = mapOf("text" to clean),
        )
        return row
    }

    @Transactional
    fun clear(key: String) {
        policy.require(Role.COMPLIANCE_LEAD, Role.SYSTEM_ADMINISTRATOR)
        policy.assertNotReadOnlyActor()
        val existing = copy.findById(key) ?: return
        val before = existing.text
        copy.delete(existing)
        audit.record(
            entityType = "PageCopy",
            event = "page_copy.cleared",
            businessKey = key,
            before = mapOf("text" to before),
        )
    }

    private companion object {
        val KEY = Regex("^[a-z0-9][a-z0-9.-]{0,119}$")
        const val MAX_LENGTH = 2000
    }
}
