import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import {
  useAssign,
  usePositions,
  useRequirements,
  useSlots,
  useSuggestions,
  useSwingEvaluation,
  useSwingGaps,
  useUnassign,
} from '../api/queries'
import {
  ApiError,
  type AssignmentEvaluation,
  type Cell,
  type GapReportRow,
  type Quota,
  type Slot,
  type Suggestion,
  type SwingEvaluation,
} from '../api/client'
import { useHasRole, useSession } from '../api/session'
import { DataTable, type Column } from '../components/DataTable'
import { ErrorPanel } from '../components/ErrorPanel'
import { Spinner } from '../components/Spinner'
import { StateChip } from '../components/StateChip'
import { SwingSelector } from '../components/SwingSelector'
import { Lapse, Leg, Ruler, RulerRow, Seam } from '../components/Ruler'
import { downloadCsv, toCsv } from '../domain/csv'
import { epochDay, formatDateRange, formatDayMonth, formatShortRange } from '../domain/dates'
import { cellState, cellStateRank, needsAttention } from '../domain/enums'
import { requirementLookup } from './Dashboard'

/** The roles the server accepts for an assignment write — mirrored to hide the controls. */
const ROSTER_EDITORS = ['crew_coordinator', 'system_administrator'] as const

/**
 * ADM-2 — the swing planner.
 *
 * The selection lives in the URL, so a planner view is a link someone can paste into a message
 * ("every entity view deep-links", §6).
 *
 * Nothing on this screen re-derives compliance. Slot coverage, cell states, quota results and the
 * gap ordering all arrive evaluated from `/swings/{pt}/{cc}/…`; the screen arranges them — now
 * along the swing's own time axis, so a handover reads as two legs and an expiry reads as the day
 * the vessel stops being compliant rather than as the words "expires mid-swing".
 */
export function SwingPlanner(): React.ReactNode {
  const [params, setParams] = useSearchParams()
  const partnership = params.get('partnership')
  const cc = params.get('cc')
  const [slotUnderConsideration, setSlotUnderConsideration] = useState<number | null>(null)

  const evaluation = useSwingEvaluation(partnership, cc)

  function select(nextPartnership: string | null, nextCc: string | null): void {
    const next = new URLSearchParams()
    if (nextPartnership !== null) next.set('partnership', nextPartnership)
    if (nextCc !== null) next.set('cc', nextCc)
    setSlotUnderConsideration(null)
    setParams(next)
  }

  return (
    <div className="screen">
      <header className="screen__header">
        <div className="screen__headline">
          <h1 className="screen__title">
            {evaluation.data === undefined
              ? 'Swing planner'
              : headline(partnership as string, evaluation.data)}
          </h1>
          {evaluation.data !== undefined && evaluation.data.openSlots.length > 0 && (
            <span className="chip chip--critical">
              {evaluation.data.openSlots.length === 1
                ? '1 open slot'
                : `${evaluation.data.openSlots.length} open slots`}
            </span>
          )}
        </div>
        <SwingSelector partnership={partnership} cc={cc} onChange={select} />
      </header>

      {(partnership === null || cc === null) && (
        <p className="empty">Choose a partnership and crew change.</p>
      )}

      {evaluation.isPending && partnership !== null && cc !== null && (
        <Spinner label="Evaluating swing" />
      )}
      {evaluation.error !== null && (
        <ErrorPanel title="Could not evaluate this swing" error={evaluation.error} />
      )}

      {evaluation.data !== undefined && partnership !== null && cc !== null && (
        <>
          <SwingSummary evaluation={evaluation.data} />

          <section className="section">
            <SlotAxis
              evaluation={evaluation.data}
              onConsiderSlot={setSlotUnderConsideration}
              selectedSlotRef={slotUnderConsideration}
            />
          </section>

          {slotUnderConsideration !== null && (
            <section className="section">
              <h2 className="section__title">Suggestions for slot {slotUnderConsideration}</h2>
              <Suggestions
                partnership={partnership}
                cc={cc}
                slotRef={slotUnderConsideration}
                swing={evaluation.data}
                onAssigned={() => setSlotUnderConsideration(null)}
              />
            </section>
          )}

          <section className="section">
            <div className="section__header">
              <div>
                <h2 className="section__title">Quotas</h2>
                <p className="section__note">
                  Footnote rules. A shortfall can exist with no individual gap behind it.
                </p>
              </div>
            </div>
            <QuotaPanel quotas={evaluation.data.quotas} />
          </section>

          <section className="section">
            <div className="section__header">
              <div>
                <h2 className="section__title">Gap report</h2>
                <p className="section__note">
                  In the engine's order — worst first. Sorting is something you ask for.
                </p>
              </div>
            </div>
            <GapReport partnership={partnership} cc={cc} />
          </section>
        </>
      )}
    </div>
  )
}

function headline(partnership: string, evaluation: SwingEvaluation): string {
  const open = evaluation.openSlots.length
  const short = evaluation.quotas.filter((quota) => !quota.satisfied).length
  if (open > 0) {
    return `${partnership} ${evaluation.ccId} sails with ${open === 1 ? 'one slot' : `${open} slots`} open.`
  }
  if (short > 0) return `${partnership} ${evaluation.ccId} is short on a quota.`
  return `${partnership} ${evaluation.ccId} is fully crewed.`
}

function SwingSummary({ evaluation }: { evaluation: SwingEvaluation }): React.ReactNode {
  return (
    <dl className="facts">
      <div>
        <dt>Swing</dt>
        <dd className="facts__date">{formatShortRange(evaluation.from, evaluation.to)}</dd>
      </div>
      <div>
        <dt>Cutoff</dt>
        <dd className="facts__date">{formatDayMonth(evaluation.cutoff)}</dd>
      </div>
      <div>
        <dt>Assigned</dt>
        <dd>{evaluation.assignments.length}</dd>
      </div>
      <div>
        <dt>Open slots</dt>
        <dd className={evaluation.openSlots.length > 0 ? 'value--attention' : undefined}>
          {evaluation.openSlots.length}
        </dd>
      </div>
      <div>
        <dt>Part-covered</dt>
        <dd>{evaluation.partiallyCoveredSlots.length}</dd>
      </div>
      <div>
        <dt>Quota short</dt>
        <dd className={evaluation.quotas.some((quota) => !quota.satisfied) ? 'value--attention' : undefined}>
          {evaluation.quotas.filter((quota) => !quota.satisfied).length}
        </dd>
      </div>
    </dl>
  )
}

/**
 * The shift, or nothing at all when it does not apply.
 *
 * The server already sends this display-ready (`Shift 1`, `N/A`), so this only drops the
 * not-applicable spellings — a Master's slot is not on a shift, and "Master · N/A" says less than
 * "Master". Anything unrecognised falls through unchanged rather than being blanked, so a new
 * shift added in Kotlin still reaches the screen.
 */
const NO_SHIFT = new Set(['n/a', 'na', 'not_applicable', 'none', ''])

function shiftLabel(shift: string): string | null {
  return NO_SHIFT.has(shift.trim().toLowerCase()) ? null : shift
}

interface SlotRow {
  readonly ref: number
  readonly shift: string
  readonly position: string
  readonly assignments: readonly AssignmentEvaluation[]
  readonly coverage: 'open' | 'partial' | 'covered'
}

/**
 * The slot model drawn on the swing's own axis (ADM-2).
 *
 * Replaces the flat slot table: coverage, handovers and mid-swing expiries are all facts about
 * *when*, and a table can only spell them out in words. Everything the table carried is still
 * here — crew links, Sam #, attention counts, unassign, suggest, CSV — arranged by date.
 */
function SlotAxis({
  evaluation,
  onConsiderSlot,
  selectedSlotRef,
}: {
  evaluation: SwingEvaluation
  onConsiderSlot: (slotRef: number | null) => void
  selectedSlotRef: number | null
}): React.ReactNode {
  const session = useSession()
  const slots = useSlots()
  const positions = usePositions()
  const requirements = useRequirements()
  const canEditRoster = useHasRole(...ROSTER_EDITORS)
  const unassign = useUnassign()

  if (slots.isPending) return <Spinner label="Loading slot model" />
  if (slots.error !== null) return <ErrorPanel title="Could not load the slot model" error={slots.error} />

  const positionName = new Map((positions.data ?? []).map((position) => [position.id, position.name]))
  const codeFor = requirementLookup(requirements.data)
  const open = new Set(evaluation.openSlots.map((slot) => slot.ref))
  const partial = new Set(evaluation.partiallyCoveredSlots.map((slot) => slot.ref))

  const bySlot = new Map<number, AssignmentEvaluation[]>()
  for (const assignment of evaluation.assignments) {
    const existing = bySlot.get(assignment.slotRef)
    if (existing === undefined) bySlot.set(assignment.slotRef, [assignment])
    else existing.push(assignment)
  }

  const rows: SlotRow[] = (slots.data as Slot[]).map((slot) => ({
    ref: slot.ref,
    shift: slot.shift,
    position: slot.allowedPositionIds.map((id) => positionName.get(id) ?? `#${id}`).join(' / '),
    // Legs in date order, so the seam between two of them is always drawn between neighbours.
    assignments: (bySlot.get(slot.ref) ?? []).sort((a, b) => epochDay(a.from) - epochDay(b.from)),
    coverage: open.has(slot.ref) ? 'open' : partial.has(slot.ref) ? 'partial' : 'covered',
  }))

  return (
    <>
      <div className="section__header">
        <div>
          <h2 className="section__title">Slots</h2>
          <p className="section__note">
            {rows.length} slots on the class model. Coverage is the server's.
          </p>
        </div>
        <button
          type="button"
          className="button"
          onClick={() =>
            downloadCsv(
              `swing-${evaluation.ccId}-slots.csv`,
              toCsv(rows, [
                { header: 'Slot', value: (row) => row.ref },
                { header: 'Shift', value: (row) => row.shift },
                { header: 'Position', value: (row) => row.position },
                { header: 'Coverage', value: (row) => row.coverage },
                {
                  header: 'Crew',
                  value: (row) => row.assignments.map((a) => `${a.name} (${a.sam})`).join('; '),
                },
                {
                  header: 'From',
                  value: (row) => row.assignments.map((a) => a.from).join('; '),
                },
                { header: 'To', value: (row) => row.assignments.map((a) => a.to).join('; ') },
                {
                  header: 'Roll-up',
                  value: (row) => row.assignments.map((a) => a.evaluation.rollUp).join('; '),
                },
              ]),
            )
          }
        >
          Export CSV
        </button>
      </div>

      {unassign.error !== null && (
        <ErrorPanel title="Could not remove that assignment" error={unassign.error} />
      )}

      <Ruler
        from={evaluation.from}
        to={evaluation.to}
        today={session.today}
        labelHeader="Slot"
        valueHeader="Cells needing attention"
        markCount={5}
      >
        {rows.map((row) => (
          <RulerRow
            key={row.ref}
            label={String(row.ref).padStart(2, '0')}
            note={[row.position, shiftLabel(row.shift)].filter(Boolean).join(' · ')}
            dates={slotDates(row, evaluation, codeFor)}
            attention={row.coverage === 'open' || row.assignments.some(hasAttention)}
            value={
              <SlotActions
                row={row}
                canEditRoster={canEditRoster}
                unassignPending={unassign.isPending}
                onUnassign={(assignmentId) => unassign.mutate(assignmentId)}
                onConsiderSlot={onConsiderSlot}
                selectedSlotRef={selectedSlotRef}
              />
            }
          >
            {row.coverage === 'open' ? (
              <Leg from={evaluation.from} to={evaluation.to} tone="open">
                <span>Open — nobody assigned</span>
              </Leg>
            ) : (
              row.assignments.map((assignment, index) => (
                <SlotLeg
                  key={assignment.assignmentId}
                  assignment={assignment}
                  previous={row.assignments[index - 1]}
                  codeFor={codeFor}
                />
              ))
            )}
          </RulerRow>
        ))}
      </Ruler>
    </>
  )
}

function SlotLeg({
  assignment,
  previous,
  codeFor,
}: {
  assignment: AssignmentEvaluation
  previous: AssignmentEvaluation | undefined
  codeFor: (id: number) => string
}): React.ReactNode {
  const lapse = firstLapse(assignment)
  const tone = hasGap(assignment) ? 'attention' : hasUnknown(assignment) ? 'unknown' : 'normal'

  return (
    <>
      {previous !== undefined && <Seam date={assignment.from} />}
      <Leg from={assignment.from} to={assignment.to} tone={tone}>
        <span className="track__leg-name">
          <Link to={`/people/${assignment.personId}`}>{assignment.name}</Link>
        </span>
        <span className="track__leg-note">{assignment.sam}</span>
      </Leg>
      {lapse !== null && (
        <Lapse
          expiry={lapse.expiry as string}
          to={assignment.to}
          label={`${codeFor(lapse.requirementId).split(' ')[0]} lapses ${formatDayMonth(lapse.expiry as string)}`}
        />
      )}
    </>
  )
}

/**
 * The earliest expiring cell on an assignment.
 *
 * `expiring` is the engine's word for "held at the start, invalid before the end" (§5.1), so the
 * expiry date is the day the vessel stops being compliant — the one fact the old slot table could
 * only render as a chip.
 */
function firstLapse(assignment: AssignmentEvaluation): Cell | null {
  const expiring = assignment.evaluation.cells.filter(
    (cell) => cell.state === 'expiring' && cell.expiry !== null,
  )
  if (expiring.length === 0) return null
  return expiring.reduce((earliest, cell) =>
    epochDay(cell.expiry as string) < epochDay(earliest.expiry as string) ? cell : earliest,
  )
}

function hasGap(assignment: AssignmentEvaluation): boolean {
  return assignment.evaluation.cells.some((cell) => cell.state === 'gap')
}

function hasUnknown(assignment: AssignmentEvaluation): boolean {
  return assignment.evaluation.cells.some((cell) => cell.state === 'unknown')
}

function hasAttention(assignment: AssignmentEvaluation): boolean {
  return assignment.evaluation.cells.some((cell) => needsAttention(cell.state))
}

/** The row's window in words — what the track becomes on a narrow screen. */
function slotDates(
  row: SlotRow,
  evaluation: SwingEvaluation,
  codeFor: (id: number) => string,
): string {
  if (row.coverage === 'open') {
    return `${formatShortRange(evaluation.from, evaluation.to)} · nobody assigned`
  }
  const legs = row.assignments
    .map((assignment) => `${assignment.name} ${formatShortRange(assignment.from, assignment.to)}`)
    .join(', then ')
  const lapses = row.assignments
    .map((assignment) => {
      const lapse = firstLapse(assignment)
      return lapse === null
        ? null
        : `${codeFor(lapse.requirementId).split(' ')[0]} lapses ${formatDayMonth(lapse.expiry as string)}`
    })
    .filter((note): note is string => note !== null)
  return [legs, ...lapses].join(' · ')
}

function SlotActions({
  row,
  canEditRoster,
  unassignPending,
  onUnassign,
  onConsiderSlot,
  selectedSlotRef,
}: {
  row: SlotRow
  canEditRoster: boolean
  unassignPending: boolean
  onUnassign: (assignmentId: number) => void
  onConsiderSlot: (slotRef: number | null) => void
  selectedSlotRef: number | null
}): React.ReactNode {
  const counts = new Map<string, number>()
  for (const assignment of row.assignments) {
    for (const cell of assignment.evaluation.cells) {
      if (cell.state === 'ok' || cell.state === 'na' || cell.state === 'recommended') continue
      counts.set(cell.state, (counts.get(cell.state) ?? 0) + 1)
    }
  }

  return (
    <>
      <ul className="counts">
        {counts.size === 0 && row.coverage !== 'open' && (
          <li className="counts__item">
            <span className="chip chip--good">All clear</span>
          </li>
        )}

        {/* Worst first, mirroring the engine's own gap ordering, so the eye finds the same thing
            here as in the gap report below. */}
        {[...counts]
          .sort(([a], [b]) => cellStateRank(a) - cellStateRank(b))
          .map(([state, count]) => (
            <li key={state} className="counts__item">
              <span
                className={`chip chip--${cellState(state).tone}`}
                title={cellState(state).description}
              >
                <span className="counts__value">{count}</span>
                {cellState(state).label}
              </span>
            </li>
          ))}
      </ul>

      <div className="ruler__actions">
        {/* A fully covered slot has nowhere to put anyone: any window would overlap the person
            already there, and the server refuses that outright. Unassign first. */}
        {row.coverage !== 'covered' && (
          <button
            type="button"
            className="button button--quiet"
            onClick={() => onConsiderSlot(selectedSlotRef === row.ref ? null : row.ref)}
          >
            {selectedSlotRef === row.ref ? 'Hide suggestions' : 'Suggest crew'}
          </button>
        )}

        {canEditRoster &&
          row.assignments.map((assignment) => (
            <button
              key={assignment.assignmentId}
              type="button"
              className="button button--quiet"
              disabled={unassignPending}
              onClick={() => onUnassign(assignment.assignmentId)}
              title={`Remove ${assignment.name} from slot ${row.ref}`}
            >
              {/* Named only on a handover, where there are two people to tell apart. */}
              {row.assignments.length > 1
                ? `Unassign ${assignment.name.split(' ')[0]}`
                : 'Unassign'}
            </button>
          ))}
      </div>
    </>
  )
}

function QuotaPanel({ quotas }: { quotas: readonly Quota[] }): React.ReactNode {
  const requirements = useRequirements()
  const codeFor = requirementLookup(requirements.data)

  if (quotas.length === 0) return <p className="empty">No quota rules apply to this swing.</p>

  return (
    <ul className="quotas">
      {quotas.map((quota) => (
        <li
          key={`${quota.footnote}-${quota.requirementId}-${quota.shift ?? 'swing'}`}
          className={quota.satisfied ? 'quota' : 'quota quota--short'}
        >
          <span className="quota__footnote">{quota.footnote}</span>
          <span className="quota__requirement">{codeFor(quota.requirementId)}</span>
          <span className="quota__scope">
            {quota.shift === null ? 'Whole swing' : (shiftLabel(quota.shift) ?? 'Whole swing')}
          </span>
          <span className="quota__count">
            {quota.actual} of {quota.min}
          </span>
          <span className={`chip chip--${quota.satisfied ? 'good' : 'critical'}`}>
            {quota.satisfied ? 'Met' : `Short by ${quota.shortfall}`}
          </span>
        </li>
      ))}
    </ul>
  )
}

function GapReport({ partnership, cc }: { partnership: string; cc: string }): React.ReactNode {
  const gaps = useSwingGaps(partnership, cc)
  const requirements = useRequirements()
  const codeFor = requirementLookup(requirements.data)

  if (gaps.isPending) return <Spinner label="Building gap report" />
  if (gaps.error !== null) return <ErrorPanel title="Could not build the gap report" error={gaps.error} />

  const columns: Column<GapReportRow>[] = [
    {
      id: 'state',
      header: 'State',
      accessorFn: (row) => row.state,
      cell: ({ row }) => <StateChip state={row.original.state} />,
    },
    {
      id: 'name',
      header: 'Person',
      accessorFn: (row) => row.name,
      cell: ({ row }) => <Link to={`/people/${row.original.personId}`}>{row.original.name}</Link>,
    },
    {
      id: 'sam',
      header: 'Sam #',
      accessorFn: (row) => row.sam,
      cell: ({ row }) => <span className="mono">{row.original.sam}</span>,
    },
    { id: 'slot', header: 'Slot', accessorFn: (row) => row.slotRef },
    { id: 'requirement', header: 'Requirement', accessorFn: (row) => codeFor(row.requirementId) },
    { id: 'level', header: 'Level', accessorFn: (row) => row.level },
    {
      id: 'register',
      header: 'Register',
      accessorFn: (row) => row.registerRecordId ?? '',
      // §6, ADM-2: a gap with no register record gets a one-click pre-filled exemption request.
      // Everything the form needs is already on this row, so nothing has to be retyped.
      cell: ({ row }) =>
        row.original.registerRecordId !== null ? (
          <Link className="mono" to={`/register/${encodeURIComponent(row.original.registerRecordId)}`}>
            {row.original.registerRecordId}
          </Link>
        ) : (
          <Link
            className="button button--quiet"
            to={
              `/register/new?partnership=${encodeURIComponent(partnership)}` +
              `&cc=${encodeURIComponent(cc)}` +
              `&personId=${row.original.personId}&requirementId=${row.original.requirementId}`
            }
          >
            Raise request
          </Link>
        ),
    },
    {
      id: 'notes',
      header: 'Notes',
      enableSorting: false,
      accessorFn: (row) => row.notes.join(' '),
      cell: ({ row }) => <span className="table__wrap">{row.original.notes.join(' · ')}</span>,
    },
  ]

  return (
    <DataTable
      rows={gaps.data}
      columns={columns}
      filterPlaceholder="Filter gaps"
      empty="No gaps — every cell is ok or not applicable."
      rowClassName={(row) => (needsAttention(row.state) ? 'table__row--attention' : undefined)}
      csv={{
        filename: `swing-${cc}-gaps.csv`,
        columns: [
          { header: 'State', value: (row) => row.state },
          { header: 'Sam #', value: (row) => row.sam },
          { header: 'Name', value: (row) => row.name },
          { header: 'Slot', value: (row) => row.slotRef },
          { header: 'Requirement', value: (row) => codeFor(row.requirementId) },
          { header: 'Level', value: (row) => row.level },
          { header: 'Expiry', value: (row) => row.expiry },
          { header: 'Register record', value: (row) => row.registerRecordId },
          { header: 'Notes', value: (row) => row.notes.join(' | ') },
        ],
      }}
    />
  )
}

/**
 * Ranked candidates for a slot, and the control that fills it (ADM-2).
 *
 * The assign flow has one deliberate wrinkle: a candidate who is committed elsewhere in the
 * window comes back as a 409 `assignment_clash` rather than a success. That is not an error to
 * report and forget — §5.4's position is that a clash is *shown*, never hidden, and the
 * coordinator may still mean it. So the clash is rendered on the row and the button becomes
 * "Assign anyway", which resends the same request with `acknowledgeClash`. The acknowledgement
 * lands in the audit event on the server side.
 */
function Suggestions({
  partnership,
  cc,
  slotRef,
  swing,
  onAssigned,
}: {
  partnership: string
  cc: string
  slotRef: number
  swing: SwingEvaluation
  onAssigned: () => void
}): React.ReactNode {
  const suggestions = useSuggestions(partnership, cc, slotRef)
  const canEditRoster = useHasRole(...ROSTER_EDITORS)
  const assign = useAssign(partnership, cc)

  // The window to assign for. Defaults to the whole swing; narrowing it is how a mid-swing
  // handover is built, one leg at a time (§4.3).
  const [from, setFrom] = useState(swing.from)
  const [to, setTo] = useState(swing.to)
  const [clash, setClash] = useState<{ personId: number; detail: string } | null>(null)

  if (suggestions.isPending) return <Spinner label="Ranking candidates" />
  if (suggestions.error !== null) {
    return <ErrorPanel title="Could not rank candidates" error={suggestions.error} />
  }

  const wholeSwing = from === swing.from && to === swing.to

  function submit(personId: number, acknowledgeClash: boolean): void {
    setClash(null)
    assign.mutate(
      {
        slotRef,
        personId,
        // Send dates only for a handover leg, so the ordinary case records the server's own
        // idea of the swing window rather than a copy of it that could drift.
        from: wholeSwing ? null : from,
        to: wholeSwing ? null : to,
        acknowledgeClash,
      },
      {
        onSuccess: onAssigned,
        onError: (error) => {
          if (error instanceof ApiError && error.code === 'assignment_clash') {
            setClash({ personId, detail: error.message })
          }
        },
      },
    )
  }

  const columns: Column<Suggestion>[] = [
    {
      id: 'name',
      header: 'Candidate',
      accessorFn: (row) => row.name,
      cell: ({ row }) => <Link to={`/people/${row.original.personId}`}>{row.original.name}</Link>,
    },
    {
      id: 'sam',
      header: 'Sam #',
      accessorFn: (row) => row.sam,
      cell: ({ row }) => <span className="mono">{row.original.sam}</span>,
    },
    { id: 'score', header: 'Score', accessorFn: (row) => row.score },
    { id: 'gaps', header: 'Gaps', accessorFn: (row) => row.gapCount },
    { id: 'unknown', header: 'Unknown', accessorFn: (row) => row.unknownCount },
    { id: 'expiring', header: 'Expiring', accessorFn: (row) => row.expiringCount },
    {
      id: 'flags',
      header: 'Flags',
      enableSorting: false,
      accessorFn: (row) => `${row.clash ? 'clash' : ''} ${row.crossPartnership ? 'cross' : ''}`,
      cell: ({ row }) => (
        <>
          {/* §5.4: a clashing candidate is shown and scored, never filtered out. */}
          {row.original.clash && <span className="chip chip--critical">Clash</span>}
          {row.original.crossPartnership && <span className="chip chip--neutral">Cross-partnership</span>}
        </>
      ),
    },
    {
      id: 'reasons',
      header: 'Why',
      enableSorting: false,
      accessorFn: (row) => row.reasons.join(' '),
      cell: ({ row }) => <span className="table__wrap">{row.original.reasons.join(' · ')}</span>,
    },
  ]

  if (canEditRoster) {
    columns.push({
      id: 'assign',
      header: '',
      enableSorting: false,
      accessorFn: () => '',
      cell: ({ row }) => {
        const clashed = clash !== null && clash.personId === row.original.personId
        return (
          <>
            <button
              type="button"
              className={clashed ? 'button button--quiet' : 'button button--primary button--quiet'}
              disabled={assign.isPending}
              onClick={() => submit(row.original.personId, clashed)}
            >
              {clashed ? 'Assign anyway' : 'Assign'}
            </button>
            {clashed && <span className="editor__error">{clash.detail}</span>}
          </>
        )
      },
    })
  }

  return (
    <>
      <p className="section__note">Ranked by the server (§5.4).</p>

      {canEditRoster && (
        <div className="editor">
          <label className="field field--inline">
            <span className="field__label">Assign from</span>
            <input
              className="input"
              type="date"
              value={from}
              min={swing.from}
              max={swing.to}
              onChange={(event) => setFrom(event.target.value)}
            />
          </label>
          <label className="field field--inline">
            <span className="field__label">Assign to</span>
            <input
              className="input"
              type="date"
              value={to}
              min={swing.from}
              max={swing.to}
              onChange={(event) => setTo(event.target.value)}
            />
          </label>
          <p className="selector__note">
            {wholeSwing
              ? 'The whole swing. Narrow the dates to build one leg of a mid-swing handover.'
              : `A handover leg, ${formatDateRange(from, to)}. The rest of the slot stays open.`}
          </p>
        </div>
      )}

      {assign.error !== null &&
        !(assign.error instanceof ApiError && assign.error.code === 'assignment_clash') && (
          <ErrorPanel title="Could not make that assignment" error={assign.error} />
        )}

      <DataTable
        rows={suggestions.data}
        columns={columns}
        empty="No candidates for this slot."
        csv={{
          filename: `swing-${cc}-slot-${slotRef}-suggestions.csv`,
          columns: [
            { header: 'Sam #', value: (row) => row.sam },
            { header: 'Name', value: (row) => row.name },
            { header: 'Score', value: (row) => row.score },
            { header: 'Gaps', value: (row) => row.gapCount },
            { header: 'Unknown', value: (row) => row.unknownCount },
            { header: 'Expiring', value: (row) => row.expiringCount },
            { header: 'Clash', value: (row) => row.clash },
            { header: 'Cross-partnership', value: (row) => row.crossPartnership },
            { header: 'Reasons', value: (row) => row.reasons.join(' | ') },
          ],
        }}
      />
    </>
  )
}
