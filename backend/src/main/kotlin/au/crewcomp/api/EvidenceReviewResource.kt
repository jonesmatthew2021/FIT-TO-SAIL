package au.crewcomp.api

import au.crewcomp.engine.HoldingStatus
import au.crewcomp.evidence.EvidencePipeline
import au.crewcomp.evidence.EvidenceReviewService
import au.crewcomp.evidence.VerificationStatus
import io.quarkus.security.Authenticated
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.tags.Tag

/**
 * ADM-9 — the evidence verification queue (§8 stage 5).
 *
 * Rooted at `/api/v1/evidence-review` rather than under `/api/v1/evidence`, and that is not
 * cosmetic: [EvidenceResource] is already rooted at `/api/v1/evidence`, and JAX-RS matches
 * sub-paths only **within** the one root resource it selected by prefix. A `/evidence/queue` method
 * on a second class would be unreachable and answer 404 with no start-up warning — the trap
 * documented on [ComplianceResource], from the other direction.
 */
@Path("/api/v1/evidence-review")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Evidence review", description = "The §8 verification queue (ADM-9)")
class EvidenceReviewResource(
    private val review: EvidenceReviewService,
    private val pipeline: EvidencePipeline,
) {

    /**
     * The queue.
     *
     * `status` may be repeated. The default is the work plus the auto-accepted spot-check list (§8
     * stage 4), because a decision nobody ever sees is a decision nobody can audit.
     */
    @GET
    @Operation(summary = "Documents awaiting verification, plus the auto-accepted spot-check list")
    fun queue(@QueryParam("status") statuses: List<String>?): List<EvidenceDocumentDto> {
        val requested = statuses
            ?.filter { it.isNotBlank() }
            ?.map { VerificationStatus.fromWire(it) }
            ?.takeIf { it.isNotEmpty() }
            ?: EvidenceReviewService.DEFAULT_QUEUE_STATUSES
        return review.queue(requested).map { it.toReviewDto() }
    }

    @GET
    @Path("/{publicId}")
    @Operation(summary = "One document with its extraction and confidences")
    fun detail(@PathParam("publicId") publicId: String): EvidenceDocumentDto =
        review.detail(java.util.UUID.fromString(publicId)).toReviewDto()

    /**
     * The document's bytes, for the side-by-side view.
     *
     * `inline` so the SPA can render it in an `<img>` or a sandboxed frame, with `nosniff` so the
     * browser cannot be talked into treating it as something else. What makes that safe is the
     * ingest allow-list rather than anything here: only PDFs and four image types were ever stored
     * (`EvidenceService.ACCEPTED_CONTENT_TYPES`), and the stored content type is what is asserted —
     * a client's `Accept` header has no say. Replaced by a platform signed URL in the spike, which is
     * where SEC-7 wants this to end up.
     */
    @GET
    @Path("/{publicId}/content")
    @Produces(MediaType.WILDCARD)
    @Operation(summary = "The stored document bytes, for the side-by-side review view")
    fun content(@PathParam("publicId") publicId: String): Response {
        val content = review.content(java.util.UUID.fromString(publicId))
        return Response.ok(content.bytes)
            .type(content.contentType)
            .header("Content-Disposition", "inline; filename=\"${content.filename}\"")
            .header("X-Content-Type-Options", "nosniff")
            // Evidence is personal data: no shared cache should ever hold a copy (SEC-5).
            .header("Cache-Control", "private, no-store")
            .build()
    }

    /**
     * Accepts the document and writes the holding it evidences.
     *
     * The request carries the values the **reviewer** decided, which is what makes "accept" and
     * "correct" one operation: the audit event records both what was extracted and what was
     * accepted, so a correction is visible as one (LLM-2's precision measurement depends on that).
     */
    @POST
    @Path("/{publicId}/accept")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Accept or correct — writes the holding, audited with the evidence link")
    fun accept(
        @PathParam("publicId") publicId: String,
        request: AcceptEvidenceRequest,
    ): EvidenceDocumentDto = review.accept(
        publicId = java.util.UUID.fromString(publicId),
        requirementId = request.requirementId,
        status = HoldingStatus.fromWire(request.status),
        expiry = request.expiry,
        issueDate = request.issueDate,
        note = request.note,
    ).toReviewDto()

    @POST
    @Path("/{publicId}/reject")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Reject with a reason — the submitter is notified with it")
    fun reject(
        @PathParam("publicId") publicId: String,
        request: RejectEvidenceRequest,
    ): EvidenceDocumentDto =
        review.reject(java.util.UUID.fromString(publicId), request.reason).toReviewDto()

    /**
     * Corrects a filed document's code or dates — the certificates-on-file "Edit".
     *
     * Writes the holding through the same door every holding edit uses and re-links the document,
     * audited as an amendment. Distinct from accept: accept decides an open document; this
     * corrects a decided one without rewriting the history of the decision.
     */
    @POST
    @Path("/{publicId}/amend")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Correct a filed document's code or dates — writes the holding, audited")
    fun amend(
        @PathParam("publicId") publicId: String,
        request: AcceptEvidenceRequest,
    ): EvidenceDocumentDto = review.amend(
        publicId = java.util.UUID.fromString(publicId),
        requirementId = request.requirementId,
        status = HoldingStatus.fromWire(request.status),
        expiry = request.expiry,
        issueDate = request.issueDate,
        note = request.note,
    ).toReviewDto()

    /**
     * Takes a document off the file — the certificates-on-file "Delete". Nothing is destroyed:
     * the bytes and the audit trail stay, the document reads as removed, and the holding it wrote
     * is left exactly as it is (correct that on the person's holdings if it was wrong).
     */
    @POST
    @Path("/{publicId}/remove")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Remove a document from the file with a reason — the holding is untouched")
    fun remove(
        @PathParam("publicId") publicId: String,
        request: RejectEvidenceRequest,
    ): EvidenceDocumentDto =
        review.remove(java.util.UUID.fromString(publicId), request.reason).toReviewDto()

    /**
     * Re-runs extraction for one document.
     *
     * Only useful for a document still in `pending_extraction` — the pipeline is idempotent by
     * status and will not overwrite a decision — but it is what turns "the sweep has not got to it
     * yet" from a wait into a click, and it is how the ITs drive stages 2–4 without a scheduler.
     */
    @POST
    @Path("/{publicId}/extract")
    @Operation(summary = "Run §8 stages 2–4 for this document now")
    fun extract(@PathParam("publicId") publicId: String): EvidenceDocumentDto {
        val id = java.util.UUID.fromString(publicId)
        // Authorise before doing any work: `detail` is the read this caller must already be allowed.
        review.detail(id)
        pipeline.process(id)
        return review.detail(id).toReviewDto()
    }
}
