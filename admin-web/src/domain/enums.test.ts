import { describe, expect, it } from 'vitest'
import { CELL_STATE_ORDER, cellState, cellStateRank, needsAttention } from './enums'

/** Appendix A is normative; a state the UI does not recognise must still render legibly. */
describe('cell state presentation', () => {
  it('covers every Appendix A cell state', () => {
    const appendixA = [
      'ok',
      'expiring',
      'gap',
      'exempt',
      'pending',
      'unknown',
      'review',
      'quota_only',
      'na',
      'recommended',
    ]
    expect([...CELL_STATE_ORDER].sort()).toEqual([...appendixA].sort())
    for (const state of appendixA) {
      expect(cellState(state).label).not.toBe(state)
    }
  })

  it('orders worst first, matching the engine gap-report order', () => {
    expect(CELL_STATE_ORDER.slice(0, 6)).toEqual([
      'gap',
      'expiring',
      'unknown',
      'review',
      'pending',
      'exempt',
    ])
    expect(cellStateRank('gap')).toBeLessThan(cellStateRank('ok'))
    expect(cellStateRank('ok')).toBeLessThan(cellStateRank('na'))
  })

  it('degrades to the wire value for a state it has never seen', () => {
    // A backend that adds a state must not blank the cell that carries it.
    expect(cellState('newly_invented').label).toBe('newly_invented')
    expect(cellStateRank('newly_invented')).toBe(CELL_STATE_ORDER.length)
  })

  it('flags exactly the states that need someone to act', () => {
    expect(CELL_STATE_ORDER.filter(needsAttention)).toEqual([
      'gap',
      'expiring',
      'unknown',
      'review',
    ])
  })
})
