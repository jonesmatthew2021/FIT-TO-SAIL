import { describe, expect, it } from 'vitest'
import { PARSE_FAILED, parseValue, renderValue } from './Administration'
import { suggestLabel } from './Matrix'
import { internalLink } from './Notifications'
import { isCorrection } from './Evidence'
import type { ConfigSetting, EvidenceDocument } from '../api/client'

/**
 * The pure logic behind ADM-3, ADM-8, ADM-9 and ADM-10.
 *
 * Everything tested here is a decision the screens make *about* the server's answers rather than a
 * compliance answer of their own (AUTH-1). Two of them are load-bearing for reasons that are not
 * obvious from reading the code:
 *
 *  - [parseValue] decides whether a setting reaches the server as a number or a string. Getting it
 *    wrong produces a type error about a value the user typed perfectly correctly.
 *  - [internalLink] decides whether a server-provided string becomes an `href`. Getting it wrong
 *    turns a stored value into an open redirect.
 */

function setting(overrides: Partial<ConfigSetting>): ConfigSetting {
  return {
    key: 'expiry.lead-days',
    kind: 'number',
    description: '',
    value: 90,
    defaultValue: 90,
    overridden: false,
    updatedAt: null,
    updatedBy: null,
    ...overrides,
  } as ConfigSetting
}

describe('configuration values (ADM-10)', () => {
  it('sends a number as a number, not as a string', () => {
    // The server validates `expiry.lead-days` as a whole number, so "45" would be rejected with a
    // message about a value the user got right.
    expect(parseValue(setting({}), '45')).toBe(45)
    expect(parseValue(setting({}), ' 45 ')).toBe(45)
  })

  it('treats an empty field as clearing the override', () => {
    // Null is what the server reads as "restore the default", which is also what an emptied input
    // most plausibly means.
    expect(parseValue(setting({}), '')).toBeNull()
    expect(parseValue(setting({}), '   ')).toBeNull()
  })

  it('refuses to send something that is not a number, rather than sending it anyway', () => {
    expect(parseValue(setting({}), 'ninety')).toBe(PARSE_FAILED)
    expect(parseValue(setting({ kind: 'threshold' }), 'high')).toBe(PARSE_FAILED)
  })

  it('parses a threshold as a fraction', () => {
    expect(parseValue(setting({ kind: 'threshold', key: 'evidence.auto-accept-threshold' }), '0.92')).toBe(0.92)
  })

  it('parses weights as an object and refuses malformed JSON', () => {
    expect(parseValue(setting({ kind: 'weights' }), '{"gap":200}')).toEqual({ gap: 200 })
    expect(parseValue(setting({ kind: 'weights' }), '{gap:200}')).toBe(PARSE_FAILED)
  })

  it('renders an unset value as empty rather than as "null"', () => {
    // The screen shows "— unset —" for an empty string; a literal "null" would read as a value.
    expect(renderValue(null)).toBe('')
    expect(renderValue(undefined)).toBe('')
    expect(renderValue(0)).toBe('0')
    expect(renderValue({ gap: 100 })).toBe('{"gap":100}')
  })
})

describe('matrix draft labels (ADM-3)', () => {
  it('increments a trailing number, which is how the existing labels are shaped', () => {
    expect(suggestLabel('dev-2026.1', '2026-07-27')).toBe('dev-2026.2')
    expect(suggestLabel('v9', '2026-07-27')).toBe('v10')
  })

  it('falls back rather than guessing at a label with no number', () => {
    expect(suggestLabel('baseline', '2026-07-27')).toBe('baseline-copy')
  })

  it('uses the business date for a first version, never a local clock', () => {
    // NFR-5: `today` is passed in from the session. A `new Date()` here would name a version after
    // the viewer's timezone rather than the operating one.
    expect(suggestLabel(null, '2026-07-27')).toBe('matrix-2026-07-27')
  })
})

describe('notification deep links (ADM-8)', () => {
  it('follows an internal path', () => {
    expect(internalLink('/register/UNI-CC24-1')).toBe('/register/UNI-CC24-1')
    expect(internalLink('/planner?partnership=UNI&cc=CC24')).toBe('/planner?partnership=UNI&cc=CC24')
  })

  it('does not follow the mobile app scheme, which means nothing here', () => {
    expect(internalLink('crewcomp://certifications')).toBeNull()
  })

  it('refuses a protocol-relative URL, which is an off-site redirect wearing a path', () => {
    // `//evil.example` looks relative and is not: the browser reads it as https://evil.example.
    expect(internalLink('//evil.example/phish')).toBeNull()
    expect(internalLink('https://evil.example')).toBeNull()
  })

  it('renders no link at all when there is none', () => {
    expect(internalLink(null)).toBeNull()
  })
})

describe('evidence corrections (ADM-9)', () => {
  function document(overrides: Partial<EvidenceDocument>): EvidenceDocument {
    return {
      publicId: 'abc',
      personId: 1,
      sam: 'SAM001',
      personName: 'Crew',
      partnershipAbbrev: 'UNI',
      source: 'mobile_camera',
      submittedBy: 'Crew',
      submittedAt: '2026-07-01T00:00:00Z',
      verificationStatus: 'pending_review',
      contentType: 'image/jpeg',
      byteSize: 1024,
      uploadComplete: true,
      hasContent: true,
      requirementHintId: null,
      matchedRequirementId: 7,
      extractionModel: 'dev-text-pattern/1',
      extraction: [{ name: 'expiryDate', value: '2027-06-30', confidence: 0.95 }],
      reviewReason: null,
      rejectionReason: null,
      linkedHoldingId: null,
      ...overrides,
    } as EvidenceDocument
  }

  it('accepting what was extracted is not a correction', () => {
    expect(isCorrection(document({}), 7, '2027-06-30')).toBe(false)
  })

  it('changing the expiry is a correction — this is the measurement LLM-2 needs', () => {
    expect(isCorrection(document({}), 7, '2029-06-30')).toBe(true)
  })

  it('changing the requirement is a correction', () => {
    expect(isCorrection(document({}), 9, '2027-06-30')).toBe(true)
  })

  it('an extraction that read nothing is not corrected by filling it in', () => {
    // With no provider configured every field is empty, so treating a first entry as a correction
    // would make the correction signal useless exactly when it is most needed.
    const empty = document({
      matchedRequirementId: null,
      extraction: [{ name: 'expiryDate', value: null, confidence: 0 }],
    })
    expect(isCorrection(empty, 7, '2027-06-30')).toBe(false)
  })
})
