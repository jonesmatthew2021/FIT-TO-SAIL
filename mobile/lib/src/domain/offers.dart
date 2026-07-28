/// The shapes the new screens render, for facts the sync payload does not carry yet.
///
/// Every model here is read-only data the *server* owns and does not send: the courses that fit
/// before an expiry, what the §8 pipeline read out of a document, a supervisor's watch, and the
/// two motivational credits on Home. The screens are built against these types and behave
/// correctly with none of them present — a course list with no courses is an empty state, a
/// reading with nothing read is MOB-7's third confidence level, and Home without credits simply
/// does not draw the tiles.
///
/// That is the position this codebase already takes elsewhere and the reason it takes it: a
/// screen that invents a number is worse than a screen that omits one, because the invented
/// number is indistinguishable from a real one and gets acted on. What each of these needs from
/// the backend is enumerated in `docs/handoff/mobile-crew-app-backend.md`.
library;

import 'calendar.dart';

/// MOB-0's ring: how many of the swing's requirements are answered.
///
/// Counted from the server's own evaluated cells — the app groups the answers it was given and
/// does not produce one of its own. Which states count as ready is the same `needsAttention`
/// grouping the certifications screen has always used for "Needs attention", so the ring and the
/// list can never disagree.
class Readiness {
  const Readiness({required this.ready, required this.total});

  final int ready;
  final int total;

  bool get complete => total > 0 && ready == total;
}

/// MOB-0's two credit tiles — the motivational half of the screen.
///
/// Both need history the device has never been sent: how long a person has gone without sailing
/// short, and how many renewals landed before their expiry. Neither is derivable from a snapshot
/// of the present, so Home draws the tiles only when the payload carries them.
class Credits {
  const Credits({
    required this.streakLabel,
    required this.streakCaption,
    this.secondaryLabel,
    this.secondaryCaption,
  });

  /// "14 months".
  final String streakLabel;

  /// "never sailed short".
  final String streakCaption;

  /// "3 of 4".
  final String? secondaryLabel;

  /// "renewed early".
  final String? secondaryCaption;
}

/// MOB-8: one course date that resolves a requirement before it lapses.
///
/// The screen only ever lists dates that beat the expiry — never a generic catalogue — so
/// [finishes] is load-bearing and the server is expected to have filtered on it already.
class CourseOption {
  const CourseOption({
    required this.id,
    required this.starts,
    required this.finishes,
    required this.provider,
    required this.location,
    required this.durationLabel,
    required this.seats,
    this.note,
    this.recommended = false,
    this.waitlistOnly = false,
  });

  final String id;

  /// Calendar dates, `YYYY-MM-DD`, like every other business date in this app.
  final String starts;
  final String finishes;

  final String provider;
  final String location;

  /// "2 days", "1 day refresher".
  final String durationLabel;

  /// Seats left. Zero means waitlist.
  final int seats;

  /// "Clear of your leave · 11 days before expiry".
  final String? note;

  final bool recommended;
  final bool waitlistOnly;

  String get dateLabel => formatWeekdayRange(starts, finishes);

  String get seatLabel => seats == 1 ? '1 seat' : '$seats seats';
}

/// MOB-7: what the §8 pipeline read out of a submitted document.
///
/// With no LLM provider chosen (§14.5) the extractor reports nothing and every document goes to
/// a human — so today this arrives null and MOB-7 opens at its third confidence level, with the
/// requirement unselected and every field empty. That is not a degraded mode; it is LLM-2's
/// launch posture, and the screen was designed with it as one of three cases.
class ExtractedReading {
  const ExtractedReading({
    required this.confidence,
    this.requirementId,
    this.certificateNumber,
    this.issued,
    this.expires,
    this.fileName,
    this.byteSize,
  });

  /// `high` · `review` · `unmatched`.
  final String confidence;

  final int? requirementId;
  final String? certificateNumber;

  /// `YYYY-MM-DD`.
  final String? issued;
  final String? expires;

  final String? fileName;
  final int? byteSize;

  String get confidenceLabel => switch (confidence) {
    'high' => 'high confidence',
    'review' => 'needs a look',
    _ => 'could not match',
  };
}

/// MOB-11: one member of a supervisor's watch.
///
/// Deliberately thin, and the thinness is the privacy control: a supervisor sees a status and one
/// line of reason, never a document, never a medical detail, never why a certificate lapsed. The
/// device cannot leak what it was never sent, so the rule is enforced by the shape of the
/// endpoint rather than by what this screen chooses to draw (AUTH-2).
class TeamMember {
  const TeamMember({
    required this.sam,
    required this.name,
    required this.worstState,
    this.reason,
    this.inHand = false,
    this.nudgedAt,
  });

  final String sam;
  final String name;

  /// A §5.1 cell state, the worst across everything asked of them.
  final String worstState;

  /// "Work at Heights lapsed 12 Jul · no course booked".
  final String? reason;

  /// Something is already moving — evidence awaiting review, a seat requested. Suppresses the
  /// nudge, because chasing someone who has already acted is how an app gets muted.
  final bool inHand;

  final DateTime? nudgedAt;
}

/// MOB-9: one line of the pre-sail declaration.
///
/// The wording is the design's and is legally load-bearing, so it is not composed on the device
/// from parts. [supporting] is: that line is drawn from the crew member's own record and is the
/// difference between a checkbox and an informed answer.
class Declaration {
  const Declaration({
    required this.id,
    required this.text,
    this.supporting,
    this.satisfied = false,
  });

  final String id;
  final String text;

  /// "6 requirements · 5 held, 1 renewing".
  final String? supporting;

  /// Already true from the record, so it arrives ticked. The crew member is confirming it, not
  /// asserting it from nothing.
  final bool satisfied;
}
