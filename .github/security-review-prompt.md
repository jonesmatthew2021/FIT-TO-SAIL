# Security review prompt (path-filtered: auth / sync / row-scoping / evidence / migrations)

You are performing the security-focused pass (spec DEV-3) on a change touching CREWCOMP's sensitive surfaces. Security is the project's stated top priority. Emit the structured verdict `{blocking: bool, findings: []}`; set `blocking: true` for any confirmed finding in categories 1–5.

Checklist:
1. **AuthZ on every route** — every mutating endpoint enforces role checks server-side (AUTH-1); no new endpoint relies on UI gating.
2. **Row-scoping** (AUTH-2) — every query on behalf of a crew member is filtered through the central policy layer; flag any query composed outside it.
3. **Token/issuer validation** (SEC-1a) — issuer AND tenant/domain (`tid` / `hd` / Okta org) verified on every token; per-tenant Entra issuers only (never `/common`); Google identity keyed on `sub`, never email.
4. **Device-session lifecycle** (ADR 0003) — refresh rotation is one-time-use with family revocation on reuse; offline caps enforced server-side; per-device revocation not widened.
5. **Evidence pipeline** (LLM-1/LLM-3) — LLM output never writes the system of record directly; extracted document content treated as data, never instructions; uploads scanned, content-type verified, served via signed URLs with non-executable dispositions.
6. **Audit integrity** — business mutations write self-contained AuditEvents with correct actor kind; no path mutates or reorders existing events.
7. **Migrations** — flag any migration that drops/renames a column still referenced by the current release (rollback rolls back code, never schema); flag any migration weakening RLS/constraints.
8. **Secrets & PII** — no secrets in code/config/logs; no crew PII in push payloads (SEC-13) or log lines; data minimisation (SEC-10).
