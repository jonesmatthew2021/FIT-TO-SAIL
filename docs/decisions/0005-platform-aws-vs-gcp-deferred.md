# ADR 0005 — Deployment platform: AWS vs GCP (deferred to spike verdict)

**Status:** Proposed — decision deferred pending the backend/pipeline spike (2026-07-26)
**Spec refs:** §13, §14.2, SEC-14, NFR-1..7, OPS-2
**Research:** `docs/research/14.2-deployment-platform.md`

## Context

Greenfield estate. Research eliminated: AWS App Runner (closed to new customers 30 Apr 2026), Render/Railway (no AU region), Fly.io (no documented Postgres PITR — OPS-2 breach to self-solve), Azure (dominated: pricier, no AU Claude, no familiarity). Two finalists remain:

| | **AWS** (ECS Express Mode/Fargate + RDS + S3 + EventBridge + Secrets Manager) | **GCP** (Cloud Run + Cloud SQL + GCS + Scheduler + Secret Manager) |
|---|---|---|
| In-Australia Claude inference | **Yes — Bedrock `au.*` profiles (Sydney↔Melbourne only)** | No — Vertex AI Claude nearest is Singapore |
| Cost (prod+staging, USD/mo) | **~$60–85** (SLA-backed RDS at $21) | ~$72–128 (SLA-backed DB tier is the dominant cost; +$18 ALB forced by the AU custom-domain gap) |
| Deploy/preview DX | Rolling ECS + circuit-breaker rollback; previews need ~1–2 days of workflow glue | **Best-in-class**: revision tags, near-free per-PR previews, instant rollback |
| Operator familiarity | **High (Chris's strongest cloud)** | Low |
| Platform maturity risk | Express Mode is young (GA Nov 2025); plain ECS underneath is the escape hatch | Cloud Run long-mature; AU domain-mapping still Preview-gated |

Research ranked AWS #1, chiefly on in-country LLM inference for crew evidence documents (SEC-14/O-9) plus cost and familiarity. Chris chose to validate both in the spike rather than commit on paper.

## Decision (pending)

Bootstrap cloud-agnostically: containerised app, OpenTofu with a stack per finalist (`infra/aws/`, `infra/gcp/`), platform-neutral CI up to the deploy step. The backend spike deploys to **both** and scores them.

## Spike scorecard (decide on evidence)

1. **End-to-end deploy friction:** GitHub Actions → staged deploy → health-gated traffic shift → forced rollback, on each platform; hours spent, moving parts count.
2. **Preview environment reality:** per-PR env + Neon branch DB on each; glue-code volume and teardown reliability.
3. **Bedrock `au.*` verification (AWS):** confirm available Claude models in-console (Sonnet 4.5/Haiku 4.5 confirmed by research; Opus-class unverified) and measure extraction latency/cost on sample certificates — doubles as §14.5 groundwork.
4. **GCP LLM counterfactual:** what O-9 sign-off would offshore (Singapore/US) inference require? If the privacy assessment forbids it, GCP is eliminated without further scoring.
5. **Custom domain + TLS in AU** on each (GCP needs the external ALB workaround — verify real cost/complexity).
6. **Quarkus native cold start/RSS** on Fargate 0.25 vCPU/512 MB vs Cloud Run equivalent.
7. **OPS-2 manual-touchpoint audit** re-run against what was actually built.

## Consequences

- Until resolved, no code may assume a cloud SDK outside `infra/` and a thin, interface-isolated storage/queue adapter layer in the backend (object storage, scheduler, secrets, LLM client).
- Neon (`aws-ap-southeast-2`) is retained for preview DBs under either verdict; it is same-region only under AWS.
- Decision target: end of the backend spike (before P1 schema freeze).
