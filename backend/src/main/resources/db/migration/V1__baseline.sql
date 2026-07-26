-- CREWCOMP baseline schema — spec §4 domain model.
--
-- Conventions (spec §4, NFR-5, CLAUDE.md hard rules):
--   * Every table has a surrogate `id`. Business keys (Sam #, requirement code, register record
--     ID, CC ID) are unique constraints on surrogate-keyed rows, never primary keys.
--   * Business dates are calendar dates in the operating timezone (AWST assumed, O-11) and are
--     stored as `date`. `timestamptz` appears only for system metadata and audit timing, which
--     are genuine instants.
--   * Enumerations are `text` + CHECK rather than Postgres enum types: Appendix A is expected to
--     grow, and adding a value to a CHECK is an expand-only migration that runs safely against
--     the previous application revision.
--   * Forward-only, expand/contract (CLAUDE.md): rollback rolls back code, never schema.
--
-- Requires PostgreSQL >= 15 for `UNIQUE NULLS NOT DISTINCT` (used where a NULL column
-- participates in a business-key uniqueness rule — base matrix rules, notably).

-- ===========================================================================
-- Reference layer (§4.1)
-- ===========================================================================

create table partnership (
    id            bigint generated always as identity primary key,
    abbrev        text        not null,
    name          text        not null,
    -- Nullable; drives the tier review rule (§4.1/§5.1 step 3).
    vessel_class  text,
    created_at    timestamptz not null default now(),
    created_by    text        not null,
    updated_at    timestamptz not null default now(),
    updated_by    text        not null,
    constraint partnership_abbrev_key unique (abbrev)
);

create table vessel (
    id             bigint generated always as identity primary key,
    name           text        not null,
    partnership_id bigint      not null references partnership (id),
    kind           text        not null,
    created_at     timestamptz not null default now(),
    created_by     text        not null,
    updated_at     timestamptz not null default now(),
    updated_by     text        not null,
    constraint vessel_name_key unique (name),
    constraint vessel_kind_check check (kind in ('tug', 'barge'))
);

create index vessel_partnership_idx on vessel (partnership_id);

create table crew_position (
    id         bigint generated always as identity primary key,
    name       text        not null,
    created_at timestamptz not null default now(),
    created_by text        not null,
    updated_at timestamptz not null default now(),
    updated_by text        not null,
    constraint crew_position_name_key unique (name)
);

-- Per position where applicable, e.g. Chief Officer Unlimited / 100m. Semantics pending O-3.
create table crew_position_tier (
    id          bigint generated always as identity primary key,
    position_id bigint      not null references crew_position (id),
    name        text        not null,
    created_at  timestamptz not null default now(),
    created_by  text        not null,
    updated_at  timestamptz not null default now(),
    updated_by  text        not null,
    constraint crew_position_tier_key unique (position_id, name)
);

create table position_slot (
    id         bigint generated always as identity primary key,
    ref        integer     not null,
    -- 'N/A' means aboard the whole swing (e.g. Cook) and counts toward BOTH shifts in quota
    -- evaluation (§4.1, §5.3).
    shift      text        not null,
    notes      text,
    created_at timestamptz not null default now(),
    created_by text        not null,
    updated_at timestamptz not null default now(),
    updated_by text        not null,
    constraint position_slot_ref_key unique (ref),
    constraint position_slot_shift_check check (shift in ('Shift 1', 'Shift 2', 'N/A'))
);

-- Slots 15/16 accept AE *or* GPH, hence a join table rather than a column (§4.1).
create table position_slot_allowed_position (
    slot_id     bigint not null references position_slot (id) on delete cascade,
    position_id bigint not null references crew_position (id),
    primary key (slot_id, position_id)
);

create table crew_change (
    id             bigint generated always as identity primary key,
    cc_id          text        not null,
    partnership_id bigint      not null references partnership (id),
    from_date      date        not null,
    to_date        date        not null,
    -- = from - 7 days per Q17, but STORED rather than derived so exceptions can exist (§4.1).
    cutoff_date    date        not null,
    created_at     timestamptz not null default now(),
    created_by     text        not null,
    updated_at     timestamptz not null default now(),
    updated_by     text        not null,
    constraint crew_change_key unique (cc_id, partnership_id),
    constraint crew_change_window_check check (to_date >= from_date)
);

create index crew_change_partnership_dates_idx on crew_change (partnership_id, from_date);

create table requirement (
    id                bigint generated always as identity primary key,
    code              text        not null,
    category          text        not null,
    title             text        not null,
    status            text        not null default 'active',
    issuing_authority text,
    notes             text,
    created_at        timestamptz not null default now(),
    created_by        text        not null,
    updated_at        timestamptz not null default now(),
    updated_by        text        not null,
    constraint requirement_code_key unique (code),
    constraint requirement_status_check check (status in ('active', 'retired')),
    -- Appendix A requirement categories.
    constraint requirement_category_check
        check (category in ('QL', 'VS', 'PS', 'MS', 'CS', 'HR', 'PT', 'VI', 'PI'))
);

-- Legacy free-text titles, for mapping register history (§4.1).
create table requirement_alias (
    id             bigint generated always as identity primary key,
    requirement_id bigint      not null references requirement (id) on delete cascade,
    alias          text        not null,
    created_at     timestamptz not null default now(),
    created_by     text        not null,
    constraint requirement_alias_key unique (requirement_id, alias)
);

create index requirement_alias_alias_idx on requirement_alias (lower(alias));

-- ===========================================================================
-- Rules layer (§4.2)
-- ===========================================================================

create table matrix_version (
    id             bigint generated always as identity primary key,
    label          text        not null,
    status         text        not null default 'draft',
    effective_from date,
    published_by   text,
    published_at   timestamptz,
    notes          text,
    -- The footnote label THIS version uses for the tier review rule, or NULL if it defines none.
    -- Footnote labels are data, not code: the same label means different things in different
    -- versions (§4.2), so this is per-version configuration.
    tier_footnote  text,
    created_at     timestamptz not null default now(),
    created_by     text        not null,
    updated_at     timestamptz not null default now(),
    updated_by     text        not null,
    constraint matrix_version_label_key unique (label),
    constraint matrix_version_status_check check (status in ('draft', 'published', 'superseded')),
    constraint matrix_version_published_check
        check (status <> 'published' or (effective_from is not null and published_at is not null))
);

-- Exactly one *current* published version at a time (latest effective_from wins, §4.2). The
-- "at most one draft-to-published transition at a time" invariant is enforced in the service
-- layer, which publishes atomically and supersedes the prior current version.
create index matrix_version_status_effective_idx on matrix_version (status, effective_from desc);

-- Vessel class -> tier accepted on that class, for the tier review rule (§5.1 step 3).
-- Absent mapping means "send to human review" — Mˣ is never auto-resolved (Q6).
create table matrix_tier_policy (
    id                bigint generated always as identity primary key,
    matrix_version_id bigint not null references matrix_version (id) on delete cascade,
    vessel_class      text   not null,
    accepted_tier     text   not null,
    constraint matrix_tier_policy_key unique (matrix_version_id, vessel_class, accepted_tier)
);

create table requirement_rule (
    id                bigint generated always as identity primary key,
    matrix_version_id bigint      not null references matrix_version (id) on delete cascade,
    -- NULL = the base ('*') rule that applies to every partnership (§4.2).
    partnership_id    bigint      references partnership (id),
    position_id       bigint      not null references crew_position (id),
    requirement_id    bigint      not null references requirement (id),
    -- 'M', a footnote label ('Mn'), 'R', or '' (an override that removes the requirement).
    level             text        not null,
    created_at        timestamptz not null default now(),
    created_by        text        not null,
    updated_at        timestamptz not null default now(),
    updated_by        text        not null,
    -- NULLS NOT DISTINCT so a version cannot carry two base rules for the same cell.
    constraint requirement_rule_key
        unique nulls not distinct (matrix_version_id, partnership_id, position_id, requirement_id)
);

create index requirement_rule_version_position_idx
    on requirement_rule (matrix_version_id, position_id);

create table conditional_rule (
    id                bigint generated always as identity primary key,
    matrix_version_id bigint      not null references matrix_version (id) on delete cascade,
    kind              text        not null,
    position_id       bigint      not null references crew_position (id),
    -- The target requirement for 'dependent' rules; NULL for 'one_of'.
    requirement_id    bigint      references requirement (id),
    -- Display label only, e.g. the CoC set's M⁸. Never interpreted (§4.2).
    label             text,
    created_at        timestamptz not null default now(),
    created_by        text        not null,
    updated_at        timestamptz not null default now(),
    updated_by        text        not null,
    constraint conditional_rule_kind_check check (kind in ('one_of', 'dependent')),
    constraint conditional_rule_target_check
        check ((kind = 'dependent') = (requirement_id is not null))
);

create index conditional_rule_version_position_idx
    on conditional_rule (matrix_version_id, position_id);

create table conditional_rule_member (
    id                  bigint generated always as identity primary key,
    conditional_rule_id bigint not null references conditional_rule (id) on delete cascade,
    requirement_id      bigint not null references requirement (id),
    -- 'member'            — a member of a one_of set
    -- 'required_if_holds' — a dependent rule's trigger
    -- 'unless_holds'      — a dependent rule's exclusion
    role                text   not null,
    ordinal             integer not null default 0,
    constraint conditional_rule_member_key unique (conditional_rule_id, requirement_id, role),
    constraint conditional_rule_member_role_check
        check (role in ('member', 'required_if_holds', 'unless_holds'))
);

create table quota_rule (
    id                bigint generated always as identity primary key,
    matrix_version_id bigint      not null references matrix_version (id) on delete cascade,
    -- Display label. Behaviour is keyed off this record, never off the text (§4.2).
    footnote          text        not null,
    requirement_id    bigint      not null references requirement (id),
    min_count         integer     not null,
    scope             text        not null,
    created_at        timestamptz not null default now(),
    created_by        text        not null,
    updated_at        timestamptz not null default now(),
    updated_by        text        not null,
    constraint quota_rule_key unique (matrix_version_id, footnote, requirement_id, scope),
    constraint quota_rule_scope_check check (scope in ('swing', 'shift')),
    constraint quota_rule_min_check check (min_count >= 0)
);

-- Absent rows = the quota counts people in any position (§4.2).
create table quota_rule_position (
    quota_rule_id bigint not null references quota_rule (id) on delete cascade,
    position_id   bigint not null references crew_position (id),
    primary key (quota_rule_id, position_id)
);

-- ===========================================================================
-- People layer (§4.3)
-- ===========================================================================

create table person (
    id             bigint generated always as identity primary key,
    -- Legacy business key. Deliberately NOT unique: the source data contains one known
    -- historical duplicate, preserved as an exception rather than silently cleaned (§4.3, §11).
    sam            text        not null,
    name           text        not null,
    position_id    bigint      not null references crew_position (id),
    tier           text,
    partnership_id bigint      not null references partnership (id),
    status         text        not null default 'active',
    -- New in production: required for mobile onboarding and notifications (§4.3).
    email          text,
    mobile         text,
    created_at     timestamptz not null default now(),
    created_by     text        not null,
    updated_at     timestamptz not null default now(),
    updated_by     text        not null,
    constraint person_status_check check (status in ('active', 'lookup_only', 'inactive'))
);

create index person_sam_idx on person (sam);
create index person_partnership_idx on person (partnership_id);

-- SEC-1a: only corporate backends on this administrator-managed allow-list may authenticate.
-- Onboarding a backend is an audited administrative act.
create table identity_provider (
    id               bigint generated always as identity primary key,
    -- 'entra' | 'google' | 'okta' | ... — the supported set is configuration, not code (SEC-1).
    provider         text        not null,
    -- The exact issuer URL. Entra: per-tenant only, never /common or /organizations (ADR 0003).
    issuer           text        not null,
    -- Entra tenant id, Google Workspace hosted domain (`hd`), or Okta org — the allow-list key.
    tenant_or_domain text        not null,
    display_name     text        not null,
    enabled          boolean     not null default false,
    created_at       timestamptz not null default now(),
    created_by       text        not null,
    updated_at       timestamptz not null default now(),
    updated_by       text        not null,
    constraint identity_provider_key unique (issuer, tenant_or_domain)
);

create table user_account (
    id                   bigint generated always as identity primary key,
    -- 1:1 with Person for crew members; back-office users may have none. Person records exist
    -- without accounts (not all crew onboard immediately) (§4.3).
    person_id            bigint      references person (id),
    -- SEC-1b: 'corporate' is the only kind permitted in production; 'local_test' is a flagged,
    -- enumerable transitional exception with a hard removal date.
    kind                 text        not null default 'corporate',
    identity_provider_id bigint      references identity_provider (id),
    -- The external identity linkage — and NOTHING else. No credentials, ever (SEC-1).
    issuer               text,
    subject              text,
    display_name         text        not null,
    email                text,
    status               text        not null default 'active',
    last_login_at        timestamptz,
    created_at           timestamptz not null default now(),
    created_by           text        not null,
    updated_at           timestamptz not null default now(),
    updated_by           text        not null,
    constraint user_account_kind_check check (kind in ('corporate', 'local_test')),
    constraint user_account_status_check check (status in ('active', 'suspended', 'disabled')),
    -- A corporate account is identified by (issuer, subject) and must name its allow-listed
    -- backend; a local/test account has neither.
    constraint user_account_corporate_check check (
        (kind = 'corporate' and issuer is not null and subject is not null
             and identity_provider_id is not null)
        or (kind = 'local_test' and issuer is null and subject is null)
    )
);

-- Partial rather than a table constraint: every local/test account has (NULL, NULL) here, and a
-- plain UNIQUE would therefore permit only one of them.
create unique index user_account_subject_unique
    on user_account (issuer, subject) where issuer is not null;

create unique index user_account_person_unique on user_account (person_id) where person_id is not null;

create table user_account_role (
    user_account_id bigint      not null references user_account (id) on delete cascade,
    role            text        not null,
    granted_at      timestamptz not null default now(),
    granted_by      text        not null,
    primary key (user_account_id, role),
    -- §3 role model. Role assignments themselves are audited (AUTH-4).
    constraint user_account_role_check check (role in (
        'crew_coordinator', 'workflow_manager', 'compliance_lead', 'data_steward',
        'vessel_master', 'crew_member', 'system_administrator'
    ))
);

-- Vessel Master is read-only, scoped to their vessel pair/partnership (§3).
create table user_account_partnership_scope (
    user_account_id bigint not null references user_account (id) on delete cascade,
    partnership_id  bigint not null references partnership (id),
    primary key (user_account_id, partnership_id)
);

create table qualification_holding (
    id             bigint generated always as identity primary key,
    person_id      bigint      not null references person (id),
    requirement_id bigint      not null references requirement (id),
    status         text        not null,
    -- Required iff status = 'held_expiry'.
    expiry_date    date,
    issue_date     date,
    note           text,
    created_at     timestamptz not null default now(),
    created_by     text        not null,
    updated_at     timestamptz not null default now(),
    updated_by     text        not null,
    constraint qualification_holding_key unique (person_id, requirement_id),
    constraint qualification_holding_status_check
        check (status in ('held_expiry', 'held_perpetual', 'not_held', 'unknown')),
    constraint qualification_holding_expiry_check
        check ((status = 'held_expiry') = (expiry_date is not null))
);

create index qualification_holding_person_idx on qualification_holding (person_id);
-- Drives the expiry-alert scan (§5.4) and the "unknown holdings" chase list (§4.3, Q11).
create index qualification_holding_expiry_idx on qualification_holding (expiry_date)
    where status = 'held_expiry';

create table assignment (
    id             bigint generated always as identity primary key,
    person_id      bigint      not null references person (id),
    crew_change_id bigint      not null references crew_change (id),
    partnership_id bigint      not null references partnership (id),
    slot_ref       integer     not null,
    -- May be a sub-range of the swing: mid-swing handovers put two sequential people in one slot.
    from_date      date        not null,
    to_date        date        not null,
    created_at     timestamptz not null default now(),
    created_by     text        not null,
    updated_at     timestamptz not null default now(),
    updated_by     text        not null,
    constraint assignment_window_check check (to_date >= from_date)
);

create index assignment_crew_change_idx on assignment (crew_change_id, slot_ref);
create index assignment_person_dates_idx on assignment (person_id, from_date);

create table leave_record (
    id          bigint generated always as identity primary key,
    person_id   bigint      not null references person (id),
    kind        text        not null,
    from_date   date        not null,
    to_date     date        not null,
    status      text        not null default 'recorded',
    -- Headroom for O-8: if leave becomes a mirror of an HR/payroll system, this carries the
    -- external key and `status` carries the upstream state.
    external_ref text,
    created_at  timestamptz not null default now(),
    created_by  text        not null,
    updated_at  timestamptz not null default now(),
    updated_by  text        not null,
    constraint leave_record_window_check check (to_date >= from_date),
    constraint leave_record_status_check
        check (status in ('recorded', 'requested', 'approved', 'declined', 'cancelled'))
);

create index leave_record_person_idx on leave_record (person_id, from_date);

-- ===========================================================================
-- Workflow layer (§4.4)
-- ===========================================================================

create table register_record (
    id                          bigint generated always as identity primary key,
    -- Business key {PT}{CCnn}-{seq}, auto-generated, monotonic per prefix.
    record_id                   text        not null,
    record_type                 text        not null,
    person_id                   bigint      references person (id),
    position_id                 bigint      references crew_position (id),
    -- Nullable; req_raw preserves unmapped legacy titles, badged in the UI (§4.4).
    requirement_id              bigint      references requirement (id),
    req_raw                     text,
    partnership_id              bigint      not null references partnership (id),
    crew_change_id              bigint      references crew_change (id),
    effective_from              date,
    effective_to                date,
    raised_date                 date        not null,
    status                      text        not null,
    outcome                     text,
    approval_from               date,
    approval_to                 date,
    booking_confirmed_date      date,
    lodgement_date              date,
    late_submission_acknowledged boolean    not null default false,
    created_at                  timestamptz not null default now(),
    created_by                  text        not null,
    updated_at                  timestamptz not null default now(),
    updated_by                  text        not null,
    constraint register_record_key unique (record_id),
    constraint register_record_type_check check (record_type in (
        'Exemption Request - PW',
        'Exemption Request - OPS',
        'Exemption Request following MRL Query',
        'MRL Query',
        'PW Query'
    )),
    constraint register_record_status_check check (status in (
        'Open - PW', 'Open - MRL', 'Open - OPS', 'Complete before joining',
        'Closed - Approved', 'Closed - Not Approved', 'Closed - Info Required',
        'Closed - Admin Action', 'Closed - Not Required'
    )),
    constraint register_record_outcome_check check (
        outcome is null
        or outcome in ('Approved', 'Not Approved', 'Info Required', 'Admin Action', 'Not Required')
    ),
    -- An approved record must carry its approval window (§4.4, §5.1 step 4).
    constraint register_record_approval_check check (
        outcome <> 'Approved'
        or (approval_from is not null and approval_to is not null and approval_to >= approval_from)
    )
);

create index register_record_person_requirement_idx
    on register_record (person_id, requirement_id, crew_change_id);
create index register_record_status_idx on register_record (status);
create index register_record_partnership_idx on register_record (partnership_id, raised_date desc);

-- Structured conditions (Q16), replacing free text.
create table approval_condition (
    id                 bigint generated always as identity primary key,
    register_record_id bigint      not null references register_record (id) on delete cascade,
    condition_type     text        not null,
    body               text        not null,
    created_at         timestamptz not null default now(),
    created_by         text        not null,
    constraint approval_condition_type_check
        check (condition_type in ('supervision', 'time_limit', 'duty_restriction', 'training_booked', 'other'))
);

create index approval_condition_record_idx on approval_condition (register_record_id);

-- Party-attributed notes: PW / MRL / OPS (§4.4).
create table register_note (
    id                 bigint generated always as identity primary key,
    register_record_id bigint      not null references register_record (id) on delete cascade,
    party              text        not null,
    body               text        not null,
    created_at         timestamptz not null default now(),
    created_by         text        not null,
    constraint register_note_party_check check (party in ('PW', 'MRL', 'OPS'))
);

create index register_note_record_idx on register_note (register_record_id, created_at);

-- The embedded human-readable audit trail that register records show to users (POC behaviour,
-- kept). This is IN ADDITION TO audit_event, which is the machine-readable, export-ready trail.
create table register_audit_entry (
    id                 bigint generated always as identity primary key,
    register_record_id bigint      not null references register_record (id) on delete cascade,
    ordinal            integer     not null,
    body               text        not null,
    actor              text        not null,
    occurred_at        timestamptz not null default now(),
    constraint register_audit_entry_key unique (register_record_id, ordinal)
);

-- Data-quality worklist (§4.4, §11). Anomalies are preserved and flagged, never silently cleaned.
create table exception_item (
    id                 bigint generated always as identity primary key,
    area               text        not null,
    description        text        not null,
    state              text        not null default 'open',
    linked_entity_type text,
    linked_entity_id   bigint,
    resolution_note    text,
    resolved_at        timestamptz,
    resolved_by        text,
    created_at         timestamptz not null default now(),
    created_by         text        not null,
    updated_at         timestamptz not null default now(),
    updated_by         text        not null,
    constraint exception_item_state_check check (state in ('open', 'resolved'))
);

create index exception_item_state_idx on exception_item (state, area);

create table notification (
    id                bigint generated always as identity primary key,
    recipient_user_id bigint      not null references user_account (id) on delete cascade,
    kind              text        not null,
    -- SEC-13: push payloads carry title + deep link only; the detail lives here and is fetched
    -- in-app.
    title             text        not null,
    body              text,
    deep_link         text,
    read_at           timestamptz,
    created_at        timestamptz not null default now(),
    created_by        text        not null
);

create index notification_recipient_idx on notification (recipient_user_id, created_at desc);

create table notification_delivery (
    id              bigint generated always as identity primary key,
    notification_id bigint      not null references notification (id) on delete cascade,
    channel         text        not null,
    status          text        not null,
    attempted_at    timestamptz not null default now(),
    error           text,
    constraint notification_delivery_channel_check check (channel in ('in_app', 'push', 'email')),
    constraint notification_delivery_status_check check (status in ('sent', 'failed', 'skipped'))
);

create index notification_delivery_notification_idx on notification_delivery (notification_id);

-- ===========================================================================
-- Evidence layer (§8) — pipeline lands in P3; the shape is fixed now so that P3 is additive.
-- ===========================================================================

create table evidence_document (
    id                  bigint generated always as identity primary key,
    -- Client-generated, so mobile submissions are idempotent across offline retries (§7.6).
    public_id           uuid        not null,
    person_id           bigint      not null references person (id),
    -- Opaque key into the object-storage adapter; no cloud SDK types leak into the schema
    -- (ADR 0005).
    object_key          text,
    content_type        text,
    byte_size           bigint,
    source              text        not null,
    submitted_by        text        not null,
    submitted_at        timestamptz not null default now(),
    verification_status text        not null default 'pending_extraction',
    linked_holding_id   bigint      references qualification_holding (id),
    -- Structured extraction output with per-field confidence, plus the raw model response
    -- retained for audit (§8 stage 2).
    -- Structured, queryable: the extracted fields with their per-field confidence.
    extraction          jsonb,
    extraction_model    text,
    -- The raw model response, retained verbatim for audit (§8 stage 2) — text for the same
    -- byte-fidelity reason as audit_event above.
    extraction_raw      text,
    rejection_reason    text,
    created_at          timestamptz not null default now(),
    created_by          text        not null,
    updated_at          timestamptz not null default now(),
    updated_by          text        not null,
    constraint evidence_document_public_id_key unique (public_id),
    constraint evidence_document_source_check
        check (source in ('mobile_camera', 'mobile_file', 'admin_upload')),
    constraint evidence_document_status_check check (verification_status in (
        'pending_extraction', 'pending_review', 'auto_accepted', 'verified', 'rejected'
    ))
);

create index evidence_document_person_idx on evidence_document (person_id, submitted_at desc);
create index evidence_document_queue_idx on evidence_document (verification_status, submitted_at);

-- ===========================================================================
-- Audit (§4.4, SEC-6, AIA-3) and configuration (ADM-10)
-- ===========================================================================

-- Append-only. Self-contained, immutable, and stably ordered so that the external read-only
-- audit mirror (§17.4/AUD-1..3) is a later *consumer* rather than a schema migration.
--
--   * `seq` is assigned by the application under a serialising advisory lock, so the sequence is
--     gapless and its order is the real commit order. A bare identity column would be monotonic
--     but could interleave across concurrent transactions, which defeats AUD-2's gap detection.
--   * `prev_hash`/`event_hash` form a hash chain from the first event, so a rewrite or deletion
--     is detectable by an external auditor without trusting this database.
--   * There are no UPDATE or DELETE paths in the application; the table is insert-only.
create table audit_event (
    seq               bigint      primary key,
    occurred_at       timestamptz not null default now(),
    -- AIA-3: human / AI-proposed-human-approved / AI-automatic must be distinguishable.
    actor_kind        text        not null,
    actor_user_id     bigint      references user_account (id),
    -- Denormalised on purpose: an audit event must remain readable after the actor row changes
    -- or is removed. Self-containment is the point (§4.4).
    actor_label       text        not null,
    entity_type       text        not null,
    entity_id         bigint,
    entity_business_key text,
    event             text        not null,
    -- `text`, deliberately NOT `jsonb`: jsonb reorders object keys and reformats whitespace, so
    -- a payload does not round-trip byte-for-byte. The hash chain is computed over these exact
    -- bytes, so a normalising column type would break every verification (AUD-2). Queries that
    -- want structure can cast with `::jsonb`.
    before_state      text,
    after_state       text,
    -- AIA-3: model and prompt/config version for AI-touched events.
    ai_model          text,
    ai_config_version text,
    request_id        text,
    prev_hash         text,
    event_hash        text        not null,
    constraint audit_event_actor_kind_check
        check (actor_kind in ('human', 'ai_proposed', 'ai_automatic', 'system')),
    -- An AI-proposed action must name the human who approved it (AIA-2).
    constraint audit_event_ai_actor_check
        check (actor_kind <> 'ai_proposed' or actor_user_id is not null)
);

create index audit_event_entity_idx on audit_event (entity_type, entity_id, seq);
create index audit_event_occurred_idx on audit_event (occurred_at);

-- ADM-10 configuration: expiry lead days, suggestion weights, LLM auto-accept threshold,
-- notification schedules, per-assist autonomy settings (AIA-4).
create table app_config (
    key        text        primary key,
    value      jsonb       not null,
    updated_at timestamptz not null default now(),
    updated_by text        not null
);
