import { useRef } from 'react'
import { Link } from 'react-router-dom'
import { useFileShipDocument, usePeople, useRequirements, useShipDocuments, useSlots } from '../api/queries'
import { api, type CrewChange, type Partnership, type Requirement, type SwingEvaluation } from '../api/client'
import { useHasRole } from '../api/session'
import { Copy } from './Copy'
import { formatDate, formatDayMonth } from '../domain/dates'
import { RANK_GROUPS, rankGroup } from '../domain/ranks'

const FILERS = ['compliance_lead', 'data_steward', 'system_administrator'] as const

type ShiftKey = 'swing' | 'day' | 'night'

interface Rule {
  quota: SwingEvaluation['quotas'][number]
  requirement: Requirement | undefined
  positions: string[]
  holders: { name: string; expiry: string | null }[]
  lacking: string[]
}

/**
 * Certificates required by shift — the Coolibah portal's shift allocation check, on the engine's
 * quota rules.
 *
 * The portal read the office's shift-allocation guideline off a spreadsheet and counted the
 * swing's crew against it, day shift and night shift apart. Here the guideline's rules live on
 * the matrix as footnote quotas (the seed parsed them from the same sheet), and the engine
 * counts them per shift (§5.3) — this screen lays the answer out the portal's way: the sheet
 * on file, a verdict, the whole-swing rules, then the day and night columns, each rule with the
 * names that carry it and the names that should and do not, and under each column who is
 * standing that shift by position.
 */
export function ShiftAllocation({ ship, swing, evaluation }: { ship: Partnership; swing: CrewChange; evaluation: SwingEvaluation }): React.ReactNode {
  const documents = useShipDocuments(ship.abbrev)
  const requirements = useRequirements()
  const people = usePeople()
  const slots = useSlots()
  const file = useFileShipDocument()
  const canFile = useHasRole(...FILERS)
  const input = useRef<HTMLInputElement>(null)

  const sheet = documents.data?.find((d) => d.category === 'shift-allocation')
  const requirementById = new Map((requirements.data ?? []).map((r) => [r.id, r]))
  const personById = new Map((people.data ?? []).map((p) => [p.id, p]))
  const shiftOfSlot = new Map((slots.data ?? []).map((s) => [s.ref, s.shift.trim().toLowerCase()]))
  const shiftOf = (slotRef: number): ShiftKey | 'none' => {
    const shift = shiftOfSlot.get(slotRef)
    return shift === 'shift 1' ? 'day' : shift === 'shift 2' ? 'night' : 'none'
  }
  const positionOf = (personId: number) => personById.get(personId)?.positionName ?? ''

  const onShift = (key: ShiftKey) =>
    evaluation.assignments.filter((a) => key === 'swing' || shiftOf(a.slotRef) === key)

  const rulesFor = (key: ShiftKey): Rule[] =>
    evaluation.quotas
      .filter((q) => (key === 'swing' ? q.shift === null : q.shift?.trim().toLowerCase() === (key === 'day' ? 'shift 1' : 'shift 2')))
      .map((quota) => {
        const crew = onShift(key)
        // The people the rule is written for: those whose matrix cell for the code carries the
        // rule's footnote. Holders are the ones the engine counted; the rest should and do not.
        const named = crew.filter((a) => a.evaluation.cells.some((c) => c.requirementId === quota.requirementId && c.level === quota.footnote))
        const pool = named.length > 0 ? named : crew
        const holders = pool
          .filter((a) => a.evaluation.cells.some((c) => c.requirementId === quota.requirementId && (c.state === 'ok' || c.state === 'expiring')))
          .map((a) => ({ name: a.name, expiry: a.evaluation.cells.find((c) => c.requirementId === quota.requirementId)?.expiry ?? null }))
        const lacking = pool.filter((a) => !holders.some((h) => h.name === a.name)).map((a) => a.name)
        return {
          quota,
          requirement: requirementById.get(quota.requirementId),
          positions: [...new Set(named.map((a) => positionOf(a.personId)).filter((p) => p !== ''))],
          holders,
          lacking,
        }
      })

  const rules = { swing: rulesFor('swing'), day: rulesFor('day'), night: rulesFor('night') }
  const all = [...rules.swing, ...rules.day, ...rules.night]
  const short = all.filter((r) => !r.quota.satisfied)
  const met = all.length - short.length
  const noWatch = evaluation.assignments.filter((a) => shiftOf(a.slotRef) === 'none')
  const shiftName = (key: ShiftKey) => (key === 'swing' ? 'Whole Swing' : key === 'day' ? 'Day Shift' : 'Night Shift')
  const shortOn = [...new Set(short.map((r) => shiftName(r.quota.shift === null ? 'swing' : r.quota.shift.trim().toLowerCase() === 'shift 1' ? 'day' : 'night')))]

  const verdict =
    all.length === 0
      ? 'No shift rules are on the matrix for this ship yet, so there is nothing to count the crew against.'
      : short.length === 0
        ? 'Every day, night and whole-swing minimum is met by the crew rostered on this swing.'
        : `${met > short.length ? 'Most' : 'Some'} day/night minimums are met, but ${short
            .map((r) => `${r.requirement?.code ?? `#${r.quota.requirementId}`}${r.quota.shift === null ? '' : ` on ${shiftName(r.quota.shift.trim().toLowerCase() === 'shift 1' ? 'day' : 'night').toLowerCase()}`}`)
            .join(', ')} ${short.length === 1 ? 'is' : 'are'} short.`

  return (
    <section className={`section fold fold--${all.length === 0 ? 'accent' : short.length > 0 ? 'critical' : 'good'}`}>
      <div className="section__header">
        <div>
          <h2 className="section__title">Certificates required by shift</h2>
          <p className="section__note">
            {all.length} {all.length === 1 ? 'requirement' : 'requirements'} · {met} met · {short.length} short · read for {formatDayMonth(swing.from)} – {formatDayMonth(swing.to)}
          </p>
        </div>
      </div>
      <p className="muted">
        <Copy k="shift.blurb">
          The office's guideline — how many holders of certain certificates each shift must carry — read against what this swing's crew hold. Every rule is listed with the shift it applies to; upload a newer sheet whenever the office sends one, and ask for the matrix to be updated if the rules on it change.
        </Copy>
      </p>

      <div className={sheet !== undefined ? 'sheet-card' : 'sheet-card sheet-card--missing'}>
        <div>
          <p className="nav__group swing-eyebrow">The sheet on file</p>
          {sheet !== undefined ? (
            <>
              <span className="sheet-card__file">{sheet.fileName}</span>
              <span className="muted"> · {formatDate(sheet.filedAt.slice(0, 10))}</span>
            </>
          ) : (
            <span className="editor__error">No shift allocation guideline is on file for this ship.</span>
          )}
        </div>
        <span className="row-actions">
          {sheet !== undefined && (
            <a className="button button--quiet" href={api.shipDocumentUrl(sheet.id)}>
              Open
            </a>
          )}
          {canFile && (
            <>
              <input
                ref={input}
                type="file"
                hidden
                accept=".xlsx,.xls,.csv,.pdf"
                onChange={(event) => {
                  const picked = event.target.files?.[0]
                  event.target.value = ''
                  if (picked !== undefined) file.mutate({ partnership: ship.abbrev, category: 'shift-allocation', file: picked })
                }}
              />
              <button type="button" className="button" disabled={file.isPending} onClick={() => input.current?.click()}>
                {file.isPending ? 'Filing…' : 'Upload a newer sheet'}
              </button>
            </>
          )}
        </span>
      </div>

      <div className="counts counts--inline">
        <span className="chip chip--accent chip--small">
          {evaluation.assignments.length} onboard · {formatDayMonth(swing.from)} – {formatDayMonth(swing.to)}
        </span>
        {evaluation.openSlots.length > 0 && (
          <span className="chip chip--critical chip--small">{evaluation.openSlots.length} {evaluation.openSlots.length === 1 ? 'berth' : 'berths'} unfilled</span>
        )}
        {noWatch.length > 0 && <span className="chip chip--warning chip--small">{noWatch.length} no watch set</span>}
      </div>

      <div className={`verdict verdict--${all.length === 0 ? 'none' : short.length > 0 ? 'short' : 'met'}`}>
        <div className="verdict__text">
          <p className="verdict__line">{verdict}</p>
          {shortOn.length > 0 && (
            <p className="muted">
              Short on: {shortOn.join(' and ')}. Names are the crew rostered on that shift; a name in red should hold the certificate and does not.
            </p>
          )}
        </div>
        <span className="row-actions">
          {short.length > 0 && <span className="chip chip--critical">{short.length} short</span>}
          <span className="chip chip--good">{met} met</span>
        </span>
      </div>

      {rules.swing.length > 0 && (
        <div className="shift-panel shift-panel--swing">
          <div className="shift-panel__head">
            <span>
              <strong>Whole swing</strong> <span className="muted">any shift counts</span>
            </span>
            <PanelChip rules={rules.swing} />
          </div>
          <p className="nav__group swing-eyebrow">Must carry</p>
          {rules.swing.map((rule) => (
            <RuleRow key={`${rule.quota.footnote}-${rule.quota.requirementId}`} rule={rule} />
          ))}
        </div>
      )}

      <div className="shift-panels">
        {(['day', 'night'] as const).map((key) => {
          const crew = onShift(key)
          return (
            <div key={key} className={`shift-panel shift-panel--${key}`}>
              <div className="shift-panel__head">
                <span>
                  <strong>{key === 'day' ? 'Day shift' : 'Night shift'}</strong>{' '}
                  <span className="muted">
                    {key === 'day' ? 'Shift 1 · 1200 – 2400' : 'Shift 2 · 2400 – 1200'} · {crew.length} standing
                  </span>
                </span>
                <PanelChip rules={rules[key]} />
              </div>
              <p className="nav__group swing-eyebrow">Must carry</p>
              {rules[key].length === 0 && <p className="muted">No rules for this shift on the matrix.</p>}
              {rules[key].map((rule) => (
                <RuleRow key={`${rule.quota.footnote}-${rule.quota.requirementId}`} rule={rule} />
              ))}
              <Standing crew={crew} positionOf={positionOf} />
            </div>
          )
        })}
      </div>

      {noWatch.length > 0 && (
        <p className="note">
          <strong>No watch set</strong> — {noWatch.map((a) => `${a.name} (${positionOf(a.personId)})`).join(', ')} count toward the swing but toward neither shift until a watch is set on the{' '}
          <Link to={`/planner?customer=${ship.customerId ?? ''}&operation=${ship.id}`}>roster</Link>.
        </p>
      )}
    </section>
  )
}

function PanelChip({ rules }: { rules: Rule[] }): React.ReactNode {
  const short = rules.filter((r) => !r.quota.satisfied).length
  if (rules.length === 0) return null
  return short > 0 ? <span className="chip chip--critical chip--small">{short} short</span> : <span className="chip chip--good chip--small">all met</span>
}

function RuleRow({ rule }: { rule: Rule }): React.ReactNode {
  const { quota, requirement, positions, holders, lacking } = rule
  const dots = Array.from({ length: Math.max(quota.min, quota.actual) }, (_, i) => i < quota.actual)
  return (
    <div className="shift-rule">
      <div className="shift-rule__head">
        <span>
          <span className="shift-rule__title">
            <span className="mono">{requirement?.code ?? `#${quota.requirementId}`}</span> {requirement?.title ?? ''}
          </span>
          <div className="shift-rule__who">
            {positions.length > 0 ? positions.join(' or ') : 'Any position'} · {quota.min} required
          </div>
        </span>
        <span className="row-actions">
          <span className="dots" aria-hidden="true">
            {dots.map((on, i) => (
              <span key={i} className={`dot-mark${on ? ' dot-mark--on' : ''}${!quota.satisfied ? ' dot-mark--short' : ''}`} />
            ))}
          </span>
          {quota.satisfied ? (
            <span className="chip chip--good chip--small">met · {quota.actual} of {quota.min}</span>
          ) : (
            <span className="chip chip--critical chip--small">short · {quota.actual} of {quota.min}</span>
          )}
        </span>
      </div>
      {holders.length > 0 && (
        <div className="shift-rule__names">{holders.map((h) => `${h.name}${h.expiry !== null ? ` (${formatDayMonth(h.expiry)})` : ''}`).join(' · ')}</div>
      )}
      {!quota.satisfied && lacking.length > 0 && (
        <div className="shift-rule__names shift-rule__names--short">
          {lacking.join(', ')} {lacking.length === 1 ? 'does' : 'do'} not hold {requirement?.code ?? 'it'}.
        </div>
      )}
    </div>
  )
}

function Standing({ crew, positionOf }: { crew: SwingEvaluation['assignments']; positionOf: (personId: number) => string }): React.ReactNode {
  const order = [...RANK_GROUPS.map((g) => g.label), 'Other positions']
  const positions = [...new Set(crew.map((a) => positionOf(a.personId)))].sort(
    (a, b) => order.indexOf(rankGroup(a)) - order.indexOf(rankGroup(b)) || a.localeCompare(b),
  )
  return (
    <div className="standing">
      <p className="nav__group swing-eyebrow">Standing this shift</p>
      {positions.length === 0 && <div className="standing__row"><span className="standing__pos">—</span><span className="standing__empty">Nobody standing — berth unfilled</span></div>}
      {positions.map((position) => (
        <div key={position} className="standing__row">
          <span className="standing__pos">{position || 'Unplaced'}</span>
          <span>{crew.filter((a) => positionOf(a.personId) === position).map((a) => a.name).join(' · ')}</span>
        </div>
      ))}
    </div>
  )
}
