package au.crewcomp.reference

import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.Role
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

/**
 * COM-1 — the customer directory: the client companies the operation works for, and which
 * partnerships are run for each.
 *
 * Writes are the Compliance Lead's and the administrator's, as the catalogue's are: a customer is
 * reference data the whole office reads and few people should change. Every write is audited.
 * Nothing here touches the compliance engine — a partnership's customer is a label on the
 * operation, and the engine evaluates operations.
 */
@ApplicationScoped
class CustomerService(
    private val customers: CustomerRepository,
    private val partnerships: PartnershipRepository,
    private val policy: AccessPolicy,
    private val audit: AuditWriter,
) {

    @Transactional
    fun list(): List<Customer> {
        policy.actor()
        return customers.allOrdered()
    }

    @Transactional
    fun get(customerId: Long): Customer =
        customers.findById(customerId) ?: throw EntityNotFoundException("No customer $customerId")

    /** The partnerships run for one customer, and the ones run for nobody yet. */
    @Transactional
    fun partnershipsFor(customerId: Long): List<Partnership> = partnerships.forCustomer(customerId)

    @Transactional
    fun unattachedPartnerships(): List<Partnership> =
        partnerships.allOrdered().filter { it.customer == null }

    @Transactional
    fun create(
        name: String,
        shortName: String?,
        contactName: String?,
        contactEmail: String?,
        contactPhone: String?,
        notes: String?,
    ): Customer {
        policy.require(Role.COMPLIANCE_LEAD, Role.SYSTEM_ADMINISTRATOR)
        policy.assertNotReadOnlyActor()

        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "A customer needs a name" }
        customers.byName(cleanName)?.let {
            throw IllegalArgumentException("${it.name} is already a customer")
        }

        val customer = Customer().apply {
            this.name = cleanName
            this.shortName = shortName?.trim()?.ifEmpty { null }
            this.contactName = contactName?.trim()?.ifEmpty { null }
            this.contactEmail = contactEmail?.trim()?.ifEmpty { null }
            this.contactPhone = contactPhone?.trim()?.ifEmpty { null }
            this.notes = notes?.trim()?.ifEmpty { null }
            status = "active"
            stampCreated(policy.actor().label)
        }
        customers.persist(customer)
        customers.flush()

        audit.record(
            entityType = "Customer",
            event = "customer.created",
            entityId = customer.id,
            businessKey = customer.name,
            after = snapshot(customer),
        )
        return customer
    }

    @Transactional
    fun update(
        customerId: Long,
        name: String,
        shortName: String?,
        contactName: String?,
        contactEmail: String?,
        contactPhone: String?,
        notes: String?,
        status: String,
    ): Customer {
        policy.require(Role.COMPLIANCE_LEAD, Role.SYSTEM_ADMINISTRATOR)
        policy.assertNotReadOnlyActor()
        require(status in STATUSES) { "Status must be one of ${STATUSES.joinToString()}" }

        val customer = get(customerId)
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "A customer needs a name" }
        customers.byName(cleanName)?.takeIf { it.requiredId != customerId }?.let {
            throw IllegalArgumentException("${it.name} is already a customer")
        }

        val before = snapshot(customer)
        customer.name = cleanName
        customer.shortName = shortName?.trim()?.ifEmpty { null }
        customer.contactName = contactName?.trim()?.ifEmpty { null }
        customer.contactEmail = contactEmail?.trim()?.ifEmpty { null }
        customer.contactPhone = contactPhone?.trim()?.ifEmpty { null }
        customer.notes = notes?.trim()?.ifEmpty { null }
        customer.status = status
        customer.stampUpdated(policy.actor().label)

        audit.record(
            entityType = "Customer",
            event = "customer.updated",
            entityId = customer.id,
            businessKey = customer.name,
            before = before,
            after = snapshot(customer),
        )
        return customer
    }

    /**
     * Removes a customer. Refused while any partnership is attached: detaching is an explicit act
     * on the company screen, and a delete that silently orphaned operations would hide the very
     * thing the customer row exists to say. Audited with the row's last state.
     */
    @Transactional
    fun delete(customerId: Long) {
        policy.require(Role.COMPLIANCE_LEAD, Role.SYSTEM_ADMINISTRATOR)
        policy.assertNotReadOnlyActor()

        val customer = get(customerId)
        val attached = partnerships.forCustomer(customerId)
        require(attached.isEmpty()) {
            "${customer.name} still has ${attached.size} ${if (attached.size == 1) "operation" else "operations"} " +
                "attached (${attached.joinToString { it.abbrev }}) — detach them first"
        }
        val before = snapshot(customer)
        customers.delete(customer)

        audit.record(
            entityType = "Customer",
            event = "customer.deleted",
            entityId = customerId,
            businessKey = customer.name,
            before = before,
        )
    }

    /**
     * Attaches a partnership to a customer, or (with [customerId] null) detaches it. An explicit
     * act, audited on the partnership: it changes what "the MinRes crew" means.
     */
    @Transactional
    fun attachPartnership(partnershipId: Long, customerId: Long?): Partnership {
        policy.require(Role.COMPLIANCE_LEAD, Role.SYSTEM_ADMINISTRATOR)
        policy.assertNotReadOnlyActor()

        val partnership = partnerships.findById(partnershipId)
            ?: throw EntityNotFoundException("No partnership $partnershipId")
        val customer = customerId?.let { get(it) }
        val before = partnership.customer?.name
        partnership.customer = customer
        partnership.stampUpdated(policy.actor().label)

        audit.record(
            entityType = "Partnership",
            event = if (customer == null) "partnership.detached" else "partnership.attached",
            entityId = partnership.id,
            businessKey = partnership.abbrev,
            before = mapOf("customer" to before),
            after = mapOf("customer" to customer?.name),
        )
        return partnership
    }

    private fun snapshot(customer: Customer): Map<String, Any?> = mapOf(
        "name" to customer.name,
        "shortName" to customer.shortName,
        "contactName" to customer.contactName,
        "contactEmail" to customer.contactEmail,
        "contactPhone" to customer.contactPhone,
        "notes" to customer.notes,
        "status" to customer.status,
    )

    companion object {
        val STATUSES = setOf("active", "former")
    }
}
