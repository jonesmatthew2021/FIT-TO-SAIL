# ADR 0008 — Admin web: minimal React stack, generated types, and a build-time-only development auth shim

**Status:** Accepted (2026-07-26) — the development shim is explicitly temporary and is retired by the identity spike
**Spec refs:** §6, §10.1, §14.4, DEV-2, AUTH-1, AUTH-2, SEC-1, NFR-5, O-11
**Related:** ADR 0003 (identity — direct OIDC federation, BFF session for the SPA)

## Context

§14.4 already settled React + TypeScript for the admin SPA and DEV-2 already required client types
generated from the backend's OpenAPI schema. Starting the build forced three decisions §14.4 did
not make.

**How much of npm to take on.** The §14.3 mobile research rejected React Native partly on npm's
2025–26 supply-chain record (the Shai-Hulud worms), in a project whose framing is "security
paramount". That argument does not stop at the mobile boundary: the admin app's dependency tree is
the same ecosystem, installed by the same one-person team, on the machine that also holds the
production credentials.

**How to authenticate locally.** ADR 0003 puts the OIDC code flow in a backend-for-frontend: tokens
server-side, an opaque `HttpOnly` cookie in the browser. That BFF is the identity spike's
deliverable and needs a real corporate IdP to build against. Meanwhile `quarkus.oidc.enabled=false`
in dev and test, so every `@Authenticated` endpoint answers 401 and the SPA cannot be developed at
all. Something has to stand in, and whatever stands in is by definition a way to authenticate
without a credential.

**Where "today" comes from.** NFR-5 and O-11 make business dates calendar dates in the operating
timezone (AWST). A browser knows neither that timezone nor the admin date override, so any client
that computes "today" locally disagrees with the server's compliance answers for part of each day —
for some viewers, silently, and only sometimes.

## Decision

### 1. A deliberately small dependency set

Vite + React + TypeScript with five runtime dependencies: `react`, `react-dom`,
`react-router-dom`, `@tanstack/react-query`, `@tanstack/react-table`. No component library, no CSS
framework, no ESLint. 190 packages installed in total.

TanStack Table earns its place — the planner, gap report, directory and holdings grid are one
component with different columns, and a headless table is the difference between that and four
bespoke renderings. A design system does not: for nine table-and-chip screens it is a large
transitive surface bought for styling that gets overridden anyway. ESLint is omitted because
`tsc` under `strict` + `noUncheckedIndexedAccess` + `exactOptionalPropertyTypes` catches the class
of defect a lint pass would here; adding one later should be a decision with a reason.

### 2. Generated types, committed, and verified in CI

`quarkus.smallrye-openapi.store-schema-directory=target/openapi` writes the schema at build time.
`admin-web/scripts/generate-api-types.sh` turns it into `src/api/schema.d.ts`, which is committed
so the SPA builds without a JDK, and `npm run verify:api` regenerates and diffs it so CI fails when
the backend's contract has moved and the types have not. Every type in the API client is an alias
of that file; none is written by hand.

### 3. `GET /api/v1/session` as the stable half of the auth contract

One endpoint returns the actor's label, roles, person id, partnership scope, and **the server's
business date**. It says nothing about how the session was established, so it survives the identity
spike unchanged. A 401 from it is the SPA's signal to send the user to the BFF's login route.

Clients take `today` from here and never call `new Date()` for it. `src/domain/dates.ts` does
calendar arithmetic on `YYYY-MM-DD` strings without ever constructing a local `Date`.

### 4. A development auth shim that a production build does not contain

`DevAuthenticationMechanism` authenticates any request naming roles in `X-Dev-Roles`. It is
guarded three ways:

- **Removed at build time.** `@IfBuildProperty(name = "crewcomp.dev-auth.enabled")` is true only
  under `%dev` and `%test`, so the bean is not in a production artefact — JVM or native. It is
  absent, not disabled.
- **Refuses to start under `prod`** if the property is somehow true anyway.
- **Asks for no password.** A shim with a fake credential invites being mistaken for
  authentication; one that plainly trusts a request header cannot be.

The SPA's half sits behind `import.meta.env.DEV`, a compile-time constant, so the role picker and
the header names are stripped by the bundler's dead-code pass. This is verified, not asserted:
`grep -c "X-Dev-Roles" dist/assets/*.js` returns 0.

`DevDataSeeder` follows the same pattern for development data, and additionally refuses to run
against a database that already holds people.

### 5. UI role gating is defence in depth, and nothing else

`useHasRole` hides controls the server would refuse. The server refuses them regardless, and
`ApiIT` asserts it endpoint by endpoint. This is the POC's `App.canEdit()` demoted to a courtesy,
which is exactly what AUTH-1 asks for.

## Consequences

- The SPA is developable and demonstrable today, against a real database, without an IdP.
- The temporary thing is temporary *by construction* rather than by intention. The usual failure
  mode — a dev backdoor behind a runtime flag that survives to production — is not available here,
  because the code is not in the artefact.
- Dropping a component library means writing table, chip, form and layout styling by hand. That is
  ~600 lines of CSS, already written, and it is the part most likely to be replaced if the client
  brings a design language.
- Committing generated types means a backend DTO change is a two-repo-directory commit. The CI
  check makes forgetting it a build failure rather than a runtime `undefined`.
- No ESLint means no automated enforcement of import order, hook rules or accessibility lint. The
  hook-rule risk is the real one; React 19's compiler-adjacent warnings and code review cover it
  for now.

## Alternatives considered

**A mock API for frontend development.** Would have avoided inventing an auth path, but it puts a
hand-written fixture layer between the SPA and the contract it is generated from, and the divergence
it invites is exactly what DEV-2 exists to prevent. Developing against the real backend with a real
Postgres caught four defects in the first run — three of them (a JAX-RS routing conflict, an HQL
attribute-name error, a lazy-initialisation failure in DTO mapping) invisible to any mock.

**Quarkus OIDC's `web-app` mode as the BFF.** It does run the code flow server-side, but it stores
the tokens in an encrypted cookie rather than server-side against an opaque handle, which is not
what ADR 0003 specified and not what RFC 9700 prefers. Worth re-examining in the identity spike on
its merits, not adopting now as a side effect of needing a local login.

**`quarkus-elytron-security-properties-file` for dev users.** Real authentication with real roles,
but it puts usernames and passwords in a config file — against SEC-9's "no secrets in code or
config", and a fake credential is easier to mistake for a real one than a header is.
