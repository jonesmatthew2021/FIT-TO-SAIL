-- ===========================================================================
-- Crew statements — what a crew member *says* about a requirement (§7.5, MOB-5).
--
-- The crew app's one-tap answers ("I have booked the course", "I need help arranging it") are
-- statements, never decisions. Nothing here is read by the §5 engine, nothing here changes a cell
-- state, and nothing here is a holding: AUTH-1 keeps the compliance answer the engine's, and §7.5
-- lists evidence submission and read-marks as the only client-originated writes to the system of
-- record. This is a third thing — a message from the person to the office, durable enough to be
-- worked and audited, and deliberately outside the evaluation path.
--
-- Why it is a table rather than only a notification:
--
--   * A notification needs a recipient, and back-office accounts do not exist yet (ADR 0003). A
--     crew member who taps "Course booked" today would otherwise have their answer fan out to
--     nobody and vanish, with the app cheerfully reporting "Sent to the office".
--   * `about_expiry` lets the expiry scan stop chasing a person who has already answered, and
--     start again when the fact changes. That needs the statement to outlive the tap.
--
-- Write-once. A statement is never edited — superseding one is a new row — which is why there are
-- no `updated_*` columns (see CreatedEntity).
-- ===========================================================================

create table crew_statement (
    id             bigint      generated always as identity primary key,
    -- The device's idempotency key for the queue entry that carried this (§7.6). Unique, so a
    -- retry after a dropped connection re-posts the same id and lands as a no-op rather than as a
    -- second answer.
    op_id          text        not null,
    person_id      bigint      not null references person (id),
    requirement_id bigint      not null references requirement (id),
    kind           text        not null,
    -- The expiry date this statement was about, read from the person's holding at the time.
    --
    -- The same rule as a notification dedupe key: the identity includes the *value*, not just the
    -- subject. A booking answered against a 14 Aug expiry stops the chasing for that expiry; when
    -- the certificate is renewed the expiry moves, no statement matches it, and the scan resumes.
    -- Null when there was no expiring holding to be about, in which case it suppresses nothing.
    about_expiry   date,
    created_at     timestamptz not null default now(),
    created_by     text        not null,
    -- §10.3 sync cursor, trigger-assigned. Present from birth rather than added later: every
    -- person-scoped table carries one, and a table that acquires a cursor in a second migration
    -- starts with rows that have none.
    updated_seq    bigint      not null,
    constraint crew_statement_op_id_key unique (op_id),
    constraint crew_statement_kind_check check (kind in ('course_booked', 'help_requested'))
);

create index crew_statement_person_idx on crew_statement (person_id, requirement_id);
create index crew_statement_sync_idx on crew_statement (person_id, updated_seq);

create trigger crew_statement_sync_seq before insert or update on crew_statement
    for each row execute function sync_stamp();

create trigger crew_statement_sync_tombstone after delete on crew_statement
    for each row execute function sync_tombstone_record('CrewStatement', 'person_id');

comment on table crew_statement is
    'A crew member''s own statement about a requirement (§7.5). Never read by the §5 engine.';
