package au.crewcomp.reference

import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.persistence.AuditedEntity
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.Role
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.transaction.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * One bill to a customer (BUS-1, V17): a period, an amount, and where it stands — drafted, sent,
 * paid, or void. The office's own record of what it has charged and what has come in; the
 * accountant's ledger is elsewhere. Nothing in the compliance engine reads it.
 */
@Entity
@Table(name = "invoice")
class Invoice : AuditedEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    lateinit var customer: Customer

    @Column(name = "reference", nullable = false)
    lateinit var reference: String

    @Column(name = "period_start", nullable = false)
    lateinit var periodStart: LocalDate

    @Column(name = "period_end", nullable = false)
    lateinit var periodEnd: LocalDate

    @Column(name = "amount", nullable = false)
    lateinit var amount: BigDecimal

    @Column(name = "status", nullable = false)
    var status: String = "draft"

    @Column(name = "issued_on")
    var issuedOn: LocalDate? = null

    @Column(name = "due_on")
    var dueOn: LocalDate? = null

    @Column(name = "paid_on")
    var paidOn: LocalDate? = null

    @Column(name = "note")
    var note: String? = null
}

@ApplicationScoped
class InvoiceRepository : PanacheRepositoryBase<Invoice, Long> {
    fun allOrdered(): List<Invoice> =
        find("from Invoice i join fetch i.customer order by i.periodStart desc, i.id desc").list()

    fun forCustomer(customerId: Long): List<Invoice> =
        find("from Invoice i join fetch i.customer where i.customer.id = ?1 order by i.periodStart desc, i.id desc", customerId).list()

    fun byReference(reference: String): Invoice? = find("reference", reference).firstResult()
}

/**
 * Billing — the office's side of the business. System administrator only: a customer's own
 * staff never see what they are charged here, and the compliance roles have no business with it.
 */
@ApplicationScoped
class BillingService(
    private val invoices: InvoiceRepository,
    private val customers: CustomerRepository,
    private val policy: AccessPolicy,
    private val audit: AuditWriter,
) {

    @Transactional
    fun list(customerId: Long?): List<Invoice> {
        policy.require(Role.SYSTEM_ADMINISTRATOR)
        return if (customerId == null) invoices.allOrdered() else invoices.forCustomer(customerId)
    }

    @Transactional
    fun create(
        customerId: Long,
        periodStart: LocalDate,
        periodEnd: LocalDate,
        amount: BigDecimal,
        reference: String?,
        note: String?,
    ): Invoice {
        policy.require(Role.SYSTEM_ADMINISTRATOR)
        val customer = customers.findById(customerId) ?: throw EntityNotFoundException("No customer $customerId")
        require(!periodEnd.isBefore(periodStart)) { "The period ends before it starts" }
        require(amount.signum() >= 0) { "An invoice cannot be for a negative amount" }
        val ref = reference?.trim()?.ifEmpty { null } ?: nextReference(periodStart)
        invoices.byReference(ref)?.let { throw IllegalArgumentException("Invoice $ref already exists") }

        val invoice = Invoice().apply {
            this.customer = customer
            this.reference = ref
            this.periodStart = periodStart
            this.periodEnd = periodEnd
            this.amount = amount.setScale(2, RoundingMode.HALF_UP)
            this.note = note?.trim()?.ifEmpty { null }
            stampCreated(policy.actor().label)
        }
        invoices.persist(invoice)
        invoices.flush()
        audit.record(
            entityType = "Invoice",
            event = "invoice.created",
            entityId = invoice.id,
            businessKey = invoice.reference,
            after = snapshot(invoice),
        )
        return invoice
    }

    @Transactional
    fun update(invoiceId: Long, periodStart: LocalDate, periodEnd: LocalDate, amount: BigDecimal, note: String?): Invoice {
        policy.require(Role.SYSTEM_ADMINISTRATOR)
        val invoice = get(invoiceId)
        require(invoice.status == "draft") { "Only a draft can be changed — void it and raise another" }
        require(!periodEnd.isBefore(periodStart)) { "The period ends before it starts" }
        val before = snapshot(invoice)
        invoice.periodStart = periodStart
        invoice.periodEnd = periodEnd
        invoice.amount = amount.setScale(2, RoundingMode.HALF_UP)
        invoice.note = note?.trim()?.ifEmpty { null }
        invoice.stampUpdated(policy.actor().label)
        audit.record(entityType = "Invoice", event = "invoice.updated", entityId = invoice.id, businessKey = invoice.reference, before = before, after = snapshot(invoice))
        return invoice
    }

    /** Draft → sent → paid, or void from either; the dates are the office's, defaulting to today. */
    @Transactional
    fun setStatus(invoiceId: Long, status: String, on: LocalDate?, dueOn: LocalDate?, today: LocalDate): Invoice {
        policy.require(Role.SYSTEM_ADMINISTRATOR)
        require(status in STATUSES) { "Status must be one of ${STATUSES.joinToString()}" }
        val invoice = get(invoiceId)
        val before = snapshot(invoice)
        when (status) {
            "sent" -> {
                invoice.issuedOn = on ?: today
                invoice.dueOn = dueOn ?: (on ?: today).plusDays(DEFAULT_TERMS_DAYS)
                invoice.paidOn = null
            }
            "paid" -> {
                if (invoice.issuedOn == null) invoice.issuedOn = on ?: today
                invoice.paidOn = on ?: today
            }
            "draft" -> {
                invoice.issuedOn = null
                invoice.dueOn = null
                invoice.paidOn = null
            }
            "void" -> invoice.paidOn = null
        }
        invoice.status = status
        invoice.stampUpdated(policy.actor().label)
        audit.record(entityType = "Invoice", event = "invoice.status_set", entityId = invoice.id, businessKey = invoice.reference, before = before, after = snapshot(invoice))
        return invoice
    }

    private fun get(invoiceId: Long): Invoice =
        invoices.findById(invoiceId) ?: throw EntityNotFoundException("No invoice $invoiceId")

    private fun nextReference(periodStart: LocalDate): String {
        val stem = "INV-${periodStart.year}"
        var n = invoices.count() + 1
        while (invoices.byReference("$stem-%04d".format(n)) != null) n++
        return "$stem-%04d".format(n)
    }

    private fun snapshot(invoice: Invoice): Map<String, Any?> = mapOf(
        "customer" to invoice.customer.name,
        "reference" to invoice.reference,
        "periodStart" to invoice.periodStart.toString(),
        "periodEnd" to invoice.periodEnd.toString(),
        "amount" to invoice.amount.toPlainString(),
        "status" to invoice.status,
        "issuedOn" to invoice.issuedOn?.toString(),
        "dueOn" to invoice.dueOn?.toString(),
        "paidOn" to invoice.paidOn?.toString(),
        "note" to invoice.note,
    )

    companion object {
        val STATUSES = setOf("draft", "sent", "paid", "void")
        const val DEFAULT_TERMS_DAYS = 14L
    }
}
