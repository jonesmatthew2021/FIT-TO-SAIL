import 'package:crewcomp_crew/src/domain/calendar.dart';
import 'package:flutter_test/flutter_test.dart';

/// NFR-5 / O-11: business dates are calendar dates in the operating timezone.
void main() {
  group('epochDay', () {
    test('is timezone-independent', () {
      // The specific failure this exists to prevent: `DateTime.parse` on a bare date yields
      // *local* midnight, so on a phone set west of AWST the same string is a different day.
      // These values are fixed and must not move with the host's timezone.
      expect(epochDay('1970-01-01'), 0);
      expect(epochDay('2026-07-26'), 20660);
      expect(epochDay('2026-07-27') - epochDay('2026-07-26'), 1);
    });

    test('rejects anything that is not a calendar date', () {
      expect(() => epochDay('2026-07-26T00:00:00Z'), throwsArgumentError);
      expect(() => epochDay('26/07/2026'), throwsArgumentError);
      expect(() => epochDay(''), throwsArgumentError);
    });

    test('crosses month, year and leap-day boundaries', () {
      expect(daysBetween('2026-01-31', '2026-02-01'), 1);
      expect(daysBetween('2026-12-31', '2027-01-01'), 1);
      expect(daysBetween('2028-02-28', '2028-03-01'), 2); // 2028 is a leap year
    });
  });

  group('daysBetween', () {
    test('is signed', () {
      expect(daysBetween('2026-07-01', '2026-07-29'), 28);
      expect(daysBetween('2026-07-29', '2026-07-01'), -28);
      expect(daysBetween('2026-07-01', '2026-07-01'), 0);
    });
  });

  group('isWithin', () {
    test('includes both ends of a swing window', () {
      expect(isWithin('2026-07-20', '2026-07-20', '2026-08-16'), isTrue);
      expect(isWithin('2026-08-16', '2026-07-20', '2026-08-16'), isTrue);
      expect(isWithin('2026-07-19', '2026-07-20', '2026-08-16'), isFalse);
      expect(isWithin('2026-08-17', '2026-07-20', '2026-08-16'), isFalse);
    });
  });

  group('formatting', () {
    test('writes dates day-first, as the operating region does', () {
      expect(formatDate('2026-08-01'), '1 Aug 2026');
      expect(formatDate('2026-12-25'), '25 Dec 2026');
    });

    test('collapses the parts a date range shares', () {
      expect(formatDateRange('2026-08-01', '2026-08-28'), '1–28 Aug 2026');
      expect(formatDateRange('2026-07-20', '2026-08-16'), '20 Jul – 16 Aug 2026');
      expect(formatDateRange('2026-12-20', '2027-01-16'), '20 Dec 2026 – 16 Jan 2027');
    });

    test('passes a malformed date through rather than inventing one', () {
      expect(formatDate('not-a-date'), 'not-a-date');
    });

    test('carries the weekday on a date someone has to plan around', () {
      // MOB-8: "3 Aug" does not tell a crew member whether a two-day course eats their weekend,
      // and that is the first thing they check. 1970-01-01 was a Thursday, in every timezone —
      // which is why this comes off the epoch day rather than a `DateTime`.
      expect(formatDateWeekday('1970-01-01'), 'Thu 1 Jan');
      expect(formatDateWeekday('2026-08-03'), 'Mon 3 Aug');
      expect(formatDateWeekday('2026-08-03', withYear: true), 'Mon 3 Aug 2026');
    });

    test('rewrites an ISO date inside server-composed prose, and touches nothing else', () {
      // `NotificationService` builds bodies with a raw `LocalDate.toString()`. 08-14 and 14-08 are
      // two different days to two people in the same crew room.
      expect(
        humaniseDates('Your Sea Survival certificate expires on 2026-08-14, before the end.'),
        'Your Sea Survival certificate expires on 14 Aug 2026, before the end.',
      );
      expect(
        humaniseDates('UNI CC24, slot 2, 2026-07-21 to 2026-08-17.'),
        'UNI CC24, slot 2, 21 Jul 2026 to 17 Aug 2026.',
      );
      // A requirement code is not a date, and a version label is not one either.
      expect(humaniseDates('MS-02 under matrix dev-2026.1'), 'MS-02 under matrix dev-2026.1');
    });

    test('collapses the month a course range shares, and a one-day course to a date', () {
      expect(formatWeekdayRange('2026-08-03', '2026-08-04'), 'Mon 3 – Tue 4 Aug');
      expect(formatWeekdayRange('2026-07-30', '2026-08-01'), 'Thu 30 Jul – Sat 1 Aug');
      expect(formatWeekdayRange('2026-08-06', '2026-08-06'), 'Thu 6 Aug');
    });
  });

  group('relativeDays', () {
    test('reads from the server\'s today, never the device clock', () {
      expect(relativeDays('2026-07-26', '2026-07-26'), 'today');
      expect(relativeDays('2026-07-26', '2026-07-27'), 'tomorrow');
      expect(relativeDays('2026-07-26', '2026-07-25'), 'yesterday');
      expect(relativeDays('2026-07-26', '2026-08-05'), 'in 10 days');
      expect(relativeDays('2026-07-26', '2026-07-16'), '10 days ago');
    });
  });
}
