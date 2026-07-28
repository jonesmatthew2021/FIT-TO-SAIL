/// MOB-6 sending the certificate, and MOB-7 confirming what the server read out of it.
///
/// Two rules run through both, and neither is negotiable:
///
///  * **The parse happens on the server.** Nothing here attempts on-device OCR. The client
///    uploads bytes and renders whatever the §8 pipeline reports back; where it reports nothing,
///    the crew member types the fields and a Data Steward checks them (LLM-2's launch posture,
///    which is what the pipeline actually does today with no provider chosen).
///  * **Confirming is not accepting.** "Send for review" produces a submission for the ADM-9
///    queue. It does not move a holding, and MOB-7's closing line says so.
library;

import 'package:flutter/material.dart';
import 'package:phosphor_icons/phosphor_icons.dart';

import '../data/evidence_capture.dart';
import '../domain/calendar.dart';
import '../domain/intents.dart';
import '../domain/offers.dart';
import 'app_state.dart';
import 'nocturne.dart';
import 'widgets.dart';

// ---------------------------------------------------------------------------
// MOB-6 — Send the certificate
// ---------------------------------------------------------------------------

/// The intake sheet: where is the certificate, and what happens once it is sent.
///
/// **What the mock draws and this does not.** MOB-6 in the handoff is the *operating system's*
/// share sheet, with Attest sitting first among Files, Print and More — the point being that a
/// certificate can be sent from Mail without opening this app at all. That sheet is the OS's to
/// draw and an iOS Share Extension plus an Android intent filter to earn; drawing our own
/// four-up with a greyed-out "Print" in it would be a picture of a feature rather than the
/// feature. The share-target registration is native work and is listed in the backend handoff
/// alongside the server side of it.
///
/// What is here is the same sheet's in-app half, at the same geometry: where the file is, what
/// the server will do with it, and one tap per source.
Future<void> showSendCertificateSheet({
  required BuildContext context,
  required AppState state,
  int? requirementId,
  String? requirementLabel,
}) async {
  final source = await showNocturneSheet<CaptureSource>(
    context: context,
    builder: (sheetContext) => Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.baseline,
          textBaseline: TextBaseline.alphabetic,
          children: [
            Text('Send the certificate', style: NoctType.cardTitleSm),
            if (requirementLabel != null) ...[
              const SizedBox(width: 8),
              Flexible(
                child: Text(
                  requirementLabel,
                  overflow: TextOverflow.ellipsis,
                  style: NoctType.meta,
                ),
              ),
            ],
          ],
        ),
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(
              child: SourceTile(
                icon: PhosphorIconsRegular.filePdf,
                label: 'Files',
                caption: 'A PDF or image you already have',
                accented: true,
                onTap: () => Navigator.pop(sheetContext, CaptureSource.file),
              ),
            ),
            Expanded(
              child: SourceTile(
                icon: PhosphorIconsRegular.folder,
                label: 'Photos',
                caption: 'A picture already on this phone',
                onTap: () => Navigator.pop(sheetContext, CaptureSource.photoLibrary),
              ),
            ),
            Expanded(
              child: SourceTile(
                icon: PhosphorIconsRegular.camera,
                label: 'Camera',
                caption: 'Photograph the paper copy',
                onTap: () => Navigator.pop(sheetContext, CaptureSource.camera),
              ),
            ),
          ],
        ),
        const SizedBox(height: 14),
        const Divider(color: Nocturne.neutral800),
        const SizedBox(height: 14),
        InfoBand(
          text: requirementLabel == null
              ? 'Attest reads it on the server and matches it to a requirement. You only confirm.'
              : 'Attest reads it on the server and matches it to $requirementLabel. '
                    'You only confirm.',
          emphasis: [?requirementLabel],
        ),
        const SizedBox(height: 10),
        Center(child: Text('Works offline — it sends when you have signal.', style: NoctType.meta)),
      ],
    ),
  );

  if (source == null || !context.mounted) return;

  final result = await state.submitEvidence(source: source, requirementId: requirementId);
  if (!context.mounted) return;

  if (result.message != null) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(result.message!)));
    return;
  }
  if (!result.queued) return;

  await Navigator.of(context).push(
    MaterialPageRoute<void>(
      builder: (_) => ConfirmReadingScreen(
        state: state,
        submissionPublicId: result.publicId!,
        fileName: result.fileName,
        byteSize: result.size,
        requirementId: requirementId,
      ),
      fullscreenDialog: true,
    ),
  );
}

// ---------------------------------------------------------------------------
// MOB-7 — Confirm the reading
// ---------------------------------------------------------------------------

class ConfirmReadingScreen extends StatelessWidget {
  const ConfirmReadingScreen({
    super.key,
    required this.state,
    required this.submissionPublicId,
    this.fileName,
    this.byteSize,
    this.requirementId,
  });

  final AppState state;
  final String submissionPublicId;
  final String? fileName;
  final int? byteSize;
  final int? requirementId;

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<List<CertificationRow>>(
      stream: state.watchCertifications(),
      builder: (context, snapshot) {
        final rows = snapshot.data ?? const <CertificationRow>[];
        return ConfirmReadingView(
          reading: state.readingFor(submissionPublicId),
          fileName: fileName,
          byteSize: byteSize,
          requirementId: requirementId,
          requirements: [
            for (final row in rows)
              (id: row.cell.requirementId, label: '${row.code} · ${row.title}'),
          ],
          // "Accepting this clears the last thing on your Home screen" is only true when it is.
          clearsTheLast: rows.where((row) => row.outstanding).length == 1,
          onSend: (reading) async {
            await state.answer(
              kind: IntentKind.readingConfirmed,
              summary: 'Confirmed the reading of ${fileName ?? 'a document'}',
              requirementId: reading.requirementId,
              subjectRef: submissionPublicId,
              payload: {
                'submissionPublicId': submissionPublicId,
                'certificateNumber': reading.certificateNumber,
                'issued': reading.issued,
                'expires': reading.expires,
              },
            );
            if (context.mounted) Navigator.of(context).pop();
          },
        );
      },
    );
  }
}

class ConfirmReadingView extends StatefulWidget {
  const ConfirmReadingView({
    super.key,
    required this.requirements,
    required this.onSend,
    this.reading,
    this.fileName,
    this.byteSize,
    this.requirementId,
    this.clearsTheLast = false,
  });

  /// What the §8 pipeline read. Null is a first-class case, not an error: with no LLM provider
  /// configured the extractor reports nothing, every document goes to a human, and this screen
  /// opens at its third confidence level with the fields empty.
  final ExtractedReading? reading;

  final String? fileName;
  final int? byteSize;

  /// What the *device* said the document was for, used to preselect when the server has not.
  final int? requirementId;

  final List<({int id, String label})> requirements;
  final bool clearsTheLast;
  final Future<void> Function(ExtractedReading corrected) onSend;

  @override
  State<ConfirmReadingView> createState() => _ConfirmReadingViewState();
}

class _ConfirmReadingViewState extends State<ConfirmReadingView> {
  late final TextEditingController _number;
  late final TextEditingController _issued;
  late final TextEditingController _expires;
  int? _requirementId;
  bool _sending = false;

  @override
  void initState() {
    super.initState();
    final reading = widget.reading;
    _number = TextEditingController(text: reading?.certificateNumber ?? '');
    _issued = TextEditingController(
      text: reading?.issued == null ? '' : formatDate(reading!.issued!),
    );
    _expires = TextEditingController(
      text: reading?.expires == null ? '' : formatDate(reading!.expires!),
    );
    _requirementId = reading?.requirementId ?? widget.requirementId;
  }

  @override
  void dispose() {
    _number.dispose();
    _issued.dispose();
    _expires.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final reading = widget.reading;
    final confidence = reading?.confidence ?? 'unmatched';
    final matched = _requirementId != null;

    return Scaffold(
      backgroundColor: Nocturne.bg,
      appBar: const NocturneNavBar(title: 'Check what we read', closeIcon: true),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(Nocturne.gutter, 16, Nocturne.gutter, 24),
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Container(
                width: 76,
                height: 96,
                decoration: BoxDecoration(
                  color: Nocturne.neutral800,
                  border: Border.all(color: Nocturne.neutral700),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: const Icon(
                  PhosphorIconsRegular.filePdf,
                  size: 24,
                  color: Nocturne.neutral600,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      widget.fileName ?? 'Your document',
                      style: NoctType.bodyText.copyWith(fontSize: 13.5, color: Nocturne.text),
                    ),
                    const SizedBox(height: 3),
                    Text(_provenance(reading), style: NoctType.meta),
                    const SizedBox(height: 8),
                    NOutlineTag(label: _confidenceLabel(confidence, matched)),
                  ],
                ),
              ),
            ],
          ),

          const SizedBox(height: 12),
          InfoBand(
            text: reading == null
                ? 'Nothing has been read from this document yet, so the fields below are yours '
                      'to fill in. The office reviews it either way.'
                : 'Everything below was read from the document. Fix anything wrong — the office '
                      'reviews it either way.',
          ),

          const SizedBox(height: 12),
          NField(
            label: 'This is',
            child: _RequirementSelect(
              requirements: widget.requirements,
              value: _requirementId,
              onChanged: (value) => setState(() => _requirementId = value),
            ),
          ),
          const SizedBox(height: 10),
          NField(
            label: 'Certificate number',
            child: NTextInput(
              controller: _number,
              hintText: reading?.certificateNumber == null ? 'Not read — add it' : null,
              textCapitalization: TextCapitalization.characters,
            ),
          ),
          const SizedBox(height: 10),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: NField(
                  label: 'Issued',
                  child: NTextInput(
                    controller: _issued,
                    hintText: 'Not read — add it',
                    keyboardType: TextInputType.datetime,
                  ),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: NField(
                  label: 'Expires',
                  child: NTextInput(
                    controller: _expires,
                    hintText: 'Not read — add it',
                    keyboardType: TextInputType.datetime,
                  ),
                ),
              ),
            ],
          ),

          if (widget.clearsTheLast) ...[
            const SizedBox(height: 12),
            NCard(
              padding: const EdgeInsets.fromLTRB(13, 11, 13, 11),
              child: Row(
                children: [
                  const Icon(PhosphorIconsRegular.checkCircle, size: 20, color: Nocturne.goodText),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      'Accepting this clears the last thing on your Home screen.',
                      style: NoctType.cardBody.copyWith(color: Nocturne.neutral300),
                    ),
                  ),
                ],
              ),
            ),
          ],

          const SizedBox(height: 20),
          NButton(
            label: 'Send for review',
            variant: NButtonVariant.primary,
            block: true,
            minHeight: 48,
            onPressed: _sending ? null : _send,
          ),
          const SizedBox(height: 8),
          Center(
            child: Text('Works offline — it sends when you have signal.', style: NoctType.meta),
          ),
        ],
      ),
    );
  }

  Future<void> _send() async {
    setState(() => _sending = true);
    await widget.onSend(
      ExtractedReading(
        confidence: widget.reading?.confidence ?? 'unmatched',
        requirementId: _requirementId,
        certificateNumber: _number.text.trim().isEmpty ? null : _number.text.trim(),
        issued: _issued.text.trim().isEmpty ? null : _issued.text.trim(),
        expires: _expires.text.trim().isEmpty ? null : _expires.text.trim(),
        fileName: widget.fileName,
        byteSize: widget.byteSize,
      ),
    );
    if (mounted) setState(() => _sending = false);
  }

  /// The provenance line has to be true about *this* document. Saying "read on the server" of
  /// something still sitting in the outbox would be the one lie the whole screen is built to
  /// avoid.
  String _provenance(ExtractedReading? reading) {
    final size = widget.byteSize;
    return [
      if (reading == null) 'Queued to send' else 'Read on the server',
      if (size != null) formatBytes(size),
    ].join(' · ');
  }

  static String _confidenceLabel(String confidence, bool matched) {
    if (!matched) return 'Not matched — pick the requirement';
    return switch (confidence) {
      'high' => 'Matched · high confidence',
      'review' => 'Matched · needs a look',
      _ => 'Matched by you · not yet read',
    };
  }
}

/// The "This is" control. A plain menu rather than a searchable picker: the list is the crew
/// member's own standing, which is a handful of rows, and a search box over six items is friction
/// pretending to be power.
class _RequirementSelect extends StatelessWidget {
  const _RequirementSelect({
    required this.requirements,
    required this.value,
    required this.onChanged,
  });

  final List<({int id, String label})> requirements;
  final int? value;
  final ValueChanged<int?> onChanged;

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: const BoxConstraints(minHeight: 44),
      padding: const EdgeInsets.symmetric(horizontal: 10),
      decoration: BoxDecoration(
        color: Nocturne.surface,
        border: Border.all(color: Nocturne.divider),
        borderRadius: Nocturne.borderMd,
      ),
      child: DropdownButtonHideUnderline(
        child: DropdownButton<int?>(
          value: value,
          isExpanded: true,
          dropdownColor: Nocturne.surface,
          borderRadius: Nocturne.borderMd,
          icon: const Icon(PhosphorIconsRegular.caretDown, size: 14, color: Nocturne.neutral500),
          style: NoctType.input,
          hint: Text(
            'Not read — pick one',
            style: NoctType.input.copyWith(color: Nocturne.neutral600),
          ),
          items: [
            for (final requirement in requirements)
              DropdownMenuItem<int?>(
                value: requirement.id,
                child: Text(requirement.label, overflow: TextOverflow.ellipsis),
              ),
          ],
          onChanged: onChanged,
        ),
      ),
    );
  }
}

/// `1.1 MB`. One decimal place, because two is noise on a screen this size.
String formatBytes(int bytes) {
  if (bytes < 1024) return '$bytes B';
  if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(0)} KB';
  return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
}
