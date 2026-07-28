import type { components } from './schema'

/**
 * The typed HTTP client.
 *
 * Two rules hold everything else together:
 *
 *  1. **No hand-written request or response types** (DEV-2). Everything below aliases the
 *     generated schema, so a backend DTO change fails `tsc` here instead of surfacing as an
 *     `undefined` in a table cell.
 *  2. **No token ever touches JavaScript** (ADR 0003, SEC-1). Authentication is the BFF's
 *     `HttpOnly` session cookie; `credentials: 'same-origin'` is the whole of the client's part
 *     in it. There is no place to put an `Authorization` header, and that is deliberate.
 */

type Schemas = components['schemas']

export type Session = Schemas['SessionDto']
export type Partnership = Schemas['PartnershipDto']
export type CrewChange = Schemas['CrewChangeDto']
export type Requirement = Schemas['RequirementDto']
export type Position = Schemas['PositionDto']
export type Slot = Schemas['SlotDto']
export type Person = Schemas['PersonDto']
export type Holding = Schemas['HoldingDto']
export type Assignment = Schemas['AssignmentDto']
export type LeaveRecord = Schemas['LeaveRecordDto']
export type SwingEvaluation = Schemas['SwingEvaluationDto']
export type AssignmentEvaluation = Schemas['AssignmentEvaluationDto']
export type PersonEvaluation = Schemas['PersonEvaluationDto']
export type Cell = Schemas['CellDto']
export type Quota = Schemas['QuotaDto']
export type GapReportRow = Schemas['GapReportRowDto']
export type Suggestion = Schemas['SuggestionDto']
export type ExpiryAlert = Schemas['ExpiryAlertDto']
export type SetHoldingRequest = Schemas['SetHoldingRequest']
export type AssignRequest = Schemas['AssignRequest']
export type RequirementDetail = Schemas['RequirementDetailDto']
export type RequirementAlias = Schemas['RequirementAliasDto']
export type RequirementUsage = Schemas['RequirementUsageDto']
export type SaveRequirementRequest = Schemas['SaveRequirementRequest']
export type ExceptionItem = Schemas['ExceptionItemDto']
export type UnknownHolding = Schemas['UnknownHoldingDto']
export type CrewRequest = Schemas['CrewRequestDto']
export type CrewRequestSummary = Schemas['CrewRequestSummaryDto']

/** ADM-11's filter. `all` is this screen's word for "no status parameter", not a server state. */
export type CrewRequestStatus = 'open' | 'actioned' | 'dismissed' | 'all'
export type RaiseExceptionRequest = Schemas['RaiseExceptionRequest']
export type RegisterRecord = Schemas['RegisterRecordDto']
export type RegisterRecordDetail = Schemas['RegisterRecordDetailDto']
export type ApprovalCondition = Schemas['ApprovalConditionDto']
export type RegisterNote = Schemas['RegisterNoteDto']
export type CreateRegisterRecordRequest = Schemas['CreateRegisterRecordRequest']
export type CloseRegisterRecordRequest = Schemas['CloseRegisterRecordRequest']
export type AddConditionRequest = Schemas['AddConditionRequest']

// ADM-3 — matrix versions (§5.5)
export type MatrixVersion = Schemas['MatrixVersionDto']
export type MatrixVersionSummary = Schemas['MatrixVersionSummaryDto']
export type MatrixVersionDetail = Schemas['MatrixVersionDetailDto']
export type MatrixRule = Schemas['MatrixRuleDto']
export type MatrixQuotaRule = Schemas['MatrixQuotaRuleDto']
export type MatrixConditionalRule = Schemas['MatrixConditionalRuleDto']
export type MatrixDiff = Schemas['MatrixDiffDto']
export type RuleDiffEntry = Schemas['RuleDiffEntryDto']
export type QuotaDiffEntry = Schemas['QuotaDiffEntryDto']
export type CreateMatrixDraftRequest = Schemas['CreateMatrixDraftRequest']
export type UpdateMatrixDraftRequest = Schemas['UpdateMatrixDraftRequest']
export type SetMatrixCellRequest = Schemas['SetMatrixCellRequest']
export type PublicationResult = Schemas['PublicationResultDto']

// ADM-8 — notifications (§9)
export type Notification = Schemas['AdminNotificationDto']
export type NotificationSummary = Schemas['NotificationSummaryDto']

// ADM-9 — evidence verification queue (§8)
export type EvidenceDocument = Schemas['EvidenceDocumentDto']
export type ExtractedField = Schemas['ExtractedFieldDto']
export type AcceptEvidenceRequest = Schemas['AcceptEvidenceRequest']

// ADM-10 — administration
export type ConfigSetting = Schemas['ConfigSettingDto']
export type ScheduledJob = Schemas['ScheduledJobDto']
export type JobRun = Schemas['JobRunDto']
export type UserAccount = Schemas['UserAccountDto']
export type IdentityProvider = Schemas['IdentityProviderDto']
export type CreateTransitionalAccountRequest = Schemas['CreateTransitionalAccountRequest']
export type CreateIdentityProviderRequest = Schemas['CreateIdentityProviderRequest']

/** ADM-4 list filters. `state` omitted means the whole history, which is the point of the module. */
export interface RegisterFilters {
  partnership?: string | undefined
  cc?: string | undefined
  type?: string | undefined
  state?: 'open' | 'closed' | 'all' | undefined
}

/** A failed call, carrying the backend's `ErrorDto` code so screens can branch on it. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }

  get isUnauthenticated(): boolean {
    return this.status === 401
  }

  get isForbidden(): boolean {
    return this.status === 403
  }

  get isNotFound(): boolean {
    return this.status === 404
  }
}

/**
 * The development identity, sent as headers to the backend's dev-auth shim.
 *
 * Stripped from production builds: `import.meta.env.DEV` is a compile-time constant, so the
 * whole branch — and the header names with it — is removed by the bundler's dead-code pass.
 * In production the session cookie is the only credential.
 */
export interface DevIdentity {
  user: string
  roles: string[]
  personId?: number
  partnershipIds?: number[]
}

const DEV_IDENTITY_KEY = 'crewcomp.dev-identity'

export function readDevIdentity(): DevIdentity | null {
  if (!import.meta.env.DEV) return null
  const raw = window.localStorage.getItem(DEV_IDENTITY_KEY)
  if (raw === null) return null
  try {
    return JSON.parse(raw) as DevIdentity
  } catch {
    return null
  }
}

export function writeDevIdentity(identity: DevIdentity | null): void {
  if (!import.meta.env.DEV) return
  if (identity === null) window.localStorage.removeItem(DEV_IDENTITY_KEY)
  else window.localStorage.setItem(DEV_IDENTITY_KEY, JSON.stringify(identity))
}

function devHeaders(): Record<string, string> {
  if (!import.meta.env.DEV) return {}
  const identity = readDevIdentity()
  if (identity === null) return {}
  const headers: Record<string, string> = {
    'X-Dev-User': identity.user,
    'X-Dev-Roles': identity.roles.join(','),
  }
  if (identity.personId !== undefined) headers['X-Dev-Person-Id'] = String(identity.personId)
  if (identity.partnershipIds !== undefined && identity.partnershipIds.length > 0) {
    headers['X-Dev-Partnerships'] = identity.partnershipIds.join(',')
  }
  return headers
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    credentials: 'same-origin',
    headers: {
      Accept: 'application/json',
      ...(init?.body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...devHeaders(),
      ...init?.headers,
    },
  })

  if (!response.ok) {
    throw new ApiError(response.status, ...(await errorBody(response)))
  }
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

/**
 * The backend's `ErrorDto`, declared here rather than taken from the generated schema: no
 * operation lists it as a declared response, so OpenAPI does not carry it. If the error contract
 * ever grows, annotate the mappers' shape onto the operations and this alias goes away.
 */
interface ErrorBody {
  error?: string
  detail?: string | null
}

async function errorBody(response: Response): Promise<[code: string, message: string]> {
  try {
    const body = (await response.json()) as ErrorBody | undefined
    if (body?.error !== undefined && body.error !== null) {
      return [body.error, body.detail ?? body.error]
    }
  } catch {
    // A non-JSON error body (a proxy 502, say) is still an error — fall through to the status.
  }
  return ['http_error', `${response.status} ${response.statusText}`]
}

function query(params: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined) search.set(key, String(value))
  }
  const rendered = search.toString()
  return rendered === '' ? '' : `?${rendered}`
}

export const api = {
  session: (): Promise<Session> => request('/api/v1/session'),

  partnerships: (): Promise<Partnership[]> => request('/api/v1/partnerships'),

  crewChanges: (partnership: string): Promise<CrewChange[]> =>
    request(`/api/v1/partnerships/${encodeURIComponent(partnership)}/crew-changes`),

  requirements: (): Promise<Requirement[]> => request('/api/v1/requirements'),

  positions: (): Promise<Position[]> => request('/api/v1/positions'),

  slots: (): Promise<Slot[]> => request('/api/v1/slots'),

  people: (): Promise<Person[]> => request('/api/v1/people'),

  person: (personId: number): Promise<Person> => request(`/api/v1/people/${personId}`),

  holdings: (personId: number): Promise<Holding[]> => request(`/api/v1/people/${personId}/holdings`),

  assignments: (personId: number): Promise<Assignment[]> =>
    request(`/api/v1/people/${personId}/assignments`),

  leave: (personId: number): Promise<LeaveRecord[]> => request(`/api/v1/people/${personId}/leave`),

  swingEvaluation: (partnership: string, cc: string, matrixVersionId?: number): Promise<SwingEvaluation> =>
    request(
      `/api/v1/swings/${encodeURIComponent(partnership)}/${encodeURIComponent(cc)}/evaluation` +
        query({ matrixVersionId }),
    ),

  swingGaps: (partnership: string, cc: string, matrixVersionId?: number): Promise<GapReportRow[]> =>
    request(
      `/api/v1/swings/${encodeURIComponent(partnership)}/${encodeURIComponent(cc)}/gaps` +
        query({ matrixVersionId }),
    ),

  suggestions: (partnership: string, cc: string, slotRef: number, limit = 20): Promise<Suggestion[]> =>
    request(
      `/api/v1/swings/${encodeURIComponent(partnership)}/${encodeURIComponent(cc)}/suggestions` +
        query({ slotRef, limit }),
    ),

  personEvaluation: (
    personId: number,
    partnership: string,
    cc: string,
    matrixVersionId?: number,
  ): Promise<PersonEvaluation> =>
    request(`/api/v1/people/${personId}/evaluation` + query({ partnership, cc, matrixVersionId })),

  expiryAlerts: (leadDays: number): Promise<ExpiryAlert[]> =>
    request('/api/v1/expiry-alerts' + query({ leadDays })),

  setHolding: (personId: number, requirementId: number, body: SetHoldingRequest): Promise<Holding> =>
    request(`/api/v1/people/${personId}/holdings/${requirementId}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),

  assign: (partnership: string, cc: string, body: AssignRequest): Promise<Assignment> =>
    request(
      `/api/v1/swings/${encodeURIComponent(partnership)}/${encodeURIComponent(cc)}/assignments`,
      { method: 'POST', body: JSON.stringify(body) },
    ),

  unassign: (assignmentId: number): Promise<void> =>
    request(`/api/v1/assignments/${assignmentId}`, { method: 'DELETE' }),

  catalogue: (): Promise<RequirementDetail[]> => request('/api/v1/requirements/catalogue'),

  createRequirement: (body: SaveRequirementRequest): Promise<RequirementDetail> =>
    request('/api/v1/requirements', { method: 'POST', body: JSON.stringify(body) }),

  updateRequirement: (
    requirementId: number,
    body: SaveRequirementRequest,
  ): Promise<RequirementDetail> =>
    request(`/api/v1/requirements/${requirementId}`, { method: 'PUT', body: JSON.stringify(body) }),

  addAlias: (requirementId: number, alias: string): Promise<RequirementDetail> =>
    request(`/api/v1/requirements/${requirementId}/aliases`, {
      method: 'POST',
      body: JSON.stringify({ alias }),
    }),

  removeAlias: (requirementId: number, aliasId: number): Promise<RequirementDetail> =>
    request(`/api/v1/requirements/${requirementId}/aliases/${aliasId}`, { method: 'DELETE' }),

  exceptions: (state: 'open' | 'resolved' | 'all'): Promise<ExceptionItem[]> =>
    request('/api/v1/exceptions' + query({ state: state === 'all' ? undefined : state })),

  unknownHoldings: (): Promise<UnknownHolding[]> => request('/api/v1/exceptions/unknown-holdings'),

  raiseException: (body: RaiseExceptionRequest): Promise<ExceptionItem> =>
    request('/api/v1/exceptions', { method: 'POST', body: JSON.stringify(body) }),

  resolveException: (exceptionItemId: number, note: string): Promise<ExceptionItem> =>
    request(`/api/v1/exceptions/${exceptionItemId}/resolve`, {
      method: 'POST',
      body: JSON.stringify({ note }),
    }),

  reopenException: (exceptionItemId: number): Promise<ExceptionItem> =>
    request(`/api/v1/exceptions/${exceptionItemId}/reopen`, { method: 'POST' }),

  crewRequests: (status: CrewRequestStatus): Promise<CrewRequest[]> =>
    request('/api/v1/crew-requests' + query({ status: status === 'all' ? undefined : status })),

  crewRequestSummary: (): Promise<CrewRequestSummary> => request('/api/v1/crew-requests/open-count'),

  decideCrewRequest: (
    crewRequestId: number,
    decision: 'action' | 'dismiss',
    note: string,
  ): Promise<CrewRequest> =>
    request(`/api/v1/crew-requests/${crewRequestId}/${decision}`, {
      method: 'POST',
      body: JSON.stringify({ note }),
    }),

  register: (filters: RegisterFilters): Promise<RegisterRecord[]> =>
    request(
      '/api/v1/register' +
        query({
          partnership: filters.partnership,
          cc: filters.cc,
          type: filters.type,
          state: filters.state === 'all' ? undefined : filters.state,
        }),
    ),

  registerRecord: (recordId: string): Promise<RegisterRecordDetail> =>
    request(`/api/v1/register/${encodeURIComponent(recordId)}`),

  nextRecordId: (partnership: string, cc: string): Promise<{ recordId: string }> =>
    request('/api/v1/register/next-id' + query({ partnership, cc })),

  createRegisterRecord: (body: CreateRegisterRecordRequest): Promise<RegisterRecordDetail> =>
    request('/api/v1/register', { method: 'POST', body: JSON.stringify(body) }),

  transitionRegisterRecord: (recordId: string, status: string): Promise<RegisterRecordDetail> =>
    request(`/api/v1/register/${encodeURIComponent(recordId)}/transition`, {
      method: 'POST',
      body: JSON.stringify({ status }),
    }),

  closeRegisterRecord: (
    recordId: string,
    body: CloseRegisterRecordRequest,
  ): Promise<RegisterRecordDetail> =>
    request(`/api/v1/register/${encodeURIComponent(recordId)}/close`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  addRegisterNote: (recordId: string, party: string, body: string): Promise<RegisterRecordDetail> =>
    request(`/api/v1/register/${encodeURIComponent(recordId)}/notes`, {
      method: 'POST',
      body: JSON.stringify({ party, body }),
    }),

  addRegisterCondition: (
    recordId: string,
    body: AddConditionRequest,
  ): Promise<RegisterRecordDetail> =>
    request(`/api/v1/register/${encodeURIComponent(recordId)}/conditions`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  // --- ADM-3, matrix versions (§5.5) ---------------------------------------

  matrixVersions: (): Promise<MatrixVersionSummary[]> => request('/api/v1/matrix-versions'),

  matrixVersion: (versionId: number): Promise<MatrixVersionDetail> =>
    request(`/api/v1/matrix-versions/${versionId}`),

  matrixDiff: (from: number, to: number): Promise<MatrixDiff> =>
    request(`/api/v1/matrix-versions/${from}/diff/${to}`),

  createMatrixDraft: (body: CreateMatrixDraftRequest): Promise<MatrixVersion> =>
    request('/api/v1/matrix-versions', { method: 'POST', body: JSON.stringify(body) }),

  updateMatrixDraft: (versionId: number, body: UpdateMatrixDraftRequest): Promise<MatrixVersion> =>
    request(`/api/v1/matrix-versions/${versionId}`, { method: 'PUT', body: JSON.stringify(body) }),

  discardMatrixDraft: (versionId: number): Promise<void> =>
    request(`/api/v1/matrix-versions/${versionId}`, { method: 'DELETE' }),

  setMatrixCell: (versionId: number, body: SetMatrixCellRequest): Promise<MatrixRule> =>
    request(`/api/v1/matrix-versions/${versionId}/cells`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),

  clearMatrixCell: (
    versionId: number,
    cell: { positionId: number; requirementId: number; partnershipId?: number },
  ): Promise<void> =>
    request(
      `/api/v1/matrix-versions/${versionId}/cells` +
        query({
          positionId: cell.positionId,
          requirementId: cell.requirementId,
          partnershipId: cell.partnershipId,
        }),
      { method: 'DELETE' },
    ),

  publishMatrixVersion: (versionId: number, effectiveFrom?: string): Promise<PublicationResult> =>
    request(`/api/v1/matrix-versions/${versionId}/publish`, {
      method: 'POST',
      body: JSON.stringify({ effectiveFrom: effectiveFrom ?? null }),
    }),

  // --- ADM-8, notifications (§9) ------------------------------------------

  notifications: (state: 'all' | 'unread' | 'read'): Promise<Notification[]> =>
    request('/api/v1/notifications' + query({ state })),

  notificationSummary: (): Promise<NotificationSummary> =>
    request('/api/v1/notifications/summary'),

  markNotificationRead: (notificationId: number): Promise<void> =>
    request(`/api/v1/notifications/${notificationId}/read`, { method: 'POST' }),

  markAllNotificationsRead: (): Promise<NotificationSummary> =>
    request('/api/v1/notifications/read-all', { method: 'POST' }),

  // --- ADM-9, evidence verification (§8) ----------------------------------

  evidenceQueue: (statuses: readonly string[]): Promise<EvidenceDocument[]> =>
    request(
      '/api/v1/evidence-review' +
        (statuses.length === 0
          ? ''
          : '?' + statuses.map((s) => `status=${encodeURIComponent(s)}`).join('&')),
    ),

  evidenceDocument: (publicId: string): Promise<EvidenceDocument> =>
    request(`/api/v1/evidence-review/${encodeURIComponent(publicId)}`),

  /**
   * The URL the side-by-side view points an `<img>` or frame at.
   *
   * A URL rather than a fetch: the bytes are megabytes of image and the browser is better at
   * fetching, caching and decoding them than we are at shuttling them through a blob. It carries
   * the session cookie the same way every other request does.
   */
  evidenceContentUrl: (publicId: string): string =>
    `/api/v1/evidence-review/${encodeURIComponent(publicId)}/content`,

  acceptEvidence: (publicId: string, body: AcceptEvidenceRequest): Promise<EvidenceDocument> =>
    request(`/api/v1/evidence-review/${encodeURIComponent(publicId)}/accept`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  rejectEvidence: (publicId: string, reason: string): Promise<EvidenceDocument> =>
    request(`/api/v1/evidence-review/${encodeURIComponent(publicId)}/reject`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
    }),

  extractEvidence: (publicId: string): Promise<EvidenceDocument> =>
    request(`/api/v1/evidence-review/${encodeURIComponent(publicId)}/extract`, { method: 'POST' }),

  // --- ADM-10, administration ---------------------------------------------

  config: (): Promise<ConfigSetting[]> => request('/api/v1/administration/config'),

  setConfig: (key: string, value: unknown): Promise<ConfigSetting> =>
    request(`/api/v1/administration/config/${encodeURIComponent(key)}`, {
      method: 'PUT',
      body: JSON.stringify({ value }),
    }),

  clearConfig: (key: string): Promise<ConfigSetting> =>
    request(`/api/v1/administration/config/${encodeURIComponent(key)}`, { method: 'DELETE' }),

  jobs: (): Promise<ScheduledJob[]> => request('/api/v1/administration/jobs'),

  runJob: (name: string): Promise<JobRun> =>
    request(`/api/v1/administration/jobs/${encodeURIComponent(name)}/run`, { method: 'POST' }),

  userAccounts: (): Promise<UserAccount[]> => request('/api/v1/administration/users'),

  createUserAccount: (body: CreateTransitionalAccountRequest): Promise<UserAccount> =>
    request('/api/v1/administration/users', { method: 'POST', body: JSON.stringify(body) }),

  grantRole: (userAccountId: number, role: string): Promise<UserAccount> =>
    request(`/api/v1/administration/users/${userAccountId}/roles/${encodeURIComponent(role)}`, {
      method: 'POST',
    }),

  revokeRole: (userAccountId: number, role: string): Promise<UserAccount> =>
    request(`/api/v1/administration/users/${userAccountId}/roles/${encodeURIComponent(role)}`, {
      method: 'DELETE',
    }),

  setUserAccountStatus: (userAccountId: number, status: string): Promise<UserAccount> =>
    request(`/api/v1/administration/users/${userAccountId}/status`, {
      method: 'PUT',
      body: JSON.stringify({ status }),
    }),

  setUserAccountScopes: (userAccountId: number, partnershipIds: number[]): Promise<UserAccount> =>
    request(`/api/v1/administration/users/${userAccountId}/scopes`, {
      method: 'PUT',
      body: JSON.stringify({ partnershipIds }),
    }),

  identityProviders: (): Promise<IdentityProvider[]> =>
    request('/api/v1/administration/identity-providers'),

  createIdentityProvider: (body: CreateIdentityProviderRequest): Promise<IdentityProvider> =>
    request('/api/v1/administration/identity-providers', {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  setIdentityProviderEnabled: (
    identityProviderId: number,
    enabled: boolean,
  ): Promise<IdentityProvider> =>
    request(`/api/v1/administration/identity-providers/${identityProviderId}/enabled`, {
      method: 'PUT',
      body: JSON.stringify({ enabled }),
    }),
}
