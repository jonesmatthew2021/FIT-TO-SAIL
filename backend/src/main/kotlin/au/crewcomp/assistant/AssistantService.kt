package au.crewcomp.assistant

import au.crewcomp.platform.security.AccessDeniedException
import au.crewcomp.platform.security.ActorContext
import au.crewcomp.platform.security.Role
import jakarta.enterprise.context.ApplicationScoped

/**
 * The admin shell's read-only assistant (§17.1's first surface).
 *
 * The contract the panel is built against, stated here because the server is where each rule is
 * enforced (never by prompt):
 *
 *  - **Read-only** (LLM-1). Nothing in this package may reach a write path. An answer that
 *    implies an action links to the screen that performs it.
 *  - **Role-scoped.** The assistant sees exactly what the caller's roles can see. Crew members
 *    are refused outright: this is back-office shell chrome, and the crew app's assistant — if
 *    one is ever designed — is a separate thing with a separate scope.
 *  - **Citations are mandatory.** Any answer asserting a fact carries at least one source; the
 *    retrieval layer must therefore work from the same service layer the screens read, so every
 *    number on screen has a record behind it.
 *  - **Audited.** Prompt, answer, cited record ids, actor and business date are logged as a read
 *    path over compliance data. The audit lands with the provider adapter, because until one
 *    exists there is no answer to record.
 *
 * No LLM provider is selected (§14.5), so [ask] currently refuses with
 * [AssistantUnconfiguredException] — the same honest posture as the evidence pipeline's
 * `UnconfiguredLlmClient`: a finished surface whose missing half is a provider decision, not
 * code. Choosing a provider turns this into a retrieval layer plus an adapter; nothing in the
 * panel's contract moves.
 */
@ApplicationScoped
class AssistantService(private val actorContext: ActorContext) {

    fun ask(question: String, screen: String?, recordId: String?): AssistantAnswer {
        val actor = actorContext.require()
        // The handoff includes a Vessel Master (with their partnership scope); a bare crew
        // member is refused — the panel is admin chrome they never see, and answering them
        // here would be a second, unscoped read path into the back office.
        if (actor.roles.intersect(ALLOWED).isEmpty()) {
            throw AccessDeniedException("The assistant answers back-office and Vessel Master roles only.")
        }
        require(question.isNotBlank()) { "Ask a question." }

        throw AssistantUnconfiguredException()
    }

    companion object {
        private val ALLOWED = setOf(
            Role.CREW_COORDINATOR, Role.WORKFLOW_MANAGER, Role.COMPLIANCE_LEAD,
            Role.DATA_STEWARD, Role.SYSTEM_ADMINISTRATOR, Role.VESSEL_MASTER,
        )
    }
}

/** An answer with the records it came from. Empty [sources] is legal only for "I have nothing". */
data class AssistantAnswer(val text: String, val sources: List<AssistantSource>)

/** A citation chip: a label and the in-app route it deep-links to. */
data class AssistantSource(val label: String, val deepLink: String?)

/**
 * §14.5: no assistant model is configured. Deliberately an error rather than a canned answer —
 * a 200 with prose would be a turn the panel renders as fact, and there is no record behind it.
 */
class AssistantUnconfiguredException : RuntimeException(
    "No assistant model is configured (§14.5). The panel is ready; answering waits on the " +
        "provider decision and its privacy assessment (O-9, LLM-4).",
)
