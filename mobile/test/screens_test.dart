import 'package:crewcomp_crew/src/data/local_store.dart';
import 'package:crewcomp_crew/src/ui/app_state.dart';
import 'package:crewcomp_crew/src/ui/screens.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

/// The crew screens, exercised as pure functions of their data.
///
/// They take rows rather than streams precisely so this file can exist: a widget owning a drift
/// stream drags the database's timers into the widget-test fake-async zone, where the test
/// framework's "a Timer is still pending" invariant fires and the run hangs. The stream plumbing
/// lives in the `*Screen` wrappers, and the replication behaviour it depends on is covered in
/// `sync_engine_test.dart` against a real database.
void main() {
  LocalSyncState syncState({
    String? ccId = 'CC24',
    String? rollUp = 'expiring',
  }) =>
      LocalSyncState(
        id: 0,
        cursor: 100,
        referenceCursor: 50,
        serverToday: '2026-07-26',
        lastSyncedAt: DateTime.utc(2026, 7, 26, 7),
        standingCcId: ccId,
        standingPartnership: ccId == null ? null : 'UNI',
        standingFrom: ccId == null ? null : '2026-07-20',
        standingTo: ccId == null ? null : '2026-08-16',
        standingCurrent: ccId == null ? null : true,
        standingRollUp: ccId == null ? null : rollUp,
      );

  CertificationRow row(int requirementId, String state, String code, String title) =>
      CertificationRow(
        cell: LocalStandingCell(requirementId: requirementId, level: 'M', state: state),
        requirement: LocalRequirement(
          id: requirementId,
          code: code,
          category: code.split('-').first,
          title: title,
          status: 'active',
        ),
      );

  Future<void> pump(WidgetTester tester, Widget child) =>
      tester.pumpWidget(MaterialApp(home: Scaffold(body: child)));

  group('MOB-1 certifications', () {
    testWidgets('put the worst state first, as the engine ordered it', (tester) async {
      await pump(
        tester,
        CertificationsView(
          rows: [
            row(12, 'ok', 'VI-01', 'Vessel Induction'),
            row(11, 'gap', 'MS-02', 'Sea Survival'),
          ],
          sync: syncState(),
          today: '2026-07-26',
        ),
      );

      expect(find.text('Sea Survival'), findsOneWidget);
      expect(find.text('Vessel Induction'), findsOneWidget);
      expect(find.text('NEEDS ATTENTION'), findsOneWidget);

      // §5.4 worklist order, on screen: the gap sits above the compliant row even though it
      // arrived second.
      expect(
        tester.getTopLeft(find.text('Sea Survival')).dy,
        lessThan(tester.getTopLeft(find.text('Vessel Induction')).dy),
      );
    });

    testWidgets('name the swing the roll-up was computed against', (tester) async {
      // A roll-up is only meaningful against a swing (§5.2), so the screen says which one
      // rather than leaving the reader to assume it is "now".
      await pump(
        tester,
        CertificationsView(
          rows: [row(11, 'expiring', 'MS-02', 'Sea Survival')],
          sync: syncState(),
          today: '2026-07-26',
        ),
      );

      expect(find.text('UNI CC24'), findsOneWidget);
      expect(find.textContaining('in progress'), findsOneWidget);
      expect(find.text('Expiring'), findsWidgets);
    });

    testWidgets('tell a crew member with no swing so, rather than implying compliance',
        (tester) async {
      await pump(
        tester,
        CertificationsView(rows: const [], sync: syncState(ccId: null), today: '2026-07-26'),
      );

      expect(find.text('No upcoming swing'), findsOneWidget);
      expect(find.textContaining('assessed against a swing'), findsOneWidget);
    });

    testWidgets('count expiry against the server\'s today, not the device clock',
        (tester) async {
      await pump(
        tester,
        CertificationsView(
          rows: [
            CertificationRow(
              cell: LocalStandingCell(
                requirementId: 11,
                level: 'M',
                state: 'expiring',
                expiry: '2026-08-05',
              ),
              requirement: LocalRequirement(
                id: 11,
                code: 'MS-02',
                category: 'MS',
                title: 'Sea Survival',
                status: 'active',
              ),
            ),
          ],
          sync: syncState(),
          today: '2026-07-26',
        ),
      );

      // 10 days from the server's 2026-07-26, whatever this machine's clock and zone say.
      expect(find.textContaining('expires in 10 days'), findsOneWidget);
    });

    testWidgets('render an unrecognised state verbatim rather than as reassurance',
        (tester) async {
      await pump(
        tester,
        CertificationsView(
          rows: [row(11, 'invented_state', 'MS-02', 'Sea Survival')],
          sync: syncState(),
          today: '2026-07-26',
        ),
      );

      expect(find.text('invented_state'), findsOneWidget);
      expect(find.text('OK'), findsNothing);
    });
  });

  group('MOB-2 roster', () {
    testWidgets('mark the swing in progress using the server\'s today', (tester) async {
      await pump(
        tester,
        RosterView(
          assignments: [
            LocalAssignment(
              id: 900,
              crewChangeId: 5,
              ccId: 'CC24',
              partnershipAbbrev: 'UNI',
              slotRef: 2,
              fromDate: '2026-07-20',
              toDate: '2026-08-16',
            ),
            LocalAssignment(
              id: 901,
              crewChangeId: 6,
              ccId: 'CC25',
              partnershipAbbrev: 'UNI',
              slotRef: 2,
              fromDate: '2026-09-20',
              toDate: '2026-10-16',
            ),
          ],
          leave: const [],
          today: '2026-07-26',
        ),
      );

      expect(find.text('UNI CC24'), findsOneWidget);
      expect(find.text('UNI CC25'), findsOneWidget);
      // Exactly one, decided against serverToday.
      expect(find.text('Current'), findsOneWidget);
    });

    testWidgets('show leave read-only, with no way to request or amend it', (tester) async {
      // O-8 is unresolved — leave may become a mirror of an HR system — so V1 offers no
      // write path at all rather than one that might have to be withdrawn.
      await pump(
        tester,
        RosterView(
          assignments: const [],
          leave: [
            LocalLeave(
              id: 1,
              kind: 'annual_leave',
              fromDate: '2026-08-19',
              toDate: '2026-08-28',
              status: 'recorded',
            ),
          ],
          today: '2026-07-26',
        ),
      );

      expect(find.text('annual leave'), findsOneWidget);
      expect(find.text('19–28 Aug 2026'), findsOneWidget);
      expect(find.byType(TextField), findsNothing);
      expect(find.byType(FilledButton), findsNothing);
    });
  });

  group('MOB-3 notifications', () {
    testWidgets('show the detail in-app, not only the push-safe title', (tester) async {
      // SEC-13: the title is all a push payload may carry; the body must be reachable here.
      await pump(
        tester,
        NotificationsView(
          notifications: [
            LocalNotification(
              id: 4,
              kind: 'expiry_warning',
              title: 'A qualification is expiring soon',
              body: 'Your Sea Survival certificate expires on 13 Aug 2026.',
              createdAt: DateTime.utc(2026, 7, 25),
            ),
          ],
          onMarkRead: (_) {},
        ),
      );

      expect(find.text('A qualification is expiring soon'), findsOneWidget);
      expect(
        find.text('Your Sea Survival certificate expires on 13 Aug 2026.'),
        findsOneWidget,
      );
    });

    testWidgets('mark an unread notification read on tap', (tester) async {
      final marked = <int>[];
      await pump(
        tester,
        NotificationsView(
          notifications: [
            LocalNotification(
              id: 3,
              kind: 'expiry_warning',
              title: 'A qualification is expiring soon',
              createdAt: DateTime.utc(2026, 7, 25),
            ),
          ],
          onMarkRead: marked.add,
        ),
      );

      await tester.tap(find.text('A qualification is expiring soon'));
      await tester.pump();

      expect(marked, [3]);
    });

    testWidgets('do not re-mark one that is already read', (tester) async {
      final marked = <int>[];
      await pump(
        tester,
        NotificationsView(
          notifications: [
            LocalNotification(
              id: 3,
              kind: 'expiry_warning',
              title: 'A qualification is expiring soon',
              createdAt: DateTime.utc(2026, 7, 25),
              readAt: DateTime.utc(2026, 7, 25, 12),
            ),
          ],
          onMarkRead: marked.add,
        ),
      );

      await tester.tap(find.text('A qualification is expiring soon'));
      await tester.pump();

      expect(marked, isEmpty);
    });
  });
}
