-- ===========================================================================
-- Customers — the companies the operation takes on as clients (COM-1).
--
-- A partnership (§4.1) is one operation: a vessel pairing, a rotation, a roster. The company
-- above it — who the operation is run *for* — had no row of its own; "MinRes Onslow" lived
-- inside the partnership's name. As the office takes on more than one client that stops being
-- enough: the client is who the contacts belong to, whose paperwork the certificates satisfy,
-- and what a coordinator means by "the MinRes crew".
--
-- One table, and a nullable foreign key from partnership: existing partnerships keep working
-- unattached (expand/contract), and attaching one is an explicit act on the company screen.
-- ===========================================================================

create table customer (
    id            bigint generated always as identity primary key,
    name          text        not null,
    -- How the office says it ("MinRes"), where the legal name is longer.
    short_name    text,
    contact_name  text,
    contact_email text,
    contact_phone text,
    notes         text,
    status        text        not null default 'active',
    created_at    timestamptz not null default now(),
    created_by    text        not null,
    updated_at    timestamptz not null default now(),
    updated_by    text        not null,
    constraint customer_name_key unique (name),
    constraint customer_status_check check (status in ('active', 'former'))
);

comment on table customer is
    'A client company the operation works for (COM-1). Partnerships hang off it; nothing in the compliance engine reads it.';

alter table partnership
    add column customer_id bigint references customer (id);

create index partnership_customer_idx on partnership (customer_id);
