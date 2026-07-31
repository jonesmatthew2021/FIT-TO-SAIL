import 'package:crewcomp_crew/src/data/local_store.dart';
import 'package:crewcomp_crew/src/domain/intents.dart';
import 'package:crewcomp_crew/src/domain/offers.dart';
import 'package:crewcomp_crew/src/ui/action_screens.dart';
import 'package:crewcomp_crew/src/ui/app_state.dart';
import 'package:crewcomp_crew/src/ui/nocturne.dart';
import 'package:crewcomp_crew/src/ui/widgets.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

/// The four screens a crew member answers on — MOB-5, MOB-8, MOB-10 and MOB-9.
///
/// Each is a pure `*View` over its data, so none of these tests needs a database or a network.
/// What they mostly assert is the *refusals*: the send button that stays dead until a reason is
/// picked, the sign-off that stays dead until every line is confirmed, and the course list that
/// says it has no dates rather than showing a plausible one.
void main() {
  CertificationRow row({String state = 'expiring', String? expiry = '2026-08-14'}) =>
      CertificationRow(
        cell: LocalStandingCell(requirementId: 11, level: 'M', state: state, expiry: expiry),
        requirement: const LocalRequirement(
          id: 11,
          code: 'MS-02',
          category: 'MS',
          title: 'Sea Survival',
          status: 'active',
        ),
      );

  const assignment = LocalAssignment(
    id: 900,
    crewChangeId: 5,
    ccId: 'CC24',
    partnershipAbbrev: 'UNI',
    slotRef: 2,
    fromDate: '2026-07-28',
    toDate: '2026-08-17',
  );

  Future<void> pump(WidgetTester tester, Widget child) async {
    tester.view.physicalSize = const Size(392 * 3, 790 * 3);
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(MaterialApp(theme: nocturneTheme(), home: child));
  }

  Future<void> scrollTo(WidgetTester tester, Finder target) =>
      tester.dragUntilVisible(target, find.byType(Scrollable).first, const Offset(0, -120));

  group('MOB-5 one-tap update', () {
    testWidgets('offers three answers and fires the one tapped', (tester) async {
      final tapped = <String>[];
      await pump(
        tester,
        OneTapUpdateView(
          row: row(),
          today: '2026-07-26',
          swingTo: '2026-08-17',
          onHaveIt: () => tapped.add('have'),
          onCourseBooked: () => tapped.add('booked'),
          onNeedHelp: () => tapped.add('help'),
        ),
      );

      expect(find.text('EXPIRES IN 19 DAYS'), findsOneWidget);

      await tester.tap(find.text('I have the new certificate'));
      await tester.tap(find.text('I have booked the course'));
      await tester.tap(find.text('I need help arranging it'));
      await tester.pump();

      expect(tapped, ['have', 'booked', 'help']);
    });

    testWidgets('says asking for help costs nothing — the whole point of the screen', (
      tester,
    ) async {
      await pump(tester, OneTapUpdateView(row: row(), today: '2026-07-26'));

      expect(
        find.textContaining('Nothing is recorded against you for asking for help'),
        findsOneWidget,
      );
    });

    testWidgets('stops asking once an answer is already queued', (tester) async {
      await pump(
        tester,
        OneTapUpdateView(
          row: row(),
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

      expect(find.text('I have booked the course'), findsNothing);
      expect(find.text('You told them it is booked'), findsOneWidget);
    });
  });

  group('MOB-8 course booking', () {
    testWidgets('says no date would work rather than showing a plausible one', (tester) async {
      // The catalogue exists now, so an empty list means something specific: every date it holds
      // either finishes too late or runs while this person is at sea. For anybody rostered across
      // a whole swing that is not an edge case — an expiring certificate can never have an
      // attendable date — so the empty state offers the two things that do exist.
      var asked = 0;
      await pump(
        tester,
        CourseBookingView(
          row: row(),
          options: const [],
          today: '2026-07-26',
          onOtherDates: () => asked++,
        ),
      );

      expect(find.text('No dates that would work'), findsOneWidget);
      await tester.tap(find.text('Ask the office to arrange it'));
      await tester.pump();
      expect(asked, 1);
    });

    testWidgets('marks the recommended date and reads one seat as pressure', (tester) async {
      CourseOption? requested;
      await pump(
        tester,
        CourseBookingView(
          row: row(),
          today: '2026-07-26',
          swingTo: '2026-08-17',
          options: const [
            CourseOption(
              id: 'c1',
              starts: '2026-08-03',
              finishes: '2026-08-04',
              provider: 'Maritime Training Centre',
              location: 'Fremantle',
              durationLabel: '2 days',
              seats: 4,
              note: 'Clear of your leave · 11 days before expiry',
              recommended: true,
            ),
            CourseOption(
              id: 'c2',
              starts: '2026-08-06',
              finishes: '2026-08-06',
              provider: 'Ocean Safety School',
              location: 'Henderson',
              durationLabel: '1 day refresher',
              seats: 1,
            ),
          ],
          onRequestSeat: (option) => requested = option,
        ),
      );

      expect(find.text('Mon 3 – Tue 4 Aug'), findsOneWidget);
      expect(find.text('Thu 6 Aug'), findsOneWidget);
      expect(find.text('4 seats'), findsOneWidget);
      expect(find.text('1 seat'), findsOneWidget);

      await tester.tap(find.text('Request seat').first);
      await tester.pump();
      expect(requested?.id, 'c1');
    });

    const seatOption = CourseOption(
      id: 'c1',
      starts: '2026-08-03',
      finishes: '2026-08-04',
      provider: 'MTC',
      location: 'Fremantle',
      durationLabel: '2 days',
      seats: 4,
    );

    testWidgets('will not ask twice for the same seat', (tester) async {
      await pump(
        tester,
        CourseBookingView(
          row: row(),
          today: '2026-07-26',
          options: const [seatOption],
          answers: const [
            Answer(
              opId: 'op-1',
              kind: 'course.seat_request',
              state: AnswerState.queued,
              summary: 'Seat requested for Mon 3 – Tue 4 Aug',
              subjectRef: 'c1',
            ),
          ],
        ),
      );

      expect(find.text('Requested'), findsOneWidget);
      expect(find.text('Request seat'), findsNothing);
    });

    testWidgets('remembers a successful request after the intent is pruned', (tester) async {
      // Issue #15: the intent is deleted the moment the server's statement lands, so the card
      // must read the merged view — a screen keyed on intents forgets the *successful* path,
      // re-arms the button, and a second tap mints a duplicate ADM-11 row.
      await pump(
        tester,
        CourseBookingView(
          row: row(),
          today: '2026-07-26',
          options: const [seatOption],
          // What answersFrom produces once only the server's statement remains.
          answers: const [
            Answer(
              opId: 'op-1',
              kind: 'course.seat_request',
              state: AnswerState.sent,
              summary: 'Seat requested',
              subjectRef: 'c1',
            ),
          ],
        ),
      );

      expect(find.text('Requested'), findsOneWidget);
      expect(find.text('Request seat'), findsNothing);
    });

    testWidgets('a dismissed request puts the button back', (tester) async {
      await pump(
        tester,
        CourseBookingView(
          row: row(),
          today: '2026-07-26',
          options: const [seatOption],
          answers: const [
            Answer(
              opId: 'op-1',
              kind: 'course.seat_request',
              state: AnswerState.dismissed,
              summary: 'Seat requested',
              subjectRef: 'c1',
              detail: 'No budget approval for this provider.',
            ),
          ],
        ),
      );

      expect(find.text('Request seat'), findsOneWidget);
      // The office's own words stay on screen — the ask re-arms, the answer does not vanish.
      expect(find.textContaining('No budget approval'), findsOneWidget);
    });
  });

  group('MOB-10 exemption request', () {
    testWidgets('will not send without a reason', (tester) async {
      var sends = 0;
      await pump(
        tester,
        ExemptionRequestView(
          row: row(),
          today: '2026-07-26',
          swingTo: '2026-08-17',
          onSend: (_, _, _) async => sends++,
        ),
      );

      await scrollTo(tester, find.text('Send the request'));
      await tester.tap(find.text('Send the request'));
      await tester.pump();
      expect(sends, 0, reason: 'a reason is the whole content of the request');

      await tester.tap(find.text('No seat before the expiry date'));
      await tester.pump();
      await scrollTo(tester, find.text('Send the request'));
      await tester.tap(find.text('Send the request'));
      await tester.pump();
      expect(sends, 1);
    });

    testWidgets('is single-select — a crew member has one reason, not three', (tester) async {
      String? sentReason;
      await pump(
        tester,
        ExemptionRequestView(
          row: row(),
          today: '2026-07-26',
          onSend: (reason, _, _) async => sentReason = reason,
        ),
      );

      await tester.tap(find.text('No seat before the expiry date'));
      await tester.pump();
      await tester.tap(find.text('Medical or personal reason'));
      await tester.pump();

      await scrollTo(tester, find.text('Send the request'));
      await tester.tap(find.text('Send the request'));
      await tester.pump();

      expect(sentReason, 'medical_personal');
    });

    testWidgets('attaches the earlier requests without making the crew member explain them', (
      tester,
    ) async {
      List<String>? attached;
      await pump(
        tester,
        ExemptionRequestView(
          row: row(),
          today: '2026-07-26',
          attachedOpIds: const ['op-waitlist', 'op-help'],
          onSend: (_, _, attachments) async => attached = attachments,
        ),
      );

      expect(find.textContaining('attached automatically'), findsOneWidget);

      await tester.tap(find.text('No seat before the expiry date'));
      await tester.pump();
      await scrollTo(tester, find.text('Send the request'));
      await tester.tap(find.text('Send the request'));
      await tester.pump();

      expect(attached, ['op-waitlist', 'op-help']);
    });

    testWidgets('names the consequence of not being granted one', (tester) async {
      await pump(
        tester,
        ExemptionRequestView(row: row(), today: '2026-07-26', onSend: (_, _, _) async {}),
      );

      await scrollTo(tester, find.textContaining('you cannot sail past'));
      expect(find.textContaining('you cannot sail past 14 Aug 2026'), findsOneWidget);
    });
  });

  group('MOB-9 attestation', () {
    final declarations = declarationsFor([
      CertificationRow(
        cell: const LocalStandingCell(requirementId: 11, level: 'M', state: 'expiring'),
        requirement: const LocalRequirement(
          id: 11,
          code: 'MS-02',
          category: 'MS',
          title: 'Sea Survival',
          status: 'active',
        ),
      ),
      CertificationRow(
        cell: const LocalStandingCell(
          requirementId: 12,
          level: 'M',
          state: 'ok',
          expiry: '2027-07-27',
        ),
        requirement: const LocalRequirement(
          id: 12,
          code: 'MS-01',
          category: 'MS',
          title: 'Seafarer Medical',
          status: 'active',
        ),
      ),
    ]);

    test('draws its supporting facts from the evaluated record, not from nowhere', () {
      expect(declarations, hasLength(3));
      expect(declarations.first.supporting, '2 requirements · 1 held, 1 renewing');
      expect(declarations[1].supporting, 'Seafarer Medical valid to 27 Jul 2027');
      // The last line is a statement only the person can make, so nothing pre-ticks it.
      expect(declarations.last.satisfied, isFalse);
    });

    testWidgets('will not send until every line is confirmed', (tester) async {
      List<String>? sent;
      await pump(
        tester,
        AttestationView(
          assignment: assignment,
          declarations: declarations,
          personName: 'Bruno Oyelaran',
          today: '2026-07-28',
          onAttest: (confirmed) async => sent = confirmed,
        ),
      );

      expect(find.text('Due today'), findsOneWidget);

      await scrollTo(tester, find.text('Attest and send'));
      await tester.tap(find.text('Attest and send'));
      await tester.pump();
      expect(sent, isNull);

      // Two lines are outstanding: the medical one arrives ticked from the record, the other two
      // are the person's own statements.
      await scrollTo(tester, find.text('My certificates are the ones on record'));
      await tester.tap(find.text('My certificates are the ones on record'));
      await tester.pump();

      await scrollTo(tester, find.text('Attest and send'));
      await tester.tap(find.text('Attest and send'));
      await tester.pump();
      expect(sent, isNull, reason: 'two of three is not a declaration');

      await scrollTo(tester, find.text('Nothing has changed that affects my fitness or licences'));
      await tester.tap(find.text('Nothing has changed that affects my fitness or licences'));
      await tester.pump();

      await scrollTo(tester, find.text('Attest and send'));
      await tester.tap(find.text('Attest and send'));
      await tester.pump();

      expect(sent, ['medically_fit', 'nothing_changed', 'records_correct']);
    });

    testWidgets('does not print a signature and a timestamp it cannot produce', (tester) async {
      // The mock shows "Face ID · 28 Jul 2026, 07:05 AWST". There is no biometric binding on this
      // build and the timestamp has to be the server's, in the vessel's timezone — a legal
      // declaration timestamped from a phone clock is worth nothing.
      await pump(
        tester,
        AttestationView(
          assignment: assignment,
          declarations: declarations,
          personName: 'Bruno Oyelaran',
          today: '2026-07-28',
          onAttest: (_) async {},
        ),
      );

      await scrollTo(tester, find.textContaining('Signed by'));
      expect(find.text('Signed by Bruno Oyelaran'), findsOneWidget);
      expect(find.textContaining('Face ID'), findsNothing);
      expect(find.textContaining('the office records the time it arrives'), findsOneWidget);
    });

    testWidgets('shows what was actually signed, not what happened to be true', (tester) async {
      // The bug this replaces: the ticks were rebuilt from `Declaration.satisfied` every time the
      // screen opened, so reopening a signed attestation showed only the lines that were already
      // true from the record — every line the crew member confirmed by hand came back empty, on a
      // record whose whole point is being able to say what somebody confirmed.
      await pump(
        tester,
        AttestationView(
          assignment: assignment,
          declarations: declarations,
          today: '2026-07-28',
          attempt: const Answer(
            opId: 'op-1',
            kind: 'attestation.sign_off',
            state: AnswerState.sent,
            summary: 'Signed off',
          ),
          // Only one of the three, and deliberately not the one `satisfied` would have produced:
          // signing with a line outstanding is a real thing to do, and which line it was is the
          // record.
          confirmed: const ['records_correct'],
          signedLine: '28 Jul 2026, 07:05 AWST',
          onAttest: (_) async {},
        ),
      );

      final rows = tester.widgetList<ChoiceRow>(find.byType(ChoiceRow)).toList();
      expect(rows.where((row) => row.selected).length, 1);
      expect(
        rows.firstWhere((row) => row.selected).label,
        'My certificates are the ones on record',
      );
    });

    testWidgets("prints the office's own signature line once it has one", (tester) async {
      await pump(
        tester,
        AttestationView(
          assignment: assignment,
          declarations: declarations,
          personName: 'Bruno Oyelaran',
          today: '2026-07-28',
          attempt: const Answer(
            opId: 'op-1',
            kind: 'attestation.sign_off',
            state: AnswerState.sent,
            summary: 'Signed off',
          ),
          confirmed: const ['records_correct'],
          // Formatted by the server, in the vessel's timezone. The device never composes one: it
          // knows neither the operating timezone nor the admin date override.
          signedLine: '28 Jul 2026, 07:05 AWST',
          onAttest: (_) async {},
        ),
      );

      await scrollTo(tester, find.textContaining('AWST'));
      expect(find.text('28 Jul 2026, 07:05 AWST'), findsOneWidget);
      expect(find.textContaining('the office records the time it arrives'), findsNothing);
    });

    testWidgets('a refused sign-off is not a signature, and can be sent again', (tester) async {
      // A failed attempt used to render as "Already signed" with the rows locked: the crew member
      // believed they had signed and the office had nothing. The record of the tap is still shown
      // — it must never vanish — but it reads as a failure and the screen is live again.
      final retried = <String>[];
      await pump(
        tester,
        AttestationView(
          assignment: assignment,
          declarations: declarations,
          today: '2026-07-28',
          attempt: const Answer(
            opId: 'op-1',
            kind: 'attestation.sign_off',
            state: AnswerState.failed,
            summary: 'Signed off for UNI CC24',
            detail: "Unsupported operation type 'attestation.sign_off'",
          ),
          confirmed: const ['records_correct', 'medically_fit', 'no_change'],
          onRetry: retried.add,
          onAttest: (_) async {},
        ),
      );

      await scrollTo(tester, find.text('Attest and send'));
      expect(find.text('Already signed'), findsNothing);
      expect(find.textContaining("Couldn't send"), findsOneWidget);
      // And the signature block does not claim a time for something that never arrived.
      expect(find.textContaining('the office records the time it arrives'), findsOneWidget);

      await tester.tap(find.text('Retry'));
      await tester.pump();
      expect(retried, ['op-1']);
    });

    testWidgets('states the weight of a false declaration', (tester) async {
      await pump(
        tester,
        AttestationView(
          assignment: assignment,
          declarations: declarations,
          today: '2026-07-28',
          onAttest: (_) async {},
        ),
      );

      await scrollTo(tester, find.textContaining('disciplinary matter'));
      expect(find.textContaining('A false declaration is a disciplinary matter'), findsOneWidget);
    });
  });
}
