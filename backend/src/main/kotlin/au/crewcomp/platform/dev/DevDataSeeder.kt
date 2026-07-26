package au.crewcomp.platform.dev

import au.crewcomp.engine.HoldingStatus
import au.crewcomp.engine.PersonStatus
import au.crewcomp.engine.QuotaScope
import au.crewcomp.engine.RuleLevel
import au.crewcomp.engine.Shift
import au.crewcomp.notify.Notification
import au.crewcomp.notify.NotificationKind
import au.crewcomp.people.Assignment
import au.crewcomp.people.LeaveRecord
import au.crewcomp.people.Person
import au.crewcomp.people.QualificationHolding
import au.crewcomp.people.UserAccount
import au.crewcomp.people.UserAccountKind
import au.crewcomp.platform.security.Role
import au.crewcomp.platform.time.BusinessClock
import au.crewcomp.reference.CrewChange
import au.crewcomp.reference.CrewPosition
import au.crewcomp.reference.Partnership
import au.crewcomp.reference.PositionSlot
import au.crewcomp.reference.Requirement
import au.crewcomp.rules.MatrixStatus
import au.crewcomp.rules.MatrixVersion
import au.crewcomp.rules.QuotaRule
import au.crewcomp.rules.RequirementRule
import io.quarkus.arc.properties.IfBuildProperty
import io.quarkus.runtime.StartupEvent
import io.quarkus.runtime.configuration.ConfigUtils
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.jboss.logging.Logger
import java.time.Instant
import java.time.LocalDate

/**
 * A development fixture, so `quarkus dev` and the admin SPA have something to render.
 *
 * **This is not the §11 migration.** The real load comes from the POC's validated
 * `seed` CSV extract, is re-runnable, produces ExceptionItems for every fix-up class, and is
 * acceptance-tested by diffing the regenerated CC24/CC25 views against the POC's rendering. None
 * of that is here. What is here is a small synthetic dataset shaped to exercise the screens: a
 * cell in every interesting state, a quota that is short on one shift, a mid-swing handover, and
 * a person whose holding expires inside the swing.
 *
 * The names are invented. Nothing in this file may ever reach an environment holding real crew
 * data, so it is guarded three ways: the bean is removed at build time unless
 * `crewcomp.dev-seed.enabled` is true (dev only), start-up fails if it is somehow enabled under
 * `prod`, and it does nothing at all unless the database is completely empty.
 */
@ApplicationScoped
@IfBuildProperty(name = "crewcomp.dev-seed.enabled", stringValue = "true")
class DevDataSeeder(
    private val em: EntityManager,
    private val clock: BusinessClock,
) {
    private val log = Logger.getLogger(DevDataSeeder::class.java)

    @Transactional
    fun onStart(@Observes event: StartupEvent) {
        if (ConfigUtils.getProfiles().contains("prod")) {
            throw IllegalStateException(
                "crewcomp.dev-seed.enabled is true under the production profile. The development " +
                    "seed writes invented people and must never run against real data.",
            )
        }

        val existing = em.createQuery("select count(p) from Person p", java.lang.Long::class.java)
            .singleResult
        if (existing.toLong() > 0) {
            log.infof("Development seed skipped: %d people already present", existing.toLong())
            return
        }

        seed()
        log.warn("Development seed applied: invented crew, vessels and holdings. Not real data.")
    }

    private fun seed() {
        val now = Instant.now()
        val actor = "dev-seed"
        val today = clock.today()

        fun position(name: String) = CrewPosition().apply {
            this.name = name
            stampCreated(actor, now)
        }.also { em.persist(it) }

        val master = position("Master")
        val chiefOfficer = position("Chief Officer")
        val engineer = position("Engineer")
        val gph = position("GPH")

        fun requirement(code: String, category: String, title: String) = Requirement().apply {
            this.code = code
            this.category = category
            this.title = title
            stampCreated(actor, now)
        }.also { em.persist(it) }

        val coc = requirement("QL-01", "QL", "Certificate of Competency — Master")
        val coc2 = requirement("QL-02", "QL", "Certificate of Competency — Chief Officer")
        val gphTicket = requirement("QL-08", "QL", "General Purpose Hand ticket")
        val medical = requirement("MS-01", "MS", "Seafarer Medical")
        val sea = requirement("MS-02", "MS", "Sea Survival")
        val heights = requirement("PS-04", "PS", "Work at Heights")
        val confined = requirement("PS-05", "PS", "Confined Space Entry")
        val induction = requirement("VI-01", "VI", "Vessel Induction")

        // Slots 15/16 accept AE or GPH in the real model; here every slot is single-position
        // except the two that carry the quota footnote.
        val slots = listOf(
            Triple(1, Shift.NOT_APPLICABLE, setOf(master)),
            Triple(2, Shift.SHIFT_1, setOf(chiefOfficer)),
            Triple(3, Shift.SHIFT_2, setOf(chiefOfficer)),
            Triple(4, Shift.SHIFT_1, setOf(engineer)),
            Triple(5, Shift.SHIFT_2, setOf(engineer)),
            Triple(15, Shift.SHIFT_1, setOf(gph)),
            Triple(16, Shift.SHIFT_2, setOf(gph)),
        )
        slots.forEach { (slotRef, slotShift, positions) ->
            em.persist(
                PositionSlot().apply {
                    ref = slotRef
                    shift = slotShift
                    allowedPositions = positions.toMutableSet()
                    stampCreated(actor, now)
                },
            )
        }

        val matrix = MatrixVersion().apply {
            label = "dev-2026.1"
            status = MatrixStatus.PUBLISHED
            effectiveFrom = today.minusMonths(6)
            publishedAt = now
            publishedBy = actor
            stampCreated(actor, now)
        }
        em.persist(matrix)

        fun rule(position: CrewPosition, requirement: Requirement, level: RuleLevel) {
            em.persist(
                RequirementRule().apply {
                    matrixVersion = matrix
                    this.position = position
                    this.requirement = requirement
                    this.level = level
                    stampCreated(actor, now)
                },
            )
        }

        listOf(master, chiefOfficer, engineer, gph).forEach { position ->
            rule(position, medical, RuleLevel.MANDATORY)
            rule(position, sea, RuleLevel.MANDATORY)
            rule(position, induction, RuleLevel.MANDATORY)
            // Work at Heights is quota-only: an individual miss is `quota_only`, not a gap.
            rule(position, heights, RuleLevel("M9"))
            rule(position, confined, RuleLevel.RECOMMENDED)
        }
        rule(master, coc, RuleLevel.MANDATORY)
        rule(chiefOfficer, coc2, RuleLevel.MANDATORY)
        rule(gph, gphTicket, RuleLevel.MANDATORY)

        em.persist(
            QuotaRule().apply {
                footnote = "M9"
                matrixVersion = matrix
                requirement = heights
                minCount = 1
                scope = QuotaScope.SHIFT
                positions = mutableSetOf(gph, engineer)
                stampCreated(actor, now)
            },
        )

        fun partnership(abbrev: String, name: String, vesselClass: String?) = Partnership().apply {
            this.abbrev = abbrev
            this.name = name
            this.vesselClass = vesselClass
            stampCreated(actor, now)
        }.also { em.persist(it) }

        val uni = partnership("UNI", "Unity Partnership", null)
        val nor = partnership("NOR", "Northern Partnership", "class-b")

        // A swing containing today, and the next one — so the dashboard's "current or next"
        // selection has both cases to choose between.
        fun crewChange(id: String, of: Partnership, from: LocalDate) = CrewChange().apply {
            ccId = id
            partnership = of
            fromDate = from
            toDate = from.plusDays(27)
            cutoffDate = from.minusDays(7)
            stampCreated(actor, now)
        }.also { em.persist(it) }

        val uniCurrent = crewChange("CC24", uni, today.minusDays(6))
        val uniNext = crewChange("CC25", uni, today.plusDays(22))
        val norCurrent = crewChange("CC24", nor, today.minusDays(2))

        var samSeq = 0
        fun person(
            name: String,
            position: CrewPosition,
            of: Partnership,
            tierName: String? = null,
            personStatus: PersonStatus = PersonStatus.ACTIVE,
        ) = Person().apply {
            samSeq += 1
            sam = "SAM%03d".format(samSeq)
            this.name = name
            this.position = position
            this.partnership = of
            tier = tierName
            status = personStatus
            email = "${name.substringBefore(' ').lowercase()}@example.invalid"
            stampCreated(actor, now)
        }.also { em.persist(it) }

        fun holding(
            who: Person,
            what: Requirement,
            state: HoldingStatus,
            expiry: LocalDate? = null,
            note: String? = null,
        ) {
            em.persist(
                QualificationHolding().apply {
                    person = who
                    requirement = what
                    status = state
                    expiryDate = if (state == HoldingStatus.HELD_EXPIRY) expiry else null
                    this.note = note
                    stampCreated(actor, now)
                },
            )
        }

        fun assign(who: Person, cc: CrewChange, slotRef: Int, from: LocalDate = cc.fromDate, to: LocalDate = cc.toDate) {
            em.persist(
                Assignment().apply {
                    person = who
                    crewChange = cc
                    partnership = cc.partnership
                    this.slotRef = slotRef
                    fromDate = from
                    toDate = to
                    stampCreated(actor, now)
                },
            )
        }

        /**
         * Everything mandatory, valid well past the swing — the baseline "ok" crew member.
         *
         * Sea Survival is parameterised because it is the requirement the interesting cases vary:
         * one person's expires mid-swing, another's was never established. A holding is unique
         * per (person, requirement), so these have to be set here rather than layered on top.
         */
        fun compliant(
            who: Person,
            extra: List<Requirement> = emptyList(),
            seaStatus: HoldingStatus = HoldingStatus.HELD_EXPIRY,
            seaExpiry: LocalDate? = today.plusYears(2),
            seaNote: String? = null,
        ) {
            holding(who, medical, HoldingStatus.HELD_EXPIRY, today.plusYears(1))
            holding(who, sea, seaStatus, seaExpiry, seaNote)
            holding(who, induction, HoldingStatus.HELD_PERPETUAL)
            extra.forEach { holding(who, it, HoldingStatus.HELD_PERPETUAL) }
        }

        // A holding row is what makes a cell knowable: with no row at all the engine says
        // `unknown`, not `gap` (§5.1). So the fixture states Work at Heights explicitly for
        // everyone — held or not held — and leaves exactly one person's genuinely unestablished,
        // which is the ADM-7 chase-list case.
        val skipper = person("Ada Nakamura", master, uni)
        compliant(skipper, listOf(coc, heights))

        val mate = person("Bruno Oyelaran", chiefOfficer, uni, tierName = "senior")
        // Expires inside the swing: `expiring`, not `gap` — the state most easily missed.
        compliant(mate, listOf(coc2), seaExpiry = uniCurrent.toDate.minusDays(3))
        holding(mate, heights, HoldingStatus.NOT_HELD)

        val chief = person("Chidi Alvarez", engineer, uni)
        compliant(chief, listOf(heights))

        // Neither engineer on the shift-2 slot holds Work at Heights, which is what leaves the
        // M9 quota short on that shift — a quota failure with no individual gap behind it.
        val handoverOut = person("Dev Ramaswamy", engineer, uni)
        compliant(handoverOut)
        holding(handoverOut, heights, HoldingStatus.NOT_HELD)

        val handoverIn = person("Eve Lindqvist", engineer, uni)
        compliant(handoverIn)
        holding(handoverIn, heights, HoldingStatus.NOT_HELD)

        // Two confirmed-not-held mandatory requirements: a real `gap` roll-up, and the row the
        // gap report puts at the top of the worklist.
        val gapCrew = person("Finn Abara", gph, uni)
        holding(gapCrew, medical, HoldingStatus.HELD_EXPIRY, today.plusYears(1))
        holding(gapCrew, induction, HoldingStatus.HELD_PERPETUAL)
        holding(gapCrew, sea, HoldingStatus.NOT_HELD)
        holding(gapCrew, gphTicket, HoldingStatus.NOT_HELD, note = "Lapsed, renewal booked")
        // Quota-footnoted, so this one is `quota_only` rather than a personal gap (§5.1 step 2).
        holding(gapCrew, heights, HoldingStatus.NOT_HELD)

        val unknownCrew = person("Gita Marchetti", gph, uni)
        // The standing "unknown holdings" chase list (§4.3 Q11, ADM-7).
        compliant(
            unknownCrew,
            listOf(gphTicket),
            seaStatus = HoldingStatus.UNKNOWN,
            seaExpiry = null,
            seaNote = "Legacy row — never established",
        )

        // Unassigned on UNI CC24. Slot 3 is the open one and takes a Chief Officer, so the
        // ranking has two candidates of the right position with different standings: one clear,
        // one carrying an unknown. A GPH is seeded unassigned too, to prove the position filter.
        val relief = person("Jun Oyelowo", chiefOfficer, uni)
        compliant(relief, listOf(coc2, heights))

        val reliefWithUnknown = person("Kira Solberg", chiefOfficer, nor)
        compliant(
            reliefWithUnknown,
            listOf(coc2),
            seaStatus = HoldingStatus.UNKNOWN,
            seaExpiry = null,
        )
        holding(reliefWithUnknown, heights, HoldingStatus.NOT_HELD)

        val spare = person("Hana Petrov", gph, nor)
        compliant(spare, listOf(gphTicket, heights))

        // Inactive: excluded from suggestion ranking (§5.4), still visible in the directory.
        val retired = person("Iris Kovač", gph, uni, personStatus = PersonStatus.INACTIVE)
        compliant(retired, listOf(gphTicket, heights))

        // UNI CC24 carries the shape of the real shortfall (§11): the M9 Work-at-Heights quota
        // is met on shift 1 (the engineer in slot 4 holds it) and short on shift 2, where neither
        // the slot-5 engineers nor the slot-16 GPH do.
        assign(skipper, uniCurrent, 1)
        assign(mate, uniCurrent, 2)
        assign(chief, uniCurrent, 4)
        assign(gapCrew, uniCurrent, 15)
        assign(unknownCrew, uniCurrent, 16)
        // A mid-swing handover: two people, one slot, sequential ranges.
        val midpoint = uniCurrent.fromDate.plusDays(13)
        assign(handoverOut, uniCurrent, 5, to = midpoint)
        assign(handoverIn, uniCurrent, 5, from = midpoint.plusDays(1))
        // Slot 3 is left open, so the planner has an open slot to suggest against.

        assign(skipper, uniNext, 1)
        assign(chief, uniNext, 4)

        assign(spare, norCurrent, 15)

        // -------------------------------------------------------------------
        // Mobile self-service fixture (§7): the crew app needs a signed-in *crew member* with
        // an account, leave, and notifications — none of which the admin screens required.
        //
        // Bruno Oyelaran is the demonstration crew member because his data is the interesting
        // shape: assigned to the swing in progress, one holding expiring inside it, and one
        // confirmed not-held. Signing in as him shows every MOB-1 state on the first screen.
        // -------------------------------------------------------------------

        fun crewAccount(who: Person) = UserAccount().apply {
            person = who
            // SEC-1b: flagged as local_test so it stays enumerable and its removal is
            // verifiable. A corporate account would need an issuer and subject, and inventing
            // those would put fictional identity linkage in the same shape as the real thing.
            kind = UserAccountKind.LOCAL_TEST
            displayName = who.name
            email = who.email
            grantRole(Role.CREW_MEMBER, actor, now)
            stampCreated(actor, now)
        }.also { em.persist(it) }

        val mateAccount = crewAccount(mate)
        crewAccount(gapCrew)
        crewAccount(skipper)

        fun leave(who: Person, kindName: String, from: LocalDate, days: Long) {
            em.persist(
                LeaveRecord().apply {
                    person = who
                    kind = kindName
                    fromDate = from
                    toDate = from.plusDays(days)
                    status = "recorded"
                    stampCreated(actor, now)
                },
            )
        }

        // Leave sits between the current swing and the next, which is the ordinary pattern and
        // the one the roster calendar has to render without overlapping an assignment.
        leave(mate, "annual_leave", uniCurrent.toDate.plusDays(3), 9)
        leave(skipper, "annual_leave", today.plusDays(60), 13)

        fun notify(
            to: UserAccount,
            kind: NotificationKind,
            title: String,
            body: String? = null,
            deepLink: String? = null,
            readAt: Instant? = null,
        ) {
            em.persist(
                Notification().apply {
                    recipient = to
                    this.kind = kind.wire
                    this.title = title
                    this.body = body
                    this.deepLink = deepLink
                    this.readAt = readAt
                    stampCreated(actor, now)
                },
            )
        }

        // SEC-13 in the fixture as well as the code: every title here is safe on a lock screen.
        // "A qualification is expiring soon" — not which one, not whose, not when.
        notify(
            to = mateAccount,
            kind = NotificationKind.EXPIRY_WARNING,
            title = "A qualification is expiring soon",
            body = "Your Sea Survival certificate expires on " +
                "${uniCurrent.toDate.minusDays(3)}, before the end of your current swing.",
            deepLink = "crewcomp://certifications/${sea.requiredId}",
        )
        notify(
            to = mateAccount,
            kind = NotificationKind.ASSIGNMENT_ADDED,
            title = "You have a new assignment",
            body = "UNI ${uniCurrent.ccId}, slot 2, ${uniCurrent.fromDate} to ${uniCurrent.toDate}.",
            deepLink = "crewcomp://roster/${uniCurrent.requiredId}",
            readAt = now.minusSeconds(86_400),
        )
        notify(
            to = mateAccount,
            kind = NotificationKind.REQUIREMENT_ADDED,
            title = "A new requirement applies to your position",
            body = "Work at Heights is now required for Chief Officer under matrix " +
                "${matrix.label}.",
            deepLink = "crewcomp://certifications/${heights.requiredId}",
        )

        em.flush()
    }
}
