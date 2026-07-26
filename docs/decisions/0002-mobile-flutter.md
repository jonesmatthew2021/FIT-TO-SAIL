# ADR 0002 — Mobile: Flutter

**Status:** Accepted (2026-07-26)
**Spec refs:** §7 (MOB-0..6), §14.3
**Research:** `docs/research/14.3-mobile-stack.md`

## Context

iOS + Android with a hard feature-parity mandate (MOB-0), built and maintained by one person. The riskiest requirements are offline-first sync over an encrypted local store, kill-surviving resumable multi-MB uploads over poor maritime connectivity, camera document capture, and OIDC PKCE against corporate IdPs. Candidates: Flutter, React Native + Expo, Kotlin Multiplatform, native ×2.

## Decision

**Flutter** (stable channel, 3.44+ at time of writing) with: drift + SQLite3MultipleCiphers (encrypted store; the officially recommended path — not SQLCipher), `background_downloader` (resumable, kill-surviving uploads via URLSession/WorkManager), a VisionKit/ML-Kit document-scan wrapper, `flutter_appauth` (PKCE), `flutter_secure_storage` v10 + `local_auth` with biometric-bound keys, `firebase_messaging` for push.

## Rationale

- Parity is structural (one codebase, one renderer, one test suite) rather than maintained by discipline — the only per-platform seam is the document scanner.
- Every hard requirement is covered by a maintained package; nothing on the §7 feature list requires hand-written native code. React Native's background-upload story would need a custom native module for the single hardest feature.
- pub.dev's supply-chain posture (no install scripts, small trees) materially beats npm's 2025–26 record (Shai-Hulud worms) for a "security paramount" product.
- KMP loses despite the Kotlin backend synergy: this app's risk concentrates in platform glue (camera, background transfer, biometric keys) — exactly what KMP requires writing twice, in Swift not yet in the skill set.

## Consequences

- Dart is a new language; the P2 timeline absorbs ramp-up, and the pre-P2 spike (the "upload gauntlet" in the research report) validates the risky slice before feature work.
- The spec's "drift + SQLCipher" reference should read "drift + SQLite3MultipleCiphers".
- Hand-rolled thin sync client over the encrypted store (per spec §7.6 preference) — sanity-checked in research: sound for this workload; revisit only if crew-editable shared records appear.
- Release lane: Codemagic or fastlane + GitHub Actions (EAS is RN-only).
- Accepted risk: Google's "steady, not growing" Flutter investment; mitigated by the installed base and the thin OpenAPI-generated API contract.
