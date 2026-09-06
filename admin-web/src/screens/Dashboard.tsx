import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQueries } from '@tanstack/react-query'
import { keys, useExpiryAlerts, usePartnerships, useRequirements } from '../api/queries'
import {
  api,
  type Cell,
  type CrewChange,
  type ExpiryAlert,
  type Partnership,
  type SwingEvaluation,
} from '../api/client'
import { useSession } from '../api/session'
import { defaultCrewChange } from '../components/SwingSelector'
import { DataTable, type Column } from '../components/DataTable'
import { ErrorPanel } from '../components/ErrorPanel'
import { Modal } from '../components/Modal'
import { RequirementLabel } from '../components/RequirementLabel'
import { ShipBar } from '../components/ShipBar'
import { Spinner } from '../components/Spinner'
import { StateChip } from '../components/StateChip'
import { Band, Cut, Lead, Ruler, RulerRow } from '../components/Ruler'
import { requirementLookup, requirementParts } from '../domain/requirements'
import {
  CELL_STATE_ORDER,
  EXPIRY_LEAD_DAYS_DEFAULT,
  cellState,
  cellStateRank,
  expiryImpact,
  needsAttention,
  slotRef,
  type Tone,
} from '../domain/enums'
import {
  daysBetween,
  epochDay,
  formatDate,
  formatDayMonth,
  formatShortRange,
} from '../domain/dates'

/**
 * ADM-1 — the landing page.
 *
 * Every partnership's current or next swing on one time axis, so the question the module exists to
 * answer — *which of these will not sail clean, and how long have I got* — is answered by the
 * shape of the page rather than by reading five cards. The cutoff's distance from the datum line
 * is the time remaining; see components/Ruler.tsx.
 *
 * Nothing here re-derives compliance. State counts and quota results arrive from the §5.3
 * evaluation and are rendered as received (AUTH-1). The only choice made here is *which* swing to
 * show, and that is calendar selection (`defaultCrewChange`), not evaluation.
 */

/*
 * The state column lists what needs attention and stays silent about what does not.
 *
 * The roll-up question here is "does this need me". Tallying `20 OK` beside `2 Gap` made every row
 * wrap and put the loudest number on the least urgent fact; `pending` and `exempt` are someone
 * else's move or already resolved. The set of states that need a person is `needsAttention` in
 * domain/enums.ts — the app's existing, tested definition rather than a second one invented here.
 * The full breakdown for one swing is on the planner.
 */

/** A cutoff this close is called out in red — the row is the one to act on today. */
const CUTOFF_URGENT_DAYS = 3

interface SwingRow {
  readonly partnership: Partnership
  readonly swing: CrewChange | null
  readonly evaluation: SwingEvaluation | undefined
  readonly isPending: boolean
  readonly error: Error | null
}

/** One swing, fully loaded — what the certification-state modal is opened on. */
interface Inspection {
  readonly partnership: Partnership
  readonly swing: CrewChange
  readonly evaluation: SwingEvaluation
}

export function Dashboard(): React.ReactNode {
  const session = useSession()
  const partnerships = usePartnerships()
  const [leadDays, setLeadDays] = useState(EXPIRY_LEAD_DAYS_DEFAULT)
  const [inspecting, setInspecting] = useState<Inspection | null>(null)

  const list = partnerships.data ?? []

  // Both fan-outs are one hook each, so the number of partnerships can change between renders
  // without breaking hook order. The keys are the shared ones from `queries.ts`, so a planner
  // visit and a dashboard visit hit the same cache entry.
  const calendars = useQueries({
    queries: list.map((partnership) => ({
      queryKey: keys.crewChanges(partnership.abbrev),
      queryFn: () => api.crewChanges(partnership.abbrev),
      staleTime: 60 * 60 * 1000,
    })),
  })

  const chosen = list.map((partnership, index) => ({
    partnership,
    swing: defaultCrewChange(calendars[index]?.data ?? [], session.today),
  }))

  const evaluations = useQueries({
    queries: chosen.map(({ partnership, swing }) => ({
      queryKey: keys.swing(partnership.abbrev, swing?.ccId ?? ''),
      queryFn: () => api.swingEvaluation(partnership.abbrev, swing?.ccId as string),
      enabled: swing !== null,
    })),
  })

  const rows: SwingRow[] = chosen.map(({ partnership, swing }, index) => ({
    partnership,
    swing,
    evaluation: evaluations[index]?.data,
    isPending: (calendars[index]?.isPending ?? false) || (evaluations[index]?.isPending ?? false),
    error: calendars[index]?.error ?? evaluations[index]?.error ?? null,
  }))

  if (partnerships.isPending) return <Spinner label="Loading partnerships" />
  if (partnerships.error !== null) {
    return <ErrorPanel title="Could not load partnerships" error={partnerships.error} />
  }

  const dirty = rows.filter((row) => row.evaluation !== undefined && isDirty(row.evaluation))
  const soonest = rows
    .filter((row) => row.swing !== null && epochDay(row.swing.cutoff) >= epochDay(session.today))
    .sort((a, b) => epochDay(a.swing?.cutoff ?? '') - epochDay(b.swing?.cutoff ?? ''))[0]

  // The expiry alerts' "affected swing": the swing already on the axis with this person aboard.
  // A presentation join against evaluations the page has loaded — never a second evaluation.
  const swingByPerson = new Map<number, Inspection>()
  for (const row of rows) {
    if (row.swing === null || row.evaluation === undefined) continue
    for (const assignment of row.evaluation.assignments) {
      if (!swingByPerson.has(assignment.personId)) {
        swingByPerson.set(assignment.personId, {
          partnership: row.partnership,
          swing: row.swing,
          evaluation: row.evaluation,
        })
      }
    }
  }

  return (
    <div className="screen">
      <header className="screen__header screen__header--bar">
        <div>
          <div className="screen__headline">
            <h1 className="screen__title">{headline(rows, dirty.length)}</h1>
            {soonest?.swing != null && <CutoffBadge today={session.today} cutoff={soonest.swing.cutoff} />}
          </div>
          <p className="screen__subtitle">
            Every partnership's current or next crew change, on one axis. The dashed line is today —
            the server's business date in AWST, not this browser's. Cutoffs sit to its left once
            passed, so the distance is the time you have left.
          </p>
        </div>
        {/* The ship in view, chosen here as well as in the rail, with add and remove beside it. */}
        <ShipBar />
      </header>

      {list.length === 0 ? (
        <p className="empty">No partnerships are visible to your roles.</p>
      ) : (
        <SwingAxis rows={rows} today={session.today} onInspect={setInspecting} />
      )}

      <section className="section">
        <div className="section__header">
          <div>
            <h2 className="section__title">Expiry alerts</h2>
            <p className="section__note">
              Impact is measured against the assignment the holding affects.
            </p>
          </div>
          <label className="field field--inline" style={{ width: 150 }}>
            <span className="field__label">Lead time</span>
            <select
              className="input"
              value={leadDays}
              onChange={(event) => setLeadDays(Number(event.target.value))}
            >
              {[30, 60, 90, 180].map((days) => (
                <option key={days} value={days}>
                  {days} days
                </option>
              ))}
            </select>
          </label>
        </div>
        <ExpiryAlerts
          leadDays={leadDays}
          swingFor={(personId) => swingByPerson.get(personId) ?? null}
          onInspect={setInspecting}
        />
      </section>

      {inspecting !== null && (
        <SwingStateModal target={inspecting} onClose={() => setInspecting(null)} />
      )}
    </div>
  )
}

/** The answer first, in a sentence. */
function headline(rows: readonly SwingRow[], dirtyCount: number): string {
  const evaluated = rows.filter((row) => row.evaluation !== undefined).length
  if (evaluated === 0) return 'No swings to evaluate yet.'
  if (dirtyCount === 0) return 'Every swing sails clean.'
  if (dirtyCount === 1) return 'One swing will not sail clean.'
  return `${dirtyCount} swings will not sail clean.`
}

/**
 * The soonest cutoff still ahead, as a chip beside the headline.
 *
 * Critical at three days or fewer and warning above it: a cutoff is always a deadline, so it is
 * never a quiet fact — what changes with proximity is whether it is the thing to do today.
 */
function CutoffBadge({ today, cutoff }: { today: string; cutoff: string }): React.ReactNode {
  const days = daysBetween(today, cutoff)
  const tone = days <= CUTOFF_URGENT_DAYS ? 'critical' : 'warning'
  const when = days === 0 ? 'today' : days === 1 ? 'tomorrow' : `in ${days} days`
  return <span className={`chip chip--${tone}`}>Next cutoff {when}</span>
}

/**
 * A swing needs someone when a cell needs attention or a quota is short.
 *
 * The quota half matters: §5.1 step 2 lets a footnote quota fall short with no individual gap
 * behind it, so a per-person tally alone would call that swing clean.
 */
function isDirty(evaluation: SwingEvaluation): boolean {
  const cells = Object.entries(evaluation.stateCounts).some(
    ([state, count]) => count > 0 && needsAttention(state),
  )
  return cells || evaluation.quotas.some((quota) => !quota.satisfied)
}

function SwingAxis({
  rows,
  today,
  onInspect,
}: {
  rows: readonly SwingRow[]
  today: string
  onInspect: (target: Inspection) => void
}): React.ReactNode {
  const dated = rows.filter((row): row is SwingRow & { swing: CrewChange } => row.swing !== null)

  if (dated.length === 0) {
    const anyPending = rows.some((row) => row.isPending)
    if (anyPending) return <Spinner label="Loading calendars" />
    return <p className="empty">No swings are recorded for your partnerships.</p>
  }

  // The domain spans every cutoff and every swing end on screen, and always includes today so the
  // datum has a real position rather than being pinned to an end it has run off.
  const edges = [today, ...dated.flatMap((row) => [row.swing.cutoff, row.swing.to])]
  const from = edges.reduce((a, b) => (epochDay(a) <= epochDay(b) ? a : b))
  const to = edges.reduce((a, b) => (epochDay(a) >= epochDay(b) ? a : b))

  return (
    <>
      <Ruler from={from} to={to} today={today} labelHeader="Partnership" valueHeader="State">
        {dated.map((row) => (
          <PartnershipRow key={row.partnership.id} row={row} today={today} onInspect={onInspect} />
        ))}
      </Ruler>

      {rows
        .filter((row) => row.error !== null)
        .map((row) => (
          <ErrorPanel
            key={row.partnership.id}
            title={`Could not evaluate ${row.partnership.abbrev}`}
            error={row.error as Error}
          />
        ))}
    </>
  )
}

function PartnershipRow({
  row,
  today,
  onInspect,
}: {
  row: SwingRow & { swing: CrewChange }
  today: string
  onInspect: (target: Inspection) => void
}): React.ReactNode {
  const navigate = useNavigate()
  const { partnership, swing, evaluation } = row
  const days = daysBetween(today, swing.cutoff)
  const passed = days < 0
  const cutoffLabel = passed
    ? `Cutoff ${formatDayMonth(swing.cutoff)} (passed)`
    : `Cutoff ${formatDayMonth(swing.cutoff)} · ${days === 0 ? 'today' : days === 1 ? '1 day' : `${days} days`}`

  const attention = evaluation !== undefined && isDirty(evaluation)

  return (
    <RulerRow
      label={partnership.abbrev}
      note={
        partnership.vesselClass === null
          ? partnership.name
          : `${partnership.name} · ${partnership.vesselClass}`
      }
      dates={`${cutoffLabel} · swing ${formatShortRange(swing.from, swing.to)}`}
      attention={attention}
      onOpen={() =>
        void navigate(
          `/planner?partnership=${encodeURIComponent(partnership.abbrev)}&cc=${encodeURIComponent(swing.ccId)}`,
        )
      }
      openLabel={`Open ${partnership.abbrev} ${swing.ccId} in the swing planner`}
      value={<SwingStates row={row} onInspect={onInspect} />}
    >
      <Cut date={swing.cutoff} label={cutoffLabel} tight={!passed && days <= CUTOFF_URGENT_DAYS} />
      <Lead from={swing.cutoff} to={swing.from} />
      <Band
        from={swing.from}
        to={swing.to}
        label={`${swing.ccId} · ${formatShortRange(swing.from, swing.to)}`}
        tone={evaluation === undefined ? 'evaluating' : attention ? 'attention' : 'clean'}
      />
    </RulerRow>
  )
}

function SwingStates({
  row,
  onInspect,
}: {
  row: SwingRow
  onInspect: (target: Inspection) => void
}): React.ReactNode {
  // "Spinner + label" is the rule everywhere else; on the ruler a pending evaluation is this chip
  // beside a dashed bar, because a skeleton where a dated bar goes would be a shape asserting dates
  // it does not have.
  if (row.isPending) return <span className="chip chip--muted">Evaluating…</span>

  const { swing, evaluation } = row
  if (evaluation === undefined) return <span className="chip chip--muted">Evaluating…</span>

  const open =
    swing === null ? null : () => onInspect({ partnership: row.partnership, swing, evaluation })

  const noteworthy = CELL_STATE_ORDER.filter(
    (state) => needsAttention(state) && (evaluation.stateCounts[state] ?? 0) > 0,
  )
  const short = evaluation.quotas.filter((quota) => !quota.satisfied).length

  if (noteworthy.length === 0 && short === 0) {
    return <span className="chip chip--good">All clear</span>
  }

  return (
    <ul className="counts">
      {noteworthy.map((state) => (
        <li key={state} className="counts__item">
          <CountChip
            tone={cellState(state).tone}
            title={cellState(state).description}
            count={evaluation.stateCounts[state] ?? 0}
            label={cellState(state).label}
            onOpen={open}
          />
        </li>
      ))}
      {short > 0 && (
        <li className="counts__item">
          {/* A quota can be short with no individual gap behind it (§5.1 step 2), which is exactly
              the failure a per-person tally hides. */}
          <CountChip
            tone="critical"
            title="A footnote quota is not met on this swing."
            count={short}
            label={short === 1 ? 'Quota short' : 'Quotas short'}
            onOpen={open}
          />
        </li>
      )}
    </ul>
  )
}

/** A state count — a button when there is a loaded swing to open the detail of. */
function CountChip({
  tone,
  title,
  count,
  label,
  onOpen,
}: {
  tone: Tone
  title: string
  count: number
  label: string
  onOpen: (() => void) | null
}): React.ReactNode {
  const body = (
    <>
      <span className="counts__value">{count}</span>
      {label}
    </>
  )
  if (onOpen === null) {
    return (
      <span className={`chip chip--${tone}`} title={title}>
        {body}
      </span>
    )
  }
  return (
    <button
      type="button"
      className={`chip chip--${tone}`}
      title={`${title} Click for every certification on this swing.`}
      onClick={onOpen}
    >
      {body}
    </button>
  )
}

function ExpiryAlerts({
  leadDays,
  swingFor,
  onInspect,
}: {
  leadDays: number
  /** The loaded swing this person is assigned on, if the axis has one — else null. */
  swingFor: (personId: number) => Inspection | null
  onInspect: (target: Inspection) => void
}): React.ReactNode {
  const alerts = useExpiryAlerts(leadDays)
  const requirements = useRequirements()

  if (alerts.isPending) return <Spinner label="Loading expiry alerts" />
  if (alerts.error !== null) return <ErrorPanel title="Could not load expiry alerts" error={alerts.error} />

  const codeFor = requirementLookup(requirements.data)
  const partsFor = requirementParts(requirements.data)

  const columns: Column<ExpiryAlert>[] = [
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
    {
      id: 'requirement',
      header: 'Requirement',
      accessorFn: (row) => codeFor(row.requirementId),
      cell: ({ row }) => <RequirementLabel {...partsFor(row.original.requirementId)} />,
    },
    {
      id: 'expiry',
      header: 'Expires',
      accessorFn: (row) => row.expiry,
      cell: ({ row }) => formatDate(row.original.expiry),
    },
    {
      id: 'daysRemaining',
      header: 'Days',
      accessorFn: (row) => row.daysRemaining,
      cell: ({ row }) => (
        <span className={row.original.daysRemaining <= 0 ? 'value--attention' : undefined}>
          {row.original.daysRemaining}
        </span>
      ),
    },
    {
      id: 'impact',
      header: 'Impact',
      accessorFn: (row) => row.impact,
      // The same pill-opens-the-swing behaviour as the axis above: when the affected swing is on
      // the page, its impact chip opens that swing's full certification list.
      cell: ({ row }) => {
        const target = swingFor(row.original.personId)
        if (target === null) return <StateChip kind="impact" state={row.original.impact} />
        const display = expiryImpact(row.original.impact)
        return (
          <button
            type="button"
            className={`chip chip--${display.tone}`}
            title={`${display.description} Click for ${target.partnership.abbrev} ${target.swing.ccId}'s certification states.`}
            onClick={() => onInspect(target)}
          >
            {display.label}
          </button>
        )
      },
    },
  ]

  return (
    <DataTable
      rows={alerts.data}
      columns={columns}
      filterPlaceholder="Filter by person or requirement"
      empty={`Nothing expires within ${leadDays} days.`}
      csv={{
        filename: `expiry-alerts-${leadDays}d.csv`,
        columns: [
          { header: 'Sam #', value: (row) => row.sam },
          { header: 'Name', value: (row) => row.name },
          { header: 'Requirement', value: (row) => codeFor(row.requirementId) },
          { header: 'Expiry', value: (row) => row.expiry },
          { header: 'Days remaining', value: (row) => row.daysRemaining },
          { header: 'Impact', value: (row) => row.impact },
        ],
      }}
    />
  )
}

/** One assignment × requirement cell, flattened for the modal's table. */
interface SwingCellRow extends Cell {
  readonly personId: number
  readonly name: string
  readonly sam: string
  readonly slotRef: number
}

/**
 * The certification-state modal: every crew member × requirement cell for one swing, worst first.
 *
 * This is the drill-down behind the dashboard's count pills — the counts say "2 Gap", this says
 * whose, for what, and when it lapses. All of it is the §5.2 evaluation the page already holds;
 * the sort mirrors the engine's own worst-first ordering (`cellStateRank`), it does not re-derive
 * anything.
 */
function SwingStateModal({
  target,
  onClose,
}: {
  target: Inspection
  onClose: () => void
}): React.ReactNode {
  const requirements = useRequirements()
  const codeFor = requirementLookup(requirements.data)
  const partsFor = requirementParts(requirements.data)
  const { partnership, swing, evaluation } = target

  const cells: SwingCellRow[] = evaluation.assignments
    .flatMap((assignment) =>
      assignment.evaluation.cells.map((cell) => ({
        ...cell,
        personId: assignment.personId,
        name: assignment.name,
        sam: assignment.sam,
        slotRef: assignment.slotRef,
      })),
    )
    .sort(
      (a, b) =>
        cellStateRank(a.state) - cellStateRank(b.state) ||
        codeFor(a.requirementId).localeCompare(codeFor(b.requirementId)) ||
        a.name.localeCompare(b.name),
    )

  const short = evaluation.quotas.filter((quota) => !quota.satisfied)

  const columns: Column<SwingCellRow>[] = [
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
    {
      id: 'slot',
      header: 'Slot',
      accessorFn: (row) => row.slotRef,
      cell: ({ row }) => <span className="mono">{slotRef(row.original.slotRef)}</span>,
    },
    {
      id: 'requirement',
      header: 'Requirement',
      accessorFn: (row) => codeFor(row.requirementId),
      cell: ({ row }) => <RequirementLabel {...partsFor(row.original.requirementId)} />,
    },
    {
      id: 'level',
      header: 'Level',
      accessorFn: (row) => row.level,
      cell: ({ row }) => <span className="mono">{row.original.level}</span>,
    },
    {
      id: 'expiry',
      header: 'Expires',
      accessorFn: (row) => row.expiry ?? '',
      cell: ({ row }) => formatDate(row.original.expiry),
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
    <Modal
      title={`${partnership.abbrev} ${swing.ccId} — certification states`}
      note={`Swing ${formatShortRange(swing.from, swing.to)} · every assigned crew member × requirement, worst first. States are the server's §5.2 evaluation.`}
      wide
      onClose={onClose}
    >
      {/* A quota can be short with no individual gap behind it (§5.1 step 2), so a shortfall has no
          row in the table below and has to be stated separately or it disappears. */}
      {short.length > 0 && (
        <ul className="list-plain list-plain--tight">
          {short.map((quota) => (
            <li
              key={`${quota.footnote}-${quota.requirementId}-${quota.shift ?? 'swing'}`}
              className="tint-card tag-row"
            >
              <span className="mono">{quota.footnote}</span>
              <RequirementLabel {...partsFor(quota.requirementId)} />
              <span>
                {quota.actual} of {quota.min}
              </span>
              <span className="chip chip--critical">Short by {quota.shortfall}</span>
            </li>
          ))}
        </ul>
      )}

      <DataTable
        rows={cells}
        columns={columns}
        filterPlaceholder="Filter by person, requirement or state"
        empty="Nobody is assigned to this swing yet."
        csv={{
          filename: `swing-${swing.ccId}-states.csv`,
          columns: [
            { header: 'State', value: (row) => row.state },
            { header: 'Sam #', value: (row) => row.sam },
            { header: 'Name', value: (row) => row.name },
            { header: 'Slot', value: (row) => row.slotRef },
            { header: 'Requirement', value: (row) => codeFor(row.requirementId) },
            { header: 'Level', value: (row) => row.level },
            { header: 'Expiry', value: (row) => row.expiry },
            { header: 'Notes', value: (row) => row.notes.join(' | ') },
          ],
        }}
      />
    </Modal>
  )
}

