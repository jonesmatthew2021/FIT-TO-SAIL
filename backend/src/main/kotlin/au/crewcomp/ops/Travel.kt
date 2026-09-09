package au.crewcomp.ops

import au.crewcomp.people.Person
import au.crewcomp.people.PersonRepository
import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.persistence.AuditedEntity
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.Role
import au.crewcomp.reference.CrewChange
import au.crewcomp.reference.CrewChangeRepository
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
 * Crew change travel (OPS): the flights, beds and transfers behind a swing, per person or for
 * the change as a whole, each planned, booked, done or cancelled. The checklist the office
 * works through the week before a fly-out.
 */
@Entity
@Table(name = "travel_item")
class TravelItem : AuditedEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crew_change_id", nullable = false)
    lateinit var crewChange: CrewChange

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    var person: Person? = null

    /** `flight` · `accommodation` · `transfer` · `other`. */
    @Column(name = "kind", nullable = false)
    lateinit var kind: String

    @Column(name = "detail", nullable = false)
    lateinit var detail: String

    @Column(name = "on_date")
    var onDate: LocalDate? = null

    /** `planned` · `booked` · `done` · `cancelled`. */
    @Column(name = "status", nullable = false)
    var status: String = "planned"

    @Column(name = "note")
    var note: String? = null
}

@ApplicationScoped
class TravelItemRepository : PanacheRepositoryBase<TravelItem, Long> {
    fun forCrewChange(crewChangeId: Long): List<TravelItem> =
        find("from TravelItem t left join fetch t.person where t.crewChange.id = ?1 order by t.onDate nulls last, t.id", crewChangeId).list()

    fun byIdLoaded(id: Long): TravelItem? =
        find("from TravelItem t join fetch t.crewChange c join fetch c.partnership left join fetch t.person where t.id = ?1", id).firstResult()
}

data class TravelItemDto(
    val id: Long,
    val ccId: String,
    val personId: Long?,
    val personName: String?,
    val kind: String,
    val detail: String,
    val onDate: LocalDate?,
    val status: String,
    val note: String?,
)

data class SaveTravelItemRequest(
    val personId: Long? = null,
    val kind: String,
    val detail: String,
    val onDate: LocalDate? = null,
    val status: String = "planned",
    val note: String? = null,
)

@ApplicationScoped
class TravelService(
    private val items: TravelItemRepository,
    private val crewChanges: CrewChangeRepository,
    private val people: PersonRepository,
    private val policy: AccessPolicy,
    private val audit: AuditWriter,
) {

    @Transactional
    fun list(abbrev: String, ccId: String): List<TravelItemDto> {
        val crewChange = crewChange(abbrev, ccId)
        return items.forCrewChange(crewChange.requiredId).map { it.toDto() }
    }

    @Transactional
    fun create(abbrev: String, ccId: String, request: SaveTravelItemRequest): TravelItemDto {
        writers()
        val crewChange = crewChange(abbrev, ccId)
        val item = TravelItem().apply {
            this.crewChange = crewChange
            stampCreated(policy.actor().label)
        }
        apply(item, request)
        items.persist(item)
        items.flush()
        audit.record(entityType = "TravelItem", event = "travel.added", entityId = item.id, businessKey = "$abbrev/$ccId", after = snapshot(item))
        return item.toDto()
    }

    @Transactional
    fun update(id: Long, request: SaveTravelItemRequest): TravelItemDto {
        writers()
        val item = get(id)
        val before = snapshot(item)
        apply(item, request)
        item.stampUpdated(policy.actor().label)
        audit.record(entityType = "TravelItem", event = "travel.updated", entityId = item.id, businessKey = "${item.crewChange.partnership.abbrev}/${item.crewChange.ccId}", before = before, after = snapshot(item))
        return item.toDto()
    }

    @Transactional
    fun delete(id: Long) {
        writers()
        val item = get(id)
        val before = snapshot(item)
        val key = "${item.crewChange.partnership.abbrev}/${item.crewChange.ccId}"
        items.delete(item)
        audit.record(entityType = "TravelItem", event = "travel.removed", businessKey = key, before = before)
    }

    private fun writers() {
        policy.require(Role.CREW_COORDINATOR, Role.COMPLIANCE_LEAD, Role.SYSTEM_ADMINISTRATOR)
        policy.assertNotReadOnlyActor()
    }

    private fun apply(item: TravelItem, request: SaveTravelItemRequest) {
        require(request.kind in KINDS) { "Travel is a flight, accommodation, a transfer or other" }
        require(request.status in STATUSES) { "Status is planned, booked, done or cancelled" }
        require(request.detail.isNotBlank()) { "Say what it is — the flight number, the hotel, the transfer" }
        item.kind = request.kind
        item.detail = request.detail.trim()
        item.onDate = request.onDate
        item.status = request.status
        item.note = request.note?.trim()?.ifEmpty { null }
        item.person = request.personId?.let { id ->
            val person = people.findById(id) ?: throw EntityNotFoundException("No person $id")
            policy.assertCanSeePerson(person.requiredId, person.partnership.requiredId)
            person
        }
    }

    private fun get(id: Long): TravelItem {
        val item = items.byIdLoaded(id) ?: throw EntityNotFoundException("No travel item $id")
        policy.assertCanSeePartnership(item.crewChange.partnership.requiredId)
        return item
    }

    private fun crewChange(abbrev: String, ccId: String): CrewChange {
        val crewChange = crewChanges.byBusinessKey(ccId, abbrev) ?: throw EntityNotFoundException("No swing $ccId on $abbrev")
        policy.assertCanSeePartnership(crewChange.partnership.requiredId)
        return crewChange
    }

    private fun snapshot(item: TravelItem) = mapOf(
        "person" to item.person?.name, "kind" to item.kind, "detail" to item.detail,
        "onDate" to item.onDate?.toString(), "status" to item.status, "note" to item.note,
    )

    private fun TravelItem.toDto() = TravelItemDto(
        id = requiredId,
        ccId = crewChange.ccId,
        personId = person?.requiredId,
        personName = person?.name,
        kind = kind,
        detail = detail,
        onDate = onDate,
        status = status,
        note = note,
    )

    companion object {
        val KINDS = setOf("flight", "accommodation", "transfer", "other")
        val STATUSES = setOf("planned", "booked", "done", "cancelled")
    }
}

@Path("/api/v1/ops/travel")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
class TravelResource(private val travel: TravelService) {

    @GET
    @Path("/{partnership}/{cc}")
    @Operation(summary = "The travel behind a swing")
    fun list(@PathParam("partnership") partnership: String, @PathParam("cc") cc: String): List<TravelItemDto> = travel.list(partnership, cc)

    @POST
    @Path("/{partnership}/{cc}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Add a flight, a bed or a transfer — audited")
    fun create(@PathParam("partnership") partnership: String, @PathParam("cc") cc: String, request: SaveTravelItemRequest): TravelItemDto =
        travel.create(partnership, cc, request)

    @PUT
    @Path("/item/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Change a travel item — audited")
    fun update(@PathParam("id") id: Long, request: SaveTravelItemRequest): TravelItemDto = travel.update(id, request)

    @DELETE
    @Path("/item/{id}")
    @Operation(summary = "Remove a travel item — audited")
    fun delete(@PathParam("id") id: Long): Response {
        travel.delete(id)
        return Response.noContent().build()
    }
}
