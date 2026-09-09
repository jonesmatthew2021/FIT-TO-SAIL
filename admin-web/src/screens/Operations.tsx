import { useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import {
  useAcknowledgeNotice,
  useAddFinding,
  useAddRegulatory,
  useAddStandardRegulatory,
  useAddTravel,
  useAudits,
  useCreateAudit,
  useAllPartnerships,
  useAttachVesselCertificateFile,
  useClearRest,
  useCreateVesselCertificate,
  useCustomerScope,
  useGenerateReminders,
  useLeave,
  useManning,
  useMarkReminderSent,
  useNoticeAcks,
  useNotices,
  usePeople,
  usePositions,
  usePostNotice,
  useRecordLeave,
  useRegulatory,
  useReminders,
  useRemoveFinding,
  useRemoveRegulatory,
  useRemoveTravel,
  useRestSummary,
  useSetLeaveStatus,
  useSetManning,
  useSetRest,
  useSwingEvaluation,
  useTimesheet,
  useTravel,
  useUpcomingSwings,
  useUpdateAudit,
  useUpdateFinding,
  useUpdateRegulatory,
  useUpdateTravel,
  useUpdateVesselCertificate,
  useVesselCertificates,
  useVessels,
  useWithdrawNotice,
  useWithdrawVesselCertificate,
} from '../api/queries'
import {
  api,
  ApiError,
  type AuditFinding,
  type AuditRecord,
  type ManningRequirement,
  type RegulatoryItem,
  type SaveAuditRequest,
  type SaveFindingRequest,
  type SaveRegulatoryItemRequest,
  type Notice,
  type Partnership,
  type Person,
  type SaveTravelItemRequest,
  type SaveVesselCertificateRequest,
  type TravelItem,
  type VesselCertificate,
} from '../api/client'
import { useHasRole, useToday } from '../api/session'
import { Copy } from '../components/Copy'
import { ErrorPanel } from '../components/ErrorPanel'
import { Modal } from '../components/Modal'
import { AuditPack } from '../components/AuditPack'
import { ShipBar } from '../components/ShipBar'
import { Spinner } from '../components/Spinner'
import { downloadCsv, toCsv } from '../domain/csv'
import { dateFromEpochDay, daysBetween, epochDay, formatDate, formatDayMonth } from '../domain/dates'
import { RANK_GROUPS, rankGroup } from '../domain/ranks'

const TABS = [
  { id: 'leave', label: 'Leave' },
  { id: 'rest', label: 'Hours of rest' },
  { id: 'vessel', label: 'Vessel certificates' },
  { id: 'manning', label: 'Manning' },
  { id: 'travel', label: 'Crew change travel' },
  { id: 'notices', label: 'Notices' },
  { id: 'reminders', label: 'Reminders' },
  { id: 'timesheets', label: 'Timesheets' },
  { id: 'auditing', label: 'Auditing' },
  { id: 'amsa', label: 'AMSA requirements' },
] as const

type TabId = (typeof TABS)[number]['id']

const WRITERS = ['crew_coordinator', 'compliance_lead', 'data_steward', 'system_administrator'] as const

/**
 * Operations (OPS-1) — what runs around the compliance engine for one ship: leave and hours of
 * rest, the vessel's own papers, minimum safe manning, the travel behind each swing, notices
 * with acknowledgement, the reminder queue, and timesheets for payroll. Each is a tab, each is
 * per ship, and none of it changes a compliance answer.
 */
export function Operations(): React.ReactNode {
  const scope = useCustomerScope()
  const partnerships = useAllPartnerships()
  const [params, setParams] = useSearchParams()
  const tab = (TABS.find((t) => t.id === params.get('tab'))?.id ?? 'leave') as TabId

  if (partnerships.isPending) return <Spinner label="Loading the ship" />
  if (partnerships.error !== null) return <ErrorPanel title="Could not load the ship" error={partnerships.error} />
  const ship = partnerships.data.find((p) => p.id === scope.operationId)
  if (ship === undefined) return <p className="empty">Choose a ship in the rail.</p>

  return (
    <div className="screen">
      <header className="screen__header screen__header--bar">
        <div>
          <h1 className="screen__title">Operations</h1>
          <p className="screen__subtitle">{ship.name}</p>
        </div>
        <ShipBar />
      </header>

      <nav className="tabs" aria-label="Operations tabs">
        {TABS.map((t) => (
          <button
            key={t.id}
            type="button"
            className={t.id === tab ? 'tabs__tab tabs__tab--active' : 'tabs__tab'}
            aria-current={t.id === tab}
            onClick={() => {
              const next = new URLSearchParams(params)
              next.set('tab', t.id)
              setParams(next)
            }}
          >
            {t.label}
          </button>
        ))}
      </nav>

      {tab === 'leave' && <LeaveTab ship={ship} />}
      {tab === 'rest' && <RestTab ship={ship} />}
      {tab === 'vessel' && <VesselTab ship={ship} />}
      {tab === 'manning' && <ManningTab ship={ship} />}
      {tab === 'travel' && <TravelTab ship={ship} />}
      {tab === 'notices' && <NoticesTab ship={ship} />}
      {tab === 'reminders' && <RemindersTab ship={ship} />}
      {tab === 'timesheets' && <TimesheetsTab ship={ship} />}
      {tab === 'auditing' && <AuditingTab ship={ship} />}
      {tab === 'amsa' && <AmsaTab ship={ship} />}
    </div>
  )
}

function errorText(error: unknown): string {
  return error instanceof ApiError ? error.message : String(error)
}

function useShipCrew(ship: Partnership): Person[] {
  const people = usePeople()
  const order = [...RANK_GROUPS.map((g) => g.label), 'Other positions']
  return (people.data ?? [])
    .filter((p) => p.partnershipId === ship.id && p.status === 'active')
    .sort((a, b) => order.indexOf(rankGroup(a.positionName)) - order.indexOf(rankGroup(b.positionName)) || a.name.localeCompare(b.name))
}

function CrewSelect({ crew, value, onChange, allowNone = false }: { crew: readonly Person[]; value: number | null; onChange: (id: number | null) => void; allowNone?: boolean }): React.ReactNode {
  return (
    <select className="input" value={value ?? ''} onChange={(event) => onChange(event.target.value === '' ? null : Number(event.target.value))}>
      <option value="">{allowNone ? 'The whole change' : 'Choose…'}</option>
      {crew.map((p) => (
        <option key={p.id} value={p.id}>
          {p.name} · {p.positionName}
        </option>
      ))}
    </select>
  )
}

// ---------------------------------------------------------------------------
// Leave
// ---------------------------------------------------------------------------

function LeaveTab({ ship }: { ship: Partnership }): React.ReactNode {
  const today = useToday()
  const leave = useLeave(ship.abbrev, dateFromEpochDay(epochDay(today) - 60))
  const record = useRecordLeave()
  const setStatus = useSetLeaveStatus()
  const canEdit = useHasRole(...WRITERS)
  const crew = useShipCrew(ship)
  const [personId, setPersonId] = useState<number | null>(null)
  const [kind, setKind] = useState('annual')
  const [from, setFrom] = useState(today)
  const [to, setTo] = useState(today)

  if (leave.isPending) return <Spinner label="Loading leave" />
  if (leave.error !== null) return <ErrorPanel title="Could not load leave" error={leave.error} />

  const standing = leave.data.filter((l) => ['recorded', 'requested', 'approved'].includes(l.status))
  const awayNow = standing.filter((l) => epochDay(l.from) <= epochDay(today) && epochDay(l.to) >= epochDay(today))

  return (
    <div className="swing-page">
      <section className={`section fold fold--${awayNow.length > 0 ? 'accent' : 'good'}`}>
        <h2 className="section__title">
          {awayNow.length === 0 ? 'Nobody is away today.' : `${awayNow.length} away today: ${awayNow.map((l) => l.personName).join(', ')}.`}
        </h2>
        <p className="section__note">
          <Copy k="ops.leave-note">
            Leave that stands — recorded, requested or approved — is a clash on the planner and keeps the pattern from rostering the person onto a swing. Declined or cancelled leave does not.
          </Copy>
        </p>
      </section>

      {canEdit && (
        <form
          className="editor"
          onSubmit={(event) => {
            event.preventDefault()
            if (personId === null) return
            record.mutate({ personId, kind, from, to }, { onSuccess: () => setPersonId(null) })
          }}
        >
          <label className="field field--inline field--grow">
            <span className="field__label">Who</span>
            <CrewSelect crew={crew} value={personId} onChange={setPersonId} />
          </label>
          <label className="field field--inline">
            <span className="field__label">Kind</span>
            <select className="input" value={kind} onChange={(event) => setKind(event.target.value)}>
              {['annual', 'sick', 'unpaid', 'training', 'compassionate', 'other'].map((k) => (
                <option key={k} value={k}>
                  {k[0]?.toUpperCase()}{k.slice(1)}
                </option>
              ))}
            </select>
          </label>
          <label className="field field--inline">
            <span className="field__label">From</span>
            <input className="input" type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
          </label>
          <label className="field field--inline">
            <span className="field__label">To</span>
            <input className="input" type="date" value={to} onChange={(event) => setTo(event.target.value)} />
          </label>
          <div className="editor__actions">
            <button type="submit" className="button button--primary" disabled={record.isPending || personId === null || epochDay(to) < epochDay(from)}>
              {record.isPending ? 'Recording…' : 'Record leave'}
            </button>
          </div>
          {record.error !== null && <p className="editor__error">{errorText(record.error)}</p>}
        </form>
      )}

      <div className="table-block table-block--plain">
        <table className="table">
          <thead>
            <tr>
              <th scope="col">Who</th>
              <th scope="col">Kind</th>
              <th scope="col">From</th>
              <th scope="col">To</th>
              <th scope="col">Days</th>
              <th scope="col">Status</th>
              {canEdit && <th scope="col" />}
            </tr>
          </thead>
          <tbody>
            {leave.data.length === 0 && (
              <tr>
                <td colSpan={7} className="empty">
                  No leave recorded from the last two months on.
                </td>
              </tr>
            )}
            {leave.data.map((l) => (
              <tr key={l.id}>
                <td>
                  <Link to={`/people/${l.personId}`}>{l.personName}</Link> <span className="muted">· {l.position}</span>
                </td>
                <td>{l.kind.replace('_', ' ')}</td>
                <td>{formatDate(l.from)}</td>
                <td>{formatDate(l.to)}</td>
                <td>{daysBetween(l.from, l.to) + 1}</td>
                <td>
                  <span className={`chip chip--${l.status === 'approved' ? 'good' : l.status === 'declined' || l.status === 'cancelled' ? 'muted' : 'accent'} chip--small`}>{l.status}</span>
                </td>
                {canEdit && (
                  <td className="row-actions">
                    {l.status !== 'approved' && l.status !== 'cancelled' && l.status !== 'declined' && (
                      <button type="button" className="link-action" onClick={() => setStatus.mutate({ leaveId: l.id, status: 'approved' })}>
                        Approve
                      </button>
                    )}
                    {l.status === 'requested' && (
                      <button type="button" className="link-action" onClick={() => setStatus.mutate({ leaveId: l.id, status: 'declined' })}>
                        Decline
                      </button>
                    )}
                    {(l.status === 'recorded' || l.status === 'approved' || l.status === 'requested') && (
                      <button type="button" className="link-action" onClick={() => setStatus.mutate({ leaveId: l.id, status: 'cancelled' })}>
                        Cancel
                      </button>
                    )}
                    {(l.status === 'cancelled' || l.status === 'declined') && (
                      <button type="button" className="link-action" onClick={() => setStatus.mutate({ leaveId: l.id, status: 'recorded' })}>
                        Reinstate
                      </button>
                    )}
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {setStatus.error !== null && <p className="editor__error">{errorText(setStatus.error)}</p>}
    </div>
  )
}

// ---------------------------------------------------------------------------
// Hours of rest
// ---------------------------------------------------------------------------

function RestTab({ ship }: { ship: Partnership }): React.ReactNode {
  const today = useToday()
  const [from, setFrom] = useState(dateFromEpochDay(epochDay(today) - 13))
  const [to, setTo] = useState(today)
  const summary = useRestSummary(ship.abbrev, from, to)
  const setRest = useSetRest()
  const clearRest = useClearRest()
  const canEdit = useHasRole(...WRITERS, 'vessel_master')
  const crew = useShipCrew(ship)
  const [editing, setEditing] = useState<{ personId: number; day: string; value: string } | null>(null)

  const days: string[] = []
  for (let d = epochDay(from); d <= epochDay(to) && days.length < 31; d++) days.push(dateFromEpochDay(d))

  if (summary.isPending) return <Spinner label="Loading hours of rest" />
  if (summary.error !== null) return <ErrorPanel title="Could not load hours of rest" error={summary.error} />

  const value = new Map(summary.data.records.map((r) => [`${r.personId}/${r.day}`, r.restHours]))
  const breached = new Set(summary.data.breaches.map((b) => `${b.personId}/${b.day}`))

  return (
    <div className="swing-page">
      <section className={`section fold fold--${summary.data.breaches.length > 0 ? 'critical' : 'good'}`}>
        <h2 className="section__title">
          {summary.data.breaches.length === 0 ? 'No rest breaches in the range.' : `${summary.data.breaches.length} rest ${summary.data.breaches.length === 1 ? 'breach' : 'breaches'} in the range.`}
        </h2>
        <p className="section__note">
          <Copy k="ops.rest-note">
            At least 10 hours' rest in any day and 77 in any seven. Type the hours into the grid — a blank day is a day nobody has written down, not a breach. The week check only runs once all seven days have a figure.
          </Copy>
        </p>
      </section>

      <div className="row-actions roster-toolbar">
        <label className="field field--inline">
          <span className="field__label">From</span>
          <input className="input" type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
        </label>
        <label className="field field--inline">
          <span className="field__label">To</span>
          <input className="input" type="date" value={to} onChange={(event) => setTo(event.target.value)} />
        </label>
        <span className="muted">Up to 31 days at a time.</span>
      </div>

      <div className="daygrid-scroll">
        <table className="daygrid rest-grid">
          <thead>
            <tr>
              <th scope="col" className="daygrid__name">Crew</th>
              {days.map((day) => (
                <th key={day} scope="col" className={day === today ? 'daygrid__day daygrid__day--today' : 'daygrid__day'} title={formatDate(day)}>
                  <span className="daygrid__dom">{day.slice(8)}</span>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {crew.map((p) => (
              <tr key={p.id}>
                <th scope="row" className="daygrid__name">
                  <Link to={`/people/${p.id}`}>{p.name}</Link>
                  <span className="muted daygrid__pos">{p.positionName}</span>
                </th>
                {days.map((day) => {
                  const key = `${p.id}/${day}`
                  const hours = value.get(key)
                  const isEditing = editing !== null && editing.personId === p.id && editing.day === day
                  return (
                    <td key={day} className={`rest-cell${breached.has(key) ? ' rest-cell--breach' : hours !== undefined ? ' rest-cell--ok' : ''}`}>
                      {isEditing ? (
                        <input
                          className="rest-cell__input"
                          autoFocus
                          value={editing.value}
                          onChange={(event) => setEditing({ ...editing, value: event.target.value })}
                          onBlur={() => {
                            const n = Number(editing.value)
                            if (editing.value.trim() === '') clearRest.mutate({ personId: p.id, day })
                            else if (!Number.isNaN(n)) setRest.mutate({ personId: p.id, day, restHours: n, note: null })
                            setEditing(null)
                          }}
                          onKeyDown={(event) => {
                            if (event.key === 'Enter') (event.target as HTMLInputElement).blur()
                            if (event.key === 'Escape') setEditing(null)
                          }}
                        />
                      ) : (
                        <button
                          type="button"
                          className="rest-cell__button"
                          disabled={!canEdit}
                          title={`${p.name} · ${formatDate(day)}${hours !== undefined ? ` · ${hours} h rest` : ''}`}
                          onClick={() => setEditing({ personId: p.id, day, value: hours === undefined ? '' : String(hours) })}
                        >
                          {hours === undefined ? '' : hours}
                        </button>
                      )}
                    </td>
                  )
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {summary.data.breaches.length > 0 && (
        <ul className="list-plain list-plain--tight">
          {summary.data.breaches.map((b, i) => (
            <li key={i}>
              <span className="chip chip--critical chip--small">{formatDayMonth(b.day)}</span> {b.personName} — {b.rule} ({b.hours} h)
            </li>
          ))}
        </ul>
      )}
      {(setRest.error !== null || clearRest.error !== null) && <p className="editor__error">{errorText(setRest.error ?? clearRest.error)}</p>}
    </div>
  )
}

// ---------------------------------------------------------------------------
// Vessel certificates
// ---------------------------------------------------------------------------

const VESSEL_KINDS = ['survey', 'class', 'safety', 'radio', 'insurance', 'registration', 'other'] as const

function VesselTab({ ship }: { ship: Partnership }): React.ReactNode {
  const today = useToday()
  const certificates = useVesselCertificates(ship.abbrev)
  const vessels = useVessels()
  const withdraw = useWithdrawVesselCertificate()
  const attach = useAttachVesselCertificateFile()
  const canEdit = useHasRole('compliance_lead', 'data_steward', 'system_administrator')
  const [editing, setEditing] = useState<VesselCertificate | 'new' | null>(null)
  const fileFor = useRef<number | null>(null)
  const input = useRef<HTMLInputElement>(null)

  if (certificates.isPending) return <Spinner label="Loading the vessel's certificates" />
  if (certificates.error !== null) return <ErrorPanel title="Could not load the vessel's certificates" error={certificates.error} />

  const soon = certificates.data.filter((c) => c.expiresOn !== null && daysBetween(today, c.expiresOn) <= 90)
  const expired = certificates.data.filter((c) => c.expiresOn !== null && daysBetween(today, c.expiresOn) < 0)

  return (
    <div className="swing-page">
      <section className={`section fold fold--${expired.length > 0 ? 'critical' : soon.length > 0 ? 'accent' : 'good'}`}>
        <h2 className="section__title">
          {certificates.data.length === 0
            ? 'No vessel certificates on file yet.'
            : expired.length > 0
              ? `${expired.length} of the vessel's certificates ${expired.length === 1 ? 'has' : 'have'} expired.`
              : soon.length > 0
                ? `${soon.length} of the vessel's certificates ${soon.length === 1 ? 'runs' : 'run'} out inside 90 days.`
                : "The vessel's certificates are all in date."}
        </h2>
        <p className="section__note">
          <Copy k="ops.vessel-note">
            Survey, class, safety equipment, radio, insurance, registration — the ship's own papers, with the day each runs out. A compliant crew on a ship that is out of survey does not sail.
          </Copy>
        </p>
      </section>

      {canEdit && (
        <div className="row-actions roster-toolbar">
          <button type="button" className="button button--primary" onClick={() => setEditing('new')}>
            Add a vessel certificate
          </button>
          <input
            ref={input}
            type="file"
            hidden
            accept="application/pdf,image/*"
            onChange={(event) => {
              const picked = event.target.files?.[0]
              event.target.value = ''
              if (picked !== undefined && fileFor.current !== null) attach.mutate({ id: fileFor.current, file: picked })
            }}
          />
        </div>
      )}

      <div className="table-block table-block--plain">
        <table className="table">
          <thead>
            <tr>
              <th scope="col">Certificate</th>
              <th scope="col">Kind</th>
              <th scope="col">Vessel</th>
              <th scope="col">Reference</th>
              <th scope="col">Issued</th>
              <th scope="col">Expires</th>
              <th scope="col">Scan</th>
              {canEdit && <th scope="col" />}
            </tr>
          </thead>
          <tbody>
            {certificates.data.map((c) => {
              const days = c.expiresOn === null ? null : daysBetween(today, c.expiresOn)
              return (
                <tr key={c.id}>
                  <td>
                    <strong>{c.title}</strong>
                    {c.issuer !== null && <span className="muted"> · {c.issuer}</span>}
                    {c.note !== null && <div className="meta">{c.note}</div>}
                  </td>
                  <td>{c.kind}</td>
                  <td>{c.vesselName ?? <span className="dim">whole ship</span>}</td>
                  <td className="mono">{c.reference ?? '—'}</td>
                  <td>{formatDate(c.issuedOn)}</td>
                  <td>
                    {c.expiresOn === null ? (
                      <span className="dim">no expiry</span>
                    ) : (
                      <span className={`chip chip--${days !== null && days < 0 ? 'critical' : days !== null && days <= 30 ? 'critical' : days !== null && days <= 90 ? 'warning' : 'good'} chip--small`}>
                        {formatDate(c.expiresOn)}
                        {days !== null && ` · ${days < 0 ? `${-days} days ago` : `in ${days} days`}`}
                      </span>
                    )}
                  </td>
                  <td>
                    {c.fileName !== null ? (
                      <a href={api.vesselCertificateUrl(c.id)} target="_blank" rel="noreferrer">
                        Open
                      </a>
                    ) : (
                      <span className="dim">none</span>
                    )}
                  </td>
                  {canEdit && (
                    <td className="row-actions">
                      <button type="button" className="link-action" onClick={() => setEditing(c)}>
                        Edit
                      </button>
                      <button
                        type="button"
                        className="link-action"
                        disabled={attach.isPending}
                        onClick={() => {
                          fileFor.current = c.id
                          input.current?.click()
                        }}
                      >
                        {c.fileName === null ? 'Attach scan' : 'Replace scan'}
                      </button>
                      <button type="button" className="link-action" disabled={withdraw.isPending} onClick={() => withdraw.mutate(c.id)}>
                        Withdraw
                      </button>
                    </td>
                  )}
                </tr>
              )
            })}
            {certificates.data.length === 0 && (
              <tr>
                <td colSpan={8} className="empty">
                  Nothing on file. Add the survey and class certificates first.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      {(withdraw.error !== null || attach.error !== null) && <p className="editor__error">{errorText(withdraw.error ?? attach.error)}</p>}

      {editing !== null && <VesselCertificateForm ship={ship} vessels={(vessels.data ?? []).filter((v) => v.partnershipId === ship.id)} {...(editing === 'new' ? {} : { existing: editing })} onClose={() => setEditing(null)} />}
    </div>
  )
}

function VesselCertificateForm({
  ship,
  vessels,
  existing,
  onClose,
}: {
  ship: Partnership
  vessels: readonly { id: number; name: string }[]
  existing?: VesselCertificate
  onClose: () => void
}): React.ReactNode {
  const create = useCreateVesselCertificate()
  const update = useUpdateVesselCertificate()
  const [title, setTitle] = useState(existing?.title ?? '')
  const [kind, setKind] = useState(existing?.kind ?? 'survey')
  const [vesselId, setVesselId] = useState<number | null>(existing?.vesselId ?? null)
  const [reference, setReference] = useState(existing?.reference ?? '')
  const [issuer, setIssuer] = useState(existing?.issuer ?? '')
  const [issuedOn, setIssuedOn] = useState(existing?.issuedOn ?? '')
  const [expiresOn, setExpiresOn] = useState(existing?.expiresOn ?? '')
  const [note, setNote] = useState(existing?.note ?? '')
  const pending = create.isPending || update.isPending
  const body: SaveVesselCertificateRequest = {
    vesselId,
    title,
    kind,
    reference: reference === '' ? null : reference,
    issuer: issuer === '' ? null : issuer,
    issuedOn: issuedOn === '' ? null : issuedOn,
    expiresOn: expiresOn === '' ? null : expiresOn,
    note: note === '' ? null : note,
  }
  return (
    <Modal title={existing === undefined ? `Add a certificate for ${ship.abbrev}` : `Edit ${existing.title}`} onClose={onClose}>
      <form
        className="editor"
        onSubmit={(event) => {
          event.preventDefault()
          if (existing === undefined) create.mutate({ partnership: ship.abbrev, body }, { onSuccess: onClose })
          else update.mutate({ id: existing.id, body }, { onSuccess: onClose })
        }}
      >
        <label className="field field--inline field--grow">
          <span className="field__label">Title</span>
          <input className="input" value={title} onChange={(event) => setTitle(event.target.value)} placeholder="Certificate of Survey" />
        </label>
        <label className="field field--inline">
          <span className="field__label">Kind</span>
          <select className="input" value={kind} onChange={(event) => setKind(event.target.value)}>
            {VESSEL_KINDS.map((k) => (
              <option key={k} value={k}>
                {k[0]?.toUpperCase()}{k.slice(1)}
              </option>
            ))}
          </select>
        </label>
        <label className="field field--inline">
          <span className="field__label">Vessel</span>
          <select className="input" value={vesselId ?? ''} onChange={(event) => setVesselId(event.target.value === '' ? null : Number(event.target.value))}>
            <option value="">The whole ship</option>
            {vessels.map((v) => (
              <option key={v.id} value={v.id}>
                {v.name}
              </option>
            ))}
          </select>
        </label>
        <label className="field field--inline">
          <span className="field__label">Reference</span>
          <input className="input" value={reference} onChange={(event) => setReference(event.target.value)} />
        </label>
        <label className="field field--inline">
          <span className="field__label">Issued by</span>
          <input className="input" value={issuer} onChange={(event) => setIssuer(event.target.value)} placeholder="AMSA, class society, insurer" />
        </label>
        <label className="field field--inline">
          <span className="field__label">Issued on</span>
          <input className="input" type="date" value={issuedOn} onChange={(event) => setIssuedOn(event.target.value)} />
        </label>
        <label className="field field--inline">
          <span className="field__label">Expires on</span>
          <input className="input" type="date" value={expiresOn} onChange={(event) => setExpiresOn(event.target.value)} />
        </label>
        <label className="field field--inline field--grow">
          <span className="field__label">Note</span>
          <input className="input" value={note} onChange={(event) => setNote(event.target.value)} />
        </label>
        <div className="editor__actions">
          <button type="submit" className="button button--primary" disabled={pending || title.trim() === ''}>
            {pending ? 'Saving…' : 'Save'}
          </button>
          <button type="button" className="button" onClick={onClose}>
            Cancel
          </button>
        </div>
        {(create.error ?? update.error) !== null && <p className="editor__error">{errorText(create.error ?? update.error)}</p>}
      </form>
    </Modal>
  )
}

// ---------------------------------------------------------------------------
// Manning
// ---------------------------------------------------------------------------

function ManningTab({ ship }: { ship: Partnership }): React.ReactNode {
  const manning = useManning(ship.abbrev)
  const positions = usePositions()
  const save = useSetManning()
  const canEdit = useHasRole('compliance_lead', 'system_administrator')
  const [draft, setDraft] = useState<ManningRequirement[] | null>(null)

  if (manning.isPending || positions.isPending) return <Spinner label="Loading the manning table" />
  if (manning.error !== null) return <ErrorPanel title="Could not load the manning table" error={manning.error} />

  const rows = draft ?? manning.data
  const positionName = (id: number) => positions.data?.find((p) => p.id === id)?.name ?? `#${id}`

  return (
    <div className="swing-page">
      <section className="section fold fold--accent">
        <h2 className="section__title">Minimum safe manning</h2>
        <p className="section__note">
          <Copy k="ops.manning-note">
            From the ship's manning document: how many of each position every swing must carry, on the whole swing or on a shift of it. Each swing is checked against this table on Swing and shift compliance.
          </Copy>
        </p>
      </section>

      <div className="table-block table-block--plain">
        <table className="table">
          <thead>
            <tr>
              <th scope="col">Position</th>
              <th scope="col">Where</th>
              <th scope="col">At least</th>
              {draft !== null && <th scope="col" />}
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 && (
              <tr>
                <td colSpan={4} className="empty">
                  No manning table yet.
                </td>
              </tr>
            )}
            {rows.map((r, i) => (
              <tr key={`${r.positionId}-${r.shift ?? ''}-${i}`}>
                <td>
                  {draft === null ? (
                    positionName(r.positionId)
                  ) : (
                    <select className="input" value={r.positionId} onChange={(event) => setDraft(rows.map((x, j) => (j === i ? { ...x, positionId: Number(event.target.value), positionName: positionName(Number(event.target.value)) } : x)))}>
                      {(positions.data ?? []).map((p) => (
                        <option key={p.id} value={p.id}>
                          {p.name}
                        </option>
                      ))}
                    </select>
                  )}
                </td>
                <td>
                  {draft === null ? (
                    (r.shift ?? 'Whole swing')
                  ) : (
                    <select className="input" value={r.shift ?? ''} onChange={(event) => setDraft(rows.map((x, j) => (j === i ? { ...x, shift: event.target.value === '' ? null : event.target.value } : x)))}>
                      <option value="">Whole swing</option>
                      <option value="Shift 1">Shift 1 (days)</option>
                      <option value="Shift 2">Shift 2 (nights)</option>
                    </select>
                  )}
                </td>
                <td>
                  {draft === null ? (
                    r.required
                  ) : (
                    <input className="input input--level" inputMode="numeric" value={r.required} onChange={(event) => setDraft(rows.map((x, j) => (j === i ? { ...x, required: Number(event.target.value) || 0 } : x)))} />
                  )}
                </td>
                {draft !== null && (
                  <td>
                    <button type="button" className="link-action" onClick={() => setDraft(rows.filter((_, j) => j !== i))}>
                      Remove
                    </button>
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {canEdit && (
        <div className="editor__actions">
          {draft === null ? (
            <button type="button" className="button button--primary" onClick={() => setDraft([...manning.data])}>
              Edit the table
            </button>
          ) : (
            <>
              <button
                type="button"
                className="button"
                onClick={() => {
                  const first = positions.data?.[0]
                  if (first !== undefined) setDraft([...rows, { positionId: first.id, positionName: first.name, shift: null, required: 1 }])
                }}
              >
                Add a line
              </button>
              <button
                type="button"
                className="button button--primary"
                disabled={save.isPending}
                onClick={() => save.mutate({ partnership: ship.abbrev, requirements: rows }, { onSuccess: () => setDraft(null) })}
              >
                {save.isPending ? 'Saving…' : 'Save the table'}
              </button>
              <button type="button" className="button" onClick={() => setDraft(null)}>
                Cancel
              </button>
            </>
          )}
        </div>
      )}
      {save.error !== null && <p className="editor__error">{errorText(save.error)}</p>}
    </div>
  )
}

// ---------------------------------------------------------------------------
// Crew change travel
// ---------------------------------------------------------------------------

const TRAVEL_KINDS = [
  { id: 'flight', label: 'Flight' },
  { id: 'accommodation', label: 'Accommodation' },
  { id: 'transfer', label: 'Transfer' },
  { id: 'other', label: 'Other' },
] as const

function TravelTab({ ship }: { ship: Partnership }): React.ReactNode {
  const today = useToday()
  const upcoming = useUpcomingSwings(ship.abbrev)
  const swings = [...(upcoming.data?.swings ?? [])].sort((a, b) => epochDay(a.from) - epochDay(b.from))
  const [cc, setCc] = useState<string | null>(null)
  const chosen = swings.find((s) => s.ccId === cc) ?? swings.find((s) => epochDay(s.to) >= epochDay(today)) ?? swings[0]
  const items = useTravel(ship.abbrev, chosen?.ccId ?? null)
  const add = useAddTravel()
  const update = useUpdateTravel()
  const remove = useRemoveTravel()
  const canEdit = useHasRole('crew_coordinator', 'compliance_lead', 'system_administrator')
  const crew = useShipCrew(ship)
  const [form, setForm] = useState<SaveTravelItemRequest>({ personId: null, kind: 'flight', detail: '', onDate: null, status: 'planned', note: null })

  if (upcoming.isPending) return <Spinner label="Loading the swings" />
  if (chosen === undefined) return <p className="empty">No swings on the calendar yet.</p>

  const list = items.data ?? []
  const done = list.filter((i) => i.status === 'done' || i.status === 'booked').length

  return (
    <div className="swing-page">
      <div className="swing-strip" role="tablist" aria-label="Swings">
        {swings.map((s) => (
          <button key={s.ccId} type="button" role="tab" aria-selected={s.ccId === chosen.ccId} className={s.ccId === chosen.ccId ? 'swing-pill swing-pill--picked' : 'swing-pill'} onClick={() => setCc(s.ccId)}>
            <span className="swing-pill__title">{formatDayMonth(s.from)} – {formatDayMonth(dateFromEpochDay(epochDay(s.to) + 1))}</span>
            <span className="swing-pill__meta mono">{s.rotation !== null ? `crew ${s.rotation}` : s.ccId}</span>
          </button>
        ))}
      </div>

      <section className={`section fold fold--${list.length > 0 && done === list.length ? 'good' : 'accent'}`}>
        <h2 className="section__title">
          Travel for {formatDayMonth(chosen.from)} – {formatDayMonth(chosen.to)} · {list.length === 0 ? 'nothing planned yet' : `${done} of ${list.length} booked or done`}
        </h2>
        <p className="section__note">
          <Copy k="ops.travel-note">
            The flights, beds and transfers behind the change — one line each, for a person or for the whole change. Planned, then booked, then done; the office works down the list the week before fly-out.
          </Copy>
        </p>
      </section>

      {canEdit && (
        <form
          className="editor"
          onSubmit={(event) => {
            event.preventDefault()
            add.mutate({ partnership: ship.abbrev, cc: chosen.ccId, body: form }, { onSuccess: () => setForm({ ...form, detail: '', note: null }) })
          }}
        >
          <label className="field field--inline field--grow">
            <span className="field__label">For</span>
            <CrewSelect crew={crew} value={form.personId ?? null} onChange={(id) => setForm({ ...form, personId: id })} allowNone />
          </label>
          <label className="field field--inline">
            <span className="field__label">What</span>
            <select className="input" value={form.kind} onChange={(event) => setForm({ ...form, kind: event.target.value })}>
              {TRAVEL_KINDS.map((k) => (
                <option key={k.id} value={k.id}>
                  {k.label}
                </option>
              ))}
            </select>
          </label>
          <label className="field field--inline field--grow">
            <span className="field__label">Detail</span>
            <input className="input" value={form.detail} onChange={(event) => setForm({ ...form, detail: event.target.value })} placeholder="QF 1073 PER–KTA 06:10, Hedland Lodge 3 nights, bus to wharf" />
          </label>
          <label className="field field--inline">
            <span className="field__label">On</span>
            <input className="input" type="date" value={form.onDate ?? ''} onChange={(event) => setForm({ ...form, onDate: event.target.value === '' ? null : event.target.value })} />
          </label>
          <div className="editor__actions">
            <button type="submit" className="button button--primary" disabled={add.isPending || form.detail.trim() === ''}>
              {add.isPending ? 'Adding…' : 'Add to the list'}
            </button>
          </div>
          {add.error !== null && <p className="editor__error">{errorText(add.error)}</p>}
        </form>
      )}

      <div className="table-block table-block--plain">
        <table className="table">
          <thead>
            <tr>
              <th scope="col">On</th>
              <th scope="col">For</th>
              <th scope="col">What</th>
              <th scope="col">Detail</th>
              <th scope="col">Status</th>
              {canEdit && <th scope="col" />}
            </tr>
          </thead>
          <tbody>
            {list.length === 0 && (
              <tr>
                <td colSpan={6} className="empty">
                  Nothing on the list for this swing.
                </td>
              </tr>
            )}
            {list.map((item) => (
              <TravelRow key={item.id} item={item} canEdit={canEdit} onStatus={(status) => update.mutate({ id: item.id, body: { personId: item.personId, kind: item.kind, detail: item.detail, onDate: item.onDate, status, note: item.note } })} onRemove={() => remove.mutate(item.id)} />
            ))}
          </tbody>
        </table>
      </div>
      {(update.error ?? remove.error) !== null && <p className="editor__error">{errorText(update.error ?? remove.error)}</p>}
    </div>
  )
}

function TravelRow({ item, canEdit, onStatus, onRemove }: { item: TravelItem; canEdit: boolean; onStatus: (status: string) => void; onRemove: () => void }): React.ReactNode {
  return (
    <tr>
      <td>{formatDate(item.onDate)}</td>
      <td>{item.personName ?? <span className="dim">the change</span>}</td>
      <td>{TRAVEL_KINDS.find((k) => k.id === item.kind)?.label ?? item.kind}</td>
      <td className="table__wrap">
        {item.detail}
        {item.note !== null && <span className="meta"> · {item.note}</span>}
      </td>
      <td>
        <span className={`chip chip--${item.status === 'done' ? 'good' : item.status === 'booked' ? 'accent' : item.status === 'cancelled' ? 'muted' : 'warning'} chip--small`}>{item.status}</span>
      </td>
      {canEdit && (
        <td className="row-actions">
          {item.status === 'planned' && (
            <button type="button" className="link-action" onClick={() => onStatus('booked')}>
              Booked
            </button>
          )}
          {item.status !== 'done' && item.status !== 'cancelled' && (
            <button type="button" className="link-action" onClick={() => onStatus('done')}>
              Done
            </button>
          )}
          {item.status !== 'cancelled' && (
            <button type="button" className="link-action" onClick={() => onStatus('cancelled')}>
              Cancel
            </button>
          )}
          <button type="button" className="link-action" onClick={onRemove}>
            Remove
          </button>
        </td>
      )}
    </tr>
  )
}

// ---------------------------------------------------------------------------
// Notices
// ---------------------------------------------------------------------------

function NoticesTab({ ship }: { ship: Partnership }): React.ReactNode {
  const notices = useNotices(ship.id)
  const post = usePostNotice()
  const withdraw = useWithdrawNotice()
  const canEdit = useHasRole('compliance_lead', 'crew_coordinator', 'workflow_manager', 'system_administrator')
  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [requiresAck, setRequiresAck] = useState(true)
  const [open, setOpen] = useState<Notice | null>(null)

  if (notices.isPending) return <Spinner label="Loading notices" />
  if (notices.error !== null) return <ErrorPanel title="Could not load notices" error={notices.error} />

  return (
    <div className="swing-page">
      <section className="section fold fold--accent">
        <h2 className="section__title">Notices to the crew</h2>
        <p className="section__note">
          <Copy k="ops.notices-note">
            A safety alert, a changed procedure, a message before the swing — posted to this ship and read by name. Who has not acknowledged is the point: a notice nobody can be shown to have read was not given.
          </Copy>
        </p>
      </section>

      {canEdit && (
        <form
          className="editor"
          onSubmit={(event) => {
            event.preventDefault()
            post.mutate({ partnershipId: ship.id, title, body, requiresAck }, {
              onSuccess: () => {
                setTitle('')
                setBody('')
              },
            })
          }}
        >
          <label className="field field--inline field--grow">
            <span className="field__label">Title</span>
            <input className="input" value={title} onChange={(event) => setTitle(event.target.value)} />
          </label>
          <label className="field field--inline field--grow">
            <span className="field__label">Notice</span>
            <textarea className="input" rows={4} value={body} onChange={(event) => setBody(event.target.value)} />
          </label>
          <label className="check check--box">
            <input type="checkbox" checked={requiresAck} onChange={(event) => setRequiresAck(event.target.checked)} />
            <span className="dot" />
            Crew must acknowledge it
          </label>
          <div className="editor__actions">
            <button type="submit" className="button button--primary" disabled={post.isPending || title.trim() === '' || body.trim() === ''}>
              {post.isPending ? 'Posting…' : 'Post to this ship'}
            </button>
          </div>
          {post.error !== null && <p className="editor__error">{errorText(post.error)}</p>}
        </form>
      )}

      {notices.data.length === 0 && <p className="empty">No notices posted.</p>}
      {notices.data.map((n) => (
        <div key={n.id} className="panel">
          <div className="section__header">
            <div>
              <p className="panel__title">{n.title}</p>
              <p className="meta">
                {formatDate(n.postedAt.slice(0, 10))} · {n.postedBy} · {n.partnershipAbbrev ?? n.customerName ?? 'everyone'}
                {n.requiresAck && ` · ${n.acknowledged} of ${n.reaches} acknowledged`}
              </p>
            </div>
            <span className="row-actions">
              {n.requiresAck && (
                <span className={`chip chip--${n.acknowledged >= n.reaches ? 'good' : 'warning'} chip--small`}>
                  {n.acknowledged >= n.reaches ? 'all read' : `${n.reaches - n.acknowledged} to read`}
                </span>
              )}
              <button type="button" className="button button--quiet" onClick={() => setOpen(n)}>
                Who has read it
              </button>
              {canEdit && (
                <button type="button" className="button button--quiet" disabled={withdraw.isPending} onClick={() => withdraw.mutate(n.id)}>
                  Withdraw
                </button>
              )}
            </span>
          </div>
          <p className="panel__detail" style={{ whiteSpace: 'pre-line' }}>
            {n.body}
          </p>
        </div>
      ))}
      {withdraw.error !== null && <p className="editor__error">{errorText(withdraw.error)}</p>}
      {open !== null && <NoticeAcks ship={ship} notice={open} onClose={() => setOpen(null)} />}
    </div>
  )
}

function NoticeAcks({ ship, notice, onClose }: { ship: Partnership; notice: Notice; onClose: () => void }): React.ReactNode {
  const acks = useNoticeAcks(notice.id)
  const acknowledge = useAcknowledgeNotice()
  const canEdit = useHasRole('compliance_lead', 'crew_coordinator', 'workflow_manager', 'system_administrator')
  const crew = useShipCrew(ship)
  const acked = new Set((acks.data ?? []).map((a) => a.personId))
  return (
    <Modal title={notice.title} note={`${acked.size} of ${crew.length} have acknowledged`} onClose={onClose}>
      <div className="table-block table-block--plain">
        <table className="table">
          <thead>
            <tr>
              <th scope="col">Crew</th>
              <th scope="col">Read</th>
              {canEdit && <th scope="col" />}
            </tr>
          </thead>
          <tbody>
            {crew.map((p) => {
              const ack = acks.data?.find((a) => a.personId === p.id)
              return (
                <tr key={p.id}>
                  <td>
                    {p.name} <span className="muted">· {p.positionName}</span>
                  </td>
                  <td>{ack !== undefined ? <span className="chip chip--good chip--small">{formatDate(ack.ackedAt.slice(0, 10))}</span> : <span className="chip chip--warning chip--small">not yet</span>}</td>
                  {canEdit && (
                    <td>
                      {ack === undefined && (
                        <button type="button" className="link-action" disabled={acknowledge.isPending} onClick={() => acknowledge.mutate({ id: notice.id, personId: p.id })}>
                          Mark read on their word
                        </button>
                      )}
                    </td>
                  )}
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </Modal>
  )
}

// ---------------------------------------------------------------------------
// Reminders
// ---------------------------------------------------------------------------

function RemindersTab({ ship }: { ship: Partnership }): React.ReactNode {
  const today = useToday()
  const [pendingOnly, setPendingOnly] = useState(true)
  const reminders = useReminders(ship.abbrev, pendingOnly)
  const generate = useGenerateReminders()
  const markSent = useMarkReminderSent()
  const canEdit = useHasRole(...WRITERS)

  if (reminders.isPending) return <Spinner label="Loading the reminder queue" />
  if (reminders.error !== null) return <ErrorPanel title="Could not load the reminder queue" error={reminders.error} />

  const pending = reminders.data.filter((r) => r.sentAt === null)
  const byPerson = new Map<number, typeof reminders.data>()
  for (const r of reminders.data) byPerson.set(r.personId, [...(byPerson.get(r.personId) ?? []), r])

  return (
    <div className="swing-page">
      <section className={`section fold fold--${pending.length > 0 ? 'critical' : 'good'}`}>
        <div className="section__header">
          <div>
            <h2 className="section__title">{pending.length === 0 ? 'Nobody is owed a reminder.' : `${pending.length} ${pending.length === 1 ? 'reminder' : 'reminders'} to send.`}</h2>
            <p className="section__note">
              <Copy k="ops.reminders-note">
                One reminder per person, certificate and band — 90, 60 and 30 days out, and on the day. Press "Bring up to date" to read the holdings again; send each by the email link or however you reach them, then mark it sent so nobody is chased twice. When an email service is wired in, sending becomes automatic.
              </Copy>
            </p>
          </div>
          <span className="row-actions">
            {canEdit && (
              <button type="button" className="button button--primary" disabled={generate.isPending} onClick={() => generate.mutate(ship.abbrev)}>
                {generate.isPending ? 'Reading…' : 'Bring up to date'}
              </button>
            )}
            <label className="check check--box">
              <input type="checkbox" checked={!pendingOnly} onChange={(event) => setPendingOnly(!event.target.checked)} />
              <span className="dot" />
              Show sent too
            </label>
          </span>
        </div>
        {generate.data !== undefined && <p className="muted">{generate.data.made} new · {generate.data.pending} waiting.</p>}
      </section>

      {[...byPerson.entries()].map(([personId, list]) => {
        const first = list[0]
        if (first === undefined) return null
        const email = first.personEmail
        const subject = encodeURIComponent(`Certificate renewal — ${list.map((r) => r.code).join(', ')}`)
        const bodyText = encodeURIComponent(
          `Hi ${first.personName.split(',').slice(-1)[0]?.trim() ?? ''},\n\n` +
            list.map((r) => `${r.code} ${r.title} runs out on ${formatDate(r.expiresOn)}.`).join('\n') +
            '\n\nPlease book the renewal and send the new certificate through when you have it.\n\nThanks,\nFIT TO SAIL',
        )
        return (
          <div key={personId} className="report-person" style={{ borderLeftColor: list.some((r) => r.sentAt === null) ? undefined : 'var(--color-good-500, #4caf8a)' }}>
            <div className="report-person__head">
              <span>
                <Link className="report-person__name" to={`/people/${personId}`}>
                  {first.personName}
                </Link>{' '}
                <span className="muted">· {first.position}</span>
              </span>
              <span className="row-actions">
                {email !== null ? (
                  <a className="button button--quiet" href={`mailto:${email}?subject=${subject}&body=${bodyText}`}>
                    Email {email}
                  </a>
                ) : (
                  <span className="dim">no email on file</span>
                )}
              </span>
            </div>
            {list.map((r) => (
              <div key={r.id} className="report-line" style={{ gridTemplateColumns: '70px 1fr 160px 120px 220px' }}>
                <span className="mono">{r.code}</span>
                <span>{r.title}</span>
                <span className="report-line__why">{r.daysBefore === 0 ? (daysBetween(today, r.expiresOn) < 0 ? `ran out ${-daysBetween(today, r.expiresOn)} days ago` : 'runs out today') : `${r.daysBefore} days before`}</span>
                <span>{formatDate(r.expiresOn)}</span>
                <span className="row-actions">
                  {r.sentAt !== null ? (
                    <span className="chip chip--good chip--small">sent {formatDate(r.sentAt.slice(0, 10))} · {r.channel}</span>
                  ) : canEdit ? (
                    <>
                      <button type="button" className="link-action" disabled={markSent.isPending} onClick={() => markSent.mutate({ id: r.id, channel: 'email' })}>
                        Sent by email
                      </button>
                      <button type="button" className="link-action" disabled={markSent.isPending} onClick={() => markSent.mutate({ id: r.id, channel: 'text' })}>
                        by text
                      </button>
                      <button type="button" className="link-action" disabled={markSent.isPending} onClick={() => markSent.mutate({ id: r.id, channel: 'phone' })}>
                        by phone
                      </button>
                    </>
                  ) : (
                    <span className="chip chip--warning chip--small">waiting</span>
                  )}
                </span>
              </div>
            ))}
          </div>
        )
      })}
      {(generate.error ?? markSent.error) !== null && <p className="editor__error">{errorText(generate.error ?? markSent.error)}</p>}
    </div>
  )
}

// ---------------------------------------------------------------------------
// Timesheets
// ---------------------------------------------------------------------------

function TimesheetsTab({ ship }: { ship: Partnership }): React.ReactNode {
  const today = useToday()
  const [from, setFrom] = useState(`${today.slice(0, 7)}-01`)
  const [to, setTo] = useState(today)
  const timesheet = useTimesheet(ship.abbrev, from, to)

  if (timesheet.isPending) return <Spinner label="Reading the roster" />
  if (timesheet.error !== null) return <ErrorPanel title="Could not build the timesheet" error={timesheet.error} />

  return (
    <div className="swing-page">
      <section className="section fold fold--accent">
        <div className="section__header">
          <div>
            <h2 className="section__title">
              {timesheet.data.totalDays} crew-days onboard, {formatDayMonth(from)} – {formatDate(to)}
            </h2>
            <p className="section__note">
              <Copy k="ops.timesheets-note">
                Days onboard per person, read off the roster and clipped to the range — the figure payroll wants. A swing that straddles a month end is split where the pay period is.
              </Copy>
            </p>
          </div>
          <span className="row-actions">
            <button
              type="button"
              className="button"
              onClick={() =>
                downloadCsv(
                  `timesheet-${ship.abbrev}-${from}-${to}.csv`,
                  toCsv(timesheet.data.lines, [
                    { header: 'Sam #', value: (l) => l.sam },
                    { header: 'Name', value: (l) => l.name },
                    { header: 'Position', value: (l) => l.position },
                    { header: 'Crew', value: (l) => l.rotation },
                    { header: 'Days onboard', value: (l) => l.days },
                    { header: 'Legs', value: (l) => l.legs.map((g) => `${g.ccId} ${g.from}..${g.to} (${g.days})`).join('; ') },
                  ]),
                )
              }
            >
              Export CSV for payroll
            </button>
            <a className="button button--quiet" href={api.calendarUrl(ship.abbrev)}>
              Swing calendar (.ics)
            </a>
          </span>
        </div>
      </section>

      <div className="row-actions roster-toolbar">
        <label className="field field--inline">
          <span className="field__label">From</span>
          <input className="input" type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
        </label>
        <label className="field field--inline">
          <span className="field__label">To</span>
          <input className="input" type="date" value={to} onChange={(event) => setTo(event.target.value)} />
        </label>
      </div>

      <div className="table-block table-block--plain">
        <table className="table">
          <thead>
            <tr>
              <th scope="col">Crew</th>
              <th scope="col">Position</th>
              <th scope="col">Crew letter</th>
              <th scope="col">Days onboard</th>
              <th scope="col">Legs</th>
            </tr>
          </thead>
          <tbody>
            {timesheet.data.lines.length === 0 && (
              <tr>
                <td colSpan={5} className="empty">
                  Nobody was rostered onboard in that range.
                </td>
              </tr>
            )}
            {timesheet.data.lines.map((l) => (
              <tr key={l.personId}>
                <td>
                  <Link to={`/people/${l.personId}`}>{l.name}</Link> <span className="mono muted">{l.sam}</span>
                </td>
                <td>{l.position}</td>
                <td className="mono">{l.rotation ?? '—'}</td>
                <td>
                  <strong>{l.days}</strong>
                </td>
                <td className="table__wrap muted">{l.legs.map((g) => `${g.ccId} ${formatDayMonth(g.from)} – ${formatDayMonth(g.to)} (${g.days})`).join(' · ')}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Auditing
// ---------------------------------------------------------------------------

const AUDIT_KINDS = [
  { id: 'internal', label: 'Internal' },
  { id: 'amsa', label: 'AMSA' },
  { id: 'class', label: 'Class' },
  { id: 'customer', label: 'Customer' },
  { id: 'flag', label: 'Flag state' },
  { id: 'other', label: 'Other' },
] as const

function AuditingTab({ ship }: { ship: Partnership }): React.ReactNode {
  const today = useToday()
  const audits = useAudits(ship.abbrev)
  const upcoming = useUpcomingSwings(ship.abbrev)
  const canEdit = useHasRole('compliance_lead', 'system_administrator')
  const [editing, setEditing] = useState<AuditRecord | 'new' | null>(null)
  const [raising, setRaising] = useState<AuditRecord | null>(null)
  const [editingFinding, setEditingFinding] = useState<AuditFinding | null>(null)
  const swings = [...(upcoming.data?.swings ?? [])].sort((a, b) => epochDay(a.from) - epochDay(b.from))
  const onNow = swings.find((s) => epochDay(s.from) <= epochDay(today) && epochDay(s.to) >= epochDay(today)) ?? swings.find((s) => epochDay(s.from) > epochDay(today)) ?? swings[0]
  const evaluation = useSwingEvaluation(onNow === undefined ? null : ship.abbrev, onNow?.ccId ?? null)

  if (audits.isPending) return <Spinner label="Loading the audit register" />
  if (audits.error !== null) return <ErrorPanel title="Could not load the audit register" error={audits.error} />

  const open = audits.data.flatMap((a) => a.findings).filter((f) => f.closedOn === null)
  const overdue = open.filter((f) => f.dueOn !== null && epochDay(f.dueOn) < epochDay(today))

  return (
    <div className="swing-page">
      <section className={`section fold fold--${overdue.length > 0 ? 'critical' : open.length > 0 ? 'accent' : 'good'}`}>
        <div className="section__header">
          <div>
            <h2 className="section__title">
              {audits.data.length === 0
                ? 'No audits recorded yet.'
                : open.length === 0
                  ? 'Every finding is closed.'
                  : `${open.length} open ${open.length === 1 ? 'finding' : 'findings'}${overdue.length > 0 ? `, ${overdue.length} overdue` : ''}.`}
            </h2>
            <p className="section__note">
              <Copy k="ops.audit-note">
                Every audit the ship has had — internal, AMSA, class, the customer's — and what it found. A finding carries its severity, the corrective action, who owns it and when it is due; what the next auditor wants to see is the last lot closed. The audit pack writes the current swing down for them.
              </Copy>
            </p>
          </div>
          <span className="row-actions">
            {onNow !== undefined && evaluation.data !== undefined && <AuditPack ship={ship} swing={onNow} evaluation={evaluation.data} />}
            {canEdit && (
              <button type="button" className="button button--primary" onClick={() => setEditing('new')}>
                Record an audit
              </button>
            )}
          </span>
        </div>
      </section>

      {audits.data.map((audit) => (
        <div key={audit.id} className="panel">
          <div className="section__header">
            <div>
              <p className="panel__title">
                {AUDIT_KINDS.find((k) => k.id === audit.kind)?.label ?? audit.kind} audit · {formatDate(audit.auditDate)}
                {audit.auditor !== null && <span className="muted"> · {audit.auditor}</span>}
              </p>
              <p className="meta">
                {audit.scope ?? 'no scope noted'}
                {audit.outcome !== null && ` · outcome: ${audit.outcome}`}
              </p>
            </div>
            <span className="row-actions">
              <span className={`chip chip--${audit.status === 'closed' ? 'good' : audit.status === 'planned' ? 'muted' : 'accent'} chip--small`}>{audit.status}</span>
              {canEdit && (
                <>
                  <button type="button" className="button button--quiet" onClick={() => setEditing(audit)}>
                    Edit
                  </button>
                  <button type="button" className="button button--quiet" onClick={() => setRaising(audit)}>
                    Raise a finding
                  </button>
                </>
              )}
            </span>
          </div>
          {audit.note !== null && <p className="panel__detail">{audit.note}</p>}
          {audit.findings.length > 0 && (
            <div className="table-block table-block--plain">
              <table className="table">
                <thead>
                  <tr>
                    <th scope="col">Ref</th>
                    <th scope="col">Severity</th>
                    <th scope="col">Finding</th>
                    <th scope="col">Corrective action</th>
                    <th scope="col">Owner</th>
                    <th scope="col">Due</th>
                    <th scope="col">Closed</th>
                    {canEdit && <th scope="col" />}
                  </tr>
                </thead>
                <tbody>
                  {audit.findings.map((f) => {
                    const late = f.closedOn === null && f.dueOn !== null && epochDay(f.dueOn) < epochDay(today)
                    return (
                      <tr key={f.id}>
                        <td className="mono">{f.reference ?? '—'}</td>
                        <td>
                          <span className={`chip chip--${f.severity === 'major' ? 'critical' : f.severity === 'minor' ? 'warning' : 'muted'} chip--small`}>{f.severity}</span>
                        </td>
                        <td className="table__wrap">{f.description}</td>
                        <td className="table__wrap">{f.correctiveAction ?? <span className="dim">—</span>}</td>
                        <td>{f.owner ?? <span className="dim">—</span>}</td>
                        <td>{f.dueOn === null ? <span className="dim">—</span> : <span className={late ? 'chip chip--critical chip--small' : ''}>{formatDate(f.dueOn)}</span>}</td>
                        <td>{f.closedOn === null ? <span className="chip chip--warning chip--small">open</span> : <span className="chip chip--good chip--small">{formatDate(f.closedOn)}</span>}</td>
                        {canEdit && (
                          <td>
                            <button type="button" className="link-action" onClick={() => setEditingFinding(f)}>
                              {f.closedOn === null ? 'Update / close' : 'Edit'}
                            </button>
                          </td>
                        )}
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      ))}

      {editing !== null && <AuditForm ship={ship} {...(editing === 'new' ? {} : { existing: editing })} onClose={() => setEditing(null)} />}
      {raising !== null && <FindingForm auditId={raising.id} onClose={() => setRaising(null)} />}
      {editingFinding !== null && <FindingForm auditId={editingFinding.auditId} existing={editingFinding} onClose={() => setEditingFinding(null)} />}
    </div>
  )
}

function AuditForm({ ship, existing, onClose }: { ship: Partnership; existing?: AuditRecord; onClose: () => void }): React.ReactNode {
  const today = useToday()
  const create = useCreateAudit()
  const update = useUpdateAudit()
  const [kind, setKind] = useState(existing?.kind ?? 'internal')
  const [auditor, setAuditor] = useState(existing?.auditor ?? '')
  const [auditDate, setAuditDate] = useState(existing?.auditDate ?? today)
  const [scope, setScope] = useState(existing?.scope ?? '')
  const [outcome, setOutcome] = useState(existing?.outcome ?? '')
  const [status, setStatus] = useState(existing?.status ?? 'open')
  const [note, setNote] = useState(existing?.note ?? '')
  const pending = create.isPending || update.isPending
  const body: SaveAuditRequest = { kind, auditor: auditor === '' ? null : auditor, auditDate, scope: scope === '' ? null : scope, outcome: outcome === '' ? null : outcome, status, note: note === '' ? null : note }
  return (
    <Modal title={existing === undefined ? `Record an audit of ${ship.abbrev}` : 'Edit the audit'} onClose={onClose}>
      <form
        className="editor"
        onSubmit={(event) => {
          event.preventDefault()
          if (existing === undefined) create.mutate({ partnership: ship.abbrev, body }, { onSuccess: onClose })
          else update.mutate({ id: existing.id, body }, { onSuccess: onClose })
        }}
      >
        <label className="field field--inline">
          <span className="field__label">Kind</span>
          <select className="input" value={kind} onChange={(event) => setKind(event.target.value)}>
            {AUDIT_KINDS.map((k) => (
              <option key={k.id} value={k.id}>
                {k.label}
              </option>
            ))}
          </select>
        </label>
        <label className="field field--inline">
          <span className="field__label">Date</span>
          <input className="input" type="date" value={auditDate} onChange={(event) => setAuditDate(event.target.value)} />
        </label>
        <label className="field field--inline field--grow">
          <span className="field__label">Auditor</span>
          <input className="input" value={auditor} onChange={(event) => setAuditor(event.target.value)} placeholder="Who audited, and for whom" />
        </label>
        <label className="field field--inline field--grow">
          <span className="field__label">Scope</span>
          <input className="input" value={scope} onChange={(event) => setScope(event.target.value)} placeholder="SMS review, crew certificates, vessel survey" />
        </label>
        <label className="field field--inline field--grow">
          <span className="field__label">Outcome</span>
          <input className="input" value={outcome} onChange={(event) => setOutcome(event.target.value)} placeholder="Passed with two minors" />
        </label>
        <label className="field field--inline">
          <span className="field__label">Status</span>
          <select className="input" value={status} onChange={(event) => setStatus(event.target.value)}>
            <option value="planned">Planned</option>
            <option value="open">Open</option>
            <option value="closed">Closed</option>
          </select>
        </label>
        <label className="field field--inline field--grow">
          <span className="field__label">Note</span>
          <textarea className="input" rows={2} value={note} onChange={(event) => setNote(event.target.value)} />
        </label>
        <div className="editor__actions">
          <button type="submit" className="button button--primary" disabled={pending}>
            {pending ? 'Saving…' : 'Save'}
          </button>
          <button type="button" className="button" onClick={onClose}>
            Cancel
          </button>
        </div>
        {(create.error ?? update.error) !== null && <p className="editor__error">{errorText(create.error ?? update.error)}</p>}
      </form>
    </Modal>
  )
}

function FindingForm({ auditId, existing, onClose }: { auditId: number; existing?: AuditFinding; onClose: () => void }): React.ReactNode {
  const today = useToday()
  const add = useAddFinding()
  const update = useUpdateFinding()
  const remove = useRemoveFinding()
  const [reference, setReference] = useState(existing?.reference ?? '')
  const [severity, setSeverity] = useState(existing?.severity ?? 'minor')
  const [description, setDescription] = useState(existing?.description ?? '')
  const [correctiveAction, setCorrectiveAction] = useState(existing?.correctiveAction ?? '')
  const [owner, setOwner] = useState(existing?.owner ?? '')
  const [dueOn, setDueOn] = useState(existing?.dueOn ?? '')
  const [closedOn, setClosedOn] = useState(existing?.closedOn ?? '')
  const pending = add.isPending || update.isPending || remove.isPending
  const body: SaveFindingRequest = {
    reference: reference === '' ? null : reference,
    severity,
    description,
    correctiveAction: correctiveAction === '' ? null : correctiveAction,
    owner: owner === '' ? null : owner,
    dueOn: dueOn === '' ? null : dueOn,
    closedOn: closedOn === '' ? null : closedOn,
  }
  return (
    <Modal title={existing === undefined ? 'Raise a finding' : 'The finding'} onClose={onClose}>
      <form
        className="editor"
        onSubmit={(event) => {
          event.preventDefault()
          if (existing === undefined) add.mutate({ auditId, body }, { onSuccess: onClose })
          else update.mutate({ id: existing.id, body }, { onSuccess: onClose })
        }}
      >
        <label className="field field--inline">
          <span className="field__label">Reference</span>
          <input className="input input--level" value={reference} onChange={(event) => setReference(event.target.value)} placeholder="NC-1" />
        </label>
        <label className="field field--inline">
          <span className="field__label">Severity</span>
          <select className="input" value={severity} onChange={(event) => setSeverity(event.target.value)}>
            <option value="major">Major</option>
            <option value="minor">Minor</option>
            <option value="observation">Observation</option>
          </select>
        </label>
        <label className="field field--inline field--grow">
          <span className="field__label">What was found</span>
          <textarea className="input" rows={3} value={description} onChange={(event) => setDescription(event.target.value)} />
        </label>
        <label className="field field--inline field--grow">
          <span className="field__label">Corrective action</span>
          <textarea className="input" rows={2} value={correctiveAction} onChange={(event) => setCorrectiveAction(event.target.value)} />
        </label>
        <label className="field field--inline">
          <span className="field__label">Owner</span>
          <input className="input" value={owner} onChange={(event) => setOwner(event.target.value)} />
        </label>
        <label className="field field--inline">
          <span className="field__label">Due</span>
          <input className="input" type="date" value={dueOn} onChange={(event) => setDueOn(event.target.value)} />
        </label>
        <label className="field field--inline">
          <span className="field__label">Closed on</span>
          <input className="input" type="date" value={closedOn} onChange={(event) => setClosedOn(event.target.value)} />
        </label>
        <div className="editor__actions">
          <button type="submit" className="button button--primary" disabled={pending || description.trim() === ''}>
            {pending ? 'Saving…' : 'Save'}
          </button>
          {existing !== undefined && existing.closedOn === null && (
            <button type="button" className="button" disabled={pending} onClick={() => update.mutate({ id: existing.id, body: { ...body, closedOn: today } }, { onSuccess: onClose })}>
              Close it today
            </button>
          )}
          {existing !== undefined && (
            <button type="button" className="button button--quiet" disabled={pending} onClick={() => remove.mutate(existing.id, { onSuccess: onClose })}>
              Remove
            </button>
          )}
          <button type="button" className="button" onClick={onClose}>
            Cancel
          </button>
        </div>
        {(add.error ?? update.error ?? remove.error) !== null && <p className="editor__error">{errorText(add.error ?? update.error ?? remove.error)}</p>}
      </form>
    </Modal>
  )
}

// ---------------------------------------------------------------------------
// AMSA requirements
// ---------------------------------------------------------------------------

const REG_STATUSES = [
  { id: 'met', label: 'Met', tone: 'good' },
  { id: 'not_met', label: 'Not met', tone: 'critical' },
  { id: 'unknown', label: 'Not yet known', tone: 'caution' },
  { id: 'not_applicable', label: 'Not applicable', tone: 'muted' },
] as const

function AmsaTab({ ship }: { ship: Partnership }): React.ReactNode {
  const today = useToday()
  const items = useRegulatory(ship.abbrev)
  const addStandard = useAddStandardRegulatory()
  const canEdit = useHasRole('compliance_lead', 'system_administrator')
  const [editing, setEditing] = useState<RegulatoryItem | 'new' | null>(null)

  if (items.isPending) return <Spinner label="Loading the requirements" />
  if (items.error !== null) return <ErrorPanel title="Could not load the requirements" error={items.error} />

  const notMet = items.data.filter((i) => i.status === 'not_met')
  const unknown = items.data.filter((i) => i.status === 'unknown')
  const dueSoon = items.data.filter((i) => i.nextDue !== null && i.status !== 'not_applicable' && daysBetween(today, i.nextDue) <= 60)
  const regime = ship.regime === 'international' ? 'international (STCW / SOLAS)' : ship.regime === 'domestic' ? 'domestic commercial vessel (National Law)' : 'not yet chosen'

  return (
    <div className="swing-page">
      <section className={`section fold fold--${notMet.length > 0 ? 'critical' : unknown.length > 0 ? 'accent' : items.data.length === 0 ? 'accent' : 'good'}`}>
        <div className="section__header">
          <div>
            <h2 className="section__title">
              {items.data.length === 0
                ? 'No requirements listed yet.'
                : notMet.length > 0
                  ? `${notMet.length} ${notMet.length === 1 ? 'requirement is' : 'requirements are'} not met.`
                  : unknown.length > 0
                    ? `${unknown.length} ${unknown.length === 1 ? 'requirement has' : 'requirements have'} not been checked yet.`
                    : 'Every listed requirement is met.'}
            </h2>
            <p className="section__note">
              <Copy k="ops.amsa-note">
                What the vessel answers to and where each item stands — met, not met, not yet known — with the evidence that says so and when it next falls due. Start from the standard list for the vessel's regime and edit it to the vessel.
              </Copy>{' '}
              This ship: <strong>{ship.registry === 'international' ? 'internationally registered' : ship.registry === 'australian' ? 'Australian registered' : 'registration not recorded'}</strong>, manning regime <strong>{regime}</strong>.
            </p>
          </div>
          <span className="row-actions">
            {canEdit && (
              <>
                <button type="button" className="button" disabled={addStandard.isPending} onClick={() => addStandard.mutate(ship.abbrev)}>
                  {addStandard.isPending ? 'Adding…' : ship.regime === 'international' ? 'Add the international list' : 'Add the National Law list'}
                </button>
                <button type="button" className="button button--primary" onClick={() => setEditing('new')}>
                  Add a requirement
                </button>
              </>
            )}
          </span>
        </div>
        {dueSoon.length > 0 && (
          <p className="muted">
            Falling due inside 60 days: {dueSoon.map((i) => `${i.title} (${formatDayMonth(i.nextDue as string)})`).join(' · ')}
          </p>
        )}
      </section>

      <div className="table-block table-block--plain">
        <table className="table">
          <thead>
            <tr>
              <th scope="col">Reference</th>
              <th scope="col">Requirement</th>
              <th scope="col">Kind</th>
              <th scope="col">Status</th>
              <th scope="col">Evidence</th>
              <th scope="col">Next due</th>
              <th scope="col">Responsible</th>
              {canEdit && <th scope="col" />}
            </tr>
          </thead>
          <tbody>
            {items.data.length === 0 && (
              <tr>
                <td colSpan={8} className="empty">
                  Nothing listed. Add the standard list to start.
                </td>
              </tr>
            )}
            {items.data.map((i) => {
              const st = REG_STATUSES.find((x) => x.id === i.status)
              const late = i.nextDue !== null && i.status !== 'not_applicable' && epochDay(i.nextDue) < epochDay(today)
              return (
                <tr key={i.id}>
                  <td className="mono">{i.reference}</td>
                  <td className="table__wrap">
                    {i.title}
                    {i.note !== null && <span className="meta"> · {i.note}</span>}
                  </td>
                  <td>{i.kind}</td>
                  <td>
                    <span className={`chip chip--${st?.tone ?? 'muted'} chip--small`}>{st?.label ?? i.status}</span>
                  </td>
                  <td className="table__wrap muted">{i.evidence ?? '—'}</td>
                  <td>{i.nextDue === null ? <span className="dim">—</span> : <span className={late ? 'chip chip--critical chip--small' : ''}>{formatDate(i.nextDue)}</span>}</td>
                  <td>{i.responsible ?? <span className="dim">—</span>}</td>
                  {canEdit && (
                    <td>
                      <button type="button" className="link-action" onClick={() => setEditing(i)}>
                        Update
                      </button>
                    </td>
                  )}
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
      {addStandard.error !== null && <p className="editor__error">{errorText(addStandard.error)}</p>}
      {editing !== null && <RegulatoryForm ship={ship} {...(editing === 'new' ? {} : { existing: editing })} onClose={() => setEditing(null)} />}
    </div>
  )
}

function RegulatoryForm({ ship, existing, onClose }: { ship: Partnership; existing?: RegulatoryItem; onClose: () => void }): React.ReactNode {
  const add = useAddRegulatory()
  const update = useUpdateRegulatory()
  const remove = useRemoveRegulatory()
  const [reference, setReference] = useState(existing?.reference ?? '')
  const [title, setTitle] = useState(existing?.title ?? '')
  const [kind, setKind] = useState(existing?.kind ?? 'record')
  const [status, setStatus] = useState(existing?.status ?? 'unknown')
  const [evidence, setEvidence] = useState(existing?.evidence ?? '')
  const [nextDue, setNextDue] = useState(existing?.nextDue ?? '')
  const [responsible, setResponsible] = useState(existing?.responsible ?? '')
  const [note, setNote] = useState(existing?.note ?? '')
  const pending = add.isPending || update.isPending || remove.isPending
  const body: SaveRegulatoryItemRequest = {
    reference,
    title,
    kind,
    status,
    evidence: evidence === '' ? null : evidence,
    nextDue: nextDue === '' ? null : nextDue,
    responsible: responsible === '' ? null : responsible,
    note: note === '' ? null : note,
  }
  return (
    <Modal title={existing === undefined ? 'Add a requirement' : existing.title} onClose={onClose}>
      <form
        className="editor"
        onSubmit={(event) => {
          event.preventDefault()
          if (existing === undefined) add.mutate({ partnership: ship.abbrev, body }, { onSuccess: onClose })
          else update.mutate({ id: existing.id, body }, { onSuccess: onClose })
        }}
      >
        <label className="field field--inline">
          <span className="field__label">Reference</span>
          <input className="input" value={reference} onChange={(event) => setReference(event.target.value)} placeholder="Marine Order 504" />
        </label>
        <label className="field field--inline field--grow">
          <span className="field__label">Requirement</span>
          <input className="input" value={title} onChange={(event) => setTitle(event.target.value)} />
        </label>
        <label className="field field--inline">
          <span className="field__label">Kind</span>
          <select className="input" value={kind} onChange={(event) => setKind(event.target.value)}>
            {['certificate', 'system', 'record', 'survey', 'other'].map((k) => (
              <option key={k} value={k}>
                {k[0]?.toUpperCase()}{k.slice(1)}
              </option>
            ))}
          </select>
        </label>
        <div className="kind-choice" role="radiogroup" aria-label="Status">
          {REG_STATUSES.map((s) => (
            <button key={s.id} type="button" className={status === s.id ? 'kind-choice__option kind-choice__option--on' : 'kind-choice__option'} onClick={() => setStatus(s.id)}>
              <strong>{s.label}</strong>
            </button>
          ))}
        </div>
        <label className="field field--inline field--grow">
          <span className="field__label">Evidence</span>
          <input className="input" value={evidence} onChange={(event) => setEvidence(event.target.value)} placeholder="Certificate no., where it is filed, who checked" />
        </label>
        <label className="field field--inline">
          <span className="field__label">Next due</span>
          <input className="input" type="date" value={nextDue} onChange={(event) => setNextDue(event.target.value)} />
        </label>
        <label className="field field--inline">
          <span className="field__label">Responsible</span>
          <input className="input" value={responsible} onChange={(event) => setResponsible(event.target.value)} />
        </label>
        <label className="field field--inline field--grow">
          <span className="field__label">Note</span>
          <input className="input" value={note} onChange={(event) => setNote(event.target.value)} />
        </label>
        <div className="editor__actions">
          <button type="submit" className="button button--primary" disabled={pending || reference.trim() === '' || title.trim() === ''}>
            {pending ? 'Saving…' : 'Save'}
          </button>
          {existing !== undefined && (
            <button type="button" className="button button--quiet" disabled={pending} onClick={() => remove.mutate(existing.id, { onSuccess: onClose })}>
              Remove
            </button>
          )}
          <button type="button" className="button" onClick={onClose}>
            Cancel
          </button>
        </div>
        {(add.error ?? update.error ?? remove.error) !== null && <p className="editor__error">{errorText(add.error ?? update.error ?? remove.error)}</p>}
      </form>
    </Modal>
  )
}
