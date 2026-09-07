import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQueries } from '@tanstack/react-query'
import {
  keys,
  useCustomerScope,
  usePeople,
  useRequirements,
  useRosterFromRotation,
  useSetSwingDates,
  useSetSwingPattern,
  useSwingPattern,
  useUpcomingSwings,
  useUseSwingPattern,
} from '../api/queries'
import { ApiError, api, type CrewChange, type Partnership, type SwingEvaluation } from '../api/client'
import { useHasRole, useToday } from '../api/session'
import { ErrorPanel } from './ErrorPanel'
import { Spinner } from './Spinner'
import { StateChip } from './StateChip'
import { SwingDayGrid } from './SwingDayGrid'
import { SwingRoster } from './SwingRoster'
import { downloadCsv, toCsv } from '../domain/csv'
import { dateFromEpochDay, epochDay, formatDate, formatDayMonth } from '../domain/dates'
import { needsAttention } from '../domain/enums'

/** The roles the server accepts for the pattern and the dates — mirrored to hide the controls. */
const SWING_EDITORS = ['crew_coordinator', 'compliance_lead', 'system_administrator'] as const

/**
 * Swing compliance — the Coolibah portal's page of the same name, per ship.
 *
 * The swing on now sits by itself, the five coming side by side under it; every card carries the
 * dates the swing is read against, in boxes the office can type over, and the engine's verdict on
 * its roster. Press a card and the section below follows it. Two screens share this: Swing and
 * shift compliance (`show="compliance"`) puts who is clear, who is not, what is expiring onboard
 * and whether the swing carries the certificates each shift needs under the cards; Crew and shift
 * distribution (`show="distribution"`) puts the roster board there instead.
 *
 * Where the portal worked the verdict out in the page, here every state is the server's (§5.1,
 * AUTH-1): "not clear" is a `gap` roll-up, "expiring onboard" is `expiring`, "needs a look" is
 * anything the engine could not settle — unknown, review, pending. The words are the portal's;
 * the reasoning is the engine's. The four-week pattern, the A/B rotation and the typed dates are
 * the portal's rules exactly, kept on the server (`SwingService`) so the crew app and every other
 * screen read the same calendar.
 */
export function SwingCompliance({ ship, show }: { ship: Partnership; show: 'compliance' | 'distribution' }): React.ReactNode {
  const today = useToday()
  const pattern = useSwingPattern(ship.abbrev)
  const upcoming = useUpcomingSwings(ship.abbrev)
  const requirements = useRequirements()
  const people = usePeople()
  const canEdit = useHasRole(...SWING_EDITORS)
  const suffix = useCustomerScope().suffix
  const [picked, setPicked] = useState<string | null>(null)
  // Crew and shift distribution: the swing pressed opens day by day, in a window of its own.
  const [opened, setOpened] = useState<string | null>(null)

  const swings = [...(upcoming.data?.swings ?? [])].sort((a, b) => epochDay(a.from) - epochDay(b.from))
  const evaluations = useQueries({
    queries: swings.map((swing) => ({
      queryKey: keys.swing(ship.abbrev, swing.ccId),
      queryFn: () => api.swingEvaluation(ship.abbrev, swing.ccId),
    })),
  })

  if (pattern.isPending || upcoming.isPending) return <Spinner label="Reading the swing pattern" />
  if (pattern.error !== null) return <ErrorPanel title="Could not read the ship's pattern" error={pattern.error} />
  if (upcoming.error !== null) return <ErrorPanel title="Could not read the swings" error={upcoming.error} />

  const now = epochDay(today)
  const onNow = swings.find((s) => epochDay(s.from) <= now && epochDay(s.to) >= now) ?? swings.find((s) => epochDay(s.from) > now) ?? swings[0]
  // The swing on now and the five after it — a renewal's lead time and a bit, as the office asked.
  const coming = swings.filter((s) => s !== onNow && epochDay(s.to) >= now).slice(0, 5)
  const here = swings.find((s) => s.ccId === picked) ?? onNow
  const evaluationOf = (swing: CrewChange | undefined) =>
    swing === undefined ? undefined : evaluations[swings.indexOf(swing)]?.data
  const pick = (swing: CrewChange) => {
    setPicked(swing.ccId)
    if (show === 'distribution') setOpened(swing.ccId)
  }
  const openedSwing = swings.find((s) => s.ccId === opened)

  const rotationless = (people.data ?? []).filter((p) => p.partnershipId === ship.id && p.rotation === null && p.status === 'active')

  return (
    <div className="swing-page">
      <p className="screen__subtitle">
        {show === 'compliance'
          ? 'Who is on each swing, and whether they are clear to sail. Press a swing to work on it — the sections below follow it, and each one\'s heading says whether it needs you before you open it.'
          : 'Who is on each swing and which watch they keep. Press a swing to work on it — the roster below follows it.'}
      </p>

      <PatternPanel ship={ship} pattern={pattern.data} canEdit={canEdit} />

      {swings.length === 0 && (
        <p className="empty">
          No swings on the calendar yet. Set the ship's pattern above and they are made from it.
        </p>
      )}

      {onNow !== undefined && (
        <>
          <div className="swing-on">
            <p className="nav__group swing-eyebrow">On swing</p>
            <SwingCard ship={ship} swing={onNow} evaluation={evaluationOf(onNow)} picked={here?.ccId === onNow.ccId} canEdit={canEdit} onPick={() => pick(onNow)} opens={show === 'distribution'} />
          </div>
          {coming.length > 0 && (
            <>
              <p className="nav__group swing-eyebrow">Upcoming swings · next {coming.length}</p>
              <div className="swing-cards">
                {coming.map((swing) => (
                  <SwingCard
                    key={swing.ccId}
                    ship={ship}
                    swing={swing}
                    evaluation={evaluationOf(swing)}
                    picked={here?.ccId === swing.ccId}
                    canEdit={canEdit}
                    onPick={() => pick(swing)}
                    opens={show === 'distribution'}
                  />
                ))}
              </div>
            </>
          )}
        </>
      )}

      {(upcoming.data?.unrostered.length ?? 0) > 0 && (
        <div className="panel">
          <p className="panel__title">Could not be rostered from the rotation</p>
          <ul className="list-plain list-plain--tight">
            {upcoming.data?.unrostered.map((u) => (
              <li key={`${u.ccId}-${u.personId}`}>
                <span className="mono">{u.ccId}</span> · {u.name} <span className="muted">· {u.position} — {u.reason}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {rotationless.length > 0 && (
        <p className="note">
          {rotationless.length} crew on this ship are on neither crew A nor B, so the pattern cannot roster them —{' '}
          <Link to={`/people${suffix}`}>set their rotation</Link> in Crew and certification.
        </p>
      )}

      {here !== undefined && show === 'distribution' && <SwingRoster ship={ship} swing={here} evaluation={evaluationOf(here)} />}
      {openedSwing !== undefined && show === 'distribution' && (
        <SwingDayGrid ship={ship} swing={openedSwing} evaluation={evaluationOf(openedSwing)} onClose={() => setOpened(null)} />
      )}
      {here !== undefined && show === 'compliance' && (
        <WhoIsClear ship={ship} swing={here} evaluation={evaluationOf(here)} requirementCodes={requirements.data ?? []} canEdit={canEdit} />
      )}
    </div>
  )
}

// ---------------------------------------------------------------------------
// The pattern
// ---------------------------------------------------------------------------

function PatternPanel({ ship, pattern, canEdit }: { ship: Partnership; pattern: SwingPatternShape | undefined; canEdit: boolean }): React.ReactNode {
  const save = useSetSwingPattern()
  const [editing, setEditing] = useState(pattern?.rosterAnchor == null)
  const [anchor, setAnchor] = useState(pattern?.rosterAnchor ?? '')
  const [cycle, setCycle] = useState(String(pattern?.rosterCycleDays ?? 28))
  const [crew, setCrew] = useState(pattern?.rosterAnchorCrew ?? 'B')

  if (pattern === undefined) return null
  if (!editing) {
    return (
      <p className="section__note swing-pattern-line">
        Pattern: crew {pattern.rosterAnchorCrew} flies out {formatDate(pattern.rosterAnchor)}, every {pattern.rosterCycleDays} days, the crews alternating — today is swing {pattern.currentK}.
        {canEdit && (
          <>
            {' '}
            <button type="button" className="button button--quiet" onClick={() => setEditing(true)}>
              Change
            </button>
          </>
        )}
      </p>
    )
  }
  return (
    <form
      className="editor"
      onSubmit={(event) => {
        event.preventDefault()
        save.mutate(
          { partnership: ship.abbrev, body: { rosterAnchor: anchor, rosterCycleDays: Number(cycle), rosterAnchorCrew: crew } },
          { onSuccess: () => setEditing(false) },
        )
      }}
    >
      <p className="note">
        The swing pattern: the day the first swing flies out, how many days a cycle runs, and which crew
        flies out on that day. Every swing after is made from it, crews alternating, until the office
        types real dates over one.
      </p>
      <label className="field field--inline">
        <span className="field__label">First swing flies out</span>
        <input className="input" type="date" value={anchor} onChange={(event) => setAnchor(event.target.value)} />
      </label>
      <label className="field field--inline">
        <span className="field__label">Cycle (days)</span>
        <input className="input input--level" value={cycle} onChange={(event) => setCycle(event.target.value)} />
      </label>
      <label className="field field--inline">
        <span className="field__label">Crew on that swing</span>
        <select className="input" value={crew} onChange={(event) => setCrew(event.target.value)}>
          <option value="A">A</option>
          <option value="B">B</option>
        </select>
      </label>
      <div className="editor__actions">
        <button type="submit" className="button button--primary" disabled={!canEdit || save.isPending || anchor === '' || Number(cycle) <= 0}>
          {save.isPending ? 'Saving…' : 'Set the pattern'}
        </button>
        {pattern.rosterAnchor !== null && (
          <button type="button" className="button" onClick={() => setEditing(false)}>
            Cancel
          </button>
        )}
      </div>
      {save.error !== null && <p className="editor__error">{errorText(save.error)}</p>}
    </form>
  )
}

interface SwingPatternShape {
  rosterAnchor: string | null
  rosterCycleDays: number | null
  rosterAnchorCrew: string | null
  currentK: number | null
}

// ---------------------------------------------------------------------------
// One swing as a card
// ---------------------------------------------------------------------------

/** The portal's four words for a roster, from the engine's roll-ups. */
export function verdict(evaluation: SwingEvaluation | undefined) {
  const rows = evaluation?.assignments ?? []
  const notClear = rows.filter((a) => a.evaluation.rollUp === 'gap')
  const watch = rows.filter((a) => a.evaluation.rollUp === 'expiring')
  const look = rows.filter((a) => ['unknown', 'review', 'pending'].includes(a.evaluation.rollUp))
  const clear = rows.filter((a) => !notClear.includes(a) && !watch.includes(a) && !look.includes(a))
  return { rows, notClear, watch, look, clear }
}

/** "09 Sep – 25 Sep": the day out to the day home (the day after the onboard window ends). */
export function swingLabel(swing: CrewChange): string {
  return `${formatDayMonth(swing.from)} – ${formatDayMonth(flyHome(swing))}`
}

function flyHome(swing: CrewChange): string {
  return dateFromEpochDay(epochDay(swing.to) + 1)
}

function SwingCard({
  ship,
  swing,
  evaluation,
  picked,
  canEdit,
  onPick,
  opens = false,
}: {
  ship: Partnership
  swing: CrewChange
  evaluation: SwingEvaluation | undefined
  picked: boolean
  canEdit: boolean
  onPick: () => void
  /** Pressing the card opens it day by day (Crew and shift distribution) as well as picking it. */
  opens?: boolean
}): React.ReactNode {
  const setDates = useSetSwingDates()
  const usePattern = useUseSwingPattern()
  const [out, setOut] = useState(swing.from)
  const [home, setHome] = useState(flyHome(swing))
  const v = verdict(evaluation)
  const backwards = out !== '' && home !== '' && epochDay(home) <= epochDay(out)
  const changed = out !== swing.from || home !== flyHome(swing)

  const commit = () => {
    if (!changed || backwards || out === '' || home === '') return
    setDates.mutate({ partnership: ship.abbrev, cc: swing.ccId, body: { from: out, to: dateFromEpochDay(epochDay(home) - 1) } })
  }

  return (
    <div
      className={['swing-card', picked ? 'swing-card--picked' : '', v.notClear.length > 0 ? 'swing-card--bad' : ''].filter(Boolean).join(' ')}
      onClick={(event) => {
        if ((event.target as HTMLElement).closest('input, button, select, label')) return
        onPick()
      }}
    >
      <div className="swing-card__head">
        <span className="swing-card__title">{swingLabel(swing)}</span>
        {evaluation === undefined ? (
          <span className="chip chip--muted chip--small">evaluating…</span>
        ) : v.notClear.length > 0 ? (
          <span className="chip chip--critical">{v.notClear.length} not clear</span>
        ) : (
          <span className="chip chip--good">All clear</span>
        )}
      </div>
      <p className="swing-card__meta mono">
        {swing.rotation !== null ? `Crew ${swing.rotation}` : swing.ccId} · {v.rows.length} rostered
        {v.watch.length > 0 && ` · ${v.watch.length} expiring onboard`}
        {v.look.length > 0 && ` · ${v.look.length} to look at`}
      </p>
      <div className="swing-card__dates">
        <label className="field">
          <span className="field__label">Flies out</span>
          <input className="input" type="date" value={out} disabled={!canEdit} onChange={(event) => setOut(event.target.value)} onBlur={commit} />
        </label>
        <label className="field">
          <span className="field__label">Flies home</span>
          <input className="input" type="date" value={home} disabled={!canEdit} onChange={(event) => setHome(event.target.value)} onBlur={commit} />
        </label>
      </div>
      <div className="swing-card__foot">
        <span className={backwards ? 'editor__error' : 'muted'}>
          {backwards
            ? 'The day home is on or before the day out, so this swing keeps its current dates.'
            : `Onboard ${formatDate(swing.from)} to ${formatDate(swing.to)}.`}
        </span>
        <span className="row-actions">
          {canEdit && swing.patternK !== null && (
            <button
              type="button"
              className="button button--quiet"
              disabled={usePattern.isPending}
              onClick={() =>
                usePattern.mutate(
                  { partnership: ship.abbrev, cc: swing.ccId },
                  { onSuccess: (updated) => { setOut(updated.from); setHome(flyHome(updated)) } },
                )
              }
            >
              Use the pattern
            </button>
          )}
          <button type="button" className={picked && !opens ? 'button' : 'button button--quiet'} onClick={onPick}>
            {opens ? 'Open day by day' : picked ? 'Shown below' : 'Show below'}
          </button>
        </span>
      </div>
      {setDates.error !== null && <p className="editor__error">{errorText(setDates.error)}</p>}
    </div>
  )
}

// ---------------------------------------------------------------------------
// Who's clear, who isn't
// ---------------------------------------------------------------------------

function WhoIsClear({
  ship,
  swing,
  evaluation,
  requirementCodes,
  canEdit,
}: {
  ship: Partnership
  swing: CrewChange
  evaluation: SwingEvaluation | undefined
  requirementCodes: readonly { id: number; code: string }[]
  canEdit: boolean
}): React.ReactNode {
  const reroster = useRosterFromRotation()
  const people = usePeople()
  if (evaluation === undefined) return <Spinner label={`Evaluating ${swing.ccId}`} />
  const v = verdict(evaluation)
  const code = (id: number) => requirementCodes.find((r) => r.id === id)?.code ?? `#${id}`
  const positionOf = (personId: number) => people.data?.find((p) => p.id === personId)?.positionName ?? ''
  const attention = (a: SwingEvaluation['assignments'][number]) =>
    a.evaluation.cells.filter((c) => needsAttention(c.state)).map((c) => `${code(c.requirementId)} ${c.state}`)

  const tone = v.notClear.length > 0 ? 'critical' : 'good'
  return (
    <>
      <section className={`section fold fold--${tone}`}>
        <div className="section__header">
          <div>
            <h2 className="section__title">Who's clear, who isn't · {swingLabel(swing)}</h2>
            <p className="section__note">
              {v.notClear.length} not clear · {v.watch.length} expiring onboard · {v.clear.length} clear
              {v.look.length > 0 && ` · ${v.look.length} to look at`}
              {evaluation.openSlots.length > 0 && ` · ${evaluation.openSlots.length} open slots`}
            </p>
          </div>
          <div className="row-actions">
            {canEdit && swing.rotation !== null && (
              <button
                type="button"
                className="button button--quiet"
                disabled={reroster.isPending}
                onClick={() => reroster.mutate({ partnership: ship.abbrev, cc: swing.ccId })}
              >
                Roster crew {swing.rotation} into free slots
              </button>
            )}
            <button
              type="button"
              className="button"
              onClick={() =>
                downloadCsv(
                  `swing-compliance-${ship.abbrev}-${swing.ccId}.csv`,
                  toCsv(v.rows, [
                    { header: 'Sam #', value: (a) => a.sam },
                    { header: 'Name', value: (a) => a.name },
                    { header: 'Position', value: (a) => positionOf(a.personId) },
                    { header: 'Slot', value: (a) => a.slotRef },
                    { header: 'Verdict', value: (a) => a.evaluation.rollUp },
                    { header: 'Needs attention', value: (a) => attention(a).join('; ') },
                  ]),
                )
              }
            >
              Export CSV
            </button>
          </div>
        </div>

        <div className="tiles">
          <Tile label="Rostered onboard" value={v.rows.length} tone="accent" />
          <Tile label="Clear to sail" value={v.clear.length} tone="good" />
          <Tile label="Not clear" value={v.notClear.length} tone="critical" />
          <Tile label="Expiring onboard" value={v.watch.length} tone="warning" />
          <Tile label="To look at" value={v.look.length} tone="caution" />
        </div>

        <div className="table-block">
          <div className="table-scroll">
            <table className="table">
              <thead>
                <tr>
                  <th scope="col">Crew</th>
                  <th scope="col">Position</th>
                  <th scope="col">Slot</th>
                  <th scope="col">Verdict</th>
                  <th scope="col">Needs attention</th>
                </tr>
              </thead>
              <tbody>
                {[...v.notClear, ...v.watch, ...v.look, ...v.clear].map((a) => (
                  <tr key={a.assignmentId}>
                    <td>
                      <Link to={`/people/${a.personId}`}>{a.name}</Link> <span className="mono muted">{a.sam}</span>
                    </td>
                    <td>{positionOf(a.personId)}</td>
                    <td className="mono">{String(a.slotRef).padStart(2, '0')}</td>
                    <td>
                      <StateChip state={a.evaluation.rollUp} />
                    </td>
                    <td className="muted">{attention(a).join(' · ') || '—'}</td>
                  </tr>
                ))}
                {v.rows.length === 0 && (
                  <tr>
                    <td colSpan={5} className="empty">
                      Nobody is rostered onto this swing yet.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>

      </section>

      <section className={`section fold fold--${evaluation.quotas.some((q) => !q.satisfied) ? 'critical' : evaluation.quotas.length > 0 ? 'good' : 'accent'}`}>
        <div className="section__header">
          <div>
            <h2 className="section__title">Certificates required by shift</h2>
            <p className="section__note">
              {evaluation.quotas.length === 0
                ? 'No shift rules on the matrix for this ship yet.'
                : `${evaluation.quotas.filter((q) => q.satisfied).length} requirements met · ${evaluation.quotas.filter((q) => !q.satisfied).length} short`}
            </p>
          </div>
        </div>
        {evaluation.quotas.length > 0 && (
          <ul className="list-plain list-plain--tight">
            {evaluation.quotas.map((q) => (
              <li key={`${q.footnote}-${q.requirementId}-${q.shift ?? ''}`} className="tag-row">
                <span className={`chip chip--${q.satisfied ? 'good' : 'critical'} chip--small`}>{q.satisfied ? 'met' : `${q.shortfall} short`}</span>
                <span>
                  <span className="mono">{q.footnote}</span> · at least {q.min} with <span className="mono">{code(q.requirementId)}</span> per {q.scope}
                  {q.shift !== null && ` (${q.shift})`} — {q.actual} on this swing
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  )
}

function Tile({ label, value, tone }: { label: string; value: number; tone: string }): React.ReactNode {
  return (
    <div className={`tile tile--${tone}`}>
      <span className="tile__label">{label}</span>
      <span className="tile__value">{value}</span>
    </div>
  )
}

function errorText(error: unknown): string {
  return error instanceof ApiError ? error.message : String(error)
}
