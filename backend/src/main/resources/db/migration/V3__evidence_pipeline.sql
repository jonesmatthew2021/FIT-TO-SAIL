-- §8 evidence pipeline, stages 3 and 4: what the pipeline resolved a document to, and why it
-- sent the document to a human instead of accepting it.
--
-- Expand-only and additive: both columns are nullable, so the previous application revision — which
-- knows nothing about them — keeps working against this schema unchanged.
--
-- `matched_requirement_id` is deliberately separate from the two requirement references
-- evidence_document already carries:
--
--   requirement_hint_id  — the crew member's tag for what they think this evidences (§7.5). An
--                          input to matching, and it may be wrong.
--   matched_requirement_id — what stage 3 resolved. Recorded even when stage 4 declines to accept,
--                          because "matched but awaiting review" is the normal state while
--                          auto-acceptance is off (LLM-2) and it is what pre-fills ADM-9's form.
--   linked_holding_id    — set only once a holding has actually been written (stage 4 or 5). It is
--                          the evidence of a *decision*, and LLM-1 is the reason it cannot be set
--                          by extraction.
alter table evidence_document
    add column matched_requirement_id bigint references requirement (id);

alter table evidence_document
    add column review_reason text;

comment on column evidence_document.matched_requirement_id is
    'Requirement resolved by §8 stage 3. Never written by the model itself (LLM-1).';

comment on column evidence_document.review_reason is
    'Why §8 stage 4 routed this to ADM-9 rather than auto-accepting. Shown in the review queue.';
