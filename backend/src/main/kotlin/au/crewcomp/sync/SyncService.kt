package au.crewcomp.sync

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
import au.crewcomp.evidence.EvidenceService
import au.crewcomp.evidence.EvidenceSource
import au.crewcomp.notify.NotificationRepository
import au.crewcomp.notify.NotificationService
import au.crewcomp.people.Assignment
import au.crewcomp.people.AssignmentRepository
import au.crewcomp.people.CrewStatementKind
import au.crewcomp.people.CrewStatementRepository
import au.crewcomp.people.CrewStatementService
import au.crewcomp.people.LeaveRecordRepository
import au.crewcomp.people.PersonRepository
import au.crewcomp.people.QualificationHoldingRepository
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessDeniedException
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.time.BusinessClock
import au.crewcomp.evidence.EvidenceDocumentRepository
import au.crewcomp.reference.CrewChangeRepository
import au.crewcomp.reference.CrewPositionRepository
import au.crewcomp.reference.PartnershipRepository
import au.crewcomp.reference.RequirementRepository
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
    private val crewStatementRepository: CrewStatementRepository,
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
            reference = reference(referenceCursor),
            standing = standingFor(personId),
        )
    }

    /** Changes since [cursor]: changed rows, tombstones, and the recomputed standing. */
    @Transactional
    fun delta(cursor: Long): SyncDeltaDto {
        require(cursor >= 0) { "A sync cursor cannot be negative" }
        val personId = selfPersonId()

        val newCursor = delta.currentCursor()
        val referenceCursor = delta.referenceCursor()

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
            tombstones = delta.tombstones(personId, cursor).map { it.toDto() },
            // Always recomputed, never diffed: a holding expiring overnight changes the roll-up
            // without changing a single row, so a client that only applied row deltas would show
            // a stale "compliant" indefinitely.
            standing = standingFor(personId),
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

        const val STATUS_APPLIED = "applied"
        const val STATUS_REJECTED = "rejected"
        const val STATUS_FAILED = "failed"

        /** Bounded so a device cannot present an unbounded batch after a long offline spell. */
        const val MAX_BATCH = 200
    }
}
