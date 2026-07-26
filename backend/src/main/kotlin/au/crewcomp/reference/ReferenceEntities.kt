package au.crewcomp.reference

import au.crewcomp.engine.Shift
import au.crewcomp.platform.persistence.AuditedEntity
import au.crewcomp.platform.persistence.CreatedEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.LocalDate

/**
 * Spec §4.1 reference layer.
 *
 * Enumerated columns are stored as their Appendix A wire strings and exposed through typed
 * accessors; the string field is what Hibernate maps, the typed property is what code uses.
 */

@Entity
@Table(name = "partnership")
class Partnership : AuditedEntity() {

    @Column(name = "abbrev", nullable = false)
    lateinit var abbrev: String

    @Column(name = "name", nullable = false)
    lateinit var name: String

    /** Nullable; drives the tier review rule (§4.1, §5.1 step 3). */
    @Column(name = "vessel_class")
    var vesselClass: String? = null
}

@Entity
@Table(name = "vessel")
class Vessel : AuditedEntity() {

    @Column(name = "name", nullable = false)
    lateinit var name: String

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partnership_id", nullable = false)
    lateinit var partnership: Partnership

    @Column(name = "kind", nullable = false)
    lateinit var kind: String
}

/**
 * A crew position (Master, Chief Officer, …).
 *
 * The table is `crew_position` rather than `position`: `position` is a SQL keyword, and a table
 * name that needs quoting in generated SQL is a trap that costs more than the shorter name saves.
 */
@Entity
@Table(name = "crew_position")
class CrewPosition : AuditedEntity() {

    @Column(name = "name", nullable = false)
    lateinit var name: String
}

@Entity
@Table(name = "crew_position_tier")
class PositionTier : AuditedEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "position_id", nullable = false)
    lateinit var position: CrewPosition

    @Column(name = "name", nullable = false)
    lateinit var name: String
}

@Entity
@Table(name = "position_slot")
class PositionSlot : AuditedEntity() {

    @Column(name = "ref", nullable = false)
    var ref: Int = 0

    @Column(name = "shift", nullable = false)
    lateinit var shiftValue: String

    @Column(name = "notes")
    var notes: String? = null

    /** Slots 15/16 accept AE **or** GPH (§4.1). */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "position_slot_allowed_position",
        joinColumns = [JoinColumn(name = "slot_id")],
        inverseJoinColumns = [JoinColumn(name = "position_id")],
    )
    var allowedPositions: MutableSet<CrewPosition> = mutableSetOf()

    var shift: Shift
        get() = Shift.fromWire(shiftValue)
        set(value) {
            shiftValue = value.wire
        }
}

@Entity
@Table(name = "crew_change")
class CrewChange : AuditedEntity() {

    @Column(name = "cc_id", nullable = false)
    lateinit var ccId: String

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partnership_id", nullable = false)
    lateinit var partnership: Partnership

    @Column(name = "from_date", nullable = false)
    lateinit var fromDate: LocalDate

    @Column(name = "to_date", nullable = false)
    lateinit var toDate: LocalDate

    /** Stored rather than derived from `from - 7 days`, so exceptions can exist (§4.1, Q17). */
    @Column(name = "cutoff_date", nullable = false)
    lateinit var cutoffDate: LocalDate
}

@Entity
@Table(name = "requirement")
class Requirement : AuditedEntity() {

    @Column(name = "code", nullable = false)
    lateinit var code: String

    @Column(name = "category", nullable = false)
    lateinit var category: String

    @Column(name = "title", nullable = false)
    lateinit var title: String

    @Column(name = "status", nullable = false)
    var status: String = "active"

    @Column(name = "issuing_authority")
    var issuingAuthority: String? = null

    @Column(name = "notes")
    var notes: String? = null

    /** Legacy free-text titles, for mapping register history (§4.1). */
    @OneToMany(mappedBy = "requirement", cascade = [CascadeType.ALL], orphanRemoval = true)
    var aliases: MutableList<RequirementAlias> = mutableListOf()

    val isActive: Boolean get() = status == "active"
}

@Entity
@Table(name = "requirement_alias")
class RequirementAlias : CreatedEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requirement_id", nullable = false)
    lateinit var requirement: Requirement

    @Column(name = "alias", nullable = false)
    lateinit var alias: String
}
