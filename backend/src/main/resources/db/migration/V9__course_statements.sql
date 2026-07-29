-- ===========================================================================
-- Two more crew statements — MOB-8's "I want that seat" and "put me on the waitlist".
--
-- These are the same kind of thing as `course_booked` and `help_requested` and deliberately land
-- in the same table and the same ADM-11 queue: a crew member asking the office for something, with
-- no power to grant it. A crew member cannot commit a training budget or bind a provider, so a
-- seat request is an *ask* and the office's answer is what makes it real. Nothing here holds a
-- seat, decrements an inventory or promises a place — the system does not have a contract with
-- the provider and must not behave as though it did.
--
-- Two new columns, because a course statement names something the other two never did:
--
--   * `subject_ref`  — the course option the crew member picked, as the device named it. Opaque
--                      to this table on purpose: it is a catalogue key, and the catalogue is
--                      behind an adapter that may one day be a provider feed rather than a table
--                      here.
--   * `subject_label`— the same option rendered as a sentence, by the **server**, at the moment
--                      the statement was recorded. Denormalised for the same reason a register
--                      record's note is: the coordinator reading this queue in three weeks needs
--                      to know which course was asked for even if the option has since been
--                      withdrawn from the catalogue, and a dangling key tells them nothing.
--
-- The label is the server's rendering rather than the device's summary text, which is the same
-- rule the attestation's signature line follows: what appears in the office's record of what a
-- crew member asked for should not be composed on the crew member's phone.
-- ===========================================================================

alter table crew_statement
    add column subject_ref   text,
    add column subject_label text;

-- Forward-only and backward-compatible (expand/contract): the previous revision writes neither
-- column and reads neither, and every existing row keeps a null in both — which is the honest
-- value, since `course_booked` and `help_requested` name no subject beyond the requirement.
alter table crew_statement
    drop constraint crew_statement_kind_check;

alter table crew_statement
    add constraint crew_statement_kind_check
        check (kind in ('course_booked', 'help_requested', 'seat_requested', 'waitlisted'));

comment on column crew_statement.subject_ref is
    'MOB-8: the course option the crew member picked, as the catalogue adapter names it. Opaque here.';
comment on column crew_statement.subject_label is
    'The option as a sentence, rendered server-side when the statement was recorded. Survives the option.';
