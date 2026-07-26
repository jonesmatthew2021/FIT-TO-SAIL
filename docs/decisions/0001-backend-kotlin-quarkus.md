# ADR 0001 — Backend: Kotlin + Quarkus 3.33 LTS

**Status:** Accepted (2026-07-26)
**Spec refs:** §13, §13.1, §14.1, DEV-5
**Research:** `docs/research/14.1-backend-language-framework.md`

## Context

The spec requires a monolithic application server with strong security defaults, an embedded MCP server from the first commit (§13.1), multi-provider OIDC federation (SEC-1), OpenAPI-generated client types (DEV-2), and small-footprint deployment on a managed PaaS. Chris is JVM-proficient but ruled out Spring/Spring Boot. Live candidates: lightweight JVM frameworks (Ktor, Quarkus, Micronaut, Javalin, http4k) vs Go.

## Decision

Kotlin on **Quarkus 3.33 LTS**, targeting GraalVM native image for deployment (JVM mode in dev), with: quarkus-rest, quarkus-oidc (multitenancy for multi-IdP federation), quarkus-mcp-server (streamable HTTP), Hibernate ORM/Panache or jOOQ (spike decides), Flyway, SmallRye OpenAPI, JobRunr for background jobs.

## Rationale

- The Quarkiverse MCP server extension (v1.13.0) is the most mature MCP server implementation on any JVM — declarative tools sharing the CDI service layer, exactly the MCP-2/MCP-5 shape. The Kotlin SDK (Ktor's path) is still pre-1.0 on a day-one hard requirement.
- quarkus-oidc multitenancy supports distinct providers per tenant as configuration — directly serving SEC-1/SEC-1a and ADR 0003.
- Native image (20–50 MB RSS, 20–80 ms cold start) removes the "JVM too heavy for cheap PaaS" objection; fits the smallest Fargate/Cloud Run tiers.
- Batteries included without Spring: security, Flyway, scheduler, OpenAPI, health/metrics under one curated BOM (supply-chain benefit), Red Hat-backed LTS patch stream.
- Preserves Kotlin proficiency; huge conventional documentation corpus (AI-assist affinity, DEV-5).

Go (chi + sqlc/pgx + river + official MCP SDK) ranked a close #2 and remains the documented fallback if the spike fails a gate.

## Consequences

- The backend spike must validate: native-image build of the full extension combo (MCP + OIDC + ORM + Flyway + JobRunr) running in ≤512 MB; MCP tool and REST endpoint sharing one service; OpenAPI → TypeScript codegen round-trip; Kotlin-flavoured Quarkus ergonomics with Claude Code.
- Docs/examples skew Java; expect to translate idioms to Kotlin.
- Native-image reflection issues are the known failure mode: develop in JVM mode, build/test native in CI on every merge.
