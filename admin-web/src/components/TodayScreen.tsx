import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAllHoldings, useEvidenceQueue, usePeople, useRequirements, useShipDocuments } from '../api/queries'
import type { Partnership } from '../api/client'
import { useToday } from '../api/session'
import { ErrorPanel } from './ErrorPanel'
import { Spinner } from './Spinner'
import { daysBetween } from '../domain/dates'

/**
 * Today — the Admin tab's landing page: one screen that answers "what needs me today?".
 *
 * Every number on it exists elsewhere on these tabs; this page only gathers them, worst first,
 * each with the tab that goes to the fix. The worklist is the portal's: expired, inside 30 days,
 * or required and never held — most overdue first. Under it, the quieter things worth a look
 * once the list is clear, absent entirely when there is nothing.
 */
interface WorkItem {
  readonly personId: number
  readonly person: string
  readonly position: string
  readonly code: string
  readonly title: string
  readonly kind: 'not' | 'expired' | 'soon'
  readonly urgency: number
  readonly say: string
}

export function TodayScreen({ ship, go }: { ship: Partnership; go: (tab: string) => void }): React.ReactNode {
  const today = useToday()
  const people = usePeople()
  const requirements = useRequirements()
  const personIds = (people.data ?? []).map((p) => p.id)
  const holdings = useAllHoldings(personIds)
  const awaiting = useEvidenceQueue(['pending_review', 'pending_extraction'])
  const sheets = useShipDocuments(ship.abbrev)
  const [showAll, setShowAll] = useState(false)

  if (people.isPending || requirements.isPending || holdings.isPending) return <Spinner label="Gathering the day" />
  if (people.error !== null) return <ErrorPanel title="Could not load the crew" error={people.error} />
  if (requirements.error !== null) return <ErrorPanel title="Could not load the catalogue" error={requirements.error} />
  if (holdings.error !== null) return <ErrorPanel title="Could not load the holdings" error={holdings.error} />

  const byId = new Map(requirements.data.map((r) => [r.id, r]))
  const work: WorkItem[] = []
  let in60 = 0
  let unknown = 0
  for (const person of people.data) {
    for (const holding of holdings.byPerson.get(person.id) ?? []) {
      const requirement = byId.get(holding.requirementId)
      if (requirement === undefined) continue
      const base = { personId: person.id, person: person.name, position: person.positionName, code: requirement.code, title: requirement.title }
      if (holding.status === 'not_held') {
        work.push({ ...base, kind: 'not', urgency: 9000, say: 'never held' })
      } else if (holding.status === 'unknown') {
        unknown++
      } else if (holding.status === 'held_expiry' && holding.expiry !== null) {
        const d = daysBetween(today, holding.expiry)
        if (d < 0) work.push({ ...base, kind: 'expired', urgency: -d + 10000, say: `expired ${Math.abs(d)} days ago` })
        else if (d <= 30) work.push({ ...base, kind: 'soon', urgency: 30 - d, say: `${d} days left` })
        else if (d <= 60) in60++
      }
    }
  }
  work.sort((a, b) => b.urgency - a.urgency)
  const shown = showAll ? work : work.slice(0, 6)

  const awaitingCount = (awaiting.data ?? []).filter((d) => personIds.includes(d.personId)).length
  const sheetAge = (category: string): number | null => {
    const sheet = sheets.data?.find((s) => s.category === category)
    return sheet === undefined ? null : Math.max(0, -daysBetween(today, sheet.filedAt.slice(0, 10)))
  }
  const opmsAge = sheetAge('opms-sheet')
  const certSheetAge = sheetAge('certificate-sheet')

  const attention: { n: number | string; text: string; target: string; act: string }[] = []
  if (in60 > 0) attention.push({ n: in60, text: "expiring in 30 to 60 days — next month's bookings", target: 'checker', act: 'See them' })
  if (unknown > 0) attention.push({ n: unknown, text: 'marked with a question mark nobody has answered', target: 'checker', act: 'Settle them' })
  if (awaitingCount > 0) attention.push({ n: awaitingCount, text: 'scans on file waiting for a code — file them to what they evidence', target: 'required-docs', act: 'Check them' })
  if (opmsAge !== null && opmsAge > 9) attention.push({ n: `${opmsAge}d`, text: 'since Portways refreshed the OPMS spreadsheet — chase the weekly export', target: 'opms', act: 'OPMS' })
  if (certSheetAge !== null && certSheetAge > 30) attention.push({ n: `${certSheetAge}d`, text: 'since the crew certificates spreadsheet was updated', target: 'required-docs', act: 'Go' })

  return (
    <div className="swing-page">
      <section className={`section fold fold--${work.length === 0 ? 'good' : 'critical'}`}>
        <h2 className="section__title">
          {work.length === 0
            ? "Nothing is urgent. You're on top of it."
            : `${work.length} ${work.length === 1 ? 'item needs' : 'items need'} you. Start at the top.`}
        </h2>
        <p className="section__note">Expired, running out inside 30 days, or required and never held — worst first.</p>
      </section>

      {shown.map((x) => (
        <div key={`${x.personId}-${x.code}`} className="today-line">
          <span className={`chip chip--${x.kind === 'not' ? 'accent' : 'critical'} chip--small`}>{x.say}</span>
          <span className="today-line__who">
            <Link to={`/people/${x.personId}`}>{x.person}</Link>
            <span className="muted">
              {' '}
              · <span className="mono">{x.code}</span> {x.title}
            </span>
          </span>
          <span className="mono muted">{x.position}</span>
        </div>
      ))}
      {work.length > 6 && (
        <div>
          <button type="button" className="button button--quiet" onClick={() => setShowAll((s) => !s)}>
            {showAll ? 'Show just the first six' : `Show all ${work.length}`}
          </button>
        </div>
      )}

      {attention.length > 0 && (
        <section className="section">
          <p className="nav__group swing-eyebrow">Worth a look when the list above is clear</p>
          {attention.map((a, i) => (
            <div key={i} className="today-attention">
              <span className="today-attention__n">{a.n}</span>
              <span className="today-attention__text">{a.text}</span>
              <button type="button" className="button button--quiet" onClick={() => go(a.target)}>
                {a.act}
              </button>
            </div>
          ))}
        </section>
      )}
      {attention.length === 0 && (
        <p className="section__note">Everything else is in order — spreadsheets fresh, files where they belong, no open questions.</p>
      )}
    </div>
  )
}
