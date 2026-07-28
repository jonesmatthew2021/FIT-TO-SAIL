import { useState } from 'react'
import { Link } from 'react-router-dom'
import {
  useExceptions,
  useRaiseException,
  useReopenException,
  useResolveException,
  useUnknownHoldings,
} from '../api/queries'
import { ApiError, type ExceptionItem, type UnknownHolding } from '../api/client'
import { useHasRole } from '../api/session'
import { DataTable, type Column } from '../components/DataTable'
import { ErrorPanel } from '../components/ErrorPanel'
import { Spinner } from '../components/Spinner'
import { formatDate } from '../domain/dates'

/** The roles the server accepts for a worklist write — mirrored here to hide the controls. */
const EXCEPTION_RESOLVERS = ['data_steward', 'compliance_lead', 'system_administrator'] as const

/**
 * ADM-7 — the data-quality worklist.
 *
 * The system's standing position on dirty data is that anomalies are preserved and flagged, never
 * silently cleaned (§1, §11). Two lists follow from that, and they behave differently on purpose:
 *
 *  - **Data-quality items** are stored rows. Someone decided each one was worth recording, and
 *    each is closed by a person writing down what they did. The note is mandatory.
 *  - **Unknown holdings** are a live query. A holding whose status was never established is not
 *    a worklist row to tick off; recording the answer on the person's page is what removes it.
 *    That is why there is no "resolve" button here — only a link to the place the answer goes.
 */
export function Exceptions(): React.ReactNode {
  const [state, setState] = useState<'open' | 'resolved' | 'all'>('open')
  const canResolve = useHasRole(...EXCEPTION_RESOLVERS)
  const [raising, setRaising] = useState(false)

  return (
    <div className="screen screen--narrow" style={{ maxWidth: 'var(--content-max)' }}>
      <header className="screen__header screen__header--action">
        <div>
          <h1 className="screen__title">Exceptions</h1>
          <p className="screen__subtitle">
            The data-quality worklist. Anomalies are preserved and flagged, never silently cleaned.
          </p>
        </div>
        {canResolve && (
          <button type="button" className="button button--primary" onClick={() => setRaising(true)}>
            Raise an item
          </button>
        )}
      </header>

      {raising && <RaiseForm onDone={() => setRaising(false)} />}

      <section className="section">
        <div className="section__header">
          <h2 className="section__title">Data-quality items</h2>
        </div>
        <Worklist state={state} onState={setState} canResolve={canResolve} />
      </section>

      <section className="section">
        <div className="section__header">
          <div>
            <h2 className="section__title">Unknown holdings</h2>
            <p className="section__note">
              The standing chase list: a holding whose status was never established. Recording the
              holding is what removes the row.
            </p>
          </div>
        </div>
        <ChaseList />
      </section>
    </div>
  )
}

function Worklist({
  state,
  onState,
  canResolve,
}: {
  state: 'open' | 'resolved' | 'all'
  onState: (state: 'open' | 'resolved' | 'all') => void
  canResolve: boolean
}): React.ReactNode {
  const items = useExceptions(state)
  const resolve = useResolveException()
  const reopen = useReopenException()
  const [resolving, setResolving] = useState<number | null>(null)

  if (items.isPending) return <Spinner label="Loading the worklist" />
  if (items.error !== null) return <ErrorPanel title="Could not load the worklist" error={items.error} />

  const columns: Column<ExceptionItem>[] = [
    {
      id: 'state',
      header: 'State',
      accessorFn: (row) => row.state,
      // Open work is a warning: somebody has to do something about it, and unlike an `unknown`
      // holding it is not an absence of information — it is a recorded anomaly.
      cell: ({ row }) => (
        <span className={`chip chip--${row.original.state === 'open' ? 'warning' : 'good'}`}>
          {row.original.state === 'open' ? 'Open' : 'Resolved'}
        </span>
      ),
    },
    {
      id: 'area',
      header: 'Area',
      accessorFn: (row) => row.area,
      cell: ({ row }) => <span className="text-sm">{row.original.area}</span>,
    },
    { id: 'description', header: 'What is wrong', accessorFn: (row) => row.description },
    {
      id: 'linked',
      header: 'Affects',
      accessorFn: (row) => `${row.linkedEntityType ?? ''} ${row.linkedEntityId ?? ''}`,
      cell: ({ row }) => <LinkedEntity item={row.original} />,
    },
    {
      id: 'raised',
      header: 'Raised',
      accessorFn: (row) => row.createdAt,
      cell: ({ row }) => (
        <span className="muted">{formatDate(row.original.createdAt.slice(0, 10))}</span>
      ),
    },
  ]

  if (canResolve) {
    columns.push({
      id: 'actions',
      header: '',
      enableSorting: false,
      accessorFn: () => '',
      // Once resolved, the row carries what was done rather than a button — the note is the record
      // of the resolution, and an item closed with no explanation is indistinguishable from one
      // dismissed to clear the list.
      cell: ({ row }) =>
        row.original.state === 'open' ? (
          <button
            type="button"
            className="button button--quiet"
            onClick={() => setResolving(row.original.id)}
          >
            Resolve
          </button>
        ) : (
          <span className="table__wrap">
            {row.original.resolutionNote ?? 'Resolved'}
            {row.original.resolvedBy !== null && ` — ${row.original.resolvedBy}`}{' '}
            <button
              type="button"
              className="link-action"
              disabled={reopen.isPending}
              onClick={() => reopen.mutate(row.original.id)}
            >
              Reopen
            </button>
          </span>
        ),
    })
  }

  const toolbar = (
    <div className="seg">
      {(['open', 'resolved', 'all'] as const).map((value) => (
        <label key={value} className="seg__opt">
          <input
            type="radio"
            name="exception-state"
            checked={state === value}
            onChange={() => onState(value)}
          />
          {value === 'open' ? 'Open' : value === 'resolved' ? 'Resolved' : 'All'}
        </label>
      ))}
    </div>
  )

  return (
    <>
      {reopen.error !== null && <ErrorPanel title="Could not reopen that item" error={reopen.error} />}

      {resolving !== null && (
        <ResolveForm
          onCancel={() => setResolving(null)}
          onSubmit={(note) =>
            resolve.mutate({ id: resolving, note }, { onSuccess: () => setResolving(null) })
          }
          pending={resolve.isPending}
          error={resolve.error}
        />
      )}

      <DataTable
        rows={items.data}
        columns={columns}
        toolbar={toolbar}
        filterPlaceholder="Filter by area or description"
        empty={state === 'open' ? 'Nothing open. The worklist is clear.' : 'No items in this state.'}
        csv={{
          filename: `exceptions-${state}.csv`,
          columns: [
            { header: 'State', value: (row) => row.state },
            { header: 'Area', value: (row) => row.area },
            { header: 'Description', value: (row) => row.description },
            { header: 'Affects', value: (row) => row.linkedEntityType },
            { header: 'Affects id', value: (row) => row.linkedEntityId },
            { header: 'Raised', value: (row) => row.createdAt },
            { header: 'Raised by', value: (row) => row.createdBy },
            { header: 'Resolution', value: (row) => row.resolutionNote },
            { header: 'Resolved by', value: (row) => row.resolvedBy },
          ],
        }}
      />
    </>
  )
}

/**
 * "Every entity view deep-links" (§6). Only `Person` has a screen to link to yet; the others show
 * the type and id rather than a link that would 404, which is more honest than nothing.
 */
function LinkedEntity({ item }: { item: ExceptionItem }): React.ReactNode {
  if (item.linkedEntityType === null || item.linkedEntityId === null) {
    return <span className="dim">—</span>
  }
  if (item.linkedEntityType === 'Person') {
    return <Link to={`/people/${item.linkedEntityId}`}>Person #{item.linkedEntityId}</Link>
  }
  return (
    <span className="muted">
      {item.linkedEntityType} #{item.linkedEntityId}
    </span>
  )
}

function ResolveForm({
  onCancel,
  onSubmit,
  pending,
  error,
}: {
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
        <span className="field__label">What was done</span>
        <input
          className="input"
          value={note}
          autoFocus
          placeholder="Confirmed distinct people; both records kept."
          onChange={(event) => setNote(event.target.value)}
        />
      </label>
      {/* Required by the server too. An item closed with no explanation is indistinguishable
          from one dismissed to clear the list. */}
      {error !== null && <p className="editor__error">{errorText(error)}</p>}
      <div className="editor__actions">
        <button
          type="submit"
          className="button button--primary"
          disabled={pending || note.trim() === ''}
        >
          {pending ? 'Resolving…' : 'Resolve'}
        </button>
        <button type="button" className="button" onClick={onCancel}>
          Cancel
        </button>
      </div>
    </form>
  )
}

function RaiseForm({ onDone }: { onDone: () => void }): React.ReactNode {
  const raise = useRaiseException()
  const [area, setArea] = useState('')
  const [description, setDescription] = useState('')

  return (
    <form
      className="editor"
      onSubmit={(event) => {
        event.preventDefault()
        raise.mutate({ area, description }, { onSuccess: onDone })
      }}
    >
      <label className="field field--inline">
        <span className="field__label">Area</span>
        <input
          className="input"
          value={area}
          placeholder="people, holdings, catalogue…"
          onChange={(event) => setArea(event.target.value)}
        />
      </label>
      <label className="field field--inline field--grow">
        <span className="field__label">What is wrong</span>
        <input
          className="input"
          value={description}
          onChange={(event) => setDescription(event.target.value)}
        />
      </label>
      {raise.error !== null && <p className="editor__error">{errorText(raise.error)}</p>}
      <div className="editor__actions">
        <button
          type="submit"
          className="button button--primary"
          disabled={raise.isPending || area.trim() === '' || description.trim() === ''}
        >
          Raise
        </button>
        <button type="button" className="button" onClick={onDone}>
          Cancel
        </button>
      </div>
    </form>
  )
}

function ChaseList(): React.ReactNode {
  const unknown = useUnknownHoldings()

  if (unknown.isPending) return <Spinner label="Loading the chase list" />
  if (unknown.error !== null) {
    return <ErrorPanel title="Could not load the chase list" error={unknown.error} />
  }

  const columns: Column<UnknownHolding>[] = [
    {
      id: 'name',
      header: 'Person',
      accessorFn: (row) => row.name,
      // The link is the whole point: the fix is recording the holding, not ticking a box here.
      cell: ({ row }) => <Link to={`/people/${row.original.personId}`}>{row.original.name}</Link>,
    },
    {
      id: 'sam',
      header: 'Sam #',
      accessorFn: (row) => row.sam,
      cell: ({ row }) => <span className="mono">{row.original.sam}</span>,
    },
    {
      id: 'code',
      header: 'Requirement',
      accessorFn: (row) => row.code,
      cell: ({ row }) => <span className="mono">{row.original.code}</span>,
    },
    {
      id: 'title',
      header: 'Title',
      accessorFn: (row) => row.title,
      cell: ({ row }) => (
        <span className="meta">{row.original.title}</span>
      ),
    },
    // No Resolve button, and that is the point: recording the holding on the person's page is what
    // removes the row. There is nothing to tick off here (Q11).
  ]

  return (
    <DataTable
      rows={unknown.data}
      columns={columns}
      filterPlaceholder="Filter by person or requirement"
      empty="No unknown holdings — every holding has an established status."
      csv={{
        filename: 'unknown-holdings.csv',
        columns: [
          { header: 'Sam #', value: (row) => row.sam },
          { header: 'Name', value: (row) => row.name },
          { header: 'Requirement', value: (row) => row.code },
          { header: 'Title', value: (row) => row.title },
        ],
      }}
    />
  )
}

function errorText(error: unknown): string {
  return error instanceof ApiError ? error.message : String(error)
}
