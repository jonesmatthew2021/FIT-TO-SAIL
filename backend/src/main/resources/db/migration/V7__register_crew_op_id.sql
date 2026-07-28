-- ===========================================================================
-- MOB-10 — the device's queue-entry id on the register record it raised.
--
-- A crew member's exemption request arrives through the §7.6 outbox, which re-posts an operation
-- it never saw a verdict for. Without an idempotency key here, a dropped connection at the wrong
-- moment allocates a **second business key** for one request: two `{PT}{CCnn}-n` rows, both open,
-- both about the same requirement, and a Compliance Lead deciding one while the other sits there.
--
-- `op_id` is that key, exactly as it is for `crew_statement` and for an evidence submission's
-- `public_id`. The uniqueness is the mechanism — a read-then-write that two concurrent deliveries
-- could both pass would not be one.
--
-- It doubles as provenance: a record with this set was raised from a phone rather than by a
-- coordinator, which is worth being able to ask about and is not otherwise recoverable from the
-- row. (The trail says so in words; this says so in a query.)
--
-- Expand-only: nullable, and null for every record a coordinator raises.
-- ===========================================================================

alter table register_record add column crew_op_id text;

-- Partial, because only crew-raised records have one and a plain UNIQUE would permit exactly one
-- coordinator-raised record in the whole table.
create unique index register_record_crew_op_id_unique
    on register_record (crew_op_id)
    where crew_op_id is not null;

comment on column register_record.crew_op_id is
    'MOB-10: the device queue-entry id that raised this, and its idempotency key. Null when a coordinator raised it.';
