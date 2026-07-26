package au.crewcomp.platform.security.oidc

import au.crewcomp.people.IdentityProviderRepository
import au.crewcomp.people.UserAccount
import au.crewcomp.people.UserAccountKind
import au.crewcomp.people.UserAccountRepository
import au.crewcomp.platform.security.AccessDeniedException
import au.crewcomp.platform.security.Actor
import au.crewcomp.platform.security.ActorKind
import io.quarkus.runtime.configuration.ConfigUtils
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import org.eclipse.microprofile.jwt.JsonWebToken
import org.jboss.logging.Logger

/**
 * SEC-1 / SEC-1a — turns a verified corporate ID token into an [Actor].
 *
 * quarkus-oidc has already validated the token's signature, expiry and audience against the
 * configured tenant. What this class adds is the part SEC-1a insists on and no library does for
 * us: checking the **issuer and tenant/domain against the administrator-managed allow-list on
 * every token**, not just at first login, and rejecting anything from an unvalidated backend
 * *before* any account resolution happens.
 *
 * Identity is keyed on `(issuer, subject)`. Never on email: an email address can be reassigned
 * inside a tenant, and a domain can change hands (ADR 0003).
 */
@ApplicationScoped
class ActorResolver(
    private val identityProviders: IdentityProviderRepository,
    private val userAccounts: UserAccountRepository,
) {
    private val log = Logger.getLogger(ActorResolver::class.java)

    @Transactional
    fun resolve(token: JsonWebToken): Actor {
        val issuer = token.issuer
            ?: throw AccessDeniedException("Token carries no issuer")
        val subject = token.subject
            ?: throw AccessDeniedException("Token carries no subject")

        val tenant = tenantClaim(token, issuer)

        // SEC-1a: the allow-list check happens first, so a token from an unknown backend never
        // reaches account resolution.
        val provider = identityProviders.enabledFor(issuer, tenant)
            ?: throw AccessDeniedException(
                "Identity backend ($issuer, $tenant) is not on the allow-list. Onboarding a " +
                    "corporate backend is an audited administrative act (SEC-1a).",
            )

        val account = userAccounts.byExternalIdentity(issuer, subject)
            ?: throw AccessDeniedException(
                "No account is linked to this corporate identity. Self-registration is closed; " +
                    "crew access is provisioned against a Person record (MOB-6).",
            )

        assertUsable(account, provider.displayName)

        return Actor(
            userAccountId = account.requiredId,
            personId = account.person?.id,
            roles = account.roles,
            kind = ActorKind.HUMAN,
            label = "${account.displayName} <${provider.displayName}>",
            partnershipIds = account.scopedPartnershipIds.toSet(),
        )
    }

    /**
     * The tenant/domain half of the SEC-1a allow-list key:
     *  - Entra: the `tid` claim, double-checked against the per-tenant issuer (ADR 0003 forbids
     *    the `/common` and `/organizations` issuers, so issuer and `tid` must agree).
     *  - Google: the `hd` (hosted domain) claim on the returned ID token — the authorize-request
     *    parameter is a UI hint only and must never be trusted.
     *  - Okta: the org authorization server's issuer *is* the org domain, so it is its own key.
     *
     * A Google token with no `hd` is a personal Gmail account, and personal accounts are not a
     * corporate backend: falling back to the issuer would let every Google user in, so an
     * absent `hd` on a Google issuer is a rejection.
     */
    private fun tenantClaim(token: JsonWebToken, issuer: String): String {
        token.getClaim<String?>("tid")?.let { tid ->
            if (!issuer.contains(tid)) {
                throw AccessDeniedException(
                    "Token `tid` ($tid) does not match its issuer ($issuer) — multi-tenant " +
                        "issuers are not accepted (ADR 0003).",
                )
            }
            return tid
        }
        if (issuer.contains("accounts.google.com")) {
            return token.getClaim<String?>("hd")
                ?: throw AccessDeniedException(
                    "Google token carries no `hd` claim: personal accounts are not a corporate " +
                        "identity backend (SEC-1).",
                )
        }
        return issuer
    }

    private fun assertUsable(account: UserAccount, providerName: String) {
        if (!account.isActive) {
            throw AccessDeniedException("Account ${account.displayName} is ${account.status}")
        }
        if (account.kind == UserAccountKind.LOCAL_TEST && isProduction()) {
            // SEC-1b: local/test accounts are disabled in production by default. Belt and braces
            // — they should not exist there at all, and their removal is an exit criterion.
            log.errorf(
                "Local/test account %s attempted to authenticate in production",
                account.displayName,
            )
            throw AccessDeniedException("Local/test accounts are disabled in production (SEC-1b)")
        }
        if (account.roles.isEmpty()) {
            throw AccessDeniedException("Account ${account.displayName} ($providerName) holds no roles")
        }
    }

    private fun isProduction(): Boolean = ConfigUtils.getProfiles().contains("prod")
}
