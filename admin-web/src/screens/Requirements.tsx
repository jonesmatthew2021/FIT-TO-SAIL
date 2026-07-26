import { useState } from 'react'
import {
  useAddAlias,
  useCatalogue,
  useCreateRequirement,
  useRemoveAlias,
  useUpdateRequirement,
} from '../api/queries'
import { ApiError, type RequirementDetail, type SaveRequirementRequest } from '../api/client'
import { useHasRole } from '../api/session'
import { DataTable, type Column } from '../components/DataTable'
import { ErrorPanel } from '../components/ErrorPanel'
import { Spinner } from '../components/Spinner'
import { REQUIREMENT_CATEGORIES, categoryLabel } from '../domain/enums'

/** The roles the server accepts for a catalogue write — mirrored here to hide the controls. */
const CATALOGUE_EDITORS = ['compliance_lead', 'system_administrator'] as const

/**
 * ADM-6 — the requirement catalogue.
 *
 * This is the vocabulary the whole system is written in: every matrix rule, holding, register
 * record and evidence match names a requirement. Two consequences shape the screen.
 *
 * **Retired entries stay.** The status filter defaults to active because that is the working set,
 * but retired rows are one click away and never hidden from the data — a superseded matrix
 * version and a five-year-old register record still have to render their codes (§4.1).
 *
 * **Usage is shown before a retirement, not after.** The counts come from the server across every
 * matrix version, and they are the answer to the only question worth asking before retiring
 * something: is anything still standing on it.
 */
export function Requirements(): React.ReactNode {
  const catalogue = useCatalogue()
  const canEdit = useHasRole(...CATALOGUE_EDITORS)
  const [status, setStatus] = useState<'active' | 'retired' | 'all'>('active')
  const [category, setCategory] = useState('all')
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [adding, setAdding] = useState(false)

  if (catalogue.isPending) return <Spinner label="Loading the catalogue" />
  if (catalogue.error !== null) {
    return <ErrorPanel title="Could not load the catalogue" error={catalogue.error} />
  }

  const rows = catalogue.data.filter(
    (requirement) =>
      (status === 'all' || requirement.status === status) &&
      (category === 'all' || requirement.category === category),
  )
  const selected = catalogue.data.find((requirement) => requirement.id === selectedId) ?? null

  const columns: Column<RequirementDetail>[] = [
    {
      id: 'code',
      header: 'Code',
      accessorFn: (row) => row.code,
      cell: ({ row }) => <span className="mono">{row.original.code}</span>,
    },
    { id: 'category', header: 'Category', accessorFn: (row) => categoryLabel(row.category) },
    { id: 'title', header: 'Title', accessorFn: (row) => row.title },
    {
      id: 'status',
      header: 'Status',
      accessorFn: (row) => row.status,
      cell: ({ row }) => (
        <span className={`chip chip--${row.original.status === 'active' ? 'good' : 'muted'}`}>
          {row.original.status}
        </span>
      ),
    },
    {
      id: 'authority',
      header: 'Issuing authority',
      accessorFn: (row) => row.issuingAuthority ?? '',
      cell: ({ row }) => row.original.issuingAuthority ?? <span className="muted">—</span>,
    },
    {
      id: 'aliases',
      header: 'Aliases',
      accessorFn: (row) => row.aliases.length,
      cell: ({ row }) =>
        row.original.aliases.length === 0 ? (
          <span className="muted">—</span>
        ) : (
          row.original.aliases.length
        ),
    },
    {
      id: 'usage',
      header: 'Used by',
      accessorFn: (row) => row.usage.total,
      cell: ({ row }) => <UsageSummary requirement={row.original} />,
    },
  ]

  return (
    <div className="screen">
      <header className="screen__header">
        <h1 className="screen__title">Requirements</h1>
        <p className="screen__subtitle">
          ADM-6 — the catalogue every rule, holding and register record is written against.
        </p>
      </header>

      <div className="selector">
        <label className="field field--inline">
          <span className="field__label">Status</span>
          <select
            className="input"
            value={status}
            onChange={(event) => setStatus(event.target.value as typeof status)}
          >
            <option value="active">Active</option>
            <option value="retired">Retired</option>
            <option value="all">All</option>
          </select>
        </label>

        <label className="field field--inline">
          <span className="field__label">Category</span>
          <select
            className="input"
            value={category}
            onChange={(event) => setCategory(event.target.value)}
          >
            <option value="all">All categories</option>
            {REQUIREMENT_CATEGORIES.map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </select>
        </label>

        {canEdit && (
          <button
            type="button"
            className="button button--primary"
            onClick={() => {
              setAdding(true)
              setSelectedId(null)
            }}
          >
            Add requirement
          </button>
        )}
      </div>

      {adding && <RequirementEditor onDone={() => setAdding(false)} />}

      <DataTable
        rows={rows}
        columns={columns}
        filterPlaceholder="Filter by code, title or authority"
        empty="No catalogue entries match this filter."
        onRowClick={(row) => setSelectedId(row.id === selectedId ? null : row.id)}
        rowClassName={(row) => (row.status === 'retired' ? 'table__row--retired' : undefined)}
        csv={{
          filename: 'requirement-catalogue.csv',
          columns: [
            { header: 'Code', value: (row) => row.code },
            { header: 'Category', value: (row) => row.category },
            { header: 'Title', value: (row) => row.title },
            { header: 'Status', value: (row) => row.status },
            { header: 'Issuing authority', value: (row) => row.issuingAuthority },
            { header: 'Notes', value: (row) => row.notes },
            { header: 'Aliases', value: (row) => row.aliases.map((a) => a.alias).join(' | ') },
            { header: 'Holdings', value: (row) => row.usage.holdings },
            { header: 'Matrix rules', value: (row) => row.usage.requirementRules },
            { header: 'Quota rules', value: (row) => row.usage.quotaRules },
            { header: 'Conditional rules', value: (row) => row.usage.conditionalRules },
            { header: 'Register records', value: (row) => row.usage.registerRecords },
          ],
        }}
      />

      {selected !== null && (
        <section className="section">
          <h2 className="section__title">
            <span className="mono">{selected.code}</span> {selected.title}
          </h2>
          <RequirementDetailPanel requirement={selected} canEdit={canEdit} />
        </section>
      )}
    </div>
  )
}

/**
 * "Used by 14" with the breakdown behind a title attribute rather than five columns.
 *
 * Zero is rendered as a plain "unused" rather than a 0, because the difference between "nothing
 * depends on this" and "one thing does" is the whole decision a Compliance Lead is making.
 */
function UsageSummary({ requirement }: { requirement: RequirementDetail }): React.ReactNode {
  const { usage } = requirement
  if (usage.total === 0) return <span className="muted">unused</span>
  return (
    <span title={usageBreakdown(requirement)}>
      {usage.total}
      {usage.requirementRules + usage.quotaRules + usage.conditionalRules > 0 && (
        <span className="muted"> · in the matrix</span>
      )}
    </span>
  )
}

function usageBreakdown(requirement: RequirementDetail): string {
  const { usage } = requirement
  return [
    `${usage.holdings} holdings`,
    `${usage.requirementRules} matrix rules`,
    `${usage.quotaRules} quota rules`,
    `${usage.conditionalRules} conditional rules`,
    `${usage.registerRecords} register records`,
  ].join(', ')
}

function RequirementDetailPanel({
  requirement,
  canEdit,
}: {
  requirement: RequirementDetail
  canEdit: boolean
}): React.ReactNode {
  const [editing, setEditing] = useState(false)

  return (
    <>
      <dl className="facts">
        <div>
          <dt>Category</dt>
          <dd>{categoryLabel(requirement.category)}</dd>
        </div>
        <div>
          <dt>Status</dt>
          <dd>{requirement.status}</dd>
        </div>
        <div>
          <dt>Issuing authority</dt>
          <dd>{requirement.issuingAuthority ?? '—'}</dd>
        </div>
        <div>
          <dt>Used by</dt>
          <dd>{usageBreakdown(requirement)}</dd>
        </div>
      </dl>

      {requirement.notes !== null && <p className="note">{requirement.notes}</p>}

      {canEdit && !editing && (
        <button type="button" className="button button--quiet" onClick={() => setEditing(true)}>
          Edit
        </button>
      )}
      {editing && (
        <RequirementEditor existing={requirement} onDone={() => setEditing(false)} />
      )}

      <h3 className="holding-group__title">Legacy titles</h3>
      <AliasEditor requirement={requirement} canEdit={canEdit} />
    </>
  )
}

/**
 * The alias list (§4.1).
 *
 * Aliases are how 445 rows of historic free-text register titles join the catalogue, and how the
 * evidence pipeline matches a document that names a certificate by its old name (§8 stage 3).
 * The server refuses an alias that already resolves to another entry — an alias matching two
 * requirements would send every document naming it to review permanently.
 */
function AliasEditor({
  requirement,
  canEdit,
}: {
  requirement: RequirementDetail
  canEdit: boolean
}): React.ReactNode {
  const add = useAddAlias()
  const remove = useRemoveAlias()
  const [alias, setAlias] = useState('')

  return (
    <>
      {requirement.aliases.length === 0 && (
        <p className="empty">No legacy titles map to this requirement.</p>
      )}
      {requirement.aliases.length > 0 && (
        <ul className="crew-list">
          {requirement.aliases.map((entry) => (
            <li key={entry.id} className="crew-list__item">
              <span>{entry.alias}</span>
              {canEdit && (
                <button
                  type="button"
                  className="button button--quiet"
                  disabled={remove.isPending}
                  onClick={() =>
                    remove.mutate({ requirementId: requirement.id, aliasId: entry.id })
                  }
                >
                  Remove
                </button>
              )}
            </li>
          ))}
        </ul>
      )}

      {canEdit && (
        <form
          className="editor"
          onSubmit={(event) => {
            event.preventDefault()
            if (alias.trim() === '') return
            add.mutate(
              { requirementId: requirement.id, alias: alias.trim() },
              { onSuccess: () => setAlias('') },
            )
          }}
        >
          <label className="field field--inline field--grow">
            <span className="field__label">Add a legacy title</span>
            <input
              className="input"
              value={alias}
              placeholder="e.g. Working at Height"
              onChange={(event) => setAlias(event.target.value)}
            />
          </label>
          <div className="editor__actions">
            <button
              type="submit"
              className="button button--primary"
              disabled={add.isPending || alias.trim() === ''}
            >
              Add
            </button>
          </div>
          {add.error !== null && <p className="editor__error">{errorText(add.error)}</p>}
          {remove.error !== null && <p className="editor__error">{errorText(remove.error)}</p>}
        </form>
      )}
    </>
  )
}

/**
 * Create and edit share a form, with one difference that matters: **the code is set once**.
 *
 * The server enforces it too. A requirement code is the business key every matrix rule, CSV
 * export and register record is written against, so renaming it in place would silently rewrite
 * history. A miscoded entry is retired and replaced, which leaves a trail.
 */
function RequirementEditor({
  existing,
  onDone,
}: {
  existing?: RequirementDetail
  onDone: () => void
}): React.ReactNode {
  const create = useCreateRequirement()
  const update = useUpdateRequirement()
  const mutation = existing === undefined ? create : update

  const [code, setCode] = useState(existing?.code ?? '')
  const [category, setCategory] = useState(existing?.category ?? REQUIREMENT_CATEGORIES[0])
  const [title, setTitle] = useState(existing?.title ?? '')
  const [issuingAuthority, setIssuingAuthority] = useState(existing?.issuingAuthority ?? '')
  const [notes, setNotes] = useState(existing?.notes ?? '')
  const [status, setStatus] = useState(existing?.status ?? 'active')

  function submit(event: React.FormEvent): void {
    event.preventDefault()
    const body: SaveRequirementRequest = {
      category: category as string,
      title,
      issuingAuthority: issuingAuthority === '' ? null : issuingAuthority,
      notes: notes === '' ? null : notes,
      status,
    }
    if (existing === undefined) {
      create.mutate({ ...body, code }, { onSuccess: onDone })
    } else {
      update.mutate({ requirementId: existing.id, body }, { onSuccess: onDone })
    }
  }

  const retiringInUse =
    existing !== undefined && status === 'retired' && existing.status === 'active' && existing.usage.total > 0

  return (
    <form className="editor" onSubmit={submit}>
      <label className="field field--inline">
        <span className="field__label">Code</span>
        <input
          className="input"
          value={code}
          disabled={existing !== undefined}
          placeholder="PS-04"
          onChange={(event) => setCode(event.target.value)}
        />
      </label>

      <label className="field field--inline">
        <span className="field__label">Category</span>
        <select
          className="input"
          value={category}
          onChange={(event) => setCategory(event.target.value)}
        >
          {REQUIREMENT_CATEGORIES.map((value) => (
            <option key={value} value={value}>
              {value}
            </option>
          ))}
        </select>
      </label>

      <label className="field field--inline field--grow">
        <span className="field__label">Title</span>
        <input className="input" value={title} onChange={(event) => setTitle(event.target.value)} />
      </label>

      <label className="field field--inline">
        <span className="field__label">Issuing authority</span>
        <input
          className="input"
          value={issuingAuthority}
          onChange={(event) => setIssuingAuthority(event.target.value)}
        />
      </label>

      <label className="field field--inline field--grow">
        <span className="field__label">Notes</span>
        <input className="input" value={notes} onChange={(event) => setNotes(event.target.value)} />
      </label>

      {existing !== undefined && (
        <label className="field field--inline">
          <span className="field__label">Status</span>
          <select
            className="input"
            value={status}
            onChange={(event) => setStatus(event.target.value)}
          >
            <option value="active">Active</option>
            <option value="retired">Retired</option>
          </select>
        </label>
      )}

      {/* Not a block: retiring something still in use is a legitimate first step before the rules
          that reference it are rewritten. It is worth saying out loud, once. */}
      {retiringInUse && (
        <p className="editor__error">
          Retiring this leaves {usageBreakdown(existing)} pointing at it. Nothing breaks — retired
          entries still resolve — but the matrix will keep requiring it until the rules change.
        </p>
      )}

      {mutation.error !== null && <p className="editor__error">{errorText(mutation.error)}</p>}

      <div className="editor__actions">
        <button
          type="submit"
          className="button button--primary"
          disabled={mutation.isPending || title.trim() === '' || (existing === undefined && code.trim() === '')}
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

function errorText(error: unknown): string {
  return error instanceof ApiError ? error.message : String(error)
}
