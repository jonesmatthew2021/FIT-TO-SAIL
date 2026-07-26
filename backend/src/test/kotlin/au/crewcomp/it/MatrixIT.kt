package au.crewcomp.it

import au.crewcomp.compliance.MatrixSnapshotService
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import jakarta.inject.Inject
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * ADM-3 — the matrix versioning contract over real HTTP against a real database.
 *
 * The property under test throughout is **immutability of a published version** (§5.5). Everything
 * else here is machinery; that is the invariant that makes a past evaluation reproducible, and it
 * is the one a well-meaning refactor would break by allowing "just fix this cell".
 */
@QuarkusTest
@DisplayName("Matrix versioning (ADM-3)")
class MatrixIT {

    @Inject lateinit var seeder: FixtureSeeder
    @Inject lateinit var matrixSnapshots: MatrixSnapshotService

    private lateinit var seed: FixtureSeeder.Seed

    @BeforeEach
    fun setUp() {
        seeder.clear()
        matrixSnapshots.invalidateAll()
        seed = seeder.seed()
    }

    private fun asRoles(vararg roles: String) =
        given()
            .header("X-Dev-User", "Test ${roles.joinToString("+")}")
            .header("X-Dev-Roles", roles.joinToString(","))
            .contentType(ContentType.JSON)

    /** Creates a draft copied from the seeded published version and returns its id. */
    private fun draftFromSeed(label: String = "draft-1"): Int =
        asRoles("compliance_lead")
            .body("""{"label":"$label","copyFromVersionId":${seed.matrixVersionId}}""")
            .post("/api/v1/matrix-versions")
            .then()
            .statusCode(200)
            .body("status", equalTo("draft"))
            .body("editable", equalTo(true))
            .extract()
            .path("id")

    @Nested
    @DisplayName("Version list")
    inner class VersionList {

        @Test
        fun `carries the rule counts a Compliance Lead decides from`() {
            asRoles("compliance_lead")
                .get("/api/v1/matrix-versions")
                .then()
                .statusCode(200)
                .body("$", hasSize<Any>(1))
                .body("[0].version.label", equalTo("test-v1"))
                .body("[0].version.status", equalTo("published"))
                .body("[0].version.editable", equalTo(false))
                .body("[0].requirementRuleCount", equalTo(2))
                .body("[0].quotaRuleCount", equalTo(1))
        }

        @Test
        fun `is readable by every back-office role, because every swing is evaluated against one`() {
            listOf("crew_coordinator", "workflow_manager", "data_steward").forEach { role ->
                asRoles(role).get("/api/v1/matrix-versions").then().statusCode(200)
            }
            // A crew member has no business here: their app is §7, and it is told its answers.
            asRoles("crew_member").get("/api/v1/matrix-versions").then().statusCode(403)
        }

        @Test
        fun `a version's detail carries the levels the cell editor renders`() {
            asRoles("compliance_lead")
                .get("/api/v1/matrix-versions/${seed.matrixVersionId}")
                .then()
                .statusCode(200)
                .body("version.label", equalTo("test-v1"))
                .body("rules", hasSize<Any>(2))
                .body("rules.find { it.requirementId == ${seed.medRequirementId} }.level", equalTo("M"))
                .body("rules.find { it.requirementId == ${seed.wahRequirementId} }.level", equalTo("M9"))
                // The base rule: null partnership, applying to every partnership (§4.2).
                .body("rules[0].partnershipId", equalTo(null))
                .body("quotas", hasSize<Any>(1))
                .body("quotas[0].footnote", equalTo("M9"))
                .body("quotas[0].scope", equalTo("shift"))
        }
    }

    @Nested
    @DisplayName("Drafting")
    inner class Drafting {

        @Test
        fun `a draft is a deep copy, so editing it cannot reach the version it came from`() {
            val draftId = draftFromSeed()

            asRoles("compliance_lead")
                .get("/api/v1/matrix-versions/$draftId")
                .then()
                .statusCode(200)
                .body("rules", hasSize<Any>(2))
                .body("quotas", hasSize<Any>(1))

            // Change the medical from mandatory to recommended in the draft.
            asRoles("compliance_lead")
                .body(
                    """
                    {"positionId":${seed.gphPositionId},
                     "requirementId":${seed.medRequirementId},
                     "level":"R"}
                    """.trimIndent(),
                )
                .put("/api/v1/matrix-versions/$draftId/cells")
                .then()
                .statusCode(200)
                .body("level", equalTo("R"))

            // The published version is untouched — this is the assertion the whole module exists for.
            asRoles("compliance_lead")
                .get("/api/v1/matrix-versions/${seed.matrixVersionId}")
                .then()
                .statusCode(200)
                .body("rules.find { it.requirementId == ${seed.medRequirementId} }.level", equalTo("M"))
        }

        @Test
        fun `only the Compliance Lead drafts, edits and publishes`() {
            val body = """{"label":"nope","copyFromVersionId":${seed.matrixVersionId}}"""

            listOf("crew_coordinator", "workflow_manager", "data_steward", "vessel_master").forEach { role ->
                asRoles(role)
                    .body(body)
                    .post("/api/v1/matrix-versions")
                    .then()
                    .statusCode(403)
                    .body("error", equalTo("forbidden"))
            }

            val draftId = draftFromSeed()
            asRoles("data_steward")
                .body("""{"positionId":${seed.gphPositionId},"requirementId":${seed.medRequirementId},"level":"R"}""")
                .put("/api/v1/matrix-versions/$draftId/cells")
                .then()
                .statusCode(403)

            // Publication is narrower still: §5.5 names the Compliance Lead alone, so even a
            // System Administrator is refused rather than quietly permitted.
            asRoles("system_administrator")
                .post("/api/v1/matrix-versions/$draftId/publish")
                .then()
                .statusCode(403)
        }

        @Test
        fun `a duplicate label is refused, because the label is how a version is referred to`() {
            asRoles("compliance_lead")
                .body("""{"label":"test-v1"}""")
                .post("/api/v1/matrix-versions")
                .then()
                .statusCode(400)
                .body("detail", containsString("already a matrix version labelled"))
        }

        @Test
        fun `a draft can be discarded, and a published version cannot`() {
            val draftId = draftFromSeed()

            asRoles("compliance_lead")
                .delete("/api/v1/matrix-versions/$draftId")
                .then()
                .statusCode(204)

            asRoles("compliance_lead")
                .get("/api/v1/matrix-versions/$draftId")
                .then()
                .statusCode(404)

            asRoles("compliance_lead")
                .delete("/api/v1/matrix-versions/${seed.matrixVersionId}")
                .then()
                .statusCode(409)
                .body("error", equalTo("matrix_not_editable"))
        }
    }

    @Nested
    @DisplayName("Immutability of a published version")
    inner class Immutability {

        @Test
        fun `every edit to a published version is a 409 that names the remedy`() {
            val cell =
                """{"positionId":${seed.gphPositionId},"requirementId":${seed.medRequirementId},"level":"R"}"""

            asRoles("compliance_lead")
                .body(cell)
                .put("/api/v1/matrix-versions/${seed.matrixVersionId}/cells")
                .then()
                .statusCode(409)
                .body("error", equalTo("matrix_not_editable"))
                // The message has to carry the way forward, or the screen is a dead end.
                .body("detail", containsString("Create a draft from it instead"))

            asRoles("compliance_lead")
                .body("""{"label":"renamed"}""")
                .put("/api/v1/matrix-versions/${seed.matrixVersionId}")
                .then()
                .statusCode(409)

            asRoles("compliance_lead")
                .delete(
                    "/api/v1/matrix-versions/${seed.matrixVersionId}/cells" +
                        "?positionId=${seed.gphPositionId}&requirementId=${seed.medRequirementId}",
                )
                .then()
                .statusCode(409)
        }
    }

    @Nested
    @DisplayName("Cell editing")
    inner class CellEditing {

        @Test
        fun `a partnership override is a different cell from the base rule`() {
            val draftId = draftFromSeed()

            // An override saying this partnership does NOT require the medical. Blank is a
            // positive statement here, and is why clearing an override is a separate operation.
            asRoles("compliance_lead")
                .body(
                    """
                    {"partnershipId":${seed.partnershipId},
                     "requirementId":${seed.medRequirementId},
                     "positionId":${seed.gphPositionId},
                     "level":""}
                    """.trimIndent(),
                )
                .put("/api/v1/matrix-versions/$draftId/cells")
                .then()
                .statusCode(200)
                .body("partnershipId", equalTo(seed.partnershipId.toInt()))
                .body("level", equalTo(""))

            asRoles("compliance_lead")
                .get("/api/v1/matrix-versions/$draftId")
                .then()
                .statusCode(200)
                // Three rules now: two base and one override, not two with one rewritten.
                .body("rules", hasSize<Any>(3))
                .body("rules.findAll { it.partnershipId == null }", hasSize<Any>(2))

            // Clearing the override restores the base rule for this partnership.
            asRoles("compliance_lead")
                .delete(
                    "/api/v1/matrix-versions/$draftId/cells?partnershipId=${seed.partnershipId}" +
                        "&positionId=${seed.gphPositionId}&requirementId=${seed.medRequirementId}",
                )
                .then()
                .statusCode(204)

            asRoles("compliance_lead")
                .get("/api/v1/matrix-versions/$draftId")
                .then()
                .body("rules", hasSize<Any>(2))
        }

        @Test
        fun `a blank level on the base rule is refused rather than stored as a no-op row`() {
            val draftId = draftFromSeed()

            asRoles("compliance_lead")
                .body("""{"positionId":${seed.gphPositionId},"requirementId":${seed.medRequirementId},"level":""}""")
                .put("/api/v1/matrix-versions/$draftId/cells")
                .then()
                .statusCode(400)
                .body("detail", containsString("clear the cell instead"))
        }

        @Test
        fun `clearing a cell that is not there is not found`() {
            val draftId = draftFromSeed()
            asRoles("compliance_lead")
                .delete(
                    "/api/v1/matrix-versions/$draftId/cells" +
                        "?positionId=${seed.gphPositionId}&requirementId=999999",
                )
                .then()
                .statusCode(404)
        }
    }

    @Nested
    @DisplayName("§5.5 diff")
    inner class Diff {

        @Test
        fun `reports added, removed and level-changed rules between any two versions`() {
            val draftId = draftFromSeed()

            // Level change: the medical becomes recommended.
            asRoles("compliance_lead")
                .body("""{"positionId":${seed.gphPositionId},"requirementId":${seed.medRequirementId},"level":"R"}""")
                .put("/api/v1/matrix-versions/$draftId/cells")
                .then().statusCode(200)

            // Removal: Work at Heights is no longer in the matrix at all.
            asRoles("compliance_lead")
                .delete(
                    "/api/v1/matrix-versions/$draftId/cells" +
                        "?positionId=${seed.gphPositionId}&requirementId=${seed.wahRequirementId}",
                )
                .then().statusCode(204)

            asRoles("compliance_lead")
                .get("/api/v1/matrix-versions/${seed.matrixVersionId}/diff/$draftId")
                .then()
                .statusCode(200)
                .body("empty", equalTo(false))
                .body("rules", hasSize<Any>(2))
                .body(
                    "rules.find { it.requirementId == ${seed.medRequirementId} }.kind",
                    equalTo("level_changed"),
                )
                .body("rules.find { it.requirementId == ${seed.medRequirementId} }.from", equalTo("M"))
                .body("rules.find { it.requirementId == ${seed.medRequirementId} }.to", equalTo("R"))
                .body("rules.find { it.requirementId == ${seed.wahRequirementId} }.kind", equalTo("removed"))
        }

        @Test
        fun `a diff of a version against itself is empty`() {
            asRoles("compliance_lead")
                .get("/api/v1/matrix-versions/${seed.matrixVersionId}/diff/${seed.matrixVersionId}")
                .then()
                .statusCode(200)
                .body("empty", equalTo(true))
                .body("rules", hasSize<Any>(0))
                .body("quotas", hasSize<Any>(0))
        }
    }

    @Nested
    @DisplayName("Publication")
    inner class Publication {

        @Test
        fun `publishing supersedes the previous version and changes what a swing evaluates against`() {
            // Before: the seeded matrix makes the medical mandatory, so the gap crew has a gap.
            asRoles("crew_coordinator")
                .get("/api/v1/swings/UNI/CC24/gaps")
                .then()
                .statusCode(200)
                .body("findAll { it.state == 'gap' }", hasSize<Any>(greaterThan(0)))

            val draftId = draftFromSeed("test-v2")
            // Drop the medical requirement entirely in the new version.
            asRoles("compliance_lead")
                .delete(
                    "/api/v1/matrix-versions/$draftId/cells" +
                        "?positionId=${seed.gphPositionId}&requirementId=${seed.medRequirementId}",
                )
                .then().statusCode(204)

            asRoles("compliance_lead")
                .body("""{"effectiveFrom":"2026-07-01"}""")
                .post("/api/v1/matrix-versions/$draftId/publish")
                .then()
                .statusCode(200)
                .body("published.status", equalTo("published"))
                .body("published.effectiveFrom", equalTo("2026-07-01"))
                .body("published.publishedAt", notNullValue())
                .body("superseded.label", equalTo("test-v1"))
                .body("superseded.status", equalTo("superseded"))

            // After: no medical rule, so no medical gap. The evaluation followed the publication
            // without anything being invalidated by hand — `current()` re-reads which version is
            // published on every call.
            asRoles("crew_coordinator")
                .get("/api/v1/swings/UNI/CC24/gaps")
                .then()
                .statusCode(200)
                .body("findAll { it.requirementId == ${seed.medRequirementId} }", hasSize<Any>(0))

            // And the superseded version is still evaluable, which is what makes a historical
            // swing reconstructible (§5.5).
            asRoles("crew_coordinator")
                .get("/api/v1/swings/UNI/CC24/gaps?matrixVersionId=${seed.matrixVersionId}")
                .then()
                .statusCode(200)
                .body("findAll { it.requirementId == ${seed.medRequirementId} }", hasSize<Any>(greaterThan(0)))
        }

        @Test
        fun `an empty draft is refused, because publishing nothing makes every cell na`() {
            val emptyDraftId: Int = asRoles("compliance_lead")
                .body("""{"label":"empty"}""")
                .post("/api/v1/matrix-versions")
                .then()
                .statusCode(200)
                .extract()
                .path("id")

            asRoles("compliance_lead")
                .post("/api/v1/matrix-versions/$emptyDraftId/publish")
                .then()
                .statusCode(400)
                .body("detail", containsString("would make every"))
        }

        @Test
        fun `publishing into the past is refused, since the latest effective date wins`() {
            val draftId = draftFromSeed("test-v2")

            // The seeded version is effective 2026-01-01. A version effective earlier than that
            // would be published and immediately not current, which is never what anyone means.
            asRoles("compliance_lead")
                .body("""{"effectiveFrom":"2025-12-31"}""")
                .post("/api/v1/matrix-versions/$draftId/publish")
                .then()
                .statusCode(400)
                .body("detail", containsString("publish into the past"))
        }

        @Test
        fun `a published version cannot be published again`() {
            asRoles("compliance_lead")
                .post("/api/v1/matrix-versions/${seed.matrixVersionId}/publish")
                .then()
                .statusCode(409)
                .body("error", equalTo("matrix_not_editable"))
        }
    }
}
