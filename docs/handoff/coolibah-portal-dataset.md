# The Coolibah portal as a real dataset

Matt runs a proof-of-concept crew portal for TSV Coolibah (Netlify + Postgres; the source,
state and every uploaded file are shared read-only over Tailscale at
`https://matt.tail029157.ts.net/`). It holds **real, current crew data** — the roster, the
qualification matrix, and 1,379 certificate PDFs — maintained by the people who own that data.
This document is the index of what is in it, the mapping onto our §4 domain, and the repeatable
process for using it as a development dataset in place of the generated one. It supersedes
nothing: the POC extracts (`extracted` dataset, ~/shipping) remain the behavioural reference for
the engine; this is a second, *live* real dataset with a different shape and one operation's
worth of scope.

Indexed from **revision 342, saved 2026-08-23** — the portal is actively maintained, so counts
below will drift; the process, not the counts, is the contract.

## The source

The share is a static mirror of the portal, rebuilt by Matt (`README.txt` at the root describes
it). Three things matter:

| URL | What it is |
|---|---|
| `/portal-state.json` | Everything that isn't a file, as one revision-stamped JSON document (`{rev, savedAt, data}`). The portal's own database is exactly this: one `portal_state` row (JSONB) plus a `documents` table — see `/source/db/schema.ts`. |
| `/documents/index.json` | One record per uploaded file: category, path, person, SHA-256 checksum, filed/removed metadata. 1,421 files, 34 soft-removed (superseded, kept under `removed/`). |
| `/documents/<path>` | The bytes, laid out as the portal files them (`certification/<person-slug>/…`, `matrices/…`, `roster/…`). Verified: bytes match the index's checksums. |

`/source/` is the whole app (front end, Netlify functions, Drizzle schema) and `/preview.html`
the served page — useful for reading intent, not inputs to our load.

## What is in `portal-state.json` (rev 342)

| Section | Count | What it is | Do we load it? |
|---|---|---|---|
| `quals.cols` | 54 | The requirement catalogue: `[code, title, category]`. Codes use exactly our Appendix A prefixes (QL/VS/PS/MS/CS/HR/PT/VI/PI). | **Yes** — catalogue |
| `validityPeriods.periods` | 45 | Per-code validity (`months`, `neverExpires`, free-text `validFor`). 9 matrix codes have no entry (PI-02, QL-05..11, QL-20). | **Yes** — catalogue validity |
| `quals.rows` | 40 | Per person: `[name "SURNAME, First", rank, employee-id, dates[54]]`. Cells: ISO date (985), `Y` (150), `N` (22), `?` (4), blank (999). | **Yes** — people + holdings |
| `people` | 41 | Portal roster: id (`p-evan`), crew `A`/`B`/null, dept, active. | **Yes** — crew membership |
| `certDates.map` | 1,034 | `"SURNAME, First::CODE"` → `{url, issued, expires}` — the link from each matrix cell to the certificate file behind it. | **Yes** — evidence linkage |
| `swingLists` | 2 | Crew A and Crew B lists with per-person rank, from uploaded crew-list spreadsheets. | **Yes** — assignments |
| `swingDates`, `swingReport.swing` | — | Swing windows (`flyOut`/`flyHome`; e.g. swing 1 = crew A, 08 Sep–07 Oct 2026). | **Yes** — crew changes |
| `swingBoard`, `swingBoards` | — | Per-person on/off toggles per swing (includes placeholder ids for fill-ins). | Maybe — refines assignments |
| `swingReport` | 1 | The portal's own compliance verdict for the next swing: per-person `blocked`/`watch`/`clear` with per-code reasons. | **No — acceptance oracle** (below) |
| `shiftAnalysis`, `certAnalysis`, `opmsAnalysis` | — | AI-produced analyses (superseded certificates, OPMS discrepancies, per-shift minima). | No — cross-checks and ADM-7 candidates |
| `history` | 328 | The portal's change log. | No |
| `notes`, `comments`, `correspondence`, `suggestions`, `docs` | ~0 | Handover notes / social features; essentially empty at rev 342. | No |

## What is in `documents/index.json`

- **1,379 live certificates** (plus 35 removed), one folder per person, all 40 matrix people
  represented, every one SHA-256-checksummed. `qualCode`/`expiresOn` are blank on the file
  records — the linkage lives in `certDates.map` instead.
- **Spreadsheets** (live ones): a skills matrix (`matrices/skills/ATB Skills Matrix 29.06.26
  REVISED 17.07.26`), a validity matrix, a training matrix (`CREW QUALIFICATION EXPIRY`), a
  shift-allocation sheet (`Shift allocation updated for engineer class 3`), an OPMS completion
  export, and a `United TR02 - Completion Overview by Person` certificate sheet.
- One document-library file (safety meeting minutes).

## Mapping onto §4

One partnership's worth of data: a single tug-and-barge operation (TSV Coolibah, MinRes Onslow
project), two rotating crews. Decision 1 below: it seeds a
single partnership of its own.

| Portal | CREWCOMP | Notes |
|---|---|---|
| `quals.cols` + `validityPeriods` | `Requirement` (code, title, category, validity months) | Prefixes already match `ExtractedSeedLoader.CATEGORY_BY_NAME`; category *names* differ slightly ("High Risk Work Licence (HRWL)" vs "High Risk Work"). The 9 codes without validity → ADM-7 worklist items, not guesses. |
| `quals.rows` name + employee id + rank | `Person` | The employee id (a letter, five digits, a letter — the fixture's `a11111A` shows the shape) is the person business key within this dataset, its own keyspace (decision 2). Rank vocabulary map: see defaults below. |
| `quals.rows` dated cell | `QualificationHolding` `held_expiry` | Date is the expiry. `issued` comes from `certDates.map` where present. |
| `quals.rows` `Y` | holding with no expiry (held, never-expires or unverified) | Cross-check against `validityPeriods.neverExpires`; a `Y` on a code that *does* expire is an ADM-7 item. |
| `quals.rows` `N` / `?` | no holding (gap) / unknown | Decision 4: `N` is a gap, `?` is no holding + ADM-7 item. |
| `people.crew` + `swingLists` + `swingDates` | `CrewChange` + `Assignment` | Crews A/B on a fixed rotation; ranks per swing come from `swingLists` entries. |
| `certDates.map` + certificate files | §8 `EvidenceDocument` (future) | See "The certificate files" below — this is the deferred half. |
| `swingReport`, `shiftAnalysis` | — | Not data. Acceptance oracles: our engine's evaluation of the same swing should reproduce the portal's blocked/clear verdicts (the same role the CC24/CC25 diff plays for the POC extracts). Divergence is a finding, in either system's favour. |

**Not in the portal state at all: matrix *rules*** (which position requires which code at which
level). The portal's grid is person×code (holdings), not position×code (requirements). The rules
live in the **shift-allocation and skills-matrix spreadsheets**, which we have not given a schema
yet — until they are parsed (or the client hands us the rules another way), a `portal` dataset
seeds a provisional matrix from `shiftAnalysis.check` (decision 3), and the sheets remain
part of the spreadsheet note below.

## The repeatable process

Three stages; the first two are built, the third is the refresh loop they make cheap.

**1. Snapshot — built, run it any time.**

```bash
./scripts/portal-snapshot.sh               # portal-state.json + documents/index.json
./scripts/portal-snapshot.sh --documents   # + every file, checksum-verified (~460 MB once;
                                           # incremental afterwards)
```

Snapshots land in `~/coolibah-portal/snapshots/<savedAt>-rev<rev>/` with `latest` pointing at
the newest; document bytes are mirrored once under `~/coolibah-portal/documents/` and re-verified
against the index's checksums. Re-running against an unchanged portal is a no-op. **Everything
under `~/coolibah-portal` is real crew data and never enters this repository** — the same rule,
and the same mechanism, as the POC extracts in `~/shipping`.

**2. Load — a third dev dataset, `portal`. Built (26 Aug 2026).** `PortalSeedLoader` sits beside
`ExtractedSeedLoader` and reads the snapshot JSON directly (config:
`crewcomp.dev-seed.dataset=portal`, `crewcomp.dev-seed.portal-root`), surfaced as
`./scripts/dev-start.sh --dataset portal` (root defaults to `~/coolibah-portal/latest`). It reads
the JSON rather than transforming to the POC CSV layout first: the snapshot is already a
machine-readable, revision-stamped contract; half of what it holds (certificate linkage, validity
periods, swing boards) has no column in the POC CSVs; and a CSV intermediate would be a second
format to keep honest. Everything the extracted dataset already generalises applies verbatim —
out-of-repo root, empty-database guard, build-time gating, every fix-up an ADM-7 `ExceptionItem`,
crew accounts per person so the mobile app works against it, Masters supervising (MOB-11). The
published matrix is labelled `Coolibah portal rev <N>`, so a running stack says which portal
revision it is showing. `PortalSeedIT` proves every prescribed reading against real PostgreSQL
using a fictional fixture (`src/test/resources/portal-fixture`) — a real snapshot is personal
data and is never committed.

Against rev 342 the loader seeds: 54 requirements, 40 people, **1,161 holdings** with issue
dates joined from the certificate linkage, the dated swing (CC01, 17 assignments with watches
mapped to shifts), 5 quota rules + 1 one-of set parsed from the shift-allocation check, and
13 worklist items — among them a genuine catch: **two crew share one employee id** (the
pair is named on the ADM-7 worklist), imported both and flagged, never merged.

**3. Refresh — the loop this makes cheap.** Matt keeps working; when the portal moves:

```bash
./scripts/portal-snapshot.sh --documents   # picks up the new revision
./scripts/dev-stop.sh                      # datasets only take effect on a fresh database
./scripts/dev-start.sh --dataset portal
```

Plus the acceptance cross-check as a committed test once the loader exists: evaluate the
snapshot's next swing through our engine and diff against `swingReport` from the same snapshot —
self-contained, no live portal needed in CI, refreshed by refreshing the snapshot fixture (a
*sanitised* one, if committed — or run only where a snapshot exists, like the extracted lane).

The first run already produced one divergence of each kind, and both are informative rather than
bugs. **Agreement:** the engine reports the S5 quota (QL-07, Engineer Class 3) short on Shift 2
with zero holders — exactly the portal's own "night Assistant Engineers do not hold QL-07"
finding. **Divergence:** the portal calls night-shift QL-19 short (1 of 2) where the engine says
satisfied, because §5.3 counts `N/A`-shift slots (Master, Cook — aboard the whole swing) toward
both shifts while the portal counts only the people whose watch *is* night. Which reading the
operation intends is a real question for Matt/the client, and precisely what the oracle exists to
surface.

## The certificate files — noted for later, deliberately not mapped yet

The user-visible ask was the state; the certificates are the expansion. What we know now, so the
decision is ready when we take it:

- 1,379 live PDFs (+14 certificate-sheet spreadsheets), one folder per person, SHA-256 per file,
  and `certDates.map` already ties 1,034 of them to a (person, code, issued, expires) tuple.
- **Future use 1 — seeding §8:** load each linked certificate as an `EvidenceDocument` in
  `verified` state attached to its holding, bytes from the mirror. That gives ADM-9 and MOB-6/7 a
  real corpus, and makes the crew app's "view my certificate" real.
- **Future use 2 — the §14.5 extraction corpus:** 1,379 real certificates with human-confirmed
  (person, code, issued, expires) ground truth is precisely the evaluation set the LLM provider
  choice needs. Nothing synthetic can substitute for it.
- **The spreadsheets have no schema yet** (skills matrix, validity matrix, training matrix, shift
  allocation, OPMS export, TR02 completion sheet). The shift-allocation and skills sheets are
  where the matrix *rules* live (see mapping), so parsing them — or getting their content as data
  from the client — is what turns the `portal` dataset from holdings-only into a fully evaluable
  matrix. Park until we have the schema or the client's word on which is authoritative.

## Decisions (taken 26 Aug 2026)

1. **Own partnership.** The `portal` dataset seeds exactly one partnership, created from portal
   data (TSV Coolibah tug + barge, crews A/B). It does not map onto any of the extracted
   dataset's five — datasets never mix, and if it later proves to be one of them, that is a
   rename, not a remodel.
2. **The employee id is its own keyspace.** The portal employee id is the person business key
   *within this dataset*, with no reconciliation against POC Sam #s. Becomes a §11 question only if the
   datasets ever converge.
3. **Matrix rules seed from `shiftAnalysis.check`** — the portal's own machine-readable parse of
   the shift-allocation sheet (11 requirements with per-position/per-shift minimums). The seeded
   matrix is marked provisional via ADM-7 items until the spreadsheets are schema'd or the client
   states the rules directly.
4. **Cell semantics, conservative reading.** `Y` → holding with no expiry (a `Y` on a code whose
   validity period says it expires is an ADM-7 item). `N` → not held, a gap the engine reports.
   `?` → no holding plus an ADM-7 item. Nothing invented; every oddity lands on the worklist.

## Defaults taken without asking (cheap to change, each flagged where uncertain)

- **Rank vocabulary map** (three portal vocabularies → one `CrewPosition` each): Master → Master;
  "Chief Officer - Unlimited" / "Chief Mate" → **Chief Officer** (the codebase's canonical name —
  the spec and both other datasets use it, so the loader does too); "Chief Officer - 100m" →
  Chief Officer with the grade kept as a `tier` ("100m"); "Second Mate" / "2nd Mate" → Second
  Mate; Chief Engineer → Chief Engineer; "First Engineer" / "1st Engineer" → First Engineer;
  "Assistant Engineer" (matrix) and "Junior Engineer" (swing lists) → one position, Assistant
  Engineer; GPH → GPH; Cook → Cook. `people.dept` ("Masters", "DECK OFFICERS", "Engineers",
  "GPH", "Chefs") is grouping, not rank, and is not loaded — nor is the `people` list itself:
  its names are board labels ("Macca", "Dave"), where the swing lists and the swing report join
  to the matrix cleanly.
- **The 9 codes with no validity period** (PI-02, QL-05..11, QL-20) load with validity unset —
  treated as unassessed rather than never-expiring, each an ADM-7 item. Dated holdings on them
  keep their dates.
- **Swing numbering is not trusted beyond what is explicit.** `swingDates` keys (`0`, `-1`) and
  `swingBoards` keys are opaque until Matt confirms the scheme; the loader takes crew changes
  only from explicitly dated structures (`swingReport.swing`, `swingDates` entries with coherent
  windows) and flags the rest.

## Still for Matt (none block the loader)

1. Confirm the swing-numbering scheme behind `swingDates` / `swingBoards` keys.
2. Confirm the two Chief Officer grades: one position, or a tier distinction we should model?
3. The barge's name (the portal names the tug; the vessel pair needs both).
