package au.crewcomp.it

import au.crewcomp.compliance.MatrixSnapshotService
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import jakarta.inject.Inject
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The HTTP contract the admin SPA is generated against.
 *
 * [ComplianceIT] proves the engine, mapping and audit trail work against a real database by
 * calling services directly. This proves the layer above: that authentication populates an
 * actor, that authorisation and row-scoping survive the trip through JAX-RS, that failures map
 * to the status codes the client branches on, and that the JSON field names the generated
 * TypeScript expects are the ones actually emitted.
 *
 * Requests authenticate through the development shim (`X-Dev-Roles`), which is present in the
 * test profile and absent from a production build — see `DevAuth`.
 */
@QuarkusTest
@DisplayName("Admin API contract")
class ApiIT {

    @Inject lateinit var seeder: FixtureSeeder
    @Inject lateinit var matrixSnapshots: MatrixSnapshotService

    private lateinit var seed: FixtureSeeder.Seed

    @BeforeEach
    fun setUp() {
        seeder.clear()
        matrixSnapshots.invalidateAll()
        seed = seeder.seed()
    }

    private fun asRoles(vararg roles: String, personId: Long? = null) =
        given()
            .header("X-Dev-User", "Test ${roles.joinToString("+")}")
            .header("X-Dev-Roles", roles.joinToString(","))
            .apply { if (personId != null) header("X-Dev-Person-Id", personId.toString()) }

    @Nested
    @DisplayName("Session")
    inner class SessionEndpoint {

        @Test
        fun `an unauthenticated request is refused`() {
            // No X-Dev-Roles and no configured default: the request is anonymous, and every
            // endpoint under @Authenticated must answer 401 rather than fall through actorless.
            given().get("/api/v1/session").then().statusCode(401)
            given().get("/api/v1/people").then().statusCode(401)
            given().get("/api/v1/swings/UNI/CC24/evaluation").then().statusCode(401)
        }

        @Test
        fun `reports the actor, their roles and the business date`() {
            asRoles("data_steward", "crew_coordinator")
                .get("/api/v1/session")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("label", containsString("Test"))
                .body("roles", contains("crew_coordinator", "data_steward"))
                .body("today", notNullValue())
                .body("dateOverridden", equalTo(false))
        }
    }

    @Nested
    @DisplayName("Reference data")
    inner class Reference {

        @Test
        fun `a back-office role sees every partnership`() {
            asRoles("crew_coordinator")
                .get("/api/v1/partnerships")
                .then()
                .statusCode(200)
                .body("abbrev", contains("UNI"))
        }

        @Test
        fun `a Vessel Master sees only the partnerships they are scoped to`() {
            // No X-Dev-Partnerships, so the scope is empty and the list is empty — the scoped
            // role gets nothing rather than everything when its scope is unset.
            asRoles("vessel_master")
                .get("/api/v1/partnerships")
                .then()
                .statusCode(200)
                .body("$", hasSize<Any>(0))

            given()
                .header("X-Dev-Roles", "vessel_master")
                .header("X-Dev-Partnerships", seed.partnershipId.toString())
                .get("/api/v1/partnerships")
                .then()
                .statusCode(200)
                .body("abbrev", contains("UNI"))
        }

        @Test
        fun `the swing calendar is scoped to its partnership`() {
            asRoles("crew_coordinator")
                .get("/api/v1/partnerships/UNI/crew-changes")
                .then()
                .statusCode(200)
                .body("ccId", contains("CC24"))
                .body("[0].from", equalTo("2026-08-01"))
                .body("[0].cutoff", equalTo("2026-07-25"))
        }

        @Test
        fun `an unknown partnership is not found`() {
            asRoles("crew_coordinator")
                .get("/api/v1/partnerships/NOPE/crew-changes")
                .then()
                .statusCode(404)
                .body("error", equalTo("not_found"))
        }

        @Test
        fun `the requirement catalogue carries the codes the client renders`() {
            asRoles("crew_coordinator")
                .get("/api/v1/requirements")
                .then()
                .statusCode(200)
                .body("code", hasItem("PS-04"))
                .body("find { it.code == 'PS-04' }.title", equalTo("Work at Heights"))
                .body("find { it.code == 'PS-04' }.category", equalTo("PS"))
        }
    }

    @Nested
    @DisplayName("People")
    inner class People {

        @Test
        fun `a back-office role sees the whole directory`() {
            asRoles("data_steward")
                .get("/api/v1/people")
                .then()
                .statusCode(200)
                .body("$", hasSize<Any>(2))
                .body("sam", hasItem("SAM001"))
                .body("find { it.sam == 'SAM001' }.positionName", equalTo("GPH"))
                .body("find { it.sam == 'SAM001' }.partnershipAbbrev", equalTo("UNI"))
        }

        @Test
        fun `a crew member sees only themselves`() {
            // AUTH-2: the scope is applied centrally, and the endpoint takes no filter argument
            // that could have been forgotten.
            asRoles("crew_member", personId = seed.gapPersonId)
                .get("/api/v1/people")
                .then()
                .statusCode(200)
                .body("$", hasSize<Any>(1))
                .body("[0].id", equalTo(seed.gapPersonId.toInt()))
        }

        @Test
        fun `another person is not found rather than forbidden, for a scoped reader`() {
            // 403 would confirm the row exists, letting a crew member enumerate the person table
            // by status code. Absent and invisible must be indistinguishable.
            asRoles("crew_member", personId = seed.gapPersonId)
                .get("/api/v1/people/${seed.compliantPersonId}")
                .then()
                .statusCode(404)

            asRoles("crew_member", personId = seed.gapPersonId)
                .get("/api/v1/people/99999")
                .then()
                .statusCode(404)
        }

        @Test
        fun `holdings come back grouped-ready, with the requirement id the catalogue joins on`() {
            asRoles("data_steward")
                .get("/api/v1/people/${seed.compliantPersonId}/holdings")
                .then()
                .statusCode(200)
                .body("$", hasSize<Any>(2))
                .body("find { it.requirementId == ${seed.medRequirementId} }.status", equalTo("held_expiry"))
                .body("find { it.requirementId == ${seed.wahRequirementId} }.status", equalTo("held_perpetual"))
                .body("find { it.requirementId == ${seed.wahRequirementId} }.expiry", equalTo(null))
        }
    }

    @Nested
    @DisplayName("Holding writes")
    inner class HoldingWrites {

        @Test
        fun `a Data Steward may set a holding`() {
            asRoles("data_steward")
                .contentType(ContentType.JSON)
                .body("""{"status":"held_expiry","expiry":"2027-06-30","note":"Renewed"}""")
                .put("/api/v1/people/${seed.gapPersonId}/holdings/${seed.medRequirementId}")
                .then()
                .statusCode(200)
                .body("status", equalTo("held_expiry"))
                .body("expiry", equalTo("2027-06-30"))
                .body("note", equalTo("Renewed"))
        }

        @Test
        fun `a Vessel Master may not - and is told why`() {
            asRoles("vessel_master")
                .contentType(ContentType.JSON)
                .body("""{"status":"held_perpetual"}""")
                .put("/api/v1/people/${seed.gapPersonId}/holdings/${seed.medRequirementId}")
                .then()
                .statusCode(403)
                .body("error", equalTo("forbidden"))
                .body("detail", containsString("vessel_master"))
        }

        @Test
        fun `an expiring holding without an expiry date is rejected before it reaches the database`() {
            asRoles("data_steward")
                .contentType(ContentType.JSON)
                .body("""{"status":"held_expiry"}""")
                .put("/api/v1/people/${seed.gapPersonId}/holdings/${seed.medRequirementId}")
                .then()
                .statusCode(400)
                .body("error", equalTo("invalid_request"))
                .body("detail", containsString("expiry date"))
        }
    }

    @Nested
    @DisplayName("Engine-derived reads")
    inner class EngineReads {

        @Test
        fun `a swing evaluation carries the fields the planner renders`() {
            asRoles("crew_coordinator")
                .get("/api/v1/swings/UNI/CC24/evaluation")
                .then()
                .statusCode(200)
                .body("ccId", equalTo("CC24"))
                .body("from", equalTo("2026-08-01"))
                .body("assignments", hasSize<Any>(2))
                .body("assignments[0].evaluation.rollUp", notNullValue())
                .body("assignments[0].evaluation.cells", notNullValue())
                .body("quotas", hasSize<Any>(2))
                .body("stateCounts", notNullValue())
        }

        @Test
        fun `the known UNI shortfall is visible over HTTP`() {
            // The §11 acceptance shape: shift 1 has no GPH with Work at Heights.
            asRoles("crew_coordinator")
                .get("/api/v1/swings/UNI/CC24/quotas")
                .then()
                .statusCode(200)
                // Appendix A wire values, verbatim: the shift is "Shift 1", not "1".
                .body("find { it.shift == 'Shift 1' }.satisfied", equalTo(false))
                .body("find { it.shift == 'Shift 1' }.actual", equalTo(0))
                .body("find { it.shift == 'Shift 1' }.min", equalTo(1))
                .body("find { it.shift == 'Shift 2' }.satisfied", equalTo(true))
        }

        @Test
        fun `the gap report arrives in worklist order`() {
            asRoles("crew_coordinator")
                .get("/api/v1/swings/UNI/CC24/gaps")
                .then()
                .statusCode(200)
                .body("[0].state", equalTo("gap"))
                .body("[0].sam", equalTo("SAM002"))
        }

        @Test
        fun `a crew member may evaluate themselves but not a colleague`() {
            asRoles("crew_member", personId = seed.gapPersonId)
                .get("/api/v1/people/${seed.gapPersonId}/evaluation?partnership=UNI&cc=CC24")
                .then()
                .statusCode(200)
                .body("personId", equalTo(seed.gapPersonId.toInt()))

            asRoles("crew_member", personId = seed.gapPersonId)
                .get("/api/v1/people/${seed.compliantPersonId}/evaluation?partnership=UNI&cc=CC24")
                .then()
                .statusCode(403)
        }

        @Test
        fun `suggestions are a coordinator activity`() {
            asRoles("data_steward")
                .get("/api/v1/swings/UNI/CC24/suggestions?slotRef=15")
                .then()
                .statusCode(403)

            // Both seeded crew are already on this swing, and §5.4 excludes anyone already
            // assigned to it — so an empty ranking is the correct answer here, not a thin test.
            // Ranking itself is covered by the pure PlanningTest cases; what this asserts is that
            // the endpoint authorises, runs and answers with a list.
            asRoles("crew_coordinator")
                .get("/api/v1/swings/UNI/CC24/suggestions?slotRef=15&limit=5")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", equalTo(0))
        }
    }
}
