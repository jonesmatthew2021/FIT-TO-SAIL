# Runbooks

Operational runbooks, one markdown file per alert/failure class. These are **load-bearing**: the AI ops-triage workflow (ADR 0004, spec OPS-3) reads the incoming alert, matches it against this directory, and either remediates per runbook (whitelisted actions only), drafts a fix, or escalates with a diagnosis.

Conventions (apply once runbooks exist):
- Filename = alert class (e.g. `deploy-rollback.md`, `sync-job-failure.md`, `db-backup-verify-failure.md`).
- Each runbook states: symptoms, diagnosis steps (commands the triage agent may run read-only), remediation (marked `AUTO` if whitelisted for unattended execution, `HUMAN` otherwise), and escalation criteria.
- The autonomy boundary (which remediations are `AUTO`) is a client policy decision — spec O-16. Default everything to `HUMAN` until O-16 is answered.
