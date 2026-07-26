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
    <div className="screen">
      <header className="screen__header">
        <h1 className="screen__title">Exceptions</h1>
        <p className="screen__subtitle">
          ADM-7 — anomalies preserved and flagged, never silently cleaned (§11).
        </p>
      </header>

      <section className="section">
        <div className="section__header">
          <h2 className="section__title">Data-quality items</h2>
        </div>

        <div className="selector">
          <label className="field field--inline">
            <span className="field__label">State</span>
            <select
              className="input"
              value={state}
              onChange={(event) => setState(event.target.value as typeof state)}
            >
              <option value="open">Open</option>
              <option value="resolved">Resolved</option>
              <option value="all">All</option>
            </select>
          </label>
          {canResolve && (
            <button
              type="button"
              className="button button--primary"
              onClick={() => setRaising(true)}
            >
              Raise an item
            </button>
          )}
        </div>

        {raising && <RaiseForm onDone={() => setRaising(false)} />}

        <Worklist state={state} canResolve={canResolve} />
      </section>

      <section className="section">
        <h2 className="section__title">Unknown holdings</h2>
        <p className="note">
          Q11's standing chase list: a holding whose status was never established. Blank in the
          source workbook is not the same as "not held", so these stay visible until someone
          records the answer.
        </p>
        <ChaseList />
      </section>
    </div>
  )
}

function Worklist({
  state,
  canResolve,
}: {
  state: 'open' | 'resolved' | 'all'
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
      cell: ({ row }) => (
        <span className={`chip chip--${row.original.state === 'open' ? 'caution' : 'good'}`}>
          {row.original.state}
        </span>
      ),
    },
    { id: 'area', header: 'Area', accessorFn: (row) => row.area },
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
        <>
          {formatDate(row.original.createdAt.slice(0, 10))}
          <span className="muted"> {row.original.createdBy}</span>
        </>
      ),
    },
    {
      id: 'resolution',
      header: 'Resolution',
      accessorFn: (row) => row.resolutionNote ?? '',
      cell: ({ row }) =>
        row.original.resolutionNote === null ? (
          <span className="muted">—</span>
        ) : (
          <>
            {row.original.resolutionNote}
            <span className="muted"> — {row.original.resolvedBy}</span>
          </>
        ),
    },
  ]

  if (canResolve) {
    columns.push({
      id: 'actions',
      header: '',
      enableSorting: false,
      accessorFn: () => '',
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
          <button
            type="button"
            className="button button--quiet"
            disabled={reopen.isPending}
            onClick={() => reopen.mutate(row.original.id)}
          >
            Reopen
          </button>
        ),
    })
  }

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
    return <span className="muted">—</span>
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
        <button type="button" className="button button--quiet" onClick={onCancel}>
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
        <button type="button" className="button button--quiet" onClick={onDone}>
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
    { id: 'sam', header: 'Sam #', accessorFn: (row) => row.sam },
    {
      id: 'code',
      header: 'Requirement',
      accessorFn: (row) => row.code,
      cell: ({ row }) => <span className="mono">{row.original.code}</span>,
    },
    { id: 'title', header: 'Title', accessorFn: (row) => row.title },
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
