/// Presentation of the engine's Appendix A enumerations — **labels, tones and icons only**.
///
/// AUTH-1: no compliance logic lives in a client. Cell states, roll-ups and expiry impacts are
/// the server's answers, arriving pre-computed in the sync payload; this file decides what tone
/// "expiring" wears and nothing else. The moment something here starts deciding what a state
/// *is*, the rule is broken — and on mobile it would be broken offline, where nobody can see it
/// happen.
///
/// The mapping is the admin console's, ported from `admin-web/src/domain/enums.ts` state for
/// state. Two clients that disagree about what colour a gap is are two clients a coordinator and
/// a crew member cannot talk to each other over.
library;

import 'package:flutter/widgets.dart';
import 'package:phosphor_icons/phosphor_icons.dart';

import '../ui/nocturne.dart';

/// §5.1 cell states, worst first. Mirrors the engine's own gap-report ordering, so a
/// client-sorted view and the server's worklist agree.
const cellStateOrder = <String>[
  'gap',
  'expiring',
  'unknown',
  'review',
  'pending',
  'exempt',
  'ok',
  'quota_only',
  'recommended',
  'na',
];

/// Where an unrecognised state sorts: first, so a state this build has never heard of surfaces
/// rather than hiding at the bottom of a list.
///
/// This is the one place the mobile app deliberately differs from the console, which sorts an
/// unknown state last. The console is read by someone who can go and look the state up; a crew
/// member offline cannot, so it is put where it will be seen.
int cellStateRank(String state) {
  final index = cellStateOrder.indexOf(state);
  return index < 0 ? -1 : index;
}

String cellStateLabel(String state) => switch (state) {
  'ok' => 'OK',
  'gap' => 'Gap',
  'expiring' => 'Expiring',
  'unknown' => 'Unknown',
  'review' => 'Review',
  'pending' => 'Pending',
  'exempt' => 'Exempt',
  'quota_only' => 'Quota only',
  'recommended' => 'Recommended',
  'na' => 'n/a',
  // Shown verbatim rather than mapped to something reassuring: a state the app does not
  // know about must not render as "OK".
  _ => state,
};

/// The tone a cell state wears.
///
/// `pending`, `unknown` and `review` take `caution` rather than `warning` on purpose: they are an
/// *absence* of information rather than a verdict, and a requirement nobody has established
/// anything about must not read to a crew member as a failure they caused.
Tone cellStateTone(String state) => switch (state) {
  'gap' => Tone.critical,
  'expiring' => Tone.warning,
  'unknown' || 'review' || 'pending' => Tone.caution,
  'exempt' => Tone.neutral,
  'ok' => Tone.good,
  'quota_only' || 'recommended' || 'na' => Tone.muted,
  _ => Tone.neutral,
};

/// True for the states a crew member should act on. Drives the "needs attention" grouping, the
/// MOB-0 asked-of-you list and the readiness count.
bool needsAttention(String state) =>
    state == 'gap' || state == 'expiring' || state == 'unknown' || state == 'review';

/// Appendix A `qualification_holding.status`.
String holdingStatusLabel(String status) => switch (status) {
  'held_expiry' => 'Held, expires',
  'held_perpetual' => 'Held',
  'not_held' => 'Not held',
  'unknown' => 'Unknown',
  _ => status,
};

/// Appendix A `evidence_document.verification_status` — MOB-4's visible queue state.
String submissionStatusLabel(String status) => switch (status) {
  'pending_extraction' => 'Processing',
  'pending_review' => 'Awaiting review',
  'auto_accepted' => 'Accepted',
  'verified' => 'Verified',
  'rejected' => 'Rejected',
  _ => status,
};

Tone submissionStatusTone(String status) => switch (status) {
  'auto_accepted' || 'verified' => Tone.good,
  'rejected' => Tone.critical,
  'pending_review' => Tone.caution,
  _ => Tone.muted,
};

String notificationKindLabel(String kind) => switch (kind) {
  'expiry_warning' => 'Expiry',
  'assignment_added' => 'Assignment',
  'assignment_removed' => 'Assignment',
  'assignment_changed' => 'Assignment',
  'requirement_added' => 'Requirement',
  'evidence_received' => 'Submission',
  'evidence_verified' => 'Submission',
  'evidence_rejected' => 'Submission',
  _ => 'Notice',
};

IconData notificationKindIcon(String kind) => switch (kind) {
  'expiry_warning' => PhosphorIconsRegular.clockCountdown,
  'assignment_added' ||
  'assignment_removed' ||
  'assignment_changed' => PhosphorIconsRegular.calendarPlus,
  'requirement_added' => PhosphorIconsRegular.listChecks,
  'evidence_verified' => PhosphorIconsRegular.checkCircle,
  'evidence_rejected' => PhosphorIconsRegular.warningCircle,
  'evidence_received' => PhosphorIconsRegular.cloudCheck,
  _ => PhosphorIconsRegular.bell,
};

/// The icon's colour. Only the ones that carry urgency are tinted; the rest stay neutral, so a
/// tinted glyph in the list means something rather than being decoration.
Color notificationKindColour(String kind) => switch (kind) {
  'expiry_warning' => Nocturne.warningText,
  'evidence_rejected' => Nocturne.criticalText,
  'evidence_verified' => Nocturne.goodText,
  _ => Nocturne.neutral400,
};

/// The inline action an alert carries, or null when it only reports.
///
/// MOB-4's rule, and the one the shipped build breaks: **every alert that implies an action must
/// offer it inline.** An alert that says a certificate is expiring and then leaves the crew
/// member to find the right screen is a notification that has done half its job.
String? notificationActionLabel(String kind) => switch (kind) {
  'expiry_warning' => 'Submit evidence',
  'evidence_rejected' => 'Send another',
  'requirement_added' => 'See what is required',
  _ => null,
};

/// MOB-0's card title: the requirement, phrased as the thing being asked.
///
/// A verb per state, and nothing more — the mapping is as fixed as the labels above it. "Sea
/// Survival" is a noun a crew member has to work out what to do with; "Renew Sea Survival" is the
/// ask. The verb comes from the server's state, so it can never contradict the tag beside it.
String askTitle(String state, String title) => switch (state) {
  'expiring' => 'Renew $title',
  'gap' => 'Get $title',
  'unknown' => 'Confirm $title',
  'review' => '$title needs a look',
  _ => title,
};

/// Requirement category codes (Appendix A: QL · VS · PS · MS · CS · HR · PT · VI · PI).
///
/// Shown raw, deliberately. Nothing in the spec says what they expand to, and inventing an
/// expansion would put a confident wrong label in front of people who know the right one. Same
/// position as the admin SPA takes; it is a question for the client.
String categoryLabel(String category) => category;
