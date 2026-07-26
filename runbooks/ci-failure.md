# CI failure

**Alert class:** a `CI` workflow run failed on `main` (ADR 0004 routes failed workflow runs to the
triage workflow via `repository_dispatch`).

## Symptoms

A red run of `.github/workflows/ci.yml`. Which job failed matters far more than that one did, because
four of the six jobs fail for reasons that have nothing to do with each other.

## Diagnosis

Read the failing job's name first. Every lane below is a command a developer can run locally, with the
exception of the two that are the whole reason CI exists.

| Job | Reproduce locally | Notes |
|---|---|---|
| `backend` | `cd backend && ./mvnw verify -DskipITs=false` | Needs a container runtime. On a Colima machine, export `DOCKER_HOST` and `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` first — see `backend/CLAUDE.md`. |
| `backend-native` | **not reproducible locally** — no GraalVM | See below. |
| `admin-web` | `cd admin-web && npm run verify:api && npm run build && npm test` | `verify:api` needs `backend/target/openapi/openapi.json`; build the backend first. |
| `mobile` | `cd mobile && dart run tool/generate_api.dart --check && flutter analyze && flutter test` | Use Flutter's bundled `dart`, not a standalone one. |
| `mobile-android` | **not reproducible locally** — no Android SDK | See below. |
| `mobile-ios` | `cd mobile && flutter build ios --no-codesign --debug` | Needs Xcode. |

Read-only commands the triage agent may run:

```bash
gh run view <run-id> --log-failed
gh run view <run-id> --json jobs --jq '.jobs[] | {name, conclusion}'
git log --oneline -5
```

## The four failure classes worth naming

**1. Generated client types are stale** — `admin-web` or `mobile` fails on its `--check` step with
"does not match the backend schema".

Not a broken build: it is DEV-2 working. Someone changed a backend DTO and did not regenerate a
client. Note the direction that catches people out — an **admin-only** DTO makes *mobile's* types
stale too, because `schema.g.dart` is generated from the whole schema.

- Remediation (`HUMAN`): regenerate and commit both.
  ```bash
  cd backend && ./mvnw package -DskipTests
  cd ../admin-web && npm run generate:api
  cd ../mobile && dart run tool/generate_api.dart
  ```
- Never "fix" this by editing a generated file. `verify:api` will fail again on the next change, and
  the committed file will then disagree with the server in a way nothing detects.

**2. The dev auth shim is in the production bundle** — `admin-web` fails on
"Assert the dev auth shim is absent".

Treat this as a security incident, not a build break. It means `import.meta.env.DEV` has stopped being
a compile-time constant on that path — most likely because someone moved the branch behind a runtime
check — and the bundle now carries a header-authentication path (SEC-1).

- Remediation (`HUMAN`): find what made the branch reachable. `grep -rl 'X-Dev-Roles'
  admin-web/dist/` names the chunk. The fix is to restore the compile-time constant, never to relax
  the check.
- Escalate immediately if the commit is already deployed anywhere.

**3. `backend-native` fails and `backend` passed.** Expected the first few times, and the reason
ADR 0001 made this a per-merge job: GraalVM needs reflection registration for the entity and DTO
graph, and JVM mode does not. Two newer sources of the same failure: the `jsonb` mappings go through
a Jackson `FormatMapper` (`DatabaseJsonFormatMapper`), and `quarkus-scheduler` resolves its cron
expressions at build time.

- Remediation (`HUMAN`): read the native-image build log for the missing class, add
  `@RegisterForReflection` or a `reflect-config.json` entry, and re-run. This is a code change and
  belongs in a PR.

**4. `mobile-android` fails and `mobile` passed.** This is the job that exists precisely because
nobody can run it locally — there is no Android SDK on the development machine, so ADR 0002's
feature-parity mandate (MOB-0) has no other enforcement anywhere.

- **Do not treat a red Android lane as flakiness and re-run it.** A plugin without an Android
  implementation, or a Gradle/AGP/NDK mismatch, fails deterministically and will keep failing.
- Remediation (`HUMAN`): read the Gradle output for the offending plugin or configuration. If the
  cause is a dependency added for iOS with no Android support, that is a parity exception and ADR 0002
  requires it to be approved explicitly rather than absorbed.

## Escalation

- **Immediately**, whatever the autonomy policy: failure class 2 (the auth shim in a bundle).
- Any failure on `main` that is still red after the fix commit — a second red run means the diagnosis
  was wrong, not that the fix needs retrying.
- A `mobile-android` failure that has been red for more than one working day: parity is a hard
  requirement and a quietly-red Android lane is how it stops being one.

## Autonomy

Everything here is `HUMAN`. Per `runbooks/README.md`, the autonomy boundary is a client policy
decision (spec O-16) and defaults to `HUMAN` until it is answered — and a CI failure is a poor first
candidate for unattended remediation anyway, since two of the four classes above are code changes and
one is a security incident.
