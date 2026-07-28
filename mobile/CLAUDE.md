# mobile/ — Flutter crew self-service app (iOS + Android)

**All twelve of the design handoff's screens are built, on the Nocturne dark design system.** The
four that shipped before — certifications, requirement detail, roster, alerts — are restyled onto
the same ground as the admin console; eight are new: MOB-0 home, MOB-5 one-tap update, MOB-6 send
the certificate, MOB-7 confirm the reading, MOB-8 course booking, MOB-9 attestation sign-off,
MOB-10 exemption request, MOB-11 team compliance. Every one has been driven on an iOS simulator
against the live backend.

Under them is the offline spine: an encrypted local store with snapshot + delta sync, a durable
outbound queue, and a resumable chunked upload.

**Two MOB numberings are in play and they collide.** The spec's §7 numbers *capabilities* — MOB-1
certifications, MOB-2 roster, MOB-3 notifications, MOB-4 evidence submission, MOB-5 offline sync,
MOB-6 onboarding and identity. The design handoff numbers *screens*, and reuses several of those
digits for different things: its MOB-3 is the roster, its MOB-4 is alerts, its MOB-6 is sending a
certificate. Below, a number in a sentence about a **screen** is the handoff's; a number in a
sentence about a **capability or a rule** is the spec's. Where it could go either way, the sentence
says which.

**Three-quarters of the new screens are ahead of the backend, deliberately.** The server has no
course catalogue, no extraction fields, no credits history, no team endpoint and — until 28 July —
none of the seven new sync operations the one-tap answers post. **Two of those have now landed**
(`requirement.progress`, `requirement.help`), and they needed no change in `lib/` beyond the
regenerated schema: the app was already sending exactly what the server grew a reader for. The rest
degrade honestly rather than convincingly — see "What the payload does not carry yet" — and
`docs/handoff/mobile-crew-app-backend.md` is the list of what has to become true.

**MOB-4 submission is built end to end** — capture (camera, photo library, PDF/image attachment),
a staged copy inside the app container, a queue entry, and a resumable chunked upload that asks
the server for its offset before every attempt. Verified on the simulator against the live
backend: two chunks, digest accepted, and §8 reporting `Extracted 1: 0 auto-accepted, 1 to review`
— correctly nothing extracted with no LLM provider configured, and a Data Steward left to type the
fields. **The camera itself is still unproven**: a simulator has none, so only the library and file
paths have actually run (see "What is unverified" #4).

**iOS builds and runs; Android has never been built.** Xcode 26.6 / iOS 26.5 SDK is installed
here, so `flutter build ios` and a simulator run are verified (see below for exactly what that
proved). There is still no Android SDK, so `flutter build apk` is the remaining unrun lane — which
matters, because ADR 0002 mandates feature parity and nothing enforces it yet. See "What is
unverified" before trusting anything else about the app on a phone.

## Stack (decided — ADR 0002, amended by ADR 0009)

Flutter 3.44 stable / Dart 3.12. Runtime dependencies, all of them: `drift` (local store),
`sqlite3`, `path_provider`, `flutter_secure_storage`, `http`, `image_picker` (MOB-4 camera and
photo library), `file_picker` (MOB-4 attachments), `crypto` (the SHA-256 a resumed upload is
verified against), `phosphor_icons` (the design system's icon set, as bundled fonts).
Dev: `drift_dev`, `build_runner`, `flutter_lints`.

Inter is **vendored** in `fonts/`, two static instances at 400 and 500 — Nocturne is never bolder
than 500, so those two are the whole type system. Not `google_fonts`: it downloads at first paint,
which on a vessel means the first launch out of range renders in the platform fallback, with
different metrics and a layout that only breaks where nobody is watching.

`phosphor_icons`, **not** the more widely referenced `phosphor_flutter`. The latter is stuck at
2.1.0 and declares `class PhosphorIconData extends IconData`, which stopped compiling when Flutter
made `IconData` a final class. It fails in the *kernel* compiler rather than the analyzer, so
`flutter analyze` reports a clean tree and then every widget test fails to load with an error that
names the package, not the cause.

Two upload-related things ADR 0002 names are deliberately **not** here yet, and both are the
device spike's: `background_downloader` (kill-surviving background transfer — the uploader below
survives an app *restart*, because the queue and the staged file are both on disk, but not a kill
mid-chunk) and the VisionKit/ML-Kit document scanner (edge detection and deskew; `image_picker`
returns a plain photo).

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
flutter test                              # 127 tests
flutter analyze                           # clean
dart run tool/generate_api.dart           # regenerate lib/src/api/schema.g.dart
dart run tool/generate_api.dart --check   # fail if committed types are stale — the CI check
CREWCOMP_OPENAPI=/path/to/openapi.json dart run tool/generate_api.dart --check   # read the schema elsewhere
dart run build_runner build               # regenerate drift's local_store.g.dart
```

`flutter` here is `/opt/homebrew/share/flutter/bin` (Homebrew cask). Note that `dart` on the
PATH may be the standalone Homebrew Dart, a different version from Flutter's bundled one — put
`/opt/homebrew/share/flutter/bin` first, or `flutter doctor` warns about it.

**When it is the wrong `dart`, the error does not say so.** `dart run tool/generate_api.dart` with
the standalone Dart fails inside a native build hook with `Can't load Kernel binary: Invalid kernel
binary format version (expected 127, found 130)` — which reads like a corrupt package, not a version
mismatch. Use Flutter's own:
`/opt/homebrew/share/flutter/bin/cache/dart-sdk/bin/dart run tool/generate_api.dart`.

The full local loop needs the backend. Quarkus Dev Services drives Testcontainers, which looks
for `/var/run/docker.sock` and will **not** find Colima's socket on its own:

```bash
export DOCKER_HOST=unix:///Users/chrisjones/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
(cd ../backend && CREWCOMP_MCP_TOKEN=<32+ chars> ./mvnw quarkus:dev)

xcrun simctl boot 'iPhone 17 Pro'
open /Applications/Xcode.app/Contents/Developer/Applications/Simulator.app
flutter run -d <simulator-udid> --dart-define=CREWCOMP_DEV_PERSON=2

# MOB-11's Team tab, which needs a supervisory role nothing yet grants. Debug-only: `kDebugMode`
# is a compile-time constant, so a release build cannot reach the flag at all.
flutter run -d <udid> --dart-define=CREWCOMP_DEV_PERSON=2 --dart-define=CREWCOMP_DEV_SUPERVISOR=true
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
- **The app never writes a holding** (§7.5). The client-originated writes are evidence submissions,
  notification read-marks and the one-tap **crew statements**. Read-marks are monotonic; the other
  two are append-only under a client-minted id — so all three are idempotent by construction, which
  is what removes conflict resolution from this codebase entirely and is the test a fourth write
  would have to pass. None of the three is read by the §5 engine.
- **Push payloads carry title + deep link only** (SEC-13). The body is synced and shown in-app.
  The dev fixture's notification titles follow the rule too, so the screens are never accidentally
  designed around detail a real push cannot carry.
- **A one-tap answer never reverts silently.** The seven crew-intent operations ride the same
  outbox as an evidence submission, and `CrewIntents` keeps the verdict after the outbox entry is
  gone. A server rejection deletes the queue entry — a poison entry retried forever wedges the
  queue — and if that were the only record, the crew member's tap would vanish and the row would
  un-say itself on the next snapshot. Failed intents show the server's own words and a Retry that
  re-posts under the original `opId`.
- **A screen omits rather than invents.** Where the payload does not carry a fact, the screen says
  so or draws nothing: no course dates, no credit tiles, no team list. An invented number is
  indistinguishable from a real one and gets acted on.
- **Development sign-in is compiled out.** `DevIdentity` is constructed only under `kDebugMode`,
  a compile-time constant, so a release build contains neither the header names nor a way to set
  them — the same guarantee the backend gives by removing its shim bean at build time.

## Layout

| Path | Contents |
|---|---|
| `lib/src/api/` | `schema.g.dart` (generated from the backend's OpenAPI, committed), `crewcomp_api.dart` (typed HTTP + the dev shim) |
| `lib/src/data/` | `local_store.dart` drift schema, `database_opener.dart` encryption and key handling, `sync_engine.dart` snapshot/delta/outbox/crew intents, `evidence_capture.dart` MOB-4 pickers and staging, `evidence_uploader.dart` MOB-5a resumable chunk loop |
| `lib/src/domain/` | `calendar.dart` calendar-date arithmetic, `states.dart` Appendix A presentation, `urgency.dart` the one {clear, soon, blocking} mapping, `intents.dart` the one-tap operation names, `offers.dart` the shapes the payload does not carry yet |
| `lib/src/ui/` | `nocturne.dart` the token sheet and theme, `widgets.dart` the component kit, `app_state.dart` (the only thing that talks to the engine), then the screens: `screens.dart` shell + MOB-0/1/3/4, `detail_screens.dart` MOB-2 + swing, `action_screens.dart` MOB-5/8/9/10, `evidence_screens.dart` MOB-6/7, `team_screen.dart` MOB-11 |
| `fonts/` | Inter 400 and 500, vendored |
| `tool/` | `generate_api.dart` — the DEV-2 generator |

Throughout the UI the `*Screen` / `*View` split holds: the wrapper owns the streams and the
navigation, the view is a pure function of its data. That is what every widget test depends on,
and the reason is in "Traps" below.

`CREWCOMP_OPENAPI` exists for CI: the backend job publishes the schema as an artefact and both
client jobs check against *that*, rather than each building its own. A job that regenerated the schema
itself could never catch a stale committed file — it would produce a matching pair and pass. Same
variable, same reason, as `admin-web/scripts/generate-api-types.sh`.

`schema.g.dart` is generated from the **whole** backend schema, so it carries types for admin-only
DTOs the app never uses (ADM-10's configuration, the evidence review queue). That is deliberate: the
generator mirrors the contract rather than curating it, and a hand-maintained subset is exactly the
thing DEV-2 exists to prevent. It does mean an admin-side DTO change makes this file stale and the
`--check` lane fail — which is the intended signal, not noise.

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
- **A snapshot would strand an in-flight submission's bytes.** `local_path` is the one column on
  `submissions` the server does not own, and `_applySnapshot` replaces every server-owned row
  wholesale. Dropping it does not fail anything: the row returns from the payload looking like an
  ordinary in-flight submission, the uploader skips it forever because there is nothing to read,
  and the crew member waits on a verdict for a document that will never arrive. The paths are
  captured before the delete and re-applied after. A delta is fine — `insertOnConflictUpdate` with
  the column absent from the companion leaves it alone.
- **`drift` exports a `min`**, the SQL aggregate, and it shadows `dart:math`'s inside any file
  importing both. The error names a `double` argument, which points nowhere near the real cause.
  `evidence_uploader.dart` imports `dart:math as math` for that reason.
- **`file_picker` v11 moved `pickFiles` onto the class.** `FilePicker.platform.pickFiles(...)` is
  the v3–v10 form and every answer on the internet; v11 is `FilePicker.pickFiles(...)`. Worth
  knowing because `flutter pub add file_picker` resolved **3.0.4** here — five years stale —
  rather than 11, and the old API would have compiled cleanly against it.
- **`AccumulatorSink` is in `package:convert`, not `package:crypto`**, though every chunked-hashing
  example pairs them. `dart:convert`'s own `ChunkedConversionSink.withCallback` does the same job
  without a fourth dependency.
- **A `Column` inside a `bottomNavigationBar` fills the screen.** `Scaffold` hands its
  `bottomNavigationBar` loose constraints up to the *full* screen height, so a `Column` left at its
  default `MainAxisSize.max` grows into all of it: the tab bar becomes the whole app, the body is
  squeezed to zero, and what you see is a blank screen with four tabs floating in the middle. No
  exception is thrown anywhere. It shipped past `flutter analyze` and 121 green tests, because
  every `*View` test renders its screen without a `Scaffold` around the bar. `test/shell_test.dart`
  now asserts the bar's height, which is the cheap version of the check.
- **`CrossAxisAlignment.stretch` on a `Row` inside a scroll view throws.** The cross axis is
  vertical and unbounded there, so the Row is asked to be infinitely tall. Home's credit tiles want
  equal heights; `IntrinsicHeight` is the form that works.
- **Flutter's `BorderStyle` has no `dashed`.** `BoxDecoration(border: Border.all(style: ...))`
  accepts only `none` and `solid`, and asking for a dash silently draws a solid line — so MOB-9's
  signature block, which the design draws dashed to mean "nothing here yet", read as an ordinary
  empty card. `DashedBorder` in `widgets.dart` paints it with a path-metric walk.
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

**MOB-4's pickers changed that last part.** Adding `image_picker` and `file_picker` made Flutter
generate an `ios/Podfile` and integrate CocoaPods into the workspace, where the build had been
pure SPM. The lockfile resolves to `Flutter (1.0.0)` and nothing else — both plugins ship SPM
packages and neither contributes a pod — so this is an empty CocoaPods integration that exists
because the toolchain adds one when any plugin is present. It costs a `pod install` on a clean
checkout and is worth knowing before someone deletes the Podfile as unused.

## What the 28 July run proved (the Nocturne restyle and the eight new screens)

Every one of the twelve screens was rendered on the iPhone 17 Pro simulator against the live
backend and read against the design. Three things came out of it that no test had caught, and all
three are in "Traps" above: the tab bar filling the screen, the credit tiles' unbounded stretch,
and `BorderStyle` silently refusing to draw a dash. Two more were design defects rather than
crashes — ISO dates reaching crew inside notification bodies, and MOB-8 promising "you only pick a
date" above an empty list.

Driving a pushed screen without a tap is the awkward part. `xcrun simctl` cannot tap, and
AppleScript clicking needs an accessibility grant this machine does not have. What works: run
`flutter run` with its stdin on a FIFO, then `printf 'R' > fifo` to hot restart after temporarily
pointing `_CrewHomeState.initState` at the screen you want. Hot restart is ~400ms, so the whole
set takes a couple of minutes rather than a rebuild each.

## What the payload does not carry yet, and what the screens do about it

The design handoff describes a finished product. Eight of its twelve screens are built against
server-owned data that does not exist, and every one of them degrades in a way a crew member can
read rather than in a way that looks finished. This is the same position `admin-web/CLAUDE.md`
records for the console, and for the same reason: **an invented number is indistinguishable from a
real one, and gets acted on.**

Each of these is one stub in `app_state.dart`, so landing the backend work is a change in one file.
`docs/handoff/mobile-crew-app-backend.md` is the full list from the server's side.

| The design asks for | Today | The screen does |
|---|---|---|
| MOB-0's credit tiles — "14 months · never sailed short" | needs history the device is never sent | draws no tiles at all; the rest of Home is unaffected |
| MOB-0's readiness ring | derived on the device from the server's own cells | counts them with the same `needsAttention` grouping the list uses, so ring and list cannot disagree — but *which states count as ready* should be the engine's |
| MOB-0's headline sentence | mapped from `standing.evaluation.rollUp` | a `switch` in `HomeView._headline`; it is a compliance sentence living in a client and should come down the wire |
| MOB-8's course dates | no course catalogue exists | says so, and offers the one action that does — asking the office |
| MOB-7's extracted fields | no LLM provider (§14.5) | opens at its third confidence level with `Not read — add it` in every field, which **is** LLM-2's launch posture, not a degraded mode |
| MOB-11's watch | no team endpoint, no supervisory role | the tab is off unless `--dart-define=CREWCOMP_DEV_SUPERVISOR=true` under `kDebugMode`, and the screen says the app is not sent a watch — *not* an empty list a supervisor would read as "everyone is fine" |
| MOB-9's declaration wording | a client constant carrying the handoff's copy | renders it, and draws the supporting fact under each line from the person's real evaluated cells |
| MOB-9's "Face ID · 28 Jul 2026, 07:05 AWST" | no biometric binding, and the timestamp must be the server's | the block says "the office records the time it arrives, in the vessel's timezone" rather than printing a plausible one |
| `requirement.progress` / `requirement.help` | **landed** — a `crew_statement` row plus a Coordinator notification | nothing; the row reports "Sent to the office". Neither moves a holding, so the card still shows the gap |
| the other five one-tap operations | `SyncService` rejects each as `Unsupported operation type` | queues them anyway; the row shows the server's own words and a Retry. Evidence submissions and read-marks are unaffected — rejection is per operation, not per batch |
| MOB-6 as an OS share target | an iOS Share Extension and an Android intent filter, unwritten | the in-app half of the sheet at the mock's geometry (Files · Photos · Camera). The mock's four-up with Files/Print/More is the *operating system's* sheet; drawing our own greyed-out "Print" would be a picture of a feature |
| crew-facing dates in notification bodies | the server composes them with a raw `LocalDate.toString()` | `humaniseDates` rewrites `2026-08-14` to `14 Aug 2026` at display time. A date-format substitution and nothing else, deletable the moment the server composes properly |

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
   not a dependency yet. The list is no longer fixture-only, though: assigning or unassigning a
   crew member in the admin SPA raises a real `assignment_added` / `assignment_removed`
   notification, which arrives on the next delta — the easiest way to watch sync work live.
4. **The camera has never run.** MOB-4's capture, staging, queue and upload are built and tested,
   and the photo-library and file-attachment paths work. `ImageSource.camera` cannot be exercised
   on a simulator, which has no camera, so that branch and its `NSCameraUsageDescription` prompt
   are unproven — as is the doc-scan wrapper ADR 0002 wants in front of it.
5. **Uploads do not survive backgrounding.** The outbox survives app *restarts* — it is a table —
   and resumes from the server-held offset. True kill-surviving background transfer is
   `background_downloader` (URLSession/WorkManager), which cannot be validated without Xcode.
6. **No sign-in.** Onboarding and identity — the *spec's* MOB-6 — is the identity spike's, like
   the admin SPA's. The header shim stands in. It is also what gates MOB-11: a supervisory role
   has nowhere to come from until a session does.
7. **No biometric unlock.** ADR 0002 wants biometric-*bound* keys via `local_auth`; the key is
   currently Keychain-held with `first_unlock` accessibility and no biometric gate.
8. **Accessibility has had a first pass, not an audit.** Every tag carries a text label as well as
   a colour, every tap target clears 44pt, the choice rows announce as a mutually exclusive group,
   and the focus ring is Nocturne's accent rather than Material's overlay. Nobody has driven the
   app with VoiceOver or TalkBack, and Dynamic Type has not been exercised past the default —
   the 10px section labels are the ones most likely to break first.
9. **Nothing enforces TLS.** `CREWCOMP_API` defaults to `http://127.0.0.1:8080` and any value is
   accepted. Because `dart:io` bypasses App Transport Security (see Traps), iOS will not block a
   release build from talking cleartext — a misconfigured `--dart-define` would ship crew personal
   data over plain HTTP with nothing complaining. The fix is ours to write: reject a non-`https`
   base URL unless `kDebugMode`, the same compile-time pattern the dev identity already uses.
