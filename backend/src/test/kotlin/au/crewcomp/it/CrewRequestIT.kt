package au.crewcomp.it

import au.crewcomp.compliance.MatrixSnapshotService
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import io.restassured.specification.RequestSpecification
import jakarta.inject.Inject
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * ADM-11 — the crew request queue.
 *
 * The queue exists because a notification is a "something happened" signal and nothing else: it is
 * addressed to whoever held the role at the moment it was raised, one person marks it read, and no
 * query anywhere answers "what has the crew asked us for that nobody has dealt with". These tests
 * are mostly about the properties that make it a *worklist* rather than a second inbox — a decision
 * is terminal, a decision needs a reason, and a dismissal has a consequence.
 */
@QuarkusTest
@DisplayName("Crew request queue (ADM-11)")
class CrewRequestIT {

    @Inject lateinit var seeder: FixtureSeeder
    @Inject lateinit var matrixSnapshots: MatrixSnapshotService

    private lateinit var seed: FixtureSeeder.Seed

    @BeforeEach
    fun setUp() {
        seeder.clear()
        matrixSnapshots.invalidateAll()
        seed = seeder.seed()
    }

    private fun asRoles(vararg roles: String, personId: Long? = null): RequestSpecification =
        given()
            .header("X-Dev-User", "Test ${roles.joinToString("+")}")
            .header("X-Dev-Roles", roles.joinToString(","))
            .contentType(ContentType.JSON)
            .apply { if (personId != null) header("X-Dev-Person-Id", personId.toString()) }

    /** Raises one request from the crew app and returns its queue id. */
    private fun raise(kind: String = "requirement.help", opId: String = "op-1"): Int {
        asRoles("crew_member", personId = seed.compliantPersonId)
            .body(
                """{"operations":[{"opId":"$opId","type":"$kind",
                    "requirementId":${seed.medRequirementId}}]}""".trimIndent(),
            )
            .post("/api/v1/sync/queue")
            .then()
            .statusCode(200)
            .body("results[0].status", equalTo("applied"))

        return asRoles("crew_coordinator")
            .get("/api/v1/crew-requests?status=open")
            .then()
            .statusCode(200)
            .extract()
            .path("[-1].id")
    }

    @Nested
    @DisplayName("The queue")
    inner class Queue {

        @Test
        fun `is empty until a crew member asks for something`() {
            asRoles("crew_coordinator").get("/api/v1/crew-requests").then()
                .statusCode(200).body("size()", equalTo(0))
            asRoles("crew_coordinator").get("/api/v1/crew-requests/open-count").then()
                .body("open", equalTo(0))
        }

        @Test
        fun `carries enough to triage a row without a second call`() {
            raise()

            asRoles("crew_coordinator")
                .get("/api/v1/crew-requests")
                .then()
                .statusCode(200)
                .body("[0].kind", equalTo("help_requested"))
                .body("[0].status", equalTo("open"))
                .body("[0].sam", equalTo("SAM001"))
                .body("[0].personName", equalTo("Compliant Crew"))
                .body("[0].positionName", notNullValue())
                .body("[0].partnershipAbbrev", notNullValue())
                .body("[0].code", notNullValue())
                .body("[0].title", notNullValue())
                .body("[0].raisedAt", notNullValue())
                .body("[0].decidedAt", nullValue())
        }

        @Test
        fun `filters by status, and counts what is outstanding`() {
            val first = raise(opId = "op-a")
            raise(opId = "op-b")

            asRoles("crew_coordinator").get("/api/v1/crew-requests/open-count")
                .then().body("open", equalTo(2))

            asRoles("crew_coordinator")
                .body("""{"note":"Booked them onto the August course."}""")
                .post("/api/v1/crew-requests/$first/action")
                .then().statusCode(200).body("status", equalTo("actioned"))

            asRoles("crew_coordinator").get("/api/v1/crew-requests?status=open")
                .then().body("size()", equalTo(1))
            asRoles("crew_coordinator").get("/api/v1/crew-requests?status=actioned")
                .then().body("size()", equalTo(1))
            asRoles("crew_coordinator").get("/api/v1/crew-requests")
                .then().body("size()", equalTo(2))
            asRoles("crew_coordinator").get("/api/v1/crew-requests/open-count")
                .then().body("open", equalTo(1))
        }

        @Test
        fun `is refused without authentication`() {
            given().get("/api/v1/crew-requests").then().statusCode(401)
        }

        @Test
        fun `is not readable by the crew member who raised it`() {
            raise()

            // The queue is a back-office worklist over the whole fleet. A crew member reading it
            // would read every other crew member's requests, which is a scope question rather than
            // a UI one (AUTH-2).
            asRoles("crew_member", personId = seed.compliantPersonId)
                .get("/api/v1/crew-requests")
                .then()
                .statusCode(403)
        }
    }

    @Nested
    @DisplayName("Deciding one")
    inner class Deciding {

        @Test
        fun `refuses a decision with no note`() {
            val id = raise()

            asRoles("crew_coordinator")
                .body("""{"note":"   "}""")
                .post("/api/v1/crew-requests/$id/action")
                .then()
                .statusCode(400)

            // A queue emptied with no explanation is indistinguishable from one emptied to clear
            // the badge, so the row is still open.
            asRoles("crew_coordinator").get("/api/v1/crew-requests?status=open")
                .then().body("size()", equalTo(1))
        }

        @Test
        fun `is terminal in both directions`() {
            val id = raise()

            asRoles("crew_coordinator")
                .body("""{"note":"Arranged with the training provider."}""")
                .post("/api/v1/crew-requests/$id/action")
                .then().statusCode(200)

            // No reopen, and no second decision. A crew member still waiting asks again, and that
            // second statement carries its own date — which says "we did not fix this" better than
            // one row bouncing between states.
            asRoles("crew_coordinator")
                .body("""{"note":"Actually, no."}""")
                .post("/api/v1/crew-requests/$id/dismiss")
                .then()
                .statusCode(400)
                .body("detail", containsString("already actioned"))
        }

        @Test
        fun `is refused to a Data Steward, who may read the queue but not decide it`() {
            val id = raise()

            asRoles("data_steward").get("/api/v1/crew-requests").then().statusCode(200)

            // Dismissing restarts the chasing of a named crew member. That is the Crew
            // Coordinator's call, not something to do while tidying data.
            asRoles("data_steward")
                .body("""{"note":"Tidying."}""")
                .post("/api/v1/crew-requests/$id/dismiss")
                .then()
                .statusCode(403)
        }

        @Test
        fun `records who decided it and what they said`() {
            val id = raise()

            asRoles("crew_coordinator")
                .body("""{"note":"Seat confirmed for 12 Aug."}""")
                .post("/api/v1/crew-requests/$id/action")
                .then()
                .statusCode(200)
                .body("decisionNote", equalTo("Seat confirmed for 12 Aug."))
                .body("decidedBy", containsString("crew_coordinator"))
                .body("decidedAt", notNullValue())
        }

        @Test
        fun `answers 404 for a request that does not exist`() {
            asRoles("crew_coordinator")
                .body("""{"note":"..."}""")
                .post("/api/v1/crew-requests/999999/action")
                .then()
                .statusCode(404)
        }
    }
}
