/**
 * Calendar-date arithmetic.
 *
 * NFR-5 / O-11: business dates are **calendar dates in the operating timezone** (AWST), not
 * instants. Two consequences the whole app depends on:
 *
 *  - Nothing here constructs a `Date` from a date-only string via `new Date('2026-07-26')`. That
 *    parses as UTC midnight and then renders in the browser's zone, which is the day before for
 *    anyone west of Greenwich — a silent off-by-one in every expiry countdown.
 *  - Nothing here knows today's date. "Today" is the server's business date, delivered by
 *    `GET /api/v1/session`, and is passed in. A browser clock in Perth, London or a CI runner
 *    must not change what the screen says.
 */

const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/

/** Whole days since the epoch for a `YYYY-MM-DD` string, timezone-free. */
export function epochDay(isoDate: string): number {
  const match = ISO_DATE.exec(isoDate)
  if (match === null) throw new Error(`Not a calendar date: ${isoDate}`)
  const [, year, month, day] = match
  return Date.UTC(Number(year), Number(month) - 1, Number(day)) / 86_400_000
}

/** `to - from` in whole days. Negative when `to` is in the past. */
export function daysBetween(from: string, to: string): number {
  return epochDay(to) - epochDay(from)
}

export function isBefore(a: string, b: string): boolean {
  return epochDay(a) < epochDay(b)
}

export function isOnOrAfter(a: string, b: string): boolean {
  return epochDay(a) >= epochDay(b)
}

const MONTHS = [
  'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
  'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
]

/** `2027-06-30` → `30 Jun 2027`. Day-first, as every Australian maritime document is. */
export function formatDate(isoDate: string | null | undefined): string {
  if (isoDate === null || isoDate === undefined || isoDate === '') return '—'
  const match = ISO_DATE.exec(isoDate)
  if (match === null) return isoDate
  const [, year, month, day] = match
  return `${Number(day)} ${MONTHS[Number(month) - 1]} ${year}`
}

/** `30 Jun 2027 – 14 Jul 2027`, collapsing a shared month or year. */
export function formatDateRange(from: string, to: string): string {
  return `${formatDate(from)} – ${formatDate(to)}`
}

/**
 * A days-remaining phrase relative to the business date. `today` is always the server's, never
 * the browser's.
 */
export function relativeDays(today: string, target: string): string {
  const days = daysBetween(today, target)
  if (days === 0) return 'today'
  if (days === 1) return 'tomorrow'
  if (days === -1) return 'yesterday'
  if (days > 0) return `in ${days} days`
  return `${Math.abs(days)} days ago`
}
