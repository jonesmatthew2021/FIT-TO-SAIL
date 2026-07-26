import { describe, expect, it } from 'vitest'
import {
  CELL_STATE_ORDER,
  REGISTER_OPEN_STATUSES,
  REGISTER_OUTCOMES,
  REQUIREMENT_CATEGORIES,
  cellState,
  cellStateRank,
  needsAttention,
  registerStatusTone,
} from './enums'

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

/**
 * The catalogue and register enumerations are duplicated from the backend by necessity — the
 * editors have to offer them before the server can reject a wrong one. These tests are what stops
 * the copy drifting silently; the ITs assert the same values on the server side.
 */
describe('catalogue and register enumerations', () => {
  it('offers exactly the Appendix A requirement categories', () => {
    expect([...REQUIREMENT_CATEGORIES]).toEqual(['QL', 'VS', 'PS', 'MS', 'CS', 'HR', 'PT', 'VI', 'PI'])
  })

  it('never offers a closed status as a transition', () => {
    // Closing carries an outcome and, for an approval, a window that §5.1 reads. Reaching
    // "Closed - Approved" through a bare status change would produce an exemption with no dates.
    for (const status of REGISTER_OPEN_STATUSES) {
      expect(status.startsWith('Closed')).toBe(false)
    }
    for (const outcome of REGISTER_OUTCOMES) {
      expect(REGISTER_OPEN_STATUSES).not.toContain(`Closed - ${outcome}`)
    }
  })

  it('tones open work for attention and an approval as good, without colouring a refusal as a fault', () => {
    expect(registerStatusTone('Open - PW')).toBe('caution')
    expect(registerStatusTone('Closed - Approved')).toBe('good')
    expect(registerStatusTone('Closed - Not Approved')).toBe('muted')
    expect(registerStatusTone('Complete before joining')).toBe('neutral')
  })
})
