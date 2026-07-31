package au.crewcomp.it

import au.crewcomp.courses.CourseOptionEntity
import au.crewcomp.engine.HoldingStatus
import au.crewcomp.engine.PersonStatus
import au.crewcomp.engine.QuotaScope
import au.crewcomp.engine.RuleLevel
import au.crewcomp.engine.Shift
import au.crewcomp.people.Assignment
import au.crewcomp.notify.Notification
import au.crewcomp.notify.NotificationKind
import au.crewcomp.people.LeaveRecord
import au.crewcomp.people.Person
import au.crewcomp.people.UserAccount
import au.crewcomp.people.UserAccountKind
import au.crewcomp.platform.security.Role
import au.crewcomp.people.QualificationHolding
import au.crewcomp.reference.CrewChange
import au.crewcomp.reference.CrewPosition
import au.crewcomp.reference.Partnership
import au.crewcomp.reference.PositionSlot
import au.crewcomp.reference.Requirement
import au.crewcomp.rules.MatrixStatus
import au.crewcomp.rules.MatrixVersion
import au.crewcomp.rules.QuotaRule
import au.crewcomp.rules.RequirementRule
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import java.time.Instant
import java.time.LocalDate

/**
 * Builds a minimal but realistic dataset for the DB-backed tests: one partnership, one swing,
 * two GPH crew on opposite shifts, a published matrix with a mandatory rule and a shift-scoped
 * quota.
 *
 * Written against the entities and repositories rather than raw SQL on purpose — seeding through
 * the mapped model is what proves the JPA mapping and the Flyway schema agree.
 */
@ApplicationScoped
class FixtureSeeder(private val em: EntityManager) {

    data class Seed(
        val partnershipId: Long,
        val crewChangeId: Long,
        val gphPositionId: Long,
        val wahRequirementId: Long,
        val medRequirementId: Long,
        val compliantPersonId: Long,
        val gapPersonId: Long,
        val matrixVersionId: Long,
        /** The crew member's own account — sync and notifications are addressed to it. */
        val compliantUserAccountId: Long,
        val notificationId: Long,
    )

    @Transactional
    fun clear() {
        // Child-first, so foreign keys stay satisfied.
        listOf(
            "delete from AuditEvent",
            "delete from ExceptionItem",
            // The FKs cascade in the database, but a JPQL bulk delete does not fire them — the
            // children go first or the parent delete violates them.
            "delete from RegisterAuditEntry",
            "delete from RegisterNote",
            "delete from ApprovalCondition",
            "delete from RegisterRecord",
            "delete from Notification",
            "delete from EvidenceDocument",
            // ADM-10 configuration is global mutable state in a table. A test that sets the
            // auto-accept threshold and did not reset it would silently change what every later
            // test's pipeline decides — so the fixture resets it, rather than trusting each test to.
            "delete from AppConfigEntry",
            "delete from LeaveRecord",
            // Before Person and Requirement, which it references.
            "delete from CrewStatement",
            // Before Person, Assignment and CrewChange. `attestation_declaration` goes with it on
            // the database's own ON DELETE CASCADE — that one is a real FK cascade rather than a
            // JPA annotation, so a JPQL bulk delete does fire it.
            "delete from Attestation",
            "delete from UserAccount",
            // After UserAccount, which references it. ADM-10's allow-list tests create rows here and
            // a leaked one would make the next test's list longer than it seeded.
            "delete from IdentityProvider",
            "delete from Assignment",
            "delete from QualificationHolding",
            "delete from RequirementRule",
            "delete from QuotaRule",
            // Members before rules, for the same reason as the register's children: the FK
            // cascades in the database but a JPQL bulk delete does not fire it. Drafts created by
            // the ADM-3 tests carry both.
            "delete from ConditionalRuleMember",
            "delete from ConditionalRule",
            "delete from MatrixTierPolicy",
            // Before Requirement, which it references. MOB-8's catalogue.
            "delete from CourseOptionEntity",
            "delete from Person",
            "delete from CrewChange",
            "delete from MatrixVersion",
            "delete from PositionSlot",
            // The extracted-dataset loader (ExtractedSeedIT) is the only writer of these three;
            // they sit before their parents for the same bulk-delete reason as everything else.
            "delete from RequirementAlias",
            "delete from PositionTier",
            "delete from Vessel",
            "delete from Requirement",
            "delete from CrewPosition",
            "delete from Partnership",
            // Last, and deliberately so: every delete above fires the AFTER DELETE triggers from
            // V2__sync_change_tracking.sql, so clearing the fixture *creates* tombstones. Wiping
            // them first would leave the table populated for the next test, which would then see
            // deletions of rows it never had.
            "delete from SyncTombstone",
        ).forEach { em.createQuery(it).executeUpdate() }
    }

    /**
     * Deletes one holding, so a test can exercise the tombstone path. Lives here rather than in
     * the test class because `@Transactional` self-invocation inside a test bypasses the
     * interceptor — and because there is deliberately no delete endpoint to call instead.
     */
    @Transactional
    fun deleteHolding(personId: Long, requirementId: Long): Int =
        em.createQuery(
            "delete from QualificationHolding h where h.person.id = :p and h.requirement.id = :r",
        ).setParameter("p", personId).setParameter("r", requirementId).executeUpdate()

    /**
     * Gives a seeded person a crew account, for the tests that need a notification to have
     * somewhere to land.
     *
     * Not folded into [seed] on purpose. §9's fan-out counts accounts, so an extra one changes
     * what several unrelated tests observe — a fixture that quietly grew a recipient would make
     * those tests wrong in a way that reads as a scan bug.
     */
    @Transactional
    fun giveAccount(personId: Long, vararg roles: Role): Long {
        val person = em.find(Person::class.java, personId)
        val now = Instant.now()
        val account = UserAccount().apply {
            this.person = person
            kind = UserAccountKind.LOCAL_TEST
            displayName = person.name
            (roles.toList().ifEmpty { listOf(Role.CREW_MEMBER) }).forEach {
                grantRole(it, "test-seeder", now)
            }
            stampCreated("test-seeder", now)
        }
        em.persist(account)
        em.flush()
        return account.requiredId
    }

    /** Moves a holding's expiry, for the tests that need a cell to read `expiring`. */
    @Transactional
    fun setHoldingExpiry(personId: Long, requirementId: Long, expiry: String): Int =
        em.createQuery(
            "update QualificationHolding h set h.statusValue = 'held_expiry', " +
                "h.expiryDate = :expiry where h.person.id = :p and h.requirement.id = :r",
        )
            .setParameter("expiry", LocalDate.parse(expiry))
            .setParameter("p", personId)
            .setParameter("r", requirementId)
            .executeUpdate()

    /**
     * An unassigned, fully compliant GPH candidate — someone for §5.4's suggestion ranking to
     * find. Both seeded crew are already on the fixture swing, and §5.4 excludes anyone assigned,
     * so a test of the ranking itself has to add its own candidate.
     *
     * Not folded into [seed] for the same reason as [giveAccount]: several tests count people.
     */
    @Transactional
    fun seedCandidate(
        sam: String,
        name: String,
        leaveFrom: LocalDate? = null,
        leaveTo: LocalDate? = null,
        leaveStatus: String = "approved",
    ): Long {
        val now = Instant.now()
        val gph = em.createQuery("from CrewPosition where name = 'GPH'", CrewPosition::class.java).singleResult
        val partnership = em.createQuery("from Partnership where abbrev = 'UNI'", Partnership::class.java).singleResult
        val candidate = person(sam, name, gph, partnership, "test-seeder", now)
        em.persist(candidate)
        listOf("PS-04", "MS-01").forEach { code ->
            val requirement = em.createQuery(
                "from Requirement where code = :c", Requirement::class.java,
            ).setParameter("c", code).singleResult
            holding(candidate, requirement, HoldingStatus.HELD_PERPETUAL, null, "test-seeder", now)
        }
        if (leaveFrom != null && leaveTo != null) {
            em.persist(
                LeaveRecord().apply {
                    person = candidate
                    kind = "annual_leave"
                    fromDate = leaveFrom
                    toDate = leaveTo
                    status = leaveStatus
                    stampCreated("test-seeder", now)
                },
            )
        }
        em.flush()
        return candidate.requiredId
    }

    /** One MOB-8 course date. Returns its business ref, which is what a device sends back. */
    @Transactional
    fun seedCourseOption(
        ref: String,
        requirementId: Long,
        starts: LocalDate,
        finishes: LocalDate = starts.plusDays(1),
        seats: Int = 5,
        providerName: String = "Fremantle Marine Training",
        where: String = "Fremantle",
    ): String {
        em.persist(
            CourseOptionEntity().apply {
                optionRef = ref
                requirement = em.find(Requirement::class.java, requirementId)
                this.starts = starts
                this.finishes = finishes
                provider = providerName
                location = where
                durationLabel = "2 days"
                this.seats = seats
                stampCreated("test-seeder", Instant.now())
            },
        )
        em.flush()
        return ref
    }

    @Transactional
    fun seed(): Seed {
        val now = Instant.now()
        val actor = "test-seeder"

        val partnership = Partnership().apply {
            abbrev = "UNI"
            name = "Unity Partnership"
            vesselClass = null
            stampCreated(actor, now)
        }
        em.persist(partnership)

        val gph = CrewPosition().apply {
            name = "GPH"
            stampCreated(actor, now)
        }
        em.persist(gph)

        val wah = Requirement().apply {
            code = "PS-04"
            category = "PS"
            title = "Work at Heights"
            stampCreated(actor, now)
        }
        val med = Requirement().apply {
            code = "MS-01"
            category = "MS"
            title = "Seafarer Medical"
            stampCreated(actor, now)
        }
        em.persist(wah)
        em.persist(med)

        listOf(15 to Shift.SHIFT_1, 16 to Shift.SHIFT_2).forEach { (slotRef, slotShift) ->
            em.persist(
                PositionSlot().apply {
                    ref = slotRef
                    shift = slotShift
                    allowedPositions = mutableSetOf(gph)
                    stampCreated(actor, now)
                },
            )
        }

        val swingFrom = LocalDate.of(2026, 8, 1)
        val crewChange = CrewChange().apply {
            ccId = "CC24"
            this.partnership = partnership
            fromDate = swingFrom
            toDate = swingFrom.plusDays(27)
            cutoffDate = swingFrom.minusDays(7)
            stampCreated(actor, now)
        }
        em.persist(crewChange)

        val matrix = MatrixVersion().apply {
            label = "test-v1"
            status = MatrixStatus.PUBLISHED
            effectiveFrom = LocalDate.of(2026, 1, 1)
            publishedAt = now
            publishedBy = actor
            stampCreated(actor, now)
        }
        em.persist(matrix)

        em.persist(
            RequirementRule().apply {
                matrixVersion = matrix
                position = gph
                requirement = med
                level = RuleLevel.MANDATORY
                stampCreated(actor, now)
            },
        )
        // Work at Heights is quota-only: an individual gap is not a personal gap (§5.1 step 2).
        em.persist(
            RequirementRule().apply {
                matrixVersion = matrix
                position = gph
                requirement = wah
                level = RuleLevel("M9")
                stampCreated(actor, now)
            },
        )
        em.persist(
            QuotaRule().apply {
                matrixVersion = matrix
                footnote = "M9"
                requirement = wah
                minCount = 1
                scope = QuotaScope.SHIFT
                positions = mutableSetOf(gph)
                stampCreated(actor, now)
            },
        )

        val compliant = person("SAM001", "Compliant Crew", gph, partnership, actor, now)
        val gapped = person("SAM002", "Gap Crew", gph, partnership, actor, now)
        em.persist(compliant)
        em.persist(gapped)

        holding(compliant, wah, HoldingStatus.HELD_PERPETUAL, null, actor, now)
        holding(compliant, med, HoldingStatus.HELD_EXPIRY, swingFrom.plusYears(1), actor, now)
        holding(gapped, wah, HoldingStatus.NOT_HELD, null, actor, now)
        holding(gapped, med, HoldingStatus.NOT_HELD, null, actor, now)

        // Shift 1 gets the crew member WITHOUT Work at Heights — the known shortfall shape.
        assignment(gapped, crewChange, partnership, 15, actor, now)
        assignment(compliant, crewChange, partnership, 16, actor, now)

        // The crew member's own account (§7.7). `local_test` because a corporate account would
        // need an issuer and subject, and SEC-1b wants invented linkage flagged and enumerable.
        val compliantAccount = UserAccount().apply {
            person = compliant
            kind = UserAccountKind.LOCAL_TEST
            displayName = compliant.name
            grantRole(Role.CREW_MEMBER, actor, now)
            stampCreated(actor, now)
        }
        em.persist(compliantAccount)

        em.persist(
            LeaveRecord().apply {
                person = compliant
                kind = "annual_leave"
                fromDate = swingFrom.plusDays(40)
                toDate = swingFrom.plusDays(50)
                status = "recorded"
                stampCreated(actor, now)
            },
        )

        // SEC-13: the title alone is what a push payload may carry, so it names no requirement.
        val notification = Notification().apply {
            recipient = compliantAccount
            kind = NotificationKind.EXPIRY_WARNING.wire
            title = "A qualification is expiring soon"
            body = "Your Seafarer Medical expires within the alert window."
            deepLink = "crewcomp://certifications/${'$'}{med.requiredId}"
            stampCreated(actor, now)
        }
        em.persist(notification)

        em.flush()

        return Seed(
            partnershipId = partnership.requiredId,
            crewChangeId = crewChange.requiredId,
            gphPositionId = gph.requiredId,
            wahRequirementId = wah.requiredId,
            medRequirementId = med.requiredId,
            compliantPersonId = compliant.requiredId,
            gapPersonId = gapped.requiredId,
            matrixVersionId = matrix.requiredId,
            compliantUserAccountId = compliantAccount.requiredId,
            notificationId = notification.requiredId,
        )
    }

    private fun person(
        samNumber: String,
        personName: String,
        position: CrewPosition,
        partnership: Partnership,
        actor: String,
        now: Instant,
    ) = Person().apply {
        sam = samNumber
        name = personName
        this.position = position
        this.partnership = partnership
        status = PersonStatus.ACTIVE
        stampCreated(actor, now)
    }

    private fun holding(
        person: Person,
        requirement: Requirement,
        holdingStatus: HoldingStatus,
        expiry: LocalDate?,
        actor: String,
        now: Instant,
    ) {
        em.persist(
            QualificationHolding().apply {
                this.person = person
                this.requirement = requirement
                status = holdingStatus
                expiryDate = expiry
                stampCreated(actor, now)
            },
        )
    }

    private fun assignment(
        person: Person,
        crewChange: CrewChange,
        partnership: Partnership,
        slot: Int,
        actor: String,
        now: Instant,
    ) {
        em.persist(
            Assignment().apply {
                this.person = person
                this.crewChange = crewChange
                this.partnership = partnership
                slotRef = slot
                fromDate = crewChange.fromDate
                toDate = crewChange.toDate
                stampCreated(actor, now)
            },
        )
    }
}
