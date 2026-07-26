# CREWCOMP — Production Specification (V2 draft)

**Status:** Draft for cooperative review — deployment, language and framework selection to be researched together against §14.
**Supersedes:** the V1 proof-of-concept (this repo's `app/`) as a product definition. The POC remains the behavioural reference implementation.
**Inputs:** `crew-compliance-migration-plan.md` (rev 3, stakeholder decisions Q1–Q25), the delivered V1 POC (Phases 0–3), client feedback from the POC validation, the additional requirements of 25 Jul 2026 (mobile apps, rich admin interface, monolithic backend), and the forward requirements of 26 Jul 2026 (AI assistance throughout, one-person DevOps, AI-assisted development pipeline, external audit trail, architecture flexibility — §17).

---

## 1. Background and POC outcomes

The V1 proof-of-concept replaced two forked Excel workbooks (exemption/query register + crew qualification expiry grid) with a working single-page application covering Phases 0–3 of the migration plan: normalised reference data, a versioned requirements matrix with machine-checked conditional/quota rules, whole-swing compliance evaluation, the full 445-row exemption register with workflow, and manual swing planning with ranked crew suggestions. It was validated end-to-end against the real workbook extracts by the client and accepted as a correct model of the domain.

**What the POC proved (carry forward as settled):**

- The domain model in migration-plan §4 is right: rules + holdings + assignments + exemptions → derived compliance, rather than hand-maintained grids.
- The compliance evaluation semantics (§5 below) match how the client reasons about the work, including the awkward parts: quota-only footnotes, one-of sets, mandatory-if-applicable → human review, whole-swing validity, mid-swing expiry.
- The exceptions-as-worklist approach to dirty data works: preserve anomalies, flag every fix-up, never silently clean.
- Stakeholder decisions Q1–Q25 stand except where explicitly revised in this document (see §1.1).

**What the POC deliberately did not address (now in scope):**

- Real persistence, multi-user concurrency, authentication and authorization (POC: localStorage, persona dropdown, honour-system read-only).
- Crew members as users. The POC had five back-office personas; production adds every crew member as a first-class, self-service user on their own phone.
- Phase 4 of the migration plan — document/evidence intake with OCR/LLM extraction — which the mobile requirement now pulls into core scope.
- Notifications as real push/email delivery rather than an in-app list.
- A real clock. The POC pins "today" to 2026-07-25; production evaluates against the current date (with a date-override capability retained for audit reconstruction and testing, admin-only).

### 1.1 Decisions revised by the new requirements

| Prior decision | Revision |
|---|---|
| §8 of migration plan: BaaS (Supabase) + React/Capacitor | Superseded. Backend is now a **monolithic application server + standard relational database** on a managed PaaS (§13). Frontend/mobile stack is an open research question (§14). |
| Q2: internal-only system, PW/MRL not direct users | Partially revised: still no PW/MRL organisational users, but **crew members become direct users** via the mobile app (self-service, own-data-only). |
| Q14: single workflow manager, role model deferred | Role model can no longer be deferred: mobile self-service requires a real RBAC model from day one (§3). Single workflow *manager* for register decisions still stands. |
| Q23: no privacy constraints identified | Must be revisited **before build**, not before hosting: crew personal data (identity, qualifications, medicals-adjacent evidence, roster/leave) will be processed by LLMs, cached on personal devices, and pushed over notification channels. See SEC-10..14 and open question O-9. |
| 25 Jul requirement: "monolithic backend" | Relaxed 26 Jul: the monolith is a **default, not a mandate**. Service decomposition is acceptable — welcomed — where the motivation is security or scalability (canonical example: a mobile-facing gateway holding no database credentials). §13 stands as the baseline; the revision rule is §17.5. |
| SEC-1 as first drafted: OIDC, back-office SSO "when available", crew via invite-based application accounts | Revised 26 Jul: **corporate SSO is the sole authentication path for all users**, federated across the major corporate identity providers (Microsoft Entra ID / Office 365, Google Workspace, Okta, and peers) — corporate backends only, each validated before its users can log in, no application-stored accounts or passwords. Local/test accounts are a testing/early-phase exception with a hard removal date (SEC-1..1b). |

---

## 2. System components

Three deliverable components over one backend:

- **C1 — Backend**: monolithic application server (baseline — see §13 and §17.5) + relational database. Single system of record. Owns all business rules (compliance engine, workflow, ID generation, validation), the API consumed by both frontends, the sync protocol for mobile, background jobs (expiry scanning, notification fan-out, document processing), and integrations (push notification services, LLM provider, email).
- **C2 — Admin web application** ("Phase 2 admin interface" in the client's terms): rich web app for back-office roles. Starting feature set = functional parity with the V1 POC (§6), on real auth/data.
- **C3 — Mobile applications** (iOS + Android, feature parity mandated): crew-member self-service subset — own certifications/expiries, own roster/swings/leave, push notifications, evidence submission with camera/file capture, offline-first with sync and queued writes (§7).

A single API serves C2 and C3. C3 additionally uses the sync endpoints (§7.6, §10.3). No business logic lives only in a client: the mobile app may *pre-validate* offline, but the backend re-validates every write.

---

## 3. Actors, roles and permissions

The POC personas map to production roles; crew member is new. Roles are assignable per user; one user may hold several.

| Role | Origin | Scope of access |
|---|---|---|
| **Crew Coordinator** (PW) | POC persona | Read all; write rosters/assignments, raise exemption requests, edit holdings. |
| **Workflow Manager** (OPS) | POC persona | Read all; owns register lifecycle: transitions, outcomes, conditions, notes. Sole decision authority (Q14). |
| **Compliance Lead** (OPS) | POC persona | Read all; owns requirement catalogue and matrix: drafts, edits, diffs, publication. |
| **Data Steward** (PW) | POC persona | Read all; owns exceptions worklist, holdings data quality, evidence **verification queue** (new, §8). |
| **Vessel Master** | POC persona | Read-only, scoped to their vessel pair/partnership. |
| **Crew Member** | **New** | Own records only: own profile, holdings, evidence submissions, assignments/roster, leave, notifications, and own exemption requests (read-only view of status/outcome). May submit evidence; may not edit holdings directly. |
| **System Administrator** | New | User/role management, configuration (lead-time thresholds, LLM auto-accept thresholds, notification schedules), integration credentials. No implicit access to workflow decisions. |

Authorization requirements:

- **AUTH-1** Every mutating endpoint enforces role checks server-side. The POC's `App.canEdit()` UI-gating pattern becomes defence-in-depth only.
- **AUTH-2** Crew Member access is **row-scoped**: every query executed on behalf of a crew member is filtered to their person record. This must be enforced in one central place (e.g. policy layer or DB row-level security), not per-endpoint.
- **AUTH-3** All decisions (register transitions, outcomes, matrix publication, holding edits, evidence verification) record the authenticated actor in the audit log — replacing the POC's hardcoded `who: 'Workflow manager'`.
- **AUTH-4** Role assignments themselves are audited.

---

## 4. Domain model

Normative entity catalogue. Field lists are the minimum; types shown as logical types. All entities carry `id` (system-generated surrogate key), `created_at/by`, `updated_at/by`. Legacy/manual identifiers (Sam #, register `{PT}CC{nn}-{seq}` IDs) are retained as unique business keys, never as primary keys.

### 4.1 Reference layer

- **Partnership** — `abbrev` (NOR/SIN/OPT/UNI/SMG), `name`, `vessel_class` (nullable; drives tier review rule), vessels.
- **Vessel** — `name`, `partnership`, `kind` (tug/barge). (POC derived vessels from partnership; production models them, since requirements are dimensioned by partnership × vessel per Q7.)
- **Position** — `name` (Master, Chief Officer, …).
- **PositionTier** — per position where applicable (e.g. Chief Officer Unlimited / 100m). Semantics pending Q12 (open question O-3).
- **PositionSlot** — `ref` (1–17), `shift` (Shift 1 / Shift 2 / N/A), `allowed_positions` (list — slots 15/16 accept AE **or** GPH), `notes`. Shift `N/A` means aboard the whole swing (e.g. Cook) and counts toward **both** shifts in quota evaluation (POC behaviour, confirmed).
- **CrewChange (Swing)** — `cc_id` (CCnn), `partnership`, `from`, `to`, `cutoff` (= from − 7 days per Q17; stored, not derived, so exceptions can exist). Unique per (cc_id, partnership); dates staggered weekly across partnerships.
- **Requirement** — `code` (QL-01…PI-10 scheme, canonical per Q4), `category` (QL/VS/PS/MS/CS/HR/PT/VI/PI), `title`, `status` (active/retired), `aliases` (legacy free-text titles for register history mapping), `issuing_authority` (nullable), `notes`. The catalogue's known defects (uncoded Advanced Fire Fighting, VS-03 gap) remain worklist items, not silently fixed.

### 4.2 Rules layer

- **MatrixVersion** — `label`, `status` (draft / published / superseded), `effective_from`, `published_by/at`, `notes`. Exactly one *current* published version at a time (latest `effective_from` wins); drafts are edited, diffed against any version, and published atomically (publish supersedes the prior current version). Historical versions are immutable once superseded.
- **RequirementRule** — `matrix_version`, `partnership` (`'*'` = base rule), `position`, `requirement`, `level` ∈ {`M`, footnote label `Mn`, `R`, `''`}. Resolution semantics (normative, as implemented in POC `Engine.effectiveRules`): base (`'*'`) rules apply to every partnership; a partnership-specific row for the same (position, requirement) **overrides** the base; an override with empty level **removes** the requirement for that partnership. *Granularity is per position pending Q5 (O-2); the schema must allow adding a slot/shift dimension without migration pain.*
- **ConditionalRule** — attached to a matrix version. Types:
  - `one_of` — `position`, `requirements[]`: person must hold at least one member (e.g. GPH: QL-08/09/10; Chief Officer CoC set M⁸: QL-01/02/03 with seniority supersession).
  - `dependent` — `position`, `requirement`, `required_if_holds[]`, `unless_holds[]` (e.g. COST required only if the qualification held is a non-STCW Certificate of Recognition).
- **QuotaRule** — attached to a matrix version: `footnote` (display label), `requirement`, `min`, `scope` (swing / shift), `positions[]` (nullable = any). E.g. ≥4 crew with FRC per swing; ≥1 GPH with Work at Heights per shift.
- **Footnote labels are data, not code.** The POC's two datasets proved the same label (M⁷) means different rules in different matrix versions. The engine must key behaviour off the rule records (`QuotaRule`, `tier_footnote` config on the version), never off the label text.

### 4.3 People layer

- **Person** — `sam` (legacy key, unique but with one known historical duplicate preserved as an exception), `name`, `position`, `tier` (nullable), `partnership` (home), `status` (active / lookup_only / inactive), contact details (new — required for mobile onboarding and notifications: email, mobile number).
- **UserAccount** — authentication identity, linked 1:1 to Person for crew members; back-office users may have no Person. Person records must exist without accounts (not all crew will onboard immediately). Holds only the **external identity linkage** (provider, issuer/tenant identifier, subject identifier) plus app-level role assignments — never credentials (SEC-1). A `kind` flag distinguishes corporate identities from transitional local/test accounts (SEC-1b) so the latter are enumerable and removable.
- **QualificationHolding** — `person`, `requirement`, `status` ∈ {`held_expiry`, `held_perpetual`, `not_held`, `unknown`}, `expiry` (required iff `held_expiry`), `issue_date` (nullable, new), `note`, `evidence[]` (links to EvidenceDocument, new). `unknown` is always surfaced/flagged (Q11). This system is the **system of record** for holdings (Q9).
- **EvidenceDocument** (new, §8) — `person`, uploaded file (object-storage ref), `source` (mobile_camera / mobile_file / admin_upload), `submitted_by/at`, extraction results, `verification_status` ∈ {pending_extraction, pending_review, auto_accepted, verified, rejected}, `linked_holding` (nullable until matched).
- **Assignment** — `person`, `crew_change`, `partnership`, `slot`, `from`, `to` (may be a sub-range of the swing: mid-swing handovers put two sequential people in one slot). Overlap of the same person across partnerships is a warning, not a hard block (POC "clash" behaviour).
- **LeaveRecord** (new — required by the mobile "holidays" requirement) — `person`, `type` (annual leave / …), `from`, `to`, `status`. **The source of truth for leave is an open question (O-8)**: entered by coordinators in CREWCOMP, or read-only mirror of an HR/payroll system. Until answered, spec as coordinator-entered with a status enum designed for later external sync.

### 4.4 Workflow layer

- **ExemptionRequest / Query (RegisterRecord)** — `record_id` (business key `{PT}{CCnn}-{seq}`, auto-generated, monotonic per prefix), `type` ∈ {Exemption Request - PW, Exemption Request - OPS, Exemption Request following MRL Query, MRL Query, PW Query}, `person`, `position`, `requirement` (nullable; `req_raw` preserves unmapped legacy titles, badged), `partnership`, `crew_change`, `effective_from/to` (validated inside the swing window), `raised`, `status` ∈ {Open - PW, Open - MRL, Open - OPS, Complete before joining, Closed - *outcome*}, `outcome` ∈ {Approved, Not Approved, Info Required, Admin Action, Not Required}, `approval_from/to` (required for Approved; validated inside swing window), `conditions[]`, remediation dates (`booking_confirmed`, `lodgement_date`), `notes[]` (party-attributed: PW/MRL/OPS), `audit[]`, `late_submission_acknowledged` (Q17).
- **ApprovalCondition** (structured, Q16) — `type` ∈ {supervision, time_limit, duty_restriction, training_booked, other}, `text`.
- **Notification** — `recipient` (user), `kind`, `text`, `deep_link`, `read_at`, delivery records per channel (in-app / push / email). §9.
- **ExceptionItem** (data-quality worklist) — `area`, `description`, `state` (open/resolved), `linked_entity`. Migration fix-ups, unknown holdings chase list, catalogue defects.
- **AuditEvent** — append-only, on every entity mutation of business significance: actor, timestamp, entity, event, before/after where practical. Register records additionally keep their embedded human-readable audit trail (POC behaviour, kept — it is shown to users). Events must be **self-contained, immutable records with a stable global order** (monotonic sequence), and the actor field must distinguish human / AI-proposed-human-approved / AI-automatic (AIA-3) — both properties are P1 schema decisions that let the external read-only audit mirror (§17.4) be added later without re-shaping history.

---

## 5. Compliance engine (normative semantics)

The POC engine (`app/js/engine.js`) is the reference implementation; production reimplements these semantics server-side with the same test-visible behaviour. This section is normative.

### 5.1 Cell evaluation — one (person, requirement rule, swing)

Inputs: the person's holding for the requirement, any register record for (person, requirement, swing), the rule level, the swing window `[from, to]`.

Base classification against the **whole swing window** (Q13):

| Holding | Base state |
|---|---|
| `held_perpetual`, or `held_expiry` with expiry ≥ swing.to | `ok` |
| `held_expiry` with swing.from ≤ expiry < swing.to | `expiring` (mid-swing — needs rebooking) |
| `held_expiry` with expiry < swing.from | `gap` |
| `not_held` | `gap` |
| `unknown` or no record | `unknown` |

Then, in order of precedence:

1. **Recommended (`R`)**: never a gap. `ok` if held; otherwise informational state `recommended`.
2. **Quota footnote levels** (any level named by a QuotaRule on the active matrix version): the cell is not individually mandatory. `gap` → `quota_only` (counted only at swing level); `unknown` stays `unknown`; held stays `ok`.
3. **Tier footnote** (only if the matrix version defines one): if the person's tier conflicts with the partnership's vessel class → `review` with an explanatory note (Q6: Mˣ → human review, never auto-resolved).
4. **Exemption overlay**: if base state ∈ {gap, unknown, expiring} and there is a register record for (person, requirement, this swing): outcome Approved with an approval window → `exempt`; still open → `pending`.

### 5.2 Person evaluation post-passes (conditional rules)

- **one_of**: if any member of the set is `ok`, the other members become `na`; if none is satisfied, members remain gaps (softened to `unknown` if any member is unknown — the gap may not be real).
- **dependent**: if the trigger condition doesn't hold (person holds none of `required_if_holds`, or holds something in `unless_holds`), the target requirement becomes `na`; if triggered and missing, it stays a gap with an explanatory note.

Per-person roll-up severity order: `gap` > `expiring` > `pending` > `unknown` > `review` > `exempt` > `ok`.

### 5.3 Swing evaluation

Per (crew change, partnership): evaluate every assignment (slots may hold sequential assignments — evaluate each), list open slots, aggregate state counts, and evaluate **quota rules**:

- Scope `swing`: count assigned people (optionally filtered by rule positions) whose holding is valid through swing end; compare to `min`.
- Scope `shift`: evaluate per shift; crew in `N/A`-shift slots count toward both shifts. Shortfalls are **warnings**, surfaced per shift (Q8). *(The plan also calls for per-day shortfall detection across mid-swing handovers — partially covered in the POC; production must evaluate quota satisfaction over each day of the swing where assignment sub-ranges differ. Flagged as ENG-1.)*

### 5.4 Suggestions, gap report, alerts

- **Crew suggestions** for an open slot: active people whose position fits the slot's allowed positions and who are not already assigned to this swing; ranked ascending by penalty score — reference weights from the POC: gap ×100, unknown ×10, expiring ×5, cross-partnership +3, overlapping assignment elsewhere +1000 (shown as "clash", not hidden). Weights become configuration.
- **Gap report** per swing: every non-`ok`/`na` cell, ordered gap → expiring → unknown → review → pending → exempt; each row pre-fills a new exemption request (one-click, POC behaviour kept).
- **Expiry alerts**: holdings with `held_expiry` within the configured lead window (default 90 days, configurable — Q13 follow-up pending) for active people, classified by impact on their next assignment: `expired_before_swing` / `mid_swing` / `none`. Feeds notifications (§9) and the mobile app.

### 5.5 Matrix versioning

Draft → edit → **diff** (added / removed / level-changed vs any version) → publish (supersedes current). Evaluation of a historical swing may pin a historical matrix version; default is the currently published version. Publication is restricted to the Compliance Lead role pending Q3 (O-1).

---

## 6. Functional specification — C2 Admin web application

Starting scope is **feature parity with the V1 POC**, re-based on real auth, server persistence, and multi-user concurrency. Module by module (POC route in parentheses):

- **ADM-1 Dashboard** (`dashboard`): per-partnership current/next swing cards with compliance totals, quota shortfalls, open register counts, expiry alert list, exceptions summary. Role-emphasised landing per §3.
- **ADM-2 Swing planner** (`swings`): partnership × crew-change selector scoped to that partnership's calendar; slot table with per-assignment person evaluation chips; assign via ranked suggestions (§5.4) with clash warnings; unassign; partial/handover assignments rendered as sequential rows; quota panel with per-shift status; gap report with one-click pre-filled exemption request; whole-swing CSV export.
- **ADM-3 Matrix** (`matrix`): generated matrix view per swing (partnership-resolved effective rules × roster × holdings → cell states, replacing the CC sheets); version list; draft creation from any version; cell editor (level set/clear, partnership overrides); diff view; publish with confirmation; matrix CSV export (audit-shaped — pending Q26/O-7).
- **ADM-4 Register** (`register`): filterable/searchable list (open/closed/all, partnership, CC, type, free text); detail view with facts, structured conditions, party notes, audit log; create (auto-ID preview, window validation inside swing, cutoff enforcement with audited late-acknowledgement); status transitions; close with outcome + approval window validation + structured conditions; CSV export. Full 445-row history present.
- **ADM-5 People & holdings** (`people`): person list with compliance roll-up; person detail: holdings grid by category with status/expiry editing (Data Steward / Coordinator), assignment history, register history. **New:** evidence documents per holding, account/onboarding status, leave records.
- **ADM-6 Requirements catalogue** (`requirements`): catalogue by category, status (active/retired), aliases/legacy titles, usage counts; catalogue edits restricted to Compliance Lead.
- **ADM-7 Exceptions worklist** (`exceptions`): open/resolved data-quality items with links to the affected records; resolution notes. Includes the standing "unknown holdings" chase list.
- **ADM-8 Notifications centre** (`notifications`): all notifications with read state and deep links; per-user in production (POC was global).
- **ADM-9 Evidence verification queue** (new, §8): pending LLM-extracted submissions for human verification — side-by-side document image and extracted fields, accept / correct / reject, resulting holding update audited.
- **ADM-10 Administration** (new): users/roles, configuration (expiry lead days, suggestion weights, LLM auto-accept threshold, notification schedules), integration health.

Cross-cutting: every list exports CSV; every entity view deep-links; all times/dates in the operational timezone (single-timezone assumption — confirm, O-11).

---

## 7. Functional specification — C3 Mobile applications (iOS & Android)

### 7.1 Scope and principles

Crew-member self-service **subset**. The mobile app is: my data, my notifications, my evidence uploads. It is not a planning or decision tool. iOS and Android ship with **feature parity** (MOB-0, hard requirement) — this makes a shared-codebase approach (§14.3) strongly preferred; any platform-specific divergence must be approved as an explicit exception.

### 7.2 My certifications (MOB-1)

- List of the user's qualification holdings grouped by category, with status, expiry date, and days-remaining; visual states aligned with the engine's (ok / expiring / gap / unknown).
- "What I need" view: requirements applicable to the user's position under the current published matrix (individually-mandatory items and one-of sets), with the user's current standing against each — so crew can see *upcoming certification requirements*, not just what they hold.
- Detail per holding: dates, evidence documents previously accepted, linked open exemption (status only).

### 7.3 My roster: swings, shifts, leave (MOB-2)

- Upcoming and past assignments: partnership, vessel pair, slot/position, shift, from/to dates, swing window; current swing highlighted.
- Leave/holiday records (read-only in V1 of the mobile app; request-submission is a candidate later feature — O-8).
- Personal calendar view combining assignments and leave; OS calendar export (ICS / native calendar integration) as a fast-follow.

### 7.4 Notifications (MOB-3)

Push notifications (APNs / FCM) for, at minimum:

- **Expiry warnings** for own holdings at configurable thresholds (default aligned with the 90-day lead; repeat cadence configurable, e.g. 90/30/7 days).
- **Assignment events**: assigned to / removed from a swing; assignment dates changed.
- **Upcoming requirement changes**: a newly published matrix version that adds a requirement applicable to the user's position.
- **Evidence pipeline events**: submission received, verified, or rejected (with reason).

All pushes are also recorded as in-app notifications (the source of truth); push payloads carry minimal PII (see SEC-13) and deep-link into the app. Per-user notification preferences (channel opt-outs except for safety-critical classes — policy O-10).

### 7.5 Evidence submission (MOB-4)

- Capture from **camera** (with document-edge guidance) or **file attachment** (PDF/image), multiple pages per submission.
- User optionally tags the requirement it evidences (pre-filled when launched from a specific holding's "renew" action); tagging is a hint, not binding — extraction may correct it.
- Submissions upload to the backend pipeline (§8) and are **queued offline** (§7.6): capture must work with no connectivity, with visible queue state (pending / uploading / processing / verified / rejected) and retry.
- The user sees extraction results and final outcome; a rejected submission shows the reviewer's reason and invites recapture.
- The mobile app never writes holdings directly — holdings change only via the verified pipeline or back-office edit.

### 7.6 Offline-first sync (MOB-5)

Data must be available offline; the app maintains a local store and a durable outbound queue.

- **Read model**: the user's scoped dataset (own person record, holdings, applicable requirements + published matrix subset, assignments, leave, notifications, submission statuses) is replicated to an on-device database. Estimated size is trivial (≪1 MB text + evidence thumbnails), so **full per-user snapshot + incremental delta sync** is the baseline design: each sync sends the client's high-water mark (server change cursor), receives changed/deleted rows. No peer-to-peer or partial-graph complexity is warranted.
- **Write model**: the only client-originated writes are evidence submissions, notification read-marks, and preference changes — all **commutative or idempotent by construction** (append-only submissions with client-generated UUIDs; read-marks are monotonic). This eliminates general conflict resolution: last-write-wins on preferences, server-authoritative everything else. If later features add contended writes, revisit.
- **Queue semantics**: durable across app restarts; automatic retry with backoff; explicit user-visible failure after exhaustion; uploads resumable (evidence files may be several MB on poor maritime connectivity — chunked/resumable upload required, MOB-5a).
- **Staleness indicator**: the app always shows last-successful-sync time; compliance-ish displays (expiry countdowns) compute from local clock against synced data and are safe to show stale.
- **Security of the local store**: encrypted at rest (platform keystore-backed), wiped on logout and remotely revocable (SEC-12).

### 7.7 Onboarding & identity (MOB-6)

Crew access is provisioned by the System Administrator/Data Steward against Person records: the Person is linked to their **corporate identity** (provider + tenant/domain + subject), and the invite (email/SMS) deep-links into the corporate SSO sign-in (SEC-1) — the app never issues or stores its own credentials; self-registration is closed. Whether every crew member across the five partnerships holds a corporate account (and with which provider/tenant, or as a guest identity) is an open question that gates crew onboarding — O-17. Device binding: standard OIDC token auth with refresh; biometric unlock optional on-device. MFA policy per SEC-2.

---

## 8. Evidence intake & LLM extraction pipeline (backend)

This productionises migration-plan Phase 4, promoted to core scope by the mobile requirement.

Pipeline stages (each stage transition audited):

1. **Ingest** — authenticated upload (mobile or admin), virus/malware scan, file-type validation, store original in object storage (immutable), create EvidenceDocument in `pending_extraction`.
2. **Extract** — LLM/vision extraction of: document type, holder name, identifying numbers, issuing authority, issue date, expiry date, qualification/course title. Multi-page aware. Output stored as structured fields **with per-field confidence** and the raw model response retained for audit.
3. **Match** — resolve to (person, requirement): person from the authenticated submitter (mobile) or explicit selection (admin); requirement via catalogue matching (code, title, aliases) seeded by the user's hint. Ambiguous matches → `pending_review`, never guessed silently.
4. **Decide** — if all critical fields (person match, requirement match, expiry/issue dates) exceed the configured confidence threshold **and** the change is low-risk (e.g. renewal extending an existing holding of the same requirement): **auto-accept** — update the holding, link evidence, notify the user, and log an auto-acceptance audit event that the Data Steward's queue also surfaces for retrospective spot-checking. Otherwise → `pending_review` in the admin verification queue (ADM-9).
5. **Verify** (human) — accept / correct fields / reject with reason. Acceptance updates the holding (status, issue, expiry) and links the evidence; rejection notifies the submitter.

Constraints:

- **LLM-1** The LLM never mutates the system of record directly; only the decide/verify stages write holdings, and every write is audited with its evidence link.
- **LLM-2** Auto-accept is **off by default** at launch (threshold = "always review"); enabled per policy once precision is measured against the review queue.
- **LLM-3** Prompt-injection hardening: extracted document content is data, never instructions; extraction runs with a fixed schema (tool/structured output), no agentic capabilities, no network access beyond the model call.
- **LLM-4** Provider and data-processing terms must satisfy the privacy assessment (O-9): no training on submitted data, regional processing if required. Provider selection in §14.5.
- **LLM-5** Cost/latency envelope: extraction is asynchronous (seconds–minutes acceptable); user is notified on completion — no interactive blocking.

---

## 9. Notifications architecture

- **Source of truth**: Notification rows in the database (per recipient), created by domain events (register transitions, assignment changes, matrix publication) and by **scheduled scans** (expiry lead-time job — daily; cutoff-approaching job for coordinators).
- **Channels**: in-app (C2 + C3), push (APNs/FCM via a unified sender), email (transactional provider) — per-class channel routing with user preferences (MOB-3).
- **Delivery tracking**: per-channel delivery records (sent/failed) for supportability; failures never lose the in-app record.
- **Back-office notifications** (POC behaviour, kept and made per-role): register events → Workflow Manager; quota shortfalls and roster gaps → Coordinators; new exceptions → Data Steward.

---

## 10. API design (outline)

### 10.1 Style

JSON over HTTPS; resource-oriented; server-side validation is authoritative. Versioned (`/api/v1`). OpenAPI schema published and used to generate client types for both frontends (parity aid). Idempotency keys on mutating endpoints called from the mobile queue.

### 10.2 Resource groups

`/partnerships /vessels /positions /slots /crew-changes /requirements /matrix-versions /rules /conditional-rules /quota-rules /people /holdings /assignments /leave /register /evidence /notifications /exceptions /users /config` — plus derived read endpoints that mirror the engine: `/swings/{pt}/{cc}/evaluation`, `/swings/{pt}/{cc}/quotas`, `/swings/{pt}/{cc}/gaps`, `/people/{id}/evaluation`, `/suggestions?slot=…`, `/expiry-alerts`, `/matrix-versions/{a}/diff/{b}`.

### 10.3 Mobile sync endpoints

- `GET /sync/snapshot` — full scoped dataset + server cursor.
- `GET /sync/delta?cursor=…` — changes since cursor (changed + tombstones).
- `POST /sync/queue` — batched idempotent client writes (submission metadata, read-marks, preferences).
- `POST /evidence/{id}/chunks` — resumable file upload.

Change tracking via a per-row `updated_seq` (monotonic global sequence) or change-log table — implementation choice for §14 research, but the contract above is fixed.

---

## 11. Data migration & data quality

- **Source**: the POC's extraction pipeline (`extract_seed.py` → `seed/*.csv` + `exceptions.csv`) is the validated master extract; production migration loads from these CSVs (re-runnable), not from the workbooks directly. The POC's fix-up rules (Sam # resolution by name, `UNREC-xx` placeholder IDs, legacy title preservation in `req_raw`, slot-collapse with M⁸ one-of set) carry over verbatim.
- **Everything flagged**: every migration fix-up class produces ExceptionItems (35 known). Anomalies are preserved, not cleaned (unknown holdings, reused Sam #, stray legend values, uncoded Advanced Fire Fighting, VS-03 gap).
- **Full history**: 445 register rows (267 extractable + placeholders per POC rules), CC18–CC25 rosters (174 assignments incl. handovers), both matrix template versions, 1,155+ holdings.
- **Acceptance test**: regenerate the CC24/CC25 compliance views post-migration and diff against the POC's live-dataset rendering (which the client validated) — including the known real quota shortfall (UNI CC24, 0/1 GPH with Work at Heights on Shift 1).
- The synthetic demo dataset does **not** ship in production; a demo/training environment is seeded separately (its catalogue codes differ from the real one and must never mix).

---

## 12. Non-functional requirements

Security is the stated top priority; requirements are numbered for traceability.

### Security

- **SEC-1** Authentication: **corporate SSO is the sole authentication path for all users** — back-office and crew alike sign in with their corporate account via OIDC federation with the major corporate identity providers (**Microsoft Entra ID / Office 365, Google Workspace, Okta**, and peers — the supported set is configuration, not code). The application stores **no user accounts of its own and no passwords, ever**; UserAccount holds only the external identity linkage (§4.3). Multi-provider federation should be delegated to a standard OIDC library or a managed federation broker rather than hand-rolled per provider — evaluate in §14 research.
- **SEC-1a** Provider/tenant validation: only corporate backends on an explicit administrator-managed allow-list may authenticate — allow-listed at the level of (provider, tenant/domain), e.g. a specific Entra tenant ID, Google Workspace hosted domain, or Okta org. Issuer and tenant/domain claims are verified on **every** token, not just at first login; tokens from unvalidated backends are rejected before any account resolution. Onboarding a partnership's corporate backend is an audited administrative act.
- **SEC-1b** Transitional exception: local/test accounts are permissible during testing and early phases **only** — clearly flagged in UserAccount (§4.3), disabled in production by default via configuration, and removed or constrained to the demo/training environment as an exit criterion of P2 at the latest. Their existence and removal are tracked as a standing exception item.
- **SEC-2** MFA required for all back-office roles, enforced at the corporate IdP (conditional-access/session policies — a benefit of SEC-1: MFA, device compliance, and account lifecycle are the corporate backend's problem, not the application's); risk-based/optional for crew members (policy decision O-10).
- **SEC-3** Authorization per §3 (AUTH-1..4); crew-member row-scoping enforced centrally.
- **SEC-4** Transport: TLS ≥1.2 everywhere, HSTS, certificate management via platform.
- **SEC-5** At rest: encrypted database and object storage (platform-managed keys acceptable; document the KMS story).
- **SEC-6** Audit: append-only audit trail for all business mutations (AUTH-3); admin access to production data logged. An external, independent, read-only audit mirror is a forward requirement — see §17.4 and O-14; the in-DB trail must be export-ready from P1 (§4.4).
- **SEC-7** Input handling: all writes validated server-side; file uploads scanned, content-type verified, served with non-executable dispositions from object storage via signed URLs.
- **SEC-8** Dependency & platform hygiene: language/framework chosen partly for security posture (§14.1); automated dependency scanning; managed runtime patching (PaaS).
- **SEC-9** Secrets in a managed secret store; no secrets in code or config files.
- **SEC-10** Privacy: crew personal data (identity, qualifications, contact details, leave) is personal information under the Australian Privacy Act — a privacy impact assessment is required **before build** (revises Q23; O-9). Data minimisation applies throughout.
- **SEC-11** LLM data handling per LLM-3/LLM-4.
- **SEC-12** Mobile: encrypted local store; token revocation invalidates offline caches at next connectivity and the app enforces a maximum offline-validity window (e.g. 30 days) after which cached data locks; remote sign-out per device.
- **SEC-13** Push payloads: no sensitive content in notification payloads (title + deep link only, e.g. "A qualification is expiring soon"); details fetched in-app.
- **SEC-14** Data residency: default to Australian-region hosting for data at rest (confirm in O-9).

### Availability, performance, scale

- **NFR-1** Scale is small and bounded (≈60 crew, 5 partnerships, ~10³ holdings, ~10² register rows/year; ≈60 mobile devices syncing). A single modest app-server instance with a managed relational DB is ample; the architecture must scale *operationally* (zero-downtime deploys, managed failover), not horizontally.
- **NFR-2** Availability target 99.5% monthly for the API (coordination tool, not safety-of-navigation system); mobile app fully usable offline per MOB-5, so brief outages are low-impact.
- **NFR-3** Interactive API responses (incl. swing evaluation) < 500 ms p95 — trivially achievable at this scale if evaluation is computed on demand rather than materialised; do not pre-optimise.
- **NFR-4** RPO ≤ 24 h (daily backups minimum; managed PITR preferred), RTO ≤ 1 business day. Evidence object storage versioned/immutable.
- **NFR-5** Timezone: all business dates are calendar dates in the operating timezone (AWST assumed — confirm O-11); store dates as dates, not timestamps, matching POC semantics.

### Operability

- **NFR-6** Structured logging, error tracking, uptime monitoring, and background-job observability from day one; single-pane health view for the Administrator (ADM-10).
- **NFR-7** Environments: production, staging (client UAT), demo/training (synthetic seed). Migrations are forward-only and automated.
- **NFR-8** Excel-shaped exports retained for MRL/AMSA audit needs pending Q26 (O-7) — CSV at minimum (POC parity), XLSX if the audit answer requires it.

---

## 13. System architecture

Per the stated constraints: **simple, standard, boring on purpose.**

```
   iOS app ─┐                                  ┌─ Managed relational DB (PostgreSQL)
Android app ─┼─ HTTPS ─→  Monolithic app server ┼─ Object storage (evidence originals)
  Admin SPA ─┘            (API + engine + sync  ├─ Background jobs (in-process scheduler
                           + jobs + MCP server) │   or platform cron): expiry scan,
Claude Code ── MCP ──→     (§13.1)              │   notification fan-out, LLM pipeline
(dev/staging only)                              ├─ LLM provider API (extraction)
                                                ├─ APNs / FCM (push), email provider
                                                └─ Corporate IdPs (OIDC federation — SEC-1:
                                                    Entra ID/O365, Google Workspace, Okta, …)
```

- **Monolith**: one deployable containing API, compliance engine, workflow, sync, and job workers (jobs may run as a second process of the same artefact if the platform prefers). Modular internally (reference / rules / people / workflow / evidence / sync modules mirroring §4) so later extraction is possible but not planned.
- **Monolith is the default, not a mandate** (26 Jul revision, §17.5): decomposition into services is acceptable where the written justification names a **security or scalability** property gained — the client's own example being a mobile-facing API/sync gateway that never holds database credentials. The module boundaries above are the approved seams. At NFR-1's scale, scalability alone will rarely justify extraction; security may.
- **Database**: PostgreSQL as the default assumption — standard, relational, strong constraint/enum support, row-level security available for AUTH-2, first-class on every candidate PaaS. Genuinely contested only if the platform choice (§14.2) bundles an equivalent (e.g. Cloud SQL variants).
- **Platform**: a managed PaaS in the Google App Engine class — candidate set in §14.2. Requirements on the platform: managed TLS, zero-downtime deploys, managed DB with PITR, object storage, scheduled jobs, secret management, Australian region.
- **Admin SPA** served as static assets (from the monolith or a CDN); mobile apps via the App Store / Play Store with a standard release pipeline (see §14.3 for OTA-update considerations).

### 13.1 Built-in MCP server — day-one requirement

The application server embeds an **MCP (Model Context Protocol) server** from the first commit, so that Claude Code can investigate the application's internal state and modify it while developing — automated testing, debugging, fixture seeding, and state reproduction. This is a load-bearing part of the one-person development model (§17.2/§17.3), not a nice-to-have.

- **MCP-1** Capability surface: read tools over domain state (the §4 entities, engine evaluations §5, background-job/queue status, sync cursors, configuration, recent logs/errors) and targeted write tools (load/seed fixtures, mutate entities, trigger jobs, drive the admin date-override) — enough for an agent to set up a scenario, exercise it, and inspect the outcome without a human clicking through the UI.
- **MCP-2** Same rules as everyone: MCP write tools call the **identical validation/authorization/service layer as the API** — never raw database access. That is precisely the testing value (agent-driven mutations exercise real code paths), and it means MCP actions appear in the audit trail under an agent actor identity (AIA-3).
- **MCP-3** Environment gating: enabled in development and staging; **disabled in production by default**. Any production enablement is an explicit, audited, time-boxed configuration act and is read-only diagnostics at most (posture to confirm — O-18).
- **MCP-4** Authenticated even in development: token-authenticated, never bound to a public interface (localhost/private-network binding or an authenticated tunnel). An unauthenticated MCP endpoint with write tools is a remote-code-execution-equivalent hole and is prohibited in every environment.
- **MCP-5** Packaging: a module of the monolith sharing the service layer (so it cannot drift from real application behaviour), not a separate service — consistent with §17.5's rule, since neither security nor scalability motivates extracting it.

---

## 14. Technology selection — research agenda

Decisions to make **cooperatively**; this section frames criteria and candidate shortlists rather than concluding. Scoring criteria for all: security posture, fit for a rules-heavy relational domain, team availability/hiring, operational simplicity on managed PaaS, longevity, and (mobile) parity + offline maturity. The 26 Jul forward requirements add two heavily-weighted criteria across every table: **solo-operability** (one-person DevOps, §17.2 — how little unautomated attention the choice demands in production) and **AI-assist affinity** (§17.3/DEV-5 — how well the ecosystem supports AI-assisted development: strong typing, conventional frameworks, well-trodden documentation).

### 14.1 Backend language & framework

"Very strong security posture" reads as: memory safety, strong static typing, mature security-patch process, well-trodden framework with good secure defaults (authz, CSRF, injection resistance), and low ecosystem-supply-chain risk. Shortlist:

| Candidate | For | Against |
|---|---|---|
| **Kotlin (or Java) + Spring Boot** | Memory-safe JVM, best-in-class security ecosystem (Spring Security), superb PostgreSQL/ORM tooling, hires well, first-class on App Engine | Heavier runtime; Spring's size demands discipline |
| **C# + ASP.NET Core** | Memory-safe, excellent secure defaults, strong typing, great tooling, very good on Azure App Service (and fine in containers anywhere) | Weakest fit if the platform choice is GCP-native |
| **Go (stdlib + chi/echo)** | Memory-safe, tiny attack surface, minimal dependencies (supply-chain win), trivially deployable, GAE-native | More hand-rolling (authz, validation); thinner "batteries-included" security framework |
| **Rust (Axum)** | Strongest memory-safety story | Slowest to build in, smallest hiring pool — likely disproportionate for this domain's scale |
| **TypeScript + NestJS/Fastify** | One language across all three components | npm supply-chain risk profile is the worst of the set — sits awkwardly with "security is paramount"; include mainly as a baseline comparison |

Working recommendation to test in research: **Kotlin/Spring Boot** or **Go** depending on the team's background; both are safe answers, differing mainly in batteries-included vs minimal-surface philosophy.

Additional criterion from §13.1: maturity of the language's **MCP server SDK** (official SDKs exist for all shortlisted languages, but maturity varies) — verify in the backend spike, since the MCP interface must exist from the first commit.

### 14.2 Deployment platform

Candidates (all satisfy §13 platform requirements; verify Australian regions and DB PITR per candidate):

- **Google Cloud Run + Cloud SQL** — the modern default for "App Engine or similar"; container-based so language-agnostic; scale-to-small pricing fits NFR-1. Likely front-runner.
- **Google App Engine (standard/flex) + Cloud SQL** — the named reference point; fine, but Cloud Run is its effective successor.
- **Azure App Service + Azure Database for PostgreSQL** — strongest if the client org is Microsoft-centric. Note SEC-1's multi-provider federation (Entra/Google/Okta/…) is deliberately provider-neutral, so identity no longer forces a platform; Azure gains only if the partnerships' backends turn out to be predominantly Entra (O-13/O-17).
- **AWS App Runner / Elastic Beanstalk + RDS** — equivalent capability; choose if client has AWS estate.
- **Fly.io / Render / Railway** — simplest DX; assess maturity of compliance posture and AU regions before shortlisting for a "security paramount" client.

Research task: confirm the client's existing cloud estate and which corporate IdPs the partnerships actually run (O-13) — it likely decides this table. (SEC-1 fixes the *policy* — corporate SSO only, multi-provider — but the provider mix in the estate still matters here.)

### 14.3 Mobile stack

The hard parity requirement (MOB-0) plus a small team strongly favours one shared codebase:

| Candidate | For | Against |
|---|---|---|
| **Flutter** | True single codebase, excellent parity by construction, strong offline/SQLite (drift) ecosystem, first-class camera; good long-term Google support | Dart is a new language for most teams |
| **React Native (Expo)** | Huge ecosystem, TS skills transfer to/from admin SPA, Expo's build/update tooling (incl. OTA updates) is excellent | Parity needs more testing discipline; JS supply-chain caveat as 14.1 |
| **Kotlin Multiplatform** | Shared logic with fully native UI; natural if backend is Kotlin | UI still ~2×; youngest ecosystem of the three |
| Native ×2 (Swift + Kotlin) | Best-in-class per platform | Directly conflicts with parity + team-size reality; include only as the null hypothesis |

Offline sync layer: given the deliberately simple sync contract (§7.6, §10.3 — scoped snapshot + delta + idempotent queue), a **hand-rolled thin sync client over local SQLite** is preferred over adopting a sync framework (PowerSync, ElectricSQL, WatermelonDB); evaluate frameworks only if research shows the hand-rolled path exceeding ~2–3 weeks of effort.

### 14.4 Admin web frontend

Lower stakes; pick alongside mobile: **React + TypeScript** (default, shares types via OpenAPI codegen; shares skills with React Native if chosen) vs **Flutter web** (only if Flutter wins mobile *and* the team wants one UI stack — weigh its web ergonomics honestly against the POC's dense-table-heavy screens) vs server-rendered (HTMX/Rails-style — viable given the monolith, but the POC's interaction density suggests an SPA).

### 14.5 LLM provider (evidence extraction)

Criteria: vision/document quality on certificates and phone photos, structured output support, data-processing terms (no training on inputs, regional processing), cost per document, availability from the chosen cloud. Candidates: Anthropic Claude (direct API or via Vertex AI on GCP — the latter keeps data in-cloud and simplifies procurement), Google Gemini via Vertex (same in-cloud argument), OpenAI via Azure (if Azure is the platform). Research task: benchmark all shortlisted models on a redacted sample set of real certificates before choosing; measure field-level precision against Data Steward ground truth.

### 14.6 Recommended research sequence

1. Confirm client cloud/IdP estate (decides much of 14.2, influences 14.1/14.5).
2. Privacy impact assessment kickoff (O-9) — its residency/processing answers gate 14.5.
3. Backend language spike: implement the §5 engine + 2–3 API endpoints in the top two candidates; compare.
4. Mobile spike: offline list + camera capture + queued upload in Flutter and RN/Expo; compare parity effort.
5. LLM benchmark on sample certificates.
6. Development pipeline definition (§17.3): CI/CD, AI code review, preview environments, test-suite strategy — recommended alongside the language spike so the pipeline exists before feature work starts.
7. Solo-operations assessment of the shortlisted platform (§17.2): enumerate every manual touchpoint (cert rotation, DB patching, scaling, backup verification) and confirm each is managed or automatable, including the AI-triage hook for alerts (OPS-3).

---

## 15. Delivery phasing

Each phase independently shippable; admin-first so the back office runs on the new system before crew onboard.

- **P1 — Backend foundation + admin parity.** Schema (§4), migration (§11), engine (§5), auth/RBAC (§3), admin modules ADM-1..8. Exit: back office abandons the spreadsheets (parity with the validated POC on real infrastructure).
- **P2 — Mobile read-only + notifications.** Onboarding (MOB-6), sync read path (MOB-5), my-certifications (MOB-1), my-roster (MOB-2), push (MOB-3, §9), leave records (coordinator-entered). Exit: crew self-serve their compliance status; expiry chase moves from phone calls to push.
- **P3 — Evidence pipeline.** Mobile capture + offline queue (MOB-4, MOB-5a), backend pipeline with review queue (§8, ADM-9), auto-accept measured then enabled per LLM-2. Exit: holdings upkeep is submission-driven; the "unknown holdings" chase list burns down.
- **P4 — Refinements.** Per-day quota evaluation (ENG-1), calendar export, leave workflow (pending O-8), audit-shaped XLSX exports (pending O-7), role-model extension beyond single workflow manager if Q14 evolves. External read-only audit mirror (§17.4) lands here at the latest — earlier if O-14 confirms a regulator/assurance driver.
- **P5 — AI assistance expansion (§17.1).** Persona assists beyond the P3 evidence pipeline — swing-planning assistant, risk review, request drafting, exceptions triage — sequenced by value per O-15, each following the propose-approve-audit template (AIA-2/AIA-3).

The one-person DevOps and AI-assisted development-pipeline requirements (§17.2, §17.3) are not phased: they apply from the first commit of P1 and are part of the §14 research deliverable.

---

## 16. Open questions

Carried from the migration plan (renumbered) plus new ones raised by this specification:

| # | Question | Blocks |
|---|---|---|
| O-1 (Q3) | Matrix ownership — who can publish a version? | P1 role config only (default: Compliance Lead) |
| O-2 (Q5) | Rule granularity: per position vs per slot/shift | §4.2 schema headroom decided; needs answer before P1 schema freeze |
| O-3 (Q12) | Position tier semantics (derived from CoC held?) | Tier review rule, one-of sets |
| O-4 (Q14/15) | Full role model & approval granularity | P4; single-manager stands |
| O-5 (Q17) | Confirm cutoff column = submission cutoff | Already enforced as such; confirm |
| O-6 (Q13) | Grace/lead-time thresholds (90 days default) | Config default only |
| O-7 (Q26) | Excel-shaped audit export required by MRL/AMSA? | P4 export format |
| O-8 (new) | Leave/holidays: system of record — CREWCOMP-entered or HR-system mirror? Request workflow needed? | MOB-2 depth, P4 leave workflow |
| O-9 (new, urgent) | Privacy impact assessment: crew PII on devices, LLM processing terms, data residency, retention, crew consent/works-council considerations | LLM provider choice, SEC-10..14, before build |
| O-10 (new) | Notification & MFA policy for crew: mandatory classes, opt-outs, personal-device expectations (BYOD policy) | MOB-3, SEC-2 |
| O-11 (new) | Operating timezone confirmation (AWST?) and any multi-timezone edge (fly-in dates) | NFR-5 |
| O-12 (new) | Extended swing / partial handover approval process (ex-Q21) | Still manual; unchanged |
| O-13 (new) | Client cloud estate (existing GCP/Azure/AWS tenancy?) and the actual IdP mix across the partnerships (Entra? Google Workspace? Okta?). *Policy fixed 26 Jul (corporate SSO only, multi-provider — SEC-1); the concrete provider inventory still needed* | §14.1/14.2/14.5 research, SEC-1a allow-list |
| O-14 (new) | External audit trail: who is the consumer (regulator, MRL/AMSA, client assurance)? Required independence level, retention period, and technology (WORM object storage vs ledger DB vs third-party service) | §17.4 design and phasing; AuditEvent export shape is P1 regardless |
| O-15 (new) | AI assistance priority: which persona activities deliver value first, and which the client would trust with graduated automation | §17.1 / P5 sequencing |
| O-16 (new) | AI-ops autonomy boundary: which operational events may be remediated automatically vs triaged-and-escalated (OPS-3) | §17.2, ADM-10 config |
| O-17 (new) | Crew identity coverage: do all crew across the five partnerships hold corporate accounts with a supported IdP? Which provider/tenant per partnership? Guest accounts acceptable? What happens for crew without one? | SEC-1a allow-list, MOB-6 onboarding, P2 |
| O-18 (new) | MCP interface production posture: fully disabled vs admin-authenticated read-only diagnostics | MCP-3 |

---

## 17. Forward requirements (26 Jul 2026) — deferred delivery, immediate design constraints

Added after the V2 draft was written. None of these change P1–P3 feature scope; all of them constrain decisions being made now (schema, architecture rules, §14 technology research). Each subsection states the requirement, then what **bites now** — the part that must be honoured in P1 even though the feature itself lands later.

### 17.1 AI assistance as a first-class capability (AIA)

AI automation is designed in from day 1, not bolted on: any activity performed by any §3 persona is a standing candidate for AI assistance or automation.

- **AIA-1** Named early candidates, roughly by expected value: **swing planning** (draft a compliant roster for a swing and explain the trade-offs — extending §5.4 from ranking candidates to proposing plans); **certificate/qualification parsing** (§8 — already core scope, and the template for the rest); **risk review** (summarise a swing's or partnership's compliance risks, surface anomalous register/exception patterns); **exemption request drafting** (pre-write request text and proposed conditions from the gap context); **exceptions triage** (propose resolutions for data-quality worklist items); **register/note summarisation** for the Workflow Manager.
- **AIA-2** Assistance is advisory by default: AI proposes, an authorised human disposes. The §8 evidence pattern — confidence threshold, auto-accept off until precision is measured, audited auto-actions surfaced in a human spot-check queue — is the normative template for any assist that graduates to writing data.
- **AIA-3** AI actions are auditable actors: AuditEvent distinguishes human / AI-proposed-human-approved / AI-automatic, and records model and prompt/config version for AI-touched events (§4.4).
- **AIA-4** The ADM-10 configuration surface generalises to per-assist settings: enablement, autonomy level, thresholds, model selection.

**Bites now (P1):** the engine, suggestion ranking, and gap report must be exposed as clean internal/service APIs rather than view-bound logic — an assistant is just another consumer of them; AUTH-1's "all writes through validated endpoints" is what makes tool-calling assistants safe to add later, so no bypass paths; audit schema per AIA-3; §14.5 provider selection weighs general assistance (reasoning over structured compliance data, tool use) alongside document extraction.

### 17.2 One-person development & operations (OPS)

The development and operations team is effectively **one person**. Architecture, hosting, build, and CI must run without manual intervention except when genuinely unavoidable.

- **OPS-1** Fully automated delivery: commit → CI (build, tests, dependency/secret/security scanning) → staging deploy → production deploy with automated rollback. No manual release steps.
- **OPS-2** Managed-everything hardens from preference (§13) to rule: no self-managed servers, databases, queues, TLS rotation, or backup jobs. §14.2 platform scoring gains a heavily-weighted **solo-operability** criterion; §14.6 item 7 audits every remaining manual touchpoint.
- **OPS-3** AI-integrated operations: monitoring alerts, error-tracker events, and failed background jobs feed an AI triage step (an agent that reads the alert, logs, and runbook, then remediates per runbook, drafts a fix, or escalates with a diagnosis). Human attention is the exception path. Autonomy boundary per O-16.
- **OPS-4** Everything as code: infrastructure, configuration, dashboards, alert rules, and runbooks live in the repository — for reproducibility, and so AI tooling can read and act on them.

### 17.3 AI-assisted development pipeline (DEV)

Best-practice recommendation for the sole developer working alongside Claude Code; finalised in §14 research (item 6), applied from the first commit.

- **DEV-1** Spec-as-source-of-truth: this document and its successors live in the repository; per-component CLAUDE.md files orient the agent (the POC already demonstrates the pattern).
- **DEV-2** Guardrails substitute for review bandwidth: strong static typing, API client types generated from the OpenAPI schema, the §5 engine semantics as an executable test suite (ported from the validated POC behaviour), and automated migration checks — the pipeline must catch what a second reviewer would, because there isn't one.
- **DEV-3** AI code review on every change, with a security-focused pass on anything touching auth, sync, row-scoping, or the evidence pipeline.
- **DEV-4** Ephemeral preview environments per change where the platform provides them cheaply; promotion to staging automated on green.
- **DEV-5** §14.1's language decision gains the AI-assist-affinity criterion: typed, conventional, well-documented ecosystems give code agents the best ground truth and the pipeline the strongest static guarantees.
- **DEV-6** The built-in MCP server (§13.1) is the primary hook for agent-driven testing and debugging: Claude Code sets up state, exercises behaviour through real code paths, and inspects outcomes without UI-driving. Automated test suites may use the same fixture/seeding tools.

### 17.4 External independent audit trail (AUD)

As a compliance application, traceability must survive fault or compromise of the primary system. Candidate requirement, to be confirmed via O-14:

- **AUD-1** A **read-only, append-only mirror of AuditEvent outside the main datastore**: separate storage account/project with independent access control; write-once storage (object-lock/WORM or ledger-style); the application holds append-only credentials — nothing in the system can update or delete mirrored events.
- **AUD-2** Tamper-evidence: events exported with their monotonic sequence and hash-chained, so gaps or rewrites are detectable by an external auditor.
- **AUD-3** The mirror is asynchronous, near-real-time, best-effort (export job with backlog alerting); the in-DB AuditEvent (SEC-6) remains the operational trail and the mirror never becomes a runtime dependency.

**Bites now (P1):** the AuditEvent shape in §4.4 — self-contained, immutable, stably ordered — is designed for this export from the start, so the mirror is an added consumer, not a schema migration.

### 17.5 Architecture flexibility — monolith as default, not mandate

The 25 Jul "monolithic backend" requirement is relaxed: the client is happy with service decomposition **where the motivation is security or scalability** — not as a style choice.

- Canonical example (client-supplied): the mobile apps connect to a separate API/sync gateway service that **never connects to the database** — it talks only to the core application's internal API. This shrinks the injection/blast-radius exposure of the internet-facing mobile surface (sync, evidence upload) and lets that surface scale independently of the core.
- The §13 internal module boundaries (reference / rules / people / workflow / evidence / sync) are the approved seams; any extraction carries a written justification naming the specific security or scalability property gained.
- At NFR-1's scale (~60 users), scalability alone will rarely clear that bar; security-motivated extraction — the gateway pattern above, or isolating the LLM pipeline — plausibly will. This subsection changes the rule under which §13 may be revised during §14 research; §13 remains the baseline drawing.

---

## Appendix A — Enumerations (normative reference)

- **Cell states**: `ok · expiring · gap · exempt · pending · unknown · review · quota_only · na · recommended`
- **Holding status**: `held_expiry · held_perpetual · not_held · unknown`
- **Register types**: Exemption Request - PW · Exemption Request - OPS · Exemption Request following MRL Query · MRL Query · PW Query
- **Register statuses**: Open - PW · Open - MRL · Open - OPS · Complete before joining · Closed - {outcome}
- **Outcomes**: Approved · Not Approved · Info Required · Admin Action · Not Required
- **Condition types**: supervision · time_limit · duty_restriction · training_booked · other
- **Evidence verification**: pending_extraction · pending_review · auto_accepted · verified · rejected
- **Expiry impact**: expired_before_swing · mid_swing · none
- **Requirement categories**: QL · VS · PS · MS · CS · HR · PT · VI · PI

## Appendix B — POC → specification traceability

| POC artefact | Production counterpart |
|---|---|
| `engine.js` evaluation semantics | §5 (normative), server-side engine, P1 |
| `store.js` state + localStorage | §4 schema + PostgreSQL, P1 |
| Personas + `App.canEdit()` | §3 roles + server-side RBAC (AUTH-1) |
| Views: dashboard/planner/matrix/register/people/catalog/exceptions/notifications | ADM-1..8, P1 |
| In-app notification list | §9 notification service + push/email channels, P2 |
| Seed pipeline (`extract_seed.py`, `build_real_seed.py`) + `exceptions.csv` | §11 migration, P1 |
| Deferred Phase 4 (document OCR) | §8 evidence pipeline + ADM-9 + MOB-4, P3 |
| Pinned "today" (2026-07-25) | Real clock; admin-only date override for testing/audit reconstruction |
| Demo dataset | Separate demo/training environment seed (never mixed with live catalogue) |
