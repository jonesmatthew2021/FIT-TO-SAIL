import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { usePartnerships, useCrewChanges, useRegister } from '../api/queries'
import type { RegisterFilters, RegisterRecord } from '../api/client'
import { useHasRole } from '../api/session'
import { DataTable, type Column } from '../components/DataTable'
import { ErrorPanel } from '../components/ErrorPanel'
import { Spinner } from '../components/Spinner'
import { REGISTER_TYPES, registerStatusTone } from '../domain/enums'
import { RegisterDetailPanel } from './RegisterDetail'

/** §3: the Crew Coordinator raises requests; OPS also raises its own. */
export const REGISTER_RAISERS = ['crew_coordinator', 'workflow_manager', 'system_administrator'] as const

/** Q14: the Workflow Manager is the sole decision authority. */
export const REGISTER_DECIDERS = ['workflow_manager', 'system_administrator'] as const

/**
 * ADM-4 — the exemption and query register: the workflow that turns a `gap` into `exempt`.
 *
 * ### List beside detail, on one screen
 *
 * Working the register is working a queue — read a record, act on it, move to the next one — so the
 * detail sits beside the list rather than replacing it. The selected record is still a **route**
 * (`/register/{recordId}`), not component state, because §6 asks every entity view to deep-link and
 * because a notification about a register decision has to be able to point at one.
 *
 * ### The filters live in the URL
 *
 * So a filtered register is a link someone can paste. The default is the open work, because that is
 * the day job — but the whole history is one segment away, and it is the reason this module replaces
 * a 445-row workbook rather than just tracking what is outstanding.
 */
export function Register(): React.ReactNode {
  const [params, setParams] = useSearchParams()
  const { recordId } = useParams()
  const navigate = useNavigate()
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

  /** Opening a record keeps the filters: the list beside it has to stay the list you were working. */
  function open(record: RegisterRecord): void {
    const query = params.toString()
    void navigate(
      `/register/${encodeURIComponent(record.recordId)}${query === '' ? '' : `?${query}`}`,
    )
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
    {
      id: 'type',
      header: 'Type',
      accessorFn: (row) => row.type,
      cell: ({ row }) => (
        <>
          <span className="text-sm">{row.original.type}</span>
          {/* Q17: lateness is a fact about the record, not a footnote in a log. */}
          {row.original.lateSubmissionAcknowledged && (
            <>
              {' '}
              <span
                className="chip chip--critical chip--small"
                title="Lodged after the swing's submission cutoff"
              >
                late
              </span>
            </>
          )}
        </>
      ),
    },
    {
      id: 'person',
      header: 'Person',
      accessorFn: (row) => row.personName ?? '',
      cell: ({ row }) =>
        row.original.personId === null ? (
          <span className="dim">—</span>
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
    {
      id: 'cc',
      header: 'Swing',
      accessorFn: (row) => `${row.partnershipAbbrev} ${row.ccId ?? ''}`,
      cell: ({ row }) =>
        row.original.ccId === null ? (
          <span className="dim">—</span>
        ) : (
          `${row.original.partnershipAbbrev} ${row.original.ccId}`
        ),
    },
    // Raised, Approved for and the effective window are on the detail beside this list rather than
    // in it: nine columns in a 700px pane wrapped every cell onto two lines, and the whole point of
    // the pane is that the record you selected is *right there*. The CSV still carries all of them.
  ]

  const toolbar = (
    <>
      <div className="seg">
        {(['open', 'closed', 'all'] as const).map((value) => (
          <label key={value} className="seg__opt">
            <input
              type="radio"
              name="register-state"
              checked={(filters.state ?? 'open') === value}
              onChange={() => setFilter('state', value)}
            />
            {value === 'open' ? 'Open' : value === 'closed' ? 'Closed' : 'All'}
          </label>
        ))}
      </div>

      <select
        className="input input--auto"
        aria-label="Partnership"
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

      {/* Only once a partnership is chosen: a CC id means a different fortnight in each partnership,
          so a global swing list would invite selecting a pair that does not exist. */}
      {filters.partnership !== undefined && (
        <select
          className="input input--auto"
          aria-label="Swing"
          value={filters.cc ?? ''}
          onChange={(event) => setFilter('cc', event.target.value)}
        >
          <option value="">All swings</option>
          {(crewChanges.data ?? []).map((crewChange) => (
            <option key={crewChange.id} value={crewChange.ccId}>
              {crewChange.ccId}
            </option>
          ))}
        </select>
      )}

      <select
        className="input input--auto"
        aria-label="Type"
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
    </>
  )

  return (
    <div className="screen screen--split">
      <header className="screen__header screen__header--action">
        <div>
          <h1 className="screen__title">Register</h1>
          <p className="screen__subtitle">
            The exemption and query register — the workflow that turns a gap into{' '}
            <span className="chip chip--neutral">Exempt</span>.
          </p>
        </div>
        {canRaise && (
          <Link className="button button--primary" to="/register/new">
            Raise a request
          </Link>
        )}
      </header>

      <div className="split">
        <div>
          {records.isPending && <Spinner label="Loading the register" />}
          {records.error !== null && (
            <ErrorPanel title="Could not load the register" error={records.error} />
          )}
          {records.data !== undefined && (
            <DataTable
              rows={records.data}
              columns={columns}
              toolbar={toolbar}
              minWidth={560}
              filterPlaceholder="Search record, person or requirement"
              empty="No records match these filters."
              onRowClick={open}
              rowClassName={(row) =>
                row.recordId === recordId ? 'table__row--selected' : undefined
              }
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

        {recordId === undefined ? (
          <p className="empty">
            Choose a record to read its conditions, its party notes and what has happened to it.
          </p>
        ) : (
          <RegisterDetailPanel key={recordId} recordId={recordId} />
        )}
      </div>
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
        <span className="muted">{record.reqRaw}</span>{' '}
        <span className="chip chip--muted chip--small">unmapped</span>
      </>
    )
  }
  return <span className="dim">—</span>
}
