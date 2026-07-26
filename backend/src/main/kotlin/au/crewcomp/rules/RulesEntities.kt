package au.crewcomp.rules

import au.crewcomp.engine.QuotaScope
import au.crewcomp.engine.RuleLevel
import au.crewcomp.platform.persistence.AuditedEntity
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
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

/** Spec §4.2 rules layer. */

enum class MatrixStatus(val wire: String) {
    DRAFT("draft"),
    PUBLISHED("published"),
    SUPERSEDED("superseded");

    companion object {
        fun fromWire(wire: String): MatrixStatus =
            entries.firstOrNull { it.wire == wire } ?: throw IllegalArgumentException("Unknown matrix status: $wire")
    }
}

@Entity
@Table(name = "matrix_version")
class MatrixVersion : AuditedEntity() {

    @Column(name = "label", nullable = false)
    lateinit var label: String

    @Column(name = "status", nullable = false)
    var statusValue: String = MatrixStatus.DRAFT.wire

    @Column(name = "effective_from")
    var effectiveFrom: LocalDate? = null

    @Column(name = "published_by")
    var publishedBy: String? = null

    @Column(name = "published_at")
    var publishedAt: Instant? = null

    @Column(name = "notes")
    var notes: String? = null

    /**
     * The footnote label this version uses for the tier review rule, or null if it defines none.
     * Per-version because footnote labels are data, not code (§4.2).
     */
    @Column(name = "tier_footnote")
    var tierFootnote: String? = null

    @OneToMany(mappedBy = "matrixVersion", cascade = [CascadeType.ALL], orphanRemoval = true)
    var tierPolicies: MutableList<MatrixTierPolicy> = mutableListOf()

    var status: MatrixStatus
        get() = MatrixStatus.fromWire(statusValue)
        set(value) {
            statusValue = value.wire
        }

    val isEditable: Boolean get() = status == MatrixStatus.DRAFT
}

/**
 * Vessel class → a tier accepted on that class, for the tier review rule (§5.1 step 3).
 * An absent mapping means "send to human review": Mˣ is never auto-resolved (Q6).
 */
@Entity
@Table(name = "matrix_tier_policy")
class MatrixTierPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "matrix_version_id", nullable = false)
    lateinit var matrixVersion: MatrixVersion

    @Column(name = "vessel_class", nullable = false)
    lateinit var vesselClass: String

    @Column(name = "accepted_tier", nullable = false)
    lateinit var acceptedTier: String
}

@Entity
@Table(name = "requirement_rule")
class RequirementRule : AuditedEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "matrix_version_id", nullable = false)
    lateinit var matrixVersion: MatrixVersion

    /** Null = the base (`'*'`) rule that applies to every partnership (§4.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partnership_id")
    var partnership: Partnership? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "position_id", nullable = false)
    lateinit var position: CrewPosition

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requirement_id", nullable = false)
    lateinit var requirement: Requirement

    /** 'M', a footnote label, 'R', or '' (an override that removes the requirement). */
    @Column(name = "level", nullable = false)
    var levelValue: String = ""

    var level: RuleLevel
        get() = RuleLevel(levelValue)
        set(value) {
            levelValue = value.label
        }
}

enum class ConditionalKind(val wire: String) {
    ONE_OF("one_of"),
    DEPENDENT("dependent");

    companion object {
        fun fromWire(wire: String): ConditionalKind =
            entries.firstOrNull { it.wire == wire } ?: throw IllegalArgumentException("Unknown conditional kind: $wire")
    }
}

enum class ConditionalMemberRole(val wire: String) {
    MEMBER("member"),
    REQUIRED_IF_HOLDS("required_if_holds"),
    UNLESS_HOLDS("unless_holds");

    companion object {
        fun fromWire(wire: String): ConditionalMemberRole =
            entries.firstOrNull { it.wire == wire } ?: throw IllegalArgumentException("Unknown member role: $wire")
    }
}

@Entity
@Table(name = "conditional_rule")
class ConditionalRule : AuditedEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "matrix_version_id", nullable = false)
    lateinit var matrixVersion: MatrixVersion

    @Column(name = "kind", nullable = false)
    lateinit var kindValue: String

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "position_id", nullable = false)
    lateinit var position: CrewPosition

    /** The target requirement for `dependent` rules; null for `one_of`. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requirement_id")
    var requirement: Requirement? = null

    /** Display label only, e.g. the CoC set's M⁸. Never interpreted (§4.2). */
    @Column(name = "label")
    var label: String? = null

    @OneToMany(mappedBy = "conditionalRule", cascade = [CascadeType.ALL], orphanRemoval = true)
    var members: MutableList<ConditionalRuleMember> = mutableListOf()

    var kind: ConditionalKind
        get() = ConditionalKind.fromWire(kindValue)
        set(value) {
            kindValue = value.wire
        }

    fun requirementsWithRole(role: ConditionalMemberRole): List<Requirement> =
        members.filter { it.role == role }.sortedBy { it.ordinal }.map { it.requirement }
}

@Entity
@Table(name = "conditional_rule_member")
class ConditionalRuleMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conditional_rule_id", nullable = false)
    lateinit var conditionalRule: ConditionalRule

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requirement_id", nullable = false)
    lateinit var requirement: Requirement

    @Column(name = "role", nullable = false)
    lateinit var roleValue: String

    @Column(name = "ordinal", nullable = false)
    var ordinal: Int = 0

    var role: ConditionalMemberRole
        get() = ConditionalMemberRole.fromWire(roleValue)
        set(value) {
            roleValue = value.wire
        }
}

@Entity
@Table(name = "quota_rule")
class QuotaRule : AuditedEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "matrix_version_id", nullable = false)
    lateinit var matrixVersion: MatrixVersion

    /** Display label. Behaviour is keyed off this record, never off the text (§4.2). */
    @Column(name = "footnote", nullable = false)
    lateinit var footnote: String

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requirement_id", nullable = false)
    lateinit var requirement: Requirement

    @Column(name = "min_count", nullable = false)
    var minCount: Int = 0

    @Column(name = "scope", nullable = false)
    lateinit var scopeValue: String

    /** Empty = the quota counts people in any position (§4.2). */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "quota_rule_position",
        joinColumns = [JoinColumn(name = "quota_rule_id")],
        inverseJoinColumns = [JoinColumn(name = "position_id")],
    )
    var positions: MutableSet<CrewPosition> = mutableSetOf()

    var scope: QuotaScope
        get() = QuotaScope.entries.first { it.wire == scopeValue }
        set(value) {
            scopeValue = value.wire
        }
}
