-- §9: scheduled scans need to be idempotent.
--
-- The expiry lead-time job runs daily and recomputes what is due. Without an identity for "this
-- notification, about this fact", a crew member whose medical expires in 90 days receives the same
-- warning every morning for 90 mornings, and the one notification that matters — the new one — is
-- indistinguishable from the 89 that came before it. Notification fatigue is not a cosmetic
-- problem: it is how a real expiry gets ignored.
--
-- `dedupe_key` is that identity, chosen by the raising code and stable across runs (for an expiry
-- warning: person, requirement and the expiry date it is about). A second raise with the same key
-- for the same recipient is a no-op.
--
-- Expand-only: nullable, so a notification raised by a domain event carries none and the previous
-- application revision keeps working unchanged.
alter table notification
    add column dedupe_key text;

-- Partial and per-recipient. Per-recipient because the same fact is a separate notification for
-- each person it concerns (§9: "Notification rows in the database, per recipient"); partial because
-- event-driven notifications have no key and a plain UNIQUE would permit only one of them.
create unique index notification_dedupe_key_unique
    on notification (recipient_user_id, dedupe_key)
    where dedupe_key is not null;

comment on column notification.dedupe_key is
    'Stable identity for a scheduled notification, so a daily scan does not re-raise it (§9).';
