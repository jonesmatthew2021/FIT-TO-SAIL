# admin-web/ — React + TypeScript admin SPA

The shell, the API layer and six of the ten §6 modules exist: ADM-1 dashboard, ADM-2 swing planner
(now with the assignment write path), ADM-4 register, ADM-5 people & holdings, ADM-6 requirements
catalogue, ADM-7 exceptions worklist. The remaining four — ADM-3 matrix, ADM-8 notifications
centre, ADM-9 evidence queue, ADM-10 administration — are routed and named in the navigation, and
each says what it is waiting on. See `src/screens/NotBuilt.tsx`, which is the honest scope list.

## Stack (decided)

Vite 7 + React 19 + TypeScript 5.9, `strict` plus `noUncheckedIndexedAccess` and
`exactOptionalPropertyTypes`. Runtime dependencies, all of them: `react`, `react-dom`,
`react-router-dom`, `@tanstack/react-query` (server state), `@tanstack/react-table` (headless,
for the dense grids). No component library and no CSS framework — `src/styles.css` is the whole
of it.

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

The full local loop needs the backend running:

```bash
(cd ../backend && CREWCOMP_MCP_TOKEN=… ./mvnw quarkus:dev)   # :8080, seeds a dev fixture
npm run dev                                                   # :5173
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
| `src/components/` | `Layout`, `DataTable`, `StateChip`, `SwingSelector`, `Spinner`, `ErrorPanel` |
| `src/screens/` | `Dashboard` (ADM-1), `SwingPlanner` (ADM-2), `Register`/`RegisterDetail`/`RegisterNew` (ADM-4), `People`/`PersonDetail` (ADM-5), `Requirements` (ADM-6), `Exceptions` (ADM-7), `SignIn`, `NotBuilt` |

## Development sign-in

There is no login flow yet. ADR 0003 puts the OIDC code flow and the session cookie in the BFF,
which is the identity spike's deliverable; until then the backend carries a header-based shim
(`X-Dev-Roles`) that is **removed from the artefact at build time** outside dev and test, and the
SPA carries a matching role picker behind `import.meta.env.DEV`.

Both halves are compile-time stripped, and the production bundle is checked for it:

```bash
npm run build && grep -c "X-Dev-Roles" dist/assets/*.js   # expect 0
```

Worth keeping in the CI lane — it is the kind of guarantee that holds until someone moves the
constant behind a runtime check.

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
4. **No CSV for the matrix**, because ADM-3 does not exist yet. Every other list exports.
5. **The register enumerations are duplicated** in `domain/enums.ts` — the editor has to offer
   them before the server can reject a wrong one. `enums.test.ts` pins the copy; the ITs pin the
   server's. Nothing links the two, so a new status added in Kotlin will not appear here until
   somebody adds it.
6. **Accessibility has had a first pass, not an audit.** Chips carry text labels, tables use
   `aria-sort`, the spinner respects `prefers-reduced-motion`. Nobody has driven it with a
   screen reader.
7. **No E2E test.** The vitest suite covers the date, CSV, enumeration and validation logic plus
   `DataTable`; the HTTP contract is covered on the backend side by `ApiIT`. What is untested is
   the two meeting in a browser.
