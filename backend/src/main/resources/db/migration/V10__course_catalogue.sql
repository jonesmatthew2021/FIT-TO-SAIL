-- ===========================================================================
-- The course catalogue — MOB-8's dates.
--
-- ### Why a table here, and why behind an adapter
--
-- A course catalogue is not in the §4 domain model, and it may one day be a provider feed rather
-- than anything this system stores. It is a table today for two reasons and neither is permanent:
-- no provider integration has been identified, and writing one against a hypothetical API is the
-- same mistake as writing a cloud stack before ADR 0005 resolves.
--
-- What *is* permanent is the seam. Everything above reads `CourseCatalogue`, a port whose only
-- implementation happens to be backed by this table; swapping in a feed replaces the adapter and
-- nothing else. That boundary is worth getting right now because the interesting half of MOB-8 is
-- not the dates — it is the join against the crew member's own roster and expiry, which is ours
-- whoever supplies the dates.
--
-- ### What a row is and is not
--
-- A row is an *offer*: a date somebody could attend, with the seat count the provider last stated.
-- It is not an inventory this system controls. Nothing decrements `seats`, no request holds a
-- place, and a crew member tapping "request this seat" writes a `crew_statement` for a coordinator
-- to action (MOB-8, ADM-11). Pretending otherwise would have the system assert a reservation with
-- a third party it has no contract with.
--
-- `option_ref` rather than the surrogate key is what travels: it is the business key a provider
-- feed would supply, it is what a device holds in its outbox after the option is withdrawn, and
-- business keys are never primary keys here.
-- ===========================================================================

create table course_option (
    id             bigint      generated always as identity primary key,
    -- The business key. Stable across a re-import, and what a crew statement's `subject_ref`
    -- points at — including after this row is gone, which is why the statement also stores a
    -- rendered label rather than only the key.
    option_ref     text        not null,
    requirement_id bigint      not null references requirement (id),
    -- Calendar dates in the operating timezone (NFR-5, O-11). A course runs on days, not instants.
    starts         date        not null,
    finishes       date        not null,
    provider       text        not null,
    location       text        not null,
    -- "2 days", "1 day refresher" — the provider's own phrasing, shown verbatim. Derived from the
    -- date range it would be wrong as often as right: a two-day course can span a weekend.
    duration_label text,
    -- What the provider last said. Zero means the option is waitlist-only, which is a real thing
    -- to offer rather than a reason to hide the date.
    seats          integer     not null default 0,
    -- Withdrawn options are deactivated, never deleted: a crew statement may point at one, and a
    -- coordinator reading that request needs the row to still resolve.
    active         boolean     not null default true,
    created_at     timestamptz not null default now(),
    created_by     text        not null,
    updated_at     timestamptz not null default now(),
    updated_by     text        not null,
    constraint course_option_ref_key unique (option_ref),
    constraint course_option_dates_check check (finishes >= starts),
    constraint course_option_seats_check check (seats >= 0)
);

create index course_option_requirement_idx on course_option (requirement_id, starts);

comment on table course_option is
    'MOB-8 course dates. An offer, never an inventory: nothing here holds a seat (see CourseCatalogue).';
