package au.crewcomp.people

import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.security.AccessPolicy
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.Instant

/**
 * MOB-9's write path — the pre-sail declaration.
 *
 * Like a crew statement, this is a record of something the crew member *said* and the §5 engine
 * never reads it: attesting that your certificates are the ones on record does not make them so,
 * and a person who signs with a gap still has the gap. Unlike a crew statement, it is evidence
 * rather than a request — nobody actions an attestation, they produce it later — which is why there
 * is no queue for it and no notification chasing anyone to deal with it.
 *
 * Two things this refuses to take from the device:
 *
 *  * **The time.** [Attestation.signedAt] is the server's. A declaration timestamped from the phone
 *    of the person making it is worth nothing as evidence.
 *  * **Whose it is.** The assignment must belong to the authenticated crew member, checked here and
 *    not merely by whoever called.
 */
@ApplicationScoped
class AttestationService(
    private val attestations: AttestationRepository,
    private val assignments: AssignmentRepository,
    private val policy: AccessPolicy,
    private val audit: AuditWriter,
) {

    /**
     * Records one signed declaration.
     *
     * @param opId the device's queue-entry id — the idempotency key. Two attestations for one swing
     *   would be two legal records of one act, so a replay returns the first.
     * @param declarations the ids the crew member confirmed. May legitimately be a subset: signing
     *   with one line outstanding is a real thing to do, and which line it was is the record.
     */
    @Transactional
    fun sign(
        opId: String,
        personId: Long,
        assignmentId: Long,
        declarations: Collection<String>,
    ): Attestation {
        policy.assertCanSeePerson(personId)

        attestations.byOpId(opId)?.let { existing ->
            require(existing.person.id == personId) { "Operation '$opId' is not yours to replay" }
            return existing
        }

        require(declarations.isNotEmpty()) { "An attestation confirms at least one declaration" }

        // The person's own assignment, resolved from their roster rather than loaded by the id the
        // device sent: `forPersonScoped` is what makes signing for somebody else's slot unreachable
        // rather than merely checked.
        val assignment = assignments.forPersonScoped(personId)
            .firstOrNull { it.requiredId == assignmentId }
            ?: throw IllegalArgumentException(
                "Assignment $assignmentId is not yours, so it cannot be signed for",
            )

        val actor = policy.actor()
        val attestation = Attestation().apply {
            this.opId = opId
            this.person = assignment.person
            this.assignment = assignment
            this.crewChange = assignment.crewChange
            // The server's clock. Not negotiable and not a parameter — there is deliberately no way
            // for a caller to supply one.
            this.signedAt = Instant.now()
            this.declarations = declarations.map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
            stampCreated(actor.label)
        }
        attestations.persist(attestation)

        audit.record(
            entityType = "Attestation",
            event = "attestation.signed",
            entityId = attestation.id,
            businessKey = "${assignment.person.sam}/${assignment.crewChange.ccId}",
            after = mapOf(
                "opId" to opId,
                "assignmentId" to assignmentId,
                "ccId" to assignment.crewChange.ccId,
                "declarations" to attestation.declarations.sorted(),
                "signedAt" to attestation.signedAt.toString(),
            ),
        )
        return attestation
    }
}
