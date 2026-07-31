import 'package:crewcomp_crew/src/data/local_store.dart';
import 'package:crewcomp_crew/src/domain/intents.dart';
import 'package:crewcomp_crew/src/ui/app_state.dart';
import 'package:crewcomp_crew/src/ui/detail_screens.dart';
import 'package:crewcomp_crew/src/ui/nocturne.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

/// The drill-down screens, exercised as pure functions of their data — same split and same
/// reason as `screens_test.dart`: the `*Screen` wrappers own the streams, the `*View` widgets
/// own nothing.
void main() {
  CertificationRow row({
    String state = 'expiring',
    String level = 'M',
    String? cellExpiry = '2026-08-13',
    String? notes,
    String? registerRecordId,
    LocalHolding? holding,
  }) =>
      CertificationRow(
        cell: LocalStandingCell(
          requirementId: 11,
          level: level,
          state: state,
          expiry: cellExpiry,
          notes: notes,
          registerRecordId: registerRecordId,
        ),
        requirement: const LocalRequirement(
          id: 11,
          code: 'MS-02',
          category: 'MS',
          title: 'Sea Survival',
          status: 'active',
          issuingAuthority: 'AMSA',
        ),
        holding: holding,
      );

  LocalSubmission submission({
    String publicId = 'doc-1',
    String status = 'pending_extraction',
    int offset = 0,
    int? size = 1000,
    bool complete = false,
    String source = 'mobile_camera',
  }) =>
      LocalSubmission(
        publicId: publicId,
        source: source,
        declaredSize: size,
        uploadOffset: offset,
        uploadComplete: complete,
        verificationStatus: status,
        submittedAt: DateTime.utc(2026, 7, 27),
      );

  const assignment = LocalAssignment(
    id: 900,
    crewChangeId: 5,
    ccId: 'CC24',
    partnershipAbbrev: 'UNI',
    slotRef: 2,
    fromDate: '2026-07-20',
    toDate: '2026-08-16',
  );

  /// Pumped at the design's own 392×790 logical viewport (an iPhone 17 Pro class device), not
  /// the test framework's default 800×600. The handoff's layout is expressed at that size, and a
  /// screen that only fits in a landscape-shaped window is not the screen that was designed.
  Future<void> pump(WidgetTester tester, Widget child) async {
    tester.view.physicalSize = const Size(392 * 3, 790 * 3);
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(MaterialApp(theme: nocturneTheme(), home: child));
  }

  /// The detail screen is taller than one viewport by design — it ends in a pinned action bar,
  /// so everything below the fold has to be scrolled to rather than merely rendered.
  Future<void> scrollTo(WidgetTester tester, Finder target) => tester.dragUntilVisible(
        target,
        find.byType(Scrollable).first,
        const Offset(0, -120),
      );

  group('certification detail', () {
    testWidgets('shows the cell as evaluated, and what is held beneath it', (tester) async {
      await pump(
        tester,
        CertificationDetailView(
          row: row(
            holding: const LocalHolding(
              id: 1,
              requirementId: 11,
              status: 'held_expiry',
              expiry: '2026-08-13',
              issueDate: '2021-08-13',
            ),
          ),
          submissions: const [],
          today: '2026-07-26',
          onSubmit: () {},
        ),
      );

      expect(find.text('Sea Survival'), findsOneWidget);
      expect(find.text('Expiring'), findsOneWidget);
      expect(find.text('Held, expires'), findsOneWidget);
      expect(find.text('AMSA'), findsOneWidget);
      // Counted from the server's today, which is the only date the device is allowed to use.
      expect(find.textContaining('in 18 days'), findsOneWidget);
    });

    testWidgets('renders an unrecognised state verbatim rather than as reassurance',
        (tester) async {
      await pump(
        tester,
        CertificationDetailView(
          row: row(state: 'quarantined'),
          submissions: const [],
          today: '2026-07-26',
          onSubmit: () {},
        ),
      );

      expect(find.text('quarantined'), findsOneWidget);
      expect(find.text('OK'), findsNothing);
    });

    testWidgets('names the register record behind an overlaid cell', (tester) async {
      await pump(
        tester,
        CertificationDetailView(
          row: row(state: 'exempt', registerRecordId: 'UNI24-003'),
          submissions: const [],
          today: '2026-07-26',
          onSubmit: () {},
        ),
      );

      await scrollTo(tester, find.text('UNI24-003'));
      expect(find.text('UNI24-003'), findsOneWidget);
    });

    testWidgets('reports upload progress against the declared size', (tester) async {
      await pump(
        tester,
        CertificationDetailView(
          row: row(),
          submissions: [submission(offset: 250, size: 1000)],
          today: '2026-07-26',
          onSubmit: () {},
        ),
      );

      await scrollTo(tester, find.textContaining('Sending — 25%'));
      expect(find.textContaining('Sending — 25%'), findsOneWidget);
      expect(find.text('Processing'), findsOneWidget);
    });

    testWidgets('says a submission is sent once the bytes are the server\'s', (tester) async {
      await pump(
        tester,
        CertificationDetailView(
          row: row(),
          submissions: [
            submission(offset: 1000, complete: true, status: 'pending_review'),
          ],
          today: '2026-07-26',
          onSubmit: () {},
        ),
      );

      await scrollTo(tester, find.text('Awaiting review').first);
      expect(find.textContaining('Sent'), findsOneWidget);
      expect(find.text('Awaiting review'), findsWidgets);
    });

    testWidgets('does not promise the submission changes anything by itself', (tester) async {
      await pump(
        tester,
        CertificationDetailView(
          row: row(),
          submissions: const [],
          today: '2026-07-26',
          onSubmit: () {},
        ),
      );

      // LLM-1/LLM-2: read automatically, decided by a person. The screen must not imply that
      // sending a photo makes someone compliant.
      expect(find.textContaining('checked by the compliance team'), findsOneWidget);
    });

    testWidgets('puts the submit action within reach without scrolling', (tester) async {
      // Pinned, deliberately: the action is the point of the screen, and a crew member who has
      // to scroll to find it decides the app is not worth opening.
      var submits = 0;
      await pump(
        tester,
        CertificationDetailView(
          row: row(),
          submissions: const [],
          today: '2026-07-26',
          onSubmit: () => submits++,
        ),
      );

      await tester.tap(find.text('Submit evidence'));
      await tester.pump();

      expect(submits, 1);
    });

    testWidgets('offers the two one-tap answers beside the evidence action', (tester) async {
      final answered = <String>[];
      await pump(
        tester,
        CertificationDetailView(
          row: row(),
          submissions: const [],
          today: '2026-07-26',
          onSubmit: () {},
          onCourseBooked: () => answered.add('booked'),
          onNeedHelp: () => answered.add('help'),
        ),
      );

      // "Update the office", not "Course booked": this button *navigates* to the one-tap
      // screen, and it must not share a label with Home's button, which posts the statement
      // (issue #16 — same words doing two things one screen apart).
      expect(find.text('Course booked'), findsNothing);
      await tester.tap(find.text('Update the office'));
      await tester.tap(find.text('Need help'));
      await tester.pump();

      expect(answered, ['booked', 'help']);
    });

    testWidgets('reports a failed answer with the reason and a way to retry', (tester) async {
      // The rule the whole crew-intent path exists for: a one-tap answer that did not land says
      // so, rather than reverting silently on the next snapshot.
      final retried = <String>[];
      await pump(
        tester,
        CertificationDetailView(
          row: row(),
          submissions: const [],
          today: '2026-07-26',
          answers: const [
            Answer(
              opId: 'op-1',
              kind: 'requirement.progress',
              requirementId: 11,
              summary: 'Course booked for Sea Survival',
              state: AnswerState.failed,
              detail: "Unsupported operation type 'requirement.progress'",
            ),
          ],
          onSubmit: () {},
          onRetryIntent: retried.add,
        ),
      );

      await scrollTo(tester, find.textContaining("Couldn't send"));
      expect(find.textContaining("Couldn't send"), findsOneWidget);
      expect(find.textContaining('Unsupported operation type'), findsOneWidget);

      await tester.tap(find.text('Retry'));
      await tester.pump();

      expect(retried, ['op-1']);
    });
  });

  group('swing detail', () {
    testWidgets('counts the day against the server\'s today, inclusive of both ends',
        (tester) async {
      await pump(
        tester,
        const SwingDetailView(
          assignment: assignment,
          crewChange: null,
          leave: [],
          today: '2026-07-26',
        ),
      );

      // 20 Jul – 16 Aug is 28 days; 26 Jul is day 7 of it.
      expect(find.text('28 days'), findsOneWidget);
      expect(find.text('7 of 28'), findsOneWidget);
      expect(find.text('Current'), findsOneWidget);
    });

    testWidgets('says when a future swing starts rather than pretending it is current',
        (tester) async {
      await pump(
        tester,
        const SwingDetailView(
          assignment: assignment,
          crewChange: null,
          leave: [],
          today: '2026-07-01',
        ),
      );

      expect(find.text('Current'), findsNothing);
      expect(find.text('in 19 days'), findsOneWidget);
    });

    testWidgets('shows the cut-off from the crew change', (tester) async {
      await pump(
        tester,
        const SwingDetailView(
          assignment: assignment,
          crewChange: LocalCrewChange(
            id: 5,
            ccId: 'CC24',
            fromDate: '2026-07-20',
            toDate: '2026-08-16',
            cutoff: '2026-07-13',
          ),
          leave: [],
          today: '2026-07-26',
        ),
      );

      expect(find.textContaining('13 Jul 2026'), findsOneWidget);
      expect(find.textContaining('13 days ago'), findsOneWidget);
    });

    testWidgets('surfaces only the leave that overlaps this swing', (tester) async {
      await pump(
        tester,
        const SwingDetailView(
          assignment: assignment,
          crewChange: null,
          leave: [
            LocalLeave(
              id: 1,
              kind: 'annual_leave',
              fromDate: '2026-08-10',
              toDate: '2026-08-20',
              status: 'recorded',
            ),
            LocalLeave(
              id: 2,
              kind: 'unpaid_leave',
              fromDate: '2026-11-01',
              toDate: '2026-11-10',
              status: 'recorded',
            ),
          ],
          today: '2026-07-26',
        ),
      );

      expect(find.text('Annual leave'), findsOneWidget);
      expect(find.text('Unpaid leave'), findsNothing);
    });

    testWidgets('renders with no server date rather than falling back to the device clock',
        (tester) async {
      await pump(
        tester,
        const SwingDetailView(
          assignment: assignment,
          crewChange: null,
          leave: [],
          today: null,
        ),
      );

      expect(find.text('UNI CC24'), findsOneWidget);
      expect(find.text('Where you are'.toUpperCase()), findsNothing);
    });
  });
}
