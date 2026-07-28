import { createContext, useContext } from 'react'
import { dateFromEpochDay, epochDay, formatDate, formatDayMonth } from '../domain/dates'

/**
 * The ruler — a shared time axis, and the one thing on these screens the two Excel workbooks could
 * never draw.
 *
 * Everything in this domain is "does date range A cover date range B": a swing has a window and a
 * cutoff (§4.2), a mid-swing handover splits one slot into two legs (§4.3), an expiry is a date
 * landing inside or before a window (§5.1). A table can only say "expires mid-swing"; the ruler
 * says *which day*, and how much of the swing is uncovered after it.
 *
 * Three rules hold:
 *
 *  - **Only dates sit on the axis.** Counts, states and scores live in the value column. The
 *    moment a cell count is given a horizontal position, the axis stops meaning one thing.
 *  - **Nothing here knows today.** The datum is drawn at the `today` passed in, which is the
 *    server's business date from `GET /api/v1/session` (NFR-5, O-11). No `new Date()` anywhere in
 *    this file — a planner in London must not shift the line.
 *  - **Positions are presentation, never evaluation.** Every date rendered here arrives from the
 *    server; this component divides, it does not decide (AUTH-1).
 *
 * Below 900px the axis is hidden by the stylesheet and each track falls back to the `dates` string
 * it was given. A 27-day axis in 380px is about 14px a day, and geometry you cannot read is
 * geometry that lies.
 */

interface Axis {
  readonly from: string
  readonly to: string
}

const AxisContext = createContext<Axis | null>(null)

function useAxis(): Axis {
  const axis = useContext(AxisContext)
  if (axis === null) throw new Error('Ruler parts must be rendered inside a <Ruler>')
  return axis
}

/** Where a date falls along the axis, 0–1, clamped to the ends. */
function fraction(axis: Axis, date: string): number {
  const start = epochDay(axis.from)
  // A zero-width axis would divide by zero; one day is the smallest span that can be drawn.
  const span = Math.max(1, epochDay(axis.to) - start)
  return Math.min(1, Math.max(0, (epochDay(date) - start) / span))
}

function percent(value: number): string {
  return `${(value * 100).toFixed(3)}%`
}

/** `left`/`width` for a span, plus whether it was cut off by the end of the axis. */
function extent(
  axis: Axis,
  from: string,
  to: string,
): { readonly left: string; readonly width: string; readonly clipped: boolean } {
  const start = fraction(axis, from)
  const end = fraction(axis, to)
  return {
    left: percent(start),
    width: percent(Math.max(0, end - start)),
    clipped: epochDay(to) > epochDay(axis.to),
  }
}

/**
 * How close to the datum a tick may sit before its label collides with the "Today" pill, as a
 * fraction of the axis. The pill is about 60px wide against tracks of 400–900px.
 */
const DATUM_CLEARANCE = 0.06

/**
 * Evenly spaced axis labels.
 *
 * Derived rather than passed in, so no caller has to invent tick dates — and so a screen cannot
 * accidentally label the axis with dates that are not on it.
 *
 * A tick too near the datum is dropped rather than drawn under the "Today" pill: two dates
 * overlapping is worse than one date missing, and the pill already names its own position.
 */
function axisMarks(axis: Axis, count: number, datum: string | null): readonly string[] {
  const start = epochDay(axis.from)
  const span = Math.max(1, epochDay(axis.to) - start)
  const steps = Math.max(2, Math.min(count, span + 1))
  const marks = Array.from({ length: steps }, (_, index) =>
    dateFromEpochDay(start + Math.round((span * index) / (steps - 1))),
  )
  if (datum === null) return marks
  const at = fraction(axis, datum)
  return marks.filter((mark) => Math.abs(fraction(axis, mark) - at) >= DATUM_CLEARANCE)
}

export function Ruler({
  from,
  to,
  today,
  labelHeader,
  valueHeader,
  markCount = 6,
  children,
}: {
  from: string
  to: string
  today: string
  labelHeader: string
  valueHeader: string
  markCount?: number
  children: React.ReactNode
}): React.ReactNode {
  const axis: Axis = { from, to }
  const todayDay = epochDay(today)
  // Drawn only when it is actually on the axis: a line pinned to an end it has run off would
  // claim a position it does not have.
  const showDatum = todayDay >= epochDay(from) && todayDay <= epochDay(to)
  const marks = axisMarks(axis, markCount, showDatum ? today : null)

  return (
    <AxisContext.Provider value={axis}>
      <div className="ruler">
        <div className="ruler__head ruler__head--label">{labelHeader}</div>

        <div
          className="ruler__axis"
          role="img"
          aria-label={`Time axis, ${formatDate(from)} to ${formatDate(to)}`}
        >
          {marks.map((mark, index) => (
            <div
              key={mark}
              className={[
                'ruler__mark',
                index === 0 ? 'ruler__mark--start' : '',
                index === marks.length - 1 ? 'ruler__mark--end' : '',
              ]
                .filter(Boolean)
                .join(' ')}
              style={{ left: percent(fraction(axis, mark)) }}
            >
              <span>{formatDayMonth(mark)}</span>
            </div>
          ))}
        </div>

        <div className="ruler__head ruler__head--value">{valueHeader}</div>

        {showDatum && (
          <div
            className="ruler__datum"
            style={{ '--ruler-at': fraction(axis, today) } as React.CSSProperties}
            aria-hidden="true"
          >
            <div className="ruler__datum-line" />
            <div className="ruler__datum-tag">Today</div>
          </div>
        )}

        {children}
      </div>
    </AxisContext.Provider>
  )
}

/**
 * One row: a label, a track, and a value column.
 *
 * `dates` is the row's own window in words. It is what the track becomes on a narrow screen, and
 * it is also the row's accessible description — the bars themselves are decorative given the
 * value column already carries the states as text.
 *
 * `onOpen` makes the whole row an activation target (the dashboard's rows open the planner).
 * Controls inside the row keep working: a click that lands on a link or button is theirs, not the
 * row's.
 */
export function RulerRow({
  label,
  note,
  value,
  dates,
  attention = false,
  onOpen,
  openLabel,
  children,
}: {
  label: React.ReactNode
  note?: React.ReactNode
  value: React.ReactNode
  dates: string
  attention?: boolean
  onOpen?: () => void
  openLabel?: string
  children: React.ReactNode
}): React.ReactNode {
  const interactive =
    onOpen === undefined
      ? {}
      : {
          role: 'link' as const,
          tabIndex: 0,
          'aria-label': openLabel,
          onClick: (event: React.MouseEvent) => {
            if ((event.target as HTMLElement).closest('a, button, input, select') !== null) return
            onOpen()
          },
          onKeyDown: (event: React.KeyboardEvent) => {
            if (event.target !== event.currentTarget) return
            if (event.key === 'Enter' || event.key === ' ') {
              event.preventDefault()
              onOpen()
            }
          },
        }

  return (
    <div
      className={onOpen === undefined ? 'ruler__row' : 'ruler__row ruler__row--clickable'}
      {...interactive}
    >
      <div className="ruler__label">
        <span className="ruler__label-key">{label}</span>
        {note !== undefined && <span className="ruler__label-note">{note}</span>}
      </div>

      <div className="track" data-dates={dates} {...(attention ? { 'data-attention': '' } : {})}>
        {children}
      </div>

      <div className="ruler__value">{value}</div>
    </div>
  )
}

/** The cutoff, as a tick with its label in the clear lane above the bars. */
export function Cut({
  date,
  label,
  tight = false,
}: {
  date: string
  label: string
  tight?: boolean
}): React.ReactNode {
  const axis = useAxis()
  return (
    <div
      className={tight ? 'track__cut track__cut--tight' : 'track__cut'}
      style={{ left: percent(fraction(axis, date)) }}
    >
      <span className="track__cut-label">{label}</span>
    </div>
  )
}

/** Cutoff → window start: the lead time, drawn as an approach rather than a thing. */
export function Lead({ from, to }: { from: string; to: string }): React.ReactNode {
  const axis = useAxis()
  const { left, width } = extent(axis, from, to)
  return <div className="track__lead" style={{ left, width }} />
}

/** A swing window. */
export function Band({
  from,
  to,
  label,
  attention = false,
}: {
  from: string
  to: string
  label: string
  attention?: boolean
}): React.ReactNode {
  const axis = useAxis()
  const { left, width, clipped } = extent(axis, from, to)
  return (
    <div
      className={[
        'track__band',
        attention ? 'track__band--attention' : '',
        clipped ? 'track__band--clipped' : '',
      ]
        .filter(Boolean)
        .join(' ')}
      style={{ left, width }}
    >
      <span className="track__band-label">{label}</span>
    </div>
  )
}

/** One person's leg of a slot. `open` is a slot with nobody in it at all. */
export function Leg({
  from,
  to,
  tone = 'normal',
  children,
}: {
  from: string
  to: string
  tone?: 'normal' | 'attention' | 'unknown' | 'open'
  children: React.ReactNode
}): React.ReactNode {
  const axis = useAxis()
  const { left, width } = extent(axis, from, to)
  return (
    <div className={`track__leg track__leg--${tone}`} style={{ left, width }}>
      {children}
    </div>
  )
}

/**
 * The stretch of a leg after a mandatory holding lapses, and the tick marking the day it happens.
 *
 * This is the ruler earning its place: the gap report can say "expiring", but only a position on
 * the axis says the vessel is uncovered from the 13th to the 16th.
 */
export function Lapse({
  expiry,
  to,
  label,
}: {
  expiry: string
  to: string
  label: string
}): React.ReactNode {
  const axis = useAxis()
  const { left, width } = extent(axis, expiry, to)
  return (
    <>
      <div className="track__lapse" style={{ left, width }} />
      <div className="track__expiry" style={{ left }}>
        <span className="track__expiry-label">{label}</span>
      </div>
    </>
  )
}

/** The join between two legs of a handover. */
export function Seam({ date }: { date: string }): React.ReactNode {
  const axis = useAxis()
  return <div className="track__seam" style={{ left: percent(fraction(axis, date)) }} />
}
