import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAllHoldings, useEvidenceQueue, usePeople, useRequirements } from '../api/queries'
import { api, type Holding, type Person, type Requirement } from '../api/client'
import { useToday } from '../api/session'
import { ErrorPanel } from './ErrorPanel'
import { Spinner } from './Spinner'
import { downloadCsv, toCsv } from '../domain/csv'
import { daysBetween, formatDate } from '../domain/dates'
import { categoryLabel } from '../domain/enums'

/**
 * E-learning status — the Coolibah portal's page of the same name, per ship.
 *
 * The modules that are sat at a computer rather than on a course: the project inductions, the
 * vessel and tug/barge inductions, the project modules, and the named exceptions filed under
 * other groups (Helm CONNECT, anything MRN/MRM/MinRes issue). They come back as a tick rather
 * than a ticket, so what matters is who has not done them yet. A module counts as done when it
 * is held or carries a date still to run; one marked not held counts as not done; a module nobody
 * has asked of that person is left out of their count — counting it would put everyone
 * permanently behind. Percentages round *down*, here and per person: one module outstanding
 * across the whole crew must not round up to 100%.
 */
const ELEARNING_CATEGORIES = new Set(['PI', 'VI', 'PS'])
const ELEARNING_CODES = new Set(['VS-04'])
const ELEARNING_MARKS = /\b(MRN|MRM|MinRes)\b/i

export function isELearning(requirement: Requirement): boolean {
  return ELEARNING_CATEGORIES.has(requirement.category) || ELEARNING_CODES.has(requirement.code) || ELEARNING_MARKS.test(requirement.title)
}

type ModuleState = 'done' | 'overdue' | 'outstanding' | 'unconfirmed'

interface Module {
  readonly requirement: Requirement
  readonly holding: Holding
  readonly state: ModuleState
}

function moduleState(holding: Holding, today: string): ModuleState | null {
  if (holding.status === 'held_perpetual') return 'done'
  if (holding.status === 'held_expiry') return holding.expiry !== null && daysBetween(today, holding.expiry) < 0 ? 'overdue' : 'done'
  if (holding.status === 'not_held') return 'outstanding'
  if (holding.status === 'unknown') return 'unconfirmed'
  return null
}

export function ELearningStatus(): React.ReactNode {
  const today = useToday()
  const people = usePeople()
  const requirements = useRequirements()
  const personIds = (people.data ?? []).map((p) => p.id)
  const holdings = useAllHoldings(personIds)
  const documents = useEvidenceQueue(['verified', 'auto_accepted'])
  const [view, setView] = useState<'crew' | 'module'>('crew')
  const [onlyOpen, setOnlyOpen] = useState(false)
  const [search, setSearch] = useState('')
  const [openModule, setOpenModule] = useState<number | null>(null)

  if (people.isPending || requirements.isPending || holdings.isPending) return <Spinner label="Reading the modules" />
  if (people.error !== null) return <ErrorPanel title="Could not load the crew" error={people.error} />
  if (requirements.error !== null) return <ErrorPanel title="Could not load the catalogue" error={requirements.error} />
  if (holdings.error !== null) return <ErrorPanel title="Could not load the holdings" error={holdings.error} />

  const modules = requirements.data.filter((r) => r.status === 'active' && isELearning(r)).sort((a, b) => a.code.localeCompare(b.code))
  const byId = new Map(modules.map((m) => [m.id, m]))
  const needle = search.trim().toLowerCase()

  const crew = people.data.map((person) => {
    const own: Module[] = []
    for (const holding of holdings.byPerson.get(person.id) ?? []) {
      const requirement = byId.get(holding.requirementId)
      if (requirement === undefined) continue
      const state = moduleState(holding, today)
      if (state !== null) own.push({ requirement, holding, state })
    }
    own.sort((a, b) => a.requirement.code.localeCompare(b.requirement.code))
    const count = (s: ModuleState) => own.filter((m) => m.state === s).length
    const done = count('done')
    return {
      person,
      modules: own,
      done,
      overdue: count('overdue'),
      outstanding: count('outstanding'),
      unconfirmed: count('unconfirmed'),
      pct: own.length > 0 ? Math.floor((done / own.length) * 100) : 100,
    }
  })

  const shownCrew = crew
    .filter((c) => !onlyOpen || c.pct < 100)
    .filter((c) => needle === '' || `${c.person.name} ${c.person.positionName}`.toLowerCase().includes(needle))
    .sort((a, b) => a.pct - b.pct || a.person.name.localeCompare(b.person.name))

  const byModule = modules
    .map((requirement) => {
      const held = crew.flatMap((c) => c.modules.filter((m) => m.requirement.id === requirement.id).map((m) => ({ ...m, person: c.person })))
      const done = held.filter((m) => m.state === 'done').length
      return { requirement, tracked: held.length, done, open: held.length - done, people: held, pct: held.length > 0 ? Math.floor((done / held.length) * 100) : 100 }
    })
    .filter((m) => m.tracked > 0)
    .sort((a, b) => a.pct - b.pct || a.requirement.code.localeCompare(b.requirement.code))

  const shownModules = byModule
    .filter((m) => !onlyOpen || m.open > 0)
    .filter((m) => needle === '' || `${m.requirement.code} ${m.requirement.title}`.toLowerCase().includes(needle))

  const total = (k: 'done' | 'overdue' | 'outstanding' | 'unconfirmed') => crew.reduce((n, c) => n + c[k], 0)
  const trackedTotal = crew.reduce((n, c) => n + c.modules.length, 0)
  const complete = crew.filter((c) => c.modules.length > 0 && c.pct === 100).length
  const documentFor = (personId: number, requirementId: number) =>
    (documents.data ?? []).find((d) => d.personId === personId && d.matchedRequirementId === requirementId)

  return (
    <div className="swing-page">
      <div className="tiles">
        <Tile label="Modules complete" value={trackedTotal > 0 ? `${Math.floor((total('done') / trackedTotal) * 100)}%` : '—'} tone="good" />
        <Tile label="Crew fully up to date" value={`${complete}/${crew.length}`} tone={complete === crew.length ? 'good' : 'warning'} />
        <Tile label="Overdue" value={total('overdue')} tone="critical" />
        <Tile label="Not done" value={total('outstanding')} tone="critical" />
        <Tile label="Unconfirmed" value={total('unconfirmed')} tone="caution" />
      </div>

      <p className="screen__subtitle">
        The inductions and online modules on the matrix — project inductions, the tug and barge inductions
        and the project modules. A module counts as done when it is held or carries a date still to run;
        one marked not held counts as not done; a module nobody has asked of that person is left out of
        their count. Module validity is how long each stays valid, from the catalogue.
      </p>

      <div className="selector">
        <div className="seg" role="radiogroup" aria-label="View">
          {(['crew', 'module'] as const).map((v) => (
            <label key={v} className="seg__opt">
              <input type="radio" name="elearning-view" value={v} checked={view === v} onChange={() => setView(v)} />
              {v === 'crew' ? 'By crew' : 'By module'}
            </label>
          ))}
        </div>
        <label className="check check--box">
          <input type="checkbox" checked={onlyOpen} onChange={(event) => setOnlyOpen(event.target.checked)} />
          <span className="dot" />
          Not complete only
        </label>
        <input
          className="input"
          style={{ width: 260 }}
          placeholder={view === 'crew' ? 'Search crew or position' : 'Search module or code'}
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
        <div className="row-actions push">
          <button
            type="button"
            className="button"
            onClick={() =>
              downloadCsv(
                `e-learning-status-${today}.csv`,
                toCsv(
                  crew.flatMap((c) => c.modules.map((m) => ({ c, m }))),
                  [
                    { header: 'Name', value: ({ c }) => c.person.name },
                    { header: 'Position', value: ({ c }) => c.person.positionName },
                    { header: 'Code', value: ({ m }) => m.requirement.code },
                    { header: 'Module', value: ({ m }) => m.requirement.title },
                    { header: 'State', value: ({ m }) => m.state },
                    { header: 'Completed', value: ({ m }) => m.holding.issueDate },
                    { header: 'Expires', value: ({ m }) => m.holding.expiry },
                  ],
                ),
              )
            }
          >
            Export CSV
          </button>
        </div>
      </div>

      {view === 'crew' &&
        (shownCrew.length === 0 ? (
          <p className="empty">{onlyOpen ? 'Every crew member is up to date.' : 'Nothing matches that search.'}</p>
        ) : (
          shownCrew.map((c) => (
            <section key={c.person.id} className={`section fold fold--${toneFor(c.pct)}`}>
              <div className="section__header">
                <div>
                  <h2 className="section__title">
                    <Link to={`/people/${c.person.id}`}>{c.person.name}</Link> <span className="muted">· {c.person.positionName}</span>
                  </h2>
                </div>
                <div className="row-actions">
                  {c.overdue > 0 && <span className="chip chip--critical chip--small">{c.overdue} overdue</span>}
                  {c.outstanding > 0 && <span className="chip chip--critical chip--small">{c.outstanding} not done</span>}
                  {c.unconfirmed > 0 && <span className="chip chip--caution chip--small">{c.unconfirmed} unconfirmed</span>}
                  <Bar pct={c.pct} />
                  <span className={`mono bar__label bar__label--${toneFor(c.pct)}`}>
                    {c.done}/{c.modules.length} · {c.pct}%
                  </span>
                </div>
              </div>
              {c.modules.length === 0 && <p className="empty">No modules tracked against this person.</p>}
              {c.modules.length > 0 && (
                <div className="gap-lines">
                  {c.modules.map((m) => {
                    const doc = documentFor(c.person.id, m.requirement.id)
                    return (
                      <div key={m.requirement.id} className="gap-line">
                        <span className="mono">{m.requirement.code}</span>
                        <span className="gap-line__title">{m.requirement.title}</span>
                        <span className="muted">{validityLabel(m.requirement)}</span>
                        <span className="muted">{m.holding.issueDate === null ? '—' : formatDate(m.holding.issueDate)}</span>
                        <span className="muted">{m.holding.expiry === null ? '—' : formatDate(m.holding.expiry)}</span>
                        <span>
                          {doc === undefined ? (
                            <span className="dim">no scan</span>
                          ) : (
                            <a href={api.evidenceContentUrl(doc.publicId)} target="_blank" rel="noreferrer">
                              Open
                            </a>
                          )}
                        </span>
                        <span className="mono muted">{categoryLabel(m.requirement.category)}</span>
                        <span>
                          <StateTag state={m.state} />
                        </span>
                      </div>
                    )
                  })}
                </div>
              )}
            </section>
          ))
        ))}

      {view === 'module' &&
        (shownModules.length === 0 ? (
          <p className="empty">{onlyOpen ? 'Every module is complete across the crew.' : 'Nothing matches that search.'}</p>
        ) : (
          <div className="table-block table-block--plain">
            {shownModules.map((m) => {
              const opened = openModule === m.requirement.id
              const people = [...m.people].sort((a, b) => {
                const ka = a.holding.issueDate ?? a.holding.expiry ?? ''
                const kb = b.holding.issueDate ?? b.holding.expiry ?? ''
                if (ka === '' && kb === '') return a.person.name.localeCompare(b.person.name)
                if (ka === '' || kb === '') return ka !== '' ? 1 : -1
                return ka.localeCompare(kb) || a.person.name.localeCompare(b.person.name)
              })
              return (
                <div key={m.requirement.id} className="module-row">
                  <button type="button" className="module-row__head" aria-expanded={opened} onClick={() => setOpenModule(opened ? null : m.requirement.id)}>
                    <span className="mono muted">{opened ? '▾' : '▸'}</span>
                    <span className="mono">{m.requirement.code}</span>
                    <span className="gap-line__title">
                      {m.requirement.title} <span className="mono muted">{categoryLabel(m.requirement.category)}</span>
                    </span>
                    <span className="muted">{validityLabel(m.requirement)}</span>
                    {m.open > 0 && <span className="chip chip--critical chip--small">{m.open} outstanding</span>}
                    <Bar pct={m.pct} />
                    <span className={`mono bar__label bar__label--${toneFor(m.pct)}`}>
                      {m.done}/{m.tracked} · {m.pct}%
                    </span>
                  </button>
                  {opened && (
                    <div className="module-row__people">
                      {people.map((p) => (
                        <div key={p.person.id} className="module-person">
                          <span>
                            <Link to={`/people/${p.person.id}`}>{p.person.name}</Link> <span className="muted">· {p.person.positionName}</span>
                          </span>
                          <span className="muted">{p.holding.issueDate === null ? '—' : formatDate(p.holding.issueDate)}</span>
                          <span className="muted">{p.holding.expiry === null ? '—' : formatDate(p.holding.expiry)}</span>
                          <StateTag state={p.state} />
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        ))}
    </div>
  )
}

function StateTag({ state }: { state: ModuleState }): React.ReactNode {
  const tone = state === 'done' ? 'good' : state === 'unconfirmed' ? 'caution' : 'critical'
  const label = state === 'done' ? 'Done' : state === 'overdue' ? 'Overdue' : state === 'outstanding' ? 'Not done' : '?'
  return <span className={`chip chip--${tone} chip--small`}>{label}</span>
}

function Bar({ pct }: { pct: number }): React.ReactNode {
  return (
    <span className="bar" aria-hidden="true">
      <i className={`bar__fill bar__fill--${toneFor(pct)}`} style={{ width: `${pct}%` }} />
    </span>
  )
}

function Tile({ label, value, tone }: { label: string; value: number | string; tone: string }): React.ReactNode {
  return (
    <div className={`tile tile--${tone}`}>
      <span className="tile__label">{label}</span>
      <span className="tile__value">{value}</span>
    </div>
  )
}

function toneFor(pct: number): string {
  return pct === 100 ? 'good' : pct >= 75 ? 'caution' : pct >= 50 ? 'warning' : 'critical'
}

function validityLabel(requirement: Requirement): string {
  if (requirement.validityText !== null) return requirement.validityText
  if (requirement.validityMonths !== null) {
    const months = requirement.validityMonths
    return months % 12 === 0 ? `${months / 12} ${months === 12 ? 'year' : 'years'}` : `${months} months`
  }
  return '—'
}

export type { Person }
