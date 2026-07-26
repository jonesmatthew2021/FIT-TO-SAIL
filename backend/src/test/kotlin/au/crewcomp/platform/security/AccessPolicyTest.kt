package au.crewcomp.platform.security

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** AUTH-1 / AUTH-2 — role checks and the single place a data scope is derived. */
@DisplayName("Access policy and crew row-scoping")
class AccessPolicyTest {

    private fun policyFor(actor: Actor?): Pair<AccessPolicy, ActorContext> {
        val context = ActorContext()
        actor?.let { context.set(it) }
        return AccessPolicy(context) to context
    }

    private fun actor(
        vararg roles: Role,
        personId: Long? = null,
        partnerships: Set<Long> = emptySet(),
        kind: ActorKind = ActorKind.HUMAN,
    ) = Actor(
        userAccountId = 1,
        personId = personId,
        roles = roles.toSet(),
        kind = kind,
        label = "Test Actor",
        partnershipIds = partnerships,
    )

    @Nested
    @DisplayName("scope derivation")
    inner class Scopes {

        @Test
        fun `back-office roles read everything`() {
            for (role in Role.UNRESTRICTED_READERS) {
                val (policy, _) = policyFor(actor(role))
                assertThat(policy.scope()).isEqualTo(DataScope.All)
            }
        }

        @Test
        fun `a crew member is scoped to their own person record`() {
            val (policy, _) = policyFor(actor(Role.CREW_MEMBER, personId = 42))
            assertThat(policy.scope()).isEqualTo(DataScope.OwnPersonOnly(42))
        }

        @Test
        fun `a vessel master is scoped to their partnerships`() {
            val (policy, _) = policyFor(actor(Role.VESSEL_MASTER, partnerships = setOf(1, 2)))
            assertThat(policy.scope()).isEqualTo(DataScope.Partnerships(setOf(1, 2)))
        }

        @Test
        fun `a crew member with no linked person record gets no scope at all`() {
            val (policy, _) = policyFor(actor(Role.CREW_MEMBER, personId = null))
            assertThat(policy.scope()).isEqualTo(DataScope.None)
        }

        @Test
        fun `an unauthenticated caller gets no scope`() {
            val (policy, _) = policyFor(null)
            assertThat(policy.scope()).isEqualTo(DataScope.None)
        }

        @Test
        fun `holding both a back-office role and crew member gives the wider scope`() {
            val (policy, _) = policyFor(actor(Role.CREW_MEMBER, Role.DATA_STEWARD, personId = 42))
            assertThat(policy.scope()).isEqualTo(DataScope.All)
        }
    }

    @Nested
    @DisplayName("role checks (AUTH-1)")
    inner class RoleChecks {

        @Test
        fun `require passes when the actor holds one of the allowed roles`() {
            val (policy, _) = policyFor(actor(Role.DATA_STEWARD))
            policy.require(Role.CREW_COORDINATOR, Role.DATA_STEWARD)
        }

        @Test
        fun `require throws when the actor holds none of them`() {
            val (policy, _) = policyFor(actor(Role.CREW_MEMBER, personId = 1))
            assertThatThrownBy { policy.require(Role.WORKFLOW_MANAGER) }
                .isInstanceOf(AccessDeniedException::class.java)
                .hasMessageContaining("workflow_manager")
        }

        @Test
        fun `require throws rather than returning false when unauthenticated`() {
            val (policy, _) = policyFor(null)
            assertThatThrownBy { policy.require(Role.DATA_STEWARD) }
                .isInstanceOf(NotAuthenticatedException::class.java)
        }

        @Test
        fun `a system actor bypasses role checks but is not a human`() {
            val (policy, _) = policyFor(Actor.system("expiry-scan"))
            policy.require(Role.WORKFLOW_MANAGER)
            assertThat(policy.actor().kind).isEqualTo(ActorKind.SYSTEM)
        }

        @Test
        fun `read-only roles are refused write intent`() {
            val (crewPolicy, _) = policyFor(actor(Role.CREW_MEMBER, personId = 1))
            assertThatThrownBy { crewPolicy.assertNotReadOnlyActor() }
                .isInstanceOf(AccessDeniedException::class.java)

            val (masterPolicy, _) = policyFor(actor(Role.VESSEL_MASTER, partnerships = setOf(1)))
            assertThatThrownBy { masterPolicy.assertNotReadOnlyActor() }
                .isInstanceOf(AccessDeniedException::class.java)
        }
    }

    @Nested
    @DisplayName("ScopeGuard")
    inner class Guard {

        @Test
        fun `an unrestricted actor gets a permit-all clause`() {
            val (policy, _) = policyFor(actor(Role.DATA_STEWARD))
            val clause = ScopeGuard(policy).clause("h.person.id", "h.partnership.id")

            assertThat(clause).isEqualTo(ScopeClause.PERMIT_ALL)
        }

        @Test
        fun `a crew member's clause binds their own person id`() {
            val (policy, _) = policyFor(actor(Role.CREW_MEMBER, personId = 42))
            val clause = ScopeGuard(policy).clause("h.person.id")

            assertThat(clause.hql).isEqualTo("h.person.id = :scopePersonId")
            assertThat(clause.params).containsEntry("scopePersonId", 42L)
        }

        @Test
        fun `a partnership-scoped actor querying an entity with no partnership path is denied all`() {
            // Failing closed matters more than convenience: a query that cannot express the
            // restriction must return nothing, not everything.
            val (policy, _) = policyFor(actor(Role.VESSEL_MASTER, partnerships = setOf(1)))
            val clause = ScopeGuard(policy).clause("h.person.id", partnershipPath = null)

            assertThat(clause).isEqualTo(ScopeClause.DENY_ALL)
        }

        @Test
        fun `assertVisible fails closed for another person's data`() {
            val (policy, _) = policyFor(actor(Role.CREW_MEMBER, personId = 42))
            val guard = ScopeGuard(policy)

            guard.assertVisible(42)
            assertThatThrownBy { guard.assertVisible(43) }
                .isInstanceOf(AccessDeniedException::class.java)
        }

        @Test
        fun `assertVisible denies an actor with no scope`() {
            val (policy, _) = policyFor(null)
            assertThatThrownBy { ScopeGuard(policy).assertVisible(1) }
                .isInstanceOf(AccessDeniedException::class.java)
        }

        @Test
        fun `retain drops out-of-scope rows that a query returned anyway`() {
            data class Row(val personId: Long, val partnershipId: Long)

            val (policy, _) = policyFor(actor(Role.CREW_MEMBER, personId = 42))
            val guard = ScopeGuard(policy)
            val rows = listOf(Row(42, 1), Row(43, 1), Row(44, 2))

            assertThat(guard.retain(rows, { it.personId }, { it.partnershipId }))
                .containsExactly(Row(42, 1))
        }

        @Test
        fun `retain keeps only the vessel master's partnerships`() {
            data class Row(val personId: Long, val partnershipId: Long)

            val (policy, _) = policyFor(actor(Role.VESSEL_MASTER, partnerships = setOf(2)))
            val rows = listOf(Row(42, 1), Row(43, 2))

            assertThat(ScopeGuard(policy).retain(rows, { it.personId }, { it.partnershipId }))
                .containsExactly(Row(43, 2))
        }

        @Test
        fun `clauses compose with and`() {
            val (policy, _) = policyFor(actor(Role.CREW_MEMBER, personId = 42))
            val clause = ScopeGuard(policy).clause("h.person.id").and("h.status = :status", mapOf("status" to "ok"))

            assertThat(clause.hql).isEqualTo("(h.person.id = :scopePersonId) and (h.status = :status)")
            assertThat(clause.params).containsKeys("scopePersonId", "status")
        }
    }

    @Test
    fun `runAs restores the previous actor`() {
        val (_, context) = policyFor(actor(Role.DATA_STEWARD))
        val system = Actor.system("job")

        context.runAs(system) {
            assertThat(context.require().kind).isEqualTo(ActorKind.SYSTEM)
        }

        assertThat(context.require().kind).isEqualTo(ActorKind.HUMAN)
    }
}
