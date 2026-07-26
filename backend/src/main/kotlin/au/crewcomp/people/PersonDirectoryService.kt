package au.crewcomp.people

import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessPolicy
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

/**
 * Read access to the §4.3 people layer — ADM-5's person list and person detail, and the same
 * data the mobile app reads about its own user.
 *
 * Every method here is **row-scoped by construction** (AUTH-2): the repositories take their
 * restriction from [au.crewcomp.platform.security.ScopeGuard], not from the caller, so a crew
 * member calling `list()` gets a one-row list and a Vessel Master gets their partnerships. There
 * is deliberately no `personId` filter parameter on `list()` — a parameter is something a caller
 * can forget, and AUTH-2 says the scope is not the caller's to decide.
 */
@ApplicationScoped
class PersonDirectoryService(
    private val people: PersonRepository,
    private val holdings: QualificationHoldingRepository,
    private val assignments: AssignmentRepository,
    private val leave: LeaveRecordRepository,
    private val policy: AccessPolicy,
) {

    @Transactional
    fun list(): List<Person> {
        policy.actor()
        return people.listScoped()
    }

    /**
     * One person. Invisible and absent are the same answer — see [EntityNotFoundException] for
     * why the scoped read must not distinguish them.
     */
    @Transactional
    fun get(personId: Long): Person = people.findScoped(personId)
        ?: throw EntityNotFoundException("No person $personId")

    @Transactional
    fun holdingsFor(personId: Long): List<QualificationHolding> {
        get(personId)
        return holdings.forPersonScoped(personId).sortedWith(
            compareBy({ it.requirement.category }, { it.requirement.code }),
        )
    }

    @Transactional
    fun assignmentsFor(personId: Long): List<Assignment> {
        get(personId)
        return assignments.forPersonScoped(personId)
    }

    @Transactional
    fun leaveFor(personId: Long): List<LeaveRecord> {
        get(personId)
        return leave.forPersonScoped(personId)
    }
}
