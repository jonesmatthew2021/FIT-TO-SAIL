package au.crewcomp.courses

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * MOB-8 — which course dates are worth offering, and what the line under each one says.
 *
 * Pure, and tested here rather than through a database because these are the rules with the
 * judgement in them: a wrong answer books somebody onto a course they cannot attend, or hides the
 * only date that would have saved their certificate.
 */
@DisplayName("Course offers (MOB-8)")
class CourseOffersTest {

    private val today = LocalDate.of(2026, 7, 29)

    private fun option(
        ref: String,
        starts: LocalDate,
        days: Long = 1,
        seats: Int = 5,
        active: Boolean = true,
    ) = CatalogueOption(
        ref = ref,
        requirementId = 1,
        starts = starts,
        finishes = starts.plusDays(days),
        provider = "Fremantle Marine Training",
        location = "Fremantle",
        durationLabel = "2 days",
        seats = seats,
        active = active,
    )

    @Nested
    @DisplayName("what is offered at all")
    inner class Filtering {

        @Test
        fun `a date that finishes after the expiry cannot resolve it and is not offered`() {
            val offers = CourseOffers.offers(
                options = listOf(
                    option("in-time", today.plusDays(5)),
                    option("too-late", today.plusDays(40)),
                ),
                today = today,
                expiry = today.plusDays(20),
            )

            assertThat(offers.map { it.option.ref }).containsExactly("in-time")
        }

        @Test
        fun `finishing on the expiry date still resolves it`() {
            // Inclusive on purpose: the certificate is renewed that day. The note says so plainly
            // rather than reporting a cheerful "0 days before expiry".
            val offers = CourseOffers.offers(
                options = listOf(option("same-day", today.plusDays(9), days = 1)),
                today = today,
                expiry = today.plusDays(10),
            )

            assertThat(offers).hasSize(1)
            assertThat(offers.single().note).contains("finishes the day it expires")
        }

        @Test
        fun `with no expiry there is no deadline, so every future date qualifies`() {
            // A gap, not a lapse. Somebody who has never held the certificate should see every
            // course, not none — the absence of a deadline is not the absence of dates.
            val offers = CourseOffers.offers(
                options = listOf(option("soon", today.plusDays(5)), option("later", today.plusYears(1))),
                today = today,
                expiry = null,
            )

            assertThat(offers.map { it.option.ref }).containsExactly("soon", "later")
        }

        @Test
        fun `a date inside a swing they are rostered onto is dropped, not labelled`() {
            // They cannot attend from a vessel, and offering it is offering a mistake.
            val offers = CourseOffers.offers(
                options = listOf(option("at-sea", today.plusDays(5))),
                today = today,
                expiry = null,
                rostered = listOf(DateRange(today, today.plusDays(20))),
            )

            assertThat(offers).isEmpty()
        }

        @Test
        fun `a date inside their leave is offered and labelled, never hidden`() {
            // Whether a course is worth a few days off is the crew member's call. Silently hiding
            // the only date that beats an expiry would be a compliance system making a personal
            // decision on somebody's behalf.
            val offers = CourseOffers.offers(
                options = listOf(option("on-leave", today.plusDays(5))),
                today = today,
                expiry = null,
                onLeave = listOf(DateRange(today.plusDays(4), today.plusDays(12))),
            )

            assertThat(offers).hasSize(1)
            assertThat(offers.single().note).isEqualTo("Overlaps your leave")
            assertThat(offers.single().recommended).isFalse()
        }

        @Test
        fun `booking into the past is not an offer`() {
            val offers = CourseOffers.offers(
                options = listOf(option("gone", today.minusDays(1))),
                today = today,
                expiry = null,
            )

            assertThat(offers).isEmpty()
        }

        @Test
        fun `a withdrawn option is not offered`() {
            val offers = CourseOffers.offers(
                options = listOf(option("withdrawn", today.plusDays(5), active = false)),
                today = today,
                expiry = null,
            )

            assertThat(offers).isEmpty()
        }
    }

    @Nested
    @DisplayName("the line under the date")
    inner class Notes {

        @Test
        fun `both halves appear, and the margin is counted from the last day`() {
            val offers = CourseOffers.offers(
                options = listOf(option("fits", today.plusDays(5), days = 2)),
                today = today,
                expiry = today.plusDays(18),
            )

            // Finishes on day 7, expires on day 18 — eleven days of margin.
            assertThat(offers.single().note).isEqualTo("Clear of your leave · 11 days before expiry")
        }

        @Test
        fun `one day reads as one day`() {
            val offers = CourseOffers.offers(
                options = listOf(option("tight", today.plusDays(5), days = 2)),
                today = today,
                expiry = today.plusDays(8),
            )

            assertThat(offers.single().note).endsWith("1 day before expiry")
        }

        @Test
        fun `with no expiry the line is only about leave`() {
            val offers = CourseOffers.offers(
                options = listOf(option("open", today.plusDays(5))),
                today = today,
                expiry = null,
            )

            assertThat(offers.single().note).isEqualTo("Clear of your leave")
        }
    }

    @Nested
    @DisplayName("recommendation and waitlist")
    inner class Ranking {

        @Test
        fun `the soonest date with a seat and no leave clash is recommended, and only it`() {
            val offers = CourseOffers.offers(
                options = listOf(
                    option("third", today.plusDays(20)),
                    option("first", today.plusDays(5)),
                    option("second", today.plusDays(10)),
                ),
                today = today,
                expiry = null,
            )

            assertThat(offers.map { it.option.ref }).containsExactly("first", "second", "third")
            assertThat(offers.filter { it.recommended }.map { it.option.ref }).containsExactly("first")
        }

        @Test
        fun `a full date is offered as a waitlist and is never the recommendation`() {
            // Recommending a waitlist would be recommending a maybe.
            val offers = CourseOffers.offers(
                options = listOf(
                    option("full", today.plusDays(5), seats = 0),
                    option("has-seats", today.plusDays(12)),
                ),
                today = today,
                expiry = null,
            )

            assertThat(offers.single { it.option.ref == "full" }.waitlistOnly).isTrue()
            assertThat(offers.filter { it.recommended }.map { it.option.ref }).containsExactly("has-seats")
        }

        @Test
        fun `a date clashing with leave is skipped for the recommendation but still offered`() {
            val offers = CourseOffers.offers(
                options = listOf(
                    option("on-leave", today.plusDays(5)),
                    option("clear", today.plusDays(30)),
                ),
                today = today,
                expiry = null,
                onLeave = listOf(DateRange(today.plusDays(4), today.plusDays(12))),
            )

            assertThat(offers).hasSize(2)
            assertThat(offers.filter { it.recommended }.map { it.option.ref }).containsExactly("clear")
        }

        @Test
        fun `nothing is recommended when every date is full or on leave`() {
            // No recommendation is an honest answer. Promoting the least-bad option would tell a
            // crew member the system had found them something when it had not.
            val offers = CourseOffers.offers(
                options = listOf(
                    option("full", today.plusDays(5), seats = 0),
                    option("on-leave", today.plusDays(8)),
                ),
                today = today,
                expiry = null,
                onLeave = listOf(DateRange(today.plusDays(7), today.plusDays(12))),
            )

            assertThat(offers).hasSize(2)
            assertThat(offers.none { it.recommended }).isTrue()
        }
    }
}
