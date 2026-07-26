import { NavLink, Outlet } from 'react-router-dom'
import { useSession } from '../api/session'
import { useNotificationSummary } from '../api/queries'
import { writeDevIdentity } from '../api/client'
import { formatDate } from '../domain/dates'
import { roleLabel } from '../domain/enums'

/**
 * The application shell.
 *
 * Every ADM module from §6 appears in the navigation, including the ones with no backend behind
 * them yet — they route to a page that says so. Hiding unbuilt modules would make the app look
 * finished and leave the gap invisible; naming them keeps the remaining scope on screen.
 */

interface NavItem {
  readonly to: string
  readonly label: string
  readonly module: string
  readonly built: boolean
}

export const NAV_ITEMS: readonly NavItem[] = [
  { to: '/', label: 'Dashboard', module: 'ADM-1', built: true },
  { to: '/planner', label: 'Swing planner', module: 'ADM-2', built: true },
  { to: '/people', label: 'People & holdings', module: 'ADM-5', built: true },
  { to: '/matrix', label: 'Matrix', module: 'ADM-3', built: true },
  { to: '/register', label: 'Register', module: 'ADM-4', built: true },
  { to: '/requirements', label: 'Requirements', module: 'ADM-6', built: true },
  { to: '/exceptions', label: 'Exceptions', module: 'ADM-7', built: true },
  { to: '/notifications', label: 'Notifications', module: 'ADM-8', built: true },
  { to: '/evidence', label: 'Evidence queue', module: 'ADM-9', built: true },
  { to: '/administration', label: 'Administration', module: 'ADM-10', built: true },
]

export function Layout(): React.ReactNode {
  const session = useSession()

  return (
    <div className="shell">
      <header className="shell__header">
        <div className="shell__brand">
          Crewcomp
          <span className="shell__env">admin</span>
        </div>

        <div className="shell__session">
          <UnreadBadge />
          <span className="shell__today" title="The server's business date in the operating timezone">
            {formatDate(session.today)}
          </span>
          {session.dateOverridden && <span className="shell__pinned">Date pinned</span>}
          <span className="shell__roles">{session.roles.map(roleLabel).join(' · ')}</span>
          <span className="shell__actor">{session.label}</span>
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
            §6 exists, without reading the marker on every row. */}
        <nav className="shell__nav" aria-label="Modules">
          <NavGroup label="Built" items={NAV_ITEMS.filter((item) => item.built)} />
          <NavGroup label="Not built" items={NAV_ITEMS.filter((item) => !item.built)} later />
        </nav>

        <main className="shell__main">
          <Outlet />
        </main>
      </div>
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
          <i className="nav__mark" aria-hidden="true" />
          <span className="nav__label">{item.label}</span>
          <span className="nav__module">{item.module}</span>
        </NavLink>
      ))}
    </>
  )
}
