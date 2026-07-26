# ADR 0006 — Persistence: Hibernate ORM with Panache (not jOOQ)

**Status:** Accepted (2026-07-26) — provisional, revisit at the P1 schema freeze if the spike surfaces a blocker
**Spec refs:** §4, §13, NFR-3, NFR-7, DEV-2, DEV-5
**Supersedes:** the open ORM question left by ADR 0001 ("Hibernate ORM/Panache or jOOQ — spike decides")

## Context

ADR 0001 deferred the ORM choice. Beginning the backend forces it, because the entity mapping, the
repository shape and the migration workflow all follow from it.

The domain (§4) is a conventional normalised relational model with heavy read paths that are all
small (NFR-1: ~60 people, ~10³ holdings, ~10² register rows/year). The compliance engine (§5) is a
pure function over loaded values, so the persistence layer's only job is to load entity graphs
cheaply and to write validated changes with an audit event.

## Decision

**Hibernate ORM with Panache repositories**, entities mapped by hand against a Flyway-owned schema:

- **Flyway owns DDL absolutely.** `quarkus.hibernate-orm.schema-management.strategy=validate`;
  Hibernate never generates DDL. Migrations are forward-only and expand/contract, so a
  Hibernate-authored `ALTER` would break the rule that a migration must run against the previous
  application revision.
- **Repository pattern, not active record.** Entities are data; `PanacheRepositoryBase` classes hold
  the queries. This keeps the service layer the single validated entry point (AUTH-1) and keeps
  the crew row-scoping clause (AUTH-2) in one composable place.
- **Enumerations as `text` + CHECK in the schema, `String` fields with typed accessors in the
  entities.** Appendix A values differ from Kotlin's naming, and a JPA `AttributeConverter` per
  enum is boilerplate that buys nothing over an accessor.

## Rationale

- **The engine already isolates the hard part.** jOOQ's advantage is precise, typed SQL for
  complex queries — but §5's complexity lives in pure Kotlin over loaded values, not in SQL. There
  is no analytical query here for jOOQ to win.
- **jOOQ's codegen needs a live database at build time.** That inserts a container dependency into
  every compile, including the AI-review and preview-environment lanes, against OPS-1's fully
  automated pipeline and the one-person constraint.
- **AI-assist affinity (DEV-5).** Quarkus + Panache is the single most heavily documented Quarkus
  persistence path; jOOQ-on-Quarkus is a thin, less-trodden corner.
- **Lazy-loading traps are bounded here.** The N+1 risk that usually argues against an ORM is
  contained because every engine input is loaded through an explicit repository method with
  `join fetch`, and the datasets are small enough that a mistake is a code-review issue rather
  than a production incident.

## Consequences

- Entity/schema drift is caught by `validate` at start-up, but only where a test boots the
  application: `ComplianceIT` exists for exactly that reason and must stay in CI's required set.
- The crew row-scoping clause is composed into HQL by `ScopeGuard` rather than enforced by the
  database. **Postgres row-level security remains the stronger option** the spec offers (AUTH-2)
  and is the recommended P1 hardening — see the follow-up in `backend/CLAUDE.md`.
- If a genuinely complex reporting query appears later (the §17.4 audit export, XLSX audit shapes
  per O-7), jOOQ or plain SQL can be added alongside for that query without revisiting this ADR:
  the decision is about the *domain* mapping, not a ban on SQL.
