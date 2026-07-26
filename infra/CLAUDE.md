# infra/ — OpenTofu

Not yet scaffolded — pipeline bootstrap creates the baselines. **ADR 0005 is unresolved**: maintain parallel `aws/` and `gcp/` stacks until the spike verdict, then delete the loser.

## Rules

- OpenTofu (not Terraform); remote state in cloud object storage with native locking; no SaaS state backend.
- Everything as code (OPS-4): services, DB, buckets, schedules, secrets *containers* (never secret values), monitoring dashboards, alert policies, uptime checks.
- No long-lived cloud keys anywhere: GitHub Actions authenticates via OIDC / workload identity federation.
- AU region only for data at rest (SEC-14): AWS `ap-southeast-2` / GCP `australia-southeast1`.
- Cost guardrails from research: no NAT gateway on AWS (public subnets/VPC endpoints); shared ALB across environments; non-prod scales to zero.
- Environments: production, staging, demo/training (NFR-7) + ephemeral per-PR previews with Neon branch DBs.
