package au.crewcomp.ops

import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.persistence.AuditedEntity
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.Role
import au.crewcomp.reference.Partnership
import au.crewcomp.reference.PartnershipRepository
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
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.Operation
import java.time.LocalDate

/**
 * Auditing (OPS): the audits a ship has had — internal, AMSA, class, the customer's — and what
 * each found. A finding carries its severity, the corrective action, who owns it, when it is
 * due and when it was closed. What an auditor wants to see next time is the last audit's
 * findings closed, so this is the register that shows it.
 */
@Entity
@Table(name = "audit")
class Audit : AuditedEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partnership_id", nullable = false)
    lateinit var partnership: Partnership

    /** `internal` · `amsa` · `class` · `customer` · `flag` · `other`. */
    @Column(name = "kind", nullable = false)
    lateinit var kind: String

    @Column(name = "auditor")
    var auditor: String? = null

    @Column(name = "audit_date", nullable = false)
    lateinit var auditDate: LocalDate

    @Column(name = "scope")
    var scope: String? = null

    @Column(name = "outcome")
    var outcome: String? = null

    /** `planned` · `open` · `closed`. */
    @Column(name = "status", nullable = false)
    var status: String = "open"

    @Column(name = "note")
    var note: String? = null
}

@Entity
@Table(name = "audit_finding")
class AuditFinding : AuditedEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "audit_id", nullable = false)
    lateinit var audit: Audit

    @Column(name = "reference")
    var reference: String? = null

    /** `major` · `minor` · `observation`. */
    @Column(name = "severity", nullable = false)
    lateinit var severity: String

    @Column(name = "description", nullable = false, columnDefinition = "text")
    lateinit var description: String

    @Column(name = "corrective_action", columnDefinition = "text")
    var correctiveAction: String? = null

    @Column(name = "owner")
    var owner: String? = null

    @Column(name = "due_on")
    var dueOn: LocalDate? = null

    @Column(name = "closed_on")
    var closedOn: LocalDate? = null
}

@ApplicationScoped
class AuditRepository : PanacheRepositoryBase<Audit, Long> {
    fun forPartnership(partnershipId: Long): List<Audit> = list("partnership.id = ?1 order by auditDate desc, id desc", partnershipId)
    fun byIdLoaded(id: Long): Audit? = find("from Audit a join fetch a.partnership where a.id = ?1", id).firstResult()
}

@ApplicationScoped
class AuditFindingRepository : PanacheRepositoryBase<AuditFinding, Long> {
    fun forAudits(auditIds: Collection<Long>): List<AuditFinding> =
        if (auditIds.isEmpty()) emptyList() else list("audit.id in ?1 order by closedOn nulls first, dueOn nulls last, id", auditIds)
    fun byIdLoaded(id: Long): AuditFinding? = find("from AuditFinding f join fetch f.audit a join fetch a.partnership where f.id = ?1", id).firstResult()
}

data class AuditFindingDto(
    val id: Long,
    val auditId: Long,
    val reference: String?,
    val severity: String,
    val description: String,
    val correctiveAction: String?,
    val owner: String?,
    val dueOn: LocalDate?,
    val closedOn: LocalDate?,
)

data class AuditDto(
    val id: Long,
    val kind: String,
    val auditor: String?,
    val auditDate: LocalDate,
    val scope: String?,
    val outcome: String?,
    val status: String,
    val note: String?,
    val findings: List<AuditFindingDto>,
)

data class SaveAuditRequest(
    val kind: String,
    val auditor: String? = null,
    val auditDate: LocalDate,
    val scope: String? = null,
    val outcome: String? = null,
    val status: String = "open",
    val note: String? = null,
)

data class SaveFindingRequest(
    val reference: String? = null,
    val severity: String,
    val description: String,
    val correctiveAction: String? = null,
    val owner: String? = null,
    val dueOn: LocalDate? = null,
    val closedOn: LocalDate? = null,
)

@ApplicationScoped
class AuditService(
    private val audits: AuditRepository,
    private val findings: AuditFindingRepository,
    private val partnerships: PartnershipRepository,
    private val policy: AccessPolicy,
    private val auditTrail: AuditWriter,
) {

    @Transactional
    fun list(abbrev: String): List<AuditDto> {
        val partnership = partnership(abbrev)
        policy.assertCanSeePartnership(partnership.requiredId)
        val rows = audits.forPartnership(partnership.requiredId)
        val byAudit = findings.forAudits(rows.map { it.requiredId }).groupBy { it.audit.requiredId }
        return rows.map { it.toDto(byAudit[it.requiredId] ?: emptyList()) }
    }

    @Transactional
    fun create(abbrev: String, request: SaveAuditRequest): AuditDto {
        writers()
        val partnership = partnership(abbrev)
        policy.assertCanSeePartnership(partnership.requiredId)
        val audit = Audit().apply {
            this.partnership = partnership
            stampCreated(policy.actor().label)
        }
        apply(audit, request)
        audits.persist(audit)
        audits.flush()
        auditTrail.record(entityType = "Audit", event = "audit.recorded", entityId = audit.id, businessKey = "${partnership.abbrev}/${audit.auditDate}", after = snapshot(audit))
        return audit.toDto(emptyList())
    }

    @Transactional
    fun update(id: Long, request: SaveAuditRequest): AuditDto {
        writers()
        val audit = get(id)
        val before = snapshot(audit)
        apply(audit, request)
        audit.stampUpdated(policy.actor().label)
        auditTrail.record(entityType = "Audit", event = "audit.updated", entityId = audit.id, businessKey = "${audit.partnership.abbrev}/${audit.auditDate}", before = before, after = snapshot(audit))
        return audit.toDto(findings.forAudits(listOf(audit.requiredId)))
    }

    @Transactional
    fun addFinding(auditId: Long, request: SaveFindingRequest): AuditFindingDto {
        writers()
        val audit = get(auditId)
        val finding = AuditFinding().apply {
            this.audit = audit
            stampCreated(policy.actor().label)
        }
        apply(finding, request)
        findings.persist(finding)
        findings.flush()
        auditTrail.record(entityType = "AuditFinding", event = "finding.raised", entityId = finding.id, businessKey = "${audit.partnership.abbrev}/${audit.auditDate}", after = snapshot(finding))
        return finding.toDto()
    }

    @Transactional
    fun updateFinding(id: Long, request: SaveFindingRequest): AuditFindingDto {
        writers()
        val finding = findings.byIdLoaded(id) ?: throw EntityNotFoundException("No finding $id")
        policy.assertCanSeePartnership(finding.audit.partnership.requiredId)
        val before = snapshot(finding)
        apply(finding, request)
        finding.stampUpdated(policy.actor().label)
        auditTrail.record(entityType = "AuditFinding", event = if (request.closedOn != null && before["closedOn"] == null) "finding.closed" else "finding.updated", entityId = finding.id, businessKey = "${finding.audit.partnership.abbrev}/${finding.audit.auditDate}", before = before, after = snapshot(finding))
        return finding.toDto()
    }

    @Transactional
    fun removeFinding(id: Long) {
        writers()
        val finding = findings.byIdLoaded(id) ?: throw EntityNotFoundException("No finding $id")
        policy.assertCanSeePartnership(finding.audit.partnership.requiredId)
        val before = snapshot(finding)
        val key = "${finding.audit.partnership.abbrev}/${finding.audit.auditDate}"
        findings.delete(finding)
        auditTrail.record(entityType = "AuditFinding", event = "finding.removed", businessKey = key, before = before)
    }

    private fun writers() {
        policy.require(Role.COMPLIANCE_LEAD, Role.SYSTEM_ADMINISTRATOR)
        policy.assertNotReadOnlyActor()
    }

    private fun apply(audit: Audit, r: SaveAuditRequest) {
        require(r.kind in KINDS) { "An audit is internal, amsa, class, customer, flag or other" }
        require(r.status in STATUSES) { "An audit is planned, open or closed" }
        audit.kind = r.kind
        audit.auditor = r.auditor?.trim()?.ifEmpty { null }
        audit.auditDate = r.auditDate
        audit.scope = r.scope?.trim()?.ifEmpty { null }
        audit.outcome = r.outcome?.trim()?.ifEmpty { null }
        audit.status = r.status
        audit.note = r.note?.trim()?.ifEmpty { null }
    }

    private fun apply(finding: AuditFinding, r: SaveFindingRequest) {
        require(r.severity in SEVERITIES) { "A finding is major, minor or an observation" }
        require(r.description.isNotBlank()) { "Say what was found" }
        finding.reference = r.reference?.trim()?.ifEmpty { null }
        finding.severity = r.severity
        finding.description = r.description.trim()
        finding.correctiveAction = r.correctiveAction?.trim()?.ifEmpty { null }
        finding.owner = r.owner?.trim()?.ifEmpty { null }
        finding.dueOn = r.dueOn
        finding.closedOn = r.closedOn
    }

    private fun get(id: Long): Audit {
        val audit = audits.byIdLoaded(id) ?: throw EntityNotFoundException("No audit $id")
        policy.assertCanSeePartnership(audit.partnership.requiredId)
        return audit
    }

    private fun partnership(abbrev: String): Partnership = partnerships.byAbbrev(abbrev) ?: throw EntityNotFoundException("No partnership $abbrev")

    private fun snapshot(a: Audit) = mapOf("kind" to a.kind, "auditor" to a.auditor, "date" to a.auditDate.toString(), "scope" to a.scope, "outcome" to a.outcome, "status" to a.status, "note" to a.note)
    private fun snapshot(f: AuditFinding): Map<String, String?> = mapOf("reference" to f.reference, "severity" to f.severity, "description" to f.description, "correctiveAction" to f.correctiveAction, "owner" to f.owner, "dueOn" to f.dueOn?.toString(), "closedOn" to f.closedOn?.toString())

    private fun Audit.toDto(findingRows: List<AuditFinding>) = AuditDto(requiredId, kind, auditor, auditDate, scope, outcome, status, note, findingRows.map { it.toDto() })
    private fun AuditFinding.toDto() = AuditFindingDto(requiredId, audit.requiredId, reference, severity, description, correctiveAction, owner, dueOn, closedOn)

    companion object {
        val KINDS = setOf("internal", "amsa", "class", "customer", "flag", "other")
        val STATUSES = setOf("planned", "open", "closed")
        val SEVERITIES = setOf("major", "minor", "observation")
    }
}

@Path("/api/v1/ops/audits")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
class AuditResource(private val audits: AuditService) {

    @GET
    @Path("/{partnership}")
    @Operation(summary = "The ship's audits, newest first, with their findings")
    fun list(@PathParam("partnership") partnership: String): List<AuditDto> = audits.list(partnership)

    @POST
    @Path("/{partnership}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Record an audit — audited")
    fun create(@PathParam("partnership") partnership: String, request: SaveAuditRequest): AuditDto = audits.create(partnership, request)

    @PUT
    @Path("/item/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Change an audit — audited")
    fun update(@PathParam("id") id: Long, request: SaveAuditRequest): AuditDto = audits.update(id, request)

    @POST
    @Path("/item/{id}/findings")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Raise a finding on an audit — audited")
    fun addFinding(@PathParam("id") id: Long, request: SaveFindingRequest): AuditFindingDto = audits.addFinding(id, request)

    @PUT
    @Path("/findings/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Change or close a finding — audited")
    fun updateFinding(@PathParam("id") id: Long, request: SaveFindingRequest): AuditFindingDto = audits.updateFinding(id, request)

    @DELETE
    @Path("/findings/{id}")
    @Operation(summary = "Remove a finding raised in error — audited")
    fun removeFinding(@PathParam("id") id: Long): Response {
        audits.removeFinding(id)
        return Response.noContent().build()
    }
}
