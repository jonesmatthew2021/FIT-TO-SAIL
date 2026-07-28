import 'package:crewcomp_crew/src/domain/offers.dart';
import 'package:crewcomp_crew/src/ui/evidence_screens.dart';
import 'package:crewcomp_crew/src/ui/nocturne.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

/// MOB-7 — confirming what the server read.
///
/// The case that matters most is the one the system is actually in: **no LLM provider is
/// configured**, so the pipeline extracts nothing and every document goes to a human (LLM-2).
/// The screen has to work at that confidence level as a first-class case, not as an error.
void main() {
  const requirements = [
    (id: 11, label: 'MS-02 · Sea Survival'),
    (id: 12, label: 'MS-01 · Seafarer Medical'),
  ];

  Future<void> pump(WidgetTester tester, Widget child) async {
    tester.view.physicalSize = const Size(392 * 3, 790 * 3);
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(MaterialApp(theme: nocturneTheme(), home: child));
  }

  Future<void> scrollTo(WidgetTester tester, Finder target) =>
      tester.dragUntilVisible(target, find.byType(Scrollable).first, const Offset(0, -120));

  testWidgets('with nothing read, opens empty and says so rather than looking broken', (
    tester,
  ) async {
    await pump(
      tester,
      ConfirmReadingView(
        requirements: requirements,
        fileName: 'Sea-Survival-Oyelaran.pdf',
        byteSize: 1153434,
        requirementId: 11,
        onSend: (_) async {},
      ),
    );

    expect(find.text('Sea-Survival-Oyelaran.pdf'), findsOneWidget);
    // The provenance line has to be true about *this* document. Saying "read on the server" of
    // something still in the outbox is the one lie the screen exists to avoid.
    expect(find.textContaining('Queued to send · 1.1 MB'), findsOneWidget);
    expect(find.text('Matched by you · not yet read'), findsOneWidget);
    expect(find.textContaining('Nothing has been read from this document yet'), findsOneWidget);
    expect(find.text('Not read — add it'), findsWidgets);
  });

  testWidgets('with a high-confidence reading, arrives filled in', (tester) async {
    await pump(
      tester,
      ConfirmReadingView(
        requirements: requirements,
        fileName: 'Sea-Survival-Oyelaran.pdf',
        byteSize: 1153434,
        reading: const ExtractedReading(
          confidence: 'high',
          requirementId: 11,
          certificateNumber: 'SS-4471-2026',
          issued: '2026-07-27',
          expires: '2031-07-27',
        ),
        onSend: (_) async {},
      ),
    );

    expect(find.text('Matched · high confidence'), findsOneWidget);
    expect(find.textContaining('Read on the server'), findsOneWidget);
    expect(find.text('SS-4471-2026'), findsOneWidget);
    expect(find.text('27 Jul 2026'), findsOneWidget);
    expect(find.text('27 Jul 2031'), findsOneWidget);
    expect(find.textContaining('Everything below was read from the document'), findsOneWidget);
  });

  testWidgets('sends the fields as the crew member left them, not as they arrived', (tester) async {
    ExtractedReading? sent;
    await pump(
      tester,
      ConfirmReadingView(
        requirements: requirements,
        fileName: 'scan.jpg',
        requirementId: 11,
        onSend: (corrected) async => sent = corrected,
      ),
    );

    await tester.enterText(find.byType(TextField).first, 'SS-9999-2026');
    await tester.pump();

    await scrollTo(tester, find.text('Send for review'));
    await tester.tap(find.text('Send for review'));
    await tester.pump();

    expect(sent?.certificateNumber, 'SS-9999-2026');
    expect(sent?.requirementId, 11);
  });

  testWidgets('only claims to clear the last thing when it is the last thing', (tester) async {
    await pump(
      tester,
      ConfirmReadingView(
        requirements: requirements,
        requirementId: 11,
        clearsTheLast: true,
        onSend: (_) async {},
      ),
    );

    await scrollTo(tester, find.textContaining('clears the last thing'));
    expect(find.textContaining('clears the last thing on your Home screen'), findsOneWidget);
  });

  testWidgets('promises the offline behaviour the outbox actually gives it', (tester) async {
    await pump(
      tester,
      ConfirmReadingView(requirements: requirements, requirementId: 11, onSend: (_) async {}),
    );

    await scrollTo(tester, find.textContaining('Works offline'));
    expect(find.textContaining('it sends when you have signal'), findsOneWidget);
  });

  group('byte sizes', () {
    test('read at the precision a phone screen can use', () {
      expect(formatBytes(900), '900 B');
      expect(formatBytes(2048), '2 KB');
      expect(formatBytes(1153434), '1.1 MB');
    });
  });
}
