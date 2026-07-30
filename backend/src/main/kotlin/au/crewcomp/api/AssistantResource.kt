package au.crewcomp.api

import au.crewcomp.assistant.AssistantService
import au.crewcomp.assistant.AssistantUnconfiguredException
import io.quarkus.security.Authenticated
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.tags.Tag

/**
 * The admin shell's assistant panel (see [AssistantService] for the contract it enforces).
 *
 * Rooted at `/api/v1` per the JAX-RS prefix trap documented on [ComplianceResource].
 */
@Path("/api/v1")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Assistant", description = "The admin shell's read-only, citing assistant (§17.1)")
class AssistantResource(private val assistant: AssistantService) {

    @POST
    @Path("/assistant/ask")
    @Operation(
        summary = "Ask the read-only assistant a question",
        description = "Answers cite the records they came from; the assistant never writes. " +
            "503 while no model is configured (§14.5).",
    )
    fun ask(dto: AssistantAskDto): AssistantAnswerDto {
        val answer = assistant.ask(dto.question, dto.screen, dto.recordId)
        return AssistantAnswerDto(
            text = answer.text,
            sources = answer.sources.map { AssistantSourceDto(it.label, it.deepLink) },
        )
    }
}

/** The question plus the panel's route context ("ADM-1 Dashboard", or an open record's id). */
data class AssistantAskDto(
    val question: String,
    val screen: String? = null,
    val recordId: String? = null,
)

data class AssistantSourceDto(val label: String, val deepLink: String? = null)

data class AssistantAnswerDto(val text: String, val sources: List<AssistantSourceDto>)

/**
 * 503, not 200-with-prose: the panel renders this as an error turn with the server's message
 * verbatim and a "Try again", which is the honest shape for "the capability is absent".
 */
@Provider
class AssistantUnconfiguredMapper : ExceptionMapper<AssistantUnconfiguredException> {
    override fun toResponse(exception: AssistantUnconfiguredException): Response =
        Response.status(Response.Status.SERVICE_UNAVAILABLE)
            .entity(ErrorDto("assistant_unconfigured", exception.message))
            .build()
}
