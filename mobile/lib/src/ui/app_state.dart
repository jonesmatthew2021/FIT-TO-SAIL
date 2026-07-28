import 'dart:async';

import 'package:drift/drift.dart';
import 'package:flutter/foundation.dart';

import '../data/evidence_capture.dart';
import '../data/local_store.dart';
import '../data/sync_engine.dart';
import '../domain/intents.dart';
import '../domain/offers.dart';
import '../domain/states.dart';

/// What the screens read from, and the only thing that talks to the sync engine.
///
/// Offline-first has one structural consequence and this class is it: **every screen reads the
/// local store, never the network.** A widget that awaited an HTTP call would be blank on a
/// vessel, which is precisely where this app is used. Syncing is a background activity whose
/// only visible effect is that the local data changes and the "last synced" line moves.
class AppState extends ChangeNotifier {
  AppState({
    required this.store,
    required this.engine,
    EvidenceCapture? capture,
    this.supervisor = false,
  }) : capture = capture ?? PlatformEvidenceCapture();

  final LocalStore store;
  final SyncEngine engine;

  /// Whether this person supervises a watch, which swaps MOB-11 Team into the tab bar in place
  /// of Certifications.
  ///
  /// A role, and roles are the identity spike's. Nothing in the sync payload says who supervises
  /// whom, so today this is set only from a debug-mode `--dart-define` and is `false` in any
  /// release build — the same compile-time pattern as the development sign-in shim. When ADR
  /// 0003 lands it comes from the session, and when the team endpoint lands it comes with data.
  final bool supervisor;

  /// MOB-4's camera and file pickers, behind an interface so the widget tests never touch a
  /// platform channel.
  final EvidenceCapture capture;

  bool _syncing = false;
  String? _lastError;
  LocalSyncState? _syncState;
  LocalPerson? _person;

  bool get syncing => _syncing;
  String? get lastError => _lastError;
  LocalSyncState? get syncState => _syncState;
  LocalPerson? get person => _person;

  DateTime? get lastSyncedAt => _syncState?.lastSyncedAt;

  /// The server's business date. Null before the first successful sync — and genuinely unknown
  /// until then, which is why nothing falls back to the device clock (NFR-5, O-11).
  String? get serverToday => _syncState?.serverToday;

  /// SEC-12: cached data locks after the offline-validity window.
  bool get locked {
    final state = _syncState;
    return state != null && engine.isOfflineExpired(state);
  }

  Future<void> load() async {
    _syncState = await engine.currentState();
    _person = await store.select(store.people).getSingleOrNull();
    notifyListeners();
  }

  Future<void> sync() async {
    if (_syncing) return;
    _syncing = true;
    _lastError = null;
    notifyListeners();

    final outcome = await engine.sync();
    _lastError = outcome.succeeded ? null : outcome.error;
    _syncing = false;
    await load();
  }

  Future<void> markRead(int notificationId) async {
    await engine.queueReadMark(notificationId);
    notifyListeners();
    // Best-effort push. Failing here is normal and not worth surfacing: the entry is durable
    // and the next sync will carry it.
    unawaited(engine.flushOutbox().catchError((_) => 0));
  }

  /// Records one of the crew member's one-tap answers and starts trying to deliver it.
  ///
  /// Returns the moment the answer is durable, not when it lands. That ordering is the whole
  /// promise: the row ticks in a dead spot, the queue carries it out, and if the office refuses
  /// it the row says so rather than quietly unticking itself on the next snapshot.
  Future<void> answer({
    required String kind,
    required String summary,
    int? requirementId,
    String? subjectRef,
    Map<String, Object?> payload = const {},
  }) async {
    await engine.queueIntent(
      kind: kind,
      summary: summary,
      requirementId: requirementId,
      subjectRef: subjectRef,
      payload: payload,
    );
    notifyListeners();
    // Best-effort, exactly as a read-mark and a submission are. Failing here is the ordinary
    // offline case, and the queue already holds everything needed to try again.
    unawaited(engine.flushOutbox().catchError((_) => 0));
  }

  Future<void> retryAnswer(String opId) async {
    await engine.retryIntent(opId);
    notifyListeners();
    unawaited(engine.flushOutbox().catchError((_) => 0));
  }

  Future<void> discardAnswer(String opId) async {
    await engine.discardIntent(opId);
    notifyListeners();
  }

  // -------------------------------------------------------------------------
  // Facts the sync payload does not carry yet
  // -------------------------------------------------------------------------
  //
  // Each of these is a real server-owned fact the new screens are built against, and each
  // returns nothing until the payload carries it. They are methods on [AppState] rather than
  // constants inside the screens so that landing the backend work is a change in one file — see
  // `docs/handoff/mobile-crew-app-backend.md`.

  /// MOB-0's credit tiles. Absent until the payload carries the history they summarise; Home
  /// draws no tiles rather than drawing a number nobody computed.
  Credits? get credits => null;

  /// MOB-8's course dates that beat an expiry. Empty until there is a course catalogue.
  List<CourseOption> courseOptionsFor(int requirementId) => const [];

  /// MOB-7's parse result. Null with no LLM provider configured (§14.5), which is exactly what
  /// the pipeline reports today: nothing extracted, and a human to type the fields (LLM-2).
  ExtractedReading? readingFor(String submissionPublicId) => null;

  /// MOB-11's watch. Empty until there is a team endpoint — and it must stay a status-only
  /// endpoint: no documents, no medical detail, no expiry reasons.
  List<TeamMember> get team => const [];

  /// Evidence submission (the spec's MOB-4): capture a document and queue it for the §8 pipeline.
  ///
  /// Returns what happened. A cancelled picker is neither a success nor a failure and reports
  /// both fields null; a rejected file reports a message; a queued submission reports its id so
  /// the caller can open MOB-7 against it.
  ///
  /// Note what this method does *not* do. It does not upload, and it does not wait for one: the
  /// row and its outbox entry are durable the moment this returns, so the screen can say
  /// "Processing" in a dead spot and mean it. The bytes leave on the next sync, which is kicked
  /// off here only as a courtesy.
  Future<EvidenceSubmitResult> submitEvidence({
    required CaptureSource source,
    int? requirementId,
  }) async {
    final CapturedEvidence captured;
    try {
      captured = await capture.capture(source);
    } on CaptureCancelled {
      return const EvidenceSubmitResult();
    } on CaptureRejected catch (e) {
      return EvidenceSubmitResult(message: e.message);
    } on Exception catch (e) {
      return EvidenceSubmitResult(message: 'That document could not be read: $e');
    }

    final publicId = SyncEngine.newId();
    await engine.queueSubmission(
      publicId: publicId,
      source: captured.source.wire,
      contentType: captured.contentType,
      declaredSize: captured.size,
      declaredSha256: captured.sha256,
      requirementHintId: requirementId,
      localPath: captured.path,
    );
    notifyListeners();

    // Best-effort, exactly as a read-mark is. Failing is the ordinary offline case and the queue
    // already holds everything needed to try again.
    unawaited(sync());
    return EvidenceSubmitResult(
      publicId: publicId,
      fileName: captured.path.split('/').last,
      size: captured.size,
      contentType: captured.contentType,
    );
  }

  // --- Queries the screens use. Streams, so an applied delta redraws by itself. ---

  /// Every one-tap answer still worth showing, newest first.
  Stream<List<LocalCrewIntent>> watchIntents() => (store.select(store.crewIntents)
        ..orderBy([(t) => OrderingTerm(expression: t.queuedAt, mode: OrderingMode.desc)]))
      .watch();

  /// The answers raised against one requirement.
  Stream<List<LocalCrewIntent>> watchIntentsFor(int requirementId) =>
      (store.select(store.crewIntents)
            ..where((t) => t.requirementId.equals(requirementId))
            ..orderBy([(t) => OrderingTerm(expression: t.queuedAt, mode: OrderingMode.desc)]))
          .watch();

  /// The office's copy of this crew member's answers, with whatever it decided (ADM-11).
  ///
  /// Server-owned and therefore durable: this is the half that survives a reinstall and the only
  /// half that can carry a coordinator's decision.
  Stream<List<LocalCrewStatement>> watchStatements() => (store.select(store.crewStatements)
        ..orderBy([(t) => OrderingTerm(expression: t.raisedAt, mode: OrderingMode.desc)]))
      .watch();

  Stream<List<LocalCrewStatement>> watchStatementsFor(int requirementId) =>
      (store.select(store.crewStatements)
            ..where((t) => t.requirementId.equals(requirementId))
            ..orderBy([(t) => OrderingTerm(expression: t.raisedAt, mode: OrderingMode.desc)]))
          .watch();

  Stream<List<LocalNotification>> watchNotifications() =>
      (store.select(store.notifications)
            ..orderBy([
              (t) => OrderingTerm(expression: t.createdAt, mode: OrderingMode.desc),
            ]))
          .watch();

  Stream<int> watchUnreadCount() => (store.selectOnly(store.notifications)
        ..addColumns([store.notifications.id.count()])
        ..where(store.notifications.readAt.isNull()))
      .map((row) => row.read(store.notifications.id.count()) ?? 0)
      .watchSingle();

  Stream<List<LocalAssignment>> watchAssignments() => (store.select(store.assignments)
        ..orderBy([(t) => OrderingTerm(expression: t.fromDate, mode: OrderingMode.desc)]))
      .watch();

  Stream<List<LocalLeave>> watchLeave() => (store.select(store.leaveRecords)
        ..orderBy([(t) => OrderingTerm(expression: t.fromDate, mode: OrderingMode.desc)]))
      .watch();

  Stream<List<LocalSubmission>> watchSubmissions() => (store.select(store.submissions)
        ..orderBy([(t) => OrderingTerm(expression: t.submittedAt, mode: OrderingMode.desc)]))
      .watch();

  /// The submissions the crew member raised against one requirement, newest first.
  ///
  /// Hint-matched, not verdict-matched: `requirement_hint_id` is what the *device* said the
  /// document was for, and §8 stage 3 may well decide otherwise. Showing the hint is honest
  /// about that — this is "what I sent about this", not "what the system accepted".
  Stream<List<LocalSubmission>> watchSubmissionsFor(int requirementId) =>
      (store.select(store.submissions)
            ..where((t) => t.requirementHintId.equals(requirementId))
            ..orderBy([(t) => OrderingTerm(expression: t.submittedAt, mode: OrderingMode.desc)]))
          .watch();

  /// The crew change behind an assignment — where the §5.1 cutoff date lives.
  Stream<LocalCrewChange?> watchCrewChange(int crewChangeId) =>
      (store.select(store.crewChanges)..where((t) => t.id.equals(crewChangeId)))
          .watchSingleOrNull();

  /// One certification, streamed, so the detail screen redraws when a delta lands on it.
  Stream<CertificationRow?> watchCertification(int requirementId) =>
      watchCertifications().map(
        (rows) => rows
            .where((row) => row.cell.requirementId == requirementId)
            .firstOrNull,
      );

  /// The certifications view: the server's evaluated cells joined to the requirement catalogue
  /// and to whatever the crew member actually holds.
  ///
  /// The join runs left-from the **standing**, not from the holdings, and that is the difference
  /// between "what I have" and MOB-1's "what I need": a requirement the crew member holds
  /// nothing for still has a cell, and it is the one that matters most.
  Stream<List<CertificationRow>> watchCertifications() {
    final query = store.select(store.standingCells).join([
      leftOuterJoin(
        store.requirements,
        store.requirements.id.equalsExp(store.standingCells.requirementId),
      ),
      leftOuterJoin(
        store.holdings,
        store.holdings.requirementId.equalsExp(store.standingCells.requirementId),
      ),
    ]);

    return query.watch().map(
          (rows) => rows
              .map(
                (row) => CertificationRow(
                  cell: row.readTable(store.standingCells),
                  requirement: row.readTableOrNull(store.requirements),
                  holding: row.readTableOrNull(store.holdings),
                ),
              )
              .toList(growable: false),
        );
  }
}

/// What came of a capture. Three outcomes and no exception: cancelled (everything null), refused
/// ([message] set), or queued ([publicId] set).
class EvidenceSubmitResult {
  const EvidenceSubmitResult({
    this.publicId,
    this.message,
    this.fileName,
    this.size,
    this.contentType,
  });

  final String? publicId;
  final String? message;
  final String? fileName;
  final int? size;
  final String? contentType;

  bool get queued => publicId != null;
}

/// MOB-0's ring, counted from the server's own answers.
///
/// This summarises evaluations; it does not produce one. Every cell arrived with a state the
/// engine decided (§5.2), and all that happens here is a partition on the same `needsAttention`
/// grouping the certifications list has always used — which is why the ring and the list can
/// never disagree about how many things are outstanding.
///
/// `na` cells are excluded from both halves. A requirement that does not apply to this person is
/// not something they are ready for; counting it would inflate the ring with rows the screen
/// never shows.
Readiness readinessFrom(List<CertificationRow> rows) {
  final applicable = rows.where((row) => row.cell.state != 'na').toList(growable: false);
  final outstanding = applicable.where((row) => needsAttention(row.cell.state)).length;
  return Readiness(ready: applicable.length - outstanding, total: applicable.length);
}

/// Merges the two halves of the one-tap record into what a screen actually renders.
///
/// The device's outbox record and the server's statement are joined on `opId` — the device's own
/// queue-entry id, echoed back in the sync payload, which is why no second identifier had to be
/// invented for this.
///
/// **The server's row wins wherever both exist**, and that is the whole rule: it is the only one of
/// the two that can carry a coordinator's decision, and the device's copy has by then said
/// everything it knows. (It rarely comes to that — `pruneSettledIntents` deletes a `sent` intent as
/// soon as its statement lands — but the two tables are written by different code paths and a merge
/// that assumed they were disjoint would show a stale "Sent to the office" over a decision.)
///
/// Ordering is preserved from the caller's lists, both of which arrive newest-first.
List<Answer> answersFrom(
  List<LocalCrewIntent> intents,
  List<LocalCrewStatement> statements,
) {
  final confirmed = {for (final statement in statements) statement.opId};
  return [
    for (final statement in statements)
      Answer(
        opId: statement.opId,
        kind: statement.kind,
        state: switch (statement.status) {
          'actioned' => AnswerState.actioned,
          'dismissed' => AnswerState.dismissed,
          // `open`, and anything a newer server invents. Treated as "the office has it" rather
          // than rendered verbatim: unlike a cell state, an unknown value here is a workflow step
          // this build does not know about, and "sent" is true of every one of them.
          _ => AnswerState.sent,
        },
        summary: intents
                .where((intent) => intent.opId == statement.opId)
                .map((intent) => intent.summary)
                .firstOrNull ??
            answerSummary(statement.kind),
        requirementId: statement.requirementId,
        detail: statement.decisionNote,
      ),
    for (final intent in intents)
      if (!confirmed.contains(intent.opId)) answerFromIntent(intent),
  ];
}

/// One answer with only the device's half of its record.
///
/// The right shape for the five operations that will never get a server statement back — a course
/// seat request, a nudge — and the fallback for a statement that has not arrived yet.
Answer answerFromIntent(LocalCrewIntent intent) => Answer(
      opId: intent.opId,
      kind: intent.kind,
      state: switch (intent.state) {
        'sent' => AnswerState.sent,
        'failed' => AnswerState.failed,
        _ => AnswerState.queued,
      },
      summary: intent.summary,
      requirementId: intent.requirementId,
      detail: intent.detail,
    );

/// One line of the certifications screen.
class CertificationRow {
  const CertificationRow({required this.cell, this.requirement, this.holding});

  final LocalStandingCell cell;

  /// Null when the catalogue has not caught up with a rule referencing a new requirement.
  final LocalRequirement? requirement;

  /// Null when the crew member holds nothing at all against it — the `unknown`/`gap` case.
  final LocalHolding? holding;

  String get code => requirement?.code ?? 'REQ-${cell.requirementId}';
  String get title => requirement?.title ?? 'Requirement ${cell.requirementId}';
  String get category => requirement?.category ?? '—';

  /// The date this stops being valid, preferring the evaluated cell's over the raw holding's.
  ///
  /// The cell's is the one the engine reasoned about — a footnote or a register overlay can make
  /// the effective date differ from what the certificate on the person's desk says — so where the
  /// two disagree the server's answer wins.
  String? get expiry => cell.expiry ?? holding?.expiry;

  /// Whether this row is one of the things asked of the crew member — the server's state, read
  /// through the same grouping every screen uses.
  bool get outstanding => needsAttention(cell.state);
}
