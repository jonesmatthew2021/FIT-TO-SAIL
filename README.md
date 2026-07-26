# CREWCOMP

Production implementation of the crew-compliance system specified in [`docs/spec/crewcomp-production-spec.md`](docs/spec/crewcomp-production-spec.md) (V2). Replaces the validated V1 proof-of-concept with real persistence, auth, mobile self-service, and an LLM evidence pipeline.

## Components

- **`backend/`** — Kotlin + Quarkus monolith: API, compliance engine, register workflow, mobile sync, background jobs, embedded MCP server ([ADR 0001](docs/decisions/0001-backend-kotlin-quarkus.md))
- **`admin-web/`** — React + TypeScript admin SPA
- **`mobile/`** — Flutter iOS/Android crew self-service app ([ADR 0002](docs/decisions/0002-mobile-flutter.md))
- **`infra/`** — OpenTofu (AWS and GCP stacks pending [ADR 0005](docs/decisions/0005-platform-aws-vs-gcp-deferred.md))
- **`runbooks/`** — operational runbooks, consumed by the AI ops-triage workflow

## Key documents

| | |
|---|---|
| Specification | `docs/spec/crewcomp-production-spec.md` |
| Architecture decisions | `docs/decisions/` |
| Technology research (§14) | `docs/research/` — start with `00-recommendations.md` |
| Agent orientation | `CLAUDE.md` |

## Status

Pre-P1: technology decisions ratified 2026-07-26 (platform pending spike); pipeline bootstrap and spikes are next.
