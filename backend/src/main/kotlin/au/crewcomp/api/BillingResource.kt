package au.crewcomp.api

import au.crewcomp.platform.time.BusinessClock
import au.crewcomp.reference.BillingService
import au.crewcomp.reference.Invoice
import io.quarkus.security.Authenticated
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import java.math.BigDecimal
import java.time.LocalDate

/** Billing (BUS-1) — the office's invoices to its customers. System administrator only. */
@Path("/api/v1/business/invoices")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Business", description = "The business behind the program: invoices to customers")
class BillingResource(private val billing: BillingService, private val clock: BusinessClock) {

    @GET
    @Operation(summary = "Every invoice, newest period first — or one customer's")
    fun list(@QueryParam("customerId") customerId: Long?): List<InvoiceDto> = billing.list(customerId).map { it.toDto() }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Raise an invoice as a draft — audited")
    fun create(request: CreateInvoiceRequest): InvoiceDto =
        billing.create(request.customerId, request.periodStart, request.periodEnd, request.amount, request.reference, request.note).toDto()

    @PUT
    @Path("/{invoiceId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Change a draft's period, amount or note — audited")
    fun update(@PathParam("invoiceId") invoiceId: Long, request: UpdateInvoiceRequest): InvoiceDto =
        billing.update(invoiceId, request.periodStart, request.periodEnd, request.amount, request.note).toDto()

    @PUT
    @Path("/{invoiceId}/status")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Mark an invoice sent, paid, void, or back to draft — audited")
    fun setStatus(@PathParam("invoiceId") invoiceId: Long, request: SetInvoiceStatusRequest): InvoiceDto =
        billing.setStatus(invoiceId, request.status, request.on, request.dueOn, clock.today()).toDto()
}

data class InvoiceDto(
    val id: Long,
    val customerId: Long,
    val customerName: String,
    val reference: String,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val amount: BigDecimal,
    /** `draft` · `sent` · `paid` · `void`. */
    val status: String,
    val issuedOn: LocalDate?,
    val dueOn: LocalDate?,
    val paidOn: LocalDate?,
    val note: String?,
)

data class CreateInvoiceRequest(
    val customerId: Long,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val amount: BigDecimal,
    val reference: String? = null,
    val note: String? = null,
)

data class UpdateInvoiceRequest(
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val amount: BigDecimal,
    val note: String? = null,
)

data class SetInvoiceStatusRequest(
    val status: String,
    /** The day it was sent or paid; today when omitted. */
    val on: LocalDate? = null,
    /** For `sent`: when it falls due; a fortnight after sending when omitted. */
    val dueOn: LocalDate? = null,
)

fun Invoice.toDto() = InvoiceDto(
    id = requiredId,
    customerId = customer.requiredId,
    customerName = customer.name,
    reference = reference,
    periodStart = periodStart,
    periodEnd = periodEnd,
    amount = amount,
    status = status,
    issuedOn = issuedOn,
    dueOn = dueOn,
    paidOn = paidOn,
    note = note,
)
