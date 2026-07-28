import 'package:crewcomp_crew/src/data/local_store.dart';
import 'package:crewcomp_crew/src/domain/intents.dart';
import 'package:crewcomp_crew/src/domain/offers.dart';
import 'package:crewcomp_crew/src/ui/app_state.dart';
import 'package:crewcomp_crew/src/ui/nocturne.dart';
import 'package:crewcomp_crew/src/ui/screens.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

/// MOB-0 Home — the screen that answers, in five seconds: am I OK, what have I done well, what is
/// asked of me next and how urgently.
///
/// Same split and same reason as the other screen tests: [HomeView] is a pure function of its
/// data, so these hand it rows rather than a database.
void main() {
  const person = LocalPerson(
    id: 7,
    sam: 'SAM007',
    name: 'Bruno Oyelaran',
    positionId: 2,
    positionName: 'Chief Officer',
    partnershipAbbrev: 'UNI',
    status: 'active',
  );

  LocalSyncState syncState({String rollUp = 'expiring', String? ccId = 'CC24'}) => LocalSyncState(
    id: 0,
    cursor: 100,
    referenceCursor: 50,
    serverToday: '2026-07-26',
    lastSyncedAt: DateTime.now().toUtc(),
    standingCcId: ccId,
    standingPartnership: ccId == null ? null : 'UNI',
    standingFrom: ccId == null ? null : '2026-07-21',
    standingTo: ccId == null ? null : '2026-08-17',
    standingCurrent: ccId == null ? null : true,
    standingRollUp: ccId == null ? null : rollUp,
  );

  CertificationRow row(int id, String state, String code, String title, {String? expiry}) =>
      CertificationRow(
        cell: LocalStandingCell(requirementId: id, level: 'M', state: state, expiry: expiry),
        requirement: LocalRequirement(
          id: id,
          code: code,
          category: code.split('-').first,
          title: title,
          status: 'active',
        ),
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

  final swing = [
    row(11, 'expiring', 'MS-02', 'Sea Survival', expiry: '2026-08-14'),
    row(12, 'ok', 'MS-01', 'Seafarer Medical', expiry: '2027-07-27'),
    row(13, 'ok', 'QL-02', 'CoC — Chief Officer'),
    row(14, 'ok', 'VI-01', 'Vessel Induction'),
    row(15, 'quota_only', 'PS-04', 'Work at Heights'),
    row(16, 'recommended', 'PS-05', 'Confined Space Entry'),
  ];

  group('readiness', () {
    test('counts the server\'s answers, and excludes what does not apply', () {
      // A summary of evaluations, never a new one (AUTH-1). `na` is in neither half: a
      // requirement that does not apply is not something this person is ready for.
      final readiness = readinessFrom([...swing, row(17, 'na', 'PT-01', 'Something else')]);

      expect(readiness.total, 6);
      expect(readiness.ready, 5);
      expect(readiness.complete, isFalse);
    });

    test('is complete when nothing is outstanding', () {
      expect(readinessFrom([row(1, 'ok', 'A-1', 'A')]).complete, isTrue);
    });
  });

  group('answers', () {
    LocalCrewIntent intent(String opId, {String state = 'sent', String? detail}) =>
        LocalCrewIntent(
          opId: opId,
          kind: 'requirement.progress',
          requirementId: 11,
          summary: 'Course booked for Sea Survival',
          payload: '{}',
          queuedAt: DateTime.utc(2026, 7, 26),
          state: state,
          detail: detail,
        );

    LocalCrewStatement statement(String opId, {String status = 'open', String? note}) =>
        LocalCrewStatement(
          id: 1,
          opId: opId,
          kind: 'requirement.progress',
          requirementId: 11,
          status: status,
          raisedAt: DateTime.utc(2026, 7, 26),
          decisionNote: note,
        );

    test('reports what only the device knows when the office has no copy yet', () {
      final answers = answersFrom([intent('op-1', state: 'queued')], const []);

      expect(answers, hasLength(1));
      expect(answers.single.state, AnswerState.queued);
      expect(answers.single.stands, isTrue);
    });

    test('prefers the office\'s row over the device\'s wherever both exist', () {
      // They are joined on the opId — the device's own queue-entry id, echoed back — and the
      // server's wins because it is the only one of the two that can carry a decision. This is
      // the case where the intent has not yet been pruned and would otherwise say "Sent to the
      // office" over the top of an answer.
      final answers = answersFrom(
        [intent('op-1')],
        [statement('op-1', status: 'actioned', note: 'Seat confirmed.')],
      );

      expect(answers, hasLength(1));
      expect(answers.single.state, AnswerState.actioned);
      expect(answers.single.detail, 'Seat confirmed.');
      // The summary the crew member first read is kept while the intent is still there.
      expect(answers.single.summary, 'Course booked for Sea Survival');
    });

    test('falls back to a written phrase once the device record has been pruned', () {
      final answers = answersFrom(const [], [statement('op-1')]);
      expect(answers.single.summary, 'Course booked');
      expect(answers.single.state, AnswerState.sent);
    });

    test('treats a dismissal as no longer standing, and a failure likewise', () {
      expect(answersFrom(const [], [statement('op-1', status: 'dismissed')]).single.stands, isFalse);
      expect(answersFrom([intent('op-2', state: 'failed')], const []).single.stands, isFalse);
      expect(answersFrom(const [], [statement('op-3', status: 'actioned')]).single.stands, isTrue);
    });

    test('reads a status a newer server invents as "the office has it"', () {
      // Unlike a cell state, which renders verbatim because guessing at it could reassure someone
      // wrongly, an unknown workflow step here is still something the office holds.
      expect(answersFrom(const [], [statement('op-1', status: 'escalated')]).single.state,
          AnswerState.sent);
    });
  });

  group('MOB-0 home', () {
    testWidgets('leads with the server\'s roll-up, never a re-derived verdict', (tester) async {
      await pump(
        tester,
        HomeView(
          rows: swing,
          answers: const [],
          person: person,
          sync: syncState(),
          today: '2026-07-26',
        ),
      );

      expect(find.text('You can sail this swing.'), findsOneWidget);
      // The ring, counted from the same cells the list groups.
      expect(find.textContaining('5'), findsWidgets);
      expect(find.text('READY'), findsOneWidget);
    });

    testWidgets('says something is missing when the roll-up is a gap', (tester) async {
      await pump(
        tester,
        HomeView(
          rows: [row(11, 'gap', 'MS-02', 'Sea Survival')],
          answers: const [],
          person: person,
          sync: syncState(rollUp: 'gap'),
          today: '2026-07-26',
        ),
      );

      expect(find.text('Something is missing for this swing.'), findsOneWidget);
      expect(find.text('You can sail this swing.'), findsNothing);
    });

    testWidgets('names the one thing asked, as an ask rather than a noun', (tester) async {
      await pump(
        tester,
        HomeView(
          rows: swing,
          answers: const [],
          person: person,
          sync: syncState(),
          today: '2026-07-26',
        ),
      );

      expect(find.text('Renew Sea Survival'), findsOneWidget);
      expect(find.textContaining('19 DAYS LEFT'), findsOneWidget);
      // The margin against the swing, which is the fact that makes the date matter.
      expect(find.textContaining('before your swing ends'), findsOneWidget);
    });

    testWidgets('offers both one-tap answers and reports which was tapped', (tester) async {
      final tapped = <String>[];
      await pump(
        tester,
        HomeView(
          rows: swing,
          answers: const [],
          person: person,
          sync: syncState(),
          today: '2026-07-26',
          onSendCertificate: (_) => tapped.add('have it'),
          onCourseBooked: (_) => tapped.add('booked'),
        ),
      );

      await tester.tap(find.text('I have it'));
      await tester.tap(find.text('Course booked'));
      await tester.pump();

      expect(tapped, ['have it', 'booked']);
    });

    testWidgets('keeps a recommendation visible without a deadline on it', (tester) async {
      await pump(
        tester,
        HomeView(
          rows: swing,
          answers: const [],
          person: person,
          sync: syncState(),
          today: '2026-07-26',
        ),
      );

      await tester.dragUntilVisible(
        find.text('Confined Space Entry'),
        find.byType(Scrollable).first,
        const Offset(0, -120),
      );
      expect(find.textContaining('no deadline'), findsWidgets);
    });

    testWidgets('draws no credit tiles when the payload carries no credits', (tester) async {
      // The motivational half needs history the device has never been sent. Omitting the tiles
      // is the honest answer; inventing "14 months" is not.
      await pump(
        tester,
        HomeView(
          rows: swing,
          answers: const [],
          person: person,
          sync: syncState(),
          today: '2026-07-26',
        ),
      );

      expect(find.text('never sailed short'), findsNothing);
    });

    testWidgets('draws them when it does', (tester) async {
      await pump(
        tester,
        HomeView(
          rows: swing,
          answers: const [],
          person: person,
          sync: syncState(),
          today: '2026-07-26',
          credits: const Credits(
            streakLabel: '14 months',
            streakCaption: 'never sailed short',
            secondaryLabel: '3 of 4',
            secondaryCaption: 'renewed early',
          ),
        ),
      );

      expect(find.text('14 months'), findsOneWidget);
      expect(find.text('never sailed short'), findsOneWidget);
      expect(find.text('3 of 4'), findsOneWidget);
    });

    testWidgets('with nothing due, keeps the ring and shows only the reassurance', (tester) async {
      await pump(
        tester,
        HomeView(
          rows: [row(12, 'ok', 'MS-01', 'Seafarer Medical')],
          answers: const [],
          person: person,
          sync: syncState(rollUp: 'ok'),
          today: '2026-07-26',
        ),
      );

      expect(find.text('READY'), findsOneWidget);
      expect(find.text('ASKED OF YOU'), findsNothing);
      expect(find.textContaining('Nothing else is asked of you'), findsOneWidget);
    });

    testWidgets('offers a retry inline when the last sync failed, keeping the data', (
      tester,
    ) async {
      await pump(
        tester,
        HomeView(
          rows: swing,
          answers: const [],
          person: person,
          sync: syncState(),
          today: '2026-07-26',
          error: 'Connection closed',
          onSync: () {},
        ),
      );

      expect(find.textContaining("Couldn't sync"), findsOneWidget);
      expect(find.text('Retry'), findsOneWidget);
      // The point of the inline treatment: the last-known answer is still on screen.
      expect(find.text('Renew Sea Survival'), findsOneWidget);
    });

    testWidgets('reports a queued answer on the card that raised it', (tester) async {
      await pump(
        tester,
        HomeView(
          rows: swing,
          person: person,
          sync: syncState(),
          today: '2026-07-26',
          answers: const [
            Answer(
              opId: 'op-1',
              kind: 'requirement.progress',
              requirementId: 11,
              summary: 'Course booked for Sea Survival',
              state: AnswerState.queued,
            ),
          ],
        ),
      );

      expect(find.textContaining('Queued'), findsOneWidget);
      // Already answered, so the button stops asking.
      expect(find.text('Course booked'), findsNothing);
      expect(find.text('Told them'), findsOneWidget);
    });

    testWidgets("shows the office's answer once a coordinator has given one", (tester) async {
      await pump(
        tester,
        HomeView(
          rows: swing,
          person: person,
          sync: syncState(),
          today: '2026-07-26',
          answers: const [
            Answer(
              opId: 'op-1',
              kind: 'requirement.progress',
              requirementId: 11,
              summary: 'Course booked for Sea Survival',
              state: AnswerState.actioned,
              detail: 'Seat confirmed with the provider for 12 Aug.',
            ),
          ],
        ),
      );

      expect(find.textContaining('The office has it in hand'), findsOneWidget);
      // The coordinator's own words, written knowing the crew member reads them.
      expect(find.text('Seat confirmed with the provider for 12 Aug.'), findsOneWidget);
      expect(find.text('Told them'), findsOneWidget);
    });

    testWidgets('puts the ask back when the office could not act on it', (tester) async {
      // The half that matters. A dismissal is the office saying "we could not find that" — the
      // crew member has something to do again, and the server has resumed chasing them for it,
      // so a card still reading "Told them" would be the app disagreeing with the reminders.
      await pump(
        tester,
        HomeView(
          rows: swing,
          person: person,
          sync: syncState(),
          today: '2026-07-26',
          answers: const [
            Answer(
              opId: 'op-1',
              kind: 'requirement.progress',
              requirementId: 11,
              summary: 'Course booked for Sea Survival',
              state: AnswerState.dismissed,
              detail: 'No booking on the provider list for you.',
            ),
          ],
        ),
      );

      expect(find.textContaining("The office couldn't act on this"), findsOneWidget);
      expect(find.text('No booking on the provider list for you.'), findsOneWidget);
      expect(find.text('Course booked'), findsOneWidget);
      expect(find.text('Told them'), findsNothing);
    });

    testWidgets('does not claim a swing it has not been given', (tester) async {
      await pump(
        tester,
        HomeView(
          rows: const [],
          answers: const [],
          person: person,
          sync: syncState(ccId: null),
          today: '2026-07-26',
        ),
      );

      expect(find.text('No swing to check against.'), findsOneWidget);
    });
  });
}
