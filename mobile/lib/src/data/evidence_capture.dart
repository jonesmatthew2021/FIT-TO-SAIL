/// MOB-4 capture: turning "a photo" or "an attachment" into a file this app can upload.
///
/// Two things here are deliberate and worth not undoing.
///
/// **The chosen file is copied into the app container before anything else happens.** Both
/// pickers hand back a path in a temporary directory that iOS is free to reap — and reaps
/// eagerly under storage pressure, which is exactly the state a phone full of photos is in. A
/// submission is durable the moment it is queued, so the bytes it names have to be as durable
/// as the queue row; a path into `tmp/` is not.
///
/// **The content type is decided here, from the extension, against the server's allow-list.**
/// That is pre-validation and advisory only (AUTH-1): `EvidenceService.submit` checks the same
/// set and is the one that counts. Doing it here buys a message the crew member can act on
/// while the file is still in front of them, instead of a rejection that arrives after an
/// upload over a satellite link.
library;

import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:file_picker/file_picker.dart';
import 'package:image_picker/image_picker.dart';
import 'package:path_provider/path_provider.dart';

/// Where the bytes came from. Maps to Appendix A's `evidence_document.source`.
enum CaptureSource {
  camera('mobile_camera'),
  photoLibrary('mobile_file'),
  file('mobile_file');

  const CaptureSource(this.wire);

  /// `mobile_camera` only for a photo taken now. A picture chosen from the library is a file
  /// the crew member already had, and the distinction is what lets a reviewer tell a photo of a
  /// certificate on a desk from a screenshot of an email.
  final String wire;
}

/// A file the crew member chose, staged inside the app container and ready to upload.
class CapturedEvidence {
  const CapturedEvidence({
    required this.path,
    required this.contentType,
    required this.size,
    required this.sha256,
    required this.source,
  });

  final String path;
  final String contentType;
  final int size;

  /// Hex SHA-256 of the staged bytes. The server verifies it on completion (MOB-5a), which is
  /// what makes a resumed upload safe to trust.
  final String sha256;

  final CaptureSource source;
}

/// The crew member cancelled, which is not an error and must not be reported as one.
class CaptureCancelled implements Exception {}

class CaptureRejected implements Exception {
  CaptureRejected(this.message);

  final String message;

  @override
  String toString() => message;
}

/// SEC-7: the server's allow-list, mirrored. Extraction (§8 stage 2) reads images and PDFs and
/// nothing else, so anything past this is a file no pipeline stage could ever look at.
const acceptedContentTypes = <String, String>{
  'pdf': 'application/pdf',
  'jpg': 'image/jpeg',
  'jpeg': 'image/jpeg',
  'png': 'image/png',
  'heic': 'image/heic',
  'heif': 'image/heif',
};

/// The server's cap (`EvidenceService.MAX_SUBMISSION_BYTES`), mirrored for the same reason as
/// the type list: so the refusal happens before the upload rather than after it.
const maxSubmissionBytes = 64 * 1024 * 1024;

/// Seam for tests and for the device spike. The widget tests drive a fake; nothing in a test
/// touches a platform channel, which is what keeps `flutter test` runnable with no simulator.
abstract class EvidenceCapture {
  Future<CapturedEvidence> capture(CaptureSource source);
}

class PlatformEvidenceCapture implements EvidenceCapture {
  PlatformEvidenceCapture({ImagePicker? images}) : _images = images ?? ImagePicker();

  final ImagePicker _images;

  @override
  Future<CapturedEvidence> capture(CaptureSource source) async {
    final picked = switch (source) {
      CaptureSource.camera => await _pickImage(ImageSource.camera),
      CaptureSource.photoLibrary => await _pickImage(ImageSource.gallery),
      CaptureSource.file => await _pickFile(),
    };

    return _stage(picked, source);
  }

  Future<String> _pickImage(ImageSource from) async {
    final file = await _images.pickImage(
      source: from,
      // A 12-megapixel phone photo is ~5 MB of mostly-invisible detail. Capping the long edge
      // keeps a certificate legible to the extractor while making the upload survivable on a
      // maritime link — which is the constraint that actually decides whether this feature is
      // usable (LLM-5, MOB-5a).
      maxWidth: 2400,
      maxHeight: 2400,
      imageQuality: 85,
    );
    if (file == null) throw CaptureCancelled();
    return file.path;
  }

  Future<String> _pickFile() async {
    final result = await FilePicker.pickFiles(
      type: FileType.custom,
      allowedExtensions: acceptedContentTypes.keys.toList(growable: false),
      // Path, not bytes: a 64 MB attachment read into memory to then be written back out is
      // 64 MB of heap for nothing. The staging copy below streams it.
      withData: false,
    );
    final path = result?.files.single.path;
    if (path == null) throw CaptureCancelled();
    return path;
  }

  Future<CapturedEvidence> _stage(String pickedPath, CaptureSource source) async {
    final extension = pickedPath.split('.').last.toLowerCase();
    final contentType = acceptedContentTypes[extension];
    if (contentType == null) {
      throw CaptureRejected(
        'A ${extension.toUpperCase()} cannot be read as evidence. '
        'Attach a PDF or a photo.',
      );
    }

    final picked = File(pickedPath);
    final size = await picked.length();
    if (size == 0) throw CaptureRejected('That file is empty.');
    if (size > maxSubmissionBytes) {
      throw CaptureRejected(
        'That file is ${_megabytes(size)} MB. The limit is '
        '${maxSubmissionBytes ~/ (1024 * 1024)} MB.',
      );
    }

    final directory = Directory(
      '${(await getApplicationSupportDirectory()).path}/evidence',
    );
    await directory.create(recursive: true);

    // The staged name is the digest, so re-picking the same file twice cannot leave two copies
    // on a device whose storage is the reason the picker's temp file vanished in the first place.
    final digest = await _sha256(picked);
    final staged = File('${directory.path}/$digest.$extension');
    if (!await staged.exists()) await picked.copy(staged.path);

    return CapturedEvidence(
      path: staged.path,
      contentType: contentType,
      size: size,
      sha256: digest,
      source: source,
    );
  }

  static String _megabytes(int bytes) => (bytes / (1024 * 1024)).toStringAsFixed(1);

  /// Streamed rather than `sha256.convert(await file.readAsBytes())`: a 64 MB submission read
  /// whole is 64 MB of heap on a device that may have little of it.
  static Future<String> _sha256(File file) async {
    late Digest digest;
    final output = ChunkedConversionSink<Digest>.withCallback(
      (digests) => digest = digests.single,
    );
    final input = sha256.startChunkedConversion(output);
    await for (final chunk in file.openRead()) {
      input.add(chunk);
    }
    input.close();
    return digest.toString();
  }
}
