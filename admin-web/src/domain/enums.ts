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

/**
 * A slot reference, zero-padded.
 *
 * A slot ref is a business key on the class model — the source workbook writes `01`, not `1`, and
 * slots 15 and 16 are real refs rather than "the fifteenth slot". Padded so a column of them aligns
 * and so `01` and `1` never look like two different things.
 */
export function slotRef(ref: number): string {
  return String(ref).padStart(2, '0')
}

/**
 * A holding's status.
 *
 * Held is `muted`, not `good`: a holding is a *record*, not a verdict. Whether holding something is
 * enough is the engine's answer against a swing (§5.1), and colouring the record green here would
 * have the system of record making a compliance claim it is not entitled to make. What does earn a
 * warning is a held certificate inside the expiry lead window — see [holdingTone].
 */
const HOLDING_STATUSES: Record<string, StateDisplay> = {
  held_expiry: { label: 'Held (expires)', tone: 'muted', description: 'Held with an expiry date.' },
  held_perpetual: { label: 'Held', tone: 'muted', description: 'Held, does not expire.' },
  not_held: { label: 'Not held', tone: 'critical', description: 'Confirmed not held.' },
  unknown: { label: 'Unknown', tone: 'caution', description: 'Never established.' },
}

/**
 * The default `expiry.lead-days` horizon, mirrored from the server's own default.
 *
 * It decides two presentation-only things: the dashboard's initial lead-time selection, and whether
 * a held certificate's expiry reads as a warning on ADM-5. Mirrored rather than read because the
 * live value is only exposed through the ADM-10 configuration endpoint, which is role-gated — a
 * Crew Coordinator opening a person's page would get a 403 for it. Worth replacing with a
 * session-borne value; until then a stale horizon changes a colour and nothing else.
 */
export const EXPIRY_LEAD_DAYS_DEFAULT = 90

/**
 * A holding's tone, warned up when its expiry falls inside the lead window.
 *
 * `daysToExpiry` is null for a perpetual holding or one with no date. A holding that has already
 * lapsed is still shown as held-with-a-past-date rather than as not-held: the record says what it
 * says, and the engine is what decides the consequence.
 */
export function holdingTone(status: string, daysToExpiry: number | null): Tone {
  const base = holdingStatus(status).tone
  if (status !== 'held_expiry' || daysToExpiry === null) return base
  return daysToExpiry <= EXPIRY_LEAD_DAYS_DEFAULT ? 'warning' : base
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
    tone: 'neutral',
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
 * §9 notification kinds, for ADM-8's labels and the mobile list's icons.
 *
 * Tones follow the same rule as everywhere else in this file: the colour reflects what the reader has
 * to *do*, not how bad the underlying fact is. A rejected document and an unfilled slot both need
 * action; a published matrix and an accepted document are information.
 *
 * An unrecognised kind degrades to the raw wire value rather than blanking the row — the backend may
 * add one, and during a deploy both revisions are live.
 */
const NOTIFICATION_KINDS: Record<string, StateDisplay> = {
  expiry_warning: {
    label: 'Expiry',
    tone: 'warning',
    description: 'A qualification is expiring within the alert window (§5.4).',
  },
  assignment_added: { label: 'Assigned', tone: 'neutral', description: 'Added to a swing.' },
  assignment_removed: { label: 'Unassigned', tone: 'neutral', description: 'Removed from a swing.' },
  assignment_changed: { label: 'Roster change', tone: 'neutral', description: 'An assignment moved.' },
  requirement_added: {
    label: 'New requirement',
    tone: 'caution',
    description: 'A newly applicable requirement.',
  },
  evidence_received: {
    label: 'Document received',
    tone: 'muted',
    description: 'An upload was recorded.',
  },
  evidence_verified: {
    label: 'Document accepted',
    tone: 'good',
    description: 'A submitted document updated a holding.',
  },
  evidence_rejected: {
    label: 'Document rejected',
    tone: 'critical',
    description: 'A submitted document was not accepted; the reason is in the body.',
  },
  register_event: {
    label: 'Register',
    tone: 'caution',
    description: 'A register request was raised, moved or decided (§9).',
  },
  cutoff_approaching: {
    label: 'Cutoff',
    tone: 'warning',
    description: "A swing's submission cutoff is approaching (§9).",
  },
  quota_shortfall: {
    label: 'Quota short',
    tone: 'critical',
    description: 'An upcoming swing does not meet a quota rule (§5.3).',
  },
  roster_gap: {
    label: 'Unfilled slots',
    tone: 'critical',
    description: 'An upcoming swing has slots with nobody in them.',
  },
  expiry_affects_roster: {
    label: 'Expiry on roster',
    tone: 'warning',
    description: 'An expiry lands on or inside an upcoming assignment.',
  },
  matrix_published: {
    label: 'Matrix published',
    tone: 'muted',
    description: 'A new requirements matrix is in force (§5.5).',
  },
  exception_raised: {
    label: 'Data quality',
    tone: 'caution',
    description: 'A new data-quality item is on the worklist (ADM-7).',
  },
  evidence_awaiting_review: {
    label: 'Awaiting review',
    tone: 'caution',
    description: 'A document is in the verification queue (ADM-9).',
  },
  crew_progress_reported: {
    label: 'Crew answered',
    tone: 'good',
    description: 'A crew member says a course is booked. Not a holding — the gap still stands.',
  },
  crew_help_requested: {
    label: 'Crew needs help',
    tone: 'caution',
    description: 'A crew member has asked for help arranging a requirement.',
  },
}

export function notificationKind(kind: string): StateDisplay {
  return NOTIFICATION_KINDS[kind] ?? { label: kind, tone: 'neutral', description: kind }
}

/**
 * Appendix A's `evidence_document.verification_status`, for ADM-9's queue.
 *
 * `auto_accepted` shares `good` with `verified`: both are decided, and the queue's status filter is
 * what a Data Steward uses to pull up the auto-accepted set for the retrospective spot-check §8
 * stage 4 asks for. Colour is for the state of the document, not for a standing reminder — and the
 * description below is what says the row still wants an eye on it.
 */
const VERIFICATION_STATUSES: Record<string, StateDisplay> = {
  pending_extraction: {
    label: 'Awaiting extraction',
    tone: 'muted',
    description: 'Uploaded; the pipeline has not read it yet (§8 stage 2).',
  },
  pending_review: {
    label: 'Needs review',
    tone: 'caution',
    description: 'Waiting for a Data Steward to accept, correct or reject it (§8 stage 5).',
  },
  auto_accepted: {
    label: 'Auto-accepted',
    tone: 'good',
    description: 'Accepted by the pipeline, listed here for spot-checking (§8 stage 4).',
  },
  verified: {
    label: 'Verified',
    tone: 'good',
    description: 'A human accepted it and the holding was updated.',
  },
  rejected: {
    label: 'Rejected',
    tone: 'critical',
    description: 'Not accepted; the submitter was told why.',
  },
}

export function verificationStatus(status: string): StateDisplay {
  return VERIFICATION_STATUSES[status] ?? { label: status, tone: 'neutral', description: status }
}

/** The statuses ADM-9's queue offers as filters, in the order a reviewer works them. */
export const QUEUE_STATUSES: readonly string[] = [
  'pending_review',
  'pending_extraction',
  'auto_accepted',
  'verified',
  'rejected',
]

/**
 * A confidence's tone (§8 stage 2).
 *
 * The thresholds here are **presentation only** and are deliberately not the auto-accept threshold:
 * that one is server-side configuration (ADM-10) and is what decides anything. These three bands
 * only decide a colour, and a reviewer should be able to see "the model was unsure" without knowing
 * what the current policy happens to be.
 */
export function confidenceTone(confidence: number): Tone {
  // No reading at all is a warning rather than a silence: an empty field the reviewer has to fill
  // is the one thing on the panel that will not fill itself.
  if (confidence <= 0) return 'warning'
  if (confidence < 0.5) return 'critical'
  if (confidence < 0.85) return 'caution'
  return 'good'
}

/**
 * A register status's tone.
 *
 * Open work is warm, and which warm depends on whose move it is: PW and OPS are the parties who act,
 * so their queues are `warning`; an MRL query is waiting on a reading rather than on a decision, so
 * it is `caution`. An approval is `good`. Every other closure is `muted` — "Not Approved" is a
 * decision, not a fault, and colouring it red would read as one.
 */
export function registerStatusTone(status: string): Tone {
  if (status === 'Open - MRL') return 'caution'
  if (status.startsWith('Open')) return 'warning'
  if (status === 'Closed - Approved') return 'good'
  return 'muted'
}

/**
 * A matrix version's tone. A draft is an accent *outline* rather than a fill — it exists but is not
 * in force, which is exactly what an outline says.
 */
export function matrixStatusTone(status: string): Tone | 'outline' {
  if (status === 'published') return 'good'
  if (status === 'draft') return 'outline'
  return 'muted'
}
