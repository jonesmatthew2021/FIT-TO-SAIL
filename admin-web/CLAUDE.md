# admin-web/ — React + TypeScript admin SPA

**All ten §6 modules exist**: ADM-1 dashboard, ADM-2 swing planner, ADM-3 matrix, ADM-4 register,
ADM-5 people & holdings, ADM-6 requirements catalogue, ADM-7 exceptions worklist, ADM-8 notifications
centre, ADM-9 evidence verification queue, ADM-10 administration — plus **ADM-11 crew requests**,
which §6 does not enumerate. It exists because the crew app's one-tap answers had nowhere to land: a
notification tells whoever held the role at that moment and is marked read by one of them, and no
query answered "what has the crew asked us for that nobody has dealt with". **Worth confirming with
the client** — it is the first module here that the spec did not ask for. `NotBuilt.tsx` survives as the
catch-all route and as the mechanism — a module added to `NAV_ITEMS` with `built: false` routes there
with its blocker named — which is what kept the remaining scope on screen while four were
outstanding.

What is *not* here is a login flow (the identity spike's) and any E2E test. Two screens carry a
visible honesty note rather than a gap: ADM-9 says so when nothing has been extracted, because no LLM
provider is configured (§14.5); ADM-10 labels the accounts it creates as SEC-1b transitional.

## Stack (decided)

Vite 7 + React 19 + TypeScript 5.9, `strict` plus `noUncheckedIndexedAccess` and
`exactOptionalPropertyTypes`. Runtime dependencies, all of them: `react`, `react-dom`,
`react-router-dom`, `@tanstack/react-query` (server state), `@tanstack/react-table` (headless,
for the dense grids). No component library and no CSS framework — `src/styles.css` is the whole
of it.

Inter is **self-hosted** in `public/fonts/` (latin + latin-ext variable woff2, ~134 KB). Self-hosted
rather than linked: a CDN font is a third party that sees every page view, and `npm` font packages
would be two more dependencies in a tree this project deliberately keeps short. `latin-ext` is not
optional — crew names contain it (the fixture has a Kovač).

## The design system: Nocturne

The screens are a **restyle onto Nocturne**, a compact dark system, from the design handoff in
`~/Downloads/design_handoff_crewcomp_admin/`. The information architecture, column sets, copy and
state vocabulary are the spec's and did not move; the visual system did. `src/styles.css` carries
Nocturne's `:root` token sheet verbatim (ramps generated in OKLCH on one shared lightness scale) plus
the six state tones the ten §5.1 cell states need — Nocturne is a mono palette and the states are
not, so the tones were derived to sit on the same perceptual lightness steps as the ramps.

Four rules the sheet is built on. The first two are the system's; the last two are this app's:

1. **The accent is a line, never a flood.** A 1px border, a 2px nav edge bar, a dot, or a 900-step
   tinted fill. The primary button is an accent *outline* — on a dark ground a filled accent button is
   the brightest object on the page whatever else is happening.
2. **Freestanding rules fade to transparent** over 48px an end; box outlines and in-control
   separators stay solid. Headings never pass weight 500 — hierarchy is size and space.
3. **Pills carry a text label, always.** Colour is reinforcement, never the only carrier.
4. **Monospace is semantic** — and this reverses the previous sheet's "no monospace" rule. Every
   business key is monospaced (Sam #, requirement code, record id, slot ref, matrix level and version,
   config key, cron) and nothing else is: it is how a value you copy and search for is told apart from
   prose. Everything else keeps `font-variant-numeric: tabular-nums`, which aligns a column of figures
   without changing the typeface.

Six tones (`critical warning caution neutral good muted`) and their mapping live in
`domain/enums.ts` and nowhere else. `muted` is the one tone that takes a border: its fill is within a
shade of the surface it sits on, and without one the pill loses its edges.

That small list is deliberate. The §14.3 research rejected React Native partly on npm's 2025–26
supply-chain record, and the same argument applies to this app's dependency tree: for nine
table-and-chip screens, a design system is a large transitive surface bought for very little.
There is no ESLint either — `tsc` under this config catches the class of thing a lint pass would,
and adding one is a decision to revisit with a reason, not a default.

## Build and test

```bash
npm install
npm run dev          # Vite on :5173, proxying /api to :8080
npm run build        # tsc --noEmit && vite build
npm test             # vitest — 69 tests
npm run generate:api # regenerate src/api/schema.d.ts from the backend's OpenAPI schema
npm run verify:api   # fail if the committed types are stale — this is the CI check
```

The full local loop needs the backend running. From the repository root:

```bash
./scripts/dev-start.sh      # backend on :8080 (seeds a dev fixture) + this app on :5173
./scripts/dev-stop.sh       # both; `dev-stop.sh backend` leaves this app up
```

By hand, if you would rather — note the 32-character minimum on the MCP token:

```bash
(cd ../backend && CREWCOMP_MCP_TOKEN=…32+chars… ./mvnw quarkus:dev)   # :8080
npm run dev                                                           # :5173
```

`generate:api` reads `../backend/target/openapi/openapi.json`, which the backend writes during
`./mvnw package`. Run the backend build first if the file is missing.

## Non-negotiables for this component

- **Types are generated, never written** (DEV-2). `src/api/schema.d.ts` is produced from the
  backend's OpenAPI schema and committed so this app builds without a JDK; `npm run verify:api`
  fails the build when the two have diverged. Everything in `src/api/client.ts` is an alias of
  that file.
- **No compliance logic here** (AUTH-1). Cell states, roll-ups, quota results, suggestion scores
  and gap ordering are all the server's answers, rendered as received. `src/domain/enums.ts`
  holds labels and colours and nothing else; the moment it starts deciding what a state *is*,
  the POC's mistake is back.
- **"Today" comes from the session, never from `new Date()`** (NFR-5, O-11). Business dates are
  calendar dates in the operating timezone; the browser's clock is in the viewer's. `GET
  /api/v1/session` carries `today`, and `src/domain/dates.ts` never constructs a local `Date` —
  see the tests there for the specific off-by-one this avoids.
- **No token ever reaches JavaScript** (SEC-1, ADR 0003). Authentication is the BFF's `HttpOnly`
  cookie; the client sends `credentials: 'same-origin'` and has nowhere to put an
  `Authorization` header. A 401 means "not signed in" and routes to `SignIn`.
- **Role-gated UI is defence in depth only.** `useHasRole` hides controls the server would
  refuse; the server refuses them anyway. Never treat a hidden button as a control.
- **Lists render in the order received.** The gap report is the §5.4 worklist; a default sort in
  `DataTable` would silently discard the engine's ranking. Sorting is something the user asks
  for.
- **CSV exports what is on screen**, filter and sort included (§6) — exporting the unfiltered set
  would hand over rows the screen was hiding, including rows a scoped role should not have.

## Layout

| Path | Contents |
|---|---|
| `src/api/` | `schema.d.ts` (generated), `client.ts` (typed fetch + dev identity), `queries.ts` (React Query hooks and cache policy), `session.tsx` (session context, role helpers) |
| `src/domain/` | `dates.ts` calendar-date arithmetic, `enums.ts` Appendix A presentation, `csv.ts` RFC 4180 export |
| `src/components/` | `Layout` (with the unread badge), `DataTable`, `Ruler`, `StateChip`, `SwingSelector`, `Spinner`, `ErrorPanel` |
| `src/screens/` | `Dashboard` (ADM-1), `SwingPlanner` (ADM-2), `Matrix` (ADM-3), `Register`/`RegisterDetail`/`RegisterNew` (ADM-4), `People`/`PersonDetail` (ADM-5), `Requirements` (ADM-6), `Exceptions` (ADM-7), `CrewRequests` (ADM-11), `Notifications` (ADM-8), `Evidence` (ADM-9), `Administration` (ADM-10), `SignIn`, `NotBuilt` |

## Development sign-in

There is no login flow yet. ADR 0003 puts the OIDC code flow and the session cookie in the BFF,
which is the identity spike's deliverable; until then the backend carries a header-based shim
(`X-Dev-Roles`) that is **removed from the artefact at build time** outside dev and test, and the
SPA carries a matching role picker behind `import.meta.env.DEV`.

Both halves are compile-time stripped, and the production bundle is checked for it:

```bash
npm run build
find dist -type f \( -name '*.js' -o -name '*.css' -o -name '*.html' \) ! -name '*.map' \
  | xargs grep -l 'X-Dev-Roles'      # expect no output
```

This is a CI lane (`.github/workflows/ci.yml`) — it is the kind of guarantee that holds until
someone moves the constant behind a runtime check, at which point the bundle would carry a
header-authentication path into production.

**Check what ships as code, not `dist/` recursively.** `build.sourcemap` is on, and a source map
contains the pre-transform source by definition — the dev-shim branch included. A recursive grep
therefore fails on `index-*.js.map` on every single build while telling you nothing about what
executes, which is exactly what the first version of the CI step did. The shim was correctly stripped
from the JS all along.

Separately worth knowing: a deployed source map hands out the app's source. It is a debugging
artefact to upload to an error tracker, not something to serve — one for the deploy lane to exclude
when ADR 0005 resolves.

## The ruler (`components/Ruler.tsx`)

The dashboard and the planner share one time axis with a single dashed rule — the *datum* — at the
session's business date. It exists because everything in this domain is "does date range A cover
date range B": a swing has a window and a cutoff, a handover splits a slot into two legs (§4.3),
an expiry is a date landing inside or before a window (§5.1). The gap report can only say
"expiring"; the ruler says the certificate lapses on the 13th and three days of the swing are
uncovered. It is the one thing on these screens the two source workbooks could not draw.

Its shape is a three-column grid — label, lane, value — repeated for the header row and every data
row, so a lane's percentages line up down the whole panel. The panel scrolls horizontally and every
row carries `min-width: 880px`: below that the tick labels collide and a `nowrap` bar escapes its
lane. Two variants, `axis` (ADM-1) and `slots` (ADM-2), differ only in the label and value widths.

Rules it holds to, each of which cost something to learn:

- **Only dates sit on the axis.** Counts and states live in the value column. Give a cell count a
  horizontal position and the axis stops meaning one thing.
- **No `new Date()`, ever.** Positions come from `epochDay`, and `today` is passed in from the
  session (NFR-5). `dateFromEpochDay` is its exact inverse, tested by round-trip, because the axis
  labels itself at computed intervals and an off-by-one there would look like nothing at all.
- **A window includes its last day.** The lane divides by the number of *days* (`cells`), not the
  number of intervals between the end dates (`steps`) — the latter drops every bar's final day, which
  put a one-day hole between the two legs of a fully covered handover. On a screen whose job is
  finding coverage gaps, inventing one is the worst available bug. Ticks still interpolate over
  `steps`, or the last one would land a day past the axis.
- **The datum is drawn per row, not once across the panel.** A single line positioned against the
  panel's own box slides off its lane the moment the panel scrolls sideways, which is exactly what
  this panel does. `--ruler-at` is today's fraction of the lane.
- **Dashed, and above the bars.** It crosses crew names on the planner, and a solid rule through a
  name reads as a strikethrough — but drawing it *under* the bars (as the mock does) hides the one
  thing it is there to say, which is that today falls inside this swing.
- **A tick within 6% of the datum is dropped**, rather than printed under the "Today" pill.
- **A label past 55% of the lane flips to the left of its tick.** Both the cutoff and the lapse
  marker sit in the clear lane above the bars, so they cannot collide with one — the only constraint
  is not running off the lane's end into the value column. Anchor a flipped label with
  `left: 0` + `translateX(-100%)`, never `right: 0`: on a zero-width parent the box already sits at
  `[-w, 0]` and the transform then pushes it a second width away.
- **Nothing animates.** The bars had a clip-path wipe; it is gone. For its first few hundred
  milliseconds it showed a shorter swing than the one it described, and with
  `animation-fill-mode: both` a bar that never got its frame stayed invisible. On a screen whose
  claim is "this bar is these dates", an effect that transiently lies about the dates is not worth
  having.
- **Below 900px the geometry goes and the dates stay.** Each row carries its own window in words on
  `data-dates`, which the stylesheet renders instead of the bars. A 27-day axis in 380px is ~14px a
  day; geometry you cannot read is geometry that lies.

`Ruler.test.tsx` pins the arithmetic — handover split, lapse extent, clamping past the ends, the
suppressed tick — because every value is a percentage the eye reads as a date.

## Where the restyle deviates from the handoff, and why

Five places. Each is a case where following the mock exactly would have put something on screen that
nothing checked, or lost information the screen exists to carry.

1. **No AI assist strip on ADM-1.** The design has an optional accent strip reading "Overnight scan
   re-evaluated 5 swings — UNI CC24 changed from All clear to 1 Expiring". Nothing serves that: there
   is no endpoint for "what changed since you last looked", and hard-coding the sentence would put a
   fabricated claim about the evaluation on the landing page. It lands when a change-feed does.
2. **ADM-9's "What was read" fields are read-only** (styled as inputs, so the panel reads as the
   design intends). The design calls them "yours to correct", but correcting the *reading* is not a
   thing the server offers: an accept records the pair (extracted, accepted), and measuring the gap
   between those two is exactly what LLM-2 needs before auto-acceptance can be switched on. Fields
   that looked editable and were discarded would destroy that measurement while appearing to help.
   Corrections go in "What the record will say", which *is* the accept form.
3. **ADM-9's consequence line is the true one.** The design says "Accepting closes UNICC24-4 and
   clears the Gap on UNI CC25 slot 01". `EvidenceDocumentDto` carries no register record and no swing
   cell, so this says what is actually known: which person's holding it writes, and that their swings
   re-evaluate against it. Making the design's stronger line real is a server field — see the
   follow-ups.
4. **ADM-4's list shows six of its nine columns.** Raised, Approved for and the effective window are
   on the detail pane beside it. Nine columns in a ~700px pane wrapped every cell onto two lines, and
   the whole point of the pane is that the selected record is right there. The CSV carries all of them.
5. **The MOB screens are not built here.** The handoff bundles two crew-app screens as the other end
   of ADM-9; the crew app is the Flutter client in `mobile/`, and rebuilding it in React would be a
   second implementation of it. The tokens and the state tones are the same ones, so porting them
   there is a styling pass on `mobile/lib/src/ui/`.

Smaller, and worth knowing: the design marks a row needing attention with nothing at all, so the
per-row red leading edge is gone — the state chip in the row already carries it, and at ten rows the
edges were a wall. `table__row--selected` survives for genuine selections (the open register record,
the open evidence document, the chosen matrix version) and takes the accent, because a selection is
not a state.

## Four screens with a rule you would otherwise have to rediscover

**ADM-3 never offers an edit the server would refuse.** A published matrix version is immutable
(§5.5) — that is what makes a past evaluation reproducible — so the screen renders levels as
read-only tokens rather than selects, says why, and offers the thing the user actually wants: "Draft
from this". The server answers 409 either way; a UI that discovered the refusal by trying would be a
dead end wearing an error message. Discard and Publish act on the *selected* version and live on the
editor's controls row, where the thing they act on is on screen — an in-row Publish button beside
seven other rows invites publishing the wrong one.

**ADM-3's level tokens keep four cases apart, and two of them must never merge.** `M`/`M8`/`M9`/`Mˣ`
take the accent tint, `R` the neutral one, an **em dash** is no rule at all, and **"not required"** is
a partnership override that positively removes the rule. The legend under the grid says so on screen
for the same reason this paragraph does.

**ADM-3's partnership overrides are a separate pass, not a column.** The selector at the top of the
cell editor switches between the base rules and one partnership's overrides, because the two are
genuinely different things and only that arrangement makes the difference visible: an override set to
blank says "this partnership does not require it", and no override at all says "follow the base
rule". Clearing an override is therefore a different action from blanking it, and the diff renders
`—` (no rule) apart from `not required` (a blank override). Collapsing those was a real bug in the
first version of the diff view.

**ADM-3's "generated per swing" is a pivot, not an endpoint.** §6 asks for a generated matrix view
that replaces the CC sheets: crew × requirements with a cell state in each. It is built from the same
`/swings/{pt}/{cc}/evaluation` the planner uses, rearranged. Adding an endpoint that re-derived those
states would be a second implementation of §5.1 in all but name (AUTH-1).

**ADM-9's accept and correct are one form.** The record fields are pre-filled from the extraction and
are entirely editable; submitting accepts *what is in the form*. The server records both what was
extracted and what was accepted, so a correction is visible as one — and measuring that gap is
exactly what LLM-2 needs before auto-acceptance can be switched on. A separate "correct" button would
have made it invisible. Confidence is shown per field, and the colour bands here are presentation
only: what gates auto-acceptance is a server-side threshold in ADM-10.

**ADM-4, ADM-6 and ADM-9 are split panes, and the selection is a route.** `/register/{recordId}`,
`/evidence/{publicId}` and ADM-6's selected code all *select* within the screen rather than replacing
it, because working these modules is working a queue — read one, act on it, move to the next. Keeping
the selection in the URL is what lets a §9 notification point at the document it is about; ADM-9's
route existed and did nothing until this pass.

**ADM-11 is generic over kinds, and that is why MOB-8 needed no screen.** Four now land in it —
`course_booked`, `help_requested`, `seat_requested`, `waitlisted` — and a seat request arrived with
nothing to build but two labels and a second line under one cell. A kind this build has never heard
of renders as its wire value rather than as something reassuring, because both revisions are live
during an expand/contract deploy. Seat and waitlist are kept apart deliberately: booking a seat and
chasing a waitlist are different jobs, and a coordinator triaging their morning should be able to
tell them apart before opening anything.

**A course request shows the date, in the server's words.** `subjectLabel` is rendered server-side
when the request is made and stored beside the catalogue key, so it still reads correctly after the
option has been withdrawn — and it is not the device's summary text, for the same reason MOB-9's
signature line is composed server-side.

**ADM-4 badges a crew-raised record, and gives it no triage state.** `raisedByCrew` (from
`register_record.crew_op_id`) shows as *from the app* beside the `late` chip. It is provenance, not
a state: the record enters the same §6.4 workflow with the same statuses, because a triage step
would be a second definition of "open" and a second queue to forget. What it changes is how the row
reads — "the crew member noticed this" and "a coordinator raised it on their behalf" are different
facts, and only one means somebody in the office has already looked.

**ADM-11 carries no compliance state, and that is the whole design.** A crew statement is not a
compliance answer (AUTH-1): saying a course is booked does not close a gap, and a cell-state column
on this queue would read as though it had. The person's actual standing is one click away on their
page. Two smaller rules follow the same line — every label is **reported speech** ("Says a course is
booked", never "Course booked"), and the positive action is `Confirm` / `Arranged` rather than
`Resolve` (and `Booked` for the two course kinds), because nothing here resolves anything the engine evaluates.

**ADM-11's Dismiss states its consequence before the click, not after.** Dismissing a `course_booked`
request resumes the expiry reminders that statement had switched off on the crew member's phone —
the safety valve for "we have no record of that booking". It is the one control in this app whose
effect lands on somebody else's device, so the form says so above the button. The mutation
invalidates the notification queries for the same reason.

**ADM-8 checks a deep link before rendering it as an href.** `deepLink` is a server-provided string
and is either an app path or the mobile app's `crewcomp://` scheme. `internalLink` accepts only a
single-slash relative path, so `//evil.example` — which looks relative and is not — comes back null.
A stored value rendered straight into an `href` is an open redirect.

**ADM-10 shows job schedules read-only, and that is not laziness.** `quarkus-scheduler` resolves its
cron at start-up from deployment configuration, so a cron editable on this screen would be a setting
that looks live and does nothing. What the screen offers instead is "run now", which is the control an
operator reaches for anyway.

## Two flows worth understanding before changing them

**A 409 is a question, not a failure.** Two writes come back with one — assigning someone already
committed elsewhere (`assignment_clash`) and lodging a register record after the swing's cutoff
(`late_submission`). Neither is an error to report and forget: §5.4 says a clash is *shown*, never
hidden, and Q17 permits a late submission that is acknowledged. So the screen branches on
`ApiError.code`, renders the server's explanation in place, and offers the same request again with
`acknowledgeClash` / `acknowledgeLateSubmission` set. The acknowledgement lands in the audit event.
Treating either as a plain `ErrorPanel` would turn a workflow into a dead end.

**The gap report raises register records.** A gap row with no register record links to
`/register/new` with the partnership, swing, person and requirement already in the query string —
§6's "one-click pre-filled exemption request". That link is the join between ADM-2 and ADM-4, and
it is why `RegisterNew` reads its initial state from `useSearchParams` rather than starting empty.

## Known follow-ups

1. **The ADM-5 person list has no compliance roll-up column**, which §6 asks for. A roll-up is
   only defined against a swing (§5.2), and there is no swing-free evaluation endpoint; a number
   computed against an arbitrarily chosen swing would be worse than none. The roll-up is on the
   person detail page against a swing you pick. Resolving this properly means either a
   denormalised per-person summary or an explicit "as of the current swing" endpoint.
2. **Requirement category codes are shown raw.** Appendix A enumerates `QL · VS · PS · MS · CS ·
   HR · PT · VI · PI` but nothing says what they expand to, and inventing expansions would put a
   wrong label in front of people who know the right one. A question for the client, and the
   reason ADM-6's category filter offers bare codes.
3. **The register's free-text search is client-side.** `DataTable`'s filter searches the rows
   already fetched; the server takes partnership, CC, type and state but no `q`. Fine for a few
   hundred records, wrong for the full 445-row history plus years of new ones — the server needs
   the text predicate before this list is paged.
4. **The matrix CSV's shape is a guess at "audit-shaped"** (§6, pending Q26/O-7). It exports one row
   per rule — requirement, position, partnership-or-`*`, level — rather than a spreadsheet-shaped
   grid, because a grid loses the base/override distinction. Worth confirming against whatever the
   client actually audits with.
5. **The register enumerations are duplicated** in `domain/enums.ts` — the editor has to offer
   them before the server can reject a wrong one. `enums.test.ts` pins the copy; the ITs pin the
   server's. Nothing links the two, so a new status added in Kotlin will not appear here until
   somebody adds it.
6. **Accessibility has had a first pass, not an audit.** Chips carry text labels, tables use
   `aria-sort`, the spinner respects `prefers-reduced-motion`. Nobody has driven it with a
   screen reader. The ruler's bars are `aria-hidden` decoration — the value column carries the
   states as text and each row carries its window on `data-dates` — but that arrangement has not
   been tested with one either.
7. **The dashboard's state column lists only what needs attention** (`needsAttention` plus quota
   shortfalls), so a partnership with nothing outstanding reads "All clear" rather than
   "28 OK · 4 n/a". The OK, n/a, `quota_only` and `recommended` tallies are no longer on ADM-1 at
   all; they are on the planner for a given swing. Deliberate — tallying `20 OK` beside `2 Gap`
   made every row wrap and put the loudest number on the least urgent fact — but it *is* less
   information than the old cards showed, and worth confirming with the client.
7a. **`expiry.lead-days` is mirrored, not read** (`EXPIRY_LEAD_DAYS_DEFAULT` in `domain/enums.ts`).
    It decides the dashboard's initial lead-time selection and whether a held certificate's expiry
    reads as a warning on ADM-5. The live value is only exposed through the role-gated ADM-10
    configuration endpoint, so a Crew Coordinator opening a person's page would get a 403 for it.
    Carrying it on the session response would fix this; until then a stale horizon changes a colour
    and nothing else.
7b. **ADM-9 cannot say what accepting resolves.** The design's consequence line names the register
    record an acceptance closes and the swing cell it clears; `EvidenceDocumentDto` carries neither.
    Two nullable fields on that DTO — the covering register record id, and the (partnership, cc, slot)
    of the cell — would make the design's line real. Worth doing: it is the difference between a
    Data Steward accepting a document and a Data Steward knowing what they are unblocking.
8. **The gap report offers "Raise request" on `recommended` rows.** Pre-existing: the column
   offers the link for any row with no register record, and a recommended requirement is never a
   gap, so an exemption for one is meaningless. Harmless but noisy — the fixture's seven
   `PS-05 Confined Space Entry` rows all carry the button.
9. **No E2E test.** The vitest suite covers the date, CSV, enumeration and validation logic plus
   `DataTable`; the HTTP contract is covered on the backend side by the six `*IT` suites. What is
   untested *in the repository* is the two meeting in a browser — though every screen has been driven
   in a real Chromium against a live backend and the seeded fixture, which is how the diff view's
   blank-level bug, an empty "Not built" nav heading and the restyle's off-by-one handover hole were
   found. Making that a committed lane is the pipeline bootstrap's, and the first two cases it should
   carry are already known: **no screen may scroll the page horizontally** at 1600 / 1280 / 1100 / 980
   / 900 / 760 / 420px, and every ruler bar must cover its own end date.
12. **The shell scrolls horizontally at 980px and 420px** — 16px and 132px respectively, identically
    on every screen, so it is the shell rather than any one of them. `shell__header` and
    `shell__body` are the overflowing boxes; at 420px the widest child is `shell__session`, the
    **dev-only** role picker and its "Switch role" button, which a production bundle does not
    contain. Measured with a headless Chromium against the live backend on 28 July 2026, across
    `/exceptions`, `/crew-requests`, `/register` and `/evidence`, all four identical. The rule above
    was checked by hand per screen and this is a shell-level regression underneath it — one fix in
    `Layout`/`styles.css`, but it needs all eleven screens re-checked afterwards, which is why it is
    listed rather than done.
10. **ADM-8's role proxy shows other people's copies.** Until the identity spike gives a back-office
    user their own account, an account-less actor reads the notifications addressed to the accounts
    holding their roles — so a §9 fan-out to four Workflow Managers reads as four rows. The recipient
    name on each row distinguishes them and the screen says so when it detects duplicates, but it is
    a scaffold with a removal date, not the finished behaviour.
11. **ADM-9's document preview is not yet a signed URL.** SEC-7 wants evidence served from a
    platform-signed URL; `LocalObjectStorage` returns a `file:` URI, which no browser will fetch. So
    the bytes are streamed through the API `inline` with `nosniff`, a PDF goes in a `sandbox=""`
    iframe, and what makes that safe is the ingest allow-list — only PDFs and four image types were
    ever stored. Replaced in the platform spike.
