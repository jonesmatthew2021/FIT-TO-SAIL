import { useState } from 'react'
import { Link } from 'react-router-dom'
import {
  useBringOnboard,
  usePeople,
  useSendAshore,
  useSetActive,
  useSetRotation,
  useSetWatch,
  useSlots,
  useSwitchCrew,
} from '../api/queries'
import { ApiError, type CrewChange, type Partnership, type Person, type SwingEvaluation } from '../api/client'
import { useHasRole } from '../api/session'
import { NewPersonForm } from './CertificatesOnFile'
import { Modal } from './Modal'
import { StateChip } from './StateChip'
import { RANK_GROUPS, rankGroup } from '../domain/ranks'
import { formatDate } from '../domain/dates'

/** The roles the server accepts for an assignment write — mirrored to hide the controls. */
const ROSTER_EDITORS = ['crew_coordinator', 'system_administrator'] as const

type Watch = 'day' | 'night' | 'none'

/** Somebody on the swing: their legs on it, and the watch their slot keeps. */
interface Onboard {
  personId: number
  name: string
  sam: string
  positionName: string
  rotation: string | null
  watch: Watch
  rollUp: string
}

/**
 * The swing roster — the Coolibah portal's page of the same name, under the swing picked on the
 * cards above.
 *
 * Two columns: who is on this swing, with the watch each keeps and a way off it; and who is not,
 * with a way on. Every move is an assignment write on the server (`SwingRosterService`): bringing
 * somebody onboard fills a slot for their position, a watch moves them to a slot on that shift,
 * sending them ashore clears theirs. The slot planner beside this shows the same roster the other
 * way round — by slot rather than by person.
 *
 * The board is the swing's own. Anyone the pattern rosters and anyone brought on by hand look the
 * same here; "who follows the rotation" is the Swing Alpha and Bravo view, and the crew change —
 * the swing carrying the other crew — is Switch swings.
 */
export function SwingRoster({
  ship,
  swing,
  evaluation,
}: {
  ship: Partnership
  swing: CrewChange
  evaluation: SwingEvaluation | undefined
}): React.ReactNode {
  const canEdit = useHasRole(...ROSTER_EDITORS)
  const people = usePeople()
  const slots = useSlots()
  const [view, setView] = useState<'board' | 'lists' | 'switch'>('board')
  const [adding, setAdding] = useState(false)
  const bring = useBringOnboard()

  const crew = (people.data ?? []).filter((p) => p.partnershipId === ship.id && p.status === 'active')
  const gone = (people.data ?? []).filter((p) => p.partnershipId === ship.id && p.status === 'inactive')
  const byId = new Map((people.data ?? []).map((p) => [p.id, p]))
  const shiftOf = new Map((slots.data ?? []).map((s) => [s.ref, s.shift]))
  const watchOf = (slotRef: number): Watch => {
    const shift = (shiftOf.get(slotRef) ?? '').trim().toLowerCase()
    return shift === 'shift 1' ? 'day' : shift === 'shift 2' ? 'night' : 'none'
  }

  // One row per person, whatever legs they hold; the first leg's slot says the watch.
  const onboard: Onboard[] = []
  for (const a of evaluation?.assignments ?? []) {
    if (onboard.some((row) => row.personId === a.personId)) continue
    const person = byId.get(a.personId)
    onboard.push({
      personId: a.personId,
      name: a.name,
      sam: a.sam,
      positionName: person?.positionName ?? '',
      rotation: person?.rotation ?? null,
      watch: watchOf(a.slotRef),
      rollUp: a.evaluation.rollUp,
    })
  }
  const onboardIds = new Set(onboard.map((row) => row.personId))
  const off = crew.filter((p) => !onboardIds.has(p.id))

  const onDay = onboard.filter((r) => r.watch === 'day').length
  const onNight = onboard.filter((r) => r.watch === 'night').length
  const noWatch = onboard.length - onDay - onNight

  const views: { id: 'board' | 'lists' | 'switch'; label: string }[] = [
    { id: 'board', label: 'Onboard and off swing' },
    { id: 'lists', label: 'Swing Alpha and Bravo' },
    ...(canEdit ? [{ id: 'switch' as const, label: 'Switch swings' }] : []),
  ]

  return (
    <section className="section fold fold--accent">
      <div className="section__header">
        <div>
          <p className="nav__group swing-eyebrow">Roster · {onboard.length} onboard {swingLabelShort(swing)} · move crew on and off, set watches, switch swings</p>
          <h2 className="section__title">Swing roster</h2>
        </div>
        <span className="mono muted">
          {onboard.length} onboard · {off.length} off swing
        </span>
      </div>

      <nav className="tabs tabs--inner" aria-label="Roster views">
        {views.map((v) => (
          <button
            key={v.id}
            type="button"
            className={v.id === view ? 'tabs__tab tabs__tab--active' : 'tabs__tab'}
            aria-current={v.id === view}
            onClick={() => setView(v.id)}
          >
            {v.label}
          </button>
        ))}
      </nav>

      {view === 'board' && (
        <>
          <div className="row-actions roster-toolbar">
            {canEdit && (
              <button type="button" className="button button--primary" onClick={() => setAdding(true)}>
                Add crew member
              </button>
            )}
            <span className="muted">
              {onDay} on days · {onNight} on nights{noWatch > 0 && ` · ${noWatch} without a watch`}
            </span>
          </div>

          {evaluation === undefined && <p className="muted">Evaluating the swing…</p>}

          <div className="roster-columns">
            <RosterColumn
              title="Onboard swing"
              tone="good"
              count={onboard.length}
              blurb={canEdit ? 'On this swing. Set each one to days or nights, or send them to the off swing.' : 'On this swing.'}
              empty="Nobody is on this swing yet."
              rows={onboard.map((row) => ({ personId: row.personId, name: row.name, positionName: row.positionName, rotation: row.rotation }))}
              render={(row) => (
                <OnboardRow ship={ship} swing={swing} row={onboard.find((r) => r.personId === row.personId) as Onboard} canEdit={canEdit} />
              )}
            />
            <RosterColumn
              title="Off swing"
              tone="muted"
              count={off.length}
              blurb={canEdit ? 'Ashore. Bring anyone onboard who has come out early or is filling in.' : 'Ashore.'}
              empty="Nobody."
              rows={off.map((p) => ({ personId: p.id, name: p.name, positionName: p.positionName, rotation: p.rotation }))}
              render={(row) => <OffRow ship={ship} swing={swing} person={byId.get(row.personId) as Person} canEdit={canEdit} />}
            />
          </div>

          {gone.length > 0 && <GoneLine gone={gone} canEdit={canEdit} />}
        </>
      )}

      {view === 'lists' && <CrewLists crew={crew} canEdit={canEdit} />}

      {view === 'switch' && canEdit && (
        <SwitchSwings ship={ship} swing={swing} onboard={onboard} crew={crew} onDone={() => setView('board')} />
      )}

      {adding && (
        <Modal title={`Add a crew member to ${ship.abbrev}`} onClose={() => setAdding(false)}>
          <NewPersonForm
            suggestedName={null}
            shipId={ship.id}
            note={`Added to this ship's crew and to People & holdings, and brought onto ${swingLabelShort(swing)} straight away.`}
            onCreated={(person) => {
              setAdding(false)
              // Somebody added while a swing is on screen was added for that swing.
              bring.mutate({ partnership: ship.abbrev, cc: swing.ccId, personId: person.id, acknowledgeClash: true })
            }}
            onCancel={() => setAdding(false)}
          />
        </Modal>
      )}
    </section>
  )
}

function swingLabelShort(swing: CrewChange): string {
  return `${formatDate(swing.from)} – ${formatDate(swing.to)}`
}

// ---------------------------------------------------------------------------
// A column, grouped by rank
// ---------------------------------------------------------------------------

interface RosterLine {
  personId: number
  name: string
  positionName: string
  rotation: string | null
}

function RosterColumn({
  title,
  tone,
  count,
  blurb,
  empty,
  rows,
  render,
}: {
  title: string
  tone: 'good' | 'muted'
  count: number
  blurb: string
  empty: string
  rows: RosterLine[]
  render: (row: RosterLine) => React.ReactNode
}): React.ReactNode {
  const order = [...RANK_GROUPS.map((g) => g.label), 'Other positions']
  const groups = order
    .map((label) => ({ label, rows: rows.filter((row) => rankGroup(row.positionName) === label) }))
    .filter((group) => group.rows.length > 0)

  return (
    <div className={`roster-col roster-col--${tone}`}>
      <div className="roster-col__head">
        <span className={`nav__group swing-eyebrow roster-col__title roster-col__title--${tone}`}>{title}</span>
        <span className="mono muted">{count}</span>
      </div>
      <p className="muted roster-col__blurb">{blurb}</p>
      {groups.length === 0 && <p className="empty">{empty}</p>}
      {groups.map((group) => (
        <div key={group.label} className="roster-group">
          <p className="nav__group swing-eyebrow">{group.label}</p>
          {group.rows.map((row) => (
            <div key={row.personId} className="roster-row">
              {render(row)}
            </div>
          ))}
        </div>
      ))}
    </div>
  )
}

function NameCell({ personId, name, rotation }: { personId: number; name: string; rotation: string | null }): React.ReactNode {
  return (
    <span className="roster-row__who">
      <Link to={`/people/${personId}`} className="roster-row__name">
        {name}
      </Link>
      <span className="mono muted roster-row__crew">{rotation ?? '—'}</span>
    </span>
  )
}

function OnboardRow({ ship, swing, row, canEdit }: { ship: Partnership; swing: CrewChange; row: Onboard; canEdit: boolean }): React.ReactNode {
  const setWatch = useSetWatch()
  const ashore = useSendAshore()
  const choose = (watch: Watch) =>
    setWatch.mutate({ partnership: ship.abbrev, cc: swing.ccId, personId: row.personId, watch: row.watch === watch ? 'none' : watch })
  const busy = setWatch.isPending || ashore.isPending
  return (
    <>
      <NameCell personId={row.personId} name={row.name} rotation={row.rotation} />
      <StateChip state={row.rollUp} />
      <span className="roster-row__watch">
        <button
          type="button"
          className={row.watch === 'day' ? 'watch-btn watch-btn--day watch-btn--on' : 'watch-btn'}
          disabled={!canEdit || busy}
          title={canEdit ? `Put ${row.name} on the day shift` : ''}
          onClick={() => choose('day')}
        >
          Day
        </button>
        <button
          type="button"
          className={row.watch === 'night' ? 'watch-btn watch-btn--night watch-btn--on' : 'watch-btn'}
          disabled={!canEdit || busy}
          title={canEdit ? `Put ${row.name} on the night shift` : ''}
          onClick={() => choose('night')}
        >
          Night
        </button>
      </span>
      {canEdit && (
        <button
          type="button"
          className="button button--quiet"
          disabled={busy}
          title={`Send ${row.name} to the off swing`}
          onClick={() => ashore.mutate({ partnership: ship.abbrev, cc: swing.ccId, personId: row.personId })}
        >
          {ashore.isPending ? 'Sending…' : 'Send ashore'}
        </button>
      )}
      {canEdit && <RemoveButton personId={row.personId} name={row.name} />}
      {setWatch.error !== null && <span className="editor__error roster-row__error">{errorText(setWatch.error)}</span>}
      {ashore.error !== null && <span className="editor__error roster-row__error">{errorText(ashore.error)}</span>}
    </>
  )
}

/**
 * Off the crew altogether — the portal's "Remove". Asks first: it takes the person off every
 * swing still to sail and tells their phone. Nothing recorded about them is lost, and the line
 * under the board brings them back.
 */
function RemoveButton({ personId, name }: { personId: number; name: string }): React.ReactNode {
  const setActive = useSetActive()
  const [confirming, setConfirming] = useState(false)
  if (!confirming) {
    return (
      <button type="button" className="button button--quiet roster-row__remove" title={`Take ${name} off the crew`} onClick={() => setConfirming(true)}>
        Remove…
      </button>
    )
  }
  return (
    <span className="row-actions roster-row__error">
      <span className="dim">Takes {name} off the crew and off every swing still to sail. Their records stay.</span>
      <button
        type="button"
        className="button button--quiet"
        disabled={setActive.isPending}
        onClick={() => setActive.mutate({ personId, active: false }, { onSettled: () => setConfirming(false) })}
      >
        {setActive.isPending ? 'Removing…' : 'Remove from crew'}
      </button>
      <button type="button" className="button button--quiet" onClick={() => setConfirming(false)}>
        Keep
      </button>
      {setActive.error !== null && <span className="editor__error">{errorText(setActive.error)}</span>}
    </span>
  )
}

/** Who has been taken off the crew, and the way back on. */
function GoneLine({ gone, canEdit }: { gone: readonly Person[]; canEdit: boolean }): React.ReactNode {
  const setActive = useSetActive()
  return (
    <p className="muted roster-gone">
      Not on the crew at the moment:{' '}
      {gone.map((p, i) => (
        <span key={p.id}>
          {i > 0 && ', '}
          <Link to={`/people/${p.id}`}>{p.name}</Link>
          {canEdit && (
            <>
              {' '}
              <button type="button" className="link-action" disabled={setActive.isPending} onClick={() => setActive.mutate({ personId: p.id, active: true })}>
                add back
              </button>
            </>
          )}
        </span>
      ))}
      .{setActive.error !== null && <span className="editor__error"> {errorText(setActive.error)}</span>}
    </p>
  )
}

/**
 * Off the swing, with the way on. A clash — already assigned elsewhere in the window, or on
 * leave — comes back as a 409 and is shown on the row with "Bring onboard anyway" (§5.4: a clash
 * is shown, never hidden, and the coordinator may still mean it).
 */
function OffRow({ ship, swing, person, canEdit }: { ship: Partnership; swing: CrewChange; person: Person; canEdit: boolean }): React.ReactNode {
  const bring = useBringOnboard()
  const [clash, setClash] = useState<string | null>(null)
  const go = (acknowledgeClash: boolean) => {
    setClash(null)
    bring.mutate(
      { partnership: ship.abbrev, cc: swing.ccId, personId: person.id, acknowledgeClash },
      { onError: (error) => { if (error instanceof ApiError && error.code === 'assignment_clash') setClash(error.message) } },
    )
  }
  return (
    <>
      <NameCell personId={person.id} name={person.name} rotation={person.rotation} />
      {canEdit && (
        <button
          type="button"
          className="button button--quiet"
          disabled={bring.isPending}
          title={`Bring ${person.name} onboard`}
          onClick={() => go(clash !== null)}
        >
          {bring.isPending ? 'Bringing…' : clash !== null ? 'Bring onboard anyway' : 'Bring onboard'}
        </button>
      )}
      {canEdit && <RemoveButton personId={person.id} name={person.name} />}
      {clash !== null && <span className="editor__error roster-row__error">{clash}</span>}
      {bring.error !== null && !(bring.error instanceof ApiError && bring.error.code === 'assignment_clash') && (
        <span className="editor__error roster-row__error">{errorText(bring.error)}</span>
      )}
    </>
  )
}

// ---------------------------------------------------------------------------
// Swing Alpha and Bravo — who follows which rotation
// ---------------------------------------------------------------------------

function CrewLists({ crew, canEdit }: { crew: readonly Person[]; canEdit: boolean }): React.ReactNode {
  const setRotation = useSetRotation()
  const lists: { letter: string | null; title: string; blurb: string }[] = [
    { letter: 'A', title: 'Swing Alpha', blurb: 'Crew A — rostered onto every A swing by the pattern.' },
    { letter: 'B', title: 'Swing Bravo', blurb: 'Crew B — rostered onto every B swing by the pattern.' },
    { letter: null, title: 'Not on a rotation', blurb: 'Fill-ins and shore-based crew. Brought onto a swing by hand.' },
  ]
  const move = (person: Person, rotation: string | null) => setRotation.mutate({ personId: person.id, rotation })
  return (
    <>
      <p className="muted">
        Which crew each person sails with. The pattern rosters crew A onto A swings and crew B onto B swings; moving
        somebody here changes every swing still to be rostered, not one already on the board.
      </p>
      <div className="roster-columns roster-columns--three">
        {lists.map((list) => (
          <RosterColumn
            key={list.letter ?? 'none'}
            title={list.title}
            tone={list.letter === null ? 'muted' : 'good'}
            count={crew.filter((p) => p.rotation === list.letter).length}
            blurb={list.blurb}
            empty="Nobody."
            rows={crew.filter((p) => p.rotation === list.letter).map((p) => ({ personId: p.id, name: p.name, positionName: p.positionName, rotation: p.rotation }))}
            render={(row) => {
              const person = crew.find((p) => p.id === row.personId) as Person
              return (
                <>
                  <NameCell personId={person.id} name={person.name} rotation={null} />
                  {canEdit && (
                    <span className="roster-row__watch">
                      {lists
                        .filter((other) => other.letter !== list.letter)
                        .map((other) => (
                          <button
                            key={other.letter ?? 'none'}
                            type="button"
                            className="watch-btn"
                            disabled={setRotation.isPending}
                            onClick={() => move(person, other.letter)}
                          >
                            {other.letter === null ? 'Off rotation' : `To crew ${other.letter}`}
                          </button>
                        ))}
                    </span>
                  )}
                </>
              )
            }}
          />
        ))}
      </div>
      {setRotation.error !== null && <p className="editor__error">{errorText(setRotation.error)}</p>}
    </>
  )
}

// ---------------------------------------------------------------------------
// Switch swings — the crew change
// ---------------------------------------------------------------------------

function SwitchSwings({
  ship,
  swing,
  onboard,
  crew,
  onDone,
}: {
  ship: Partnership
  swing: CrewChange
  onboard: readonly Onboard[]
  crew: readonly Person[]
  onDone: () => void
}): React.ReactNode {
  const switchCrew = useSwitchCrew()
  const [confirming, setConfirming] = useState(false)
  const was = swing.rotation
  const now = was === 'A' ? 'B' : was === 'B' ? 'A' : null
  const goingAshore = onboard.filter((r) => r.rotation === was && was !== null)
  const comingOn = crew.filter((p) => p.rotation === now && now !== null && !onboard.some((r) => r.personId === p.id))

  if (was === null || now === null) {
    return <p className="empty">This swing is not a crew A or B swing, so there is no other crew to switch to.</p>
  }

  return (
    <div className="panel">
      <p className="panel__title">Switch swings · {swingLabelShort(swing)}</p>
      <p className="panel__detail">
        Crew change. This swing carries crew {was}; press the button and it carries crew {now} instead — crew {was} goes
        to the off swing and crew {now} is rostered on from the rotation. Only people on a rotation are carried across:
        a fill-in or anybody without a crew letter stays where they are and is moved by hand.
      </p>
      <p className="panel__detail">
        Watches are set again from the slots the pattern finds free, so check them on the board afterwards.
      </p>
      <div className="callout callout--quiet">
        <div>
          <strong>Going ashore ({goingAshore.length}):</strong> {goingAshore.length > 0 ? goingAshore.map((r) => r.name).join(', ') : 'nobody'}
        </div>
        <div>
          <strong>Coming onboard ({comingOn.length}):</strong> {comingOn.length > 0 ? comingOn.map((p) => p.name).join(', ') : 'nobody'}
        </div>
      </div>
      <div className="editor__actions">
        {!confirming ? (
          <button type="button" className="button button--primary" onClick={() => setConfirming(true)}>
            Switch the swings
          </button>
        ) : (
          <>
            <span className="dim">Moves {goingAshore.length} ashore and {comingOn.length} onboard, and tells anyone with the app.</span>
            <button
              type="button"
              className="button button--primary"
              disabled={switchCrew.isPending}
              onClick={() => switchCrew.mutate({ partnership: ship.abbrev, cc: swing.ccId }, { onSuccess: onDone })}
            >
              {switchCrew.isPending ? 'Switching…' : 'Yes, switch them'}
            </button>
            <button type="button" className="button" onClick={() => setConfirming(false)}>
              Keep
            </button>
          </>
        )}
      </div>
      {switchCrew.error !== null && <p className="editor__error">{errorText(switchCrew.error)}</p>}
      {switchCrew.data !== undefined && switchCrew.data.unrostered.length > 0 && (
        <ul className="list-plain list-plain--tight">
          {switchCrew.data.unrostered.map((u) => (
            <li key={u.personId}>
              {u.name} <span className="muted">· {u.position} — {u.reason}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function errorText(error: unknown): string {
  return error instanceof ApiError ? error.message : String(error)
}
