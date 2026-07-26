import { NavLink, Outlet } from 'react-router-dom'
import { useSession } from '../api/session'
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
  { to: '/matrix', label: 'Matrix', module: 'ADM-3', built: false },
  { to: '/register', label: 'Register', module: 'ADM-4', built: true },
  { to: '/requirements', label: 'Requirements', module: 'ADM-6', built: true },
  { to: '/exceptions', label: 'Exceptions', module: 'ADM-7', built: true },
  { to: '/notifications', label: 'Notifications', module: 'ADM-8', built: false },
  { to: '/evidence', label: 'Evidence queue', module: 'ADM-9', built: false },
  { to: '/administration', label: 'Administration', module: 'ADM-10', built: false },
]

export function Layout(): React.ReactNode {
  const session = useSession()

  return (
    <div className="shell">
      <header className="shell__header">
        <div className="shell__brand">
          CREWCOMP
          <span className="shell__env">admin</span>
        </div>

        <div className="shell__session">
          <span className="shell__today" title="The server's business date in the operating timezone">
            {formatDate(session.today)}
            {session.dateOverridden && <span className="shell__pinned">pinned</span>}
          </span>
          <span className="shell__actor">{session.label}</span>
          <span className="shell__roles">{session.roles.map(roleLabel).join(' · ')}</span>
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
        <nav className="shell__nav" aria-label="Modules">
          {NAV_ITEMS.map((item) => (
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
              <span className="nav__module">{item.module}</span>
            </NavLink>
          ))}
        </nav>

        <main className="shell__main">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
