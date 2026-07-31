package au.crewcomp.engine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** Spec §5.4 — crew suggestions, gap report, expiry alerts. */
@DisplayName("§5.4 suggestions, gap report and expiry alerts")
class PlanningTest {

    private val swing = Fx.SWING
    private val slot = Fx.slot(15, Shift.SHIFT_1, Fx.AE, Fx.GPH)
    private val matrix = Fx.matrix(
        rules = listOf(
            Fx.rule(Fx.GPH, Fx.WAH, "M"),
            Fx.rule(Fx.GPH, Fx.MED, "M"),
        ),
    )

    private fun inputs(
        assignments: List<AssignmentView> = emptyList(),
        people: List<PersonView> = emptyList(),
        holdings: Map<PersonId, Map<RequirementId, HoldingView>> = emptyMap(),
    ) = SwingEvaluationInputs(
        swing = swing,
        partnership = Fx.NOR,
        matrix = matrix,
        slots = listOf(slot),
        assignments = assignments,
        people = people.associateBy { it.id },
        holdings = holdings,
    )

    @Nested
    @DisplayName("crew suggestions")
    inner class Suggestions {

        private val clean = Fx.person(1, name = "Clean", position = Fx.GPH)
        private val oneGap = Fx.person(2, name = "One Gap", position = Fx.GPH)
        private val oneUnknown = Fx.person(3, name = "One Unknown", position = Fx.GPH)
        private val expiring = Fx.person(4, name = "Expiring", position = Fx.GPH)

        private val holdings = mapOf(
            clean.id to Fx.holdings(Fx.perpetual(Fx.WAH), Fx.perpetual(Fx.MED)),
            oneGap.id to Fx.holdings(Fx.notHeld(Fx.WAH), Fx.perpetual(Fx.MED)),
            oneUnknown.id to Fx.holdings(Fx.unknown(Fx.WAH), Fx.perpetual(Fx.MED)),
            expiring.id to Fx.holdings(Fx.held(Fx.WAH, swing.to.minusDays(1)), Fx.perpetual(Fx.MED)),
        )

        private val candidates = listOf(clean, oneGap, oneUnknown, expiring)

        @Test
        fun `candidates are ranked ascending by penalty score`() {
            val suggestions = Planning.suggestCrew(slot, inputs(people = candidates, holdings = holdings), candidates)

            assertThat(suggestions.map { it.person.name })
                .containsExactly("Clean", "Expiring", "One Unknown", "One Gap")
            assertThat(suggestions.map { it.score }).containsExactly(0, 5, 10, 100)
        }

        @Test
        fun `people whose position does not fit the slot are excluded`() {
            val master = Fx.person(9, name = "Master", position = Fx.MASTER)
            val suggestions = Planning.suggestCrew(
                slot, inputs(people = candidates + master, holdings = holdings), candidates + master,
            )

            assertThat(suggestions.map { it.person.name }).doesNotContain("Master")
        }

        @Test
        fun `inactive and lookup-only people are excluded`() {
            val inactive = Fx.person(10, name = "Inactive", position = Fx.GPH, status = PersonStatus.INACTIVE)
            val lookup = Fx.person(11, name = "Lookup", position = Fx.GPH, status = PersonStatus.LOOKUP_ONLY)
            val all = candidates + inactive + lookup

            val suggestions = Planning.suggestCrew(slot, inputs(people = all, holdings = holdings), all)

            assertThat(suggestions.map { it.person.name }).doesNotContain("Inactive", "Lookup")
        }

        @Test
        fun `people already assigned to this swing are excluded`() {
            val assigned = inputs(
                assignments = listOf(Fx.assignment(1, clean.id, slotRef = 16)),
                people = candidates,
                holdings = holdings,
            )

            val suggestions = Planning.suggestCrew(slot, assigned, candidates)

            assertThat(suggestions.map { it.person.name }).doesNotContain("Clean")
        }

        @Test
        fun `a cross-partnership candidate carries a small penalty`() {
            val visitor = Fx.person(5, name = "Visitor", position = Fx.GPH, home = Fx.UNI.id)
            val all = listOf(clean, visitor)
            val withHoldings = holdings + (visitor.id to Fx.holdings(Fx.perpetual(Fx.WAH), Fx.perpetual(Fx.MED)))

            val suggestions = Planning.suggestCrew(slot, inputs(people = all, holdings = withHoldings), all)

            assertThat(suggestions.first { it.person.name == "Visitor" }.score).isEqualTo(3)
            assertThat(suggestions.first { it.person.name == "Visitor" }.crossPartnership).isTrue()
        }

        @Test
        fun `a clashing candidate sorts last but is shown, not hidden`() {
            val busy = Fx.person(6, name = "Busy", position = Fx.GPH)
            val all = listOf(oneGap, busy)
            val withHoldings = holdings + (busy.id to Fx.holdings(Fx.perpetual(Fx.WAH), Fx.perpetual(Fx.MED)))
            val elsewhere = listOf(
                AssignmentView(
                    AssignmentId(99), busy.id, CrewChangeId(99), Fx.UNI.id, 3,
                    swing.from.plusDays(2), swing.from.plusDays(10),
                ),
            )

            val suggestions = Planning.suggestCrew(
                slot, inputs(people = all, holdings = withHoldings), all, otherAssignments = elsewhere,
            )

            val busySuggestion = suggestions.first { it.person.name == "Busy" }
            assertThat(busySuggestion.hasClash).isTrue()
            assertThat(busySuggestion.score).isEqualTo(1000)
            assertThat(suggestions.last().person.name).isEqualTo("Busy")
            assertThat(busySuggestion.reasons).anyMatch { it.contains("clash") }
        }

        @Test
        fun `an assignment elsewhere that does not overlap the swing is not a clash`() {
            val busy = Fx.person(6, name = "Busy", position = Fx.GPH)
            val withHoldings = holdings + (busy.id to Fx.holdings(Fx.perpetual(Fx.WAH), Fx.perpetual(Fx.MED)))
            val elsewhere = listOf(
                AssignmentView(
                    AssignmentId(99), busy.id, CrewChangeId(99), Fx.UNI.id, 3,
                    swing.to.plusDays(5), swing.to.plusDays(30),
                ),
            )

            val suggestions = Planning.suggestCrew(
                slot, inputs(people = listOf(busy), holdings = withHoldings), listOf(busy),
                otherAssignments = elsewhere,
            )

            assertThat(suggestions.single().hasClash).isFalse()
        }

        @Test
        fun `weights are configuration`() {
            val weights = SuggestionWeights(gap = 1, unknown = 1, expiring = 1, crossPartnership = 0, overlappingAssignment = 1)
            val suggestions = Planning.suggestCrew(
                slot, inputs(people = candidates, holdings = holdings), candidates, weights = weights,
            )

            assertThat(suggestions.map { it.score }).containsExactly(0, 1, 1, 1)
        }

        @Test
        fun `a candidate on standing leave is scored and labelled, not hidden`() {
            val away = Fx.person(7, name = "Away", position = Fx.GPH)
            val all = listOf(oneGap, away)
            val withHoldings = holdings + (away.id to Fx.holdings(Fx.perpetual(Fx.WAH), Fx.perpetual(Fx.MED)))
            val leave = listOf(
                LeaveView(away.id, "annual_leave", swing.from.plusDays(3), swing.from.plusDays(9)),
            )

            val suggestions = Planning.suggestCrew(
                slot, inputs(people = all, holdings = withHoldings), all, leave = leave,
            )

            val awaySuggestion = suggestions.first { it.person.name == "Away" }
            assertThat(awaySuggestion.onLeave).isTrue()
            assertThat(awaySuggestion.score).isEqualTo(800)
            assertThat(suggestions.last().person.name).isEqualTo("Away")
            assertThat(awaySuggestion.reasons).anyMatch { it.contains("annual leave") }
        }

        @Test
        fun `leave outside the swing window is not a clash`() {
            val away = Fx.person(7, name = "Away", position = Fx.GPH)
            val withHoldings = holdings + (away.id to Fx.holdings(Fx.perpetual(Fx.WAH), Fx.perpetual(Fx.MED)))
            val leave = listOf(
                LeaveView(away.id, "annual_leave", swing.to.plusDays(1), swing.to.plusDays(14)),
            )

            val suggestions = Planning.suggestCrew(
                slot, inputs(people = listOf(away), holdings = withHoldings), listOf(away), leave = leave,
            )

            assertThat(suggestions.single().onLeave).isFalse()
            assertThat(suggestions.single().score).isEqualTo(0)
        }

        @Test
        fun `leave weighs less than a hard clash, so of two clashing candidates the one on leave ranks first`() {
            val away = Fx.person(7, name = "Away", position = Fx.GPH)
            val busy = Fx.person(8, name = "Busy", position = Fx.GPH)
            val all = listOf(away, busy)
            val withHoldings = holdings +
                (away.id to Fx.holdings(Fx.perpetual(Fx.WAH), Fx.perpetual(Fx.MED))) +
                (busy.id to Fx.holdings(Fx.perpetual(Fx.WAH), Fx.perpetual(Fx.MED)))
            val elsewhere = listOf(
                AssignmentView(
                    AssignmentId(99), busy.id, CrewChangeId(99), Fx.UNI.id, 3,
                    swing.from, swing.to,
                ),
            )
            val leave = listOf(LeaveView(away.id, "annual_leave", swing.from, swing.to))

            val suggestions = Planning.suggestCrew(
                slot, inputs(people = all, holdings = withHoldings), all,
                otherAssignments = elsewhere, leave = leave,
            )

            assertThat(suggestions.map { it.person.name }).containsExactly("Away", "Busy")
        }
    }

    @Nested
    @DisplayName("gap report")
    inner class GapReport {

        @Test
        fun `rows are ordered gap, expiring, unknown, review, pending, exempt`() {
            val gapPerson = Fx.person(1, name = "A Gap", position = Fx.GPH)
            val expiringPerson = Fx.person(2, name = "B Expiring", position = Fx.GPH)
            val unknownPerson = Fx.person(3, name = "C Unknown", position = Fx.GPH)
            val pendingPerson = Fx.person(4, name = "D Pending", position = Fx.GPH)
            val exemptPerson = Fx.person(5, name = "E Exempt", position = Fx.GPH)
            val okPerson = Fx.person(6, name = "F OK", position = Fx.GPH)
            val people = listOf(gapPerson, expiringPerson, unknownPerson, pendingPerson, exemptPerson, okPerson)

            val evaluation = SwingEvaluator.evaluate(
                SwingEvaluationInputs(
                    swing = swing,
                    partnership = Fx.NOR,
                    matrix = Fx.matrix(rules = listOf(Fx.rule(Fx.GPH, Fx.WAH, "M"))),
                    slots = listOf(slot),
                    assignments = people.mapIndexed { i, p -> Fx.assignment(i + 1L, p.id, slotRef = i + 1) },
                    people = people.associateBy { it.id },
                    holdings = mapOf(
                        gapPerson.id to Fx.holdings(Fx.notHeld(Fx.WAH)),
                        expiringPerson.id to Fx.holdings(Fx.held(Fx.WAH, swing.to.minusDays(1))),
                        unknownPerson.id to Fx.holdings(Fx.unknown(Fx.WAH)),
                        pendingPerson.id to Fx.holdings(Fx.notHeld(Fx.WAH)),
                        exemptPerson.id to Fx.holdings(Fx.notHeld(Fx.WAH)),
                        okPerson.id to Fx.holdings(Fx.perpetual(Fx.WAH)),
                    ),
                    registerRecords = mapOf(
                        pendingPerson.id to mapOf(Fx.WAH to Fx.open()),
                        exemptPerson.id to mapOf(Fx.WAH to Fx.approved()),
                    ),
                ),
            )

            val report = Planning.gapReport(evaluation)

            assertThat(report.map { it.state }).containsExactly(
                CellState.GAP, CellState.EXPIRING, CellState.UNKNOWN, CellState.PENDING, CellState.EXEMPT,
            )
            assertThat(report.map { it.person.name }).doesNotContain("F OK")
        }

        @Test
        fun `na cells are excluded`() {
            val person = Fx.person(1, position = Fx.CHIEF_OFFICER)
            val evaluation = SwingEvaluator.evaluate(
                SwingEvaluationInputs(
                    swing = swing,
                    partnership = Fx.NOR,
                    matrix = Fx.matrix(
                        rules = listOf(
                            Fx.rule(Fx.CHIEF_OFFICER, Fx.QL02, "M⁸"),
                            Fx.rule(Fx.CHIEF_OFFICER, Fx.QL03, "M⁸"),
                        ),
                        conditionals = listOf(OneOfRuleView(Fx.CHIEF_OFFICER, listOf(Fx.QL02, Fx.QL03))),
                    ),
                    slots = listOf(Fx.slot(1, Shift.SHIFT_1, Fx.CHIEF_OFFICER)),
                    assignments = listOf(Fx.assignment(1, person.id, 1)),
                    people = mapOf(person.id to person),
                    holdings = mapOf(person.id to Fx.holdings(Fx.perpetual(Fx.QL02), Fx.notHeld(Fx.QL03))),
                ),
            )

            assertThat(Planning.gapReport(evaluation)).isEmpty()
        }

        @Test
        fun `each row carries the context needed to pre-fill an exemption request`() {
            val person = Fx.person(1, name = "Needs Exemption", position = Fx.GPH)
            val evaluation = SwingEvaluator.evaluate(
                SwingEvaluationInputs(
                    swing = swing,
                    partnership = Fx.NOR,
                    matrix = Fx.matrix(rules = listOf(Fx.rule(Fx.GPH, Fx.WAH, "M"))),
                    slots = listOf(slot),
                    assignments = listOf(Fx.assignment(7, person.id, slotRef = 15)),
                    people = mapOf(person.id to person),
                    holdings = mapOf(person.id to Fx.holdings(Fx.notHeld(Fx.WAH))),
                ),
            )

            val row = Planning.gapReport(evaluation).single()
            assertThat(row.person.sam).isEqualTo(person.sam)
            assertThat(row.requirementId).isEqualTo(Fx.WAH)
            assertThat(row.slotRef).isEqualTo(15)
            assertThat(row.assignmentId).isEqualTo(AssignmentId(7))
            assertThat(row.level).isEqualTo(RuleLevel("M"))
        }
    }

    @Nested
    @DisplayName("expiry alerts")
    inner class ExpiryAlerts {

        private val today = LocalDate.of(2026, 7, 26)
        private val person = Fx.person(1, name = "Expiring Soon", position = Fx.GPH)

        @Test
        fun `holdings expiring inside the lead window are alerted, later ones are not`() {
            val holdings = mapOf(
                person.id to Fx.holdings(
                    Fx.held(Fx.WAH, today.plusDays(30)),
                    Fx.held(Fx.MED, today.plusDays(200)),
                ),
            )

            val alerts = Planning.expiryAlerts(listOf(person), holdings, emptyList(), today)

            assertThat(alerts.map { it.requirementId }).containsExactly(Fx.WAH)
            assertThat(alerts.single().daysRemaining).isEqualTo(30)
        }

        @Test
        fun `the boundary of the lead window is inclusive`() {
            val holdings = mapOf(person.id to Fx.holdings(Fx.held(Fx.WAH, today.plusDays(90))))
            assertThat(Planning.expiryAlerts(listOf(person), holdings, emptyList(), today)).hasSize(1)
        }

        @Test
        fun `already-expired holdings are alerted, not dropped`() {
            val holdings = mapOf(person.id to Fx.holdings(Fx.held(Fx.WAH, today.minusDays(10))))
            val alerts = Planning.expiryAlerts(listOf(person), holdings, emptyList(), today)

            assertThat(alerts.single().daysRemaining).isEqualTo(-10)
        }

        @Test
        fun `perpetual and not-held holdings never alert`() {
            val holdings = mapOf(person.id to Fx.holdings(Fx.perpetual(Fx.WAH), Fx.notHeld(Fx.MED), Fx.unknown(Fx.FRC)))
            assertThat(Planning.expiryAlerts(listOf(person), holdings, emptyList(), today)).isEmpty()
        }

        @Test
        fun `inactive people are excluded`() {
            val inactive = Fx.person(2, position = Fx.GPH, status = PersonStatus.INACTIVE)
            val holdings = mapOf(inactive.id to Fx.holdings(Fx.held(Fx.WAH, today.plusDays(5))))

            assertThat(Planning.expiryAlerts(listOf(inactive), holdings, emptyList(), today)).isEmpty()
        }

        @Test
        fun `impact is classified against the person's next assignment`() {
            val nextSwing = swing.copy(from = today.plusDays(20), to = today.plusDays(48))
            val assignment = Fx.assignment(1, person.id, slotRef = 15, swing = nextSwing)

            fun impactFor(expiry: LocalDate) = Planning.expiryAlerts(
                listOf(person),
                mapOf(person.id to Fx.holdings(Fx.held(Fx.WAH, expiry))),
                listOf(assignment),
                today,
            ).single().impact

            assertThat(impactFor(today.plusDays(10))).isEqualTo(ExpiryImpact.EXPIRED_BEFORE_SWING)
            assertThat(impactFor(today.plusDays(30))).isEqualTo(ExpiryImpact.MID_SWING)
            assertThat(impactFor(today.plusDays(60))).isEqualTo(ExpiryImpact.NONE)
        }

        @Test
        fun `impact is none where the person has no upcoming assignment`() {
            val past = Fx.assignment(
                1, person.id, slotRef = 15,
                swing = swing.copy(from = today.minusDays(60), to = today.minusDays(30)),
            )
            val holdings = mapOf(person.id to Fx.holdings(Fx.held(Fx.WAH, today.plusDays(5))))

            assertThat(Planning.expiryAlerts(listOf(person), holdings, listOf(past), today).single().impact)
                .isEqualTo(ExpiryImpact.NONE)
        }

        @Test
        fun `alerts are ordered by expiry date`() {
            val other = Fx.person(2, name = "Other", position = Fx.GPH)
            val holdings = mapOf(
                person.id to Fx.holdings(Fx.held(Fx.WAH, today.plusDays(60))),
                other.id to Fx.holdings(Fx.held(Fx.MED, today.plusDays(10))),
            )

            val alerts = Planning.expiryAlerts(listOf(person, other), holdings, emptyList(), today)

            assertThat(alerts.map { it.person.name }).containsExactly("Other", "Expiring Soon")
        }
    }
}
