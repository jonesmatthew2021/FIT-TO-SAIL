package au.crewcomp.api

import au.crewcomp.compliance.NoPublishedMatrixException
import au.crewcomp.evidence.ChunkOutOfOrderException
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessDeniedException
import au.crewcomp.platform.security.NotAuthenticatedException
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

@Provider
class NoPublishedMatrixMapper : ExceptionMapper<NoPublishedMatrixException> {
    override fun toResponse(exception: NoPublishedMatrixException): Response =
        Response.status(Response.Status.CONFLICT)
            .entity(ErrorDto("no_published_matrix", exception.message))
            .build()
}
