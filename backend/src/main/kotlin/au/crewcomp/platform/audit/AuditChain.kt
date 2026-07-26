package au.crewcomp.platform.audit

import java.security.MessageDigest
import java.time.temporal.ChronoUnit

/**
 * The tamper-evidence rules of AUD-2, as pure functions.
 *
 * Kept out of [AuditWriter] so that the hashing contract can be exercised directly by tests and
 * reused by the §17.4 export job and the ADM-10 integrity view — an auditor's check and the
 * writer's must be the same code, or the guarantee is only a claim.
 */
object AuditChain {

    /**
     * The canonical serialisation the chain hashes.
     *
     * Field order and the separator are part of the audit contract: changing either invalidates
     * every hash already written, so a change here needs a re-chaining migration and an ADR.
     * Field values have the separator escaped out of them, so no value can forge a field
     * boundary.
     */
    fun canonicalForm(event: AuditEvent): String = listOf(
        event.seq.toString(),
        // Microsecond precision is the storage precision; hashing it explicitly makes the
        // hash identical whether the event is freshly built or reloaded from the database.
        event.occurredAt.truncatedTo(ChronoUnit.MICROS).toString(),
        event.actorKind,
        event.actorUserId?.toString() ?: "",
        event.actorLabel,
        event.entityType,
        event.entityId?.toString() ?: "",
        event.entityBusinessKey ?: "",
        event.event,
        event.beforeState ?: "",
        event.afterState ?: "",
        event.aiModel ?: "",
        event.aiConfigVersion ?: "",
        event.prevHash ?: "",
    ).joinToString(FIELD_SEPARATOR) { it.replace(FIELD_SEPARATOR, ESCAPED_SEPARATOR) }

    fun hash(event: AuditEvent): String =
        MessageDigest.getInstance("SHA-256")
            .digest(canonicalForm(event).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /**
     * Recomputes the chain over [events] and reports the first inconsistency — a missing
     * sequence number, a broken link, or contents that no longer match their hash.
     *
     * @param events events in ascending `seq` order.
     * @param expectedFirstSeq the sequence the range must start at; pass 1 to verify the whole
     *   trail from its beginning, or the first seq of a page to verify a slice.
     */
    fun verify(events: List<AuditEvent>, expectedFirstSeq: Long = 1): ChainVerification {
        if (events.isEmpty()) return ChainVerification.Intact

        var expected = expectedFirstSeq
        var previousHash: String? = null

        for ((index, event) in events.withIndex()) {
            if (event.seq != expected) {
                return ChainVerification.Broken(event.seq, "expected sequence $expected, found ${event.seq}")
            }
            // The first event of a partial range links to something outside the range, so its
            // prev_hash is taken on trust; every subsequent link is checked.
            if (index > 0 && event.prevHash != previousHash) {
                return ChainVerification.Broken(event.seq, "prev_hash does not match the preceding event")
            }
            if (hash(event) != event.eventHash) {
                return ChainVerification.Broken(event.seq, "event_hash does not match the event contents")
            }
            previousHash = event.eventHash
            expected += 1
        }
        return ChainVerification.Intact
    }

    /** ASCII unit separator, written as an escape so it stays visible in source. */
    private const val FIELD_SEPARATOR = "\u001F"
    private const val ESCAPED_SEPARATOR = "\\u001f"
}

sealed interface ChainVerification {
    data object Intact : ChainVerification
    data class Broken(val atSeq: Long, val reason: String) : ChainVerification

    val isIntact: Boolean get() = this is Intact
}
