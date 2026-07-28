import 'package:crewcomp_crew/src/domain/urgency.dart';
import 'package:flutter_test/flutter_test.dart';

/// The one function that decides how loud a deadline is allowed to be.
///
/// Tested on its own because three screens read it — Home's cards, the certifications list and
/// the one-tap update — and the bug it exists to prevent is a certificate that reads amber on one
/// and green on another.
void main() {
  group('urgency', () {
    test('the server\'s verdict dominates: a gap is blocking whatever the dates say', () {
      // A certificate you do not hold has no expiry to count down to, and no date can soften it.
      expect(
        urgencyFor(state: 'gap', today: '2026-07-26', swingTo: '2026-12-31'),
        Urgency.blocking,
      );
      expect(urgencyFor(state: 'gap', expiry: '2027-01-01', today: '2026-07-26'), Urgency.blocking);
    });

    test('a lapsed date is blocking', () {
      expect(
        urgencyFor(state: 'expiring', expiry: '2026-07-20', today: '2026-07-26'),
        Urgency.blocking,
      );
      // Today is the last valid day, not the first invalid one.
      expect(
        urgencyFor(state: 'expiring', expiry: '2026-07-26', today: '2026-07-26'),
        Urgency.blocking,
      );
    });

    test('expiring inside the swing window is soon, outside it is not', () {
      expect(
        urgencyFor(state: 'ok', expiry: '2026-08-14', today: '2026-07-26', swingTo: '2026-08-17'),
        Urgency.soon,
      );
      expect(
        urgencyFor(state: 'ok', expiry: '2026-09-14', today: '2026-07-26', swingTo: '2026-08-17'),
        Urgency.clear,
      );
    });

    test('a state that needs attention is at least soon, with no dates at all', () {
      // `unknown` has nothing to count: nobody ever established whether it is held.
      expect(urgencyFor(state: 'unknown'), Urgency.soon);
      expect(urgencyFor(state: 'review'), Urgency.soon);
      expect(urgencyFor(state: 'ok'), Urgency.clear);
      expect(urgencyFor(state: 'quota_only'), Urgency.clear);
    });

    test('the three urgencies take three visibly different marks', () {
      final marks = Urgency.values.map(urgencyMark).toSet();
      expect(marks, hasLength(3));
    });
  });

  group('lead window', () {
    test('is empty outside the horizon and full once the date has passed', () {
      expect(leadElapsed(200), 0);
      expect(leadElapsed(0), 1);
      expect(leadElapsed(-10), 1);
    });

    test('fills as the deadline closes', () {
      expect(leadElapsed(45, leadDays: 90), closeTo(0.5, 0.001));
      expect(leadElapsed(18, leadDays: 90), closeTo(0.8, 0.001));
      expect(leadElapsed(60) < leadElapsed(18), isTrue);
    });

    test('mirrors the server\'s own default horizon', () {
      // `Planning.DEFAULT_EXPIRY_LEAD_DAYS`. Mirrored rather than read because the payload does
      // not carry it yet; a stale horizon moves a bar and nothing else.
      expect(expiryLeadDaysDefault, 90);
    });
  });

  group('countdown', () {
    test('always names the unit, and never shows an ISO date', () {
      expect(countdownLabel('2026-07-26', '2026-08-13'), '18 days left');
      expect(countdownLabel('2026-07-26', '2026-07-27'), '1 day left');
      expect(countdownLabel('2026-07-26', '2026-07-26'), 'due today');
      expect(countdownLabel('2026-07-26', '2026-07-25'), 'lapsed yesterday');
      expect(countdownLabel('2026-07-26', '2026-07-12'), 'lapsed 14 days ago');
    });
  });
}
