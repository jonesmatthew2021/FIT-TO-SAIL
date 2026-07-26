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
  type GapReportRow,
  type Quota,
  type Slot,
  type Suggestion,
  type SwingEvaluation,
} from '../api/client'
import { useHasRole } from '../api/session'
import { DataTable, type Column } from '../components/DataTable'
import { ErrorPanel } from '../components/ErrorPanel'
import { Spinner } from '../components/Spinner'
import { StateChip } from '../components/StateChip'
import { SwingSelector } from '../components/SwingSelector'
import { formatDate, formatDateRange } from '../domain/dates'
import { needsAttention } from '../domain/enums'
import { requirementLookup } from './Dashboard'

/** The roles the server accepts for an assignment write — mirrored to hide the controls. */
const ROSTER_EDITORS = ['crew_coordinator', 'system_administrator'] as const

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
  const canEditRoster = useHasRole(...ROSTER_EDITORS)
  const unassign = useUnassign()

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
                {canEditRoster && (
                  <button
                    type="button"
                    className="button button--quiet"
                    disabled={unassign.isPending}
                    onClick={() => unassign.mutate(assignment.assignmentId)}
                  >
                    Unassign
                  </button>
                )}
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
      // A fully covered slot has nowhere to put anyone: any window would overlap the person
      // already there, and the server refuses that outright. Unassign first.
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
    <>
      {unassign.error !== null && (
        <ErrorPanel title="Could not remove that assignment" error={unassign.error} />
      )}
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
              value: (row) =>
                row.allowedPositionIds.map((id) => positionName.get(id) ?? `#${id}`).join(' / '),
            },
            { header: 'Coverage', value: (row) => row.coverage },
            {
              header: 'Crew',
              value: (row) => row.assignments.map((a) => `${a.name} (${a.sam})`).join('; '),
            },
            {
              header: 'Roll-up',
              value: (row) => row.assignments.map((a) => a.evaluation.rollUp).join('; '),
            },
          ],
        }}
      />
    </>
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
            Raise
          </Link>
        ),
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
              className={clashed ? 'button' : 'button button--primary'}
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
      <p className="note">Ranked by the server (§5.4).</p>

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
