import 'dart:async';

import 'package:drift/drift.dart';
import 'package:flutter/foundation.dart';

import '../data/local_store.dart';
import '../data/sync_engine.dart';

/// What the screens read from, and the only thing that talks to the sync engine.
///
/// Offline-first has one structural consequence and this class is it: **every screen reads the
/// local store, never the network.** A widget that awaited an HTTP call would be blank on a
/// vessel, which is precisely where this app is used. Syncing is a background activity whose
/// only visible effect is that the local data changes and the "last synced" line moves.
class AppState extends ChangeNotifier {
  AppState({required this.store, required this.engine});

  final LocalStore store;
  final SyncEngine engine;

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

  // --- Queries the screens use. Streams, so an applied delta redraws by itself. ---

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
}
