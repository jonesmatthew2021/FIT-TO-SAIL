package au.crewcomp.courses

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * MOB-8's filtering: which of a requirement's course dates are worth offering this crew member,
 * and what the line under each one says.
 *
 * Pure, and separated from [CourseOfferService] for the usual reason — this is the part with the
 * judgement in it, and it should be testable against a handful of dates rather than against a
 * database and a roster. It is also the part that survives the catalogue question: whether the
 * dates come from a table here or a provider's feed, *this* is ours, because every rule below
 * needs the crew member's own roster and expiry.
 *
 * ### The screen only ever lists dates that would work
 *
 * That is the design's phrasing and it is load-bearing. A generic catalogue makes the crew member
 * do the arithmetic — does this finish in time, am I at sea that week — and doing it wrong means
 * booking a course that resolves nothing. So an option that cannot help is not shown greyed out,
 * it is not shown.
 *
 * Two exclusions, and the difference between them is the whole model:
 *
 *  * **At sea is impossible.** An option overlapping a swing the person is rostered onto is
 *    dropped. They cannot attend, and offering it is offering a mistake.
 *  * **On leave is merely unwelcome.** It is kept and *labelled*, because whether a course is
 *    worth a few days of leave is the crew member's call and not this system's. Silently hiding
 *    the only date that beats an expiry, on the grounds that it falls in someone's time off, would
 *    be a compliance system making a personal decision on their behalf.
 */
object CourseOffers {

    /**
     * @param options the catalogue's dates for one requirement, any order.
     * @param today the operating timezone's date (NFR-5) — never the device's.
     * @param expiry when the crew member's certificate lapses, or null for a gap. With no expiry
     *   there is no deadline to beat, so every future date qualifies.
     * @param rostered swings this person is committed to. Overlapping options are dropped.
     * @param onLeave leave that still stands. Overlapping options are kept and labelled.
     */
    fun offers(
        options: List<CatalogueOption>,
        today: LocalDate,
        expiry: LocalDate?,
        rostered: List<DateRange> = emptyList(),
        onLeave: List<DateRange> = emptyList(),
    ): List<CourseOffer> {
        val viable = options
            .asSequence()
            .filter { it.active }
            // Booking into the past is not an offer. The catalogue query filters this too; doing it
            // again here costs nothing and means the rule is stated where it can be tested.
            .filter { !it.starts.isBefore(today) }
            // "Resolves the requirement before it lapses". Finishing *on* the expiry date still
            // resolves it that day, so the comparison is inclusive — and the note below says so
            // plainly rather than reporting a cheerful "0 days before expiry".
            .filter { expiry == null || !it.finishes.isAfter(expiry) }
            .filter { option -> rostered.none { it.overlaps(option.starts, option.finishes) } }
            .sortedWith(compareBy({ it.starts }, { it.ref }))
            .toList()

        // Exactly one recommendation, and only if one deserves it: the soonest date that has a
        // seat and does not eat into the person's leave. Recommending a waitlist would be
        // recommending a maybe.
        val recommended = viable.firstOrNull { it.seats > 0 && onLeave.none { l -> l.overlaps(it.starts, it.finishes) } }

        return viable.map { option ->
            val clashesWithLeave = onLeave.any { it.overlaps(option.starts, option.finishes) }
            CourseOffer(
                option = option,
                note = note(option, expiry, clashesWithLeave),
                recommended = option.ref == recommended?.ref,
                waitlistOnly = option.seats <= 0,
            )
        }
    }

    /**
     * The sentence under the date — "Clear of your leave · 11 days before expiry".
     *
     * Both halves are facts the crew member cannot easily check on a phone at sea, which is the
     * only reason this line exists. It is composed here rather than on the device because both
     * need the roster.
     */
    private fun note(option: CatalogueOption, expiry: LocalDate?, clashesWithLeave: Boolean): String {
        val parts = mutableListOf(
            if (clashesWithLeave) "Overlaps your leave" else "Clear of your leave",
        )
        if (expiry != null) {
            parts += when (val margin = ChronoUnit.DAYS.between(option.finishes, expiry)) {
                0L -> "finishes the day it expires"
                1L -> "1 day before expiry"
                else -> "$margin days before expiry"
            }
        }
        return parts.joinToString(" · ")
    }
}

/** An inclusive calendar-date range — a swing, or a stretch of leave. */
data class DateRange(val from: LocalDate, val to: LocalDate) {
    fun overlaps(otherFrom: LocalDate, otherTo: LocalDate): Boolean =
        !from.isAfter(otherTo) && !to.isBefore(otherFrom)
}

/** One catalogue date, judged against one crew member. */
data class CourseOffer(
    val option: CatalogueOption,
    val note: String,
    val recommended: Boolean,
    /** Seats exhausted. The date is still offered — joining a waitlist is a real thing to do. */
    val waitlistOnly: Boolean,
)
