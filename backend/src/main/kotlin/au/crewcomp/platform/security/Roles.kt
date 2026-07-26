package au.crewcomp.platform.security

/**
 * Spec §3 role model. Roles are assignable per user; one user may hold several.
 *
 * The wire values match the `user_account_role.role` CHECK constraint in V1__baseline.sql.
 */
enum class Role(val wire: String) {
    CREW_COORDINATOR("crew_coordinator"),
    WORKFLOW_MANAGER("workflow_manager"),
    COMPLIANCE_LEAD("compliance_lead"),
    DATA_STEWARD("data_steward"),
    VESSEL_MASTER("vessel_master"),
    CREW_MEMBER("crew_member"),
    SYSTEM_ADMINISTRATOR("system_administrator");

    companion object {
        private val byWire = entries.associateBy { it.wire }

        fun fromWire(wire: String): Role =
            byWire[wire] ?: throw IllegalArgumentException("Unknown role: $wire")

        /** Roles that read the whole dataset (§3). Crew Member and Vessel Master do not. */
        val UNRESTRICTED_READERS: Set<Role> = setOf(
            CREW_COORDINATOR, WORKFLOW_MANAGER, COMPLIANCE_LEAD, DATA_STEWARD, SYSTEM_ADMINISTRATOR,
        )
    }
}

/**
 * AIA-3 — the actor kinds an AuditEvent must distinguish.
 *
 * `AI_PROPOSED` means an AI drafted the change and an authorised human approved it: the audit
 * event then carries both the AI model/config and the approving human's user account.
 */
enum class ActorKind(val wire: String) {
    HUMAN("human"),
    AI_PROPOSED("ai_proposed"),
    AI_AUTOMATIC("ai_automatic"),
    /** Scheduled jobs and migrations — never used for a change a person or an AI initiated. */
    SYSTEM("system");
}
