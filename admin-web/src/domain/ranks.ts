import type { Person } from '../api/client'

/**
 * The rank groups, in sailing order: Master, then Officers, Deck crew, Engineers, Stewards, Cooks.
 *
 * Presentation grouping only, matched on the position's *name* because that is all `PersonDto`
 * carries — a position no pattern recognises lands in a trailing group rather than vanishing,
 * for the same reason an unknown enum value renders as its wire value. A group with nobody in it
 * is not rendered at all (there are no stewards on the current roster, and an empty STEWARDS
 * heading would be a claim about the roster this view has no business making).
 *
 * Shared by the crew matrix (ADM-3) and the certificates on file, which are the two screens the
 * Coolibah portal lays out this way.
 */
export const RANK_GROUPS: readonly { label: string; matches: (positionName: string) => boolean }[] = [
  { label: 'Master', matches: (name) => /\bmaster\b/i.test(name) },
  { label: 'Officers', matches: (name) => /officer|mate/i.test(name) },
  { label: 'Deck crew', matches: (name) => /gph|deck|rating|seafarer/i.test(name) },
  { label: 'Engineers', matches: (name) => /engineer/i.test(name) },
  { label: 'Stewards', matches: (name) => /steward/i.test(name) },
  { label: 'Cooks', matches: (name) => /cook|chef/i.test(name) },
]

export function rankGroup(positionName: string): string {
  return RANK_GROUPS.find((group) => group.matches(positionName))?.label ?? 'Other positions'
}

/** The people, grouped in RANK_GROUPS order; empty groups dropped, unmatched last. */
export function groupByRank(people: readonly Person[]): { label: string; people: Person[] }[] {
  const order = [...RANK_GROUPS.map((group) => group.label), 'Other positions']
  return order
    .map((label) => ({ label, people: people.filter((person) => rankGroup(person.positionName) === label) }))
    .filter((group) => group.people.length > 0)
}
