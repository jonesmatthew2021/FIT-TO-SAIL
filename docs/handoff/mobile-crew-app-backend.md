# Handoff: what the crew app's new screens need from the backend

**From:** `mobile/` — the Nocturne restyle plus MOB-0, MOB-5 to MOB-11 (July 2026)
**To:** `backend/`, and one item for `admin-web/`
**Status:** the client half is built, tested and running on a simulator. Every item below is the
server half of a screen that already exists.

**Two of the seven operations have landed** (28 July 2026): `requirement.progress` and
`requirement.help` are implemented, tested and verified against the live backend. §1 records what
they do and what changed; the remaining five are still rejected, still per operation, and still
listed below.

---

## Why this document exists

The design handoff for the crew app asked for two things: a restyle of the four shipped screens
onto Nocturne, and eight new ones. The restyle needed nothing new from the server. The eight new
screens needed rather a lot, and none of it exists.

Rather than stub the screens or invent the data, the client was built to **degrade honestly**:

* A course list with no course catalogue says it has no dates and offers the one action that does
  exist — asking the office.
* A parse result with no LLM provider opens at its third confidence level with empty fields, which
  is exactly LLM-2's launch posture and was one of the three cases the screen was designed for.
* Home draws no credit tiles rather than inventing "14 months, never sailed short".
* A supervisor with no team endpoint is told the app does not yet send them their watch — *not*
  shown an empty list, which they would read as "everyone is fine".

And the seven new write paths are **already queued and already posted**. The device raises them
through the existing §7.6 outbox, and `SyncService` rejects the unimplemented ones with
`Unsupported operation type '<kind>'`, per operation, without failing the batch. The crew member
sees `Couldn't send` with that string and a Retry. That is the intended state: nothing is silently
dropped, evidence submissions and read-marks keep flowing, and the failure message names the work.

It is also a state with a short half-life, and the first two are out of it. Whoever taps the button
reads that string, and "Unsupported operation type 'requirement.progress'" is a developer's sentence
on a crew member's phone.

So this document is the list of what has to become true for those messages to stop.

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
| `course.seat_request` | `requirementId`, `subjectRef` (course option id), `starts`, `finishes` | Register interest in a specific course date (see §3). |
| `course.waitlist` | as above | Same, for a date with no seats. |
| `evidence.reading` | `submissionPublicId`, `requirementId`, `certificateNumber`, `issued`, `expires` | The fields as the crew member confirmed or corrected them, against a submission `evidence.submit` already registered. See §4. |
| `register.exemption_request` | `requirementId`, `ccId`, `reason`, `note`, `attachedOpIds[]` | Create a §6.4 register record of type exemption, in `raised`, addressed to a Compliance Lead. See §5. |
| `attestation.sign_off` | `assignmentId`, `subjectRef` (cc id), `declarations[]` (the ids confirmed) | Record the pre-sail declaration. See §6. |

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

Still outstanding for these two, and deliberately not done:

* **The statement is not in the sync payload.** The table carries `updated_seq` and a tombstone
  trigger from birth, so it is ready to be replicated, but the device's own `CrewIntents` row is
  still the only place the answer shows on screen. That means a reinstall loses "Course booked" and
  Home asks again. It belongs with §2's payload additions.
* **Nothing in `admin-web` shows a statement.** The coordinator's notification is the whole
  surfacing today. This document previously said "surfaces on ADM-7 / the person page as *in
  progress*"; the person page is the right home for it and it is not built.

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

## 3. A course catalogue — MOB-8

The screen lists **only dates that resolve the requirement before it lapses** — never a generic
catalogue. The filtering is the server's; the client renders what it is given, in order.

Per option: `id`, `starts`, `finishes` (calendar dates), `provider`, `location`, `durationLabel`,
`seats`, `note`, `recommended`, `waitlistOnly`.

`note` is the sentence under the date — "Clear of your leave · 11 days before expiry" — and it is
where the two facts that make an option choosable live: whether it collides with the person's leave
and how much margin it leaves. Both need the person's roster, so both are the server's to compute.

Where this sits in the domain is an open question worth answering before building it: a course
catalogue is not in the §4 model, and it may be a third-party provider feed rather than a CREWCOMP
table.

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

## 5. A mobile-originated exemption request — MOB-10

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

## 6. Attestation — MOB-9

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

## 7. A team endpoint — MOB-11

`GET /api/v1/me/team`, returning per member: `sam`, `name`, `worstState` (a §5.1 cell state),
`reason` (one line), `inHand` (something is already moving), `nudgedAt`.

**The privacy rule is the shape of the payload, not the discretion of the screen.** A supervisor
sees requirement status and one line of reason — never a document, never medical detail, never why
a certificate lapsed. A device that received the detail and chose not to draw it would leak it to
anyone who read the local database, which is precisely what SEC-12 encrypts against. The client's
`TeamMember` has nowhere to put a document for exactly this reason; please keep the DTO the same
shape.

Also needed: **who supervises whom**. Nothing in the payload says a person has a supervisory role.
The tab is currently switched on by a `kDebugMode`-gated `--dart-define`
(`CREWCOMP_DEV_SUPERVISOR`), which a release build cannot reach. This is the identity spike's to
resolve properly, but the team scoping is the backend's either way — a supervisor's watch is a
row-scoping question (AUTH-2) and must be enforced centrally, not by the endpoint.

`team.nudge` sends a push and is logged. The person nudged should be able to see that they were and
by whom; a nudge nobody can trace is a way to harass someone quietly.

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
  and `notification_delivery` is ready for per-channel records. `team.nudge` needs this to be worth
  anything.
* **The camera.** MOB-4's capture branch has never run — a simulator has no camera.
* **`admin-web`:** a crew-raised exemption and an attestation both want somewhere to land in the
  console. The register (ADM-4) probably absorbs the first; the second may want a column on the
  planner rather than a screen of its own.

---

## What the client will need to change when this lands

Small, and deliberately concentrated:

* `mobile/lib/src/ui/app_state.dart` — `credits`, `courseOptionsFor`, `readingFor` and `team` are
  four stubs returning nothing. They are methods on `AppState` rather than constants inside the
  screens precisely so that landing the backend work is a change in one file.
* `mobile/lib/src/domain/urgency.dart` — `expiryLeadDaysDefault` becomes a payload field.
* `mobile/lib/src/ui/screens.dart` — `HomeView._headline` and `readinessFrom` are deleted in favour
  of the payload's own.
* `mobile/lib/src/ui/action_screens.dart` — `declarationsFor` keeps composing the supporting facts
  but takes its wording from the payload.
* `mobile/lib/src/api/schema.g.dart` regenerates from the OpenAPI schema (DEV-2); nothing is
  hand-written.

The seven operations need no client change at all. They are already being sent — which the two that
landed demonstrated: `requirement.progress` and `requirement.help` went from rejected to applied
with nothing touched in `mobile/lib` but the generated schema file.
