import 'dart:convert';
import 'dart:math';

import 'package:drift/drift.dart';

import '../api/crewcomp_api.dart';
import '../api/schema.g.dart';
import 'evidence_uploader.dart';
import 'local_store.dart';

/// MOB-5: snapshot + delta replication, and the durable outbound queue.
///
/// The design is the spec's, not an invention: a full per-user snapshot, incremental deltas
/// against one scalar cursor, and a write model restricted to operations that are idempotent by
/// construction (§7.6). That restriction is what removes conflict resolution from this app
/// entirely — there is no merge logic below because there is nothing to merge.
///
/// Ordering rule, and the reason for it: **the outbox is flushed before the pull**. A device
/// that pulls first would receive rows that predate its own queued writes and then apply them
/// over the top, so a read-mark made offline would visibly un-tick itself on the next sync.
class SyncEngine {
  SyncEngine({
    required this.api,
    required this.store,
    this.now = _systemNow,
    EvidenceUploader? uploader,
  }) : uploader = uploader ?? EvidenceUploader(api: api, store: store);

  final CrewcompApi api;
  final LocalStore store;

  /// MOB-5a. Separate from the queue because the bytes are: see `evidence_uploader.dart`.
  final EvidenceUploader uploader;

  /// Injected so retry back-off is testable without waiting. Not a source of business dates —
  /// those come from the server's `serverToday`.
  final DateTime Function() now;

  static DateTime _systemNow() => DateTime.now().toUtc();

  /// SEC-12: after this long without a successful sync the cached data locks.
  static const offlineValidity = Duration(days: 30);

  /// How long a delivered one-tap answer stays on the device. Long enough that "you told the
  /// office on 25 Jul" is still on the requirement when the certificate finally arrives.
  static const intentRetention = Duration(days: 30);

  static const maxAttempts = 8;

  /// Runs a full sync cycle: push the queue, then pull.
  ///
  /// Returns what happened, so the UI can distinguish "nothing to say" from "we are offline"
  /// without inspecting exceptions.
  Future<SyncOutcome> sync() async {
    try {
      final pushed = await flushOutbox();

      // Between the push and the pull, and in that order for two reasons. The flush is what
      // registers a submission, so nothing can be appended to before it runs; and the pull is
      // what brings back the verdict, which is only worth asking for once the bytes are there.
      final uploaded = await uploader.uploadPending();

      final pulled = await pull();

      // After the pull, so an intent is only forgotten once whatever it caused has had a chance
      // to come back down as a real record.
      await pruneSettledIntents();

      return SyncOutcome(
        succeeded: true,
        operationsPushed: pushed,
        uploadsCompleted: uploaded,
        resnapshotted: pulled.resnapshotted,
      );
    } on ApiException catch (e) {
      return SyncOutcome(succeeded: false, error: e.toString(), unauthenticated: e.isUnauthenticated);
    } on Exception catch (e) {
      // Offline is the ordinary case out here, not an error worth a dialog.
      return SyncOutcome(succeeded: false, error: e.toString());
    }
  }

  // -------------------------------------------------------------------------
  // Pull
  // -------------------------------------------------------------------------

  Future<({bool resnapshotted})> pull() async {
    final state = await currentState();
    if (state.cursor == 0) {
      await _applySnapshot(await api.snapshot());
      return (resnapshotted: true);
    }

    final delta = await api.delta(state.cursor);
    await _applyDelta(delta);

    // Reference data moved — a matrix publication, most likely. Applying half of one would show
    // a view assembled from two matrix versions, so the whole payload is refetched.
    if (delta.referenceStale) {
      await _applySnapshot(await api.snapshot());
      return (resnapshotted: true);
    }
    return (resnapshotted: false);
  }

  Future<void> _applySnapshot(SyncSnapshotDto snapshot) async {
    // `local_path` is the one column on a submission the server does not own, and a snapshot
    // replaces every server-owned row wholesale. Losing it would strand the bytes: the row comes
    // back from the payload looking like an ordinary in-flight submission, with nothing left to
    // say where the file is, so the uploader skips it forever and the crew member waits on a
    // verdict for a document that will never arrive. Carried across the replacement by hand.
    final stagedPaths = {
      for (final row in await (store.select(store.submissions)
                ..where((t) => t.localPath.isNotNull()))
              .get())
        row.publicId: row.localPath!,
    };

    await store.transaction(() async {
      // A snapshot is authoritative for everything server-owned, so the replica is replaced
      // rather than merged. The outbox is untouched: those are the device's own writes and the
      // server has not seen them yet.
      await store.delete(store.people).go();
      await store.delete(store.holdings).go();
      await store.delete(store.assignments).go();
      await store.delete(store.leaveRecords).go();
      await store.delete(store.notifications).go();
      await store.delete(store.submissions).go();
      await store.delete(store.requirements).go();
      await store.delete(store.crewChanges).go();
      // Server-owned, so it is replaced like the rest. `crew_intents` beside it is *not* — those
      // are this device's own records of what it sent, and a snapshot has nothing to say about
      // an answer the server has not seen yet.
      await store.delete(store.crewStatements).go();
      await store.delete(store.attestations).go();

      await _upsertPerson(snapshot.person);
      for (final holding in snapshot.holdings) {
        await _upsertHolding(holding);
      }
      for (final assignment in snapshot.assignments) {
        await _upsertAssignment(assignment);
      }
      for (final leave in snapshot.leave) {
        await _upsertLeave(leave);
      }
      for (final notification in snapshot.notifications) {
        await _upsertNotification(notification);
      }
      for (final submission in snapshot.submissions) {
        await _upsertSubmission(submission);
        final staged = stagedPaths[submission.publicId];
        if (staged != null) {
          await (store.update(store.submissions)
                ..where((t) => t.publicId.equals(submission.publicId)))
              .write(SubmissionsCompanion(localPath: Value(staged)));
        }
      }
      for (final statement in snapshot.crewStatements) {
        await _upsertCrewStatement(statement);
      }
      for (final attestation in snapshot.attestations) {
        await _upsertAttestation(attestation);
      }
      for (final requirement in snapshot.reference.requirements) {
        await store.into(store.requirements).insertOnConflictUpdate(
              RequirementsCompanion.insert(
                id: Value(requirement.id),
                code: requirement.code,
                category: requirement.category,
                title: requirement.title,
                status: requirement.status,
                issuingAuthority: Value(requirement.issuingAuthority),
              ),
            );
      }
      for (final crewChange in snapshot.reference.crewChanges) {
        await store.into(store.crewChanges).insertOnConflictUpdate(
              CrewChangesCompanion.insert(
                id: Value(crewChange.id),
                ccId: crewChange.ccId,
                fromDate: crewChange.from,
                toDate: crewChange.to,
                cutoff: crewChange.cutoff,
              ),
            );
      }

      await _replaceStanding(snapshot.standing);
      await _writeState(
        cursor: snapshot.cursor,
        referenceCursor: snapshot.referenceCursor,
        serverToday: snapshot.serverToday,
        standing: snapshot.standing,
      );
    });
  }

  Future<void> _applyDelta(SyncDeltaDto delta) async {
    await store.transaction(() async {
      if (delta.person != null) await _upsertPerson(delta.person!);
      for (final holding in delta.holdings) {
        await _upsertHolding(holding);
      }
      for (final assignment in delta.assignments) {
        await _upsertAssignment(assignment);
      }
      for (final leave in delta.leave) {
        await _upsertLeave(leave);
      }
      for (final notification in delta.notifications) {
        await _upsertNotification(notification);
      }
      for (final submission in delta.submissions) {
        await _upsertSubmission(submission);
      }
      for (final statement in delta.crewStatements) {
        await _upsertCrewStatement(statement);
      }
      for (final attestation in delta.attestations) {
        await _upsertAttestation(attestation);
      }
      for (final tombstone in delta.tombstones) {
        await _applyTombstone(tombstone);
      }

      // Always replaced, never diffed: a roll-up can change with no row change at all, when a
      // holding simply expires overnight.
      await _replaceStanding(delta.standing);
      await _writeState(
        cursor: delta.cursor,
        referenceCursor: delta.referenceCursor,
        serverToday: delta.serverToday,
        standing: delta.standing,
      );
    });
  }

  Future<void> _applyTombstone(SyncTombstoneDto tombstone) async {
    switch (tombstone.entityType) {
      case 'QualificationHolding':
        await (store.delete(store.holdings)
              ..where((t) => t.id.equals(tombstone.entityId)))
            .go();
      case 'Assignment':
        await (store.delete(store.assignments)
              ..where((t) => t.id.equals(tombstone.entityId)))
            .go();
      case 'LeaveRecord':
        await (store.delete(store.leaveRecords)
              ..where((t) => t.id.equals(tombstone.entityId)))
            .go();
      case 'Notification':
        await (store.delete(store.notifications)
              ..where((t) => t.id.equals(tombstone.entityId)))
            .go();
      case 'CrewStatement':
        await (store.delete(store.crewStatements)
              ..where((t) => t.id.equals(tombstone.entityId)))
            .go();
      case 'Attestation':
        await (store.delete(store.attestations)
              ..where((t) => t.id.equals(tombstone.entityId)))
            .go();
      case 'Person':
        // The crew member's own record was deleted. Nothing to show and nothing to sync;
        // treated as a sign-out by the caller when the person table comes back empty.
        await store.delete(store.people).go();
      default:
        // An entity type this build does not know about. Ignoring it is right — a future
        // server may replicate rows this version never stored.
        break;
    }
  }

  Future<void> _replaceStanding(SyncStandingDto? standing) async {
    await store.delete(store.standingCells).go();
    if (standing == null) return;
    for (final cell in standing.evaluation.cells) {
      await store.into(store.standingCells).insertOnConflictUpdate(
            StandingCellsCompanion.insert(
              requirementId: Value(cell.requirementId),
              level: cell.level,
              state: cell.state,
              expiry: Value(cell.expiry),
              notes: Value(cell.notes.isEmpty ? null : cell.notes.join('\n')),
              registerRecordId: Value(cell.registerRecordId),
            ),
          );
    }
  }

  Future<void> _writeState({
    required int cursor,
    required int referenceCursor,
    required String serverToday,
    required SyncStandingDto? standing,
  }) async {
    await store.into(store.syncStates).insertOnConflictUpdate(
          SyncStatesCompanion.insert(
            id: const Value(0),
            cursor: Value(cursor),
            referenceCursor: Value(referenceCursor),
            serverToday: Value(serverToday),
            lastSyncedAt: Value(now()),
            standingCcId: Value(standing?.ccId),
            standingPartnership: Value(standing?.partnershipAbbrev),
            standingFrom: Value(standing?.from),
            standingTo: Value(standing?.to),
            standingCurrent: Value(standing?.current),
            standingRollUp: Value(standing?.evaluation.rollUp),
          ),
        );
  }

  // -------------------------------------------------------------------------
  // Push
  // -------------------------------------------------------------------------

  /// Queues a notification read-mark. Monotonic, so replaying it is harmless (§7.6).
  Future<void> queueReadMark(int notificationId) async {
    final opId = newId();
    await store.into(store.outbox).insertOnConflictUpdate(
          OutboxCompanion.insert(
            opId: opId,
            type: 'notification.read',
            payload: jsonEncode({
              'opId': opId,
              'type': 'notification.read',
              'notificationId': notificationId,
              'readAt': now().toIso8601String(),
            }),
            queuedAt: now(),
          ),
        );

    // Applied locally at once. The screen must tick immediately whether or not there is signal —
    // that is the whole promise of offline-first — and the server will agree when it hears.
    await (store.update(store.notifications)..where((t) => t.id.equals(notificationId)))
        .write(NotificationsCompanion(readAt: Value(now())));
  }

  /// Registers an evidence submission. The bytes upload separately (MOB-5a).
  Future<String> queueSubmission({
    required String publicId,
    required String source,
    String? contentType,
    int? declaredSize,
    String? declaredSha256,
    int? requirementHintId,
    String? localPath,
  }) async {
    final opId = newId();
    await store.transaction(() async {
      await store.into(store.outbox).insertOnConflictUpdate(
            OutboxCompanion.insert(
              opId: opId,
              type: 'evidence.submit',
              payload: jsonEncode({
                'opId': opId,
                'type': 'evidence.submit',
                'submission': {
                  'publicId': publicId,
                  'source': source,
                  'contentType': contentType,
                  'declaredSize': declaredSize,
                  'declaredSha256': declaredSha256,
                  'requirementHintId': requirementHintId,
                },
              }),
              queuedAt: now(),
            ),
          );

      // Visible in the queue immediately, before the server has ever heard of it — MOB-4
      // requires the crew member to see "pending" the moment they hit submit, in a dead spot.
      await store.into(store.submissions).insertOnConflictUpdate(
            SubmissionsCompanion.insert(
              publicId: publicId,
              requirementHintId: Value(requirementHintId),
              source: source,
              contentType: Value(contentType),
              declaredSize: Value(declaredSize),
              verificationStatus: 'pending_extraction',
              submittedAt: now(),
              localPath: Value(localPath),
            ),
          );
    });
    return opId;
  }

  /// Queues one of the crew member's one-tap answers and records it locally so the row can
  /// report itself immediately.
  ///
  /// Two rows, one transaction: the [Outbox] entry that will be posted, and the [CrewIntents] row
  /// the screens read. They share an `opId`, which is also the server's idempotency key, so a
  /// retry after a half-delivered request cannot produce two seat requests for the same course.
  ///
  /// This does **not** write a holding or a compliance state, and never will (§7.5). An intent
  /// says what the crew member told the office; what the office does with it comes back down the
  /// sync payload like every other answer.
  Future<String> queueIntent({
    required String kind,
    required String summary,
    Map<String, Object?> payload = const {},
    int? requirementId,
    String? subjectRef,
  }) async {
    final opId = newId();
    final body = jsonEncode({
      'opId': opId,
      'type': kind,
      'requirementId': requirementId,
      'subjectRef': subjectRef,
      ...payload,
    });

    await store.transaction(() async {
      await store.into(store.outbox).insertOnConflictUpdate(
            OutboxCompanion.insert(opId: opId, type: kind, payload: body, queuedAt: now()),
          );
      await store.into(store.crewIntents).insertOnConflictUpdate(
            CrewIntentsCompanion.insert(
              opId: opId,
              kind: kind,
              requirementId: Value(requirementId),
              subjectRef: Value(subjectRef),
              summary: summary,
              payload: body,
              queuedAt: now(),
            ),
          );
    });
    return opId;
  }

  /// Puts a failed intent back on the queue, under its original `opId`.
  ///
  /// Reusing the id is the whole safety property: the failure may have been a connection dropped
  /// after the server applied the operation, and an idempotency key the server has already seen
  /// makes the second attempt a no-op rather than a duplicate seat request.
  Future<void> retryIntent(String opId) async {
    final intent = await (store.select(store.crewIntents)..where((t) => t.opId.equals(opId)))
        .getSingleOrNull();
    if (intent == null) return;

    await store.transaction(() async {
      await store.into(store.outbox).insertOnConflictUpdate(
            OutboxCompanion.insert(
              opId: opId,
              type: intent.kind,
              payload: intent.payload,
              queuedAt: now(),
              attempts: const Value(0),
              nextAttemptAt: const Value.absent(),
            ),
          );
      await (store.update(store.crewIntents)..where((t) => t.opId.equals(opId))).write(
        const CrewIntentsCompanion(state: Value('queued'), detail: Value(null)),
      );
    });
  }

  /// Drops an intent the crew member has acknowledged the failure of.
  Future<void> discardIntent(String opId) async {
    await store.transaction(() async {
      await (store.delete(store.outbox)..where((t) => t.opId.equals(opId))).go();
      await (store.delete(store.crewIntents)..where((t) => t.opId.equals(opId))).go();
    });
  }

  /// Forgets delivered intents once they are old enough that nothing on screen still refers to
  /// them. Failed and queued ones are never swept — those are the two states a crew member may
  /// still be waiting on an answer about.
  /// Drops the device's record of an answer once it has nothing left to say.
  ///
  /// Two rules, because two things can end an intent's usefulness:
  ///
  ///  * **The office has it.** A matching `crew_statements` row is the server's own copy, it
  ///    survives a reinstall, and it can report a decision the intent never could. Keeping both
  ///    would leave two rows about one tap and a screen having to choose.
  ///  * **It has simply aged.** The other six operation kinds have no server record to arrive
  ///    (`docs/handoff/mobile-crew-app-backend.md` §1), so those age out on [intentRetention] or
  ///    they would accumulate for the life of the install.
  ///
  /// Only `sent` in both cases. A `queued` intent is unsent work and a `failed` one is a retry the
  /// crew member has not dealt with; deleting either would lose something.
  Future<void> pruneSettledIntents() async {
    final confirmed = [
      ...await store.select(store.crewStatements).map((row) => row.opId).get(),
      ...await store.select(store.attestations).map((row) => row.opId).get(),
    ];
    await (store.delete(store.crewIntents)
          ..where((t) => t.state.equals('sent'))
          ..where(
            (t) =>
                t.opId.isIn(confirmed) |
                t.queuedAt.isSmallerThanValue(now().subtract(intentRetention)),
          ))
        .go();
  }

  /// Sends every due queue entry and reconciles the verdicts. Returns how many were applied.
  Future<int> flushOutbox() async {
    final due = await (store.select(store.outbox)
          ..where((t) => t.nextAttemptAt.isSmallerOrEqualValue(now()) | t.nextAttemptAt.isNull())
          ..orderBy([(t) => OrderingTerm(expression: t.queuedAt)])
          ..limit(100))
        .get();
    if (due.isEmpty) return 0;

    final operations = due
        .map((entry) => SyncOperationDto.fromJson(
              jsonDecode(entry.payload) as Map<String, dynamic>,
            ))
        .toList(growable: false);

    final SyncQueueResultDto result;
    try {
      result = await api.queue(operations);
    } on Exception {
      await _backOff(due);
      rethrow;
    }

    var applied = 0;
    for (final verdict in result.results) {
      switch (verdict.status) {
        case 'applied':
          applied += 1;
          await (store.delete(store.outbox)..where((t) => t.opId.equals(verdict.opId))).go();
          await _settleIntent(verdict.opId, 'sent', null);
        case 'rejected':
          // Permanently refused. Dropping it from the queue is the point: a poison entry retried
          // forever is how an offline queue wedges and every good entry behind it stops moving.
          //
          // The *intent* is not dropped with it. If it were, a crew member's tap would disappear
          // without a word and the row would revert on the next snapshot — the one failure mode
          // the one-tap design explicitly rules out.
          await (store.delete(store.outbox)..where((t) => t.opId.equals(verdict.opId))).go();
          await _settleIntent(
            verdict.opId,
            'failed',
            verdict.detail ?? 'The office could not accept this',
          );
        default:
          await _backOffOne(verdict.opId, verdict.detail);
      }
    }
    return applied;
  }

  Future<void> _settleIntent(String opId, String state, String? detail) =>
      (store.update(store.crewIntents)..where((t) => t.opId.equals(opId))).write(
        CrewIntentsCompanion(state: Value(state), detail: Value(detail)),
      );

  Future<void> _backOff(List<OutboxEntry> entries) async {
    for (final entry in entries) {
      await _backOffOne(entry.opId, null, entry.attempts);
    }
  }

  Future<void> _backOffOne(String opId, String? error, [int? knownAttempts]) async {
    final attempts = knownAttempts ??
        (await (store.select(store.outbox)..where((t) => t.opId.equals(opId))).getSingleOrNull())
            ?.attempts ??
        0;
    final next = attempts + 1;

    if (next >= maxAttempts) {
      // §7.6: "explicit user-visible failure after exhaustion". The entry stays, with its error,
      // so the app can say so rather than dropping the crew member's work silently.
      await (store.update(store.outbox)..where((t) => t.opId.equals(opId))).write(
        OutboxCompanion(
          attempts: Value(next),
          lastError: Value(error ?? 'Gave up after $next attempts'),
          nextAttemptAt: Value(null),
        ),
      );
      // Same reason as a rejection: the crew member must be told their tap did not land. The
      // outbox entry stays so it can be retried by hand; the intent carries the message.
      await _settleIntent(opId, 'failed', error ?? 'Could not reach the office after $next tries');
      return;
    }

    await (store.update(store.outbox)..where((t) => t.opId.equals(opId))).write(
      OutboxCompanion(
        attempts: Value(next),
        lastError: Value(error),
        nextAttemptAt: Value(now().add(backOffDelay(next))),
      ),
    );
  }

  /// Exponential back-off, capped. Maritime connectivity comes in windows, so the cap is minutes
  /// rather than hours — waiting out a window is worse than a few wasted requests.
  static Duration backOffDelay(int attempt) {
    final seconds = min(300, 2 << min(attempt, 8));
    return Duration(seconds: seconds);
  }

  // -------------------------------------------------------------------------
  // State
  // -------------------------------------------------------------------------

  Future<LocalSyncState> currentState() async {
    final existing = await (store.select(store.syncStates)..where((t) => t.id.equals(0)))
        .getSingleOrNull();
    if (existing != null) return existing;

    await store.into(store.syncStates).insertOnConflictUpdate(
          SyncStatesCompanion.insert(id: const Value(0)),
        );
    return (store.select(store.syncStates)..where((t) => t.id.equals(0))).getSingle();
  }

  /// SEC-12: cached data locks once it is this stale, and the app must re-authenticate.
  ///
  /// The check uses wall-clock elapsed time rather than a business date, because it is about
  /// how long a revoked token could keep working, not about a compliance question.
  bool isOfflineExpired(LocalSyncState state) {
    final last = state.lastSyncedAt;
    if (last == null) return false;
    return now().difference(last) > offlineValidity;
  }

  /// A fresh v4-shaped identifier: outbox idempotency keys, and the `publicId` a submission
  /// carries from the moment the device mints it (§7.6).
  static String newId() {
    // A v4-shaped identifier. Uniqueness is what matters — it is an idempotency key, not a
    // security token — and this avoids a dependency for sixteen bytes of randomness.
    final random = Random.secure();
    String hex(int count) => List.generate(
          count,
          (_) => random.nextInt(256).toRadixString(16).padLeft(2, '0'),
        ).join();
    return '${hex(4)}-${hex(2)}-4${hex(2).substring(1)}-a${hex(2).substring(1)}-${hex(6)}';
  }

  // -------------------------------------------------------------------------
  // Row mapping
  // -------------------------------------------------------------------------

  Future<void> _upsertPerson(PersonDto person) =>
      store.into(store.people).insertOnConflictUpdate(
            PeopleCompanion.insert(
              id: Value(person.id),
              sam: person.sam,
              name: person.name,
              positionId: person.positionId,
              positionName: person.positionName,
              tier: Value(person.tier),
              partnershipAbbrev: person.partnershipAbbrev,
              status: person.status,
              email: Value(person.email),
            ),
          );

  Future<void> _upsertHolding(HoldingDto holding) =>
      store.into(store.holdings).insertOnConflictUpdate(
            HoldingsCompanion.insert(
              id: Value(holding.id),
              requirementId: holding.requirementId,
              status: holding.status,
              expiry: Value(holding.expiry),
              issueDate: Value(holding.issueDate),
              note: Value(holding.note),
            ),
          );

  Future<void> _upsertAssignment(AssignmentDto assignment) =>
      store.into(store.assignments).insertOnConflictUpdate(
            AssignmentsCompanion.insert(
              id: Value(assignment.id),
              crewChangeId: assignment.crewChangeId,
              ccId: assignment.ccId,
              partnershipAbbrev: assignment.partnershipAbbrev,
              slotRef: assignment.slotRef,
              fromDate: assignment.from,
              toDate: assignment.to,
            ),
          );

  Future<void> _upsertLeave(LeaveRecordDto leave) =>
      store.into(store.leaveRecords).insertOnConflictUpdate(
            LeaveRecordsCompanion.insert(
              id: Value(leave.id),
              kind: leave.kind,
              fromDate: leave.from,
              toDate: leave.to,
              status: leave.status,
            ),
          );

  Future<void> _upsertNotification(NotificationDto notification) =>
      store.into(store.notifications).insertOnConflictUpdate(
            NotificationsCompanion.insert(
              id: Value(notification.id),
              kind: notification.kind,
              title: notification.title,
              body: Value(notification.body),
              deepLink: Value(notification.deepLink),
              createdAt: notification.createdAt,
              readAt: Value(notification.readAt),
            ),
          );

  Future<void> _upsertSubmission(EvidenceSubmissionDto submission) =>
      store.into(store.submissions).insertOnConflictUpdate(
            SubmissionsCompanion.insert(
              publicId: submission.publicId,
              requirementHintId: Value(submission.requirementHintId),
              source: submission.source,
              contentType: Value(submission.contentType),
              declaredSize: Value(submission.declaredSize),
              uploadOffset: Value(submission.uploadOffset),
              uploadComplete: Value(submission.uploadComplete),
              verificationStatus: submission.verificationStatus,
              rejectionReason: Value(submission.rejectionReason),
              submittedAt: submission.submittedAt,
            ),
          );

  /// Stores the office's copy of a one-tap answer, and settles the local intent it belongs to.
  ///
  /// The settle is the point of doing these together. Once the server has the statement, the
  /// device's own `crew_intents` row has nothing left to say — it exists to report *queued* and
  /// *failed*, states only the device knows — and leaving it at `queued` beside an arrived
  /// statement would show a crew member "sends when you have signal" for something the office is
  /// already looking at. That happens on a real device more often than it sounds: the outbox
  /// pushes, the connection drops before the verdict is read, and the answer is only ever
  /// confirmed by the next pull.
  Future<void> _upsertCrewStatement(CrewStatementSyncDto statement) async {
    await store.into(store.crewStatements).insertOnConflictUpdate(
          CrewStatementsCompanion.insert(
            id: Value(statement.id),
            opId: statement.opId,
            kind: statement.kind,
            requirementId: statement.requirementId,
            status: statement.status,
            aboutExpiry: Value(statement.aboutExpiry),
            raisedAt: statement.raisedAt,
            decisionNote: Value(statement.decisionNote),
            decidedAt: Value(statement.decidedAt),
          ),
        );
    await _settleIntent(statement.opId, 'sent', null);
  }

  /// Stores a signed declaration, and settles the intent that sent it.
  ///
  /// The same pairing as a crew statement, and for the same reason: once the server holds the
  /// record, the device's own copy has nothing left to say and leaving it at `queued` beside an
  /// arrived attestation would tell a crew member their signature was still waiting for signal.
  Future<void> _upsertAttestation(AttestationSyncDto attestation) async {
    await store.into(store.attestations).insertOnConflictUpdate(
          AttestationsCompanion.insert(
            id: Value(attestation.id),
            opId: attestation.opId,
            assignmentId: attestation.assignmentId,
            ccId: attestation.ccId,
            declarations: attestation.declarations.join('\n'),
            signedAt: attestation.signedAt,
            signedAtDisplay: attestation.signedAtDisplay,
          ),
        );
    await _settleIntent(attestation.opId, 'sent', null);
  }
}

class SyncOutcome {
  const SyncOutcome({
    required this.succeeded,
    this.operationsPushed = 0,
    this.uploadsCompleted = 0,
    this.resnapshotted = false,
    this.error,
    this.unauthenticated = false,
  });

  final bool succeeded;
  final int operationsPushed;

  /// Evidence submissions whose bytes finished uploading during this cycle (MOB-5a).
  final int uploadsCompleted;

  final bool resnapshotted;
  final String? error;
  final bool unauthenticated;
}
