package au.crewcomp.it

import au.crewcomp.compliance.MatrixSnapshotService
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.LocalDate

/**
 * The portal-dataset loader against real PostgreSQL, driven by the **fictional** fixture in
 * `src/test/resources/portal-fixture` — shaped like a Coolibah portal snapshot, anomalies
 * included, with invented people. A real snapshot is personal data and is never committed; what
 * this proves is that every reading the mapping decisions prescribe (26 Aug 2026,
 * docs/handoff/coolibah-portal-dataset.md) survives the schema's constraints.
 */
@QuarkusTest
@DisplayName("Coolibah portal dataset loader")
class PortalSeedIT {

    @Inject lateinit var seeder: FixtureSeeder
    @Inject lateinit var runner: PortalFixtureRunner
    @Inject lateinit var matrixSnapshots: MatrixSnapshotService

    @BeforeEach
    fun setUp() {
        seeder.clear()
        matrixSnapshots.invalidateAll()
        runner.load(Path.of(javaClass.getResource("/portal-fixture")!!.toURI()))
    }

    @AfterEach
    fun tearDown() {
        seeder.clear()
        matrixSnapshots.invalidateAll()
    }

    @Test
    fun `people load with ranks mapped, tiers recorded, and the shared employee id flagged not merged`() {
        assertThat(runner.count("select count(p) from Person p")).isEqualTo(4)
        // "Junior Engineer" and the matrix's rank vocabulary collapse onto canonical positions.
        assertThat(
            runner.query(
                "select p.position.name from Person p where p.name = 'DUPLICATE, Dana'", String::class.java,
            ).single(),
        ).isEqualTo("Assistant Engineer")
        // "Chief Officer - 100m" → Chief Officer with the grade kept as a tier.
        assertThat(
            runner.query("select p.tier from Person p where p.sam = 'b22222B'", String::class.java).single(),
        ).isEqualTo("100m")
        // The duplicate id imports both rows (Person.sam is deliberately non-unique) and flags it.
        assertThat(runner.count("select count(p) from Person p where p.sam = 'a11111A'")).isEqualTo(2)
        assertThat(
            runner.count("select count(e) from ExceptionItem e where e.description like '%share employee id a11111A%'"),
        ).isEqualTo(1)
    }

    @Test
    fun `cells read conservatively - dates expire, Y is perpetual and cross-checked, N is a gap, ? is chased`() {
        assertThat(runner.count("select count(h) from QualificationHolding h")).isEqualTo(13)
        assertThat(
            runner.query(
                "select h.expiryDate from QualificationHolding h where h.statusValue = 'held_expiry' " +
                    "and h.person.name = 'TESTER, Alice' and h.requirement.code = 'QL-01'",
                LocalDate::class.java,
            ).single(),
        ).isEqualTo(LocalDate.of(2031, 5, 26))
        // The issue date joins in from the portal's certificate linkage, across name spellings.
        assertThat(
            runner.query(
                "select h.issueDate from QualificationHolding h " +
                    "where h.person.name = 'TESTER, Alice' and h.requirement.code = 'QL-01'",
                LocalDate::class.java,
            ).single(),
        ).isEqualTo(LocalDate.of(2026, 5, 26))
        // Y on a code the validity list says expires is imported held-perpetual and flagged;
        // Y on a never-expires code is not flagged.
        assertThat(
            runner.count("select count(h) from QualificationHolding h where h.statusValue = 'held_perpetual'"),
        ).isEqualTo(3)
        assertThat(
            runner.count("select count(e) from ExceptionItem e where e.description like '%''Y'' for QL-19%'"),
        ).isEqualTo(1)
        assertThat(
            runner.count("select count(e) from ExceptionItem e where e.description like '%''Y'' for QL-20%'"),
        ).isZero()
        // N is a gap the engine reports; ? is unknown plus a chase item.
        assertThat(
            runner.count("select count(h) from QualificationHolding h where h.statusValue = 'not_held'"),
        ).isEqualTo(2)
        assertThat(
            runner.count(
                "select count(h) from QualificationHolding h where h.statusValue = 'unknown' " +
                    "and h.person.sam = 'b22222B' and h.requirement.code = 'QL-19'",
            ),
        ).isEqualTo(1)
        assertThat(
            runner.count("select count(e) from ExceptionItem e where e.description like '[CHASE]%QL-19%'"),
        ).isEqualTo(1)
        // A certificate whose expiry disagrees with the grid is counted, never preferred.
        assertThat(
            runner.count("select count(e) from ExceptionItem e where e.description like '%disagrees with the matrix%HR-02%'"),
        ).isEqualTo(1)
        // PT-03 has no validity period: unassessed, flagged, dated holdings untouched.
        assertThat(
            runner.count("select count(e) from ExceptionItem e where e.description like '%No validity period%PT-03%'"),
        ).isEqualTo(1)
    }

    @Test
    fun `only the dated swing is seeded, with slots from the crew list and watches mapped to shifts`() {
        assertThat(runner.count("select count(c) from CrewChange c")).isEqualTo(1)
        assertThat(
            runner.query("select c.ccId from CrewChange c", String::class.java).single(),
        ).isEqualTo("CC02")
        // The cutoff is not in the portal: assumed a week before fly-out, and flagged.
        assertThat(
            runner.query("select c.cutoffDate from CrewChange c", LocalDate::class.java).single(),
        ).isEqualTo(LocalDate.of(2026, 9, 1))
        assertThat(
            runner.count("select count(e) from ExceptionItem e where e.description like '%no register cutoff%'"),
        ).isEqualTo(1)
        // Four crew-list entries → four slots; the unmatched name leaves its slot open, flagged.
        assertThat(runner.count("select count(s) from PositionSlot s")).isEqualTo(4)
        assertThat(runner.count("select count(a) from Assignment a")).isEqualTo(3)
        assertThat(
            runner.count("select count(e) from ExceptionItem e where e.description like '%Zoe Unknown%'"),
        ).isEqualTo(1)
        // "Chris Sample" reaches "SAMPLE, Christopher" by given-name prefix despite two SAMPLEs,
        // and his night watch maps to Shift 2.
        assertThat(
            runner.query(
                "select s.shiftValue from PositionSlot s where s.ref = 2", String::class.java,
            ).single(),
        ).isEqualTo("Shift 2")
        assertThat(
            runner.count(
                "select count(a) from Assignment a where a.slotRef = 2 and a.person.name = 'SAMPLE, Christopher'",
            ),
        ).isEqualTo(1)
    }

    @Test
    fun `the provisional matrix carries parsed quotas, one one-of set, footnote cells, and its caveat`() {
        assertThat(
            runner.query("select m.label from MatrixVersion m", String::class.java).single(),
        ).isEqualTo("Coolibah portal rev 7")
        assertThat(
            runner.count("select count(m) from MatrixVersion m where m.statusValue = 'published'"),
        ).isEqualTo(1)
        // Three quotas: QL-19 per shift (differing day/night minimums → the larger, flagged),
        // QL-20 per swing for GPH, HR-02 per shift for two positions.
        assertThat(runner.count("select count(q) from QuotaRule q")).isEqualTo(3)
        assertThat(
            runner.query(
                "select q.minCount from QuotaRule q where q.requirement.code = 'QL-19'",
                Integer::class.java,
            ).single().toInt(),
        ).isEqualTo(3)
        assertThat(
            runner.count("select count(e) from ExceptionItem e where e.description like '%differs between%shifts%'"),
        ).isEqualTo(1)
        // The parenthesised position phrase resolves, and the one-of set lands with two members.
        assertThat(runner.count("select count(c) from ConditionalRule c")).isEqualTo(1)
        assertThat(
            runner.count(
                "select count(m) from ConditionalRuleMember m " +
                    "where m.conditionalRule.position.name = 'Assistant Engineer'",
            ),
        ).isEqualTo(2)
        // Footnote cells exist exactly for the positions the rules name: GPH×QL-20,
        // (Chief Officer, GPH)×HR-02, Assistant Engineer×(QL-01, QL-17). Nothing mandatory.
        assertThat(runner.count("select count(r) from RequirementRule r")).isEqualTo(5)
        assertThat(runner.count("select count(r) from RequirementRule r where r.levelValue = 'M'")).isZero()
        // The single-code non-numeric rule is not guessed at, and the matrix says it is provisional.
        assertThat(
            runner.count("select count(e) from ExceptionItem e where e.description like '%PT-03%not machine-readable%'"),
        ).isEqualTo(1)
        assertThat(
            runner.count("select count(e) from ExceptionItem e where e.description like '%matrix is provisional%'"),
        ).isEqualTo(1)
    }

    @Test
    fun `accounts are one per person plus the five back-office roles, the Master supervising`() {
        assertThat(runner.count("select count(a) from UserAccount a where a.person is not null")).isEqualTo(4)
        assertThat(runner.count("select count(a) from UserAccount a where a.person is null")).isEqualTo(5)
        assertThat(
            runner.query(
                "select a.displayName from UserAccount a join a.roleAssignments r where r.role = 'vessel_master'",
                String::class.java,
            ),
        ).containsExactly("TESTER, Alice")
        assertThat(runner.count("select count(a) from UserAccount a where a.kindValue <> 'local_test'")).isZero()
    }
}
