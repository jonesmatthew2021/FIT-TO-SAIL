-- ===========================================================================
-- MOB-9 — the pre-sail attestation.
--
-- A crew member confirming, before they join a swing, that their certificates are the ones on
-- record and that they are fit to sail. The screen says "a false declaration is a disciplinary
-- matter", which is the whole reason this is a table rather than a notification: it is a record
-- someone may later be asked to stand behind.
--
-- Three properties follow from that, and each is a column here rather than a convention:
--
--   * **The timestamp is the server's.** `signed_at` is set on arrival, in the operating timezone's
--     terms, because a legal declaration timestamped from a phone clock is worth nothing — a device
--     clock is settable by the person making the declaration. The app deliberately never prints one
--     of its own; its signature block says "the office records the time it arrives".
--   * **What was confirmed is stored, line by line** (`attestation_declaration`). "They signed it"
--     is not the record; *which lines they ticked* is, and a crew member can legitimately sign with
--     one line outstanding.
--   * **It is keyed on the device's `op_id`.** The §7.6 outbox re-posts an operation it never saw a
--     verdict for, and two attestations for one swing would be two legal records of one act.
--
-- What is deliberately *not* here yet: a biometric assertion. ADR 0002 wants `local_auth`-bound
-- keys and there is none on this build, so the record means "confirmed on this device by the
-- authenticated crew member" — weaker than a signature, and stored as such rather than dressed up.
-- `assertion` is the column it will land in; null until the device spike.
-- ===========================================================================

create table attestation (
    id             bigint      generated always as identity primary key,
    op_id          text        not null,
    person_id      bigint      not null references person (id),
    assignment_id  bigint      not null references assignment (id),
    -- Denormalised from the assignment so the record survives the assignment being changed. A
    -- declaration is about the swing the person signed for, and re-rostering them must not silently
    -- rewrite what they attested to.
    crew_change_id bigint      not null references crew_change (id),
    -- The server's clock, in the operating timezone's terms (O-11). Never the device's.
    signed_at      timestamptz not null default now(),
    -- Reserved for the device spike's biometric assertion (ADR 0002). Null means "confirmed on this
    -- device by the authenticated crew member", which is what this build can honestly claim.
    assertion      text,
    created_at     timestamptz not null default now(),
    created_by     text        not null,
    updated_seq    bigint      not null,
    constraint attestation_op_id_key unique (op_id)
);

-- Which lines were ticked. A child table rather than a delimited string: this is the part of the
-- record that gets read back and compared, and "did they confirm `medically_fit`" should be a
-- predicate rather than a substring search.
create table attestation_declaration (
    attestation_id bigint not null references attestation (id) on delete cascade,
    declaration_id text   not null,
    primary key (attestation_id, declaration_id)
);

create index attestation_person_idx on attestation (person_id, crew_change_id);
create index attestation_sync_idx on attestation (person_id, updated_seq);

create trigger attestation_sync_seq before insert or update on attestation
    for each row execute function sync_stamp();

create trigger attestation_sync_tombstone after delete on attestation
    for each row execute function sync_tombstone_record('Attestation', 'person_id');

comment on table attestation is
    'MOB-9 pre-sail declaration. The timestamp is the server''s; the device never supplies one.';
