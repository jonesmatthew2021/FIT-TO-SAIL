# ADR 0009 — Mobile sync contract, encrypted local store, and resumable upload

**Status:** Accepted (2026-07-26)
**Spec refs:** §7 (MOB-1..6), §7.6, §10.3, §8 stage 1, AUTH-1, AUTH-2, AUTH-4, SEC-12, SEC-13, NFR-5, O-11
**Related:** ADR 0002 (Flutter — **amended here**, see §1), ADR 0003 (identity), ADR 0008 (admin web: the
same build-time-removal and generated-types patterns)

## Context

§10.3 fixes the sync *contract* (`snapshot`, `delta?cursor=`, `queue`, chunked upload) and leaves
the mechanism to implementation. Building the crew app forced five decisions that contract does
not make, and turned up one thing ADR 0002 got wrong.

## Decision

### 1. The encrypted store is selected by a build hook, not a package (amends ADR 0002)

ADR 0002 specifies "drift + SQLite3MultipleCiphers … not SQLCipher", via the
`sqlite3_flutter_libs` family of packages. **Those packages are end-of-life.** Since `sqlite3`
3.x the package bundles its own native SQLite through Dart's build hooks, and both
`sqlite3_flutter_libs` and `sqlcipher_flutter_libs` now publish as `+eol` versions containing no
code. The cipher build is chosen in `pubspec.yaml` instead:

```yaml
hooks:
  user_defines:
    sqlite3:
      source: sqlite3mc
```

The choice of SQLite3MultipleCiphers over SQLCipher is unchanged and still right. Only the
mechanism moves. This has a pleasant side effect: because the library is bundled for the host
platform too, the encryption is testable on a development machine rather than only on a device.

**And it is tested, not asserted.** `test/encrypted_store_test.dart` writes a distinctive crew
name through the real opener, reads the file back as raw bytes, and fails if the name is in
them — plus a wrong-key open that must fail. This matters more than it looks: plain SQLite
**silently ignores** an unknown `pragma key`, so an app that lost the hook would run perfectly,
sync perfectly, and store everything readable. `main()` refuses to start if
`sqlite3mc_version()` is unavailable.

**Confirmed on iOS (26 July 2026).** The hook was written before any platform build was possible;
it has since been validated on Xcode 26.6. `sqlite3mc.framework` builds for the `arm64` device and
simulator slices exporting `_sqlite3mc_version` and `_sqlite3_key_v2`, and the store written by
the app in its iOS container is unreadable as SQLite and contains no crew name in plaintext. The
host test was therefore a good proxy, not a substitute — but it did predict the real result.

Detecting the cipher build is fiddly and two obvious probes do not work: `pragma compile_options`
is identical to a plain build's (the ciphers are a codec layer, not a compile flag), and
`pragma cipher_version` returns an empty result set, exactly as any unknown pragma does. The SQL
function `sqlite3mc_version()` is the discriminator.

### 2. `updated_seq` is stamped by a database trigger, not by application code

The delta needs a monotonic per-row cursor. The obvious implementation is a field on
`AuditedEntity` maintained by the write paths; V2 uses a `BEFORE INSERT OR UPDATE` trigger over a
single global sequence instead, with `AFTER DELETE` triggers writing tombstones.

The reason is the failure mode, not elegance. A cursor a write path can forget is a cursor that
silently stops replicating: the client syncs successfully and simply never sees that row again,
with nothing anywhere reporting an error. A trigger cannot be forgotten by a new service, an MCP
tool, a Flyway data fix-up, or a `psql` session. The cost — `updated_seq` is stale on a
Hibernate-managed instance until refreshed — is paid by mapping it read-only and never reading it
outside a sync query.

`cache 1` on the sequence is deliberate: a cached sequence hands out per-session blocks, which
would let a later commit take a *lower* number than an earlier one and fall permanently behind a
client's cursor.

### 3. Reference data is versioned coarsely, person data finely

Person-scoped rows (holdings, assignments, leave, notifications, submissions) stream row-by-row
with tombstones. Reference data — the requirement catalogue, the published matrix and its rules —
does not: the client compares a single `referenceCursor` and re-snapshots when it moves.

A matrix publication changes the meaning of most of a device's cached rules at once. Applying
half of one would render a coherent-looking view assembled from two matrix versions, which is a
worse outcome than a slightly larger download a few times a year.

### 4. The queue returns a verdict per operation, and `rejected` means "stop"

`POST /sync/queue` always answers 200; the body carries `applied` / `rejected` / `failed` per
entry, each applied in its own transaction.

An all-or-nothing batch is the wrong shape for a durable offline queue. One malformed entry would
fail the batch, the client would retry the batch, and every good entry behind the poison one
would stop moving — offline, on a vessel, indefinitely. Splitting the verdicts lets the device
drop what will never succeed and keep what will. An entry that exhausts its retries is **kept**
with its error rather than dropped, because §7.6 requires an explicit user-visible failure and
silently discarding a crew member's submission is the one outcome that is not acceptable.

### 5. Resumable upload is a small HTTP protocol, not tus

`GET`/`POST /api/v1/evidence/{publicId}/chunks` with an `Upload-Offset` header. The server holds
the offset; a chunk at the wrong offset gets **409 plus the offset to seek to**, so a confused
client seeks instead of restarting a 20 MB transfer over maritime connectivity. A replayed chunk
is a no-op, which is what makes retry safe. Completion verifies the device's declared SHA-256 and
*fails the document* on mismatch rather than feeding corrupt bytes to extraction (§8).

Chunks are stored as separate objects and concatenated on completion, because `ObjectStorage`
offers put/get and deliberately not append (ADR 0005). Submission *metadata* travels through the
sync queue rather than this endpoint, so a capture made in a dead spot is recorded immediately
and the bytes follow over however many connectivity windows it takes.

### 6. The sync service cannot be asked about anyone else

`SyncService` takes no person id in any method, and no request carries one. It answers for
`policy.actor().personId` and nothing else. This is stronger than a scope check on a supplied id:
the whole-fleet leak is not a missing `if`, it is unreachable. Back-office actors have no Person
record and get 403 — "this endpoint is not for you" rather than an empty snapshot.

The engine's evaluation travels *in* the payload (`standing`), pre-computed, because AUTH-1
forbids a client deciding what a cell state is — and on a device that decision would be made
offline where nobody can see it happen. It is recomputed on every delta rather than diffed: a
roll-up changes when a holding expires overnight, with no row changing at all.

## Consequences

- The crew app works with no connectivity, which is the point, and every screen is a pure
  function of the local replica.
- `LocalDate` is `String` in Dart, never `DateTime`. A Dart `DateTime` always carries a time and
  a zone, so parsing `2026-08-01` yields a different day depending on where the phone is;
  `domain/calendar.dart` does calendar arithmetic on the strings, mirroring the admin SPA.
- Dart API types are generated from the same OpenAPI schema as the admin SPA's (DEV-2), by a
  200-line generator rather than `openapi-generator` — which would bring an entire HTTP client
  and dependency stack, against the small-tree argument ADR 0002 rests on.
- Screens take data, not streams. A widget owning a drift stream drags the database's timers into
  the widget-test fake-async zone, where the framework's pending-timer invariant fires and the
  run hangs; the split makes the presentation testable and is better structure regardless.
- **Not yet real:** push delivery (APNs/FCM needs a Firebase project and signing), background
  upload that survives the app being killed *while backgrounded* (`background_downloader` — Xcode
  is now available, so this is buildable work rather than a blocked lane), camera capture (MOB-4's
  capture half), and sign-in (MOB-6 — the same header shim as the admin SPA, compiled out of
  release builds by `kDebugMode`).
- **iOS enforces no transport security for us.** `dart:io` bypasses App Transport Security
  entirely, which is why cleartext to `127.0.0.1` needs no `Info.plist` exception. The corollary
  is that a release build pointed at an `http://` base URL would ship crew personal data in the
  clear with nothing objecting, so the `https`-unless-`kDebugMode` check has to be written in
  Dart. Not yet done.

## Alternatives considered

**A change-log table instead of per-row `updated_seq`.** Equivalent for correctness and better
for high-churn tables, but it needs every write path to append to it — the same forgettability
problem — unless it is also trigger-written, at which point it is strictly more machinery for the
same guarantee. Tombstones already are that table, for the one case a per-row column cannot cover.

**tus for resumable upload.** A real standard with real client libraries, and worth revisiting if
uploads get harder. It was not adopted now because the protocol above is about forty lines of
server code against a contract §10.3 already fixed, and tus would add a dependency and a second
notion of upload identity beside the client-generated `publicId` that already exists for
idempotency.

**Letting the device compute expiry states offline.** Tempting — it has the holdings and the
dates. Rejected: AUTH-1, and more practically the device would have to re-implement §5.1
precedence, quota footnotes and the exemption overlay, then keep up with them. §7.6 permits
counting *days* against synced data, which is what the app does.
