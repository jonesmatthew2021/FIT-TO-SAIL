import { useState } from 'react'
import { Link } from 'react-router-dom'
import {
  useAcceptEvidence,
  useEvidenceQueue,
  useExtractEvidence,
  useRejectEvidence,
  useRequirements,
} from '../api/queries'
import { api, ApiError, type EvidenceDocument, type ExtractedField } from '../api/client'
import { useHasRole } from '../api/session'
import { DataTable, type Column } from '../components/DataTable'
import { ErrorPanel } from '../components/ErrorPanel'
import { Spinner } from '../components/Spinner'
import {
  HOLDING_STATUS_VALUES,
  QUEUE_STATUSES,
  confidenceTone,
  holdingStatus,
  verificationStatus,
} from '../domain/enums'

/** §8 stage 5: the Data Steward decides. Everyone else may look at the backlog. */
const EVIDENCE_DECIDERS = ['data_steward', 'system_administrator'] as const

/**
 * ADM-9 — the evidence verification queue (§8).
 *
 * ### The screen exists because of LLM-1
 *
 * The pipeline reads a document and writes what it read into `evidence_document`. It does **not**
 * touch a holding unless the auto-accept gate is deliberately open, and that gate is shut at launch
 * (LLM-2). So every document arrives here, and this screen is the only thing that turns a
 * photograph into a compliance fact. That is the whole design, not a limitation of it.
 *
 * ### Accept and correct are one action
 *
 * The form below is pre-filled from the extraction and is entirely editable. Submitting it accepts
 * *the values in the form*, which may or may not be the ones the model produced — and the server
 * records both, so a correction is visible as a correction. Measuring the gap between extracted and
 * accepted is exactly what LLM-2 needs before auto-acceptance can responsibly be switched on, and a
 * separate "correct" button would have made that gap invisible.
 *
 * ### Confidence is shown, and it is not the decision
 *
 * Every field carries the model's confidence beside it. The colours are presentation only: what
 * actually gates auto-acceptance is a server-side threshold in ADM-10, and a reviewer's job is to
 * read the document, not to arbitrate a number.
 */
export function Evidence(): React.ReactNode {
  const [statuses, setStatuses] = useState<readonly string[]>([
    'pending_review',
    'pending_extraction',
    'auto_accepted',
  ])
  const queue = useEvidenceQueue(statuses)
  const requirements = useRequirements()
  const canDecide = useHasRole(...EVIDENCE_DECIDERS)
  const [selectedId, setSelectedId] = useState<string | null>(null)

  if (queue.isPending) return <Spinner label="Loading the verification queue" />
  if (queue.error !== null) {
    return <ErrorPanel title="Could not load the queue" error={queue.error} />
  }

  const rows = queue.data
  const selected = rows.find((row) => row.publicId === selectedId) ?? null
  const code = (id: number | null) =>
    id === null ? null : (requirements.data?.find((r) => r.id === id)?.code ?? `#${id}`)

  const columns: Column<EvidenceDocument>[] = [
    {
      id: 'submittedAt',
      header: 'Submitted',
      accessorFn: (row) => row.submittedAt,
      cell: ({ row }) => formatMoment(row.original.submittedAt),
    },
    {
      id: 'person',
      header: 'Crew',
      accessorFn: (row) => `${row.personName} ${row.sam}`,
      cell: ({ row }) => (
        <>
          <Link to={`/people/${row.original.personId}`} onClick={(event) => event.stopPropagation()}>
            {row.original.personName}
          </Link>{' '}
          <span className="muted mono">{row.original.sam}</span>
        </>
      ),
    },
    { id: 'partnership', header: 'Partnership', accessorFn: (row) => row.partnershipAbbrev },
    {
      id: 'status',
      header: 'Status',
      accessorFn: (row) => row.verificationStatus,
      cell: ({ row }) => {
        const display = verificationStatus(row.original.verificationStatus)
        return (
          <span className={`chip chip--${display.tone}`} title={display.description}>
            {display.label}
          </span>
        )
      },
    },
    {
      id: 'matched',
      header: 'Read as',
      accessorFn: (row) => code(row.matchedRequirementId) ?? '',
      cell: ({ row }) => {
        const matched = code(row.original.matchedRequirementId)
        return matched === null ? (
          <span className="muted">unmatched</span>
        ) : (
          <span className="mono">{matched}</span>
        )
      },
    },
    {
      id: 'hint',
      header: 'Tagged as',
      accessorFn: (row) => code(row.requirementHintId) ?? '',
      cell: ({ row }) => {
        const hint = code(row.original.requirementHintId)
        return hint === null ? <span className="muted">—</span> : <span className="mono">{hint}</span>
      },
    },
    {
      id: 'reason',
      header: 'Why it is here',
      accessorFn: (row) => row.reviewReason ?? '',
      cell: ({ row }) =>
        row.original.reviewReason ?? <span className="muted">—</span>,
    },
    { id: 'source', header: 'Source', accessorFn: (row) => row.source },
  ]

  return (
    <div className="screen">
      <header className="screen__header">
        <h1 className="screen__title">Evidence queue</h1>
        <p className="screen__subtitle">
          ADM-9 — documents the pipeline has read and a human has to decide on (§8). The model never
          writes a holding (LLM-1).
        </p>
      </header>

      <div className="selector">
        <label className="field field--inline">
          <span className="field__label">Statuses</span>
          <span className="checkbox-group">
            {QUEUE_STATUSES.map((status) => (
              <label key={status} className="checkbox-group__item">
                <input
                  type="checkbox"
                  className="checkbox"
                  checked={statuses.includes(status)}
                  onChange={(event) =>
                    setStatuses((current) =>
                      event.target.checked
                        ? [...current, status]
                        : current.filter((value) => value !== status),
                    )
                  }
                />
                {verificationStatus(status).label}
              </label>
            ))}
          </span>
        </label>
      </div>

      <DataTable
        rows={rows}
        columns={columns}
        filterPlaceholder="Filter by crew, requirement or reason"
        empty="Nothing in the queue for these statuses."
        onRowClick={(row) => setSelectedId(row.publicId === selectedId ? null : row.publicId)}
        rowClassName={(row) =>
          row.verificationStatus === 'pending_review' ? 'table__row--attention' : undefined
        }
        csv={{
          filename: 'evidence-queue.csv',
          columns: [
            { header: 'Submitted at', value: (row) => row.submittedAt },
            { header: 'Sam #', value: (row) => row.sam },
            { header: 'Name', value: (row) => row.personName },
            { header: 'Partnership', value: (row) => row.partnershipAbbrev },
            { header: 'Status', value: (row) => row.verificationStatus },
            { header: 'Read as', value: (row) => code(row.matchedRequirementId) },
            { header: 'Tagged as', value: (row) => code(row.requirementHintId) },
            { header: 'Reason', value: (row) => row.reviewReason },
            { header: 'Rejection reason', value: (row) => row.rejectionReason },
            { header: 'Extraction model', value: (row) => row.extractionModel },
            { header: 'Source', value: (row) => row.source },
          ],
        }}
      />

      {selected !== null && (
        <ReviewPanel key={selected.publicId} document={selected} canDecide={canDecide} />
      )}
    </div>
  )
}

/**
 * The side-by-side review: the document on one side, the fields on the other (§6).
 *
 * `key={publicId}` on the caller is doing real work — it remounts this component when the reviewer
 * moves to the next document, so the form resets to the new extraction instead of keeping the last
 * one's typed values.
 */
function ReviewPanel({
  document,
  canDecide,
}: {
  document: EvidenceDocument
  canDecide: boolean
}): React.ReactNode {
  const requirements = useRequirements()
  const accept = useAcceptEvidence()
  const reject = useRejectEvidence()
  const extract = useExtractEvidence()

  const extracted = (name: string): ExtractedField | undefined =>
    document.extraction.find((field) => field.name === name)

  const [requirementId, setRequirementId] = useState<number | null>(
    document.matchedRequirementId ?? document.requirementHintId ?? null,
  )
  const [status, setStatus] = useState('held_expiry')
  const [expiry, setExpiry] = useState(extracted('expiryDate')?.value ?? '')
  const [issueDate, setIssueDate] = useState(extracted('issueDate')?.value ?? '')
  const [note, setNote] = useState('')
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')

  const decided = document.verificationStatus === 'verified' || document.verificationStatus === 'rejected'
  const expiryRequired = status === 'held_expiry'

  return (
    <section className="section review">
      <div className="review__document">
        <h2 className="section__title">The document</h2>
        {document.hasContent ? (
          <DocumentPreview document={document} />
        ) : (
          <p className="empty">
            No bytes are stored for this submission
            {document.uploadComplete ? '.' : ' — the upload has not finished (MOB-5a).'}
          </p>
        )}
        <dl className="facts">
          <div>
            <dt>Submitted by</dt>
            <dd>{document.submittedBy}</dd>
          </div>
          <div>
            <dt>Source</dt>
            <dd>{document.source}</dd>
          </div>
          <div>
            <dt>Extraction</dt>
            <dd>{document.extractionModel ?? 'not extracted yet'}</dd>
          </div>
          {document.byteSize !== null && (
            <div>
              <dt>Size</dt>
              <dd>{Math.round(document.byteSize / 1024)} KB</dd>
            </div>
          )}
        </dl>

        {document.verificationStatus === 'pending_extraction' && (
          <button
            type="button"
            className="button button--quiet"
            disabled={extract.isPending || !document.uploadComplete}
            onClick={() => extract.mutate(document.publicId)}
            title="Runs §8 stages 2–4 now rather than waiting for the periodic sweep"
          >
            {extract.isPending ? 'Extracting…' : 'Extract now'}
          </button>
        )}
        {extract.error !== null && <p className="editor__error">{errorText(extract.error)}</p>}
      </div>

      <div className="review__fields">
        <h2 className="section__title">What was read</h2>
        {document.extractionModel === null && (
          <p className="note">
            Nothing has been extracted. With no LLM provider configured (§14.5) that is the expected
            state — the fields below are yours to fill from the document.
          </p>
        )}
        <ul className="extraction">
          {document.extraction.map((field) => (
            <li key={field.name} className="extraction__field">
              <span className="extraction__name">{fieldLabel(field.name)}</span>
              <span className="extraction__value">
                {field.value ?? <span className="muted">not read</span>}
              </span>
              <span className={`chip chip--${confidenceTone(field.confidence)}`}>
                {field.confidence <= 0 ? 'no reading' : `${Math.round(field.confidence * 100)}%`}
              </span>
            </li>
          ))}
        </ul>

        {document.reviewReason !== null && <p className="note">{document.reviewReason}</p>}
        {document.rejectionReason !== null && (
          <p className="editor__error">Rejected: {document.rejectionReason}</p>
        )}

        <h2 className="section__title">What the record will say</h2>

        {decided && (
          <p className="note">
            This document is {verificationStatus(document.verificationStatus).label.toLowerCase()} and
            cannot be decided again. A holding recorded in error is corrected on the person's
            holdings, where the correction is audited as one.
          </p>
        )}

        {!decided && !canDecide && (
          <p className="note">
            Accepting or rejecting is the Data Steward's decision (§8 stage 5). You can see the queue
            and the backlog, which is what this view is for.
          </p>
        )}

        {!decided && canDecide && !rejecting && (
          <form
            className="editor"
            onSubmit={(event) => {
              event.preventDefault()
              if (requirementId === null) return
              accept.mutate({
                publicId: document.publicId,
                body: {
                  requirementId,
                  status,
                  expiry: expiryRequired && expiry !== '' ? expiry : null,
                  issueDate: issueDate === '' ? null : issueDate,
                  note: note === '' ? null : note,
                },
              })
            }}
          >
            <label className="field field--inline field--grow">
              <span className="field__label">Requirement</span>
              <select
                className="input"
                value={requirementId ?? ''}
                onChange={(event) =>
                  setRequirementId(event.target.value === '' ? null : Number(event.target.value))
                }
              >
                <option value="">Select…</option>
                {(requirements.data ?? [])
                  .filter((requirement) => requirement.status === 'active')
                  .map((requirement) => (
                    <option key={requirement.id} value={requirement.id}>
                      {requirement.code} — {requirement.title}
                    </option>
                  ))}
              </select>
            </label>

            <label className="field field--inline">
              <span className="field__label">Holding status</span>
              <select
                className="input"
                value={status}
                onChange={(event) => setStatus(event.target.value)}
              >
                {HOLDING_STATUS_VALUES.map((value) => (
                  <option key={value} value={value}>
                    {holdingStatus(value).label}
                  </option>
                ))}
              </select>
            </label>

            <label className="field field--inline">
              <span className="field__label">Expiry{expiryRequired ? '' : ' (n/a)'}</span>
              <input
                className="input"
                type="date"
                value={expiry}
                disabled={!expiryRequired}
                onChange={(event) => setExpiry(event.target.value)}
              />
            </label>

            <label className="field field--inline">
              <span className="field__label">Issue date</span>
              <input
                className="input"
                type="date"
                value={issueDate}
                onChange={(event) => setIssueDate(event.target.value)}
              />
            </label>

            <label className="field field--inline field--grow">
              <span className="field__label">Note</span>
              <input
                className="input"
                value={note}
                placeholder="Optional — recorded on the holding"
                onChange={(event) => setNote(event.target.value)}
              />
            </label>

            {isCorrection(document, requirementId, expiry) && (
              <p className="note">
                This differs from what was extracted. That is fine and expected — the audit event
                records both, which is how extraction accuracy gets measured (LLM-2).
              </p>
            )}

            {accept.error !== null && <p className="editor__error">{errorText(accept.error)}</p>}

            <div className="editor__actions">
              <button
                type="submit"
                className="button button--primary"
                disabled={
                  accept.isPending || requirementId === null || (expiryRequired && expiry === '')
                }
              >
                {accept.isPending ? 'Saving…' : 'Accept and update the holding'}
              </button>
              <button
                type="button"
                className="button button--quiet"
                onClick={() => setRejecting(true)}
              >
                Reject…
              </button>
            </div>
          </form>
        )}

        {!decided && canDecide && rejecting && (
          <form
            className="editor"
            onSubmit={(event) => {
              event.preventDefault()
              reject.mutate({ publicId: document.publicId, reason })
            }}
          >
            <label className="field field--inline field--grow">
              <span className="field__label">Reason</span>
              <input
                className="input"
                value={reason}
                autoFocus
                placeholder="Shown to the person who submitted it"
                onChange={(event) => setReason(event.target.value)}
              />
            </label>
            <p className="panel__hint">
              The reason reaches the submitter. Without one they cannot tell whether to re-photograph
              the same certificate or find a different one.
            </p>
            {reject.error !== null && <p className="editor__error">{errorText(reject.error)}</p>}
            <div className="editor__actions">
              <button
                type="submit"
                className="button button--primary"
                disabled={reject.isPending || reason.trim() === ''}
              >
                {reject.isPending ? 'Rejecting…' : 'Reject'}
              </button>
              <button
                type="button"
                className="button button--quiet"
                onClick={() => setRejecting(false)}
              >
                Cancel
              </button>
            </div>
          </form>
        )}

        {document.linkedHoldingId !== null && (
          <p className="note">
            <Link to={`/people/${document.personId}`}>
              The holding this evidences
            </Link>{' '}
            was updated from this document.
          </p>
        )}
      </div>
    </section>
  )
}

/**
 * The document itself.
 *
 * An image goes in an `<img>`; a PDF goes in a **sandboxed** iframe. The sandbox is the point: a PDF
 * is an active format, and the one place an untrusted document is rendered is the one place worth
 * being careful. The bytes are served `inline` with `nosniff` and only from the ingest allow-list of
 * four image types and PDF, so nothing else can reach here — and a signed platform URL replaces the
 * whole arrangement in the spike (SEC-7).
 */
function DocumentPreview({ document }: { document: EvidenceDocument }): React.ReactNode {
  const url = api.evidenceContentUrl(document.publicId)
  const isImage = (document.contentType ?? '').startsWith('image/')

  if (isImage) {
    return (
      <img
        className="review__image"
        src={url}
        alt={`Evidence submitted by ${document.personName}`}
      />
    )
  }
  return (
    <>
      <iframe className="review__frame" src={url} title="Evidence document" sandbox="" />
      <p className="panel__hint">
        <a href={url} target="_blank" rel="noreferrer noopener">
          Open in a new tab
        </a>
      </p>
    </>
  )
}

/** Whether what the reviewer is about to accept differs from what the model read. */
export function isCorrection(
  document: EvidenceDocument,
  requirementId: number | null,
  expiry: string,
): boolean {
  const extractedExpiry = document.extraction.find((field) => field.name === 'expiryDate')?.value
  if (document.matchedRequirementId !== null && requirementId !== document.matchedRequirementId) {
    return true
  }
  return extractedExpiry !== null && extractedExpiry !== undefined && extractedExpiry !== expiry
}

/** Schema field names are camelCase on the wire; these are what a person reads. */
export function fieldLabel(name: string): string {
  const labels: Record<string, string> = {
    documentType: 'Document type',
    holderName: 'Holder name',
    identifyingNumber: 'Certificate number',
    issuingAuthority: 'Issuing authority',
    issueDate: 'Issue date',
    expiryDate: 'Expiry date',
    qualificationTitle: 'Qualification',
  }
  return labels[name] ?? name
}

function formatMoment(iso: string): string {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(
    new Date(iso),
  )
}

function errorText(error: unknown): string {
  return error instanceof ApiError ? error.message : String(error)
}
