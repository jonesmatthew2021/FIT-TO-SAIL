package au.crewcomp.it

import au.crewcomp.compliance.MatrixSnapshotService
import au.crewcomp.platform.audit.AuditEvent
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import io.restassured.specification.RequestSpecification
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * MOB-11 — a supervisor's watch and the nudge.
 *
 * The two properties worth defending here are both about *scope*. A supervisor's team is derived
 * from co-assignment rather than from an org chart nobody maintains, and the nudge is authorised by
 * exactly the same derivation — a supervisor who could nudge anyone in their partnership would have
 * a wider write than read, which is backwards. Everything else is payload shape, and the shape is
 * the privacy control (SEC-12): there is nowhere in it to put a document or a medical detail.
 */
@QuarkusTest
@DisplayName("Supervisor's watch (MOB-11)")
class TeamIT {

    @Inject lateinit var seeder: FixtureSeeder
    @Inject lateinit var matrixSnapshots: MatrixSnapshotService
    @Inject lateinit var em: EntityManager

    private lateinit var seed: FixtureSeeder.Seed

    @BeforeEach
    fun setUp() {
        seeder.clear()
        matrixSnapshots.invalidateAll()
        seed = seeder.seed()
    }

    /**
     * The supervisor: the compliant crew member, assigned to slot 16 of CC24 — so their watch is
     * whoever else is on that swing, which is the gapped crew member in slot 15.
     *
     * The partnership header matters. A Vessel Master's ambient scope is partnership-wide and the
     * swing evaluation behind the watch needs it; co-assignment narrows *within* that rather than
     * replacing it.
     */
    private fun asSupervisor(personId: Long = seed.compliantPersonId): RequestSpecification = given()
        .header("X-Dev-User", "Supervisor")
        .header("X-Dev-Roles", "crew_member,vessel_master")
        .header("X-Dev-Person-Id", personId.toString())
        .header("X-Dev-Partnerships", seed.partnershipId.toString())
        .contentType(ContentType.JSON)

    private fun asCrew(personId: Long): RequestSpecification = given()
        .header("X-Dev-User", "Crew")
        .header("X-Dev-Roles", "crew_member")
        .header("X-Dev-Person-Id", personId.toString())
        .contentType(ContentType.JSON)

    private fun nudge(
        sam: String,
        note: String? = null,
        opId: String = "nudge-1",
        spec: RequestSpecification = asSupervisor(),
    ) = spec.body(
        """{"operations":[{"opId":"$opId","type":"team.nudge","targetSam":"$sam"""" +
            (note?.let { ""","note":"$it"""" } ?: "") + "}]}",
    ).post("/api/v1/sync/queue").then().statusCode(200)

    @Transactional
    fun auditEvents(): List<AuditEvent> =
        em.createQuery("select e from AuditEvent e order by e.seq", AuditEvent::class.java).resultList

    @Nested
    @DisplayName("Who is on the watch")
    inner class Scope {

        @Test
        fun `is everyone else rostered onto the same swing`() {
            asSupervisor().get("/api/v1/me/team").then()
                .statusCode(200)
                .body("ccId", equalTo("CC24"))
                .body("partnershipAbbrev", equalTo("UNI"))
                // One member, and the supervisor is not on their own watch.
                .body("members.sam", contains("SAM002"))
        }

        @Test
        fun `is reciprocal — the other crew member's watch is the first`() {
            asSupervisor(seed.gapPersonId).get("/api/v1/me/team").then()
                .statusCode(200)
                .body("members.sam", contains("SAM001"))
        }

        @Test
        fun `carries a state and one line of reason, and nowhere to put anything else`() {
            // The privacy rule is the shape of the payload, not the discretion of the screen: a
            // device sent a medical detail and told not to draw it would still leak it to anyone
            // who read the local database (SEC-12).
            val member = asSupervisor().get("/api/v1/me/team").then()
                .statusCode(200)
                .extract().jsonPath().getMap<String, Any?>("members[0]")

            assertThat(member.keys)
                .containsExactlyInAnyOrder("sam", "name", "worstState", "reason", "inHand", "nudgedAt")
        }

        @Test
        fun `names the requirement and its state, never why it is missing`() {
            asSupervisor().get("/api/v1/me/team").then()
                .body("members[0].worstState", equalTo("gap"))
                .body("members[0].reason", equalTo("MS-01 not held"))
        }

        @Test
        fun `never shows a crew member an ISO date`() {
            // A supervisor is crew, and `08-16` and `16-08` are two different days to two people
            // in the same crew room. Composed in the display format at the source rather than
            // rewritten on the device.
            seeder.setHoldingExpiry(seed.gapPersonId, seed.medRequirementId, "2026-08-16")

            asSupervisor().get("/api/v1/me/team").then()
                .body("members[0].reason", equalTo("MS-01 expires 16 Aug 2026"))
        }

        @Test
        fun `a crew member without the supervisory role cannot read one at all`() {
            asCrew(seed.compliantPersonId).get("/api/v1/me/team").then().statusCode(403)
        }
    }

    @Nested
    @DisplayName("Being a supervisor does not break being crew")
    inner class OwnData {

        @Test
        fun `a supervising crew member can still sync their own data`() {
            // The trap this guards: a Vessel Master's ambient scope is partnership-wide, and every
            // person-scoped read the crew app makes passes a person and no partnership. Without
            // their own person id on that scope, being promoted would silently stop the app they
            // use from working.
            asSupervisor().get("/api/v1/sync/snapshot").then()
                .statusCode(200)
                .body("person.sam", equalTo("SAM001"))
                .body("holdings.size()", equalTo(2))
        }
    }

    @Nested
    @DisplayName("The nudge")
    inner class Nudge {

        @Test
        fun `reaches the person nudged, and names who sent it`() {
            // A nudge nobody can trace is a way to harass someone quietly. The sender's name is in
            // the body, and there is no setting that removes it.
            seeder.giveAccount(seed.gapPersonId)
            nudge("SAM002", note = "Have a look before Monday")
                .body("results[0].status", equalTo("applied"))

            val notifications = asCrew(seed.gapPersonId).get("/api/v1/sync/snapshot").then()
                .statusCode(200)
                .extract().jsonPath()

            val nudged = "notifications.find { it.kind == 'crew_nudged' }"
            asCrew(seed.gapPersonId).get("/api/v1/sync/snapshot").then()
                .body("$nudged.body", containsString("Compliant Crew"))
                .body("$nudged.body", containsString("Have a look before Monday"))
                // SEC-13: the title is the only field a push may carry, and it names neither the
                // qualification nor the message.
                .body("$nudged.title", equalTo("A message from your supervisor"))
                .body("$nudged.deepLink", equalTo("crewcomp://certifications"))

            assertThat(notifications.getList<Any>("notifications")).isNotEmpty()
        }

        @Test
        fun `shows up as nudgedAt on the watch`() {
            seeder.giveAccount(seed.gapPersonId)
            nudge("SAM002")

            asSupervisor().get("/api/v1/me/team").then()
                .body("members[0].nudgedAt", notNullValue())
        }

        @Test
        fun `is refused for somebody who is not on the watch`() {
            // Authorised by the same co-assignment the read uses — there is one definition, so a
            // supervisor can never nudge somebody they cannot see.
            nudge("SAM001").body("results[0].status", equalTo("rejected"))
        }

        @Test
        fun `twice in a day is one nudge`() {
            seeder.giveAccount(seed.gapPersonId)
            nudge("SAM002", opId = "nudge-a")
            nudge("SAM002", opId = "nudge-b").body("results[0].status", equalTo("applied"))

            val nudges = asCrew(seed.gapPersonId).get("/api/v1/sync/snapshot").then()
                .extract().jsonPath()
                .getList<Any>("notifications.findAll { it.kind == 'crew_nudged' }")
            assertThat(nudges).hasSize(1)

            // And one audit event, not two. A replayed outbox entry is not a second nudge.
            assertThat(auditEvents().filter { it.event == "team.nudged" }).hasSize(1)
        }

        @Test
        fun `is recorded even when the person has no account to deliver to`() {
            // Crew and back-office accounts alike arrive with the identity spike. That a supervisor
            // chased a named crew member is the traceability, and it must not wait on it.
            nudge("SAM002").body("results[0].status", equalTo("applied"))

            val event = auditEvents().single { it.event == "team.nudged" }
            assertThat(event.entityBusinessKey).isEqualTo("SAM002")
            assertThat(event.afterState).contains("\"delivered\":false")
        }

        @Test
        fun `is refused to a crew member who is not a supervisor`() {
            nudge("SAM001", spec = asCrew(seed.gapPersonId))
                .body("results[0].status", equalTo("rejected"))
        }
    }
}
