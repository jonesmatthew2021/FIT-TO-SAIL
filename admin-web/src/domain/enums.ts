/**
 * Presentation metadata for the Appendix A enumerations.
 *
 * This file holds **labels and colours only**. It deliberately contains no evaluation logic: the
 * §5 engine decides what state a cell is in, and re-deriving any part of that here would be the
 * POC's mistake — business logic in a client (AUTH-1). Where a list's order carries meaning, the
 * server sends it in order and the UI renders it in the order received.
 *
 * The state names come from the generated schema as plain strings, so an unrecognised value is
 * possible if the backend adds one; every lookup below degrades to showing the raw wire value
 * rather than blanking the cell.
 */

export type Tone = 'critical' | 'warning' | 'caution' | 'neutral' | 'good' | 'muted'

interface StateDisplay {
  readonly label: string
  readonly tone: Tone
  readonly description: string
}

const CELL_STATES: Record<string, StateDisplay> = {
  gap: {
    label: 'Gap',
    tone: 'critical',
    description: 'Mandatory and not held — blocks the assignment.',
  },
  expiring: {
    label: 'Expiring',
    tone: 'warning',
    description: 'Held, but expires before the swing ends.',
  },
  pending: {
    label: 'Pending',
    tone: 'caution',
    description: 'An open register record covers this cell; no decision yet.',
  },
  unknown: {
    label: 'Unknown',
    tone: 'caution',
    description: 'Holding status was never established — chase list (ADM-7).',
  },
  review: {
    label: 'Review',
    tone: 'caution',
    description: 'A tier footnote applies and is never auto-resolved (§5.1).',
  },
  exempt: {
    label: 'Exempt',
    tone: 'neutral',
    description: 'An approved exemption covers this cell.',
  },
  ok: { label: 'OK', tone: 'good', description: 'Held and valid through the whole swing.' },
  quota_only: {
    label: 'Quota only',
    tone: 'muted',
    description: 'Not individually mandatory; counts toward a quota footnote.',
  },
  recommended: {
    label: 'Recommended',
    tone: 'muted',
    description: 'Recommended (R), never a gap.',
  },
  na: { label: 'n/a', tone: 'muted', description: 'Not applicable to this person.' },
}

/**
 * Display order for cell states, worst first.
 *
 * This mirrors the engine's gap-report ordering so that a client-sorted view and the server's
 * worklist agree. It is used for *presentation grouping only* — the gap report itself arrives
 * ordered and is rendered as received.
 */
export const CELL_STATE_ORDER: readonly string[] = [
  'gap',
  'expiring',
  'unknown',
  'review',
  'pending',
  'exempt',
  'ok',
  'quota_only',
  'recommended',
  'na',
]

export function cellState(state: string): StateDisplay {
  return CELL_STATES[state] ?? { label: state, tone: 'neutral', description: state }
}

export function cellStateRank(state: string): number {
  const index = CELL_STATE_ORDER.indexOf(state)
  return index === -1 ? CELL_STATE_ORDER.length : index
}

/** States that need someone to do something — the dashboard's and planner's attention counts. */
export function needsAttention(state: string): boolean {
  return state === 'gap' || state === 'expiring' || state === 'unknown' || state === 'review'
}

const HOLDING_STATUSES: Record<string, StateDisplay> = {
  held_expiry: { label: 'Held (expires)', tone: 'good', description: 'Held with an expiry date.' },
  held_perpetual: { label: 'Held', tone: 'good', description: 'Held, does not expire.' },
  not_held: { label: 'Not held', tone: 'critical', description: 'Confirmed not held.' },
  unknown: { label: 'Unknown', tone: 'caution', description: 'Never established.' },
}

export function holdingStatus(status: string): StateDisplay {
  return HOLDING_STATUSES[status] ?? { label: status, tone: 'neutral', description: status }
}

export const HOLDING_STATUS_VALUES: readonly string[] = [
  'held_expiry',
  'held_perpetual',
  'not_held',
  'unknown',
]

const EXPIRY_IMPACTS: Record<string, StateDisplay> = {
  expired_before_swing: {
    label: 'Expires before swing',
    tone: 'critical',
    description: 'Already invalid when the next assignment starts.',
  },
  mid_swing: {
    label: 'Expires mid-swing',
    tone: 'warning',
    description: 'Valid at the start of the next assignment but not at the end.',
  },
  none: {
    label: 'No assignment impact',
    tone: 'muted',
    description: 'Expiring, but no assignment it affects.',
  },
}

export function expiryImpact(impact: string): StateDisplay {
  return EXPIRY_IMPACTS[impact] ?? { label: impact, tone: 'neutral', description: impact }
}

const ROLE_LABELS: Record<string, string> = {
  crew_coordinator: 'Crew Coordinator',
  workflow_manager: 'Workflow Manager',
  compliance_lead: 'Compliance Lead',
  data_steward: 'Data Steward',
  vessel_master: 'Vessel Master',
  crew_member: 'Crew Member',
  system_administrator: 'System Administrator',
}

export const ALL_ROLES: readonly string[] = Object.keys(ROLE_LABELS)

export function roleLabel(role: string): string {
  return ROLE_LABELS[role] ?? role
}

/**
 * Requirement categories are shown as their codes.
 *
 * Appendix A enumerates `QL · VS · PS · MS · CS · HR · PT · VI · PI` but nothing in the spec or
 * the POC says what they expand to. Guessing ("QL = Qualifications & Licences"?) would put an
 * invented label in front of users who know the real one, so the code stands until the client
 * confirms the expansions — a question for the same pass that resolves O-7.
 */
export function categoryLabel(category: string): string {
  return category
}

/**
 * Appendix A's categories, in the spec's order. Mirrors `ReferenceService.CATEGORIES` and the
 * database CHECK constraint — the server rejects anything else, and this is only what the
 * catalogue editor offers so a Compliance Lead never has to remember them.
 */
export const REQUIREMENT_CATEGORIES: readonly string[] = [
  'QL',
  'VS',
  'PS',
  'MS',
  'CS',
  'HR',
  'PT',
  'VI',
  'PI',
]

export const REQUIREMENT_STATUSES: readonly string[] = ['active', 'retired']

/**
 * Appendix A's register enumerations, as their exact wire strings.
 *
 * The wire value **is** the label here — "Exemption Request - PW" is what the register has always
 * called it and what the CSV export has to carry. Inventing a prettier display form would put two
 * names on one thing.
 */
export const REGISTER_TYPES: readonly string[] = [
  'Exemption Request - PW',
  'Exemption Request - OPS',
  'Exemption Request following MRL Query',
  'MRL Query',
  'PW Query',
]

/** The open statuses. Closure carries an outcome and goes through the close endpoint, not here. */
export const REGISTER_OPEN_STATUSES: readonly string[] = [
  'Open - PW',
  'Open - MRL',
  'Open - OPS',
  'Complete before joining',
]

export const REGISTER_OUTCOMES: readonly string[] = [
  'Approved',
  'Not Approved',
  'Info Required',
  'Admin Action',
  'Not Required',
]

export const CONDITION_TYPES: readonly string[] = [
  'supervision',
  'time_limit',
  'duty_restriction',
  'training_booked',
  'other',
]

export const REGISTER_PARTIES: readonly string[] = ['PW', 'MRL', 'OPS']

/**
 * A register status's tone. Open work needs attention; an approval is good; every other closure
 * is neutral — "Not Approved" is a decision, not a fault, and colouring it red would read as one.
 */
export function registerStatusTone(status: string): Tone {
  if (status.startsWith('Open')) return 'caution'
  if (status === 'Closed - Approved') return 'good'
  if (status === 'Complete before joining') return 'neutral'
  return 'muted'
}
