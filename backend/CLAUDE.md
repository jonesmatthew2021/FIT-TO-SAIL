# backend/ — Kotlin + Quarkus monolith

The P1 spine exists: the §5 compliance engine with its test suite, the §4 baseline schema, the
persistence and security layers, the compliance service, the REST API, and the embedded MCP
server. On top of it: the assignment write path (ADM-2), the register workflow (ADM-4), the
catalogue write path (ADM-6), the exceptions worklist (ADM-7), §10.3 sync and §8 stage 1 evidence
ingest. The matrix versioning service (ADM-3) and the rest of the evidence pipeline are the
substantial modules still missing.

## Stack (decided)

Kotlin 2.3, Quarkus 3.33 LTS (`io.quarkus.platform:quarkus-bom:3.33.2` — the latest 3.33 on
Central), PostgreSQL ≥ 15. Extensions: quarkus-rest-jackson, quarkus-hibernate-orm-panache-kotlin
(ADR 0006), quarkus-jdbc-postgresql, quarkus-flyway, quarkus-oidc (ADR 0003),
quarkus-mcp-server-http 1.13.1, quarkus-smallrye-openapi, quarkus-hibernate-validator,
quarkus-smallrye-health, quarkus-micrometer-registry-prometheus. GraalVM native image for
deployment; JVM mode for dev.

JobRunr is **not** in the build yet: it wants to own its own tables, and nothing schedules work
until the notification scan (P2). It lands with the first background job, its tables created by a
Flyway migration rather than by JobRunr itself.

## Build and test

```bash
./mvnw test                        # 125 pure-domain tests — no Docker needed
./mvnw verify                      # + package; ITs skipped by default
./mvnw verify -DskipITs=false      # + 52 integration tests — needs a container runtime
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
(MCP-4, below).

## Layout

| Package | Contents |
|---|---|
| `au.crewcomp.engine` | **The §5 engine. Pure Kotlin — no CDI, no JPA, no framework types.** Cell/person/swing evaluation, rule resolution, quotas, suggestions, gap report, expiry alerts, matrix diff. |
| `au.crewcomp.reference` | §4.1 partnerships, vessels, positions, slots, crew changes, requirements |
| `au.crewcomp.rules` | §4.2 matrix versions, requirement/conditional/quota rules |
| `au.crewcomp.people` | §4.3 people, user accounts, identity providers, holdings, assignments, leave |
| `au.crewcomp.workflow` | §4.4 register records, conditions, notes, exception items |
| `au.crewcomp.compliance` | Application service around the engine: entity↔engine mapping, matrix snapshots, `ComplianceService` |
| `au.crewcomp.sync` | §10.3 mobile sync: delta reads, tombstones, `SyncService`. Takes no person id anywhere — it answers for the authenticated crew member only |
| `au.crewcomp.evidence` | §8 stage 1 / MOB-4: submission, and the MOB-5a resumable chunk upload |
| `au.crewcomp.notify` | §9 notifications. The in-app record is the source of truth; push and email are channels for it |
| `au.crewcomp.api` | JAX-RS resources, DTOs, exception mappers |
| `au.crewcomp.platform.dev` | Development-only fixtures, removed from a production build |
| `au.crewcomp.mcp` | §13.1 MCP tools, token authentication, environment gating |
| `au.crewcomp.platform` | `security/` (actor, roles, `AccessPolicy`, `ScopeGuard`), `audit/`, `time/`, `adapters/`, `persistence/` |

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
   failure.
3. **The evidence pipeline stops after ingest.** §8 stage 1 (upload, store, record) is built and
   tested; extraction, matching and the review queue (ADM-9) are not, and `LlmClient` has no
   implementation. A submitted document sits at `pending_extraction` forever.
4. **Nothing schedules a notification.** `NotificationService.raise` is now called by the
   assignment write path (`assignment_added` / `assignment_removed`), so the crew app's list is
   no longer fed only by the fixture — but the §9 expiry scan and cutoff-approaching job still do
   not exist, and they are what JobRunr lands for. No push delivery either (APNs/FCM, MOB-3). A
   back-office user cannot be notified at all: notifications are addressed to a `UserAccount`, and
   back-office users have none until the identity spike creates them (that is ADM-8's real
   blocker).
5. **Seed/migration loading** (§11, from the POC's seed CSVs) is not written. The acceptance
   test is a CC24/CC25 diff against the POC rendering, including the known UNI CC24 shortfall —
   which `SwingEvaluatorTest` already encodes as a synthetic scenario. `DevDataSeeder` is a
   development fixture, not a substitute for this.
6. **No matrix versioning service** (ADM-3), the largest remaining module: version list, draft
   creation from any version, cell editing, the §5.5 diff (`MatrixDiff` already implements it) and
   publication. `MatrixSnapshotService` reads the published version; nothing writes one.
7. **The register's list has no free-text predicate.** `search` filters by partnership, CC, type
   and state; §6 also asks for free text, which the SPA currently does client-side over the rows
   it fetched. That is fine now and wrong at 445 rows plus years of growth.
8. **Login and logout do not exist.** `GET /api/v1/session` is the half of the ADR 0003 contract
   that does not depend on how the session was established; the code flow, the token store and
   the opaque cookie are the identity spike's.

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

## Local development

Two dev-only beans in `platform/dev/` and `platform/security/dev/` make the stack runnable
without a corporate IdP or a data load:

- **`DevAuth`** — a `HttpAuthenticationMechanism` that trusts `X-Dev-Roles` (plus optional
  `X-Dev-User`, `X-Dev-Person-Id`, `X-Dev-Partnerships`) and attaches a ready-made `Actor` as a
  `SecurityIdentity` attribute, which `ActorResolutionFilter` honours. It asks for no password on
  purpose: a shim with a fake credential can be mistaken for authentication, one that plainly
  trusts a header cannot. Replaced by the BFF session in the identity spike (ADR 0003).
- **`DevDataSeeder`** — invented crew, vessels, a published matrix and two swings, shaped so that
  every roll-up state appears on one screen: `ok`, `expiring`, `gap`, `unknown`, `quota_only`,
  `recommended`, an open slot, a mid-swing handover, and an M9 quota short on shift 2. It refuses
  to run against a database that already holds people. **It is not the §11 migration** — no
  ExceptionItems, no fix-up rules, no CC24/CC25 acceptance diff.

The test profile enables the shim with **no** default roles, so a test that does not name them is
anonymous — which is how `ApiIT` asserts that endpoints really are protected. The dev profile
defaults to the four back-office roles so `quarkus dev` works out of the box.

## Traps this codebase has already paid for

Three defects the first API-level integration run caught, all invisible to a unit test:

- **Enumerated columns are queried by their `*Value` field.** `Person.status` is a Kotlin
  property over the mapped `statusValue`; only the field is a JPA attribute. HQL naming the
  property compiles and then fails at runtime with "could not interpret path expression".
- **A scoped read must fetch-join whatever the DTO mapping touches.** Mapping happens after the
  service transaction closes, so a lazy association reached there throws
  `LazyInitializationException` — in production, on a screen, never in a test that has no session.
- **JAX-RS picks one root resource class by path prefix**, then matches sub-paths only within it.
  Once `PeopleResource` is rooted at `/api/v1/people`, a `/people/{id}/evaluation` method on a
  class rooted at `/api/v1` is unreachable and answers 404 with no start-up warning.
- **An `@ElementCollection` must map every non-null column of its collection table.**
  `user_account_role` carries `granted_by not null` (AUTH-4), but the mapping named only `role`,
  so *no* role could ever be assigned — a constraint violation on the first insert. It compiled
  and shipped because nothing had assigned a role until the crew-account fixture did. It is now
  an `@Embeddable` (`RoleAssignment`) carrying the grant metadata.
- **A `@Transactional` method called from inside the same class is self-invoked**, bypassing the
  interceptor and running with no transaction. Test helpers that need one go on an injected bean
  (`FixtureSeeder`), not on the test class.

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
