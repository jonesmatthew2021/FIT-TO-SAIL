-- ===========================================================================
-- The swing pattern — the Coolibah portal's four-week A/B rotation, per ship.
--
-- The portal reads every swing off a pattern: crews A and B alternate, each swing flying out on
-- an anchor date plus a whole number of cycles, and the office types real dates over the pattern's
-- when a changeover slips. The engine here evaluates a swing given its roster; what it lacked was
-- the rotation that *produces* the roster, and the pattern that produces the swings. Three small
-- expansions, all nullable so nothing existing changes meaning:
--
--   * `person.rotation`          — which crew the person sails with: 'A' or 'B'. Null: not on
--                                  a rotation (a relief, or a ship with no pattern).
--   * `partnership.roster_*`     — the ship's pattern: the anchor day out, the cycle in days,
--                                  and which crew flies out on the anchor. Null: no pattern.
--   * `crew_change.rotation`     — which crew a swing carries, and `pattern_k` its number in the
--                                  pattern (anchor + k cycles), so "use the pattern" can put a
--                                  swing's dates back once the office has typed over them.
-- ===========================================================================

alter table person
    add column rotation text,
    add constraint person_rotation_check check (rotation is null or rotation in ('A', 'B'));

alter table partnership
    add column roster_anchor      date,
    add column roster_cycle_days  integer,
    add column roster_anchor_crew text,
    add constraint partnership_roster_cycle_check check (roster_cycle_days is null or roster_cycle_days > 0),
    add constraint partnership_roster_crew_check check (roster_anchor_crew is null or roster_anchor_crew in ('A', 'B'));

alter table crew_change
    add column rotation  text,
    add column pattern_k integer,
    add constraint crew_change_rotation_check check (rotation is null or rotation in ('A', 'B'));

comment on column person.rotation is 'The crew the person sails with on the ship''s rotation: A or B. Null: not on a rotation.';
comment on column partnership.roster_anchor is 'The day the pattern''s swing 0 flies out; swing k flies out anchor + k cycles.';
comment on column crew_change.pattern_k is 'The swing''s number in the ship''s pattern, when it was made from it — what "use the pattern" reads.';
