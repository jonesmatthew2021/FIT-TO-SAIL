package au.crewcomp.reference

import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.DataScope
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
    private val vessels: VesselRepository,
    private val policy: AccessPolicy,
    private val audit: AuditWriter,
) {

    @Transactional
    fun list(): List<Customer> {
        policy.actor()
        val all = customers.allOrdered()
        // A customer's own staff see their own company and nobody else's (BUS-1).
        if (policy.scope() is DataScope.All) return all
        return all.filter { customer -> partnerships.forCustomer(customer.requiredId).any { policy.canSeePartnership(it.requiredId) } }
    }

    @Transactional
    fun get(customerId: Long): Customer =
        customers.findById(customerId) ?: throw EntityNotFoundException("No customer $customerId")

    /** Whether the actor may see what customers are charged (BUS-1): the office, not a customer's staff. */
    fun canSeeBusiness(): Boolean = policy.actor().hasRole(Role.SYSTEM_ADMINISTRATOR)

    /** The business side (BUS-1): system administrator only, since it is what the customer is charged. */
    @Transactional
    fun updateBusiness(
        customerId: Long,
        billingEmail: String?,
        abn: String?,
        address: String?,
        plan: String?,
        ratePerShipMonth: java.math.BigDecimal?,
        billingNotes: String?,
    ): Customer {
        policy.require(Role.SYSTEM_ADMINISTRATOR)
        val customer = get(customerId)
        require(ratePerShipMonth == null || ratePerShipMonth.signum() >= 0) { "A rate cannot be negative" }
        val before = businessSnapshot(customer)
        customer.billingEmail = billingEmail?.trim()?.ifEmpty { null }
        customer.abn = abn?.trim()?.ifEmpty { null }
        customer.address = address?.trim()?.ifEmpty { null }
        customer.plan = plan?.trim()?.ifEmpty { null }
        customer.ratePerShipMonth = ratePerShipMonth?.setScale(2, java.math.RoundingMode.HALF_UP)
        customer.billingNotes = billingNotes?.trim()?.ifEmpty { null }
        customer.stampUpdated(policy.actor().label)
        audit.record(
            entityType = "Customer",
            event = "customer.business_updated",
            entityId = customer.id,
            businessKey = customer.name,
            before = before,
            after = businessSnapshot(customer),
        )
        return customer
    }

    private fun businessSnapshot(customer: Customer): Map<String, Any?> = mapOf(
        "billingEmail" to customer.billingEmail,
        "abn" to customer.abn,
        "address" to customer.address,
        "plan" to customer.plan,
        "ratePerShipMonth" to customer.ratePerShipMonth?.toPlainString(),
        "billingNotes" to customer.billingNotes,
    )

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

    // ------------------------------------------------------------------ the fleet

    /**
     * Creates an operation (a partnership, §4.1) for a customer. A customer with a large fleet is
     * many operations — each its own roster, swing calendar and vessel pairing — and this is how
     * they are added; the seeders were the only way before.
     */
    @Transactional
    fun createPartnership(customerId: Long, abbrev: String, name: String, vesselClass: String?, registry: String? = null, regime: String? = null): Partnership {
        policy.require(Role.COMPLIANCE_LEAD, Role.SYSTEM_ADMINISTRATOR)
        policy.assertNotReadOnlyActor()

        val customer = get(customerId)
        val cleanAbbrev = abbrev.trim().uppercase()
        val cleanName = name.trim()
        require(Regex("^[A-Z0-9]{2,6}$").matches(cleanAbbrev)) {
            "An operation's code is 2–6 letters or digits (the swing calendar and the register key on it)"
        }
        require(cleanName.isNotEmpty()) { "An operation needs a name" }
        require(registry == null || registry in setOf("australian", "international")) { "A vessel is Australian or internationally registered" }
        require(regime == null || regime in setOf("domestic", "international")) { "The manning regime is domestic (National Law) or international (STCW/SOLAS)" }
        partnerships.byAbbrev(cleanAbbrev)?.let {
            throw IllegalArgumentException("Code $cleanAbbrev is already ${it.name}")
        }

        val partnership = Partnership().apply {
            this.abbrev = cleanAbbrev
            this.name = cleanName
            this.vesselClass = vesselClass?.trim()?.ifEmpty { null }
            this.registry = registry
            this.regime = regime
            this.customer = customer
            stampCreated(policy.actor().label)
        }
        partnerships.persist(partnership)
        partnerships.flush()

        audit.record(
            entityType = "Partnership",
            event = "partnership.created",
            entityId = partnership.id,
            businessKey = partnership.abbrev,
            after = mapOf("name" to cleanName, "vesselClass" to partnership.vesselClass, "customer" to customer.name, "registry" to registry, "regime" to regime),
        )
        return partnership
    }

    /**
     * Removes an operation. Refused while anything hangs off it — crew, swings, assignments,
     * register records, matrix overrides, account scopes: a ship with history is retired, not
     * deleted, and this system has no retirement yet. Its vessels go with it. Audited.
     */
    @Transactional
    fun deletePartnership(partnershipId: Long) {
        policy.require(Role.COMPLIANCE_LEAD, Role.SYSTEM_ADMINISTRATOR)
        policy.assertNotReadOnlyActor()

        val partnership = partnerships.findById(partnershipId)
            ?: throw EntityNotFoundException("No partnership $partnershipId")
        val em = partnerships.getEntityManager()
        val inUse = REFERENCING_TABLES.mapNotNull { (table, what) ->
            val count = (em.createNativeQuery("select count(*) from $table where partnership_id = ?1")
                .setParameter(1, partnershipId)
                .singleResult as Number).toLong()
            if (count > 0) "$count $what" else null
        }
        require(inUse.isEmpty()) {
            "${partnership.name} still has ${inUse.joinToString(", ")} — a ship with history is not removed"
        }

        val ownVessels = vessels.forPartnership(partnershipId)
        val before = mapOf(
            "abbrev" to partnership.abbrev,
            "name" to partnership.name,
            "customer" to partnership.customer?.name,
            "vessels" to ownVessels.map { it.name },
        )
        ownVessels.forEach { vessels.delete(it) }
        partnerships.delete(partnership)

        audit.record(
            entityType = "Partnership",
            event = "partnership.deleted",
            entityId = partnershipId,
            businessKey = partnership.abbrev,
            before = before,
        )
    }

    /** Every vessel, for the company screen's fleet view. Reference data; readable by any actor. */
    @Transactional
    fun listVessels(): List<Vessel> {
        policy.actor()
        return vessels.allOrdered()
    }

    @Transactional
    fun addVessel(partnershipId: Long, name: String, kind: String): Vessel {
        policy.require(Role.COMPLIANCE_LEAD, Role.SYSTEM_ADMINISTRATOR)
        policy.assertNotReadOnlyActor()

        val partnership = partnerships.findById(partnershipId)
            ?: throw EntityNotFoundException("No partnership $partnershipId")
        val cleanName = name.trim()
        val cleanKind = kind.trim().lowercase()
        require(cleanName.isNotEmpty()) { "A vessel needs a name" }
        require(cleanKind.isNotEmpty()) { "A vessel needs a kind (tug, barge, ship…)" }
        vessels.forPartnership(partnershipId).firstOrNull { it.name.equals(cleanName, ignoreCase = true) }?.let {
            throw IllegalArgumentException("${it.name} is already on ${partnership.abbrev}")
        }

        val vessel = Vessel().apply {
            this.name = cleanName
            this.kind = cleanKind
            this.partnership = partnership
            stampCreated(policy.actor().label)
        }
        vessels.persist(vessel)
        vessels.flush()

        audit.record(
            entityType = "Vessel",
            event = "vessel.added",
            entityId = vessel.id,
            businessKey = "${partnership.abbrev}/${vessel.name}",
            after = mapOf("kind" to cleanKind, "partnership" to partnership.abbrev),
        )
        return vessel
    }

    @Transactional
    fun removeVessel(vesselId: Long) {
        policy.require(Role.COMPLIANCE_LEAD, Role.SYSTEM_ADMINISTRATOR)
        policy.assertNotReadOnlyActor()

        val vessel = vessels.findById(vesselId) ?: throw EntityNotFoundException("No vessel $vesselId")
        val key = "${vessel.partnership.abbrev}/${vessel.name}"
        val before = mapOf("name" to vessel.name, "kind" to vessel.kind, "partnership" to vessel.partnership.abbrev)
        vessels.delete(vessel)

        audit.record(entityType = "Vessel", event = "vessel.removed", entityId = vesselId, businessKey = key, before = before)
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

        /** What hangs off a partnership (V1's foreign keys, bar vessel, which goes with it). */
        private val REFERENCING_TABLES = listOf(
            "person" to "crew",
            "crew_change" to "swings",
            "assignment" to "assignments",
            "register_record" to "register records",
            "requirement_rule" to "matrix overrides",
            "user_account_partnership_scope" to "account scopes",
        )
    }
}
