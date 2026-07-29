import 'dart:convert';

import 'package:http/http.dart' as http;

import 'schema.g.dart';

/// The HTTP client for the §10.3 contract.
///
/// Authentication is the same story as the admin SPA's (ADR 0003): the real thing is an OIDC
/// code flow through the system browser with tokens held outside JavaScript — here, outside Dart
/// — and it is the identity spike's deliverable. Until it lands, [DevIdentity] supplies the same
/// header-based development shim the backend carries, and it is compiled out of a release build
/// exactly as the backend's half is removed from a production artefact.
class CrewcompApi {
  CrewcompApi({required this.baseUrl, http.Client? client, this.devIdentity})
      : _client = client ?? http.Client();

  final Uri baseUrl;
  final http.Client _client;

  /// Non-null only in debug builds — see [DevIdentity].
  final DevIdentity? devIdentity;

  Future<SyncSnapshotDto> snapshot() async {
    final json = await _getJson('/api/v1/sync/snapshot');
    return SyncSnapshotDto.fromJson(json);
  }

  Future<SyncDeltaDto> delta(int cursor) async {
    final json = await _getJson('/api/v1/sync/delta?cursor=$cursor');
    return SyncDeltaDto.fromJson(json);
  }

  Future<SyncQueueResultDto> queue(List<SyncOperationDto> operations) async {
    final response = await _client.post(
      baseUrl.resolve('/api/v1/sync/queue'),
      headers: {'content-type': 'application/json', ..._authHeaders()},
      body: jsonEncode({
        'operations': operations.map((o) => o.toJson()).toList(growable: false),
      }),
    );
    _check(response);
    return SyncQueueResultDto.fromJson(
      jsonDecode(response.body) as Map<String, dynamic>,
    );
  }

  /// MOB-11's watch, or **null when this person does not supervise one**.
  ///
  /// The 403 is the answer, not an error. Whether somebody holds the supervisory role is the
  /// server's to decide (§3, AUTH-1), and asking it is how the app finds out — the alternative
  /// was a `kDebugMode` compile-time flag that a release build could never reach and that no
  /// deployment could ever change. Every other status still throws.
  Future<TeamDto?> team() async {
    final response = await _client.get(
      baseUrl.resolve('/api/v1/me/team'),
      headers: _authHeaders(),
    );
    if (response.statusCode == 403) return null;
    _check(response);
    return TeamDto.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  /// Where a resuming upload should continue from (MOB-5a).
  Future<UploadStateDto> uploadState(String publicId) async {
    final json = await _getJson('/api/v1/evidence/$publicId/chunks');
    return UploadStateDto.fromJson(json);
  }

  /// Appends one chunk at [offset].
  ///
  /// A 409 means the server is at a different offset and has told us which; that is a seek, not
  /// a failure, so it surfaces as [ChunkOutOfOrder] rather than as a generic error.
  Future<UploadStateDto> appendChunk(
    String publicId,
    int offset,
    List<int> bytes,
  ) async {
    final response = await _client.post(
      baseUrl.resolve('/api/v1/evidence/$publicId/chunks'),
      headers: {
        'content-type': 'application/octet-stream',
        'Upload-Offset': '$offset',
        ..._authHeaders(),
      },
      body: bytes,
    );

    if (response.statusCode == 409) {
      final expected = int.tryParse(response.headers['upload-offset'] ?? '');
      throw ChunkOutOfOrder(expected ?? 0);
    }
    _check(response);
    return UploadStateDto.fromJson(
      jsonDecode(response.body) as Map<String, dynamic>,
    );
  }

  Future<Map<String, dynamic>> _getJson(String path) async {
    final response = await _client.get(
      baseUrl.resolve(path),
      headers: _authHeaders(),
    );
    _check(response);
    return jsonDecode(response.body) as Map<String, dynamic>;
  }

  Map<String, String> _authHeaders() {
    // In a release build `devIdentity` is always null: nothing constructs one, because the only
    // call site is behind `kDebugMode` and the tree-shaker removes the rest.
    final identity = devIdentity;
    if (identity == null) return const {};
    return identity.headers();
  }

  void _check(http.Response response) {
    if (response.statusCode >= 200 && response.statusCode < 300) return;
    throw ApiException(response.statusCode, _detail(response));
  }

  String? _detail(http.Response response) {
    try {
      final body = jsonDecode(response.body);
      if (body is Map && body['detail'] is String) return body['detail'] as String;
      if (body is Map && body['error'] is String) return body['error'] as String;
    } on FormatException {
      // A non-JSON error body is not worth a second failure.
    }
    return null;
  }

  void close() => _client.close();
}

class ApiException implements Exception {
  ApiException(this.statusCode, this.detail);

  final int statusCode;
  final String? detail;

  bool get isUnauthenticated => statusCode == 401;
  bool get isForbidden => statusCode == 403;

  /// 4xx other than 401/408/429 will not succeed on retry — the queue drops these rather than
  /// spinning on them.
  bool get isPermanent =>
      statusCode >= 400 &&
      statusCode < 500 &&
      statusCode != 401 &&
      statusCode != 408 &&
      statusCode != 429;

  @override
  String toString() => 'HTTP $statusCode${detail == null ? '' : ': $detail'}';
}

/// The server is at a different byte offset than the client assumed. Carries where to seek to.
class ChunkOutOfOrder implements Exception {
  ChunkOutOfOrder(this.expectedOffset);

  final int expectedOffset;

  @override
  String toString() => 'Server expects the next chunk at byte $expectedOffset';
}

/// The development sign-in shim, mirroring the backend's `DevAuth`.
///
/// It asks for no password on purpose — a shim with a fake credential invites being mistaken for
/// authentication, one that plainly sets a header cannot be. Constructed only under
/// `kDebugMode`, so a release build contains neither the header names nor a way to set them.
class DevIdentity {
  const DevIdentity({
    required this.personId,
    required this.label,
    this.supervisor = false,
    this.partnershipIds = const <int>[],
  });

  final int personId;
  final String label;

  /// Claims `vessel_master` alongside `crew_member`, for driving MOB-11 locally.
  ///
  /// A **claim about the shim's identity**, not a switch on the app: the tab still appears only
  /// because `/api/v1/me/team` answered 200, which is the server deciding. That is the difference
  /// from the `CREWCOMP_DEV_SUPERVISOR` flag this replaced — that one turned the screen on
  /// regardless of whether any watch existed.
  final bool supervisor;

  /// The partnerships a Vessel Master is scoped to (§3). The real thing comes from the account.
  final List<int> partnershipIds;

  Map<String, String> headers() => {
        'X-Dev-User': label,
        'X-Dev-Roles': supervisor ? 'crew_member,vessel_master' : 'crew_member',
        'X-Dev-Person-Id': '$personId',
        if (partnershipIds.isNotEmpty)
          'X-Dev-Partnerships': partnershipIds.join(','),
      };
}
