import { Fragment, useState } from 'react'
import {
  useAllHoldings,
  useClearMatrixCell,
  useCreateMatrixDraft,
  useDiscardMatrixDraft,
  useMatrixDiff,
  useMatrixVersion,
  useMatrixVersions,
  usePartnerships,
  usePeople,
  usePositions,
  usePublishMatrixVersion,
  useRequirements,
  useSetMatrixCell,
  useSwingEvaluation,
} from '../api/queries'
import {
  ApiError,
  type Holding,
  type MatrixVersionSummary,
  type Person,
  type Position,
  type Requirement,
} from '../api/client'
import { useHasRole, useToday } from '../api/session'
import { DataTable, type Column } from '../components/DataTable'
import { ErrorPanel } from '../components/ErrorPanel'
import { RequirementLabel } from '../components/RequirementLabel'
import { Spinner } from '../components/Spinner'
import { StateChip } from '../components/StateChip'
import { SwingSelector } from '../components/SwingSelector'
import { downloadCsv, toCsv } from '../domain/csv'
import { daysBetween, formatDate } from '../domain/dates'
import {
  EXPIRY_LEAD_DAYS_DEFAULT,
  EXPIRY_WINDOWS,
  REQUIREMENT_CATEGORIES,
  categoryLabel,
  expiryWindow,
  expiryWindowTone,
  matrixStatusTone,
  slotRef,
  type ExpiryWindowId,
} from '../domain/enums'
import { groupByRank } from '../domain/ranks'

/** §5.5 restricts publication to the Compliance Lead; drafting and editing follow it. */
const MATRIX_EDITORS = ['compliance_lead', 'system_administrator'] as const
const MATRIX_PUBLISHERS = ['compliance_lead'] as const

/** The levels the editor offers. Anything else is a footnote label, typed in. */
const COMMON_LEVELS = ['M', 'R'] as const

type Tab = 'editor' | 'diff' | 'generated' | 'crew'

/**
 * ADM-3 — the requirements matrix.
 *
 * ### The one thing to understand before changing this screen
 *
 * **A published version is immutable, and the UI must say so rather than discover it.** §5.5 makes a
 * matrix version the thing a past evaluation is reproducible against, so editing one in place would
 * silently rewrite history. The server refuses it with a 409; this screen never offers the edit at
 * all, and instead offers the thing the user actually wants — "draft from this version".
 *
 * ### Three views, because ADM-3 is really three jobs
 *
 *  - **Editor** — the version's own rules as positions × requirements, with partnership overrides on
 *    a separate pass. This is the CC sheet's replacement as an *editable* artefact.
 *  - **Diff** — §5.5's added / removed / level-changed between any two versions. It is also the
 *    confirmation step before publishing, because "what am I about to change for everyone" is the
 *    only question worth asking at that moment.
 *  - **Generated** — the matrix as *evaluated* for one swing: crew × requirements with cell states.
 *    This is the view that replaces reading a CC sheet, and every cell in it is the server's answer
 *    to §5.1 rather than anything computed here (AUTH-1). It is a pivot of the swing evaluation the
 *    planner already uses — deliberately not a second endpoint, because a second endpoint would be a
 *    second implementation of the engine in all but name.
 */
export function Matrix(): React.ReactNode {
  const versions = useMatrixVersions()
  const canEdit = useHasRole(...MATRIX_EDITORS)
  const canPublish = useHasRole(...MATRIX_PUBLISHERS)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  // The crew matrix opens first: it is the view the office lives on; the rules are one tab away.
  const [tab, setTab] = useState<Tab>('crew')

  if (versions.isPending) return <Spinner label="Loading matrix versions" />
  if (versions.error !== null) {
    return <ErrorPanel title="Could not load the matrix versions" error={versions.error} />
  }

  const rows = versions.data
  // Default to whatever is published: it is the version every compliance answer on every other
  // screen came from, so it is the one a reader arriving here is asking about.
  const selected =
    rows.find((row) => row.version.id === selectedId) ??
    rows.find((row) => row.version.status === 'published') ??
    rows[0] ??
    null

  return (
    <div className={tab === 'crew' ? 'screen screen--full' : 'screen'}>
      <header className="screen__header">
        <h1 className="screen__title">Matrix</h1>
        <p className="screen__subtitle">
          The requirement rules every compliance answer derives from. A published version is
          immutable, so a past evaluation stays reproducible.
        </p>
      </header>

      {/* Three tabs because ADM-3 is three jobs; the version list belongs to all of them, so it sits
          above rather than inside one. */}
      <nav className="tabs" aria-label="Matrix views">
        <TabButton current={tab} value="editor" label="Cell editor" onSelect={setTab} />
        <TabButton current={tab} value="diff" label="Diff" onSelect={setTab} />
        <TabButton current={tab} value="generated" label="Generated per swing" onSelect={setTab} />
        <TabButton current={tab} value="crew" label="Crew matrix" onSelect={setTab} />
      </nav>

      {/* The crew matrix reads holdings, not a matrix version, so it renders without the version
          list — a reader arriving for "who holds what" should not scroll past versions to get it. */}
      {tab === 'crew' && <CrewMatrix />}

      {tab !== 'crew' && (
        <VersionList
          rows={rows}
          selectedId={selected?.version.id ?? null}
          canEdit={canEdit}
          onSelect={(id) => setSelectedId(id)}
        />
      )}

      {tab !== 'crew' && selected !== null && (
        <>
          {tab === 'editor' && (
            <CellEditor summary={selected} versions={rows} canEdit={canEdit} canPublish={canPublish} />
          )}
          {tab === 'diff' && <DiffView versions={rows} toVersionId={selected.version.id} />}
          {tab === 'generated' && <GeneratedMatrix />}
        </>
      )}
    </div>
  )
}

function TabButton({
  current,
  value,
  label,
  onSelect,
}: {
  current: Tab
  value: Tab
  label: string
  onSelect: (tab: Tab) => void
}): React.ReactNode {
  return (
    <button
      type="button"
      className={current === value ? 'tabs__tab tabs__tab--active' : 'tabs__tab'}
      aria-current={current === value}
      onClick={() => onSelect(value)}
    >
      {label}
    </button>
  )
}

// ---------------------------------------------------------------------------
// The version list
// ---------------------------------------------------------------------------

function VersionList({
  rows,
  selectedId,
  canEdit,
  onSelect,
}: {
  rows: readonly MatrixVersionSummary[]
  selectedId: number | null
  canEdit: boolean
  onSelect: (id: number) => void
}): React.ReactNode {
  const createDraft = useCreateMatrixDraft()
  const today = useToday()
  const [drafting, setDrafting] = useState<MatrixVersionSummary | null>(null)
  const [label, setLabel] = useState('')

  const columns: Column<MatrixVersionSummary>[] = [
    {
      id: 'label',
      header: 'Version',
      accessorFn: (row) => row.version.label,
      cell: ({ row }) => <span className="mono">{row.original.version.label}</span>,
    },
    {
      id: 'status',
      header: 'Status',
      accessorFn: (row) => row.version.status,
      cell: ({ row }) => <VersionStatusChip status={row.original.version.status} />,
    },
    {
      id: 'effectiveFrom',
      header: 'Effective from',
      accessorFn: (row) => row.version.effectiveFrom ?? '',
      cell: ({ row }) =>
        row.original.version.effectiveFrom === null ? (
          <span className="dim">—</span>
        ) : (
          formatDate(row.original.version.effectiveFrom)
        ),
    },
    { id: 'rules', header: 'Rules', accessorFn: (row) => row.requirementRuleCount },
    { id: 'quotas', header: 'Quotas', accessorFn: (row) => row.quotaRuleCount },
    { id: 'conditionals', header: 'Conditional', accessorFn: (row) => row.conditionalRuleCount },
    {
      id: 'publishedBy',
      header: 'Published by',
      accessorFn: (row) => row.version.publishedBy ?? '',
      cell: ({ row }) => row.original.version.publishedBy ?? <span className="dim">—</span>,
    },
    {
      id: 'actions',
      header: '',
      enableSorting: false,
      accessorFn: () => '',
      // Only the one action a published row can offer. Editing, discarding and publishing act on the
      // *selected* version and live on the editor's own controls row below, where the thing they act
      // on is on screen — an in-row Publish button next to seven other rows invites publishing the
      // wrong one.
      cell: ({ row }) =>
        canEdit ? (
          <button
            type="button"
            className="button button--quiet"
            onClick={(event) => {
              event.stopPropagation()
              setDrafting(row.original)
              setLabel(suggestLabel(row.original.version.label, today))
            }}
          >
            Draft from this
          </button>
        ) : null,
    },
  ]

  return (
    <section className="section">
      <DataTable
        rows={rows}
        columns={columns}
        filterPlaceholder="Filter versions"
        empty="No matrix versions exist yet. Create a draft to build the first one."
        onRowClick={(row) => onSelect(row.version.id)}
        rowClassName={(row) => (row.version.id === selectedId ? 'table__row--selected' : undefined)}
        csv={{
          filename: 'matrix-versions.csv',
          columns: [
            { header: 'Label', value: (row) => row.version.label },
            { header: 'Status', value: (row) => row.version.status },
            { header: 'Effective from', value: (row) => row.version.effectiveFrom },
            { header: 'Published by', value: (row) => row.version.publishedBy },
            { header: 'Published at', value: (row) => row.version.publishedAt },
            { header: 'Requirement rules', value: (row) => row.requirementRuleCount },
            { header: 'Quota rules', value: (row) => row.quotaRuleCount },
            { header: 'Conditional rules', value: (row) => row.conditionalRuleCount },
            { header: 'Notes', value: (row) => row.version.notes },
          ],
        }}
      />

      {canEdit && rows.length === 0 && (
        <button
          type="button"
          className="button button--primary"
          onClick={() => {
            setDrafting(null)
            setLabel(suggestLabel(null, today))
          }}
        >
          Create the first draft
        </button>
      )}

      {(drafting !== null || label !== '') && canEdit && (
        <form
          className="editor"
          onSubmit={(event) => {
            event.preventDefault()
            createDraft.mutate(
              {
                label,
                copyFromVersionId: drafting?.version.id ?? null,
                notes: null,
              },
              {
                onSuccess: (created) => {
                  setDrafting(null)
                  setLabel('')
                  onSelect(created.id)
                },
              },
            )
          }}
        >
          <label className="field field--inline field--grow">
            <span className="field__label">
              {drafting === null
                ? 'New empty draft, labelled'
                : `Draft copied from ${drafting.version.label}, labelled`}
            </span>
            <input className="input" value={label} onChange={(event) => setLabel(event.target.value)} />
          </label>
          <div className="editor__actions">
            <button
              type="submit"
              className="button button--primary"
              disabled={createDraft.isPending || label.trim() === ''}
            >
              {createDraft.isPending ? 'Creating…' : 'Create draft'}
            </button>
            <button
              type="button"
              className="button"
              onClick={() => {
                setDrafting(null)
                setLabel('')
              }}
            >
              Cancel
            </button>
          </div>
          {createDraft.error !== null && (
            <p className="editor__error">{errorText(createDraft.error)}</p>
          )}
        </form>
      )}
    </section>
  )
}

function VersionStatusChip({ status }: { status: string }): React.ReactNode {
  return <span className={`chip chip--${matrixStatusTone(status)}`}>{status}</span>
}

/**
 * "yesterday's label, plus one" — a starting point, not a scheme.
 *
 * Labels are free text and the server only requires uniqueness, so this guesses rather than
 * enforces: `dev-2026.1` suggests `dev-2026.2`, and anything unrecognised falls back to the date.
 */
export function suggestLabel(from: string | null, today: string): string {
  if (from === null) return `matrix-${today}`
  const match = /^(.*?)(\d+)$/.exec(from)
  if (match === null) return `${from}-copy`
  return `${match[1]}${Number(match[2]) + 1}`
}

/**
 * The publish step, with the diff in front of it.
 *
 * §6 asks for "publish with confirmation", and the confirmation that means something is not "are you
 * sure" — it is what is about to change and for whom. Publishing changes the answer to every
 * compliance question in the system at once, so the diff against the version being superseded is
 * shown here rather than being one tab away.
 */
function PublishConfirmation({
  summary,
  currentlyPublished,
  onCancel,
  onPublish,
  pending,
  error,
}: {
  summary: MatrixVersionSummary
  currentlyPublished: MatrixVersionSummary | null
  onCancel: () => void
  onPublish: (effectiveFrom: string) => void
  pending: boolean
  error: unknown
}): React.ReactNode {
  const today = useToday()
  const [effectiveFrom, setEffectiveFrom] = useState(today)
  const diff = useMatrixDiff(currentlyPublished?.version.id ?? null, summary.version.id)

  return (
    <div className="panel">
      <p className="panel__title">Publish {summary.version.label}?</p>
      <p className="panel__detail">
        {currentlyPublished === null
          ? 'This will be the first published version. Until it is published, no swing can be evaluated at all.'
          : `${currentlyPublished.version.label} will be superseded. Every compliance answer in the ` +
            'system — dashboards, planner, gap reports — will come from this version from its ' +
            'effective date onward.'}
      </p>

      {currentlyPublished !== null && (
        <div className="section">
          <h3 className="section__title">What changes</h3>
          {diff.isPending && <Spinner label="Comparing versions" />}
          {diff.data !== undefined && <DiffSummary diff={diff.data} />}
        </div>
      )}

      <form
        className="editor"
        onSubmit={(event) => {
          event.preventDefault()
          onPublish(effectiveFrom)
        }}
      >
        <label className="field field--inline">
          <span className="field__label">Effective from</span>
          <input
            className="input"
            type="date"
            value={effectiveFrom}
            onChange={(event) => setEffectiveFrom(event.target.value)}
          />
        </label>
        <div className="editor__actions">
          <button type="submit" className="button button--primary" disabled={pending}>
            {pending ? 'Publishing…' : 'Publish'}
          </button>
          <button type="button" className="button" onClick={onCancel}>
            Cancel
          </button>
        </div>
        {error !== null && <p className="editor__error">{errorText(error)}</p>}
      </form>
    </div>
  )
}

// ---------------------------------------------------------------------------
// The cell editor
// ---------------------------------------------------------------------------

/**
 * The version's rules as a grid of requirements × positions.
 *
 * Requirements down and positions across, not the other way about: there are nine positions and
 * several hundred catalogue entries, so this is the orientation that fits on a screen and scrolls in
 * the direction people scroll.
 *
 * The **partnership selector** is the whole of §6's "partnership overrides". Choosing a partnership
 * shows that partnership's *effective* level with its override marked, and editing then writes an
 * override rather than the base rule. That is the only arrangement in which the two are visibly
 * different things, which they are: an override set to blank says "this partnership does not require
 * it", and no override at all says "follow the base rule".
 */
function CellEditor({
  summary,
  versions,
  canEdit,
  canPublish,
}: {
  summary: MatrixVersionSummary
  versions: readonly MatrixVersionSummary[]
  canEdit: boolean
  canPublish: boolean
}): React.ReactNode {
  const detail = useMatrixVersion(summary.version.id)
  const requirements = useRequirements()
  const positions = usePositions()
  const partnerships = usePartnerships()
  const setCell = useSetMatrixCell()
  const clearCell = useClearMatrixCell()
  const discard = useDiscardMatrixDraft()
  const publish = usePublishMatrixVersion()

  const [partnershipId, setPartnershipId] = useState<number | null>(null)
  const [showOnlyUsed, setShowOnlyUsed] = useState(true)
  const [publishing, setPublishing] = useState(false)
  // Discard asks first (#20): it deletes every edit in the draft, and it sits directly beside
  // Publish — the one place a mis-click costs a morning's work.
  const [confirmingDiscard, setConfirmingDiscard] = useState(false)

  if (detail.isPending || requirements.isPending || positions.isPending) {
    return <Spinner label="Loading the matrix" />
  }
  if (detail.error !== null) {
    return <ErrorPanel title="Could not load this version" error={detail.error} />
  }
  if (requirements.error !== null || positions.error !== null) {
    return (
      <ErrorPanel
        title="Could not load the catalogue"
        error={requirements.error ?? positions.error}
      />
    )
  }

  const rules = detail.data.rules
  const editable = summary.version.editable && canEdit

  /** The base level and the override for one cell, kept apart — see the doc comment above. */
  function levelsFor(positionId: number, requirementId: number) {
    const base = rules.find(
      (rule) =>
        rule.partnershipId === null &&
        rule.positionId === positionId &&
        rule.requirementId === requirementId,
    )
    const override =
      partnershipId === null
        ? undefined
        : rules.find(
            (rule) =>
              rule.partnershipId === partnershipId &&
              rule.positionId === positionId &&
              rule.requirementId === requirementId,
          )
    return { base, override }
  }

  const usedRequirementIds = new Set(rules.map((rule) => rule.requirementId))
  const visibleRequirements = [...requirements.data]
    .filter((requirement) => !showOnlyUsed || usedRequirementIds.has(requirement.id))
    .sort((a, b) => a.code.localeCompare(b.code))
  const visiblePositions = [...positions.data].sort((a, b) => a.name.localeCompare(b.name))

  return (
    <section className="section">
      <div className="selector">
        {/*
         * The partnership selector is the whole of §6's "partnership overrides", and it is a
         * separate pass rather than a column because the two are genuinely different things: an
         * override set to blank says "this partnership does not require it", and no override at all
         * says "follow the base rule".
         */}
        <label className="field field--inline" style={{ width: 260 }}>
          <span className="field__label">Rules for</span>
          <select
            className="input"
            value={partnershipId ?? ''}
            onChange={(event) =>
              setPartnershipId(event.target.value === '' ? null : Number(event.target.value))
            }
          >
            <option value="">Base rules (every partnership)</option>
            {(partnerships.data ?? []).map((option) => (
              <option key={option.id} value={option.id}>
                {option.abbrev} — overrides
              </option>
            ))}
          </select>
        </label>

        <label className="check check--box">
          <input
            type="checkbox"
            checked={showOnlyUsed}
            onChange={(event) => setShowOnlyUsed(event.target.checked)}
          />
          <span className="dot" />
          Only requirements this version uses
        </label>

        <div className="row-actions push">
          <button
            type="button"
            className="button"
            onClick={() =>
              downloadCsv(
                `matrix-${summary.version.label}.csv`,
                matrixCsv(visibleRequirements, visiblePositions, rules),
              )
            }
          >
            Export CSV
          </button>
          {canEdit && summary.version.editable && !confirmingDiscard && (
            <button
              type="button"
              className="button"
              disabled={discard.isPending}
              onClick={() => setConfirmingDiscard(true)}
            >
              Discard…
            </button>
          )}
          {canEdit && summary.version.editable && confirmingDiscard && (
            <>
              <span className="dim">Deletes every edit in {summary.version.label}.</span>
              <button
                type="button"
                className="button"
                disabled={discard.isPending}
                onClick={() => {
                  discard.mutate(summary.version.id)
                  setConfirmingDiscard(false)
                }}
              >
                Discard the draft
              </button>
              <button type="button" className="button" onClick={() => setConfirmingDiscard(false)}>
                Keep editing
              </button>
            </>
          )}
          {canPublish && summary.version.editable && (
            <button
              type="button"
              className="button button--primary"
              onClick={() => setPublishing(true)}
            >
              Publish {summary.version.label}
            </button>
          )}
        </div>
      </div>

      {discard.error !== null && <p className="editor__error">{errorText(discard.error)}</p>}

      {publishing && (
        <PublishConfirmation
          summary={summary}
          currentlyPublished={versions.find((row) => row.version.status === 'published') ?? null}
          onCancel={() => setPublishing(false)}
          onPublish={(effectiveFrom) =>
            publish.mutate(
              { versionId: summary.version.id, effectiveFrom },
              { onSuccess: () => setPublishing(false) },
            )
          }
          pending={publish.isPending}
          error={publish.error}
        />
      )}

      {/*
       * The screen never offers an edit the server would refuse. A published version is immutable
       * (§5.5) — that is what makes a past evaluation reproducible — so this says why and points at
       * the thing the reader actually wants. A UI that discovered the 409 by trying would be a dead
       * end wearing an error message.
       */}
      {!summary.version.editable && (
        <p className="note">
          {summary.version.label} is {summary.version.status} and cannot be edited. A published
          version is immutable so that a past evaluation stays reproducible — use
          <strong> Draft from this</strong> above and edit the draft.
        </p>
      )}

      {visibleRequirements.length === 0 && (
        <p className="empty">
          This version has no rules yet. Turn off the filter above to see the whole catalogue and set
          a level.
        </p>
      )}

      {visibleRequirements.length > 0 && (
        <div className="table-block">
          <div className="table-scroll">
          <table className="table matrix-grid">
            <thead>
              <tr>
                <th scope="col">Requirement</th>
                {visiblePositions.map((position) => (
                  <th key={position.id} scope="col">
                    {position.name}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {visibleRequirements.map((requirement) => (
                <tr key={requirement.id}>
                  <th scope="row">
                    <RequirementLabel code={requirement.code} title={requirement.title} />
                  </th>
                  {visiblePositions.map((position) => {
                    const { base, override } = levelsFor(position.id, requirement.id)
                    return (
                      <td key={position.id}>
                        <LevelCell
                          base={base?.level ?? null}
                          override={override === undefined ? undefined : override.level}
                          editable={editable}
                          overriding={partnershipId !== null}
                          onSet={(level) =>
                            setCell.mutate({
                              versionId: summary.version.id,
                              body: {
                                positionId: position.id,
                                requirementId: requirement.id,
                                partnershipId,
                                level,
                              },
                            })
                          }
                          onClear={() =>
                            clearCell.mutate({
                              versionId: summary.version.id,
                              cell: {
                                positionId: position.id,
                                requirementId: requirement.id,
                                ...(partnershipId === null ? {} : { partnershipId }),
                              },
                            })
                          }
                        />
                      </td>
                    )
                  })}
                </tr>
              ))}
            </tbody>
          </table>
          </div>

          {/*
           * The legend says out loud the one distinction this grid turns on. "— no rule" means
           * nothing applies (or, under a partnership, follow the base rule); "not required" is an
           * override that positively removes the requirement. Collapsing the two was a real bug in
           * the first version of the diff view, and they must never be collapsed here either.
           */}
          <p className="matrix-legend">
            <span className="level level--none">—</span> no rule ·{' '}
            <strong>not required</strong> a partnership override that removes the rule · M8 / M9 / Mˣ
            carry a quota footnote
          </p>
        </div>
      )}

      {setCell.error !== null && <p className="editor__error">{errorText(setCell.error)}</p>}
      {clearCell.error !== null && <p className="editor__error">{errorText(clearCell.error)}</p>}

      <QuotaAndConditionalSummary summary={summary} />
    </section>
  )
}

/**
 * One cell: a `select` when editable, a chip when not.
 *
 * A free-text option is offered alongside `M` and `R` because footnote labels are **data, not code**
 * (§4.2) — `M9`, `Mˣ` and whatever the next version invents are all valid levels, and a fixed list
 * would quietly make the matrix unable to express the thing the source workbook already expresses.
 */
/** Select-option sentinel for a blank override — never sent to the server as a level. */
const NOT_REQUIRED_OPTION = '__not_required__'

function LevelCell({
  base,
  override,
  editable,
  overriding,
  onSet,
  onClear,
}: {
  base: string | null
  /** `undefined` = no override row; `''` = an override that removes the requirement. */
  override: string | undefined
  editable: boolean
  overriding: boolean
  onSet: (level: string) => void
  onClear: () => void
}): React.ReactNode {
  const [typing, setTyping] = useState(false)
  const [typed, setTyped] = useState('')

  const effective = overriding && override !== undefined ? override : base
  const overridden = overriding && override !== undefined

  if (!editable) {
    return <LevelChip level={effective} overridden={overridden} />
  }

  if (typing) {
    return (
      <form
        onSubmit={(event) => {
          event.preventDefault()
          if (typed.trim() !== '') onSet(typed.trim())
          setTyping(false)
          setTyped('')
        }}
      >
        <input
          className="input input--level"
          autoFocus
          value={typed}
          placeholder="M9"
          aria-label="Footnote label"
          onChange={(event) => setTyped(event.target.value)}
          onBlur={() => setTyping(false)}
        />
      </form>
    )
  }

  const current = effective ?? ''
  // A blank override is "not required" — a positive statement the server accepts and the diff
  // renders apart from "no rule". It gets its own option so it can be *created*, not only read:
  // routing it through the blank option would make it indistinguishable from clearing, which is
  // the opposite edit (clear = follow the base rule again).
  const selectValue =
    overridden && override === ''
      ? NOT_REQUIRED_OPTION
      : COMMON_LEVELS.includes(current as (typeof COMMON_LEVELS)[number])
        ? current
        : current === ''
          ? ''
          : 'other'
  return (
    <select
      className={overridden ? 'input input--level input--overridden' : 'input input--level'}
      value={selectValue}
      title={overridden ? `Overrides the base rule (${base ?? 'none'})` : undefined}
      onChange={(event) => {
        const value = event.target.value
        if (value === '') {
          // Clearing a base rule removes it; clearing an override restores the base rule.
          if (effective !== null || overridden) onClear()
        } else if (value === NOT_REQUIRED_OPTION) {
          onSet('')
        } else if (value === 'other') {
          setTyping(true)
          setTyped(current === '' ? '' : current)
        } else {
          onSet(value)
        }
      }}
    >
      <option value="">{overriding ? '— base —' : '—'}</option>
      {overriding && <option value={NOT_REQUIRED_OPTION}>not required</option>}
      {COMMON_LEVELS.map((level) => (
        <option key={level} value={level}>
          {level}
        </option>
      ))}
      <option value="other">{current !== '' && !COMMON_LEVELS.includes(current as never) ? current : 'footnote…'}</option>
    </select>
  )
}

/**
 * A level, as a token.
 *
 * Four presentations, and the last two must never merge: `M`/`M8`/`M9`/`Mˣ` are mandatory and take
 * the accent tint, `R` is recommended and takes the neutral one, an **em dash** is no rule at all,
 * and **"not required"** is a partnership override that positively removes the rule. See the legend
 * under the grid.
 */
function LevelChip({
  level,
  overridden,
}: {
  level: string | null
  overridden: boolean
}): React.ReactNode {
  if (level === null) return <span className="level level--none">—</span>
  if (level === '') {
    return (
      <span
        className="level level--not-required"
        title="A partnership override that removes the requirement"
      >
        not required
      </span>
    )
  }
  return (
    <span
      className={[
        'level',
        level === 'R' ? 'level--recommended' : '',
        overridden ? 'level--overridden' : '',
      ]
        .filter(Boolean)
        .join(' ')}
      title={
        overridden
          ? 'Overrides the base rule for this partnership'
          : level === 'R'
            ? 'Recommended — never a gap'
            : undefined
      }
    >
      {level}
    </span>
  )
}

/**
 * Quota and conditional rules, read-only.
 *
 * §6's ADM-3 asks for the cell editor and not for a quota-rule editor, and there is a reason not to
 * invent one: a quota footnote's *meaning* is a record in `quota_rule` and its *appearance* is a
 * level in the grid above, so an editor for one without the other would let the two disagree —
 * a cell reading `M9` with no `M9` quota behind it evaluates as an ordinary footnote and silently
 * stops being a quota. Showing them here is what makes that disagreement visible. Editing them is
 * scope for the same pass that resolves O-7.
 */
function QuotaAndConditionalSummary({
  summary,
}: {
  summary: MatrixVersionSummary
}): React.ReactNode {
  const detail = useMatrixVersion(summary.version.id)
  const requirements = useRequirements()
  const positions = usePositions()

  if (detail.data === undefined) return null
  const code = (id: number) =>
    requirements.data?.find((requirement) => requirement.id === id)?.code ?? `#${id}`
  const positionName = (id: number) =>
    positions.data?.find((position) => position.id === id)?.name ?? `#${id}`

  return (
    <div className="rule-cards">
      <div className="panel">
        <h3 className="section__title section__title--panel">Quota rules</h3>
        {detail.data.quotas.length === 0 && (
          <p className="section__note">This version defines no quotas.</p>
        )}
        {detail.data.quotas.length > 0 && (
          <div className="rule-list">
            {detail.data.quotas.map((quota) => (
              <div key={quota.id}>
                <span className="mono">{quota.footnote}</span> — at least {quota.minCount} with{' '}
                <span className="mono">{code(quota.requirementId)}</span> per {quota.scope}
                {quota.positionIds.length > 0 && (
                  <span className="muted">
                    {' '}
                    · counting {quota.positionIds.map(positionName).join(', ')}
                  </span>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="panel">
        <h3 className="section__title section__title--panel">Conditional rules</h3>
        {detail.data.conditionals.length === 0 && (
          <p className="section__note">This version defines no one-of sets or dependent rules.</p>
        )}
        {detail.data.conditionals.length > 0 && (
          <div className="rule-list">
            {detail.data.conditionals.map((rule) => (
              <div key={rule.id}>
                {rule.label !== null && <span className="mono">{rule.label} </span>}
                {rule.kind === 'one_of' ? 'One of' : 'Dependent'} —{' '}
                {positionName(rule.positionId)}
                {rule.kind === 'dependent' && rule.requirementId !== null && (
                  <>
                    {' '}
                    · requires <span className="mono">{code(rule.requirementId)}</span>
                  </>
                )}
                <span className="muted">
                  {' '}
                  ·{' '}
                  {rule.members
                    .map((member) => `${code(member.requirementId)} (${member.role})`)
                    .join(', ')}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

/**
 * The matrix as an audit-shaped CSV: one row per (requirement, position) with the base level and
 * every partnership override alongside.
 *
 * "Audit-shaped" is §6's word and Q26/O-7's open question, so the shape here is the conservative
 * reading: one row per rule with everything needed to reconstruct the cell, rather than a
 * spreadsheet-shaped grid that loses the base/override distinction.
 */
function matrixCsv(
  requirements: readonly Requirement[],
  positions: readonly Position[],
  rules: readonly { partnershipId: number | null; positionId: number; requirementId: number; level: string }[],
): string {
  const rows = rules.map((rule) => ({
    code: requirements.find((r) => r.id === rule.requirementId)?.code ?? `#${rule.requirementId}`,
    title: requirements.find((r) => r.id === rule.requirementId)?.title ?? '',
    position: positions.find((p) => p.id === rule.positionId)?.name ?? `#${rule.positionId}`,
    scope: rule.partnershipId === null ? '*' : String(rule.partnershipId),
    level: rule.level,
  }))
  return toCsv(rows, [
    { header: 'Requirement code', value: (row) => row.code },
    { header: 'Requirement title', value: (row) => row.title },
    { header: 'Position', value: (row) => row.position },
    { header: 'Partnership (* = base rule)', value: (row) => row.scope },
    { header: 'Level', value: (row) => row.level },
  ])
}

// ---------------------------------------------------------------------------
// The §5.5 diff
// ---------------------------------------------------------------------------

function DiffView({
  versions,
  toVersionId,
}: {
  versions: readonly MatrixVersionSummary[]
  toVersionId: number
}): React.ReactNode {
  // Default to comparing against the currently published version: "what does my draft change" is
  // the question, nearly always.
  const publishedId = versions.find((row) => row.version.status === 'published')?.version.id ?? null
  const [fromId, setFromId] = useState<number | null>(
    publishedId === toVersionId ? (versions.find((row) => row.version.id !== toVersionId)?.version.id ?? null) : publishedId,
  )
  const [toId, setToId] = useState<number>(toVersionId)
  const diff = useMatrixDiff(fromId, toId)

  return (
    <section className="section">
      <div className="selector">
        <label className="field field--inline">
          <span className="field__label">From</span>
          <select
            className="input"
            value={fromId ?? ''}
            onChange={(event) => setFromId(event.target.value === '' ? null : Number(event.target.value))}
          >
            <option value="">Select…</option>
            {versions.map((row) => (
              <option key={row.version.id} value={row.version.id}>
                {row.version.label} ({row.version.status})
              </option>
            ))}
          </select>
        </label>
        <label className="field field--inline">
          <span className="field__label">To</span>
          <select
            className="input"
            value={toId}
            onChange={(event) => setToId(Number(event.target.value))}
          >
            {versions.map((row) => (
              <option key={row.version.id} value={row.version.id}>
                {row.version.label} ({row.version.status})
              </option>
            ))}
          </select>
        </label>
      </div>

      {fromId === null && <p className="empty">Pick a version to compare against.</p>}
      {diff.isPending && fromId !== null && <Spinner label="Comparing versions" />}
      {diff.error !== null && <ErrorPanel title="Could not compare these versions" error={diff.error} />}
      {diff.data !== undefined && <DiffSummary diff={diff.data} detailed />}
    </section>
  )
}

function DiffSummary({
  diff,
  detailed = false,
}: {
  diff: {
    rules: readonly {
      partnershipId: number | null
      positionId: number
      requirementId: number
      from: string | null
      to: string | null
      kind: string
    }[]
    quotas: readonly {
      footnote: string
      requirementId: number
      scope: string
      fromMin: number | null
      toMin: number | null
      kind: string
    }[]
    empty: boolean
  }
  detailed?: boolean
}): React.ReactNode {
  const requirements = useRequirements()
  const positions = usePositions()
  const partnerships = usePartnerships()

  if (diff.empty) {
    return <p className="empty">These two versions carry identical rules and quotas.</p>
  }

  const code = (id: number) =>
    requirements.data?.find((requirement) => requirement.id === id)?.code ?? `#${id}`
  const positionName = (id: number) =>
    positions.data?.find((position) => position.id === id)?.name ?? `#${id}`
  const scopeName = (id: number | null) =>
    id === null ? '*' : (partnerships.data?.find((p) => p.id === id)?.abbrev ?? `#${id}`)

  const counts = countByKind(diff.rules.map((rule) => rule.kind))

  return (
    <>
      <div className="counts counts--inline">
        <span className="counts__item">
          <span className="counts__value">{counts.added}</span> added
        </span>
        <span className="counts__item">
          <span className="counts__value">{counts.removed}</span> removed
        </span>
        <span className="counts__item">
          <span className="counts__value">{counts.level_changed}</span> level changed
        </span>
        {diff.quotas.length > 0 && (
          <span className="counts__item">
            <span className="counts__value">{diff.quotas.length}</span> quota changes
          </span>
        )}
      </div>

      {detailed && (
        <>
          <div className="table-block">
            <div className="table-scroll">
              <table className="table" style={{ minWidth: 720 }}>
                <thead>
                  <tr>
                    <th scope="col">Change</th>
                    <th scope="col">Scope</th>
                    <th scope="col">Position</th>
                    <th scope="col">Requirement</th>
                    <th scope="col">From</th>
                    <th scope="col">To</th>
                  </tr>
                </thead>
                <tbody>
                  {diff.rules.map((rule, index) => (
                    <tr
                      key={`${rule.positionId}-${rule.requirementId}-${rule.partnershipId ?? 'base'}-${index}`}
                    >
                      <td>
                        <span className={`chip chip--${diffTone(rule.kind)}`}>
                          {rule.kind.replace('_', ' ')}
                        </span>
                      </td>
                      <td className="mono">{scopeName(rule.partnershipId)}</td>
                      <td>{positionName(rule.positionId)}</td>
                      <td className="mono">{code(rule.requirementId)}</td>
                      <td>
                        <LevelChip level={rule.from} overridden={false} />
                      </td>
                      <td>
                        <LevelChip level={rule.to} overridden={false} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {diff.quotas.length > 0 && (
            <>
              <h3 className="section__title section__title--panel">Quota changes</h3>
              <ul className="list-plain list-plain--tight">
                {diff.quotas.map((quota, index) => (
                  <li
                    key={`${quota.footnote}-${quota.requirementId}-${index}`}
                    className="tint-card tag-row"
                  >
                    <span className={`chip chip--${diffTone(quota.kind)}`}>
                      {quota.kind.replace('_', ' ')}
                    </span>
                    <span>
                      <span className="mono">{quota.footnote}</span> ·{' '}
                      <span className="mono">{code(quota.requirementId)}</span> per {quota.scope} ·{' '}
                      {quota.fromMin ?? '—'} → {quota.toMin ?? '—'}
                    </span>
                  </li>
                ))}
              </ul>
            </>
          )}

          <button
            type="button"
            className="button self-start"
            onClick={() =>
              downloadCsv(
                'matrix-diff.csv',
                toCsv(diff.rules, [
                  { header: 'Change', value: (row) => row.kind },
                  { header: 'Partnership (* = base)', value: (row) => scopeName(row.partnershipId) },
                  { header: 'Position', value: (row) => positionName(row.positionId) },
                  { header: 'Requirement', value: (row) => code(row.requirementId) },
                  { header: 'From', value: (row) => row.from },
                  { header: 'To', value: (row) => row.to },
                ]),
              )
            }
          >
            Export CSV
          </button>
        </>
      )}
    </>
  )
}

function countByKind(kinds: readonly string[]): Record<'added' | 'removed' | 'level_changed', number> {
  return {
    added: kinds.filter((kind) => kind === 'added').length,
    removed: kinds.filter((kind) => kind === 'removed').length,
    level_changed: kinds.filter((kind) => kind === 'level_changed').length,
  }
}

function diffTone(kind: string): string {
  return kind === 'added' ? 'good' : kind === 'removed' ? 'critical' : 'caution'
}

// ---------------------------------------------------------------------------
// The generated matrix — the CC sheet's replacement
// ---------------------------------------------------------------------------

/**
 * The matrix as **evaluated** for one swing: crew down, requirements across, a cell state in each.
 *
 * This is the view the two source workbooks' CC sheets were, and every cell in it is the server's
 * §5.1 answer rendered as received. Nothing here decides what a state is (AUTH-1) — it is a pivot of
 * the same `/swings/{pt}/{cc}/evaluation` the planner uses, from crew × cells into a grid.
 *
 * Requirement columns are only those the evaluation actually returned cells for, so the grid is as
 * wide as the matrix makes it rather than as wide as the catalogue.
 */
function GeneratedMatrix(): React.ReactNode {
  const [partnership, setPartnership] = useState<string | null>(null)
  const [cc, setCc] = useState<string | null>(null)
  const evaluation = useSwingEvaluation(partnership, cc)
  const requirements = useRequirements()

  return (
    <section className="section">
      <SwingSelector
        partnership={partnership}
        cc={cc}
        onChange={(nextPartnership, nextCc) => {
          setPartnership(nextPartnership)
          setCc(nextCc)
        }}
      />

      {(partnership === null || cc === null) && (
        <p className="empty">Choose a partnership and crew change to generate the matrix.</p>
      )}
      {evaluation.isPending && partnership !== null && cc !== null && (
        <Spinner label="Evaluating the swing" />
      )}
      {evaluation.error !== null && (
        <ErrorPanel title="Could not evaluate this swing" error={evaluation.error} />
      )}

      {evaluation.data !== undefined && (
        <GeneratedGrid
          assignments={evaluation.data.assignments}
          requirements={requirements.data ?? []}
          filename={`matrix-${partnership}-${cc}.csv`}
        />
      )}
    </section>
  )
}

function GeneratedGrid({
  assignments,
  requirements,
  filename,
}: {
  assignments: readonly {
    personId: number
    sam: string
    name: string
    slotRef: number
    evaluation: { rollUp: string; cells: readonly { requirementId: number; level: string; state: string }[] }
  }[]
  requirements: readonly Requirement[]
  filename: string
}): React.ReactNode {
  if (assignments.length === 0) {
    return <p className="empty">Nobody is assigned to this swing yet.</p>
  }

  // Column order follows the catalogue's code order, over the requirements the engine returned.
  const columnIds = [...new Set(assignments.flatMap((a) => a.evaluation.cells.map((c) => c.requirementId)))]
  const columns = columnIds
    .map((id) => requirements.find((requirement) => requirement.id === id) ?? null)
    .filter((requirement): requirement is Requirement => requirement !== null)
    .sort((a, b) => a.code.localeCompare(b.code))

  return (
    <>
      <div className="table-block">
        <div className="table-scroll">
          <table className="table matrix-grid">
            <thead>
              <tr>
                <th scope="col">Crew</th>
                <th scope="col">Slot</th>
                <th scope="col">Roll-up</th>
                {columns.map((requirement) => (
                  <th key={requirement.id} scope="col" title={requirement.title}>
                    <span className="mono">{requirement.code}</span>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {assignments.map((assignment) => (
                <tr key={`${assignment.personId}-${assignment.slotRef}`}>
                  <th scope="row">
                    {assignment.name} <span className="mono muted">{assignment.sam}</span>
                  </th>
                  <td className="mono">{slotRef(assignment.slotRef)}</td>
                  <td>
                    <StateChip state={assignment.evaluation.rollUp} />
                  </td>
                  {columns.map((requirement) => {
                    const cell = assignment.evaluation.cells.find(
                      (candidate) => candidate.requirementId === requirement.id,
                    )
                    return (
                      <td key={requirement.id}>
                        {cell === undefined ? (
                          <span className="dim">—</span>
                        ) : (
                          <StateChip state={cell.state} title={`${cell.level} — ${cell.state}`} />
                        )}
                      </td>
                    )
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <button
        type="button"
        className="button self-start"
        onClick={() => {
          const rows = assignments.flatMap((assignment) =>
            assignment.evaluation.cells.map((cell) => ({
              sam: assignment.sam,
              name: assignment.name,
              slotRef: assignment.slotRef,
              rollUp: assignment.evaluation.rollUp,
              code: requirements.find((r) => r.id === cell.requirementId)?.code ?? `#${cell.requirementId}`,
              level: cell.level,
              state: cell.state,
            })),
          )
          downloadCsv(
            filename,
            toCsv(rows, [
              { header: 'Sam #', value: (row) => row.sam },
              { header: 'Name', value: (row) => row.name },
              { header: 'Slot', value: (row) => row.slotRef },
              { header: 'Roll-up', value: (row) => row.rollUp },
              { header: 'Requirement', value: (row) => row.code },
              { header: 'Level', value: (row) => row.level },
              { header: 'State', value: (row) => row.state },
            ]),
          )
        }}
      >
        Export CSV
      </button>
    </>
  )
}

// ---------------------------------------------------------------------------
// The crew matrix — every person's holdings, as the Coolibah portal drew them
// ---------------------------------------------------------------------------

/**
 * Crew down, the whole catalogue across, the *holding* in each cell — a port of the Coolibah
 * portal's CREW MATRIX page (the `portal` dev dataset's source), restyled onto Nocturne.
 *
 * This is the **records** view where the generated view is the **verdicts** view, and the split is
 * AUTH-1's: a date here is banded by distance from the business date so forty crew can be scanned,
 * but the band decides nothing, a far-away date is deliberately not green, and whether holding
 * something *suffices* for a swing is only ever the engine's answer one tab over. The portal's
 * "position differs from ticket" callout is deliberately not ported for the same reason — inferring
 * it here from rank names would be compliance logic in the client.
 */
function CrewMatrix(): React.ReactNode {
  const people = usePeople()
  const requirements = useRequirements()
  const today = useToday()
  const personIds = (people.data ?? []).map((person) => person.id)
  const holdings = useAllHoldings(personIds)

  const [category, setCategory] = useState('all')
  const [search, setSearch] = useState('')
  const [window, setWindow] = useState<ExpiryWindowId | null>(null)
  const [attentionOnly, setAttentionOnly] = useState(false)

  if (people.isPending || requirements.isPending) {
    return <Spinner label="Loading the crew" />
  }
  if (people.error !== null) {
    return <ErrorPanel title="Could not load the crew" error={people.error} />
  }
  if (requirements.error !== null) {
    return <ErrorPanel title="Could not load the catalogue" error={requirements.error} />
  }
  if (holdings.error !== null) {
    return <ErrorPanel title="Could not load the holdings" error={holdings.error} />
  }
  if (holdings.isPending) {
    return <Spinner label="Loading every crew member's holdings" />
  }

  const columns = [...requirements.data]
    .filter((requirement) => requirement.status === 'active')
    .filter((requirement) => category === 'all' || requirement.category === category)
    .sort((a, b) => a.code.localeCompare(b.code))

  const categories = REQUIREMENT_CATEGORIES.filter((code) =>
    requirements.data.some((requirement) => requirement.category === code),
  )

  const holdingFor = (personId: number, requirementId: number): Holding | undefined =>
    holdings.byPerson.get(personId)?.find((holding) => holding.requirementId === requirementId)

  const cellWindow = (holding: Holding): ExpiryWindowId | null =>
    holding.status === 'held_expiry' && holding.expiry !== null
      ? expiryWindow(daysBetween(today, holding.expiry))
      : null

  /** Attention, in the holdings vocabulary: confirmed missing, never established, or inside the lead window. */
  const wantsEyes = (holding: Holding): boolean =>
    holding.status === 'not_held' ||
    holding.status === 'unknown' ||
    (holding.status === 'held_expiry' &&
      holding.expiry !== null &&
      daysBetween(today, holding.expiry) <= EXPIRY_LEAD_DAYS_DEFAULT)

  const needle = search.trim().toLowerCase()
  const rows = people.data.filter((person) => {
    if (needle !== '' && !`${person.name} ${person.sam} ${person.positionName}`.toLowerCase().includes(needle)) {
      return false
    }
    const visible = columns
      .map((requirement) => holdingFor(person.id, requirement.id))
      .filter((holding): holding is Holding => holding !== undefined)
    if (window !== null && !visible.some((holding) => cellWindow(holding) === window)) return false
    if (attentionOnly && !visible.some(wantsEyes)) return false
    return true
  })

  // The four window counts tally what is on screen — same rule as every CSV export (§6): a count
  // over rows the filters are hiding would disagree with the grid under it.
  const windowCounts = new Map<ExpiryWindowId, number>()
  for (const person of rows) {
    for (const requirement of columns) {
      const holding = holdingFor(person.id, requirement.id)
      const id = holding === undefined ? null : cellWindow(holding)
      if (id !== null) windowCounts.set(id, (windowCounts.get(id) ?? 0) + 1)
    }
  }

  return (
    <section className="section">
      <div className="counts counts--inline" role="group" aria-label="Expiry windows">
        {EXPIRY_WINDOWS.map((band) => (
          <button
            key={band.id}
            type="button"
            className={`chip chip--toggle chip--${band.id === window ? expiryWindowTone(band.id) : 'outline'}`}
            aria-pressed={band.id === window}
            title="Show only crew with a certificate in this window"
            onClick={() => setWindow(window === band.id ? null : band.id)}
          >
            {windowCounts.get(band.id) ?? 0} {band.label}
          </button>
        ))}
      </div>

      <div className="selector">
        <input
          className="input"
          style={{ width: 280 }}
          placeholder="Search crew, Sam # or position"
          aria-label="Search crew"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
        <label className="check check--box">
          <input
            type="checkbox"
            checked={attentionOnly}
            onChange={(event) => setAttentionOnly(event.target.checked)}
          />
          <span className="dot" />
          Needs attention
        </label>
        <div className="row-actions push">
          <button
            type="button"
            className="button"
            onClick={() =>
              // The CSV carries the rows in the order the screen shows them — rank order.
              downloadCsv(
                'crew-matrix.csv',
                crewMatrixCsv(groupByRank(rows).flatMap((group) => group.people), columns, holdingFor),
              )
            }
          >
            Export CSV
          </button>
        </div>
      </div>

      <div className="seg" role="radiogroup" aria-label="Category">
        {['all', ...categories].map((value) => (
          <label key={value} className="seg__opt">
            <input
              type="radio"
              name="crew-matrix-category"
              value={value}
              checked={category === value}
              onChange={() => setCategory(value)}
            />
            {value === 'all' ? 'All' : categoryLabel(value)}
          </label>
        ))}
      </div>

      {rows.length === 0 && <p className="empty">No crew match these filters.</p>}

      {rows.length > 0 && (
        <div className="table-block">
          {/* The legend sits above the grid: the grid's bottom edge is the horizontal scrollbar's
              home, and anything below it pushes that bar off the screen. */}
          <p className="matrix-legend matrix-legend--above">
            A date is banded by distance from today’s business date — and a far-off date stays
            plain rather than green, because a holding is a record, not a verdict ·{' '}
            <span className="chip chip--muted chip--small">Held</span> never expires ·{' '}
            <span className="chip chip--critical chip--small">Not held</span> confirmed missing ·{' '}
            <span className="chip chip--caution chip--small">?</span> never established (chased on
            the exceptions worklist) · <span className="level level--none">—</span> nothing
            recorded · whether a holding <em>suffices</em> for a swing is the engine’s answer on{' '}
            <strong>Generated per swing</strong>
          </p>
          <div className="table-scroll table-scroll--fill">
            <table className="table matrix-grid matrix-grid--titled">
              <thead>
                <tr>
                  <th scope="col">Crew</th>
                  <th scope="col">Position</th>
                  {/* The code alone is a key only its author can read — the title in words is the
                      header, the code its handle. */}
                  {columns.map((requirement) => (
                    <th key={requirement.id} scope="col" title={categoryLabel(requirement.category)}>
                      <span className="mono">{requirement.code}</span>
                      <span className="th-title">{requirement.title}</span>
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {groupByRank(rows).map((group) => (
                  <Fragment key={group.label}>
                    <tr className="matrix-grid__group">
                      <th scope="colgroup" colSpan={columns.length + 2}>
                        <span>{group.label}</span>
                      </th>
                    </tr>
                    {group.people.map((person) => (
                      <tr key={person.id}>
                        <th scope="row">
                          {person.name} <span className="mono muted">{person.sam}</span>
                        </th>
                        <td className="matrix-grid__pos">{person.positionName}</td>
                        {columns.map((requirement) => (
                          <td key={requirement.id}>
                            <HoldingCell holding={holdingFor(person.id, requirement.id)} today={today} />
                          </td>
                        ))}
                      </tr>
                    ))}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </section>
  )
}

/** One holding as a grid cell: the date banded by window, or the status it does not have a date for. */
function HoldingCell({
  holding,
  today,
}: {
  holding: Holding | undefined
  today: string
}): React.ReactNode {
  if (holding === undefined) return <span className="level level--none">—</span>
  if (holding.status === 'held_perpetual') {
    return <span className="chip chip--muted chip--small" title="Held — does not expire">Held</span>
  }
  if (holding.status === 'not_held') {
    return <span className="chip chip--critical chip--small" title="Confirmed not held">Not held</span>
  }
  if (holding.status === 'unknown') {
    return <span className="chip chip--caution chip--small" title="Never established — on the chase list">?</span>
  }
  if (holding.expiry === null) {
    return <span className="chip chip--muted chip--small">Held</span>
  }
  const days = daysBetween(today, holding.expiry)
  const tone = expiryWindowTone(expiryWindow(days))
  return (
    <span
      className={`chip chip--${tone} chip--small`}
      title={days < 0 ? `Expired ${Math.abs(days)} days ago` : `Expires in ${days} days`}
    >
      {formatDate(holding.expiry)}
    </span>
  )
}

/** What is on screen, exactly (§6): the filtered people against the filtered columns. */
function crewMatrixCsv(
  people: readonly Person[],
  columns: readonly Requirement[],
  holdingFor: (personId: number, requirementId: number) => Holding | undefined,
): string {
  const rows = people.flatMap((person) =>
    columns.map((requirement) => {
      const holding = holdingFor(person.id, requirement.id)
      return {
        sam: person.sam,
        name: person.name,
        position: person.positionName,
        code: requirement.code,
        title: requirement.title,
        status: holding?.status ?? '',
        expiry: holding?.expiry ?? '',
      }
    }),
  )
  return toCsv(rows, [
    { header: 'Sam #', value: (row) => row.sam },
    { header: 'Name', value: (row) => row.name },
    { header: 'Position', value: (row) => row.position },
    { header: 'Requirement', value: (row) => row.code },
    { header: 'Title', value: (row) => row.title },
    { header: 'Holding', value: (row) => row.status },
    { header: 'Expiry', value: (row) => row.expiry },
  ])
}

function errorText(error: unknown): string {
  return error instanceof ApiError ? error.message : String(error)
}
