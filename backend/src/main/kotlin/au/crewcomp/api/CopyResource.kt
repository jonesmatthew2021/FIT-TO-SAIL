package au.crewcomp.api

import au.crewcomp.reference.PageCopy
import au.crewcomp.reference.PageCopyService
import io.quarkus.security.Authenticated
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.tags.Tag

/** Page text the office has rewritten (Edit text). */
@Path("/api/v1/copy")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Page text", description = "Sentences on the pages, as rewritten by the office")
class CopyResource(private val copy: PageCopyService) {

    @GET
    @Operation(summary = "Every rewritten sentence, by key")
    fun list(): List<PageCopyDto> = copy.list().map { it.toDto() }

    @PUT
    @Path("/{key}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Rewrite one sentence — audited")
    fun set(@PathParam("key") key: String, request: SetCopyRequest): PageCopyDto = copy.set(key, request.text).toDto()

    @DELETE
    @Path("/{key}")
    @Operation(summary = "Back to the original wording — audited")
    fun clear(@PathParam("key") key: String): Response {
        copy.clear(key)
        return Response.noContent().build()
    }
}

data class PageCopyDto(val key: String, val text: String, val updatedBy: String, val updatedAt: java.time.Instant)

data class SetCopyRequest(val text: String)

fun PageCopy.toDto() = PageCopyDto(key = key, text = text, updatedBy = updatedBy, updatedAt = updatedAt)
