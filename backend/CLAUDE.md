# backend/ — Kotlin + Quarkus monolith

The P1 spine exists: the §5 compliance engine with its test suite, the §4 baseline schema, the
persistence and security layers, the compliance service, the REST API, and the embedded MCP
server. On top of it, **every §6 admin module now has a backend**: assignment (ADM-2), matrix
versioning (ADM-3), the register workflow (ADM-4), the catalogue (ADM-6), the exceptions worklist
(ADM-7), notifications with the §9 scans (ADM-8), the §8 evidence pipeline and its verification
queue (ADM-9), and configuration, jobs, users and the SEC-1a allow-list (ADM-10) — plus §10.3 sync
and **ADM-11**, the crew request queue, which §6 does not enumerate and the crew app's one-tap
answers needed.

**Every operation the crew app posts is now accepted** (29 July 2026). The last three needed
decisions rather than work, and got them: a course catalogue that is ours *behind a
`CourseCatalogue` port* (`au.crewcomp.courses`), a seat request modelled as a **statement** rather
than a booking, and `vessel_master` reused for MOB-11 with a watch derived from co-assignment
(`TeamService`). `docs/handoff/mobile-crew-app-backend.md` records what each one settled.

What is left is not a module but the four spikes: the platform decision, identity, the pipeline, and
mobile hardware. Two things in this component are honest placeholders rather than gaps, and both are
waiting on a decision rather than on work: **no LLM provider** is selected (§14.5), so extraction
returns nothing and every document goes to a human — which is LLM-2's launch posture, not a
degradation; and **no §11 migration** — though its first cut now exists as `ExtractedSeedLoader`,
which loads the POC's workbook extracts as the dev stack's second dataset (see "Local
development"). What §11 still owes is re-runnability against a populated database and the
CC24/CC25 acceptance diff.

## Stack (decided)

Kotlin 2.3, Quarkus 3.33 LTS (`io.quarkus.platform:quarkus-bom:3.33.2` — the latest 3.33 on
Central), PostgreSQL ≥ 15. Extensions: quarkus-rest-jackson, quarkus-hibernate-orm-panache-kotlin
(ADR 0006), quarkus-jdbc-postgresql, quarkus-flyway, quarkus-oidc (ADR 0003),
quarkus-mcp-server-http 1.13.1, quarkus-smallrye-openapi, quarkus-hibernate-validator,
quarkus-smallrye-health, quarkus-micrometer-registry-prometheus. GraalVM native image for
deployment; JVM mode for dev.

Scheduled work is **quarkus-scheduler**, not JobRunr. JobRunr wants to own its own tables, and
nothing here needs a job store: every scheduled job is an idempotent scan that recomputes what is
due and dedupes what it raises, so a missed run is caught by the next one and an interrupted run is
finished by it. The `TaskScheduler` adapter already declares the contract as "run this named job on
this cadence" and names an in-process scheduler as a valid implementation, so this stays swappable
for a platform scheduler when ADR 0005 resolves. **Revisit if a job ever needs retries, a queue, or
to survive a restart mid-run** — that is the property to check before adding one (`JobRegistry`).

## Build and test

```bash
./mvnw test                        # 141 pure-domain tests — no Docker needed
./mvnw verify                      # + package; ITs skipped by default
./mvnw verify -DskipITs=false      # + 193 integration tests — needs a container runtime
./mvnw -Dnative verify -DskipITs=false   # native image; CI on every merge (ADR 0001)
./mvnw quarkus:dev                 # :8080, dev auth shim + dev data fixture (see below)
```

`quarkus:dev` also writes `target/openapi/openapi.json`, which `admin-web` generates its
TypeScript from (DEV-2). `./mvnw package` writes it too.

The container runtime here is **Colima** (`brew install colima docker`, `colima start --cpu 2
--memory 2 --disk 20`; `colima stop` reclaims the RAM). Testcontainers does not pick up Colima's
Docker context on its own, so the IT lane needs:

```bash
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock   # Ryuk mounts the socket by its conventional path
```

Worth putting in `~/.zshrc`. CI runners have a native Docker socket and need neither.

`*IT` tests run under Failsafe, not Surefire. This is not cosmetic: a `@QuarkusTest` boots the
application — and therefore Dev Services, and therefore a container — at JUnit *discovery* time,
so simply having one on Surefire's list breaks `mvn test` on a machine without Docker. Keep
DB-backed tests named `*IT`.

`quarkus dev` needs `CREWCOMP_MCP_TOKEN` set; start-up fails without it while MCP is enabled
(MCP-4, below). **It must be at least 32 characters** — a short token fails start-up exactly as
hard as a missing one, and the message is an `IllegalStateException` from `McpConfigurationCheck`
rather than anything that names the length.

`../scripts/dev-start.sh backend` handles that token and Colima's socket for you; see the root
guide. Nothing below assumes you used it.

**`quarkus:dev` and a Maven build fight over `target/`.** Stop the dev server before running
`verify` (`../scripts/dev-stop.sh backend` leaves the SPA up), and restart it after. Dev mode is
otherwise the fastest compile check there is — any request recompiles and reports the error — so
the working loop is dev mode for iteration, a full `verify` before committing.

### Three things about the IT harness

- **`FixtureSeeder.clear()` must delete children explicitly.** The FKs cascade in the database,
  but a JPQL bulk delete does not fire them: `delete from RegisterRecord` with notes still
  attached is a constraint violation, not a cascade. The order in `clear()` is load-bearing at
  both ends — children first for the FKs, `SyncTombstone` last because every delete above it
  fires the tombstone triggers.
- **Pin the business date for anything that depends on "today".** The fixture swing is fixed at
  2026-08-01..28 with a cutoff of 2026-07-25, and `BusinessClock` returns the real date, so
  whether a register submission is late depends on when the suite runs. `clock.overrideToday(...)`
  in a `@BeforeEach` (and `clearOverride()` after) makes both sides of Q17 testable instead of
  one of them being whichever the calendar allows. The admin date override exists for exactly this
  (§1). Note the latent fragility this exposes: the older ITs assert against that fixture window
  with an unpinned clock, so they will start failing once the real date passes it.
- **RestAssured does not decode a raw path string.** `get("/api/v1/register?type=MRL%20Query")`
  sends the `%20` literally and the server sees `MRL%20Query`. Use `.queryParam("type", "MRL
  Query")` for any value with a space in it — which, given Appendix A's register enumerations are
  human-readable phrases, is most of them.

## Layout

| Package | Contents |
|---|---|
| `au.crewcomp.engine` | **The §5 engine. Pure Kotlin — no CDI, no JPA, no framework types.** Cell/person/swing evaluation, rule resolution, quotas, suggestions, gap report, expiry alerts, matrix diff. |
| `au.crewcomp.reference` | §4.1 partnerships, vessels, positions, slots, crew changes, requirements. `ReferenceService` also carries the ADM-6 catalogue write path and its usage counts |
| `au.crewcomp.rules` | §4.2 matrix versions, requirement/conditional/quota rules. `MatrixService` is ADM-3's whole lifecycle: draft → edit → publish |
| `au.crewcomp.people` | §4.3 people, user accounts, identity providers, holdings, assignments, leave. `HoldingService` and `AssignmentService` are the two write paths; `UserAdminService` is ADM-10's access surface; `CrewStatementService` is the crew app's one-tap answers (deliberately none of the above) and ADM-11's queue over them; `AttestationService` is MOB-9's pre-sail declaration |
| `au.crewcomp.people` (cont.) | `TeamService` is MOB-11's watch and its nudge — the one read here that answers about *other* people, scoped by co-assignment rather than by the actor's ambient `DataScope` |
| `au.crewcomp.courses` | MOB-8's catalogue. `CourseCatalogue` is a **port** (the domain question of who owns these dates is open); `DatabaseCourseCatalogue` is its only adapter; `CourseOffers` is the pure per-person filtering; `CourseOfferService` gathers what it needs; `CourseCatalogueService` is the MCP-only write path |
| `au.crewcomp.workflow` | §4.4 register records, conditions, notes, exception items. `RegisterService` is ADM-4's whole lifecycle; `ExceptionService` is ADM-7's worklist |
| `au.crewcomp.compliance` | Application service around the engine: entity↔engine mapping, matrix snapshots, `ComplianceService` |
| `au.crewcomp.sync` | §10.3 mobile sync: delta reads, tombstones, `SyncService`. Takes no person id anywhere — it answers for the authenticated crew member only |
| `au.crewcomp.evidence` | §8 all five stages. `EvidenceService` is ingest and the MOB-5a chunk upload; `EvidencePipeline`/`ExtractionStages` are extract/match/decide; `EvidenceReviewService` is ADM-9's queue |
| `au.crewcomp.notify` | §9 notifications. The in-app record is the source of truth; push and email are channels for it. `NotificationScans` is the three scheduled scans |
| `au.crewcomp.api` | JAX-RS resources, DTOs, exception mappers |
| `au.crewcomp.assistant` | §17.1's first surface: the admin shell's read-only assistant. `AssistantService` states the contract (read-only, role-scoped, citations mandatory, audited) and refuses with 503 until a §14.5 provider is chosen — the same posture as `UnconfiguredLlmClient` |
| `au.crewcomp.platform.dev` | Development-only fixtures, removed from a production build |
| `au.crewcomp.mcp` | §13.1 MCP tools, token authentication, environment gating |
| `au.crewcomp.platform` | `security/` (actor, roles, `AccessPolicy`, `ScopeGuard`), `audit/`, `time/`, `adapters/`, `persistence/`, `config/` (ADM-10's `app_config` store), `jobs/` (the scheduler and its health view) |

`reference / rules / people / workflow / evidence / sync` are the approved extraction seams
(§13, §17.5) — keep them package-separated. `sync` and `evidence` are the mobile-facing surface
the client's own decomposition example names (§17.5): if anything is extracted first, it is
these.

## Non-negotiables for this component

- **Engine semantics come from spec §5, verbatim.** `src/test/kotlin/au/crewcomp/engine/` is the
  executable statement of them (DEV-2); changing a test there means changing the spec.
- **The engine stays pure.** It takes values and returns values. That is what lets its suite run
  without a database, and what lets the API, the MCP tools and a later AI assist (§17.1) all be
  consumers of one implementation.
- **MCP tools call the identical service layer as REST** (MCP-2). A tool that touches a repository
  — let alone the database — is a defect, not an optimisation.
- **Row-scoping goes through `ScopeGuard`, in one place** (AUTH-2). Note the hole it cannot close:
  a load by primary key is not reachable by any query predicate, so anything fetched that way
  needs an explicit `assertVisible`. See the RLS follow-up below.
- **Every business mutation writes an AuditEvent** in the same transaction (AUTH-3, ADR 0007).
- **Flyway owns the schema.** Hibernate is `validate`-only. Migrations forward-only,
  expand/contract, and must run against the previous application revision.
- **No cloud SDK outside `infra/`** until ADR 0005 resolves — everything goes through
  `platform/adapters`.
- **A crew member's device may not write compliance data.** §7.5 allows three client-originated
  writes and no fourth: an evidence submission, a read-mark, and a **crew statement** — what the
  person *said* ("course booked", "I need help"). Nothing in `au.crewcomp.engine` reads
  `crew_statement`, no cell state moves because of one, and the only consequence any of them has is
  to make a notification *stop* (see `CrewStatementService`). Adding a fourth, or letting a
  statement reach the engine, breaks AUTH-1 at its most load-bearing point: the crew member would be
  grading their own compliance.
- **A timestamp on a legal record is the server's, and so is its formatting.** MOB-9's attestation
  takes no time from the device — a declaration timestamped from the phone of the person making it
  is an assertion by the party it is evidence against. `AttestationSyncDto.signedAtDisplay` is also
  rendered here, in `BusinessClock.zone`, for the same reason `serverToday` is sent rather than
  computed: a phone knows neither the operating timezone (O-11) nor the admin date override.
- **A client-originated write that silences something must be reversible by the office.** A
  `course_booked` statement suppresses the crew-facing expiry warning on the crew member's word
  alone; ADM-11's dismissal is what puts it back. The general rule: if a device can turn a warning
  off, a human has to be able to turn it back on, and the queue that lets them is part of the
  feature rather than a follow-up to it.
- **What earns that silence is stated once, as data.** `CrewStatementKind.suppression` is
  `NEVER` / `ON_WORD` / `ON_ACTION`, and the expiry scan's query reads it rather than naming kinds.
  The middle case is the design: "I have booked the course" is a fact only the crew member knows,
  so they are believed until contradicted; "I would like that seat" is an unanswered ask, so it
  earns nothing until a coordinator actions it. A fourth kind that got the wrong one of those would
  quietly stop chasing somebody whose certificate was still lapsing.
- **A supervisor's watch is narrower than their scope, and one definition serves both ends.** A
  Vessel Master reads their whole partnership; MOB-11's watch is only the crew co-assigned to their
  swing, and `TeamService.supervisedCrew` is what both the list *and* the nudge authorise against.
  A supervisor who could nudge anyone in their partnership would have a wider write than read.
- **Dates are calendar dates** (NFR-5). `BusinessClock` is the only source of "today", so the
  admin date override has exactly one place to take effect. `GET /api/v1/session` hands it to
  clients, because a browser cannot know the operating timezone.
- **Development-only code is removed at build time, not disabled at runtime.** The auth shim and
  the data fixture are both `@IfBuildProperty`, false everywhere but `%dev`/`%test`, so a
  production artefact does not contain them. Each *also* fails start-up if enabled under `prod`.
  A runtime flag would be one config override away from a header-authenticated production.

## Known follow-ups

1. **Postgres row-level security for AUTH-2.** `ScopeGuard` enforces scoping in application code,
   which the spec permits ("policy layer *or* DB row-level security"). RLS is stronger — it also
   covers by-id loads and any future raw SQL — and is the recommended P1 hardening. Deliberately
   not written blind: it needs a real Postgres to verify, so it belongs in the spike.
2. **Native-image build still unverified** (no GraalVM locally). ADR 0001 makes this a CI job on
   every merge; reflection registration for the entity and DTO graph is the expected first
   failure. Two new things to expect there: the `jsonb` mappings (`app_config.value`,
   `evidence_document.extraction`) go through a Jackson `FormatMapper` and will want reflection
   registration, and `quarkus-scheduler`'s cron expressions are resolved at build time.
3. **No LLM provider (§14.5).** `UnconfiguredLlmClient` is the default and returns nothing at zero
   confidence, so every document reaches ADM-9's queue and a Data Steward types the fields. That is
   LLM-2's stated launch posture rather than a gap — and note the safety property it produces: zero
   confidence plus a threshold that refuses to be set to 0 means **no configuration of this system
   lets an unconfigured extractor write a holding**. `DevTextPatternLlmClient` (dev/test only,
   build-time removed) reads labelled ASCII so stages 3–5 are exercisable without a provider.
   Choosing a provider is an adapter implementation and a prompt; nothing else moves.
4. **No push delivery** (APNs/FCM, MOB-3). The in-app record is the source of truth and the
   `notification_delivery` table is ready for per-channel records; the unified sender is the mobile
   spike's. Email is in the same position.
5. **Seed/migration loading** (§11) is half-built. `ExtractedSeedLoader` ports every POC fix-up
   rule (`build_real_seed.py`) and loads the extracts into the real schema, with each anomaly an
   ADM-7 `ExceptionItem` — that part is done, tested against a fictional fixture in
   `ExtractedSeedIT`, and usable via the dataset switch below. Still owed to §11 proper:
   re-runnability against a populated database (today it only seeds an empty one) and the
   acceptance test — a CC24/CC25 diff against the POC rendering, including the known UNI CC24
   shortfall, which `SwingEvaluatorTest` encodes only as a synthetic scenario.
6. **No quota- or conditional-rule editor.** ADM-3 edits requirement-rule *cells*; the quota and
   conditional rules behind a footnote are read-only on screen. That is deliberate rather than
   unfinished: a footnote's meaning is a `quota_rule` row and its appearance is a level in the
   grid, so editing one without the other would let a cell read `M9` with no `M9` quota behind it —
   which evaluates as an ordinary footnote and silently stops being a quota. They move together, in
   the pass that resolves O-7.
7. **The register's list has no free-text predicate.** `search` filters by partnership, CC, type
   and state; §6 also asks for free text, which the SPA currently does client-side over the rows
   it fetched. That is fine now and wrong at 445 rows plus years of growth.
8. **Login and logout do not exist.** `GET /api/v1/session` is the half of the ADR 0003 contract
   that does not depend on how the session was established; the code flow, the token store and
   the opaque cookie are the identity spike's.
9. **Back-office accounts are transitional.** §9's per-role fan-out needs a `UserAccount` to address
   a row to, and ADR 0003 creates a corporate one at first sign-in. Until then `UserAdminService`
   creates SEC-1b `local_test` accounts, **refused under `prod`** — so in production the fan-out
   correctly finds no recipients and every write path carries on regardless. `NotificationService`
   also carries a dev-only *role proxy* for reads (an actor with no account reads the rows addressed
   to accounts holding their roles); it is unreachable in production because `ActorResolver` refuses
   an identity with no account, and it is gated so a **crew** actor never takes it — see the trap
   below. Both disappear with the identity spike.
10. **Job history is in memory.** `InMemoryTaskScheduler` remembers each job's last run per
    instance, lost on restart. Enough to answer "is the expiry scan running?" on a single instance
    and nothing more; a durable history is the platform's monitoring (NFR-6). `JobHealth` says so
    on screen rather than presenting a table that looks authoritative.

## Sync mechanics worth knowing before touching them

`updated_seq` and `sync_tombstone` are maintained by **database triggers** (V2), not by
application code, and ADR 0009 §2 explains why: a cursor a write path can forget is a cursor that
silently stops replicating a row to a device, with no error anywhere. Consequences:

- Entities map `updated_seq` **read-only** (`insertable = false, updatable = false`). Its value
  on a just-persisted instance is stale until refreshed; nothing should read it outside a sync
  query.
- `sync_seq` is declared `cache 1`. A cached sequence hands out per-session blocks, which would
  let a later commit take a lower number than an earlier one and fall permanently behind a
  client's cursor.
- `FixtureSeeder.clear()` deletes `SyncTombstone` **last**, because every delete above it fires
  the tombstone triggers.
- The snapshot reads its cursor *before* the rows, in the same transaction, so the cursor can
  never be ahead of what the client actually received.

## Three advisory locks, and why they are separate

Postgres advisory locks are a flat global namespace keyed by a `bigint`, so two unrelated uses of
one number serialise against each other for no reason. There are three, and they must stay distinct:

- `AuditWriter.ADVISORY_LOCK_KEY` serialises audit appends so `seq` follows commit order (ADR
  0007). One lock for the whole trail; that is the point.
- `RegisterRecordRepository.lockPrefix` serialises business-key allocation for one `{PT}{CCnn}-`
  prefix, because "read the highest, add one" is a race between two coordinators raising a request
  for the same swing at the same instant (§4.4 wants the key monotonic per prefix). Keyed per
  prefix rather than globally, so raising a UNI CC24 request does not wait behind a NOR CC25 one.

- `MatrixService.PUBLISH_ADVISORY_LOCK_KEY` serialises matrix publication, so two simultaneous
  publishes cannot both supersede the same predecessor and leave two versions published. "Exactly
  one current published version" is a service-layer invariant; the schema's index only orders
  defensively.

Both are `pg_advisory_xact_lock`, released on commit — there is no unlock to forget, and a
rollback cannot strand one. A fourth use needs a namespace of its own, not a borrowed constant.

## Local development

Three dev-only beans make the stack runnable without a corporate IdP, a data load or an LLM
provider. All three are `@IfBuildProperty` and therefore **absent from a production artefact**
rather than disabled in it:

- **`DevAuth`** — a `HttpAuthenticationMechanism` that trusts `X-Dev-Roles` (plus optional
  `X-Dev-User`, `X-Dev-Person-Id`, `X-Dev-Partnerships`) and attaches a ready-made `Actor` as a
  `SecurityIdentity` attribute, which `ActorResolutionFilter` honours. It asks for no password on
  purpose: a shim with a fake credential can be mistaken for authentication, one that plainly
  trusts a header cannot. Replaced by the BFF session in the identity spike (ADR 0003).
- **`DevDataSeeder`** — now a two-dataset switch (`crewcomp.dev-seed.dataset`):

  - **`synthetic`** (default) — invented crew, vessels, a published matrix and two swings, shaped
    so every roll-up state appears on one screen (details below). Safe for any audience.
  - **`extracted`** — the POC's workbook extracts, loaded by `ExtractedSeedLoader` from
    `crewcomp.dev-seed.extract-root` (the POC checkout, `~/shipping` by convention — **real crew
    data, never committed here**). For demonstrations to the people who know the data: 5
    partnerships, the real 55-code catalogue, both matrix template versions with the M8 CoC
    one-of set, 1,155 holdings, the CC18–CC25 rosters, all 267 register rows normalised into
    Appendix A's vocabulary, and every fix-up flagged on ADM-7's worklist. Crew accounts are
    created per active person (SEC-1b `local_test`, no email) and Masters get a
    partnership-scoped `vessel_master`, so the crew app and §9 scans work against it. No course
    catalogue, no evidence documents: nothing fictional is mixed into the real set.

  The two catalogues must never mix (§11), and the empty-database guard enforces it: switching
  datasets means `dev-stop.sh` (Ryuk reaps the database) then
  `dev-start.sh --dataset extracted|synthetic`. A mismatch between the configured dataset and
  what a non-empty database holds is detected by matrix label and logged, not "fixed".

  The synthetic dataset is shaped so that
  every roll-up state appears on one screen: `ok`, `expiring`, `gap`, `unknown`, `quota_only`,
  `recommended`, an open slot, a mid-swing handover, and an M9 quota short on shift 2. It also seeds
  **one back-office account per role**, because §9's fan-out addresses a row to each account holding
  a role and with none the notifications centre is a correctly-empty screen that cannot be seen to
  work; and **three evidence documents** in the three shapes a reviewer meets — a clean extraction to
  confirm, a low-confidence one to correct, and one nothing could be matched to. The documents carry
  no stored bytes on purpose: ADM-9 renders "no document stored" rather than a broken image, and a
  fabricated certificate image is the one artefact nobody should be able to mistake for a real one.
  It refuses to run against a database that already holds people. **It is not the §11 migration** —
  no fix-up rules, no CC24/CC25 acceptance diff.
- **`DevTextPatternLlmClient`** — reads labelled ASCII (`Expiry date: 2027-03-01`) out of the
  uploaded bytes, so §8 stages 3–5 are exercisable without a provider, a bill or a network call. A
  genuine deterministic extractor rather than a fabricator: it finds the label or reports nothing,
  and it drops an ambiguous date rather than guessing, because `03/04/2027` is either March or April
  depending on where the certificate was printed and a pipeline that picks one is how a medical
  silently expires eleven months early.

The test profile enables the shim with **no** default roles, so a test that does not name them is
anonymous — which is how `ApiIT` asserts that endpoints really are protected. The dev profile
defaults to the four back-office roles so `quarkus dev` works out of the box.

The test profile also sets **`quarkus.scheduler.enabled=false`**. A background sweep firing
mid-assertion would make the evidence tests flaky in the worst way — passing locally and failing on a
slower CI runner — so the ITs trigger the jobs explicitly through `/administration/jobs/{name}/run`
instead. That endpoint and the scheduler run the same registered body, so triggering one by hand
exercises exactly what the clock would.

## Traps this codebase has already paid for

Defects this codebase has actually hit. Most surfaced only against a real database over real HTTP
— they compiled cleanly, read correctly, and no unit test could have seen them. The one that did
fail to compile is here because it very nearly did not.

- **A payload's whole set is a shape the client's fixtures have to know about.** Adding
  `courseOptions` to the snapshot and delta broke nine Flutter tests with
  `type 'Null' is not a subtype of type 'List<dynamic>'` — their stub payloads predated the field.
  That is the *good* failure mode, and the reason to add non-nullable arrays rather than nullable
  ones: the same omission behind a nullable field is a screen that silently shows nothing.
- **A new endpoint called from `sync()` reaches every test that stubs the client.** `/me/team` is
  fetched on every sync, so twenty inline `MockClient` handlers that asserted on a path started
  seeing a request they were never written for. One passthrough client answering that path with a
  403 fixed all of them — and the 403 is the production answer too, so the stub is not a fiction.
- **Enumerated columns are queried by their `*Value` field.** `Person.status` is a Kotlin
  property over the mapped `statusValue`; only the field is a JPA attribute. HQL naming the
  property compiles and then fails at runtime with "could not interpret path expression".
- **A scoped read must fetch-join whatever the DTO mapping touches.** Mapping happens after the
  service transaction closes, so a lazy association reached there throws
  `LazyInitializationException` — in production, on a screen, never in a test that has no session.
  The register module paid for this twice more, in two shapes the original note did not cover:
  - **A newly created entity is assembled from proxies too.** `RegisterService.create` set
    `position = person.position`, which is a lazy proxy even though nothing was "loaded" — and
    `RegisterRecordDto` reads `position.name`. A mutation that returns an entity has to re-read it
    through the fetch-joining query before returning, which is what `create` now does.
  - **The list query and the detail query must both cover the whole DTO.** `searchScoped`
    fetch-joined person, requirement, partnership and crew change but not position, so the detail
    endpoint worked and the list 500'd on the same mapping function. If two queries feed one DTO,
    they need the same fetch set.
- **Fetch-joining two collections in one query is a cartesian product.** Hibernate will build it
  and de-duplicate in memory: three notes × two conditions × five trail rows is thirty rows off
  the wire. `detailByRecordId` issues one query per collection instead — and then **touches
  `.size` on each**, because loading the children into the persistence context is not enough on
  its own. A collection is only marked initialised when something reads it, and the read resolves
  from the context rather than causing another round trip.
- **JAX-RS picks one root resource class by path prefix**, then matches sub-paths only within it.
  Once `PeopleResource` is rooted at `/api/v1/people`, a `/people/{id}/evaluation` method on a
  class rooted at `/api/v1` is unreachable and answers 404 with no start-up warning. The
  *converse* is fine and the API relies on it: five resource classes sit at exactly `/api/v1` and
  coexist. It is the longer prefix that swallows everything beneath it, which is why the register,
  exception and assignment resources are all rooted at `/api/v1` rather than at their own paths.
- **A local name shadows the receiver's property inside `apply`.** In
  `ApprovalCondition().apply { type = conditionType }`, `type` resolved to the *enclosing
  function's* `type: RegisterType` parameter, not to the condition's own property — Kotlin
  resolves a simple name against enclosing locals before an implicit receiver's members. It failed
  to compile here ("'val' cannot be reassigned"), which was luck: had the outer name been a `var`
  of a compatible type it would have compiled and assigned the wrong thing. Inside `apply`, write
  `this.x =` whenever the enclosing scope has a name that could collide. The same rule bites for
  functions — a private member named `require(id: Long)` sits beside `kotlin.require(Boolean)`
  and is a coin toss for the next reader.
- **An `@ElementCollection` must map every non-null column of its collection table.**
  `user_account_role` carries `granted_by not null` (AUTH-4), but the mapping named only `role`,
  so *no* role could ever be assigned — a constraint violation on the first insert. It compiled
  and shipped because nothing had assigned a role until the crew-account fixture did. It is now
  an `@Embeddable` (`RoleAssignment`) carrying the grant metadata.
- **A `@Transactional` method called from inside the same class is self-invoked**, bypassing the
  interceptor and running with no transaction. Test helpers that need one go on an injected bean
  (`FixtureSeeder`), not on the test class. This is why the evidence pipeline is **two** beans:
  `EvidencePipeline` orchestrates and `ExtractionStages` holds the transactional halves, because the
  model call must happen with no transaction open (LLM-5 budgets minutes) and a private call would
  have silently run the database work outside one too. `NotificationService.raiseForRoles` and
  `EvidenceReviewService.content` avoid the same shape by calling a private helper rather than a
  sibling `@Transactional` method — in both cases it *would* have worked, because a caller had
  already opened a transaction, which is exactly what makes the pattern a trap.
- **A role-proxy read must be gated on the actor not being crew.** `NotificationService` lets an
  actor with no `UserAccount` read the notifications addressed to accounts holding their roles, so
  ADM-8 works under the development shim. The first version gated on nothing — and a crew member
  whose Person record has no account yet holds `crew_member`, so the fallback handed them **every
  other crew member's notifications**. An IT caught it. A crew-shaped actor now reads their own
  account's rows or nothing at all, and the fallback drops `CREW_MEMBER` from the roles it queries.
  The general lesson: a fallback written for one class of actor has to *exclude* the others by name,
  because "has no account" was true of both.
- **Quarkus refuses to start with an unconfigured `jsonb` mapping, and it is right to.** Adding the
  first `@JdbcTypeCode(SqlTypes.JSON)` column fails start-up with a message about the application's
  REST `ObjectMapper` being customised (MCP registers a customiser; `write-dates-as-timestamps` is
  off). Sharing it would mean a change to how the API renders JSON changes how the database stores
  it. `DatabaseJsonFormatMapper` is a `@JsonFormat @PersistenceUnitExtension` bean with a plain
  `ObjectMapper` of its own — and note what it deliberately does not touch: `audit_event`'s
  before/after states and `extraction_raw` are `text`, written by explicit `writeValueAsString`, and
  hashed byte-for-byte. Those must never be routed through a mapper that might normalise them.
- **Map `jsonb` to a `Map`, not to a `String`.** Hibernate's JSON support serialises through the
  format mapper, so a `String` attribute would be JSON-*encoded* into the column — quotes and all.
  `app_config.value` therefore wraps its payload in a single-member object (`{"value": 90}`), which
  also means a setting that grows from a number into an object changes what is inside `value` rather
  than the row's shape.
- **`FixtureSeeder.clear()` has to delete the new global tables too.** `AppConfigEntry` and
  `IdentityProvider` are global mutable state, and a leaked row changes what a *later* test does
  rather than failing the test that left it — an auto-accept threshold set by one IT silently
  changes what every subsequent pipeline decides. Both are now in `clear()`; a test that sets one
  should still reset it, but the fixture no longer depends on that.

## Audit byte-fidelity — do not "tidy" these

The hash chain (ADR 0007) is computed over the exact stored bytes, so anything that silently
reformats a payload breaks every verification. The first run of `ComplianceIT` against real
Postgres caught two of these, and both look like improvements if you meet them cold:

- **`audit_event.before_state` / `after_state` are `text`, not `jsonb`.** `jsonb` reorders object
  keys and reformats whitespace, so a payload does not round-trip. Same for
  `evidence_document.extraction_raw`, which is the verbatim model response. (`extraction` stays
  `jsonb` — it is structured data to query, not bytes to attest.)
- **`AuditEvent.occurredAt` is truncated to microseconds** on write and again in the canonical
  form. `Instant.now()` carries nanoseconds that `timestamptz` cannot store.

A third trap, unrelated to hashing but the same flavour: **a default argument on a CDI bean method
must be a constant.** Kotlin compiles defaults into a static `foo$default` bridge that reads
instance fields directly, bypassing the client proxy, so a default like
`actor: Actor = actorContext.require()` resolves against a proxy whose fields are all null.

## Acknowledged conflicts, not blocked ones

Two write paths refuse an operation the first time and accept it on a retry that carries an
acknowledgement, answering 409 in between: [`AssignmentClashException`] when a person is already
committed in the window, and [`LateSubmissionException`] when a register record is lodged after
the swing's cutoff (Q17). Both follow the same argument, and it is worth keeping when adding a
third: the operation may well be what the user intends, so blocking it outright would be wrong —
but doing it *silently* loses the fact that anyone noticed. The acknowledgement goes into the
audit event, and for the register onto the record itself as `late_submission_acknowledged`.

Do not "simplify" either into a plain validation failure. A 400 says "you cannot do this", and
both of these are cases where you can.

## Spec questions this code has an opinion on

Flagged where the implementation had to choose something the spec leaves open. Each is a comment
at the relevant code, and each is cheap to change:

- **§5.1 precedence, tier vs exemption.** A tier-footnote conflict returns `review` *before* the
  exemption overlay runs, so an approved exemption does not clear a tier conflict. This follows
  the spec's stated precedence order and Q6's "never auto-resolved" — worth confirming with the
  client, since the opposite reading is defensible.
- **§5.1 step 2, quota-only cells.** A `quota_only` cell is not overlaid by an exemption: the cell
  was never individually mandatory, so there is nothing to exempt. `unknown` and `expiring` under
  a quota footnote *do* still take the overlay.
- **Tier policy (O-3).** Unresolved, so the default is conservative: a tier-footnote cell goes to
  `review` unless the matrix version's tier policy explicitly clears the person's tier for the
  partnership's vessel class. Absent policy means review, never silent pass.
- **§5.2 roll-up.** `na`, `quota_only` and `recommended` are absent from the spec's severity list;
  they rank below `ok`, and an all-`na` person rolls up to `ok`.
- **§5.3 open slots.** Partially covered slots are reported separately from open ones — the input
  ENG-1's per-day evaluation will need, and a real planning signal now.
- **§4.4 register transitions.** The spec enumerates the statuses but not the graph between them.
  `RegisterService.defaultStatusFor` encodes the reading the names imply — PW raises and holds its
  own requests, an MRL query starts with MRL, an OPS request starts with OPS — and `transition`
  allows any open→open move rather than a fixed chain. A closed record is never reopened; a new
  one is raised. All three are one map or one `require` away from changing.
- **§4.1 catalogue codes are immutable.** `updateRequirement` edits everything except the code,
  because the code is the business key every matrix rule, register row and CSV export is written
  against; renaming it in place would rewrite history rather than record a change. A miscoded
  entry is retired and replaced. Retirement itself is permitted whatever the usage counts say —
  the API reports them so the decision is informed, and retired entries still resolve.
- **§5.5 publication is the Compliance Lead's alone.** The spec says "restricted to the Compliance
  Lead role pending Q3", read literally: `MatrixService.publish` refuses even a System Administrator,
  where every other write path here accepts one. That is separation of duties on the one act that
  changes every compliance answer at once — but it is one `require` away from changing, and it is
  worth confirming, because it also means a locked-out deployment cannot publish its first matrix
  without granting the role.
- **A blank level is only meaningful as a partnership override.** `setCell` refuses a blank on a base
  rule: an absent base rule already means "not required", so a blank base row says nothing and would
  show up in a §5.5 diff as a change when nothing changed. On an *override* a blank is a positive
  statement — "this partnership does not require it" — which is why clearing an override is a
  separate operation from blanking it, and why the diff renders the two differently.
- **§8 stage 4's "low-risk" is read strictly.** Auto-acceptance requires an existing `held_expiry`
  holding for the same requirement whose expiry the document *extends*. A first-ever grant, a
  different holding status, and a date that moves backwards all go to a human — the last most of all,
  because a superseded or mis-scanned certificate looks exactly like it. The issue date is
  corroborating rather than critical: a holding is valid without one but never without an expiry, so
  a low-confidence issue date is dropped rather than blocking the acceptance.
- **A decided evidence document is terminal.** `verified` and `rejected` are not reopened; a holding
  recorded in error is corrected on the person's holdings, where the change is audited as what it is.
  `auto_accepted` *is* still decidable, deliberately — that is what §8's spot-check list is for.
- **Accept and correct are one operation.** `EvidenceReviewService.accept` takes the values the
  reviewer decided, and the audit event carries both those and what was extracted, so a correction is
  visible as one (`evidence.corrected` rather than `evidence.verified`). Measuring that gap is what
  LLM-2 needs before auto-acceptance can be enabled, and a separate "correct" endpoint would have
  made it invisible. An extraction that read *nothing* is not counted as corrected, or every
  acceptance would be one while no provider is configured.
- **Configuration holds policy; cadence is deployment config.** `app_config` holds lead days,
  thresholds and weights. Cron expressions live in `application.properties`, because
  `quarkus-scheduler` resolves them at start-up and a cron in the database would be a setting the
  scheduler never reads — editable on screen and completely inert. ADM-10 shows the schedules
  read-only beside a "run now" button, which is the control an operator actually reaches for.
- **The auto-accept threshold refuses 0.** A threshold of zero would accept every extraction
  unconditionally, which is not "auto-accept enabled" but "review disabled". Clearing the setting is
  how "always review" is expressed. If the other thing is ever genuinely wanted it should be a
  separate, named, loudly audited setting rather than an edge case of this one.
- **The last System Administrator cannot be revoked or suspended.** Not paternalism about a mistake,
  but the one mistake nothing inside the application can undo: with no administrator left, nothing
  can grant the role back and the remedy is hand-written SQL against production.
