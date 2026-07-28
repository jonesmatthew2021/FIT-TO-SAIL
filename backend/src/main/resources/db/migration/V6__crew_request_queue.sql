-- ===========================================================================
-- ADM-11 — the crew request queue.
--
-- V5 made a crew statement durable. This makes it *workable*: a Crew Coordinator can see what is
-- outstanding, do something about it, and say what they did.
--
-- A notification is a "something happened" signal and nothing more. It is addressed to whoever
-- held the role at the moment it was raised, it is marked read by one person, and there is no
-- query anywhere that answers "what has the crew asked us for that nobody has dealt with". Without
-- that query the statement table is an inbox nobody empties.
--
-- ### Why the statement itself is still immutable
--
-- Nothing added here touches what the crew member *said*: `kind`, `requirement_id` and
-- `about_expiry` remain write-once. What becomes mutable is the office's **disposition** of it,
-- which is a different fact with a different author. That is why the row gains `updated_*` (it is
-- now an AuditedEntity) rather than the statement being copied into a second table: one row, two
-- authors, and the audit trail distinguishes them by event name.
--
-- ### The one behavioural change
--
-- `dismissed` **resumes expiry chasing**. V5 let a `course_booked` statement silence the crew-facing
-- expiry warning for that expiry, unconditionally and permanently — defensible only while nobody
-- could disagree with it. Now that a coordinator can answer "we have no record of that booking",
-- the warning has to come back, or a crew member who mis-tapped goes unchased until the certificate
-- lapses. See `CrewStatementRepository.courseBookedIndexUnscoped`.
--
-- Expand-only: every column is nullable or defaulted, so the previous application revision keeps
-- writing statements that are simply always `open`.
-- ===========================================================================

alter table crew_statement add column status text not null default 'open';

-- What the coordinator did, in their words. Required on a decision — a queue emptied with no
-- explanation is indistinguishable from one emptied to clear the badge, which is the same argument
-- ADM-7 makes for its resolution note.
alter table crew_statement add column decision_note text;
alter table crew_statement add column decided_at timestamptz;
alter table crew_statement add column decided_by text;

-- The disposition has an author and a time of its own; the statement's `created_*` belongs to the
-- crew member. Defaulted so existing rows are valid without a backfill pass.
alter table crew_statement add column updated_at timestamptz not null default now();
alter table crew_statement add column updated_by text not null default 'system';

alter table crew_statement
    add constraint crew_statement_status_check check (status in ('open', 'actioned', 'dismissed'));

-- Open iff undecided, in both directions: a decided row without a timestamp and an open row with
-- one are each a bug that would otherwise only show up as a confusing queue.
alter table crew_statement
    add constraint crew_statement_decided_check check ((status = 'open') = (decided_at is null));

-- The queue's own query — open items, oldest first, because the oldest unanswered request is the
-- one someone is still waiting on.
create index crew_statement_status_idx on crew_statement (status, created_at);

comment on column crew_statement.status is
    'ADM-11 queue state. Both non-open states are terminal — asking again raises a new statement.';
