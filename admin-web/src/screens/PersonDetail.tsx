import { Fragment, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  useHoldings,
  usePerson,
  usePersonAssignments,
  usePersonEvidence,
  useRequirements,
  useSetHolding,
} from '../api/queries'
import { api, ApiError, type EvidenceDocument, type Holding, type Requirement, type SetHoldingRequest } from '../api/client'
import { useHasRole, useSession } from '../api/session'
import { PersonCertificates, validityLabel } from '../components/CertificatesOnFile'
import { ErrorPanel } from '../components/ErrorPanel'
import { RequirementLabel } from '../components/RequirementLabel'
import { Spinner } from '../components/Spinner'
import { StateChip } from '../components/StateChip'
import { daysBetween, formatDate, formatDateRange, relativeDays } from '../domain/dates'
import {
  EXPIRY_LEAD_DAYS_DEFAULT,
  categoryLabel,
  HOLDING_STATUS_VALUES,
  holdingStatus,
  holdingTone,
  slotRef,
} from '../domain/enums'
import { downloadCsv, toCsv } from '../domain/csv'

/** The roles the server accepts for a holding write — mirrored here to hide the controls. */
const HOLDING_EDITORS = ['data_steward', 'crew_coordinator', 'system_administrator'] as const

/**
 * ADM-5 person detail — Crew and certification: the holdings by category with inline editing,
 * the scans behind them, and the assignment history. Compliance against a swing is not here; it
 * is the swing's business (Swing and shift compliance), and this page is the person's papers.
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
      <p className="screen__breadcrumb">
        <Link to="/people">Crew and certification</Link> ·{' '}
        <span>{person.data.partnershipAbbrev}</span>
      </p>

      <header className="screen__header">
        <h1 className="screen__title">{person.data.name}</h1>
        {/* A wrapping row of label-over-value rather than tiles: these are names and dates, and a
            date is a phrase, not a quantity — setting them at tile size would shout at prose. */}
        <dl className="fact-grid fact-grid--row" style={{ marginTop: 12 }}>
          <div>
            <dt>Sam #</dt>
            <dd className="mono">{person.data.sam}</dd>
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
            <dd>
              <span
                className={`chip chip--${person.data.status === 'active' ? 'good' : 'muted'}`}
              >
                {person.data.status}
              </span>
            </dd>
          </div>
          <div>
            <dt>Email</dt>
            <dd>{person.data.email ?? <span className="dim">—</span>}</dd>
          </div>
        </dl>
      </header>

      <HoldingsGrid personId={personId} sam={person.data.sam} />

      {/* The scans behind the holdings, on the same page as the holdings — where the portal's
          users look for them. */}
      <PersonCertificates person={person.data} />

      <section className="section">
        <h2 className="section__title">Assignment history</h2>
        <AssignmentHistory personId={personId} />
      </section>
    </div>
  )
}

function HoldingsGrid({ personId, sam }: { personId: number; sam: string }): React.ReactNode {
  const holdings = useHoldings(personId)
  const requirements = useRequirements()
  const documents = usePersonEvidence(personId)
  const canEdit = useHasRole(...HOLDING_EDITORS)
  const today = useSession().today
  const [editing, setEditing] = useState<number | null>(null)
  const [adding, setAdding] = useState(false)

  if (holdings.isPending || requirements.isPending) return <Spinner label="Loading holdings" />
  if (holdings.error !== null) return <ErrorPanel title="Could not load holdings" error={holdings.error} />

  const byId = new Map((requirements.data ?? []).map((requirement) => [requirement.id, requirement]))
  const held = new Set(holdings.data.map((holding) => holding.requirementId))
  // The scans filed against each code, newest first — the certificate behind the holding.
  const scans = new Map<number, EvidenceDocument[]>()
  for (const document of documents.data ?? []) {
    if (document.matchedRequirementId === null) continue
    if (document.verificationStatus !== 'verified' && document.verificationStatus !== 'auto_accepted') continue
    const list = scans.get(document.matchedRequirementId) ?? []
    list.push(document)
    scans.set(document.matchedRequirementId, list)
  }
  for (const list of scans.values()) list.sort((a, b) => b.submittedAt.localeCompare(a.submittedAt))
  const columns = canEdit ? 7 : 6

  const grouped = new Map<string, Holding[]>()
  for (const holding of holdings.data) {
    const category = byId.get(holding.requirementId)?.category ?? '—'
    const existing = grouped.get(category)
    if (existing === undefined) grouped.set(category, [holding])
    else existing.push(holding)
  }

  return (
    <section className="section">
      <div className="section__header">
        <div>
          <h2 className="section__title">Holdings</h2>
          <p className="section__note">
            {holdings.data.length} {holdings.data.length === 1 ? 'holding' : 'holdings'} · the system
            of record
          </p>
        </div>
        <div className="row-actions">
        <button
          type="button"
          className="button"
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
                { header: 'Validity', value: (row) => validityLabel(byId.get(row.requirementId)) },
                { header: 'Certificate', value: (row) => scans.get(row.requirementId)?.[0]?.fileName ?? '' },
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

      {holdings.data.length === 0 && <p className="empty">No holdings recorded.</p>}

      {/*
       * One table, with the categories as full-width heading rows rather than as separate tables.
       * The columns then line up down the whole list, which is what makes a column of expiry dates
       * readable — and the bare category code is all there is to show, because nothing in Appendix A
       * says what QL, PS or MS expand to and inventing an expansion would put a wrong label in front
       * of people who know the right one.
       */}
      {holdings.data.length > 0 && (
        <div className="table-block table-block--plain">
          {/* Fixed column widths: the requirement gets the room its title needs, the dates and the
              certificate link sit right beside it, and the note takes what is left. */}
          <table className="table table--holdings">
            <colgroup>
              <col className="col-requirement" />
              <col className="col-status" />
              <col className="col-date" />
              <col className="col-expiry" />
              <col className="col-validity" />
              <col className="col-certificate" />
              {canEdit && <col className="col-edit" />}
            </colgroup>
            <thead>
              <tr>
                <th scope="col">Requirement</th>
                <th scope="col">Status</th>
                <th scope="col">Issued</th>
                <th scope="col">Expiry</th>
                <th scope="col">Validity period</th>
                <th scope="col">Certificate</th>
                {canEdit && <th scope="col" />}
              </tr>
            </thead>
            <tbody>
              {[...grouped.entries()]
                .sort(([a], [b]) => a.localeCompare(b))
                .map(([category, rows]) => (
                  <Fragment key={category}>
                    <tr className="table__group">
                      <td colSpan={columns}>{categoryLabel(category)}</td>
                    </tr>
                    {rows.map((holding) => {
                      const requirement = byId.get(holding.requirementId)
                      const filed = scans.get(holding.requirementId) ?? []
                      const days =
                        holding.expiry === null ? null : daysBetween(today, holding.expiry)
                      return editing === holding.requirementId ? (
                        <tr key={holding.id}>
                          <td colSpan={columns}>
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
                            <RequirementLabel
                              code={requirement?.code ?? `#${holding.requirementId}`}
                              title={requirement?.title}
                            />
                          </td>
                          <td>
                            <StateChip
                              kind="holding"
                              state={holding.status}
                              tone={holdingTone(holding.status, days)}
                            />
                          </td>
                          <td>{formatDate(holding.issueDate)}</td>
                          <td>
                            {holding.status === 'held_perpetual' ? (
                              <span className="dim">perpetual</span>
                            ) : (
                              <>
                                {formatDate(holding.expiry)}
                                {holding.expiry !== null && (
                                  <span
                                    className={
                                      days !== null && days <= EXPIRY_LEAD_DAYS_DEFAULT
                                        ? undefined
                                        : 'dim'
                                    }
                                    style={
                                      days !== null && days <= EXPIRY_LEAD_DAYS_DEFAULT
                                        ? { color: 'var(--tone-warning-text)' }
                                        : undefined
                                    }
                                  >
                                    {' '}
                                    ({relativeDays(today, holding.expiry)})
                                  </span>
                                )}
                              </>
                            )}
                          </td>
                          <td className="muted">{validityLabel(requirement)}</td>
                          <td className="table__wrap">
                            {filed.length === 0 ? (
                              <span className="dim">no scan on file</span>
                            ) : (
                              filed.map((document, index) => (
                                <span key={document.publicId}>
                                  {index > 0 && ' · '}
                                  <a href={api.evidenceContentUrl(document.publicId)} target="_blank" rel="noreferrer" title={document.fileName ?? undefined}>
                                    {index === 0 ? 'Open' : `Open ${index + 1}`}
                                  </a>
                                </span>
                              ))
                            )}
                          </td>
                          {canEdit && (
                            <td>
                              <button
                                type="button"
                                className="link-action"
                                onClick={() => setEditing(holding.requirementId)}
                              >
                                Edit
                              </button>
                            </td>
                          )}
                        </tr>
                      )
                    })}
                  </Fragment>
                ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
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
        <button type="button" className="button" onClick={onDone}>
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
    <div className="table-block table-block--plain">
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
              <td className="mono">{slotRef(assignment.slotRef)}</td>
              <td>{formatDateRange(assignment.from, assignment.to)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
