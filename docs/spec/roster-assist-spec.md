# Roster Assist — auto-suggested swing rosters

**Status: draft proposal, 31 Jul 2026.** Not yet part of the production spec. This is the
"swing planning" assist AIA-1 names as the highest-value AI candidate ("draft a compliant
roster for a swing and explain the trade-offs — extending §5.4 from ranking candidates to
proposing plans"), specified far enough to build the procedural core now rather than waiting
for P5. Like ADM-11, anything here that changes what the client sees should be confirmed with
them before it ships; unlike ADM-11, nothing here invents a workflow — it composes the ones
§5 and §6.2 already define.

Spec references are to `crewcomp-production-spec.md`. Code references are to `backend/` as of
commit 382e3f2.

## 1. What this is

Today the planner answers one question at a time: *who could fill slot 07?* (`§5.4`,
`engine/Planning.kt`). Filling a swing is therefore N greedy, order-dependent cycles, each a
full modal round-trip, with nothing to stop the same person being the top candidate for two
slots and nothing that works backwards from a quota shortfall to the people who would close
it.

Roster Assist answers the whole question at once: *given this swing, its current assignments,
everyone's holdings, exemptions, leave and existing commitments — propose a complete roster,
score it, explain every line, and let a coordinator accept it line by line.* The proposal is
a **plan, not a write**: nothing changes until a human applies it, and every applied line goes
through the same validated `AssignmentService.assign` door every human assignment goes
through (AUTH-1).

Three principles, fixed up front:

- **The engine computes, the coordinator disposes.** A proposal is advisory in exactly the
  sense §5.3 makes quota shortfalls advisory. There is no autonomy level at which Roster
  Assist writes an assignment without a named human accepting it — see §8 for how that is
  enforced by the schema, not by policy.
- **Deterministic first, model later.** The solver is pure Kotlin in `au.crewcomp.engine`
  (no CDI, no JPA, no network — the same purity rule the rest of the engine lives by). An
  LLM's only future role is *narrating* a proposal (§8.3); it never chooses, ranks or fills.
  Every line of a proposal is explainable from `reasons[]` today, which matters more in an
  auditable domain than a cleverer fill.
- **A proposal that cannot be evaluated without being persisted is not a proposal.** The
  what-if endpoint (§4) comes first and is independently useful.

## 2. What already exists, and the three defects in the way

The hard half is built. `SwingEvaluator.evaluate` is a pure function over
`SwingEvaluationInputs` — plain data classes, no framework types — and
`ComplianceService.suggestions` already demonstrates substituting hypothetical inputs via
`.copy()` (`compliance/ComplianceService.kt:163-167`). `Planning.suggestCrew` is the per-slot
ranking with an explicit penalty function (`gap ×100, unknown ×10, expiring ×5,
cross-partnership +3, clash +1000`, name tiebreak). `AssignmentService.assign` validates
position eligibility, window containment and same-slot overlap, and turns a personal clash
into an acknowledged 409 rather than a refusal. The audit schema already knows how to record
an AI-proposed, human-approved change (§8).

Three defects found in review (31 Jul 2026) must be fixed before any of this is built on,
because an automated caller amplifies each one from an annoyance into a failure:

- **P0-a — configured suggestion weights are inert.** `ConfigService.suggestionWeights()`
  exists, is validated and settable on ADM-10, and has zero callers.
  `ComplianceService.suggestions` uses `SuggestionWeights()` defaults. A Compliance Lead can
  tune the weights, watch the change be audited, and change nothing. Wire the reader in
  (`ComplianceService.kt:145`); §5.4 already says "weights become configuration" and the
  engine test (`PlanningTest.kt` "weights are configuration") already asserts the engine
  honours them.
- **P0-b — the ranking is blind to leave; the write path is not.** `Planning.score` counts
  only overlapping assignments; `AssignmentService.clashesFor` unions assignments **and**
  standing leave. So `GET /suggestions` ranks a person on approved leave at score 0 and the
  subsequent POST 409s. For a solver this is a guaranteed dead end at commit time. Fix by
  passing standing-leave ranges into `suggestCrew` with `CourseOffers`' two-tier semantics
  (`courses/CourseOffers.kt`): an overlapping *assignment* stays a near-exclusion (+1000,
  shown as a clash); overlapping *leave* is a labelled, heavily-weighted signal (new weight,
  suggested default 500 — leave is a 409 the coordinator may acknowledge, so it must sort
  below clean candidates but never disappear). `LeaveRecordRepository.STANDING_STATUSES`
  exists precisely so two callers cannot disagree about who is away; the planner becomes its
  third caller.
- **P0-c — slot double-booking is guarded only by an unlocked read-then-check.**
  `AssignmentService.validate` reads then checks; `assignment` has no unique or exclusion
  constraint on `(crew_change_id, slot_ref)` overlap. Two concurrent POSTs for the same slot
  can both commit. Near-certain once a batch apply exists. Fix with both belts: a fourth
  advisory-lock namespace (per the backend guide — never a borrowed constant) taken by
  `assign` per `(crewChangeId, slotRef)`, and a Postgres exclusion constraint on the date
  range as the backstop the application cannot forget.

P0 is worth doing whether or not Roster Assist ever ships — all three are live defects in the
existing planner.

## 3. Delivery phases

| Phase | Delivers | Depends on |
|---|---|---|
| **P0** | The three fixes above | nothing |
| **P1** | What-if evaluation endpoint (§4) | P0-a only |
| **P2** | The solver + proposal generation, read-only (§5, §6) | P1 |
| **P3** | Proposal apply — the batch write path (§7) and the planner UI (§9) | P0-c, P2 |
| **P4** | Autonomy ladder: scheduled draft proposals; LLM narration (§8) | P3, §14.5 provider for narration only |

Each phase is independently shippable and independently useful; P1 in particular gives the
existing planner a "what would this change do to my numbers" answer nothing offers today.

## 4. What-if evaluation (P1)

`POST /api/v1/swings/{partnership}/{cc}/evaluate` — body: a complete hypothetical assignment
list (`[{slotRef, personId, from?, to?}]`, same shape the planner already knows). Response:
the same `SwingEvaluationDto` the GET returns, evaluated as if those were the swing's
assignments, plus the current evaluation's `stateCounts`/`quotas` so a client can diff
without a second call.

Semantics:

- **Nothing persists.** No audit event (nothing happened), no notifications, no tombstones.
  Implementation is `inputsFor(crewChange).copy(assignments = hypothetical)` — the same move
  `suggestions` already makes for candidate holdings.
- Hypothetical people not currently assigned get their holdings merged into inputs, exactly
  as `ComplianceService.kt:163-167` does today (candidates aren't assigned yet, so their
  holdings aren't in the swing's input set — this is a known trap, documented there).
- Validation is advisory, not blocking: a hypothetical list containing a position mismatch or
  an overlap evaluates anyway and reports the violation per line, because the whole point is
  to look before committing. The response carries a `violations[]` array per line using the
  same rules `AssignmentService.validate` enforces, so a client can pre-flight a plan without
  a single 400.
- Role gate: same six roles as `evaluateSwing` — this is a read.
- `asOf` is the business date via `BusinessClock`, as everywhere.

## 5. The solver (P2) — `Planning.proposeRoster`

A new pure function in `au.crewcomp.engine`, beside `suggestCrew`:

```kotlin
fun proposeRoster(
    inputs: SwingEvaluationInputs,      // current state, as evaluated
    candidates: List<PersonView>,        // active fleet, holdings merged by the caller
    candidateHoldings: Map<PersonId, Map<RequirementId, HoldingView>>,
    otherAssignments: List<AssignmentView>,   // commitments outside this swing
    standingLeave: Map<PersonId, List<DateRange>>,
    weights: SuggestionWeights,
    options: ProposalOptions,            // fillPartCovered, allowCrossPartnership, maxLegsPerSlot…
): RosterProposal
```

### 5.1 Objective

`SwingEvaluation` today has no single scalar — deliberately, and this spec does not add one
to the engine's public answers. The solver's internal objective is:

```
minimise  Σ per-line penalty (the §5.4 score, leave-extended per P0-b)
        + Σ quota shortfalls × weightQuotaShortfall      (suggested default 800)
        + open slots remaining × weightOpenSlot           (suggested default 2000)
```

so that leaving a slot open is worse than filling it imperfectly, and closing a quota is
worth more than avoiding several expiring cells but less than avoiding a hard gap on the
person filling it. All three new weights join `SuggestionWeights` and flow through the same
ADM-10 configuration P0-a wires in. The objective is stated in the proposal output
(`totalScore`, per-line scores, quota deltas) — never a hidden number.

### 5.2 Algorithm

Scale is small — ≤17 slots, ~60 people — so no dependency and no cleverness is needed:

1. **Evaluate every (candidate × slot) pair** where the candidate's position is in the
   slot's `allowedPositions`, they are active, and they are not already assigned to this
   swing. One `PersonEvaluator.evaluate` per candidate (not per pair — the evaluation is
   per swing, reused across slots).
2. **Most-constrained-slot-first greedy fill**: order open slots by ascending candidate
   count, assign the best-scoring candidate, remove them from the pool, repeat. This kills
   the "same person is top candidate for two slots" failure of per-slot greed.
3. **Quota repair pass**: for each unsatisfied `QuotaEvaluation`, compute the inverse
   suggestion — candidates (assigned or poolside) who would count under
   `validThroughSwing` for the quota's requirement, position filter and shift — and try
   swaps that close the shortfall for the smallest objective increase. This inverse query
   (`suggestForQuota`) is exposed as its own engine function and its own endpoint, because
   the ADM-2 quota drill-down needs it even without Roster Assist: today the modal shows who
   *counts* and offers no way to find who *would*.
4. **Handover-leg construction** (behind `options.maxLegsPerSlot > 1`): where every whole-
   swing candidate for a slot scores badly on a mid-swing expiry, propose a two-leg split at
   the lapse date — the planner's ruler already computes exactly this geometry (`firstLapse`)
   for display. Legs obey the same abutment rule the write path enforces (sequential, never
   overlapping).
5. **Determinism**: same inputs + same weights ⇒ same proposal, byte for byte. Ties break on
   `(score, sam)` — sam, not name, because sam is the business key. No randomness, no clock
   reads (`asOf` is a parameter; the engine never calls `LocalDate.now()`).

Quota semantics are inherited unchanged, and two are worth restating because a solver is the
first caller that could get them wrong: **a holding expiring mid-swing never counts toward a
quota** (`validThroughSwing`: expiry ≥ swing end), and **an exemption never satisfies a
quota** — quotas ask about real holdings. Shift `N/A` crew count toward both shifts. Per-day
quota variation across handover legs remains ENG-1 (P4 of the main spec): a proposal that
introduces legs reports `partiallyCoveredSlots` honestly rather than pretending per-day
evaluation exists.

### 5.3 What a proposal contains

Every line is self-explaining, because the audit trail and the review UI both need it:

```
RosterProposalLine:
  slotRef, personId, from, to           // whole swing unless a leg
  score, reasons[]                       // the §5.4 prose: "2 gaps", "leave 1–14 Aug", …
  clashes[]                              // the clashing assignment/leave identities, not a boolean
  closesQuotas[]                         // footnotes this line contributes to
  evaluation summary                     // state counts for this person on this swing

RosterProposal:
  lines[], unfillableSlots[] (with why: no eligible candidate / all candidates clash)
  before/after: stateCounts, quota evaluations, open-slot count, gap-report length
  totalScore, weights used, asOf, matrixVersionId
```

Note `clashes[]` carries identities. The existing `SuggestionDto` drops
`clashingAssignmentIds` and the embedded evaluation on the way to the wire — that
impoverishment is not repeated here, and fixing `SuggestionDto` to match is in scope for P2
(the ADM-2 review found coordinators diagnosing clashes by attempting the write and reading
the 409).

## 6. Proposal lifecycle (P2–P3)

A proposal must survive between the machine making it and a human deciding on it — which is
new: nothing today parks an AI-or-machine suggestion awaiting approval. The evidence pipeline
is the normative template (AIA-2), and this follows it.

New table `roster_proposal` (+ `roster_proposal_line`), surrogate-keyed, person-free business
key `RP-{seq}`:

```
draft ──► applied            (some or all lines accepted; per-line outcomes recorded)
      └─► discarded          (terminal, note required — same terminal-with-a-note rule as ADM-11)
```

- A proposal is **immutable once generated** — regeneration is a new proposal, the same rule
  as matrix versions and statements. What it was generated *from* (`asOf`, matrix version,
  the assignment set snapshot) is recorded, because a proposal reviewed a week later against
  a moved roster must be detectably stale.
- Proposals are partnership-scoped like everything else and visible to the same roles as the
  planner.
- Generation is audited (`roster_proposal.created`, actor per §8) but generation notifies
  nobody — a draft nobody asked to be told about is noise. Application notifies exactly as
  manual assignment does.
- Retention: discarded and superseded drafts age out; applied proposals are permanent (they
  are referenced from audit events).

**Not a scheduled job that resumes.** If P4's autonomy ladder adds a scheduled "draft a
proposal for next swing" step, it must be a `JobRegistry`-shaped idempotent scan: recompute,
dedupe on `(partnership, cc, input-state hash)`, never resume a half-generated plan. A
planner job that needed a queue or restart survival would trip the exact contract
`JobRegistry` states — and would be the signal to move it out of `JobRegistry`, not to bend
the rule.

## 7. Apply (P3) — the batch write

`POST /api/v1/roster-proposals/{id}/apply` — body: the accepted line ids and, per line with
a clash, an explicit `acknowledgeClash` (see below).

- **Per-line, not all-or-nothing.** Each accepted line goes through
  `AssignmentService.assign` — the same validation, the same audit event, the same crew
  notification, the same sync tombstone as a manual assignment. A line that fails
  (roster moved since generation, person suspended, slot filled) fails alone and reports
  the server's reason; the batch continues. This is the sync batch's semantics and ADM-11's
  no-second-queue instinct applied here: a half-applied proposal is an honest partial
  outcome, not a rollback.
- **The solver never acknowledges a clash on its own authority.** `acknowledgeClash` is a
  human decision per line, surfaced in the review UI with the clash's identity, and the
  acknowledgement lands in the audit event exactly as it does today. A proposal line with an
  unacknowledged clash is refused with the existing 409, per line.
- Applying re-validates against current state under the P0-c lock; the proposal's snapshot
  is advisory, the live database is the truth.
- The apply is one HTTP call but N assignments, N audit events, N notifications — no new
  notification kind is needed, though `ASSIGNMENT_CHANGED` (declared, never raised) becomes
  worth wiring when a re-plan moves a person between slots, so the crew member reads one
  "your slot changed" rather than a removal and an addition with no causal link.

## 8. Actors, audit and the autonomy ladder

### 8.1 Who did this?

`ActorKind` already distinguishes `human / ai_proposed / ai_automatic / system`, `Actor`
carries `aiModel` + `aiConfigVersion`, and the baseline schema enforces the pairing with a
CHECK: **an `ai_proposed` event must name the approving human.** That constraint is the
schema-level enforcement of "AI proposes, a human disposes", and Roster Assist inherits it:

- A **deterministic** proposal applied by a coordinator writes `human` assignment events
  (the coordinator decided; the solver is a calculator), with the proposal's business key in
  the event payload so provenance is reconstructible.
- An **LLM-narrated or LLM-influenced** proposal applied by a coordinator writes
  `ai_proposed` events naming the coordinator, with model and config version — the CHECK
  makes forgetting this impossible.
- `ai_automatic` is reserved and unused: no phase of this spec auto-applies a roster. If
  that ever changes it follows AIA-2's graduation protocol — measured precision against
  human decisions first, per-assist configuration on ADM-10, audited auto-actions surfaced
  in a spot-check queue — and it changes this spec first.

### 8.2 MCP

A `proposeRoster` MCP tool is in scope (read-only: generate and return a proposal). An
`applyProposal` MCP write tool is **not**: the MCP agent actor is `ai_automatic` holding
coordinator roles, and §8.1 says no automatic application exists. The tool calls
`ComplianceService`, never a repository (MCP-2), like every other tool.

### 8.3 The LLM's role, when §14.5 resolves

Narration only, inside the assistant panel's existing contract (read-only, role-scoped,
citations mandatory, audited, 503 until configured): *"CC25 fills 16 of 17. Slot 12 is
unfillable — both eligible AEs clash with NOR CC25. Accepting line 4 closes the M9 shortfall
on Shift 2 but puts Vidić cross-partnership."* Every sentence must be derivable from the
proposal's own `reasons[]`, `clashes[]` and quota deltas — the model composes prose over
computed facts and is given no authority to add facts (LLM-3's data-not-instructions rule,
applied to a proposal instead of a document).

## 9. UI (P3) — ADM-2, not a new module

Roster Assist is a planner feature, not a twelfth module. On a swing with open slots or
quota shortfalls, the planner offers **Draft a roster**. The result renders as a review pane
in the planner's own vocabulary:

- One row per proposed line: slot, person (link), window, score, `reasons[]` as prose, clash
  identity with a link to the clashing swing, quota footnotes it closes. Accept / skip per
  line; a clash line's accept is labelled with the acknowledgement it implies.
- A **before/after strip**: state counts, each quota's `actual/min`, open slots, gap-report
  length — the §4 what-if diff, which is also available standalone ("preview") before
  anything is applied.
- Unfillable slots listed with their reason — "no eligible candidate" is an answer, not an
  omission (the same honesty rule the mobile app's empty course list follows).
- Apply reports per-line outcomes in place; failures keep the server's words and a retry,
  matching the crew app's outbox pattern.

Two smaller planner changes ride along because the solver makes them urgent and the review
found coordinators already paying for them: surface leave on the ruler (a lane per slot's
assigned people, from the same standing-leave read the solver uses — `api.leave` is
currently dead code in the SPA), and make the quota drill-down actionable via
`suggestForQuota` (§5.2 step 3).

## 10. Non-goals

- **No availability calendar, no fatigue or rotation rules.** The only availability signal
  is standing leave; positive availability, day-count limits, min-rest and swing patterns
  have no data anywhere in the system. If O-8 lands an HR leave mirror, the solver reads it
  through the same repository and nothing here changes shape.
- **No creating swings, slots or people.** The solver fills the swing it is given.
- **No per-day quota evaluation** — that is ENG-1, and this spec neither depends on it nor
  pretends it exists.
- **No auto-apply, at any threshold, in any phase of this spec** (§8.1).
- **No new compliance semantics.** The solver consumes §5's answers; it never re-derives a
  cell state, and the review UI renders the engine's evaluation, not the solver's opinion of
  it (AUTH-1, same as every screen).

## 11. Open questions

1. **Module/section number.** This extends §5.4 and ADM-2; proposed spec home is a new §5.6
   ("Roster proposals") plus an ADM-2 subsection. Client confirmation wanted, as with ADM-11.
2. **Cross-partnership policy.** Today cross-partnership is a +3 nudge. Should a proposal
   be allowed to reach across partnerships by default, or behind an option the coordinator
   turns on per run? (Suggested: on by default, since the manual planner already offers
   cross-partnership candidates — but it is a visible policy choice.)
3. **Who may generate.** Suggestions are Coordinator/Sysadmin-gated; evaluation is six
   roles. Proposal generation is read-only, so the wider gate is defensible, but generation
   at coordinator-only keeps the surprise surface small. (Suggested: match `suggestions`.)
4. **Leave weight default** (P0-b) and the two new objective weights (§5.1) — numbers above
   are proposals; they are configuration and the client will want to feel them on real data
   (the `extracted` dataset makes that demonstrable — UNI CC24's known GPH shortfall is the
   canonical quota-repair test case).
5. **Does a discarded proposal need a reason taxonomy** (like MOB-10's closed reason set) or
   is a free note enough? Free note suggested; nobody reports on discard reasons yet.

## 12. Acceptance criteria

- Same inputs, same weights ⇒ identical proposal (determinism test, pure engine, no DB).
- A candidate on standing leave never appears in a proposal without a leave-labelled reason
  and never outranks an otherwise-equal free candidate (P0-b regression).
- Two concurrent applies of proposals sharing a slot produce one assignment and one
  per-line refusal, never a double booking (P0-c regression, real Postgres).
- On the `extracted` dataset, a proposal for UNI CC24 closes the Shift 1 GPH/Work-at-Heights
  shortfall when an eligible candidate exists, and reports it unfillable when none does.
- The what-if endpoint leaves zero rows changed, zero audit events, zero notifications.
- Applying a proposal produces exactly the audit events, notifications and sync tombstones
  that the same assignments made by hand would produce, plus the proposal key in each
  event's payload.
- An `ai_proposed` assignment event without an approving human is impossible (already
  enforced by the baseline CHECK; the test asserts the wiring passes the human through).
- ADM-10 weight changes visibly re-rank both suggestions and proposals (P0-a regression).
