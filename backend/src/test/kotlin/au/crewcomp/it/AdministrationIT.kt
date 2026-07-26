package au.crewcomp.it

import au.crewcomp.compliance.MatrixSnapshotService
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import io.restassured.specification.RequestSpecification
import jakarta.inject.Inject
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * ADM-10 — configuration, users and roles, and the SEC-1a identity allow-list.
 *
 * Three properties get most of the attention here, because each is a thing that goes wrong quietly:
 *
 *  * **A missing setting means "use the default", never "broken".** The table starts empty and the
 *    system works; an override is a deliberate act with an audit event behind it.
 *  * **The system cannot be locked out of itself.** Revoking the last System Administrator would
 *    leave nothing able to grant the role back, and the remedy would be hand-written SQL against
 *    production.
 *  * **Enabling an identity backend is its own act.** Onboarding one and letting it authenticate
 *    people are separate events, because the second is the one an auditor looks for (SEC-1a).
 */
@QuarkusTest
@DisplayName("Administration (ADM-10)")
class AdministrationIT {

    @Inject lateinit var seeder: FixtureSeeder
    @Inject lateinit var matrixSnapshots: MatrixSnapshotService

    private lateinit var seed: FixtureSeeder.Seed

    @BeforeEach
    fun setUp() {
        seeder.clear()
        matrixSnapshots.invalidateAll()
        seed = seeder.seed()
    }

    private fun asRoles(vararg roles: String): RequestSpecification =
        given()
            .header("X-Dev-User", "Test ${roles.joinToString("+")}")
            .header("X-Dev-Roles", roles.joinToString(","))
            .contentType(ContentType.JSON)

    private fun createAccount(vararg roles: String, name: String): Int =
        asRoles("system_administrator")
            .body("""{"displayName":"$name","roles":[${roles.joinToString(",") { "\"$it\"" }}]}""")
            .post("/api/v1/administration/users")
            .then()
            .statusCode(200)
            .extract()
            .path("id")

    @Nested
    @DisplayName("Configuration")
    inner class Configuration {

        @Test
        fun `every setting reports its default, and an empty table is not a broken one`() {
            asRoles("compliance_lead")
                .get("/api/v1/administration/config")
                .then()
                .statusCode(200)
                .body("key", hasItem("expiry.lead-days"))
                .body("find { it.key == 'expiry.lead-days' }.value", equalTo(90))
                .body("find { it.key == 'expiry.lead-days' }.defaultValue", equalTo(90))
                .body("find { it.key == 'expiry.lead-days' }.overridden", equalTo(false))
                .body("find { it.key == 'expiry.lead-days' }.updatedBy", nullValue())
                // LLM-2's launch posture: unset means always review, and unset is the default.
                .body("find { it.key == 'evidence.auto-accept-threshold' }.value", nullValue())
                .body("find { it.key == 'evidence.auto-accept-threshold' }.overridden", equalTo(false))
        }

        @Test
        fun `an override is recorded with who set it, and clearing it restores the default`() {
            asRoles("compliance_lead")
                .body("""{"value":45}""")
                .put("/api/v1/administration/config/expiry.lead-days")
                .then()
                .statusCode(200)
                .body("value", equalTo(45))
                .body("overridden", equalTo(true))
                .body("updatedBy", containsString("Test"))
                .body("updatedAt", notNullValue())

            asRoles("compliance_lead")
                .delete("/api/v1/administration/config/expiry.lead-days")
                .then()
                .statusCode(200)
                .body("value", equalTo(90))
                .body("overridden", equalTo(false))
        }

        @Test
        fun `a nonsense value is refused rather than stored`() {
            asRoles("system_administrator")
                .body("""{"value":"ninety"}""")
                .put("/api/v1/administration/config/expiry.lead-days")
                .then()
                .statusCode(400)
                .body("detail", containsString("whole number of days"))

            asRoles("system_administrator")
                .body("""{"value":9999}""")
                .put("/api/v1/administration/config/expiry.lead-days")
                .then()
                .statusCode(400)

            asRoles("system_administrator")
                .body("""{"value":{"gap":50,"nonsense":1}}""")
                .put("/api/v1/administration/config/suggestion.weights")
                .then()
                .statusCode(400)
                .body("detail", containsString("no weight named nonsense"))

            asRoles("system_administrator")
                .body("""{"value":2}""")
                .put("/api/v1/administration/config/evidence.auto-accept-threshold")
                .then()
                .statusCode(400)
        }

        @Test
        fun `an unknown key names the ones that exist rather than failing silently`() {
            asRoles("system_administrator")
                .body("""{"value":1}""")
                .put("/api/v1/administration/config/made.up.setting")
                .then()
                .statusCode(400)
                .body("detail", containsString("expiry.lead-days"))
        }

        @Test
        fun `a coordinator may read the settings but not change them`() {
            asRoles("crew_coordinator").get("/api/v1/administration/config").then().statusCode(200)

            asRoles("crew_coordinator")
                .body("""{"value":30}""")
                .put("/api/v1/administration/config/expiry.lead-days")
                .then()
                .statusCode(403)

            // Reading who holds which role is a map of the system's authority, so it is narrower.
            asRoles("crew_coordinator").get("/api/v1/administration/users").then().statusCode(403)
        }

        @Test
        fun `weights are stored as an object and read back whole`() {
            asRoles("system_administrator")
                .body("""{"value":{"gap":200,"unknown":20,"expiring":10,"crossPartnership":5,"overlappingAssignment":2000}}""")
                .put("/api/v1/administration/config/suggestion.weights")
                .then()
                .statusCode(200)
                .body("value.gap", equalTo(200))
                .body("value.overlappingAssignment", equalTo(2000))

            asRoles("system_administrator")
                .get("/api/v1/administration/config")
                .then()
                .body("find { it.key == 'suggestion.weights' }.value.gap", equalTo(200))
        }
    }

    @Nested
    @DisplayName("Users and roles (AUTH-4)")
    inner class UsersAndRoles {

        @Test
        fun `the crew member's seeded account is listed with its role and person linkage`() {
            asRoles("system_administrator")
                .get("/api/v1/administration/users")
                .then()
                .statusCode(200)
                .body("$", hasSize<Any>(1))
                .body("[0].displayName", equalTo("Compliant Crew"))
                .body("[0].kind", equalTo("local_test"))
                .body("[0].roles", equalTo(listOf("crew_member")))
                .body("[0].personId", equalTo(seed.compliantPersonId.toInt()))
                // SEC-1: the issuer and subject are never exposed. Whether a binding exists is the
                // operational question, and that is a boolean.
                .body("[0].identityLinked", equalTo(false))
        }

        @Test
        fun `a role is granted and revoked, and both are audited events of their own`() {
            val accountId = createAccount("crew_coordinator", name = "Coordinator")

            asRoles("system_administrator")
                .post("/api/v1/administration/users/$accountId/roles/workflow_manager")
                .then()
                .statusCode(200)
                .body("roles", equalTo(listOf("crew_coordinator", "workflow_manager")))

            // Re-granting is a no-op, not a duplicate row.
            asRoles("system_administrator")
                .post("/api/v1/administration/users/$accountId/roles/workflow_manager")
                .then()
                .body("roles", hasSize<Any>(2))

            asRoles("system_administrator")
                .delete("/api/v1/administration/users/$accountId/roles/workflow_manager")
                .then()
                .statusCode(200)
                .body("roles", equalTo(listOf("crew_coordinator")))
        }

        @Test
        fun `the last System Administrator cannot be revoked or suspended`() {
            val onlyAdmin = createAccount("system_administrator", name = "The Only Admin")

            asRoles("system_administrator")
                .delete("/api/v1/administration/users/$onlyAdmin/roles/system_administrator")
                .then()
                .statusCode(400)
                .body("detail", containsString("last active System Administrator"))

            asRoles("system_administrator")
                .body("""{"status":"suspended"}""")
                .put("/api/v1/administration/users/$onlyAdmin/status")
                .then()
                .statusCode(400)

            // With a second one in place, the first may go.
            createAccount("system_administrator", name = "A Second Admin")
            asRoles("system_administrator")
                .delete("/api/v1/administration/users/$onlyAdmin/roles/system_administrator")
                .then()
                .statusCode(200)
        }

        @Test
        fun `Crew Member needs a Person record, because it is a role about a person's own data`() {
            asRoles("system_administrator")
                .body("""{"displayName":"Nobody","roles":["crew_member"]}""")
                .post("/api/v1/administration/users")
                .then()
                .statusCode(400)
                .body("detail", containsString("linked Person record"))

            val accountId = createAccount("data_steward", name = "Steward")
            asRoles("system_administrator")
                .post("/api/v1/administration/users/$accountId/roles/crew_member")
                .then()
                .statusCode(400)
        }

        @Test
        fun `a person already holding an account cannot be given a second one`() {
            asRoles("system_administrator")
                .body(
                    """{"displayName":"Duplicate","roles":["crew_member"],
                        "personId":${seed.compliantPersonId}}""".trimIndent(),
                )
                .post("/api/v1/administration/users")
                .then()
                .statusCode(400)
                .body("detail", containsString("already has an account"))
        }

        @Test
        fun `an account is suspended rather than deleted, and a Vessel Master is scoped`() {
            val master = createAccount("vessel_master", name = "Master")

            asRoles("system_administrator")
                .body("""{"partnershipIds":[${seed.partnershipId}]}""")
                .put("/api/v1/administration/users/$master/scopes")
                .then()
                .statusCode(200)
                .body("scopedPartnershipIds", equalTo(listOf(seed.partnershipId.toInt())))

            asRoles("system_administrator")
                .body("""{"status":"suspended"}""")
                .put("/api/v1/administration/users/$master/status")
                .then()
                .statusCode(200)
                .body("status", equalTo("suspended"))

            // Still listed: an account is the subject of audit events and never disappears.
            asRoles("system_administrator")
                .get("/api/v1/administration/users")
                .then()
                .body("find { it.displayName == 'Master' }.status", equalTo("suspended"))

            asRoles("system_administrator")
                .body("""{"status":"nonsense"}""")
                .put("/api/v1/administration/users/$master/status")
                .then()
                .statusCode(400)
        }

        @Test
        fun `only a System Administrator administers users`() {
            val accountId = createAccount("data_steward", name = "Steward")

            listOf("compliance_lead", "crew_coordinator", "workflow_manager", "data_steward").forEach { role ->
                asRoles(role)
                    .post("/api/v1/administration/users/$accountId/roles/crew_coordinator")
                    .then()
                    .statusCode(403)
            }

            // The Compliance Lead may still *see* who holds what — that is a governance question.
            asRoles("compliance_lead").get("/api/v1/administration/users").then().statusCode(200)
        }
    }

    @Nested
    @DisplayName("The SEC-1a identity allow-list")
    inner class IdentityAllowList {

        @Test
        fun `a backend is onboarded disabled, and enabling it is a separate act`() {
            val id: Int = asRoles("system_administrator")
                .body(
                    """{"provider":"entra","issuer":"https://login.microsoftonline.com/abc-123/v2.0",
                        "tenantOrDomain":"abc-123","displayName":"Acme"}""".trimIndent(),
                )
                .post("/api/v1/administration/identity-providers")
                .then()
                .statusCode(200)
                // Created disabled, always. Nobody can authenticate through it yet.
                .body("enabled", equalTo(false))
                .extract()
                .path("id")

            asRoles("system_administrator")
                .body("""{"enabled":true}""")
                .put("/api/v1/administration/identity-providers/$id/enabled")
                .then()
                .statusCode(200)
                .body("enabled", equalTo(true))

            asRoles("system_administrator")
                .get("/api/v1/administration/identity-providers")
                .then()
                .body("$", hasSize<Any>(1))
                .body("[0].displayName", equalTo("Acme"))
        }

        @Test
        fun `a multi-tenant Entra issuer is refused, because it would allow-list everyone`() {
            listOf("common", "organizations", "consumers").forEach { path ->
                asRoles("system_administrator")
                    .body(
                        """{"provider":"entra","issuer":"https://login.microsoftonline.com/$path/v2.0",
                            "tenantOrDomain":"anything","displayName":"Everyone"}""".trimIndent(),
                    )
                    .post("/api/v1/administration/identity-providers")
                    .then()
                    .statusCode(400)
                    .body("detail", containsString("multi-tenant issuer"))
            }
        }

        @Test
        fun `an http issuer, an unknown provider and a duplicate key are all refused`() {
            asRoles("system_administrator")
                .body(
                    """{"provider":"entra","issuer":"http://login.microsoftonline.com/t/v2.0",
                        "tenantOrDomain":"t","displayName":"Insecure"}""".trimIndent(),
                )
                .post("/api/v1/administration/identity-providers")
                .then()
                .statusCode(400)
                .body("detail", containsString("https"))

            asRoles("system_administrator")
                .body(
                    """{"provider":"facebook","issuer":"https://example.test",
                        "tenantOrDomain":"example.test","displayName":"Nope"}""".trimIndent(),
                )
                .post("/api/v1/administration/identity-providers")
                .then()
                .statusCode(400)
                .body("detail", containsString("not a supported provider"))

            val body =
                """{"provider":"okta","issuer":"https://acme.okta.com","tenantOrDomain":"acme.okta.com",
                    "displayName":"Acme Okta"}""".trimIndent()
            asRoles("system_administrator")
                .body(body).post("/api/v1/administration/identity-providers")
                .then().statusCode(200)
            asRoles("system_administrator")
                .body(body).post("/api/v1/administration/identity-providers")
                .then()
                .statusCode(400)
                .body("detail", containsString("already on the allow-list"))
        }

        @Test
        fun `a duplicate is caught even when the existing row is disabled`() {
            // The regression this guards: a uniqueness check written against the *enabled* rows would
            // let a second row through here and hit the table's unique constraint as a 500.
            val body =
                """{"provider":"google","issuer":"https://accounts.google.com",
                    "tenantOrDomain":"acme.test","displayName":"Acme Google"}""".trimIndent()
            asRoles("system_administrator")
                .body(body).post("/api/v1/administration/identity-providers")
                .then().statusCode(200).body("enabled", equalTo(false))

            asRoles("system_administrator")
                .body(body).post("/api/v1/administration/identity-providers")
                .then()
                .statusCode(400)
                .body("detail", containsString("already on the allow-list"))
        }

        @Test
        fun `only a System Administrator changes the allow-list`() {
            asRoles("compliance_lead")
                .body(
                    """{"provider":"okta","issuer":"https://acme.okta.com",
                        "tenantOrDomain":"acme.okta.com","displayName":"Acme"}""".trimIndent(),
                )
                .post("/api/v1/administration/identity-providers")
                .then()
                .statusCode(403)

            // Seeing which backends are trusted is a governance read, and the Compliance Lead has it.
            asRoles("compliance_lead").get("/api/v1/administration/identity-providers").then().statusCode(200)
            asRoles("crew_coordinator").get("/api/v1/administration/identity-providers").then().statusCode(403)
        }
    }
}
