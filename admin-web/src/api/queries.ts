import { useMutation, useQuery, useQueryClient, type UseQueryResult } from '@tanstack/react-query'
import {
  api,
  type Assignment,
  type CrewChange,
  type ExpiryAlert,
  type GapReportRow,
  type Holding,
  type Partnership,
  type Person,
  type Position,
  type Requirement,
  type SetHoldingRequest,
  type Slot,
  type Suggestion,
  type SwingEvaluation,
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
      void client.invalidateQueries({ queryKey: ['swing'] })
      void client.invalidateQueries({ queryKey: ['gaps'] })
      void client.invalidateQueries({ queryKey: ['suggestions'] })
      void client.invalidateQueries({ queryKey: ['expiry-alerts'] })
    },
  })
}
