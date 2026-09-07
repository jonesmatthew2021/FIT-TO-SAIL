package au.crewcomp.api

import au.crewcomp.reference.ShipDocument
import au.crewcomp.reference.ShipDocumentService
import io.quarkus.security.Authenticated
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import java.time.Instant

/** The sheets a ship is run from (the portal's "Required documents for upload"). */
@Path("/api/v1/ship-documents")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Ship documents", description = "The spreadsheets and sheets an operation is run from")
class ShipDocumentResource(private val documents: ShipDocumentService) {

    @GET
    @Path("/{partnership}")
    @Operation(summary = "The current document in each category for this ship")
    fun current(@PathParam("partnership") partnership: String): List<ShipDocumentDto> =
        documents.current(partnership).map { it.toDto() }

    /** Raw bytes, as the certificate intake takes them; the filename URL-encoded in a header. */
    @POST
    @Path("/{partnership}/{category}")
    @Consumes(MediaType.WILDCARD)
    @Operation(summary = "File a document under a category, replacing the current one — audited")
    fun file(
        @PathParam("partnership") partnership: String,
        @PathParam("category") category: String,
        @HeaderParam("Content-Type") contentType: String?,
        @HeaderParam(EvidenceResource.FILE_NAME_HEADER) fileName: String?,
        bytes: ByteArray,
    ): ShipDocumentDto = documents.file(
        abbrev = partnership,
        category = category,
        fileName = fileName?.let { java.net.URLDecoder.decode(it, Charsets.UTF_8) } ?: "document",
        contentType = contentType?.substringBefore(';')?.trim()?.ifEmpty { null } ?: "application/octet-stream",
        bytes = bytes,
    ).toDto()

    @GET
    @Path("/content/{documentId}")
    @Produces(MediaType.WILDCARD)
    @Operation(summary = "The document's bytes")
    fun content(@PathParam("documentId") documentId: Long): Response {
        val content = documents.content(documentId)
        val safeName = content.fileName.replace("\"", "")
        return Response.ok(content.bytes)
            .type(content.contentType)
            .header("Content-Disposition", "attachment; filename=\"$safeName\"")
            .header("X-Content-Type-Options", "nosniff")
            .header("Cache-Control", "private, no-store")
            .build()
    }

    @DELETE
    @Path("/content/{documentId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Withdraw a document from the file — it becomes history; audited")
    fun withdraw(@PathParam("documentId") documentId: Long, request: RejectEvidenceRequest): Response {
        documents.withdraw(documentId, request.reason)
        return Response.noContent().build()
    }
}

data class ShipDocumentDto(
    val id: Long,
    val partnershipId: Long,
    val category: String,
    val fileName: String,
    val contentType: String,
    val byteSize: Long,
    val sha256: String?,
    val filedBy: String,
    val filedAt: Instant,
)

fun ShipDocument.toDto() = ShipDocumentDto(
    id = requiredId,
    partnershipId = partnership.requiredId,
    category = category,
    fileName = fileName,
    contentType = contentType,
    byteSize = byteSize,
    sha256 = sha256,
    filedBy = filedBy,
    filedAt = filedAt,
)
