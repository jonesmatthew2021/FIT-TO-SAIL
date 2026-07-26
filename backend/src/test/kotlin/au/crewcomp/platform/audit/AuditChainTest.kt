package au.crewcomp.platform.audit

import au.crewcomp.platform.security.ActorKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * AUD-2 — tamper-evidence. These tests are the statement of what an external auditor will be
 * able to detect from an exported trail (§17.4).
 */
@DisplayName("Audit chain tamper-evidence")
class AuditChainTest {

    private fun event(
        seq: Long,
        event: String = "holding.updated",
        actorKind: ActorKind = ActorKind.HUMAN,
        after: String? = """{"status":"held_perpetual"}""",
        prevHash: String? = null,
    ) = AuditEvent().apply {
        this.seq = seq
        occurredAt = Instant.parse("2026-07-26T02:00:00Z").plusSeconds(seq)
        this.actorKind = actorKind.wire
        actorUserId = 7
        actorLabel = "D. Steward <Acme Entra>"
        entityType = "QualificationHolding"
        entityId = 100 + seq
        entityBusinessKey = "SAM001/QL-01"
        this.event = event
        afterState = after
        this.prevHash = prevHash
    }

    /** Builds a correctly chained run of events, as the writer would. */
    private fun chainOf(count: Int): List<AuditEvent> {
        var previous: String? = null
        return (1..count).map { seq ->
            event(seq.toLong(), prevHash = previous).also {
                it.eventHash = AuditChain.hash(it)
                previous = it.eventHash
            }
        }
    }

    @Test
    fun `an untouched chain verifies`() {
        assertThat(AuditChain.verify(chainOf(5)).isIntact).isTrue()
    }

    @Test
    fun `an empty trail verifies`() {
        assertThat(AuditChain.verify(emptyList()).isIntact).isTrue()
    }

    @Test
    fun `a removed event is detected as a sequence gap`() {
        val events = chainOf(5).filterNot { it.seq == 3L }

        val result = AuditChain.verify(events)

        assertThat(result).isInstanceOf(ChainVerification.Broken::class.java)
        assertThat((result as ChainVerification.Broken).atSeq).isEqualTo(4)
        assertThat(result.reason).contains("expected sequence 3")
    }

    @Test
    fun `an edited event is detected by its own hash`() {
        val events = chainOf(3)
        events[1].afterState = """{"status":"not_held"}"""

        val result = AuditChain.verify(events)

        assertThat(result).isInstanceOf(ChainVerification.Broken::class.java)
        assertThat((result as ChainVerification.Broken).atSeq).isEqualTo(2)
        assertThat(result.reason).contains("event_hash")
    }

    @Test
    fun `an edited event rehashed in place is still detected by the next link`() {
        // The interesting attack: someone rewrites an event *and* recomputes its hash. The chain
        // catches it because the following event's prev_hash no longer matches.
        val events = chainOf(3)
        events[1].afterState = """{"status":"not_held"}"""
        events[1].eventHash = AuditChain.hash(events[1])

        val result = AuditChain.verify(events)

        assertThat(result).isInstanceOf(ChainVerification.Broken::class.java)
        assertThat((result as ChainVerification.Broken).atSeq).isEqualTo(3)
        assertThat(result.reason).contains("prev_hash")
    }

    @Test
    fun `a reordered pair is detected`() {
        val events = chainOf(4).toMutableList()
        events[1] = events[2].also { events[2] = events[1] }

        assertThat(AuditChain.verify(events).isIntact).isFalse()
    }

    @Test
    fun `a page of the trail can be verified from its own starting sequence`() {
        val page = chainOf(6).drop(3)

        assertThat(AuditChain.verify(page, expectedFirstSeq = 4).isIntact).isTrue()
        assertThat(AuditChain.verify(page, expectedFirstSeq = 1).isIntact).isFalse()
    }

    @Test
    fun `the actor kind is part of the hash - an AI action cannot be relabelled as human`() {
        // AIA-3 is only meaningful if the distinction is tamper-evident.
        val events = chainOf(1)
        val original = events.single().eventHash

        events.single().actorKind = ActorKind.AI_AUTOMATIC.wire

        assertThat(AuditChain.hash(events.single())).isNotEqualTo(original)
    }

    @Test
    fun `sub-microsecond precision does not change the hash`() {
        // Postgres `timestamptz` stores microseconds. If the canonical form were sensitive to the
        // nanoseconds Instant.now() carries, every event would verify in memory and fail after a
        // round-trip through the database — which is exactly what ComplianceIT caught.
        val nanos = event(1).apply {
            occurredAt = occurredAt.plusNanos(123)
            eventHash = ""
        }
        val truncated = event(1).apply { eventHash = "" }

        assertThat(AuditChain.hash(nanos)).isEqualTo(AuditChain.hash(truncated))
    }

    @Test
    fun `field boundaries cannot be forged by a value containing the separator`() {
        val separator = "\u001F"
        val a = event(1).apply { entityBusinessKey = "SAM001${separator}injected"; eventHash = "" }
        val b = event(1).apply { entityBusinessKey = "SAM001"; event = "injected"; eventHash = "" }

        assertThat(AuditChain.hash(a)).isNotEqualTo(AuditChain.hash(b))
    }
}
