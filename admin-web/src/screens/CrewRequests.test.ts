import { describe, expect, it } from 'vitest'
import { actionLabel, kindLabel } from './CrewRequests'

/**
 * ADM-11's labels.
 *
 * Small, but the wording carries a rule rather than a preference: a crew statement is a claim the
 * office has not yet confirmed, and every label on this screen has to keep saying so. A row that
 * read "Course booked" would state as fact the thing the coordinator is being asked to check —
 * and, worse, would read as though the requirement were dealt with, which is precisely what a
 * statement does not do (AUTH-1).
 */
describe('crew request labels', () => {
  it('reports what the crew member said as speech, never as fact', () => {
    expect(kindLabel('course_booked')).toBe('Says a course is booked')
    expect(kindLabel('help_requested')).toBe('Needs help arranging it')
  })

  it('degrades to the raw wire value for a kind this revision does not know', () => {
    // Five more operation types are coming (docs/handoff/mobile-crew-app-backend.md §1), and both
    // revisions are live during an expand/contract deploy. A row must not go blank.
    expect(kindLabel('course_seat_request')).toBe('course_seat_request')
  })

  it('names the positive action for what the coordinator actually did', () => {
    // "Confirm" against a claim, "Arranged" against a request. Neither is "Resolve": nothing here
    // resolves a compliance state, and a shared verb would suggest it did.
    expect(actionLabel('course_booked')).toBe('Confirm')
    expect(actionLabel('help_requested')).toBe('Arranged')
  })
})
