import { Link, useSearchParams } from 'react-router-dom'
import { usePartnerships, useCrewChanges, useRegister } from '../api/queries'
import type { RegisterFilters, RegisterRecord } from '../api/client'
import { useHasRole } from '../api/session'
import { DataTable, type Column } from '../components/DataTable'
import { ErrorPanel } from '../components/ErrorPanel'
import { Spinner } from '../components/Spinner'
import { formatDate, formatDateRange } from '../domain/dates'
import { REGISTER_TYPES, registerStatusTone } from '../domain/enums'

/** §3: the Crew Coordinator raises requests; OPS also raises its own. */
export const REGISTER_RAISERS = ['crew_coordinator', 'workflow_manager', 'system_administrator'] as const

/** Q14: the Workflow Manager is the sole decision authority. */
export const REGISTER_DECIDERS = ['workflow_manager', 'system_administrator'] as const

/**
 * ADM-4 — the exemption and query register.
 *
 * The filters live in the URL, so a filtered register is a link someone can paste ("every entity
 * view deep-links", §6). The default is the open work, because that is the day job — but the
 * whole history is one select away and is the reason the module replaces a 445-row workbook
 * rather than just tracking what is outstanding.
 */
export function Register(): React.ReactNode {
  const [params, setParams] = useSearchParams()
  const canRaise = useHasRole(...REGISTER_RAISERS)

  const filters: RegisterFilters = {
    partnership: params.get('partnership') ?? undefined,
    cc: params.get('cc') ?? undefined,
    type: params.get('type') ?? undefined,
    state: (params.get('state') as RegisterFilters['state']) ?? 'open',
  }

  const partnerships = usePartnerships()
  const crewChanges = useCrewChanges(filters.partnership ?? null)
  const records = useRegister(filters)

  function setFilter(key: string, value: string): void {
    const next = new URLSearchParams(params)
    if (value === '') next.delete(key)
    else next.set(key, value)
    // A CC id only means something inside a partnership, so changing the partnership drops it
    // rather than leaving a filter that silently matches nothing.
    if (key === 'partnership') next.delete('cc')
    setParams(next)
  }

  const columns: Column<RegisterRecord>[] = [
    {
      id: 'recordId',
      header: 'Record',
      accessorFn: (row) => row.recordId,
      cell: ({ row }) => (
        <Link className="mono" to={`/register/${encodeURIComponent(row.original.recordId)}`}>
          {row.original.recordId}
        </Link>
      ),
    },
    {
      id: 'status',
      header: 'Status',
      accessorFn: (row) => row.status,
      cell: ({ row }) => (
        <span className={`chip chip--${registerStatusTone(row.original.status)}`}>
          {row.original.status}
        </span>
      ),
    },
    { id: 'type', header: 'Type', accessorFn: (row) => row.type },
    {
      id: 'person',
      header: 'Person',
      accessorFn: (row) => row.personName ?? '',
      cell: ({ row }) =>
        row.original.personId === null ? (
          <span className="muted">—</span>
        ) : (
          <Link to={`/people/${row.original.personId}`}>{row.original.personName}</Link>
        ),
    },
    {
      id: 'requirement',
      header: 'Requirement',
      accessorFn: (row) => row.requirementCode ?? row.reqRaw ?? '',
      cell: ({ row }) => <RequirementCell record={row.original} />,
    },
    { id: 'partnership', header: 'Partnership', accessorFn: (row) => row.partnershipAbbrev },
    {
      id: 'cc',
      header: 'Swing',
      accessorFn: (row) => row.ccId ?? '',
      cell: ({ row }) => row.original.ccId ?? <span className="muted">—</span>,
    },
    {
      id: 'raised',
      header: 'Raised',
      accessorFn: (row) => row.raisedDate,
      cell: ({ row }) => (
        <>
          {formatDate(row.original.raisedDate)}
          {/* Q17: lateness is a fact about the record, not a footnote in a log. */}
          {row.original.lateSubmissionAcknowledged && (
            <span className="chip chip--caution" title="Lodged after the swing's submission cutoff">
              late
            </span>
          )}
        </>
      ),
    },
    {
      id: 'approval',
      header: 'Approved for',
      accessorFn: (row) => row.approvalFrom ?? '',
      cell: ({ row }) =>
        row.original.approvalFrom === null || row.original.approvalTo === null ? (
          <span className="muted">—</span>
        ) : (
          formatDateRange(row.original.approvalFrom, row.original.approvalTo)
        ),
    },
  ]

  return (
    <div className="screen">
      <header className="screen__header">
        <h1 className="screen__title">Register</h1>
        <p className="screen__subtitle">
          ADM-4 — exemption requests and queries. An approved record is what turns a gap into an
          exemption on the planner (§5.1).
        </p>
      </header>

      <div className="selector">
        <label className="field field--inline">
          <span className="field__label">State</span>
          <select
            className="input"
            value={filters.state ?? 'open'}
            onChange={(event) => setFilter('state', event.target.value)}
          >
            <option value="open">Open</option>
            <option value="closed">Closed</option>
            <option value="all">All</option>
          </select>
        </label>

        <label className="field field--inline">
          <span className="field__label">Partnership</span>
          <select
            className="input"
            value={filters.partnership ?? ''}
            onChange={(event) => setFilter('partnership', event.target.value)}
          >
            <option value="">All partnerships</option>
            {(partnerships.data ?? []).map((partnership) => (
              <option key={partnership.id} value={partnership.abbrev}>
                {partnership.abbrev}
              </option>
            ))}
          </select>
        </label>

        <label className="field field--inline">
          <span className="field__label">Swing</span>
          <select
            className="input"
            value={filters.cc ?? ''}
            disabled={filters.partnership === undefined}
            onChange={(event) => setFilter('cc', event.target.value)}
          >
            <option value="">All swings</option>
            {(crewChanges.data ?? []).map((crewChange) => (
              <option key={crewChange.id} value={crewChange.ccId}>
                {crewChange.ccId}
              </option>
            ))}
          </select>
        </label>

        <label className="field field--inline">
          <span className="field__label">Type</span>
          <select
            className="input"
            value={filters.type ?? ''}
            onChange={(event) => setFilter('type', event.target.value)}
          >
            <option value="">All types</option>
            {REGISTER_TYPES.map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </select>
        </label>

        {canRaise && (
          <Link className="button button--primary" to="/register/new">
            Raise a request
          </Link>
        )}
      </div>

      {records.isPending && <Spinner label="Loading the register" />}
      {records.error !== null && (
        <ErrorPanel title="Could not load the register" error={records.error} />
      )}

      {records.data !== undefined && (
        <DataTable
          rows={records.data}
          columns={columns}
          filterPlaceholder="Search record, person or requirement"
          empty="No records match these filters."
          csv={{
            filename: `register-${filters.state ?? 'open'}.csv`,
            columns: [
              { header: 'Record', value: (row) => row.recordId },
              { header: 'Type', value: (row) => row.type },
              { header: 'Status', value: (row) => row.status },
              { header: 'Outcome', value: (row) => row.outcome },
              { header: 'Sam #', value: (row) => row.sam },
              { header: 'Person', value: (row) => row.personName },
              { header: 'Position', value: (row) => row.positionName },
              { header: 'Requirement', value: (row) => row.requirementCode ?? row.reqRaw },
              { header: 'Partnership', value: (row) => row.partnershipAbbrev },
              { header: 'Swing', value: (row) => row.ccId },
              { header: 'Effective from', value: (row) => row.effectiveFrom },
              { header: 'Effective to', value: (row) => row.effectiveTo },
              { header: 'Approval from', value: (row) => row.approvalFrom },
              { header: 'Approval to', value: (row) => row.approvalTo },
              { header: 'Raised', value: (row) => row.raisedDate },
              { header: 'Late acknowledged', value: (row) => row.lateSubmissionAcknowledged },
            ],
          }}
        />
      )}
    </div>
  )
}

/**
 * A requirement that never mapped to a catalogue code shows its legacy title, badged (§4.4).
 * Preserving the raw text is the point — §11 forbids silently cleaning it — so the UI has to make
 * "this is unmapped" visible rather than rendering it as if it were a code.
 */
export function RequirementCell({ record }: { record: RegisterRecord }): React.ReactNode {
  if (record.requirementCode !== null) return <span className="mono">{record.requirementCode}</span>
  if (record.reqRaw !== null) {
    return (
      <>
        {record.reqRaw} <span className="chip chip--muted">unmapped</span>
      </>
    )
  }
  return <span className="muted">—</span>
}
