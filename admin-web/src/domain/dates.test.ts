import { describe, expect, it } from 'vitest'
import {
  dateFromEpochDay,
  daysBetween,
  epochDay,
  formatDate,
  formatDateRange,
  formatDayMonth,
  formatShortRange,
  relativeDays,
} from './dates'

/**
 * NFR-5 / O-11. These tests exist because the natural JavaScript spelling of every one of these
 * operations is wrong for calendar dates, and wrong only for some viewers, in some timezones,
 * for part of the day — the hardest kind of bug to see in review.
 */
describe('calendar dates', () => {
  it('parses a calendar date without a timezone', () => {
    // The trap: `new Date('2026-07-26').getDate()` is 25 for any viewer west of Greenwich,
    // because the string parses as UTC midnight and then renders locally.
    expect(epochDay('2026-07-26')).toBe(Date.UTC(2026, 6, 26) / 86_400_000)
  })

  it('is unaffected by the browser timezone', () => {
    // epochDay never constructs a local Date, so the result is a pure function of the string.
    // Recomputing it by hand is the point: no Date-in-local-zone appears anywhere.
    const days = epochDay('2026-01-01')
    expect(days).toBe(20_454)
    expect(epochDay('2026-01-02') - days).toBe(1)
  })

  it('counts whole days across a month and a leap day', () => {
    expect(daysBetween('2026-07-26', '2026-08-26')).toBe(31)
    expect(daysBetween('2028-02-28', '2028-03-01')).toBe(2)
    expect(daysBetween('2027-02-28', '2027-03-01')).toBe(1)
  })

  it('counts backwards for a past date', () => {
    expect(daysBetween('2026-07-26', '2026-07-19')).toBe(-7)
  })

  it('rejects anything that is not a calendar date', () => {
    expect(() => epochDay('2026-07-26T00:00:00Z')).toThrow(/Not a calendar date/)
  })

  it('formats day-first with no leading zero', () => {
    expect(formatDate('2027-06-30')).toBe('30 Jun 2027')
    expect(formatDate('2027-01-05')).toBe('5 Jan 2027')
    expect(formatDate(null)).toBe('—')
    expect(formatDate(undefined)).toBe('—')
  })

  it('formats a range', () => {
    expect(formatDateRange('2026-07-26', '2026-08-09')).toBe('26 Jul 2026 – 9 Aug 2026')
  })

  it('round-trips epoch days back to a calendar date', () => {
    // The ruler labels its own axis at computed intervals, so this inverse has to be exact — an
    // off-by-one here would put every tick a day out and nothing would look obviously wrong.
    for (const date of ['2026-01-01', '2026-07-26', '2026-12-31', '2028-02-29']) {
      expect(dateFromEpochDay(epochDay(date))).toBe(date)
    }
  })

  it('formats a day and month without the year', () => {
    expect(formatDayMonth('2026-08-16')).toBe('16 Aug')
    expect(formatDayMonth('2026-01-05')).toBe('5 Jan')
    expect(formatShortRange('2026-07-20', '2026-08-16')).toBe('20 Jul – 16 Aug')
  })

  it('describes days remaining against the business date, not the browser clock', () => {
    expect(relativeDays('2026-07-26', '2026-07-26')).toBe('today')
    expect(relativeDays('2026-07-26', '2026-07-27')).toBe('tomorrow')
    expect(relativeDays('2026-07-26', '2026-07-25')).toBe('yesterday')
    expect(relativeDays('2026-07-26', '2026-10-24')).toBe('in 90 days')
    expect(relativeDays('2026-07-26', '2026-06-26')).toBe('30 days ago')
  })
})
