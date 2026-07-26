import { describe, expect, it } from 'vitest'
import {
  CELL_STATE_ORDER,
  QUEUE_STATUSES,
  REGISTER_OPEN_STATUSES,
  REGISTER_OUTCOMES,
  REQUIREMENT_CATEGORIES,
  cellState,
  cellStateRank,
  confidenceTone,
  needsAttention,
  notificationKind,
  registerStatusTone,
  verificationStatus,
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

/**
 * The §8 and §9 enumerations, added with ADM-8 and ADM-9.
 *
 * Same reasoning as above: these are copies of Appendix A and of `NotificationKind`, and the copy is
 * what lets a filter offer a status before the server has sent one. What is tested is the two places
 * where the presentation makes a *judgement* rather than a translation — an auto-acceptance still
 * wanting a human's eye, and a confidence band — because those are the ones a later tidy-up would
 * quietly get wrong.
 */
describe('evidence and notification enumerations', () => {
  it('labels every verification status Appendix A defines', () => {
    for (const status of [
      'pending_extraction',
      'pending_review',
      'auto_accepted',
      'verified',
      'rejected',
    ]) {
      expect(verificationStatus(status).label).not.toBe(status)
    }
  })

  it('does not colour an auto-acceptance as finished work', () => {
    // §8 stage 4 surfaces auto-acceptances "for retrospective spot-checking". Toning one `good`
    // would tell a reviewer to skip the row the spec put in front of them on purpose.
    expect(verificationStatus('auto_accepted').tone).toBe('caution')
    expect(verificationStatus('verified').tone).toBe('good')
    expect(verificationStatus('rejected').tone).toBe('critical')
  })

  it('offers the queue filters in the order a reviewer works them', () => {
    expect(QUEUE_STATUSES[0]).toBe('pending_review')
    expect([...QUEUE_STATUSES]).toContain('auto_accepted')
  })

  it('bands confidence, and treats "not read" as absence rather than as low', () => {
    // Zero is the unconfigured extractor's answer for every field (§14.5). Rendering it as a red
    // "0%" would read as "the model looked and disagreed", which is the opposite of what happened.
    expect(confidenceTone(0)).toBe('muted')
    expect(confidenceTone(0.4)).toBe('critical')
    expect(confidenceTone(0.8)).toBe('caution')
    expect(confidenceTone(0.95)).toBe('good')
  })

  it('labels back-office notification kinds as well as crew ones', () => {
    expect(notificationKind('expiry_warning').label).toBe('Expiry')
    expect(notificationKind('quota_shortfall').tone).toBe('critical')
    expect(notificationKind('matrix_published').tone).toBe('neutral')
  })

  it('degrades to the raw wire value for a kind this revision does not know', () => {
    // Both revisions are live during an expand/contract deploy, so a newer kind must not blank a row.
    expect(notificationKind('something_new_entirely').label).toBe('something_new_entirely')
    expect(verificationStatus('something_new').label).toBe('something_new')
  })
})
