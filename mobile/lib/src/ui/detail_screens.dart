/// Drill-down from the list screens: MOB-2 requirement detail and the swing detail behind a
/// roster row.
///
/// The same rule as `screens.dart` holds throughout: these read the local store and nothing
/// else, and they compute no compliance (AUTH-1). Every state, level and note rendered here
/// arrived pre-evaluated in the sync payload and is shown as received — an unrecognised state
/// renders verbatim rather than being rounded to something reassuring.
///
/// The one thing counted on the device is days, against `serverToday` from the payload (§7.6).
library;

import 'package:flutter/material.dart';
import 'package:phosphor_icons/phosphor_icons.dart';

import '../data/local_store.dart';
import '../domain/calendar.dart';
import '../domain/intents.dart';
import '../domain/states.dart';
import '../domain/urgency.dart';
import 'action_screens.dart';
import 'app_state.dart';
import 'evidence_screens.dart';
import 'nocturne.dart';
import 'screens.dart';
import 'widgets.dart';

/// Pushes MOB-2 for a requirement. One function so every entry point — a list row, a Home card,
/// an alert's inline action — lands on the same screen with the same back behaviour.
void openRequirement(BuildContext context, AppState state, int requirementId) {
  Navigator.of(context).push(
    MaterialPageRoute<void>(
      builder: (_) => CertificationDetailScreen(state: state, requirementId: requirementId),
    ),
  );
}

// ---------------------------------------------------------------------------
// MOB-2 — one requirement
// ---------------------------------------------------------------------------

class CertificationDetailScreen extends StatelessWidget {
  const CertificationDetailScreen({super.key, required this.state, required this.requirementId});

  final AppState state;
  final int requirementId;

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<CertificationRow?>(
      stream: state.watchCertification(requirementId),
      builder: (context, rowSnapshot) {
        return StreamBuilder<List<LocalSubmission>>(
          stream: state.watchSubmissionsFor(requirementId),
          builder: (context, submissionSnapshot) {
            return StreamBuilder<List<LocalCrewIntent>>(
              stream: state.watchIntentsFor(requirementId),
              builder: (context, intentSnapshot) {
                return StreamBuilder<List<LocalCrewStatement>>(
                  stream: state.watchStatementsFor(requirementId),
                  builder: (context, statementSnapshot) {
                    final row = rowSnapshot.data;
                    return CertificationDetailView(
                      row: row,
                      submissions: submissionSnapshot.data ?? const <LocalSubmission>[],
                      answers: answersFrom(
                        intentSnapshot.data ?? const <LocalCrewIntent>[],
                        statementSnapshot.data ?? const <LocalCrewStatement>[],
                      ),
                      today: state.serverToday,
                      swingTo: state.syncState?.standingTo,
                      onSubmit: row == null
                          ? null
                          : () => showSendCertificateSheet(
                              context: context,
                              state: state,
                              requirementId: requirementId,
                              requirementLabel: '${row.code} ${row.title}',
                            ),
                      onCourseBooked:
                          row == null ? null : () => openOneTapUpdate(context, state, row),
                      onNeedHelp: row == null
                          ? null
                          : () => state.answer(
                              kind: IntentKind.helpNeeded,
                              summary: 'Asked for help with ${row.title}',
                              requirementId: requirementId,
                            ),
                      onRetryIntent: state.retryAnswer,
                      onDiscardIntent: state.discardAnswer,
                      onRetrySubmission: state.retrySubmission,
                    );
                  },
                );
              },
            );
          },
        );
      },
    );
  }
}

class CertificationDetailView extends StatelessWidget {
  const CertificationDetailView({
    super.key,
    required this.row,
    required this.submissions,
    required this.today,
    this.answers = const <Answer>[],
    this.swingTo,
    this.onSubmit,
    this.onCourseBooked,
    this.onNeedHelp,
    this.onRetryIntent,
    this.onDiscardIntent,
    this.onRetrySubmission,
  });

  final CertificationRow? row;
  final List<LocalSubmission> submissions;
  final List<Answer> answers;
  final String? today;
  final String? swingTo;
  final VoidCallback? onSubmit;
  final VoidCallback? onCourseBooked;
  final VoidCallback? onNeedHelp;
  final void Function(String opId)? onRetryIntent;
  final void Function(String opId)? onDiscardIntent;

  /// Re-queues a submission whose registration failed (issue #13).
  final void Function(String publicId)? onRetrySubmission;

  @override
  Widget build(BuildContext context) {
    final row = this.row;
    if (row == null) {
      return const Scaffold(
        backgroundColor: Nocturne.bg,
        appBar: NocturneNavBar(title: ''),
        body: EmptyState(
          icon: PhosphorIconsRegular.question,
          title: 'No longer required',
          message: 'This requirement is not part of your current standing.',
        ),
      );
    }

    final cell = row.cell;
    final expiry = row.expiry;
    final urgency = urgencyFor(state: cell.state, expiry: expiry, today: today, swingTo: swingTo);

    return Scaffold(
      backgroundColor: Nocturne.bg,
      appBar: NocturneNavBar(
        title: row.code,
        titleSpan: monoSpan(row.code, fontSize: 15, colour: Nocturne.text),
      ),
      body: Column(
        children: [
          Expanded(
            child: ListView(
              padding: const EdgeInsets.fromLTRB(Nocturne.gutter, 18, Nocturne.gutter, 8),
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.center,
                  children: [
                    Expanded(child: Text(row.title, style: NoctType.screenName)),
                    const SizedBox(width: 12),
                    NTag(label: cellStateLabel(cell.state), tone: cellStateTone(cell.state)),
                  ],
                ),

                const SizedBox(height: 22),
                const SectionLabel('Requirement', padding: EdgeInsets.only(bottom: 10)),
                DefinitionRow(
                  label: 'Code',
                  value: row.code,
                  valueSpan: monoSpan(row.code, fontSize: 13, colour: Nocturne.text),
                ),
                DefinitionRow(label: 'Category', value: categoryLabel(row.category)),
                // §5.1's level for this cell — M, R, M9 and the rest. Rendered raw: what a level
                // means is the matrix's business, and the app is told the answer rather than
                // deriving it.
                DefinitionRow(
                  label: 'Level',
                  value: cell.level,
                  valueSpan: monoSpan(cell.level, fontSize: 13, colour: Nocturne.text),
                ),
                if (row.requirement?.issuingAuthority != null)
                  DefinitionRow(label: 'Issued by', value: row.requirement!.issuingAuthority!),

                const SizedBox(height: 14),
                const SectionLabel('What you hold', padding: EdgeInsets.only(bottom: 10)),
                if (row.holding == null)
                  const DefinitionRow(label: 'Status', value: 'Nothing recorded')
                else ...[
                  DefinitionRow(label: 'Status', value: holdingStatusLabel(row.holding!.status)),
                  if (row.holding!.issueDate != null)
                    DefinitionRow(label: 'Issued', value: formatDate(row.holding!.issueDate!)),
                ],
                if (expiry != null)
                  DefinitionRow(
                    label: 'Expires',
                    value: formatDate(expiry),
                    valueSpan: TextSpan(
                      children: [
                        TextSpan(text: formatDate(expiry)),
                        // Counted from the server's business date, never the device clock.
                        if (today != null)
                          TextSpan(
                            text: ' · ${relativeDays(today!, expiry)}',
                            style: TextStyle(color: toneColours(urgencyTone(urgency)).text),
                          ),
                      ],
                    ),
                  ),
                if (row.holding?.note != null)
                  DefinitionRow(label: 'Note', value: row.holding!.note!),

                if (cell.notes != null) ...[
                  const SizedBox(height: 14),
                  const SectionLabel('Notes', padding: EdgeInsets.only(bottom: 10)),
                  Text(cell.notes!, style: NoctType.bodyText),
                ],

                if (cell.registerRecordId != null) ...[
                  const SizedBox(height: 14),
                  const SectionLabel('Register', padding: EdgeInsets.only(bottom: 10)),
                  // The overlay from §5.1 step 4: an exemption or query is why this cell reads as
                  // it does. The app names the record so the crew member can quote it; it cannot
                  // open it, because the register is a back-office workflow (§6.4).
                  DefinitionRow(
                    label: 'Record',
                    value: cell.registerRecordId!,
                    valueSpan: monoSpan(
                      cell.registerRecordId!,
                      fontSize: 13,
                      colour: Nocturne.text,
                    ),
                  ),
                ],

                if (answers.isNotEmpty) ...[
                  const SizedBox(height: 14),
                  const SectionLabel(
                    'What you have told the office',
                    padding: EdgeInsets.only(bottom: 10),
                  ),
                  for (final answer in answers) ...[
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
                    // A failed *reading* beside a delivered document reads as "my certificate
                    // didn't get through" — the opposite of the truth (issue #13). Say which
                    // half failed.
                    if (answer.kind == IntentKind.readingConfirmed &&
                        answer.failed &&
                        submissions.any((s) => s.uploadComplete))
                      Padding(
                        padding: const EdgeInsets.only(left: 23, bottom: 8),
                        child: Text(
                          'The document itself is with the office — only your typed details '
                          'did not send.',
                          style: NoctType.meta,
                        ),
                      ),
                  ],
                ],

                const SizedBox(height: 14),
                const SectionLabel('Evidence you have sent', padding: EdgeInsets.only(bottom: 10)),
                if (submissions.isEmpty)
                  Text('Nothing submitted for this requirement yet.', style: NoctType.cardBody)
                else
                  for (final submission in submissions)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: SubmissionTile(
                        submission: submission,
                        onRetry: onRetrySubmission == null
                            ? null
                            : () => onRetrySubmission!(submission.publicId),
                      ),
                    ),
              ],
            ),
          ),

          // Pinned, because the action is the point of the screen and scrolling to find it is how
          // a crew member decides the app is not worth opening.
          Container(
            padding: const EdgeInsets.fromLTRB(Nocturne.gutter, 12, Nocturne.gutter, 8),
            decoration: const BoxDecoration(
              color: Nocturne.bg,
              border: Border(top: BorderSide(color: Nocturne.neutral900)),
            ),
            child: SafeArea(
              top: false,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  NButton(
                    label: 'Submit evidence',
                    icon: PhosphorIconsRegular.cameraPlus,
                    iconSize: 18,
                    variant: NButtonVariant.primary,
                    block: true,
                    minHeight: 48,
                    onPressed: onSubmit,
                  ),
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      Expanded(
                        child: NButton(
                          // Named for what it does — it *opens* the one-tap update screen. The
                          // old label, "Course booked", was identical to Home's button, which
                          // posts the statement; same words doing two things one screen apart
                          // is how a false statement gets made by muscle memory (issue #16).
                          label: 'Update the office',
                          icon: PhosphorIconsRegular.calendarCheck,
                          fontSize: 12.5,
                          onPressed: onCourseBooked,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: NButton(
                          label: 'Need help',
                          icon: PhosphorIconsRegular.chatCircleDots,
                          variant: NButtonVariant.ghost,
                          fontSize: 12.5,
                          onPressed: onNeedHelp,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  // Setting the expectation the pipeline actually meets (LLM-1, LLM-2): a
                  // submission is read and proposed, and a person decides. Nothing the crew
                  // member sends changes their compliance by itself, and the screen must not
                  // imply otherwise.
                  Text(
                    'A photo or PDF is read on the server and checked by the compliance team. '
                    'Your record changes once they accept it.',
                    style: NoctType.cardBody.copyWith(fontSize: 12),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// One submission, with the part of its life the crew member can see.
///
/// The primary line never claims a server status while the document is still on the phone
/// (issue #13): "Processing" above "Sending — 0%" was two contradicting truths on one tile, and
/// a registration the office refused used to keep that reading forever, with no error and no
/// way out. The send states here are the same five-state honesty the one-tap answers have.
class SubmissionTile extends StatelessWidget {
  const SubmissionTile({super.key, required this.submission, this.onTap, this.onRetry});

  final LocalSubmission submission;
  final VoidCallback? onTap;

  /// Re-queues a refused or exhausted registration. Shown only when [submission] has failed.
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    final size = submission.declaredSize;
    final failed = submission.sendState == 'failed';
    final queued = submission.sendState == 'queued';
    final bytesLocal = !submission.uploadComplete;

    final String primary;
    if (failed) {
      primary = "Couldn't send";
    } else if (queued) {
      primary = 'Waiting to send';
    } else if (bytesLocal) {
      primary = 'Sending to the office';
    } else {
      primary = submissionStatusLabel(submission.verificationStatus);
    }

    final secondary = [
      if (failed)
        // The server's own words, exactly like a failed one-tap answer.
        submission.sendError ?? 'The office could not accept this'
      else if (queued)
        'Sends when you have signal'
      else if (bytesLocal && size != null && size > 0)
        'Sending — ${((submission.uploadOffset / size) * 100).clamp(0, 100).round()}%'
      else if (bytesLocal)
        'Waiting to send'
      else
        'Sent ${_shortDate(submission.submittedAt)}',
      if (submission.source == 'mobile_camera') 'Photo' else 'File',
      if (submission.rejectionReason != null) submission.rejectionReason!,
    ].join(' · ');

    return NCard(
      onTap: onTap,
      padding: const EdgeInsets.fromLTRB(15, 13, 15, 13),
      child: Row(
        children: [
          Icon(
            failed
                ? PhosphorIconsRegular.warningCircle
                : queued
                    ? PhosphorIconsRegular.clockCountdown
                    : submission.uploadComplete
                        ? PhosphorIconsRegular.cloudCheck
                        : PhosphorIconsRegular.cloudArrowUp,
            size: 22,
            color: failed ? Nocturne.criticalText : Nocturne.accent,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  primary,
                  style: NoctType.listPrimary.copyWith(
                    color: failed ? Nocturne.criticalText : null,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  secondary,
                  style: NoctType.listSecondary.copyWith(color: Nocturne.neutral600),
                ),
              ],
            ),
          ),
          const SizedBox(width: 12),
          if (failed)
            NButton(
              label: 'Retry',
              variant: NButtonVariant.ghost,
              fontSize: 12,
              minHeight: 36,
              onPressed: onRetry,
            )
          else if (queued)
            const SizedBox.shrink()
          else if (submission.uploadComplete)
            NTag(
              label: submissionStatusLabel(submission.verificationStatus),
              tone: submissionStatusTone(submission.verificationStatus),
            )
          else
            SizedBox(
              width: 18,
              height: 18,
              child: CircularProgressIndicator(
                strokeWidth: 2,
                color: Nocturne.accent,
                value: (size != null && size > 0) ? submission.uploadOffset / size : null,
              ),
            ),
        ],
      ),
    );
  }

  /// A wall-clock timestamp, not a business date — "when did I send this" is a different question
  /// from "what day is it", and only the second one has to come from the server.
  static String _shortDate(DateTime when) {
    const months = [
      'Jan',
      'Feb',
      'Mar',
      'Apr',
      'May',
      'Jun',
      'Jul',
      'Aug',
      'Sep',
      'Oct',
      'Nov',
      'Dec',
    ];
    final local = when.toLocal();
    return '${local.day} ${months[local.month - 1]}';
  }
}

// ---------------------------------------------------------------------------
// Roster detail — one swing
// ---------------------------------------------------------------------------

class SwingDetailScreen extends StatelessWidget {
  const SwingDetailScreen({super.key, required this.state, required this.assignment});

  final AppState state;
  final LocalAssignment assignment;

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<LocalCrewChange?>(
      stream: state.watchCrewChange(assignment.crewChangeId),
      builder: (context, crewChangeSnapshot) {
        return StreamBuilder<List<LocalLeave>>(
          stream: state.watchLeave(),
          builder: (context, leaveSnapshot) {
            return StreamBuilder<List<CertificationRow>>(
              stream: state.watchCertifications(),
              builder: (context, rowSnapshot) => SwingDetailView(
                assignment: assignment,
                crewChange: crewChangeSnapshot.data,
                leave: leaveSnapshot.data ?? const <LocalLeave>[],
                today: state.serverToday,
                // MOB-9 is only offered for the swing the standing was evaluated against —
                // signing off against a swing whose requirements nobody has evaluated would be
                // asking a crew member to attest to an unknown.
                onAttest: state.syncState?.standingCcId == assignment.ccId
                    ? () => openAttestation(
                        context,
                        state,
                        assignment: assignment,
                        rows: rowSnapshot.data ?? const <CertificationRow>[],
                      )
                    : null,
              ),
            );
          },
        );
      },
    );
  }
}

class SwingDetailView extends StatelessWidget {
  const SwingDetailView({
    super.key,
    required this.assignment,
    required this.crewChange,
    required this.leave,
    required this.today,
    this.onAttest,
  });

  final LocalAssignment assignment;
  final LocalCrewChange? crewChange;
  final List<LocalLeave> leave;
  final String? today;
  final VoidCallback? onAttest;

  @override
  Widget build(BuildContext context) {
    final today = this.today;
    final current = today != null && isWithin(today, assignment.fromDate, assignment.toDate);

    // Day counting is the one calculation §7.6 permits on the device, and only against the
    // server's business date. Inclusive of both ends, which is how a swing is spoken about.
    final length = daysBetween(assignment.fromDate, assignment.toDate) + 1;

    // Leave that overlaps the swing. Shown because the two touching is the thing a crew member
    // most wants to spot, and read-only because O-8 leaves ownership of leave unresolved.
    final overlapping = leave
        .where(
          (record) =>
              daysBetween(record.fromDate, assignment.toDate) >= 0 &&
              daysBetween(assignment.fromDate, record.toDate) >= 0,
        )
        .toList(growable: false);

    return Scaffold(
      backgroundColor: Nocturne.bg,
      appBar: NocturneNavBar(title: '${assignment.partnershipAbbrev} ${assignment.ccId}'),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(Nocturne.gutter, 18, Nocturne.gutter, 32),
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  formatDateRange(assignment.fromDate, assignment.toDate),
                  style: NoctType.screenName,
                ),
              ),
              if (current) const NTag(label: 'Current', tone: Tone.good),
            ],
          ),

          const SizedBox(height: 22),
          const SectionLabel('Swing', padding: EdgeInsets.only(bottom: 10)),
          DefinitionRow(label: 'Partnership', value: assignment.partnershipAbbrev),
          DefinitionRow(
            label: 'Crew change',
            value: assignment.ccId,
            valueSpan: monoSpan(assignment.ccId, fontSize: 13, colour: Nocturne.text),
          ),
          DefinitionRow(
            label: 'Slot',
            value: '${assignment.slotRef}',
            valueSpan: monoSpan(
              assignment.slotRef.toString().padLeft(2, '0'),
              fontSize: 13,
              colour: Nocturne.text,
            ),
          ),
          DefinitionRow(label: 'Starts', value: formatDate(assignment.fromDate)),
          DefinitionRow(label: 'Ends', value: formatDate(assignment.toDate)),
          DefinitionRow(label: 'Length', value: '$length days'),

          if (today != null) ...[
            const SizedBox(height: 14),
            const SectionLabel('Where you are', padding: EdgeInsets.only(bottom: 10)),
            if (current) ...[
              DefinitionRow(
                label: 'Day',
                value: '${daysBetween(assignment.fromDate, today) + 1} of $length',
              ),
              DefinitionRow(label: 'Ends', value: relativeDays(today, assignment.toDate)),
            ] else
              DefinitionRow(
                label: daysBetween(today, assignment.fromDate) > 0 ? 'Starts' : 'Ended',
                value: relativeDays(
                  today,
                  daysBetween(today, assignment.fromDate) > 0
                      ? assignment.fromDate
                      : assignment.toDate,
                ),
              ),
          ],

          if (crewChange != null) ...[
            const SizedBox(height: 14),
            const SectionLabel('Cut-off', padding: EdgeInsets.only(bottom: 10)),
            DefinitionRow(
              label: 'Requests by',
              value: today == null
                  ? formatDate(crewChange!.cutoff)
                  : '${formatDate(crewChange!.cutoff)} · '
                        '${relativeDays(today, crewChange!.cutoff)}',
            ),
            // Q17: a late request is accepted on an acknowledged retry rather than refused, so
            // the wording is a deadline that matters, not a door that closes.
            Text(
              'Exemption and query requests for this swing are expected before this date.',
              style: NoctType.cardBody,
            ),
          ],

          if (overlapping.isNotEmpty) ...[
            const SizedBox(height: 14),
            const SectionLabel('Leave in this window', padding: EdgeInsets.only(bottom: 10)),
            for (final record in overlapping)
              Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: NCard(
                  padding: const EdgeInsets.fromLTRB(15, 12, 15, 12),
                  child: Row(
                    children: [
                      const Icon(
                        PhosphorIconsRegular.umbrella,
                        size: 20,
                        color: Nocturne.neutral500,
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(leaveKindLabel(record.kind), style: NoctType.listPrimarySm),
                            const SizedBox(height: 2),
                            Text(
                              formatDateRange(record.fromDate, record.toDate),
                              style: NoctType.listSecondary,
                            ),
                          ],
                        ),
                      ),
                      Text(record.status, style: NoctType.meta),
                    ],
                  ),
                ),
              ),
          ],

          if (onAttest != null) ...[
            const SizedBox(height: 24),
            NButton(
              label: 'Sign off before you sail',
              icon: PhosphorIconsRegular.sealCheck,
              iconSize: 18,
              variant: NButtonVariant.primary,
              block: true,
              minHeight: 48,
              onPressed: onAttest,
            ),
          ],
        ],
      ),
    );
  }
}
