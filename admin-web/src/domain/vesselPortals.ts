/**
 * Which ships have a crew portal of their own, and where each one lives.
 *
 * A vessel portal is Matt's Coolibah portal stamped for a company from the
 * `vessel-portal/` template at the repo root — its own worker, crew password,
 * file store and AI account per company, deliberately sharing nothing with
 * CREWCOMP or with each other. The certification checkers over there and the
 * compliance engine here read the same certificates independently, which is
 * what makes each a cross-check on the other.
 *
 * Keyed by partnership abbreviation. A deliberate interim: the right home for
 * a portal address is a field on the partnership itself (entity + migration +
 * regenerated types), which this branch doesn't add so the schema change gets
 * Chris's eyes first. Until then this map is the registry, kept in step with
 * `vessel-portal/vessels/*.json` by hand.
 */
const PORTALS: Record<string, string> = {
  UNI: 'https://coolibah-portal.coolibah-portal-worker.workers.dev',
}

/** The ship's own portal address, or null when the company doesn't have one yet. */
export function vesselPortalUrl(partnershipAbbrev: string): string | null {
  return PORTALS[partnershipAbbrev] ?? null
}
