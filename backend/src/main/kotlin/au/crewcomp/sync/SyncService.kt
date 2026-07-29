package au.crewcomp.sync

import au.crewcomp.api.CourseOfferDto
import au.crewcomp.api.EvidenceSubmissionDto
import au.crewcomp.api.HoldingDto
import au.crewcomp.api.NotificationDto
import au.crewcomp.api.SyncDeltaDto
import au.crewcomp.api.SyncOperationDto
import au.crewcomp.api.SyncOperationResultDto
import au.crewcomp.api.SyncQueueRequest
import au.crewcomp.api.SyncQueueResultDto
import au.crewcomp.api.SyncReferenceDto
import au.crewcomp.api.SyncSnapshotDto
import au.crewcomp.api.SyncStandingDto
import au.crewcomp.api.toDto
import au.crewcomp.api.toSyncDto
import au.crewcomp.compliance.ComplianceService
import au.crewcomp.courses.CourseOfferService
import au.crewcomp.evidence.EvidenceService
import au.crewcomp.evidence.EvidenceSource
import au.crewcomp.notify.NotificationRepository
import au.crewcomp.notify.NotificationService
import au.crewcomp.people.Assignment
import au.crewcomp.people.AttestationRepository
import au.crewcomp.people.AttestationService
import au.crewcomp.people.AssignmentRepository
import au.crewcomp.people.CrewStatementKind
import au.crewcomp.people.CrewStatementRepository
import au.crewcomp.people.CrewStatementService
import au.crewcomp.people.LeaveRecordRepository
import au.crewcomp.people.PersonRepository
import au.crewcomp.people.QualificationHoldingRepository
import au.crewcomp.people.TeamService
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessDeniedException
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.time.BusinessClock
import au.crewcomp.evidence.EvidenceDocumentRepository
import au.crewcomp.reference.CrewChangeRepository
import au.crewcomp.reference.CrewPositionRepository
import au.crewcomp.reference.PartnershipRepository
import au.crewcomp.reference.RequirementRepository
import au.crewcomp.workflow.RegisterService
import au.crewcomp.rules.MatrixVersionRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import org.jboss.logging.Logger
import java.time.Instant
import java.time.LocalDate

/**
 * §10.3 — the mobile sync service.
 *
 * **This service never takes a person id.** Every method answers for `policy.actor().personId`
 * and there is no parameter through which a caller could name someone else. That is a stronger
 * guarantee than a scope check on a supplied id: the whole-fleet leak is not a missing `if`, it
 * is unreachable. Back-office users have no Person record and therefore cannot sync at all,
 * which is correct — sync is the crew self-service read model, not a second admin API.
 *
 * The delta contract is deliberately dumb: rows whose `updated_seq` exceeds the client's cursor,
 * plus tombstones, plus a re-computed standing. There is no attempt to work out what the client
 * "needs"; a row the device already has is re-applied harmlessly, and being wrong in that
 * direction costs a few kilobytes while being wrong in the other direction loses data silently.
 */
@ApplicationScoped
class SyncService(
    private val delta: SyncDeltaRepository,
    private val people: PersonRepository,
    private val holdings: QualificationHoldingRepository,
    private val assignments: AssignmentRepository,
    private val leaveRecords: LeaveRecordRepository,
    private val notifications: NotificationRepository,
    private val documents: EvidenceDocumentRepository,
    private val partnerships: PartnershipRepository,
    private val positions: CrewPositionRepository,
    private val crewChanges: CrewChangeRepository,
    private val requirements: RequirementRepository,
    private val matrixVersions: MatrixVersionRepository,
    private val compliance: ComplianceService,
    private val evidence: EvidenceService,
    private val notificationService: NotificationService,
    private val crewStatements: CrewStatementService,
    private val courseOffers: CourseOfferService,
    private val crewStatementRepository: CrewStatementRepository,
    private val register: RegisterService,
    private val attestationService: AttestationService,
    private val attestationRepository: AttestationRepository,
    private val team: TeamService,
    private val policy: AccessPolicy,
    private val clock: BusinessClock,
) {

    /**
     * The full scoped dataset plus a cursor (§7.6 "full per-user snapshot").
     *
     * Estimated at well under a megabyte per crew member, which is why this exists at all: a
     * device that has been offline past its cursor's usefulness, or has just been wiped,
     * re-snapshots rather than reconciling.
     */
    @Transactional
    fun snapshot(): SyncSnapshotDto {
        val personId = selfPersonId()
        val person = people.findScoped(personId) ?: throw EntityNotFoundException("No person $personId")
        val standing = standingFor(personId)

        // Read the cursor FIRST, inside the same transaction and therefore the same snapshot as
        // the rows below. Taken afterwards it could include a sequence assigned by a concurrent
        // commit whose row this transaction cannot see — and the client would never ask for that
        // row again.
        val cursor = delta.currentCursor()
        val referenceCursor = delta.referenceCursor()

        return SyncSnapshotDto(
            cursor = cursor,
            referenceCursor = referenceCursor,
            serverToday = clock.today(),
            person = person.toDto(),
            holdings = holdings.forPersonScoped(personId).map { it.toDto() },
            assignments = assignments.forPersonScoped(personId).map { it.toDto() },
            leave = leaveRecords.forPersonScoped(personId).map { it.toDto() },
            notifications = notifications.forPersonScoped(personId).map { it.toDto() },
            submissions = documents.forPersonScoped(personId).map { it.toDto() },
            crewStatements = crewStatementRepository.forPersonScoped(personId).map { it.toSyncDto() },
            attestations = attestationRepository.forPersonScoped(personId).map { it.toSyncDto(clock.zone) },
            courseOptions = courseOffersFor(personId, standing),
            reference = reference(referenceCursor),
            standing = standing,
        )
    }

    /** Changes since [cursor]: changed rows, tombstones, and the recomputed standing. */
    @Transactional
    fun delta(cursor: Long): SyncDeltaDto {
        require(cursor >= 0) { "A sync cursor cannot be negative" }
        val personId = selfPersonId()

        val newCursor = delta.currentCursor()
        val referenceCursor = delta.referenceCursor()
        val standing = standingFor(personId)

        return SyncDeltaDto(
            cursor = newCursor,
            referenceCursor = referenceCursor,
            referenceStale = referenceCursor > cursor,
            serverToday = clock.today(),
            person = delta.person(personId, cursor)?.toDto(),
            holdings = delta.holdings(personId, cursor).map { it.toDto() },
            assignments = delta.assignments(personId, cursor).map { it.toDto() },
            leave = delta.leave(personId, cursor).map { it.toDto() },
            notifications = delta.notifications(personId, cursor).map { it.toDto() },
            submissions = delta.submissions(personId, cursor).map { it.toDto() },
            crewStatements = delta.crewStatements(personId, cursor).map { it.toSyncDto() },
            attestations = delta.attestations(personId, cursor).map { it.toSyncDto(clock.zone) },
            courseOptions = courseOffersFor(personId, standing),
            tombstones = delta.tombstones(personId, cursor).map { it.toDto() },
            // Always recomputed, never diffed: a holding expiring overnight changes the roll-up
            // without changing a single row, so a client that only applied row deltas would show
            // a stale "compliant" indefinitely.
            standing = standing,
        )
    }

    /**
     * Applies a batch of queued client writes, one transaction and one verdict each.
     *
     * Not annotated `@Transactional` on purpose — each operation is applied by its own
     * transactional service call, so one rejection does not roll back the operations that
     * succeeded beside it. See [SyncQueueResultDto] for why an all-or-nothing batch is the wrong
     * shape for a durable offline queue.
     */
    fun enqueue(request: SyncQueueRequest): SyncQueueResultDto {
        val personId = selfPersonId()
        require(request.operations.size <= MAX_BATCH) {
            "A queue batch may carry at most $MAX_BATCH operations"
        }

        val results = request.operations.map { operation ->
            try {
                apply(operation, personId)
            } catch (e: IllegalArgumentException) {
                // A malformed operation will never succeed; tell the device to drop it rather
                // than retry it until the end of time.
                rejected(operation, e.message)
            } catch (e: AccessDeniedException) {
                rejected(operation, e.message)
            } catch (e: EntityNotFoundException) {
                rejected(operation, e.message)
            } catch (e: RuntimeException) {
                log.warn("Sync operation ${operation.opId} (${operation.type}) failed", e)
                SyncOperationResultDto(operation.opId, STATUS_FAILED, "Temporary failure; retry")
            }
        }

        return SyncQueueResultDto(cursor = currentCursor(), results = results)
    }

    @Transactional
    fun currentCursor(): Long = delta.currentCursor()

    private fun apply(operation: SyncOperationDto, personId: Long): SyncOperationResultDto =
        when (operation.type) {
            OP_NOTIFICATION_READ -> {
                val notificationId = requireNotNull(operation.notificationId) {
                    "notification.read requires a notificationId"
                }
                val marked = notificationService.markRead(
                    notificationId = notificationId,
                    personId = personId,
                    // The device's timestamp is accepted for *when* it was read, but never
                    // trusted for whether it may be: the person scope decided that already.
                    at = operation.readAt ?: Instant.now(),
                )
                if (marked) {
                    SyncOperationResultDto(operation.opId, STATUS_APPLIED)
                } else {
                    rejected(operation, "No notification $notificationId for this crew member")
                }
            }

            OP_EVIDENCE_SUBMIT -> {
                val submission = requireNotNull(operation.submission) {
                    "evidence.submit requires a submission"
                }
                val source = EvidenceSource.fromWire(submission.source)
                require(source.isMobile) { "A device may not submit as '${submission.source}'" }

                val document = evidence.submit(
                    publicId = submission.publicId,
                    personId = personId,
                    source = source,
                    contentType = submission.contentType,
                    declaredSize = submission.declaredSize,
                    declaredSha256 = submission.declaredSha256,
                    requirementHintId = submission.requirementHintId,
                )
                SyncOperationResultDto(
                    opId = operation.opId,
                    status = STATUS_APPLIED,
                    // The resume point, so a device that queued the metadata offline knows
                    // immediately where its upload should continue from (MOB-5a).
                    uploadOffset = document.uploadOffset,
                )
            }

            // MOB-5's one-tap answers. Both are *statements* rather than decisions: they record
            // what the crew member said and route it to a Coordinator, and neither touches a
            // holding, a cell state or a roll-up (§7.5, AUTH-1). See [CrewStatementService].
            OP_REQUIREMENT_PROGRESS -> crewStatement(operation, personId, CrewStatementKind.COURSE_BOOKED)
            OP_REQUIREMENT_HELP -> crewStatement(operation, personId, CrewStatementKind.HELP_REQUESTED)

            // MOB-8's two, and they are statements for the same reason: a crew member cannot
            // commit a training budget or bind a provider, so asking for a seat is an *ask*. They
            // land in ADM-11 beside the other two and a coordinator books the course. Nothing here
            // holds a seat — see [au.crewcomp.courses.CourseCatalogue].
            OP_COURSE_SEAT_REQUEST -> crewStatement(operation, personId, CrewStatementKind.SEAT_REQUESTED)
            OP_COURSE_WAITLIST -> crewStatement(operation, personId, CrewStatementKind.WAITLISTED)

            // MOB-10. Unlike the two above this is *not* a statement — it writes a real §6.4
            // register record, which §5.1 step 4 then overlays onto the crew member's cell. It is
            // the one client-originated write that changes what the engine answers, and it does so
            // only by asking: the cell moves to `pending`, never to `exempt`, until a Workflow
            // Manager decides.
            OP_REGISTER_EXEMPTION_REQUEST -> {
                val requirementId = requireNotNull(operation.requirementId) {
                    "${operation.type} requires a requirementId"
                }
                val reason = requireNotNull(operation.reason) {
                    "${operation.type} requires a reason"
                }
                // Idempotent on `opId`, which the record itself carries (V7) — a replay returns the
                // request it already made rather than allocating a second business key for one ask.
                register.raiseCrewExemption(
                    opId = operation.opId,
                    personId = personId,
                    requirementId = requirementId,
                    ccId = operation.ccId,
                    reason = reason,
                    note = operation.note,
                    attachedOpIds = operation.attachedOpIds ?: emptyList(),
                )
                SyncOperationResultDto(operation.opId, STATUS_APPLIED)
            }

            // MOB-9. Evidence rather than a request: nobody actions an attestation, so it goes
            // into no queue and chases nobody. What matters is that the timestamp on it is the
            // server's — see [AttestationService].
            OP_ATTESTATION_SIGN_OFF -> {
                val assignmentId = requireNotNull(operation.assignmentId) {
                    "${operation.type} requires an assignmentId"
                }
                attestationService.sign(
                    opId = operation.opId,
                    personId = personId,
                    assignmentId = assignmentId,
                    declarations = operation.declarations ?: emptyList(),
                )
                SyncOperationResultDto(operation.opId, STATUS_APPLIED)
            }

            // MOB-11. The only operation on this queue that acts on somebody *else*, which is why
            // the target is checked against the supervisor's own watch rather than trusted, and why
            // the person nudged is always told who sent it — see [TeamService.nudge].
            OP_TEAM_NUDGE -> {
                val sam = requireNotNull(operation.targetSam) {
                    "${operation.type} requires the sam of the person to nudge"
                }
                team.nudge(sam = sam, note = operation.note)
                SyncOperationResultDto(operation.opId, STATUS_APPLIED)
            }

            else -> rejected(operation, "Unsupported operation type '${operation.type}'")
        }

    private fun crewStatement(
        operation: SyncOperationDto,
        personId: Long,
        kind: CrewStatementKind,
    ): SyncOperationResultDto {
        val requirementId = requireNotNull(operation.requirementId) {
            "${operation.type} requires a requirementId"
        }
        crewStatements.record(
            opId = operation.opId,
            personId = personId,
            requirementId = requirementId,
            kind = kind,
            // Passed straight through, mismatch and all. Only the two course kinds may carry one
            // and the service refuses either error rather than tidying it away: a seat request
            // that does not say which seat is not actionable, and a "course booked" that names a
            // catalogue option is a client that has confused two screens. Both are worth a
            // rejection the device can show, not a silent null.
            subjectRef = operation.subjectRef,
        )
        return SyncOperationResultDto(operation.opId, STATUS_APPLIED)
    }

    private fun rejected(operation: SyncOperationDto, detail: String?) =
        SyncOperationResultDto(operation.opId, STATUS_REJECTED, detail)

    // -----------------------------------------------------------------------
    // Assembly
    // -----------------------------------------------------------------------

    /**
     * The authenticated crew member's person id.
     *
     * A back-office actor has no Person record and gets a 403 rather than an empty snapshot:
     * "you have no data" and "this endpoint is not for you" are different answers, and the
     * second one is the true one.
     */
    private fun selfPersonId(): Long {
        val actor = policy.actor()
        return actor.personId ?: throw AccessDeniedException(
            "Sync serves a crew member's own data; ${actor.label} has no linked Person record",
        )
    }

    private fun reference(cursor: Long): SyncReferenceDto {
        val published = matrixVersions.currentPublished()
        return SyncReferenceDto(
            cursor = cursor,
            matrixVersionId = published?.id,
            matrixVersionLabel = published?.label,
            // The full catalogue: it is a few hundred rows of codes and titles, and filtering it
            // to the person's position would break the moment they are considered for another.
            requirements = requirements.listAll().map { it.toDto() },
            positions = positions.allOrdered().map { it.toDto() },
            partnerships = partnerships.allOrdered().map { it.toDto() },
            crewChanges = crewChanges.listAll().map { it.toDto() },
        )
    }

    /**
     * Evaluates the crew member against their current swing, or their next one if none is in
     * progress. Returns null when they have neither — the app shows "no upcoming swing".
     */
    private fun standingFor(personId: Long): SyncStandingDto? {
        val today = clock.today()
        val mine = assignments.forPersonScoped(personId)
        val chosen = currentOrNext(mine, today) ?: return null

        val crewChange = chosen.crewChange
        val evaluation = compliance.evaluatePerson(
            personId = personId,
            partnershipAbbrev = crewChange.partnership.abbrev,
            ccId = crewChange.ccId,
        )

        return SyncStandingDto(
            ccId = crewChange.ccId,
            partnershipAbbrev = crewChange.partnership.abbrev,
            from = crewChange.fromDate,
            to = crewChange.toDate,
            current = !today.isBefore(crewChange.fromDate) && !today.isAfter(crewChange.toDate),
            evaluation = evaluation.toDto(),
        )
    }

    /**
     * MOB-8's dates, for the requirements this crew member actually needs something for.
     *
     * Driven off the standing evaluation rather than off the catalogue, which is what keeps the
     * design's "only dates that would work" promise honest at the other end too: no swing means no
     * evaluation means no offers, and a requirement the person already holds gets none either.
     *
     * [ATTENTION_STATES] is deliberately the same grouping the app calls `needsAttention`
     * (`mobile/lib/src/domain/states.dart`). The screen that offers a course is reached from a
     * certification row in that group, so a mismatch would produce either a row with a "Book a
     * course" button and no dates behind it, or dates for a row nobody can reach.
     */
    private fun courseOffersFor(personId: Long, standing: SyncStandingDto?): List<CourseOfferDto> {
        val needs = standing?.evaluation?.cells
            ?.filter { it.state in ATTENTION_STATES }
            ?.map { it.requirementId }
            ?: return emptyList()

        return courseOffers.forPerson(personId, needs).map { it.toDto() }
    }

    private fun currentOrNext(assignments: List<Assignment>, today: LocalDate): Assignment? {
        val inProgress = assignments.firstOrNull {
            !today.isBefore(it.crewChange.fromDate) && !today.isAfter(it.crewChange.toDate)
        }
        if (inProgress != null) return inProgress
        return assignments
            .filter { it.crewChange.fromDate.isAfter(today) }
            .minByOrNull { it.crewChange.fromDate }
    }

    companion object {
        private val log: Logger = Logger.getLogger(SyncService::class.java)

        const val OP_NOTIFICATION_READ = "notification.read"
        const val OP_EVIDENCE_SUBMIT = "evidence.submit"

        /**
         * MOB-5's two answers, named by the enum rather than by a literal here.
         *
         * One source of truth on purpose: the same strings travel *back* to the device in
         * `CrewStatementSyncDto.kind`, and a copy of them in this file is a copy that can drift
         * from the one the payload sends — which is exactly the bug that shipped the first time,
         * where the queue accepted `requirement.progress` and the payload answered `course_booked`.
         */
        val OP_REQUIREMENT_PROGRESS: String = CrewStatementKind.COURSE_BOOKED.operation
        val OP_REQUIREMENT_HELP: String = CrewStatementKind.HELP_REQUESTED.operation

        /** MOB-8's two, named the same way and for the same reason. */
        val OP_COURSE_SEAT_REQUEST: String = CrewStatementKind.SEAT_REQUESTED.operation
        val OP_COURSE_WAITLIST: String = CrewStatementKind.WAITLISTED.operation

        /** MOB-10: "I cannot get this in time — please consider an exemption." */
        const val OP_REGISTER_EXEMPTION_REQUEST = "register.exemption_request"

        /** MOB-9: the pre-sail declaration, signed. */
        const val OP_ATTESTATION_SIGN_OFF = "attestation.sign_off"

        /** MOB-11: a supervisor chasing a member of their watch. */
        const val OP_TEAM_NUDGE = "team.nudge"

        const val STATUS_APPLIED = "applied"
        const val STATUS_REJECTED = "rejected"
        const val STATUS_FAILED = "failed"

        /** Bounded so a device cannot present an unbounded batch after a long offline spell. */
        const val MAX_BATCH = 200

        /**
         * Cell states worth offering a course against — the app's `needsAttention` grouping,
         * kept in step deliberately.
         *
         * `pending` and `exempt` are absent because something is already moving: offering a course
         * to somebody whose exemption is with a Workflow Manager invites them to solve a problem
         * twice.
         */
        val ATTENTION_STATES = setOf("gap", "expiring", "unknown", "review")
    }
}
