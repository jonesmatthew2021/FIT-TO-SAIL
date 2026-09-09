-- ===========================================================================
-- The business behind the program (BUS-1): what the office needs to run FIT TO SAIL as a
-- service to its customers, kept apart from what the compliance engine reads.
--
-- A customer gains the details a bill goes to and the terms it is billed on; an invoice is one
-- bill — a period, an amount, and where it is between drafted and paid. Nothing here touches a
-- compliance answer.
-- ===========================================================================

alter table customer
    add column billing_email        text,
    add column abn                  text,
    add column address              text,
    add column plan                 text,
    add column rate_per_ship_month  numeric(12, 2),
    add column billing_notes        text;

create table invoice (
    id            bigint generated always as identity primary key,
    customer_id   bigint         not null references customer (id),
    reference     text           not null,
    period_start  date           not null,
    period_end    date           not null,
    amount        numeric(12, 2) not null,
    status        text           not null default 'draft',
    issued_on     date,
    due_on        date,
    paid_on       date,
    note          text,
    created_at    timestamptz    not null default now(),
    created_by    text           not null,
    updated_at    timestamptz    not null default now(),
    updated_by    text           not null,
    constraint invoice_reference_key unique (reference),
    constraint invoice_status_check check (status in ('draft', 'sent', 'paid', 'void')),
    constraint invoice_period_check check (period_end >= period_start)
);

create index invoice_customer_idx on invoice (customer_id);

comment on table invoice is
    'One bill to a customer (BUS-1): a period, an amount, and where it stands. The office''s record, not the accountant''s.';
