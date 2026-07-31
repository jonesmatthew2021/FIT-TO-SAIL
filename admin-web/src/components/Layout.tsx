import { NavLink, Outlet } from 'react-router-dom'
import { useSession } from '../api/session'
import { useCrewRequestSummary, useNotificationSummary } from '../api/queries'
import { writeDevIdentity } from '../api/client'
import { formatDate } from '../domain/dates'
import { roleLabel } from '../domain/enums'
import { AssistantPanel, AssistantTrigger, useAssistantPanel } from './AssistantPanel'

/**
 * The application shell — a 52px header and a 236px navigation rail.
 *
 * Every ADM module from §6 appears in the rail, including any with no backend behind them yet —
 * those route to a page that says so. Hiding unbuilt modules would make the app look finished and
 * leave the gap invisible; naming them keeps the remaining scope on screen.
 */

interface NavItem {
  readonly to: string
  readonly label: string
  readonly module: string
  readonly built: boolean
}

/**
 * The rail's order is the *working* order, not the numeric one: the dashboard and the planner are
 * the daily loop, People & holdings is where the loop's answers are corrected, and the records the
 * loop links to follow. ADM-5 therefore sits above ADM-3 and ADM-4.
 */
export const NAV_ITEMS: readonly NavItem[] = [
  { to: '/', label: 'Dashboard', module: 'ADM-1', built: true },
  { to: '/planner', label: 'Swing planner', module: 'ADM-2', built: true },
  { to: '/people', label: 'People & holdings', module: 'ADM-5', built: true },
  { to: '/matrix', label: 'Matrix', module: 'ADM-3', built: true },
  { to: '/register', label: 'Register', module: 'ADM-4', built: true },
  { to: '/requirements', label: 'Requirements', module: 'ADM-6', built: true },
  { to: '/exceptions', label: 'Exceptions', module: 'ADM-7', built: true },
  // Beside the other two queues, and after them: the evidence queue and the exceptions worklist are
  // the daily ones. ADM-11 post-dates §6, which enumerates ADM-1 to ADM-10 — the crew app's one-tap
  // answers had nowhere to land, and a notification is not a worklist.
  { to: '/crew-requests', label: 'Crew requests', module: 'ADM-11', built: true },
  { to: '/notifications', label: 'Notifications', module: 'ADM-8', built: true },
  { to: '/evidence', label: 'Evidence queue', module: 'ADM-9', built: true },
  { to: '/administration', label: 'Administration', module: 'ADM-10', built: true },
]

/**
 * The screen codes beside each label are a review affordance rather than product chrome — they let
 * a reviewer check a screen against the spec by name. One constant so they can go without touching
 * the rail's markup.
 */
const SHOW_SCREEN_CODES = true

export function Layout(): React.ReactNode {
  const session = useSession()
  const assistant = useAssistantPanel()

  return (
    <div className="shell" style={{ '--assistant-push': assistant.pushWidth } as React.CSSProperties}>
      <header className="shell__header">
        <div className="shell__brand">
          Crewcomp
          <span className="shell__env">Admin</span>
        </div>

        <div className="shell__session">
          <UnreadBadge />
          {/*
           * The server's business date in AWST, never the browser's (NFR-5, O-11). The timezone is
           * spelled out beside it for the same reason: a reader in another zone has to know not to
           * reconcile this against their own clock.
           */}
          <span className="shell__today" title="The server's business date in the operating timezone">
            {formatDate(session.today)}
          </span>
          <span className="shell__zone">AWST</span>
          {session.dateOverridden && <span className="shell__pinned">Date pinned</span>}
          <span className="shell__divider" aria-hidden="true" />
          <span className="shell__roles">{session.roles.map(roleLabel).join(' · ')}</span>
          <span className="shell__actor">{session.label}</span>
          <AssistantTrigger state={assistant} />
          {import.meta.env.DEV && (
            <button
              type="button"
              className="button button--quiet"
              onClick={() => {
                writeDevIdentity(null)
                window.location.reload()
              }}
            >
              Switch role
            </button>
          )}
        </div>
      </header>

      <div className="shell__body">
        {/* Grouped rather than interleaved: a reader should be able to see at a glance how much of
            §6 exists, without reading a marker on every row. */}
        <nav className="shell__nav" aria-label="Modules">
          <NavGroup label="Compliance" items={NAV_ITEMS.filter((item) => item.built)} />
          <NavGroup label="Not built" items={NAV_ITEMS.filter((item) => !item.built)} later />
        </nav>

        <main className="shell__main">
          <Outlet />
        </main>
      </div>

      <AssistantPanel state={assistant} />
    </div>
  )
}

/**
 * The unread notification count, linking to ADM-8.
 *
 * Rendered only when there is something unread. A badge showing zero trains people to ignore the
 * badge, and the count is polled rather than pushed — §9's notifications come from scheduled scans
 * and from other people's actions, so there is no local event to react to.
 */
function UnreadBadge(): React.ReactNode {
  const summary = useNotificationSummary()
  const unread = summary.data?.unread ?? 0
  if (unread === 0) return null

  return (
    <NavLink to="/notifications" className="shell__badge" aria-label={`${unread} unread notifications`}>
      {unread}
    </NavLink>
  )
}

function NavGroup({
  label,
  items,
  later = false,
}: {
  label: string
  items: readonly NavItem[]
  later?: boolean
}): React.ReactNode {
  // All ten §6 modules are built, so the "Not built" group is empty — and a heading with nothing
  // under it reads as a rendering fault rather than as good news.
  if (items.length === 0) return null

  return (
    <>
      <p className={later ? 'nav__group nav__group--later' : 'nav__group'}>{label}</p>
      {items.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.to === '/'}
          className={({ isActive }) =>
            ['nav__item', isActive ? 'nav__item--active' : '', item.built ? '' : 'nav__item--unbuilt']
              .filter(Boolean)
              .join(' ')
          }
        >
          <span className="nav__label">{item.label}</span>
          {item.to === '/crew-requests' && <CrewRequestCount />}
          {SHOW_SCREEN_CODES && <span className="nav__module">{item.module}</span>}
        </NavLink>
      ))}
    </>
  )
}

/**
 * How many crew requests are waiting on somebody (#20). The count was computed and rendered
 * nowhere, which made ADM-11 a queue you had to remember to check — the failure mode the module
 * exists to prevent. Same rule as the unread badge: never shown at zero.
 */
function CrewRequestCount(): React.ReactNode {
  const summary = useCrewRequestSummary()
  const open = summary.data?.open ?? 0
  if (open === 0) return null

  return (
    <span className="nav__count" aria-label={`${open} open crew requests`}>
      {open}
    </span>
  )
}
