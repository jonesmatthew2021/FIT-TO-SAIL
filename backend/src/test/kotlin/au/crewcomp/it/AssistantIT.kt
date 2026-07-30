package au.crewcomp.it

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import io.restassured.specification.RequestSpecification
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The assistant endpoint's launch posture (§14.5, §17.1): authenticated and role-gated like any
 * read path, and honestly unavailable — 503 with the reason — rather than answering with prose
 * no record backs. When a provider is chosen, the 503 case here becomes the contract test for
 * real answers; nothing else should need to move.
 */
@QuarkusTest
@DisplayName("Assistant (§17.1 launch posture)")
class AssistantIT {

    private fun asRoles(vararg roles: String): RequestSpecification =
        given().header("X-Dev-Roles", roles.joinToString(","))

    private fun body(question: String) = """{"question":"$question","screen":"ADM-1 Dashboard"}"""

    @Test
    fun `anonymous is refused`() {
        given().contentType(ContentType.JSON).body(body("What blocks the next cutoff?"))
            .post("/api/v1/assistant/ask")
            .then().statusCode(401)
    }

    @Test
    fun `a bare crew member is refused - admin chrome answers back-office roles only`() {
        asRoles("crew_member").contentType(ContentType.JSON).body(body("Am I compliant?"))
            .post("/api/v1/assistant/ask")
            .then().statusCode(403)
            .body("error", equalTo("forbidden"))
    }

    @Test
    fun `a back-office role gets the honest unconfigured answer, not prose`() {
        asRoles("crew_coordinator").contentType(ContentType.JSON).body(body("What blocks the next cutoff?"))
            .post("/api/v1/assistant/ask")
            .then().statusCode(503)
            .body("error", equalTo("assistant_unconfigured"))
            .body("detail", containsString("No assistant model is configured"))
    }

    @Test
    fun `a vessel master is allowed in - their scope is the service layer's problem`() {
        asRoles("vessel_master").contentType(ContentType.JSON).body(body("Is my crew ready?"))
            .post("/api/v1/assistant/ask")
            .then().statusCode(503)
    }

    @Test
    fun `a blank question is a 400, before any model is involved`() {
        asRoles("crew_coordinator").contentType(ContentType.JSON).body(body("  "))
            .post("/api/v1/assistant/ask")
            .then().statusCode(400)
    }
}
