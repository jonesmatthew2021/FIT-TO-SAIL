# CREWCOMP — monorepo

Maritime crew-compliance system replacing two forked Excel workbooks: a versioned requirements matrix, whole-swing compliance evaluation, exemption/query register workflow, crew self-service mobile apps, and an LLM evidence-extraction pipeline. ~60 users, 5 partnerships, security paramount, one-person dev+ops working with Claude Code.

## Source of truth

- **Spec (normative):** `docs/spec/crewcomp-production-spec.md` — V2 draft. §5 (engine semantics) and §4 (domain model) are normative; Appendix A enumerations are canonical. The V1 POC (separate repo, `app/`) is the behavioural reference for the engine.
- **Decisions:** `docs/decisions/` — ADRs. 0001 backend (Kotlin+Quarkus), 0002 mobile (Flutter — its encrypted-store mechanism is **amended by 0009**), 0003 identity (direct OIDC federation), 0004 pipeline (GitHub Actions), 0005 platform (**deferred**: AWS vs GCP, spike decides), 0006 persistence (Hibernate+Panache), 0007 audit ordering & hash chain, 0008 admin web stack & development auth shim, 0009 mobile sync contract, encrypted local store & resumable upload.
- **Research:** `docs/research/` — the §14 technology research (July 2026, web-verified) behind the ADRs. `00-recommendations.md` is the synthesis.
- **Handoffs:** `docs/handoff/` — work one component owes another, written when the two get out of step. `mobile-crew-app-backend.md` is the server side of the crew app's eight new screens. `mobile-encrypted-store-for-a-new-app.md` is the outward one: the encrypted-store layer — cipher pin, key handling, start-up guard, the host-runnable encryption assertion, the Android native-library build and its CI check — written verbatim so a second Flutter project can start from working code rather than rediscovering that plain SQLite ignores `pragma key` silently.

## Layout

| Path | Contents | Status |
|---|---|---|
| `backend/` | Kotlin + Quarkus monolith: API, compliance engine, workflow, sync, jobs, embedded MCP server | **P1 spine + all ten §6 modules + ADM-11 + every crew-app write path** — engine + tests, §4 schema, security/audit, compliance service, REST API, matrix versioning, register workflow, catalogue and exception write paths, §8 evidence pipeline, §9 notifications and scans, ADM-10 configuration/jobs/users, MCP, §10.3 sync, MOB-8's course catalogue and MOB-11's supervisor watch |
| `admin-web/` | React + TypeScript admin SPA (types generated from backend OpenAPI) | **all 10 §6 modules plus ADM-11, on the Nocturne dark design system** — shell, session, dashboard, swing planner, matrix, register, people & holdings, requirements catalogue, exceptions worklist, crew requests, notifications centre, evidence queue, administration — plus the shell's **AI assistant panel** (push/overlay, ⌘K), whose server half answers 503 until §14.5's provider is chosen |
| `mobile/` | Flutter app (iOS + Android, feature parity mandated) | **all twelve of the design handoff's screens, on the Nocturne dark design system** — the four shipped ones restyled, eight new ones built; over an encrypted store, sync, outbox and resumable evidence upload. **iOS and Android both build and run on simulators** — Android as of 29 July, with an encrypted Keystore-keyed store and a working offline cold start; no physical device yet |
| `infra/` | OpenTofu; `aws/` and `gcp/` stacks until ADR 0005 resolves | empty — pipeline bootstrap |
| `deploy/` | Docker Compose for **one persistent instance on its own Tailscale node** — Postgres with a volume, the backend under the `demo` profile, Caddy serving the built SPA, and a `tailscale` sidecar the SPA runs inside so nothing binds a host port; `rebuild.sh`, `reset.sh`, `backup.sh` | **running** at `https://attest.monster-mora.ts.net`. **Not `infra/`**: no cloud service anywhere in it, so it does not pre-empt ADR 0005 |
| `runbooks/` | Operational runbooks (markdown, consumed by the AI triage bot) | one written (`ci-failure.md`); the rest arrive with the alerts they answer |
| `scripts/` | `dev-start.sh` / `dev-stop.sh` — the local development stack; `mobile-start.sh` — the crew app on an iOS simulator; `android-start.sh` / `android-stop.sh` — the same app on an emulator | works; see below |
| `docs/` | spec, ADRs, research, handoffs | current |
| `.github/` | workflows + AI review prompts | **verification lanes, AI review and scanning built**; deploy lane and previews wait on ADR 0005 |

## Running it locally

```bash
./scripts/dev-start.sh              # backend :8080 + admin SPA :5173, waits until both serve
./scripts/dev-start.sh backend      # or one at a time
./scripts/dev-stop.sh               # both
./scripts/dev-stop.sh backend       # before `./mvnw verify` — dev mode fights it over target/

./scripts/dev-start.sh --dataset extracted   # seed the POC's workbook extracts instead of the
                                             # synthetic fixture — REAL crew data, read from
                                             # ~/shipping (--extract-root overrides). Stop first:
                                             # a dataset only takes effect on a fresh database.
```

Two dev datasets exist (`crewcomp.dev-seed.dataset`): **synthetic** (default — invented crew,
safe for any audience, including test cases) and **extracted** (the POC's validated workbook
extracts, for demonstrations to the people who know the data). The extracts are real personal
data: they live outside this repository and are never committed to it. The two catalogues never
mix — switching is stop, then start with the other flag.

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

Early P1 across three components, with **the whole of §6 now built**, one module beyond it (ADM-11),
and **every operation the crew app posts now accepted**. Green: **141 pure-domain tests**,
**193 backend integration tests** against real PostgreSQL (Colima + Quarkus Dev Services),
**69 frontend tests** with a production bundle that builds, and **145 Flutter tests** including a
real encrypted SQLite file. `backend/CLAUDE.md`, `admin-web/CLAUDE.md` and `mobile/CLAUDE.md` carry the
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
  no extraction fields on a submission, no credits history, no team endpoint, and three of the seven
  new sync operations the one-tap answers post are still rejected — per operation, without failing
  the batch. Each screen degrades in a way a crew
  member can read rather than in a way that looks finished: no course dates rather than invented
  ones, no credit tiles rather than a made-up streak, "the app is not sent your watch" rather than
  an empty list a supervisor reads as *everyone is fine*. `docs/handoff/mobile-crew-app-backend.md`
  is what has to become true, and `mobile/CLAUDE.md` records each departure and why.

- **The first two one-tap answers now reach the office, and land in a queue somebody works.**
  `requirement.progress` and `requirement.help` become a **crew statement** — §7.5's third
  client-originated write, beside the evidence submission and the read-mark, and deliberately the
  least powerful of the three. Nothing in the engine reads it: a person who says "course booked"
  against a lapsed medical still evaluates as a gap, because they still do not hold it. Its one
  consequence is to make a notification *stop* — the expiry scan skips the crew warning for the exact
  expiry the person answered, and starts again by itself when a renewal moves the date. The
  coordinator's roster warning is not suppressed, because a booked course is not a held certificate.
  Neither operation needed a line of client code: the app was already sending what the server grew a
  reader for.

- **ADM-11, the crew request queue, is the first module beyond §6.** A notification is a "something
  happened" signal — addressed to whoever held the role at that moment, marked read by one of them —
  and no query answered *what has the crew asked us for that nobody has dealt with*. The queue does,
  generically over kinds, so three of the five outstanding one-tap operations land in it without a
  second screen. `open` → `actioned` | `dismissed`, both terminal, both needing a note, no reopen.
  **Dismissal is why it could not wait**: the suppression above silences a crew member's expiry
  warning on their word alone, and a coordinator who finds no such booking has to be able to put it
  back. §6 enumerates ADM-1 to ADM-10 and stops, so the module number is ours and worth confirming.

- **Two more one-tap operations landed, and neither is a statement.** `register.exemption_request`
  writes a real §6.4 record — the one client-originated write that changes what the engine answers,
  and it does so only by *asking*: the cell moves to `pending`, never to `exempt`, until a Workflow
  Manager decides. That overlay is also how the decision reaches the phone, which beats notifying a
  crew member about a record they cannot open. `attestation.sign_off` writes a pre-sail declaration
  whose **timestamp and its formatting are both the server's** — a legal record timestamped from the
  phone of the person making it is an assertion by the party it is evidence against.

- **The last three operations landed, and each needed a decision rather than work.** A **course
  catalogue** that is ours *behind a `CourseCatalogue` port* — because a provider feed may replace it
  and the interesting half is ours either way: an option is only worth showing if it beats the crew
  member's expiry and does not fall inside a swing they are aboard for, and both come from our
  roster. **A seat request is a statement, not a booking** — a crew member cannot commit a training
  budget, so it lands in ADM-11 beside the other asks, and nothing holds a seat this system has no
  contract for. And **`vessel_master` is the supervisory role**, with a watch derived from
  co-assignment rather than from an org chart nobody would maintain: change the roster and the watch
  changes with it. A nudge names its sender, is audited whether or not it could be delivered, and is
  one nudge however many times a device replays it.

- **Two rules came out of that pass and are worth keeping.** Suppression is now stated as data
  (`NEVER` / `ON_WORD` / `ON_ACTION`): "I have booked the course" is believed on the crew member's
  word because only they know it, where "I would like that seat" earns nothing until the office
  answers — asking is not having, and the certificate is still lapsing. And a supervisor's *write*
  is never wider than their read: `TeamService.supervisedCrew` is the single definition both the
  watch and the nudge authorise against.

- **And the loop closes: the decision reaches the phone.** The statement is now a replicated
  person-scoped row like a holding — same trigger-assigned cursor, same tombstones — joined to the
  device's own outbox record by the `opId` the device minted, so the app matches a decision to the
  tap that produced it with no second identifier in existence. A §9 notification arrives with it,
  carrying the coordinator's note; ADM-11's form labels that field *"the crew member reads this"*,
  which is what turns "no record" into "we could not find your booking — can you forward the
  confirmation?". On the card a dismissal **puts the ask back**, because the server has
  simultaneously resumed chasing: two expressions of one fact rather than two rules that can drift.

The largest functional gaps, in the order they bite:

1. **No physical device has run either app.** Both platforms now build *and* run on simulators —
   iOS on Xcode 26.6, and **Android as of 29 July**, which closes the parity gap ADR 0002 cares
   about as far as a simulator can. The Android run established the three things only the platform
   itself could: `libsqlite3mc.so` cross-compiles for all three ABIs, the on-disk store is genuinely
   encrypted (no crew name recoverable from a file the app is displaying), and
   `flutter_secure_storage` reaches the **Android Keystore** — direct evidence, where iOS had it only
   by consequence. Offline cold start works on both (`mobile/CLAUDE.md` §"What the Android run
   proved"). What hardware still owes: a **real** Keystore/Keychain (an emulator's is software-backed,
   with no TEE or StrongBox, which is the property SEC-12 leans on), code signing on both stores, the
   camera (no simulator has one, so evidence submission's library and file paths have run and its
   capture branch has not), biometric binding, and background upload surviving a kill. Also: only the
   four shipped tabs have been driven on Android — the eight newer screens are iOS-only so far.
2. **No login** anywhere, and it now blocks more than it did. `GET /api/v1/session` is the stable
   half of the ADR 0003 contract; the code flow, token store and opaque cookie are the identity
   spike's. Until it lands, back-office users have no real accounts — so §9's per-role fan-out has
   nowhere to deliver in production, ADM-10 creates SEC-1b `local_test` accounts instead (refused
   under `prod`), and ADM-8 reads through a dev-only role proxy. Every one of those is a scaffold
   with a removal date rather than a design.
3. **No LLM provider (§14.5).** The pipeline is complete around a `LlmClient` that returns nothing.
   Choosing a provider is an adapter implementation plus a prompt; the privacy assessment (O-9,
   LLM-4) is the actual gate. Until then ADM-9's queue works with empty extractions and a Data
   Steward types the fields, which is exactly LLM-2's launch posture — and the admin shell's
   assistant panel (30 July) sits behind the same gate: the panel is complete, and
   `POST /assistant/ask` answers an honest 503 until a provider and its retrieval layer exist.
4. **No push or email delivery** (MOB-3). The in-app record is the source of truth and
   `notification_delivery` is ready for per-channel records; the unified APNs/FCM sender is the
   mobile spike's.
5. **§11 migration is half-built.** `ExtractedSeedLoader` (30 July) ports every POC fix-up rule
   and loads the real extracts as the dev stack's `extracted` dataset — the known UNI CC24
   shortfall (M7, 0/1 GPH on Shift 1) reproduces through the real engine, and ADM-7's worklist
   carries the full extract anomaly set rather than three invented items. Still owed:
   re-runnability against a populated database, and the CC24/CC25 acceptance diff as a committed
   test rather than a spot check.
6. **No E2E test in the repository.** Every back-office screen has been driven in a real Chromium
   against a live backend, and every crew screen on an iOS simulator against the same — which is
   how five rendering bugs have been found so far — but making either a committed lane is the
   pipeline bootstrap's.
7. **Two of the crew app's screens are still ahead of the server, down from eight.** The course
   catalogue, the team endpoint and all seven sync operations landed on 29 July. What is left is
   **MOB-7's extraction fields**, which are waiting on §14.5's provider choice rather than on work,
   and **MOB-0's credit tiles**, which need swing history nobody has computed. Readiness, the
   headline and the declaration wording are all still client-derived and should come down the wire.
   `docs/handoff/mobile-crew-app-backend.md` says what each one needs. MOB-6's real intake route —
   Attest as a share target in Mail, Files and WhatsApp — is native iOS and Android work rather
   than backend work.

Next steps, in order (per `docs/research/00-recommendations.md` §"Recommended spike sequence"):
1. Pipeline bootstrap — **the verification half is built and the deploy half cannot be.**
   `.github/workflows/` now carries every lane the three components' CLAUDE.md files tell a developer
   to run, plus two nobody can run locally: the **native image** (no GraalVM) and the
   **generated-types checks** against a schema artefact the backend job publishes — checking
   against a schema the client job built itself could never catch a stale committed file. Also DEV-3's
   two AI review passes (advisory correctness on every PR; a path-filtered blocking security pass),
   Gitleaks, Semgrep and grouped Dependabot.

   **The Android lane is no longer one of the unrunnable ones**, and running it locally is what found
   its two bugs: it installed no NDK (AGP auto-downloads build-tools and CMake but not the NDK, so
   the lane would have failed on the encrypted store's `libsqlite3mc.so`), and it pinned a JDK the
   build has never been run on. It now derives the NDK revision from the Flutter SDK rather than
   hardcoding it, and asserts `libsqlite3mc.so` is present for all three ABIs — a build can succeed
   with that library missing for one architecture, and nothing else in CI would notice.

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
unwritten ones**: the identity spike replaces ADM-8's and ADM-10's scaffolding and is where MOB-11's
`vessel_master` claim comes from once a session exists — the *scoping* is built and the Team tab
already appears on the server's say-so, so what the spike owns is narrower than it was. The platform
spike replaces ADM-9's byte-streaming preview with a
signed URL and the in-memory job history with real monitoring, §14.5's provider choice turns the
evidence pipeline from a correct empty extractor into a working one *and* fills MOB-7's fields, and
the device spike is the only way the camera, biometric binding and kill-surviving upload behind
MOB-6 and MOB-9 get proven. That is a better position to be in — each spike now has a screen to
verify itself against.

Sitting alongside them, and not a spike: what is left of the crew app's backend handoff
(`docs/handoff/mobile-crew-app-backend.md`). Most of it has been done — six decisions taken on
29 July closed the course catalogue, the supervisory role, the seat-request model, the register's
triage question and ADM-11's module number. What remains there is genuinely waiting on §14.5 and on
a history nobody has computed, rather than on a decision.
