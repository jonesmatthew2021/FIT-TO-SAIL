# admin-web/ — React + TypeScript admin SPA

Not yet scaffolded — starts after the backend spike publishes an OpenAPI schema.

## Stack (decided)

React + TypeScript. API client types **generated from the backend's OpenAPI schema** (DEV-2) — never hand-written request/response types. Served as static assets; authenticates via the backend's BFF session cookie (ADR 0003 — no tokens in the browser, no OIDC logic in this app).

## Scope

Feature parity with the V1 POC on real auth/data: modules ADM-1..10 (spec §6). Dense-table-heavy screens (matrix, register, planner) — favour capable table components over bespoke rendering. Every list exports CSV; every entity view deep-links; role-gated UI is defence-in-depth only (the server enforces, AUTH-1).
