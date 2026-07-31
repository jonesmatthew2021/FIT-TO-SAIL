-- ADM-2: one slot, one person at a time — enforced where it cannot be forgotten.
--
-- The application validates "two people share a slot only in sequence" with a read-then-check,
-- now serialised by an advisory lock per (crew_change_id, slot_ref). This constraint is the
-- backstop the application cannot be: a future write path (a batch apply, a migration script,
-- hand-written SQL) that never heard of the lock still cannot commit two overlapping people
-- into one slot.
--
-- btree_gist is what lets equality columns join a GiST exclusion constraint. It is a trusted
-- extension, so creating it needs the database owner, not a superuser.
create extension if not exists btree_gist;

-- Inclusive bounds ('[]'): assignments are calendar-date ranges and an assignment ending on the
-- 14th overlaps one starting on the 14th — a handover is 01..14 then 15..28 (NFR-5).
alter table assignment
    add constraint assignment_slot_no_overlap
        exclude using gist (
            crew_change_id with =,
            slot_ref with =,
            daterange(from_date, to_date, '[]') with &&
        );

comment on constraint assignment_slot_no_overlap on assignment is
    'Two people never hold one slot at once (§4.3); sequential legs of a handover are permitted.';
