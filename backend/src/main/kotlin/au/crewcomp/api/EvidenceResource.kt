package au.crewcomp.api

import au.crewcomp.evidence.EvidenceService
import au.crewcomp.evidence.UploadState
import io.quarkus.security.Authenticated
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.UUID

/**
 * MOB-5a — resumable evidence upload.
 *
 * The submission's metadata arrives through the sync queue (`evidence.submit`), which is what
 * makes it survivable offline; the bytes arrive here, chunk by chunk, whenever there is signal.
 * Splitting them that way is the point: a 20 MB multi-page scan captured in a dead spot is
 * recorded as a submission immediately and uploads over however many connectivity windows it
 * takes.
 *
 * The protocol is deliberately small and HTTP-shaped rather than tus: the server holds the
 * offset, the client asks for it, and every response repeats it.
 *
 *   GET  /api/v1/evidence/{publicId}/chunks   → where to resume from
 *   POST /api/v1/evidence/{publicId}/chunks   → append at `Upload-Offset`
 *
 * `Upload-Offset` on the request is the byte position this chunk begins at. A mismatch answers
 * **409 with the expected offset**, so a confused client seeks instead of restarting.
 */
@Path("/api/v1/evidence")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
class EvidenceResource(private val evidence: EvidenceService) {

    /** The resume point. A client that crashed mid-upload starts here. */
    @GET
    @Path("/{publicId}/chunks")
    fun uploadState(@PathParam("publicId") publicId: UUID): UploadStateDto =
        evidence.uploadState(publicId).toDto()

    /**
     * Appends one chunk.
     *
     * The body is raw bytes rather than multipart: a multipart envelope around a resumable
     * chunk buys nothing and costs the client a boundary to get wrong on a flaky link.
     */
    @POST
    @Path("/{publicId}/chunks")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    fun appendChunk(
        @PathParam("publicId") publicId: UUID,
        @HeaderParam(UPLOAD_OFFSET_HEADER) offsetHeader: String?,
        bytes: ByteArray,
    ): Response {
        val offset = offsetHeader?.toLongOrNull()
            ?: throw IllegalArgumentException(
                "$UPLOAD_OFFSET_HEADER must be the byte position this chunk begins at",
            )
        require(offset >= 0) { "$UPLOAD_OFFSET_HEADER cannot be negative" }

        val state = evidence.appendChunk(publicId, offset, bytes)
        return Response.ok(state.toDto())
            // Repeated on every response so a client never has to track it independently.
            .header(UPLOAD_OFFSET_HEADER, state.offset)
            .build()
    }

    companion object {
        const val UPLOAD_OFFSET_HEADER = "Upload-Offset"
    }
}

fun UploadState.toDto() = UploadStateDto(
    offset = offset,
    complete = complete,
    declaredSize = declaredSize,
)
