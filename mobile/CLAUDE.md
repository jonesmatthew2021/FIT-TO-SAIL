# mobile/ — Flutter (iOS + Android)

Not yet scaffolded — the mobile spike ("upload gauntlet", `docs/research/14.3-mobile-stack.md` §5) creates the Flutter project here. P2 component.

## Stack (decided — ADR 0002)

Flutter stable. Key packages: drift + SQLite3MultipleCiphers (encrypted local store — key in Keychain/Keystore via flutter_secure_storage v10, biometric-**bound**, not merely biometric-prompted), `background_downloader` (resumable kill-surviving uploads), VisionKit/ML-Kit doc-scan wrapper, `flutter_appauth` (PKCE via system browser), `firebase_messaging`.

## Non-negotiables for this component

- Feature parity iOS/Android (MOB-0) — platform-specific divergence needs an explicit approved exception.
- Crew self-service subset only: my data, my notifications, my evidence uploads. Never writes holdings directly.
- Offline-first: snapshot + delta sync against the fixed contract (spec §10.3); durable outbound queue; client-generated UUIDs for idempotency; always show last-sync time.
- Local store wiped on logout, remotely revocable; ~30-day offline validity window then cached data locks (SEC-12).
- Push payloads carry no sensitive content — title + deep link only (SEC-13).
