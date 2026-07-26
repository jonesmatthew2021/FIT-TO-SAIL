package au.crewcomp.platform.security

/**
 * AUTH-2 — the read scope of the current actor, derived once from their roles.
 *
 * Deriving this in exactly one place ([AccessPolicy]) is what makes "crew row-scoping is enforced
 * centrally, never per-endpoint" true: an endpoint cannot accidentally widen its own scope,
 * because it never computes one.
 */
sealed interface DataScope {

    /** Back-office roles that read all data (§3). */
    data object All : DataScope

    /** Vessel Master: read-only, scoped to their vessel pair/partnership (§3). */
    data class Partnerships(val partnershipIds: Set<Long>) : DataScope

    /** Crew Member: own records only (§3, AUTH-2). */
    data class OwnPersonOnly(val personId: Long) : DataScope

    /** No authenticated actor, or an account with no usable role — deny everything. */
    data object None : DataScope

    val isUnrestricted: Boolean get() = this is All
}
