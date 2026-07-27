/// Drill-down from the two list screens, and MOB-4's submit flow.
///
/// The same rule as `screens.dart` holds throughout: these read the local store and nothing
/// else, and they compute no compliance (AUTH-1). Every state, level and note rendered here
/// arrived pre-evaluated in the sync payload and is shown as received — an unrecognised state
/// renders verbatim rather than being rounded to something reassuring.
///
/// The one thing counted on the device is days, against `serverToday` from the payload (§7.6).
library;

import 'package:flutter/material.dart';

import '../data/evidence_capture.dart';
import '../data/local_store.dart';
import '../domain/calendar.dart';
import '../domain/states.dart';
import 'app_state.dart';
import 'widgets.dart';

// ---------------------------------------------------------------------------
// MOB-1 detail — one certification
// ---------------------------------------------------------------------------

class CertificationDetailScreen extends StatelessWidget {
  const CertificationDetailScreen({
    super.key,
    required this.state,
    required this.requirementId,
  });

  final AppState state;
  final int requirementId;

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<CertificationRow?>(
      stream: state.watchCertification(requirementId),
      builder: (context, rowSnapshot) {
        return StreamBuilder<List<LocalSubmission>>(
          stream: state.watchSubmissionsFor(requirementId),
          builder: (context, submissionSnapshot) => CertificationDetailView(
            row: rowSnapshot.data,
            submissions: submissionSnapshot.data ?? const <LocalSubmission>[],
            today: state.serverToday,
            onSubmit: (source) => state.submitEvidence(
              source: source,
              requirementId: requirementId,
            ),
          ),
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
    required this.onSubmit,
  });

  final CertificationRow? row;
  final List<LocalSubmission> submissions;
  final String? today;

  /// Returns a message to show, or null when there is nothing to say.
  final Future<String?> Function(CaptureSource source) onSubmit;

  @override
  Widget build(BuildContext context) {
    final row = this.row;
    if (row == null) {
      return Scaffold(
        appBar: AppBar(),
        body: const EmptyState(
          icon: Icons.help_outline,
          title: 'No longer required',
          message: 'This requirement is not part of your current standing.',
        ),
      );
    }

    final cell = row.cell;
    final colours = cellStateColours(cell.state, Theme.of(context).brightness);
    final expiry = cell.expiry ?? row.holding?.expiry;

    return Scaffold(
      appBar: AppBar(title: Text(row.code)),
      body: ListView(
        padding: const EdgeInsets.only(bottom: 32),
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Text(row.title, style: Theme.of(context).textTheme.titleLarge),
                ),
                const SizedBox(width: 12),
                StateChip(label: cellStateLabel(cell.state), colours: colours),
              ],
            ),
          ),

          const SectionHeader('Requirement'),
          DetailRow(label: 'Code', value: row.code),
          DetailRow(label: 'Category', value: categoryLabel(row.category)),
          // §5.1's level for this cell — M, R, M9 and the rest. Rendered raw: what a level means
          // is the matrix's business, and the app is told the answer rather than deriving it.
          DetailRow(label: 'Level', value: cell.level),
          if (row.requirement?.issuingAuthority != null)
            DetailRow(label: 'Issued by', value: row.requirement!.issuingAuthority!),

          const SectionHeader('What you hold'),
          if (row.holding == null)
            const DetailRow(label: 'Status', value: 'Nothing recorded')
          else ...[
            DetailRow(label: 'Status', value: holdingStatusLabel(row.holding!.status)),
            if (row.holding!.issueDate != null)
              DetailRow(label: 'Issued', value: formatDate(row.holding!.issueDate!)),
            if (row.holding!.note != null) DetailRow(label: 'Note', value: row.holding!.note!),
          ],
          if (expiry != null)
            DetailRow(
              label: 'Expires',
              // Counted from the server's business date, never the device clock (NFR-5, O-11).
              value: today == null
                  ? formatDate(expiry)
                  : '${formatDate(expiry)} · ${relativeDays(today!, expiry)}',
              emphasis: needsAttention(cell.state),
            ),

          if (cell.notes != null) ...[
            const SectionHeader('Notes'),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
              child: Text(cell.notes!),
            ),
          ],

          if (cell.registerRecordId != null) ...[
            const SectionHeader('Register'),
            // The overlay from §5.1 step 4: an exemption or query is why this cell reads as it
            // does. The app names the record so the crew member can quote it; it cannot open it,
            // because the register is a back-office workflow (§6.4).
            DetailRow(label: 'Record', value: cell.registerRecordId!),
          ],

          const SectionHeader('Evidence you have sent'),
          if (submissions.isEmpty)
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 16, vertical: 4),
              child: Text('Nothing submitted for this requirement yet.'),
            )
          else
            ...submissions.map((submission) => SubmissionTile(submission: submission)),

          Padding(
            padding: const EdgeInsets.fromLTRB(16, 24, 16, 8),
            child: FilledButton.icon(
              onPressed: () => showSubmitEvidenceSheet(context, onSubmit),
              icon: const Icon(Icons.add_a_photo_outlined),
              label: const Text('Submit evidence'),
            ),
          ),
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 16),
            // Setting the expectation the pipeline actually meets (LLM-1, LLM-2): a submission
            // is read and proposed, and a person decides. Nothing the crew member sends changes
            // their compliance by itself, and the screen should not imply otherwise.
            child: Text(
              'A photo or PDF is read automatically and checked by the compliance team. '
              'Your record changes once they accept it.',
            ),
          ),
        ],
      ),
    );
  }
}

/// One submission, with the part of its life the crew member can see.
class SubmissionTile extends StatelessWidget {
  const SubmissionTile({super.key, required this.submission});

  final LocalSubmission submission;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final size = submission.declaredSize;

    final String progress;
    if (submission.uploadComplete) {
      progress = 'Sent';
    } else if (size != null && size > 0) {
      progress = 'Sending — ${((submission.uploadOffset / size) * 100).clamp(0, 100).round()}%';
    } else {
      progress = 'Waiting to send';
    }

    return ListTile(
      leading: Icon(
        submission.uploadComplete ? Icons.cloud_done_outlined : Icons.cloud_upload_outlined,
      ),
      title: Text(submissionStatusLabel(submission.verificationStatus)),
      subtitle: Text(
        [
          progress,
          if (submission.source == 'mobile_camera') 'Photo' else 'File',
          if (submission.rejectionReason != null) submission.rejectionReason!,
        ].join(' · '),
        style: theme.textTheme.bodySmall,
      ),
      trailing: submission.uploadComplete
          ? null
          : SizedBox(
              width: 18,
              height: 18,
              child: CircularProgressIndicator(
                strokeWidth: 2,
                value: (size != null && size > 0) ? submission.uploadOffset / size : null,
              ),
            ),
    );
  }
}

// ---------------------------------------------------------------------------
// MOB-4 — the submit sheet
// ---------------------------------------------------------------------------

/// The three ways a document gets here. Kept as one sheet so every entry point offers the same
/// set, and so a new source is added in one place.
Future<void> showSubmitEvidenceSheet(
  BuildContext context,
  Future<String?> Function(CaptureSource source) onSubmit,
) async {
  final source = await showModalBottomSheet<CaptureSource>(
    context: context,
    builder: (sheetContext) => SafeArea(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const SectionHeader('Submit evidence'),
          ListTile(
            leading: const Icon(Icons.photo_camera_outlined),
            title: const Text('Take a photo'),
            onTap: () => Navigator.pop(sheetContext, CaptureSource.camera),
          ),
          ListTile(
            leading: const Icon(Icons.photo_library_outlined),
            title: const Text('Choose from library'),
            onTap: () => Navigator.pop(sheetContext, CaptureSource.photoLibrary),
          ),
          ListTile(
            leading: const Icon(Icons.attach_file),
            title: const Text('Attach a file'),
            subtitle: const Text('PDF or image'),
            onTap: () => Navigator.pop(sheetContext, CaptureSource.file),
          ),
          const SizedBox(height: 8),
        ],
      ),
    ),
  );

  if (source == null) return;

  final message = await onSubmit(source);
  if (message == null || !context.mounted) return;
  ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
}

// ---------------------------------------------------------------------------
// MOB-2 detail — one swing
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
          builder: (context, leaveSnapshot) => SwingDetailView(
            assignment: assignment,
            crewChange: crewChangeSnapshot.data,
            leave: leaveSnapshot.data ?? const <LocalLeave>[],
            today: state.serverToday,
          ),
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
  });

  final LocalAssignment assignment;
  final LocalCrewChange? crewChange;
  final List<LocalLeave> leave;
  final String? today;

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
      appBar: AppBar(title: Text('${assignment.partnershipAbbrev} ${assignment.ccId}')),
      body: ListView(
        padding: const EdgeInsets.only(bottom: 32),
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    formatDateRange(assignment.fromDate, assignment.toDate),
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                ),
                if (current)
                  const StateChip(
                    label: 'Current',
                    colours: (background: Color(0xFFE3F5E8), foreground: Color(0xFF1B5E33)),
                  ),
              ],
            ),
          ),

          const SectionHeader('Swing'),
          DetailRow(label: 'Partnership', value: assignment.partnershipAbbrev),
          DetailRow(label: 'Crew change', value: assignment.ccId),
          DetailRow(label: 'Slot', value: '${assignment.slotRef}'),
          DetailRow(label: 'Starts', value: formatDate(assignment.fromDate)),
          DetailRow(label: 'Ends', value: formatDate(assignment.toDate)),
          DetailRow(label: 'Length', value: '$length days'),

          if (today != null) ...[
            const SectionHeader('Where you are'),
            if (current) ...[
              DetailRow(
                label: 'Day',
                value: '${daysBetween(assignment.fromDate, today) + 1} of $length',
                emphasis: true,
              ),
              DetailRow(
                label: 'Ends',
                value: relativeDays(today, assignment.toDate),
              ),
            ] else
              DetailRow(
                label: daysBetween(today, assignment.fromDate) > 0 ? 'Starts' : 'Ended',
                value: relativeDays(
                  today,
                  daysBetween(today, assignment.fromDate) > 0
                      ? assignment.fromDate
                      : assignment.toDate,
                ),
                emphasis: true,
              ),
          ],

          if (crewChange != null) ...[
            const SectionHeader('Cut-off'),
            DetailRow(
              label: 'Requests by',
              value: today == null
                  ? formatDate(crewChange!.cutoff)
                  : '${formatDate(crewChange!.cutoff)} · '
                      '${relativeDays(today, crewChange!.cutoff)}',
            ),
            const Padding(
              padding: EdgeInsets.fromLTRB(16, 4, 16, 0),
              // Q17: a late request is accepted on an acknowledged retry rather than refused, so
              // the wording is a deadline that matters, not a door that closes.
              child: Text(
                'Exemption and query requests for this swing are expected before this date.',
              ),
            ),
          ],

          if (overlapping.isNotEmpty) ...[
            const SectionHeader('Leave in this window'),
            ...overlapping.map(
              (record) => ListTile(
                leading: const Icon(Icons.beach_access_outlined),
                title: Text(record.kind.replaceAll('_', ' ')),
                subtitle: Text(formatDateRange(record.fromDate, record.toDate)),
                trailing: Text(record.status),
              ),
            ),
          ],
        ],
      ),
    );
  }
}
