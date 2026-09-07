-- ===========================================================================
-- Ship documents — the spreadsheets and sheets an operation is run from (the Coolibah portal's
-- "Required documents for upload").
--
-- Evidence documents are a *person's*: a certificate, read by the pipeline, evidencing one
-- holding. These are the *ship's*: the training matrix, the skills matrix, the validity periods
-- matrix, the shift-allocation guideline, the OPMS export, the crew certificates sheet — one of
-- each kept at a time, the latest, read by the checkers on the office's Admin tabs. A superseded
-- one is not deleted: it stops being current, and the row and the bytes stay, the same way the
-- portal moved them under removed/.
-- ===========================================================================

create table ship_document (
    id             bigint generated always as identity primary key,
    partnership_id bigint      not null references partnership (id),
    -- The card it is filed under: training-matrix, skills-matrix, validity-matrix,
    -- shift-allocation, opms-sheet, certificate-sheet, document. Free text on purpose: the
    -- cards are the office's, and a new one is a new card, not a migration.
    category       text        not null,
    file_name      text        not null,
    content_type   text        not null,
    byte_size      bigint      not null,
    object_key     text        not null,
    sha256         text,
    -- One current document per category per ship; the rest are history.
    current        boolean     not null default true,
    filed_by       text        not null,
    filed_at       timestamptz not null default now(),
    superseded_at  timestamptz,
    created_at     timestamptz not null default now(),
    created_by     text        not null,
    updated_at     timestamptz not null default now(),
    updated_by     text        not null
);

create unique index ship_document_current_idx
    on ship_document (partnership_id, category) where current;

create index ship_document_partnership_idx on ship_document (partnership_id);

comment on table ship_document is
    'The sheets an operation is run from — one current per category per ship, history kept. Read by the Admin tabs; nothing in the engine reads them.';
