/// MOB-5a: pushing a submission's bytes, resumably.
///
/// The queue entry and the bytes travel separately and that separation is the design, not an
/// accident of layering. `evidence.submit` is a small idempotent operation that goes through the
/// ordinary outbox with every other write; the file is a multi-megabyte transfer over a link
/// that may drop halfway. Putting the bytes in the queue entry would make the whole queue block
/// on the worst connection the device ever sees.
///
/// So: **the server is always asked where it got to before a single byte is sent.** The device's
/// own idea of the offset is a cache, and a wrong one is how a resumed upload writes a hole into
/// the middle of a file that then fails its digest check with nothing to point at. Two things
/// follow — a 409 carrying the true offset is a seek rather than an error, and a submission the
/// server has never heard of is skipped rather than started, because its outbox entry has not
/// been flushed yet.
library;

import 'dart:async';
import 'dart:io';
import 'dart:math' as math;

import 'package:drift/drift.dart';

import '../api/crewcomp_api.dart';
import '../api/schema.g.dart';
import 'local_store.dart';

/// 256 KB. Small enough that a drop costs little and a progress bar moves; large enough that a
/// 2 MB certificate is eight round trips rather than five hundred.
const defaultChunkSize = 256 * 1024;

class EvidenceUploader {
  EvidenceUploader({
    required this.api,
    required this.store,
    this.chunkSize = defaultChunkSize,
  });

  final CrewcompApi api;
  final LocalStore store;
  final int chunkSize;

  /// Uploads every submission that still has bytes on the device. Returns how many finished.
  ///
  /// Never throws: this runs inside the ordinary sync cycle, and a submission that cannot upload
  /// right now is the normal offline case rather than a failure of the sync. The row keeps its
  /// local path, so the next sync tries again.
  Future<int> uploadPending() async {
    final pending = await (store.select(store.submissions)
          // Only registrations the office has accepted: a queued one has nothing server-side to
          // append to yet, and a failed one would 404 here on every sync, forever, describing a
          // refusal as an upload in progress (issue #13).
          ..where((t) =>
              t.localPath.isNotNull() &
              t.uploadComplete.equals(false) &
              t.sendState.equals('sent'))
          ..orderBy([(t) => OrderingTerm(expression: t.submittedAt)]))
        .get();

    var completed = 0;
    for (final submission in pending) {
      try {
        if (await uploadOne(submission)) completed += 1;
      } on Exception {
        // Deliberately swallowed, and only here. One unreachable server must not stop the
        // submission behind it from trying, and must not fail the sync that called us.
        continue;
      }
    }
    return completed;
  }

  /// Returns true when the bytes are fully on the server.
  Future<bool> uploadOne(LocalSubmission submission) async {
    final path = submission.localPath;
    if (path == null) return false;

    final file = File(path);
    if (!await file.exists()) {
      // The staged copy is gone — deleted by the OS, or by a reinstall. Nothing can finish this
      // upload, so stop claiming it is in flight and say why on the screen.
      await _markUnrecoverable(submission.publicId, 'The file is no longer on this device');
      return false;
    }
    final total = await file.length();

    // Ask first, always. See the note at the top of the file.
    final UploadStateDto state;
    try {
      state = await api.uploadState(submission.publicId);
    } on ApiException catch (e) {
      // 404: the `evidence.submit` operation has not been flushed yet, so there is nothing to
      // append to. The next sync flushes the outbox before it gets here.
      if (e.statusCode == 404) return false;
      rethrow;
    }

    if (state.complete) {
      await _finish(submission.publicId, total);
      return true;
    }

    var offset = state.offset;
    final handle = await file.open();
    try {
      while (offset < total) {
        await handle.setPosition(offset);
        final bytes = await handle.read(math.min(chunkSize, total - offset));

        try {
          final result = await api.appendChunk(submission.publicId, offset, bytes);
          offset = result.offset;
        } on ChunkOutOfOrder catch (e) {
          // The server is somewhere else — a chunk that landed after we gave up on it, most
          // likely. Seek to where it actually is and carry on; restarting would re-send
          // everything already accepted.
          offset = e.expectedOffset;
          continue;
        }

        await _recordProgress(submission.publicId, offset);
      }
    } finally {
      await handle.close();
    }

    await _finish(submission.publicId, total);
    return true;
  }

  Future<void> _recordProgress(String publicId, int offset) =>
      (store.update(store.submissions)..where((t) => t.publicId.equals(publicId)))
          .write(SubmissionsCompanion(uploadOffset: Value(offset)));

  /// The bytes are the server's now, so the staged copy goes.
  ///
  /// Deleting it is what stops the app container growing by a certificate every time somebody
  /// submits one. The row stays — it is the crew member's record of having submitted, and §8's
  /// verdict arrives on it later.
  Future<void> _finish(String publicId, int total) async {
    final row = await (store.select(store.submissions)
          ..where((t) => t.publicId.equals(publicId)))
        .getSingleOrNull();

    final path = row?.localPath;
    if (path != null) {
      try {
        final staged = File(path);
        if (await staged.exists()) await staged.delete();
      } on FileSystemException {
        // Leaving a stray file behind is not worth failing a completed upload over.
      }
    }

    await (store.update(store.submissions)..where((t) => t.publicId.equals(publicId))).write(
      SubmissionsCompanion(
        uploadOffset: Value(total),
        uploadComplete: const Value(true),
        localPath: const Value(null),
      ),
    );
  }

  Future<void> _markUnrecoverable(String publicId, String reason) =>
      (store.update(store.submissions)..where((t) => t.publicId.equals(publicId))).write(
        SubmissionsCompanion(
          localPath: const Value(null),
          rejectionReason: Value(reason),
        ),
      );
}
