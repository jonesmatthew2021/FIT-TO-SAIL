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

  it('tones open work by whose move it is, without colouring a refusal as a fault', () => {
    // PW and OPS are the parties who act, so their queues are warm; an MRL query is waiting on a
    // reading rather than on a decision. A refusal is a decision and stays muted.
    expect(registerStatusTone('Open - PW')).toBe('warning')
    expect(registerStatusTone('Open - OPS')).toBe('warning')
    expect(registerStatusTone('Open - MRL')).toBe('caution')
    expect(registerStatusTone('Closed - Approved')).toBe('good')
    expect(registerStatusTone('Closed - Not Approved')).toBe('muted')
    expect(registerStatusTone('Complete before joining')).toBe('muted')
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

  it('separates a decided document from one still waiting on a person', () => {
    // Colour tracks the document's state, not a standing reminder: auto-accepted and verified are
    // both decided, and what pulls up the auto-accepted set for §8 stage 4's retrospective
    // spot-check is the queue's own status filter.
    expect(verificationStatus('auto_accepted').tone).toBe('good')
    expect(verificationStatus('verified').tone).toBe('good')
    expect(verificationStatus('pending_review').tone).toBe('caution')
    expect(verificationStatus('pending_extraction').tone).toBe('muted')
    expect(verificationStatus('rejected').tone).toBe('critical')
  })

  it('offers the queue filters in the order a reviewer works them', () => {
    expect(QUEUE_STATUSES[0]).toBe('pending_review')
    expect([...QUEUE_STATUSES]).toContain('auto_accepted')
  })

  it('bands confidence, and treats "not read" as a field to fill rather than as a low reading', () => {
    // Zero is the unconfigured extractor's answer for every field (§14.5). It renders as "no
    // reading" in a warning tone: not red, because the model did not look and disagree — but not
    // silent either, because an empty field is the one thing on the panel that will not fill itself.
    expect(confidenceTone(0)).toBe('warning')
    expect(confidenceTone(0.4)).toBe('critical')
    expect(confidenceTone(0.62)).toBe('caution')
    expect(confidenceTone(0.87)).toBe('good')
  })

  it('labels back-office notification kinds as well as crew ones', () => {
    expect(notificationKind('expiry_warning').label).toBe('Expiry')
    expect(notificationKind('quota_shortfall').tone).toBe('critical')
    // A published matrix is information, not work: nobody has to do anything about having been told.
    expect(notificationKind('matrix_published').tone).toBe('muted')
  })

  it('degrades to the raw wire value for a kind this revision does not know', () => {
    // Both revisions are live during an expand/contract deploy, so a newer kind must not blank a row.
    expect(notificationKind('something_new_entirely').label).toBe('something_new_entirely')
    expect(verificationStatus('something_new').label).toBe('something_new')
  })
})
