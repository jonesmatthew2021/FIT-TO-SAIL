import { describe, expect, it } from 'vitest'
import { validateHolding } from './PersonDetail'

/**
 * These mirror `HoldingService.validate` in the backend. They are advisory (AUTH-1) — the server
 * enforces the same rules and its answer wins — so the value of the test is that the two stay in
 * step, and that a mistake is caught before a round trip rather than instead of one.
 */
describe('holding validation (advisory)', () => {
  it('requires an expiry date for an expiring holding', () => {
    expect(validateHolding('held_expiry', '', null)).toMatch(/must carry an expiry date/)
    expect(validateHolding('held_expiry', '2027-06-30', null)).toBeNull()
  })

  it('refuses an expiry date on a holding that cannot expire', () => {
    expect(validateHolding('held_perpetual', '2027-06-30', null)).toMatch(/Only a/)
    expect(validateHolding('not_held', '2027-06-30', null)).toMatch(/Only a/)
    expect(validateHolding('unknown', '2027-06-30', null)).toMatch(/Only a/)
  })

  it('accepts the non-expiring statuses with no dates', () => {
    expect(validateHolding('held_perpetual', '', '2020-01-01')).toBeNull()
    expect(validateHolding('not_held', null, null)).toBeNull()
    expect(validateHolding('unknown', null, null)).toBeNull()
  })

  it('refuses an issue date after the expiry date', () => {
    expect(validateHolding('held_expiry', '2027-06-30', '2027-07-01')).toMatch(/after the expiry/)
    expect(validateHolding('held_expiry', '2027-06-30', '2027-06-30')).toBeNull()
  })
})
