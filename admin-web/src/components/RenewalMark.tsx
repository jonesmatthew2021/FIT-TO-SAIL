import { useState } from 'react'
import { useClearRenewal, useSetRenewal } from '../api/queries'
import { ApiError, type Renewal } from '../api/client'
import { formatDate } from '../domain/dates'

const STATUSES = [
  { id: 'booked', label: 'Booked', tone: 'accent' },
  { id: 'chased', label: 'Chased', tone: 'warning' },
  { id: 'evidence_in', label: 'Evidence in', tone: 'good' },
] as const

/**
 * A renewal mark (OPS) — the Coolibah portal's Booked / Chased / Evidence-in on an expiring
 * certificate, with the provider and the course date behind it. Shown as a chip; pressed, it
 * opens the three choices and the two fields. What the certificate's state is stays the
 * engine's; this is only what the office is doing about it.
 */
export function RenewalMark({
  personId,
  requirementId,
  renewal,
  canEdit,
}: {
  personId: number
  requirementId: number
  renewal: Renewal | undefined
  canEdit: boolean
}): React.ReactNode {
  const set = useSetRenewal()
  const clear = useClearRenewal()
  const [open, setOpen] = useState(false)
  const [provider, setProvider] = useState(renewal?.provider ?? '')
  const [courseDate, setCourseDate] = useState(renewal?.courseDate ?? '')
  const [note, setNote] = useState(renewal?.note ?? '')
  const current = STATUSES.find((s) => s.id === renewal?.status)

  if (!open) {
    return (
      <span className="renewal">
        {current !== undefined ? (
          <span className={`chip chip--${current.tone} chip--small`} title={[renewal?.provider, renewal?.courseDate !== null && renewal?.courseDate !== undefined ? formatDate(renewal.courseDate) : null, renewal?.note].filter(Boolean).join(' · ') || undefined}>
            {current.label}
            {renewal?.courseDate !== null && renewal?.courseDate !== undefined && ` · ${formatDate(renewal.courseDate)}`}
          </span>
        ) : (
          <span className="dim">—</span>
        )}
        {canEdit && (
          <button type="button" className="link-action renewal__edit" onClick={() => setOpen(true)}>
            {current === undefined ? 'Mark' : 'Change'}
          </button>
        )}
      </span>
    )
  }

  return (
    <span className="renewal renewal--open">
      <span className="row-actions">
        {STATUSES.map((s) => (
          <button
            key={s.id}
            type="button"
            className={`chip chip--toggle chip--${s.tone} chip--small${renewal?.status === s.id ? ' chip--pressed' : ''}`}
            disabled={set.isPending}
            onClick={() =>
              set.mutate(
                { personId, requirementId, body: { status: s.id, provider: provider === '' ? null : provider, courseDate: courseDate === '' ? null : courseDate, note: note === '' ? null : note } },
                { onSuccess: () => setOpen(false) },
              )
            }
          >
            {s.label}
          </button>
        ))}
      </span>
      <span className="row-actions">
        <input className="input input--tight" value={provider} placeholder="Provider" onChange={(event) => setProvider(event.target.value)} />
        <input className="input input--tight" type="date" value={courseDate} onChange={(event) => setCourseDate(event.target.value)} />
        <input className="input input--tight" value={note} placeholder="Note" onChange={(event) => setNote(event.target.value)} />
        {renewal !== undefined && (
          <button type="button" className="link-action" disabled={clear.isPending} onClick={() => clear.mutate({ personId, requirementId }, { onSuccess: () => setOpen(false) })}>
            Clear
          </button>
        )}
        <button type="button" className="link-action" onClick={() => setOpen(false)}>
          Close
        </button>
      </span>
      {(set.error ?? clear.error) !== null && <span className="editor__error">{errorText(set.error ?? clear.error)}</span>}
    </span>
  )
}

function errorText(error: unknown): string {
  return error instanceof ApiError ? error.message : String(error)
}
