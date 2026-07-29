# Handoff: what the crew app's new screens need from the backend

**From:** `mobile/` — the Nocturne restyle plus MOB-0, MOB-5 to MOB-11 (July 2026)
**To:** `backend/`, and one item for `admin-web/`
**Status:** the client half is built, tested and running on a simulator. Every item below is the
server half of a screen that already exists.

**All seven operations have landed** (29 July 2026). The last three — `course.seat_request`,
`course.waitlist` and `team.nudge` — went in with the decisions they were waiting on: a
CREWCOMP-owned course catalogue behind a `CourseCatalogue` port, a seat request modelled as a
*statement* rather than a booking, and `vessel_master` reused as the supervisory role with a watch
derived from co-assignment. Nothing on this queue is rejected any more.

What is still outstanding is **§4** (extraction fields on a submission, waiting on §14.5's provider
choice), **§2's credits**, and the two items in §9 that are not the backend's at all.

---

## Why this document exists

The design handoff for the crew app asked for two things: a restyle of the four shipped screens
onto Nocturne, and eight new ones. The restyle needed nothing new from the server. The eight new
screens needed rather a lot, and none of it exists.

Rather than stub the screens or invent the data, the client was built to **degrade honestly**:

* ~~A course list with no course catalogue says it has no dates and offers the one action that
  does exist — asking the office.~~ **Landed.** The empty state survives and now means something
  narrower and more useful: every date the catalogue holds either finishes too late or runs while
  this person is at sea.
* A parse result with no LLM provider opens at its third confidence level with empty fields, which
  is exactly LLM-2's launch posture and was one of the three cases the screen was designed for.
* Home draws no credit tiles rather than inventing "14 months, never sailed short".
* ~~A supervisor with no team endpoint is told the app does not yet send them their watch — *not*
  shown an empty list, which they would read as "everyone is fine".~~ **Landed.** The distinction
  survives as two different empty states: "No swing under way" and "Nobody else on CC24".

And the seven new write paths are **already queued and already posted**. The device raises them
through the existing §7.6 outbox, and `SyncService` rejects the unimplemented ones with
`Unsupported operation type '<kind>'`, per operation, without failing the batch. The crew member
sees `Couldn't send` with that string and a Retry. That is the intended state: nothing is silently
dropped, evidence submissions and read-marks keep flowing, and the failure message names the work.

It was a state with a short half-life, and **nothing is in it any more**. Whoever taps a button
reads that string, and "Unsupported operation type 'requirement.progress'" was a developer's
sentence on a crew member's phone.

What remains below is the list of screens still waiting on something, which is now §4 and the two
non-backend items in §9.

---

## 1. Seven sync operations (§7.6, `SyncService.apply`)

The device posts these through `POST /api/v1/sync/queue` alongside `evidence.submit` and
`notification.read`. Every one carries a client-minted `opId` that is **the idempotency key** — a
retry after a dropped connection re-posts the same id, and the server must treat the second
delivery as a no-op rather than as a second seat request.

None of them may write a holding, a cell state or a roll-up. They are *statements by the crew
member*, and the compliance answer stays the engine's (AUTH-1, §7.5).

| `type` | Payload beyond `opId` | What the server should do |
|---|---|---|
| ~~`requirement.progress`~~ | `requirementId` | **Done** — see §1.1. |
| ~~`requirement.help`~~ | `requirementId` | **Done** — see §1.1. |
| ~~`course.seat_request`~~ | `requirementId`, `subjectRef` (course option id) | **Done** — see §1.4. |
| ~~`course.waitlist`~~ | as above | **Done** — see §1.4. |
| `evidence.reading` | `submissionPublicId`, `requirementId`, `certificateNumber`, `issued`, `expires` | The fields as the crew member confirmed or corrected them, against a submission `evidence.submit` already registered. See §4. |
| ~~`register.exemption_request`~~ | `requirementId`, `ccId`, `reason`, `note`, `attachedOpIds[]` | **Done** — see §5. |
| ~~`attestation.sign_off`~~ | `assignmentId`, `declarations[]` | **Done** — see §6. |
| ~~`team.nudge`~~ | `targetSam` (and `note`) | **Done** — see §7. |

Every one is a business mutation and therefore audited with the authenticated actor (AUTH-3). The
actor is a human — none of these is AI-proposed.

**Reason codes for `register.exemption_request`** are a closed set the client already sends:
`no_seat`, `medical_personal`, `with_authority`. If the register wants different ones, they are a
one-line change in `mobile/lib/src/ui/action_screens.dart` — say so and it will move.

### 1.1 The two that landed — `requirement.progress` and `requirement.help`

Implemented 28 July 2026. **No client change was needed**: the app was already posting both, with
the `requirementId` the DTO now reads.

They are stored as a **crew statement** (`crew_statement`, V5) — a new, deliberately small third
kind of client-originated write beside the evidence submission and the read-mark. A statement is
what the crew member *said*, and it is kept well away from the evaluation path:

* Nothing in `au.crewcomp.engine` reads the table. A person who says "course booked" against a
  lapsed medical still evaluates as a gap, on their phone and on the planner, because they still do
  not hold it (AUTH-1, §7.5). `SyncIT` asserts the roll-up and the holding are untouched.
* It is keyed on the device's `opId`, unique in the schema, so the outbox can replay freely.
* It is write-once. Changing your mind is a new statement — the office needs to see the answer move,
  and when.

Two decisions worth challenging if they are wrong:

**`requirement.help` does create a row**, which contradicts what this document said before
("nothing may be recorded against the person"). The screen's promise — *"Nothing is recorded against
you for asking for help"* — is read as a promise about **consequence**: no compliance state moves,
the engine never sees it, and no scan changes behaviour because of it. It is not read as a promise
that the request evaporates. With no back-office accounts yet (ADR 0003) the notification fan-out
resolves to nobody, so if the row did not exist the tap would be answered "Sent to the office" and
reach no office at all. A request nobody can find is a request nobody can action, which makes the
button a placebo. **If the intended reading was the stronger one, this is one `if` to change.**

**`requirement.progress` suppresses the crew-facing expiry warning**, and only that one. The
statement records `about_expiry` — the expiry as the holding stood when the person answered — and
the expiry scan skips a crew warning for exactly that (person, requirement, expiry). This is the
same "the key includes the value, not just the subject" rule as the dedupe key beside it: renew the
certificate, the expiry moves, no statement matches, and the chasing resumes on its own. The
**coordinator's** `expiry_affects_roster` notice is *not* suppressed — a booked course is not a held
certificate, and someone deciding whether to crew a swing needs the risk rather than the
reassurance.

Both raise a per-role notification to Crew Coordinators — `crew_progress_reported` and
`crew_help_requested`, both new `NotificationKind`s, both SEC-13-safe in the title.

### 1.2 ADM-11 — where they land

A notification is a "something happened" signal and nothing more: it is addressed to whoever held the
role at the moment it was raised, one person marks it read, and no query answers *what has the crew
asked us for that nobody has dealt with*. So the statements are also a **queue**, and `crew_statement`
gained a status (V6).

`open` → `actioned` | `dismissed`, both terminal, both requiring a note. No reopen, matching the
register: a crew member still waiting asks again, and the second statement carries its own date.

The queue is generic over kinds on purpose — three of the five operations still outstanding in §1
(`course.seat_request`, `course.waitlist`, `register.exemption_request`) land in the same screen with
no second worklist.

**Dismissal is the safety valve, and the reason the queue could not wait.** §1.1's suppression
silences a crew member's expiry warning on their word alone; dismissing the request resumes it. The
console says so on the form, above the button, because it is the one control in the back office whose
effect lands on somebody else's phone.

`GET /api/v1/crew-requests`, `…/open-count`, `POST …/{id}/action`, `POST …/{id}/dismiss`. Readable by
every back-office role; decidable by Crew Coordinator, Workflow Manager and System Administrator —
not the Data Steward, because restarting the chasing of a named crew member is not something to do
while tidying data.

**§6 enumerates ADM-1 to ADM-10 and stops.** ADM-11 is the first module here the spec did not ask
for, and it is worth confirming with the client rather than quietly numbering.

### 1.3 The loop closes — the decision reaches the phone

Both halves, because either alone is half a feature:

**`crewStatements[]` in the snapshot and the delta.** The statement is now a replicated,
person-scoped row like a holding or a notification — same trigger-assigned cursor, same tombstone
path, and `crew_statement` added to `currentCursor()` (a person-scoped table left out of that keeps
its rows above the cursor the client is handed, so the same rows arrive in every delta forever,
harmlessly and permanently). It carries `opId`, and **that is the join**: the device's own
queue-entry id, echoed back, so the app matches a statement to the tap that produced it without a
second identifier existing anywhere.

**A §9 notification back to the crew member** — `crew_request_actioned` / `crew_request_dismissed`,
crew-audience, SEC-13-safe in the title, with the coordinator's note in the body. The statement row
is the record; this is the prompt. Someone who never opens the notification still sees the decision
on the requirement.

On the device the two halves of the record merge into one `Answer` (`answersFrom` in
`ui/app_state.dart`), with five states: *queued*, *sent*, *actioned*, *dismissed*, *failed*. The
server's row wins wherever both exist, because it is the only one that can carry a decision. The
device's own intent is deleted as soon as its statement arrives — one tap, one row.

**A dismissal puts the ask back on the card**, matching the server: `Answer.stands` is false for a
dismissal exactly as the suppression query excludes one, so the phone offering "Course booked" again
and the expiry scan chasing again are the same fact expressed twice rather than two rules that could
drift.

**The decision note is crew-facing, by design.** ADM-11's form labels the field *"the crew member
reads this"* and its placeholder is written to the person. A note composed believing it were internal
and then shown to its subject would be the wrong way round; making it crew-facing up front is what
turns "no record" into "we could not find your booking — can you forward the confirmation?".

Still outstanding for these two:

* **Nothing in `admin-web` shows a statement on the person's page.** ADM-11's queue is the whole
  surfacing; this document previously said "surfaces on ADM-7 / the person page as *in progress*",
  and the person page is still the right second home for it.

### 1.4 MOB-8's two — `course.seat_request` and `course.waitlist` — **done**

Implemented 29 July 2026, and again with **no client change**: the payload the app was already
sending (`requirementId` plus `subjectRef`) is the payload the server grew a reader for.

**They are statements, not bookings**, and that was the decision they were waiting on. A crew member
cannot commit a training budget or bind a provider, so asking for a seat is an *ask* — it lands in
ADM-11 beside the other two, generic over kinds, with no second screen. Nothing holds a seat:
`course_option.seats` is what the provider last said, nothing decrements it, and a request that
promised a place would be this system asserting a reservation with a third party it has no contract
with.

Two things follow, and both are visible:

* **They are separate kinds, not one with a flag.** Booking a seat and chasing a waitlist are
  different jobs for the office, and ADM-11 says which it is before a coordinator opens anything.
* **They earn no silence until the office answers.** `course_booked` suppresses the crew expiry
  warning on the crew member's own word, because whether they have booked a course is a fact only
  they know. A seat *request* suppresses nothing until a coordinator actions it: nothing is booked,
  the certificate is still lapsing, and stopping the reminders on an unanswered ask would quietly
  drop someone. The rule is `CrewStatementKind.suppression` — `NEVER` / `ON_WORD` / `ON_ACTION` —
  stated once rather than left as an `if` in the scan's query.

The statement stores both `subject_ref` (the catalogue key) and `subject_label` (**the server's**
rendering of the date, in the display format). The label is denormalised for the same reason the
attestation's signature line is composed server-side: a coordinator opening the request in three
weeks needs to know which course was meant even after the option has been withdrawn, and what
appears in the office's record should not be composed on the phone of the person asking.

---

## 2. Fields the sync payload should carry (§10.3)

All of these are read-only additions to `SyncSnapshotDto` / the delta. Each has a screen behind it
that renders correctly without it today.

### 2.1 `standing.readiness { ready, total }` — MOB-0's ring

The device currently counts this itself, by partitioning the server's own evaluated cells on the
same `needsAttention` grouping the certifications list has always used, and excluding `na`. That is
a summary of answers rather than a new one, so it does not break AUTH-1 — but *which states count
as ready* is a compliance judgement and belongs with the engine. Sending it also lets the two
numbers stop being derivable at all, which is the stronger position.

### 2.2 `standing.headline` — MOB-0's one sentence

"You can sail this swing." is currently mapped from `standing.evaluation.rollUp` by a `switch` in
`HomeView._headline`. It is a faithful mapping, but it is a compliance sentence living in a client.
A server-supplied string removes the last place the app phrases a verdict.

### 2.3 `credits { streakLabel, streakCaption, secondaryLabel, secondaryCaption }` — MOB-0's tiles

**The one thing with no client-side fallback at all.** Both credits need history the device has
never been sent:

* "14 months · never sailed short" — the longest run of swings sailed with no gap or exemption.
* "3 of 4 · renewed early" — renewals whose new certificate landed before the old one expired.

Neither is derivable from a snapshot of the present, so Home simply omits the row. This is the
*motivational half* of the screen and the design's answer to "crew aren't motivated to act", so it
is the highest-value item in this document even though it is not the most urgent.

They must be **factual and positive**. A credit computed to look encouraging rather than to be true
is worse than none — a crew member who catches it once will not read the screen again.

### 2.4 `expiryLeadDays` — the urgency bar's denominator

The progress fill under a countdown is "how much of the warning window has gone". The device
mirrors `Planning.DEFAULT_EXPIRY_LEAD_DAYS = 90` as a constant, exactly as `admin-web` mirrors
`EXPIRY_LEAD_DAYS_DEFAULT`, because the live value is ADM-10 configuration behind a role gate. A
stale horizon moves a bar and nothing else, but it is one field.

### 2.5 `standing.declarations[]` — MOB-9's wording

What a crew member is asked to attest to is a company policy question with legal weight, and it is
currently a Dart constant carrying the design handoff's copy. Each line wants
`{ id, text }`; the device supplies the supporting fact under it from the person's own evaluated
cells ("6 requirements · 5 held, 1 renewing").

---

## 3. A course catalogue — MOB-8 — **done**

Implemented 29 July 2026. `course_option` (V10), a `CourseCatalogue` port with a database-backed
adapter, `CourseOffers` for the filtering and `CourseOfferService` to gather what it needs.

**The open question was where this sits in the domain, and the answer is "ours, behind a seam".** A
course catalogue is still not in the §4 model and may one day be a provider feed. It is a table
today for two reasons, neither permanent: no provider integration has been identified, and writing
one against a hypothetical API is the same mistake as writing a cloud stack before ADR 0005. What
*is* permanent is the boundary — everything above reads `CourseCatalogue`, so a feed replaces one
class. That boundary was worth getting right first because the interesting half of MOB-8 is ours
whoever supplies the dates: an option is only worth showing if it beats the crew member's expiry and
does not fall inside a swing they are aboard for, and both facts come from our roster.

**Two exclusions, and the difference between them is the model.** At sea is *impossible*, so an
option overlapping an assignment is dropped — offering it is offering a mistake. On leave is merely
*unwelcome*, so it is kept and labelled: whether a course is worth a few days off is the crew
member's call, and silently hiding the only date that beats an expiry would be a compliance system
making a personal decision on somebody's behalf.

`note` is composed server-side — "Clear of your leave · 11 days before expiry" — and `recommended`
marks at most one option, the soonest with a seat that is clear of leave. Nothing is recommended
when every date is full or on leave: promoting the least-bad option would say the system had found
something when it had not.

**The offers ride in the sync payload, recomputed on every snapshot and delta like `standing`.** An
offer is a derived answer rather than a row — it changes when the catalogue changes, when the roster
changes, when a certificate is renewed and when a day passes, none of which move a cursor — so
there is deliberately no cursor and no tombstone for one, and the whole set is replaced each time.
Which requirements get offers is driven off the standing evaluation's attention cells, the same
`needsAttention` grouping the app uses, so there can be no row with a "Book a course" button and no
dates behind it.

Two things worth knowing about what this cannot do:

* **An empty list is a real and common answer.** For anybody rostered across a whole swing, an
  `expiring` cell can never have an attendable date: a course finishing before the expiry finishes
  before the swing ends, and they are at sea for all of it. That is not a gap in the feature — it is
  precisely the squeeze MOB-10's exemption request exists for, and the empty state now says so.
* **Maintenance is MCP-only, deliberately.** `CourseCatalogueService` is the validated, audited,
  role-checked write path and `OperationsTools` exposes it (MCP-2). There is no ADM screen, because
  a console screen is the most expensive thing to build against an answer that may still move. If
  the catalogue settles as ours, that service is what a screen would call; if it becomes a feed, it
  is what the importer calls.

---

## 4. Extraction fields on a submission — MOB-7

`EvidenceSubmissionDto` carries the upload's progress and its verification status, and nothing
about what §8 read out of the document. MOB-7 needs:

```
extraction {
  confidence: "high" | "review" | "unmatched",
  requirementId, certificateNumber, issued, expires
}
```

Nullable throughout, and **null is the normal case today** — with no LLM provider chosen (§14.5)
the extractor reports zero confidence and every document goes to a human. The screen already
handles that: it opens with the requirement matched by the device's own hint, the fields empty
behind "Not read — add it", and a tag that says `Matched by you · not yet read`.

Two related notes:

* `evidence.reading` (§1) is a **separate operation** from `evidence.submit` on purpose. The bytes
  upload at sea; the reading is checked when the crew member next looks at their phone, possibly
  days later and on a different connection. Folding the fields into the submit payload would mean
  holding the upload until someone confirms it.
* The corrected fields are a *proposal*, not an acceptance. They should reach ADM-9 as the crew
  member's answer alongside whatever the extractor said, and a Data Steward still decides (LLM-1).

---

## 5. A mobile-originated exemption request — MOB-10 — **done**

Implemented 28 July 2026. `RegisterService.raiseCrewExemption` writes a real §6.4 record:
`Exemption Request - PW`, status `Open - PW`, against the crew member's own requirement and their
own swing.

**This is the one client-originated write that changes what the engine answers** — and it does so
only by asking. §5.1 step 4's overlay moves the cell to `pending` the moment it is raised, and to
`exempt` only when a Workflow Manager approves. That is also how the decision reaches the phone:
through the compliance evaluation the payload already carries, which is a better answer than
notifying a crew member about a register record they cannot open.

Five things differ from a coordinator-raised record, each recorded on the method:

* **Authorisation is scope, not role.** A crew member holds none of the register's `RAISERS`, so the
  check is their own person plus an assignment they are actually on.
* **The `ccId` is verified, not accepted.** It is the one field the device chooses that names
  something outside the crew member.
* **A late submission is acknowledged rather than refused.** Q17's acknowledgement is a deliberate
  human act and tapping "Send the request" is one; there is no round trip on a sync queue to ask
  twice, and refusing would leave someone who has just found a problem with nothing to do about it.
  The trail records that it came from a device after the cutoff.
* **Reason and note become a PW note.** A register record has no "why" field and inventing one would
  be the wrong shape — what the crew member wrote is a statement by a party, which is what a note is.
* **Earlier attempts are resolved into a sentence.** The `opId`s the device attaches become "already
  tried: asked the office for help arranging it"; ids with no server record are dropped, because an
  operation the server never accepted did not happen.

`register_record.crew_op_id` (V7) is the idempotency key and doubles as provenance — a record with
one was raised from a phone. Still open: **nothing in ADM-4 badges that yet**; the trail says so in
words only.

### 5.1 The original text, for the open question it still contains

This one contradicts a position the app previously took, so it is worth being explicit. The
requirement-detail screen deliberately *names* a register record and cannot open it, because the
register is a back-office workflow (§6.4). MOB-10 does not change that: the crew member is not
working the register, they are **raising** one record against one requirement, with a reason from a
closed set and an optional note.

What the client attaches automatically, and what the info band promises: the `opId`s of any earlier
`course.seat_request`, `course.waitlist` and `requirement.help` against the same requirement. The
server should link them onto the register record so the Compliance Lead can see the person already
tried, without the person having to write it out. That is the whole reason the crew-intent table
keeps settled entries for 30 days.

Open question for the register: does a crew-raised request enter the same workflow as a
coordinator-raised one, or does it need a triage state first?

---

## 6. Attestation — MOB-9 — **done**

Implemented 28 July 2026. `attestation` + `attestation_declaration` (V8), `AttestationService`, and
the record comes back in the sync payload.

Both constraints this document named are met, and a third turned out to matter:

* **The timestamp is the server's**, in the vessel's timezone. It is also *formatted* server-side —
  `signedAtDisplay`, "28 Jul 2026, 07:05 AWST" — for the same reason `serverToday` is sent rather
  than computed: a phone knows neither the operating timezone (O-11) nor the admin date override,
  and this string sits on a legal record.
* **The signature is not a biometric assertion.** `attestation.assertion` is the column it will land
  in and is null on this build; the record means "confirmed on this device by the authenticated crew
  member", stored as such.
* **What was ticked is stored line by line**, and this was not merely tidiness. The screen used to
  rebuild its checkboxes from "which lines are already true from the record", so reopening a signed
  attestation showed a *different* set from the one signed — every line confirmed by hand came back
  empty, on a record whose entire purpose is being able to say what somebody confirmed. The payload's
  `declarations[]` is what the screen now renders.

The declaration *set* is still §2.5's — a client constant. When it becomes server-owned, the
**wording as presented** should be stored beside each id, because what someone signed is the
sentence and not the key.

### 6.1 The original text

An entirely new record: person, assignment, the declaration ids confirmed, and a timestamp.

Two constraints the screen already honours and the server must complete:

* **The timestamp is the server's**, in the vessel's business timezone (AWST, O-11). A legal
  declaration timestamped from a phone clock is worth nothing, so the signature block deliberately
  does not print one — it says "the office records the time it arrives".
* **The signature is not a biometric assertion yet.** The mock shows "Face ID · 28 Jul 2026, 07:05
  AWST"; there is no biometric binding on this build (ADR 0002 wants `local_auth`, and it is the
  device spike's). When it lands, the assertion should travel with the operation and be stored with
  the record. Until then the record is "confirmed on this device by the authenticated crew member",
  which is weaker and should be stored as such rather than as a signature.

The declaration set itself is §2.5.

---

## 7. A team endpoint — MOB-11 — **done**

Implemented 29 July 2026. `GET /api/v1/me/team`, `TeamService`, and `team.nudge` on the sync queue.

**Who supervises whom was the decision, and the answer needed no new structure.** A supervisor's
team is *the people rostered onto a swing they are also rostered onto*, derived from assignments
that already exist. Change the roster and the watch changes with it, which is the correct behaviour
on a vessel where the team is the swing — and there is no org chart to drift out of step with it.

The role is `vessel_master`, which §3 already defines as restricted and read-only. It did not need
inventing; what it needed was a **narrower scope than the one it has**. A Vessel Master reads their
whole partnership, and a partnership is far more people than the crew on their deck — so the watch
uses co-assignment instead, and `TeamService.supervisedCrew` is the single definition both the list
and the nudge go through. That matters: a supervisor who could nudge anyone in their partnership
would have a wider write than read, which is backwards.

**The endpoint answers 403 for anybody who does not hold the role, and that 403 is the feature.**
The app asks on every sync and stores which answer it got, which is what replaced the
`kDebugMode`-gated `CREWCOMP_DEV_SUPERVISOR` flag — a compile-time guess a release build could
never reach, about a fact that belongs to §3's role model. The Team tab now appears because the
server said so.

The privacy rule held: `TeamMemberDto` is `sam`, `name`, `worstState`, `reason`, `inHand`,
`nudgedAt` and nothing else, and an IT asserts that field set exactly. `reason` names a requirement
code and a state — "PS-04 not confirmed", "MS-02 expires 16 Aug 2026" — never a document, never a
medical detail, never why something lapsed.

Three smaller things, each of which cost a decision:

* **A missing swing and an empty watch are different answers.** `ccId` null means the supervisor is
  rostered nowhere; an empty `members` with a swing means they are alone on it. The app renders them
  as two different empty states, because an empty list a supervisor reads as *everyone is fine* is
  the worst thing this tab could do.
* **`inHand` is wider than the expiry suppression.** Asking for help is not a reason to stop the
  expiry reminders, but it is very much a reason not to nudge someone again.
* **A nudge is traceable and there is no setting that changes that.** The person nudged gets a
  §9 notification naming who sent it and carrying their note, keyed per sender, swing and day so a
  stuck finger — or a replayed outbox entry — is still one nudge. It is audited whether or not there
  was an account to deliver to, with `delivered` recording which: that a supervisor chased a named
  crew member is the traceability, and it must not wait on the identity spike. Push (MOB-3) would be
  a second channel for the same record, not a prerequisite.

**One thing this fixed on the way past.** `AccessPolicy` gave a Vessel Master `DataScope.Partnerships`
with no room for their own person, so a crew member who was promoted would have found their own app
quietly stopping — every person-scoped read the crew app makes passes a person id and no
partnership, which that scope denies. `Partnerships` now carries `ownPersonId`, and an IT pins that a
supervising crew member can still sync.

---

## 8. Notification bodies contain ISO dates

Small, visible, and a one-line fix at each call site. `NotificationService` composes crew-facing
bodies with a raw `LocalDate.toString()`, so an expiry warning arrives reading

> Your Sea Survival certificate expires on 2026-08-14, before the end of your current swing.

The design rule is that **a crew member is never shown an ISO date** — always `14 Aug 2026`, and
where it helps, `in 17 days`. `08-14` and `14-08` are two different days to two people in the same
crew room, and the format is the only thing that tells them apart.

The client rewrites them at display time (`humaniseDates` in `domain/calendar.dart`) so the screen
is right today. That function is a date-format substitution and cannot alter anything that is not
shaped like a calendar date, but it is still a client rewriting server prose. Compose in the
display format at the source and it becomes a no-op that can be deleted.

**Every string added since follows the rule at the source**: MOB-11's `reason` ("MS-02 expires
16 Aug 2026"), the crew statement's `subject_label` ("20–21 Aug 2026 · …") and MOB-9's
`signedAtDisplay`. The three of them are also the reason this is worth finishing rather than living
with — two conventions in one notification list is worse than either.

The same applies to the admin console's notification centre, which shows the same strings.

---

## 9. Not the backend's, but blocking the same screens

Listed here so the whole picture is in one place.

* **The OS share target (MOB-6).** The design's primary intake route is sharing a certificate PDF
  into Attest from Mail, Files or WhatsApp *without opening the app*. That is an iOS Share
  Extension plus an Android intent filter — native work, an app group for the staged file, and a
  cold-start path into the existing outbox. The mock's four-up grid with Files / Print / More is
  the operating system's sheet; the client draws the in-app half of it (Files / Photos / Camera at
  the same geometry) rather than a picture of the OS's.
* **Push delivery (MOB-3).** Still no APNs/FCM sender. The in-app list remains the source of truth
  and `notification_delivery` is ready for per-channel records. `team.nudge` shipped without it
  deliberately — a nudge that appears in the crew member's notification list is a real nudge, and
  waiting for a delivery channel to build the record would have been the wrong way round.
* **The camera.** MOB-4's capture branch has never run — a simulator has no camera.
* **`admin-web`:** ~~a crew-raised exemption~~ now badges *from the app* on ADM-4's list and CSV,
  from `register_record.crew_op_id`. It enters the **same** §6.4 workflow with the same statuses and
  deliberately has no triage state in front of it — a second definition of "open" would be a second
  queue to forget, and the Compliance Lead already has the reason, the crew member's own words and
  a resolved list of what they had already tried. An attestation still has nowhere to land; it may
  want a column on the planner rather than a screen of its own.

---

## What the client will need to change when this lands

Two stubs left, down from four:

* `mobile/lib/src/ui/app_state.dart` — `credits` and `readingFor` still return nothing.
  `courseOptionsFor` and `team` now read replicas the sync fills. They are methods on `AppState`
  rather than constants inside the screens precisely so that landing the backend work is a change
  in one file, which is what the last two turned out to be.
* `mobile/lib/src/domain/urgency.dart` — `expiryLeadDaysDefault` becomes a payload field.
* `mobile/lib/src/ui/screens.dart` — `HomeView._headline` and `readinessFrom` are deleted in favour
  of the payload's own.
* `mobile/lib/src/ui/action_screens.dart` — `declarationsFor` keeps composing the supporting facts
  but takes its wording from the payload.
* `mobile/lib/src/api/schema.g.dart` regenerates from the OpenAPI schema (DEV-2); nothing is
  hand-written.

**None of the seven operations needed a client change**, which is the thing worth remembering from
this handoff. All seven went from rejected to applied with nothing touched in `mobile/lib` but the
generated schema file and, for `team.nudge`, one field name on a payload the screen was already
building. Writing the client against the contract it wanted rather than the one that existed is what
made that true.
