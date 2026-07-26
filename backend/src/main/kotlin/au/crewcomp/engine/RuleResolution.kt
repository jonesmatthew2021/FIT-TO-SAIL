package au.crewcomp.engine

/**
 * §4.2 rule resolution — normative, as implemented in the POC's `Engine.effectiveRules`.
 *
 *  - base (`'*'`, modelled here as `partnershipId == null`) rules apply to every partnership;
 *  - a partnership-specific row for the same (position, requirement) **overrides** the base;
 *  - an override with an empty level **removes** the requirement for that partnership.
 *
 * Granularity is per position pending O-2. The resolution key is isolated in [RuleKey] so that
 * adding a slot/shift dimension is a change to one type rather than a migration of call sites.
 */
data class RuleKey(val positionId: PositionId, val requirementId: RequirementId)

/**
 * Resolves the rules of [this] matrix version as they apply to [partnershipId].
 *
 * @return the effective level per (position, requirement); removed requirements are absent
 *   from the map rather than present with a blank level.
 */
fun MatrixSnapshot.effectiveRules(partnershipId: PartnershipId): Map<RuleKey, RuleLevel> {
    val resolved = LinkedHashMap<RuleKey, RuleLevel>()

    for (rule in rules) {
        if (rule.partnershipId != null) continue
        val key = RuleKey(rule.positionId, rule.requirementId)
        if (rule.level.isBlank) resolved.remove(key) else resolved[key] = rule.level
    }

    for (rule in rules) {
        if (rule.partnershipId != partnershipId) continue
        val key = RuleKey(rule.positionId, rule.requirementId)
        if (rule.level.isBlank) resolved.remove(key) else resolved[key] = rule.level
    }

    return resolved
}

/** The effective rules for one position within [partnershipId], keyed by requirement. */
fun MatrixSnapshot.effectiveRulesForPosition(
    partnershipId: PartnershipId,
    positionId: PositionId,
): Map<RequirementId, RuleLevel> =
    effectiveRules(partnershipId)
        .filterKeys { it.positionId == positionId }
        .mapKeys { (key, _) -> key.requirementId }

/** The conditional rules attached to this version that apply to [positionId]. */
fun MatrixSnapshot.conditionalsForPosition(positionId: PositionId): List<ConditionalRuleView> =
    conditionals.filter { it.positionId == positionId }
