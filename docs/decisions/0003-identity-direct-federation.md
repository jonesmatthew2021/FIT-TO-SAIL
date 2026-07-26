# ADR 0003 — Identity: direct multi-issuer OIDC federation (no broker)

**Status:** Accepted (2026-07-26)
**Spec refs:** SEC-1, SEC-1a, SEC-1b, SEC-2, SEC-12, MOB-6, §4.3 UserAccount
**Research:** `docs/research/sec-1-identity-federation.md`

## Context

Corporate SSO is the sole authentication path; the confirmed IdP mix across the five partnerships is Entra ID, Google Workspace, **and** Okta. SEC-1a requires issuer AND tenant/domain verification on every token against an admin-managed allow-list. The choice was a managed federation broker (Auth0 was the best-scoring at ~$150–250/mo, AU region) vs the application federating directly via standard OIDC libraries.

## Decision

**Direct federation.** The application backend is the confidential OIDC client, with a dynamic issuer registry (one verifier per allow-listed backend — the registry *is* the SEC-1a allow-list), implemented on quarkus-oidc multitenancy (ADR 0001):

- **Entra:** per-tenant issuers only (`https://login.microsoftonline.com/{tenant-id}/v2.0`) — never `/common`/`/organizations`; `tid` claim double-checked.
- **Google:** enforcement on the returned ID token's `hd` claim (the authorize parameter is a UI hint only); identity keyed on `sub`, never email; admin allow-listing of users on first login as defence against the defunct-domain inheritance risk.
- **Okta:** org authorization server (`https://{org}.okta.com`) — its issuer is the org domain, exactly the allow-list key.
- **Admin SPA:** BFF pattern — tokens held server-side, browser gets an opaque `HttpOnly; Secure; SameSite` session cookie (per RFC 9700 / browser-based-apps BCP).
- **Mobile:** auth-code + PKCE via system browser (AppAuth), then app-issued rotating one-time-use device tokens: reuse detection revokes the token family; ~30-day idle cap (the SEC-12 offline window) + absolute lifetime forcing corporate re-auth; one device-session row per device → per-device remote sign-out is a row revocation.

## Rationale

- No broker enforces the Google `hd` restriction purely by configuration — every path involves claim-validation code, so the broker's core value proposition is weaker than assumed.
- SEC-1a's per-token verification is *natively* satisfied by direct federation; a broker requires propagating upstream claims and re-verifying them anyway (code in two places).
- Strongest data-residency story: no third-party identity store at all (SEC-14, O-9).
- $0/mo vs Auth0's $150–250/mo — which would exceed all hosting costs combined — with documented broker pricing volatility.
- The provider set is exactly three types at ~60 users: a bounded, auditable build.

## Consequences

- We own the mobile device-session layer (rotation, family revocation, caps, revocation table) as security-critical code — ~1–2 weeks, specified in the research report; it gets the path-filtered AI security review (DEV-3) and priority test coverage.
- We own the identifier-first login-routing UX (email domain → issuer).
- **Fallback:** Auth0 AU-region tenant remains the documented buy-option if the identity spike shows unexpected friction; users are portable (federated linkages only) in both directions.
- Local/test accounts (SEC-1b) are implemented as a flagged, enumerable UserAccount kind with a hard removal exit criterion at P2.
