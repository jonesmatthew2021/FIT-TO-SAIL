package au.crewcomp.courses

import au.crewcomp.platform.persistence.AuditedEntity
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import au.crewcomp.reference.Requirement
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * MOB-8's course dates: the port, its value type, and the adapter that reads them from this
 * database.
 *
 * ### The seam, and why it is here
 *
 * A course catalogue is **not in the §4 domain model**. It may be a table we maintain, or a
 * provider's feed, or eventually both — and that question is genuinely open. What is not open is
 * that the interesting half of MOB-8 belongs to us regardless of who supplies the dates: an option
 * is only worth showing if it finishes before the crew member's certificate lapses and does not
 * fall inside a swing they are rostered onto, and both facts come from *our* roster. So the shape
 * this file fixes is "somewhere there are dates for a requirement", and everything downstream —
 * the filtering, the ordering, the sentence under each date — is [CourseOfferService]'s and stays
 * put whichever way the question resolves.
 *
 * This is the same posture as the cloud adapters: a thin port now so that the decision, when it is
 * taken, changes one class rather than a screen's worth of behaviour.
 *
 * ### An offer, never an inventory
 *
 * [CatalogueOption.seats] is what the provider last told us. Nothing in this system decrements it,
 * no request holds a place, and a crew member choosing a date raises a `crew_statement` for a
 * coordinator to action rather than a booking. The system has no contract with the provider and
 * must not behave as though it did — a screen that said "seat reserved" on the strength of this
 * number would be lying to someone who then does not turn up to a course.
 */
interface CourseCatalogue {

    /**
     * Every current option for [requirementId] starting on or after [notBefore], in date order.
     *
     * Deliberately **not** filtered by person: this port knows nothing about crew, rosters or
     * expiries, and a provider feed could not answer such a question anyway. The per-person
     * filtering is [CourseOfferService]'s, which is where it can be tested without a catalogue.
     */
    fun optionsFor(requirementId: Long, notBefore: LocalDate): List<CatalogueOption>

    /** One option by its business key, or null — including inactive ones, so a past ask resolves. */
    fun byRef(optionRef: String): CatalogueOption?
}

/**
 * One course date, as the catalogue knows it.
 *
 * A value type rather than the entity, so that nothing above the adapter can accidentally depend
 * on there being a row — or lazily load one after its transaction closed.
 */
data class CatalogueOption(
    /** The business key. What a device holds and what a crew statement points at. */
    val ref: String,
    val requirementId: Long,
    val starts: LocalDate,
    val finishes: LocalDate,
    val provider: String,
    val location: String,
    /** The provider's own phrasing — "2 days", "1 day refresher". Never derived from the dates. */
    val durationLabel: String?,
    /** What the provider last said. Zero is a real answer meaning waitlist-only, not "unknown". */
    val seats: Int,
    val active: Boolean,
) {
    /**
     * The option as one line, for a record that has to outlive it.
     *
     * Rendered here rather than on the device for the same reason MOB-9's signature line is: this
     * string ends up in the office's record of what a crew member asked for, and what appears there
     * should not be composed on the phone of the person asking.
     *
     * Never ISO. `08-09` and `09-08` are two different days to two people reading the same queue,
     * and this string is stored — a coordinator opening the request in three weeks gets whatever
     * was written here and no chance to re-render it.
     */
    val label: String
        get() = "${dates()} · $provider, $location"

    /** `20–21 Aug 2026`, or `28 Aug – 2 Sep 2026` when the course crosses a month. */
    private fun dates(): String = when {
        starts == finishes -> DAY_MONTH_YEAR.format(starts)
        starts.month == finishes.month && starts.year == finishes.year ->
            "${starts.dayOfMonth}–${DAY_MONTH_YEAR.format(finishes)}"
        else -> "${DAY_MONTH.format(starts)} – ${DAY_MONTH_YEAR.format(finishes)}"
    }

    private companion object {
        val DAY_MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH)
        val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    }
}

@Entity
@Table(name = "course_option")
class CourseOptionEntity : AuditedEntity() {

    @Column(name = "option_ref", nullable = false)
    lateinit var optionRef: String

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requirement_id", nullable = false)
    lateinit var requirement: Requirement

    @Column(name = "starts", nullable = false)
    lateinit var starts: LocalDate

    @Column(name = "finishes", nullable = false)
    lateinit var finishes: LocalDate

    @Column(name = "provider", nullable = false)
    lateinit var provider: String

    @Column(name = "location", nullable = false)
    lateinit var location: String

    @Column(name = "duration_label")
    var durationLabel: String? = null

    @Column(name = "seats", nullable = false)
    var seats: Int = 0

    @Column(name = "active", nullable = false)
    var active: Boolean = true

    fun toCatalogueOption() = CatalogueOption(
        ref = optionRef,
        requirementId = requirement.requiredId,
        starts = starts,
        finishes = finishes,
        provider = provider,
        location = location,
        durationLabel = durationLabel,
        seats = seats,
        active = active,
    )
}

@ApplicationScoped
class CourseOptionRepository : PanacheRepositoryBase<CourseOptionEntity, Long> {

    fun byRef(optionRef: String): CourseOptionEntity? = find("optionRef", optionRef).firstResult()

    fun currentFor(requirementId: Long, notBefore: LocalDate): List<CourseOptionEntity> = find(
        "from CourseOptionEntity o join fetch o.requirement " +
            "where o.requirement.id = ?1 and o.active = true and o.starts >= ?2 " +
            "order by o.starts",
        requirementId, notBefore,
    ).list()

    fun allOrdered(): List<CourseOptionEntity> = find(
        "from CourseOptionEntity o join fetch o.requirement order by o.starts, o.optionRef",
    ).list()
}

/**
 * The only implementation today: the catalogue is a table in this database.
 *
 * Unscoped reads, and correctly so — a course date is reference data like a requirement or a
 * position, not person-scoped data. Who may *ask* is settled above; there is nothing here that one
 * crew member could learn about another.
 */
@ApplicationScoped
class DatabaseCourseCatalogue(private val options: CourseOptionRepository) : CourseCatalogue {

    override fun optionsFor(requirementId: Long, notBefore: LocalDate): List<CatalogueOption> =
        options.currentFor(requirementId, notBefore).map { it.toCatalogueOption() }

    /**
     * Inactive rows included on purpose. This resolves the option a crew member asked for weeks
     * ago, and a withdrawn course is exactly the case where the coordinator most needs to see
     * which date they meant.
     */
    override fun byRef(optionRef: String): CatalogueOption? =
        options.byRef(optionRef)?.toCatalogueOption()
}
