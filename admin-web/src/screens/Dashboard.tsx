import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useCrewChanges, useExpiryAlerts, usePartnerships, useRequirements } from '../api/queries'
import { api, type ExpiryAlert, type Partnership, type Requirement } from '../api/client'
import { useQuery } from '@tanstack/react-query'
import { useSession } from '../api/session'
import { defaultCrewChange } from '../components/SwingSelector'
import { DataTable, type Column } from '../components/DataTable'
import { ErrorPanel } from '../components/ErrorPanel'
import { Spinner } from '../components/Spinner'
import { StateChip } from '../components/StateChip'
import { CELL_STATE_ORDER, needsAttention } from '../domain/enums'
import { formatDate, formatDateRange } from '../domain/dates'

/**
 * ADM-1 — the landing page: a card per partnership for its current or next swing, and the
 * expiry-alert worklist.
 *
 * Every number on a card is the server's: the card shows `stateCounts` and quota results from
 * the §5.3 evaluation exactly as returned. The only choice made here is *which* swing to show.
 */
export function Dashboard(): React.ReactNode {
  const partnerships = usePartnerships()
  const [leadDays, setLeadDays] = useState(90)

  if (partnerships.isPending) return <Spinner label="Loading partnerships" />
  if (partnerships.error !== null) {
    return <ErrorPanel title="Could not load partnerships" error={partnerships.error} />
  }

  return (
    <div className="screen">
      <header className="screen__header">
        <h1 className="screen__title">Dashboard</h1>
        <p className="screen__subtitle">Current swing compliance across your partnerships.</p>
      </header>

      {partnerships.data.length === 0 ? (
        <p className="empty">No partnerships are visible to your roles.</p>
      ) : (
        <div className="cards">
          {partnerships.data.map((partnership) => (
            <PartnershipCard key={partnership.id} partnership={partnership} />
          ))}
        </div>
      )}

      <section className="section">
        <div className="section__header">
          <h2 className="section__title">Expiry alerts</h2>
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

function PartnershipCard({ partnership }: { partnership: Partnership }): React.ReactNode {
  const session = useSession()
  const crewChanges = useCrewChanges(partnership.abbrev)
  const swing = crewChanges.data === undefined ? null : defaultCrewChange(crewChanges.data, session.today)

  const evaluation = useQuery({
    queryKey: ['swing', partnership.abbrev, swing?.ccId ?? ''],
    queryFn: () => api.swingEvaluation(partnership.abbrev, swing?.ccId as string),
    enabled: swing !== null,
  })

  return (
    <article className="card">
      <header className="card__header">
        <h2 className="card__title">
          {partnership.abbrev}
          <span className="card__subtitle">{partnership.name}</span>
        </h2>
        {swing !== null && (
          <Link className="card__link" to={`/planner?partnership=${partnership.abbrev}&cc=${swing.ccId}`}>
            {swing.ccId} · {formatDateRange(swing.from, swing.to)}
          </Link>
        )}
      </header>

      {crewChanges.isPending && <Spinner label="Loading calendar" />}
      {swing === null && !crewChanges.isPending && <p className="card__empty">No swings recorded.</p>}
      {evaluation.error !== null && (
        <ErrorPanel title={`Could not evaluate ${partnership.abbrev}`} error={evaluation.error} />
      )}
      {evaluation.isPending && swing !== null && <Spinner label="Evaluating" />}

      {evaluation.data !== undefined && (
        <>
          <ul className="counts">
            {CELL_STATE_ORDER.filter((state) => (evaluation.data.stateCounts[state] ?? 0) > 0).map(
              (state) => (
                <li key={state} className={needsAttention(state) ? 'counts__item counts__item--attention' : 'counts__item'}>
                  <StateChip state={state} />
                  <span className="counts__value">{evaluation.data.stateCounts[state]}</span>
                </li>
              ),
            )}
          </ul>

          <dl className="card__facts">
            <div>
              <dt>Assigned</dt>
              <dd>{evaluation.data.assignments.length}</dd>
            </div>
            <div>
              <dt>Open slots</dt>
              <dd className={evaluation.data.openSlots.length > 0 ? 'value--attention' : undefined}>
                {evaluation.data.openSlots.length}
              </dd>
            </div>
            <div>
              <dt>Partly covered</dt>
              <dd>{evaluation.data.partiallyCoveredSlots.length}</dd>
            </div>
            <div>
              <dt>Quota shortfalls</dt>
              <dd
                className={
                  evaluation.data.quotas.some((quota) => !quota.satisfied) ? 'value--attention' : undefined
                }
              >
                {evaluation.data.quotas.filter((quota) => !quota.satisfied).length}
              </dd>
            </div>
            <div>
              <dt>Cutoff</dt>
              <dd>{formatDate(evaluation.data.cutoff)}</dd>
            </div>
          </dl>
        </>
      )}
    </article>
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
    { id: 'sam', header: 'Sam #', accessorFn: (row) => row.sam },
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
      filterPlaceholder="Filter alerts"
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
