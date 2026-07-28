package au.crewcomp.it

import au.crewcomp.compliance.MatrixSnapshotService
import au.crewcomp.platform.time.BusinessClock
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import io.restassured.specification.RequestSpecification
import jakarta.inject.Inject
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * ADM-8 and §9 — the notifications centre, the per-role fan-out, and the scheduled scans.
 *
 * Two properties are under test, and the scans exist to demonstrate the second:
 *
 *  * **Per recipient, not per role.** A role-routed event becomes one row per account holding the
 *    role, which is what keeps read state per-user (§6) without a second table.
 *  * **Idempotent.** Every scan is a daily recomputation of what is due, so running one twice must
 *    produce exactly what running it once did. Without that, the expiry warning nobody needs arrives
 *    ninety times and the one that matters is invisible.
 *
 * The clock is pinned throughout. The fixture swing is fixed at 2026-08-01..28 with a 2026-07-25
 * cutoff, and `BusinessClock` otherwise returns the real date — so whether a cutoff is "approaching"
 * or already past would depend on the day the suite happens to run.
 */
@QuarkusTest
@DisplayName("Notifications (ADM-8, §9)")
class NotificationIT {

    @Inject lateinit var seeder: FixtureSeeder
    @Inject lateinit var matrixSnapshots: MatrixSnapshotService
    @Inject lateinit var clock: BusinessClock

    private lateinit var seed: FixtureSeeder.Seed

    /** Inside the fixture's cutoff lead window (cutoff 2026-07-25, default lead 10 days). */
    private val pinnedToday = LocalDate.of(2026, 7, 20)

    @BeforeEach
    fun setUp() {
        seeder.clear()
        matrixSnapshots.invalidateAll()
        seed = seeder.seed()
        clock.overrideToday(pinnedToday)
    }

    @AfterEach
    fun tearDown() = clock.clearOverride()

    private fun asRoles(vararg roles: String, personId: Long? = null): RequestSpecification =
        given()
            .header("X-Dev-User", "Test ${roles.joinToString("+")}")
            .header("X-Dev-Roles", roles.joinToString(","))
            .contentType(ContentType.JSON)
            .apply { if (personId != null) header("X-Dev-Person-Id", personId.toString()) }

    private fun asCrew() = asRoles("crew_member", personId = seed.compliantPersonId)

    /**
     * Creates a back-office account holding [roles], so §9's fan-out has somewhere to land.
     *
     * SEC-1b transitional accounts, which is what ADM-10 creates until the identity spike provisions
     * real ones at first corporate sign-in. Without one, `raiseForRoles` correctly finds no
     * recipients and every back-office assertion below would pass vacuously.
     */
    private fun backOfficeAccount(vararg roles: String, name: String = "Back Office"): Int =
        asRoles("system_administrator")
            .body(
                """{"displayName":"$name","email":"back.office@example.test",
                    "roles":[${roles.joinToString(",") { "\"$it\"" }}]}""".trimIndent(),
            )
            .post("/api/v1/administration/users")
            .then()
            .statusCode(200)
            .body("kind", equalTo("local_test"))
            .extract()
            .path("id")

    private fun runJob(name: String) =
        asRoles("system_administrator")
            .post("/api/v1/administration/jobs/$name/run")
            .then()
            .statusCode(200)
            .body("outcome", equalTo("succeeded"))

    @Nested
    @DisplayName("The crew member's own list")
    inner class CrewList {

        @Test
        fun `a crew member reads their own notifications and marks them read`() {
            asCrew()
                .get("/api/v1/notifications")
                .then()
                .statusCode(200)
                .body("$", hasSize<Any>(1))
                .body("[0].kind", equalTo("expiry_warning"))
                .body("[0].audience", equalTo("crew"))
                .body("[0].read", equalTo(false))
                // SEC-13: the title carries no qualification name, because it is the only field a
                // push payload may include.
                .body("[0].title", not(containsString("Medical")))

            asCrew().get("/api/v1/notifications/summary").then().body("unread", equalTo(1))

            asCrew()
                .post("/api/v1/notifications/${seed.notificationId}/read")
                .then()
                .statusCode(204)

            asCrew().get("/api/v1/notifications/summary").then().body("unread", equalTo(0))
            asCrew().get("/api/v1/notifications?state=unread").then().body("$", hasSize<Any>(0))
        }

        /**
         * The gap crew member has a Person record but no account — the ordinary state before
         * onboarding (§4.3) — and the seeded notification belongs to someone else.
         *
         * This is the case that caught a real hole: the back-office role proxy, which lets an
         * account-less actor read the notifications addressed to accounts holding their roles, would
         * have handed this crew member every *other* crew member's notifications. A crew-shaped
         * actor now reads their own account's rows or nothing.
         */
        @Test
        fun `an account-less crew member sees nothing, not their colleagues' notifications`() {
            val other = asRoles("crew_member", personId = seed.gapPersonId)

            other.get("/api/v1/notifications").then().statusCode(200).body("$", hasSize<Any>(0))
            other.get("/api/v1/notifications/summary").then().body("unread", equalTo(0))

            // Absent and invisible answer alike, so the status code cannot enumerate ids.
            asRoles("crew_member", personId = seed.gapPersonId)
                .post("/api/v1/notifications/${seed.notificationId}/read")
                .then()
                .statusCode(404)
        }

        @Test
        fun `a read-mark is monotonic, so a replaying device cannot unset it`() {
            asCrew().post("/api/v1/notifications/${seed.notificationId}/read").then().statusCode(204)
            val firstReadAt: String = asCrew()
                .get("/api/v1/notifications")
                .then()
                .extract()
                .path("[0].readAt")

            // §7.6: the mobile outbound queue replays read-marks freely.
            asCrew().post("/api/v1/notifications/${seed.notificationId}/read").then().statusCode(204)

            asCrew().get("/api/v1/notifications").then().body("[0].readAt", equalTo(firstReadAt))
        }
    }

    @Nested
    @DisplayName("Per-role fan-out (§9)")
    inner class RoleFanOut {

        @Test
        fun `a register event reaches the Workflow Manager, once per account`() {
            val first = backOfficeAccount("workflow_manager", name = "WM One")
            val second = backOfficeAccount("workflow_manager", name = "WM Two")
            // A Data Steward holds a different role and must not receive a register event.
            backOfficeAccount("data_steward", name = "Steward")

            asRoles("crew_coordinator")
                .body(
                    """{"type":"Exemption Request - PW","partnership":"UNI","cc":"CC24",
                        "personId":${seed.gapPersonId},"requirementId":${seed.medRequirementId}}""".trimIndent(),
                )
                .post("/api/v1/register")
                .then()
                .statusCode(200)

            asRoles("workflow_manager")
                .get("/api/v1/notifications")
                .then()
                .statusCode(200)
                // Two rows for one event: one per Workflow Manager account. That is §9's
                // "notification rows per recipient" with per-role routing, and it is what makes the
                // read state below each person's own.
                .body("findAll { it.kind == 'register_event' }", hasSize<Any>(2))
                .body("findAll { it.recipient == 'WM One' }", hasSize<Any>(1))
                .body("findAll { it.recipient == 'WM Two' }", hasSize<Any>(1))
                .body("find { it.kind == 'register_event' }.audience", equalTo("back_office"))

            asRoles("data_steward")
                .get("/api/v1/notifications")
                .then()
                .body("findAll { it.kind == 'register_event' }", hasSize<Any>(0))

            // Both accounts exist and both have a row — proving the fan-out wrote two, not one
            // shared row that two people happen to see.
            check(first != second)
        }

        @Test
        fun `publishing a matrix tells everyone who plans against it`() {
            backOfficeAccount("crew_coordinator", name = "Coordinator")

            val draftId: Int = asRoles("compliance_lead")
                .body("""{"label":"notify-v2","copyFromVersionId":${seed.matrixVersionId}}""")
                .post("/api/v1/matrix-versions")
                .then().statusCode(200).extract().path("id")

            asRoles("compliance_lead")
                .post("/api/v1/matrix-versions/$draftId/publish")
                .then()
                .statusCode(200)

            asRoles("crew_coordinator")
                .get("/api/v1/notifications")
                .then()
                .body("find { it.kind == 'matrix_published' }.body", containsString("notify-v2"))
                .body("find { it.kind == 'matrix_published' }.deepLink", equalTo("/matrix"))
        }

        @Test
        fun `an exception raised tells the Data Steward`() {
            backOfficeAccount("data_steward", name = "Steward")

            asRoles("data_steward")
                .body("""{"area":"holdings","description":"Sam # 001 appears twice"}""")
                .post("/api/v1/exceptions")
                .then()
                .statusCode(200)

            asRoles("data_steward")
                .get("/api/v1/notifications")
                .then()
                .body("find { it.kind == 'exception_raised' }.body", containsString("appears twice"))
        }

        @Test
        fun `with no back-office account the write still succeeds and raises nothing`() {
            // The state of every deployment until the identity spike creates accounts. A workflow
            // write must not depend on a notification having somewhere to go.
            asRoles("crew_coordinator")
                .body(
                    """{"type":"Exemption Request - PW","partnership":"UNI","cc":"CC24",
                        "personId":${seed.gapPersonId},"requirementId":${seed.medRequirementId}}""".trimIndent(),
                )
                .post("/api/v1/register")
                .then()
                .statusCode(200)

            asRoles("workflow_manager")
                .get("/api/v1/notifications")
                .then()
                .statusCode(200)
                .body("$", hasSize<Any>(0))
        }
    }

    @Nested
    @DisplayName("Scheduled scans (§9)")
    inner class Scans {

        @Test
        fun `the expiry scan warns the crew member, and running it twice changes nothing`() {
            // The seeded medical expires 2027-08-01; pinned "today" is 2026-07-20, so a lead window
            // wide enough to reach it has to be set.
            asRoles("system_administrator")
                .body("""{"value":500}""")
                .put("/api/v1/administration/config/expiry.lead-days")
                .then()
                .statusCode(200)
                .body("value", equalTo(500))
                .body("overridden", equalTo(true))

            runJob("expiry-scan").body("detail", containsString("expiries within 500 days"))

            val afterFirst: Int = asCrew()
                .get("/api/v1/notifications")
                .then()
                .statusCode(200)
                .extract()
                .path("size()")
            check(afterFirst > 1) { "the scan should have added an expiry warning to the seeded one" }

            // The whole point of the dedupe key: tomorrow's run must not repeat today's warning.
            runJob("expiry-scan")
            asCrew().get("/api/v1/notifications").then().body("size()", equalTo(afterFirst))
        }

        @Test
        fun `the expiry scan stops chasing a crew member who has answered, and tells coordinators anyway`() {
            backOfficeAccount("crew_coordinator", name = "Coordinator")

            // Move the medical so it lapses part-way through the seeded swing (2026-08-01..28).
            // The fixture's own expiry is a year out, which is inside no assignment and therefore
            // raises nothing for a coordinator — the half of this test that has to *not* be
            // suppressed would pass vacuously.
            asRoles("data_steward")
                .body("""{"status":"held_expiry","expiry":"2026-08-15"}""")
                .put("/api/v1/people/${seed.compliantPersonId}/holdings/${seed.medRequirementId}")
                .then()
                .statusCode(200)

            val beforeAnswering: Int = asCrew().get("/api/v1/notifications").then().extract().path("size()")

            // MOB-5's one tap: "I have booked the course."
            asCrew()
                .body(
                    """{"operations":[{"opId":"op-answered","type":"requirement.progress",
                        "requirementId":${seed.medRequirementId}}]}""".trimIndent(),
                )
                .post("/api/v1/sync/queue")
                .then()
                .statusCode(200)
                .body("results[0].status", equalTo("applied"))

            runJob("expiry-scan")
                .body("detail", containsString("1 already answered"))
                .body("detail", containsString("0 addressed to crew"))

            // The crew member is not asked again about the expiry they just answered — the only
            // notification they have is the one the fixture seeded.
            asCrew().get("/api/v1/notifications").then().body("size()", equalTo(beforeAnswering))

            // The coordinator still hears about it. A booked course is not a held certificate, and
            // somebody deciding whether to crew a swing needs the risk rather than the reassurance.
            asRoles("crew_coordinator")
                .get("/api/v1/notifications")
                .then()
                .body("findAll { it.kind == 'expiry_affects_roster' }.size()", greaterThan(0))
        }

        @Test
        fun `a crew member's one-tap answer reaches the coordinator`() {
            backOfficeAccount("crew_coordinator", name = "Coordinator")

            asCrew()
                .body(
                    """{"operations":[{"opId":"op-help","type":"requirement.help",
                        "requirementId":${seed.medRequirementId}}]}""".trimIndent(),
                )
                .post("/api/v1/sync/queue")
                .then()
                .statusCode(200)
                .body("results[0].status", equalTo("applied"))

            asRoles("crew_coordinator")
                .get("/api/v1/notifications")
                .then()
                // SEC-13: the title is the only field a push may carry, so it names neither the
                // person nor the qualification. Both are in the body, which is fetched in-app.
                .body("find { it.kind == 'crew_help_requested' }.title", equalTo("A crew member has asked for help"))
                .body("find { it.kind == 'crew_help_requested' }.body", containsString("SAM001"))
        }

        @Test
        fun `the cutoff scan warns coordinators about an approaching cutoff, once`() {
            backOfficeAccount("crew_coordinator", name = "Coordinator")

            runJob("cutoff-scan").body("detail", containsString("cutoffs within"))

            asRoles("crew_coordinator")
                .get("/api/v1/notifications")
                .then()
                .body("findAll { it.kind == 'cutoff_approaching' }", hasSize<Any>(1))
                .body("find { it.kind == 'cutoff_approaching' }.body", containsString("UNI CC24"))
                // 2026-07-25 minus the pinned 2026-07-20.
                .body("find { it.kind == 'cutoff_approaching' }.body", containsString("in 5 days"))

            runJob("cutoff-scan")
            asRoles("crew_coordinator")
                .get("/api/v1/notifications")
                .then()
                .body("findAll { it.kind == 'cutoff_approaching' }", hasSize<Any>(1))
        }

        @Test
        fun `the roster scan reports the known quota shortfall to coordinators`() {
            backOfficeAccount("crew_coordinator", name = "Coordinator")

            runJob("roster-scan").body("detail", containsString("upcoming swings scanned"))

            asRoles("crew_coordinator")
                .get("/api/v1/notifications")
                .then()
                // The §11 acceptance shape: UNI CC24 shift 1 has no GPH with Work at Heights.
                .body("find { it.kind == 'quota_shortfall' }.body", containsString("UNI CC24"))
                .body("find { it.kind == 'quota_shortfall' }.body", containsString("Shift 1"))
                .body("find { it.kind == 'quota_shortfall' }.body", containsString("PS-04"))

            runJob("roster-scan")
            asRoles("crew_coordinator")
                .get("/api/v1/notifications")
                .then()
                .body("findAll { it.kind == 'quota_shortfall' }", hasSize<Any>(1))
        }

        @Test
        fun `only a System Administrator may trigger a job, and everyone may see the schedule`() {
            asRoles("crew_coordinator")
                .post("/api/v1/administration/jobs/expiry-scan/run")
                .then()
                .statusCode(403)

            asRoles("crew_coordinator")
                .get("/api/v1/administration/jobs")
                .then()
                .statusCode(200)
                .body("size()", greaterThan(3))
                .body("find { it.name == 'expiry-scan' }.schedule", equalTo("0 15 6 * * ?"))

            asRoles("system_administrator")
                .post("/api/v1/administration/jobs/no-such-job/run")
                .then()
                .statusCode(200)
                .body("outcome", equalTo("not_found"))
        }

        @Test
        fun `a job's last run is visible in the health view`() {
            runJob("cutoff-scan")

            asRoles("system_administrator")
                .get("/api/v1/administration/jobs")
                .then()
                .statusCode(200)
                .body("find { it.name == 'cutoff-scan' }.lastRun.outcome", equalTo("succeeded"))
                .body("find { it.name == 'cutoff-scan' }.lastRun.detail", containsString("cutoffs"))
        }
    }

    @Nested
    @DisplayName("Read-all")
    inner class ReadAll {

        @Test
        fun `clears the whole list for the caller and nobody else`() {
            backOfficeAccount("workflow_manager", name = "WM One")
            asRoles("crew_coordinator")
                .body(
                    """{"type":"Exemption Request - PW","partnership":"UNI","cc":"CC24",
                        "personId":${seed.gapPersonId},"requirementId":${seed.medRequirementId}}""".trimIndent(),
                )
                .post("/api/v1/register")
                .then().statusCode(200)

            asRoles("workflow_manager")
                .post("/api/v1/notifications/read-all")
                .then()
                .statusCode(200)
                .body("unread", equalTo(0))

            // The crew member's own unread notification is untouched.
            asCrew().get("/api/v1/notifications/summary").then().body("unread", equalTo(1))
        }
    }
}
