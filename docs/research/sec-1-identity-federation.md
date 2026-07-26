# SEC-1/1a/2 Authentication & federation architecture — research report

**Research date:** 26 July 2026 (live web verification). Context: ~60 users, admin SPA + iOS/Android, SSO-only (no app passwords ever), allow-listed corporate IdPs (Entra tenants, Google Workspace domains, Okta orgs), issuer+tenant verified on every token, one-person ops, AU residency preferred.

---

## Executive summary

- At 60 MAU, per-user pricing is noise everywhere. The real cost driver is **per-enterprise-connection gating** (Auth0 ~$150/mo floor; WorkOS $125/connection/mo), and the real differentiators are **who enforces the tenant/domain allow-list** (config vs your code), **per-device revocation** (SEC-12), and **AU residency**.
- **No broker enforces Google Workspace `hd` restriction purely by configuration.** Every candidate ends up with enforcement code somewhere (Auth0 Action, Cognito/GCIP Lambda/blocking function, Zitadel Action, or your app). This materially weakens the "buy a broker so you never write claim-validation code" argument.
- **Hard failures**: GCIP (no AU residency, non-expiring non-rotating refresh tokens, per-user-only revocation), WorkOS & Stytch (US-only residency), Entra External ID (no per-device revocation, no Google hd enforcement, custom OIDC IdP preview-flagged), FusionAuth (no real "disable passwords" switch; SSO webhook-enforcement bug #2806).
- **Recommendation**: **(1) Auth0 AU-region tenant** — the only broker where nearly everything is config, with mature per-device revocation; **(2) Direct multi-issuer OIDC federation behind a BFF** — unusually viable here because the spec *already requires* the app to verify issuer+tenant on every token, the provider set is only three types, and it gives the strongest residency/lock-in story. Cognito (near-free, Sydney) and Zitadel Cloud (AU on free tier, open-source escape hatch) are credible runners-up with caveats.

---

## 1. Managed broker comparison (mid-2026)

| | Multi-Entra-tenant | Google `hd` enforcement | Okta inbound OIDC | Allow-list enforced by | SSO-only mode | PKCE + rotation | ~30-day offline | Per-device revoke (SEC-12) | AU residency | Cost @ 60 MAU, ~4–6 connections |
|---|---|---|---|---|---|---|---|---|---|---|
| **Auth0 (Okta CIC)** | Yes — one connection per tenant | Config + recommended post-login Action | Yes, native (free/unlimited since Feb 2026) | Broker (config + Action) | Yes (no DB connection) | Yes / yes (reuse detection) | Yes (idle+absolute config) | **Yes** — device-credentials + Session Mgmt API | **Yes, Sydney, incl. free plan** (fixed at creation) | **~$150–250/mo** (B2B Essentials + $100/extra non-Okta conn) |
| **AWS Cognito** | Yes — one OIDC IdP per tenant (issuer-pinned) | **No — your Lambda** (pre-sign-up / inbound-federation trigger, Jan 2026) | Yes (generic OIDC) | Broker config + **your Lambdas** | Yes (exclude COGNITO provider) | Yes / yes (Essentials, Apr 2025) | Yes (default is 30 d) | **Yes** — `RevokeToken` per refresh token | **Yes, ap-southeast-2** | **~$0.15/mo** |
| **Entra External ID** | Yes — documented first-class | **No native option** | Yes, but custom OIDC IdP **preview-flagged** | Broker (issuer pinning) except Google | Mostly | Yes / RTs replace but old RT not revoked | Yes (90 d native RT) | **No — all-sessions only** | Paid **Go-Local** add-on | ~$0 base + Go-Local fee |
| **Google Identity Platform** | Yes | Blocking function (your code) | Yes | Broker hooks = **your code** | Yes | **RTs never expire, never rotate** | Indefinite (itself the problem) | **No — per-user only** | **No AU residency at all** | ~$0.15–0.30/mo |
| **Zitadel Cloud** | Yes — Entra template has Tenant ID field | Action JS deny-login | Yes (generic OIDC) | Broker (config + Action) | **Yes — real toggle** | Yes / yes | Yes (config) | Yes — RFC 7009 + revoke-one/all (single-device path needs PoC) | **Yes, incl. Free tier** | $0 (Free, 100 DAU) — but **only 3 IdP connections included**; >3 unpriced |
| **FusionAuth Cloud** | Yes | Effectively no — bug #2806 | Yes | Broker config | **No first-class switch** | Yes / yes | Yes | **Yes — strong** per-token API | Yes (paid; Starter has no backups) | ~$162/mo |
| **WorkOS** | Yes | Not broker-enforced in SSO-API mode | Yes | Your app (SSO-API mode) | Yes | AuthKit yes; **no first-party mobile SDKs** | Yes | Sessions yours (SSO-API) | **No — US only** | **$125/connection/mo** → $500+/mo |
| **Stytch B2B (Twilio)** | Via per-org SSO connections | Email-domain JIT, not `hd`/`tid` pinning | Yes | Broker-enforced | Yes | Yes; first-party mobile SDKs | Yes | **Yes** | **No — US** | **$0** (10k MAU + 5 SSO connections free) |

### Notable per-product findings

- **Auth0**: Feb 2026 plan changes made **Okta Workforce connections free/unlimited on every tier**. Realistic floor with ≥2 Entra tenants + ≥1 Workspace domain: **B2B Essentials ~$150/mo** (3 connections, +$100/mo each extra). Identifier-first Home Realm Discovery is the most polished of any candidate. Even Auth0's own docs recommend a post-login Action verifying the Workspace domain — belt-and-braces `hd` enforcement is broker-side *code* everywhere. Pricing has been volatile (2023 and Feb 2026 repricings).
- **Cognito**: 50 federated MAU free → ~$0.15/mo here. Refresh rotation GA Apr 2025; default RT lifetime literally 30 days; clean per-device `RevokeToken`. But `hd`/`tid` allow-listing is **security-critical Lambda code you own**, and email-domain HRD covers SAML only — for OIDC IdPs you build the identifier-first screen yourself. Managed Login browser cookies (1 h) survive token revocation — pair with `/logout`.
- **Entra External ID**: B2C closed to new customers (May 2025); this is the successor. Disqualifiers: no Google `hd` enforcement, no per-device revocation, custom OIDC IdP preview-flagged, SPA refresh tokens capped 24 h, possible double-MFA, 7-day log retention. AU residency needs paid Go-Local.
- **GCIP**: blocking functions are the cleanest single choke-point for allow-lists, but refresh tokens never expire/rotate, revocation is per-user only, no AU residency — three independent spec violations; **eliminate**.
- **Zitadel Cloud**: best capability-per-dollar surprise — AU region on the free tier, genuine "external IdPs only" toggle, Entra provider with native Tenant ID field, rotating RTs, RFC 7009. Caveats: **3 IdP connections on Free/Pro** with no published add-on price; Actions/Login V1→V2 migration churn; two token-endpoint CVEs (CVE-2026-55672/56668) disclosed Jun 2026, patched quickly. AGPL open source = real self-host escape hatch.
- **FusionAuth**: excellent per-device token APIs, but no first-class way to disable passwords, and the webhook path for rejecting logins doesn't stop SSO-session logins (open bug #2806) — direct conflict with SEC-1a. Cloud Starter runs on hosting with **no backups**.
- **WorkOS**: exact "app keeps its own session" architecture, but $125/connection/mo from connection #1 (~$500/mo at 4 IdPs), US-only, no first-party mobile SDKs.
- **Stytch B2B**: $0 at this scale, strongest first-party mobile SDKs, broker-enforced org allow-listing — but US residency only, heavier lock-in, and Twilio acquisition (Nov 2025) roadmap/pricing uncertainty.

---

## 2. Library / direct multi-issuer federation — honest assessment

**Architecture**: app backend is a single confidential OIDC client with a **dynamic issuer registry** (config table: provider type, issuer URL, tenant/domain constraint, client credentials). Standard libraries (coreos/go-oidc v3, Nimbus SDK / pac4j on JVM) each verify one issuer; you instantiate **one verifier per allow-listed backend** — which is exactly the allow-list, so "supported providers are configuration, not code" falls out naturally.

**Genuinely straightforward** (libraries do it): per-issuer discovery, JWKS fetch/caching/rotation, signature + `iss`/`aud`/`exp`/`nonce` validation; auth-code + PKCE per RFC 9700. Tenant validation is trivial when you never use multi-tenant endpoints: register each Entra client tenant as its own issuer `https://login.microsoftonline.com/{tenant-id}/v2.0` — issuer equality does the pinning. Storing `(provider, issuer, sub)` as the UserAccount linkage matches the spec exactly.

**Genuinely risky / the real work**:
- **Mobile session & device management** — the broker value you'd be rebuilding: app-issued opaque device tokens with one-time-use rotation, reuse detection (revoke family on replay), 30-day idle/absolute caps, per-device revocation list. At 60 users this is one `device_sessions` table and a few hundred lines, but it is security-critical code with no vendor to blame. This — not JWT verification — is the honest cost of the library path.
- **Login UX**: identifier-first email-domain → issuer routing screen, error states, IdP outage handling.
- **Per-provider quirks** (verified):
  - **Entra multi-tenant**: `/common`/`/organizations` discovery has a templated issuer that breaks naive libraries (go-oidc issue #344). Avoid the whole bug class by registering per-tenant issuers only — which the allow-list forces anyway. Check `tid` claim as second factor.
  - **Google `hd`**: only present for Workspace accounts; enforcement must be on the **returned ID token's `hd` claim** (the authorize parameter is only a UI hint); key identity on `sub`, never email; note the Truffle Security defunct-domain finding — residual risk for any Google-federated system (brokers included), mitigated by admin-managed user allow-listing on first login.
  - **Okta**: use the **org authorization server** (`https://{org}.okta.com`) — its issuer is the org domain, exactly your allow-list key; custom authorization servers need an API Access Management license, unnecessary here.
- **Every-token verification**: SEC-1a is *natively* satisfied here. With a broker, the app's tokens are broker-issued; satisfying SEC-1a requires the broker to propagate upstream `iss`/`tid`/`hd` as custom claims and the app to re-verify — the broker path *also* ends up with claim-verification code in two places.

**Verdict**: for exactly three provider types and ~60 users, direct federation is a bounded, auditable build (~1–3 weeks including the device-session layer), and the only option with zero identity-vendor residency/lock-in exposure. Its risk is concentrated in the mobile token-lifecycle code you must get right alone.

---

## 3. SSO-as-API vs full broker vs direct federation

- **SSO-as-API (WorkOS)**: architecturally the cleanest match to "app keeps its own session and roles" — but dies on economics and residency, and does *less* than needed on mobile (you'd pay $500+/mo *and* write the device-session code).
- **Full OIDC broker**: broker owns login UX, HRD, federation, token/session lifecycle; app becomes resource server + role store. Mature rotation/revocation for free, but a second identity store (residency, export, lock-in) and claim-propagation code to satisfy SEC-1a. Best when the mobile token lifecycle is what you most want to buy — defensible for a one-person security-critical shop.
- **Direct federation**: the app *is* the broker for its three IdP types. Strongest match to the spec's data model and every-token rule; strongest residency story; most self-owned security code.

Net: the real decision is **full broker vs direct federation**.

---

## 4. Session architecture recommendation

**Admin SPA — cookie session via BFF, not tokens in the browser.** Per RFC 9700 and the IETF browser-based-apps BCP (draft-26): backend is the confidential OAuth client, holds tokens server-side, issues the SPA an opaque `HttpOnly; Secure; SameSite` cookie. XSS can't exfiltrate what isn't there; "sign out this user" is a server-side session delete. Works identically with broker or direct IdPs.

**Mobile — auth-code + PKCE via system browser (AppAuth), then a rotating per-device credential with a 30-day cap.** One-time-use rotating refresh tokens with reuse detection → revoke the token family; idle timeout ≈ 30 days (the offline window) + absolute lifetime (e.g. 90 days) forcing re-auth through the corporate IdP — which is when disabled corporate accounts and Conditional Access re-assert. Credential in Keychain/Keystore. Each refresh token = one device record → **per-device remote sign-out is "revoke that token"**.

Subtlety: the *app backend's* session enforces the 30-day offline window for API access (mobile talks to your API, not the broker, when syncing) — don't let a broker's longer default silently extend the spec's window.

---

## 5. Ranked recommendation

### 1. Auth0 (Okta CIC), AU-region tenant, B2B Essentials (~US$150/mo + $100/mo per non-Okta connection past 3)
The only candidate where essentially every SEC-1/1a/2 requirement is configuration: dedicated Entra/Workspace/Okta connection types (one per client backend = the allow-list), polished identifier-first HRD, SSO-only by having no database connection, mature PKCE + rotating refresh tokens with reuse detection, real per-device revocation APIs, Sydney region on any plan (fixed at tenant creation). Risks: pricing volatility, $100/connection growth curve, small post-login Action for Google `hd`, Actions/Universal Login lock-in (mitigated: users are just federated linkages — re-creatable anywhere).

### 2. Direct multi-issuer OIDC federation (library) behind a BFF — app-owned sessions
go-oidc / Nimbus with one verifier per allow-listed issuer; per-tenant Entra issuers (never `/common`), `hd`-claim + `sub`-keyed identity for Google, Okta org authorization servers; HttpOnly-cookie BFF for the SPA; app-issued rotating device tokens for mobile. Strongest fit to SEC-1a, the UserAccount model, and AU residency; zero lock-in; $0/mo. Honest cost: you own the mobile token-lifecycle security code alone — bounded (~1–3 weeks) but unshared risk.

**Runners-up**: **Cognito** (Sydney, ~$0.15/mo) if you accept `hd`/`tid` Lambdas + DIY OIDC HRD — library-path security code inside someone else's opinionated service. **Zitadel Cloud** very attractive **if** >3 IdP connections price sanely and the Actions-V2 deny-login path proves solid; June 2026 CVEs + V1/V2 churn keep it just behind. **Eliminated**: GCIP, WorkOS, Stytch (residency+), Entra External ID (SEC-12 impossible), FusionAuth (no SSO-only switch; bug #2806).

### Integration spike (1–2 weeks, both finalists side by side)
1. **Auth0**: AU tenant; one real Entra tenant + one Workspace domain + one Okta org; confirm connection count/pricing against the five partnerships; post-login Action rejecting wrong `hd`/`tid` and injecting upstream claims for every-token re-verification; identifier-first HRD; AppAuth on iOS+Android with rotation, 30-day idle lifetime, per-device revocation from your admin UI.
2. **Library path**: per-tenant Entra issuer verification, Google `hd` + `email_verified` + `sub`-keyed linkage, Okta org-server validation, JWKS rotation behaviour, full device-session prototype (rotation, reuse-detection family revocation, 30-day cap, admin per-device sign-out).
3. Either: end-to-end mobile login UX for crew (corporate-IdP MFA via system browser on poor connectivity), and the offline-30-day → forced re-auth experience.

### AU-residency gotchas
- **Auth0**: region fixed at tenant creation — create in AU from day one.
- **Cognito**: pools region-locked (ap-southeast-2); Melbourne unverified.
- **GCIP**: no residency control — disqualifying. **WorkOS/Stytch**: US-only.
- **Entra External ID**: AU needs paid Go-Local (rate unverified).
- **Zitadel**: AU even on Free. **FusionAuth**: AU only on paid Cloud (Starter has no backups).
- **Direct federation**: residency is wherever CREWCOMP runs — no third-party identity store; ID-token round-trips still transit the IdPs' global endpoints (true under every option).

**Uncertainty flags** (re-verify with vendors before commitment): Auth0 B2B Professional exact tiers; Zitadel >3-connection pricing; Entra External ID custom-OIDC GA status and Go-Local rate; Cognito Managed Login domain-search for OIDC and Melbourne region; WorkOS residency (no primary statement); Stytch legacy tiers.

Key sources (all retrieved 2026-07-26): auth0.com/pricing · auth0.com/blog/auth0-b2b-plans-upgraded · aws.amazon.com/cognito/pricing · Cognito inbound-federation trigger (Jan 2026) · learn.microsoft.com/entra/external-id (federation, pricing, refresh tokens, claims-validation) · GCIP blocking functions / manage-sessions / locations · zitadel.com/pricing + Entra provider + Actions + causalsecurity.com CVE writeup · fusionauth.io JWT APIs + bug #2806 · workos.com/pricing · stytch.com/pricing + Twilio acquisition · RFC 9700 · draft-ietf-oauth-browser-based-apps-26 · Google verify-ID-token/hd docs · Truffle Security Google OAuth flaw · Okta auth-servers concepts · go-oidc issue #344.
