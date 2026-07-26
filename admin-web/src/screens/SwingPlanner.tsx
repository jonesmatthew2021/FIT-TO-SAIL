import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import {
  usePositions,
  useRequirements,
  useSlots,
  useSuggestions,
  useSwingEvaluation,
  useSwingGaps,
} from '../api/queries'
import type {
  AssignmentEvaluation,
  GapReportRow,
  Quota,
  Slot,
  Suggestion,
  SwingEvaluation,
} from '../api/client'
import { DataTable, type Column } from '../components/DataTable'
import { ErrorPanel } from '../components/ErrorPanel'
import { Spinner } from '../components/Spinner'
import { StateChip } from '../components/StateChip'
import { SwingSelector } from '../components/SwingSelector'
import { formatDate, formatDateRange } from '../domain/dates'
import { needsAttention } from '../domain/enums'
import { requirementLookup } from './Dashboard'

/**
 * ADM-2 — the swing planner.
 *
 * The selection lives in the URL, so a planner view is a link someone can paste into a message
 * ("every entity view deep-links", §6).
 *
 * Nothing on this screen re-derives compliance. Slot coverage, cell states, quota results and
 * the gap ordering all arrive evaluated from `/swings/{pt}/{cc}/…`; the screen arranges them.
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
        <h1 className="screen__title">Swing planner</h1>
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
            <h2 className="section__title">Slots</h2>
            <SlotTable
              evaluation={evaluation.data}
              onConsiderSlot={setSlotUnderConsideration}
              selectedSlotRef={slotUnderConsideration}
            />
          </section>

          {slotUnderConsideration !== null && (
            <section className="section">
              <h2 className="section__title">Suggestions for slot {slotUnderConsideration}</h2>
              <Suggestions partnership={partnership} cc={cc} slotRef={slotUnderConsideration} />
            </section>
          )}

          <section className="section">
            <h2 className="section__title">Quotas</h2>
            <QuotaPanel quotas={evaluation.data.quotas} />
          </section>

          <section className="section">
            <h2 className="section__title">Gap report</h2>
            <GapReport partnership={partnership} cc={cc} />
          </section>
        </>
      )}
    </div>
  )
}

function SwingSummary({ evaluation }: { evaluation: SwingEvaluation }): React.ReactNode {
  return (
    <dl className="facts">
      <div>
        <dt>Swing</dt>
        <dd>{formatDateRange(evaluation.from, evaluation.to)}</dd>
      </div>
      <div>
        <dt>Cutoff</dt>
        <dd>{formatDate(evaluation.cutoff)}</dd>
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
        <dt>Partly covered</dt>
        <dd>{evaluation.partiallyCoveredSlots.length}</dd>
      </div>
    </dl>
  )
}

interface SlotRow {
  readonly ref: number
  readonly shift: string
  readonly allowedPositionIds: readonly number[]
  readonly assignments: readonly AssignmentEvaluation[]
  readonly coverage: 'open' | 'partial' | 'covered'
}

function SlotTable({
  evaluation,
  onConsiderSlot,
  selectedSlotRef,
}: {
  evaluation: SwingEvaluation
  onConsiderSlot: (slotRef: number | null) => void
  selectedSlotRef: number | null
}): React.ReactNode {
  const slots = useSlots()
  const positions = usePositions()

  if (slots.isPending) return <Spinner label="Loading slot model" />
  if (slots.error !== null) return <ErrorPanel title="Could not load the slot model" error={slots.error} />

  const positionName = new Map((positions.data ?? []).map((position) => [position.id, position.name]))
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
    allowedPositionIds: slot.allowedPositionIds,
    assignments: bySlot.get(slot.ref) ?? [],
    coverage: open.has(slot.ref) ? 'open' : partial.has(slot.ref) ? 'partial' : 'covered',
  }))

  const columns: Column<SlotRow>[] = [
    { id: 'ref', header: 'Slot', accessorFn: (row) => row.ref },
    { id: 'shift', header: 'Shift', accessorFn: (row) => row.shift },
    {
      id: 'positions',
      header: 'Position',
      accessorFn: (row) =>
        row.allowedPositionIds.map((id) => positionName.get(id) ?? `#${id}`).join(' / '),
    },
    {
      id: 'coverage',
      header: 'Coverage',
      accessorFn: (row) => row.coverage,
      cell: ({ row }) => <CoverageChip coverage={row.original.coverage} />,
    },
    {
      id: 'crew',
      header: 'Crew',
      accessorFn: (row) => row.assignments.map((assignment) => assignment.name).join(', '),
      cell: ({ row }) =>
        row.original.assignments.length === 0 ? (
          <span className="muted">—</span>
        ) : (
          // A mid-swing handover puts two people in one slot; each gets its own line with its
          // own dates, which is how the POC rendered a handover and how the engine models it.
          <ul className="crew-list">
            {row.original.assignments.map((assignment) => (
              <li key={assignment.assignmentId} className="crew-list__item">
                <Link to={`/people/${assignment.personId}`}>{assignment.name}</Link>
                <span className="muted">{assignment.sam}</span>
                <span className="muted">{formatDateRange(assignment.from, assignment.to)}</span>
                <StateChip state={assignment.evaluation.rollUp} />
                <AttentionCounts assignment={assignment} />
              </li>
            ))}
          </ul>
        ),
    },
    {
      id: 'actions',
      header: '',
      enableSorting: false,
      accessorFn: () => '',
      cell: ({ row }) =>
        row.original.coverage === 'covered' ? null : (
          <button
            type="button"
            className="button button--quiet"
            onClick={() =>
              onConsiderSlot(selectedSlotRef === row.original.ref ? null : row.original.ref)
            }
          >
            {selectedSlotRef === row.original.ref ? 'Hide suggestions' : 'Suggest crew'}
          </button>
        ),
    },
  ]

  return (
    <DataTable
      rows={rows}
      columns={columns}
      filterPlaceholder="Filter slots"
      rowClassName={(row) => (row.coverage === 'open' ? 'table__row--attention' : undefined)}
      csv={{
        filename: `swing-${evaluation.ccId}-slots.csv`,
        columns: [
          { header: 'Slot', value: (row) => row.ref },
          { header: 'Shift', value: (row) => row.shift },
          {
            header: 'Position',
            value: (row) => row.allowedPositionIds.map((id) => positionName.get(id) ?? `#${id}`).join(' / '),
          },
          { header: 'Coverage', value: (row) => row.coverage },
          { header: 'Crew', value: (row) => row.assignments.map((a) => `${a.name} (${a.sam})`).join('; ') },
          {
            header: 'Roll-up',
            value: (row) => row.assignments.map((a) => a.evaluation.rollUp).join('; '),
          },
        ],
      }}
    />
  )
}

function CoverageChip({ coverage }: { coverage: SlotRow['coverage'] }): React.ReactNode {
  const tone = coverage === 'open' ? 'critical' : coverage === 'partial' ? 'warning' : 'good'
  const label = coverage === 'open' ? 'Open' : coverage === 'partial' ? 'Part-covered' : 'Covered'
  const title =
    coverage === 'partial'
      ? 'Assigned for part of the swing only — a handover chain that does not cover the whole window.'
      : undefined
  return (
    <span className={`chip chip--${tone}`} title={title}>
      {label}
    </span>
  )
}

function AttentionCounts({ assignment }: { assignment: AssignmentEvaluation }): React.ReactNode {
  const counts = new Map<string, number>()
  for (const cell of assignment.evaluation.cells) {
    if (needsAttention(cell.state)) counts.set(cell.state, (counts.get(cell.state) ?? 0) + 1)
  }
  if (counts.size === 0) return null
  return (
    <span className="crew-list__counts">
      {[...counts].map(([state, count]) => (
        <span key={state} className="crew-list__count">
          {count} <StateChip state={state} />
        </span>
      ))}
    </span>
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
          <span className="quota__scope">{quota.shift === null ? 'whole swing' : `shift ${quota.shift}`}</span>
          <span className="quota__count">
            {quota.actual}/{quota.min}
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
    { id: 'sam', header: 'Sam #', accessorFn: (row) => row.sam },
    { id: 'slot', header: 'Slot', accessorFn: (row) => row.slotRef },
    { id: 'requirement', header: 'Requirement', accessorFn: (row) => codeFor(row.requirementId) },
    { id: 'level', header: 'Level', accessorFn: (row) => row.level },
    {
      id: 'register',
      header: 'Register',
      accessorFn: (row) => row.registerRecordId ?? '',
      cell: ({ row }) => row.original.registerRecordId ?? <span className="muted">—</span>,
    },
    {
      id: 'notes',
      header: 'Notes',
      enableSorting: false,
      accessorFn: (row) => row.notes.join(' '),
      cell: ({ row }) => row.original.notes.join(' · '),
    },
  ]

  return (
    <DataTable
      rows={gaps.data}
      columns={columns}
      filterPlaceholder="Filter gaps"
      empty="No gaps — every cell is ok or not applicable."
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

function Suggestions({
  partnership,
  cc,
  slotRef,
}: {
  partnership: string
  cc: string
  slotRef: number
}): React.ReactNode {
  const suggestions = useSuggestions(partnership, cc, slotRef)

  if (suggestions.isPending) return <Spinner label="Ranking candidates" />
  if (suggestions.error !== null) {
    return <ErrorPanel title="Could not rank candidates" error={suggestions.error} />
  }

  const columns: Column<Suggestion>[] = [
    {
      id: 'name',
      header: 'Candidate',
      accessorFn: (row) => row.name,
      cell: ({ row }) => <Link to={`/people/${row.original.personId}`}>{row.original.name}</Link>,
    },
    { id: 'sam', header: 'Sam #', accessorFn: (row) => row.sam },
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
      cell: ({ row }) => row.original.reasons.join(' · '),
    },
  ]

  return (
    <>
      <p className="note">
        Ranked by the server (§5.4). Assigning from here needs the assignment write path, which is
        not built yet — see the planner note in <code>admin-web/CLAUDE.md</code>.
      </p>
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
