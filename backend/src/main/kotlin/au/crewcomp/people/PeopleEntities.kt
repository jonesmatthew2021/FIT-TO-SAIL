package au.crewcomp.people

import au.crewcomp.engine.HoldingStatus
import au.crewcomp.engine.PersonStatus
import au.crewcomp.platform.persistence.AuditedEntity
import au.crewcomp.platform.security.Role
import au.crewcomp.reference.CrewChange
import au.crewcomp.reference.CrewPosition
import au.crewcomp.reference.Partnership
import au.crewcomp.reference.Requirement
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

/** Spec §4.3 people layer. */

@Entity
@Table(name = "person")
class Person : AuditedEntity() {

    /**
     * Legacy business key. Deliberately **not** unique: the source data contains one known
     * historical duplicate, preserved as an ExceptionItem rather than silently cleaned
     * (§4.3, §11).
     */
    @Column(name = "sam", nullable = false)
    lateinit var sam: String

    @Column(name = "name", nullable = false)
    lateinit var name: String

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "position_id", nullable = false)
    lateinit var position: CrewPosition

    @Column(name = "tier")
    var tier: String? = null

    /** Home partnership; a person may be assigned across partnerships (a scored penalty, §5.4). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partnership_id", nullable = false)
    lateinit var partnership: Partnership

    @Column(name = "status", nullable = false)
    var statusValue: String = PersonStatus.ACTIVE.wire

    @Column(name = "email")
    var email: String? = null

    @Column(name = "mobile")
    var mobile: String? = null

    /** The crew the person sails with on the ship's rotation — 'A' or 'B' — or null (V14). */
    @Column(name = "rotation")
    var rotation: String? = null

    /**
     * §10.3 sync high-water mark, assigned by the `person_sync_seq` database trigger.
     *
     * Read-only to Hibernate on purpose: the trigger is what makes the cursor impossible for a
     * write path to forget (V2__sync_change_tracking.sql). The consequence is that the value on
     * a just-persisted instance is stale until the row is refreshed — nothing should read it
     * except a sync query, which loads the row fresh.
     */
    @Column(name = "updated_seq", insertable = false, updatable = false)
    var updatedSeq: Long = 0

    var status: PersonStatus
        get() = PersonStatus.entries.first { it.wire == statusValue }
        set(value) {
            statusValue = value.wire
        }
}

/**
 * SEC-1a — the administrator-managed allow-list of corporate identity backends. Only issuers
 * present and enabled here may authenticate anyone; onboarding one is an audited administrative
 * act (ADR 0003).
 */
@Entity
@Table(name = "identity_provider")
class IdentityProvider : AuditedEntity() {

    @Column(name = "provider", nullable = false)
    lateinit var provider: String

    /** The exact issuer URL. Entra: per-tenant only, never `/common` (ADR 0003). */
    @Column(name = "issuer", nullable = false)
    lateinit var issuer: String

    /** Entra tenant id, Google Workspace `hd`, or Okta org — the allow-list key. */
    @Column(name = "tenant_or_domain", nullable = false)
    lateinit var tenantOrDomain: String

    @Column(name = "display_name", nullable = false)
    lateinit var displayName: String

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = false
}

enum class UserAccountKind(val wire: String) {
    CORPORATE("corporate"),

    /** SEC-1b: transitional only, flagged so it is enumerable and removable. */
    LOCAL_TEST("local_test");

    companion object {
        fun fromWire(wire: String): UserAccountKind =
            entries.firstOrNull { it.wire == wire } ?: throw IllegalArgumentException("Unknown account kind: $wire")
    }
}

/**
 * §4.3 UserAccount — holds the **external identity linkage only**. No credentials, ever (SEC-1).
 */
@Entity
@Table(name = "user_account")
class UserAccount : AuditedEntity() {

    /** 1:1 with Person for crew members; back-office users may have none (§4.3). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    var person: Person? = null

    @Column(name = "kind", nullable = false)
    var kindValue: String = UserAccountKind.CORPORATE.wire

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "identity_provider_id")
    var identityProvider: IdentityProvider? = null

    @Column(name = "issuer")
    var issuer: String? = null

    @Column(name = "subject")
    var subject: String? = null

    @Column(name = "display_name", nullable = false)
    lateinit var displayName: String

    @Column(name = "email")
    var email: String? = null

    @Column(name = "status", nullable = false)
    var status: String = "active"

    @Column(name = "last_login_at")
    var lastLoginAt: Instant? = null

    /**
     * AUTH-4 — role assignments are themselves audited, so the grant carries who made it and
     * when. That is why this is a collection of [RoleAssignment] rather than of bare strings:
     * `user_account_role.granted_by` is `not null`, and a mapping that only knew about the role
     * column could not insert a row at all. (It could not, and did not: this was unreachable
     * until the crew-account fixture tried to use it.)
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_account_role", joinColumns = [JoinColumn(name = "user_account_id")])
    var roleAssignments: MutableSet<RoleAssignment> = mutableSetOf()

    /** Vessel Master scoping (§3). Empty for every other role. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "user_account_partnership_scope",
        joinColumns = [JoinColumn(name = "user_account_id")],
    )
    @Column(name = "partnership_id")
    var scopedPartnershipIds: MutableSet<Long> = mutableSetOf()

    var kind: UserAccountKind
        get() = UserAccountKind.fromWire(kindValue)
        set(value) {
            kindValue = value.wire
        }

    val roles: Set<Role> get() = roleAssignments.map { Role.fromWire(it.role) }.toSet()

    val isActive: Boolean get() = status == "active"

    /** Grants [role], recording the granting actor (AUTH-4). Re-granting is a no-op. */
    fun grantRole(role: Role, grantedBy: String, at: Instant = Instant.now()) {
        roleAssignments.add(
            RoleAssignment().apply {
                this.role = role.wire
                this.grantedBy = grantedBy
                this.grantedAt = at
            },
        )
    }
}

/**
 * One row of `user_account_role`.
 *
 * Equality is on [role] alone, matching the table's primary key of (user_account_id, role): the
 * same role granted twice is one assignment, not two, and Hibernate needs that agreement to keep
 * a Set-valued element collection stable across a flush.
 */
@Embeddable
class RoleAssignment {

    @Column(name = "role", nullable = false)
    lateinit var role: String

    @Column(name = "granted_at", nullable = false)
    var grantedAt: Instant = Instant.now()

    /** AUTH-4: who granted it. Denormalised for the same self-containment reason as audit rows. */
    @Column(name = "granted_by", nullable = false)
    lateinit var grantedBy: String

    override fun equals(other: Any?): Boolean =
        this === other || (other is RoleAssignment && role == other.role)

    override fun hashCode(): Int = role.hashCode()
}

@Entity
@Table(name = "qualification_holding")
class QualificationHolding : AuditedEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    lateinit var person: Person

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requirement_id", nullable = false)
    lateinit var requirement: Requirement

    @Column(name = "status", nullable = false)
    var statusValue: String = HoldingStatus.UNKNOWN.wire

    /** Required iff status is `held_expiry`; a calendar date, never a timestamp (NFR-5). */
    @Column(name = "expiry_date")
    var expiryDate: LocalDate? = null

    @Column(name = "issue_date")
    var issueDate: LocalDate? = null

    @Column(name = "note")
    var note: String? = null

    /** §10.3 sync cursor, trigger-assigned — see [Person.updatedSeq]. */
    @Column(name = "updated_seq", insertable = false, updatable = false)
    var updatedSeq: Long = 0

    var status: HoldingStatus
        get() = HoldingStatus.fromWire(statusValue)
        set(value) {
            statusValue = value.wire
        }
}

@Entity
@Table(name = "assignment")
class Assignment : AuditedEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    lateinit var person: Person

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crew_change_id", nullable = false)
    lateinit var crewChange: CrewChange

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partnership_id", nullable = false)
    lateinit var partnership: Partnership

    @Column(name = "slot_ref", nullable = false)
    var slotRef: Int = 0

    /** May be a sub-range of the swing: mid-swing handovers put two people in one slot (§4.3). */
    @Column(name = "from_date", nullable = false)
    lateinit var fromDate: LocalDate

    @Column(name = "to_date", nullable = false)
    lateinit var toDate: LocalDate

    /** §10.3 sync cursor, trigger-assigned — see [Person.updatedSeq]. */
    @Column(name = "updated_seq", insertable = false, updatable = false)
    var updatedSeq: Long = 0
}

@Entity
@Table(name = "leave_record")
class LeaveRecord : AuditedEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    lateinit var person: Person

    @Column(name = "kind", nullable = false)
    lateinit var kind: String

    @Column(name = "from_date", nullable = false)
    lateinit var fromDate: LocalDate

    @Column(name = "to_date", nullable = false)
    lateinit var toDate: LocalDate

    @Column(name = "status", nullable = false)
    var status: String = "recorded"

    /** Headroom for O-8: the external key if leave becomes an HR/payroll mirror. */
    @Column(name = "external_ref")
    var externalRef: String? = null

    /** §10.3 sync cursor, trigger-assigned — see [Person.updatedSeq]. */
    @Column(name = "updated_seq", insertable = false, updatable = false)
    var updatedSeq: Long = 0
}
