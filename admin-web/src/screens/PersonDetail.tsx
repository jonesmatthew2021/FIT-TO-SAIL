import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  useHoldings,
  usePerson,
  usePersonAssignments,
  useRequirements,
  useSetHolding,
} from '../api/queries'
import { api, ApiError, type Holding, type Requirement, type SetHoldingRequest } from '../api/client'
import { useHasRole, useSession } from '../api/session'
import { ErrorPanel } from '../components/ErrorPanel'
import { Spinner } from '../components/Spinner'
import { StateChip } from '../components/StateChip'
import { SwingSelector } from '../components/SwingSelector'
import { formatDate, formatDateRange, relativeDays } from '../domain/dates'
import { categoryLabel, cellStateRank, HOLDING_STATUS_VALUES, holdingStatus } from '../domain/enums'
import { downloadCsv, toCsv } from '../domain/csv'

/** The roles the server accepts for a holding write — mirrored here to hide the controls. */
const HOLDING_EDITORS = ['data_steward', 'crew_coordinator', 'system_administrator'] as const

/**
 * ADM-5 person detail: holdings by category with inline editing, assignment history, and the
 * §5.2 evaluation against a chosen swing.
 */
export function PersonDetail(): React.ReactNode {
  const { personId: raw } = useParams()
  const personId = Number(raw)
  const person = usePerson(personId)

  if (Number.isNaN(personId)) return <ErrorPanel title="Bad person id" error={new Error(raw ?? '')} />
  if (person.isPending) return <Spinner label="Loading person" />
  if (person.error !== null) return <ErrorPanel title="Could not load this person" error={person.error} />

  return (
    <div className="screen">
      <header className="screen__header">
        <h1 className="screen__title">{person.data.name}</h1>
        <dl className="facts">
          <div>
            <dt>Sam #</dt>
            <dd>{person.data.sam}</dd>
          </div>
          <div>
            <dt>Position</dt>
            <dd>
              {person.data.positionName}
              {person.data.tier !== null && ` · tier ${person.data.tier}`}
            </dd>
          </div>
          <div>
            <dt>Partnership</dt>
            <dd>{person.data.partnershipAbbrev}</dd>
          </div>
          <div>
            <dt>Status</dt>
            <dd>{person.data.status}</dd>
          </div>
          <div>
            <dt>Email</dt>
            <dd>{person.data.email ?? '—'}</dd>
          </div>
        </dl>
      </header>

      <section className="section">
        <h2 className="section__title">Compliance against a swing</h2>
        <PersonEvaluationPanel personId={personId} />
      </section>

      <section className="section">
        <h2 className="section__title">Holdings</h2>
        <HoldingsGrid personId={personId} sam={person.data.sam} />
      </section>

      <section className="section">
        <h2 className="section__title">Assignment history</h2>
        <AssignmentHistory personId={personId} />
      </section>
    </div>
  )
}

function PersonEvaluationPanel({ personId }: { personId: number }): React.ReactNode {
  const [params, setParams] = useSearchParams()
  const partnership = params.get('partnership')
  const cc = params.get('cc')
  const requirements = useRequirements()

  const evaluation = useQuery({
    queryKey: ['person-evaluation', personId, partnership ?? '', cc ?? ''],
    queryFn: () => api.personEvaluation(personId, partnership as string, cc as string),
    enabled: partnership !== null && cc !== null,
  })

  const byId = new Map((requirements.data ?? []).map((requirement) => [requirement.id, requirement]))

  return (
    <>
      <SwingSelector
        partnership={partnership}
        cc={cc}
        onChange={(nextPartnership, nextCc) => {
          const next = new URLSearchParams()
          if (nextPartnership !== null) next.set('partnership', nextPartnership)
          if (nextCc !== null) next.set('cc', nextCc)
          setParams(next)
        }}
      />

      {(partnership === null || cc === null) && (
        <p className="note">
          A compliance roll-up is defined against a swing (§5.2), so pick one to evaluate this
          person.
        </p>
      )}
      {evaluation.isPending && partnership !== null && cc !== null && <Spinner label="Evaluating" />}
      {evaluation.error !== null && (
        <ErrorPanel title="Could not evaluate this person" error={evaluation.error} />
      )}

      {evaluation.data !== undefined && (
        <>
          <p className="rollup">
            Roll-up: <StateChip state={evaluation.data.rollUp} />
          </p>
          <table className="table">
            <thead>
              <tr>
                <th scope="col">State</th>
                <th scope="col">Requirement</th>
                <th scope="col">Level</th>
                <th scope="col">Expiry</th>
                <th scope="col">Register</th>
                <th scope="col">Notes</th>
              </tr>
            </thead>
            <tbody>
              {[...evaluation.data.cells]
                .sort(
                  (a, b) =>
                    cellStateRank(a.state) - cellStateRank(b.state) ||
                    (byId.get(a.requirementId)?.code ?? '').localeCompare(
                      byId.get(b.requirementId)?.code ?? '',
                    ),
                )
                .map((cell) => (
                  <tr key={cell.requirementId}>
                    <td>
                      <StateChip state={cell.state} />
                    </td>
                    <td>
                      {byId.get(cell.requirementId)?.code ?? `#${cell.requirementId}`}{' '}
                      <span className="muted">{byId.get(cell.requirementId)?.title}</span>
                    </td>
                    <td>{cell.level}</td>
                    <td>{formatDate(cell.expiry)}</td>
                    <td>{cell.registerRecordId ?? <span className="muted">—</span>}</td>
                    <td>{cell.notes.join(' · ')}</td>
                  </tr>
                ))}
            </tbody>
          </table>
        </>
      )}
    </>
  )
}

function HoldingsGrid({ personId, sam }: { personId: number; sam: string }): React.ReactNode {
  const holdings = useHoldings(personId)
  const requirements = useRequirements()
  const canEdit = useHasRole(...HOLDING_EDITORS)
  const today = useSession().today
  const [editing, setEditing] = useState<number | null>(null)
  const [adding, setAdding] = useState(false)

  if (holdings.isPending || requirements.isPending) return <Spinner label="Loading holdings" />
  if (holdings.error !== null) return <ErrorPanel title="Could not load holdings" error={holdings.error} />

  const byId = new Map((requirements.data ?? []).map((requirement) => [requirement.id, requirement]))
  const held = new Set(holdings.data.map((holding) => holding.requirementId))

  const grouped = new Map<string, Holding[]>()
  for (const holding of holdings.data) {
    const category = byId.get(holding.requirementId)?.category ?? '—'
    const existing = grouped.get(category)
    if (existing === undefined) grouped.set(category, [holding])
    else existing.push(holding)
  }

  return (
    <>
      <div className="table-block__toolbar">
        <span className="table-block__count">{holdings.data.length} holdings</span>
        <button
          type="button"
          className="button button--quiet"
          onClick={() =>
            downloadCsv(
              `holdings-${sam}.csv`,
              toCsv(holdings.data, [
                { header: 'Requirement', value: (row) => byId.get(row.requirementId)?.code ?? row.requirementId },
                { header: 'Title', value: (row) => byId.get(row.requirementId)?.title },
                { header: 'Category', value: (row) => byId.get(row.requirementId)?.category },
                { header: 'Status', value: (row) => row.status },
                { header: 'Expiry', value: (row) => row.expiry },
                { header: 'Issued', value: (row) => row.issueDate },
                { header: 'Note', value: (row) => row.note },
              ]),
            )
          }
        >
          Export CSV
        </button>
        {canEdit && (
          <button type="button" className="button button--primary" onClick={() => setAdding(true)}>
            Add holding
          </button>
        )}
      </div>

      {adding && (
        <HoldingEditor
          personId={personId}
          requirements={(requirements.data ?? []).filter(
            (requirement) => !held.has(requirement.id) && requirement.status === 'active',
          )}
          onDone={() => setAdding(false)}
        />
      )}

      {[...grouped.entries()]
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([category, rows]) => (
          <div key={category} className="holding-group">
            <h3 className="holding-group__title">{categoryLabel(category)}</h3>
            <table className="table">
              <thead>
                <tr>
                  <th scope="col">Requirement</th>
                  <th scope="col">Status</th>
                  <th scope="col">Expiry</th>
                  <th scope="col">Issued</th>
                  <th scope="col">Note</th>
                  {canEdit && <th scope="col" />}
                </tr>
              </thead>
              <tbody>
                {rows.map((holding) => {
                  const requirement = byId.get(holding.requirementId)
                  return editing === holding.requirementId ? (
                    <tr key={holding.id}>
                      <td colSpan={canEdit ? 6 : 5}>
                        <HoldingEditor
                          personId={personId}
                          existing={holding}
                          requirements={requirement === undefined ? [] : [requirement]}
                          onDone={() => setEditing(null)}
                        />
                      </td>
                    </tr>
                  ) : (
                    <tr key={holding.id}>
                      <td>
                        <span className="mono">{requirement?.code ?? `#${holding.requirementId}`}</span>{' '}
                        <span className="muted">{requirement?.title}</span>
                      </td>
                      <td>
                        <StateChip kind="holding" state={holding.status} />
                      </td>
                      <td>
                        {formatDate(holding.expiry)}
                        {holding.expiry !== null && (
                          <span className="muted"> ({relativeDays(today, holding.expiry)})</span>
                        )}
                      </td>
                      <td>{formatDate(holding.issueDate)}</td>
                      <td>{holding.note ?? <span className="muted">—</span>}</td>
                      {canEdit && (
                        <td>
                          <button
                            type="button"
                            className="button button--quiet"
                            onClick={() => setEditing(holding.requirementId)}
                          >
                            Edit
                          </button>
                        </td>
                      )}
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        ))}

      {holdings.data.length === 0 && <p className="empty">No holdings recorded.</p>}
    </>
  )
}

/**
 * The holding edit form.
 *
 * The client-side checks below are **advisory** (AUTH-1): they exist so a mistake is caught
 * before a round trip, and the server re-validates identically in `HoldingService.validate`. If
 * the two ever disagree the server wins, and its message is what the user sees.
 */
function HoldingEditor({
  personId,
  existing,
  requirements,
  onDone,
}: {
  personId: number
  existing?: Holding
  requirements: readonly Requirement[]
  onDone: () => void
}): React.ReactNode {
  const mutation = useSetHolding(personId)
  const [requirementId, setRequirementId] = useState(
    existing?.requirementId ?? requirements[0]?.id ?? 0,
  )
  const [status, setStatus] = useState(existing?.status ?? 'unknown')
  const [expiry, setExpiry] = useState(existing?.expiry ?? '')
  const [issueDate, setIssueDate] = useState(existing?.issueDate ?? '')
  const [note, setNote] = useState(existing?.note ?? '')

  const advisory = validateHolding(status, expiry, issueDate)

  function submit(event: React.FormEvent): void {
    event.preventDefault()
    if (advisory !== null) return
    const body: SetHoldingRequest = {
      status,
      expiry: status === 'held_expiry' && expiry !== '' ? expiry : null,
      issueDate: issueDate === '' ? null : issueDate,
      note: note === '' ? null : note,
    }
    mutation.mutate({ requirementId, body }, { onSuccess: onDone })
  }

  return (
    <form className="editor" onSubmit={submit}>
      <label className="field field--inline">
        <span className="field__label">Requirement</span>
        <select
          className="input"
          value={requirementId}
          disabled={existing !== undefined}
          onChange={(event) => setRequirementId(Number(event.target.value))}
        >
          {requirements.map((requirement) => (
            <option key={requirement.id} value={requirement.id}>
              {requirement.code} — {requirement.title}
            </option>
          ))}
        </select>
      </label>

      <label className="field field--inline">
        <span className="field__label">Status</span>
        <select className="input" value={status} onChange={(event) => setStatus(event.target.value)}>
          {HOLDING_STATUS_VALUES.map((value) => (
            <option key={value} value={value}>
              {holdingStatus(value).label}
            </option>
          ))}
        </select>
      </label>

      <label className="field field--inline">
        <span className="field__label">Expiry</span>
        <input
          className="input"
          type="date"
          value={expiry ?? ''}
          disabled={status !== 'held_expiry'}
          onChange={(event) => setExpiry(event.target.value)}
        />
      </label>

      <label className="field field--inline">
        <span className="field__label">Issued</span>
        <input
          className="input"
          type="date"
          value={issueDate ?? ''}
          onChange={(event) => setIssueDate(event.target.value)}
        />
      </label>

      <label className="field field--inline field--grow">
        <span className="field__label">Note</span>
        <input className="input" value={note ?? ''} onChange={(event) => setNote(event.target.value)} />
      </label>

      {advisory !== null && <p className="editor__error">{advisory}</p>}
      {mutation.error !== null && (
        <p className="editor__error">
          {mutation.error instanceof ApiError ? mutation.error.message : String(mutation.error)}
        </p>
      )}

      <div className="editor__actions">
        <button
          type="submit"
          className="button button--primary"
          disabled={advisory !== null || mutation.isPending || requirementId === 0}
        >
          {mutation.isPending ? 'Saving…' : 'Save'}
        </button>
        <button type="button" className="button button--quiet" onClick={onDone}>
          Cancel
        </button>
      </div>
    </form>
  )
}

/** Mirrors `HoldingService.validate`. Advisory only — the server is authoritative. */
export function validateHolding(
  status: string,
  expiry: string | null,
  issueDate: string | null,
): string | null {
  const hasExpiry = expiry !== null && expiry !== ''
  if (status === 'held_expiry' && !hasExpiry) {
    return 'An expiring holding must carry an expiry date.'
  }
  if (status !== 'held_expiry' && hasExpiry) {
    return "Only a 'held (expires)' holding may carry an expiry date."
  }
  if (issueDate !== null && issueDate !== '' && hasExpiry && issueDate > (expiry as string)) {
    return 'Issue date is after the expiry date.'
  }
  return null
}

function AssignmentHistory({ personId }: { personId: number }): React.ReactNode {
  const assignments = usePersonAssignments(personId)

  if (assignments.isPending) return <Spinner label="Loading assignments" />
  if (assignments.error !== null) {
    return <ErrorPanel title="Could not load assignments" error={assignments.error} />
  }
  if (assignments.data.length === 0) return <p className="empty">No assignments recorded.</p>

  return (
    <table className="table">
      <thead>
        <tr>
          <th scope="col">Crew change</th>
          <th scope="col">Partnership</th>
          <th scope="col">Slot</th>
          <th scope="col">Dates</th>
        </tr>
      </thead>
      <tbody>
        {assignments.data.map((assignment) => (
          <tr key={assignment.id}>
            <td>
              <Link
                to={`/planner?partnership=${assignment.partnershipAbbrev}&cc=${assignment.ccId}`}
              >
                {assignment.ccId}
              </Link>
            </td>
            <td>{assignment.partnershipAbbrev}</td>
            <td>{assignment.slotRef}</td>
            <td>{formatDateRange(assignment.from, assignment.to)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
