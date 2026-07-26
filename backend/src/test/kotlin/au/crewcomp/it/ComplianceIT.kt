package au.crewcomp.it

import au.crewcomp.compliance.ComplianceService
import au.crewcomp.compliance.MatrixSnapshotService
import au.crewcomp.engine.CellState
import au.crewcomp.engine.HoldingStatus
import au.crewcomp.engine.RequirementId
import au.crewcomp.engine.Shift
import au.crewcomp.people.HoldingService
import au.crewcomp.platform.audit.AuditChain
import au.crewcomp.platform.audit.AuditEvent
import au.crewcomp.platform.security.AccessDeniedException
import au.crewcomp.platform.security.Actor
import au.crewcomp.platform.security.ActorContext
import au.crewcomp.platform.security.ActorKind
import au.crewcomp.platform.security.Role
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * End-to-end coverage of the parts a pure unit test cannot reach: that the Flyway schema and the
 * JPA mapping agree, that the engine gets the inputs the repositories build for it, and that an
 * audited write really does land a hash-chained event in the same transaction.
 *
 * Named `*IT` so it runs under Failsafe rather than Surefire: a `@QuarkusTest` bootstraps the
 * application (and therefore Dev Services, and therefore a container runtime) at *discovery*
 * time, so merely appearing on Surefire's list breaks `mvn test` on a machine without Docker.
 * The split keeps the pure suite runnable on any laptop; CI runs `mvn verify -DskipITs=false`.
 */
@QuarkusTest
@DisplayName("Compliance stack against a real database")
class ComplianceIT {

    @Inject lateinit var seeder: FixtureSeeder
    @Inject lateinit var compliance: ComplianceService
    @Inject lateinit var holdings: HoldingService
    @Inject lateinit var matrixSnapshots: MatrixSnapshotService
    @Inject lateinit var actorContext: ActorContext
    @Inject lateinit var em: EntityManager

    private lateinit var seed: FixtureSeeder.Seed

    private val backOffice = Actor(
        userAccountId = null,
        personId = null,
        roles = setOf(Role.DATA_STEWARD, Role.CREW_COORDINATOR),
        kind = ActorKind.HUMAN,
        label = "Test Steward",
    )

    @BeforeEach
    fun setUp() {
        seeder.clear()
        matrixSnapshots.invalidateAll()
        seed = seeder.seed()
        actorContext.set(backOffice)
    }

    @Test
    fun `the baseline migration and the JPA mapping agree`() {
        // Reaching this point at all means Flyway applied V1 and Hibernate validated every
        // mapped entity against it — `schema-management.strategy=validate` fails start-up
        // otherwise. The assertion below simply proves the seeded rows round-trip.
        val people = em.createQuery("select count(p) from Person p", java.lang.Long::class.java).singleResult
        assertThat(people.toLong()).isEqualTo(2)
    }

    @Test
    fun `a swing evaluates through the repositories, the mapping and the engine`() {
        val evaluation = compliance.evaluateSwing("UNI", "CC24")

        assertThat(evaluation.assignments).hasSize(2)
        assertThat(evaluation.openSlots).isEmpty()

        val gapCrew = evaluation.assignments.first { it.person.sam == "SAM002" }
        assertThat(gapCrew.evaluation.cell(RequirementId(seed.medRequirementId))!!.state)
            .isEqualTo(CellState.GAP)
        // Work at Heights is quota-only for this version, so it is not an individual gap.
        assertThat(gapCrew.evaluation.cell(RequirementId(seed.wahRequirementId))!!.state)
            .isEqualTo(CellState.QUOTA_ONLY)
    }

    @Test
    fun `the shift-scoped quota shortfall surfaces on shift 1 only`() {
        val shortfalls = compliance.quotas("UNI", "CC24").filterNot { it.satisfied }

        assertThat(shortfalls).hasSize(1)
        assertThat(shortfalls.single().shift).isEqualTo(Shift.SHIFT_1)
        assertThat(shortfalls.single().actual).isZero()
    }

    @Test
    fun `the gap report lists the mandatory gap and excludes the compliant crew member`() {
        val report = compliance.gapReport("UNI", "CC24")

        assertThat(report.map { it.person.sam }).contains("SAM002")
        assertThat(report.filter { it.person.sam == "SAM001" }).isEmpty()
    }

    @Test
    fun `a holding write is audited in the same transaction and chains correctly`() {
        holdings.setHolding(
            personId = seed.gapPersonId,
            requirementId = seed.medRequirementId,
            status = HoldingStatus.HELD_EXPIRY,
            expiry = LocalDate.of(2027, 6, 30),
            note = "Renewed at the clinic",
        )

        val events = auditEvents()
        assertThat(events).hasSize(1)

        val event = events.single()
        assertThat(event.seq).isEqualTo(1)
        assertThat(event.event).isEqualTo("holding.updated")
        assertThat(event.actorKind).isEqualTo(ActorKind.HUMAN.wire)
        assertThat(event.actorLabel).isEqualTo("Test Steward")
        assertThat(event.entityBusinessKey).isEqualTo("SAM002/MS-01")
        assertThat(event.beforeState).contains("not_held")
        assertThat(event.afterState).contains("held_expiry")
        assertThat(AuditChain.verify(events).isIntact).isTrue()
    }

    @Test
    fun `consecutive audited writes form a gapless chain`() {
        holdings.setHolding(seed.gapPersonId, seed.medRequirementId, HoldingStatus.HELD_PERPETUAL)
        holdings.setHolding(seed.gapPersonId, seed.wahRequirementId, HoldingStatus.HELD_PERPETUAL)
        holdings.setHolding(seed.compliantPersonId, seed.wahRequirementId, HoldingStatus.NOT_HELD)

        val events = auditEvents()

        assertThat(events.map { it.seq }).containsExactly(1L, 2L, 3L)
        assertThat(events.first().prevHash).isNull()
        assertThat(events[1].prevHash).isEqualTo(events[0].eventHash)
        assertThat(events[2].prevHash).isEqualTo(events[1].eventHash)
        assertThat(AuditChain.verify(events).isIntact).isTrue()
    }

    @Test
    fun `the evaluation reflects an audited holding change immediately`() {
        assertThat(compliance.quotas("UNI", "CC24").filterNot { it.satisfied }).hasSize(1)

        holdings.setHolding(seed.gapPersonId, seed.wahRequirementId, HoldingStatus.HELD_PERPETUAL)

        assertThat(compliance.quotas("UNI", "CC24").filterNot { it.satisfied }).isEmpty()
    }

    @Test
    fun `a crew member may read their own evaluation but not another's (AUTH-2)`() {
        actorContext.set(
            Actor(
                userAccountId = null,
                personId = seed.gapPersonId,
                roles = setOf(Role.CREW_MEMBER),
                kind = ActorKind.HUMAN,
                label = "Gap Crew",
            ),
        )

        val own = compliance.evaluatePerson(seed.gapPersonId, "UNI", "CC24")
        assertThat(own.personId.value).isEqualTo(seed.gapPersonId)

        assertThatThrownBy { compliance.evaluatePerson(seed.compliantPersonId, "UNI", "CC24") }
            .isInstanceOf(AccessDeniedException::class.java)
    }

    @Test
    fun `a crew member may not evaluate a whole swing (AUTH-1)`() {
        actorContext.set(
            Actor(
                userAccountId = null,
                personId = seed.gapPersonId,
                roles = setOf(Role.CREW_MEMBER),
                kind = ActorKind.HUMAN,
                label = "Gap Crew",
            ),
        )

        assertThatThrownBy { compliance.evaluateSwing("UNI", "CC24") }
            .isInstanceOf(AccessDeniedException::class.java)
    }

    @Test
    fun `a crew member may not write a holding, even their own (AUTH-1)`() {
        actorContext.set(
            Actor(
                userAccountId = null,
                personId = seed.gapPersonId,
                roles = setOf(Role.CREW_MEMBER),
                kind = ActorKind.HUMAN,
                label = "Gap Crew",
            ),
        )

        assertThatThrownBy {
            holdings.setHolding(seed.gapPersonId, seed.medRequirementId, HoldingStatus.HELD_PERPETUAL)
        }.isInstanceOf(AccessDeniedException::class.java)

        assertThat(auditEvents()).isEmpty()
    }

    @Test
    fun `a rejected write leaves no audit event and no holding change`() {
        assertThatThrownBy {
            holdings.setHolding(seed.gapPersonId, seed.medRequirementId, HoldingStatus.HELD_EXPIRY, expiry = null)
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThat(auditEvents()).isEmpty()
        assertThat(compliance.gapReport("UNI", "CC24").map { it.person.sam }).contains("SAM002")
    }

    @Transactional
    fun auditEvents(): List<AuditEvent> =
        em.createQuery("select e from AuditEvent e order by e.seq", AuditEvent::class.java).resultList
}
