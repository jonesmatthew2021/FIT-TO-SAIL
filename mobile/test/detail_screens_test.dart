import 'package:crewcomp_crew/src/data/evidence_capture.dart';
import 'package:crewcomp_crew/src/data/local_store.dart';
import 'package:crewcomp_crew/src/ui/app_state.dart';
import 'package:crewcomp_crew/src/ui/detail_screens.dart';
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

  Future<void> pump(WidgetTester tester, Widget child) =>
      tester.pumpWidget(MaterialApp(home: child));

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
          onSubmit: (_) async => null,
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
          onSubmit: (_) async => null,
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
          onSubmit: (_) async => null,
        ),
      );

      expect(find.text('UNI24-003'), findsOneWidget);
    });

    testWidgets('reports upload progress against the declared size', (tester) async {
      await pump(
        tester,
        CertificationDetailView(
          row: row(),
          submissions: [submission(offset: 250, size: 1000)],
          today: '2026-07-26',
          onSubmit: (_) async => null,
        ),
      );

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
          onSubmit: (_) async => null,
        ),
      );

      expect(find.textContaining('Sent'), findsOneWidget);
      expect(find.text('Awaiting review'), findsOneWidget);
    });

    testWidgets('does not promise the submission changes anything by itself', (tester) async {
      await pump(
        tester,
        CertificationDetailView(
          row: row(),
          submissions: const [],
          today: '2026-07-26',
          onSubmit: (_) async => null,
        ),
      );

      // LLM-1/LLM-2: read automatically, decided by a person. The screen must not imply that
      // sending a photo makes someone compliant.
      expect(find.textContaining('checked by the compliance team'), findsOneWidget);
    });

    testWidgets('offers all three capture sources and reports the one chosen', (tester) async {
      CaptureSource? chosen;
      await pump(
        tester,
        CertificationDetailView(
          row: row(),
          submissions: const [],
          today: '2026-07-26',
          onSubmit: (source) async {
            chosen = source;
            return null;
          },
        ),
      );

      await tester.tap(find.text('Submit evidence'));
      await tester.pumpAndSettle();

      expect(find.text('Take a photo'), findsOneWidget);
      expect(find.text('Choose from library'), findsOneWidget);
      expect(find.text('Attach a file'), findsOneWidget);

      await tester.tap(find.text('Choose from library'));
      await tester.pumpAndSettle();

      expect(chosen, CaptureSource.photoLibrary);
    });

    testWidgets('shows the message a refused capture came back with', (tester) async {
      await pump(
        tester,
        CertificationDetailView(
          row: row(),
          submissions: const [],
          today: '2026-07-26',
          onSubmit: (_) async => 'That file is 92.0 MB. The limit is 64 MB.',
        ),
      );

      await tester.tap(find.text('Submit evidence'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Attach a file'));
      await tester.pumpAndSettle();

      expect(find.textContaining('The limit is 64 MB'), findsOneWidget);
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

      expect(find.text('annual leave'), findsOneWidget);
      expect(find.text('unpaid leave'), findsNothing);
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
