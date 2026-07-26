package au.crewcomp.platform.persistence

/**
 * The requested entity does not exist, **or** the actor may not see it.
 *
 * Conflating the two is deliberate. A scoped read (AUTH-2) that answered 403 for a row the actor
 * cannot see and 404 for one that does not exist would let a crew member enumerate the person
 * table by watching status codes. Where the actor's own scope decides visibility, "not found" is
 * the only answer that leaks nothing.
 *
 * Authorisation failures that do not depend on row scope — a role check, a partnership check on
 * a resource the actor already named — still raise
 * [au.crewcomp.platform.security.AccessDeniedException], because there the actor already knows
 * the thing exists and an opaque 404 would only cost support time.
 */
class EntityNotFoundException(message: String) : RuntimeException(message)
