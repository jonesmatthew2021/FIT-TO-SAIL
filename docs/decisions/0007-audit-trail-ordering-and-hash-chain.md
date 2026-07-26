# ADR 0007 — Audit trail: application-allocated sequence and hash chain

**Status:** Accepted (2026-07-26)
**Spec refs:** §4.4 AuditEvent, SEC-6, AUTH-3, AIA-3, §17.4 (AUD-1..3), O-14

## Context

§4.4 requires AuditEvents to be "self-contained, immutable records with a stable global order
(monotonic sequence)", and §17.4 says this shape **bites now in P1** so the external read-only
mirror is later a *consumer* rather than a schema migration. AUD-2 wants tamper-evidence: an
external auditor must be able to detect gaps or rewrites.

The obvious implementation — a `bigint generated always as identity` column — satisfies
"monotonic" but not the property the auditor actually needs.

## Decision

Three things, all in `AuditWriter` and `AuditChain`:

1. **`seq` is allocated by the application**, inside the business transaction, under
   `pg_advisory_xact_lock`. Audit appends therefore serialise, and `seq` follows real commit
   order with no gaps.
2. **Every event is hash-chained**: `event_hash = SHA-256(canonical_form)` where the canonical
   form includes `prev_hash`. The canonical form's field order and its ASCII unit-separator (`\u001F`) delimiter (escaped
   out of field values) are part of the audit contract.
3. **`audit_event` is insert-only.** The entity is mapped `@Immutable`; there is no update or
   delete path anywhere in the application.
4. **Everything hashed must round-trip byte-for-byte.** Two consequences that look like
   infelicities until you know why:
   - `before_state` / `after_state` are `text`, not `jsonb`. Postgres `jsonb` reorders object keys
     and reformats whitespace, so a payload does not come back as it went in and every
     verification fails. Queries that want structure cast with `::jsonb`.
   - `occurred_at` is truncated to microseconds — `timestamptz`'s precision — on write and again
     in the canonical form, because `Instant.now()` carries nanoseconds the column cannot store.

   Both were found by the first run of `ComplianceIT` against real PostgreSQL; neither is
   detectable by a test that never persists.

Verification lives in `AuditChain.verify`, so the writer, the future export job (AUD-3) and the
ADM-10 integrity view all check the same rule an external auditor would.

## Rationale

- An identity column allocates its value at INSERT, not at COMMIT. Two concurrent transactions can
  take 7 and 8 and commit in the reverse order, and a rolled-back transaction burns its value
  permanently. Under AUD-2 both look exactly like tampering, so the trail would cry wolf — and a
  detector that cries wolf is one that gets ignored, which is worse than not having it.
- Serialising audit appends is free at NFR-1's scale (~60 users, ~10² register rows/year). This
  would be the wrong trade at high write volume; it is the obviously right one here.
- Hashing `prev_hash` into each event is what makes an *in-place* rewrite detectable. Without the
  chain, an attacker with UPDATE rights could edit a row and recompute its own hash.
- Denormalising `actor_label` and the before/after JSON keeps an exported event readable without
  the rest of the database — §4.4's self-containment requirement, and a precondition for the
  mirror living in separate storage with independent access control (AUD-1).

## Consequences

- Audit writes are serialised across the application. If write volume ever grows by orders of
  magnitude this becomes a bottleneck and the decision must be revisited — the escape hatch is
  per-partition chains, which costs the single global order.
- `AuditWriter.record` is `@Transactional(MANDATORY)`: calling it outside a business transaction
  fails loudly rather than silently landing the event in its own transaction.
- Changing the canonical form invalidates every hash already written. Any such change needs a
  re-chaining migration and its own ADR.
- The advisory lock key `0x4352455741554454` is fixed and must not be reused by other code.
- Still open: **O-14** (who consumes the external trail, what independence and retention it needs)
  determines the mirror's technology — WORM object storage, ledger DB, or a third-party service.
  Nothing above pre-commits that choice.
