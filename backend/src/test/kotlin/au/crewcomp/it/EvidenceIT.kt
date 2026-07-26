package au.crewcomp.it

import au.crewcomp.compliance.MatrixSnapshotService
import au.crewcomp.platform.config.ConfigKey
import au.crewcomp.platform.config.ConfigService
import au.crewcomp.platform.security.Actor
import au.crewcomp.platform.security.ActorContext
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import io.restassured.specification.RequestSpecification
import jakarta.inject.Inject
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * §8 stages 2–5 and ADM-9 — the evidence pipeline end to end over real HTTP.
 *
 * The property under test throughout is **LLM-1: the model never mutates the system of record.**
 * Extraction runs, matching runs, and a holding changes only when the auto-accept gate is
 * deliberately opened or a human decides. Everything else here exists to demonstrate that gate
 * being closed in each of the ways it can be closed.
 *
 * Extraction is driven by `DevTextPatternLlmClient`, which is present under the test profile and
 * absent from a production artefact. It reads labelled ASCII from the uploaded bytes, which is why
 * the fixtures below are documents that say `Expiry date: …` in plain text.
 */
@QuarkusTest
@DisplayName("Evidence pipeline (§8, ADM-9)")
class EvidenceIT {

    @Inject lateinit var seeder: FixtureSeeder
    @Inject lateinit var matrixSnapshots: MatrixSnapshotService
    @Inject lateinit var config: ConfigService
    @Inject lateinit var actorContext: ActorContext

    private lateinit var seed: FixtureSeeder.Seed

    @BeforeEach
    fun setUp() {
        seeder.clear()
        matrixSnapshots.invalidateAll()
        seed = seeder.seed()
        clearAutoAccept()
    }

    @AfterEach
    fun tearDown() {
        // The threshold is global state in a table. A test that opens the auto-accept gate and does
        // not close it would silently change what every later test's pipeline does.
        clearAutoAccept()
    }

    private fun clearAutoAccept() = setAutoAccept(null)

    private fun setAutoAccept(threshold: Double?) {
        actorContext.runAs(Actor.system("evidence-it")) {
            config.set(ConfigKey.EVIDENCE_AUTO_ACCEPT_THRESHOLD, threshold)
        }
    }

    private fun asRoles(vararg roles: String, personId: Long? = null): RequestSpecification =
        given()
            .header("X-Dev-User", "Test ${roles.joinToString("+")}")
            .header("X-Dev-Roles", roles.joinToString(","))
            .apply { if (personId != null) header("X-Dev-Person-Id", personId.toString()) }

    private fun asCrew() = asRoles("crew_member", personId = seed.compliantPersonId)

    /**
     * Submits and fully uploads a document as the crew member, then returns its id.
     *
     * Declared as `application/pdf` with plain-text content: the ingest allow-list checks the
     * *declared* type, and the dev extractor reads ASCII, so a text body under a PDF content type is
     * the smallest fixture that exercises both.
     */
    private fun upload(body: String, requirementHintId: Long? = null): UUID {
        val publicId = UUID.randomUUID()
        val bytes = body.toByteArray()

        asCrew()
            .contentType(ContentType.JSON)
            .body(
                mapOf(
                    "operations" to listOf(
                        mapOf(
                            "opId" to "submit-$publicId",
                            "type" to "evidence.submit",
                            "submission" to buildMap {
                                put("publicId", publicId.toString())
                                put("source", "mobile_file")
                                put("contentType", "application/pdf")
                                put("declaredSize", bytes.size)
                                if (requirementHintId != null) put("requirementHintId", requirementHintId)
                            },
                        ),
                    ),
                ),
            )
            .post("/api/v1/sync/queue")
            .then()
            .statusCode(200)
            .body("results[0].status", equalTo("applied"))

        asCrew()
            .contentType("application/octet-stream")
            .header("Upload-Offset", "0")
            .body(bytes)
            .post("/api/v1/evidence/$publicId/chunks")
            .then()
            .statusCode(200)
            .body("complete", equalTo(true))

        return publicId
    }

    /** Runs §8 stages 2–4 for a document, the way the scheduled sweep would. */
    private fun extract(publicId: UUID) =
        asRoles("data_steward")
            .post("/api/v1/evidence-review/$publicId/extract")
            .then()
            .statusCode(200)

    private fun medicalCertificate(expiry: String, issued: String = "2026-01-05") = """
        %PDF-1.4
        Document type: Certificate
        Holder name: Compliant Crew
        Certificate number: MED-99812
        Issuing authority: Department of Transport
        Qualification: Seafarer Medical
        Issue date: $issued
        Expiry date: $expiry
    """.trimIndent()

    @Nested
    @DisplayName("Stage 2 — extraction")
    inner class Extraction {

        @Test
        fun `an uploaded document is extracted with a confidence per field`() {
            val publicId = upload(medicalCertificate("2027-06-30"))

            extract(publicId)
                .body("verificationStatus", equalTo("pending_review"))
                .body("extractionModel", equalTo("dev-text-pattern/1"))
                // Every schema field is present whether the extractor filled it or not, so the
                // reviewer's form has a row to type into for the ones it missed.
                .body("extraction", hasSize<Any>(7))
                .body("extraction.find { it.name == 'expiryDate' }.value", equalTo("2027-06-30"))
                // A float, not a double: RestAssured's JSON path hands back what Groovy parsed, and
                // `closeTo` here would compare a Float against a Double and always fail.
                .body("extraction.find { it.name == 'expiryDate' }.confidence", equalTo(0.95f))
                .body("extraction.find { it.name == 'holderName' }.value", equalTo("Compliant Crew"))
                .body("extraction.find { it.name == 'issueDate' }.value", equalTo("2026-01-05"))
        }

        @Test
        fun `a document whose bytes have not arrived is left alone, not failed`() {
            // Submitted, never uploaded: MOB-5a means "not finished uploading" is a normal state.
            val publicId = UUID.randomUUID()
            asCrew()
                .contentType(ContentType.JSON)
                .body(
                    mapOf(
                        "operations" to listOf(
                            mapOf(
                                "opId" to "submit-only",
                                "type" to "evidence.submit",
                                "submission" to mapOf(
                                    "publicId" to publicId.toString(),
                                    "source" to "mobile_camera",
                                    "contentType" to "image/jpeg",
                                    "declaredSize" to 4096,
                                ),
                            ),
                        ),
                    ),
                )
                .post("/api/v1/sync/queue")
                .then()
                .statusCode(200)

            extract(publicId)
                .body("verificationStatus", equalTo("pending_extraction"))
                .body("uploadComplete", equalTo(false))
                .body("extractionModel", nullValue())
        }

        @Test
        fun `extraction is idempotent — a second run does not overwrite a decision`() {
            val publicId = upload(medicalCertificate("2027-06-30"))
            extract(publicId).body("verificationStatus", equalTo("pending_review"))

            asRoles("data_steward")
                .contentType(ContentType.JSON)
                .body(
                    """{"requirementId":${seed.medRequirementId},"status":"held_expiry",
                        "expiry":"2027-06-30"}""".trimIndent(),
                )
                .post("/api/v1/evidence-review/$publicId/accept")
                .then()
                .statusCode(200)
                .body("verificationStatus", equalTo("verified"))

            // The sweep runs again, as it will every two minutes forever.
            extract(publicId).body("verificationStatus", equalTo("verified"))
        }
    }

    @Nested
    @DisplayName("Stage 3 — matching")
    inner class Matching {

        @Test
        fun `an extracted title matching one catalogue entry resolves it`() {
            val publicId = upload(medicalCertificate("2027-06-30"))
            extract(publicId)
                .body("matchedRequirementId", equalTo(seed.medRequirementId.toInt()))
        }

        @Test
        fun `a title matching nothing falls back to the submitter's hint`() {
            val publicId = upload(
                """
                Qualification: Some Certificate Nobody Has Catalogued
                Expiry date: 2027-06-30
                """.trimIndent(),
                requirementHintId = seed.medRequirementId,
            )
            extract(publicId)
                .body("matchedRequirementId", equalTo(seed.medRequirementId.toInt()))
                .body("reviewReason", containsString("fell back to the submitter's hint"))
        }

        @Test
        fun `a document that disagrees with the hint is never guessed`() {
            // The document reads as the medical; the crew member tagged it Work at Heights.
            val publicId = upload(medicalCertificate("2027-06-30"), requirementHintId = seed.wahRequirementId)
            extract(publicId)
                .body("matchedRequirementId", nullValue())
                .body("reviewReason", containsString("one of the two is wrong"))
                .body("verificationStatus", equalTo("pending_review"))
        }

        @Test
        fun `nothing extracted and no hint is reported as such rather than guessed`() {
            val publicId = upload("%PDF-1.4 an illegible photograph of a wall")
            extract(publicId)
                .body("matchedRequirementId", nullValue())
                .body("reviewReason", containsString("no hint"))
        }
    }

    @Nested
    @DisplayName("Stage 4 — decide")
    inner class Decide {

        @Test
        fun `with auto-acceptance off nothing is ever accepted, whatever the confidence`() {
            val publicId = upload(medicalCertificate("2028-06-30"))
            extract(publicId)
                .body("verificationStatus", equalTo("pending_review"))
                .body("reviewReason", containsString("Auto-acceptance is off"))
                // LLM-1, stated as an assertion: no holding was touched.
                .body("linkedHoldingId", nullValue())

            asRoles("data_steward")
                .get("/api/v1/people/${seed.compliantPersonId}/holdings")
                .then()
                .statusCode(200)
                // The seeded medical still expires a year after the swing, not in 2028.
                .body("find { it.requirementId == ${seed.medRequirementId} }.expiry", equalTo("2027-08-01"))
        }

        @Test
        fun `with the gate open a renewal that extends an existing holding is accepted`() {
            setAutoAccept(0.9)
            val publicId = upload(medicalCertificate("2028-06-30"))

            extract(publicId)
                .body("verificationStatus", equalTo("auto_accepted"))
                .body("reviewReason", containsString("extending 2027-08-01 to 2028-06-30"))
                .body("linkedHoldingId", notNullValue())

            asRoles("data_steward")
                .get("/api/v1/people/${seed.compliantPersonId}/holdings")
                .then()
                .body("find { it.requirementId == ${seed.medRequirementId} }.expiry", equalTo("2028-06-30"))
                .body("find { it.requirementId == ${seed.medRequirementId} }.issueDate", equalTo("2026-01-05"))
        }

        @Test
        fun `a date that moves backwards is never accepted automatically`() {
            setAutoAccept(0.9)
            // Earlier than the holding on file: a superseded or mis-scanned document looks exactly
            // like this, and it is the case a human most needs to see.
            val publicId = upload(medicalCertificate("2026-09-01"))

            extract(publicId)
                .body("verificationStatus", equalTo("pending_review"))
                .body("reviewReason", containsString("does not extend"))
                .body("linkedHoldingId", nullValue())
        }

        /**
         * The gap crew member's medical is recorded as `not_held`, so there is no expiring holding
         * for a renewal to extend. That is the same gate a person with no holding row at all hits —
         * §8 stage 4 only auto-accepts a renewal, and neither of these is one.
         */
        @Test
        fun `a holding that is not an expiring one is never extended automatically`() {
            setAutoAccept(0.9)
            val publicId = UUID.randomUUID()
            val bytes = medicalCertificate("2028-06-30").toByteArray()
            asRoles("crew_member", personId = seed.gapPersonId)
                .contentType(ContentType.JSON)
                .body(
                    mapOf(
                        "operations" to listOf(
                            mapOf(
                                "opId" to "submit-gap",
                                "type" to "evidence.submit",
                                "submission" to mapOf(
                                    "publicId" to publicId.toString(),
                                    "source" to "mobile_file",
                                    "contentType" to "application/pdf",
                                    "declaredSize" to bytes.size,
                                ),
                            ),
                        ),
                    ),
                )
                .post("/api/v1/sync/queue")
                .then()
                .statusCode(200)
            asRoles("crew_member", personId = seed.gapPersonId)
                .contentType("application/octet-stream")
                .header("Upload-Offset", "0")
                .body(bytes)
                .post("/api/v1/evidence/$publicId/chunks")
                .then()
                .statusCode(200)

            extract(publicId)
                .body("verificationStatus", equalTo("pending_review"))
                .body("reviewReason", containsString("is not_held, not an expiring one"))
                .body("linkedHoldingId", nullValue())
        }

        @Test
        fun `a threshold above the extractor's confidence closes the gate again`() {
            setAutoAccept(0.99)
            val publicId = upload(medicalCertificate("2028-06-30"))
            extract(publicId)
                .body("verificationStatus", equalTo("pending_review"))
                .body("reviewReason", containsString("Confidence below"))
        }

        @Test
        fun `a threshold of zero is refused, because it would accept everything`() {
            asRoles("system_administrator")
                .contentType(ContentType.JSON)
                .body("""{"value":0}""")
                .put("/api/v1/administration/config/evidence.auto-accept-threshold")
                .then()
                .statusCode(400)
                .body("detail", containsString("clear it to mean 'always review'"))
        }
    }

    @Nested
    @DisplayName("Stage 5 — the ADM-9 queue")
    inner class ReviewQueue {

        @Test
        fun `the queue carries what a reviewer needs to decide, and a crew member cannot read it`() {
            val publicId = upload(medicalCertificate("2027-06-30"))
            extract(publicId)

            asRoles("data_steward")
                .get("/api/v1/evidence-review")
                .then()
                .statusCode(200)
                .body("$", hasSize<Any>(1))
                .body("[0].publicId", equalTo(publicId.toString()))
                .body("[0].sam", equalTo("SAM001"))
                .body("[0].personName", equalTo("Compliant Crew"))
                .body("[0].partnershipAbbrev", equalTo("UNI"))
                .body("[0].hasContent", equalTo(true))
                .body("[0].reviewReason", notNullValue())

            // A crew member's own documents come back through the sync path, scoped to them; the
            // queue is everyone's, and is not theirs to read.
            asCrew().get("/api/v1/evidence-review").then().statusCode(403)
        }

        @Test
        fun `the document itself is served inline for the side-by-side view`() {
            val publicId = upload(medicalCertificate("2027-06-30"))

            asRoles("data_steward")
                .get("/api/v1/evidence-review/$publicId/content")
                .then()
                .statusCode(200)
                .contentType("application/pdf")
                .header("X-Content-Type-Options", "nosniff")
                // Evidence is personal data: no shared cache may hold a copy.
                .header("Cache-Control", containsString("no-store"))
        }

        @Test
        fun `accepting writes the holding and links the evidence`() {
            val publicId = upload(medicalCertificate("2027-06-30"))
            extract(publicId)

            asRoles("data_steward")
                .contentType(ContentType.JSON)
                .body(
                    """{"requirementId":${seed.medRequirementId},"status":"held_expiry",
                        "expiry":"2027-06-30","issueDate":"2026-01-05"}""".trimIndent(),
                )
                .post("/api/v1/evidence-review/$publicId/accept")
                .then()
                .statusCode(200)
                .body("verificationStatus", equalTo("verified"))
                .body("linkedHoldingId", notNullValue())

            asRoles("data_steward")
                .get("/api/v1/people/${seed.compliantPersonId}/holdings")
                .then()
                .body("find { it.requirementId == ${seed.medRequirementId} }.expiry", equalTo("2027-06-30"))
        }

        @Test
        fun `a correction is accepted with the reviewer's values, not the extracted ones`() {
            val publicId = upload(medicalCertificate("2027-06-30"))
            extract(publicId)

            // The reviewer reads the scan and sees 2029, not 2027.
            asRoles("data_steward")
                .contentType(ContentType.JSON)
                .body(
                    """{"requirementId":${seed.medRequirementId},"status":"held_expiry",
                        "expiry":"2029-06-30","note":"Expiry misread by extraction"}""".trimIndent(),
                )
                .post("/api/v1/evidence-review/$publicId/accept")
                .then()
                .statusCode(200)
                .body("verificationStatus", equalTo("verified"))

            asRoles("data_steward")
                .get("/api/v1/people/${seed.compliantPersonId}/holdings")
                .then()
                .body("find { it.requirementId == ${seed.medRequirementId} }.expiry", equalTo("2029-06-30"))
        }

        @Test
        fun `a rejection needs a reason, and the reason reaches the submitter`() {
            val publicId = upload(medicalCertificate("2027-06-30"))
            extract(publicId)

            asRoles("data_steward")
                .contentType(ContentType.JSON)
                .body("""{"reason":"  "}""")
                .post("/api/v1/evidence-review/$publicId/reject")
                .then()
                .statusCode(400)
                .body("detail", containsString("needs a reason"))

            asRoles("data_steward")
                .contentType(ContentType.JSON)
                .body("""{"reason":"The expiry date is not legible — please re-photograph it."}""")
                .post("/api/v1/evidence-review/$publicId/reject")
                .then()
                .statusCode(200)
                .body("verificationStatus", equalTo("rejected"))
                .body("rejectionReason", containsString("not legible"))

            // MOB-3: the crew member is told, with the reason, or they will simply upload it again.
            asCrew()
                .get("/api/v1/notifications")
                .then()
                .statusCode(200)
                .body("find { it.kind == 'evidence_rejected' }.body", containsString("not legible"))
        }

        @Test
        fun `a decided document is not decided twice`() {
            val publicId = upload(medicalCertificate("2027-06-30"))
            extract(publicId)

            val accept =
                """{"requirementId":${seed.medRequirementId},"status":"held_expiry","expiry":"2027-06-30"}"""
            asRoles("data_steward")
                .contentType(ContentType.JSON).body(accept)
                .post("/api/v1/evidence-review/$publicId/accept")
                .then().statusCode(200)

            asRoles("data_steward")
                .contentType(ContentType.JSON).body(accept)
                .post("/api/v1/evidence-review/$publicId/accept")
                .then()
                .statusCode(409)
                .body("error", equalTo("evidence_already_decided"))
        }

        @Test
        fun `only a Data Steward decides`() {
            val publicId = upload(medicalCertificate("2027-06-30"))
            extract(publicId)

            listOf("crew_coordinator", "workflow_manager", "compliance_lead").forEach { role ->
                asRoles(role)
                    .contentType(ContentType.JSON)
                    .body("""{"requirementId":${seed.medRequirementId},"status":"held_perpetual"}""")
                    .post("/api/v1/evidence-review/$publicId/accept")
                    .then()
                    .statusCode(403)

                // They may still read the queue — seeing the backlog is not deciding it.
                asRoles(role).get("/api/v1/evidence-review").then().statusCode(200)
            }
        }
    }
}
