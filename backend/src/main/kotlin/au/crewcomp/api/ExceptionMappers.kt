package au.crewcomp.api

import au.crewcomp.compliance.NoPublishedMatrixException
import au.crewcomp.evidence.ChunkOutOfOrderException
import au.crewcomp.evidence.EvidenceAlreadyDecidedException
import au.crewcomp.people.AssignmentClashException
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessDeniedException
import au.crewcomp.platform.security.NotAuthenticatedException
import au.crewcomp.rules.MatrixNotEditableException
import au.crewcomp.workflow.LateSubmissionException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import org.jboss.logging.Logger

/**
 * Error responses.
 *
 * Authorisation failures return the reason, because at ~60 known users an opaque 403 costs
 * support time and reveals nothing an authenticated actor cannot already infer. Unexpected
 * failures do the opposite: they are logged in full and reported as a bare 500, so that an
 * internal message never reaches a client.
 */

@Provider
class AccessDeniedMapper : ExceptionMapper<AccessDeniedException> {
    private val log = Logger.getLogger(AccessDeniedMapper::class.java)

    override fun toResponse(exception: AccessDeniedException): Response {
        log.warnf("Access denied: %s", exception.message)
        return Response.status(Response.Status.FORBIDDEN)
            .entity(ErrorDto("forbidden", exception.message))
            .build()
    }
}

@Provider
class NotAuthenticatedMapper : ExceptionMapper<NotAuthenticatedException> {
    override fun toResponse(exception: NotAuthenticatedException): Response =
        Response.status(Response.Status.UNAUTHORIZED)
            .entity(ErrorDto("unauthenticated", exception.message))
            .build()
}

@Provider
class IllegalArgumentMapper : ExceptionMapper<IllegalArgumentException> {
    override fun toResponse(exception: IllegalArgumentException): Response =
        Response.status(Response.Status.BAD_REQUEST)
            .entity(ErrorDto("invalid_request", exception.message))
            .build()
}

/**
 * Absent and invisible answer the same, so a scoped read cannot be used to enumerate rows —
 * see [EntityNotFoundException].
 */
@Provider
class EntityNotFoundMapper : ExceptionMapper<EntityNotFoundException> {
    override fun toResponse(exception: EntityNotFoundException): Response =
        Response.status(Response.Status.NOT_FOUND)
            .entity(ErrorDto("not_found", exception.message))
            .build()
}

/**
 * MOB-5a: a chunk arrived somewhere other than the resume point.
 *
 * 409 rather than 400, and the expected offset travels in both the header and the body, because
 * the useful response to "you are out of step" is "here is where to seek to" — a device on a
 * flaky link that has to restart a 20 MB upload may never finish it.
 */
@Provider
class ChunkOutOfOrderMapper : ExceptionMapper<ChunkOutOfOrderException> {
    override fun toResponse(exception: ChunkOutOfOrderException): Response =
        Response.status(Response.Status.CONFLICT)
            .header(EvidenceResource.UPLOAD_OFFSET_HEADER, exception.expected)
            .entity(
                ErrorDto(
                    "chunk_out_of_order",
                    "Expected the next chunk at byte ${exception.expected}, " +
                        "but it began at ${exception.received}",
                ),
            )
            .build()
}

/**
 * ADM-2: the person is committed elsewhere for part of the window.
 *
 * 409 rather than 400 because the request is well-formed and may well be what the coordinator
 * intends — a cross-partnership loan, leave about to be cancelled. The clashes travel in the
 * detail so the planner can show them and offer to proceed, which resends the same request with
 * `acknowledgeClash`.
 */
@Provider
class AssignmentClashMapper : ExceptionMapper<AssignmentClashException> {
    override fun toResponse(exception: AssignmentClashException): Response =
        Response.status(Response.Status.CONFLICT)
            .entity(ErrorDto("assignment_clash", exception.clashes.joinToString("; ")))
            .build()
}

/**
 * ADM-4, Q17: the request is being lodged after the swing's submission cutoff.
 *
 * 409, like the assignment clash and for the same reason — the request is well formed and the
 * answer is "yes, but say so out loud". The retry carries `acknowledgeLateSubmission`, and the
 * acknowledgement lands on the record itself as well as in the audit event.
 */
@Provider
class LateSubmissionMapper : ExceptionMapper<LateSubmissionException> {
    override fun toResponse(exception: LateSubmissionException): Response =
        Response.status(Response.Status.CONFLICT)
            .entity(ErrorDto("late_submission", exception.message))
            .build()
}

/**
 * ADM-3, §5.5: an edit was attempted on a version that is not a draft.
 *
 * 409 rather than 400. The request is well formed and the caller is permitted to make it — the
 * version is in the wrong state, because a published matrix is immutable so that past evaluations
 * stay reproducible. The remedy is in the message, and the SPA offers it as a button.
 */
@Provider
class MatrixNotEditableMapper : ExceptionMapper<MatrixNotEditableException> {
    override fun toResponse(exception: MatrixNotEditableException): Response =
        Response.status(Response.Status.CONFLICT)
            .entity(ErrorDto("matrix_not_editable", exception.message))
            .build()
}

/**
 * ADM-9: two reviewers opened the same queue and one of them was second.
 *
 * 409 for the same reason as the two above — the request is well formed and the caller is permitted;
 * the document has moved on. The message says what to do instead (correct the holding, rather than
 * rewrite the decision), which is the difference between a conflict and a dead end.
 */
@Provider
class EvidenceAlreadyDecidedMapper : ExceptionMapper<EvidenceAlreadyDecidedException> {
    override fun toResponse(exception: EvidenceAlreadyDecidedException): Response =
        Response.status(Response.Status.CONFLICT)
            .entity(ErrorDto("evidence_already_decided", exception.message))
            .build()
}

@Provider
class NoPublishedMatrixMapper : ExceptionMapper<NoPublishedMatrixException> {
    override fun toResponse(exception: NoPublishedMatrixException): Response =
        Response.status(Response.Status.CONFLICT)
            .entity(ErrorDto("no_published_matrix", exception.message))
            .build()
}
