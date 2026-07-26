# CREWCOMP §14 technology selection — synthesis & recommendations

**Date:** 26 July 2026. Synthesises the five research reports in this directory (all live-web-verified today). Constraints applied: greenfield cloud, cost-conscious, Chris = sole dev+ops (JVM-proficient, hard no on Spring), GitHub + Actions decided, IdP mix = Entra + Google Workspace + Okta (all three confirmed), AU data residency (SEC-14), security paramount.

---

## Recommended stack (one line each)

| Area | Recommendation | Runner-up | Report |
|---|---|---|---|
| **Platform** | **AWS Sydney: ECS Express Mode (Fargate ARM) + RDS Postgres + S3 Object Lock + EventBridge Scheduler + Secrets Manager** (~$60–85/mo prod+staging) | GCP Cloud Run (only if client accepts offshore LLM inference) | `14.2` |
| **LLM provider** | **Claude via Bedrock `au.*` inference profiles** — the only option keeping evidence-document inference entirely in Australia (Sydney+Melbourne) | Vertex/direct API (offshore — needs O-9 sign-off) | `14.2` |
| **Backend** | **Kotlin + Quarkus 3.33 LTS** (native image; Flyway, SmallRye OpenAPI, quarkus-mcp-server, JobRunr) | Go 1.26 (chi + sqlc/pgx + river + official MCP SDK) | `14.1` |
| **Identity** | **Direct multi-issuer OIDC federation** (quarkus-oidc multitenancy = config-level; BFF cookie session for SPA; app-issued rotating device tokens for mobile) | Auth0 AU-region tenant (~$150–250/mo — would be the single largest line item) | `sec-1` |
| **Mobile** | **Flutter** (drift + SQLite3MultipleCiphers, background_downloader, flutter_appauth, doc-scan wrappers) | React Native + Expo | `14.3` |
| **Admin web** | React + TypeScript SPA, types generated from the backend's OpenAPI schema | — | spec §14.4 default stands |
| **CI/CD** | GitHub Actions per the pipeline report, deploy lane adapted to ECS Express; OpenTofu + S3 state; Semgrep free tier + Trivy + Gitleaks + GitHub Secret Protection ($19/mo); claude-code-action reviews (general + path-filtered security pass); Sentry + repository_dispatch AI triage bot | — | `17.3` |
| **Preview DBs** | Neon (`aws-ap-southeast-2` — same region as the app, no cross-cloud penalty) branch-per-PR; **RDS stays the prod database** | Neon for everything | `17.3` + `14.2` |

Estimated total run cost: **~$80–130/mo** infra + tooling (+ ~$10–40/mo AI-review tokens), vs Auth0 alone costing $150–250/mo if the buy-option is taken.

---

## The five decisive findings

1. **AWS App Runner is closed to new customers (30 Apr 2026).** Its successor, ECS Express Mode (GA Nov 2025), is one API call for Fargate + shared ALB + HTTPS, plain ECS underneath (zero-rewrite escape hatch).
2. **Only AWS keeps Claude in-cloud and in-Australia** — Bedrock `au.*` profiles route inference Sydney↔Melbourne only. Vertex AI has no AU Claude (Singapore at best); Azure Foundry Claude is Global/US-only; Anthropic direct stores data in the US. Since the evidence pipeline sends crew personal documents to the model, this effectively decides §14.2 in AWS's favour unless the O-9 privacy assessment explicitly accepts offshore inference. Bonus: AWS is also the *cheapest* compliant option and the cloud Chris knows best.
3. **The Quarkus MCP server extension is the most mature MCP server implementation on any JVM** (v1.13.0, streamable HTTP, already on the 2026-07-28 stateless protocol) — and §13.1 makes MCP a day-one requirement. The Kotlin SDK (Ktor's path) is still pre-1.0. Quarkus native image (20–50 MB RSS, 20–80 ms cold start) kills the "JVM too heavy" concern; it fits Fargate's 0.25 vCPU/512 MB tier.
4. **No identity broker enforces the Google Workspace `hd` restriction purely by configuration** — every path involves some claim-validation code. Meanwhile quarkus-oidc's multitenancy makes multi-issuer federation (per-tenant Entra issuers, Google `hd`, Okta org servers) a config-level feature, and SEC-1a *already requires* the app to verify issuer+tenant on every token. Direct federation therefore buys residency (no third-party identity store), $0/mo, and exact spec fit; the honest remaining build is the mobile device-session layer (~1–2 weeks: rotating one-time-use device tokens, reuse-detection family revocation, 30-day cap, per-device sign-out table).
5. **Flutter's package ecosystem covers CREWCOMP's three hardest mobile requirements off the shelf** (kill-surviving resumable background uploads, encrypted SQLite, VisionKit/ML-Kit doc scanning), where React Native would need a custom native module for the upload queue — and npm's 2025–26 supply-chain record (Shai-Hulud worms) is a poor fit for "security paramount". KMP loses despite the Kotlin synergy: this app's risk is concentrated in exactly the platform glue KMP makes you write twice.

## Cross-report interactions resolved

- **Neon + AWS**: the pipeline report flagged a cross-cloud tradeoff (Neon on AWS Sydney vs app on GCP). With AWS chosen, it dissolves — Neon branch DBs for PR previews sit in the same region as the app. RDS remains prod (SLA-backed, $21/mo, PITR included).
- **Quarkus + direct federation**: the identity report's 1–3-week estimate for the library path shrinks because quarkus-oidc multitenancy provides the multi-issuer verification layer; what remains is the BFF session + device-token store, which the report specifies precisely (RFC 9700 patterns).
- **Quarkus + Bedrock**: AWS SDK for Java v2 (or LangChain4j) covers Bedrock; extraction jobs run under JobRunr in the same artefact per §13.
- **Flutter + CI**: the mobile lane uses Codemagic or fastlane + GitHub Actions (EAS is RN-only); store signing keys in the platform's managed signing or GitHub encrypted secrets.
- **Deploy lane on AWS** (replaces the report's Cloud Run steps): GitHub OIDC → `aws-actions/configure-aws-credentials` role; migrations as a one-off ECS task run-before-deploy; ECS rolling deploy with **deployment circuit breaker + auto-rollback**; health-check-gated; previews = Express services created/deleted by PR workflows on the shared ALB.
- **IaC state**: OpenTofu with an **S3 backend** (native lockfile locking in current versions — no DynamoDB table needed) instead of the report's GCS suggestion.

## What stays open (feeds the spec's O-list)

- **O-9 (privacy impact assessment)** remains the gate for the LLM provider terms — Bedrock AU answers residency, but the PIA must still cover processing terms, retention, and crew consent. Verify Opus-class availability on `au.*` in the console; Sonnet 4.5 + Haiku 4.5 are confirmed.
- **O-13/O-17**: provider *types* are confirmed (Entra/Google/Okta) but the per-partnership tenant/domain inventory is still needed for the SEC-1a allow-list and onboarding.
- **O-18 (MCP production posture)**: MCP-3's default-disabled stance is implementable identically under either backend.
- Auth0 remains the documented buy-option if the direct-federation spike reveals unexpected cost; the spike plan is in `sec-1-identity-federation.md` §5.

## Recommended spike sequence (before P1 feature work)

1. **Backend spike (2–3 days):** Quarkus/Kotlin — REST + MCP tool sharing one service, OIDC multitenancy against two live IdPs, native-image build of the full extension combo, OpenAPI → TS types round-trip. (Go fallback criteria in `14.1` §5.)
2. **Identity spike (1 week, overlaps):** per-tenant Entra issuer + Google `hd` + Okta org-server verification; BFF session; device-session prototype with rotation + per-device revocation.
3. **Mobile spike (1 week):** the Flutter "upload gauntlet" from `14.3` §5 (encrypted store, kill-mid-sync recovery, 20 MB resumable upload, doc scan, PKCE login). RN comparison optional — run only if Flutter fails a gate.
4. **Pipeline bootstrap (2–3 days, first):** repo scaffold, OpenTofu baseline (VPC-less public-subnet design, ECS Express, RDS, S3, Secrets), CI workflows, AI review, deploy-to-staging path — so every spike lands through the real pipeline (DEV-1..6 apply from first commit).
