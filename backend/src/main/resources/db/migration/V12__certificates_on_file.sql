-- ===========================================================================
-- Certificates on file — the office-side view of evidence, ported from the Coolibah portal.
--
-- Two small expansions, both nullable so every existing row and the previous application revision
-- keep working (expand/contract):
--
--   * `evidence_document.file_name` — the name the file arrived with. The portal lists a person's
--     certificates by filename ("EVANS_ Brenton - QL-14 AMSA GMDSS.pdf"), and a row that could only
--     be identified by its UUID is a row nobody can recognise. Never used for anything but display
--     and the `Content-Disposition` header; the stored object is still keyed by public id.
--
--   * `requirement.validity_months` / `requirement.validity_text` — the portal's per-code validity
--     period (§4.1 open until now: the seeders carried it in `notes` only). Months where the period
--     is a plain number; the free text ("1 or 2 years — as printed on the certificate") where it is
--     not. Presentation and a cross-check input, never an engine input: what a holding's expiry *is*
--     stays on the holding.
-- ===========================================================================

alter table evidence_document
    add column file_name text;

comment on column evidence_document.file_name is
    'The filename the document arrived with; display only. The stored object is keyed by public_id.';

alter table requirement
    add column validity_months integer,
    add column validity_text   text;

comment on column requirement.validity_months is
    'How long a certificate for this code stays valid, in months, where the period is a plain number. Presentation and cross-checks only.';
comment on column requirement.validity_text is
    'The validity period in the client''s own words where it is not a plain number of months ("1 or 2 years — as printed on the certificate").';
