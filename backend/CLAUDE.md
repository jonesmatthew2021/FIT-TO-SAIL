# backend/ — Kotlin + Quarkus monolith

Not yet scaffolded — the backend spike (ADR 0001 consequences) creates the Quarkus project here.

## Stack (decided)

Kotlin, Quarkus 3.33 LTS, PostgreSQL. Extensions: quarkus-rest, quarkus-oidc (multitenancy — one tenant per allow-listed IdP backend, see ADR 0003), quarkus-mcp-server-http, Flyway, SmallRye OpenAPI, JobRunr. ORM choice (Hibernate/Panache vs jOOQ) is a spike outcome. GraalVM native image for deployment; JVM mode for dev; native build verified in CI.

## Internal module boundaries (spec §13 — the approved extraction seams)

`reference / rules / people / workflow / evidence / sync` — mirror spec §4's layers. Keep them package-separated from the start.

## Non-negotiables for this component

- Engine semantics come from spec §5, verbatim — port the POC's test-visible behaviour as an executable test suite (DEV-2) before/alongside the engine itself.
- MCP tools call the identical service layer as REST endpoints (MCP-2) — a tool that bypasses validation/authz is a defect.
- Row-scoping for crew users implemented once, centrally (AUTH-2).
- Cloud services (object storage, secrets, scheduler, LLM/Bedrock) behind thin adapter interfaces until ADR 0005 resolves the platform.
- Every business mutation writes an AuditEvent (self-contained, monotonic sequence, actor kind: human / ai_proposed / ai_automatic).
- Flyway migrations: forward-only, expand/contract (must run against the previous app revision).
