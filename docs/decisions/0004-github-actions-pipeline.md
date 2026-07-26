# ADR 0004 — Repository, CI/CD and AI-assisted pipeline: GitHub + GitHub Actions

**Status:** Accepted (2026-07-26)
**Spec refs:** §17.2 (OPS-1..4), §17.3 (DEV-1..6)
**Research:** `docs/research/17.3-cicd-ai-pipeline.md`

## Context

One-person dev+ops; the pipeline must substitute for a second reviewer and run commit → CI → staging → production with automated rollback and no manual release steps.

## Decision

Monorepo on **GitHub** (Team plan + Secret Protection SKU), **GitHub Actions** for all CI/CD:

- **AI review (DEV-3):** `anthropics/claude-code-action@v1` — a general correctness pass on every PR (non-blocking) plus a **path-filtered security pass** (auth/sync/row-scoping/evidence/migrations) with a structured blocking verdict as a required check. Review policy lives in-repo (`.github/review-prompt.md`, `.github/security-review-prompt.md`).
- **Scanning:** Dependabot (grouped) + Semgrep AppSec Platform free tier (SARIF) + Gitleaks + Trivy (image CVEs + CycloneDX SBOM) + cosign keyless signing + GitHub Secret Protection push protection. CodeQL/Code Security SKU deferred.
- **Deploys:** cloud auth via OIDC/workload identity (no long-lived keys); forward-only migrations run as a gated job before traffic shift (expand/contract discipline — rollback rolls back code, never schema); health-gated progressive traffic shift with automated rollback. Concrete deploy lane lands with ADR 0005's platform verdict.
- **Previews (DEV-4):** per-PR app instance + **Neon branch database** (`aws-ap-southeast-2`), torn down on PR close.
- **IaC (OPS-4):** OpenTofu, remote state in cloud object storage with native locking; `tofu plan` commented on PRs, `apply` on merge. Dashboards, alert rules and runbooks in-repo.
- **AI ops triage (OPS-3):** Sentry + platform alerts + failed workflow runs → `repository_dispatch` → a Claude triage workflow with read-only tools + runbooks; whitelisted remediations via `workflow_dispatch`; escalation by push notification. Structured, auditable output.

## Rationale

Best AI-tooling integration of the candidate forges; the full stack lands at ~US$30–100/mo for a solo cadence; every OPS/DEV requirement maps to a concrete, verified-current mechanism (details and workflow inventory in the research report).

## Consequences

- Pipeline bootstrap precedes feature work (per spec §15: DEV/OPS requirements apply from the first commit of P1); the spikes land through the real pipeline.
- Two prompt files and the runbooks directory become load-bearing security artefacts and are themselves review-gated.
