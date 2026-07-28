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
 * ### Shape
 *
 * A three-column grid — label, lane, value — repeated for the header row and for every data row, so
 * a lane's percentages line up down the whole panel. The panel scrolls horizontally and every row
 * carries a `min-width`: below about 880px the tick labels collide and a `nowrap` bar escapes its
 * lane, and the page itself must never scroll sideways.
 *
 * ### Four rules, each of which cost something to learn
 *
 *  - **Only dates sit on the axis.** Counts, states and scores live in the value column. The moment
 *    a cell count is given a horizontal position, the axis stops meaning one thing.
 *  - **Nothing here knows today.** The datum is drawn at the `today` passed in, which is the
 *    server's business date from `GET /api/v1/session` (NFR-5, O-11). No `new Date()` anywhere in
 *    this file — a planner in London must not shift the line.
 *  - **The datum is drawn per row, not once across the panel.** A single line positioned against
 *    the panel's own box slides off its lane the moment the panel scrolls, which is exactly what a
 *    horizontally scrolling ruler does. Each row draws its own inside its lane.
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
  /** Today's fraction of the axis, or null when today is off it. */
  readonly datum: number | null
}

const AxisContext = createContext<Axis | null>(null)

function useAxis(): Axis {
  const axis = useContext(AxisContext)
  if (axis === null) throw new Error('Ruler parts must be rendered inside a <Ruler>')
  return axis
}

/**
 * The lane's arithmetic, in two parts, because a calendar range has two lengths.
 *
 * `steps` is the number of *intervals* between the end dates, which is what tick dates interpolate
 * over. `cells` is the number of *days*, which is what every position divides by — because a
 * calendar window **includes its last day** (§4.2: a swing runs from its start date to its end date,
 * both inclusive).
 *
 * Getting that wrong is not cosmetic. Drawing a window as the point-to-point span `to − from` drops
 * its final day, so the two legs of a handover — one ending the 2nd, the next starting the 3rd —
 * came out with a one-day hole between them, which on a slot that is fully covered reads as exactly
 * the coverage gap this screen exists to find.
 */
function steps(axis: { from: string; to: string }): number {
  // A zero-width axis would divide by zero; one interval is the smallest span that can be drawn.
  return Math.max(1, epochDay(axis.to) - epochDay(axis.from))
}

function cells(axis: { from: string; to: string }): number {
  return steps(axis) + 1
}

/** Where a date's own day *starts* along the axis, 0–1, clamped to the ends. */
function fraction(axis: { from: string; to: string }, date: string): number {
  const offset = epochDay(date) - epochDay(axis.from)
  return Math.min(1, Math.max(0, offset / cells(axis)))
}

function percent(value: number): string {
  return `${(value * 100).toFixed(3)}%`
}

/**
 * `left`/`width` for an inclusive window, plus whether it was cut off by the end of the axis.
 *
 * The right edge is the start of the day *after* `to`, which is what makes the window cover its own
 * last day and makes two consecutive windows meet exactly.
 */
function extent(
  axis: Axis,
  from: string,
  to: string,
): { readonly left: string; readonly width: string; readonly clipped: boolean } {
  const start = fraction(axis, from)
  const end = Math.min(1, Math.max(0, (epochDay(to) + 1 - epochDay(axis.from)) / cells(axis)))
  return {
    left: percent(start),
    width: percent(Math.max(0, end - start)),
    clipped: epochDay(to) > epochDay(axis.to),
  }
}

/**
 * How close to the datum a tick may sit before its label collides with the "Today" pill, as a
 * fraction of the axis. The pill is about 60px wide against lanes of 420–900px.
 */
const DATUM_CLEARANCE = 0.06

/**
 * Past this point along the lane a label is anchored to the *left* of its tick, so it cannot run
 * off the lane's right edge into the value column. Both the cutoff tick and the lapse marker use
 * it: which side has room is decided by the lane, since both labels sit in the clear lane above the
 * bars and cannot collide with one.
 */
const LABEL_FLIP = 0.55

/**
 * Evenly spaced axis labels.
 *
 * Derived rather than passed in, so no caller has to invent tick dates — and so a screen cannot
 * accidentally label the axis with dates that are not on it.
 *
 * A tick too near the datum is dropped rather than drawn under the "Today" pill: two dates
 * overlapping is worse than one date missing, and the pill already names its own position.
 */
function axisMarks(
  axis: { from: string; to: string },
  count: number,
  datum: string | null,
): readonly string[] {
  const start = epochDay(axis.from)
  const span = steps(axis)
  const ticks = Math.max(2, Math.min(count, span + 1))
  const marks = Array.from({ length: ticks }, (_, index) =>
    dateFromEpochDay(start + Math.round((span * index) / (ticks - 1))),
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
  variant = 'axis',
  children,
}: {
  from: string
  to: string
  today: string
  labelHeader: string
  valueHeader: string
  markCount?: number
  /** `slots` narrows the label column and widens the value column for ADM-2's chips and actions. */
  variant?: 'axis' | 'slots'
  children: React.ReactNode
}): React.ReactNode {
  const todayDay = epochDay(today)
  // The datum is drawn only when today is actually on the axis: a line pinned to an end it has run
  // off would claim a position it does not have.
  const onAxis = todayDay >= epochDay(from) && todayDay <= epochDay(to)
  const axis: Axis = { from, to, datum: onAxis ? fraction({ from, to }, today) : null }
  const marks = axisMarks({ from, to }, markCount, onAxis ? today : null)

  return (
    <AxisContext.Provider value={axis}>
      <div className={variant === 'slots' ? 'ruler ruler--slots' : 'ruler'}>
        <div className="ruler__head-row">
          <div className="ruler__head">{labelHeader}</div>

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

            {axis.datum !== null && (
              <div className="ruler__datum-tag" style={{ left: percent(axis.datum) }}>
                Today
              </div>
            )}
          </div>

          <div className="ruler__head">{valueHeader}</div>
        </div>

        {children}
      </div>
    </AxisContext.Provider>
  )
}

/**
 * One row: a label, a track, and a value column.
 *
 * `dates` is the row's own window in words. It is what the track becomes on a narrow screen, and it
 * is also the row's accessible description — the bars themselves are decorative, given the value
 * column already carries the states as text.
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
  const axis = useAxis()

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
        {axis.datum !== null && (
          <div
            className="ruler__datum"
            style={{ '--ruler-at': axis.datum } as React.CSSProperties}
            aria-hidden="true"
          />
        )}
        {children}
      </div>

      <div className="ruler__value">{value}</div>
    </div>
  )
}

/**
 * The cutoff, as a dashed tick with its label in the clear lane above the bars.
 *
 * The cutoff is swing start − 7 days and is **stored, not derived** (§4.2) — every caller reads it
 * from the record. `tight` is the ≤3-day callout, which is the row to act on today.
 */
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
  const at = fraction(axis, date)
  return (
    <div
      className={[
        'track__cut',
        tight ? 'track__cut--tight' : '',
        at > LABEL_FLIP ? 'track__cut--left' : '',
      ]
        .filter(Boolean)
        .join(' ')}
      style={{ left: percent(at) }}
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

/**
 * A swing window.
 *
 * `evaluating` is not a skeleton: a placeholder shaped like a dated bar would be asserting dates it
 * does not have. It is the same bar with a dashed edge and no colour claim, beside an "Evaluating…"
 * chip in the value column.
 */
export function Band({
  from,
  to,
  label,
  tone = 'clean',
}: {
  from: string
  to: string
  label: string
  tone?: 'clean' | 'attention' | 'evaluating'
}): React.ReactNode {
  const axis = useAxis()
  const { left, width, clipped } = extent(axis, from, to)
  return (
    <div
      className={[
        'track__band',
        tone === 'clean' ? '' : `track__band--${tone}`,
        clipped ? 'track__band--clipped' : '',
      ]
        .filter(Boolean)
        .join(' ')}
      style={{ left, width }}
    >
      {label}
    </div>
  )
}

/**
 * One person's leg of a slot. `open` is a slot with nobody in it at all.
 *
 * `segment` shapes a handover: the legs of one slot meet at the changeover date with their inner
 * corners square and no border between them, so the pair reads as one slot filled by two people
 * rather than as two slots.
 */
export function Leg({
  from,
  to,
  tone = 'normal',
  segment = 'solo',
  children,
}: {
  from: string
  to: string
  tone?: 'normal' | 'attention' | 'unknown' | 'open'
  segment?: 'solo' | 'first' | 'middle' | 'last'
  children: React.ReactNode
}): React.ReactNode {
  const axis = useAxis()
  const { left, width } = extent(axis, from, to)
  return (
    <div
      className={[
        'track__leg',
        tone === 'normal' ? '' : `track__leg--${tone}`,
        segment === 'solo' ? '' : `track__leg--${segment}`,
      ]
        .filter(Boolean)
        .join(' ')}
      style={{ left, width }}
    >
      {children}
    </div>
  )
}

/**
 * The day a mandatory holding lapses, and the stretch of the leg after it.
 *
 * This is the ruler earning its place: the gap report can say "expiring", but only a position on
 * the axis says the vessel is uncovered from the 13th to the 16th. The line and its label are the
 * marker; the wash behind them is the uncovered stretch, which is the part a table cannot carry at
 * all.
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
  const at = fraction(axis, expiry)
  return (
    <>
      <div className="track__lapse" style={{ left, width }} />
      <div className="track__expiry" style={{ left }}>
        <span
          className={
            at > LABEL_FLIP
              ? 'track__expiry-label track__expiry-label--left'
              : 'track__expiry-label'
          }
        >
          {label}
        </span>
      </div>
    </>
  )
}

/** The join between two legs of a handover: a 2px accent seam, past the bar top and bottom. */
export function Seam({ date }: { date: string }): React.ReactNode {
  const axis = useAxis()
  return <div className="track__seam" style={{ left: percent(fraction(axis, date)) }} />
}
