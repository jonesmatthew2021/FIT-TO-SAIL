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
      cell: ({ row }) => (
        <button
          type="button"
          className="link-action mono"
          onClick={() => setSelectedId(row.original.id)}
        >
          {row.original.code}
        </button>
      ),
    },
    {
      id: 'category',
      header: 'Cat',
      accessorFn: (row) => categoryLabel(row.category),
      cell: ({ row }) => <span className="mono muted">{categoryLabel(row.original.category)}</span>,
    },
    { id: 'title', header: 'Title', accessorFn: (row) => row.title },
    {
      id: 'authority',
      header: 'Issuing authority',
      accessorFn: (row) => row.issuingAuthority ?? '',
      cell: ({ row }) =>
        row.original.issuingAuthority === null ? (
          <span className="dim">—</span>
        ) : (
          <span className="meta">{row.original.issuingAuthority}</span>
        ),
    },
    {
      id: 'aliases',
      header: 'Aliases',
      accessorFn: (row) => row.aliases.length,
      cell: ({ row }) =>
        row.original.aliases.length === 0 ? (
          <span className="dim">—</span>
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

  const toolbar = (
    <>
      <div className="seg">
        {(['active', 'retired', 'all'] as const).map((value) => (
          <label key={value} className="seg__opt">
            <input
              type="radio"
              name="catalogue-status"
              checked={status === value}
              onChange={() => setStatus(value)}
            />
            {value === 'active' ? 'Active' : value === 'retired' ? 'Retired' : 'All'}
          </label>
        ))}
      </div>

      {/* Bare codes: Appendix A enumerates QL · VS · PS · MS · CS · HR · PT · VI · PI and nothing
          says what they expand to, so inventing an expansion would put a wrong label in front of
          people who know the right one. A question for the client. */}
      <select
        className="input input--auto"
        aria-label="Category"
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
    </>
  )

  return (
    <div className="screen screen--split">
      <header className="screen__header screen__header--action">
        <div>
          <h1 className="screen__title">Requirements</h1>
          <p className="screen__subtitle">
            The catalogue — the vocabulary every rule, holding and register record is written against.
          </p>
        </div>
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
      </header>

      {adding && <RequirementEditor onDone={() => setAdding(false)} />}

      <div className="split split--wide-list">
        <DataTable
          rows={rows}
          columns={columns}
          toolbar={toolbar}
          minWidth={620}
          filterPlaceholder="Filter"
          empty="No catalogue entries match this filter."
          onRowClick={(row) => setSelectedId(row.id === selectedId ? null : row.id)}
          rowClassName={(row) =>
            [
              row.status === 'retired' ? 'table__row--retired' : '',
              row.id === selectedId ? 'table__row--selected' : '',
            ]
              .filter(Boolean)
              .join(' ') || undefined
          }
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

        {selected === null ? (
          <p className="empty">
            Choose a code to see what stands on it — and, before retiring anything, whether anything
            still does.
          </p>
        ) : (
          <RequirementDetailPanel key={selected.id} requirement={selected} canEdit={canEdit} />
        )}
      </div>
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
  if (usage.total === 0) return <span className="dim">unused</span>
  return (
    <span className="text-sm" title={usageBreakdown(requirement).join(' · ')}>
      {usage.total}
      {usage.requirementRules + usage.quotaRules + usage.conditionalRules > 0 && (
        <span className="dim"> · in the matrix</span>
      )}
    </span>
  )
}

/**
 * The usage breakdown, a line per kind.
 *
 * These counts come from the server across every matrix version, and they are the answer to the only
 * question worth asking before retiring something: is anything still standing on it. In the list
 * that is a hover; in the detail panel it gets a permanent home, because that is where the decision
 * is made.
 */
function usageBreakdown(requirement: RequirementDetail): readonly string[] {
  const { usage } = requirement
  return [
    `${usage.holdings} holdings`,
    `${usage.requirementRules} matrix rules`,
    `${usage.quotaRules} quota rules`,
    `${usage.conditionalRules} conditional rules`,
    `${usage.registerRecords} register records`,
  ]
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
    <div className="detail">
      <div>
        {/* The code above the title, in the accent: it is the business key, and the title is what it
            is called. */}
        <div className="mono detail__key">{requirement.code}</div>
        <h2 className="section__title" style={{ marginTop: 2 }}>
          {requirement.title}
        </h2>
      </div>

      <dl className="fact-grid">
        <div>
          <dt>Category</dt>
          <dd className="mono">{categoryLabel(requirement.category)}</dd>
        </div>
        <div>
          <dt>Status</dt>
          <dd>
            <span className={`chip chip--${requirement.status === 'active' ? 'good' : 'muted'}`}>
              {requirement.status}
            </span>
          </dd>
        </div>
        <div>
          <dt>Issuing authority</dt>
          <dd>{requirement.issuingAuthority ?? <span className="dim">—</span>}</dd>
        </div>
        <div>
          <dt>Used by</dt>
          <dd>
            {requirement.usage.total === 0 ? <span className="dim">unused</span> : requirement.usage.total}
          </dd>
        </div>
      </dl>

      <div className="tint-card tint-card--list">
        {usageBreakdown(requirement).map((line) => (
          <div key={line}>{line}</div>
        ))}
      </div>

      {requirement.notes !== null && <p className="callout callout--quiet">{requirement.notes}</p>}

      <AliasEditor requirement={requirement} canEdit={canEdit} />

      {canEdit && !editing && (
        <div className="detail__actions">
          <button type="button" className="button" onClick={() => setEditing(true)}>
            Edit
          </button>
        </div>
      )}
      {editing && <RequirementEditor existing={requirement} onDone={() => setEditing(false)} />}
    </div>
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
  const [adding, setAdding] = useState(false)

  return (
    <div>
      <h3 className="overline">Legacy titles</h3>

      {requirement.aliases.length === 0 && (
        <p className="section__note">No legacy titles map to this requirement.</p>
      )}
      {requirement.aliases.length > 0 && (
        <div className="tag-row">
          {requirement.aliases.map((entry) => (
            <span key={entry.id} className="chip chip--muted">
              {entry.alias}
              {canEdit && (
                <button
                  type="button"
                  className="link-action"
                  aria-label={`Remove the legacy title ${entry.alias}`}
                  disabled={remove.isPending}
                  onClick={() => remove.mutate({ requirementId: requirement.id, aliasId: entry.id })}
                >
                  ×
                </button>
              )}
            </span>
          ))}
        </div>
      )}

      <p className="panel__hint">How 445 historic free-text titles join the catalogue.</p>

      {canEdit && !adding && (
        <button
          type="button"
          className="button"
          style={{ marginTop: 8 }}
          onClick={() => setAdding(true)}
        >
          Add a legacy title
        </button>
      )}

      {canEdit && adding && (
        <form
          className="editor"
          style={{ marginTop: 8 }}
          onSubmit={(event) => {
            event.preventDefault()
            if (alias.trim() === '') return
            add.mutate(
              { requirementId: requirement.id, alias: alias.trim() },
              {
                onSuccess: () => {
                  setAlias('')
                  setAdding(false)
                },
              },
            )
          }}
        >
          <label className="field field--inline field--grow">
            <span className="field__label">Legacy title</span>
            <input
              className="input"
              value={alias}
              autoFocus
              placeholder="e.g. Working at Height"
              onChange={(event) => setAlias(event.target.value)}
            />
          </label>
          {/* The server refuses an alias that already resolves to another entry — one matching two
              requirements would send every document naming it to review permanently (§8 stage 3). */}
          {add.error !== null && <p className="editor__error">{errorText(add.error)}</p>}
          {remove.error !== null && <p className="editor__error">{errorText(remove.error)}</p>}
          <div className="editor__actions">
            <button
              type="submit"
              className="button button--primary"
              disabled={add.isPending || alias.trim() === ''}
            >
              Add
            </button>
            <button type="button" className="button" onClick={() => setAdding(false)}>
              Cancel
            </button>
          </div>
        </form>
      )}
    </div>
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
          Retiring this leaves {usageBreakdown(existing).join(', ')} pointing at it. Nothing breaks —
          retired entries still resolve — but the matrix will keep requiring it until the rules change.
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
