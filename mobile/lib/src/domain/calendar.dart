/// Calendar-date arithmetic on `YYYY-MM-DD` strings.
///
/// NFR-5 and O-11 make every business date a calendar date in the operating timezone (AWST).
/// A phone knows neither that timezone nor the admin date override, so this library never
/// constructs a local `DateTime` from a business date and never calls `DateTime.now()` for
/// "today" — that comes from the server, through the sync payload's `serverToday`.
///
/// The failure this avoids is not theoretical. `DateTime.parse('2026-08-01')` yields local
/// midnight; on a vessel whose phone is set to UTC while the business runs on AWST, the day
/// arithmetic disagrees with the server for eight hours out of every twenty-four, silently, and
/// only for some viewers. This is the same rule and the same reasoning as
/// `admin-web/src/domain/dates.ts`.
library;

final RegExp _isoDate = RegExp(r'^\d{4}-\d{2}-\d{2}$');

/// Days since the epoch for a `YYYY-MM-DD` date, computed in UTC so no zone can shift it.
int epochDay(String date) {
  if (!_isoDate.hasMatch(date)) {
    throw ArgumentError.value(date, 'date', 'Expected a YYYY-MM-DD calendar date');
  }
  final year = int.parse(date.substring(0, 4));
  final month = int.parse(date.substring(5, 7));
  final day = int.parse(date.substring(8, 10));
  return DateTime.utc(year, month, day).millisecondsSinceEpoch ~/ Duration.millisecondsPerDay;
}

/// Whole days from [from] to [to]; negative when [to] is earlier.
int daysBetween(String from, String to) => epochDay(to) - epochDay(from);

/// True when [date] falls within `[from, to]`, both ends included.
bool isWithin(String date, String from, String to) {
  final day = epochDay(date);
  return day >= epochDay(from) && day <= epochDay(to);
}

const _months = [
  'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
  'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
];

/// Day-first, as the operating region writes dates: `1 Aug 2026`.
String formatDate(String date) {
  if (!_isoDate.hasMatch(date)) return date;
  final day = int.parse(date.substring(8, 10));
  final month = _months[int.parse(date.substring(5, 7)) - 1];
  return '$day $month ${date.substring(0, 4)}';
}

/// `1–28 Aug 2026`, collapsing the parts the two ends share.
String formatDateRange(String from, String to) {
  if (!_isoDate.hasMatch(from) || !_isoDate.hasMatch(to)) return '$from – $to';
  final sameYear = from.substring(0, 4) == to.substring(0, 4);
  final sameMonth = sameYear && from.substring(5, 7) == to.substring(5, 7);

  if (sameMonth) {
    return '${int.parse(from.substring(8, 10))}–${formatDate(to)}';
  }
  if (sameYear) {
    final fromDay = int.parse(from.substring(8, 10));
    final fromMonth = _months[int.parse(from.substring(5, 7)) - 1];
    return '$fromDay $fromMonth – ${formatDate(to)}';
  }
  return '${formatDate(from)} – ${formatDate(to)}';
}

const _weekdays = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

/// `Mon 3 Aug` — a date a crew member has to *plan* around rather than merely read.
///
/// The weekday is the point: "3 Aug" does not tell someone whether a two-day course eats their
/// weekend, and it is the first thing they check. Computed from the epoch day rather than a
/// `DateTime`, for the same reason as everything else here — 1970-01-01 was a Thursday, and that
/// is true in every timezone.
String formatDateWeekday(String date, {bool withYear = false, bool withMonth = true}) {
  if (!_isoDate.hasMatch(date)) return date;
  final weekday = _weekdays[(epochDay(date) + 4) % 7];
  final day = int.parse(date.substring(8, 10));
  final month = _months[int.parse(date.substring(5, 7)) - 1];
  return [
    '$weekday $day',
    if (withMonth) month,
    if (withYear) date.substring(0, 4),
  ].join(' ');
}

/// `Mon 3 – Tue 4 Aug`, collapsing the month when both ends share it, and `Thu 6 Aug` when the
/// two ends are the same day.
String formatWeekdayRange(String from, String to) {
  if (from == to) return formatDateWeekday(to);
  if (!_isoDate.hasMatch(from) || !_isoDate.hasMatch(to)) return '$from – $to';
  final sameMonth = from.substring(0, 7) == to.substring(0, 7);
  return '${formatDateWeekday(from, withMonth: !sameMonth)} – ${formatDateWeekday(to)}';
}

final RegExp _isoDateInProse = RegExp(r'\d{4}-\d{2}-\d{2}');

/// Rewrites every `YYYY-MM-DD` inside a sentence into the day-first form crew read.
///
/// For server-composed prose only — notification bodies, today. `NotificationService` builds
/// those strings with raw `LocalDate.toString()`, so an expiry warning arrives saying "expires on
/// 2026-08-14", and **a crew member must never be shown an ISO date**: 08-14 and 14-08 are two
/// different days to two people in the same crew room, and the format is what tells them apart.
///
/// This is a date *format* substitution and nothing else — it cannot alter a word, a code or a
/// number that is not shaped like a calendar date. The real fix is for the server to compose in
/// the display format, at which point this becomes a no-op and can be deleted; it is on the
/// backend handoff. Until then the screen is right rather than waiting to be.
String humaniseDates(String prose) =>
    prose.replaceAllMapped(_isoDateInProse, (match) => formatDate(match.group(0)!));

/// "in 12 days" / "yesterday" / "today", relative to the server's business date.
///
/// Deliberately takes [today] rather than reading a clock: the whole point of this library is
/// that the device does not decide what day it is.
String relativeDays(String today, String target) {
  final delta = daysBetween(today, target);
  if (delta == 0) return 'today';
  if (delta == 1) return 'tomorrow';
  if (delta == -1) return 'yesterday';
  if (delta > 0) return 'in $delta days';
  return '${-delta} days ago';
}
