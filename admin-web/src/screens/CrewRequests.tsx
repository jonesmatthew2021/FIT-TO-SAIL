import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useCrewRequests, useDecideCrewRequest } from '../api/queries'
import { ApiError, type CrewRequest, type CrewRequestStatus } from '../api/client'
import { useHasRole } from '../api/session'
import { DataTable, type Column } from '../components/DataTable'
import { ErrorPanel } from '../components/ErrorPanel'
import { Spinner } from '../components/Spinner'
import { formatDate } from '../domain/dates'

/** The roles the server accepts for a decision — mirrored here to hide the controls. */
const CREW_REQUEST_DECIDERS = ['crew_coordinator', 'workflow_manager', 'system_administrator'] as const

/**
 * ADM-11 — the crew request queue.
 *
 * What the crew app's one-tap answers land in. A crew member taps "Course booked" or "Ask" and the
 * §9 notification tells whoever held the role at that moment; this is the list that still answers
 * "what has the crew asked us for that nobody has dealt with" tomorrow morning.
 *
 * Three things about this screen are deliberate and easy to undo by accident:
 *
 *  1. **There is no compliance state on it.** A crew statement is not a compliance answer (AUTH-1):
 *     saying a course is booked does not close a gap, and a column of cell states here would read as
 *     though it had. The person's actual standing is one click away, on their page, where the engine's
 *     answer lives.
 *  2. **A decision needs a note and is terminal.** No reopen — a crew member still waiting asks
 *     again, and the second request carries its own date.
 *  3. **Dismissing has a consequence, and the screen says so before you do it.** A dismissed
 *     `course_booked` resumes the expiry chasing the statement had silenced. That is the point of
 *     having the button, and it is not something to discover afterwards.
 */
export function CrewRequests(): React.ReactNode {
  const [status, setStatus] = useState<CrewRequestStatus>('open')
  const canDecide = useHasRole(...CREW_REQUEST_DECIDERS)
  const requests = useCrewRequests(status)
  const decide = useDecideCrewRequest()
  const [deciding, setDeciding] = useState<{ id: number; decision: 'action' | 'dismiss' } | null>(
    null,
  )

  const columns: Column<CrewRequest>[] = [
    {
      id: 'status',
      header: 'Status',
      accessorFn: (row) => row.status,
      cell: ({ row }) => <StatusChip status={row.original.status} />,
    },
    {
      id: 'kind',
      header: 'Asked for',
      accessorFn: (row) => row.kind,
      // The course goes under the label rather than in a column of its own: only two of the four
      // kinds have one, and an empty column on every help request would be a worse trade than a
      // second line on the rows that need it. It is the server's rendering, stored when the
      // request was made, so it still reads correctly after the option has been withdrawn.
      cell: ({ row }) => (
        <span className="table__wrap">
          <span className="text-sm">{kindLabel(row.original.kind)}</span>
          {row.original.subjectLabel !== null && (
            <>
              <br />
              <span className="muted text-sm">{row.original.subjectLabel}</span>
            </>
          )}
        </span>
      ),
    },
    {
      id: 'person',
      header: 'Crew member',
      accessorFn: (row) => row.personName,
      cell: ({ row }) => (
        <Link to={`/people/${row.original.personId}`}>{row.original.personName}</Link>
      ),
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
      accessorFn: (row) => `${row.code} ${row.title}`,
      cell: ({ row }) => (
        <span className="table__wrap">
          <span className="mono">{row.original.code}</span> {row.original.title}
        </span>
      ),
    },
    {
      id: 'expiry',
      header: 'Lapses',
      accessorFn: (row) => row.aboutExpiry ?? '',
      // The fact the coordinator is actually judging. "Booked, and it lapses on the 15th" is a
      // different request from "booked, with a year to run", and the em dash means the requirement
      // is not held at all rather than that nobody looked.
      cell: ({ row }) =>
        row.original.aboutExpiry === null ? (
          <span className="dim">—</span>
        ) : (
          <span>{formatDate(row.original.aboutExpiry)}</span>
        ),
    },
    {
      id: 'raised',
      header: 'Asked',
      accessorFn: (row) => row.raisedAt,
      cell: ({ row }) => (
        <span className="muted">{formatDate(row.original.raisedAt.slice(0, 10))}</span>
      ),
    },
  ]

  if (canDecide) {
    columns.push({
      id: 'actions',
      header: '',
      enableSorting: false,
      accessorFn: () => '',
      cell: ({ row }) =>
        row.original.status === 'open' ? (
          <span className="row-actions">
            <button
              type="button"
              className="button button--quiet"
              onClick={() => setDeciding({ id: row.original.id, decision: 'action' })}
            >
              {actionLabel(row.original.kind)}
            </button>
            <button
              type="button"
              className="link-action"
              onClick={() => setDeciding({ id: row.original.id, decision: 'dismiss' })}
            >
              Dismiss
            </button>
            {/* §6's "one-click pre-filled exemption request", from the other end: when the course
                lands after the cutoff, the register is where this goes next. Same param names as
                the gap report's link. A statement is not swing-bound, so the swing itself is the
                one thing the coordinator still picks. */}
            <Link
              className="link-action"
              to={
                `/register/new?partnership=${encodeURIComponent(row.original.partnershipAbbrev)}` +
                `&personId=${row.original.personId}&requirementId=${row.original.requirementId}`
              }
            >
              Raise request
            </Link>
          </span>
        ) : (
          <span className="table__wrap">
            {row.original.decisionNote}
            {row.original.decidedBy !== null && ` — ${row.original.decidedBy}`}
          </span>
        ),
    })
  }

  const toolbar = (
    <div className="seg">
      {(['open', 'actioned', 'dismissed', 'all'] as const).map((value) => (
        <label key={value} className="seg__opt">
          <input
            type="radio"
            name="crew-request-status"
            checked={status === value}
            onChange={() => setStatus(value)}
          />
          {value === 'open'
            ? 'Open'
            : value === 'actioned'
              ? 'Actioned'
              : value === 'dismissed'
                ? 'Dismissed'
                : 'All'}
        </label>
      ))}
    </div>
  )

  return (
    <div className="screen screen--narrow" style={{ maxWidth: 'var(--content-max)' }}>
      <header className="screen__header">
        <div>
          <h1 className="screen__title">Crew requests</h1>
          <p className="screen__subtitle">
            What crew members have told the office from their phones. None of it changes a
            compliance answer — a booked course is not a held certificate.
          </p>
        </div>
      </header>

      {requests.isPending && <Spinner label="Loading crew requests" />}
      {requests.error !== null && (
        <ErrorPanel title="Could not load crew requests" error={requests.error} />
      )}

      {deciding !== null && (
        <DecideForm
          decision={deciding.decision}
          onCancel={() => setDeciding(null)}
          onSubmit={(note) =>
            decide.mutate(
              { id: deciding.id, decision: deciding.decision, note },
              { onSuccess: () => setDeciding(null) },
            )
          }
          pending={decide.isPending}
          error={decide.error}
        />
      )}

      {requests.data !== undefined && (
        <DataTable
          rows={requests.data}
          columns={columns}
          toolbar={toolbar}
          filterPlaceholder="Filter by crew member or requirement"
          empty={
            status === 'open'
              ? 'Nothing outstanding. Every crew request has been dealt with.'
              : 'No requests in this state.'
          }
          csv={{
            filename: `crew-requests-${status}.csv`,
            columns: [
              { header: 'Status', value: (row) => row.status },
              { header: 'Asked for', value: (row) => kindLabel(row.kind) },
              { header: 'Course', value: (row) => row.subjectLabel },
              { header: 'Sam #', value: (row) => row.sam },
              { header: 'Crew member', value: (row) => row.personName },
              { header: 'Position', value: (row) => row.positionName },
              { header: 'Partnership', value: (row) => row.partnershipAbbrev },
              { header: 'Requirement', value: (row) => row.code },
              { header: 'Title', value: (row) => row.title },
              { header: 'Lapses', value: (row) => row.aboutExpiry },
              { header: 'Asked', value: (row) => row.raisedAt },
              { header: 'Decision', value: (row) => row.decisionNote },
              { header: 'Decided by', value: (row) => row.decidedBy },
            ],
          }}
        />
      )}
    </div>
  )
}

/**
 * Both terminal states are `good`-toned rather than one of them reading as a failure.
 *
 * A dismissal is not a rejection of the crew member — most of them will be "already handled
 * elsewhere". What the row carries instead is the note, which is where the difference actually is.
 */
function StatusChip({ status }: { status: string }): React.ReactNode {
  if (status === 'open') return <span className="chip chip--warning">Open</span>
  if (status === 'actioned') return <span className="chip chip--good">Actioned</span>
  return <span className="chip chip--muted">Dismissed</span>
}

/**
 * What the crew member said, in the office's words.
 *
 * Reported speech on purpose — "Says a course is booked", not "Course booked". The distinction is
 * the whole basis of this queue: the row is a claim awaiting confirmation, and a label that stated
 * it as fact would invite exactly the reading the compliance rules forbid.
 */
export function kindLabel(kind: string): string {
  if (kind === 'course_booked') return 'Says a course is booked'
  if (kind === 'help_requested') return 'Needs help arranging it'
  if (kind === 'seat_requested') return 'Wants a seat on a course'
  // Named for what the office has to do about it, which is not the same job as booking a seat:
  // a waitlist gets chased with the provider, and a coordinator triaging their morning should be
  // able to tell the two apart before opening anything.
  if (kind === 'waitlisted') return 'Waitlisted for a course'
  // An unrecognised kind renders verbatim rather than as something reassuring — the same rule the
  // crew app applies to a cell state it has never heard of. Both revisions are live during an
  // expand/contract deploy, so a kind this build predates must not blank the row.
  return kind
}

/** The positive action, named for what the coordinator actually did. */
export function actionLabel(kind: string): string {
  if (kind === 'course_booked') return 'Confirm'
  if (kind === 'seat_requested' || kind === 'waitlisted') return 'Booked'
  return 'Arranged'
}

function DecideForm({
  decision,
  onCancel,
  onSubmit,
  pending,
  error,
}: {
  decision: 'action' | 'dismiss'
  onCancel: () => void
  onSubmit: (note: string) => void
  pending: boolean
  error: unknown
}): React.ReactNode {
  const [note, setNote] = useState('')

  return (
    <form
      className="editor"
      onSubmit={(event) => {
        event.preventDefault()
        onSubmit(note)
      }}
    >
      <label className="field field--inline field--grow">
        <span className="field__label">
          {decision === 'action' ? 'What was done' : 'Why not'} — the crew member reads this
        </span>
        <input
          className="input"
          value={note}
          autoFocus
          // Written to the person, not about them. That is the whole reason the label says who
          // reads it: "no record" is a filing note, and the sentence below is an answer.
          placeholder={
            decision === 'action'
              ? 'Seat confirmed with the provider for 12 Aug.'
              : 'We could not find your booking — can you forward the confirmation?'
          }
          onChange={(event) => setNote(event.target.value)}
        />
      </label>
      {/* Said before the click, not discovered after it: this is the one decision on the screen
          that changes what the crew member's phone will show them next. */}
      {decision === 'dismiss' && (
        <p className="editor__note">
          Dismissing resumes the expiry reminders this answer had switched off, and notifies the
          crew member.
        </p>
      )}
      {error !== null && <p className="editor__error">{errorText(error)}</p>}
      <div className="editor__actions">
        <button
          type="submit"
          className="button button--primary"
          disabled={pending || note.trim() === ''}
        >
          {pending ? 'Saving…' : decision === 'action' ? 'Mark done' : 'Dismiss'}
        </button>
        <button type="button" className="button" onClick={onCancel}>
          Cancel
        </button>
      </div>
    </form>
  )
}

function errorText(error: unknown): string {
  return error instanceof ApiError ? error.message : String(error)
}
