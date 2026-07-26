package au.crewcomp.platform.persistence

import jakarta.persistence.Column
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import java.time.Instant

/**
 * The columns every §4 entity carries: a surrogate key and created/updated stamps.
 *
 * Business keys (Sam #, requirement code, register record ID, CC ID) are unique constraints on
 * the concrete tables, never primary keys — see V1__baseline.sql.
 *
 * `createdBy`/`updatedBy` are the actor *label*, denormalised deliberately: they are a
 * convenience for reading a row, not the audit trail. The audit trail is `audit_event`, which
 * carries the actor's identity, kind and the before/after states (§4.4, AUTH-3).
 */
@MappedSuperclass
abstract class AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "created_by", nullable = false)
    var createdBy: String = "system"

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    @Column(name = "updated_by", nullable = false)
    var updatedBy: String = "system"

    /** The id, asserted present — for use after persist, where absence is a bug not a state. */
    val requiredId: Long get() = checkNotNull(id) { "${javaClass.simpleName} has not been persisted" }

    fun stampCreated(actorLabel: String, now: Instant = Instant.now()) {
        createdAt = now
        createdBy = actorLabel
        updatedAt = now
        updatedBy = actorLabel
    }

    fun stampUpdated(actorLabel: String, now: Instant = Instant.now()) {
        updatedAt = now
        updatedBy = actorLabel
    }
}

/**
 * For rows that are written once and never edited — register notes, approval conditions,
 * requirement aliases, notifications. They carry no `updated_*` columns, which is the schema
 * saying out loud that amending one is not a supported operation: a correction is a new row.
 */
@MappedSuperclass
abstract class CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "created_by", nullable = false)
    var createdBy: String = "system"

    val requiredId: Long get() = checkNotNull(id) { "${javaClass.simpleName} has not been persisted" }

    fun stampCreated(actorLabel: String, now: Instant = Instant.now()) {
        createdAt = now
        createdBy = actorLabel
    }
}
