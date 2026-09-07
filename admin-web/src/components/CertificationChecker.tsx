import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAllHoldings, useEvidenceQueue, usePeople, useRequirements } from '../api/queries'
import { api, type EvidenceDocument, type Holding, type Person, type Requirement } from '../api/client'
import { useToday } from '../api/session'
import { ErrorPanel } from './ErrorPanel'
import { Spinner } from './Spinner'
import { downloadCsv, toCsv } from '../domain/csv'
import { daysBetween, formatDate } from '../domain/dates'
import { categoryLabel } from '../domain/enums'
import { groupByRank } from '../domain/ranks'

/**
 * Certification checker — the Coolibah portal's page of the same name, per ship.
 *
 * Anything on the matrix that is already a problem today: past its date, marked as not held, or
 * left with a question mark. Upcoming expiries are the crew matrix's; this is what is red now.
 * Three sections, each written out in full under its tile, worst first within each — the
 * portal's order — and a certificate link on every line that has a scan behind it.
 *
 * The verdict per line is a fact about the holding (its status and its date against today), not
 * a compliance answer, so it is read here rather than asked of the engine — the same line ADM-5
 * draws. What a gap *means* for a swing is the swing page's.
 */
type Kind = 'missing' | 'expired' | 'unknown'

interface Gap {
  readonly person: Person
  readonly requirement: Requirement
  readonly holding: Holding
  readonly kind: Kind
  readonly document: EvidenceDocument | undefined
}

const SECTIONS: readonly { kind: Kind; label: string; tone: string; blurb: string }[] = [
  { kind: 'missing', label: 'Not held', tone: 'critical', blurb: 'Certificates marked as not held.' },
  { kind: 'expired', label: 'Expired', tone: 'warning', blurb: 'Certificates that are past their expiry date.' },
  { kind: 'unknown', label: 'Unconfirmed', tone: 'caution', blurb: 'Certificates left with a question mark. Nobody has said yes or no yet.' },
]

export function CertificationChecker(): React.ReactNode {
  const today = useToday()
  const people = usePeople()
  const requirements = useRequirements()
  const personIds = (people.data ?? []).map((p) => p.id)
  const holdings = useAllHoldings(personIds)
  const documents = useEvidenceQueue(['verified', 'auto_accepted'])
  const [search, setSearch] = useState('')

  if (people.isPending || requirements.isPending || holdings.isPending) return <Spinner label="Checking every certificate" />
  if (people.error !== null) return <ErrorPanel title="Could not load the crew" error={people.error} />
  if (requirements.error !== null) return <ErrorPanel title="Could not load the catalogue" error={requirements.error} />
  if (holdings.error !== null) return <ErrorPanel title="Could not load the holdings" error={holdings.error} />

  const requirementById = new Map(requirements.data.map((r) => [r.id, r]))
  const documentFor = (personId: number, requirementId: number) =>
    (documents.data ?? []).find((d) => d.personId === personId && d.matchedRequirementId === requirementId)

  const needle = search.trim().toLowerCase()
  const gaps: Gap[] = []
  for (const person of people.data) {
    if (needle !== '' && !`${person.name} ${person.positionName}`.toLowerCase().includes(needle)) continue
    for (const holding of holdings.byPerson.get(person.id) ?? []) {
      const requirement = requirementById.get(holding.requirementId)
      if (requirement === undefined) continue
      const kind: Kind | null =
        holding.status === 'not_held'
          ? 'missing'
          : holding.status === 'unknown'
            ? 'unknown'
            : holding.status === 'held_expiry' && holding.expiry !== null && daysBetween(today, holding.expiry) < 0
              ? 'expired'
              : null
      if (kind !== null) gaps.push({ person, requirement, holding, kind, document: documentFor(person.id, requirement.id) })
    }
  }
  const count = (kind: Kind) => gaps.filter((g) => g.kind === kind).length

  return (
    <div className="swing-page">
      <div className="tiles">
        {SECTIONS.map((s) => (
          <div key={s.kind} className={`tile tile--${s.tone}`}>
            <span className="tile__label">{s.label}</span>
            <span className="tile__value">{count(s.kind)}</span>
          </div>
        ))}
        <div className="tile tile--accent">
          <span className="tile__label">Crew with a gap</span>
          <span className="tile__value">{new Set(gaps.map((g) => g.person.id)).size}</span>
        </div>
      </div>

      <p className="screen__subtitle">
        Anything on the matrix that is past its date, marked as not held, or left with a question mark.
        Upcoming expiries are on the crew matrix — this is what is already a problem today. Every line
        with a scan behind it opens the certificate.
      </p>

      <div className="selector">
        <input
          className="input"
          style={{ width: 280 }}
          placeholder="Search crew or position"
          aria-label="Search crew"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
        <div className="row-actions push">
          <button
            type="button"
            className="button"
            onClick={() =>
              downloadCsv(
                `certification-gaps-${today}.csv`,
                toCsv(gaps, [
                  { header: 'Name', value: (g) => g.person.name },
                  { header: 'Sam #', value: (g) => g.person.sam },
                  { header: 'Position', value: (g) => g.person.positionName },
                  { header: 'Gap', value: (g) => g.kind },
                  { header: 'Code', value: (g) => g.requirement.code },
                  { header: 'Title', value: (g) => g.requirement.title },
                  { header: 'Category', value: (g) => categoryLabel(g.requirement.category) },
                  { header: 'Issued', value: (g) => g.holding.issueDate },
                  { header: 'Expires', value: (g) => g.holding.expiry },
                  { header: 'Validity', value: (g) => validityLabel(g.requirement) },
                  { header: 'Scan on file', value: (g) => (g.document === undefined ? 'no' : 'yes') },
                ]),
              )
            }
          >
            Export CSV
          </button>
        </div>
      </div>

      {gaps.length === 0 && <p className="empty">No gaps — everything on the matrix is valid.</p>}

      {SECTIONS.map((s) => {
        const own = gaps.filter((g) => g.kind === s.kind)
        const byPerson = groupByRank([...new Map(own.map((g) => [g.person.id, g.person])).values()]).flatMap((group) => group.people)
        return (
          <section key={s.kind} className={`section fold fold--${s.tone}`}>
            <div className="section__header">
              <div>
                <h2 className="section__title">
                  {s.label} · {own.length}
                </h2>
                <p className="section__note">{s.blurb}</p>
              </div>
            </div>
            {own.length === 0 && <p className="empty">{needle !== '' ? 'Nothing in this section matches that search.' : 'Nothing in this section.'}</p>}
            {byPerson.map((person) => {
              const lines = own.filter((g) => g.person.id === person.id)
              return (
                <div key={person.id} className="gap-person">
                  <div className="gap-person__head">
                    <Link to={`/people/${person.id}`}>{person.name}</Link>
                    <span className="mono muted">
                      {person.positionName} · {lines.length} {lines.length === 1 ? 'item' : 'items'}
                    </span>
                  </div>
                  <div className="gap-lines">
                    {lines.map((g) => (
                      <div key={g.requirement.id} className="gap-line">
                        <span className="mono">{g.requirement.code}</span>
                        <span className="gap-line__title">{g.requirement.title}</span>
                        <span className="muted">{validityLabel(g.requirement)}</span>
                        <span className="muted">{g.holding.issueDate === null ? '—' : formatDate(g.holding.issueDate)}</span>
                        <span>
                          {g.holding.expiry === null ? (
                            <span className="dim">—</span>
                          ) : (
                            <span className="chip chip--critical chip--small">{formatDate(g.holding.expiry)}</span>
                          )}
                        </span>
                        <span>
                          {g.document === undefined ? (
                            <span className="dim">no scan</span>
                          ) : (
                            <a href={api.evidenceContentUrl(g.document.publicId)} target="_blank" rel="noreferrer">
                              Open scan
                            </a>
                          )}
                        </span>
                        <span className="mono muted">{categoryLabel(g.requirement.category)}</span>
                        <span>
                          <span className={`chip chip--${s.tone} chip--small`}>
                            {g.kind === 'missing' ? 'Not held' : g.kind === 'expired' ? `Expired ${Math.abs(daysBetween(today, g.holding.expiry ?? today))}d ago` : '?'}
                          </span>
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )
            })}
          </section>
        )
      })}
    </div>
  )
}

function validityLabel(requirement: Requirement): string {
  if (requirement.validityText !== null) return requirement.validityText
  if (requirement.validityMonths !== null) {
    const months = requirement.validityMonths
    return months % 12 === 0 ? `${months / 12} ${months === 12 ? 'year' : 'years'}` : `${months} months`
  }
  return '—'
}
