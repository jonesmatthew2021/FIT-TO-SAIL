import 'package:crewcomp_crew/src/data/local_store.dart';
import 'package:crewcomp_crew/src/domain/offers.dart';
import 'package:crewcomp_crew/src/ui/nocturne.dart';
import 'package:crewcomp_crew/src/ui/team_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

/// MOB-11 — a supervisor's watch.
void main() {
  LocalSyncState syncState() => LocalSyncState(
    id: 0,
    cursor: 100,
    referenceCursor: 50,
    serverToday: '2026-07-26',
    lastSyncedAt: DateTime.now().toUtc(),
    standingCcId: 'CC24',
    standingPartnership: 'UNI',
    standingTo: '2026-08-17',
  );

  Future<void> pump(WidgetTester tester, Widget child) async {
    tester.view.physicalSize = const Size(392 * 3, 790 * 3);
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      MaterialApp(
        theme: nocturneTheme(),
        home: Scaffold(body: child),
      ),
    );
  }

  const watch = [
    TeamMember(
      sam: 'SAM101',
      name: 'Ada Nakamura',
      worstState: 'gap',
      reason: 'Work at Heights lapsed 12 Jul · no course booked',
    ),
    TeamMember(
      sam: 'SAM102',
      name: 'Ana Kovač',
      worstState: 'expiring',
      reason: 'CoC expires 21 Aug · evidence awaiting review',
      inHand: true,
    ),
    TeamMember(sam: 'SAM103', name: 'Bruno Oyelaran', worstState: 'ok'),
    TeamMember(sam: 'SAM104', name: 'Chidi Alvarez', worstState: 'ok'),
  ];

  testWidgets('says it has no watch rather than implying the watch is clear', (tester) async {
    // The distinction matters more here than anywhere: an empty list a supervisor reads as
    // "everyone is fine" is worse than no screen at all.
    await pump(tester, TeamView(members: const [], sync: syncState()));

    expect(find.text('No watch to show'), findsOneWidget);
    expect(find.textContaining('does not yet send a supervisor'), findsOneWidget);
  });

  testWidgets('splits the watch into what needs something and what is clear', (tester) async {
    await pump(tester, TeamView(members: watch, sync: syncState()));

    expect(find.text('2 of 4 sail clean'), findsOneWidget);
    expect(find.text('NEEDS SOMETHING'), findsOneWidget);
    expect(find.text('CLEAR'), findsOneWidget);
    expect(find.text('Ada Nakamura'), findsOneWidget);
    expect(find.text('Work at Heights lapsed 12 Jul · no course booked'), findsOneWidget);
  });

  testWidgets('does not offer to chase someone who has already acted', (tester) async {
    await pump(tester, TeamView(members: watch, sync: syncState(), onNudge: (_) {}));

    // Ada has done nothing, so she can be nudged; Ana's evidence is with a reviewer, so a nudge
    // would only annoy someone who has already done the thing.
    expect(find.text('Nudge'), findsOneWidget);
    expect(find.text('In hand'), findsOneWidget);
  });

  testWidgets('nudges once, and reports it', (tester) async {
    final nudged = <String>[];
    await pump(
      tester,
      TeamView(members: watch, sync: syncState(), onNudge: (member) => nudged.add(member.sam)),
    );

    await tester.tap(find.text('Nudge'));
    await tester.pump();
    expect(nudged, ['SAM101']);
  });

  testWidgets('will not nudge twice for the same queued nudge', (tester) async {
    await pump(
      tester,
      TeamView(
        members: watch,
        sync: syncState(),
        onNudge: (_) {},
        nudges: [
          LocalCrewIntent(
            opId: 'op-1',
            kind: 'team.nudge',
            subjectRef: 'SAM101',
            summary: 'Nudged Ada Nakamura',
            payload: '{}',
            queuedAt: DateTime.utc(2026, 7, 26),
            state: 'queued',
          ),
        ],
      ),
    );

    expect(find.text('Nudged'), findsOneWidget);
    expect(find.text('Nudge'), findsNothing);
  });

  testWidgets('states the privacy rule on the screen it applies to', (tester) async {
    await pump(tester, TeamView(members: watch, sync: syncState()));

    await tester.dragUntilVisible(
      find.textContaining('never medical detail or documents'),
      find.byType(Scrollable).first,
      const Offset(0, -120),
    );
    expect(find.textContaining('You see requirement status only'), findsOneWidget);
  });
}
