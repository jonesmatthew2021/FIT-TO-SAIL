import { Link, useLocation } from 'react-router-dom'
import { NAV_ITEMS } from '../components/Layout'

/**
 * The catch-all route, and the place a module goes when it has no backend behind it yet.
 *
 * All ten §6 modules are now built, so [BLOCKERS] is empty and this page's only ordinary job is to
 * answer an unknown URL. It is kept rather than deleted for two reasons: an unrouted path should say
 * something useful instead of rendering blank, and the mechanism — add a module to `NAV_ITEMS` with
 * `built: false` and it routes here with its blocker named — is what kept the remaining scope visible
 * on screen while the four were outstanding. That was worth having and will be again.
 */
const BLOCKERS: Record<string, string> = {}

export function NotBuilt(): React.ReactNode {
  const location = useLocation()
  const item = NAV_ITEMS.find((candidate) => candidate.to === location.pathname)
  const module = item?.module ?? ''
  const blocker = BLOCKERS[module]

  if (item === undefined || blocker === undefined) {
    return (
      <div className="screen">
        <header className="screen__header">
          <h1 className="screen__title">No such page</h1>
          <p className="screen__subtitle">
            <span className="mono">{location.pathname}</span> is not a route in this application.
          </p>
        </header>

        <div className="panel">
          <p>
            Every §6 module is in the navigation. If you followed a link to get here, it was a link to
            somewhere that no longer exists.
          </p>
          <p>
            <Link to="/">Back to the dashboard</Link>
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="screen">
      <header className="screen__header">
        <h1 className="screen__title">{item.label}</h1>
        <p className="screen__subtitle">{module} — not built yet</p>
      </header>

      <div className="panel">
        <p>{blocker}</p>
      </div>
    </div>
  )
}
