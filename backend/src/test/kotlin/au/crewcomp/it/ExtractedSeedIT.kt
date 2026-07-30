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

/**
 * The extracted-dataset loader against real PostgreSQL, driven by the **fictional** fixture in
 * `src/test/resources/extract-fixture` — shaped like the POC extracts, anomalies included, with
 * invented people. The real extracts are personal data and are never committed; what this proves
 * is that every fix-up class the real data contains survives the schema's constraints.
 */
@QuarkusTest
@DisplayName("§11 extracted dataset loader")
class ExtractedSeedIT {

    @Inject lateinit var seeder: FixtureSeeder
    @Inject lateinit var runner: ExtractFixtureRunner
    @Inject lateinit var matrixSnapshots: MatrixSnapshotService

    @BeforeEach
    fun setUp() {
        seeder.clear()
        matrixSnapshots.invalidateAll()
        runner.load(Path.of(javaClass.getResource("/extract-fixture")!!.toURI()))
    }

    @AfterEach
    fun tearDown() {
        seeder.clear()
        matrixSnapshots.invalidateAll()
    }

    @Test
    fun `people load with junk rows dropped, tiers split, and stubs for roster and register sams`() {
        assertThat(runner.count("select count(p) from Person p")).isEqualTo(5) // 3 real + 2 stubs
        assertThat(runner.count("select count(p) from Person p where p.statusValue = 'lookup_only'"))
            .isEqualTo(2) // d44444D from the roster, e55555E from the register
        assertThat(
            runner.query(
                "select p.tier from Person p where p.sam = 'b22222B'", String::class.java,
            ).single(),
        ).isEqualTo("Unlimited")
        // The roster stub takes the slot's first allowed position; slot 11 is GPH.
        assertThat(
            runner.query(
                "select p.position.name from Person p where p.sam = 'd44444D'", String::class.java,
            ).single(),
        ).isEqualTo("GPH")
    }

    @Test
    fun `holdings load, with the uncoded column joined to the catalogue's unassigned row`() {
        assertThat(runner.count("select count(h) from QualificationHolding h")).isEqualTo(8)
        assertThat(
            runner.count(
                "select count(h) from QualificationHolding h " +
                    "where h.person.sam = 'c33333C' and h.requirement.code = '(UNASSIGNED)'",
            ),
        ).isEqualTo(1)
    }

    @Test
    fun `both matrix versions load with the CoC one-of collapse and the notes-block rules`() {
        assertThat(
            runner.query("select m.label from MatrixVersion m order by m.label", String::class.java),
        ).containsExactlyInAnyOrder("Matrix 29.06.2026", "Template – Matrix (archive)")
        assertThat(
            runner.count("select count(m) from MatrixVersion m where m.statusValue = 'published'"),
        ).isEqualTo(1)
        // The three CoC cells collapse to M8 in every version, QL-01 included (Master supersedes).
        assertThat(
            runner.count(
                "select count(r) from RequirementRule r where r.position.name = 'Chief Officer' " +
                    "and r.requirement.code in ('QL-01','QL-02','QL-03') and r.levelValue = 'M8'",
            ),
        ).isEqualTo(6)
        assertThat(runner.count("select count(c) from ConditionalRule c")).isEqualTo(6) // 3 × 2 versions
        assertThat(runner.count("select count(q) from QuotaRule q")).isEqualTo(10) // 5 × 2 versions
        // The slot divergence reaches the worklist, once, for the published version.
        assertThat(
            runner.count(
                "select count(e) from ExceptionItem e where e.description like '%one-of set M8%'",
            ),
        ).isEqualTo(1)
    }

    @Test
    fun `register anomalies land inside Appendix A's vocabulary, flagged rather than cleaned`() {
        assertThat(runner.count("select count(r) from RegisterRecord r")).isEqualTo(5)

        // No ID → placeholder; no status → derived from the outcome; long-form date parsed.
        assertThat(
            runner.query(
                "select r.statusValue from RegisterRecord r where r.recordId = 'UNREC-01'",
                String::class.java,
            ).single(),
        ).isEqualTo("Closed - Info Required")
        assertThat(
            runner.query(
                "select r.raisedDate from RegisterRecord r where r.recordId = 'UNREC-01'",
                java.time.LocalDate::class.java,
            ).single(),
        ).isEqualTo(java.time.LocalDate.of(2026, 1, 23))
        // The en-dash legacy title resolves through the alias table.
        assertThat(
            runner.query(
                "select r.requirement.code from RegisterRecord r where r.recordId = 'UNREC-01'",
                String::class.java,
            ).single(),
        ).isEqualTo("QL-13")

        // Closed by a party with no outcome: Admin Action label, outcome stays unrecorded.
        assertThat(
            runner.query(
                "select r.statusValue from RegisterRecord r where r.recordId = 'UNICC23-7'",
                String::class.java,
            ).single(),
        ).isEqualTo("Closed - Admin Action")
        assertThat(
            runner.count("select count(r) from RegisterRecord r where r.recordId = 'UNICC23-7' and r.outcomeValue is null"),
        ).isEqualTo(1)
        assertThat(
            runner.query(
                "select r.reqRaw from RegisterRecord r where r.recordId = 'UNICC23-7'",
                String::class.java,
            ).single(),
        ).isEqualTo("Ye Olde Legacy Cert")

        // Approved with no approval window: never in effect, so the outcome is unrecorded (§5.1).
        assertThat(
            runner.query(
                "select r.statusValue from RegisterRecord r where r.recordId = 'UNICC24-9'",
                String::class.java,
            ).single(),
        ).isEqualTo("Closed - Approved")
        assertThat(
            runner.count("select count(r) from RegisterRecord r where r.recordId = 'UNICC24-9' and r.outcomeValue is null"),
        ).isEqualTo(1)

        // The clean row keeps everything: outcome, window, condition, multi-line note, alias title.
        assertThat(
            runner.query(
                "select r.outcomeValue from RegisterRecord r where r.recordId = 'UNICC24-1'",
                String::class.java,
            ).single(),
        ).isEqualTo("Approved")
        assertThat(
            runner.query(
                "select r.requirement.code from RegisterRecord r where r.recordId = 'UNICC24-1'",
                String::class.java,
            ).single(),
        ).isEqualTo("PT-03")
        assertThat(
            runner.count("select count(c) from ApprovalCondition c where c.registerRecord.recordId = 'UNICC24-1'"),
        ).isEqualTo(1)
        assertThat(
            runner.query(
                "select n.body from RegisterNote n where n.registerRecord.recordId = 'UNICC24-1' and n.party = 'PW'",
                String::class.java,
            ).single(),
        ).contains("Course fully booked.\nRequesting exemption")

        // Empty type derives the weaker claim; a row with no Sam # links to no person.
        assertThat(
            runner.query(
                "select r.typeValue from RegisterRecord r where r.recordId = 'UNICC25-2'",
                String::class.java,
            ).single(),
        ).isEqualTo("PW Query")
        assertThat(
            runner.count("select count(r) from RegisterRecord r where r.recordId = 'UNICC25-2' and r.person is null"),
        ).isEqualTo(1)
    }

    @Test
    fun `the worklist carries the extract's items and the import's own flags`() {
        assertThat(
            runner.count("select count(e) from ExceptionItem e where e.description like '[CHASE]%'"),
        ).isEqualTo(1)
        assertThat(
            runner.count("select count(e) from ExceptionItem e where e.description like '%without an approval window%'"),
        ).isEqualTo(1)
        // The inverted CC30 window is skipped and flagged, never guessed at.
        assertThat(
            runner.count("select count(e) from ExceptionItem e where e.description like '%window inverted at source%'"),
        ).isEqualTo(1)
        assertThat(runner.count("select count(c) from CrewChange c")).isEqualTo(3)
        assertThat(
            runner.count("select count(e) from ExceptionItem e where e.state = 'open'"),
        ).isGreaterThanOrEqualTo(6L)
    }

    @Test
    fun `accounts are one per active person plus the five back-office roles, Masters supervising`() {
        assertThat(runner.count("select count(a) from UserAccount a where a.person is not null")).isEqualTo(3)
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
