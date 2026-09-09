package au.crewcomp.api

import au.crewcomp.reference.Customer
import au.crewcomp.reference.CustomerService
import io.quarkus.security.Authenticated
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

/** COM-1 — the customer directory: the client companies the operation works for. */
@Path("/api/v1/customers")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Customers", description = "The client companies the operation works for (COM-1)")
class CustomerResource(private val customers: CustomerService) {

    @GET
    @Operation(summary = "Every customer, by name, with the partnerships run for each")
    fun list(): List<CustomerDto> {
        // The business side travels only to the office (BUS-1); a customer's own staff get the directory.
        val business = customers.canSeeBusiness()
        return customers.list().map { it.toDto(customers.partnershipsFor(it.requiredId), business) }
    }

    @GET
    @Path("/unattached-partnerships")
    @Operation(summary = "Partnerships not yet attached to a customer")
    fun unattached(): List<PartnershipDto> = customers.unattachedPartnerships().map { it.toDto() }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Add a customer — audited")
    fun create(request: SaveCustomerRequest): CustomerDto = customers.create(
        name = request.name,
        shortName = request.shortName,
        contactName = request.contactName,
        contactEmail = request.contactEmail,
        contactPhone = request.contactPhone,
        notes = request.notes,
    ).let { it.toDto(emptyList()) }

    @PUT
    @Path("/{customerId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Update a customer — audited")
    fun update(@PathParam("customerId") customerId: Long, request: SaveCustomerRequest): CustomerDto =
        customers.update(
            customerId = customerId,
            name = request.name,
            shortName = request.shortName,
            contactName = request.contactName,
            contactEmail = request.contactEmail,
            contactPhone = request.contactPhone,
            notes = request.notes,
            status = request.status,
        ).let { it.toDto(customers.partnershipsFor(customerId)) }

    @DELETE
    @Path("/{customerId}")
    @Operation(summary = "Remove a customer — refused while operations are attached; audited")
    fun delete(@PathParam("customerId") customerId: Long): Response {
        customers.delete(customerId)
        return Response.noContent().build()
    }

    @PUT
    @Path("/{customerId}/partnerships/{partnershipId}")
    @Operation(summary = "Attach a partnership to this customer — audited on the partnership")
    fun attach(
        @PathParam("customerId") customerId: Long,
        @PathParam("partnershipId") partnershipId: Long,
    ): PartnershipDto = customers.attachPartnership(partnershipId, customerId).toDto()

    @PUT
    @Path("/partnerships/{partnershipId}/detach")
    @Operation(summary = "Detach a partnership from its customer — audited on the partnership")
    fun detach(@PathParam("partnershipId") partnershipId: Long): PartnershipDto =
        customers.attachPartnership(partnershipId, null).toDto()

    // --- The fleet ---------------------------------------------------------------------------

    @POST
    @Path("/{customerId}/partnerships")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Add an operation (a partnership) for this customer — audited")
    fun createPartnership(
        @PathParam("customerId") customerId: Long,
        request: CreatePartnershipRequest,
    ): PartnershipDto =
        customers.createPartnership(customerId, request.abbrev, request.name, request.vesselClass, request.registry, request.regime).toDto()

    @DELETE
    @Path("/partnerships/{partnershipId}")
    @Operation(summary = "Remove an operation — refused while crew, swings or records hang off it; audited")
    fun deletePartnership(@PathParam("partnershipId") partnershipId: Long): Response {
        customers.deletePartnership(partnershipId)
        return Response.noContent().build()
    }

    @GET
    @Path("/vessels")
    @Operation(summary = "Every vessel, by operation then name — the fleet view")
    fun vessels(): List<VesselDto> = customers.listVessels().map { it.toDto() }

    @POST
    @Path("/partnerships/{partnershipId}/vessels")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Add a vessel to an operation — audited")
    fun addVessel(@PathParam("partnershipId") partnershipId: Long, request: AddVesselRequest): VesselDto =
        customers.addVessel(partnershipId, request.name, request.kind).toDto()

    @DELETE
    @Path("/vessels/{vesselId}")
    @Operation(summary = "Remove a vessel — audited")
    fun removeVessel(@PathParam("vesselId") vesselId: Long): Response {
        customers.removeVessel(vesselId)
        return Response.noContent().build()
    }

    @PUT
    @Path("/{customerId}/business")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "The business side of a customer — billing address, plan, rate (system administrator) — audited")
    fun setBusiness(@PathParam("customerId") customerId: Long, request: SaveBusinessRequest): CustomerDto {
        val customer = customers.updateBusiness(
            customerId, request.billingEmail, request.abn, request.address, request.plan, request.ratePerShipMonth, request.billingNotes,
        )
        return customer.toDto(customers.partnershipsFor(customerId), business = true)
    }
}

fun au.crewcomp.reference.Vessel.toDto() = VesselDto(
    id = requiredId,
    name = name,
    kind = kind,
    partnershipId = partnership.requiredId,
)

fun Customer.toDto(partnerships: List<au.crewcomp.reference.Partnership>, business: Boolean = false) = CustomerDto(
    id = requiredId,
    name = name,
    shortName = shortName,
    contactName = contactName,
    contactEmail = contactEmail,
    contactPhone = contactPhone,
    notes = notes,
    status = status,
    partnershipIds = partnerships.map { it.requiredId },
    billingEmail = if (business) billingEmail else null,
    abn = if (business) abn else null,
    address = if (business) address else null,
    plan = if (business) plan else null,
    ratePerShipMonth = if (business) ratePerShipMonth else null,
    billingNotes = if (business) billingNotes else null,
)
