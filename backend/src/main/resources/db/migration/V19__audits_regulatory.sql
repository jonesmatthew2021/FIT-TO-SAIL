-- ===========================================================================
-- Auditing and AMSA requirements (OPS): the audits a ship has had and what they found, and the
-- regulatory obligations under the National Law with where each stands.
-- ===========================================================================

create table audit (
    id             bigint generated always as identity primary key,
    partnership_id bigint      not null references partnership (id),
    kind           text        not null,
    auditor        text,
    audit_date     date        not null,
    scope          text,
    outcome        text,
    status         text        not null default 'open',
    note           text,
    created_at     timestamptz not null default now(),
    created_by     text        not null,
    updated_at     timestamptz not null default now(),
    updated_by     text        not null,
    constraint audit_kind_check check (kind in ('internal', 'amsa', 'class', 'customer', 'flag', 'other')),
    constraint audit_status_check check (status in ('planned', 'open', 'closed'))
);
create index audit_partnership_idx on audit (partnership_id);

create table audit_finding (
    id                bigint generated always as identity primary key,
    audit_id          bigint      not null references audit (id),
    reference         text,
    severity          text        not null,
    description       text        not null,
    corrective_action text,
    owner             text,
    due_on            date,
    closed_on         date,
    created_at        timestamptz not null default now(),
    created_by        text        not null,
    updated_at        timestamptz not null default now(),
    updated_by        text        not null,
    constraint finding_severity_check check (severity in ('major', 'minor', 'observation'))
);
create index audit_finding_audit_idx on audit_finding (audit_id);

create table regulatory_item (
    id             bigint generated always as identity primary key,
    partnership_id bigint      not null references partnership (id),
    reference      text        not null,
    title          text        not null,
    kind           text        not null,
    status         text        not null default 'unknown',
    evidence       text,
    next_due       date,
    responsible    text,
    note           text,
    created_at     timestamptz not null default now(),
    created_by     text        not null,
    updated_at     timestamptz not null default now(),
    updated_by     text        not null,
    constraint regulatory_kind_check check (kind in ('certificate', 'system', 'record', 'survey', 'other')),
    constraint regulatory_status_check check (status in ('met', 'not_met', 'unknown', 'not_applicable'))
);
create index regulatory_item_partnership_idx on regulatory_item (partnership_id);
