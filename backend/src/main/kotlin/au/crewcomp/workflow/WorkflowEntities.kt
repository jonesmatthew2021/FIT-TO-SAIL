package au.crewcomp.workflow

import au.crewcomp.engine.RegisterOutcome
import au.crewcomp.platform.persistence.AuditedEntity
import au.crewcomp.platform.persistence.CreatedEntity
import au.crewcomp.people.Person
import au.crewcomp.reference.CrewChange
import au.crewcomp.reference.CrewPosition
import au.crewcomp.reference.Partnership
import au.crewcomp.reference.Requirement
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

/** Spec §4.4 workflow layer. */

/** Appendix A register types. */
enum class RegisterType(val wire: String) {
    EXEMPTION_REQUEST_PW("Exemption Request - PW"),
    EXEMPTION_REQUEST_OPS("Exemption Request - OPS"),
    EXEMPTION_REQUEST_FOLLOWING_MRL_QUERY("Exemption Request following MRL Query"),
    MRL_QUERY("MRL Query"),
    PW_QUERY("PW Query");

    companion object {
        fun fromWire(wire: String): RegisterType =
            entries.firstOrNull { it.wire == wire } ?: throw IllegalArgumentException("Unknown register type: $wire")
    }
}

/**
 * Appendix A register statuses. `Closed - {outcome}` is enumerated explicitly rather than
 * composed at runtime, so that the set of legal statuses is checkable in one place and matches
 * the database CHECK constraint exactly.
 */
enum class RegisterStatus(val wire: String, val outcome: RegisterOutcome? = null) {
    OPEN_PW("Open - PW"),
    OPEN_MRL("Open - MRL"),
    OPEN_OPS("Open - OPS"),
    COMPLETE_BEFORE_JOINING("Complete before joining"),
    CLOSED_APPROVED("Closed - Approved", RegisterOutcome.APPROVED),
    CLOSED_NOT_APPROVED("Closed - Not Approved", RegisterOutcome.NOT_APPROVED),
    CLOSED_INFO_REQUIRED("Closed - Info Required", RegisterOutcome.INFO_REQUIRED),
    CLOSED_ADMIN_ACTION("Closed - Admin Action", RegisterOutcome.ADMIN_ACTION),
    CLOSED_NOT_REQUIRED("Closed - Not Required", RegisterOutcome.NOT_REQUIRED);

    val isOpen: Boolean get() = this == OPEN_PW || this == OPEN_MRL || this == OPEN_OPS

    companion object {
        fun fromWire(wire: String): RegisterStatus =
            entries.firstOrNull { it.wire == wire } ?: throw IllegalArgumentException("Unknown register status: $wire")

        fun closedWith(outcome: RegisterOutcome): RegisterStatus =
            entries.first { it.outcome == outcome }
    }
}

@Entity
@Table(name = "register_record")
class RegisterRecord : AuditedEntity() {

    /** Business key `{PT}{CCnn}-{seq}`, auto-generated and monotonic per prefix (§4.4). */
    @Column(name = "record_id", nullable = false)
    lateinit var recordId: String

    /**
     * MOB-10: the device queue-entry id that raised this, and its idempotency key. Null when a
     * coordinator raised it, which is every record the register has had until now.
     *
     * Unique where set (V7). That constraint is the mechanism rather than a belt-and-braces: the
     * outbox re-posts an operation it never saw a verdict for, and without it a dropped connection
     * allocates a second business key for one request.
     */
    @Column(name = "crew_op_id")
    var crewOpId: String? = null

    @Column(name = "record_type", nullable = false)
    lateinit var typeValue: String

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    var person: Person? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id")
    var position: CrewPosition? = null

    /** Null where the legacy title never mapped to a catalogue code; see [reqRaw] (§4.4). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requirement_id")
    var requirement: Requirement? = null

    /** Preserves unmapped legacy titles, badged in the UI. Never silently cleaned (§11). */
    @Column(name = "req_raw")
    var reqRaw: String? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partnership_id", nullable = false)
    lateinit var partnership: Partnership

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crew_change_id")
    var crewChange: CrewChange? = null

    @Column(name = "effective_from")
    var effectiveFrom: LocalDate? = null

    @Column(name = "effective_to")
    var effectiveTo: LocalDate? = null

    @Column(name = "raised_date", nullable = false)
    lateinit var raisedDate: LocalDate

    @Column(name = "status", nullable = false)
    lateinit var statusValue: String

    @Column(name = "outcome")
    var outcomeValue: String? = null

    @Column(name = "approval_from")
    var approvalFrom: LocalDate? = null

    @Column(name = "approval_to")
    var approvalTo: LocalDate? = null

    @Column(name = "booking_confirmed_date")
    var bookingConfirmedDate: LocalDate? = null

    @Column(name = "lodgement_date")
    var lodgementDate: LocalDate? = null

    /** Q17: submission after the swing's cutoff is permitted but must be acknowledged. */
    @Column(name = "late_submission_acknowledged", nullable = false)
    var lateSubmissionAcknowledged: Boolean = false

    @OneToMany(mappedBy = "registerRecord", cascade = [CascadeType.ALL], orphanRemoval = true)
    var conditions: MutableList<ApprovalCondition> = mutableListOf()

    @OneToMany(mappedBy = "registerRecord", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("createdAt asc")
    var notes: MutableList<RegisterNote> = mutableListOf()

    /** The human-readable trail shown to users (POC behaviour, kept — §4.4). */
    @OneToMany(mappedBy = "registerRecord", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("ordinal asc")
    var auditEntries: MutableList<RegisterAuditEntry> = mutableListOf()

    var type: RegisterType
        get() = RegisterType.fromWire(typeValue)
        set(value) {
            typeValue = value.wire
        }

    var status: RegisterStatus
        get() = RegisterStatus.fromWire(statusValue)
        set(value) {
            statusValue = value.wire
        }

    var outcome: RegisterOutcome?
        get() = outcomeValue?.let { RegisterOutcome.fromWire(it) }
        set(value) {
            outcomeValue = value?.wire
        }

    val isOpen: Boolean get() = status.isOpen
}

/** Structured conditions (Q16), replacing the POC's free text. */
enum class ConditionType(val wire: String) {
    SUPERVISION("supervision"),
    TIME_LIMIT("time_limit"),
    DUTY_RESTRICTION("duty_restriction"),
    TRAINING_BOOKED("training_booked"),
    OTHER("other");

    companion object {
        fun fromWire(wire: String): ConditionType =
            entries.firstOrNull { it.wire == wire } ?: throw IllegalArgumentException("Unknown condition type: $wire")
    }
}

@Entity
@Table(name = "approval_condition")
class ApprovalCondition : CreatedEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "register_record_id", nullable = false)
    lateinit var registerRecord: RegisterRecord

    @Column(name = "condition_type", nullable = false)
    lateinit var typeValue: String

    @Column(name = "body", nullable = false)
    lateinit var body: String

    var type: ConditionType
        get() = ConditionType.fromWire(typeValue)
        set(value) {
            typeValue = value.wire
        }
}

/** Party-attributed notes: PW / MRL / OPS (§4.4). */
@Entity
@Table(name = "register_note")
class RegisterNote : CreatedEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "register_record_id", nullable = false)
    lateinit var registerRecord: RegisterRecord

    @Column(name = "party", nullable = false)
    lateinit var party: String

    @Column(name = "body", nullable = false)
    lateinit var body: String
}

@Entity
@Table(name = "register_audit_entry")
class RegisterAuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "register_record_id", nullable = false)
    lateinit var registerRecord: RegisterRecord

    @Column(name = "ordinal", nullable = false)
    var ordinal: Int = 0

    @Column(name = "body", nullable = false)
    lateinit var body: String

    @Column(name = "actor", nullable = false)
    lateinit var actor: String

    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Instant = Instant.now()
}

/**
 * §4.4 ExceptionItem — the data-quality worklist. Migration fix-ups, the unknown-holdings chase
 * list, catalogue defects. Anomalies are preserved and flagged, never silently cleaned (§11).
 */
@Entity
@Table(name = "exception_item")
class ExceptionItem : AuditedEntity() {

    @Column(name = "area", nullable = false)
    lateinit var area: String

    @Column(name = "description", nullable = false)
    lateinit var description: String

    @Column(name = "state", nullable = false)
    var state: String = "open"

    @Column(name = "linked_entity_type")
    var linkedEntityType: String? = null

    @Column(name = "linked_entity_id")
    var linkedEntityId: Long? = null

    @Column(name = "resolution_note")
    var resolutionNote: String? = null

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null

    @Column(name = "resolved_by")
    var resolvedBy: String? = null

    val isOpen: Boolean get() = state == "open"
}
