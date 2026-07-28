import type { Requirement } from '../api/client'

/**
 * Joining a requirement id to the catalogue.
 *
 * Requirement ids arrive on the wire; the catalogue is cached once (`useRequirements`) and joined
 * here rather than being asked for per row. An id with no entry degrades to `#42` rather than
 * blanking the cell — during an expand/contract deploy a rule can name a requirement this revision's
 * cached catalogue has not seen.
 */
export interface RequirementParts {
  readonly code: string
  readonly title: string
}

export function requirementParts(
  requirements: readonly Requirement[] | undefined,
): (id: number) => RequirementParts {
  const byId = new Map((requirements ?? []).map((requirement) => [requirement.id, requirement]))
  return (id) => {
    const requirement = byId.get(id)
    return requirement === undefined
      ? { code: `#${id}`, title: '' }
      : { code: requirement.code, title: requirement.title }
  }
}

/**
 * The same join flattened to one string, for sorting, filtering and CSV.
 *
 * On screen a requirement is a monospaced code with its title as a grey annotation beside it
 * (`RequirementLabel`); in a CSV cell and in a filter predicate it is one field, and both halves
 * have to be searchable — somebody looking for "Work at Heights" should find `PS-02`.
 */
export function requirementLookup(
  requirements: readonly Requirement[] | undefined,
): (id: number) => string {
  const parts = requirementParts(requirements)
  return (id) => {
    const { code, title } = parts(id)
    return title === '' ? code : `${code} ${title}`
  }
}
