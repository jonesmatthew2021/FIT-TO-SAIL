package au.crewcomp.platform.audit

import au.crewcomp.platform.security.ActorKind
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable
import java.time.Instant

/**
 * §4.4 AuditEvent — append-only, self-contained, immutable, in a stable global order.
 *
 * Mapped [Immutable] so Hibernate will not emit an UPDATE for it under any circumstance: the
 * only legitimate operation on this table is INSERT. Deletion has no code path at all.
 *
 * The shape is chosen so the external read-only mirror of §17.4 is a later *consumer* rather
 * than a schema migration:
 *  - [seq] is gapless and ordered by real commit order (see `AuditWriter`), so an auditor can
 *    detect a removed event by a hole in the sequence (AUD-2);
 *  - [prevHash]/[eventHash] chain every event to its predecessor, so a rewrite is detectable
 *    without trusting this database (AUD-2);
 *  - [actorLabel] and the JSON snapshots are denormalised, so an exported event is readable on
 *    its own (§4.4).
 */
@Entity
@Immutable
@Table(name = "audit_event")
class AuditEvent {

    @Id
    @Column(name = "seq")
    var seq: Long = 0

    @Column(name = "occurred_at", nullable = false)
    lateinit var occurredAt: Instant

    @Column(name = "actor_kind", nullable = false)
    lateinit var actorKind: String

    @Column(name = "actor_user_id")
    var actorUserId: Long? = null

    @Column(name = "actor_label", nullable = false)
    lateinit var actorLabel: String

    @Column(name = "entity_type", nullable = false)
    lateinit var entityType: String

    @Column(name = "entity_id")
    var entityId: Long? = null

    /** e.g. a register record's `{PT}{CCnn}-{seq}`, so the event reads without a join. */
    @Column(name = "entity_business_key")
    var entityBusinessKey: String? = null

    @Column(name = "event", nullable = false)
    lateinit var event: String

    /**
     * JSON, stored as `text` so the bytes survive a round-trip unchanged — see the column
     * comment in V1__baseline.sql. The hash chain covers these exact strings.
     */
    @Column(name = "before_state")
    var beforeState: String? = null

    @Column(name = "after_state")
    var afterState: String? = null

    /** AIA-3: model and prompt/config version for AI-touched events. */
    @Column(name = "ai_model")
    var aiModel: String? = null

    @Column(name = "ai_config_version")
    var aiConfigVersion: String? = null

    @Column(name = "request_id")
    var requestId: String? = null

    @Column(name = "prev_hash")
    var prevHash: String? = null

    @Column(name = "event_hash", nullable = false)
    lateinit var eventHash: String

    val kind: ActorKind get() = ActorKind.entries.first { it.wire == actorKind }
}
