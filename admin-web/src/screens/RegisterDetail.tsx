import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  useAddRegisterCondition,
  useAddRegisterNote,
  useCloseRegisterRecord,
  useRegisterRecord,
  useTransitionRegisterRecord,
} from '../api/queries'
import { ApiError, type RegisterRecordDetail } from '../api/client'
import { useHasRole } from '../api/session'
import { ErrorPanel } from '../components/ErrorPanel'
import { Spinner } from '../components/Spinner'
import { formatDate, formatDateRange } from '../domain/dates'
import {
  CONDITION_TYPES,
  REGISTER_OPEN_STATUSES,
  REGISTER_OUTCOMES,
  REGISTER_PARTIES,
  registerStatusTone,
} from '../domain/enums'
import { REGISTER_DECIDERS, REGISTER_RAISERS, RequirementCell } from './Register'

/**
 * ADM-4 detail — the facts, the structured conditions, the party notes and the trail.
 *
 * The trail shown here is the **human-readable** one the POC's users read on the record itself.
 * It is not the audit chain: `audit_event` is the machine-readable, hash-linked, export-ready
 * record (§17.4, ADR 0007) and is deliberately not surfaced on a business screen — it answers
 * "can an external party verify this", which is a different question from "what happened here".
 */
export function RegisterDetail(): React.ReactNode {
  const { recordId = '' } = useParams()
  const detail = useRegisterRecord(recordId)
  const canDecide = useHasRole(...REGISTER_DECIDERS)
  const canNote = useHasRole(...REGISTER_RAISERS)

  if (detail.isPending) return <Spinner label="Loading the record" />
  if (detail.error !== null) return <ErrorPanel title="Could not load this record" error={detail.error} />

  const { record } = detail.data

  return (
    <div className="screen">
      <header className="screen__header">
        <h1 className="screen__title">
          <span className="mono">{record.recordId}</span>
        </h1>
        <p className="screen__subtitle">
          {record.type} · <span className={`chip chip--${registerStatusTone(record.status)}`}>{record.status}</span>
        </p>
        <p>
          <Link to="/register">← Back to the register</Link>
        </p>
      </header>

      <Facts detail={detail.data} />

      {canDecide && record.open && <DecisionPanel recordId={record.recordId} />}

      <section className="section">
        <h2 className="section__title">Conditions</h2>
        <Conditions detail={detail.data} canDecide={canDecide} />
      </section>

      <section className="section">
        <h2 className="section__title">Notes</h2>
        <Notes detail={detail.data} canNote={canNote} />
      </section>

      <section className="section">
        <h2 className="section__title">History</h2>
        <Trail detail={detail.data} />
      </section>
    </div>
  )
}

function Facts({ detail }: { detail: RegisterRecordDetail }): React.ReactNode {
  const { record } = detail
  return (
    <dl className="facts">
      <div>
        <dt>Person</dt>
        <dd>
          {record.personId === null ? (
            '—'
          ) : (
            <Link to={`/people/${record.personId}`}>
              {record.personName} <span className="muted">{record.sam}</span>
            </Link>
          )}
        </dd>
      </div>
      <div>
        <dt>Position</dt>
        <dd>{record.positionName ?? '—'}</dd>
      </div>
      <div>
        <dt>Requirement</dt>
        <dd>
          <RequirementCell record={record} />
        </dd>
      </div>
      <div>
        <dt>Swing</dt>
        <dd>
          {record.ccId === null ? (
            '—'
          ) : (
            <Link to={`/planner?partnership=${record.partnershipAbbrev}&cc=${record.ccId}`}>
              {record.partnershipAbbrev} {record.ccId}
            </Link>
          )}
        </dd>
      </div>
      <div>
        <dt>Effective</dt>
        <dd>
          {record.effectiveFrom === null || record.effectiveTo === null
            ? '—'
            : formatDateRange(record.effectiveFrom, record.effectiveTo)}
        </dd>
      </div>
      <div>
        <dt>Raised</dt>
        <dd>
          {formatDate(record.raisedDate)}
          {record.lateSubmissionAcknowledged && (
            <span className="chip chip--caution" title="Lodged after the swing's submission cutoff (Q17)">
              late, acknowledged
            </span>
          )}
        </dd>
      </div>
      <div>
        <dt>Outcome</dt>
        <dd>{record.outcome ?? '—'}</dd>
      </div>
      <div>
        <dt>Approved for</dt>
        <dd>
          {record.approvalFrom === null || record.approvalTo === null
            ? '—'
            : formatDateRange(record.approvalFrom, record.approvalTo)}
        </dd>
      </div>
    </dl>
  )
}

/**
 * The Workflow Manager's controls (Q14): hand the record on, or close it.
 *
 * Closing is not a status change with an extra field — it is its own act, because an approval
 * carries a window that §5.1's overlay reads. The server refuses an `Approved` with no dates, and
 * this form asks for them rather than letting the refusal be the first anyone hears of it.
 */
function DecisionPanel({ recordId }: { recordId: string }): React.ReactNode {
  const transition = useTransitionRegisterRecord()
  const close = useCloseRegisterRecord()
  const [status, setStatus] = useState(REGISTER_OPEN_STATUSES[0] as string)
  const [outcome, setOutcome] = useState(REGISTER_OUTCOMES[0] as string)
  const [approvalFrom, setApprovalFrom] = useState('')
  const [approvalTo, setApprovalTo] = useState('')
  const [note, setNote] = useState('')

  const approving = outcome === 'Approved'

  return (
    <section className="section">
      <h2 className="section__title">Decision</h2>

      <form
        className="editor"
        onSubmit={(event) => {
          event.preventDefault()
          transition.mutate({ recordId, status })
        }}
      >
        <label className="field field--inline">
          <span className="field__label">Move to</span>
          <select className="input" value={status} onChange={(event) => setStatus(event.target.value)}>
            {REGISTER_OPEN_STATUSES.map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </select>
        </label>
        <div className="editor__actions">
          <button type="submit" className="button" disabled={transition.isPending}>
            Transition
          </button>
        </div>
        {transition.error !== null && <p className="editor__error">{errorText(transition.error)}</p>}
      </form>

      <form
        className="editor"
        onSubmit={(event) => {
          event.preventDefault()
          close.mutate({
            recordId,
            body: {
              outcome,
              approvalFrom: approving && approvalFrom !== '' ? approvalFrom : null,
              approvalTo: approving && approvalTo !== '' ? approvalTo : null,
              note: note === '' ? null : note,
            },
          })
        }}
      >
        <label className="field field--inline">
          <span className="field__label">Close as</span>
          <select className="input" value={outcome} onChange={(event) => setOutcome(event.target.value)}>
            {REGISTER_OUTCOMES.map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </select>
        </label>

        <label className="field field--inline">
          <span className="field__label">Approved from</span>
          <input
            className="input"
            type="date"
            value={approvalFrom}
            disabled={!approving}
            onChange={(event) => setApprovalFrom(event.target.value)}
          />
        </label>

        <label className="field field--inline">
          <span className="field__label">Approved to</span>
          <input
            className="input"
            type="date"
            value={approvalTo}
            disabled={!approving}
            onChange={(event) => setApprovalTo(event.target.value)}
          />
        </label>

        <label className="field field--inline field--grow">
          <span className="field__label">Closing note (OPS)</span>
          <input className="input" value={note} onChange={(event) => setNote(event.target.value)} />
        </label>

        {approving && (
          <p className="note">
            The approval window must sit inside the swing. §5.1 step 4 reads exactly these two
            dates to decide whether the cell shows as exempt.
          </p>
        )}
        {close.error !== null && <p className="editor__error">{errorText(close.error)}</p>}

        <div className="editor__actions">
          <button
            type="submit"
            className="button button--primary"
            disabled={close.isPending || (approving && (approvalFrom === '' || approvalTo === ''))}
          >
            {close.isPending ? 'Closing…' : 'Close record'}
          </button>
        </div>
      </form>
    </section>
  )
}

function Conditions({
  detail,
  canDecide,
}: {
  detail: RegisterRecordDetail
  canDecide: boolean
}): React.ReactNode {
  const add = useAddRegisterCondition()
  const [type, setType] = useState(CONDITION_TYPES[0] as string)
  const [body, setBody] = useState('')

  return (
    <>
      {detail.conditions.length === 0 && <p className="empty">No conditions attached.</p>}
      {detail.conditions.length > 0 && (
        <table className="table">
          <thead>
            <tr>
              <th scope="col">Type</th>
              <th scope="col">Condition</th>
            </tr>
          </thead>
          <tbody>
            {detail.conditions.map((condition) => (
              <tr key={condition.id}>
                <td>
                  <span className="chip chip--neutral">{condition.type}</span>
                </td>
                <td>{condition.body}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {canDecide && (
        <form
          className="editor"
          onSubmit={(event) => {
            event.preventDefault()
            add.mutate(
              { recordId: detail.record.recordId, body: { type, body } },
              { onSuccess: () => setBody('') },
            )
          }}
        >
          <label className="field field--inline">
            <span className="field__label">Type</span>
            <select className="input" value={type} onChange={(event) => setType(event.target.value)}>
              {CONDITION_TYPES.map((value) => (
                <option key={value} value={value}>
                  {value}
                </option>
              ))}
            </select>
          </label>
          <label className="field field--inline field--grow">
            <span className="field__label">Condition</span>
            <input className="input" value={body} onChange={(event) => setBody(event.target.value)} />
          </label>
          {add.error !== null && <p className="editor__error">{errorText(add.error)}</p>}
          <div className="editor__actions">
            <button
              type="submit"
              className="button"
              disabled={add.isPending || body.trim() === ''}
            >
              Add condition
            </button>
          </div>
        </form>
      )}
    </>
  )
}

/** Party-attributed and append-only (§4.4): a note is a record of what someone said. */
function Notes({
  detail,
  canNote,
}: {
  detail: RegisterRecordDetail
  canNote: boolean
}): React.ReactNode {
  const add = useAddRegisterNote()
  const [party, setParty] = useState(REGISTER_PARTIES[0] as string)
  const [body, setBody] = useState('')

  return (
    <>
      {detail.notes.length === 0 && <p className="empty">No notes yet.</p>}
      {detail.notes.length > 0 && (
        <ul className="crew-list">
          {detail.notes.map((note) => (
            <li key={note.id} className="crew-list__item">
              <span className="chip chip--neutral">{note.party}</span>
              <span>{note.body}</span>
              <span className="muted">
                {formatDate(note.createdAt.slice(0, 10))} · {note.createdBy}
              </span>
            </li>
          ))}
        </ul>
      )}

      {canNote && (
        <form
          className="editor"
          onSubmit={(event) => {
            event.preventDefault()
            add.mutate(
              { recordId: detail.record.recordId, party, body },
              { onSuccess: () => setBody('') },
            )
          }}
        >
          <label className="field field--inline">
            <span className="field__label">Party</span>
            <select className="input" value={party} onChange={(event) => setParty(event.target.value)}>
              {REGISTER_PARTIES.map((value) => (
                <option key={value} value={value}>
                  {value}
                </option>
              ))}
            </select>
          </label>
          <label className="field field--inline field--grow">
            <span className="field__label">Note</span>
            <input className="input" value={body} onChange={(event) => setBody(event.target.value)} />
          </label>
          {add.error !== null && <p className="editor__error">{errorText(add.error)}</p>}
          <div className="editor__actions">
            <button type="submit" className="button" disabled={add.isPending || body.trim() === ''}>
              Add note
            </button>
          </div>
        </form>
      )}
    </>
  )
}

function Trail({ detail }: { detail: RegisterRecordDetail }): React.ReactNode {
  if (detail.trail.length === 0) return <p className="empty">Nothing recorded.</p>
  return (
    <table className="table">
      <thead>
        <tr>
          <th scope="col">#</th>
          <th scope="col">What happened</th>
          <th scope="col">Who</th>
          <th scope="col">When</th>
        </tr>
      </thead>
      <tbody>
        {detail.trail.map((entry) => (
          <tr key={entry.ordinal}>
            <td>{entry.ordinal}</td>
            <td>{entry.body}</td>
            <td>{entry.actor}</td>
            <td>{formatDate(entry.occurredAt.slice(0, 10))}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function errorText(error: unknown): string {
  return error instanceof ApiError ? error.message : String(error)
}
