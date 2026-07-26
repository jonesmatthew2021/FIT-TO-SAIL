package au.crewcomp.sync

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A deleted row, remembered so a delta can carry the deletion (§10.3 "changed + tombstones").
 *
 * Written by AFTER DELETE triggers rather than by application code — same reasoning as the
 * `updated_seq` stamp it shares a sequence with (V2__sync_change_tracking.sql). A delete that
 * bypasses the application still reaches every device.
 *
 * Read-only from Kotlin: there is no code path that inserts or amends one.
 */
@Entity
@Table(name = "sync_tombstone")
class SyncTombstone {

    /** From the same `sync_seq` as `updated_seq`, so one client cursor orders both. */
    @Id
    @Column(name = "seq")
    var seq: Long = 0

    /** The entity type as the sync contract names it — `QualificationHolding`, `Assignment`, … */
    @Column(name = "entity_type", nullable = false)
    lateinit var entityType: String

    @Column(name = "entity_id", nullable = false)
    var entityId: Long = 0

    /** The owning crew member. Null for reference data, which is not person-scoped. */
    @Column(name = "person_id")
    var personId: Long? = null

    @Column(name = "deleted_at", nullable = false)
    var deletedAt: Instant = Instant.now()
}
