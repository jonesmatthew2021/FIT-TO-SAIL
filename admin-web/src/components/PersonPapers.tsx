import { useState } from 'react'
import { usePersonDetail, useSetPersonDetail } from '../api/queries'
import { ApiError, type PersonDetail, type SavePersonDetailRequest } from '../api/client'
import { useHasRole, useToday } from '../api/session'
import { ErrorPanel } from './ErrorPanel'
import { Spinner } from './Spinner'
import { daysBetween, formatDate } from '../domain/dates'

const WRITERS = ['crew_coordinator', 'compliance_lead', 'data_steward', 'system_administrator'] as const

/**
 * Papers and contacts (OPS): passport, visa, seafarer's book, next of kin, emergency contact,
 * PPE sizes, dietary needs — on the person's own page, edited in place. Personal data: the
 * audit trail records which fields changed, never their values.
 */
export function PersonPapers({ personId }: { personId: number }): React.ReactNode {
  const today = useToday()
  const detail = usePersonDetail(personId)
  const save = useSetPersonDetail()
  const canEdit = useHasRole(...WRITERS)
  const [draft, setDraft] = useState<SavePersonDetailRequest | null>(null)

  if (detail.isPending) return <Spinner label="Loading papers" />
  if (detail.error !== null) return <ErrorPanel title="Could not load papers" error={detail.error} />

  const d = detail.data
  const expiry = (label: string, date: string | null) => {
    if (date === null) return { label, text: '—', tone: 'dim' }
    const days = daysBetween(today, date)
    return { label, text: `${formatDate(date)} · ${days < 0 ? `${-days} days ago` : `in ${days} days`}`, tone: days < 0 ? 'critical' : days <= 180 ? 'warning' : 'good' }
  }
  const dates = [expiry('Passport expires', d.passportExpiry), expiry('Visa expires', d.visaExpiry), expiry("Seafarer's book expires", d.seafarerBookExpiry)]

  const begin = () =>
    setDraft({
      passportNumber: d.passportNumber,
      passportExpiry: d.passportExpiry,
      visaDetail: d.visaDetail,
      visaExpiry: d.visaExpiry,
      seafarerBook: d.seafarerBook,
      seafarerBookExpiry: d.seafarerBookExpiry,
      nextOfKinName: d.nextOfKinName,
      nextOfKinRelation: d.nextOfKinRelation,
      nextOfKinPhone: d.nextOfKinPhone,
      emergencyContact: d.emergencyContact,
      ppeSizes: d.ppeSizes,
      dietary: d.dietary,
      notes: d.notes,
    })

  return (
    <section className="section">
      <div className="section__header">
        <div>
          <h2 className="section__title">Papers and contacts</h2>
          <p className="section__note">Passport, visa, seafarer's book, next of kin, emergency contact, PPE sizes. Personal data — the office's eyes only.</p>
        </div>
        {canEdit && draft === null && (
          <button type="button" className="button" onClick={begin}>
            Edit
          </button>
        )}
      </div>

      {draft === null ? (
        <div className="biz-card__cols">
          <div>
            <p className="nav__group swing-eyebrow">Papers</p>
            <Fact label="Passport" value={d.passportNumber} />
            <Fact label="Visa" value={d.visaDetail} />
            <Fact label="Seafarer's book" value={d.seafarerBook} />
            {dates.map((x) => (
              <div key={x.label} className="biz-fact">
                <span className="biz-fact__label">{x.label}</span>
                <span className={x.tone === 'dim' ? 'dim' : ''}>{x.tone === 'dim' ? '—' : <span className={`chip chip--${x.tone} chip--small`}>{x.text}</span>}</span>
              </div>
            ))}
          </div>
          <div>
            <p className="nav__group swing-eyebrow">Contacts</p>
            <Fact label="Next of kin" value={d.nextOfKinName === null ? null : `${d.nextOfKinName}${d.nextOfKinRelation !== null ? ` (${d.nextOfKinRelation})` : ''}`} />
            <Fact label="Their phone" value={d.nextOfKinPhone} />
            <Fact label="Emergency contact" value={d.emergencyContact} />
          </div>
          <div>
            <p className="nav__group swing-eyebrow">Onboard</p>
            <Fact label="PPE sizes" value={d.ppeSizes} />
            <Fact label="Dietary" value={d.dietary} />
            <Fact label="Notes" value={d.notes} />
          </div>
        </div>
      ) : (
        <form
          className="editor"
          onSubmit={(event) => {
            event.preventDefault()
            save.mutate({ personId, body: draft }, { onSuccess: () => setDraft(null) })
          }}
        >
          <div className="biz-card__cols">
            <div>
              <Field label="Passport number" value={draft.passportNumber} onChange={(v) => setDraft({ ...draft, passportNumber: v })} />
              <Field label="Passport expires" value={draft.passportExpiry} onChange={(v) => setDraft({ ...draft, passportExpiry: v })} type="date" />
              <Field label="Visa" value={draft.visaDetail} onChange={(v) => setDraft({ ...draft, visaDetail: v })} placeholder="Kind and number" />
              <Field label="Visa expires" value={draft.visaExpiry} onChange={(v) => setDraft({ ...draft, visaExpiry: v })} type="date" />
              <Field label="Seafarer's book" value={draft.seafarerBook} onChange={(v) => setDraft({ ...draft, seafarerBook: v })} />
              <Field label="Book expires" value={draft.seafarerBookExpiry} onChange={(v) => setDraft({ ...draft, seafarerBookExpiry: v })} type="date" />
            </div>
            <div>
              <Field label="Next of kin" value={draft.nextOfKinName} onChange={(v) => setDraft({ ...draft, nextOfKinName: v })} />
              <Field label="Relation" value={draft.nextOfKinRelation} onChange={(v) => setDraft({ ...draft, nextOfKinRelation: v })} placeholder="Wife, partner, mother" />
              <Field label="Their phone" value={draft.nextOfKinPhone} onChange={(v) => setDraft({ ...draft, nextOfKinPhone: v })} />
              <Field label="Emergency contact" value={draft.emergencyContact} onChange={(v) => setDraft({ ...draft, emergencyContact: v })} placeholder="If not the next of kin" />
            </div>
            <div>
              <Field label="PPE sizes" value={draft.ppeSizes} onChange={(v) => setDraft({ ...draft, ppeSizes: v })} placeholder="Boots 10, overalls L, hard hat M" />
              <Field label="Dietary" value={draft.dietary} onChange={(v) => setDraft({ ...draft, dietary: v })} />
              <Field label="Notes" value={draft.notes} onChange={(v) => setDraft({ ...draft, notes: v })} />
            </div>
          </div>
          <div className="editor__actions">
            <button type="submit" className="button button--primary" disabled={save.isPending}>
              {save.isPending ? 'Saving…' : 'Save'}
            </button>
            <button type="button" className="button" onClick={() => setDraft(null)}>
              Cancel
            </button>
          </div>
          {save.error !== null && <p className="editor__error">{save.error instanceof ApiError ? save.error.message : String(save.error)}</p>}
        </form>
      )}
    </section>
  )
}

function Fact({ label, value }: { label: string; value: string | null }): React.ReactNode {
  return (
    <div className="biz-fact">
      <span className="biz-fact__label">{label}</span>
      <span className={value === null || value === '' ? 'dim' : ''}>{value === null || value === '' ? '—' : value}</span>
    </div>
  )
}

function Field({ label, value, onChange, type = 'text', placeholder }: { label: string; value: string | null | undefined; onChange: (value: string | null) => void; type?: string; placeholder?: string }): React.ReactNode {
  return (
    <label className="field field--inline field--grow">
      <span className="field__label">{label}</span>
      <input className="input" type={type} value={value ?? ''} onChange={(event) => onChange(event.target.value === '' ? null : event.target.value)} placeholder={placeholder} />
    </label>
  )
}

export type { PersonDetail }
