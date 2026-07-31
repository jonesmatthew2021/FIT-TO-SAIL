/// The four screens a crew member answers on: MOB-5 the one-tap update, MOB-8 course booking,
/// MOB-10 an exemption request, MOB-9 the pre-sail sign-off.
///
/// Every one of them ends in the same place — an entry on the outbound queue (§7.6) that says
/// what the crew member told the office. **None of them writes a holding, a register record or a
/// compliance state** (§7.5, AUTH-1). What the office decides comes back down the sync payload
/// with everything else.
///
/// Three of the four are built against server-owned data the payload does not carry yet, and
/// each degrades to an honest empty state rather than to invented content: MOB-8 with no course
/// catalogue lists no courses and says so, and MOB-9's supporting facts come from the crew
/// member's real evaluated cells or are omitted. The wording throughout is the design handoff's
/// and is deliberately not reworded here — on these screens the copy *is* the specification, and
/// "Nothing is recorded against you for asking for help" is doing more work than any control on
/// the page.
library;

import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:phosphor_icons/phosphor_icons.dart';

import '../data/local_store.dart';
import '../domain/calendar.dart';
import '../domain/intents.dart';
import '../domain/offers.dart';
import '../domain/states.dart';
import '../domain/urgency.dart';
import 'app_state.dart';
import 'evidence_screens.dart';
import 'nocturne.dart';
import 'screens.dart';
import 'widgets.dart';

// ---------------------------------------------------------------------------
// Entry points
// ---------------------------------------------------------------------------

void openOneTapUpdate(BuildContext context, AppState state, CertificationRow row) {
  Navigator.of(context).push(
    MaterialPageRoute<void>(
      builder: (_) => OneTapUpdateScreen(state: state, row: row),
    ),
  );
}

void openCourseBooking(BuildContext context, AppState state, CertificationRow row) {
  Navigator.of(context).push(
    MaterialPageRoute<void>(
      builder: (_) => CourseBookingScreen(state: state, row: row),
    ),
  );
}

void openExemptionRequest(BuildContext context, AppState state, CertificationRow row) {
  Navigator.of(context).push(
    MaterialPageRoute<void>(
      builder: (_) => ExemptionRequestScreen(state: state, row: row),
    ),
  );
}

void openAttestation(
  BuildContext context,
  AppState state, {
  required LocalAssignment assignment,
  required List<CertificationRow> rows,
}) {
  Navigator.of(context).push(
    MaterialPageRoute<void>(
      builder: (_) => AttestationScreen(state: state, assignment: assignment, rows: rows),
    ),
  );
}

// ---------------------------------------------------------------------------
// MOB-5 — One-tap update
// ---------------------------------------------------------------------------

class OneTapUpdateScreen extends StatelessWidget {
  const OneTapUpdateScreen({super.key, required this.state, required this.row});

  final AppState state;
  final CertificationRow row;

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<List<LocalCrewIntent>>(
      stream: state.watchIntentsFor(row.cell.requirementId),
      builder: (context, intentSnapshot) {
        return StreamBuilder<List<LocalCrewStatement>>(
          stream: state.watchStatementsFor(row.cell.requirementId),
          builder: (context, statementSnapshot) => StreamBuilder<List<LocalSubmission>>(
            stream: state.watchSubmissionsFor(row.cell.requirementId),
            builder: (context, submissionSnapshot) => OneTapUpdateView(
              row: row,
              today: state.serverToday,
              swingTo: state.syncState?.standingTo,
              answers: answersFrom(
                intentSnapshot.data ?? const <LocalCrewIntent>[],
                statementSnapshot.data ?? const <LocalCrewStatement>[],
              ),
              submissions: submissionSnapshot.data ?? const <LocalSubmission>[],
              onHaveIt: () => showSendCertificateSheet(
                context: context,
                state: state,
                requirementId: row.cell.requirementId,
                requirementLabel: '${row.code} ${row.title}',
              ),
              // The claim and the browsing are separate on purpose (issue #16): this button
              // states a fact and asks first, because the statement silences the crew member's
              // own reminders on their word alone. Going to look at dates is `onBrowseCourses`,
              // and posts nothing.
              onCourseBooked: () async {
                final confirmed = await confirmCourseBooked(
                  context: context,
                  requirementTitle: row.title,
                );
                if (!confirmed) return;
                await state.answer(
                  kind: IntentKind.courseBooked,
                  summary: 'Course booked for ${row.title}',
                  requirementId: row.cell.requirementId,
                );
              },
              onBrowseCourses: () => openCourseBooking(context, state, row),
              onNeedHelp: () => state.answer(
                kind: IntentKind.helpNeeded,
                summary: 'Asked for help with ${row.title}',
                requirementId: row.cell.requirementId,
              ),
              onExemption: () => openExemptionRequest(context, state, row),
              onRetryIntent: state.retryAnswer,
              onDiscardIntent: state.discardAnswer,
            ),
          ),
        );
      },
    );
  }
}

class OneTapUpdateView extends StatelessWidget {
  const OneTapUpdateView({
    super.key,
    required this.row,
    required this.today,
    this.swingTo,
    this.answers = const <Answer>[],
    this.submissions = const <LocalSubmission>[],
    this.onHaveIt,
    this.onCourseBooked,
    this.onBrowseCourses,
    this.onNeedHelp,
    this.onExemption,
    this.onRetryIntent,
    this.onDiscardIntent,
  });

  final CertificationRow row;
  final String? today;
  final String? swingTo;
  final List<Answer> answers;
  final List<LocalSubmission> submissions;
  final VoidCallback? onHaveIt;
  final VoidCallback? onCourseBooked;

  /// Opens MOB-8's course list, posting nothing — browsing dates is not claiming a booking.
  final VoidCallback? onBrowseCourses;
  final VoidCallback? onNeedHelp;
  final VoidCallback? onExemption;
  final void Function(String opId)? onRetryIntent;
  final void Function(String opId)? onDiscardIntent;

  @override
  Widget build(BuildContext context) {
    final expiry = row.expiry;
    final urgency = urgencyFor(
      state: row.cell.state,
      expiry: expiry,
      today: today,
      swingTo: swingTo,
    );
    // `stands` and not merely "exists": an answer the office dismissed puts the button back, which
    // is the same thing the server does when a dismissal restarts the expiry chasing.
    final booked =
        answers.where((a) => a.kind == IntentKind.courseBooked && a.stands).firstOrNull;
    final helped = answers.where((a) => a.kind == IntentKind.helpNeeded && a.stands).firstOrNull;

    return Scaffold(
      backgroundColor: Nocturne.bg,
      appBar: NocturneNavBar(title: row.title),
      body: Column(
        children: [
          Expanded(
            child: ListView(
              padding: const EdgeInsets.fromLTRB(Nocturne.gutter, 18, Nocturne.gutter, 8),
              children: [
                NCard(
                  elevated: true,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        // countdownLabel, not raw arithmetic: a lapsed certificate reads
                        // "lapsed 6 days ago", never "EXPIRES IN -6 DAYS" (#21).
                        (expiry != null && today != null
                                ? countdownLabel(today!, expiry)
                                : cellStateLabel(row.cell.state))
                            .toUpperCase(),
                        style: NoctType.sectionLabel.copyWith(
                          fontSize: 11,
                          letterSpacing: 1.1,
                          color: toneColours(urgencyTone(urgency)).text,
                        ),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        'Tell the office where you are. One tap is enough — no forms, no email.',
                        style: NoctType.bodyText.copyWith(color: Nocturne.neutral400),
                      ),
                    ],
                  ),
                ),

                const SizedBox(height: 14),
                NButton(
                  label: 'I have the new certificate',
                  icon: PhosphorIconsRegular.certificate,
                  iconSize: 20,
                  variant: NButtonVariant.primary,
                  block: true,
                  alignStart: true,
                  minHeight: 52,
                  onPressed: onHaveIt,
                ),
                const SizedBox(height: 8),
                NButton(
                  label: booked == null ? 'I have booked the course' : 'You told them it is booked',
                  icon: PhosphorIconsRegular.calendarCheck,
                  iconSize: 20,
                  block: true,
                  alignStart: true,
                  minHeight: 52,
                  onPressed: booked == null ? onCourseBooked : null,
                ),
                const SizedBox(height: 8),
                NButton(
                  label: helped == null ? 'I need help arranging it' : 'You have asked for help',
                  icon: PhosphorIconsRegular.chatCircleDots,
                  iconSize: 20,
                  variant: NButtonVariant.ghost,
                  block: true,
                  alignStart: true,
                  minHeight: 52,
                  onPressed: helped == null ? onNeedHelp : null,
                ),
                const SizedBox(height: 8),
                // Navigation, not an answer: the course list posts nothing until a date is
                // picked there. This is how you look before you claim (issue #16).
                NButton(
                  label: 'See course dates',
                  icon: PhosphorIconsRegular.calendarBlank,
                  iconSize: 20,
                  variant: NButtonVariant.ghost,
                  block: true,
                  alignStart: true,
                  minHeight: 52,
                  onPressed: onBrowseCourses,
                ),

                if (answers.isNotEmpty) ...[
                  const SizedBox(height: 14),
                  for (final answer in answers)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: AnswerLine(
                        answer: answer,
                        onRetry: answer.failed && onRetryIntent != null
                            ? () => onRetryIntent!(answer.opId)
                            : null,
                        onDismiss: answer.failed && onDiscardIntent != null
                            ? () => onDiscardIntent!(answer.opId)
                            : null,
                      ),
                    ),
                ],

                const SizedBox(height: 26),
                const SectionLabel('What happens', padding: EdgeInsets.only(bottom: 12)),
                const StepTimeline(
                  steps: [
                    (
                      title: 'Your tap reaches the crew coordinator',
                      detail: 'Instantly, with the swing it affects',
                    ),
                    (
                      title: 'They stop chasing you',
                      detail: 'The requirement moves to In progress',
                    ),
                    (
                      title: 'Send the certificate when it arrives',
                      detail: 'Share it from your email, or photograph it',
                    ),
                  ],
                ),

                const SizedBox(height: 20),
                // The escape hatch, kept quiet and kept last: an exemption is what you ask for
                // when none of the three answers above is true.
                NButton(
                  label: 'No course fits — ask for an exemption',
                  variant: NButtonVariant.ghost,
                  fontSize: 13,
                  onPressed: onExemption,
                ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(Nocturne.gutter, 0, Nocturne.gutter, 8),
            child: SafeArea(
              top: false,
              // Two sentences doing two jobs. The first is a receipt — you have already told us
              // something, here is when. The second removes the fear that asking for help is a
              // black mark, which is the single most common reason a crew member says nothing at
              // all until the certificate has already lapsed.
              child: Text(
                '${_lastUpdate()} '
                'Nothing is recorded against you for asking for help.',
                style: NoctType.meta,
              ),
            ),
          ),
        ],
      ),
    );
  }

  String _lastUpdate() {
    final latest = submissions.firstOrNull;
    if (latest == null) return 'No update from you yet.';
    return 'Last update from you: ${ago(latest.submittedAt)}, evidence sent.';
  }
}

// ---------------------------------------------------------------------------
// MOB-8 — Course booking
// ---------------------------------------------------------------------------

class CourseBookingScreen extends StatelessWidget {
  const CourseBookingScreen({super.key, required this.state, required this.row});

  final AppState state;
  final CertificationRow row;

  @override
  Widget build(BuildContext context) {
    // Both halves, merged — never intents alone. The intent is pruned the moment the server's
    // statement lands, so a card keyed on intents forgets a *successful* request, re-arms its
    // button, and a second tap mints a duplicate ADM-11 row (issue #15).
    return StreamBuilder<List<LocalCrewIntent>>(
      stream: state.watchIntentsFor(row.cell.requirementId),
      builder: (context, intentSnapshot) => StreamBuilder<List<LocalCrewStatement>>(
        stream: state.watchStatementsFor(row.cell.requirementId),
        builder: (context, statementSnapshot) => CourseBookingView(
          row: row,
          options: state.courseOptionsFor(row.cell.requirementId),
          today: state.serverToday,
          swingTo: state.syncState?.standingTo,
          answers: answersFrom(
            intentSnapshot.data ?? const <LocalCrewIntent>[],
            statementSnapshot.data ?? const <LocalCrewStatement>[],
          ),
          onRequestSeat: (option) => state.answer(
            kind: option.waitlistOnly ? IntentKind.waitlist : IntentKind.seatRequest,
            summary: option.waitlistOnly
                ? 'Waitlisted for ${option.dateLabel}'
                : 'Seat requested for ${option.dateLabel}',
            requirementId: row.cell.requirementId,
            subjectRef: option.id,
            payload: {'starts': option.starts, 'finishes': option.finishes},
          ),
          onOtherDates: () => state.answer(
            kind: IntentKind.helpNeeded,
            summary: 'Asked about other dates for ${row.title}',
            requirementId: row.cell.requirementId,
          ),
        ),
      ),
    );
  }
}

class CourseBookingView extends StatelessWidget {
  const CourseBookingView({
    super.key,
    required this.row,
    required this.options,
    required this.today,
    this.swingTo,
    this.answers = const <Answer>[],
    this.onRequestSeat,
    this.onOtherDates,
  });

  final CertificationRow row;
  final List<CourseOption> options;
  final String? today;
  final String? swingTo;

  /// The merged device + server record (`answersFrom`), never intents alone — the intent is
  /// pruned when the server's statement lands, and only the merge keeps a successful request
  /// remembered (issue #15).
  final List<Answer> answers;
  final void Function(CourseOption option)? onRequestSeat;
  final VoidCallback? onOtherDates;

  @override
  Widget build(BuildContext context) {
    final expiry = row.expiry;
    // Every answer naming an option is shown (a failed one must not vanish silently); whether
    // the button re-arms is `stands`, so a dismissal or a failure puts "Request seat" back —
    // the same way the office resumes its own chasing.
    final requested = {
      for (final answer in answers)
        if (answer.subjectRef != null) answer.subjectRef!: answer,
    };

    return Scaffold(
      backgroundColor: Nocturne.bg,
      appBar: NocturneNavBar(title: 'Book ${row.title}'),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(Nocturne.gutter, 16, Nocturne.gutter, 24),
        children: [
          NCard(
            elevated: true,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text.rich(
                  TextSpan(
                    children: [
                      TextSpan(text: _intro(options.length)),
                      if (expiry != null) ...[
                        const TextSpan(text: ' before your certificate lapses on '),
                        TextSpan(
                          text: formatDate(expiry),
                          style: const TextStyle(color: Nocturne.text),
                        ),
                      ],
                      // Only promised when there is something to pick. "You only pick a date"
                      // above an empty list is the screen contradicting itself.
                      TextSpan(
                        text: options.isEmpty ? '.' : '. The company pays; you only pick a date.',
                      ),
                    ],
                  ),
                  style: NoctType.bodyText,
                ),
                if (swingTo != null) ...[
                  const SizedBox(height: 10),
                  Row(
                    children: [
                      const Spacer(),
                      Text('Ashore until ${formatDate(swingTo!)}', style: NoctType.meta),
                    ],
                  ),
                ],
              ],
            ),
          ),

          // "Fits before the expiry" is only true when there is one. A gap or an unconfirmed
          // holding has no deadline to beat, and the server offers every future date against it —
          // a heading claiming otherwise would describe a filter that did not run.
          SectionLabel(
            expiry == null ? 'Dates you could attend' : 'Fits before the expiry',
            padding: const EdgeInsets.only(top: 20, bottom: 8),
          ),

          if (options.isEmpty)
            // Not a failure, and not styled as one. There *is* a catalogue now, so the honest
            // reading of an empty list is different from what it used to be: every date it holds
            // either finishes too late or falls inside a swing this person is aboard for. That is
            // a real and common answer — for anybody rostered across a whole swing, an expiring
            // certificate can never have an attendable date — and it is precisely the case the
            // exemption request exists for. The office still has to be asked either way.
            NCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('No dates that would work', style: NoctType.cardTitleSm),
                  const SizedBox(height: 4),
                  Text(
                    expiry == null
                        ? 'Nothing is currently scheduled that you could get to. Ask the office '
                              'and they will find one.'
                        : 'Every course on the calendar either finishes too late or runs while '
                              'you are at sea. Ask the office — they can look further afield, or '
                              'raise an exemption for this swing.',
                    style: NoctType.cardBody,
                  ),
                  const SizedBox(height: 12),
                  NButton(
                    label: 'Ask the office to arrange it',
                    icon: PhosphorIconsRegular.chatCircleDots,
                    variant: NButtonVariant.primary,
                    block: true,
                    onPressed: onOtherDates,
                  ),
                ],
              ),
            )
          else
            for (final option in options) ...[
              _CourseCard(
                option: option,
                answer: requested[option.id],
                onRequestSeat: onRequestSeat == null ? null : () => onRequestSeat!(option),
              ),
              const SizedBox(height: 8),
            ],

          const SizedBox(height: 10),
          const InfoBand(
            text:
                'A seat request tells the office you are handling it. The provider sends the '
                'certificate straight to Attest.',
          ),

          if (options.isNotEmpty) ...[
            const SizedBox(height: 16),
            Center(
              child: NButton(
                label: 'Other dates or providers',
                variant: NButtonVariant.ghost,
                fontSize: 12.5,
                onPressed: onOtherDates,
              ),
            ),
          ],
        ],
      ),
    );
  }

  static String _intro(int count) => switch (count) {
    0 => 'No courses are listed',
    1 => 'One course finishes',
    _ => '$count courses finish',
  };
}

class _CourseCard extends StatelessWidget {
  const _CourseCard({required this.option, this.answer, this.onRequestSeat});

  final CourseOption option;

  /// The standing answer naming this option, from the merged view — null when nothing stands.
  final Answer? answer;
  final VoidCallback? onRequestSeat;

  @override
  Widget build(BuildContext context) {
    // A single-line row for a waitlist entry: it is the option you take when the others are gone,
    // and it should not compete with them for height.
    if (option.waitlistOnly) {
      return NCard(
        padding: const EdgeInsets.fromLTRB(15, 13, 15, 13),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(option.dateLabel, style: NoctType.listPrimarySm),
                  const SizedBox(height: 2),
                  Text(
                    [option.location, if (option.note != null) option.note!].join(' · '),
                    style: NoctType.listSecondary,
                  ),
                ],
              ),
            ),
            const SizedBox(width: 10),
            NButton(
              label: answer?.stands ?? false ? 'Waitlisted' : 'Waitlist',
              variant: NButtonVariant.ghost,
              fontSize: 12.5,
              onPressed: answer?.stands ?? false ? null : onRequestSeat,
            ),
          ],
        ),
      );
    }

    return NCard(
      leftMark: option.recommended ? Nocturne.accent : null,
      padding: EdgeInsets.fromLTRB(option.recommended ? 13 : 15, 13, 15, 13),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(option.dateLabel, style: NoctType.cardTitleSm),
                    const SizedBox(height: 3),
                    Text(
                      [option.provider, option.location, option.durationLabel].join(' · '),
                      style: NoctType.listSecondary,
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 10),
              NTag(
                label: option.seatLabel,
                // One seat left is a fact worth reading as pressure; four is not.
                tone: option.seats <= 1 ? Tone.warning : Tone.good,
              ),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(
                child: Text(
                  option.note ?? '',
                  style: NoctType.listSecondary.copyWith(color: Nocturne.neutral600),
                ),
              ),
              const SizedBox(width: 8),
              NButton(
                label: answer?.stands ?? false ? 'Requested' : 'Request seat',
                variant: option.recommended ? NButtonVariant.primary : NButtonVariant.secondary,
                fontSize: 13,
                onPressed: answer?.stands ?? false ? null : onRequestSeat,
              ),
            ],
          ),
          if (answer != null) ...[
            const SizedBox(height: 8),
            AnswerLine(answer: answer!),
          ],
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// MOB-10 — Exemption request
// ---------------------------------------------------------------------------

/// The reasons a crew member can give. Single-select, and short on purpose: a free-text-only
/// form gets three-word answers, and a Compliance Lead deciding the same day needs to know which
/// of these three situations they are in before they read a word.
const exemptionReasons = <({String id, String label})>[
  (id: 'no_seat', label: 'No seat before the expiry date'),
  (id: 'medical_personal', label: 'Medical or personal reason'),
  (id: 'with_authority', label: 'Renewal is with the issuing authority'),
];

class ExemptionRequestScreen extends StatelessWidget {
  const ExemptionRequestScreen({super.key, required this.state, required this.row});

  final AppState state;
  final CertificationRow row;

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<List<LocalCrewIntent>>(
      stream: state.watchIntentsFor(row.cell.requirementId),
      builder: (context, snapshot) {
        final intents = snapshot.data ?? const <LocalCrewIntent>[];
        return ExemptionRequestView(
          row: row,
          today: state.serverToday,
          swingTo: state.syncState?.standingTo,
          // "Your waitlist request and the course search are attached automatically." The client
          // is what knows they happened, so the client is what attaches them.
          attachedOpIds: [
            for (final intent in intents)
              if (intent.kind == IntentKind.seatRequest ||
                  intent.kind == IntentKind.waitlist ||
                  intent.kind == IntentKind.helpNeeded)
                intent.opId,
          ],
          already: intents.where((i) => i.kind == IntentKind.exemptionRequest).firstOrNull,
          onSend: (reason, note, attachments) async {
            await state.answer(
              kind: IntentKind.exemptionRequest,
              summary: 'Exemption requested for ${row.title}',
              requirementId: row.cell.requirementId,
              payload: {
                'reason': reason,
                'note': note,
                'ccId': state.syncState?.standingCcId,
                'attachedOpIds': attachments,
              },
            );
            if (context.mounted) Navigator.of(context).pop();
          },
        );
      },
    );
  }
}

class ExemptionRequestView extends StatefulWidget {
  const ExemptionRequestView({
    super.key,
    required this.row,
    required this.today,
    required this.onSend,
    this.swingTo,
    this.attachedOpIds = const <String>[],
    this.already,
  });

  final CertificationRow row;
  final String? today;
  final String? swingTo;
  final List<String> attachedOpIds;
  final LocalCrewIntent? already;
  final Future<void> Function(String reason, String? note, List<String> attachments) onSend;

  @override
  State<ExemptionRequestView> createState() => _ExemptionRequestViewState();
}

class _ExemptionRequestViewState extends State<ExemptionRequestView> {
  final _note = TextEditingController();
  String? _reason;
  bool _sending = false;

  @override
  void dispose() {
    _note.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final row = widget.row;
    final expiry = row.expiry;
    final already = widget.already;

    return Scaffold(
      backgroundColor: Nocturne.bg,
      appBar: const NocturneNavBar(title: 'Ask for an exemption'),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(Nocturne.gutter, 16, Nocturne.gutter, 24),
        children: [
          NCard(
            elevated: true,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(row.title, style: NoctType.listPrimary),
                          const SizedBox(height: 2),
                          Text.rich(
                            TextSpan(
                              children: [
                                monoSpan(row.code, fontSize: 11.5, colour: Nocturne.neutral500),
                                TextSpan(text: _lapseLine(expiry)),
                              ],
                            ),
                            style: NoctType.listSecondary,
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 10),
                    NTag(
                      label: cellStateLabel(row.cell.state),
                      tone: cellStateTone(row.cell.state),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                // The gate, in the card rather than in fine print. An exemption is a Compliance
                // Lead's decision on a real risk, and the screen should make a crew member think
                // once before it becomes the easy path.
                Text(
                  'Use this only when no course fits. A Compliance Lead decides — usually the '
                  'same day.',
                  style: NoctType.cardBody.copyWith(color: Nocturne.neutral400),
                ),
              ],
            ),
          ),

          const SectionLabel(
            'Why you cannot renew in time',
            padding: EdgeInsets.only(top: 18, bottom: 10),
          ),
          for (final reason in exemptionReasons) ...[
            ChoiceRow(
              label: reason.label,
              selected: _reason == reason.id,
              onTap: already != null ? null : () => setState(() => _reason = reason.id),
            ),
            const SizedBox(height: 8),
          ],

          const SizedBox(height: 8),
          NField(
            label: 'Anything the reviewer should know',
            child: NTextInput(controller: _note, hintText: 'Optional'),
          ),

          const SizedBox(height: 14),
          InfoBand(
            text: widget.attachedOpIds.isEmpty
                ? 'Your course search is attached automatically — no need to explain it.'
                : 'Your ${widget.attachedOpIds.length == 1 ? 'earlier request' : 'earlier requests'} '
                      'and the course search are attached automatically — no need to explain them.',
          ),

          if (already != null) ...[
            const SizedBox(height: 12),
            AnswerLine(answer: answerFromIntent(already)),
          ],

          const SizedBox(height: 20),
          NButton(
            label: already == null ? 'Send the request' : 'Already requested',
            icon: PhosphorIconsRegular.paperPlaneTilt,
            iconSize: 18,
            variant: NButtonVariant.primary,
            block: true,
            minHeight: 48,
            onPressed: (_reason == null || _sending || already != null) ? null : _send,
          ),
          const SizedBox(height: 8),
          Center(
            child: Text(_consequence(expiry), textAlign: TextAlign.center, style: NoctType.meta),
          ),
        ],
      ),
    );
  }

  String _lapseLine(String? expiry) {
    if (expiry == null) return ' · not held';
    final swingTo = widget.swingTo;
    final midSwing = swingTo != null && daysBetween(expiry, swingTo) >= 0;
    return ' · lapses ${formatDate(expiry)}${midSwing ? ', mid-swing' : ''}';
  }

  String _consequence(String? expiry) => expiry == null
      ? 'Until it is granted this requirement is outstanding.'
      : 'Until it is granted you cannot sail past ${formatDate(expiry)}.';

  Future<void> _send() async {
    setState(() => _sending = true);
    await widget.onSend(
      _reason!,
      _note.text.trim().isEmpty ? null : _note.text.trim(),
      widget.attachedOpIds,
    );
    if (mounted) setState(() => _sending = false);
  }
}

// ---------------------------------------------------------------------------
// MOB-9 — Attestation sign-off
// ---------------------------------------------------------------------------

class AttestationScreen extends StatelessWidget {
  const AttestationScreen({
    super.key,
    required this.state,
    required this.assignment,
    required this.rows,
  });

  final AppState state;
  final LocalAssignment assignment;
  final List<CertificationRow> rows;

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<List<LocalCrewIntent>>(
      stream: state.watchIntents(),
      builder: (context, intentSnapshot) {
        return StreamBuilder<List<LocalAttestation>>(
          stream: state.watchAttestations(),
          builder: (context, attestationSnapshot) {
            // The server's record where there is one, the device's own until it arrives. Only the
            // server's can say *what was ticked* and when it was signed.
            final record = (attestationSnapshot.data ?? const <LocalAttestation>[])
                .where((a) => a.ccId == assignment.ccId)
                .firstOrNull;
            final intent = (intentSnapshot.data ?? const <LocalCrewIntent>[])
                .where((i) => i.kind == IntentKind.attestation && i.subjectRef == assignment.ccId)
                .firstOrNull;

            return AttestationView(
          assignment: assignment,
          declarations: declarationsFor(rows),
          personName: state.person?.name,
          today: state.serverToday,
          attempt: record != null
              ? Answer(
                  opId: record.opId,
                  kind: IntentKind.attestation,
                  state: AnswerState.sent,
                  summary: answerSummary(IntentKind.attestation),
                )
              : (intent == null ? null : answerFromIntent(intent)),
          // What was actually confirmed, from whichever record exists. Re-deriving it from
          // `satisfied` is the bug this replaces: every line the crew member ticked by hand came
          // back empty the next time they opened the screen.
          confirmed: record?.declarations.split('\n').where((id) => id.isNotEmpty).toList() ??
              signedDeclarationsFrom(intent),
          signedLine: record?.signedAtDisplay,
          onRetry: state.retryAnswer,
          onDiscard: state.discardAnswer,
          onAttest: (confirmed) async {
            await state.answer(
              kind: IntentKind.attestation,
              summary: 'Signed off for ${assignment.partnershipAbbrev} ${assignment.ccId}',
              subjectRef: assignment.ccId,
              payload: {'declarations': confirmed, 'assignmentId': assignment.id},
            );
            if (context.mounted) Navigator.of(context).pop();
          },
            );
          },
        );
      },
    );
  }
}

/// The declaration ids inside a queued attestation's own payload.
///
/// Only used before the server's record arrives — offline, or between the tap and the next sync.
/// Returns empty rather than throwing on anything unexpected: a screen that crashed because a
/// payload it wrote itself did not parse would be worse than one that shows no ticks.
List<String> signedDeclarationsFrom(LocalCrewIntent? intent) {
  if (intent == null) return const [];
  try {
    final payload = jsonDecode(intent.payload);
    if (payload is! Map<String, dynamic>) return const [];
    final declarations = payload['declarations'];
    if (declarations is! List) return const [];
    return declarations.whereType<String>().toList();
  } on FormatException {
    return const [];
  }
}

/// The declaration set, with each line's supporting fact drawn from the crew member's own record.
///
/// The wording is the design's; the facts under it are the server's. Nothing here evaluates
/// anything — "5 held, 1 renewing" is a count of states the engine already decided, and where a
/// fact cannot be established from the payload the line simply carries none rather than an
/// approximation.
///
/// The declaration *set* should ultimately be server-owned: what a crew member is asked to attest
/// to is a company policy question with legal weight, not a client string table. Until the
/// payload carries it, this is the handoff's own copy, verbatim.
List<Declaration> declarationsFor(List<CertificationRow> rows) {
  final applicable = rows.where((row) => row.cell.state != 'na').toList(growable: false);
  final held = applicable.where((row) => !row.outstanding).length;
  final renewing = applicable.length - held;

  // "I am medically fit" is supported by the medical requirement's own expiry where the
  // catalogue has one. Matched on the requirement's category rather than its title: a title is
  // free text a client should never pattern-match, a category is an Appendix A enumeration.
  final medical = applicable
      .where((row) => row.category == 'MS' && row.expiry != null && !row.outstanding)
      .firstOrNull;

  return [
    Declaration(
      id: 'records_correct',
      text: 'My certificates are the ones on record',
      // With nothing on record there is no fact to confirm against, and the line says so
      // rather than reading as effortlessly true (#21): "no data" must never present as "all
      // correct" on a declaration with disciplinary weight.
      supporting: applicable.isEmpty
          ? 'Nothing is on record to check against — confirm only if you know it to be true'
          : '${applicable.length} requirements · $held held'
                '${renewing > 0 ? ', $renewing renewing' : ''}',
      satisfied: applicable.isNotEmpty && renewing == 0,
    ),
    Declaration(
      id: 'medically_fit',
      text: 'I am medically fit to sail this swing',
      supporting: medical == null
          ? null
          : '${medical.title} valid to ${formatDate(medical.expiry!)}',
      satisfied: medical != null,
    ),
    const Declaration(
      id: 'nothing_changed',
      text: 'Nothing has changed that affects my fitness or licences',
    ),
  ];
}

class AttestationView extends StatefulWidget {
  const AttestationView({
    super.key,
    required this.assignment,
    required this.declarations,
    required this.onAttest,
    this.personName,
    this.today,
    this.attempt,
    this.confirmed = const <String>[],
    this.signedLine,
    this.onRetry,
    this.onDiscard,
  });

  final LocalAssignment assignment;
  final List<Declaration> declarations;
  final String? personName;
  final String? today;

  /// The signature attempt, in whatever state it reached — **including failed**.
  ///
  /// Named for the attempt rather than for success on purpose. Treating any record of a tap as
  /// "signed" is how a rejected sign-off ended up showing a disabled "Already signed" button with no
  /// way back: the crew member believed they had signed and the office had nothing.
  final Answer? attempt;

  /// The declarations actually confirmed, once there is a signature.
  ///
  /// Passed in rather than kept in the widget's own state, because the widget's state does not
  /// survive the screen closing — and the crew member closes it the moment they sign. Re-deriving
  /// the ticks from `Declaration.satisfied` on the way back in showed a *different* set from the
  /// one signed: every line ticked by hand came back empty.
  final List<String> confirmed;

  /// The signature line, as the **server** wrote it, in the vessel's timezone. Null until the
  /// record syncs back — and deliberately never filled in from the device clock.
  final String? signedLine;

  final Future<void> Function(List<String> confirmed) onAttest;
  final void Function(String opId)? onRetry;
  final void Function(String opId)? onDiscard;

  /// Signed only when the attempt actually stands. A failure is not a signature.
  bool get isSigned => attempt?.stands ?? false;

  @override
  State<AttestationView> createState() => _AttestationViewState();
}

class _AttestationViewState extends State<AttestationView> {
  late Set<String> _confirmed;
  bool _sending = false;

  @override
  void initState() {
    super.initState();
    _confirmed = _initial();
  }

  @override
  void didUpdateWidget(AttestationView old) {
    super.didUpdateWidget(old);
    // A signature arriving from a sync while the screen is open must move the ticks to what was
    // signed. Without this the widget keeps its pre-signature working set, and the boxes disagree
    // with the record the office now holds.
    if (widget.isSigned && !old.isSigned) {
      setState(() => _confirmed = _initial());
    }
  }

  /// The ticks to start from.
  ///
  /// Signed: exactly what was confirmed, from the record. Unsigned: **nothing** (#21). The
  /// supporting fact under each line is still drawn from the crew member's own record — that is
  /// what they check against — but the tick is theirs to make. A declaration with disciplinary
  /// weight that arrives pre-ticked is a declaration people sign without reading, and the first
  /// version of this screen did exactly that.
  Set<String> _initial() => widget.isSigned || widget.confirmed.isNotEmpty
      ? widget.confirmed.toSet()
      // Growable, not `const`: the tap handler mutates this set in place.
      : <String>{};

  @override
  Widget build(BuildContext context) {
    final assignment = widget.assignment;
    final complete = _confirmed.length == widget.declarations.length;
    final attempt = widget.attempt;
    final signed = widget.isSigned;

    return Scaffold(
      backgroundColor: Nocturne.bg,
      appBar: const NocturneNavBar(title: 'Sign off before you sail'),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(Nocturne.gutter, 16, Nocturne.gutter, 22),
        children: [
          NCard(
            elevated: true,
            child: Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '${assignment.partnershipAbbrev} ${assignment.ccId} · '
                        'Slot ${assignment.slotRef.toString().padLeft(2, '0')}',
                        style: NoctType.cardTitleSm,
                      ),
                      const SizedBox(height: 2),
                      Text(
                        'Joins ${formatDate(assignment.fromDate)}',
                        style: NoctType.listSecondary,
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 10),
                NTag(label: _dueLabel(), tone: Tone.warning),
              ],
            ),
          ),

          const SectionLabel('You are confirming', padding: EdgeInsets.only(top: 20, bottom: 10)),
          for (final declaration in widget.declarations) ...[
            ChoiceRow(
              label: declaration.text,
              supporting:
                  declaration.supporting ??
                  (_confirmed.contains(declaration.id) ? null : 'Tap to confirm'),
              selected: _confirmed.contains(declaration.id),
              // The outstanding line takes an amber mark rather than none: it is the one thing
              // left to do, not merely an option not taken.
              unselectedMark: Nocturne.markAmber,
              // Amber only where the supporting line *is* the prompt. A line that carries a real
              // fact from the record — "5 held, 1 renewing" — is information, not an instruction,
              // and colouring it amber turns the record itself into a warning.
              unselectedSupportingColour: declaration.supporting == null
                  ? Nocturne.warningText
                  : null,
              // Editable again after a failure: the office never got it, so the crew member is
              // still the one who has to sign.
              onTap: signed
                  ? null
                  : () => setState(() {
                      if (!_confirmed.remove(declaration.id)) _confirmed.add(declaration.id);
                    }),
            ),
            const SizedBox(height: 8),
          ],

          const SizedBox(height: 8),
          DashedBorder(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
              child: Row(
                children: [
                  const Icon(PhosphorIconsRegular.signature, size: 20, color: Nocturne.neutral500),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Signed by ${widget.personName ?? 'you'}',
                          style: NoctType.bodyText.copyWith(fontSize: 13),
                        ),
                        const SizedBox(height: 2),
                        // What the mock shows here is a completed signature: "Face ID · 28 Jul
                        // 2026, 07:05 AWST". Neither half exists yet — there is no biometric
                        // binding on this build (ADR 0002 wants `local_auth`), and the timestamp
                        // has to be the *server's*, in the vessel's business timezone, because a
                        // legal declaration timestamped from a phone clock is worth nothing. Rather
                        // than print a plausible one, the block says what will happen.
                        Text(_signatureLine(), style: NoctType.meta),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),

          if (attempt != null) ...[
            const SizedBox(height: 12),
            AnswerLine(
              answer: attempt,
              onRetry: attempt.failed && widget.onRetry != null
                  ? () => widget.onRetry!(attempt.opId)
                  : null,
              onDismiss: attempt.failed && widget.onDiscard != null
                  ? () => widget.onDiscard!(attempt.opId)
                  : null,
            ),
          ],

          const SizedBox(height: 20),
          NButton(
            label: signed ? 'Already signed' : 'Attest and send',
            icon: PhosphorIconsRegular.sealCheck,
            iconSize: 18,
            variant: NButtonVariant.primary,
            block: true,
            minHeight: 48,
            onPressed: (!complete || _sending || signed) ? null : _send,
          ),
          const SizedBox(height: 8),
          Center(
            child: Text(
              'A false declaration is a disciplinary matter. Your answers are stored with the '
              'timestamp.',
              textAlign: TextAlign.center,
              style: NoctType.meta,
            ),
          ),
        ],
      ),
    );
  }

  String _dueLabel() {
    final today = widget.today;
    if (today == null) return 'Due';
    final left = daysBetween(today, widget.assignment.fromDate);
    // A swing that started a week ago is not "due today" — it is late, and saying otherwise would
    // let someone read an overdue sign-off as being on time.
    if (left < 0) return 'Overdue';
    if (left == 0) return 'Due today';
    if (left == 1) return 'Due tomorrow';
    return 'Due in $left days';
  }

  /// What the signature block says under the name.
  ///
  /// Once the record is back it is the server's own line — `28 Jul 2026, 07:05 AWST`. Before that
  /// the block says what *will* happen rather than printing a plausible timestamp: the design's
  /// mock shows "Face ID · 28 Jul 2026, 07:05 AWST" and neither half of it exists on this build.
  /// There is no biometric binding (ADR 0002 wants `local_auth`), and a time taken from the phone
  /// of the person making a declaration is worth nothing as evidence.
  String _signatureLine() =>
      (widget.isSigned ? widget.signedLine : null) ??
      'Confirmed on this device · the office records the time it arrives, '
      'in the vessel’s timezone';

  Future<void> _send() async {
    setState(() => _sending = true);
    await widget.onAttest(_confirmed.toList()..sort());
    if (mounted) setState(() => _sending = false);
  }
}
