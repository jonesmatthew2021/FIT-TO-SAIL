-- ===========================================================================
-- §10.3 mobile sync — change tracking
--
-- The spec fixes the contract (`GET /sync/snapshot`, `GET /sync/delta?cursor=…`) and leaves the
-- mechanism to §14 research: "per-row `updated_seq` (monotonic global sequence) or change-log
-- table". This is the per-row form, with one deliberate difference from the obvious
-- implementation: the sequence is stamped by a **database trigger**, not by application code.
--
-- Why a trigger rather than a field on AuditedEntity:
--
--   * A cursor that a write path can forget is a cursor that silently stops replicating. The
--     failure is invisible — the client syncs successfully and simply never sees the row again.
--     A trigger cannot be forgotten by a new service, an MCP tool, a Flyway data fix-up, or a
--     psql session.
--   * It keeps sync correctness independent of Hibernate's flush order and dirty-checking.
--
-- The cost is that `updated_seq` is not meaningful in a Hibernate-managed instance until the row
-- is refreshed; entities therefore map it read-only and nothing in the application writes it.
--
-- Expand/contract: every column added here is filled by its trigger on INSERT, so the previous
-- application revision — which knows nothing about these columns — keeps working unchanged, and
-- the NOT NULL constraints below are safe against it. Rollback rolls back code, never schema.
-- ===========================================================================

-- One global sequence, so a client holds a single scalar high-water mark across every entity
-- type. `cache 1` matters: a cached sequence hands out per-session blocks, which would let a
-- later commit take a *lower* seq than an earlier one and fall behind a client's cursor forever.
create sequence sync_seq as bigint start with 1 increment by 1 cache 1;

create or replace function sync_stamp() returns trigger
    language plpgsql as
$$
begin
    new.updated_seq := nextval('sync_seq');
    return new;
end;
$$;

-- ---------------------------------------------------------------------------
-- Tombstones
--
-- A delta must carry deletions, and a deleted row cannot carry its own sequence number.
-- `person_id` is the scoping key: a client only ever receives tombstones for its own person,
-- exactly as it only receives rows for its own person. A null `person_id` means reference data,
-- which is unscoped.
-- ---------------------------------------------------------------------------
create table sync_tombstone (
    seq         bigint      primary key default nextval('sync_seq'),
    entity_type text        not null,
    entity_id   bigint      not null,
    -- Nullable: reference-data tombstones belong to no person.
    person_id   bigint,
    deleted_at  timestamptz not null default now()
);

create index sync_tombstone_person_idx on sync_tombstone (person_id, seq);
create index sync_tombstone_seq_idx on sync_tombstone (seq);

-- The owning person is captured from the deleted row itself. TG_ARGV[0] is the entity type as
-- the sync contract names it; TG_ARGV[1] is the column on the deleted row holding the person id
-- ('id' for person itself).
create or replace function sync_tombstone_record() returns trigger
    language plpgsql as
$$
declare
    owner_id bigint;
begin
    execute format('select ($1).%I', TG_ARGV[1]) into owner_id using old;
    insert into sync_tombstone (entity_type, entity_id, person_id)
    values (TG_ARGV[0], old.id, owner_id);
    return old;
end;
$$;

-- ---------------------------------------------------------------------------
-- Person-scoped tables: replicated row-by-row to the owning crew member's device (§7.6).
-- ---------------------------------------------------------------------------

alter table person add column updated_seq bigint;
alter table qualification_holding add column updated_seq bigint;
alter table assignment add column updated_seq bigint;
alter table leave_record add column updated_seq bigint;
alter table notification add column updated_seq bigint;
alter table evidence_document add column updated_seq bigint;

-- ---------------------------------------------------------------------------
-- Reference tables.
--
-- These are not person-scoped, and the delta does not stream them row-by-row: a matrix
-- publication changes the meaning of most of the client's cached rules at once, so a partial
-- application of it would show crew a coherent-looking view assembled from two matrix versions.
-- Instead the client compares a single `referenceCursor` and re-snapshots when it moves. Matrix
-- publications happen a few times a year; the payload is a few tens of kilobytes.
--
-- `conditional_rule_member` and `quota_rule_position` are deliberately absent: they are only
-- edited while their matrix version is still a draft, and a draft is never sent to a device.
-- ---------------------------------------------------------------------------

alter table partnership add column updated_seq bigint;
alter table crew_position add column updated_seq bigint;
alter table crew_change add column updated_seq bigint;
alter table requirement add column updated_seq bigint;
alter table matrix_version add column updated_seq bigint;
alter table requirement_rule add column updated_seq bigint;
alter table conditional_rule add column updated_seq bigint;
alter table quota_rule add column updated_seq bigint;
alter table position_slot add column updated_seq bigint;

-- ---------------------------------------------------------------------------
-- Triggers. BEFORE INSERT OR UPDATE so the value is present in the same tuple version the
-- statement writes; no second UPDATE, no recursion.
-- ---------------------------------------------------------------------------

create trigger person_sync_seq before insert or update on person
    for each row execute function sync_stamp();
create trigger qualification_holding_sync_seq before insert or update on qualification_holding
    for each row execute function sync_stamp();
create trigger assignment_sync_seq before insert or update on assignment
    for each row execute function sync_stamp();
create trigger leave_record_sync_seq before insert or update on leave_record
    for each row execute function sync_stamp();
create trigger notification_sync_seq before insert or update on notification
    for each row execute function sync_stamp();
create trigger evidence_document_sync_seq before insert or update on evidence_document
    for each row execute function sync_stamp();

create trigger partnership_sync_seq before insert or update on partnership
    for each row execute function sync_stamp();
create trigger crew_position_sync_seq before insert or update on crew_position
    for each row execute function sync_stamp();
create trigger crew_change_sync_seq before insert or update on crew_change
    for each row execute function sync_stamp();
create trigger requirement_sync_seq before insert or update on requirement
    for each row execute function sync_stamp();
create trigger matrix_version_sync_seq before insert or update on matrix_version
    for each row execute function sync_stamp();
create trigger requirement_rule_sync_seq before insert or update on requirement_rule
    for each row execute function sync_stamp();
create trigger conditional_rule_sync_seq before insert or update on conditional_rule
    for each row execute function sync_stamp();
create trigger quota_rule_sync_seq before insert or update on quota_rule
    for each row execute function sync_stamp();
create trigger position_slot_sync_seq before insert or update on position_slot
    for each row execute function sync_stamp();

-- Deletion tracking, person-scoped tables only. `notification` is keyed by recipient user
-- account rather than person, so its tombstone resolves the person through user_account.
create trigger person_sync_tombstone after delete on person
    for each row execute function sync_tombstone_record('Person', 'id');
create trigger qualification_holding_sync_tombstone after delete on qualification_holding
    for each row execute function sync_tombstone_record('QualificationHolding', 'person_id');
create trigger assignment_sync_tombstone after delete on assignment
    for each row execute function sync_tombstone_record('Assignment', 'person_id');
create trigger leave_record_sync_tombstone after delete on leave_record
    for each row execute function sync_tombstone_record('LeaveRecord', 'person_id');
create trigger evidence_document_sync_tombstone after delete on evidence_document
    for each row execute function sync_tombstone_record('EvidenceDocument', 'person_id');

create or replace function sync_tombstone_notification() returns trigger
    language plpgsql as
$$
declare
    owner_id bigint;
begin
    select ua.person_id into owner_id from user_account ua where ua.id = old.recipient_user_id;
    insert into sync_tombstone (entity_type, entity_id, person_id)
    values ('Notification', old.id, owner_id);
    return old;
end;
$$;

create trigger notification_sync_tombstone after delete on notification
    for each row execute function sync_tombstone_notification();

-- ---------------------------------------------------------------------------
-- Backfill and constrain. Existing rows all become "changed" exactly once, which is correct:
-- no device has synced yet, so every row is new to every client.
-- ---------------------------------------------------------------------------

update person set updated_seq = nextval('sync_seq') where updated_seq is null;
update qualification_holding set updated_seq = nextval('sync_seq') where updated_seq is null;
update assignment set updated_seq = nextval('sync_seq') where updated_seq is null;
update leave_record set updated_seq = nextval('sync_seq') where updated_seq is null;
update notification set updated_seq = nextval('sync_seq') where updated_seq is null;
update evidence_document set updated_seq = nextval('sync_seq') where updated_seq is null;
update partnership set updated_seq = nextval('sync_seq') where updated_seq is null;
update crew_position set updated_seq = nextval('sync_seq') where updated_seq is null;
update crew_change set updated_seq = nextval('sync_seq') where updated_seq is null;
update requirement set updated_seq = nextval('sync_seq') where updated_seq is null;
update matrix_version set updated_seq = nextval('sync_seq') where updated_seq is null;
update requirement_rule set updated_seq = nextval('sync_seq') where updated_seq is null;
update conditional_rule set updated_seq = nextval('sync_seq') where updated_seq is null;
update quota_rule set updated_seq = nextval('sync_seq') where updated_seq is null;
update position_slot set updated_seq = nextval('sync_seq') where updated_seq is null;

alter table person alter column updated_seq set not null;
alter table qualification_holding alter column updated_seq set not null;
alter table assignment alter column updated_seq set not null;
alter table leave_record alter column updated_seq set not null;
alter table notification alter column updated_seq set not null;
alter table evidence_document alter column updated_seq set not null;
alter table partnership alter column updated_seq set not null;
alter table crew_position alter column updated_seq set not null;
alter table crew_change alter column updated_seq set not null;
alter table requirement alter column updated_seq set not null;
alter table matrix_version alter column updated_seq set not null;
alter table requirement_rule alter column updated_seq set not null;
alter table conditional_rule alter column updated_seq set not null;
alter table quota_rule alter column updated_seq set not null;
alter table position_slot alter column updated_seq set not null;

-- Delta queries are `where person_id = ? and updated_seq > ?` per table.
create index person_sync_idx on person (updated_seq);
create index qualification_holding_sync_idx on qualification_holding (person_id, updated_seq);
create index assignment_sync_idx on assignment (person_id, updated_seq);
create index leave_record_sync_idx on leave_record (person_id, updated_seq);
create index notification_sync_idx on notification (recipient_user_id, updated_seq);
create index evidence_document_sync_idx on evidence_document (person_id, updated_seq);

-- ---------------------------------------------------------------------------
-- Resumable evidence upload (MOB-5a).
--
-- Evidence files are captured on a vessel and uploaded over maritime connectivity, so an upload
-- must survive the app being killed and resume from a byte offset rather than restarting. The
-- server tracks received bytes per document; the object-storage adapter holds the bytes
-- themselves (no cloud types in the schema — ADR 0005).
--
-- `upload_offset` is the number of contiguous bytes received from zero. A chunk arriving at any
-- other offset is rejected, which is what makes the endpoint idempotent under retry: replaying
-- an already-applied chunk is a no-op that returns the current offset.
-- ---------------------------------------------------------------------------
alter table evidence_document add column upload_offset bigint not null default 0;
alter table evidence_document add column upload_complete boolean not null default false;
-- Chunks are stored as individual objects and concatenated on completion, because the
-- ObjectStorage adapter offers put/get and deliberately not append (ADR 0005 — a narrow
-- interface is what makes the second implementation cheap). Parts are named from this counter,
-- so reassembly needs no listing capability from the adapter.
alter table evidence_document add column upload_part_count integer not null default 0;
-- Client-declared total, so the server can recognise completion and reject overruns.
alter table evidence_document add column declared_size bigint;
-- Client-declared digest, verified on completion; a mismatch fails the document rather than
-- feeding a corrupt file to the extraction pipeline (§8).
alter table evidence_document add column declared_sha256 text;

-- §8 stage 3 matching is "seeded by the user's hint" (§7.5): the crew member may tag which
-- requirement a submission evidences. It is a hint and never binding — extraction may correct
-- it — so it is deliberately separate from `linked_holding_id`, which only the verified pipeline
-- sets.
alter table evidence_document add column requirement_hint_id bigint references requirement (id);

alter table evidence_document
    add constraint evidence_document_upload_offset_check check (upload_offset >= 0);
