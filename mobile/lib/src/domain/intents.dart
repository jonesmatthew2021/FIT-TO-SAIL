/// The crew member's one-tap answers: what they are called on the wire, and how a row reports
/// one that is still in flight.
///
/// Every kind here is an *answer*, never a decision. "I have booked the course" tells the office
/// where a person is; it does not move the requirement to compliant, and nothing in this app can
/// (§7.5, AUTH-1). What the office does with the answer arrives back down the sync payload like
/// every other server-owned fact.
///
/// **Two of the eight are implemented server-side** — `requirement.progress` and
/// `requirement.help`, which land as a `crew_statement` row and a Crew Coordinator notification.
/// Neither moves a holding or a cell state, which is the whole point of calling them statements.
///
/// The other six are queued exactly the same way and come back `rejected` with
/// `Unsupported operation type '<kind>'`, which the row then shows as a failure the crew member can
/// retry. That is the intended state, not an oversight: the screens are built, the operations are
/// named, and `docs/handoff/mobile-crew-app-backend.md` is the list of what has to exist
/// server-side before each of them lands. The first two needed **no change here at all** — the
/// payload this file already sends was the payload the server grew a reader for.
library;

/// Wire names for the operations the new screens raise. Kept as constants rather than an enum
/// because they cross the network, and a renamed enum value that silently changes a wire string
/// is a bug nobody sees until an operation starts being rejected.
abstract final class IntentKind {
  /// MOB-5 / MOB-2: "I have booked the course." Moves the requirement to *in progress* in the
  /// office's view so the crew member stops being chased.
  static const courseBooked = 'requirement.progress';

  /// MOB-5 / MOB-2 / MOB-0: "I need help arranging it." Raises a task for the coordinator.
  /// Explicitly consequence-free, and the screen says so.
  static const helpNeeded = 'requirement.help';

  /// MOB-7: the fields as the crew member confirmed or corrected them, against a submission
  /// already registered by `evidence.submit`. Separate from the submission because the bytes and
  /// the reading arrive at different times — the document uploads at sea, the reading is checked
  /// when the crew member next looks at their phone.
  static const readingConfirmed = 'evidence.reading';

  /// MOB-8: a seat on a specific course date.
  static const seatRequest = 'course.seat_request';

  /// MOB-8: the waitlist row.
  static const waitlist = 'course.waitlist';

  /// MOB-10: an exemption request against a requirement, with its reason and the searches
  /// already attached.
  static const exemptionRequest = 'register.exemption_request';

  /// MOB-9: the pre-sail declaration, signed.
  static const attestation = 'attestation.sign_off';

  /// MOB-11: a supervisor nudging a member of their watch. Logged, and visible to the person
  /// nudged — a nudge nobody can see the origin of is a way to harass someone quietly.
  static const nudge = 'team.nudge';
}

/// What a queued answer says about itself on the row that raised it.
///
/// Three states and no fourth. There is deliberately nothing that means "probably sent": a crew
/// member deciding whether to chase the office by radio needs to know which side of the
/// connection their answer is sitting on.
String intentStateLabel(String state) => switch (state) {
  'queued' => 'Queued — sends when you have signal',
  'sent' => 'Sent to the office',
  'failed' => "Couldn't send",
  _ => state,
};
