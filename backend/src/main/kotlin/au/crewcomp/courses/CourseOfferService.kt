package au.crewcomp.courses

import au.crewcomp.engine.HoldingStatus
import au.crewcomp.people.AssignmentRepository
import au.crewcomp.people.LeaveRecordRepository
import au.crewcomp.people.QualificationHoldingRepository
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.time.BusinessClock
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

/**
 * MOB-8's dates for one crew member — the catalogue joined against their roster and their expiry.
 *
 * This is the half of the course question that is ours whoever supplies the dates, which is why
 * [CourseCatalogue] is a port and this is not. The judgement lives in [CourseOffers]; this class
 * only gathers what that needs, and every read it makes is already person-scoped (AUTH-2).
 *
 * ### Why the offers travel in the sync payload
 *
 * They are recomputed on every snapshot and every delta, exactly like `standing`, and for the same
 * reason: an offer is a *derived* answer, not a row. It changes when the catalogue changes, when
 * the person's roster changes, when their certificate is renewed, and when a day passes — and a
 * client applying row-level deltas would show a stale set indefinitely after any of those. There
 * is deliberately no cursor and no tombstone for a course offer; the whole set is replaced each
 * time, and being wrong in the "sent too much" direction costs a few kilobytes.
 */
@ApplicationScoped
class CourseOfferService(
    private val catalogue: CourseCatalogue,
    private val holdings: QualificationHoldingRepository,
    private val assignments: AssignmentRepository,
    private val leaveRecords: LeaveRecordRepository,
    private val policy: AccessPolicy,
    private val clock: BusinessClock,
) {

    /**
     * Every offer worth showing this person across [requirementIds], in requirement then date
     * order.
     *
     * @param requirementIds the requirements they actually need something for. The caller decides
     *   this — for sync it is the cells the engine says need attention — because offering courses
     *   against a certificate somebody already holds is noise, and against all of them is the
     *   generic catalogue the design rules out.
     */
    @Transactional
    fun forPerson(personId: Long, requirementIds: Collection<Long>): List<CourseOffer> {
        if (requirementIds.isEmpty()) return emptyList()
        policy.assertCanSeePerson(personId)

        val today = clock.today()

        // Gathered once for the whole call rather than per requirement: both are short lists for
        // one person, and re-reading them per cell would multiply the queries by the gap count.
        val rostered = assignments.forPersonScoped(personId)
            .map { DateRange(it.fromDate, it.toDate) }
        val onLeave = leaveRecords.forPersonScoped(personId)
            .filter { it.status in LeaveRecordRepository.STANDING_STATUSES }
            .map { DateRange(it.fromDate, it.toDate) }

        return requirementIds.distinct().flatMap { requirementId ->
            CourseOffers.offers(
                options = catalogue.optionsFor(requirementId, today),
                today = today,
                expiry = expiryFor(personId, requirementId),
                rostered = rostered,
                onLeave = onLeave,
            )
        }
    }

    /**
     * One option by the key a device named, for the write path.
     *
     * Deliberately not filtered by anything: a crew statement records what was asked for, and by
     * the time a coordinator reads it the option may have been withdrawn. Returning null then
     * would lose the only description of what they wanted.
     */
    fun option(optionRef: String): CatalogueOption? = catalogue.byRef(optionRef)

    /**
     * The date this requirement lapses for this person, or null.
     *
     * Only a `held_expiry` holding has one. A gap gives null, and null means "no deadline to beat"
     * rather than "no dates" — someone who has never held the certificate should see every future
     * course, not none.
     */
    private fun expiryFor(personId: Long, requirementId: Long) =
        holdings.find(personId, requirementId)
            ?.takeIf { it.status == HoldingStatus.HELD_EXPIRY }
            ?.expiryDate
}
