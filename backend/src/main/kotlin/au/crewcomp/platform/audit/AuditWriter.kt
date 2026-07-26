package au.crewcomp.platform.audit

import au.crewcomp.platform.security.Actor
import au.crewcomp.platform.security.ActorContext
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * AUTH-3 / SEC-6 — writes the audit trail. Every business mutation goes through here, inside the
 * same transaction as the change itself, so an audited change cannot commit without its event.
 *
 * ### Ordering
 *
 * [AuditEvent.seq] is allocated by this writer under a transaction-scoped advisory lock rather
 * than by a database identity column. An identity column is monotonic but *not* ordered by
 * commit: two concurrent transactions can take sequence values 7 and 8 and commit in the other
 * order, and 7 can be lost by a rollback. Either would break AUD-2's premise that a gap or an
 * out-of-order hash link means tampering. Serialising audit inserts costs nothing at NFR-1's
 * scale (~60 users) and buys an audit trail an external party can verify.
 */
@ApplicationScoped
class AuditWriter(
    private val em: EntityManager,
    private val actorContext: ActorContext,
    private val objectMapper: ObjectMapper,
) {

    /**
     * Appends an audit event. Must be called inside the business transaction — [Transactional]
     * is `MANDATORY` precisely so that an audit write outside one fails loudly at development
     * time instead of silently landing in its own transaction.
     *
     * @param actor overrides the current actor; jobs pass [Actor.system]. Defaults to `null`
     *   rather than to `actorContext.require()` on purpose: Kotlin compiles a default argument
     *   into a static `record$default` bridge that reads instance fields **directly**, bypassing
     *   the CDI client proxy, so a default referring to an injected field resolves to `null` at
     *   runtime. Any default argument on a bean method must be a constant.
     */
    @Transactional(Transactional.TxType.MANDATORY)
    fun record(
        entityType: String,
        event: String,
        entityId: Long? = null,
        businessKey: String? = null,
        before: Any? = null,
        after: Any? = null,
        actor: Actor? = null,
        requestId: String? = null,
    ): AuditEvent {
        val recordingActor = actor ?: actorContext.require()

        // Serialise audit appends so `seq` follows real commit order and the hash chain is sound.
        em.createNativeQuery("select pg_advisory_xact_lock(:key)")
            .setParameter("key", ADVISORY_LOCK_KEY)
            .singleResult

        val previous = em.createQuery(
            "select e from AuditEvent e where e.seq = (select max(x.seq) from AuditEvent x)",
            AuditEvent::class.java,
        ).resultList.firstOrNull()

        val entity = AuditEvent().apply {
            seq = (previous?.seq ?: 0L) + 1
            // Truncated to Postgres `timestamptz` precision. Instant.now() carries nanoseconds
            // that the column cannot store, so an untruncated value hashes differently before
            // and after a round-trip — which would fail every chain verification (AUD-2).
            occurredAt = Instant.now().truncatedTo(ChronoUnit.MICROS)
            actorKind = recordingActor.kind.wire
            actorUserId = recordingActor.userAccountId
            actorLabel = recordingActor.label
            this.entityType = entityType
            this.entityId = entityId
            entityBusinessKey = businessKey
            this.event = event
            beforeState = before?.let { objectMapper.writeValueAsString(it) }
            afterState = after?.let { objectMapper.writeValueAsString(it) }
            aiModel = recordingActor.aiModel
            aiConfigVersion = recordingActor.aiConfigVersion
            this.requestId = requestId
            prevHash = previous?.eventHash
        }
        entity.eventHash = AuditChain.hash(entity)

        em.persist(entity)
        return entity
    }

    /**
     * Recomputes the chain and reports the first inconsistency. Delegates to [AuditChain] so
     * that the writer, the §17.4 export job and an external auditor all check the same rule.
     */
    fun verifyChain(events: List<AuditEvent>, expectedFirstSeq: Long = 1): ChainVerification =
        AuditChain.verify(events, expectedFirstSeq)

    companion object {
        /** Arbitrary but fixed: the advisory-lock namespace for audit appends. */
        const val ADVISORY_LOCK_KEY: Long = 0x4352_4557_4155_4454L
    }
}
