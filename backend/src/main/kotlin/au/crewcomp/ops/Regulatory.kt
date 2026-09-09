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
 * AMSA requirements (OPS): the obligations a domestic commercial vessel carries under the
 * National Law and its Marine Orders, one line each, with where it stands — met, not met, not
 * yet known, not applicable — the evidence that says so, and when it next falls due. The office
 * starts a ship from the standard list and edits it to the vessel.
 */
@Entity
@Table(name = "regulatory_item")
class RegulatoryItem : AuditedEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partnership_id", nullable = false)
    lateinit var partnership: Partnership

    @Column(name = "reference", nullable = false)
    lateinit var reference: String

    @Column(name = "title", nullable = false)
    lateinit var title: String

    /** `certificate` · `system` · `record` · `survey` · `other`. */
    @Column(name = "kind", nullable = false)
    lateinit var kind: String

    /** `met` · `not_met` · `unknown` · `not_applicable`. */
    @Column(name = "status", nullable = false)
    var status: String = "unknown"

    @Column(name = "evidence", columnDefinition = "text")
    var evidence: String? = null

    @Column(name = "next_due")
    var nextDue: LocalDate? = null

    @Column(name = "responsible")
    var responsible: String? = null

    @Column(name = "note", columnDefinition = "text")
    var note: String? = null
}

@ApplicationScoped
class RegulatoryItemRepository : PanacheRepositoryBase<RegulatoryItem, Long> {
    fun forPartnership(partnershipId: Long): List<RegulatoryItem> = list("partnership.id = ?1 order by id", partnershipId)
    fun byIdLoaded(id: Long): RegulatoryItem? = find("from RegulatoryItem r join fetch r.partnership where r.id = ?1", id).firstResult()
}

data class RegulatoryItemDto(
    val id: Long,
    val reference: String,
    val title: String,
    val kind: String,
    val status: String,
    val evidence: String?,
    val nextDue: LocalDate?,
    val responsible: String?,
    val note: String?,
)

data class SaveRegulatoryItemRequest(
    val reference: String,
    val title: String,
    val kind: String,
    val status: String = "unknown",
    val evidence: String? = null,
    val nextDue: LocalDate? = null,
    val responsible: String? = null,
    val note: String? = null,
)

@ApplicationScoped
class RegulatoryService(
    private val items: RegulatoryItemRepository,
    private val partnerships: PartnershipRepository,
    private val policy: AccessPolicy,
    private val audit: AuditWriter,
) {

    @Transactional
    fun list(abbrev: String): List<RegulatoryItemDto> {
        val partnership = partnership(abbrev)
        policy.assertCanSeePartnership(partnership.requiredId)
        return items.forPartnership(partnership.requiredId).map { it.toDto() }
    }

    @Transactional
    fun create(abbrev: String, request: SaveRegulatoryItemRequest): RegulatoryItemDto {
        writers()
        val partnership = partnership(abbrev)
        policy.assertCanSeePartnership(partnership.requiredId)
        val item = RegulatoryItem().apply {
            this.partnership = partnership
            stampCreated(policy.actor().label)
        }
        apply(item, request)
        items.persist(item)
        items.flush()
        audit.record(entityType = "RegulatoryItem", event = "regulatory.added", entityId = item.id, businessKey = "${partnership.abbrev}/${item.reference}", after = snapshot(item))
        return item.toDto()
    }

    /** The standard National Law list for a domestic commercial vessel, added where the ship does not yet carry the reference. */
    @Transactional
    fun addStandardList(abbrev: String): List<RegulatoryItemDto> {
        writers()
        val partnership = partnership(abbrev)
        policy.assertCanSeePartnership(partnership.requiredId)
        val have = items.forPartnership(partnership.requiredId).map { it.reference + "|" + it.title }.toSet()
        var added = 0
        val list = if (partnership.regime == "international") INTERNATIONAL else STANDARD
        list.forEach { (reference, title, kind) ->
            if ("$reference|$title" in have) return@forEach
            items.persist(
                RegulatoryItem().apply {
                    this.partnership = partnership
                    this.reference = reference
                    this.title = title
                    this.kind = kind
                    stampCreated(policy.actor().label)
                },
            )
            added++
        }
        items.flush()
        if (added > 0) audit.record(entityType = "RegulatoryItem", event = "regulatory.standard_list_added", businessKey = partnership.abbrev, after = mapOf("added" to added))
        return items.forPartnership(partnership.requiredId).map { it.toDto() }
    }

    @Transactional
    fun update(id: Long, request: SaveRegulatoryItemRequest): RegulatoryItemDto {
        writers()
        val item = get(id)
        val before = snapshot(item)
        apply(item, request)
        item.stampUpdated(policy.actor().label)
        audit.record(entityType = "RegulatoryItem", event = "regulatory.updated", entityId = item.id, businessKey = "${item.partnership.abbrev}/${item.reference}", before = before, after = snapshot(item))
        return item.toDto()
    }

    @Transactional
    fun remove(id: Long) {
        writers()
        val item = get(id)
        val before = snapshot(item)
        val key = "${item.partnership.abbrev}/${item.reference}"
        items.delete(item)
        audit.record(entityType = "RegulatoryItem", event = "regulatory.removed", businessKey = key, before = before)
    }

    private fun writers() {
        policy.require(Role.COMPLIANCE_LEAD, Role.SYSTEM_ADMINISTRATOR)
        policy.assertNotReadOnlyActor()
    }

    private fun apply(item: RegulatoryItem, r: SaveRegulatoryItemRequest) {
        require(r.reference.isNotBlank() && r.title.isNotBlank()) { "A requirement needs a reference and a title" }
        require(r.kind in KINDS) { "A requirement is a certificate, a system, a record, a survey or other" }
        require(r.status in STATUSES) { "Status is met, not_met, unknown or not_applicable" }
        item.reference = r.reference.trim()
        item.title = r.title.trim()
        item.kind = r.kind
        item.status = r.status
        item.evidence = r.evidence?.trim()?.ifEmpty { null }
        item.nextDue = r.nextDue
        item.responsible = r.responsible?.trim()?.ifEmpty { null }
        item.note = r.note?.trim()?.ifEmpty { null }
    }

    private fun get(id: Long): RegulatoryItem {
        val item = items.byIdLoaded(id) ?: throw EntityNotFoundException("No requirement $id")
        policy.assertCanSeePartnership(item.partnership.requiredId)
        return item
    }

    private fun partnership(abbrev: String): Partnership = partnerships.byAbbrev(abbrev) ?: throw EntityNotFoundException("No partnership $abbrev")

    private fun snapshot(i: RegulatoryItem): Map<String, String?> = mapOf("reference" to i.reference, "title" to i.title, "kind" to i.kind, "status" to i.status, "evidence" to i.evidence, "nextDue" to i.nextDue?.toString(), "responsible" to i.responsible, "note" to i.note)

    private fun RegulatoryItem.toDto() = RegulatoryItemDto(requiredId, reference, title, kind, status, evidence, nextDue, responsible, note)

    companion object {
        val KINDS = setOf("certificate", "system", "record", "survey", "other")
        val STATUSES = setOf("met", "not_met", "unknown", "not_applicable")

        /**
         * The standard list for a domestic commercial vessel under the Marine Safety (Domestic
         * Commercial Vessel) National Law. A starting point the office edits to the vessel —
         * the Marine Order numbers are the ones the office quotes; the vessel's own certificates
         * say which apply.
         */
        val STANDARD: List<Triple<String, String, String>> = listOf(
            Triple("Marine Order 504", "Certificate of Operation held and current", "certificate"),
            Triple("Marine Order 504", "Safety Management System in place, reviewed within the year", "system"),
            Triple("Marine Order 504", "Appropriate crewing determined and recorded for the operation", "record"),
            Triple("Marine Order 504", "Emergency drills held and recorded", "record"),
            Triple("Marine Order 504", "Fatigue management: hours of work and rest recorded", "record"),
            Triple("Marine Order 504", "Marine incidents reported to AMSA within the required time", "record"),
            Triple("Marine Order 503", "Certificate of Survey held and current", "certificate"),
            Triple("Marine Order 503", "Periodic survey completed by the due date", "survey"),
            Triple("Marine Order 503", "Unique identifier displayed on the vessel", "record"),
            Triple("Marine Order 505", "Crew hold the certificates of competency the operation requires", "record"),
            Triple("Marine Order 505", "Crew medical fitness current where required", "record"),
            Triple("Marine Order 27", "Radio equipment fitted, surveyed and licensed", "certificate"),
            Triple("National Law", "Safety equipment carried and in date (lifejackets, EPIRB, flares, fire)", "survey"),
            Triple("National Law", "Insurance held as the operation requires", "certificate"),
        )

        /** The equivalent for a vessel under the international conventions — SOLAS, STCW, MLC 2006, ISM. */
        val INTERNATIONAL: List<Triple<String, String, String>> = listOf(
            Triple("SOLAS", "Safety certificates held and current (construction, equipment, radio)", "certificate"),
            Triple("SOLAS / Minimum Safe Manning", "Minimum Safe Manning Document held; the vessel crewed to it", "certificate"),
            Triple("ISM Code", "Safety Management Certificate and Document of Compliance current", "certificate"),
            Triple("ISM Code", "Safety Management System audited within the period", "system"),
            Triple("STCW", "Crew hold the STCW certificates the Safe Manning Document requires", "record"),
            Triple("STCW", "Medical fitness certificates current", "record"),
            Triple("MLC 2006", "Hours of work and rest recorded and within limits", "record"),
            Triple("MLC 2006", "Maritime Labour Certificate current", "certificate"),
            Triple("ISPS Code", "International Ship Security Certificate current; ship security plan in force", "certificate"),
            Triple("Load Line", "International Load Line Certificate current", "certificate"),
            Triple("MARPOL", "Pollution prevention certificates current", "certificate"),
            Triple("Class", "Class certificate current; surveys completed by their due dates", "survey"),
            Triple("Flag State", "Registration and tonnage certificates current", "certificate"),
            Triple("Insurance", "P&I and hull insurance held", "certificate"),
        )
    }
}

@Path("/api/v1/ops/regulatory")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
class RegulatoryResource(private val regulatory: RegulatoryService) {

    @GET
    @Path("/{partnership}")
    @Operation(summary = "The ship's National Law requirements and where each stands")
    fun list(@PathParam("partnership") partnership: String): List<RegulatoryItemDto> = regulatory.list(partnership)

    @POST
    @Path("/{partnership}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Add a requirement — audited")
    fun create(@PathParam("partnership") partnership: String, request: SaveRegulatoryItemRequest): RegulatoryItemDto = regulatory.create(partnership, request)

    @POST
    @Path("/{partnership}/standard")
    @Operation(summary = "Add the standard domestic commercial vessel list — audited")
    fun standard(@PathParam("partnership") partnership: String): List<RegulatoryItemDto> = regulatory.addStandardList(partnership)

    @PUT
    @Path("/item/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Change a requirement's status, evidence or due date — audited")
    fun update(@PathParam("id") id: Long, request: SaveRegulatoryItemRequest): RegulatoryItemDto = regulatory.update(id, request)

    @DELETE
    @Path("/item/{id}")
    @Operation(summary = "Remove a requirement — audited")
    fun remove(@PathParam("id") id: Long): Response {
        regulatory.remove(id)
        return Response.noContent().build()
    }
}
