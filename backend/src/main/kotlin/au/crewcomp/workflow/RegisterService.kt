package au.crewcomp.workflow

import au.crewcomp.engine.RegisterOutcome
import au.crewcomp.notify.NotificationKind
import au.crewcomp.notify.NotificationService
import au.crewcomp.people.PersonRepository
import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.Role
import au.crewcomp.platform.security.ScopeGuard
import au.crewcomp.platform.time.BusinessClock
import au.crewcomp.reference.CrewChange
import au.crewcomp.reference.CrewChangeRepository
import au.crewcomp.reference.RequirementRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.Instant
import java.time.LocalDate

/**
 * Raised when a request is being lodged after the swing's submission cutoff (Q17).
 *
 * Late submission is **permitted and acknowledged**, not blocked — a genuine problem does not
 * stop existing because a date passed. What is not permitted is lodging one silently, so the
 * first attempt fails with the cutoff in the message and the retry carries
 * `acknowledgeLateSubmission`, which lands on the record and in the audit event.
 */
class LateSubmissionException(val cutoff: LocalDate, val raised: LocalDate) :
    RuntimeException(
        "The submission cutoff for this swing was $cutoff and today is $raised. " +
            "A late request may still be lodged, but the lateness has to be acknowledged.",
    )

/**
 * ADM-4 — the exemption and query register (§4.4, §5.1 step 4).
 *
 * This is the module the POC's 445-row workbook becomes, and the one place register records are
 * written. Three properties are load-bearing:
 *
 *  - **The business key is the server's to allocate.** `{PT}{CCnn}-{seq}` is monotonic per
 *    prefix, generated under a transaction-scoped advisory lock so two coordinators raising a
 *    request for the same swing at the same moment cannot collide.
 *  - **Decision authority is the Workflow Manager's alone** (§3, Q14). A Crew Coordinator raises
 *    requests and adds PW notes; only OPS transitions, closes, or attaches approval conditions.
 *  - **An approved record with no approval window cannot exist.** The database says so too, but
 *    reaching the constraint would mean a 500 rather than a message — and §5.1 step 4's overlay
 *    reads exactly those two dates to decide whether a cell is `exempt`.
 *
 * Every act also appends a [RegisterAuditEntry]: the human-readable trail the POC's users read on
 * the record itself. That is *in addition to* the machine-readable `audit_event` chain, not
 * instead of it — the two answer different questions and both are written in the same
 * transaction.
 */
@ApplicationScoped
class RegisterService(
    private val records: RegisterRecordRepository,
    private val people: PersonRepository,
    private val requirements: RequirementRepository,
    private val crewChanges: CrewChangeRepository,
    private val policy: AccessPolicy,
    private val scopeGuard: ScopeGuard,
    private val clock: BusinessClock,
    private val audit: AuditWriter,
    private val notifications: NotificationService,
) {

    // -----------------------------------------------------------------------
    // Reads
    // -----------------------------------------------------------------------

    /** @param state `open`, `closed`, or `all`/null for the whole history. */
    @Transactional
    fun search(
        partnershipAbbrev: String? = null,
        ccId: String? = null,
        type: String? = null,
        state: String? = null,
    ): List<RegisterRecord> {
        policy.require(*READERS)
        require(state == null || state == OPEN || state == CLOSED || state == ALL) {
            "A register filter is open, closed or all — not '$state'"
        }
        // Validated here rather than passed through, so a typo answers "unknown type" instead of
        // silently returning nothing and looking like an empty register.
        type?.let { RegisterType.fromWire(it) }

        return records.searchScoped(
            partnershipAbbrev = partnershipAbbrev?.ifBlank { null },
            ccId = ccId?.ifBlank { null },
            type = type?.ifBlank { null },
            openOnly = when (state) {
                OPEN -> true
                CLOSED -> false
                else -> null
            },
        )
    }

    @Transactional
    fun get(recordId: String): RegisterRecord {
        policy.require(*READERS)
        val record = records.detailByRecordId(recordId)
            ?: throw EntityNotFoundException("No register record $recordId")
        // A load by business key is not reachable by the scope clause, so the check is explicit.
        scopeGuard.assertVisible(record.person?.id, record.partnership.requiredId)
        return record
    }

    /**
     * The next business key for a swing, for the create form's preview (§6, "auto-ID preview").
     *
     * Advisory only: the id is allocated again inside [create] under the lock, because a preview
     * two coordinators both saw is not a reservation. The form shows what it will most likely be,
     * and the record shows what it is.
     */
    @Transactional
    fun nextRecordId(partnershipAbbrev: String, ccId: String): String {
        policy.require(*RAISERS)
        val crewChange = requireCrewChange(partnershipAbbrev, ccId)
        val prefix = prefixFor(crewChange)
        return "$prefix${records.highestSequenceForPrefix(prefix) + 1}"
    }

    // -----------------------------------------------------------------------
    // Writes
    // -----------------------------------------------------------------------

    @Transactional
    @Suppress("LongParameterList")
    fun create(
        type: RegisterType,
        partnershipAbbrev: String,
        ccId: String,
        personId: Long? = null,
        requirementId: Long? = null,
        reqRaw: String? = null,
        effectiveFrom: LocalDate? = null,
        effectiveTo: LocalDate? = null,
        status: RegisterStatus? = null,
        acknowledgeLateSubmission: Boolean = false,
    ): RegisterRecord {
        policy.require(*RAISERS)

        val crewChange = requireCrewChange(partnershipAbbrev, ccId)
        val today = clock.today()

        // Q17: the cutoff is the *submission* cutoff, and it is stored rather than derived so
        // that a swing can legitimately have an unusual one.
        if (today.isAfter(crewChange.cutoffDate) && !acknowledgeLateSubmission) {
            throw LateSubmissionException(crewChange.cutoffDate, today)
        }

        val initialStatus = status ?: defaultStatusFor(type)
        require(initialStatus.isOpen || initialStatus == RegisterStatus.COMPLETE_BEFORE_JOINING) {
            "A new record starts open or 'Complete before joining', not '${initialStatus.wire}'"
        }

        val person = personId?.let {
            people.findById(it) ?: throw EntityNotFoundException("No person $it")
        }
        val requirement = requirementId?.let {
            requirements.findById(it) ?: throw EntityNotFoundException("No requirement $it")
        }
        val cleanRaw = reqRaw?.trim()?.ifEmpty { null }
        require(requirement != null || cleanRaw != null) {
            "A register record names a requirement, either by catalogue id or as the raw title " +
                "it was written with"
        }
        validateWindow("Effective", effectiveFrom, effectiveTo, crewChange)

        val actor = policy.actor()
        val record = RegisterRecord().apply {
            recordId = allocateRecordId(crewChange)
            this.type = type
            this.person = person
            this.position = person?.position
            this.requirement = requirement
            this.reqRaw = cleanRaw
            partnership = crewChange.partnership
            this.crewChange = crewChange
            this.effectiveFrom = effectiveFrom
            this.effectiveTo = effectiveTo
            raisedDate = today
            this.status = initialStatus
            lateSubmissionAcknowledged = today.isAfter(crewChange.cutoffDate)
            stampCreated(actor.label)
        }
        records.persist(record)

        trail(
            record,
            if (record.lateSubmissionAcknowledged) {
                "Raised as ${type.wire} (${initialStatus.wire}) after the ${crewChange.cutoffDate} " +
                    "cutoff; lateness acknowledged."
            } else {
                "Raised as ${type.wire} (${initialStatus.wire})."
            },
        )

        audit.record(
            entityType = "RegisterRecord",
            event = "register.created",
            entityId = record.id,
            businessKey = record.recordId,
            after = snapshot(record),
        )

        notifyWorkflow(
            record = record,
            title = "A register request has been raised",
            // A record need not name a person — §4.4 permits one raised against a raw title alone,
            // which is how the 445 rows of history are preserved.
            body = "${record.recordId}: ${type.wire}" +
                (person?.let { " for ${it.name} (${it.sam})" } ?: "") +
                " on ${crewChange.partnership.abbrev} ${crewChange.ccId}.",
        )

        // Re-read through the fetch-joining query before returning. The record was assembled from
        // lazy proxies — `person.position` in particular — and the DTO mapping happens after this
        // transaction closes, where reaching one throws LazyInitializationException. Every other
        // method here already goes through `requireRecord`, which fetches the same graph.
        return requireRecord(record.recordId)
    }

    /**
     * Moves an open record between the open statuses — PW → MRL → OPS as it changes hands.
     *
     * Closure is [close]'s job, not a transition: closing needs an outcome and, for an approval,
     * a window, and letting a bare status change reach `Closed - Approved` would be a route to a
     * record §5.1's overlay reads as an exemption with no dates on it.
     */
    @Transactional
    fun transition(recordId: String, status: RegisterStatus): RegisterRecord {
        policy.require(*DECIDERS)

        val record = requireRecord(recordId)
        require(record.isOpen || record.status == RegisterStatus.COMPLETE_BEFORE_JOINING) {
            "${record.recordId} is ${record.status.wire}; a closed record is not reopened, " +
                "a new one is raised"
        }
        require(status.isOpen || status == RegisterStatus.COMPLETE_BEFORE_JOINING) {
            "Use close() to close a record — '${status.wire}' is not a transition"
        }
        require(status != record.status) { "${record.recordId} is already ${status.wire}" }

        val before = snapshot(record)
        val from = record.status
        record.status = status
        record.stampUpdated(policy.actor().label)

        trail(record, "Moved from ${from.wire} to ${status.wire}.")
        audit.record(
            entityType = "RegisterRecord",
            event = "register.transitioned",
            entityId = record.id,
            businessKey = record.recordId,
            before = before,
            after = snapshot(record),
        )
        notifyWorkflow(
            record = record,
            title = "A register request has changed hands",
            body = "${record.recordId} moved from ${from.wire} to ${status.wire}.",
        )
        return record
    }

    /**
     * Closes a record with its outcome — the Workflow Manager's decision (Q14).
     *
     * An `Approved` outcome must carry an approval window, and the window must sit inside the
     * swing (§4.4). That is not bureaucracy: §5.1 step 4 turns a `gap` into `exempt` only for an
     * approved record whose window covers the cell, so an approval with no dates would look like
     * a decision and change nothing on screen.
     */
    @Transactional
    fun close(
        recordId: String,
        outcome: RegisterOutcome,
        approvalFrom: LocalDate? = null,
        approvalTo: LocalDate? = null,
        conditions: List<Pair<ConditionType, String>> = emptyList(),
        note: String? = null,
    ): RegisterRecord {
        policy.require(*DECIDERS)

        val record = requireRecord(recordId)
        require(record.isOpen || record.status == RegisterStatus.COMPLETE_BEFORE_JOINING) {
            "${record.recordId} is already ${record.status.wire}"
        }

        if (outcome == RegisterOutcome.APPROVED) {
            requireNotNull(approvalFrom) { "An approved record must carry its approval window" }
            requireNotNull(approvalTo) { "An approved record must carry its approval window" }
            validateWindow("Approval", approvalFrom, approvalTo, record.crewChange)
        } else {
            require(approvalFrom == null && approvalTo == null) {
                "Only an approved record carries an approval window; this one is ${outcome.wire}"
            }
        }

        val before = snapshot(record)
        val actor = policy.actor()

        record.status = RegisterStatus.closedWith(outcome)
        record.outcome = outcome
        record.approvalFrom = approvalFrom
        record.approvalTo = approvalTo
        conditions.forEach { (conditionType, body) -> attach(record, conditionType, body, actor.label) }
        if (note != null && note.isNotBlank()) {
            attachNote(record, decidingParty(), note.trim(), actor.label)
        }
        record.stampUpdated(actor.label)

        trail(
            record,
            buildString {
                append("Closed as ${outcome.wire}")
                if (approvalFrom != null) append(", approved $approvalFrom to $approvalTo")
                if (conditions.isNotEmpty()) append(", with ${conditions.size} condition(s)")
                append(".")
            },
        )
        audit.record(
            entityType = "RegisterRecord",
            event = "register.closed",
            entityId = record.id,
            businessKey = record.recordId,
            before = before,
            after = snapshot(record),
        )
        notifyWorkflow(
            record = record,
            title = "A register request has been decided",
            body = "${record.recordId} closed as ${outcome.wire}" +
                if (approvalFrom != null) ", approved $approvalFrom to $approvalTo." else ".",
        )
        return record
    }

    /**
     * Adds a party-attributed note (§4.4). Append-only: notes are a conversation record, and
     * editing one would rewrite what somebody said.
     */
    @Transactional
    fun addNote(recordId: String, party: String, body: String): RegisterRecord {
        policy.require(*RAISERS)

        val clean = body.trim()
        require(clean.isNotEmpty()) { "A note needs a body" }
        require(party in PARTIES) { "A note is attributed to ${PARTIES.joinToString(", ")}, not '$party'" }

        val record = requireRecord(recordId)
        val actor = policy.actor()
        attachNote(record, party, clean, actor.label)
        record.stampUpdated(actor.label)

        trail(record, "$party note added.")
        audit.record(
            entityType = "RegisterRecord",
            event = "register.note_added",
            entityId = record.id,
            businessKey = record.recordId,
            after = mapOf("party" to party, "body" to clean),
        )
        return record
    }

    /** Attaches a structured approval condition (Q16), replacing the POC's free text. */
    @Transactional
    fun addCondition(recordId: String, conditionType: ConditionType, body: String): RegisterRecord {
        policy.require(*DECIDERS)

        val clean = body.trim()
        require(clean.isNotEmpty()) { "A condition needs a body" }

        val record = requireRecord(recordId)
        val actor = policy.actor()
        attach(record, conditionType, clean, actor.label)
        record.stampUpdated(actor.label)

        trail(record, "Condition added (${conditionType.wire}).")
        audit.record(
            entityType = "RegisterRecord",
            event = "register.condition_added",
            entityId = record.id,
            businessKey = record.recordId,
            after = mapOf("type" to conditionType.wire, "body" to clean),
        )
        return record
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun requireCrewChange(partnershipAbbrev: String, ccId: String): CrewChange {
        val crewChange = crewChanges.byBusinessKey(ccId, partnershipAbbrev)
            ?: throw EntityNotFoundException("No crew change $ccId for partnership $partnershipAbbrev")
        policy.assertCanSeePartnership(crewChange.partnership.requiredId)
        return crewChange
    }

    private fun requireRecord(recordId: String): RegisterRecord {
        val record = records.detailByRecordId(recordId)
            ?: throw EntityNotFoundException("No register record $recordId")
        scopeGuard.assertVisible(record.person?.id, record.partnership.requiredId)
        return record
    }

    /** `{PT}{CCnn}-` — the business-key prefix (§4.4). */
    private fun prefixFor(crewChange: CrewChange): String =
        "${crewChange.partnership.abbrev}${crewChange.ccId}-"

    private fun allocateRecordId(crewChange: CrewChange): String {
        val prefix = prefixFor(crewChange)
        records.lockPrefix(prefix)
        return "$prefix${records.highestSequenceForPrefix(prefix) + 1}"
    }

    /**
     * The status a new record of each type starts in — who holds it first.
     *
     * The spec enumerates the statuses but not the transition graph, so this encodes the reading
     * the names imply: PW raises and holds its own requests, an MRL query starts with MRL, an OPS
     * request starts with OPS. Worth confirming with the client; changing it is one map.
     */
    private fun defaultStatusFor(type: RegisterType): RegisterStatus = when (type) {
        RegisterType.EXEMPTION_REQUEST_PW -> RegisterStatus.OPEN_PW
        RegisterType.PW_QUERY -> RegisterStatus.OPEN_PW
        RegisterType.MRL_QUERY -> RegisterStatus.OPEN_MRL
        RegisterType.EXEMPTION_REQUEST_FOLLOWING_MRL_QUERY -> RegisterStatus.OPEN_MRL
        RegisterType.EXEMPTION_REQUEST_OPS -> RegisterStatus.OPEN_OPS
    }

    /** A decision is OPS's (Q14), so a note attached to one is attributed to OPS. */
    private fun decidingParty(): String = "OPS"

    private fun validateWindow(
        what: String,
        from: LocalDate?,
        to: LocalDate?,
        crewChange: CrewChange?,
    ) {
        if (from == null && to == null) return
        requireNotNull(from) { "$what window needs a start date" }
        requireNotNull(to) { "$what window needs an end date" }
        require(!to.isBefore(from)) { "$what window ends ($to) before it starts ($from)" }
        if (crewChange != null) {
            require(!from.isBefore(crewChange.fromDate) && !to.isAfter(crewChange.toDate)) {
                "$what window $from..$to falls outside the swing " +
                    "${crewChange.fromDate}..${crewChange.toDate}"
            }
        }
    }

    private fun attach(record: RegisterRecord, conditionType: ConditionType, body: String, actor: String) {
        record.conditions.add(
            ApprovalCondition().apply {
                registerRecord = record
                type = conditionType
                this.body = body
                stampCreated(actor)
            },
        )
    }

    private fun attachNote(record: RegisterRecord, party: String, body: String, actor: String) {
        record.notes.add(
            RegisterNote().apply {
                registerRecord = record
                this.party = party
                this.body = body
                stampCreated(actor)
            },
        )
    }

    /** The human-readable trail shown on the record itself — POC behaviour, kept (§4.4). */
    private fun trail(record: RegisterRecord, body: String) {
        record.auditEntries.add(
            RegisterAuditEntry().apply {
                registerRecord = record
                ordinal = record.auditEntries.size + 1
                this.body = body
                actor = policy.actor().label
                occurredAt = Instant.now()
            },
        )
    }

    /**
     * §9: "register events → Workflow Manager".
     *
     * Fire-and-forget by design. `raiseForRoles` returns an empty list when no back-office account
     * holds the role — which is every deployment until the identity spike creates them — and this
     * carries on regardless. A workflow write that failed because nobody could be notified would be
     * a notification feature holding the register hostage.
     *
     * SEC-13: the title names no person and no requirement, because it is the only field a push
     * payload carries. The record id and the names are in the body, fetched in-app.
     */
    private fun notifyWorkflow(record: RegisterRecord, title: String, body: String) {
        notifications.raiseForRoles(
            roles = listOf(Role.WORKFLOW_MANAGER),
            kind = NotificationKind.REGISTER_EVENT,
            title = title,
            body = body,
            deepLink = "/register/${record.recordId}",
        )
    }

    private fun snapshot(record: RegisterRecord): Map<String, Any?> = mapOf(
        "recordId" to record.recordId,
        "type" to record.type.wire,
        "status" to record.status.wire,
        "outcome" to record.outcome?.wire,
        "personId" to record.person?.id,
        "requirementId" to record.requirement?.id,
        "reqRaw" to record.reqRaw,
        "partnership" to record.partnership.abbrev,
        "ccId" to record.crewChange?.ccId,
        "effectiveFrom" to record.effectiveFrom?.toString(),
        "effectiveTo" to record.effectiveTo?.toString(),
        "approvalFrom" to record.approvalFrom?.toString(),
        "approvalTo" to record.approvalTo?.toString(),
        "raisedDate" to record.raisedDate.toString(),
        "lateSubmissionAcknowledged" to record.lateSubmissionAcknowledged,
        "conditions" to record.conditions.map { mapOf("type" to it.type.wire, "body" to it.body) },
    )

    companion object {
        const val OPEN = "open"
        const val CLOSED = "closed"

        /** Accepted as a synonym for "no filter", so a caller does not have to omit the parameter. */
        const val ALL = "all"

        /** §4.4: notes are attributed to one of the three parties. */
        val PARTIES = listOf("PW", "MRL", "OPS")

        private val READERS = arrayOf(
            Role.CREW_COORDINATOR, Role.WORKFLOW_MANAGER, Role.COMPLIANCE_LEAD,
            Role.DATA_STEWARD, Role.VESSEL_MASTER, Role.SYSTEM_ADMINISTRATOR,
        )

        /** §3: the Crew Coordinator raises exemption requests; OPS also raises its own. */
        private val RAISERS = arrayOf(
            Role.CREW_COORDINATOR, Role.WORKFLOW_MANAGER, Role.SYSTEM_ADMINISTRATOR,
        )

        /** Q14: the Workflow Manager is the sole decision authority. */
        private val DECIDERS = arrayOf(Role.WORKFLOW_MANAGER, Role.SYSTEM_ADMINISTRATOR)
    }
}
