package au.crewcomp.reference

import au.crewcomp.people.UserAccount
import au.crewcomp.people.UserAccountRepository
import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.persistence.AuditedEntity
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.DataScope
import au.crewcomp.platform.security.Role
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
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
import org.eclipse.microprofile.openapi.annotations.tags.Tag

/**
 * A fleet (BUS-2, V21): a named group of ships drawn from any customers — "the MinRes barges",
 * whoever operates each one — and the companies given sight of it. Portways, which monitors
 * every MinRes barge, is an overseer of that fleet without operating any of its ships.
 *
 * Overseeing is what gives a company's accounts their ships: an account linked to a customer is
 * scoped to the customer's own ships plus the ships of every fleet the customer oversees, and
 * that scope is recomputed whenever a fleet's ships or its overseers change — the office sets
 * the arrangement up once and nobody re-scopes accounts by hand.
 */
@Entity
@Table(name = "fleet")
class Fleet : AuditedEntity() {

    @Column(name = "name", nullable = false)
    lateinit var name: String

    @Column(name = "note")
    var note: String? = null

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "fleet_member", joinColumns = [JoinColumn(name = "fleet_id")])
    @Column(name = "partnership_id")
    var partnershipIds: MutableSet<Long> = mutableSetOf()

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "fleet_oversight", joinColumns = [JoinColumn(name = "fleet_id")])
    @Column(name = "customer_id")
    var overseerCustomerIds: MutableSet<Long> = mutableSetOf()
}

@ApplicationScoped
class FleetRepository : PanacheRepositoryBase<Fleet, Long> {
    fun allOrdered(): List<Fleet> = listAll(io.quarkus.panache.common.Sort.by("name"))
    fun byName(name: String): Fleet? = find("lower(name) = ?1", name.lowercase()).firstResult()
    fun overseenBy(customerId: Long): List<Fleet> = allOrdered().filter { customerId in it.overseerCustomerIds }
}

data class FleetDto(
    val id: Long,
    val name: String,
    val note: String?,
    val partnershipIds: List<Long>,
    val overseerCustomerIds: List<Long>,
)

data class SaveFleetRequest(val name: String, val note: String? = null)

data class SetIdsRequest(val ids: List<Long>)

data class LinkAccountRequest(val customerId: Long?)

@ApplicationScoped
class FleetService(
    private val fleets: FleetRepository,
    private val partnerships: PartnershipRepository,
    private val customers: CustomerRepository,
    private val accounts: UserAccountRepository,
    private val policy: AccessPolicy,
    private val audit: AuditWriter,
) {

    /** The office sees every fleet; anyone else sees the fleets touching a ship they may see. */
    @Transactional
    fun list(): List<FleetDto> {
        policy.actor()
        val all = fleets.allOrdered()
        val visible = if (policy.scope() is DataScope.All) all else all.filter { f -> f.partnershipIds.any { policy.canSeePartnership(it) } }
        return visible.map { it.toDto() }
    }

    /** The ships a customer can see through the fleets it oversees — none of them its own. */
    @Transactional
    fun overseenPartnershipIds(customerId: Long): List<Long> =
        fleets.overseenBy(customerId).flatMap { it.partnershipIds }.distinct().sorted()

    @Transactional
    fun create(name: String, note: String?): FleetDto {
        office()
        val clean = name.trim()
        require(clean.isNotEmpty()) { "A fleet needs a name" }
        fleets.byName(clean)?.let { throw IllegalArgumentException("There is already a fleet called ${it.name}") }
        val fleet = Fleet().apply {
            this.name = clean
            this.note = note?.trim()?.ifEmpty { null }
            stampCreated(policy.actor().label)
        }
        fleets.persist(fleet)
        fleets.flush()
        audit.record(entityType = "Fleet", event = "fleet.created", entityId = fleet.id, businessKey = fleet.name, after = snapshot(fleet))
        return fleet.toDto()
    }

    @Transactional
    fun update(id: Long, name: String, note: String?): FleetDto {
        office()
        val fleet = get(id)
        val clean = name.trim()
        require(clean.isNotEmpty()) { "A fleet needs a name" }
        fleets.byName(clean)?.takeIf { it.requiredId != id }?.let { throw IllegalArgumentException("There is already a fleet called ${it.name}") }
        val before = snapshot(fleet)
        fleet.name = clean
        fleet.note = note?.trim()?.ifEmpty { null }
        fleet.stampUpdated(policy.actor().label)
        audit.record(entityType = "Fleet", event = "fleet.updated", entityId = fleet.id, businessKey = fleet.name, before = before, after = snapshot(fleet))
        return fleet.toDto()
    }

    @Transactional
    fun delete(id: Long) {
        office()
        val fleet = get(id)
        val before = snapshot(fleet)
        val overseers = fleet.overseerCustomerIds.toSet()
        fleets.delete(fleet)
        fleets.flush()
        audit.record(entityType = "Fleet", event = "fleet.deleted", businessKey = before["name"] as String, before = before)
        overseers.forEach { resyncCustomer(it) }
    }

    /** The fleet's ships, replaced — and every overseer's accounts re-scoped to match. */
    @Transactional
    fun setMembers(id: Long, partnershipIds: List<Long>): FleetDto {
        office()
        val fleet = get(id)
        partnershipIds.forEach { pid -> partnerships.findById(pid) ?: throw EntityNotFoundException("No ship $pid") }
        val before = snapshot(fleet)
        fleet.partnershipIds.clear()
        fleet.partnershipIds.addAll(partnershipIds)
        fleet.stampUpdated(policy.actor().label)
        fleets.flush()
        audit.record(entityType = "Fleet", event = "fleet.ships_set", entityId = fleet.id, businessKey = fleet.name, before = before, after = snapshot(fleet))
        fleet.overseerCustomerIds.forEach { resyncCustomer(it) }
        return fleet.toDto()
    }

    /** The companies overseeing the fleet, replaced — those joining and those leaving re-scoped. */
    @Transactional
    fun setOverseers(id: Long, customerIds: List<Long>): FleetDto {
        office()
        val fleet = get(id)
        customerIds.forEach { cid -> customers.findById(cid) ?: throw EntityNotFoundException("No customer $cid") }
        val before = snapshot(fleet)
        val affected = fleet.overseerCustomerIds.toSet() + customerIds
        fleet.overseerCustomerIds.clear()
        fleet.overseerCustomerIds.addAll(customerIds)
        fleet.stampUpdated(policy.actor().label)
        fleets.flush()
        audit.record(entityType = "Fleet", event = "fleet.overseers_set", entityId = fleet.id, businessKey = fleet.name, before = before, after = snapshot(fleet))
        affected.forEach { resyncCustomer(it) }
        return fleet.toDto()
    }

    /** An account attached to (or detached from) the company it works for, and re-scoped. */
    @Transactional
    fun linkAccount(accountId: Long, customerId: Long?) {
        office()
        val account = accounts.findById(accountId) ?: throw EntityNotFoundException("No account $accountId")
        customerId?.let { customers.findById(it) ?: throw EntityNotFoundException("No customer $it") }
        val before = account.customerId
        account.customerId = customerId
        account.stampUpdated(policy.actor().label)
        audit.record(entityType = "UserAccount", event = "account.company_set", entityId = account.id, businessKey = account.displayName, before = mapOf("customerId" to before), after = mapOf("customerId" to customerId))
        if (customerId != null) resync(account, customerId)
    }

    /** Every account linked to [customerId], scoped to its own ships plus the fleets it oversees. */
    private fun resyncCustomer(customerId: Long) {
        accounts.list("customerId = ?1", customerId).forEach { resync(it, customerId) }
    }

    private fun resync(account: UserAccount, customerId: Long) {
        val roles = account.roles
        // The office reads everything and a crew member reads their own record; neither takes a ship scope.
        if (Role.SYSTEM_ADMINISTRATOR in roles) return
        if (roles.isNotEmpty() && roles.all { it == Role.CREW_MEMBER }) return
        val wanted = (partnerships.forCustomer(customerId).map { it.requiredId } + overseenPartnershipIds(customerId)).toSet()
        // An empty scope on a back-office role reads the whole dataset (AccessPolicy.scope), so a
        // company left with no ships keeps its last scope rather than being handed everyone's.
        // A read-only oversight account (Vessel Master alone) with no ships reads nothing, which is right.
        if (wanted.isEmpty() && roles.any { it in Role.UNRESTRICTED_READERS }) return
        if (account.scopedPartnershipIds == wanted) return
        val before = account.scopedPartnershipIds.sorted()
        account.scopedPartnershipIds.clear()
        account.scopedPartnershipIds.addAll(wanted)
        account.stampUpdated(policy.actor().label)
        audit.record(entityType = "UserAccount", event = "account.scopes_synced", entityId = account.id, businessKey = account.displayName, before = mapOf("partnershipIds" to before), after = mapOf("partnershipIds" to wanted.sorted(), "customerId" to customerId))
    }

    private fun office() = policy.require(Role.SYSTEM_ADMINISTRATOR)

    private fun get(id: Long): Fleet = fleets.findById(id) ?: throw EntityNotFoundException("No fleet $id")

    private fun snapshot(fleet: Fleet): Map<String, Any?> = mapOf(
        "name" to fleet.name,
        "note" to fleet.note,
        "ships" to fleet.partnershipIds.mapNotNull { partnerships.findById(it)?.abbrev }.sorted(),
        "overseers" to fleet.overseerCustomerIds.mapNotNull { customers.findById(it)?.name }.sorted(),
    )

    private fun Fleet.toDto() = FleetDto(requiredId, name, note, partnershipIds.sorted(), overseerCustomerIds.sorted())
}

@Path("/api/v1/business")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Fleets", description = "Fleets across operators, and the companies that oversee them")
class FleetResource(private val fleets: FleetService) {

    @GET
    @Path("/fleets")
    @Operation(summary = "The fleets the caller may see, with their ships and overseers")
    fun list(): List<FleetDto> = fleets.list()

    @POST
    @Path("/fleets")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Make a fleet (system administrator) — audited")
    fun create(request: SaveFleetRequest): FleetDto = fleets.create(request.name, request.note)

    @PUT
    @Path("/fleets/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Rename a fleet or change its note — audited")
    fun update(@PathParam("id") id: Long, request: SaveFleetRequest): FleetDto = fleets.update(id, request.name, request.note)

    @DELETE
    @Path("/fleets/{id}")
    @Operation(summary = "Remove a fleet; its overseers lose sight of its ships — audited")
    fun delete(@PathParam("id") id: Long): Response {
        fleets.delete(id)
        return Response.noContent().build()
    }

    @PUT
    @Path("/fleets/{id}/ships")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Set the fleet's ships; overseers' accounts follow — audited")
    fun setShips(@PathParam("id") id: Long, request: SetIdsRequest): FleetDto = fleets.setMembers(id, request.ids)

    @PUT
    @Path("/fleets/{id}/overseers")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Set the companies overseeing the fleet; their accounts follow — audited")
    fun setOverseers(@PathParam("id") id: Long, request: SetIdsRequest): FleetDto = fleets.setOverseers(id, request.ids)

    @PUT
    @Path("/accounts/{accountId}/company")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Attach an account to the company it works for; its ships follow the company — audited")
    fun linkAccount(@PathParam("accountId") accountId: Long, request: LinkAccountRequest): Response {
        fleets.linkAccount(accountId, request.customerId)
        return Response.noContent().build()
    }
}
