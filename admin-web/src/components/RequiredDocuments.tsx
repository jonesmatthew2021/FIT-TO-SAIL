import { useRef, useState } from 'react'
import { useFileShipDocument, useShipDocuments, useWithdrawShipDocument } from '../api/queries'
import { ApiError, api, type Partnership, type ShipDocument } from '../api/client'
import { useHasRole } from '../api/session'
import { ErrorPanel } from './ErrorPanel'
import { Spinner } from './Spinner'
import { UploadCertificates } from './CertificatesOnFile'
import { formatDate } from '../domain/dates'

/** The roles the server accepts for filing a ship's document — mirrored to hide the controls. */
const FILERS = ['compliance_lead', 'data_steward', 'system_administrator'] as const

/**
 * The cards, as the portal names them — its MATRIX_CARDS plus the sheets that used to sit on
 * other screens. Each ship keeps one of each, the latest; the checkers on the other tabs read
 * from what is filed here, so anything missing here is missing from all of them.
 */
export const DOCUMENT_CARDS = [
  {
    category: 'training-matrix',
    title: 'Training matrix',
    noun: 'training matrix',
    blurb: "Where the crew's training stands at the moment — what each person holds and when it runs out.",
    required: true,
  },
  {
    category: 'skills-matrix',
    title: 'Skills matrix',
    noun: 'skills matrix',
    blurb: 'What the crew are required to hold: the items, who each one applies to, the shift allocations, and anything else to abide by.',
    required: true,
  },
  {
    category: 'validity-matrix',
    title: 'Validity periods matrix',
    noun: 'validity periods matrix',
    blurb: 'The other half of the skills matrix: how long each item stays valid once it has been done, and when it has to be done again.',
    required: false,
  },
  {
    category: 'shift-allocation',
    title: 'Shift allocation',
    noun: 'shift allocation sheet',
    blurb: "The office's guideline: how many holders of certain certificates each shift must carry. Read on the Swing compliance tab.",
    required: false,
  },
  {
    category: 'opms-sheet',
    title: 'OPMS export',
    noun: 'OPMS export',
    blurb: 'The latest completion export from OPMS — what its e-learning and induction records say each person has done.',
    required: false,
  },
  {
    category: 'certificate-sheet',
    title: 'OPMS spreadsheet',
    noun: 'crew certificates spreadsheet',
    blurb: 'Populated weekly from Portways: the crew records held by OPMS. Call PK if you need it.',
    required: true,
  },
] as const

export type DocumentCategory = (typeof DOCUMENT_CARDS)[number]['category']

export function RequiredDocuments({ ship }: { ship: Partnership }): React.ReactNode {
  const documents = useShipDocuments(ship.abbrev)
  const canFile = useHasRole(...FILERS)

  if (documents.isPending) return <Spinner label="Reading what is on file" />
  if (documents.error !== null) return <ErrorPanel title="Could not read the ship's documents" error={documents.error} />

  const byCategory = new Map(documents.data.map((d) => [d.category, d]))
  const missing = DOCUMENT_CARDS.filter((c) => c.required && !byCategory.has(c.category))

  return (
    <div className="swing-page">
      <section className={`section fold fold--${missing.length > 0 ? 'critical' : 'good'}`}>
        <div className="section__header">
          <div>
            <h2 className="section__title">Required documents for upload</h2>
            <p className="section__note">
              The spreadsheets and the crew's certificates. The ship keeps one of each spreadsheet — the
              latest — and every checker on these tabs reads from what is filed here, so anything missing
              is missing from all of them.
              {missing.length > 0 && ` Still to come: ${missing.map((m) => `the ${m.noun}`).join(', ')}.`}
            </p>
          </div>
          <span className={`chip chip--${missing.length > 0 ? 'critical' : 'good'}`}>
            {missing.length > 0 ? `${missing.length} of ${DOCUMENT_CARDS.length} not on file` : `all ${DOCUMENT_CARDS.length} on file`}
          </span>
        </div>
      </section>

      <div className="doc-cards">
        {DOCUMENT_CARDS.map((card) => (
          <DocumentCard key={card.category} ship={ship} card={card} record={byCategory.get(card.category)} canFile={canFile} />
        ))}
      </div>

      <section className="section">
        <div className="section__header">
          <div>
            <h2 className="section__title">Crew certificates</h2>
            <p className="section__note">
              The certificates themselves, one per person and code. Unlike the spreadsheets above, the ship
              holds as many of these as you give it — the model reads each one and you confirm who and what.
            </p>
          </div>
          {canFile && <UploadCertificates />}
        </div>
      </section>
    </div>
  )
}

function DocumentCard({
  ship,
  card,
  record,
  canFile,
}: {
  ship: Partnership
  card: (typeof DOCUMENT_CARDS)[number]
  record: ShipDocument | undefined
  canFile: boolean
}): React.ReactNode {
  const file = useFileShipDocument()
  const withdraw = useWithdrawShipDocument()
  const input = useRef<HTMLInputElement>(null)
  const [withdrawing, setWithdrawing] = useState(false)
  const [reason, setReason] = useState('')
  const tone = record !== undefined ? 'good' : card.required ? 'critical' : 'warning'

  return (
    <div className={`doc-card doc-card--${tone}`}>
      <div className="doc-card__head">
        <span className="doc-card__title">{card.title}</span>
        <span className={`chip chip--${tone} chip--small`}>
          {record !== undefined ? 'on file' : card.required ? 'required — not on file' : 'not on file'}
        </span>
      </div>
      <p className="doc-card__blurb">{card.blurb}</p>
      <p className={record !== undefined ? 'doc-card__file' : `doc-card__file editor__error`}>
        {record !== undefined
          ? record.fileName
          : card.required
            ? `No ${card.noun} has been filed. The ship is required to hold one at all times.`
            : `No ${card.noun} has been filed.`}
      </p>
      <p className="meta">
        Last updated: {record !== undefined ? formatDate(record.filedAt.slice(0, 10)) : '—'}
        {record !== undefined && ` · ${formatSize(record.byteSize)} · ${record.filedBy}`}
      </p>
      <div className="row-actions">
        {record !== undefined && (
          <a className="button button--quiet" href={api.shipDocumentUrl(record.id)}>
            Open
          </a>
        )}
        {canFile && (
          <>
            <input
              ref={input}
              type="file"
              hidden
              accept=".xlsx,.xls,.csv,.pdf,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
              onChange={(event) => {
                const picked = event.target.files?.[0]
                event.target.value = ''
                if (picked !== undefined) file.mutate({ partnership: ship.abbrev, category: card.category, file: picked })
              }}
            />
            <button
              type="button"
              className={record !== undefined ? 'button button--quiet' : 'button button--primary'}
              disabled={file.isPending}
              onClick={() => input.current?.click()}
            >
              {file.isPending ? 'Filing…' : record !== undefined ? `Replace the ${card.noun}` : `Upload the ${card.noun}`}
            </button>
            {record !== undefined && !withdrawing && (
              <button type="button" className="button button--quiet" onClick={() => setWithdrawing(true)}>
                Remove
              </button>
            )}
          </>
        )}
      </div>
      {withdrawing && record !== undefined && (
        <form
          className="cert-row__confirm"
          onSubmit={(event) => {
            event.preventDefault()
            withdraw.mutate({ documentId: record.id, reason }, { onSuccess: () => setWithdrawing(false) })
          }}
        >
          <input className="input" placeholder="Why" value={reason} onChange={(event) => setReason(event.target.value)} />
          <button type="submit" className="button" disabled={withdraw.isPending || reason.trim() === ''}>
            Remove
          </button>
          <button type="button" className="button button--quiet" onClick={() => setWithdrawing(false)}>
            Keep
          </button>
        </form>
      )}
      {file.error !== null && <p className="editor__error">{errorText(file.error)}</p>}
      {withdraw.error !== null && <p className="editor__error">{errorText(withdraw.error)}</p>}
    </div>
  )
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function errorText(error: unknown): string {
  return error instanceof ApiError ? error.message : String(error)
}
