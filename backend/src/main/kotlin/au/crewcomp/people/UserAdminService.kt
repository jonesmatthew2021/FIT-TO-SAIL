package au.crewcomp.people

import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.Role
import io.quarkus.runtime.configuration.ConfigUtils
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.Instant

/**
 * ADM-10 — users, roles and the SEC-1a identity allow-list.
 *
 * ### What this can and cannot do, and why
 *
 * ADR 0003 provisions corporate accounts by **admin allow-listing on first login**: the `(issuer,
 * subject)` pair that identifies a person is not knowable until they present a token, so nothing here
 * creates a corporate account. What it administers is everything either side of that moment — which
 * identity backends may authenticate anyone at all (the `identity_provider` allow-list *is* the
 * SEC-1a list), and what an account may do once it exists.
 *
 * [createTransitionalAccount] is the one exception, and it is deliberately narrow: SEC-1b permits
 * `local_test` accounts as "a flagged, enumerable transitional exception with a hard removal exit
 * criterion at P2". It refuses to run in production, where `ActorResolver` would refuse to
 * authenticate what it created anyway. Its purpose is to give a back-office user an account before
 * the identity spike exists, which is what §9's per-role notification fan-out needs a recipient for.
 *
 * ### AUTH-4 — role assignments are audited, and they carry who granted them
 *
 * Every grant and revocation writes an audit event, and the grant metadata is on the row itself
 * (`RoleAssignment.grantedBy`). Access changes are the events an external auditor looks for first
 * (§17.4), so they are named individually — `user.role_granted`, not a generic `user.updated`.
 */
@ApplicationScoped
class UserAdminService(
    private val accounts: UserAccountRepository,
    private val people: PersonRepository,
    private val identityProviders: IdentityProviderRepository,
    private val policy: AccessPolicy,
    private val audit: AuditWriter,
) {

    // -----------------------------------------------------------------------
    // Accounts
    // -----------------------------------------------------------------------

    /**
     * Every account, with its roles, scopes and person linkage.
     *
     * Readable by the System Administrator and the Compliance Lead: "who can approve an exemption"
     * is a governance question, not only an IT one. Nobody else — a list of who holds which role is
     * a map of the system's authority.
     */
    @Transactional
    fun listAccounts(): List<UserAccount> {
        policy.require(Role.SYSTEM_ADMINISTRATOR, Role.COMPLIANCE_LEAD)
        return accounts.allOrdered()
    }

    /** SEC-1b: the transitional accounts, enumerable so their removal can be verified. */
    @Transactional
    fun listTransitionalAccounts(): List<UserAccount> {
        policy.require(Role.SYSTEM_ADMINISTRATOR, Role.COMPLIANCE_LEAD)
        return accounts.localTestAccounts()
    }

    @Transactional
    fun grantRole(userAccountId: Long, role: Role): UserAccount {
        policy.require(Role.SYSTEM_ADMINISTRATOR)

        val account = accountById(userAccountId)
        require(role != Role.CREW_MEMBER || account.person != null) {
            "Crew Member is a role about a person's own data, so it needs a linked Person record"
        }
        if (account.roles.contains(role)) return account

        val actor = policy.actor()
        account.grantRole(role, actor.label)
        account.stampUpdated(actor.label)

        audit.record(
            entityType = "UserAccount",
            event = "user.role_granted",
            entityId = account.id,
            businessKey = account.displayName,
            after = mapOf("role" to role.wire, "grantedBy" to actor.label),
        )
        return account
    }

    /**
     * Revokes a role, refusing to remove the last System Administrator.
     *
     * Not paternalism about a mistake — it is the one mistake that cannot be undone from inside the
     * application. With no administrator left, nothing can grant the role back, and the remedy is a
     * hand-written `INSERT` against production by whoever has database credentials. That is a worse
     * position than a refused request.
     */
    @Transactional
    fun revokeRole(userAccountId: Long, role: Role): UserAccount {
        policy.require(Role.SYSTEM_ADMINISTRATOR)

        val account = accountById(userAccountId)
        if (!account.roles.contains(role)) return account

        if (role == Role.SYSTEM_ADMINISTRATOR) {
            val remaining = accounts.holdingAnyRole(listOf(Role.SYSTEM_ADMINISTRATOR))
                .count { it.requiredId != userAccountId }
            require(remaining > 0) {
                "This is the last active System Administrator. Grant the role to someone else " +
                    "first — with none, nothing inside the application can grant it back."
            }
        }

        account.roleAssignments.removeIf { it.role == role.wire }
        account.stampUpdated(policy.actor().label)

        audit.record(
            entityType = "UserAccount",
            event = "user.role_revoked",
            entityId = account.id,
            businessKey = account.displayName,
            before = mapOf("role" to role.wire),
        )
        return account
    }

    /**
     * Suspends or reactivates an account.
     *
     * Suspension rather than deletion, always. An account is the subject of audit events and the
     * recipient of notifications; deleting one would either orphan or rewrite history, and §4.4's
     * self-containment rule exists precisely so that never has to happen.
     */
    @Transactional
    fun setStatus(userAccountId: Long, status: String): UserAccount {
        policy.require(Role.SYSTEM_ADMINISTRATOR)
        require(status in ACCOUNT_STATUSES) {
            "An account is ${ACCOUNT_STATUSES.joinToString(", ")} — not '$status'"
        }

        val account = accountById(userAccountId)
        if (status != "active" && account.roles.contains(Role.SYSTEM_ADMINISTRATOR)) {
            val remaining = accounts.holdingAnyRole(listOf(Role.SYSTEM_ADMINISTRATOR))
                .count { it.requiredId != userAccountId }
            require(remaining > 0) { "This is the last active System Administrator and cannot be $status" }
        }

        val before = account.status
        if (before == status) return account
        account.status = status
        account.stampUpdated(policy.actor().label)

        audit.record(
            entityType = "UserAccount",
            event = if (status == "active") "user.reactivated" else "user.suspended",
            entityId = account.id,
            businessKey = account.displayName,
            before = mapOf("status" to before),
            after = mapOf("status" to status),
        )
        return account
    }

    /**
     * Sets the partnerships a Vessel Master is scoped to (§3).
     *
     * An empty set is permitted and means "no partnerships", which is what `AccessPolicy` already
     * treats as an empty read scope — a Vessel Master with no scope sees nothing rather than
     * everything. That default is the safe direction and this does not soften it.
     */
    @Transactional
    fun setPartnershipScopes(userAccountId: Long, partnershipIds: Set<Long>): UserAccount {
        policy.require(Role.SYSTEM_ADMINISTRATOR)

        val account = accountById(userAccountId)
        val before = account.scopedPartnershipIds.toSet()
        if (before == partnershipIds) return account

        account.scopedPartnershipIds.clear()
        account.scopedPartnershipIds.addAll(partnershipIds)
        account.stampUpdated(policy.actor().label)

        audit.record(
            entityType = "UserAccount",
            event = "user.scope_changed",
            entityId = account.id,
            businessKey = account.displayName,
            before = mapOf("partnershipIds" to before.sorted()),
            after = mapOf("partnershipIds" to partnershipIds.sorted()),
        )
        return account
    }

    /**
     * Creates a SEC-1b transitional account so a back-office user has an identity before the
     * identity spike exists.
     *
     * **Refused in production.** `ActorResolver` refuses to authenticate a `local_test` account under
     * `prod` (SEC-1b), so creating one there would produce an account nobody can sign in to — and a
     * quiet way to accumulate exactly the rows whose removal is a P2 exit criterion. The honest
     * statement is that a production back-office user gets their account at first corporate login,
     * which is what ADR 0003 says.
     */
    @Transactional
    fun createTransitionalAccount(
        displayName: String,
        email: String?,
        roles: Set<Role>,
        personId: Long? = null,
    ): UserAccount {
        policy.require(Role.SYSTEM_ADMINISTRATOR)
        // AccessDeniedException, not IllegalState: this is a refusal of authority ("nobody may do
        // this here"), which is what a 403 says, and it keeps the message — which explains the way
        // forward — in front of the caller.
        if (isProduction()) {
            throw au.crewcomp.platform.security.AccessDeniedException(
                "Transitional (local_test) accounts are refused in production (SEC-1b). A " +
                    "back-office user's account is created when they first sign in with their " +
                    "corporate identity (ADR 0003) — onboard their identity backend on the " +
                    "allow-list instead.",
            )
        }

        val cleanName = displayName.trim()
        require(cleanName.isNotEmpty()) { "An account needs a display name" }
        require(roles.isNotEmpty()) { "An account with no roles can do nothing; grant at least one" }

        val person = personId?.let {
            require(accounts.forPerson(it) == null) { "Person $it already has an account" }
            people.findById(it) ?: throw EntityNotFoundException("No person $it")
        }
        require(roles.none { it == Role.CREW_MEMBER } || person != null) {
            "Crew Member is a role about a person's own data, so it needs a linked Person record"
        }

        val actor = policy.actor()
        val now = Instant.now()
        val account = UserAccount().apply {
            this.person = person
            this.kind = UserAccountKind.LOCAL_TEST
            this.displayName = cleanName
            this.email = email?.ifBlank { null }
            this.status = "active"
            // `this.grantRole` is the entity's three-argument method; this service has a
            // `grantRole(Long, Role)` of its own, and a bare name inside `apply` is one resolution
            // rule away from meaning the other one.
            roles.forEach { this.grantRole(it, actor.label, now) }
            stampCreated(actor.label, now)
        }
        accounts.persist(account)

        audit.record(
            entityType = "UserAccount",
            event = "user.transitional_account_created",
            entityId = account.id,
            businessKey = cleanName,
            after = mapOf(
                "kind" to UserAccountKind.LOCAL_TEST.wire,
                "roles" to roles.map { it.wire }.sorted(),
                "personId" to personId,
                // Recorded in the event itself, so an auditor reading the trail sees the exception
                // being taken rather than having to know SEC-1b to notice it.
                "sec1bException" to "transitional account, removal is a P2 exit criterion",
            ),
        )
        return account
    }

    // -----------------------------------------------------------------------
    // SEC-1a — the identity allow-list
    // -----------------------------------------------------------------------

    /**
     * The corporate identity backends. This table **is** the SEC-1a allow-list — `ActorResolver`
     * checks it on every token, not just at first login — so onboarding one is the most
     * consequential administrative act in this service.
     */
    @Transactional
    fun listIdentityProviders(): List<IdentityProvider> {
        policy.require(Role.SYSTEM_ADMINISTRATOR, Role.COMPLIANCE_LEAD)
        return identityProviders.listAll(io.quarkus.panache.common.Sort.by("displayName"))
    }

    /**
     * Onboards an identity backend, **disabled**.
     *
     * Two-step by design: creating and enabling are separate acts with separate audit events, so
     * "we added Acme's tenant" and "we let Acme's tenant authenticate" are distinguishable in the
     * trail. Getting an issuer wrong is how a whole tenant becomes able to sign in, and the
     * validation below encodes ADR 0003's specific traps rather than trusting the typist.
     */
    @Transactional
    fun createIdentityProvider(
        provider: String,
        issuer: String,
        tenantOrDomain: String,
        displayName: String,
    ): IdentityProvider {
        policy.require(Role.SYSTEM_ADMINISTRATOR)

        val cleanIssuer = issuer.trim().trimEnd('/')
        val cleanTenant = tenantOrDomain.trim()
        val cleanProvider = provider.trim().lowercase()
        require(cleanProvider in PROVIDERS) {
            "'$cleanProvider' is not a supported provider; the supported set is ${PROVIDERS.joinToString()}"
        }
        require(cleanIssuer.startsWith("https://")) {
            "An issuer must be an https URL — an http issuer is not a corporate identity backend"
        }
        // ADR 0003: Entra per-tenant issuers only. The multi-tenant endpoints will happily issue
        // tokens for any tenant in the world, so allow-listing one is allow-listing everyone.
        require(!MULTI_TENANT_ISSUERS.any { cleanIssuer.contains(it) }) {
            "'$cleanIssuer' is a multi-tenant issuer. Entra must be onboarded per tenant " +
                "(https://login.microsoftonline.com/{tenant-id}/v2.0) — never /common or " +
                "/organizations (ADR 0003)."
        }
        require(cleanTenant.isNotEmpty()) {
            "The tenant or domain is half the allow-list key (SEC-1a) and cannot be blank"
        }
        // `byKey`, not `enabledFor`: the table's unique key ignores `enabled`, so checking only the
        // enabled rows would let a duplicate through whenever the existing row was disabled.
        require(identityProviders.byKey(cleanIssuer, cleanTenant) == null) {
            "($cleanIssuer, $cleanTenant) is already on the allow-list"
        }

        val actor = policy.actor()
        val entry = IdentityProvider().apply {
            this.provider = cleanProvider
            this.issuer = cleanIssuer
            this.tenantOrDomain = cleanTenant
            this.displayName = displayName.trim().ifEmpty { cleanTenant }
            // Created disabled, always. Enabling is the second act.
            this.enabled = false
            stampCreated(actor.label)
        }
        identityProviders.persist(entry)

        audit.record(
            entityType = "IdentityProvider",
            event = "identity_provider.created",
            entityId = entry.id,
            businessKey = "$cleanIssuer|$cleanTenant",
            after = mapOf(
                "provider" to cleanProvider,
                "issuer" to cleanIssuer,
                "tenantOrDomain" to cleanTenant,
                "enabled" to false,
            ),
        )
        return entry
    }

    @Transactional
    fun setIdentityProviderEnabled(identityProviderId: Long, enabled: Boolean): IdentityProvider {
        policy.require(Role.SYSTEM_ADMINISTRATOR)

        val entry = identityProviders.findById(identityProviderId)
            ?: throw EntityNotFoundException("No identity provider $identityProviderId")
        if (entry.enabled == enabled) return entry

        entry.enabled = enabled
        entry.stampUpdated(policy.actor().label)

        audit.record(
            entityType = "IdentityProvider",
            // Named separately: "an identity backend was allowed to authenticate people" is the
            // single event an auditor most wants to find, and it must not hide inside an update.
            event = if (enabled) "identity_provider.enabled" else "identity_provider.disabled",
            entityId = entry.id,
            businessKey = "${entry.issuer}|${entry.tenantOrDomain}",
            before = mapOf("enabled" to !enabled),
            after = mapOf("enabled" to enabled),
        )
        return entry
    }

    private fun accountById(userAccountId: Long): UserAccount =
        accounts.findById(userAccountId)
            ?: throw EntityNotFoundException("No user account $userAccountId")

    private fun isProduction(): Boolean = ConfigUtils.getProfiles().contains("prod")

    companion object {
        /** Mirrors the `user_account_status_check` constraint. */
        val ACCOUNT_STATUSES = listOf("active", "suspended", "disabled")

        /** ADR 0003's confirmed IdP mix. Configuration rather than code, per §4.3's comment. */
        val PROVIDERS = listOf("entra", "google", "okta")

        /** The issuers that would allow-list the entire world (ADR 0003). */
        private val MULTI_TENANT_ISSUERS = listOf(
            "login.microsoftonline.com/common",
            "login.microsoftonline.com/organizations",
            "login.microsoftonline.com/consumers",
        )
    }
}
