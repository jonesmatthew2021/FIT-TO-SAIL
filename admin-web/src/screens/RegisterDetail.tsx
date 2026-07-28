import { useState } from 'react'
import { Link } from 'react-router-dom'
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
import { formatDate, formatDateRange, formatShortRange } from '../domain/dates'
import {
  CONDITION_TYPES,
  REGISTER_OPEN_STATUSES,
  REGISTER_OUTCOMES,
  REGISTER_PARTIES,
  registerStatusTone,
} from '../domain/enums'
import { REGISTER_DECIDERS, REGISTER_RAISERS, RequirementCell } from './Register'

/**
 * ADM-4's detail pane — the facts, the structured conditions, the party notes and the trail.
 *
 * It sits beside the list rather than replacing it (see `Register`), because working the register is
 * working a queue: read a record, act on it, move to the next one. A detail that took the whole
 * screen made every decision a round trip through the browser's back button.
 *
 * The trail shown here is the **human-readable** one the POC's users read on the record itself. It is
 * not the audit chain: `audit_event` is the machine-readable, hash-linked, export-ready record (§17.4,
 * ADR 0007) and is deliberately not surfaced on a business screen — it answers "can an external party
 * verify this", which is a different question from "what happened here".
 */

/** Which of the panel's forms is open. Only one at a time: they all act on the same record. */
type OpenForm = 'none' | 'note' | 'condition' | 'close' | 'transition'

export function RegisterDetailPanel({ recordId }: { recordId: string }): React.ReactNode {
  const detail = useRegisterRecord(recordId)
  const canDecide = useHasRole(...REGISTER_DECIDERS)
  const canNote = useHasRole(...REGISTER_RAISERS)
  const [form, setForm] = useState<OpenForm>('none')

  if (detail.isPending) {
    return (
      <div className="detail">
        <Spinner label="Loading the record" />
      </div>
    )
  }
  if (detail.error !== null) {
    return <ErrorPanel title="Could not load this record" error={detail.error} />
  }

  const { record } = detail.data

  return (
    <div className="detail">
      <div>
        <div className="detail__head">
          {/* The record id *is* the title, and it is a business key — so it is monospaced. */}
          <h2 className="detail__title">{record.recordId}</h2>
          <span className={`chip chip--${registerStatusTone(record.status)}`}>{record.status}</span>
          {record.lateSubmissionAcknowledged && (
            <span className="chip chip--critical chip--small" title="Lodged after the swing's cutoff">
              late
            </span>
          )}
        </div>
        <p className="detail__subtitle">{record.type}</p>
      </div>

      <Facts detail={detail.data} />

      {detail.data.conditions.length > 0 && (
        <div>
          <h3 className="overline">Conditions</h3>
          <div className="list-plain list-plain--tight">
            {detail.data.conditions.map((condition) => (
              <div key={condition.id} className="condition">
                <span className="chip chip--muted chip--small">{condition.type}</span>
                <span>{condition.body}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {detail.data.notes.length > 0 && (
        <div>
          <h3 className="overline">Notes</h3>
          <div className="list-plain">
            {detail.data.notes.map((note) => (
              <div key={note.id} className="tint-card">
                {/* PW takes the accent; MRL and OPS do not. The party who raised a request is the
                    one whose words the reader is looking for first. */}
                <div className="tint-card__head">
                  <span
                    className={
                      note.party === 'PW' ? 'chip chip--accent chip--small' : 'chip chip--muted chip--small'
                    }
                  >
                    {note.party}
                  </span>
                  <span>
                    {note.createdBy} · {formatDate(note.createdAt.slice(0, 10))}
                  </span>
                </div>
                {note.body}
              </div>
            ))}
          </div>
        </div>
      )}

      {detail.data.trail.length > 0 && (
        <div>
          <h3 className="overline">History</h3>
          <table className="table table--borderless">
            <tbody>
              {[...detail.data.trail].reverse().map((entry) => (
                <tr key={entry.ordinal}>
                  <td className="dim" style={{ width: 24 }}>
                    {entry.ordinal}
                  </td>
                  <td>{entry.body}</td>
                  <td className="dim">{formatDate(entry.occurredAt.slice(0, 10))}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {(canNote || canDecide) && form === 'none' && (
        <div className="detail__actions">
          {canNote && (
            <button type="button" className="button" onClick={() => setForm('note')}>
              Add note
            </button>
          )}
          {canDecide && (
            <button type="button" className="button" onClick={() => setForm('condition')}>
              Add condition
            </button>
          )}
          {canDecide && record.open && (
            <button type="button" className="button" onClick={() => setForm('transition')}>
              Move to…
            </button>
          )}
          {canDecide && record.open && (
            <button type="button" className="button button--primary" onClick={() => setForm('close')}>
              Close record
            </button>
          )}
        </div>
      )}

      {form === 'note' && <NoteForm detail={detail.data} onDone={() => setForm('none')} />}
      {form === 'condition' && <ConditionForm detail={detail.data} onDone={() => setForm('none')} />}
      {form === 'transition' && (
        <TransitionForm recordId={record.recordId} onDone={() => setForm('none')} />
      )}
      {form === 'close' && <CloseForm recordId={record.recordId} onDone={() => setForm('none')} />}
    </div>
  )
}

function Facts({ detail }: { detail: RegisterRecordDetail }): React.ReactNode {
  const { record } = detail
  return (
    <dl className="fact-grid">
      <div>
        <dt>Person</dt>
        <dd>
          {record.personId === null ? (
            <span className="dim">—</span>
          ) : (
            <Link to={`/people/${record.personId}`}>
              {record.personName} <span className="mono muted">{record.sam}</span>
            </Link>
          )}
        </dd>
      </div>
      <div>
        <dt>Position</dt>
        <dd>{record.positionName ?? <span className="dim">—</span>}</dd>
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
            <span className="dim">—</span>
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
          {record.effectiveFrom === null || record.effectiveTo === null ? (
            <span className="dim">—</span>
          ) : (
            formatShortRange(record.effectiveFrom, record.effectiveTo)
          )}
        </dd>
      </div>
      <div>
        <dt>Raised</dt>
        <dd>
          {formatDate(record.raisedDate)}
          {/* Q17: lateness is a fact about the record, not a footnote in a log. */}
          {record.lateSubmissionAcknowledged && (
            <span className="text-attention"> (late, acknowledged)</span>
          )}
        </dd>
      </div>
      <div>
        <dt>Outcome</dt>
        <dd>{record.outcome ?? <span className="dim">—</span>}</dd>
      </div>
      <div>
        <dt>Approved for</dt>
        <dd>
          {record.approvalFrom === null || record.approvalTo === null ? (
            <span className="dim">—</span>
          ) : (
            formatDateRange(record.approvalFrom, record.approvalTo)
          )}
        </dd>
      </div>
    </dl>
  )
}

/** Party-attributed and append-only (§4.4): a note is a record of what someone said. */
function NoteForm({
  detail,
  onDone,
}: {
  detail: RegisterRecordDetail
  onDone: () => void
}): React.ReactNode {
  const add = useAddRegisterNote()
  const [party, setParty] = useState(REGISTER_PARTIES[0] as string)
  const [body, setBody] = useState('')

  return (
    <form
      className="editor"
      onSubmit={(event) => {
        event.preventDefault()
        add.mutate({ recordId: detail.record.recordId, party, body }, { onSuccess: onDone })
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
        <input
          className="input"
          value={body}
          autoFocus
          onChange={(event) => setBody(event.target.value)}
        />
      </label>
      {add.error !== null && <p className="editor__error">{errorText(add.error)}</p>}
      <div className="editor__actions">
        <button
          type="submit"
          className="button button--primary"
          disabled={add.isPending || body.trim() === ''}
        >
          Add note
        </button>
        <button type="button" className="button" onClick={onDone}>
          Cancel
        </button>
      </div>
    </form>
  )
}

function ConditionForm({
  detail,
  onDone,
}: {
  detail: RegisterRecordDetail
  onDone: () => void
}): React.ReactNode {
  const add = useAddRegisterCondition()
  const [type, setType] = useState(CONDITION_TYPES[0] as string)
  const [body, setBody] = useState('')

  return (
    <form
      className="editor"
      onSubmit={(event) => {
        event.preventDefault()
        add.mutate({ recordId: detail.record.recordId, body: { type, body } }, { onSuccess: onDone })
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
        <input
          className="input"
          value={body}
          autoFocus
          onChange={(event) => setBody(event.target.value)}
        />
      </label>
      {add.error !== null && <p className="editor__error">{errorText(add.error)}</p>}
      <div className="editor__actions">
        <button
          type="submit"
          className="button button--primary"
          disabled={add.isPending || body.trim() === ''}
        >
          Add condition
        </button>
        <button type="button" className="button" onClick={onDone}>
          Cancel
        </button>
      </div>
    </form>
  )
}

/** Handing the record on to the next party (Q14: the Workflow Manager decides). */
function TransitionForm({
  recordId,
  onDone,
}: {
  recordId: string
  onDone: () => void
}): React.ReactNode {
  const transition = useTransitionRegisterRecord()
  const [status, setStatus] = useState(REGISTER_OPEN_STATUSES[0] as string)

  return (
    <form
      className="editor"
      onSubmit={(event) => {
        event.preventDefault()
        transition.mutate({ recordId, status }, { onSuccess: onDone })
      }}
    >
      <label className="field field--inline field--grow">
        <span className="field__label">Move to</span>
        <select className="input" value={status} onChange={(event) => setStatus(event.target.value)}>
          {REGISTER_OPEN_STATUSES.map((value) => (
            <option key={value} value={value}>
              {value}
            </option>
          ))}
        </select>
      </label>
      {transition.error !== null && <p className="editor__error">{errorText(transition.error)}</p>}
      <div className="editor__actions">
        <button type="submit" className="button button--primary" disabled={transition.isPending}>
          Transition
        </button>
        <button type="button" className="button" onClick={onDone}>
          Cancel
        </button>
      </div>
    </form>
  )
}

/**
 * Closing the record.
 *
 * Not a status change with an extra field — it is its own act, because an approval carries a window
 * that §5.1 step 4 reads to decide whether the cell shows as exempt. The server refuses an
 * `Approved` with no dates, and this form asks for them rather than letting the refusal be the first
 * anyone hears of it.
 */
function CloseForm({ recordId, onDone }: { recordId: string; onDone: () => void }): React.ReactNode {
  const close = useCloseRegisterRecord()
  const [outcome, setOutcome] = useState(REGISTER_OUTCOMES[0] as string)
  const [approvalFrom, setApprovalFrom] = useState('')
  const [approvalTo, setApprovalTo] = useState('')
  const [note, setNote] = useState('')

  const approving = outcome === 'Approved'

  return (
    <form
      className="editor"
      onSubmit={(event) => {
        event.preventDefault()
        close.mutate(
          {
            recordId,
            body: {
              outcome,
              approvalFrom: approving && approvalFrom !== '' ? approvalFrom : null,
              approvalTo: approving && approvalTo !== '' ? approvalTo : null,
              note: note === '' ? null : note,
            },
          },
          { onSuccess: onDone },
        )
      }}
    >
      <label className="field field--inline field--grow">
        <span className="field__label">Close as</span>
        <select className="input" value={outcome} onChange={(event) => setOutcome(event.target.value)}>
          {REGISTER_OUTCOMES.map((value) => (
            <option key={value} value={value}>
              {value}
            </option>
          ))}
        </select>
      </label>

      {approving && (
        <>
          <label className="field field--inline">
            <span className="field__label">Approved from</span>
            <input
              className="input"
              type="date"
              value={approvalFrom}
              onChange={(event) => setApprovalFrom(event.target.value)}
            />
          </label>
          <label className="field field--inline">
            <span className="field__label">Approved to</span>
            <input
              className="input"
              type="date"
              value={approvalTo}
              onChange={(event) => setApprovalTo(event.target.value)}
            />
          </label>
        </>
      )}

      <label className="field field--inline field--grow">
        <span className="field__label">Closing note (OPS)</span>
        <input className="input" value={note} onChange={(event) => setNote(event.target.value)} />
      </label>

      {approving && (
        <p className="panel__hint">
          The approval window must sit inside the swing. §5.1 step 4 reads exactly these two dates to
          decide whether the cell shows as exempt.
        </p>
      )}
      {close.error !== null && <p className="editor__error">{errorText(close.error)}</p>}

      <div className="editor__actions">
        <button
          type="submit"
          className="button button--primary"
          disabled={close.isPending || (approving && (approvalFrom === '' || approvalTo === ''))}
        >
          {close.isPending ? 'Closing…' : `Close as ${outcome}`}
        </button>
        <button type="button" className="button" onClick={onDone}>
          Cancel
        </button>
      </div>
    </form>
  )
}

function errorText(error: unknown): string {
  return error instanceof ApiError ? error.message : String(error)
}
