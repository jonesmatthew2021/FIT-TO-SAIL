import { Fragment, useEffect, useRef, useState } from 'react'
import {
  useAcceptEvidence,
  useAmendEvidence,
  useCreatePerson,
  useHoldings,
  useOfficeFile,
  usePartnerships,
  usePeople,
  usePersonEvidence,
  usePositions,
  useRemoveEvidence,
  useRequirements,
} from '../api/queries'
import {
  ApiError,
  api,
  type EvidenceDocument,
  type Holding,
  type IntakeReading,
  type Person,
  type Requirement,
} from '../api/client'
import { useHasRole, useToday } from '../api/session'
import { Copy } from './Copy'
import { ErrorPanel } from './ErrorPanel'
import { Modal } from './Modal'
import { Spinner } from './Spinner'
import { daysBetween, formatDate } from '../domain/dates'
import { asPdf } from '../domain/pdf'
import {
  HOLDING_STATUS_VALUES,
  confidenceTone,
  expiryWindow,
  expiryWindowTone,
  holdingStatus,
} from '../domain/enums'

/** Roles that may file, amend and remove — the same two that decide on ADM-9. */
const FILERS = ['data_steward', 'system_administrator'] as const

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/

/**
 * Certificates on file — ADM-5's evidence half, a port of the Coolibah portal's page of the same
 * name onto each crew member's own record.
 *
 * Under every code the scans that evidence it, with their issue date, expiry and the code's
 * validity period — plus "No scan on file · matrix holds …" where the holding has a date and
 * nothing on file backs it. Lives on the person's page rather than as a screen of its own because
 * that is where the portal's users look for it: the holding and the scan behind it, together.
 *
 * The upload is the §8 pipeline with a human in the loop *before* ingest rather than after: the
 * model reads the file and suggests, the uploader confirms the person and the code, and the
 * document is created and accepted in one step (see `OfficeEvidenceService`). The person is always
 * an explicit choice — a name on a certificate is a suggestion, never a match — and when nobody on
 * the roster fits, the same dialog adds the crew member, because that is the moment the office
 * knows they exist.
 */
export function PersonCertificates({ person }: { person: Person }): React.ReactNode {
  const requirements = useRequirements()
  const holdings = useHoldings(person.id)
  const documents = usePersonEvidence(person.id)
  const canFile = useHasRole(...FILERS)
  const today = useToday()
  const [showRemoved, setShowRemoved] = useState(false)

  if (requirements.isPending || holdings.isPending || documents.isPending) {
    return <Spinner label="Loading the certificates on file" />
  }
  if (requirements.error !== null) {
    return <ErrorPanel title="Could not load the catalogue" error={requirements.error} />
  }
  if (holdings.error !== null) return <ErrorPanel title="Could not load holdings" error={holdings.error} />
  if (documents.error !== null) {
    return <ErrorPanel title="Could not load the certificates" error={documents.error} />
  }

  const requirementById = new Map(requirements.data.map((requirement) => [requirement.id, requirement]))
  const visible = documents.data.filter((d) => showRemoved || d.verificationStatus !== 'rejected')

  // Every code that has either a held holding or a document, in code order; then the documents
  // that resolved to no code at all, which is where an unfiled import or a mis-read lands.
  const codes = new Map<number, { requirement: Requirement; holding: Holding | undefined; documents: EvidenceDocument[] }>()
  for (const holding of holdings.data) {
    if (holding.status !== 'held_expiry' && holding.status !== 'held_perpetual') continue
    const requirement = requirementById.get(holding.requirementId)
    if (requirement !== undefined) codes.set(requirement.id, { requirement, holding, documents: [] })
  }
  const unfiled: EvidenceDocument[] = []
  for (const document of visible) {
    const requirement = document.matchedRequirementId === null ? undefined : requirementById.get(document.matchedRequirementId)
    if (requirement === undefined) {
      unfiled.push(document)
      continue
    }
    const entry = codes.get(requirement.id) ?? {
      requirement,
      holding: holdings.data.find((holding) => holding.requirementId === requirement.id),
      documents: [],
    }
    entry.documents.push(document)
    codes.set(requirement.id, entry)
  }
  const ordered = [...codes.values()].sort((a, b) => a.requirement.code.localeCompare(b.requirement.code))
  const fileCount = documents.data.filter((d) => d.verificationStatus !== 'rejected').length
  const awaiting = documents.data.filter(
    (d) => d.verificationStatus === 'pending_review' || d.verificationStatus === 'pending_extraction',
  ).length

  return (
    <section className="section">
      <div className="section__header">
        <div>
          <h2 className="section__title">Certificates on file</h2>
          <p className="section__note">
            {fileCount} {fileCount === 1 ? 'scan' : 'scans'}
            {awaiting > 0 && ` · ${awaiting} awaiting a code`} · the model reads an upload, you confirm
            who and what before it is filed
          </p>
        </div>
        <div className="row-actions">
          <label className="check check--box">
            <input type="checkbox" checked={showRemoved} onChange={(event) => setShowRemoved(event.target.checked)} />
            <span className="dot" />
            Show removed
          </label>
          {canFile && <UploadCertificates defaultPerson={person} />}
        </div>
      </div>

      <div className="table-block">
        <div className="cert-head">
          <span />
          <span>Issue date</span>
          <span>Expiry date</span>
          <span>Validity period</span>
          <span />
        </div>

        {ordered.length === 0 && unfiled.length === 0 && (
          <p className="empty">Nothing held and nothing on file.</p>
        )}

        {ordered.map(({ requirement, holding, documents: docs }) => (
          <Fragment key={requirement.id}>
            <div className="cert-code">
              <span>
                <span className="mono">{requirement.code}</span>
                <strong>{requirement.title}</strong>
              </span>
              <span />
              <span />
              <span />
              <span>
                {docs.length === 0 && holding !== undefined && (
                  <span className="chip chip--muted chip--small">
                    No scan on file · matrix holds{' '}
                    {holding.status === 'held_perpetual' ? 'never expires' : formatDate(holding.expiry)}
                  </span>
                )}
              </span>
            </div>
            {docs.map((document) => (
              <DocumentRow
                key={document.publicId}
                document={document}
                holding={holding}
                requirement={requirement}
                requirements={requirements.data}
                today={today}
                canFile={canFile}
              />
            ))}
          </Fragment>
        ))}

        {unfiled.length > 0 && (
          <>
            <div className="cert-code">
              <span>
                <span className="mono">—</span>
                <strong>No code yet</strong>
              </span>
              <span />
              <span />
              <span />
              <span className="dim">file each one to a code</span>
            </div>
            {unfiled.map((document) => (
              <DocumentRow
                key={document.publicId}
                document={document}
                holding={undefined}
                requirement={undefined}
                requirements={requirements.data}
                today={today}
                canFile={canFile}
              />
            ))}
          </>
        )}
      </div>
    </section>
  )
}

function DocumentRow({
  document,
  holding,
  requirement,
  requirements,
  today,
  canFile,
}: {
  document: EvidenceDocument
  holding: Holding | undefined
  requirement: Requirement | undefined
  requirements: readonly Requirement[]
  today: string
  canFile: boolean
}): React.ReactNode {
  const [editing, setEditing] = useState(false)
  const [removing, setRemoving] = useState(false)
  const [reason, setReason] = useState('')
  const remove = useRemoveEvidence()

  const removed = document.verificationStatus === 'rejected'
  const filed = document.verificationStatus === 'verified' || document.verificationStatus === 'auto_accepted'
  // The dates a filed document evidences are the holding's; an unfiled one shows what was read.
  const issue = filed ? holding?.issueDate ?? null : readField(document, 'issueDate')
  const expiry = filed ? holding?.expiry ?? null : readField(document, 'expiryDate')

  return (
    <div className={removed ? 'cert-row cert-row--removed' : 'cert-row'}>
      <span className="cert-row__file">
        <a href={api.evidenceContentUrl(document.publicId)} target="_blank" rel="noreferrer">
          {document.fileName ?? `evidence-${document.publicId}`}
        </a>
        <span className="meta">
          {requirement?.code ?? '—'} · {formatSize(document.byteSize)} · filed {formatDate(document.submittedAt.slice(0, 10))} ·{' '}
          {document.submittedBy}
          {!filed && !removed && (
            <>
              {' '}
              · <span className="chip chip--caution chip--small">awaiting a code</span>
            </>
          )}
          {removed && (
            <>
              {' '}
              · <span className="chip chip--muted chip--small">removed</span> {document.rejectionReason}
            </>
          )}
        </span>
      </span>
      <span>{issue === null ? <span className="dim">—</span> : formatDate(issue)}</span>
      <span>
        {expiry === null ? (
          holding?.status === 'held_perpetual' ? <span className="chip chip--muted chip--small">Held</span> : <span className="dim">—</span>
        ) : (
          <span className={`chip chip--${expiryWindowTone(expiryWindow(daysBetween(today, expiry)))} chip--small`}>
            {formatDate(expiry)}
          </span>
        )}
      </span>
      <span className="muted">{validityLabel(requirement)}</span>
      <span className="cert-row__actions">
        <a className="button button--quiet" href={api.evidenceContentUrl(document.publicId)} target="_blank" rel="noreferrer">
          Open
        </a>
        {canFile && !removed && (
          <button type="button" className="button button--quiet" onClick={() => setEditing(true)}>
            {filed ? 'Edit' : 'File…'}
          </button>
        )}
        {canFile && !removed && !removing && (
          <button type="button" className="button button--quiet" onClick={() => setRemoving(true)}>
            Delete
          </button>
        )}
      </span>

      {removing && (
        <form
          className="cert-row__confirm"
          onSubmit={(event) => {
            event.preventDefault()
            remove.mutate({ publicId: document.publicId, reason }, { onSuccess: () => setRemoving(false) })
          }}
        >
          <span className="dim">Takes the scan off the file; the holding stays as it is.</span>
          <input className="input" placeholder="Why (shown in its place)" value={reason} onChange={(event) => setReason(event.target.value)} />
          <button type="submit" className="button" disabled={remove.isPending || reason.trim() === ''}>
            Remove
          </button>
          <button type="button" className="button button--quiet" onClick={() => setRemoving(false)}>
            Keep
          </button>
          {remove.error !== null && <span className="editor__error">{errorText(remove.error)}</span>}
        </form>
      )}

      {editing && (
        <EditDialog
          document={document}
          holding={holding}
          requirements={requirements}
          initialRequirementId={requirement?.id ?? null}
          onClose={() => setEditing(false)}
        />
      )}
    </div>
  )
}

/** Correct a filed document (amend) or file an unfiled one (accept) — one form, two doors. */
function EditDialog({
  document,
  holding,
  requirements,
  initialRequirementId,
  onClose,
}: {
  document: EvidenceDocument
  holding: Holding | undefined
  requirements: readonly Requirement[]
  initialRequirementId: number | null
  onClose: () => void
}): React.ReactNode {
  const amend = useAmendEvidence()
  const accept = useAcceptEvidence()
  const filed = document.verificationStatus === 'verified' || document.verificationStatus === 'auto_accepted'
  const [requirementId, setRequirementId] = useState<number | null>(initialRequirementId)
  const [status, setStatus] = useState(holding?.status ?? 'held_expiry')
  const [expiry, setExpiry] = useState(holding?.expiry ?? readField(document, 'expiryDate') ?? '')
  const [issueDate, setIssueDate] = useState(holding?.issueDate ?? readField(document, 'issueDate') ?? '')
  const mutation = filed ? amend : accept

  return (
    <Modal
      title={filed ? 'Edit this certificate' : 'File this certificate'}
      {...(document.fileName === null ? {} : { note: document.fileName })}
      onClose={onClose}
    >
      <HoldingForm
        requirements={requirements}
        requirementId={requirementId}
        status={status}
        expiry={expiry}
        issueDate={issueDate}
        onRequirement={setRequirementId}
        onStatus={setStatus}
        onExpiry={setExpiry}
        onIssueDate={setIssueDate}
        submitLabel={filed ? 'Save' : 'File it'}
        pending={mutation.isPending}
        error={mutation.error}
        onSubmit={() => {
          if (requirementId === null) return
          mutation.mutate(
            {
              publicId: document.publicId,
              body: {
                requirementId,
                status,
                expiry: status === 'held_expiry' && expiry !== '' ? expiry : null,
                issueDate: issueDate === '' ? null : issueDate,
                note: null,
              },
            },
            { onSuccess: onClose },
          )
        }}
      />
    </Modal>
  )
}

// ---------------------------------------------------------------------------
// Upload — read, confirm, file
// ---------------------------------------------------------------------------

/**
 * The upload button and the dialog behind it. On a person's page [defaultPerson] is that person,
 * pre-selected; on the directory there is none and the model's suggestion leads. Either way the
 * person is confirmed by a human before anything is filed.
 */
export function UploadCertificates({ defaultPerson }: { defaultPerson?: Person }): React.ReactNode {
  const people = usePeople()
  const requirements = useRequirements()
  const [queue, setQueue] = useState<File[]>([])
  // Counts the picks, so each one opens a fresh dialog rather than reusing the last one's rows.
  const [batch, setBatch] = useState(0)
  const fileInput = useRef<HTMLInputElement>(null)

  return (
    <>
      <input
        ref={fileInput}
        type="file"
        multiple
        accept="application/pdf,image/*"
        hidden
        onChange={(event) => {
          const picked = Array.from(event.target.files ?? [])
          event.target.value = ''
          if (picked.length > 0) {
            setQueue(picked)
            setBatch((current) => current + 1)
          }
        }}
      />
      <button type="button" className="button button--primary" onClick={() => fileInput.current?.click()}>
        Upload certificates
      </button>
      {queue.length > 0 && people.data !== undefined && requirements.data !== undefined && (
        <BulkIntake
          key={batch}
          files={queue}
          people={people.data}
          requirements={requirements.data}
          {...(defaultPerson === undefined ? {} : { defaultPerson })}
          onClose={() => setQueue([])}
        />
      )}
    </>
  )
}

type Stage = 'converting' | 'reading' | 'ready' | 'filing' | 'filed' | 'failed'

interface Item {
  id: number
  original: File
  pdf: File | null
  stage: Stage
  error: string | null
  reading: IntakeReading | null
  personId: number | null
  adding: boolean
  requirementId: number | null
  expires: boolean
  expiry: string
  issueDate: string
  filedAs: string | null
}

/**
 * Bulk intake — every file picked, in one table.
 *
 * Each file is turned into a PDF in the browser if it is a picture, read by the model, and laid
 * out as a row with what was read and the model's suggestions for who it belongs to and what it
 * evidences. Nothing is filed until a person presses File on the row (or File all, for every row
 * whose suggestions they are happy with): the person is always a human's choice, never inferred
 * from a name on a certificate. On filing, the server stores the scan as
 * "SURNAME, Given - Certificate - issue date.pdf", and the row says so.
 *
 * Reads run one after another rather than all at once — each is a model call with a bill behind
 * it, and a queue of forty should not become forty simultaneous requests.
 */
function BulkIntake({
  files,
  people,
  requirements,
  defaultPerson,
  onClose,
}: {
  files: readonly File[]
  people: readonly Person[]
  requirements: readonly Requirement[]
  defaultPerson?: Person
  onClose: () => void
}): React.ReactNode {
  const fileIt = useOfficeFile()
  const [items, setItems] = useState<Item[]>(() =>
    files.map((original, id) => ({
      id,
      original,
      pdf: null,
      stage: 'converting',
      error: null,
      reading: null,
      personId: defaultPerson?.id ?? null,
      adding: false,
      requirementId: null,
      expires: true,
      expiry: '',
      issueDate: '',
      filedAs: null,
    })),
  )
  const [filingAll, setFilingAll] = useState(false)
  const started = useRef(false)

  const patch = (id: number, change: Partial<Item>) =>
    setItems((current) => current.map((item) => (item.id === id ? { ...item, ...change } : item)))

  // One pass over the queue: convert, then read, one file at a time. The ref keeps StrictMode's
  // doubled effect from reading — and paying — twice.
  useEffect(() => {
    if (started.current) return
    started.current = true
    void (async () => {
      for (const item of items) {
        let pdf: File
        try {
          pdf = await asPdf(item.original)
          patch(item.id, { pdf, stage: 'reading' })
        } catch (error) {
          patch(item.id, { stage: 'failed', error: errorText(error) })
          continue
        }
        try {
          const reading = await api.officeRead(pdf)
          const readExpiry = intakeField(reading, 'expiryDate')
          const readIssue = intakeField(reading, 'issueDate')
          const expiry = readExpiry !== null && ISO_DATE.test(readExpiry) ? readExpiry : ''
          patch(item.id, {
            reading,
            stage: 'ready',
            personId: defaultPerson?.id ?? reading.people[0]?.person.id ?? null,
            requirementId: reading.requirements[0]?.id ?? null,
            expiry,
            expires: expiry !== '' || readExpiry === null,
            issueDate: readIssue !== null && ISO_DATE.test(readIssue) ? readIssue : '',
          })
        } catch (error) {
          patch(item.id, { stage: 'failed', error: errorText(error) })
        }
      }
    })()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // What the server will accept: a person, a code, and — for a certificate that expires — the
  // date it expires, since a holding without one is a holding the engine cannot read.
  const ready = (item: Item) =>
    item.stage === 'ready' &&
    item.reading !== null &&
    item.personId !== null &&
    item.requirementId !== null &&
    !item.adding &&
    (!item.expires || item.expiry !== '')

  const fileOne = async (item: Item) => {
    if (!ready(item) || item.reading === null || item.personId === null || item.requirementId === null) return
    patch(item.id, { stage: 'filing', error: null })
    try {
      const document = await fileIt.mutateAsync({
        intakeId: item.reading.intakeId,
        body: {
          personId: item.personId,
          requirementId: item.requirementId,
          status: item.expires ? 'held_expiry' : 'held_perpetual',
          expiry: item.expires && item.expiry !== '' ? item.expiry : null,
          issueDate: item.issueDate === '' ? null : item.issueDate,
          note: null,
        },
      })
      patch(item.id, { stage: 'filed', filedAs: document.fileName })
    } catch (error) {
      patch(item.id, { stage: 'ready', error: errorText(error) })
    }
  }

  const fileAll = async () => {
    setFilingAll(true)
    for (const item of items) if (ready(item)) await fileOne(item)
    setFilingAll(false)
  }

  const counts = {
    waiting: items.filter((i) => i.stage === 'converting' || i.stage === 'reading').length,
    ready: items.filter(ready).length,
    filed: items.filter((i) => i.stage === 'filed').length,
    failed: items.filter((i) => i.stage === 'failed').length,
  }
  const personById = new Map(people.map((p) => [p.id, p]))

  return (
    <Modal
      title={files.length === 1 ? 'Filing a certificate' : `Filing ${files.length} certificates`}
      note={[
        counts.waiting > 0 ? `${counts.waiting} being read` : null,
        `${counts.ready} ready to file`,
        counts.filed > 0 ? `${counts.filed} filed` : null,
        counts.failed > 0 ? `${counts.failed} could not be read` : null,
      ]
        .filter(Boolean)
        .join(' · ')}
      wide
      onClose={onClose}
    >
      <p className="note">
        <Copy k="intake.note">
          Pictures are turned into PDFs here before they are read. The model's reading fills each row; check who it belongs to and what it evidences, then press File — or File all once every row reads right. Each scan is stored as SURNAME, Given - Certificate - issue date.pdf.
        </Copy>
      </p>

      <div className="table-scroll">
        <table className="table bulk">
          <thead>
            <tr>
              <th scope="col">File</th>
              <th scope="col">Read</th>
              <th scope="col">Crew member</th>
              <th scope="col">Certificate</th>
              <th scope="col">Issued</th>
              <th scope="col">Expiry</th>
              <th scope="col" />
            </tr>
          </thead>
          <tbody>
            {items.map((item) => (
              <Fragment key={item.id}>
                <tr className={item.stage === 'filed' ? 'bulk__row bulk__row--filed' : 'bulk__row'}>
                  <td className="bulk__file">
                    <span className="bulk__name">{item.original.name}</span>
                    <span className="meta">
                      {formatSize(item.original.size)}
                      {item.pdf !== null && item.pdf !== item.original && ' · converted to PDF'}
                    </span>
                  </td>
                  <td className="bulk__read">
                    {item.stage === 'converting' && <span className="chip chip--muted chip--small">converting…</span>}
                    {item.stage === 'reading' && <span className="chip chip--muted chip--small">reading…</span>}
                    {item.stage === 'failed' && <span className="chip chip--critical chip--small">could not read</span>}
                    {item.reading !== null && (
                      <span className="meta">
                        {(['holderName', 'documentType', 'certificateNumber'] as const).map((name) => {
                          const field = item.reading?.extraction.find((f) => f.name === name)
                          return field === undefined || field.value === null ? null : (
                            <span key={name} className="bulk__field" title={fieldLabel(name)}>
                              {field.value}{' '}
                              <span className={`chip chip--${confidenceTone(field.confidence)} chip--small`}>
                                {Math.round(field.confidence * 100)}%
                              </span>
                            </span>
                          )
                        })}
                      </span>
                    )}
                  </td>
                  <td>
                    {item.stage === 'filed' ? (
                      personById.get(item.personId ?? -1)?.name
                    ) : (
                      <select
                        className="input"
                        value={item.adding ? '__new__' : (item.personId ?? '')}
                        disabled={item.reading === null || item.stage === 'filing'}
                        onChange={(event) => {
                          if (event.target.value === '__new__') patch(item.id, { adding: true, personId: null })
                          else patch(item.id, { adding: false, personId: event.target.value === '' ? null : Number(event.target.value) })
                        }}
                      >
                        <option value="">Choose…</option>
                        {item.reading !== null && item.reading.people.length > 0 && (
                          <optgroup label="Suggested">
                            {item.reading.people.map((suggestion) => (
                              <option key={suggestion.person.id} value={suggestion.person.id}>
                                {suggestion.person.name} · {suggestion.person.positionName} — {suggestion.why}
                              </option>
                            ))}
                          </optgroup>
                        )}
                        <optgroup label="Everyone">
                          {people.map((person) => (
                            <option key={person.id} value={person.id}>
                              {person.name} · {person.positionName}
                            </option>
                          ))}
                        </optgroup>
                        <option value="__new__">＋ Add a new crew member…</option>
                      </select>
                    )}
                    {item.stage !== 'filed' && defaultPerson !== undefined && item.reading?.people[0] !== undefined && item.reading.people[0].person.id !== defaultPerson.id && (
                      <span className="editor__error bulk__warn">Reads as {item.reading.people[0].person.name} — check.</span>
                    )}
                  </td>
                  <td>
                    {item.stage === 'filed' ? (
                      requirements.find((r) => r.id === item.requirementId)?.code
                    ) : (
                      <select
                        className="input"
                        value={item.requirementId ?? ''}
                        disabled={item.reading === null || item.stage === 'filing'}
                        onChange={(event) => patch(item.id, { requirementId: event.target.value === '' ? null : Number(event.target.value) })}
                      >
                        <option value="">Choose…</option>
                        {item.reading !== null && item.reading.requirements.length > 0 && (
                          <optgroup label="Suggested">
                            {item.reading.requirements.map((requirement) => (
                              <option key={requirement.id} value={requirement.id}>
                                {requirement.code} {requirement.title}
                              </option>
                            ))}
                          </optgroup>
                        )}
                        <optgroup label="Every code">
                          {requirements.map((requirement) => (
                            <option key={requirement.id} value={requirement.id}>
                              {requirement.code} {requirement.title}
                            </option>
                          ))}
                        </optgroup>
                      </select>
                    )}
                  </td>
                  <td>
                    {item.stage === 'filed' ? (
                      formatDate(item.issueDate === '' ? null : item.issueDate)
                    ) : (
                      <input className="input input--tight" type="date" value={item.issueDate} disabled={item.reading === null || item.stage === 'filing'} onChange={(event) => patch(item.id, { issueDate: event.target.value })} />
                    )}
                  </td>
                  <td>
                    {item.stage === 'filed' ? (
                      item.expires ? formatDate(item.expiry === '' ? null : item.expiry) : 'never expires'
                    ) : (
                      <span className="row-actions">
                        <select className="input input--tight" value={item.expires ? 'expires' : 'never'} disabled={item.reading === null || item.stage === 'filing'} onChange={(event) => patch(item.id, { expires: event.target.value === 'expires' })}>
                          <option value="expires">Expires</option>
                          <option value="never">Never expires</option>
                        </select>
                        {item.expires && (
                          <input className="input input--tight" type="date" value={item.expiry} disabled={item.reading === null || item.stage === 'filing'} onChange={(event) => patch(item.id, { expiry: event.target.value })} />
                        )}
                      </span>
                    )}
                  </td>
                  <td className="bulk__actions">
                    {item.stage === 'filed' && <span className="chip chip--good chip--small">filed</span>}
                    {item.stage === 'filing' && <span className="chip chip--muted chip--small">filing…</span>}
                    {(item.stage === 'ready' || item.stage === 'reading' || item.stage === 'converting') && (
                      <button type="button" className="button button--primary button--quiet" disabled={!ready(item) || filingAll} onClick={() => void fileOne(item)}>
                        File
                      </button>
                    )}
                    {item.stage !== 'filed' && item.stage !== 'filing' && (
                      <button type="button" className="button button--quiet" onClick={() => setItems((current) => current.filter((i) => i.id !== item.id))}>
                        Skip
                      </button>
                    )}
                  </td>
                </tr>
                {(item.error !== null || item.filedAs !== null || item.adding) && (
                  <tr className="bulk__detail">
                    <td colSpan={7}>
                      {item.error !== null && <span className="editor__error">{item.error}</span>}
                      {item.filedAs !== null && (
                        <span className="muted">
                          Stored as <span className="mono">{item.filedAs}</span>
                        </span>
                      )}
                      {item.adding && item.reading !== null && (
                        <NewPersonForm
                          suggestedName={intakeField(item.reading, 'holderName')}
                          {...(defaultPerson === undefined ? {} : { shipId: defaultPerson.partnershipId })}
                          onCreated={(person) => patch(item.id, { personId: person.id, adding: false })}
                          onCancel={() => patch(item.id, { adding: false })}
                        />
                      )}
                    </td>
                  </tr>
                )}
              </Fragment>
            ))}
            {items.length === 0 && (
              <tr>
                <td colSpan={7} className="empty">
                  Nothing left to file.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="editor__actions">
        <button type="button" className="button button--primary" disabled={counts.ready === 0 || filingAll} onClick={() => void fileAll()}>
          {filingAll ? 'Filing…' : counts.ready === 1 ? 'File the ready one' : `File all ${counts.ready} ready`}
        </button>
        <button type="button" className="button" onClick={onClose}>
          {counts.filed === items.length && items.length > 0 ? 'Done' : 'Close'}
        </button>
      </div>
    </Modal>
  )
}

/**
 * The pop-up's other half: a crew member the roster does not carry yet. Also the swing roster's
 * "Add crew member" — there the ship is fixed (`shipId`) and the note says where they are going.
 */
export function NewPersonForm({
  suggestedName,
  shipId,
  note,
  onCreated,
  onCancel,
}: {
  suggestedName: string | null
  shipId?: number
  note?: string
  onCreated: (person: Person) => void
  onCancel: () => void
}): React.ReactNode {
  const positions = usePositions()
  const partnerships = usePartnerships()
  const create = useCreatePerson()
  const [name, setName] = useState(rosterForm(suggestedName))
  const [sam, setSam] = useState('')
  const [positionId, setPositionId] = useState<number | null>(null)
  const [partnershipId, setPartnershipId] = useState<number | null>(shipId ?? null)
  const [rotation, setRotation] = useState<string>('')

  const partnership = partnershipId ?? partnerships.data?.[0]?.id ?? null

  return (
    <form
      className="editor"
      onSubmit={(event) => {
        event.preventDefault()
        if (positionId === null || partnership === null) return
        create.mutate(
          { name, sam, positionId, partnershipId: partnership, email: null, rotation: rotation === '' ? null : rotation },
          { onSuccess: onCreated },
        )
      }}
    >
      <p className="note">
        {note ?? 'This crew member is not on the roster. Adding them here also adds them to Crew and certification.'}
      </p>
      <label className="field field--inline field--grow">
        <span className="field__label">Name (SURNAME, Given)</span>
        <input className="input" value={name} onChange={(event) => setName(event.target.value)} placeholder="EVANS, Brenton" />
      </label>
      <label className="field field--inline">
        <span className="field__label">Sam #</span>
        <input className="input input--level" value={sam} onChange={(event) => setSam(event.target.value)} placeholder="e91754E" />
      </label>
      <label className="field field--inline">
        <span className="field__label">Position</span>
        <select className="input" value={positionId ?? ''} onChange={(event) => setPositionId(event.target.value === '' ? null : Number(event.target.value))}>
          <option value="">Choose…</option>
          {(positions.data ?? []).map((position) => (
            <option key={position.id} value={position.id}>
              {position.name}
            </option>
          ))}
        </select>
      </label>
      {shipId === undefined && (
        <label className="field field--inline">
          <span className="field__label">Partnership</span>
          <select className="input" value={partnership ?? ''} onChange={(event) => setPartnershipId(Number(event.target.value))}>
            {(partnerships.data ?? []).map((option) => (
              <option key={option.id} value={option.id}>
                {option.abbrev} — {option.name}
              </option>
            ))}
          </select>
        </label>
      )}
      <label className="field field--inline">
        <span className="field__label">Crew</span>
        <select className="input" value={rotation} onChange={(event) => setRotation(event.target.value)}>
          <option value="">Not on a rotation</option>
          <option value="A">A</option>
          <option value="B">B</option>
        </select>
      </label>
      <div className="editor__actions">
        <button
          type="submit"
          className="button button--primary"
          disabled={create.isPending || name.trim() === '' || sam.trim() === '' || positionId === null || partnership === null}
        >
          {create.isPending ? 'Adding…' : 'Add crew member'}
        </button>
        <button type="button" className="button" onClick={onCancel}>
          Cancel
        </button>
      </div>
      {create.error !== null && <p className="editor__error">{errorText(create.error)}</p>}
    </form>
  )
}

/** Requirement, status, dates — the same four fields ADM-9's accept form carries. */
function HoldingForm({
  requirements,
  suggested = [],
  requirementId,
  status,
  expiry,
  issueDate,
  onRequirement,
  onStatus,
  onExpiry,
  onIssueDate,
  submitLabel,
  pending,
  error,
  disabled = false,
  secondary,
  onSubmit,
}: {
  requirements: readonly Requirement[]
  suggested?: readonly Requirement[]
  requirementId: number | null
  status: string
  expiry: string
  issueDate: string
  onRequirement: (id: number | null) => void
  onStatus: (status: string) => void
  onExpiry: (value: string) => void
  onIssueDate: (value: string) => void
  submitLabel: string
  pending: boolean
  error: unknown
  disabled?: boolean
  secondary?: { label: string; onClick: () => void }
  onSubmit: () => void
}): React.ReactNode {
  const expiryRequired = status === 'held_expiry'
  const suggestedIds = new Set(suggested.map((requirement) => requirement.id))
  return (
    <form
      className="editor"
      onSubmit={(event) => {
        event.preventDefault()
        onSubmit()
      }}
    >
      <label className="field field--inline field--grow">
        <span className="field__label">Requirement</span>
        <select className="input" value={requirementId ?? ''} onChange={(event) => onRequirement(event.target.value === '' ? null : Number(event.target.value))}>
          <option value="">Choose…</option>
          {suggested.length > 0 && (
            <optgroup label="Suggested">
              {suggested.map((requirement) => (
                <option key={requirement.id} value={requirement.id}>
                  {requirement.code} — {requirement.title}
                </option>
              ))}
            </optgroup>
          )}
          <optgroup label="Catalogue">
            {requirements
              .filter((requirement) => requirement.status === 'active' && !suggestedIds.has(requirement.id))
              .map((requirement) => (
                <option key={requirement.id} value={requirement.id}>
                  {requirement.code} — {requirement.title}
                </option>
              ))}
          </optgroup>
        </select>
      </label>
      <label className="field field--inline">
        <span className="field__label">Holding status</span>
        <select className="input" value={status} onChange={(event) => onStatus(event.target.value)}>
          {HOLDING_STATUS_VALUES.map((value) => (
            <option key={value} value={value}>
              {holdingStatus(value).label}
            </option>
          ))}
        </select>
      </label>
      <label className="field field--inline">
        <span className="field__label">Expiry{expiryRequired ? '' : ' (n/a)'}</span>
        <input className="input" type="date" value={expiry} disabled={!expiryRequired} onChange={(event) => onExpiry(event.target.value)} />
      </label>
      <label className="field field--inline">
        <span className="field__label">Issue date</span>
        <input className="input" type="date" value={issueDate} onChange={(event) => onIssueDate(event.target.value)} />
      </label>
      <div className="editor__actions">
        <button
          type="submit"
          className="button button--primary"
          disabled={pending || disabled || requirementId === null || (expiryRequired && expiry === '')}
        >
          {pending ? 'Saving…' : submitLabel}
        </button>
        {secondary !== undefined && (
          <button type="button" className="button" onClick={secondary.onClick}>
            {secondary.label}
          </button>
        )}
      </div>
      {error !== null && <p className="editor__error">{errorText(error)}</p>}
    </form>
  )
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function readField(document: EvidenceDocument, name: string): string | null {
  const value = document.extraction.find((field) => field.name === name)?.value ?? null
  return value !== null && ISO_DATE.test(value) ? value : null
}

function intakeField(reading: IntakeReading, name: string): string | null {
  return reading.extraction.find((field) => field.name === name)?.value ?? null
}

const FIELD_LABELS: Record<string, string> = {
  documentType: 'Document type',
  holderName: 'Holder name',
  identifyingNumber: 'Number',
  issuingAuthority: 'Issued by',
  issueDate: 'Issue date',
  expiryDate: 'Expiry date',
  qualificationTitle: 'Qualification',
}

function fieldLabel(name: string): string {
  return FIELD_LABELS[name] ?? name
}

/** "Brenton Evans" → "EVANS, Brenton", the roster's form; already-roster-form input passes through. */
function rosterForm(name: string | null): string {
  if (name === null || name.trim() === '') return ''
  if (name.includes(',')) return name.trim()
  const parts = name.trim().split(/\s+/)
  const surname = parts.pop() ?? ''
  return `${surname.toUpperCase()}, ${parts.join(' ')}`.trim()
}

/** "5 years", "18 months", or the catalogue's own words ("1 or 2 years — as printed on the certificate"). */
export function validityLabel(requirement: Requirement | undefined): string {
  if (requirement === undefined) return '—'
  if (requirement.validityText !== null) return requirement.validityText
  if (requirement.validityMonths !== null) {
    const months = requirement.validityMonths
    return months % 12 === 0 ? `${months / 12} ${months === 12 ? 'year' : 'years'}` : `${months} months`
  }
  return '—'
}

function formatSize(bytes: number | null): string {
  if (bytes === null) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function errorText(error: unknown): string {
  return error instanceof ApiError ? error.message : String(error)
}
