import { useMutation, useQuery, useQueryClient, type UseQueryResult } from '@tanstack/react-query'
import {
  api,
  type AddConditionRequest,
  type AssignRequest,
  type Assignment,
  type CloseRegisterRecordRequest,
  type CreateRegisterRecordRequest,
  type CrewChange,
  type ExceptionItem,
  type ExpiryAlert,
  type GapReportRow,
  type Holding,
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
  type SetHoldingRequest,
  type Slot,
  type Suggestion,
  type SwingEvaluation,
  type UnknownHolding,
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

/**
 * The holdings write path (ADM-5).
 *
 * On success everything derived from holdings is invalidated rather than patched in place. A
 * holding edit changes cell states, quota counts, the gap report and the roll-up through §5
 * rules this app deliberately does not implement — so the only correct local update is to ask
 * the server again.
 */
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
