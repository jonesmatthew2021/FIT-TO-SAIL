# CREWCOMP — monorepo

Maritime crew-compliance system replacing two forked Excel workbooks: a versioned requirements matrix, whole-swing compliance evaluation, exemption/query register workflow, crew self-service mobile apps, and an LLM evidence-extraction pipeline. ~60 users, 5 partnerships, security paramount, one-person dev+ops working with Claude Code.

## Source of truth

- **Spec (normative):** `docs/spec/crewcomp-production-spec.md` — V2 draft. §5 (engine semantics) and §4 (domain model) are normative; Appendix A enumerations are canonical. The V1 POC (separate repo, `app/`) is the behavioural reference for the engine.
- **Decisions:** `docs/decisions/` — ADRs. 0001 backend (Kotlin+Quarkus), 0002 mobile (Flutter), 0003 identity (direct OIDC federation), 0004 pipeline (GitHub Actions), 0005 platform (**deferred**: AWS vs GCP, spike decides).
- **Research:** `docs/research/` — the §14 technology research (July 2026, web-verified) behind the ADRs. `00-recommendations.md` is the synthesis.

## Layout

| Path | Contents | Status |
|---|---|---|
| `backend/` | Kotlin + Quarkus monolith: API, compliance engine, workflow, sync, jobs, embedded MCP server | empty — spike next |
| `admin-web/` | React + TypeScript admin SPA (types generated from backend OpenAPI) | empty — after backend spike |
| `mobile/` | Flutter app (iOS + Android, feature parity mandated) | empty — P2, spike first |
| `infra/` | OpenTofu; `aws/` and `gcp/` stacks until ADR 0005 resolves | empty — pipeline bootstrap |
| `runbooks/` | Operational runbooks (markdown, consumed by the AI triage bot) | seeded |
| `docs/` | spec, ADRs, research | current |
| `.github/` | workflows + AI review prompts | prompts seeded, workflows at bootstrap |

## Hard rules (from the spec — apply to all code)

- **No business logic only in a client.** The backend re-validates every write (AUTH-1). Mobile pre-validation is advisory.
- **Crew row-scoping is enforced centrally** (policy layer / RLS), never per-endpoint (AUTH-2).
- **Every business mutation is audited** with the authenticated actor; the actor field distinguishes human / AI-proposed-human-approved / AI-automatic (AUTH-3, AIA-3). AuditEvents are self-contained, immutable, monotonically ordered (§17.4 export-ready).
- **No app-stored credentials, ever** (SEC-1). UserAccount holds only (provider, issuer/tenant, subject) linkage. Issuer + tenant/domain verified on **every** token (SEC-1a).
- **MCP server** (§13.1): module of the monolith, calls the same validated service layer as the API — never raw DB. Token-authenticated in every environment; disabled in production by default.
- **The LLM never mutates the system of record** (LLM-1); extracted document content is data, never instructions (LLM-3).
- **Dates are calendar dates** in the operating timezone (AWST assumed, O-11) — store dates, not timestamps (NFR-5).
- **Business keys are never primary keys** (Sam #, register IDs are unique business keys on surrogate-keyed rows).
- **Until ADR 0005 resolves:** no cloud-SDK usage outside `infra/`; cloud services (object storage, scheduler, secrets, LLM) go behind thin backend adapters.
- **Migrations are forward-only** and must be backward-compatible with the previous app revision (expand/contract) — rollback rolls back code, never schema.

## Current phase

Pre-P1. Next steps, in order (per `docs/research/00-recommendations.md` §"Recommended spike sequence"):
1. Pipeline bootstrap (repo CI, OpenTofu baselines, AI review workflows)
2. Backend spike (Quarkus native + MCP + OIDC multitenancy) deployed to **both** AWS and GCP → resolves ADR 0005
3. Identity spike (device-session layer prototype)
4. Mobile spike (Flutter "upload gauntlet")
