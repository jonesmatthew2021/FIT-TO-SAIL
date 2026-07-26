import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import {
  useCreateRegisterRecord,
  useCrewChanges,
  useNextRecordId,
  usePeople,
  useRequirements,
} from '../api/queries'
import { ApiError } from '../api/client'
import { useSession } from '../api/session'
import { ErrorPanel } from '../components/ErrorPanel'
import { SwingSelector } from '../components/SwingSelector'
import { formatDate } from '../domain/dates'
import { REGISTER_TYPES } from '../domain/enums'

/**
 * ADM-4 create — raising a request or query.
 *
 * Reachable two ways, and the second is the one that matters: the gap report links here with
 * `partnership`, `cc`, `personId` and `requirementId` already in the URL, which is §6's
 * "one-click pre-filled exemption request". A coordinator looking at a gap should not have to
 * retype who and what it is about.
 *
 * Two server behaviours surface here rather than being discovered on submit:
 *
 *  - **The record id is a preview, not a reservation.** It is allocated again under a lock at
 *    create time, so two coordinators who both saw `UNICC24-4` get 4 and 5, not a collision.
 *  - **Lodging after the cutoff needs an acknowledgement** (Q17). The first attempt comes back
 *    409 `late_submission`; the checkbox appears and the retry carries it. Late is permitted —
 *    silently late is not.
 */
export function RegisterNew(): React.ReactNode {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const create = useCreateRegisterRecord()
  const today = useSession().today

  const [partnership, setPartnership] = useState<string | null>(params.get('partnership'))
  const [cc, setCc] = useState<string | null>(params.get('cc'))
  const [type, setType] = useState(REGISTER_TYPES[0] as string)
  const [personId, setPersonId] = useState(params.get('personId') ?? '')
  const [requirementId, setRequirementId] = useState(params.get('requirementId') ?? '')
  const [reqRaw, setReqRaw] = useState('')
  const [effectiveFrom, setEffectiveFrom] = useState('')
  const [effectiveTo, setEffectiveTo] = useState('')
  const [acknowledgeLate, setAcknowledgeLate] = useState(false)

  const people = usePeople()
  const requirements = useRequirements()
  const crewChanges = useCrewChanges(partnership)
  const nextId = useNextRecordId(partnership, cc)

  const swing = (crewChanges.data ?? []).find((candidate) => candidate.ccId === cc)
  const late = swing !== undefined && today > swing.cutoff
  const isLateRejection =
    create.error instanceof ApiError && create.error.code === 'late_submission'

  function submit(event: React.FormEvent): void {
    event.preventDefault()
    if (partnership === null || cc === null) return
    create.mutate(
      {
        type,
        partnership,
        cc,
        personId: personId === '' ? null : Number(personId),
        requirementId: requirementId === '' ? null : Number(requirementId),
        reqRaw: reqRaw === '' ? null : reqRaw,
        effectiveFrom: effectiveFrom === '' ? null : effectiveFrom,
        effectiveTo: effectiveTo === '' ? null : effectiveTo,
        acknowledgeLateSubmission: acknowledgeLate,
      },
      {
        onSuccess: (detail) =>
          navigate(`/register/${encodeURIComponent(detail.record.recordId)}`),
      },
    )
  }

  return (
    <div className="screen">
      <header className="screen__header">
        <h1 className="screen__title">Raise a register record</h1>
        <p className="screen__subtitle">
          ADM-4 — the id, the window validation and the cutoff rule are all the server's.
        </p>
      </header>

      <SwingSelector
        partnership={partnership}
        cc={cc}
        onChange={(nextPartnership, nextCc) => {
          setPartnership(nextPartnership)
          setCc(nextCc)
        }}
      />

      {(partnership === null || cc === null) && (
        <p className="note">
          A record belongs to a swing: its effective window is validated inside that swing, and the
          cutoff that decides whether it is late is the swing's own.
        </p>
      )}

      {partnership !== null && cc !== null && (
        <form className="editor" onSubmit={submit}>
          <div className="field field--inline">
            <span className="field__label">Record id</span>
            <span className="mono">{nextId.data?.recordId ?? '…'}</span>
          </div>

          <label className="field field--inline">
            <span className="field__label">Type</span>
            <select className="input" value={type} onChange={(event) => setType(event.target.value)}>
              {REGISTER_TYPES.map((value) => (
                <option key={value} value={value}>
                  {value}
                </option>
              ))}
            </select>
          </label>

          <label className="field field--inline field--grow">
            <span className="field__label">Person</span>
            <select
              className="input"
              value={personId}
              onChange={(event) => setPersonId(event.target.value)}
            >
              <option value="">No specific person</option>
              {(people.data ?? []).map((person) => (
                <option key={person.id} value={person.id}>
                  {person.name} — {person.sam}
                </option>
              ))}
            </select>
          </label>

          <label className="field field--inline field--grow">
            <span className="field__label">Requirement</span>
            <select
              className="input"
              value={requirementId}
              onChange={(event) => setRequirementId(event.target.value)}
            >
              <option value="">Not in the catalogue</option>
              {(requirements.data ?? []).map((requirement) => (
                <option key={requirement.id} value={requirement.id}>
                  {requirement.code} — {requirement.title}
                </option>
              ))}
            </select>
          </label>

          {requirementId === '' && (
            <label className="field field--inline field--grow">
              <span className="field__label">Requirement as written</span>
              <input
                className="input"
                value={reqRaw}
                placeholder="The title as it appears on the paperwork"
                onChange={(event) => setReqRaw(event.target.value)}
              />
            </label>
          )}

          <label className="field field--inline">
            <span className="field__label">Effective from</span>
            <input
              className="input"
              type="date"
              value={effectiveFrom}
              min={swing?.from}
              max={swing?.to}
              onChange={(event) => setEffectiveFrom(event.target.value)}
            />
          </label>

          <label className="field field--inline">
            <span className="field__label">Effective to</span>
            <input
              className="input"
              type="date"
              value={effectiveTo}
              min={swing?.from}
              max={swing?.to}
              onChange={(event) => setEffectiveTo(event.target.value)}
            />
          </label>

          {(late || isLateRejection) && (
            <label className="checkbox">
              <input
                type="checkbox"
                checked={acknowledgeLate}
                onChange={(event) => setAcknowledgeLate(event.target.checked)}
              />{' '}
              This is after the {swing === undefined ? 'submission' : formatDate(swing.cutoff)}{' '}
              cutoff — acknowledge the late submission (Q17)
            </label>
          )}

          {create.error !== null && !isLateRejection && (
            <ErrorPanel title="Could not raise this record" error={create.error} />
          )}
          {isLateRejection && (
            <p className="editor__error">{(create.error as ApiError).message}</p>
          )}

          <div className="editor__actions">
            <button
              type="submit"
              className="button button--primary"
              disabled={
                create.isPending ||
                (requirementId === '' && reqRaw.trim() === '') ||
                ((late || isLateRejection) && !acknowledgeLate)
              }
            >
              {create.isPending ? 'Raising…' : 'Raise'}
            </button>
          </div>
        </form>
      )}
    </div>
  )
}
