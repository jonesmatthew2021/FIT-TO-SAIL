import 'dart:convert';

import 'package:crewcomp_crew/src/api/crewcomp_api.dart';
import 'package:crewcomp_crew/src/data/database_opener.dart';
import 'package:crewcomp_crew/src/data/local_store.dart';
import 'package:crewcomp_crew/src/data/sync_engine.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:drift/drift.dart' as drift;
import 'package:http/testing.dart';

/// MOB-5 replication behaviour, against a stubbed server speaking the real §10.3 JSON.
///
/// The payloads below are the shapes `SyncIT` asserts on the backend side, so the two suites
/// meet at the wire format rather than at a shared mock.
void main() {
  late LocalStore store;

  /// Set by the decision test before its delta is served — the delta has to echo the very opId
  /// the device minted, which is the whole join between the two halves of the record.
  var storedOpId = '';

  setUp(() => store = LocalStore(InMemoryOpener().open()));
  tearDown(() => store.close());

  SyncEngine engineFor(MockClient client, {DateTime Function()? now}) => SyncEngine(
        api: CrewcompApi(baseUrl: Uri.parse('http://localhost'), client: client),
        store: store,
        now: now ?? () => DateTime.utc(2026, 7, 26, 8),
      );

  Map<String, dynamic> snapshotBody({
    int cursor = 100,
    int referenceCursor = 50,
    List<Map<String, dynamic>> holdings = const [],
    List<Map<String, dynamic>> notifications = const [],
    Map<String, dynamic>? standing,
  }) =>
      {
        'cursor': cursor,
        'referenceCursor': referenceCursor,
        'serverToday': '2026-07-26',
        'person': {
          'id': 7,
          'sam': 'SAM007',
          'name': 'Bruno Oyelaran',
          'positionId': 2,
          'positionName': 'Chief Officer',
          'tier': 'senior',
          'partnershipId': 1,
          'partnershipAbbrev': 'UNI',
          'status': 'active',
          'email': null,
        },
        'holdings': holdings,
        'assignments': [
          {
            'id': 900,
            'personId': 7,
            'crewChangeId': 5,
            'ccId': 'CC24',
            'partnershipAbbrev': 'UNI',
            'slotRef': 2,
            'from': '2026-07-20',
            'to': '2026-08-16',
          },
        ],
        'leave': [],
        'notifications': notifications,
        'submissions': [],
        'crewStatements': [],
        'reference': {
          'cursor': referenceCursor,
          'matrixVersionId': 3,
          'matrixVersionLabel': 'dev-2026.1',
          'requirements': [
            {
              'id': 11,
              'code': 'MS-02',
              'category': 'MS',
              'title': 'Sea Survival',
              'status': 'active',
              'issuingAuthority': null,
            },
          ],
          'positions': [],
          'partnerships': [],
          'crewChanges': [],
        },
        'standing': standing,
      };

  Map<String, dynamic> holding(int id, int requirementId, String status, {String? expiry}) => {
        'id': id,
        'personId': 7,
        'requirementId': requirementId,
        'status': status,
        'expiry': expiry,
        'issueDate': null,
        'note': null,
      };

  Map<String, dynamic> standingBody(String rollUp, List<Map<String, dynamic>> cells) => {
        'ccId': 'CC24',
        'partnershipAbbrev': 'UNI',
        'from': '2026-07-20',
        'to': '2026-08-16',
        'current': true,
        'evaluation': {'personId': 7, 'rollUp': rollUp, 'cells': cells},
      };

  Map<String, dynamic> cell(int requirementId, String state, {String? expiry}) => {
        'requirementId': requirementId,
        'level': 'M',
        'state': state,
        'expiry': expiry,
        'notes': <String>[],
        'registerRecordId': null,
      };

  group('pull', () {
    test('a first sync takes a snapshot and records the cursor', () async {
      final engine = engineFor(MockClient((request) async {
        expect(request.url.path, '/api/v1/sync/snapshot');
        return http.Response(
          jsonEncode(snapshotBody(holdings: [holding(1, 11, 'held_perpetual')])),
          200,
          headers: {'content-type': 'application/json'},
        );
      }));

      final outcome = await engine.sync();

      expect(outcome.succeeded, isTrue);
      expect(outcome.resnapshotted, isTrue);

      final state = await engine.currentState();
      expect(state.cursor, 100);
      expect(state.serverToday, '2026-07-26');
      expect(await store.select(store.holdings).get(), hasLength(1));
      expect((await store.select(store.people).getSingle()).name, 'Bruno Oyelaran');
    });

    test('a second sync asks for a delta from the stored cursor', () async {
      var deltaRequested = false;
      final engine = engineFor(MockClient((request) async {
        if (request.url.path.endsWith('/snapshot')) {
          return http.Response(jsonEncode(snapshotBody()), 200);
        }
        deltaRequested = true;
        expect(request.url.queryParameters['cursor'], '100');
        return http.Response(
          jsonEncode({
            'cursor': 140,
            'referenceCursor': 50,
            'referenceStale': false,
            'serverToday': '2026-07-27',
            'person': null,
            'holdings': [holding(2, 11, 'held_expiry', expiry: '2026-09-01')],
            'assignments': [],
            'leave': [],
            'notifications': [],
            'submissions': [],
            'crewStatements': [],
            'tombstones': [],
            'standing': null,
          }),
          200,
        );
      }));

      await engine.sync();
      await engine.sync();

      expect(deltaRequested, isTrue);
      final state = await engine.currentState();
      expect(state.cursor, 140);
      expect(state.serverToday, '2026-07-27');
      expect(await store.select(store.holdings).get(), hasLength(1));
    });

    test('a tombstone removes the row', () async {
      final engine = engineFor(MockClient((request) async {
        if (request.url.path.endsWith('/snapshot')) {
          return http.Response(
            jsonEncode(snapshotBody(holdings: [holding(1, 11, 'held_perpetual')])),
            200,
          );
        }
        return http.Response(
          jsonEncode({
            'cursor': 150,
            'referenceCursor': 50,
            'referenceStale': false,
            'serverToday': '2026-07-26',
            'person': null,
            'holdings': [],
            'assignments': [],
            'leave': [],
            'notifications': [],
            'submissions': [],
            'crewStatements': [],
            'tombstones': [
              {'entityType': 'QualificationHolding', 'entityId': 1, 'seq': 145},
            ],
            'standing': null,
          }),
          200,
        );
      }));

      await engine.sync();
      expect(await store.select(store.holdings).get(), hasLength(1));

      await engine.sync();
      expect(await store.select(store.holdings).get(), isEmpty);
    });

    test('a stale reference cursor triggers a re-snapshot', () async {
      // A matrix publication changes most of the cached rules at once. Applying half of one
      // would render a view assembled from two matrix versions.
      var snapshots = 0;
      final engine = engineFor(MockClient((request) async {
        if (request.url.path.endsWith('/snapshot')) {
          snapshots += 1;
          return http.Response(jsonEncode(snapshotBody(cursor: 100 + snapshots)), 200);
        }
        return http.Response(
          jsonEncode({
            'cursor': 200,
            'referenceCursor': 199,
            'referenceStale': true,
            'serverToday': '2026-07-26',
            'person': null,
            'holdings': [],
            'assignments': [],
            'leave': [],
            'notifications': [],
            'submissions': [],
            'crewStatements': [],
            'tombstones': [],
            'standing': null,
          }),
          200,
        );
      }));

      await engine.sync();
      expect(snapshots, 1);

      final outcome = await engine.sync();
      expect(snapshots, 2);
      expect(outcome.resnapshotted, isTrue);
    });

    test('the standing is replaced wholesale on every sync, not merged', () async {
      // A roll-up can change with no row change at all — a holding expires because the date
      // moved. Merging cells would leave a stale "ok" beside a new "expiring".
      var call = 0;
      final engine = engineFor(MockClient((request) async {
        if (request.url.path.endsWith('/snapshot')) {
          return http.Response(
            jsonEncode(snapshotBody(
              standing: standingBody('ok', [cell(11, 'ok'), cell(12, 'ok')]),
            )),
            200,
          );
        }
        call += 1;
        return http.Response(
          jsonEncode({
            'cursor': 200 + call,
            'referenceCursor': 50,
            'referenceStale': false,
            'serverToday': '2026-07-26',
            'person': null,
            'holdings': [],
            'assignments': [],
            'leave': [],
            'notifications': [],
            'submissions': [],
            'crewStatements': [],
            'tombstones': [],
            'standing': standingBody('expiring', [cell(11, 'expiring', expiry: '2026-08-01')]),
          }),
          200,
        );
      }));

      await engine.sync();
      expect(await store.select(store.standingCells).get(), hasLength(2));

      await engine.sync();
      final cells = await store.select(store.standingCells).get();
      expect(cells, hasLength(1));
      expect(cells.single.state, 'expiring');
      expect((await engine.currentState()).standingRollUp, 'expiring');
    });
  });

  group('outbound queue', () {
    test('a read-mark ticks locally before the server has heard of it', () async {
      final engine = engineFor(MockClient((request) async => http.Response('{}', 500)));

      await store.into(store.notifications).insert(
            NotificationsCompanion.insert(
              id: const drift.Value(3),
              kind: 'expiry_warning',
              title: 'A qualification is expiring soon',
              createdAt: DateTime.utc(2026, 7, 20),
            ),
          );

      await engine.queueReadMark(3);

      // Offline-first: the tick is immediate, and the queue carries the truth to the server
      // whenever there is signal.
      final notification = await (store.select(store.notifications)
            ..where((t) => t.id.equals(3)))
          .getSingle();
      expect(notification.readAt, isNotNull);
      expect(await store.select(store.outbox).get(), hasLength(1));
    });

    test('applied entries are removed and rejected entries are dropped', () async {
      late List<dynamic> sentOperations;
      final engine = engineFor(MockClient((request) async {
        final body = jsonDecode(request.body) as Map<String, dynamic>;
        sentOperations = body['operations'] as List<dynamic>;
        return http.Response(
          jsonEncode({
            'cursor': 10,
            'results': [
              {'opId': sentOperations[0]['opId'], 'status': 'applied', 'detail': null},
              {
                'opId': sentOperations[1]['opId'],
                'status': 'rejected',
                'detail': 'No such notification',
              },
            ],
          }),
          200,
        );
      }));

      await engine.queueReadMark(1);
      await engine.queueReadMark(2);
      expect(await store.select(store.outbox).get(), hasLength(2));

      final applied = await engine.flushOutbox();

      expect(applied, 1);
      // Both gone: one because it worked, one because it never will. A rejected entry retried
      // forever is how an offline queue wedges.
      expect(await store.select(store.outbox).get(), isEmpty);
    });

    test('a transport failure backs the entry off rather than losing it', () async {
      final engine = engineFor(MockClient((request) async => http.Response('nope', 503)));
      await engine.queueReadMark(1);

      await expectLater(engine.flushOutbox(), throwsA(isA<ApiException>()));

      final entry = await store.select(store.outbox).getSingle();
      expect(entry.attempts, 1);
      expect(entry.nextAttemptAt, isNotNull);
    });

    test('an entry that exhausts its attempts stays, with its error, for the UI to show', () async {
      // §7.6 requires "explicit user-visible failure after exhaustion" — dropping the crew
      // member's work silently is the one outcome that is not allowed.
      final engine = engineFor(MockClient((request) async => http.Response('nope', 503)));
      await engine.queueReadMark(1);

      for (var i = 0; i < SyncEngine.maxAttempts + 1; i++) {
        try {
          await engine.flushOutbox();
        } on ApiException {
          // expected
        }
        await (store.update(store.outbox)).write(
          const OutboxCompanion(nextAttemptAt: drift.Value(null)),
        );
      }

      final entry = await store.select(store.outbox).getSingleOrNull();
      expect(entry, isNotNull);
      expect(entry!.lastError, isNotNull);
      expect(entry.nextAttemptAt, isNull, reason: 'exhausted entries stop being retried');
    });

    test('a submission is visible in the queue before the server has it', () async {
      final engine = engineFor(MockClient((request) async => http.Response('{}', 500)));

      await engine.queueSubmission(
        publicId: '11111111-2222-4333-a444-555555555555',
        source: 'mobile_camera',
        contentType: 'application/pdf',
        declaredSize: 2048,
      );

      // MOB-4: "pending" the moment they hit submit, in a dead spot.
      final submission = await store.select(store.submissions).getSingle();
      expect(submission.verificationStatus, 'pending_extraction');
      expect(await store.select(store.outbox).get(), hasLength(1));
    });
  });

  group('one-tap answers', () {
    test('a queued answer is durable and visible before the server has heard of it', () async {
      final engine = engineFor(MockClient((request) async => http.Response('{}', 500)));

      await engine.queueIntent(
        kind: 'requirement.progress',
        summary: 'Course booked for Sea Survival',
        requirementId: 11,
      );

      final intent = await store.select(store.crewIntents).getSingle();
      expect(intent.state, 'queued');
      expect(intent.requirementId, 11);
      // One id across both rows, and it is the server's idempotency key.
      expect((await store.select(store.outbox).getSingle()).opId, intent.opId);
    });

    test('a rejected answer keeps its intent, with the reason, instead of vanishing', () async {
      // The whole point of the crew-intent table. The outbox entry is dropped — a poison entry
      // retried forever wedges the queue — but if that were the only record, the crew member's
      // tap would disappear without a word and the row would revert on the next snapshot.
      final engine = engineFor(MockClient((request) async {
        final body = jsonDecode(request.body) as Map<String, dynamic>;
        final operations = body['operations'] as List<dynamic>;
        return http.Response(
          jsonEncode({
            'cursor': 10,
            'results': [
              {
                'opId': operations[0]['opId'],
                'status': 'rejected',
                'detail': "Unsupported operation type 'requirement.progress'",
              },
            ],
          }),
          200,
        );
      }));

      await engine.queueIntent(
        kind: 'requirement.progress',
        summary: 'Course booked for Sea Survival',
        requirementId: 11,
      );
      await engine.flushOutbox();

      expect(await store.select(store.outbox).get(), isEmpty);
      final intent = await store.select(store.crewIntents).getSingle();
      expect(intent.state, 'failed');
      expect(intent.detail, contains('Unsupported operation type'));
    });

    test('retrying re-posts under the original id, so an applied answer cannot double', () async {
      final engine = engineFor(MockClient((request) async {
        final body = jsonDecode(request.body) as Map<String, dynamic>;
        final operations = body['operations'] as List<dynamic>;
        return http.Response(
          jsonEncode({
            'cursor': 10,
            'results': [
              {'opId': operations[0]['opId'], 'status': 'rejected', 'detail': 'nope'},
            ],
          }),
          200,
        );
      }));

      final opId = await engine.queueIntent(
        kind: 'course.seat_request',
        summary: 'Seat requested',
        subjectRef: 'course-1',
      );
      await engine.flushOutbox();
      await engine.retryIntent(opId);

      final requeued = await store.select(store.outbox).getSingle();
      expect(requeued.opId, opId, reason: 'the idempotency key survives a retry');
      expect(requeued.attempts, 0);
      expect((await store.select(store.crewIntents).getSingle()).state, 'queued');
    });

    test('an applied answer is swept only once it is older than the retention window', () async {
      var clock = DateTime.utc(2026, 7, 26, 8);
      final engine = engineFor(
        MockClient((request) async {
          final body = jsonDecode(request.body) as Map<String, dynamic>;
          final operations = body['operations'] as List<dynamic>;
          return http.Response(
            jsonEncode({
              'cursor': 10,
              'results': [
                {'opId': operations[0]['opId'], 'status': 'applied', 'detail': null},
              ],
            }),
            200,
          );
        }),
        now: () => clock,
      );

      await engine.queueIntent(kind: 'requirement.help', summary: 'Asked for help');
      await engine.flushOutbox();
      expect((await store.select(store.crewIntents).getSingle()).state, 'sent');

      await engine.pruneSettledIntents();
      expect(await store.select(store.crewIntents).get(), hasLength(1),
          reason: '"you told the office on 25 Jul" is still worth showing');

      clock = clock.add(SyncEngine.intentRetention + const Duration(days: 1));
      await engine.pruneSettledIntents();
      expect(await store.select(store.crewIntents).get(), isEmpty);
    });

    test('a failed answer is never swept, however old', () async {
      var clock = DateTime.utc(2026, 7, 26, 8);
      final engine = engineFor(
        MockClient((request) async => http.Response('{}', 500)),
        now: () => clock,
      );

      final opId = await engine.queueIntent(kind: 'team.nudge', summary: 'Nudged Ada');
      await (store.update(store.crewIntents)..where((t) => t.opId.equals(opId)))
          .write(const CrewIntentsCompanion(state: drift.Value('failed')));

      clock = clock.add(const Duration(days: 400));
      await engine.pruneSettledIntents();

      expect(await store.select(store.crewIntents).get(), hasLength(1));
    });

    test("the office's decision arrives on a delta and replaces the device's own record", () async {
      // The loop the outbox opens is only closed here. Before this, a crew member tapped, the row
      // said "Sent to the office", and nothing ever happened on their phone again.
      var pulled = 0;
      final engine = engineFor(MockClient((request) async {
        if (request.url.path.endsWith('/queue')) {
          final body = jsonDecode(request.body) as Map<String, dynamic>;
          final operations = body['operations'] as List<dynamic>;
          return http.Response(
            jsonEncode({
              'cursor': 100,
              'results': [
                {'opId': operations[0]['opId'], 'status': 'applied'},
              ],
            }),
            200,
          );
        }
        if (request.url.path.endsWith('/snapshot')) {
          return http.Response(jsonEncode(snapshotBody()), 200);
        }
        pulled++;
        return http.Response(
          jsonEncode({
            'cursor': 200,
            'referenceCursor': 50,
            'referenceStale': false,
            'serverToday': '2026-07-27',
            'person': null,
            'holdings': [],
            'assignments': [],
            'leave': [],
            'notifications': [],
            'submissions': [],
            'crewStatements': [
              {
                'id': 4,
                // The device's own queue-entry id, echoed back. This is the join.
                'opId': storedOpId,
                'kind': 'requirement.progress',
                'requirementId': 11,
                'status': 'dismissed',
                'aboutExpiry': '2026-08-15',
                'raisedAt': '2026-07-26T08:00:00Z',
                'decisionNote': 'We could not find your booking; can you forward it?',
                'decidedAt': '2026-07-27T02:00:00Z',
              },
            ],
            'tombstones': [],
            'standing': null,
          }),
          200,
        );
      }));

      storedOpId = await engine.queueIntent(
        kind: 'requirement.progress',
        summary: 'Course booked for Sea Survival',
        requirementId: 11,
      );
      await engine.sync();
      await engine.sync();

      expect(pulled, greaterThan(0));
      final statement = await store.select(store.crewStatements).getSingle();
      expect(statement.status, 'dismissed');
      expect(statement.decisionNote, contains('forward it'));

      // And the device's own record is gone rather than sitting beside it saying "Sent to the
      // office" over the top of a decision. One tap, one row.
      expect(await store.select(store.crewIntents).get(), isEmpty);
    });

    test('a statement tombstone removes it, so a withdrawn request does not linger', () async {
      final engine = engineFor(MockClient((request) async {
        if (request.url.path.endsWith('/snapshot')) {
          return http.Response(
            jsonEncode({
              ...snapshotBody(),
              'crewStatements': [
                {
                  'id': 4,
                  'opId': 'op-gone',
                  'kind': 'requirement.help',
                  'requirementId': 11,
                  'status': 'open',
                  'aboutExpiry': null,
                  'raisedAt': '2026-07-26T08:00:00Z',
                  'decisionNote': null,
                  'decidedAt': null,
                },
              ],
            }),
            200,
          );
        }
        return http.Response(
          jsonEncode({
            'cursor': 200,
            'referenceCursor': 50,
            'referenceStale': false,
            'serverToday': '2026-07-27',
            'person': null,
            'holdings': [],
            'assignments': [],
            'leave': [],
            'notifications': [],
            'submissions': [],
            'crewStatements': [],
            'tombstones': [
              {'entityType': 'CrewStatement', 'entityId': 4, 'seq': 190},
            ],
            'standing': null,
          }),
          200,
        );
      }));

      await engine.sync();
      expect(await store.select(store.crewStatements).get(), hasLength(1));
      await engine.sync();
      expect(await store.select(store.crewStatements).get(), isEmpty);
    });
  });

  group('offline validity (SEC-12)', () {
    test('data locks once it is older than the offline window', () async {
      var clock = DateTime.utc(2026, 7, 26);
      final engine = engineFor(
        MockClient((request) async => http.Response(jsonEncode(snapshotBody()), 200)),
        now: () => clock,
      );

      await engine.sync();
      expect(engine.isOfflineExpired(await engine.currentState()), isFalse);

      clock = clock.add(SyncEngine.offlineValidity + const Duration(days: 1));
      expect(engine.isOfflineExpired(await engine.currentState()), isTrue);
    });
  });

  group('back-off', () {
    test('grows with attempts and is capped in minutes, not hours', () {
      expect(SyncEngine.backOffDelay(1) < SyncEngine.backOffDelay(4), isTrue);
      // Maritime connectivity comes in windows; sleeping through one is worse than a few
      // wasted requests.
      expect(SyncEngine.backOffDelay(50), const Duration(seconds: 300));
    });
  });
}
