import {
  useMutation,
  useQueries,
  useQuery,
  useQueryClient,
  type UseQueryResult,
} from '@tanstack/react-query'
import {
  api,
  type AcceptEvidenceRequest,
  type AddConditionRequest,
  type AssignRequest,
  type Assignment,
  type CloseRegisterRecordRequest,
  type ConfigSetting,
  type CreateIdentityProviderRequest,
  type CreateMatrixDraftRequest,
  type CreatePersonRequest,
  type CreateRegisterRecordRequest,
  type CreateTransitionalAccountRequest,
  type CrewChange,
  type CrewRequest,
  type CrewRequestStatus,
  type CrewRequestSummary,
  type EvidenceDocument,
  type ExceptionItem,
  type ExpiryAlert,
  type FileIntakeRequest,
  type GapReportRow,
  type Holding,
  type IdentityProvider,
  type MatrixDiff,
  type MatrixVersionDetail,
  type MatrixVersionSummary,
  type Notification,
  type NotificationSummary,
  type Partnership,
  type Person,
  type Position,
  type RaiseExceptionRequest,
  type RegisterFilters,
  type RegisterRecord,
  type RegisterRecordDetail,
  type Requirement,
  type RequirementDetail,
  type SaveRequirementRequest,
  type ScheduledJob,
  type SetHoldingRequest,
  type SetMatrixCellRequest,
  type Slot,
  type Suggestion,
  type SwingEvaluation,
  type UnknownHolding,
  type UpdateMatrixDraftRequest,
  type UserAccount,
} from './client'

/**
 * Query hooks and cache keys.
 *
 * Two cache policies, chosen from how the data behaves rather than per screen:
 *
 *  - **Reference data** (partnerships, catalogue, positions, slots) changes on a Compliance
 *    Lead's deliberate act and is joined against on nearly every screen — cached for the
 *    session and fetched once.
 *  - **Evaluations and holdings** change whenever anyone edits a holding, and the whole point
 *    of the system is that the answer is current. They are invalidated by every write.
 */

const REFERENCE_CACHE = { staleTime: 60 * 60 * 1000, gcTime: 60 * 60 * 1000 }

export const keys = {
  partnerships: ['partnerships'] as const,
  crewChanges: (partnership: string) => ['crew-changes', partnership] as const,
  requirements: ['requirements'] as const,
  catalogue: ['catalogue'] as const,
  exceptions: (state: string) => ['exceptions', state] as const,
  unknownHoldings: ['exceptions', 'unknown-holdings'] as const,
  crewRequests: (status: string) => ['crew-requests', status] as const,
  crewRequestSummary: ['crew-requests', 'open-count'] as const,
  register: (filters: RegisterFilters) =>
    ['register', filters.partnership ?? '', filters.cc ?? '', filters.type ?? '', filters.state ?? 'all'] as const,
  registerRecord: (recordId: string) => ['register', 'record', recordId] as const,
  positions: ['positions'] as const,
  slots: ['slots'] as const,
  people: ['people'] as const,
  person: (personId: number) => ['person', personId] as const,
  holdings: (personId: number) => ['holdings', personId] as const,
  assignments: (personId: number) => ['assignments', personId] as const,
  swing: (partnership: string, cc: string) => ['swing', partnership, cc] as const,
  gaps: (partnership: string, cc: string) => ['gaps', partnership, cc] as const,
  suggestions: (partnership: string, cc: string, slotRef: number) =>
    ['suggestions', partnership, cc, slotRef] as const,
  expiryAlerts: (leadDays: number) => ['expiry-alerts', leadDays] as const,
  matrixVersions: ['matrix-versions'] as const,
  matrixVersion: (versionId: number) => ['matrix-version', versionId] as const,
  matrixDiff: (from: number, to: number) => ['matrix-diff', from, to] as const,
  notifications: (state: string) => ['notifications', state] as const,
  notificationSummary: ['notifications', 'summary'] as const,
  evidenceQueue: (statuses: readonly string[]) =>
    ['evidence-queue', [...statuses].sort().join(',')] as const,
  personEvidence: (personId: number) => ['person-evidence', personId] as const,
  config: ['config'] as const,
  jobs: ['jobs'] as const,
  userAccounts: ['user-accounts'] as const,
  identityProviders: ['identity-providers'] as const,
}

export function usePartnerships(): UseQueryResult<Partnership[]> {
  return useQuery({ queryKey: keys.partnerships, queryFn: api.partnerships, ...REFERENCE_CACHE })
}

export function useCrewChanges(partnership: string | null): UseQueryResult<CrewChange[]> {
  return useQuery({
    queryKey: keys.crewChanges(partnership ?? ''),
    queryFn: () => api.crewChanges(partnership as string),
    enabled: partnership !== null,
    ...REFERENCE_CACHE,
  })
}

export function useRequirements(): UseQueryResult<Requirement[]> {
  return useQuery({ queryKey: keys.requirements, queryFn: api.requirements, ...REFERENCE_CACHE })
}

export function usePositions(): UseQueryResult<Position[]> {
  return useQuery({ queryKey: keys.positions, queryFn: api.positions, ...REFERENCE_CACHE })
}

export function useSlots(): UseQueryResult<Slot[]> {
  return useQuery({ queryKey: keys.slots, queryFn: api.slots, ...REFERENCE_CACHE })
}

export function usePeople(): UseQueryResult<Person[]> {
  return useQuery({ queryKey: keys.people, queryFn: api.people })
}

export function usePerson(personId: number): UseQueryResult<Person> {
  return useQuery({ queryKey: keys.person(personId), queryFn: () => api.person(personId) })
}

export function useHoldings(personId: number): UseQueryResult<Holding[]> {
  return useQuery({ queryKey: keys.holdings(personId), queryFn: () => api.holdings(personId) })
}

/**
 * Every listed person's holdings at once, for the crew matrix (ADM-3's crew view).
 *
 * One query per person rather than a new bulk endpoint: the per-person query is what ADM-5
 * already uses, so the keys are shared with it and a holding edit invalidates both screens
 * through the same cache entry. A bulk endpoint is the right answer if the roster outgrows a
 * request fan-out of this size — revisit past a few hundred people, not at forty.
 */
export function useAllHoldings(personIds: readonly number[]): {
  byPerson: ReadonlyMap<number, Holding[]>
  isPending: boolean
  error: unknown
} {
  return useQueries({
    queries: personIds.map((personId) => ({
      queryKey: keys.holdings(personId),
      queryFn: () => api.holdings(personId),
    })),
    combine: (results) => {
      const byPerson = new Map<number, Holding[]>()
      results.forEach((result, index) => {
        const personId = personIds[index]
        if (personId !== undefined && result.data !== undefined) byPerson.set(personId, result.data)
      })
      return {
        byPerson,
        isPending: results.some((result) => result.isPending),
        error: results.find((result) => result.error !== null)?.error ?? null,
      }
    },
  })
}

export function usePersonAssignments(personId: number): UseQueryResult<Assignment[]> {
  return useQuery({
    queryKey: keys.assignments(personId),
    queryFn: () => api.assignments(personId),
  })
}

export function useSwingEvaluation(
  partnership: string | null,
  cc: string | null,
): UseQueryResult<SwingEvaluation> {
  return useQuery({
    queryKey: keys.swing(partnership ?? '', cc ?? ''),
    queryFn: () => api.swingEvaluation(partnership as string, cc as string),
    enabled: partnership !== null && cc !== null,
  })
}

export function useSwingGaps(
  partnership: string | null,
  cc: string | null,
): UseQueryResult<GapReportRow[]> {
  return useQuery({
    queryKey: keys.gaps(partnership ?? '', cc ?? ''),
    queryFn: () => api.swingGaps(partnership as string, cc as string),
    enabled: partnership !== null && cc !== null,
  })
}

export function useSuggestions(
  partnership: string | null,
  cc: string | null,
  slotRef: number | null,
): UseQueryResult<Suggestion[]> {
  return useQuery({
    queryKey: keys.suggestions(partnership ?? '', cc ?? '', slotRef ?? -1),
    queryFn: () => api.suggestions(partnership as string, cc as string, slotRef as number),
    enabled: partnership !== null && cc !== null && slotRef !== null,
  })
}

export function useExpiryAlerts(leadDays: number): UseQueryResult<ExpiryAlert[]> {
  return useQuery({
    queryKey: keys.expiryAlerts(leadDays),
    queryFn: () => api.expiryAlerts(leadDays),
  })
}

/**
 * Everything a roster change can invalidate (ADM-2).
 *
 * The same reasoning as [useSetHolding] below: an assignment changes slot coverage, the quota
 * counts, the gap report, the roll-ups and who is still a candidate elsewhere. None of that is
 * derivable here, so the cache is dropped rather than patched.
 */
function invalidateRoster(client: ReturnType<typeof useQueryClient>): void {
  void client.invalidateQueries({ queryKey: ['swing'] })
  void client.invalidateQueries({ queryKey: ['gaps'] })
  void client.invalidateQueries({ queryKey: ['suggestions'] })
  void client.invalidateQueries({ queryKey: ['assignments'] })
  void client.invalidateQueries({ queryKey: ['expiry-alerts'] })
}

/** ADM-2: fill a slot. A clash comes back as an `assignment_clash` 409, not a silent success. */
export function useAssign(partnership: string, cc: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (body: AssignRequest) => api.assign(partnership, cc, body),
    onSuccess: () => invalidateRoster(client),
  })
}

/** ADM-2: clear a slot. */
export function useUnassign() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (assignmentId: number) => api.unassign(assignmentId),
    onSuccess: () => invalidateRoster(client),
  })
}

/**
 * ADM-6 catalogue.
 *
 * Not cached under [REFERENCE_CACHE] even though the catalogue barely changes: this is the
 * *editing* view of it, and a Compliance Lead who retires an entry and sees the old row for an
 * hour would reasonably conclude the write failed. The read-only `/requirements` list keeps the
 * long cache — it is what every other screen joins codes against.
 */
export function useCatalogue(): UseQueryResult<RequirementDetail[]> {
  return useQuery({ queryKey: keys.catalogue, queryFn: api.catalogue })
}

/**
 * Every catalogue write invalidates the same set: the editing view, the cached read-only list
 * that every other screen joins against, and the evaluations — because retiring an entry or
 * renaming it changes what a cell means on screen even though no rule moved.
 */
function useCatalogueMutation<TArgs>(mutationFn: (args: TArgs) => Promise<RequirementDetail>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: keys.catalogue })
      void client.invalidateQueries({ queryKey: keys.requirements })
      void client.invalidateQueries({ queryKey: ['swing'] })
      void client.invalidateQueries({ queryKey: ['gaps'] })
    },
  })
}

export function useCreateRequirement() {
  return useCatalogueMutation((body: SaveRequirementRequest) => api.createRequirement(body))
}

export function useUpdateRequirement() {
  return useCatalogueMutation(
    ({ requirementId, body }: { requirementId: number; body: SaveRequirementRequest }) =>
      api.updateRequirement(requirementId, body),
  )
}

export function useAddAlias() {
  return useCatalogueMutation(({ requirementId, alias }: { requirementId: number; alias: string }) =>
    api.addAlias(requirementId, alias),
  )
}

export function useRemoveAlias() {
  return useCatalogueMutation(
    ({ requirementId, aliasId }: { requirementId: number; aliasId: number }) =>
      api.removeAlias(requirementId, aliasId),
  )
}

/**
 * ADM-4 — the register.
 *
 * Every write invalidates the evaluations as well as the register itself, and that is the whole
 * point of the module rather than caution: §5.1 step 4 turns a `gap` into `pending` the moment a
 * request is raised and into `exempt` when it is approved. A stale planner after a decision would
 * be showing an answer the server no longer gives.
 */
export function useRegister(filters: RegisterFilters): UseQueryResult<RegisterRecord[]> {
  return useQuery({
    queryKey: keys.register(filters),
    queryFn: () => api.register(filters),
  })
}

export function useRegisterRecord(recordId: string): UseQueryResult<RegisterRecordDetail> {
  return useQuery({
    queryKey: keys.registerRecord(recordId),
    queryFn: () => api.registerRecord(recordId),
  })
}

export function useNextRecordId(
  partnership: string | null,
  cc: string | null,
): UseQueryResult<{ recordId: string }> {
  return useQuery({
    queryKey: ['register', 'next-id', partnership ?? '', cc ?? ''],
    queryFn: () => api.nextRecordId(partnership as string, cc as string),
    enabled: partnership !== null && cc !== null,
  })
}

function useRegisterMutation<TArgs>(mutationFn: (args: TArgs) => Promise<RegisterRecordDetail>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['register'] })
      void client.invalidateQueries({ queryKey: ['swing'] })
      void client.invalidateQueries({ queryKey: ['gaps'] })
    },
  })
}

export function useCreateRegisterRecord() {
  return useRegisterMutation((body: CreateRegisterRecordRequest) => api.createRegisterRecord(body))
}

export function useTransitionRegisterRecord() {
  return useRegisterMutation(({ recordId, status }: { recordId: string; status: string }) =>
    api.transitionRegisterRecord(recordId, status),
  )
}

export function useCloseRegisterRecord() {
  return useRegisterMutation(
    ({ recordId, body }: { recordId: string; body: CloseRegisterRecordRequest }) =>
      api.closeRegisterRecord(recordId, body),
  )
}

export function useAddRegisterNote() {
  return useRegisterMutation(
    ({ recordId, party, body }: { recordId: string; party: string; body: string }) =>
      api.addRegisterNote(recordId, party, body),
  )
}

export function useAddRegisterCondition() {
  return useRegisterMutation(
    ({ recordId, body }: { recordId: string; body: AddConditionRequest }) =>
      api.addRegisterCondition(recordId, body),
  )
}

/**
 * ADM-7 — the data-quality worklist and Q11's chase list.
 *
 * The chase list is a live query over holdings rather than stored worklist rows, so it is
 * invalidated by a holding edit as well as by anything here: recording the answer to "do they
 * hold this" is what removes a row from it.
 */
export function useExceptions(state: 'open' | 'resolved' | 'all'): UseQueryResult<ExceptionItem[]> {
  return useQuery({ queryKey: keys.exceptions(state), queryFn: () => api.exceptions(state) })
}

export function useUnknownHoldings(): UseQueryResult<UnknownHolding[]> {
  return useQuery({ queryKey: keys.unknownHoldings, queryFn: api.unknownHoldings })
}

function useExceptionMutation<TArgs>(mutationFn: (args: TArgs) => Promise<ExceptionItem>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['exceptions'] })
    },
  })
}

export function useRaiseException() {
  return useExceptionMutation((body: RaiseExceptionRequest) => api.raiseException(body))
}

export function useResolveException() {
  return useExceptionMutation(({ id, note }: { id: number; note: string }) =>
    api.resolveException(id, note),
  )
}

export function useReopenException() {
  return useExceptionMutation((id: number) => api.reopenException(id))
}

// ---------------------------------------------------------------------------
// ADM-11 — the crew request queue
// ---------------------------------------------------------------------------

export function useCrewRequests(status: CrewRequestStatus): UseQueryResult<CrewRequest[]> {
  return useQuery({
    queryKey: keys.crewRequests(status),
    queryFn: () => api.crewRequests(status),
  })
}

export function useCrewRequestSummary(): UseQueryResult<CrewRequestSummary> {
  return useQuery({ queryKey: keys.crewRequestSummary, queryFn: api.crewRequestSummary })
}

/**
 * Decide one request.
 *
 * Invalidates the notifications as well as the queue, because both are views of the same arrival —
 * and the **compliance** queries too, for the case that is easy to miss: dismissing a `course_booked`
 * request resumes the expiry chasing it had silenced, so an expiry warning can reappear as a direct
 * result of this click.
 */
export function useDecideCrewRequest() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({
      id,
      decision,
      note,
    }: {
      id: number
      decision: 'action' | 'dismiss'
      note: string
    }) => api.decideCrewRequest(id, decision, note),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['crew-requests'] })
      void client.invalidateQueries({ queryKey: ['notifications'] })
    },
  })
}

/**
 * The holdings write path (ADM-5).
 *
 * On success everything derived from holdings is invalidated rather than patched in place. A
 * holding edit changes cell states, quota counts, the gap report and the roll-up through §5
 * rules this app deliberately does not implement — so the only correct local update is to ask
 * the server again.
 */
// ---------------------------------------------------------------------------
// ADM-3 — matrix versions (§5.5)
// ---------------------------------------------------------------------------

/**
 * Not under [REFERENCE_CACHE], even though a published matrix changes a handful of times a year.
 *
 * This is the *editing* view: a Compliance Lead who sets a cell and sees the old level for an hour
 * would reasonably conclude the write failed. The long cache stays on `/requirements` and
 * `/positions`, which this screen joins ids against and which really are static.
 */
export function useMatrixVersions(): UseQueryResult<MatrixVersionSummary[]> {
  return useQuery({ queryKey: keys.matrixVersions, queryFn: api.matrixVersions })
}

export function useMatrixVersion(versionId: number | null): UseQueryResult<MatrixVersionDetail> {
  return useQuery({
    queryKey: keys.matrixVersion(versionId ?? -1),
    queryFn: () => api.matrixVersion(versionId as number),
    enabled: versionId !== null,
  })
}

export function useMatrixDiff(from: number | null, to: number | null): UseQueryResult<MatrixDiff> {
  return useQuery({
    queryKey: keys.matrixDiff(from ?? -1, to ?? -1),
    queryFn: () => api.matrixDiff(from as number, to as number),
    enabled: from !== null && to !== null,
  })
}

/**
 * Every matrix write invalidates the evaluations as well as the matrix.
 *
 * Editing a *draft* cannot change a compliance answer — only the published version is evaluated
 * against — but publishing changes every answer in the system at once, and one invalidation set for
 * both is simpler than two that a later change could get wrong in the dangerous direction.
 */
function useMatrixMutation<TArgs, TResult>(mutationFn: (args: TArgs) => Promise<TResult>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['matrix-versions'] })
      void client.invalidateQueries({ queryKey: ['matrix-version'] })
      void client.invalidateQueries({ queryKey: ['matrix-diff'] })
      void client.invalidateQueries({ queryKey: ['swing'] })
      void client.invalidateQueries({ queryKey: ['gaps'] })
      void client.invalidateQueries({ queryKey: ['notifications'] })
    },
  })
}

export function useCreateMatrixDraft() {
  return useMatrixMutation((body: CreateMatrixDraftRequest) => api.createMatrixDraft(body))
}

export function useUpdateMatrixDraft() {
  return useMatrixMutation(
    ({ versionId, body }: { versionId: number; body: UpdateMatrixDraftRequest }) =>
      api.updateMatrixDraft(versionId, body),
  )
}

export function useDiscardMatrixDraft() {
  return useMatrixMutation((versionId: number) => api.discardMatrixDraft(versionId))
}

export function useSetMatrixCell() {
  return useMatrixMutation(
    ({ versionId, body }: { versionId: number; body: SetMatrixCellRequest }) =>
      api.setMatrixCell(versionId, body),
  )
}

export function useClearMatrixCell() {
  return useMatrixMutation(
    ({
      versionId,
      cell,
    }: {
      versionId: number
      cell: { positionId: number; requirementId: number; partnershipId?: number }
    }) => api.clearMatrixCell(versionId, cell),
  )
}

export function usePublishMatrixVersion() {
  return useMatrixMutation(
    ({ versionId, effectiveFrom }: { versionId: number; effectiveFrom?: string }) =>
      api.publishMatrixVersion(versionId, effectiveFrom),
  )
}

// ---------------------------------------------------------------------------
// ADM-8 — notifications (§9)
// ---------------------------------------------------------------------------

/**
 * The unread badge, polled.
 *
 * Notifications arrive from scheduled scans and from other people's actions, so unlike everything
 * else in this app there is no local event to invalidate on. A minute's refetch is the cheapest
 * honest answer: the summary is a count query, and nobody needs to learn about a roster gap within
 * the second.
 */
export function useNotificationSummary(): UseQueryResult<NotificationSummary> {
  return useQuery({
    queryKey: keys.notificationSummary,
    queryFn: api.notificationSummary,
    refetchInterval: 60 * 1000,
  })
}

export function useNotifications(
  state: 'all' | 'unread' | 'read',
): UseQueryResult<Notification[]> {
  return useQuery({ queryKey: keys.notifications(state), queryFn: () => api.notifications(state) })
}

function useNotificationMutation<TArgs, TResult>(mutationFn: (args: TArgs) => Promise<TResult>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['notifications'] })
    },
  })
}

export function useMarkNotificationRead() {
  return useNotificationMutation((notificationId: number) =>
    api.markNotificationRead(notificationId),
  )
}

export function useMarkAllNotificationsRead() {
  return useNotificationMutation(() => api.markAllNotificationsRead())
}

// ---------------------------------------------------------------------------
// ADM-9 — evidence verification queue (§8)
// ---------------------------------------------------------------------------

export function useEvidenceQueue(statuses: readonly string[]): UseQueryResult<EvidenceDocument[]> {
  return useQuery({
    queryKey: keys.evidenceQueue(statuses),
    queryFn: () => api.evidenceQueue(statuses),
  })
}

/**
 * A decision writes a holding, so everything derived from holdings goes with it.
 *
 * The notification list too: accepting or rejecting tells the submitter, and rejecting raises a
 * back-office row of its own.
 */
function useEvidenceMutation<TArgs>(mutationFn: (args: TArgs) => Promise<EvidenceDocument>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['evidence-queue'] })
      void client.invalidateQueries({ queryKey: ['person-evidence'] })
      void client.invalidateQueries({ queryKey: ['holdings'] })
      void client.invalidateQueries({ queryKey: ['swing'] })
      void client.invalidateQueries({ queryKey: ['gaps'] })
      void client.invalidateQueries({ queryKey: ['expiry-alerts'] })
      void client.invalidateQueries({ queryKey: ['notifications'] })
    },
  })
}

export function useAcceptEvidence() {
  return useEvidenceMutation(
    ({ publicId, body }: { publicId: string; body: AcceptEvidenceRequest }) =>
      api.acceptEvidence(publicId, body),
  )
}

export function useRejectEvidence() {
  return useEvidenceMutation(({ publicId, reason }: { publicId: string; reason: string }) =>
    api.rejectEvidence(publicId, reason),
  )
}

export function useExtractEvidence() {
  return useEvidenceMutation((publicId: string) => api.extractEvidence(publicId))
}

// ---------------------------------------------------------------------------
// ADM-12 — certificates on file
// ---------------------------------------------------------------------------

/** One person's certificates on file, newest first — the same cache the evidence mutations clear. */
export function usePersonEvidence(personId: number): UseQueryResult<EvidenceDocument[]> {
  return useQuery({ queryKey: keys.personEvidence(personId), queryFn: () => api.personEvidence(personId) })
}

// The read step is deliberately not a hook: it writes nothing, must never be retried (a model
// call with a bill behind it), and the dialog drives it as a plain promise — see CertificatesOnFile.

export function useOfficeFile() {
  return useEvidenceMutation(({ intakeId, body }: { intakeId: string; body: FileIntakeRequest }) =>
    api.officeFile(intakeId, body),
  )
}

export function useAmendEvidence() {
  return useEvidenceMutation(
    ({ publicId, body }: { publicId: string; body: AcceptEvidenceRequest }) =>
      api.amendEvidence(publicId, body),
  )
}

export function useRemoveEvidence() {
  return useEvidenceMutation(({ publicId, reason }: { publicId: string; reason: string }) =>
    api.removeEvidence(publicId, reason),
  )
}

export function useCreatePerson() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (body: CreatePersonRequest) => api.createPerson(body),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: keys.people })
    },
  })
}

// ---------------------------------------------------------------------------
// ADM-10 — administration
// ---------------------------------------------------------------------------

export function useConfig(): UseQueryResult<ConfigSetting[]> {
  return useQuery({ queryKey: keys.config, queryFn: api.config })
}

/**
 * A configuration change can move an engine answer — the expiry lead window and the suggestion
 * weights both feed §5.4 — so the derived reads go with it.
 */
function useConfigMutation<TArgs>(mutationFn: (args: TArgs) => Promise<ConfigSetting>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: keys.config })
      void client.invalidateQueries({ queryKey: ['expiry-alerts'] })
      void client.invalidateQueries({ queryKey: ['suggestions'] })
    },
  })
}

export function useSetConfig() {
  return useConfigMutation(({ key, value }: { key: string; value: unknown }) =>
    api.setConfig(key, value),
  )
}

export function useClearConfig() {
  return useConfigMutation((key: string) => api.clearConfig(key))
}

export function useJobs(): UseQueryResult<ScheduledJob[]> {
  return useQuery({ queryKey: keys.jobs, queryFn: api.jobs })
}

/**
 * Running a job invalidates almost everything, because that is what jobs do: the extraction sweep
 * moves documents through the pipeline and can write holdings, and each scan raises notifications.
 */
export function useRunJob() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (name: string) => api.runJob(name),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: keys.jobs })
      void client.invalidateQueries({ queryKey: ['notifications'] })
      void client.invalidateQueries({ queryKey: ['evidence-queue'] })
      void client.invalidateQueries({ queryKey: ['holdings'] })
    },
  })
}

export function useUserAccounts(): UseQueryResult<UserAccount[]> {
  return useQuery({ queryKey: keys.userAccounts, queryFn: api.userAccounts })
}

function useUserMutation<TArgs>(mutationFn: (args: TArgs) => Promise<UserAccount>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: keys.userAccounts })
      // A role change alters who §9 fans a notification out to.
      void client.invalidateQueries({ queryKey: ['notifications'] })
    },
  })
}

export function useCreateUserAccount() {
  return useUserMutation((body: CreateTransitionalAccountRequest) => api.createUserAccount(body))
}

export function useGrantRole() {
  return useUserMutation(({ userAccountId, role }: { userAccountId: number; role: string }) =>
    api.grantRole(userAccountId, role),
  )
}

export function useRevokeRole() {
  return useUserMutation(({ userAccountId, role }: { userAccountId: number; role: string }) =>
    api.revokeRole(userAccountId, role),
  )
}

export function useSetUserAccountStatus() {
  return useUserMutation(({ userAccountId, status }: { userAccountId: number; status: string }) =>
    api.setUserAccountStatus(userAccountId, status),
  )
}

export function useSetUserAccountScopes() {
  return useUserMutation(
    ({ userAccountId, partnershipIds }: { userAccountId: number; partnershipIds: number[] }) =>
      api.setUserAccountScopes(userAccountId, partnershipIds),
  )
}

export function useIdentityProviders(): UseQueryResult<IdentityProvider[]> {
  return useQuery({ queryKey: keys.identityProviders, queryFn: api.identityProviders })
}

function useIdentityProviderMutation<TArgs>(mutationFn: (args: TArgs) => Promise<IdentityProvider>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: keys.identityProviders })
    },
  })
}

export function useCreateIdentityProvider() {
  return useIdentityProviderMutation((body: CreateIdentityProviderRequest) =>
    api.createIdentityProvider(body),
  )
}

export function useSetIdentityProviderEnabled() {
  return useIdentityProviderMutation(
    ({ identityProviderId, enabled }: { identityProviderId: number; enabled: boolean }) =>
      api.setIdentityProviderEnabled(identityProviderId, enabled),
  )
}

export function useSetHolding(personId: number) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ requirementId, body }: { requirementId: number; body: SetHoldingRequest }) =>
      api.setHolding(personId, requirementId, body),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: keys.holdings(personId) })
      void client.invalidateQueries({ queryKey: keys.unknownHoldings })
      void client.invalidateQueries({ queryKey: ['swing'] })
      void client.invalidateQueries({ queryKey: ['gaps'] })
      void client.invalidateQueries({ queryKey: ['suggestions'] })
      void client.invalidateQueries({ queryKey: ['expiry-alerts'] })
    },
  })
}
