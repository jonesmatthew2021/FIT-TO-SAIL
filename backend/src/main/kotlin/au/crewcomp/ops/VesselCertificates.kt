package au.crewcomp.ops

import au.crewcomp.api.EvidenceResource
import au.crewcomp.platform.adapters.ObjectStorage
import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.persistence.AuditedEntity
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.Role
import au.crewcomp.reference.Partnership
import au.crewcomp.reference.PartnershipRepository
import au.crewcomp.reference.Vessel
import au.crewcomp.reference.VesselRepository
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.transaction.Transactional
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.Operation
import java.net.URLDecoder
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * The vessel's own papers (OPS): survey, class, safety equipment, radio, insurance, and
 * whatever else expires. The crew matrix says the people are fit to sail; this says the ship is.
 */
@Entity
@Table(name = "vessel_certificate")
class VesselCertificate : AuditedEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partnership_id", nullable = false)
    lateinit var partnership: Partnership

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vessel_id")
    var vessel: Vessel? = null

    @Column(name = "title", nullable = false)
    lateinit var title: String

    @Column(name = "kind", nullable = false)
    lateinit var kind: String

    @Column(name = "reference")
    var reference: String? = null

    @Column(name = "issuer")
    var issuer: String? = null

    @Column(name = "issued_on")
    var issuedOn: LocalDate? = null

    @Column(name = "expires_on")
    var expiresOn: LocalDate? = null

    @Column(name = "object_key")
    var objectKey: String? = null

    @Column(name = "file_name")
    var fileName: String? = null

    @Column(name = "content_type")
    var contentType: String? = null

    @Column(name = "byte_size")
    var byteSize: Long? = null

    @Column(name = "note")
    var note: String? = null

    @Column(name = "withdrawn_at")
    var withdrawnAt: Instant? = null
}

@ApplicationScoped
class VesselCertificateRepository : PanacheRepositoryBase<VesselCertificate, Long> {
    fun currentFor(partnershipId: Long): List<VesselCertificate> =
        find("from VesselCertificate c left join fetch c.vessel where c.partnership.id = ?1 and c.withdrawnAt is null order by c.expiresOn nulls last, c.title", partnershipId).list()

    fun byIdLoaded(id: Long): VesselCertificate? =
        find("from VesselCertificate c join fetch c.partnership left join fetch c.vessel where c.id = ?1", id).firstResult()
}

data class VesselCertificateDto(
    val id: Long,
    val partnershipId: Long,
    val vesselId: Long?,
    val vesselName: String?,
    val title: String,
    val kind: String,
    val reference: String?,
    val issuer: String?,
    val issuedOn: LocalDate?,
    val expiresOn: LocalDate?,
    val fileName: String?,
    val byteSize: Long?,
    val note: String?,
)

data class SaveVesselCertificateRequest(
    val vesselId: Long? = null,
    val title: String,
    val kind: String,
    val reference: String? = null,
    val issuer: String? = null,
    val issuedOn: LocalDate? = null,
    val expiresOn: LocalDate? = null,
    val note: String? = null,
)

@ApplicationScoped
class VesselCertificateService(
    private val certificates: VesselCertificateRepository,
    private val partnerships: PartnershipRepository,
    private val vessels: VesselRepository,
    private val storage: ObjectStorage,
    private val policy: AccessPolicy,
    private val audit: AuditWriter,
) {

    @Transactional
    fun list(abbrev: String): List<VesselCertificateDto> {
        val partnership = partnership(abbrev)
        policy.assertCanSeePartnership(partnership.requiredId)
        return certificates.currentFor(partnership.requiredId).map { it.toDto() }
    }

    @Transactional
    fun create(abbrev: String, request: SaveVesselCertificateRequest): VesselCertificateDto {
        writers()
        val partnership = partnership(abbrev)
        policy.assertCanSeePartnership(partnership.requiredId)
        val certificate = VesselCertificate().apply {
            this.partnership = partnership
            stampCreated(policy.actor().label)
        }
        apply(certificate, request)
        certificates.persist(certificate)
        certificates.flush()
        audit.record(entityType = "VesselCertificate", event = "vessel_certificate.created", entityId = certificate.id, businessKey = "${partnership.abbrev}/${certificate.title}", after = snapshot(certificate))
        return certificate.toDto()
    }

    @Transactional
    fun update(id: Long, request: SaveVesselCertificateRequest): VesselCertificateDto {
        writers()
        val certificate = get(id)
        val before = snapshot(certificate)
        apply(certificate, request)
        certificate.stampUpdated(policy.actor().label)
        audit.record(entityType = "VesselCertificate", event = "vessel_certificate.updated", entityId = certificate.id, businessKey = "${certificate.partnership.abbrev}/${certificate.title}", before = before, after = snapshot(certificate))
        return certificate.toDto()
    }

    @Transactional
    fun attach(id: Long, fileName: String, contentType: String, bytes: ByteArray): VesselCertificateDto {
        writers()
        val certificate = get(id)
        require(bytes.isNotEmpty()) { "The upload carried no bytes" }
        require(bytes.size <= MAX_BYTES) { "The file exceeds the $MAX_BYTES byte limit" }
        val key = "vessel-certificates/${certificate.partnership.abbrev}/${UUID.randomUUID()}"
        storage.put(key, bytes, contentType)
        certificate.objectKey = key
        certificate.fileName = fileName.trim().ifEmpty { "certificate" }
        certificate.contentType = contentType
        certificate.byteSize = bytes.size.toLong()
        certificate.stampUpdated(policy.actor().label)
        audit.record(entityType = "VesselCertificate", event = "vessel_certificate.file_attached", entityId = certificate.id, businessKey = "${certificate.partnership.abbrev}/${certificate.title}", after = mapOf("fileName" to certificate.fileName, "byteSize" to certificate.byteSize))
        return certificate.toDto()
    }

    data class Content(val bytes: ByteArray, val contentType: String, val fileName: String)

    @Transactional
    fun content(id: Long): Content {
        val certificate = get(id)
        val key = certificate.objectKey ?: throw EntityNotFoundException("No file on ${certificate.title}")
        val bytes = storage.get(key) ?: throw EntityNotFoundException("The file for ${certificate.title} is not in storage")
        return Content(bytes, certificate.contentType ?: "application/octet-stream", certificate.fileName ?: "certificate")
    }

    @Transactional
    fun withdraw(id: Long) {
        writers()
        val certificate = get(id)
        certificate.withdrawnAt = Instant.now()
        certificate.stampUpdated(policy.actor().label)
        audit.record(entityType = "VesselCertificate", event = "vessel_certificate.withdrawn", entityId = certificate.id, businessKey = "${certificate.partnership.abbrev}/${certificate.title}", before = snapshot(certificate))
    }

    private fun writers() {
        policy.require(Role.COMPLIANCE_LEAD, Role.DATA_STEWARD, Role.SYSTEM_ADMINISTRATOR)
        policy.assertNotReadOnlyActor()
    }

    private fun apply(certificate: VesselCertificate, request: SaveVesselCertificateRequest) {
        require(request.title.isNotBlank()) { "A certificate needs a title" }
        require(request.kind.isNotBlank()) { "A certificate needs a kind — survey, class, safety, radio, insurance, other" }
        if (request.issuedOn != null && request.expiresOn != null) require(!request.expiresOn.isBefore(request.issuedOn)) { "It cannot expire before it was issued" }
        certificate.title = request.title.trim()
        certificate.kind = request.kind.trim().lowercase()
        certificate.reference = request.reference?.trim()?.ifEmpty { null }
        certificate.issuer = request.issuer?.trim()?.ifEmpty { null }
        certificate.issuedOn = request.issuedOn
        certificate.expiresOn = request.expiresOn
        certificate.note = request.note?.trim()?.ifEmpty { null }
        certificate.vessel = request.vesselId?.let { id ->
            val vessel = vessels.findById(id) ?: throw EntityNotFoundException("No vessel $id")
            require(vessel.partnership.requiredId == certificate.partnership.requiredId) { "${vessel.name} is not on this ship" }
            vessel
        }
    }

    private fun get(id: Long): VesselCertificate {
        val certificate = certificates.byIdLoaded(id) ?: throw EntityNotFoundException("No vessel certificate $id")
        policy.assertCanSeePartnership(certificate.partnership.requiredId)
        return certificate
    }

    private fun partnership(abbrev: String): Partnership =
        partnerships.byAbbrev(abbrev) ?: throw EntityNotFoundException("No partnership $abbrev")

    private fun snapshot(c: VesselCertificate) = mapOf(
        "title" to c.title, "kind" to c.kind, "reference" to c.reference, "issuer" to c.issuer,
        "issuedOn" to c.issuedOn?.toString(), "expiresOn" to c.expiresOn?.toString(), "vessel" to c.vessel?.name, "note" to c.note,
    )

    private fun VesselCertificate.toDto() = VesselCertificateDto(
        id = requiredId,
        partnershipId = partnership.requiredId,
        vesselId = vessel?.requiredId,
        vesselName = vessel?.name,
        title = title,
        kind = kind,
        reference = reference,
        issuer = issuer,
        issuedOn = issuedOn,
        expiresOn = expiresOn,
        fileName = fileName,
        byteSize = byteSize,
        note = note,
    )

    companion object {
        const val MAX_BYTES = 25 * 1024 * 1024
    }
}

@Path("/api/v1/ops/vessel-certificates")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
class VesselCertificateResource(private val certificates: VesselCertificateService) {

    @GET
    @Path("/{partnership}")
    @Operation(summary = "The ship's own certificates, soonest to expire first")
    fun list(@PathParam("partnership") partnership: String): List<VesselCertificateDto> = certificates.list(partnership)

    @POST
    @Path("/{partnership}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Add a vessel certificate — audited")
    fun create(@PathParam("partnership") partnership: String, request: SaveVesselCertificateRequest): VesselCertificateDto =
        certificates.create(partnership, request)

    @PUT
    @Path("/item/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Change a vessel certificate's details — audited")
    fun update(@PathParam("id") id: Long, request: SaveVesselCertificateRequest): VesselCertificateDto = certificates.update(id, request)

    @POST
    @Path("/item/{id}/file")
    @Consumes(MediaType.WILDCARD)
    @Operation(summary = "Attach the scan — raw bytes, the file name URL-encoded in X-File-Name")
    fun attach(
        @PathParam("id") id: Long,
        @HeaderParam("Content-Type") contentType: String?,
        @HeaderParam(EvidenceResource.FILE_NAME_HEADER) fileName: String?,
        bytes: ByteArray,
    ): VesselCertificateDto = certificates.attach(
        id,
        fileName?.let { URLDecoder.decode(it, Charsets.UTF_8) } ?: "certificate",
        contentType?.substringBefore(';')?.trim()?.ifEmpty { null } ?: "application/octet-stream",
        bytes,
    )

    @GET
    @Path("/item/{id}/content")
    @Produces(MediaType.WILDCARD)
    @Operation(summary = "The scan's bytes")
    fun content(@PathParam("id") id: Long): Response {
        val content = certificates.content(id)
        return Response.ok(content.bytes)
            .type(content.contentType)
            .header("Content-Disposition", "inline; filename=\"${content.fileName.replace("\"", "")}\"")
            .header("X-Content-Type-Options", "nosniff")
            .header("Cache-Control", "private, no-store")
            .build()
    }

    @DELETE
    @Path("/item/{id}")
    @Operation(summary = "Withdraw a vessel certificate from the file — audited")
    fun withdraw(@PathParam("id") id: Long): Response {
        certificates.withdraw(id)
        return Response.noContent().build()
    }
}
