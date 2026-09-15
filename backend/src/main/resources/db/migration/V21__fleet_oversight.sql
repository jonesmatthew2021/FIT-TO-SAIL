-- ===========================================================================
-- Fleets and oversight (BUS-2): a company that watches ships it does not operate.
--
-- A ship belongs to the customer that operates it (partnership.customer_id). Some companies
-- watch a fleet across operators — Portways monitors every MinRes barge whoever runs it. A
-- fleet is a named group of ships from any customers; an overseer is a customer given sight of
-- a fleet. An account linked to a customer is scoped to that customer's own ships and the ships
-- of every fleet it oversees, kept in step whenever a fleet or its overseers change.
-- ===========================================================================

create table fleet (
    id          bigint generated always as identity primary key,
    name        text        not null,
    note        text,
    created_at  timestamptz not null default now(),
    created_by  text        not null,
    updated_at  timestamptz not null default now(),
    updated_by  text        not null,
    constraint fleet_name_key unique (name)
);

create table fleet_member (
    fleet_id       bigint not null references fleet (id) on delete cascade,
    partnership_id bigint not null references partnership (id),
    primary key (fleet_id, partnership_id)
);

create table fleet_oversight (
    fleet_id    bigint not null references fleet (id) on delete cascade,
    customer_id bigint not null references customer (id),
    primary key (fleet_id, customer_id)
);

-- The company an account works for. Null for the office's own accounts and for accounts made
-- before this existed; those keep whatever scope they were given by hand.
alter table user_account
    add column customer_id bigint references customer (id);

create index user_account_customer_idx on user_account (customer_id);
