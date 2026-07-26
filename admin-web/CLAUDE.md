# admin-web/ — React + TypeScript admin SPA

**All ten §6 modules exist**: ADM-1 dashboard, ADM-2 swing planner, ADM-3 matrix, ADM-4 register,
ADM-5 people & holdings, ADM-6 requirements catalogue, ADM-7 exceptions worklist, ADM-8 notifications
centre, ADM-9 evidence verification queue, ADM-10 administration. `NotBuilt.tsx` survives as the
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

Two webfonts are **self-hosted** in `public/fonts/` (Inter for everything, Plus Jakarta Sans for
headings; latin + latin-ext variable woff2, ~180 KB). Self-hosted rather than linked: a CDN font
is a third party that sees every page view, and `npm` font packages would be two more
dependencies in a tree this project deliberately keeps short. `latin-ext` is not optional — crew
names contain it (the fixture has a Kovač).

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
npm test             # vitest
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
| `src/screens/` | `Dashboard` (ADM-1), `SwingPlanner` (ADM-2), `Matrix` (ADM-3), `Register`/`RegisterDetail`/`RegisterNew` (ADM-4), `People`/`PersonDetail` (ADM-5), `Requirements` (ADM-6), `Exceptions` (ADM-7), `Notifications` (ADM-8), `Evidence` (ADM-9), `Administration` (ADM-10), `SignIn`, `NotBuilt` |

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

Rules it holds to, each of which cost something to learn:

- **Only dates sit on the axis.** Counts and states live in the value column. Give a cell count a
  horizontal position and the axis stops meaning one thing.
- **No `new Date()`, ever.** Positions come from `epochDay`, and `today` is passed in from the
  session (NFR-5). `dateFromEpochDay` is its exact inverse, tested by round-trip, because the axis
  labels itself at computed intervals and an off-by-one there would look like nothing at all.
- **The datum is anchored to the ruler's box, not to a grid line.** `grid-row: 1 / -1` collapses to
  a single row when the rows are implicit, which is how the line silently drew nothing at first.
  `--ruler-at` is today's fraction of the axis; `--ruler-track-x/-w` describe the lane.
- **Dashed, not solid.** It crosses crew names on the planner, and a solid rule through a name
  reads as a strikethrough.
- **A tick within 6% of the datum is dropped**, rather than printed under the "Today" pill.
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

## Four screens with a rule you would otherwise have to rediscover

**ADM-3 never offers an edit the server would refuse.** A published matrix version is immutable
(§5.5) — that is what makes a past evaluation reproducible — so the screen renders levels as chips
rather than selects, says why, and offers the thing the user actually wants: "Draft from this". The
server answers 409 either way; a UI that discovered the refusal by trying would be a dead end wearing
an error message.

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

**ADM-9's accept and correct are one form.** The fields are pre-filled from the extraction and are
entirely editable; submitting accepts *what is in the form*. The server records both what was
extracted and what was accepted, so a correction is visible as one — and measuring that gap is
exactly what LLM-2 needs before auto-acceptance can be switched on. A separate "correct" button would
have made it invisible. Confidence is shown per field, and the colour bands here are presentation
only: what gates auto-acceptance is a server-side threshold in ADM-10.

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
8. **The gap report offers "Raise request" on `recommended` rows.** Pre-existing: the column
   offers the link for any row with no register record, and a recommended requirement is never a
   gap, so an exemption for one is meaningless. Harmless but noisy — the fixture's seven
   `PS-05 Confined Space Entry` rows all carry the button.
9. **No E2E test.** The vitest suite covers the date, CSV, enumeration and validation logic plus
   `DataTable`; the HTTP contract is covered on the backend side by the six `*IT` suites. What is
   untested *in the repository* is the two meeting in a browser — though every screen has been driven
   in a real Chromium against a live backend and the seeded fixture, which is how the diff view's
   blank-level bug and an empty "Not built" nav heading were found. Making that a committed lane is
   the pipeline bootstrap's.
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
