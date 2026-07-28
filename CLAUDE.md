# CREWCOMP — monorepo

Maritime crew-compliance system replacing two forked Excel workbooks: a versioned requirements matrix, whole-swing compliance evaluation, exemption/query register workflow, crew self-service mobile apps, and an LLM evidence-extraction pipeline. ~60 users, 5 partnerships, security paramount, one-person dev+ops working with Claude Code.

## Source of truth

- **Spec (normative):** `docs/spec/crewcomp-production-spec.md` — V2 draft. §5 (engine semantics) and §4 (domain model) are normative; Appendix A enumerations are canonical. The V1 POC (separate repo, `app/`) is the behavioural reference for the engine.
- **Decisions:** `docs/decisions/` — ADRs. 0001 backend (Kotlin+Quarkus), 0002 mobile (Flutter — its encrypted-store mechanism is **amended by 0009**), 0003 identity (direct OIDC federation), 0004 pipeline (GitHub Actions), 0005 platform (**deferred**: AWS vs GCP, spike decides), 0006 persistence (Hibernate+Panache), 0007 audit ordering & hash chain, 0008 admin web stack & development auth shim, 0009 mobile sync contract, encrypted local store & resumable upload.
- **Research:** `docs/research/` — the §14 technology research (July 2026, web-verified) behind the ADRs. `00-recommendations.md` is the synthesis.
- **Handoffs:** `docs/handoff/` — work one component owes another, written when the two get out of step. `mobile-crew-app-backend.md` is the server side of the crew app's eight new screens.

## Layout

| Path | Contents | Status |
|---|---|---|
| `backend/` | Kotlin + Quarkus monolith: API, compliance engine, workflow, sync, jobs, embedded MCP server | **P1 spine + all ten §6 modules + mobile read path** — engine + tests, §4 schema, security/audit, compliance service, REST API, matrix versioning, register workflow, catalogue and exception write paths, §8 evidence pipeline, §9 notifications and scans, ADM-10 configuration/jobs/users, MCP, §10.3 sync |
| `admin-web/` | React + TypeScript admin SPA (types generated from backend OpenAPI) | **all 10 §6 modules, on the Nocturne dark design system** — shell, session, dashboard, swing planner, matrix, register, people & holdings, requirements catalogue, exceptions worklist, notifications centre, evidence queue, administration |
| `mobile/` | Flutter app (iOS + Android, feature parity mandated) | **all twelve of the design handoff's screens, on the Nocturne dark design system** — the four shipped ones restyled, eight new ones built; over an encrypted store, sync, outbox and resumable evidence upload. **iOS builds and runs on a simulator**, Android never built (no SDK) |
| `infra/` | OpenTofu; `aws/` and `gcp/` stacks until ADR 0005 resolves | empty — pipeline bootstrap |
| `runbooks/` | Operational runbooks (markdown, consumed by the AI triage bot) | one written (`ci-failure.md`); the rest arrive with the alerts they answer |
| `scripts/` | `dev-start.sh` / `dev-stop.sh` — the local development stack; `mobile-start.sh` — the crew app on an iOS simulator | works; see below |
| `docs/` | spec, ADRs, research, handoffs | current |
| `.github/` | workflows + AI review prompts | **verification lanes, AI review and scanning built**; deploy lane and previews wait on ADR 0005 |

## Running it locally

```bash
./scripts/dev-start.sh              # backend :8080 + admin SPA :5173, waits until both serve
./scripts/dev-start.sh backend      # or one at a time
./scripts/dev-stop.sh               # both
./scripts/dev-stop.sh backend       # before `./mvnw verify` — dev mode fights it over target/
```

Needs a container runtime (Colima or Docker Desktop) for the PostgreSQL that Dev Services
starts. The scripts point Testcontainers at Colima's socket, generate and reuse a compliant
`CREWCOMP_MCP_TOKEN`, and wait on the HTTP endpoint rather than a log line — a ready-line in a
log is not a contract, and the Testcontainers sidecar's is easy to mistake for the
application's. Runtime state (pidfiles, logs, the MCP token) lives in a gitignored `.dev/`.

Stopping the backend lets Ryuk reap the database container, so the next start re-migrates and
re-seeds. Anything you changed through the UI is gone — which is the same cold path CI takes.
The component guides document running each piece by hand.

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
- **A published matrix version is immutable** (§5.5). It is what makes a past evaluation reproducible, so a correction is a new draft published over the top, never an edit in place. The same rule, one layer down: a decided evidence document is terminal, and a holding recorded in error is corrected on the holding.
- **Every scheduled job is an idempotent scan** that recomputes what is due and dedupes what it raises. That property is why there is no job store; check it before adding a job. A job that needs retries, a queue, or to survive a restart mid-run does not belong in `JobRegistry`.
- **The mobile app is told its compliance answers, never left to compute them** (AUTH-1). The server's §5.2 evaluation travels in the sync payload; the device may count days against it (§7.6) and nothing more.

## Current phase

Early P1 across three components, with **the whole of §6 now built**. Green: **125 pure-domain
tests**, **143 backend integration tests** against real PostgreSQL (Colima + Quarkus Dev Services),
**64 frontend tests** with a production bundle that builds, and **127 Flutter tests** including a real
encrypted SQLite file. `backend/CLAUDE.md`, `admin-web/CLAUDE.md` and `mobile/CLAUDE.md` carry the
component detail — including the traps each has already paid for and the spec questions each takes a
position on.

What exists end to end, verified over real HTTP against a seeded database and driven in a browser:

- **The back office is complete as a set of screens, on the Nocturne dark design system.** All ten §6
  modules: sign in (development shim), per-partnership swing compliance, a swing planner that both
  ranks candidates and fills slots, the matrix with its version lifecycle, the register with its full
  workflow, the crew directory with audited holding edits, the requirement catalogue, the data-quality
  worklist, the notifications centre, the evidence verification queue, and administration. The visual
  system is a restyle onto a supplied design handoff — the information architecture, column sets, copy
  and state vocabulary are the spec's and did not move. `admin-web/CLAUDE.md` records the five places
  the implementation deliberately departs from the mock, each of which would otherwise have put a
  claim on screen that no server field backs.
- **The register closes the compliance loop.** Raising a request turns a `gap` into `pending` on
  the planner and an approval turns it into `exempt`, through §5.1 step 4's overlay — the gap
  report links straight to a pre-filled request, and the decision comes back to the same screen.
- **The matrix is versioned, and a published one cannot be edited.** Draft from any version, edit
  cells and partnership overrides, read the §5.5 diff, publish under an advisory lock that supersedes
  the previous version. Publishing changes what every swing evaluates to, immediately, and the
  superseded version stays evaluable — which is what makes a historical swing reconstructible.
- **The evidence pipeline runs all five §8 stages** and, with no LLM provider chosen, correctly
  extracts nothing and sends every document to a human. Auto-acceptance is off by default and cannot
  be switched on by accident: the threshold has no default, refuses to be set to 0, and the
  unconfigured extractor reports zero confidence — three independent facts have to change first. A
  Data Steward accepts, corrects or rejects; the holding moves through the same service the admin API
  uses, and the audit event carries both what was read and what was accepted.
- **Notifications are per recipient, routed per role, and idempotent.** Register events, matrix
  publication, exceptions and pipeline outcomes raise them; three scheduled scans find what no event
  knows about — expiries, approaching cutoffs, quota shortfalls and unfilled slots. Every scan carries
  a dedupe key, so running one twice produces exactly what running it once did.
- **Crew self-service** — a crew member's device takes a snapshot, applies deltas, survives
  deletions via tombstones, queues read-marks and evidence submissions offline, and uploads a
  2 MB document in chunks that resume after a kill, refuse to leave a hole, ignore replays and
  verify their digest. The store on disk is encrypted and unreadable without its key. A roster
  change raises a real notification to the crew member's device.
- **The crew app closes the evidence loop from the phone.** Each list drills down — a
  certification to its cell, level, holding and register overlay; a swing to its day count and
  cut-off — and evidence submission runs end to end on a simulator against the live backend: pick a
  photo or a PDF, stage it in the app container, queue it, and the uploader asks the server for
  its offset before every attempt, seeks on a 409 rather than restarting, and deletes the staged
  copy once the digest is accepted. It arrives in the Data Steward's ADM-9 queue as
  `pending_review` with nothing extracted, which is exactly LLM-2's launch posture. The camera
  branch is the one part a simulator cannot exercise.
- **The crew app is now a designed product rather than a data viewer, and on the console's own
  ground.** All twelve screens of the mobile design handoff are built and were driven on a
  simulator against the live backend: the four shipped ones restyled onto Nocturne, plus a Home
  that leads with the readiness ring and names one thing at a time, the one-tap update, course
  booking, the send-certificate sheet, the AI-parse confirmation, an exemption request, the
  pre-sail sign-off, and a supervisor's watch. Every ask answers in one tap, and every tap is a
  durable queue entry that reports itself — queued, sent, or failed with the server's own words
  and a retry.

  **Eight of those screens are ahead of the backend, deliberately.** There is no course catalogue,
  no extraction fields on a submission, no credits history, no team endpoint, and none of the seven
  new sync operations the one-tap answers post — `SyncService` rejects each as an unsupported
  operation type, per operation, without failing the batch. Each screen degrades in a way a crew
  member can read rather than in a way that looks finished: no course dates rather than invented
  ones, no credit tiles rather than a made-up streak, "the app is not sent your watch" rather than
  an empty list a supervisor reads as *everyone is fine*. `docs/handoff/mobile-crew-app-backend.md`
  is what has to become true, and `mobile/CLAUDE.md` records each departure and why.

The largest functional gaps, in the order they bite:

1. **Android has never been built, and no physical device has run either app.** iOS is real — Xcode
   26.6 is installed, `flutter build ios` succeeds, and the app runs on a simulator against the live
   backend with a genuinely encrypted Keychain-keyed store and a working offline mode
   (`mobile/CLAUDE.md` §"What the iOS run proved"). There is still no Android SDK, which is a parity
   risk ADR 0002 explicitly cares about. Still unproven anywhere: the camera (a simulator has
   none, so evidence submission's library and file paths have run and its capture branch has not),
   biometric binding, background upload surviving a kill, and code signing.
2. **No login** anywhere, and it now blocks more than it did. `GET /api/v1/session` is the stable
   half of the ADR 0003 contract; the code flow, token store and opaque cookie are the identity
   spike's. Until it lands, back-office users have no real accounts — so §9's per-role fan-out has
   nowhere to deliver in production, ADM-10 creates SEC-1b `local_test` accounts instead (refused
   under `prod`), and ADM-8 reads through a dev-only role proxy. Every one of those is a scaffold
   with a removal date rather than a design.
3. **No LLM provider (§14.5).** The pipeline is complete around a `LlmClient` that returns nothing.
   Choosing a provider is an adapter implementation plus a prompt; the privacy assessment (O-9,
   LLM-4) is the actual gate. Until then ADM-9's queue works with empty extractions and a Data
   Steward types the fields, which is exactly LLM-2's launch posture.
4. **No push or email delivery** (MOB-3). The in-app record is the source of truth and
   `notification_delivery` is ready for per-channel records; the unified APNs/FCM sender is the
   mobile spike's.
5. **No §11 migration.** `DevDataSeeder` is a synthetic development fixture, not the validated CSV
   load with its 35 ExceptionItems and CC24/CC25 acceptance diff. ADM-7's worklist is built and
   seeded with three invented items; the real 35 arrive with that load.
6. **No E2E test in the repository.** Every back-office screen has been driven in a real Chromium
   against a live backend, and every crew screen on an iOS simulator against the same — which is
   how five rendering bugs have been found so far — but making either a committed lane is the
   pipeline bootstrap's.
7. **The crew app's eight new screens are ahead of the server they need.** Course catalogue,
   extraction fields, readiness and credits in the sync payload, a status-only team endpoint, a
   mobile-raised exemption, an attestation record, and seven new sync operations. None of it is
   speculative work — every item has a built screen waiting on it, and
   `docs/handoff/mobile-crew-app-backend.md` says what each one needs. Two of them are also
   blocked on something else: the team endpoint needs a supervisory role, which is the identity
   spike's, and MOB-6's real intake route — Attest as a share target in Mail, Files and WhatsApp —
   is native iOS and Android work rather than backend work.

Next steps, in order (per `docs/research/00-recommendations.md` §"Recommended spike sequence"):
1. Pipeline bootstrap — **the verification half is built and the deploy half cannot be.**
   `.github/workflows/` now carries every lane the three components' CLAUDE.md files tell a developer
   to run, plus the three nobody can run locally: the **Android build** (no SDK here, so CI is the
   only thing that will ever enforce ADR 0002's parity mandate), the **native image** (no GraalVM),
   and the **generated-types checks** against a schema artefact the backend job publishes — checking
   against a schema the client job built itself could never catch a stale committed file. Also DEV-3's
   two AI review passes (advisory correctness on every PR; a path-filtered blocking security pass),
   Gitleaks, Semgrep and grouped Dependabot.

   **None of it has been executed** — there is no way to run a GitHub Actions workflow from here. The
   YAML parses, every command in it was run by hand first, and every pattern in the security pass's
   path filter is asserted to match at least one tracked file (the mobile patterns were wrong on the
   first attempt for exactly that reason, and a filter that matches nothing reports "not applicable"
   and passes). Expect the first real run to need adjusting anyway.

   What is deliberately absent: the deploy lane, the preview environment (DEV-4, Neon branch
   database), image scanning and cosign signing, and the OpenTofu stacks. ADR 0004 says the deploy
   lane lands with ADR 0005's platform verdict, and writing a cloud stack now would be writing the
   thing the spike decides.
2. Backend spike (Quarkus native + MCP + OIDC multitenancy) deployed to **both** AWS and GCP →
   resolves ADR 0005. Also the place to add Postgres RLS for AUTH-2 against a real database.
3. Identity spike (BFF session + device-session layer) — replaces the development auth shim in
   both clients.
4. Mobile device spike — the half of the "upload gauntlet" that needs hardware: camera capture,
   Keychain/Keystore keys, biometric binding, and `background_downloader` surviving a kill while
   backgrounded.

The spike sequence has not changed, but what depends on it has. With §6 built and the crew app's
twelve screens with it, **all four spikes are now unblocking finished features rather than enabling
unwritten ones**: the identity spike replaces ADM-8's and ADM-10's scaffolding and is what lets
MOB-11's Team tab exist at all, the platform spike replaces ADM-9's byte-streaming preview with a
signed URL and the in-memory job history with real monitoring, §14.5's provider choice turns the
evidence pipeline from a correct empty extractor into a working one *and* fills MOB-7's fields, and
the device spike is the only way the camera, biometric binding and kill-surviving upload behind
MOB-6 and MOB-9 get proven. That is a better position to be in — each spike now has a screen to
verify itself against.

Sitting alongside them, and not a spike: the crew app's backend handoff
(`docs/handoff/mobile-crew-app-backend.md`). It needs no decision, only work, and it is what turns
eight honestly-degrading screens into working ones.
