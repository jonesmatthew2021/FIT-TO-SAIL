# mobile/ — Flutter crew self-service app (iOS + Android)

The offline spine and three of §7's screens exist: MOB-1 my certifications, MOB-2 my roster,
MOB-3 notifications, over an encrypted local store with snapshot + delta sync and a durable
outbound queue (MOB-5). Evidence submission is half-built: the backend, the queue entry and the
resumable upload work end to end; the camera does not exist yet.

**iOS builds and runs; Android has never been built.** Xcode 26.6 / iOS 26.5 SDK is installed
here, so `flutter build ios` and a simulator run are verified (see below for exactly what that
proved). There is still no Android SDK, so `flutter build apk` is the remaining unrun lane — which
matters, because ADR 0002 mandates feature parity and nothing enforces it yet. See "What is
unverified" before trusting anything else about the app on a phone.

## Stack (decided — ADR 0002, amended by ADR 0009)

Flutter 3.44 stable / Dart 3.12. Runtime dependencies, all of them: `drift` (local store),
`sqlite3`, `path_provider`, `flutter_secure_storage`, `http`. Dev: `drift_dev`, `build_runner`,
`flutter_lints`.

The encrypted store is **SQLite3MultipleCiphers selected through a build hook**, not through a
`*_flutter_libs` package — those are end-of-life since `sqlite3` 3.x bundles its own native
library. ADR 0002 says otherwise and ADR 0009 §1 corrects it. The hook lives in `pubspec.yaml`:

```yaml
hooks:
  user_defines:
    sqlite3:
      source: sqlite3mc
```

Delete that and the app stores crew records in the clear — silently, because plain SQLite
*ignores* an unknown `pragma key` rather than rejecting it. `main()` refuses to start without it
and `test/encrypted_store_test.dart` fails.

## Build and test

```bash
flutter test                              # 43 tests
flutter analyze                           # clean
dart run tool/generate_api.dart           # regenerate lib/src/api/schema.g.dart
dart run tool/generate_api.dart --check   # fail if committed types are stale — the CI check
dart run build_runner build               # regenerate drift's local_store.g.dart
```

`flutter` here is `/opt/homebrew/share/flutter/bin` (Homebrew cask). Note that `dart` on the
PATH may be the standalone Homebrew Dart, a different version from Flutter's bundled one — put
`/opt/homebrew/share/flutter/bin` first, or `flutter doctor` warns about it.

The full local loop needs the backend. Quarkus Dev Services drives Testcontainers, which looks
for `/var/run/docker.sock` and will **not** find Colima's socket on its own:

```bash
export DOCKER_HOST=unix:///Users/chrisjones/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
(cd ../backend && CREWCOMP_MCP_TOKEN=<32+ chars> ./mvnw quarkus:dev)

xcrun simctl boot 'iPhone 17 Pro'
open /Applications/Xcode.app/Contents/Developer/Applications/Simulator.app
flutter run -d <simulator-udid> --dart-define=CREWCOMP_DEV_PERSON=2
```

Without `DOCKER_HOST` the backend fails at `DevServicesDatasourceProcessor#launchDatabases` and
every screen shows "Never synced". `open -a Simulator` does not resolve — the app lives inside
Xcode, at the path above.

To inspect a running app without the `flutter run` console:

```bash
xcrun simctl io <udid> screenshot /tmp/sim.png
xcrun simctl get_app_container <udid> au.crewcomp.crewcompCrew data   # the encrypted store
```

Person 2 in the dev fixture is Bruno Oyelaran, chosen as the demonstration crew member because
his data has the interesting shape: assigned to the swing in progress, one holding expiring
inside it, one confirmed not-held, three notifications and a leave record.

**Only one `flutter test` may run against this directory at a time.** Two concurrent runs
deadlock on the build directory and neither makes progress, which looks exactly like a hanging
test.

## Non-negotiables for this component

- **No compliance logic here** (AUTH-1). Cell states, roll-ups and the standing arrive
  pre-computed in the sync payload and are rendered as received. `domain/states.dart` holds
  labels and colours and nothing else. An unrecognised state renders verbatim — never as "OK".
- **"Today" comes from the sync payload's `serverToday`** (NFR-5, O-11). Business dates are
  calendar dates in the operating timezone; a phone knows neither that zone nor the admin date
  override. `domain/calendar.dart` never constructs a `DateTime` from a business date. Elapsed
  *wall-clock* time ("last synced 20 min ago", the SEC-12 window) may use the device clock —
  that is a different question from "what day is it".
- **Every screen reads the local store, never the network.** A widget that awaits an HTTP call is
  blank on a vessel, which is exactly where this app is used.
- **The outbox is flushed before the pull.** Pull-first would apply server rows that predate the
  device's own queued writes, and a read-mark made offline would visibly un-tick itself.
- **The app never writes a holding** (§7.5). The only client-originated writes are evidence
  submissions and notification read-marks, both idempotent or monotonic by construction — which
  is what removes conflict resolution from this codebase entirely.
- **Push payloads carry title + deep link only** (SEC-13). The body is synced and shown in-app.
  The dev fixture's notification titles follow the rule too, so the screens are never accidentally
  designed around detail a real push cannot carry.
- **Development sign-in is compiled out.** `DevIdentity` is constructed only under `kDebugMode`,
  a compile-time constant, so a release build contains neither the header names nor a way to set
  them — the same guarantee the backend gives by removing its shim bean at build time.

## Layout

| Path | Contents |
|---|---|
| `lib/src/api/` | `schema.g.dart` (generated from the backend's OpenAPI, committed), `crewcomp_api.dart` (typed HTTP + the dev shim) |
| `lib/src/data/` | `local_store.dart` drift schema, `database_opener.dart` encryption and key handling, `sync_engine.dart` snapshot/delta/outbox |
| `lib/src/domain/` | `calendar.dart` calendar-date arithmetic, `states.dart` Appendix A presentation |
| `lib/src/ui/` | `app_state.dart` (the only thing that talks to the engine), `screens.dart` (`*Screen` wrappers do the streams, `*View` widgets are pure) |
| `tool/` | `generate_api.dart` — the DEV-2 generator |

## Traps this codebase has already paid for

- **A widget that owns a drift stream hangs its own test.** The database's timers land in the
  widget-test fake-async zone and the framework's "a Timer is still pending" invariant fires
  after the tree is disposed. Hence the `*Screen` / `*View` split: streams in the wrapper, pure
  data in the widget.
- **`pragma compile_options` cannot detect the cipher build**, and neither can
  `pragma cipher_version` — it returns an empty result set exactly as any unknown pragma does.
  Use `select sqlite3mc_version()`.
- **`library;` must precede imports.** A doc comment plus `library;` placed after the import block
  compiles under `flutter analyze` in some orders and breaks `build_runner`'s resolver.
- **drift exports `isNull`/`isNotNull`**, which collide with `matcher`'s. Test files that import
  both need `hide isNull, isNotNull`.
- **App Transport Security does not apply to this app's HTTP.** The `http` package goes through
  `dart:io`'s own socket stack, not `NSURLSession`, so iOS never sees the request and cleartext to
  `127.0.0.1` works with no `Info.plist` exception. Convenient in development and a trap in
  production: **iOS will not stop a release build talking plain HTTP**, so nothing but our own
  code can enforce TLS. See "What is unverified" #9.
- **The iOS system log is loud.** A booting simulator emits hundreds of `Failed to index parameter
  type …` ActionKit lines into the `flutter run` console. They are Shortcuts indexing, unrelated
  to this app; filter them out before reading a build log or watching for errors.

## What the iOS run proved (26 July 2026, Xcode 26.6, iPhone 17 Pro simulator)

Recorded because these were the four things nobody could check before, and three of them were
the ones most likely to be quietly wrong.

- **The `sqlite3mc` build hook produces a correct iOS artefact.** `Runner.app/Frameworks/`
  contains `sqlite3mc.framework`: `arm64`, `LC_BUILD_VERSION platform IOS`, exporting
  `_sqlite3mc_version` and `_sqlite3_key_v2`. The running app logs
  `Local store cipher: SQLite3 Multiple Ciphers 2.3.6`, and `main()` would have refused to start
  otherwise. Both the device and simulator slices build.
- **`flutter_secure_storage` works.** Not by a unit test but by consequence: the store cannot open
  without a Keychain-held key, and it opened. `Security.framework` and
  `LocalAuthentication.framework` link into `Runner` via SPM; no separate plugin framework and no
  entitlement file was needed for the simulator.
- **The store on a device is genuinely encrypted.** The file at
  `Library/Application Support/crewcomp.db` in the app container begins `a8da e3b9 …` rather than
  `SQLite format 3`, and `strings | grep Oyelaran` returns nothing while the app displays that
  name on screen.
- **Offline works.** With the backend stopped and the app relaunched from cold, every screen
  rendered from the local replica under an `Offline · last synced 3 min ago` banner. No error
  state, no blank list. This is the property the whole architecture exists for.

The first iOS build succeeded with no source changes — no signing, CocoaPods, ATS or podspec work
was needed.

## What is unverified

Listed plainly because the test count above could otherwise imply more than it should.

1. **No Android build has run** and no physical iPhone has run this. There is no Android SDK on
   this machine, so `android/` is still untouched `flutter create` output — a real risk given ADR
   0002 mandates feature parity. On iOS, everything above was a *simulator*; a physical device
   additionally needs code signing, and the Keychain behaves differently under a real
   `first_unlock` accessibility class and a locked screen.
2. **Nothing verifies the crew's data is wiped on logout** (SEC-12). There is no logout, because
   there is no login (#6).
3. **No push notifications.** MOB-3 renders the in-app list, which is the source of truth, but
   APNs/FCM delivery needs a Firebase project and signing identities. `firebase_messaging` is
   not a dependency yet.
4. **No camera capture.** MOB-4's submission and upload paths are built and tested; the thing
   that produces the file is not. Needs a device and the doc-scan wrapper from ADR 0002.
5. **Uploads do not survive backgrounding.** The outbox survives app *restarts* — it is a table —
   and resumes from the server-held offset. True kill-surviving background transfer is
   `background_downloader` (URLSession/WorkManager), which cannot be validated without Xcode.
6. **No sign-in.** MOB-6 is the identity spike's, like the admin SPA's. The header shim stands in.
7. **No biometric unlock.** ADR 0002 wants biometric-*bound* keys via `local_auth`; the key is
   currently Keychain-held with `first_unlock` accessibility and no biometric gate.
8. **Accessibility has had a first pass, not an audit.** Every chip carries a text label as well
   as a colour; nobody has driven the app with VoiceOver or TalkBack.
9. **Nothing enforces TLS.** `CREWCOMP_API` defaults to `http://127.0.0.1:8080` and any value is
   accepted. Because `dart:io` bypasses App Transport Security (see Traps), iOS will not block a
   release build from talking cleartext — a misconfigured `--dart-define` would ship crew personal
   data over plain HTTP with nothing complaining. The fix is ours to write: reject a non-`https`
   base URL unless `kDebugMode`, the same compile-time pattern the dev identity already uses.
