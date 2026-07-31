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

/// Where a one-tap answer has got to, across both halves of its record.
///
/// The device knows two things the server cannot — that an answer is still sitting in the outbox,
/// and that the server refused it — and the server knows the one thing the device cannot, which is
/// what a coordinator decided. [Answer] is the union, and this is its state.
///
/// Five, and each is a different sentence to the person holding the phone: *your phone still has
/// it*, *the office has it*, *somebody is dealing with it*, *the office said no*, *it did not
/// send*. Anything that blurred two of those would be telling a crew member to stop worrying about
/// something nobody has looked at.
enum AnswerState { queued, sent, actioned, dismissed, failed }

String answerStateLabel(AnswerState state) => switch (state) {
  AnswerState.queued => 'Queued — sends when you have signal',
  AnswerState.sent => 'Sent to the office',
  AnswerState.actioned => 'The office has it in hand',
  AnswerState.dismissed => "The office couldn't act on this",
  AnswerState.failed => "Couldn't send",
};

/// A short phrase for an answer whose device-side record has already been pruned.
///
/// Once the office confirms an answer the local intent is deleted — the server's row says
/// everything, and two rows about one tap is one too many — so the summary the crew member first
/// saw goes with it. This is the fallback, and it is deliberately terse: the line always renders
/// inside the requirement's own card, so naming the requirement again would be noise.
String answerSummary(String kind) => switch (kind) {
  IntentKind.courseBooked => 'Course booked',
  IntentKind.helpNeeded => 'Asked for help',
  IntentKind.readingConfirmed => 'Confirmed the reading',
  IntentKind.seatRequest => 'Asked for a seat',
  IntentKind.waitlist => 'Joined the waitlist',
  IntentKind.exemptionRequest => 'Exemption requested',
  IntentKind.attestation => 'Signed off',
  IntentKind.nudge => 'Nudged',
  _ => 'Sent',
};

/// One answer, however much of its record exists.
///
/// Built from the device's outbox record, the server's statement, or both — see `answersFrom` in
/// `ui/app_state.dart`. Where both exist the server's wins, because it is the only one that can
/// have a decision on it.
class Answer {
  const Answer({
    required this.opId,
    required this.kind,
    required this.state,
    required this.summary,
    this.requirementId,
    this.subjectRef,
    this.detail,
  });

  final String opId;
  final String kind;
  final AnswerState state;

  /// One line, already written — from the intent that raised it, or [answerSummary].
  final String summary;
  final int? requirementId;

  /// The course option a seat request / waitlist named, null for the other kinds. MOB-8 keys its
  /// per-date "Requested" state on this, which is what survives the intent being pruned (#15).
  final String? subjectRef;

  /// The reason, in whoever's words they are: the server's rejection for a failure, the
  /// coordinator's own note for a decision.
  final String? detail;

  bool get failed => state == AnswerState.failed;

  /// True while the answer is still worth anything.
  ///
  /// This is what a screen asks before deciding whether the question has been answered — and the
  /// two false cases are the point. A failed answer never reached the office, and a **dismissed**
  /// one was looked at and turned down, so in both cases the ask is live again and the button
  /// comes back. That mirrors the server exactly: dismissing a booking also restarts the expiry
  /// reminders it had silenced.
  bool get stands => state != AnswerState.failed && state != AnswerState.dismissed;
}

/// The reason under an answer, in words a crew member can act on.
///
/// A coordinator's decision note is already written for the crew member (ADM-11's form says so)
/// and passes through verbatim. A *developer* string is not: "Unsupported operation type
/// 'evidence.reading'" names backend work, and on a phone at sea it reads as "something about my
/// certificate is broken" (#21). The known machine shapes are translated; anything unrecognised
/// still shows, because a failure with no cause at all is worse.
String crewFacingDetail(String detail) {
  if (detail.startsWith('Unsupported operation type')) {
    return "The office's system can't take this kind of update yet. "
        'Your other updates are unaffected.';
  }
  return detail;
}
