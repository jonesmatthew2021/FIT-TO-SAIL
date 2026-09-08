import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAllHoldings, usePeople, useRequirements, useSlots } from '../api/queries'
import { type CrewChange, type Partnership, type Requirement, type SwingEvaluation } from '../api/client'
import { useSession } from '../api/session'
import { validityLabel } from './CertificatesOnFile'
import { Copy } from './Copy'
import { downloadCsv, toCsv } from '../domain/csv'
import { epochDay, formatDate, formatDayMonth } from '../domain/dates'
import { needsAttention } from '../domain/enums'
import { saveReportPdf } from '../domain/report-pdf'
import { RANK_GROUPS, rankGroup } from '../domain/ranks'

interface Problem {
  code: string
  title: string
  why: string
  issue: string | null
  expiry: string | null
  validity: string
}

interface PersonReport {
  personId: number
  name: string
  position: string
  watch: string
  problems: Problem[]
}

/**
 * Full swing report — the Coolibah portal's "Generate swing compliance", written down.
 *
 * The portal pressed a button, read the roster against the spreadsheets and the certificates
 * on file, and wrote one answer for everyone: who is not clear to fly and exactly why, person
 * by person, with the dates. Here the answer is the engine's swing evaluation, so it is read
 * for today the moment the page opens rather than generated; the button is what writes it
 * down — a PDF in the office's hand, or a CSV for the spreadsheet people.
 *
 * "Not clear" is the engine's gap roll-up; "expiring" and "to look at" are listed alongside
 * because the office reads the whole picture before a fly-out, not only the blockers.
 */
export function SwingReport({ ship, swing, evaluation }: { ship: Partnership; swing: CrewChange; evaluation: SwingEvaluation }): React.ReactNode {
  const session = useSession()
  const requirements = useRequirements()
  const people = usePeople()
  const slots = useSlots()
  const holdings = useAllHoldings(evaluation.assignments.map((a) => a.personId))
  const [showClear, setShowClear] = useState(false)
  const [saving, setSaving] = useState(false)

  const requirementById = new Map((requirements.data ?? []).map((r) => [r.id, r]))
  const personById = new Map((people.data ?? []).map((p) => [p.id, p]))
  const shiftOfSlot = new Map((slots.data ?? []).map((s) => [s.ref, s.shift.trim().toLowerCase()]))
  const watchOf = (slotRef: number) => (shiftOfSlot.get(slotRef) === 'shift 1' ? 'Days' : shiftOfSlot.get(slotRef) === 'shift 2' ? 'Nights' : 'No watch')

  const why = (state: string, expiry: string | null): string => {
    if (state === 'gap' && expiry === null) return 'Not held'
    if (expiry !== null) {
      const runsOut = `Runs out ${formatDate(expiry)}`
      if (epochDay(expiry) < epochDay(swing.from)) return `${runsOut} — before the swing starts`
      if (epochDay(expiry) <= epochDay(swing.to)) return `${runsOut} — while they are onboard`
      return runsOut
    }
    if (state === 'unknown') return 'Never established — a question mark nobody has answered'
    if (state === 'review') return 'Under review'
    if (state === 'pending') return 'Awaiting a decision'
    return state
  }

  const order = [...RANK_GROUPS.map((g) => g.label), 'Other positions']
  const reports: PersonReport[] = evaluation.assignments
    .map((a) => {
      const position = personById.get(a.personId)?.positionName ?? ''
      const held = holdings.byPerson.get(a.personId) ?? []
      const problems = a.evaluation.cells
        .filter((c) => needsAttention(c.state))
        .map((c) => {
          const requirement: Requirement | undefined = requirementById.get(c.requirementId)
          const holding = held.find((h) => h.requirementId === c.requirementId)
          return {
            code: requirement?.code ?? `#${c.requirementId}`,
            title: requirement?.title ?? '',
            why: why(c.state, c.expiry ?? holding?.expiry ?? null),
            issue: holding?.issueDate ?? null,
            expiry: c.expiry ?? holding?.expiry ?? null,
            validity: validityLabel(requirement),
          }
        })
        .sort((x, y) => x.code.localeCompare(y.code))
      return { personId: a.personId, name: a.name, position, watch: watchOf(a.slotRef), problems, rollUp: a.evaluation.rollUp }
    })
    .sort((x, y) => order.indexOf(rankGroup(x.position)) - order.indexOf(rankGroup(y.position)) || x.name.localeCompare(y.name))

  const notClear = reports.filter((r) => evaluation.assignments.find((a) => a.personId === r.personId)?.evaluation.rollUp === 'gap')
  const toLook = reports.filter((r) => !notClear.includes(r) && r.problems.length > 0)
  const clear = reports.filter((r) => r.problems.length === 0)
  const compliant = notClear.length === 0

  const headline = compliant
    ? `The swing is compliant as it stands — all ${reports.length} rostered are clear to fly.`
    : `The swing is not compliant as it stands — ${notClear.length} of the ${reports.length} rostered ${notClear.length === 1 ? 'is' : 'are'} not clear to fly.`
  const subtitle = `${swing.rotation !== null ? `Crew ${swing.rotation}` : swing.ccId} · fly out ${formatDate(swing.from)}, home ${formatDate(swing.to)} · ${reports.length} rostered · read ${formatDate(session.today)} by ${session.label}`

  const save = async () => {
    setSaving(true)
    try {
      await saveReportPdf({
        title: `Swing compliance — ${ship.abbrev} ${formatDayMonth(swing.from)} – ${formatDayMonth(swing.to)}`,
        subtitle,
        intro: `${headline}\n\nNot clear means a required certificate is missing or runs out before or during the swing. Expiring and to-look-at items are listed after, because the office reads the whole picture before a fly-out.`,
        empty: 'Nothing to report — everyone rostered is clear to fly.',
        filename: `swing-compliance-${ship.abbrev}-${swing.from}.pdf`,
        groups: [
          { heading: 'Not clear to fly', meta: `${notClear.length}`, items: notClear.map(item) },
          { heading: 'To look at', meta: `${toLook.length}`, blurb: 'Expiring onboard, or a question mark the engine could not settle.', items: toLook.map(item) },
          { heading: 'Clear', meta: `${clear.length}`, items: clear.map((r) => ({ code: '', title: `${r.name} · ${r.position} · ${r.watch}`, status: 'clear', notes: [] })) },
        ],
      })
    } finally {
      setSaving(false)
    }
  }

  const item = (r: PersonReport) => ({
    code: '',
    title: `${r.name} · ${r.position} · ${r.watch}`,
    status: `${r.problems.length} ${r.problems.length === 1 ? 'item' : 'items'}`,
    notes: r.problems.map((p) => `${p.code} ${p.title} — ${p.why}${p.issue !== null ? ` · issued ${formatDate(p.issue)}` : ''}${p.expiry !== null ? ` · expires ${formatDate(p.expiry)}` : ''} · ${p.validity}`),
  })

  return (
    <section className={`section fold fold--${compliant ? 'good' : 'critical'}`}>
      <div className="section__header">
        <div>
          <h2 className="section__title">Full swing report</h2>
          <p className="section__note">
            <Copy k="report.note">
              One answer for everyone: the swing's roster read against the matrix and the certificates on file, person by person, with the dates. Read for today the moment this page opens; press Save to write it down.
            </Copy>
          </p>
        </div>
        <div className="row-actions">
          <button
            type="button"
            className="button"
            onClick={() =>
              downloadCsv(
                `swing-compliance-${ship.abbrev}-${swing.from}.csv`,
                toCsv(
                  reports.flatMap((r) => (r.problems.length === 0 ? [{ r, p: null as Problem | null }] : r.problems.map((p) => ({ r, p })))),
                  [
                    { header: 'Name', value: (row) => row.r.name },
                    { header: 'Position', value: (row) => row.r.position },
                    { header: 'Watch', value: (row) => row.r.watch },
                    { header: 'Verdict', value: (row) => (notClear.includes(row.r) ? 'Not clear' : row.r.problems.length > 0 ? 'To look at' : 'Clear') },
                    { header: 'Code', value: (row) => row.p?.code ?? '' },
                    { header: 'Certificate', value: (row) => row.p?.title ?? '' },
                    { header: 'Why', value: (row) => row.p?.why ?? '' },
                    { header: 'Issue date', value: (row) => row.p?.issue ?? '' },
                    { header: 'Expiry date', value: (row) => row.p?.expiry ?? '' },
                    { header: 'Validity', value: (row) => row.p?.validity ?? '' },
                  ],
                ),
              )
            }
          >
            Export CSV
          </button>
          <button type="button" className="button button--primary" disabled={saving} onClick={() => void save()}>
            {saving ? 'Writing…' : 'Save the report as PDF'}
          </button>
        </div>
      </div>

      <div className={`verdict verdict--${compliant ? 'met' : 'short'}`}>
        <div className="verdict__text">
          <p className="verdict__line">{headline}</p>
          <p className="muted">{subtitle}</p>
        </div>
        <span className="row-actions">
          {notClear.length > 0 && <span className="chip chip--critical">{notClear.length} not clear</span>}
          {toLook.length > 0 && <span className="chip chip--caution">{toLook.length} to look at</span>}
          <span className="chip chip--good">{clear.length} clear</span>
        </span>
      </div>

      {notClear.length > 0 && <p className="nav__group swing-eyebrow">Not clear to fly · {notClear.length}</p>}
      {notClear.map((r) => (
        <PersonCard key={r.personId} report={r} tone="critical" />
      ))}

      {toLook.length > 0 && <p className="nav__group swing-eyebrow">To look at · {toLook.length}</p>}
      {toLook.map((r) => (
        <PersonCard key={r.personId} report={r} tone="caution" />
      ))}

      {clear.length > 0 && (
        <p className="muted">
          <button type="button" className="link-action" onClick={() => setShowClear(!showClear)}>
            {showClear ? 'Hide' : 'Show'} the {clear.length} clear
          </button>
          {showClear && <> — {clear.map((r) => `${r.name} (${r.watch})`).join(', ')}</>}
        </p>
      )}
    </section>
  )
}

function PersonCard({ report, tone }: { report: PersonReport; tone: 'critical' | 'caution' }): React.ReactNode {
  return (
    <div className="report-person" style={tone === 'caution' ? { borderLeftColor: 'var(--tone-caution-text)' } : undefined}>
      <div className="report-person__head">
        <span>
          <Link className="report-person__name" to={`/people/${report.personId}`}>
            {report.name}
          </Link>{' '}
          <span className="muted">· {report.position}</span>
        </span>
        <span className="muted">{report.watch}</span>
      </div>
      <div className="report-line report-line--head">
        <span>Code</span>
        <span>Certificate</span>
        <span>Why</span>
        <span>Issue date</span>
        <span>Expiry date</span>
        <span>Validity</span>
      </div>
      {report.problems.map((p) => (
        <div key={p.code} className="report-line">
          <span className="mono">{p.code}</span>
          <span>{p.title}</span>
          <span className="report-line__why">{p.why}</span>
          <span>{formatDate(p.issue)}</span>
          <span>{formatDate(p.expiry)}</span>
          <span className="muted">{p.validity}</span>
        </div>
      ))}
    </div>
  )
}
