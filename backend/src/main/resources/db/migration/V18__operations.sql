-- ===========================================================================
-- Operations (OPS): what a crew management service needs around the compliance engine —
-- renewals being chased, the vessel's own certificates, minimum safe manning, hours of rest,
-- crew change travel, the crew member's personal papers, notices with acknowledgement, and
-- the reminders that leave the building. None of it changes a compliance answer; the engine
-- reads none of these tables.
-- ===========================================================================

-- A renewal being worked: what is being done about an expiring certificate.
create table renewal (
    id             bigint generated always as identity primary key,
    person_id      bigint      not null references person (id),
    requirement_id bigint      not null references requirement (id),
    status         text        not null,
    provider       text,
    course_date    date,
    note           text,
    created_at     timestamptz not null default now(),
    created_by     text        not null,
    updated_at     timestamptz not null default now(),
    updated_by     text        not null,
    constraint renewal_person_requirement_key unique (person_id, requirement_id),
    constraint renewal_status_check check (status in ('booked', 'chased', 'evidence_in'))
);

-- The vessel's own papers: survey, class, safety equipment, radio, insurance.
create table vessel_certificate (
    id             bigint generated always as identity primary key,
    partnership_id bigint      not null references partnership (id),
    vessel_id      bigint      references vessel (id),
    title          text        not null,
    kind           text        not null,
    reference      text,
    issuer         text,
    issued_on      date,
    expires_on     date,
    object_key     text,
    file_name      text,
    content_type   text,
    byte_size      bigint,
    note           text,
    withdrawn_at   timestamptz,
    created_at     timestamptz not null default now(),
    created_by     text        not null,
    updated_at     timestamptz not null default now(),
    updated_by     text        not null
);
create index vessel_certificate_partnership_idx on vessel_certificate (partnership_id);

-- Minimum safe manning: how many of each position a swing (or a shift of it) must carry.
create table manning_requirement (
    id             bigint generated always as identity primary key,
    partnership_id bigint      not null references partnership (id),
    position_id    bigint      not null references crew_position (id),
    shift          text,
    required       integer     not null,
    created_at     timestamptz not null default now(),
    created_by     text        not null,
    updated_at     timestamptz not null default now(),
    updated_by     text        not null,
    constraint manning_required_check check (required >= 0)
);
create unique index manning_requirement_key on manning_requirement (partnership_id, position_id, coalesce(shift, ''));

-- Hours of rest, one row per person per day.
create table rest_record (
    id          bigint generated always as identity primary key,
    person_id   bigint        not null references person (id),
    day         date          not null,
    rest_hours  numeric(4, 1) not null,
    note        text,
    created_at  timestamptz   not null default now(),
    created_by  text          not null,
    updated_at  timestamptz   not null default now(),
    updated_by  text          not null,
    constraint rest_record_person_day_key unique (person_id, day),
    constraint rest_record_hours_check check (rest_hours >= 0 and rest_hours <= 24)
);

-- Crew change travel: flights, accommodation, transfers, per swing, per person or for the change.
create table travel_item (
    id             bigint generated always as identity primary key,
    crew_change_id bigint      not null references crew_change (id),
    person_id      bigint      references person (id),
    kind           text        not null,
    detail         text        not null,
    on_date        date,
    status         text        not null default 'planned',
    note           text,
    created_at     timestamptz not null default now(),
    created_by     text        not null,
    updated_at     timestamptz not null default now(),
    updated_by     text        not null,
    constraint travel_kind_check check (kind in ('flight', 'accommodation', 'transfer', 'other')),
    constraint travel_status_check check (status in ('planned', 'booked', 'done', 'cancelled'))
);
create index travel_item_crew_change_idx on travel_item (crew_change_id);

-- The crew member's personal papers and contacts — personal data, read by the office only.
create table person_detail (
    id                    bigint generated always as identity primary key,
    person_id             bigint      not null references person (id),
    passport_number       text,
    passport_expiry       date,
    visa_detail           text,
    visa_expiry           date,
    seafarer_book         text,
    seafarer_book_expiry  date,
    next_of_kin_name      text,
    next_of_kin_relation  text,
    next_of_kin_phone     text,
    emergency_contact     text,
    ppe_sizes             text,
    dietary               text,
    notes                 text,
    created_at            timestamptz not null default now(),
    created_by            text        not null,
    updated_at            timestamptz not null default now(),
    updated_by            text        not null,
    constraint person_detail_person_key unique (person_id)
);

-- Notices to crew, with who has read them.
create table notice (
    id             bigint generated always as identity primary key,
    customer_id    bigint      references customer (id),
    partnership_id bigint      references partnership (id),
    title          text        not null,
    body           text        not null,
    requires_ack   boolean     not null default true,
    posted_at      timestamptz not null default now(),
    withdrawn_at   timestamptz,
    created_at     timestamptz not null default now(),
    created_by     text        not null,
    updated_at     timestamptz not null default now(),
    updated_by     text        not null
);

create table notice_ack (
    id         bigint generated always as identity primary key,
    notice_id  bigint      not null references notice (id),
    person_id  bigint      not null references person (id),
    acked_at   timestamptz not null default now(),
    acked_by   text        not null,
    constraint notice_ack_key unique (notice_id, person_id)
);

-- Reminders that leave the building: one per person, certificate and band, sent or waiting.
create table reminder (
    id             bigint generated always as identity primary key,
    person_id      bigint      not null references person (id),
    requirement_id bigint      not null references requirement (id),
    expires_on     date        not null,
    days_before    integer     not null,
    sent_at        timestamptz,
    sent_by        text,
    channel        text,
    created_at     timestamptz not null default now(),
    created_by     text        not null,
    updated_at     timestamptz not null default now(),
    updated_by     text        not null,
    constraint reminder_key unique (person_id, requirement_id, expires_on, days_before)
);
create index reminder_person_idx on reminder (person_id);
