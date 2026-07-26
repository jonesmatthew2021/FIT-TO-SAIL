import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQueries } from '@tanstack/react-query'
import { keys, useExpiryAlerts, usePartnerships, useRequirements } from '../api/queries'
import {
  api,
  type CrewChange,
  type ExpiryAlert,
  type Partnership,
  type Requirement,
  type SwingEvaluation,
} from '../api/client'
import { useSession } from '../api/session'
import { defaultCrewChange } from '../components/SwingSelector'
import { DataTable, type Column } from '../components/DataTable'
import { ErrorPanel } from '../components/ErrorPanel'
import { Spinner } from '../components/Spinner'
import { StateChip } from '../components/StateChip'
import { Band, Cut, Lead, Ruler, RulerRow } from '../components/Ruler'
import { CELL_STATE_ORDER, cellState, needsAttention } from '../domain/enums'
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

export function Dashboard(): React.ReactNode {
  const session = useSession()
  const partnerships = usePartnerships()
  const [leadDays, setLeadDays] = useState(90)

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

  return (
    <div className="screen">
      <header className="screen__header">
        <div className="screen__headline">
          <h1 className="screen__title">{headline(rows, dirty.length)}</h1>
          {soonest?.swing != null && <CutoffBadge today={session.today} cutoff={soonest.swing.cutoff} />}
        </div>
        <p className="screen__subtitle">
          Every partnership's current or next crew change, on one axis. The dashed line is today —
          the server's business date, not this browser's. Cutoffs sit to its right, so the distance
          is the time you have left.
        </p>
      </header>

      {list.length === 0 ? (
        <p className="empty">No partnerships are visible to your roles.</p>
      ) : (
        <SwingAxis rows={rows} today={session.today} />
      )}

      <section className="section">
        <div className="section__header">
          <div>
            <h2 className="section__title">Expiry alerts</h2>
            <p className="section__note">
              Impact is measured against the assignment the holding affects.
            </p>
          </div>
          <label className="field field--inline">
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
        <ExpiryAlerts leadDays={leadDays} />
      </section>
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

function CutoffBadge({ today, cutoff }: { today: string; cutoff: string }): React.ReactNode {
  const days = daysBetween(today, cutoff)
  const tone = days <= CUTOFF_URGENT_DAYS ? 'critical' : 'muted'
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
}: {
  rows: readonly SwingRow[]
  today: string
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
          <PartnershipRow key={row.partnership.id} row={row} today={today} />
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
}: {
  row: SwingRow & { swing: CrewChange }
  today: string
}): React.ReactNode {
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
      value={<SwingStates row={row} />}
    >
      <Cut date={swing.cutoff} label={cutoffLabel} tight={!passed && days <= CUTOFF_URGENT_DAYS} />
      <Lead from={swing.cutoff} to={swing.from} />
      <Band
        from={swing.from}
        to={swing.to}
        label={`${swing.ccId} · ${formatShortRange(swing.from, swing.to)}`}
        attention={attention}
      />
    </RulerRow>
  )
}

function SwingStates({ row }: { row: SwingRow }): React.ReactNode {
  if (row.isPending) return <span className="muted">Evaluating…</span>
  if (row.evaluation === undefined) return <span className="muted">—</span>

  const evaluation = row.evaluation
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
          <span className={`chip chip--${cellState(state).tone}`} title={cellState(state).description}>
            <span className="counts__value">{evaluation.stateCounts[state]}</span>
            {cellState(state).label}
          </span>
        </li>
      ))}
      {short > 0 && (
        <li className="counts__item">
          {/* A quota can be short with no individual gap behind it (§5.1 step 2), which is exactly
              the failure a per-person tally hides. */}
          <span className="chip chip--critical" title="A footnote quota is not met on this swing.">
            <span className="counts__value">{short}</span>
            {short === 1 ? 'Quota short' : 'Quotas short'}
          </span>
        </li>
      )}
    </ul>
  )
}

function ExpiryAlerts({ leadDays }: { leadDays: number }): React.ReactNode {
  const alerts = useExpiryAlerts(leadDays)
  const requirements = useRequirements()

  if (alerts.isPending) return <Spinner label="Loading expiry alerts" />
  if (alerts.error !== null) return <ErrorPanel title="Could not load expiry alerts" error={alerts.error} />

  const codeFor = requirementLookup(requirements.data)

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
      cell: ({ row }) => <StateChip kind="impact" state={row.original.impact} />,
    },
  ]

  return (
    <DataTable
      rows={alerts.data}
      columns={columns}
      filterPlaceholder="Filter by person or requirement"
      empty={`Nothing expires within ${leadDays} days.`}
      rowClassName={(row) => (row.impact === 'expired_before_swing' ? 'table__row--attention' : undefined)}
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

/** Requirement ids arrive on the wire; the catalogue is cached once and joined here. */
export function requirementLookup(
  requirements: readonly Requirement[] | undefined,
): (id: number) => string {
  const byId = new Map((requirements ?? []).map((requirement) => [requirement.id, requirement]))
  return (id) => {
    const requirement = byId.get(id)
    return requirement === undefined ? `#${id}` : `${requirement.code} ${requirement.title}`
  }
}
