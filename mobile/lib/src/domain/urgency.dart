/// The one function that decides how loud a deadline is allowed to be.
///
/// The handoff asks for exactly this: a single mapping from days-remaining and the swing window
/// to `{clear, soon, blocking}`, driving left-mark colour, tag colour, progress fill and ordering.
/// Having one is not tidiness — three screens each deciding independently what "urgent" means is
/// how a certificate ends up amber on Home and green on the list.
///
/// **This is presentation, not evaluation.** The compliance answer is the server's `state`, which
/// arrives pre-computed in the sync payload and is the *first* input here; the only thing counted
/// on the device is days, and only ever against the payload's `serverToday` (§7.6, NFR-5, O-11).
/// Nothing below can promote a cell the server called `ok` into a gap, or demote a `gap` — it can
/// only decide whether an already-known problem is drawn in amber or in red.
library;

import 'dart:ui';

import '../ui/nocturne.dart';
import 'calendar.dart';
import 'states.dart';

/// How close a deadline is, in the only three steps the design draws.
enum Urgency {
  /// Nothing to do, or nothing due inside the window that matters.
  clear,

  /// Due, and due before the swing ends.
  soon,

  /// Already lapsed, or mandatory and not held. The thing that stops a person sailing.
  blocking,
}

/// The default `expiry.lead-days` horizon, mirrored from the server's own default
/// (`Planning.DEFAULT_EXPIRY_LEAD_DAYS`).
///
/// Mirrored rather than read, for now, because the sync payload does not carry it — the live
/// value is ADM-10 configuration and only the console can see it. It decides one presentation
/// thing: how full the progress track under a countdown is drawn. A stale horizon moves a bar and
/// nothing else. Carrying it in the payload is on the backend handoff.
const expiryLeadDaysDefault = 90;

/// The urgency of one requirement.
///
/// [state] is the server's §5.1 cell state and dominates: a `gap` is blocking whatever the dates
/// say, because a certificate you do not hold has no expiry to count down to. Dates only refine
/// what the state already established.
Urgency urgencyFor({required String state, String? expiry, String? today, String? swingTo}) {
  // The server's verdict first. `gap` is mandatory-and-not-held — there is no deadline to be
  // early for.
  if (state == 'gap') return Urgency.blocking;

  if (expiry != null && today != null) {
    final left = daysBetween(today, expiry);
    if (left <= 0) return Urgency.blocking;

    // "Before the swing ends" is the question a crew member is actually asking, and it is what
    // the engine's own `expiring` state means (§5.1). Where the app knows the window it uses it
    // rather than a fixed number of days.
    if (swingTo != null && daysBetween(expiry, swingTo) >= 0) return Urgency.soon;
  }

  if (needsAttention(state)) return Urgency.soon;
  return Urgency.clear;
}

/// The 2px left mark, the progress fill and the team bar all take this colour.
Color urgencyMark(Urgency urgency) => switch (urgency) {
  Urgency.blocking => Nocturne.markRed,
  Urgency.soon => Nocturne.markAmber,
  Urgency.clear => Nocturne.markGreen,
};

Tone urgencyTone(Urgency urgency) => switch (urgency) {
  Urgency.blocking => Tone.critical,
  Urgency.soon => Tone.warning,
  Urgency.clear => Tone.good,
};

/// How much of the warning window has already gone, 0–1.
///
/// Drawn as a fill because a bare "18 days" means nothing without knowing what it is 18 days out
/// of. Returns 1 for something already lapsed — the track is full, there is no time left in it.
double leadElapsed(int daysRemaining, {int leadDays = expiryLeadDaysDefault}) {
  if (leadDays <= 0) return 1;
  if (daysRemaining <= 0) return 1;
  if (daysRemaining >= leadDays) return 0;
  return (leadDays - daysRemaining) / leadDays;
}

/// The countdown a card leads with — `18 DAYS LEFT`, `LAPSED 16 DAYS AGO`, `DUE TODAY`.
///
/// Never an ISO date, and never a bare number: the unit is always spelled out, because "14/08"
/// is read as two different dates on two sides of the same crew room.
String countdownLabel(String today, String target) {
  final left = daysBetween(today, target);
  if (left == 0) return 'due today';
  if (left == 1) return '1 day left';
  if (left > 1) return '$left days left';
  if (left == -1) return 'lapsed yesterday';
  return 'lapsed ${-left} days ago';
}
