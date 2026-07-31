package au.crewcomp.it

import au.crewcomp.compliance.MatrixSnapshotService
import au.crewcomp.platform.time.BusinessClock
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

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
    @Inject lateinit var clock: BusinessClock

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
    @DisplayName("Register (ADM-4)")
    inner class Register {

        /**
         * The fixture swing runs 2026-08-01..28 with a submission cutoff of 2026-07-25, so
         * whether a request is late depends on what day the suite happens to run. Pinning the
         * business date makes both sides of Q17 testable instead of one of them being whichever
         * the calendar allows — which is exactly what the admin date override exists for (§1).
         */
        @BeforeEach
        fun pinTheBusinessDate() {
            clock.overrideToday(LocalDate.of(2026, 7, 20))
        }

        @AfterEach
        fun unpin() {
            clock.clearOverride()
        }

        private fun raise(
            requirementId: Long = seed.medRequirementId,
            personId: Long = seed.gapPersonId,
            type: String = "Exemption Request - PW",
        ) = asRoles("crew_coordinator")
            .contentType(ContentType.JSON)
            .body(
                """{"type":"$type","partnership":"UNI","cc":"CC24","personId":$personId,""" +
                    """"requirementId":$requirementId,"effectiveFrom":"2026-08-01",""" +
                    """"effectiveTo":"2026-08-28"}""",
            )
            .post("/api/v1/register")

        @Test
        fun `the business key is the server's, and it is monotonic per swing`() {
            asRoles("crew_coordinator")
                .get("/api/v1/register/next-id?partnership=UNI&cc=CC24")
                .then()
                .statusCode(200)
                .body("recordId", equalTo("UNICC24-1"))

            raise().then().statusCode(200).body("record.recordId", equalTo("UNICC24-1"))
            raise(requirementId = seed.wahRequirementId)
                .then()
                .statusCode(200)
                .body("record.recordId", equalTo("UNICC24-2"))
                .body("record.open", equalTo(true))
                .body("record.status", equalTo("Open - PW"))
        }

        @Test
        fun `before the cutoff nothing has to be acknowledged`() {
            raise()
                .then()
                .statusCode(200)
                .body("record.lateSubmissionAcknowledged", equalTo(false))
                .body("trail[0].body", equalTo("Raised as Exemption Request - PW (Open - PW)."))
        }

        @Test
        fun `after the cutoff a request is still permitted, but never silently`() {
            clock.overrideToday(LocalDate.of(2026, 7, 30))

            raise()
                .then()
                .statusCode(409)
                .body("error", equalTo("late_submission"))
                .body("detail", containsString("2026-07-25"))

            asRoles("crew_coordinator")
                .contentType(ContentType.JSON)
                .body(
                    """{"type":"Exemption Request - PW","partnership":"UNI","cc":"CC24",""" +
                        """"personId":${seed.gapPersonId},"requirementId":${seed.medRequirementId},""" +
                        """"acknowledgeLateSubmission":true}""",
                )
                .post("/api/v1/register")
                .then()
                .statusCode(200)
                .body("record.lateSubmissionAcknowledged", equalTo(true))
                .body("trail[0].body", containsString("cutoff"))
        }

        @Test
        fun `an approval must carry a window, and the window must sit inside the swing`() {
            raise().then().statusCode(200)

            asRoles("workflow_manager")
                .contentType(ContentType.JSON)
                .body("""{"outcome":"Approved"}""")
                .post("/api/v1/register/UNICC24-1/close")
                .then()
                .statusCode(400)
                .body("detail", containsString("approval window"))

            asRoles("workflow_manager")
                .contentType(ContentType.JSON)
                .body("""{"outcome":"Approved","approvalFrom":"2026-07-01","approvalTo":"2026-08-28"}""")
                .post("/api/v1/register/UNICC24-1/close")
                .then()
                .statusCode(400)
                .body("detail", containsString("outside the swing"))

            asRoles("workflow_manager")
                .contentType(ContentType.JSON)
                .body(
                    """{"outcome":"Approved","approvalFrom":"2026-08-01","approvalTo":"2026-08-28",""" +
                        """"conditions":[{"type":"supervision","body":"Supervised by the slot-16 GPH."}],""" +
                        """"note":"Approved on the supervision condition."}""",
                )
                .post("/api/v1/register/UNICC24-1/close")
                .then()
                .statusCode(200)
                .body("record.status", equalTo("Closed - Approved"))
                .body("record.open", equalTo(false))
                .body("conditions", hasSize<Any>(1))
                .body("notes.find { it.party == 'OPS' }.body", containsString("supervision"))
        }

        @Test
        fun `an approved record turns the cell it covers from gap to exempt`() {
            // The whole reason the register exists, and the one behaviour that has to survive the
            // round trip: §5.1 step 4 reads the approval window off the closed record.
            asRoles("crew_coordinator")
                .get("/api/v1/swings/UNI/CC24/evaluation")
                .then()
                .body(
                    "assignments.find { it.sam == 'SAM002' }.evaluation.cells" +
                        ".find { it.requirementId == ${seed.medRequirementId} }.state",
                    equalTo("gap"),
                )

            raise().then().statusCode(200)

            // Open, not yet decided → `pending`, which is the state that tells a coordinator to
            // wait rather than to chase.
            asRoles("crew_coordinator")
                .get("/api/v1/swings/UNI/CC24/evaluation")
                .then()
                .body(
                    "assignments.find { it.sam == 'SAM002' }.evaluation.cells" +
                        ".find { it.requirementId == ${seed.medRequirementId} }.state",
                    equalTo("pending"),
                )

            asRoles("workflow_manager")
                .contentType(ContentType.JSON)
                .body("""{"outcome":"Approved","approvalFrom":"2026-08-01","approvalTo":"2026-08-28"}""")
                .post("/api/v1/register/UNICC24-1/close")
                .then()
                .statusCode(200)

            asRoles("crew_coordinator")
                .get("/api/v1/swings/UNI/CC24/evaluation")
                .then()
                .body(
                    "assignments.find { it.sam == 'SAM002' }.evaluation.cells" +
                        ".find { it.requirementId == ${seed.medRequirementId} }.state",
                    equalTo("exempt"),
                )
                .body(
                    "assignments.find { it.sam == 'SAM002' }.evaluation.cells" +
                        ".find { it.requirementId == ${seed.medRequirementId} }.registerRecordId",
                    equalTo("UNICC24-1"),
                )
        }

        @Test
        fun `only the Workflow Manager decides`() {
            raise().then().statusCode(200)

            // Q14: the Crew Coordinator raises requests and adds PW notes, and that is all.
            asRoles("crew_coordinator")
                .contentType(ContentType.JSON)
                .body("""{"outcome":"Not Approved"}""")
                .post("/api/v1/register/UNICC24-1/close")
                .then()
                .statusCode(403)

            asRoles("crew_coordinator")
                .contentType(ContentType.JSON)
                .body("""{"status":"Open - OPS"}""")
                .post("/api/v1/register/UNICC24-1/transition")
                .then()
                .statusCode(403)

            asRoles("crew_coordinator")
                .contentType(ContentType.JSON)
                .body("""{"party":"PW","body":"Chasing the issuing authority."}""")
                .post("/api/v1/register/UNICC24-1/notes")
                .then()
                .statusCode(200)
                .body("notes", hasSize<Any>(1))
        }

        @Test
        fun `a closed record is not reopened - a new one is raised`() {
            raise().then().statusCode(200)
            asRoles("workflow_manager")
                .contentType(ContentType.JSON)
                .body("""{"outcome":"Not Approved"}""")
                .post("/api/v1/register/UNICC24-1/close")
                .then()
                .statusCode(200)

            asRoles("workflow_manager")
                .contentType(ContentType.JSON)
                .body("""{"status":"Open - OPS"}""")
                .post("/api/v1/register/UNICC24-1/transition")
                .then()
                .statusCode(400)
                .body("detail", containsString("a new one is raised"))
        }

        @Test
        fun `the list filters by state and type, and a crew member cannot read it`() {
            raise().then().statusCode(200)
            raise(requirementId = seed.wahRequirementId, type = "MRL Query").then().statusCode(200)
            asRoles("workflow_manager")
                .contentType(ContentType.JSON)
                .body("""{"outcome":"Not Required"}""")
                .post("/api/v1/register/UNICC24-1/close")
                .then()
                .statusCode(200)

            asRoles("crew_coordinator").get("/api/v1/register").then().body("$", hasSize<Any>(2))
            asRoles("crew_coordinator")
                .get("/api/v1/register?state=open")
                .then()
                .body("$", hasSize<Any>(1))
                .body("[0].type", equalTo("MRL Query"))
            asRoles("crew_coordinator")
                .get("/api/v1/register?state=closed")
                .then()
                .body("$", hasSize<Any>(1))
            asRoles("crew_coordinator")
                .queryParam("type", "MRL Query")
                .get("/api/v1/register")
                .then()
                .body("$", hasSize<Any>(1))
            asRoles("crew_coordinator")
                .get("/api/v1/register?type=Nonsense")
                .then()
                .statusCode(400)

            // §7 is the crew member's surface; the register is not part of it.
            asRoles("crew_member", personId = seed.gapPersonId)
                .get("/api/v1/register")
                .then()
                .statusCode(403)
        }
    }

    @Nested
    @DisplayName("Exceptions worklist (ADM-7)")
    inner class Exceptions {

        private fun raise(): Int =
            asRoles("data_steward")
                .contentType(ContentType.JSON)
                .body(
                    """{"area":"people","description":"Two crew share a Sam #",""" +
                        """"linkedEntityType":"Person","linkedEntityId":${seed.gapPersonId}}""",
                )
                .post("/api/v1/exceptions")
                .then()
                .statusCode(200)
                .body("state", equalTo("open"))
                .extract()
                .path("id")

        @Test
        fun `an item is raised, resolved with a note, and can be reopened`() {
            val id = raise()

            // The note is not optional: an item closed with no explanation is indistinguishable
            // from one dismissed to clear the list, which is what §11 exists to prevent.
            asRoles("data_steward")
                .contentType(ContentType.JSON)
                .body("""{"note":"  "}""")
                .post("/api/v1/exceptions/$id/resolve")
                .then()
                .statusCode(400)
                .body("detail", containsString("note"))

            asRoles("data_steward")
                .contentType(ContentType.JSON)
                .body("""{"note":"Confirmed distinct people; both records kept."}""")
                .post("/api/v1/exceptions/$id/resolve")
                .then()
                .statusCode(200)
                .body("state", equalTo("resolved"))
                .body("resolutionNote", containsString("distinct people"))
                .body("resolvedBy", notNullValue())

            asRoles("data_steward")
                .contentType(ContentType.JSON)
                .body("""{"note":"again"}""")
                .post("/api/v1/exceptions/$id/resolve")
                .then()
                .statusCode(400)
                .body("detail", containsString("already resolved"))

            asRoles("data_steward")
                .post("/api/v1/exceptions/$id/reopen")
                .then()
                .statusCode(200)
                .body("state", equalTo("open"))
                .body("resolutionNote", equalTo(null))
        }

        @Test
        fun `the worklist filters by state`() {
            val resolved = raise()
            raise()
            asRoles("data_steward")
                .contentType(ContentType.JSON)
                .body("""{"note":"Done."}""")
                .post("/api/v1/exceptions/$resolved/resolve")
                .then()
                .statusCode(200)

            asRoles("data_steward").get("/api/v1/exceptions?state=open").then().body("$", hasSize<Any>(1))
            asRoles("data_steward").get("/api/v1/exceptions?state=resolved").then().body("$", hasSize<Any>(1))
            asRoles("data_steward").get("/api/v1/exceptions").then().body("$", hasSize<Any>(2))

            asRoles("data_steward")
                .get("/api/v1/exceptions?state=nonsense")
                .then()
                .statusCode(400)
        }

        @Test
        fun `a coordinator may read the worklist but not resolve it`() {
            val id = raise()

            asRoles("crew_coordinator").get("/api/v1/exceptions").then().statusCode(200)
            asRoles("crew_coordinator")
                .contentType(ContentType.JSON)
                .body("""{"note":"Not mine to close."}""")
                .post("/api/v1/exceptions/$id/resolve")
                .then()
                .statusCode(403)

            // A crew member has no business in the back office's worklist at all.
            asRoles("crew_member", personId = seed.gapPersonId)
                .get("/api/v1/exceptions")
                .then()
                .statusCode(403)
        }

        @Test
        fun `the unknown-holdings chase list is a live query, not stored rows`() {
            // Nothing in the seed is unknown to begin with.
            asRoles("data_steward")
                .get("/api/v1/exceptions/unknown-holdings")
                .then()
                .statusCode(200)
                .body("$", hasSize<Any>(0))

            asRoles("data_steward")
                .contentType(ContentType.JSON)
                .body("""{"status":"unknown"}""")
                .put("/api/v1/people/${seed.gapPersonId}/holdings/${seed.medRequirementId}")
                .then()
                .statusCode(200)

            asRoles("data_steward")
                .get("/api/v1/exceptions/unknown-holdings")
                .then()
                .body("$", hasSize<Any>(1))
                .body("[0].sam", equalTo("SAM002"))
                .body("[0].code", equalTo("MS-01"))

            // Recording the answer removes it, with nothing to resolve separately — the reason
            // this list is a query over holdings rather than a table of worklist rows.
            asRoles("data_steward")
                .contentType(ContentType.JSON)
                .body("""{"status":"held_perpetual"}""")
                .put("/api/v1/people/${seed.gapPersonId}/holdings/${seed.medRequirementId}")
                .then()
                .statusCode(200)

            asRoles("data_steward")
                .get("/api/v1/exceptions/unknown-holdings")
                .then()
                .body("$", hasSize<Any>(0))
        }
    }

    @Nested
    @DisplayName("Requirement catalogue (ADM-6)")
    inner class Catalogue {

        @Test
        fun `the catalogue carries usage counts so a retirement is an informed one`() {
            asRoles("compliance_lead")
                .get("/api/v1/requirements/catalogue")
                .then()
                .statusCode(200)
                // PS-04 is held by both seeded crew and named by one requirement rule and one
                // quota rule — the shape that tells a Compliance Lead this entry is load-bearing.
                .body("find { it.code == 'PS-04' }.usage.holdings", equalTo(2))
                .body("find { it.code == 'PS-04' }.usage.requirementRules", equalTo(1))
                .body("find { it.code == 'PS-04' }.usage.quotaRules", equalTo(1))
                .body("find { it.code == 'PS-04' }.usage.total", equalTo(4))
                .body("find { it.code == 'PS-04' }.aliases", hasSize<Any>(0))
        }

        @Test
        fun `only the Compliance Lead may change the catalogue`() {
            // A Data Steward edits holdings all day and must not be able to redefine what a
            // holding *is* — §6's "catalogue edits restricted to Compliance Lead".
            asRoles("data_steward")
                .contentType(ContentType.JSON)
                .body("""{"code":"CS-99","category":"CS","title":"Invented"}""")
                .post("/api/v1/requirements")
                .then()
                .statusCode(403)

            asRoles("compliance_lead")
                .contentType(ContentType.JSON)
                .body("""{"code":"cs-99","category":"CS","title":"  Confined Space Entry "}""")
                .post("/api/v1/requirements")
                .then()
                .statusCode(200)
                // Codes are normalised and titles trimmed, so the catalogue does not acquire two
                // spellings of the same thing.
                .body("code", equalTo("CS-99"))
                .body("title", equalTo("Confined Space Entry"))
                .body("status", equalTo("active"))
                .body("usage.total", equalTo(0))
        }

        @Test
        fun `a malformed code, an unknown category and a duplicate are all refused`() {
            asRoles("compliance_lead")
                .contentType(ContentType.JSON)
                .body("""{"code":"nonsense","category":"CS","title":"X"}""")
                .post("/api/v1/requirements")
                .then()
                .statusCode(400)
                .body("detail", containsString("PS-04"))

            asRoles("compliance_lead")
                .contentType(ContentType.JSON)
                .body("""{"code":"ZZ-01","category":"QQ","title":"X"}""")
                .post("/api/v1/requirements")
                .then()
                .statusCode(400)
                .body("detail", containsString("Appendix A"))

            asRoles("compliance_lead")
                .contentType(ContentType.JSON)
                .body("""{"code":"PS-04","category":"PS","title":"Duplicate"}""")
                .post("/api/v1/requirements")
                .then()
                .statusCode(400)
                .body("detail", containsString("already in the catalogue"))
        }

        @Test
        fun `retiring an entry leaves it in the catalogue`() {
            asRoles("compliance_lead")
                .contentType(ContentType.JSON)
                .body("""{"category":"PS","title":"Work at Heights","status":"retired"}""")
                .put("/api/v1/requirements/${seed.wahRequirementId}")
                .then()
                .statusCode(200)
                .body("status", equalTo("retired"))

            // Retired entries stay readable: register history and superseded matrix versions from
            // before the retirement still have to render the code (§4.1).
            asRoles("crew_coordinator")
                .get("/api/v1/requirements")
                .then()
                .body("find { it.code == 'PS-04' }.status", equalTo("retired"))
        }

        @Test
        fun `aliases map legacy titles, and may not point at two entries`() {
            asRoles("compliance_lead")
                .contentType(ContentType.JSON)
                .body("""{"alias":"Working at Height"}""")
                .post("/api/v1/requirements/${seed.wahRequirementId}/aliases")
                .then()
                .statusCode(200)
                .body("aliases", hasSize<Any>(1))
                .body("aliases[0].alias", equalTo("Working at Height"))

            // The evidence pipeline matches on code, title and alias case-insensitively (§8 stage
            // 3). An alias that resolved to two entries would send every document naming it to
            // review for ever, so it is refused at the point of creation.
            asRoles("compliance_lead")
                .contentType(ContentType.JSON)
                .body("""{"alias":"working at height"}""")
                .post("/api/v1/requirements/${seed.medRequirementId}/aliases")
                .then()
                .statusCode(400)
                .body("detail", containsString("PS-04"))

            val aliasId: Int = asRoles("compliance_lead")
                .get("/api/v1/requirements/catalogue")
                .then()
                .extract()
                .path("find { it.code == 'PS-04' }.aliases[0].id")

            asRoles("compliance_lead")
                .delete("/api/v1/requirements/${seed.wahRequirementId}/aliases/$aliasId")
                .then()
                .statusCode(200)
                .body("aliases", hasSize<Any>(0))
        }
    }

    @Nested
    @DisplayName("Assignment writes (ADM-2)")
    inner class AssignmentWrites {

        /** The seeded assignment of the gapped crew member to shift-1 slot 15. */
        private fun slot15AssignmentId(): Int =
            asRoles("crew_coordinator")
                .get("/api/v1/people/${seed.gapPersonId}/assignments")
                .then()
                .statusCode(200)
                .extract()
                .path("find { it.slotRef == 15 }.id")

        @Test
        fun `a coordinator may clear a slot and fill it again`() {
            val assignmentId = slot15AssignmentId()

            asRoles("crew_coordinator").delete("/api/v1/assignments/$assignmentId").then().statusCode(204)

            // The evaluation is a function of the assignments that exist, so the slot is open the
            // moment the row is gone — nothing caches coverage.
            asRoles("crew_coordinator")
                .get("/api/v1/swings/UNI/CC24/evaluation")
                .then()
                .body("assignments", hasSize<Any>(1))
                .body("openSlots.ref", hasItem(15))

            asRoles("crew_coordinator")
                .contentType(ContentType.JSON)
                .body("""{"slotRef":15,"personId":${seed.gapPersonId}}""")
                .post("/api/v1/swings/UNI/CC24/assignments")
                .then()
                .statusCode(200)
                .body("slotRef", equalTo(15))
                .body("personId", equalTo(seed.gapPersonId.toInt()))
                // Absent dates default to the whole swing rather than to today.
                .body("from", equalTo("2026-08-01"))
                .body("to", equalTo("2026-08-28"))

            asRoles("crew_coordinator")
                .get("/api/v1/swings/UNI/CC24/evaluation")
                .then()
                .body("assignments", hasSize<Any>(2))
                .body("openSlots", hasSize<Any>(0))
        }

        @Test
        fun `a person already committed in the window clashes, and the clash can be acknowledged`() {
            asRoles("crew_coordinator").delete("/api/v1/assignments/${slot15AssignmentId()}").then().statusCode(204)

            // The compliant crew member holds slot 16 for the whole swing. Putting them in slot 15
            // as well is physically impossible, so it is refused — but §5.4's rule is that a clash
            // is surfaced, not hidden, and the coordinator may still have a reason.
            asRoles("crew_coordinator")
                .contentType(ContentType.JSON)
                .body("""{"slotRef":15,"personId":${seed.compliantPersonId}}""")
                .post("/api/v1/swings/UNI/CC24/assignments")
                .then()
                .statusCode(409)
                .body("error", equalTo("assignment_clash"))
                .body("detail", containsString("slot 16"))

            asRoles("crew_coordinator")
                .contentType(ContentType.JSON)
                .body("""{"slotRef":15,"personId":${seed.compliantPersonId},"acknowledgeClash":true}""")
                .post("/api/v1/swings/UNI/CC24/assignments")
                .then()
                .statusCode(200)
        }

        @Test
        fun `two people may share a slot in sequence but never at once`() {
            val assignmentId = slot15AssignmentId()
            asRoles("crew_coordinator").delete("/api/v1/assignments/$assignmentId").then().statusCode(204)

            // First leg of a mid-swing handover.
            asRoles("crew_coordinator")
                .contentType(ContentType.JSON)
                .body(
                    """{"slotRef":15,"personId":${seed.gapPersonId},"from":"2026-08-01","to":"2026-08-14"}""",
                )
                .post("/api/v1/swings/UNI/CC24/assignments")
                .then()
                .statusCode(200)

            // An overlapping second leg is a double-booking, not a handover.
            asRoles("crew_coordinator")
                .contentType(ContentType.JSON)
                .body(
                    """{"slotRef":15,"personId":${seed.compliantPersonId},""" +
                        """"from":"2026-08-10","to":"2026-08-28","acknowledgeClash":true}""",
                )
                .post("/api/v1/swings/UNI/CC24/assignments")
                .then()
                .statusCode(400)
                .body("detail", containsString("in sequence"))
        }

        @Test
        fun `an assignment outside the swing window is refused`() {
            asRoles("crew_coordinator").delete("/api/v1/assignments/${slot15AssignmentId()}").then().statusCode(204)

            asRoles("crew_coordinator")
                .contentType(ContentType.JSON)
                .body(
                    """{"slotRef":15,"personId":${seed.gapPersonId},"from":"2026-07-01","to":"2026-08-28"}""",
                )
                .post("/api/v1/swings/UNI/CC24/assignments")
                .then()
                .statusCode(400)
                .body("detail", containsString("inside the swing"))
        }

        @Test
        fun `standing leave is the 409 the planner already warned about`() {
            // The planner ranks this person with an onLeave flag rather than hiding them; the
            // write path answers with the same fact rather than a surprise (issue #10).
            val away = seeder.seedCandidate(
                "SAM803", "Away Candidate",
                leaveFrom = LocalDate.parse("2026-08-05"), leaveTo = LocalDate.parse("2026-08-12"),
            )
            asRoles("crew_coordinator").delete("/api/v1/assignments/${slot15AssignmentId()}").then().statusCode(204)

            asRoles("crew_coordinator")
                .contentType(ContentType.JSON)
                .body("""{"slotRef":15,"personId":$away}""")
                .post("/api/v1/swings/UNI/CC24/assignments")
                .then()
                .statusCode(409)
                .body("error", equalTo("assignment_clash"))
                .body("detail", containsString("annual leave"))
        }

        @Test
        fun `two concurrent assigns to one slot commit exactly one`() {
            // The read-then-check is serialised by the per-(swing, slot) advisory lock, so the
            // second write sees the first's row and refuses — never two commits (issue #11). The
            // V11 exclusion constraint backstops paths that never took the lock.
            val candidate = seeder.seedCandidate("SAM804", "Second Candidate")
            asRoles("crew_coordinator").delete("/api/v1/assignments/${slot15AssignmentId()}").then().statusCode(204)

            val barrier = java.util.concurrent.CyclicBarrier(2)
            val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
            try {
                val statuses = listOf(seed.gapPersonId, candidate)
                    .map { personId ->
                        executor.submit<Int> {
                            barrier.await()
                            asRoles("crew_coordinator")
                                .contentType(ContentType.JSON)
                                .body("""{"slotRef":15,"personId":$personId}""")
                                .post("/api/v1/swings/UNI/CC24/assignments")
                                .statusCode()
                        }
                    }
                    .map { it.get() }

                org.assertj.core.api.Assertions.assertThat(statuses).containsExactlyInAnyOrder(200, 400)
            } finally {
                executor.shutdown()
            }
        }

        @Test
        fun `a Data Steward may edit holdings but may not move crew`() {
            asRoles("data_steward")
                .contentType(ContentType.JSON)
                .body("""{"slotRef":15,"personId":${seed.gapPersonId}}""")
                .post("/api/v1/swings/UNI/CC24/assignments")
                .then()
                .statusCode(403)
                .body("error", equalTo("forbidden"))
        }

        @Test
        fun `the crew member is notified, with no detail in the title`() {
            // The compliant crew member is the one with an account, so they are the one who can
            // be told. SEC-13: the title is all a push payload carries, so it must name nothing.
            asRoles("crew_coordinator")
                .delete("/api/v1/assignments/${slot16AssignmentId()}")
                .then()
                .statusCode(204)

            asRoles("crew_member", personId = seed.compliantPersonId)
                .get("/api/v1/sync/snapshot")
                .then()
                .statusCode(200)
                .body("notifications.find { it.kind == 'assignment_removed' }.title", equalTo("An assignment has been removed"))
                .body("notifications.find { it.kind == 'assignment_removed' }.body", containsString("slot 16"))
        }

        private fun slot16AssignmentId(): Int =
            asRoles("crew_coordinator")
                .get("/api/v1/people/${seed.compliantPersonId}/assignments")
                .then()
                .statusCode(200)
                .extract()
                .path("find { it.slotRef == 16 }.id")
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

        @Test
        fun `standing leave is scored and labelled, and ADM-10's weights re-rank the list`() {
            val away = seeder.seedCandidate(
                "SAM801", "Away Candidate",
                leaveFrom = LocalDate.parse("2026-08-05"), leaveTo = LocalDate.parse("2026-08-12"),
            )
            val sparse = seeder.seedCandidate("SAM802", "Sparse Candidate")
            seeder.deleteHolding(sparse, seed.wahRequirementId)

            // Default weights: one unknown holding (10) ranks ahead of standing leave (800) —
            // and the leave is on the row, not hidden (§5.4).
            asRoles("crew_coordinator")
                .get("/api/v1/swings/UNI/CC24/suggestions?slotRef=15")
                .then()
                .statusCode(200)
                .body("[0].personId", equalTo(sparse.toInt()))
                .body("[1].personId", equalTo(away.toInt()))
                .body("[1].onLeave", equalTo(true))
                .body("[1].reasons", hasItem(containsString("annual leave")))

            // The configured weights are policy, not decoration: a Compliance Lead changing them
            // on ADM-10 changes who the planner recommends (issue #9).
            asRoles("system_administrator")
                .contentType(ContentType.JSON)
                .body("""{"value":{"unknown":1000,"onLeave":1}}""")
                .put("/api/v1/administration/config/suggestion.weights")
                .then()
                .statusCode(200)

            asRoles("crew_coordinator")
                .get("/api/v1/swings/UNI/CC24/suggestions?slotRef=15")
                .then()
                .statusCode(200)
                .body("[0].personId", equalTo(away.toInt()))
                .body("[1].personId", equalTo(sparse.toInt()))
        }
    }
}
