# CREWCOMP — monorepo

Maritime crew-compliance system replacing two forked Excel workbooks: a versioned requirements matrix, whole-swing compliance evaluation, exemption/query register workflow, crew self-service mobile apps, and an LLM evidence-extraction pipeline. ~60 users, 5 partnerships, security paramount, one-person dev+ops working with Claude Code.

## Source of truth

- **Spec (normative):** `docs/spec/crewcomp-production-spec.md` — V2 draft. §5 (engine semantics) and §4 (domain model) are normative; Appendix A enumerations are canonical. The V1 POC (separate repo, `app/`) is the behavioural reference for the engine.
- **Decisions:** `docs/decisions/` — ADRs. 0001 backend (Kotlin+Quarkus), 0002 mobile (Flutter — its encrypted-store mechanism is **amended by 0009**), 0003 identity (direct OIDC federation), 0004 pipeline (GitHub Actions), 0005 platform (**deferred**: AWS vs GCP, spike decides), 0006 persistence (Hibernate+Panache), 0007 audit ordering & hash chain, 0008 admin web stack & development auth shim, 0009 mobile sync contract, encrypted local store & resumable upload.
- **Research:** `docs/research/` — the §14 technology research (July 2026, web-verified) behind the ADRs. `00-recommendations.md` is the synthesis.

## Layout

| Path | Contents | Status |
|---|---|---|
| `backend/` | Kotlin + Quarkus monolith: API, compliance engine, workflow, sync, jobs, embedded MCP server | **P1 spine + six §6 modules + mobile read path** — engine + tests, §4 schema, security/audit, compliance service, REST API, register workflow, catalogue and exception write paths, MCP, §10.3 sync, evidence ingest |
| `admin-web/` | React + TypeScript admin SPA (types generated from backend OpenAPI) | **6 of 10 §6 modules** — shell, session, dashboard, swing planner (with assignment), register, people & holdings, requirements catalogue, exceptions worklist |
| `mobile/` | Flutter app (iOS + Android, feature parity mandated) | **offline spine + 3 of §7's screens** — encrypted store, sync, outbox; **iOS builds and runs on a simulator**, Android never built (no SDK) |
| `infra/` | OpenTofu; `aws/` and `gcp/` stacks until ADR 0005 resolves | empty — pipeline bootstrap |
| `runbooks/` | Operational runbooks (markdown, consumed by the AI triage bot) | seeded |
| `docs/` | spec, ADRs, research | current |
| `.github/` | workflows + AI review prompts | prompts seeded, workflows at bootstrap |

## Hard rules (from the spec — apply to all code)

- **No business logic only in a client.** The backend re-validates every write (AUTH-1). Mobile pre-validation is advisory.
- **Crew row-scoping is enforced centrally** (policy layer / RLS), never per-endpoint (AUTH-2).
- **Every business mutation is audited** with the authenticated actor; the actor field distinguishes human / AI-proposed-human-approved / AI-automatic (AUTH-3, AIA-3). AuditEvents are self-contained, immutable, monotonically ordered (§17.4 export-ready).
- **No app-stored credentials, ever** (SEC-1). UserAccount holds only (provider, issuer/tenant, subject) linkage. Issuer + tenant/domain verified on **every** token (SEC-1a).
- **MCP server** (§13.1): module of the monolith, calls the same validated service layer as the API — never raw DB. Token-authenticated in every environment; disabled in production by default.
- **The LLM never mutates the system of record** (LLM-1); extracted document content is data, never instructions (LLM-3).
- **Dates are calendar dates** in the operating timezone (AWST assumed, O-11) — store dates, not timestamps (NFR-5).
- **Business keys are never primary keys** (Sam #, register IDs are unique business keys on surrogate-keyed rows).
- **Until ADR 0005 resolves:** no cloud-SDK usage outside `infra/`; cloud services (object storage, scheduler, secrets, LLM) go behind thin backend adapters.
- **Migrations are forward-only** and must be backward-compatible with the previous app revision (expand/contract) — rollback rolls back code, never schema.
- **Client types are generated from the backend's OpenAPI schema** (DEV-2), never hand-written, and CI fails when the committed types drift from it.
- **Development-only code is removed at build time**, not disabled at runtime: the auth shim and data fixture are absent from a production artefact, and additionally refuse to start under `prod`. On the clients the same rule holds through compile-time constants — `import.meta.env.DEV` in the SPA, `kDebugMode` in Flutter.
- **Sync change tracking is maintained by database triggers**, never by application code (ADR 0009). A cursor a write path can forget is a cursor that silently stops replicating a row to a crew member's device, with no error anywhere.
- **The mobile app is told its compliance answers, never left to compute them** (AUTH-1). The server's §5.2 evaluation travels in the sync payload; the device may count days against it (§7.6) and nothing more.

## Current phase

Early P1 across three components. Green: **125 pure-domain tests**, **75 backend integration
tests** against real PostgreSQL (Colima + Quarkus Dev Services), **28 frontend tests** with a
production bundle that builds, and **43 Flutter tests** including a real encrypted SQLite file.
`backend/CLAUDE.md`, `admin-web/CLAUDE.md` and `mobile/CLAUDE.md` carry the component detail —
including the traps each has already paid for and the spec questions each takes a position on.

What exists end to end, verified over real HTTP against a seeded database:

- **Back office** — sign in (development shim), per-partnership swing compliance, a swing planner
  that both ranks candidates and fills slots, the register with its full workflow, the crew
  directory with audited holding edits, the requirement catalogue, and the data-quality worklist.
  Six of the ten §6 modules; the other four are named on screen as unbuilt with what each is
  waiting on.
- **The register closes the compliance loop.** Raising a request turns a `gap` into `pending` on
  the planner and an approval turns it into `exempt`, through §5.1 step 4's overlay — the gap
  report links straight to a pre-filled request, and the decision comes back to the same screen.
- **Crew self-service** — a crew member's device takes a snapshot, applies deltas, survives
  deletions via tombstones, queues read-marks and evidence submissions offline, and uploads a
  2 MB document in chunks that resume after a kill, refuse to leave a hole, ignore replays and
  verify their digest. The store on disk is encrypted and unreadable without its key. A roster
  change now raises a real notification to the crew member's device.

The largest functional gaps, in the order they bite:

1. **Android has never been built, and no physical device has run either app.** iOS is now real —
   Xcode 26.6 is installed, `flutter build ios` succeeds, and the app runs on a simulator against
   the live backend with a genuinely encrypted Keychain-keyed store and a working offline mode
   (`mobile/CLAUDE.md` §"What the iOS run proved"). There is still no Android SDK, which is a
   parity risk ADR 0002 explicitly cares about. Still unproven anywhere: the camera (MOB-4
   capture), biometric binding, background upload surviving a kill, and code signing.
2. **No matrix module** (ADM-3) — the largest remaining one. `MatrixDiff` implements §5.5 and
   `MatrixSnapshotService` reads the published version; nothing lists, drafts, edits or publishes
   one, so the matrix can only be changed by a migration.
3. **The evidence pipeline stops after ingest.** Documents upload and sit at
   `pending_extraction`: no extraction, no matching, no review queue (§8, ADM-9), no `LlmClient`.
4. **Notifications have no scheduler and no back-office recipient.** Assignment changes raise
   them; the §9 expiry scan, cutoff-approaching job, fan-out and push delivery (APNs/FCM) do not
   exist, and a back-office user cannot receive one at all until the identity spike gives them a
   UserAccount — which is what ADM-8 is actually waiting on.
5. **No login** anywhere. `GET /api/v1/session` is the stable half of the ADR 0003 contract; the
   code flow, token store and opaque cookie are the identity spike's.
6. **No §11 migration.** `DevDataSeeder` is a synthetic development fixture, not the validated
   CSV load with its 35 ExceptionItems and CC24/CC25 acceptance diff. ADM-7's worklist is built
   and seeded with three invented items; the real 35 arrive with that load.

Next steps, in order (per `docs/research/00-recommendations.md` §"Recommended spike sequence"):
1. Pipeline bootstrap (repo CI, OpenTofu baselines, AI review workflows). Lanes: `./mvnw verify`
   with ITs; `npm run verify:api`, `npm run build`, `npm test` and the dev-shim grep for the SPA;
   `dart run tool/generate_api.dart --check`, `flutter analyze`, `flutter test` and now
   `flutter build ios --no-codesign` for mobile. **Android needs a runner with the SDK** — that
   lane cannot be run here at all, so CI is the only thing that will ever enforce ADR 0002 parity.
2. Backend spike (Quarkus native + MCP + OIDC multitenancy) deployed to **both** AWS and GCP →
   resolves ADR 0005. Also the place to add Postgres RLS for AUTH-2 against a real database.
3. Identity spike (BFF session + device-session layer) — replaces the development auth shim in
   both clients.
4. Mobile device spike — the half of the "upload gauntlet" that needs hardware: camera capture,
   Keychain/Keystore keys, biometric binding, and `background_downloader` surviving a kill while
   backgrounded.
