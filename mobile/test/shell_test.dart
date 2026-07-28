import 'package:crewcomp_crew/src/ui/nocturne.dart';
import 'package:crewcomp_crew/src/ui/screens.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

/// The app shell — the four-tab bar and the space it leaves for a screen.
///
/// This file exists because of a bug that shipped past `flutter analyze`, past 121 green tests
/// and onto a simulator: the tab bar's inner `Column` was left at its default `MainAxisSize.max`,
/// a `Scaffold` hands its `bottomNavigationBar` loose constraints up to the full screen height,
/// and the bar quietly grew to fill all of it. The app rendered as a blank screen with its tabs
/// floating in the middle, and nothing anywhere threw. Every `*View` test passed throughout,
/// because none of them had a `Scaffold` around the bar.
void main() {
  Future<void> pump(WidgetTester tester, Widget child) async {
    tester.view.physicalSize = const Size(392 * 3, 790 * 3);
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(MaterialApp(theme: nocturneTheme(), home: child));
  }

  testWidgets('the tab bar takes a tab bar\'s worth of height, not the screen\'s', (tester) async {
    await pump(
      tester,
      Scaffold(
        body: const Center(child: Text('the screen')),
        bottomNavigationBar: CrewTabBar(index: 0, onSelected: (_) {}),
      ),
    );

    final bar = tester.getSize(find.byType(CrewTabBar));
    expect(bar.height, lessThan(120), reason: 'a tab bar that fills the screen hides the app');
    expect(bar.height, greaterThanOrEqualTo(44), reason: 'and every tap target clears 44pt');
    // The proof that matters: there is still a screen under it.
    expect(find.text('the screen'), findsOneWidget);
  });

  testWidgets('names the four tabs, and marks the active one', (tester) async {
    await pump(tester, Scaffold(bottomNavigationBar: CrewTabBar(index: 0, onSelected: (_) {})));

    expect(find.text('Home'), findsOneWidget);
    expect(find.text('Certifications'), findsOneWidget);
    expect(find.text('Roster'), findsOneWidget);
    expect(find.text('Alerts'), findsOneWidget);
  });

  testWidgets('swaps Certifications for Team when the person supervises a watch', (tester) async {
    await pump(
      tester,
      Scaffold(bottomNavigationBar: CrewTabBar(index: 1, supervising: true, onSelected: (_) {})),
    );

    expect(find.text('Team'), findsOneWidget);
    expect(find.text('Certifications'), findsNothing);
  });

  testWidgets('reports the tab tapped', (tester) async {
    final selected = <int>[];
    await pump(
      tester,
      Scaffold(bottomNavigationBar: CrewTabBar(index: 0, onSelected: selected.add)),
    );

    await tester.tap(find.text('Roster'));
    await tester.tap(find.text('Alerts'));
    await tester.pump();

    expect(selected, [2, 3]);
  });

  testWidgets('badges the alerts tab with the unread count, and only when there is one', (
    tester,
  ) async {
    await pump(
      tester,
      Scaffold(
        bottomNavigationBar: CrewTabBar(index: 0, onSelected: (_) {}, unread: Stream.value(3)),
      ),
    );
    await tester.pump();

    expect(find.text('3'), findsOneWidget);

    await pump(
      tester,
      Scaffold(
        bottomNavigationBar: CrewTabBar(index: 0, onSelected: (_) {}, unread: Stream.value(0)),
      ),
    );
    await tester.pump();

    expect(find.byType(Badge), findsNothing);
  });
}
