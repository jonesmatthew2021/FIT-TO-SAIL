-- Which flag a ship sails under and which manning regime it answers to — asked when a ship is
-- added, and what decides which regulatory list it starts from.
alter table partnership
    add column registry text,
    add column regime   text;

alter table partnership
    add constraint partnership_registry_check check (registry is null or registry in ('australian', 'international')),
    add constraint partnership_regime_check check (regime is null or regime in ('domestic', 'international'));
