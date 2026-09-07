import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useBringOnboard, usePeople, useSendAshore, useSetWindow, useSlots } from '../api/queries'
import { ApiError, type CrewChange, type Partnership, type SwingEvaluation } from '../api/client'
import { useHasRole, useToday } from '../api/session'
import { Copy } from './Copy'
import { Modal } from './Modal'
import { dateFromEpochDay, epochDay, formatDate, formatDayMonth } from '../domain/dates'
import { RANK_GROUPS, rankGroup } from '../domain/ranks'

/** The roles the server accepts for an assignment write — mirrored to hide the controls. */
const ROSTER_EDITORS = ['crew_coordinator', 'system_administrator'] as const

const WEEKDAY = ['S', 'M', 'T', 'W', 'T', 'F', 'S']

interface Leg {
  from: string
  to: string
  slotRef: number
}

interface Row {
  personId: number
  name: string
  sam: string
  positionName: string
  rotation: string | null
  legs: Leg[]
  /** The first day of their first leg and the last of their last. */
  from: string
  to: string
  watch: 'day' | 'night' | 'none'
}

/**
 * One swing, day by day — the window that opens when a swing is pressed on Crew and shift
 * distribution.
 *
 * Every crew member on the swing is a row, every day of the swing a column; a filled cell is a
 * day they are onboard, coloured by the watch they keep. The days are the person's assignment
 * legs on the server, so what the office types here — home early, out late, a fill-in for the
 * middle week — is the roster the engine evaluates and the crew app shows, not a note beside it.
 *
 * Dates are typed as the cards type them: the day they fly out and the day they fly home (the
 * day after their last day onboard).
 */
export function SwingDayGrid({
  ship,
  swing,
  evaluation,
  onClose,
}: {
  ship: Partnership
  swing: CrewChange
  evaluation: SwingEvaluation | undefined
  onClose: () => void
}): React.ReactNode {
  const today = useToday()
  const canEdit = useHasRole(...ROSTER_EDITORS)
  const people = usePeople()
  const slots = useSlots()

  const days: string[] = []
  for (let d = epochDay(swing.from); d <= epochDay(swing.to); d++) days.push(dateFromEpochDay(d))

  const byId = new Map((people.data ?? []).map((p) => [p.id, p]))
  const shiftOf = new Map((slots.data ?? []).map((s) => [s.ref, s.shift.trim().toLowerCase()]))
  const watchOf = (slotRef: number): Row['watch'] =>
    shiftOf.get(slotRef) === 'shift 1' ? 'day' : shiftOf.get(slotRef) === 'shift 2' ? 'night' : 'none'

  const rowsById = new Map<number, Row>()
  for (const a of evaluation?.assignments ?? []) {
    const person = byId.get(a.personId)
    const row = rowsById.get(a.personId) ?? {
      personId: a.personId,
      name: a.name,
      sam: a.sam,
      positionName: person?.positionName ?? '',
      rotation: person?.rotation ?? null,
      legs: [],
      from: a.from,
      to: a.to,
      watch: watchOf(a.slotRef),
    }
    row.legs.push({ from: a.from, to: a.to, slotRef: a.slotRef })
    if (epochDay(a.from) < epochDay(row.from)) row.from = a.from
    if (epochDay(a.to) > epochDay(row.to)) row.to = a.to
    rowsById.set(a.personId, row)
  }
  const order = [...RANK_GROUPS.map((g) => g.label), 'Other positions']
  const rows = [...rowsById.values()].sort(
    (a, b) => order.indexOf(rankGroup(a.positionName)) - order.indexOf(rankGroup(b.positionName)) || a.name.localeCompare(b.name),
  )
  const offSwing = (people.data ?? []).filter((p) => p.partnershipId === ship.id && p.status === 'active' && !rowsById.has(p.id))

  const covered = (row: Row, day: string) => row.legs.some((leg) => epochDay(leg.from) <= epochDay(day) && epochDay(day) <= epochDay(leg.to))
  const partial = rows.filter((r) => r.from !== swing.from || r.to !== swing.to).length

  return (
    <Modal
      title={`${formatDayMonth(swing.from)} – ${formatDayMonth(flyHome(swing.to))} · ${swing.rotation !== null ? `crew ${swing.rotation}` : swing.ccId}`}
      note={`${rows.length} onboard · ${partial === 0 ? 'everyone the whole swing' : `${partial} for part of it`} · type a person's own fly-out and fly-home days to change them`}
      wide
      onClose={onClose}
    >
      {evaluation === undefined && <p className="muted">Evaluating the swing…</p>}

      <div className="daygrid-scroll">
        <table className="daygrid">
          <thead>
            <tr>
              <th scope="col" className="daygrid__name">Crew</th>
              {days.map((day) => (
                <th key={day} scope="col" className={day === today ? 'daygrid__day daygrid__day--today' : 'daygrid__day'} title={formatDate(day)}>
                  <span className="daygrid__dow">{WEEKDAY[new Date(`${day}T00:00:00Z`).getUTCDay()]}</span>
                  <span className="daygrid__dom">{day.slice(8)}</span>
                </th>
              ))}
              <th scope="col" className="daygrid__dates">Flies out</th>
              <th scope="col" className="daygrid__dates">Flies home</th>
              {canEdit && <th scope="col" />}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <GridRow key={row.personId} ship={ship} swing={swing} row={row} days={days} today={today} covered={covered} canEdit={canEdit} />
            ))}
            {rows.length === 0 && evaluation !== undefined && (
              <tr>
                <td colSpan={days.length + 4} className="empty">
                  Nobody is on this swing yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <p className="muted daygrid-legend">
        <span className="daygrid__swatch daygrid__swatch--day" /> days · <span className="daygrid__swatch daygrid__swatch--night" /> nights ·{' '}
        <span className="daygrid__swatch daygrid__swatch--none" /> <Copy k="daygrid.legend-tail">onboard, no watch set · blank is ashore</Copy>
      </p>

      {canEdit && <PartSwing ship={ship} swing={swing} offSwing={offSwing} />}
    </Modal>
  )
}

/** "Flies home" is the day after the last day onboard, as the cards say it. */
function flyHome(lastDay: string): string {
  return dateFromEpochDay(epochDay(lastDay) + 1)
}

function GridRow({
  ship,
  swing,
  row,
  days,
  today,
  covered,
  canEdit,
}: {
  ship: Partnership
  swing: CrewChange
  row: Row
  days: string[]
  today: string
  covered: (row: Row, day: string) => boolean
  canEdit: boolean
}): React.ReactNode {
  const setWindow = useSetWindow()
  const ashore = useSendAshore()
  const [out, setOut] = useState(row.from)
  const [home, setHome] = useState(flyHome(row.to))
  const changed = out !== row.from || home !== flyHome(row.to)
  const backwards = out !== '' && home !== '' && epochDay(home) <= epochDay(out)
  const whole = row.from === swing.from && row.to === swing.to

  const commit = () => {
    if (!changed || backwards || out === '' || home === '') return
    setWindow.mutate(
      { partnership: ship.abbrev, cc: swing.ccId, personId: row.personId, from: out, to: dateFromEpochDay(epochDay(home) - 1) },
      { onError: () => { setOut(row.from); setHome(flyHome(row.to)) } },
    )
  }

  return (
    <>
      <tr>
        <th scope="row" className="daygrid__name">
          <Link to={`/people/${row.personId}`}>{row.name}</Link>
          <span className="muted daygrid__pos">
            {row.positionName}
            {row.rotation !== null && ` · ${row.rotation}`}
          </span>
        </th>
        {days.map((day) => {
          const on = covered(row, day)
          return (
            <td
              key={day}
              className={[
                'daygrid__cell',
                on ? `daygrid__cell--${row.watch}` : 'daygrid__cell--off',
                day === today ? 'daygrid__cell--today' : '',
              ].filter(Boolean).join(' ')}
              title={`${row.name} · ${formatDate(day)} · ${on ? (row.watch === 'none' ? 'onboard' : `on ${row.watch}s`) : 'ashore'}`}
            />
          )
        })}
        <td className="daygrid__dates">
          <input className="input input--tight" type="date" value={out} min={swing.from} max={swing.to} disabled={!canEdit || setWindow.isPending} onChange={(e) => setOut(e.target.value)} onBlur={commit} />
        </td>
        <td className="daygrid__dates">
          <input className="input input--tight" type="date" value={home} min={flyHome(swing.from)} max={flyHome(swing.to)} disabled={!canEdit || setWindow.isPending} onChange={(e) => setHome(e.target.value)} onBlur={commit} />
        </td>
        {canEdit && (
          <td className="daygrid__actions">
            {!whole && (
              <button
                type="button"
                className="button button--quiet"
                disabled={setWindow.isPending}
                title="Back to the whole swing"
                onClick={() => {
                  setOut(swing.from)
                  setHome(flyHome(swing.to))
                  setWindow.mutate({ partnership: ship.abbrev, cc: swing.ccId, personId: row.personId, from: swing.from, to: swing.to })
                }}
              >
                Whole swing
              </button>
            )}
            <button
              type="button"
              className="button button--quiet"
              disabled={ashore.isPending}
              title={`Take ${row.name} off this swing altogether`}
              onClick={() => ashore.mutate({ partnership: ship.abbrev, cc: swing.ccId, personId: row.personId })}
            >
              Off swing
            </button>
          </td>
        )}
      </tr>
      {(backwards || setWindow.error !== null || ashore.error !== null) && (
        <tr>
          <td colSpan={days.length + 4} className="editor__error daygrid__error">
            {backwards
              ? 'The day home is on or before the day out, so nothing was changed.'
              : errorText(setWindow.error ?? ashore.error)}
          </td>
        </tr>
      )}
    </>
  )
}

/** Somebody onto the swing for part of it — a fill-in, a late start, a swap. */
function PartSwing({ ship, swing, offSwing }: { ship: Partnership; swing: CrewChange; offSwing: readonly { id: number; name: string; positionName: string }[] }): React.ReactNode {
  const bring = useBringOnboard()
  const [personId, setPersonId] = useState<number | null>(null)
  const [out, setOut] = useState(swing.from)
  const [home, setHome] = useState(flyHome(swing.to))
  const [clash, setClash] = useState<string | null>(null)
  const backwards = out !== '' && home !== '' && epochDay(home) <= epochDay(out)

  const go = (acknowledgeClash: boolean) => {
    if (personId === null || backwards) return
    setClash(null)
    bring.mutate(
      { partnership: ship.abbrev, cc: swing.ccId, personId, acknowledgeClash, window: { from: out, to: dateFromEpochDay(epochDay(home) - 1) } },
      {
        onSuccess: () => setPersonId(null),
        onError: (error) => { if (error instanceof ApiError && error.code === 'assignment_clash') setClash(error.message) },
      },
    )
  }

  return (
    <div className="editor daygrid-part">
      <p className="note">
        <Copy k="daygrid.part-swing-note">
          Bring somebody on for part of this swing — filling in, coming out late, or swapping with someone going home early. They share the seat of whoever holds it for the rest of the swing.
        </Copy>
      </p>
      <label className="field field--inline field--grow">
        <span className="field__label">Who</span>
        <select className="input" value={personId ?? ''} onChange={(e) => setPersonId(e.target.value === '' ? null : Number(e.target.value))}>
          <option value="">Choose…</option>
          {offSwing.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name} — {p.positionName}
            </option>
          ))}
        </select>
      </label>
      <label className="field field--inline">
        <span className="field__label">Flies out</span>
        <input className="input" type="date" value={out} min={swing.from} max={swing.to} onChange={(e) => setOut(e.target.value)} />
      </label>
      <label className="field field--inline">
        <span className="field__label">Flies home</span>
        <input className="input" type="date" value={home} min={flyHome(swing.from)} max={flyHome(swing.to)} onChange={(e) => setHome(e.target.value)} />
      </label>
      <div className="editor__actions">
        <button type="button" className="button button--primary" disabled={personId === null || backwards || bring.isPending} onClick={() => go(clash !== null)}>
          {bring.isPending ? 'Bringing…' : clash !== null ? 'Bring onboard anyway' : 'Bring onboard for those days'}
        </button>
        {backwards && <span className="editor__error">The day home is on or before the day out.</span>}
      </div>
      {clash !== null && <p className="editor__error">{clash}</p>}
      {bring.error !== null && !(bring.error instanceof ApiError && bring.error.code === 'assignment_clash') && (
        <p className="editor__error">{errorText(bring.error)}</p>
      )}
    </div>
  )
}

function errorText(error: unknown): string {
  return error instanceof ApiError ? error.message : String(error)
}
