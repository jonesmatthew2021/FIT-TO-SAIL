import { describe, expect, it } from 'vitest'
import { render } from '@testing-library/react'
import { Band, Cut, Lapse, Leg, Ruler, RulerRow, Seam } from './Ruler'

/**
 * The ruler's geometry, pinned.
 *
 * Every value here is a percentage the eye reads as a date, so a wrong divisor is not a visual
 * glitch — it is the screen asserting that a certificate lapses on a day it does not. These tests
 * are the reason the axis is derived from `epochDay` and never from a `Date`.
 */

/**
 * The dev fixture's UNI CC24: 20 Jul to 16 Aug, today six days in.
 *
 * That is 27 intervals and **28 days**, and 28 is the divisor for every position: a calendar window
 * includes its last day (§4.2), so a bar has to cover its own end date. Drawing windows as the
 * point-to-point difference dropped that day, which put a one-day hole between the two legs of a
 * fully covered handover — the exact thing this screen exists to find. Every share below is
 * therefore out of 28.
 */
const SWING = { from: '2026-07-20', to: '2026-08-16', today: '2026-07-26' }

function renderRuler(children: React.ReactNode, axis = SWING) {
  return render(
    <Ruler
      from={axis.from}
      to={axis.to}
      today={axis.today}
      labelHeader="Slot"
      valueHeader="State"
      markCount={5}
    >
      {children}
    </Ruler>,
  )
}

function style(container: HTMLElement, selector: string): CSSStyleDeclaration {
  const element = container.querySelector<HTMLElement>(selector)
  if (element === null) throw new Error(`No ${selector} rendered`)
  return element.style
}

/**
 * A positioned edge as a number.
 *
 * Compared numerically rather than as a string: jsdom re-serialises `0.000%` to `0%`, and pinning
 * the CSS spelling would make these tests fail on a formatting change that moves nothing.
 */
function edge(container: HTMLElement, selector: string, side: 'left' | 'width'): number {
  return Number.parseFloat(style(container, selector)[side])
}

/** The same fraction the component should have computed, as a percentage of the axis's days. */
function share(days: number, span = 28): number {
  return (days / span) * 100
}

describe('Ruler', () => {
  it('places the datum at today, as a fraction of the axis', () => {
    const { container } = renderRuler(<RulerRow label="01" value="" dates="" children={null} />)
    // 26 Jul is the seventh of the axis's 28 days, so its day *starts* six days in.
    expect(style(container, '.ruler__datum').getPropertyValue('--ruler-at')).toBe(String(6 / 28))
  })

  it('omits the datum when today is off the axis', () => {
    // A line pinned to an end it has run off would claim a position it does not have. The
    // dashboard axis always includes today; a planner axis for a past swing does not.
    const { container } = renderRuler(<RulerRow label="01" value="" dates="" children={null} />, {
      ...SWING,
      today: '2026-09-01',
    })
    expect(container.querySelector('.ruler__datum')).toBeNull()
  })

  it('spans a leg across the whole swing', () => {
    const { container } = renderRuler(
      <RulerRow label="01" value="" dates="">
        <Leg from={SWING.from} to={SWING.to}>
          <span>Ada Nakamura</span>
        </Leg>
      </RulerRow>,
    )
    expect(edge(container, '.track__leg', 'left')).toBeCloseTo(0, 3)
    expect(edge(container, '.track__leg', 'width')).toBeCloseTo(100, 3)
  })

  it('splits a handover into two legs that meet, with a seam at the join', () => {
    // The fixture's slot 5: Dev Ramaswamy to 2 Aug, Eve Lindqvist from 3 Aug. The slot is fully
    // covered, so the two legs have to *touch* — Dev's leg has to include the 2nd.
    const { container } = renderRuler(
      <RulerRow label="05" value="" dates="">
        <Leg from="2026-07-20" to="2026-08-02">
          <span>Dev Ramaswamy</span>
        </Leg>
        <Seam date="2026-08-03" />
        <Leg from="2026-08-03" to="2026-08-16">
          <span>Eve Lindqvist</span>
        </Leg>
      </RulerRow>,
    )
    const legs = container.querySelectorAll<HTMLElement>('.track__leg')
    expect(legs).toHaveLength(2)
    // 20 Jul – 2 Aug inclusive is 14 days; 3 Aug – 16 Aug inclusive is the other 14. No hole.
    expect(Number.parseFloat(legs[0]?.style.left ?? '')).toBeCloseTo(0, 3)
    expect(Number.parseFloat(legs[0]?.style.width ?? '')).toBeCloseTo(share(14), 3)
    expect(Number.parseFloat(legs[1]?.style.left ?? '')).toBeCloseTo(share(14), 3)
    expect(Number.parseFloat(legs[1]?.style.width ?? '')).toBeCloseTo(share(14), 3)
    expect(edge(container, '.track__seam', 'left')).toBeCloseTo(share(14), 3)
  })

  it('draws the lapse from the expiry to the end of the leg', () => {
    // Bruno Oyelaran's MS-02 expires 13 Aug: three of the swing's days are uncovered, and that
    // is the fact the old slot table could only render as the word "expiring".
    const { container } = renderRuler(
      <RulerRow label="02" value="" dates="">
        <Leg from={SWING.from} to={SWING.to}>
          <span>Bruno Oyelaran</span>
        </Leg>
        <Lapse expiry="2026-08-13" to={SWING.to} label="MS-02 lapses 13 Aug" />
      </RulerRow>,
    )
    expect(edge(container, '.track__lapse', 'left')).toBeCloseTo(share(24), 3)
    expect(edge(container, '.track__lapse', 'width')).toBeCloseTo(share(4), 3)
    expect(edge(container, '.track__expiry', 'left')).toBeCloseTo(share(24), 3)
  })

  it('clamps a cutoff that falls before the axis starts', () => {
    // A passed cutoff on the planner's own swing axis sits at the left edge rather than at a
    // negative offset that would render outside the track.
    const { container } = renderRuler(
      <RulerRow label="01" value="" dates="">
        <Cut date="2026-07-13" label="Cutoff 13 Jul (passed)" />
      </RulerRow>,
    )
    expect(edge(container, '.track__cut', 'left')).toBeCloseTo(0, 3)
  })

  it('marks a band that runs past the end of the axis as clipped', () => {
    const { container } = renderRuler(
      <RulerRow label="CEN" value="" dates="">
        <Band from="2026-08-01" to="2026-10-04" label="CC09" />
      </RulerRow>,
    )
    const band = container.querySelector('.track__band')
    expect(band?.className).toContain('track__band--clipped')
    // 1 Aug is 12 days in, and the bar runs to the axis's own end: 16 of the 28 days.
    expect(edge(container, '.track__band', 'width')).toBeCloseTo(share(16), 3)
  })

  it('carries the row dates for the narrow-screen fallback', () => {
    // Below 900px the stylesheet hides the bars and renders this attribute instead, so it has to
    // say everything the geometry did.
    const { container } = renderRuler(
      <RulerRow
        label="02"
        value=""
        dates="Bruno Oyelaran 20 Jul – 16 Aug · MS-02 lapses 13 Aug"
        attention
      >
        <Leg from={SWING.from} to={SWING.to}>
          <span>Bruno Oyelaran</span>
        </Leg>
      </RulerRow>,
    )
    const track = container.querySelector('.track')
    expect(track?.getAttribute('data-dates')).toBe(
      'Bruno Oyelaran 20 Jul – 16 Aug · MS-02 lapses 13 Aug',
    )
    expect(track?.hasAttribute('data-attention')).toBe(true)
  })

  it('labels the axis with dates that are on it, and never past its ends', () => {
    // Today is off-axis here, so no tick is suppressed and all five are drawn.
    const { container } = renderRuler(<RulerRow label="01" value="" dates="" children={null} />, {
      ...SWING,
      today: '2026-09-01',
    })
    const marks = [...container.querySelectorAll('.ruler__mark span')].map((node) => node.textContent)
    expect(marks).toEqual(['20 Jul', '27 Jul', '3 Aug', '9 Aug', '16 Aug'])
  })

  it('drops an axis tick that would sit under the Today pill', () => {
    // 27 Jul is one day from the datum: its label would print through the pill, and two dates
    // overlapping reads worse than one date missing.
    const { container } = renderRuler(<RulerRow label="01" value="" dates="" children={null} />)
    const marks = [...container.querySelectorAll('.ruler__mark span')].map((node) => node.textContent)
    expect(marks).not.toContain('27 Jul')
    // The ends still anchor the axis, and the end markers move to whatever survives.
    expect(marks[0]).toBe('20 Jul')
    expect(marks.at(-1)).toBe('16 Aug')
    expect(container.querySelector('.ruler__mark--end')?.textContent).toBe('16 Aug')
  })

  it('refuses to position a part outside a Ruler', () => {
    // The axis comes from context; rendering a bar without one would silently place it at zero.
    expect(() => render(<Seam date="2026-07-26" />)).toThrow(/inside a <Ruler>/)
  })
})
