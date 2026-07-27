import 'dart:convert';
import 'dart:io';

import 'package:crewcomp_crew/src/api/crewcomp_api.dart';
import 'package:crewcomp_crew/src/data/database_opener.dart';
import 'package:crewcomp_crew/src/data/evidence_uploader.dart';
import 'package:crewcomp_crew/src/data/local_store.dart';
import 'package:drift/drift.dart' as drift;
import 'package:flutter_test/flutter_test.dart';
import 'package:http/testing.dart';
import 'package:http/http.dart' as http;

/// MOB-5a: the resumable half of the upload gauntlet, against a stubbed server that keeps a real
/// offset and refuses anything that does not line up with it.
///
/// The point of these is the *seek*. An uploader that only ever appends from zero passes a happy
/// path and then writes a hole into the first file it retries.
void main() {
  late LocalStore store;
  late Directory scratch;

  setUp(() async {
    store = LocalStore(InMemoryOpener().open());
    scratch = await Directory.systemTemp.createTemp('evidence-test');
  });

  tearDown(() async {
    await store.close();
    if (scratch.existsSync()) await scratch.delete(recursive: true);
  });

  Future<File> stagedFile(String name, int bytes) async {
    final file = File('${scratch.path}/$name');
    await file.writeAsBytes(List<int>.generate(bytes, (i) => i % 256));
    return file;
  }

  Future<void> insertSubmission({
    required String publicId,
    required String path,
    required int size,
    bool complete = false,
  }) =>
      store.into(store.submissions).insert(
            SubmissionsCompanion.insert(
              publicId: publicId,
              source: 'mobile_camera',
              contentType: const drift.Value('image/jpeg'),
              declaredSize: drift.Value(size),
              uploadComplete: drift.Value(complete),
              verificationStatus: 'pending_extraction',
              submittedAt: DateTime.utc(2026, 7, 27),
              localPath: drift.Value(path),
            ),
          );

  /// A server that holds one offset and enforces it, exactly as `EvidenceService.appendChunk`
  /// does — including the 409 that carries the truth.
  ({MockClient client, List<int> received, List<int> chunkSizes}) fakeServer({
    int startAt = 0,
    Set<String> unknownIds = const {},
  }) {
    final received = <int>[];
    final chunkSizes = <int>[];
    var offset = startAt;

    final client = MockClient((request) async {
      final publicId = request.url.pathSegments[3];
      if (unknownIds.contains(publicId)) {
        return http.Response(jsonEncode({'detail': 'No such document'}), 404);
      }

      if (request.method == 'GET') {
        return http.Response(
          jsonEncode({'offset': offset, 'complete': false, 'declaredSize': null}),
          200,
        );
      }

      final sent = int.parse(request.headers['Upload-Offset']!);
      if (sent != offset) {
        return http.Response(
          jsonEncode({'detail': 'out of order'}),
          409,
          headers: {'upload-offset': '$offset'},
        );
      }

      chunkSizes.add(request.bodyBytes.length);
      received.addAll(request.bodyBytes);
      offset += request.bodyBytes.length;
      return http.Response(
        jsonEncode({'offset': offset, 'complete': false, 'declaredSize': null}),
        200,
      );
    });

    return (client: client, received: received, chunkSizes: chunkSizes);
  }

  EvidenceUploader uploaderFor(MockClient client, {int chunkSize = 256 * 1024}) => EvidenceUploader(
        api: CrewcompApi(baseUrl: Uri.parse('http://localhost'), client: client),
        store: store,
        chunkSize: chunkSize,
      );

  test('sends the whole file in chunks and marks the submission complete', () async {
    final file = await stagedFile('cert.jpg', 1000);
    // Read before uploading: a completed upload deletes the staged copy, which is the point.
    final whole = await file.readAsBytes();
    await insertSubmission(publicId: 'doc-1', path: file.path, size: 1000);

    final server = fakeServer();
    final completed = await uploaderFor(server.client, chunkSize: 256).uploadPending();

    expect(completed, 1);
    expect(server.received, whole);
    expect(server.chunkSizes, [256, 256, 256, 232], reason: 'the last chunk is the remainder');

    final row = await (store.select(store.submissions)
          ..where((t) => t.publicId.equals('doc-1')))
        .getSingle();
    expect(row.uploadComplete, isTrue);
    expect(row.uploadOffset, 1000);
  });

  test('resumes from the offset the server reports, not from zero', () async {
    final file = await stagedFile('cert.jpg', 1000);
    final whole = await file.readAsBytes();
    await insertSubmission(publicId: 'doc-1', path: file.path, size: 1000);

    // The server already has the first 400 bytes from an attempt that died mid-flight.
    final server = fakeServer(startAt: 400);
    await uploaderFor(server.client, chunkSize: 256).uploadPending();

    expect(server.received, whole.sublist(400),
        reason: 'only the tail should travel a second time');
    expect(server.chunkSizes, [256, 256, 88]);
  });

  test('a 409 is a seek, not a restart', () async {
    final file = await stagedFile('cert.jpg', 600);
    final whole = await file.readAsBytes();
    await insertSubmission(publicId: 'doc-1', path: file.path, size: 600);

    // The device thinks it is at 0; the server is at 200 because a chunk it had given up on
    // landed after all. The first append must 409, and the uploader must continue from 200.
    var stateCalls = 0;
    final received = <int>[];
    var offset = 200;
    final client = MockClient((request) async {
      if (request.method == 'GET') {
        stateCalls += 1;
        // Lie on the first ask, to force the 409 path rather than a clean resume.
        return http.Response(
          jsonEncode({'offset': 0, 'complete': false, 'declaredSize': null}),
          200,
        );
      }
      final sent = int.parse(request.headers['Upload-Offset']!);
      if (sent != offset) {
        return http.Response(
          jsonEncode({'detail': 'out of order'}),
          409,
          headers: {'upload-offset': '$offset'},
        );
      }
      received.addAll(request.bodyBytes);
      offset += request.bodyBytes.length;
      return http.Response(
        jsonEncode({'offset': offset, 'complete': false, 'declaredSize': null}),
        200,
      );
    });

    await uploaderFor(client, chunkSize: 256).uploadPending();

    expect(stateCalls, 1);
    expect(received, whole.sublist(200), reason: 'seek to 200 and carry on from there');
  });

  test('deletes the staged file once the bytes are the server\'s', () async {
    final file = await stagedFile('cert.jpg', 300);
    await insertSubmission(publicId: 'doc-1', path: file.path, size: 300);

    await uploaderFor(fakeServer().client, chunkSize: 256).uploadPending();

    expect(file.existsSync(), isFalse);
    final row = await (store.select(store.submissions)
          ..where((t) => t.publicId.equals('doc-1')))
        .getSingle();
    expect(row.localPath, isNull, reason: 'nothing should still point at a deleted file');
  });

  test('skips a submission the server has never heard of, leaving it queued', () async {
    final file = await stagedFile('cert.jpg', 300);
    await insertSubmission(publicId: 'doc-1', path: file.path, size: 300);

    // 404: `evidence.submit` has not been flushed yet. The next sync flushes first.
    final completed =
        await uploaderFor(fakeServer(unknownIds: {'doc-1'}).client).uploadPending();

    expect(completed, 0);
    final row = await (store.select(store.submissions)
          ..where((t) => t.publicId.equals('doc-1')))
        .getSingle();
    expect(row.uploadComplete, isFalse);
    expect(row.localPath, file.path, reason: 'still uploadable on the next attempt');
    expect(file.existsSync(), isTrue);
  });

  test('says so when the staged file has gone, rather than retrying forever', () async {
    await insertSubmission(publicId: 'doc-1', path: '${scratch.path}/never-existed.jpg', size: 10);

    final completed = await uploaderFor(fakeServer().client).uploadPending();

    expect(completed, 0);
    final row = await (store.select(store.submissions)
          ..where((t) => t.publicId.equals('doc-1')))
        .getSingle();
    expect(row.localPath, isNull);
    expect(row.rejectionReason, contains('no longer on this device'));
  });

  test('one unreachable submission does not stop the next one', () async {
    final good = await stagedFile('good.jpg', 300);
    final bad = await stagedFile('bad.jpg', 300);
    await insertSubmission(publicId: 'doc-bad', path: bad.path, size: 300);
    await insertSubmission(publicId: 'doc-good', path: good.path, size: 300);

    var offset = 0;
    final client = MockClient((request) async {
      final publicId = request.url.pathSegments[3];
      if (publicId == 'doc-bad') throw const SocketException('no route to host');
      if (request.method == 'GET') {
        return http.Response(
          jsonEncode({'offset': offset, 'complete': false, 'declaredSize': null}),
          200,
        );
      }
      offset += request.bodyBytes.length;
      return http.Response(
        jsonEncode({'offset': offset, 'complete': false, 'declaredSize': null}),
        200,
      );
    });

    final completed = await uploaderFor(client).uploadPending();

    expect(completed, 1, reason: 'the good one still goes');
    final rows = await store.select(store.submissions).get();
    expect(
      rows.firstWhere((r) => r.publicId == 'doc-good').uploadComplete,
      isTrue,
    );
    expect(
      rows.firstWhere((r) => r.publicId == 'doc-bad').uploadComplete,
      isFalse,
      reason: 'and the unreachable one stays queued',
    );
  });
}
