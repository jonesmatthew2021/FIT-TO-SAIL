package au.crewcomp.it

import au.crewcomp.compliance.MatrixSnapshotService
import au.crewcomp.engine.HoldingStatus
import au.crewcomp.people.CrewStatementKind
import au.crewcomp.people.CrewStatementRepository
import au.crewcomp.people.HoldingService
import au.crewcomp.platform.security.Actor
import au.crewcomp.platform.security.ActorContext
import au.crewcomp.platform.security.ActorKind
import au.crewcomp.platform.security.Role
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import jakarta.inject.Inject
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.time.LocalDate
import java.util.HexFormat
import java.util.UUID

/**
 * The §10.3 sync contract the Flutter app is generated against.
 *
 * These tests exist because none of the interesting behaviour here is visible to a unit test:
 * the cursor is assigned by a database trigger, the tombstones are written by another one, and
 * the scoping guarantee is a property of what the SQL *cannot* return rather than of a branch
 * in Kotlin. A mock would agree with any of it.
 */
@QuarkusTest
@DisplayName("Mobile sync contract")
class SyncIT {

    @Inject lateinit var seeder: FixtureSeeder
    @Inject lateinit var matrixSnapshots: MatrixSnapshotService
    @Inject lateinit var holdings: HoldingService
    @Inject lateinit var actorContext: ActorContext
    @Inject lateinit var statements: CrewStatementRepository

    private lateinit var seed: FixtureSeeder.Seed

    @BeforeEach
    fun setUp() {
        seeder.clear()
        matrixSnapshots.invalidateAll()
        seed = seeder.seed()
    }

    /** The crew member the fixture gives an account, a swing, leave and a notification. */
    private fun asCrew(personId: Long = seed.compliantPersonId) =
        given()
            .header("X-Dev-User", "Crew $personId")
            .header("X-Dev-Roles", Role.CREW_MEMBER.wire)
            .header("X-Dev-Person-Id", personId.toString())

    /** Runs [block] as a back-office actor, for the server-side changes a delta must notice. */
    private fun <T> asSteward(block: () -> T): T = actorContext.runAs(
        Actor(
            userAccountId = null,
            personId = null,
            roles = setOf(Role.DATA_STEWARD),
            kind = ActorKind.HUMAN,
            label = "Test Steward",
        ),
        block,
    )

    @Nested
    @DisplayName("Snapshot")
    inner class Snapshot {

        @Test
        fun `is refused without authentication`() {
            given().get("/api/v1/sync/snapshot").then().statusCode(401)
            given().get("/api/v1/sync/delta").then().statusCode(401)
        }

        @Test
        fun `carries the crew member's own dataset, a cursor and the server's business date`() {
            asCrew()
                .get("/api/v1/sync/snapshot")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("cursor", greaterThan(0))
                .body("serverToday", notNullValue())
                .body("person.id", equalTo(seed.compliantPersonId.toInt()))
                .body("person.sam", equalTo("SAM001"))
                .body("holdings", hasSize<Any>(2))
                .body("assignments", hasSize<Any>(1))
                .body("leave", hasSize<Any>(1))
                .body("notifications", hasSize<Any>(1))
                .body("notifications[0].readAt", nullValue())
                // SEC-13: the title is the only field a push may carry, so it must not name the
                // qualification. The detail is in the body, fetched in-app.
                .body("notifications[0].title", equalTo("A qualification is expiring soon"))
                .body("reference.requirements.size()", greaterThan(0))
                .body("reference.matrixVersionLabel", notNullValue())
        }

        @Test
        fun `carries the server's evaluation rather than leaving the client to compute one`() {
            // AUTH-1: cell states are the engine's answers. The app renders them; it does not
            // decide what "expiring" means.
            asCrew()
                .get("/api/v1/sync/snapshot")
                .then()
                .statusCode(200)
                .body("standing", notNullValue())
                .body("standing.ccId", equalTo("CC24"))
                .body("standing.evaluation.rollUp", notNullValue())
                .body("standing.evaluation.cells.size()", greaterThan(0))
        }

        @Test
        fun `contains no other crew member's rows`() {
            val response = asCrew().get("/api/v1/sync/snapshot").then().statusCode(200).extract()
            val personIds = response.jsonPath().getList<Int>("holdings.personId").toSet() +
                response.jsonPath().getList<Int>("assignments.personId").toSet()

            assertEquals(
                setOf(seed.compliantPersonId.toInt()),
                personIds,
                "A sync snapshot must contain exactly one person's rows",
            )
        }

        @Test
        fun `is refused for an actor with no person record`() {
            // A back-office user is not a crew member. "You have no data" and "this endpoint is
            // not for you" are different answers and the second is the true one.
            given()
                .header("X-Dev-User", "Test Steward")
                .header("X-Dev-Roles", "data_steward")
                .get("/api/v1/sync/snapshot")
                .then()
                .statusCode(403)
                .body("error", equalTo("forbidden"))
        }
    }

    @Nested
    @DisplayName("Delta")
    inner class Delta {

        @Test
        fun `returns nothing new when the cursor is current`() {
            val cursor = asCrew().get("/api/v1/sync/snapshot").then().statusCode(200)
                .extract().jsonPath().getLong("cursor")

            asCrew()
                .get("/api/v1/sync/delta?cursor=$cursor")
                .then()
                .statusCode(200)
                .body("person", nullValue())
                .body("holdings", hasSize<Any>(0))
                .body("assignments", hasSize<Any>(0))
                .body("tombstones", hasSize<Any>(0))
                .body("referenceStale", equalTo(false))
        }

        @Test
        fun `carries a holding changed after the cursor, and only that holding`() {
            val cursor = asCrew().get("/api/v1/sync/snapshot").then().statusCode(200)
                .extract().jsonPath().getLong("cursor")

            asSteward {
                holdings.setHolding(
                    personId = seed.compliantPersonId,
                    requirementId = seed.medRequirementId,
                    status = HoldingStatus.HELD_EXPIRY,
                    expiry = java.time.LocalDate.of(2027, 3, 1),
                    note = "Renewed",
                )
            }

            asCrew()
                .get("/api/v1/sync/delta?cursor=$cursor")
                .then()
                .statusCode(200)
                .body("holdings", hasSize<Any>(1))
                .body("holdings[0].requirementId", equalTo(seed.medRequirementId.toInt()))
                .body("holdings[0].note", equalTo("Renewed"))
                .body("cursor", greaterThan(cursor.toInt()))
        }

        @Test
        fun `does not carry another crew member's change`() {
            val cursor = asCrew().get("/api/v1/sync/snapshot").then().statusCode(200)
                .extract().jsonPath().getLong("cursor")

            // A change to the *other* person. The trigger stamps it with a sequence past our
            // cursor, so only the query's person filter keeps it out of this response.
            asSteward {
                holdings.setHolding(
                    personId = seed.gapPersonId,
                    requirementId = seed.medRequirementId,
                    status = HoldingStatus.HELD_PERPETUAL,
                )
            }

            asCrew()
                .get("/api/v1/sync/delta?cursor=$cursor")
                .then()
                .statusCode(200)
                .body("holdings", hasSize<Any>(0))
                // The cursor still advances: the client must not re-ask for a range it has
                // already been told about, even though nothing in it was visible to them.
                .body("cursor", greaterThan(cursor.toInt()))
        }

        @Test
        fun `recomputes the standing even when no row of the client's changed`() {
            // A roll-up can change with no row change at all — a holding expires because the
            // date moved. A client that only applied row deltas would show a stale "compliant"
            // forever, so the standing is recomputed on every delta rather than diffed.
            val cursor = asCrew().get("/api/v1/sync/snapshot").then().statusCode(200)
                .extract().jsonPath().getLong("cursor")

            asCrew()
                .get("/api/v1/sync/delta?cursor=$cursor")
                .then()
                .statusCode(200)
                .body("holdings", hasSize<Any>(0))
                .body("standing.evaluation.rollUp", notNullValue())
        }

        @Test
        fun `reports a deletion as a tombstone`() {
            val cursor = asCrew().get("/api/v1/sync/snapshot").then().statusCode(200)
                .extract().jsonPath().getLong("cursor")

            deleteAHolding(seed.compliantPersonId, seed.wahRequirementId)

            asCrew()
                .get("/api/v1/sync/delta?cursor=$cursor")
                .then()
                .statusCode(200)
                .body("tombstones", hasSize<Any>(1))
                .body("tombstones[0].entityType", equalTo("QualificationHolding"))
        }

        @Test
        fun `a cursor of zero replays everything the client can see`() {
            asCrew()
                .get("/api/v1/sync/delta?cursor=0")
                .then()
                .statusCode(200)
                .body("holdings", hasSize<Any>(2))
                .body("assignments", hasSize<Any>(1))
                .body("notifications", hasSize<Any>(1))
        }
    }

    @Nested
    @DisplayName("Outbound queue")
    inner class Queue {

        @Test
        fun `marks a notification read, and is idempotent on replay`() {
            val body = mapOf(
                "operations" to listOf(
                    mapOf(
                        "opId" to UUID.randomUUID().toString(),
                        "type" to "notification.read",
                        "notificationId" to seed.notificationId,
                    ),
                ),
            )

            repeat(2) {
                asCrew()
                    .contentType(ContentType.JSON)
                    .body(body)
                    .post("/api/v1/sync/queue")
                    .then()
                    .statusCode(200)
                    .body("results", hasSize<Any>(1))
                    .body("results[0].status", equalTo("applied"))
            }

            asCrew()
                .get("/api/v1/sync/snapshot")
                .then()
                .body("notifications[0].readAt", notNullValue())
        }

        @Test
        fun `refuses to mark another crew member's notification, without saying it exists`() {
            // The notification belongs to the compliant crew member; the gapped one asks for it
            // by id. "Rejected" rather than "applied", and the detail names no other person.
            asCrew(personId = seed.gapPersonId)
                .contentType(ContentType.JSON)
                .body(
                    mapOf(
                        "operations" to listOf(
                            mapOf(
                                "opId" to "op-1",
                                "type" to "notification.read",
                                "notificationId" to seed.notificationId,
                            ),
                        ),
                    ),
                )
                .post("/api/v1/sync/queue")
                .then()
                .statusCode(200)
                .body("results[0].status", equalTo("rejected"))
        }

        @Test
        fun `applies the good operations in a batch containing a bad one`() {
            // The property that matters for a durable offline queue: one poison entry must not
            // block the forty good ones behind it, forever, on a vessel.
            val response = asCrew()
                .contentType(ContentType.JSON)
                .body(
                    mapOf(
                        "operations" to listOf(
                            mapOf("opId" to "bad", "type" to "nonsense.operation"),
                            mapOf(
                                "opId" to "good",
                                "type" to "notification.read",
                                "notificationId" to seed.notificationId,
                            ),
                        ),
                    ),
                )
                .post("/api/v1/sync/queue")
                .then()
                .statusCode(200)
                .extract()

            val byOpId = response.jsonPath().getList<Map<String, Any>>("results")
                .associate { it["opId"] to it["status"] }

            assertEquals("rejected", byOpId["bad"])
            assertEquals("applied", byOpId["good"])
        }

        @Test
        fun `registers an evidence submission idempotently by its device-generated id`() {
            val publicId = UUID.randomUUID().toString()
            val operation = mapOf(
                "opId" to "submit-1",
                "type" to "evidence.submit",
                "submission" to mapOf(
                    "publicId" to publicId,
                    "source" to "mobile_camera",
                    "contentType" to "application/pdf",
                    "declaredSize" to 12,
                ),
            )

            repeat(2) {
                asCrew()
                    .contentType(ContentType.JSON)
                    .body(mapOf("operations" to listOf(operation)))
                    .post("/api/v1/sync/queue")
                    .then()
                    .statusCode(200)
                    .body("results[0].status", equalTo("applied"))
                    .body("results[0].uploadOffset", equalTo(0))
            }

            // Replayed twice, submitted once.
            asCrew()
                .get("/api/v1/sync/snapshot")
                .then()
                .body("submissions", hasSize<Any>(1))
                .body("submissions[0].publicId", equalTo(publicId))
                .body("submissions[0].verificationStatus", equalTo("pending_extraction"))
        }

        @Test
        fun `refuses a device claiming to be an admin upload`() {
            asCrew()
                .contentType(ContentType.JSON)
                .body(
                    mapOf(
                        "operations" to listOf(
                            mapOf(
                                "opId" to "spoof",
                                "type" to "evidence.submit",
                                "submission" to mapOf(
                                    "publicId" to UUID.randomUUID().toString(),
                                    "source" to "admin_upload",
                                ),
                            ),
                        ),
                    ),
                )
                .post("/api/v1/sync/queue")
                .then()
                .statusCode(200)
                .body("results[0].status", equalTo("rejected"))
        }
    }

    @Nested
    @DisplayName("One-tap answers (MOB-5)")
    inner class CrewStatements {

        private fun answer(opId: String, type: String, requirementId: Long?, personId: Long? = null) =
            asCrew(personId ?: seed.compliantPersonId)
                .contentType(ContentType.JSON)
                .body(
                    mapOf(
                        "operations" to listOf(
                            buildMap {
                                put("opId", opId)
                                put("type", type)
                                if (requirementId != null) put("requirementId", requirementId)
                            },
                        ),
                    ),
                )
                .post("/api/v1/sync/queue")
                .then()
                .statusCode(200)

        @Test
        fun `records 'course booked' once, however many times the device replays it`() {
            // The property the outbox depends on: a device that never saw a verdict re-posts the
            // same opId, and must not turn one answer into three.
            repeat(3) {
                answer("op-booked", "requirement.progress", seed.medRequirementId)
                    .body("results[0].status", equalTo("applied"))
            }

            val mine = statements.forPersonUnscoped(seed.compliantPersonId)
            assertEquals(1, mine.size)
            assertEquals(CrewStatementKind.COURSE_BOOKED, mine.single().kind)
            // Stamped from the holding, so the expiry scan can go quiet for *this* expiry and
            // start again when a renewal moves it.
            assertEquals(LocalDate.of(2027, 8, 1), mine.single().aboutExpiry)
        }

        @Test
        fun `records a help request, and records nothing against the person's compliance`() {
            val before = asCrew().get("/api/v1/sync/snapshot").then().extract()

            answer("op-help", "requirement.help", seed.medRequirementId)
                .body("results[0].status", equalTo("applied"))

            assertEquals(
                CrewStatementKind.HELP_REQUESTED,
                statements.forPersonUnscoped(seed.compliantPersonId).single().kind,
            )

            // "Nothing is recorded against you for asking for help" is a promise the screen makes
            // in as many words, and this is where it has to hold: no holding moved and the engine's
            // verdict is the one it was before the tap (AUTH-1, §7.5).
            asCrew()
                .get("/api/v1/sync/snapshot")
                .then()
                .body(
                    "standing.evaluation.rollUp",
                    equalTo(before.path<String>("standing.evaluation.rollUp")),
                )
                .body(
                    "holdings.find { it.requirementId == ${seed.medRequirementId} }.status",
                    equalTo(before.path<String>("holdings.find { it.requirementId == ${seed.medRequirementId} }.status")),
                )
        }

        @Test
        fun `rejects an answer that names no requirement`() {
            answer("op-nothing", "requirement.progress", requirementId = null)
                .body("results[0].status", equalTo("rejected"))
                .body("results[0].detail", containsString("requirementId"))

            assertTrue(statements.forPersonUnscoped(seed.compliantPersonId).isEmpty())
        }

        @Test
        fun `refuses a second crew member replaying someone else's opId`() {
            answer("op-shared", "requirement.progress", seed.medRequirementId)
                .body("results[0].status", equalTo("applied"))

            // A UUID collision is not a realistic accident, so this is a device presenting an id
            // it should not hold. It is refused rather than silently re-attributed.
            answer("op-shared", "requirement.progress", seed.medRequirementId, personId = seed.gapPersonId)
                .body("results[0].status", equalTo("rejected"))

            assertTrue(statements.forPersonUnscoped(seed.gapPersonId).isEmpty())
        }
    }

    @Nested
    @DisplayName("Resumable upload (MOB-5a)")
    inner class ResumableUpload {

        private val content = "%PDF-1.4 pretend certificate scan".toByteArray()

        private fun submit(publicId: UUID, size: Int = content.size, sha: String? = null) {
            asCrew()
                .contentType(ContentType.JSON)
                .body(
                    mapOf(
                        "operations" to listOf(
                            mapOf(
                                "opId" to "submit",
                                "type" to "evidence.submit",
                                "submission" to buildMap {
                                    put("publicId", publicId.toString())
                                    put("source", "mobile_file")
                                    put("contentType", "application/pdf")
                                    put("declaredSize", size)
                                    if (sha != null) put("declaredSha256", sha)
                                },
                            ),
                        ),
                    ),
                )
                .post("/api/v1/sync/queue")
                .then()
                .statusCode(200)
                .body("results[0].status", equalTo("applied"))
        }

        private fun sha256(bytes: ByteArray) =
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))

        @Test
        fun `accepts chunks in order and completes at the declared size`() {
            val publicId = UUID.randomUUID()
            submit(publicId, sha = sha256(content))

            val half = content.size / 2
            asCrew()
                .contentType("application/octet-stream")
                .header("Upload-Offset", "0")
                .body(content.copyOfRange(0, half))
                .post("/api/v1/evidence/$publicId/chunks")
                .then()
                .statusCode(200)
                .body("offset", equalTo(half))
                .body("complete", equalTo(false))

            asCrew()
                .contentType("application/octet-stream")
                .header("Upload-Offset", half.toString())
                .body(content.copyOfRange(half, content.size))
                .post("/api/v1/evidence/$publicId/chunks")
                .then()
                .statusCode(200)
                .body("offset", equalTo(content.size))
                .body("complete", equalTo(true))
        }

        @Test
        fun `tells a lost client where to resume from`() {
            val publicId = UUID.randomUUID()
            submit(publicId)

            val first = 10
            asCrew()
                .contentType("application/octet-stream")
                .header("Upload-Offset", "0")
                .body(content.copyOfRange(0, first))
                .post("/api/v1/evidence/$publicId/chunks")
                .then()
                .statusCode(200)

            // The app was killed here and has no idea how much arrived.
            asCrew()
                .get("/api/v1/evidence/$publicId/chunks")
                .then()
                .statusCode(200)
                .body("offset", equalTo(first))
                .body("complete", equalTo(false))
        }

        @Test
        fun `treats a replayed chunk as a no-op rather than duplicating bytes`() {
            val publicId = UUID.randomUUID()
            submit(publicId)

            val first = 10
            repeat(2) {
                asCrew()
                    .contentType("application/octet-stream")
                    .header("Upload-Offset", "0")
                    .body(content.copyOfRange(0, first))
                    .post("/api/v1/evidence/$publicId/chunks")
                    .then()
                    .statusCode(200)
            }

            // The second POST replayed offset 0, which the server had already consumed. If it
            // had appended, the offset would be 20 and the assembled file corrupt.
            asCrew()
                .get("/api/v1/evidence/$publicId/chunks")
                .then()
                .body("offset", equalTo(first))
        }

        @Test
        fun `refuses a chunk that would leave a hole, and says where to seek to`() {
            val publicId = UUID.randomUUID()
            submit(publicId)

            asCrew()
                .contentType("application/octet-stream")
                .header("Upload-Offset", "500")
                .body(content)
                .post("/api/v1/evidence/$publicId/chunks")
                .then()
                .statusCode(409)
                .header("Upload-Offset", "0")
                .body("error", equalTo("chunk_out_of_order"))
        }

        @Test
        fun `rejects the document when the assembled bytes do not match the declared digest`() {
            val publicId = UUID.randomUUID()
            // Declare the digest of something else entirely.
            submit(publicId, sha = sha256("a different document".toByteArray()))

            asCrew()
                .contentType("application/octet-stream")
                .header("Upload-Offset", "0")
                .body(content)
                .post("/api/v1/evidence/$publicId/chunks")
                .then()
                .statusCode(200)

            // Corrupt bytes must fail here rather than reach the extraction pipeline (§8).
            asCrew()
                .get("/api/v1/sync/snapshot")
                .then()
                .body("submissions[0].verificationStatus", equalTo("rejected"))
                .body("submissions[0].rejectionReason", notNullValue())
        }

        @Test
        fun `refuses to upload against another crew member's submission`() {
            val publicId = UUID.randomUUID()
            submit(publicId)

            asCrew(personId = seed.gapPersonId)
                .contentType("application/octet-stream")
                .header("Upload-Offset", "0")
                .body(content)
                .post("/api/v1/evidence/$publicId/chunks")
                .then()
                .statusCode(403)
        }
    }

    /**
     * Deletes a holding through the seeder bean rather than inline.
     *
     * There is no delete endpoint — deletion is the case the tombstone trigger exists for — and
     * a `@Transactional` method called from inside the same test class would be self-invoked,
     * bypassing the interceptor and running without a transaction at all.
     */
    private fun deleteAHolding(personId: Long, requirementId: Long) {
        val deleted = seeder.deleteHolding(personId, requirementId)
        assertTrue(deleted == 1, "Expected to delete exactly one holding, deleted $deleted")
    }
}
