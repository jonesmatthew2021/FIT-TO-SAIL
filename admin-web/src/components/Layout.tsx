import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useSession } from '../api/session'
import {
  rememberScope,
  useAllPartnerships,
  useCrewRequestSummary,
  useCustomerScope,
  useCustomers,
  useNotificationSummary,
  useVessels,
} from '../api/queries'
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
  /** Which rail group the item sits in. Compliance is §6's ten modules; Company sits above it. */
  readonly group?: 'company' | 'compliance'
}

/**
 * The rail's order is the *working* order, not the numeric one: the dashboard and the planner are
 * the daily loop, People & holdings is where the loop's answers are corrected, and the records the
 * loop links to follow. ADM-5 therefore sits above ADM-3 and ADM-4.
 */
export const NAV_ITEMS: readonly NavItem[] = [
  // The company page (COM-1) is reached from the customer box's "Manage customers…" option, not
  // from a rail link — the box is the whole of the Company group.
  { to: '/company', label: 'Company', module: 'COM-1', built: true, group: 'company' },
  // The portal's Admin tab set, per ship — the office's template. First, because it is where the
  // office works; the §6 modules follow.
  { to: '/admin', label: 'Admin', module: 'POR-1', built: true },
  { to: '/', label: 'Dashboard', module: 'ADM-1', built: true },
  { to: '/people', label: 'People & holdings', module: 'ADM-5', built: true },
  { to: '/planner', label: 'Crew and shift distribution', module: 'ADM-2', built: true },
  { to: '/swing-compliance', label: 'Swing and shift compliance', module: 'POR-2', built: true },
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
          FIT TO SAIL
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
          <CompanyGroup />
          <ComplianceGroup />
          <NavGroup label="Not built" items={NAV_ITEMS.filter((item) => !item.built)} later />
        </nav>

        <main className="shell__main">
          <ShipGate />
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

/**
 * The Company group: the customer box, and the company page. Picking a customer puts
 * `?customer=<id>` on the current page — the whole of the scoping mechanism, see
 * `useCustomerScope` — and the Compliance group below becomes that customer's copy of the menu.
 * "All customers" is the every-customer view.
 */
/** Select-option sentinel for the manage-customers page — never a customer id. */
const MANAGE = '__manage__'

function CompanyGroup(): React.ReactNode {
  const customers = useCustomers()
  const scope = useCustomerScope()
  const location = useLocation()
  const navigate = useNavigate()

  return (
    <>
      <p className="nav__group">Company</p>
      <select
        className="input nav__select"
        aria-label="Customer"
        value={scope.customerId ?? ''}
        onChange={(event) => {
          const value = event.target.value
          // The last option is the page where customers are added, edited and removed.
          if (value === MANAGE) {
            void navigate(`/company${scope.suffix}`)
            return
          }
          // Otherwise stay on the page; change whose it is. "All customers" is the one thing that
          // clears the remembered scope — every link in the app otherwise keeps it.
          if (value === '') {
            rememberScope(null, null)
            void navigate(location.pathname)
            return
          }
          rememberScope(Number(value), null)
          void navigate(`${location.pathname}?customer=${value}`)
        }}
      >
        <option value="">All customers</option>
        {(customers.data ?? []).map((customer) => (
          <option key={customer.id} value={customer.id}>
            {customer.shortName ?? customer.name}
          </option>
        ))}
        <option value={MANAGE}>Manage customers…</option>
      </select>
      {scope.customer !== null && <ShipBox customerId={scope.customer.id} partnershipIds={scope.customer.partnershipIds} />}
    </>
  )
}

/**
 * The ship box, under the customer box: that customer's operations, named by the vessels on
 * them. Choosing one is what gives the ship its own copy of the compliance menu below.
 */
function ShipBox({ customerId, partnershipIds }: { customerId: number; partnershipIds: readonly number[] }): React.ReactNode {
  const partnerships = useAllPartnerships()
  const vessels = useVessels()
  const scope = useCustomerScope()
  const location = useLocation()
  const navigate = useNavigate()
  const ships = (partnerships.data ?? []).filter((partnership) => partnershipIds.includes(partnership.id))

  return (
    <select
      className="input nav__select"
      aria-label="Ship"
      value={scope.operationId ?? ''}
      onChange={(event) => {
        const value = event.target.value
        rememberScope(customerId, value === '' ? null : Number(value))
        void navigate(
          value === ''
            ? `${location.pathname}?customer=${customerId}`
            : `${location.pathname}?customer=${customerId}&operation=${value}`,
        )
      }}
    >
      <option value="">All ships</option>
      {ships.map((partnership) => (
        <option key={partnership.id} value={partnership.id}>
          {shipLabel(partnership.id, partnership.abbrev, partnership.name, vessels.data ?? [])}
        </option>
      ))}
    </select>
  )
}

/** "TSV Coolibah" where the operation has vessels; its own name where it has none yet. */
export function shipLabel(
  partnershipId: number,
  abbrev: string,
  name: string,
  vessels: readonly { partnershipId: number; name: string }[],
): string {
  const own = vessels.filter((vessel) => vessel.partnershipId === partnershipId).map((vessel) => vessel.name)
  return own.length > 0 ? `${own.join(' + ')} (${abbrev})` : `${name} (${abbrev})`
}

/**
 * The compliance menu — the ship's, and only once a ship is chosen. Every screen on it is a view
 * over one ship's roster, swings and records; with no ship there is nothing for them to show, and
 * a menu that led to empty screens would read as a fault rather than as a question.
 */
function ComplianceGroup(): React.ReactNode {
  const scope = useCustomerScope()
  const partnerships = useAllPartnerships()
  const vessels = useVessels()
  const ship = scope.operationId === null ? undefined : partnerships.data?.find((p) => p.id === scope.operationId)
  if (ship === undefined) return null
  return (
    <NavGroup
      label={`Compliance · ${shipLabel(ship.id, ship.abbrev, ship.name, vessels.data ?? [])}`}
      items={NAV_ITEMS.filter((item) => item.built && item.group !== 'company')}
      suffix={scope.suffix}
    />
  )
}

/**
 * The gate in front of the compliance screens: with no ship chosen, the page asks for one rather
 * than rendering a screen with nothing in it. The company page is the exception — it is where
 * customers and ships are made, so it must be reachable before any exist.
 */
function ShipGate(): React.ReactNode {
  const scope = useCustomerScope()
  const location = useLocation()
  const customers = useCustomers()
  const partnerships = useAllPartnerships()
  const vessels = useVessels()

  if (scope.operationId !== null || location.pathname === '/company') return <Outlet />

  const list = customers.data ?? []
  return (
    <div className="screen">
      <header className="screen__header">
        <h1 className="screen__title">Choose a ship</h1>
        <p className="screen__subtitle">
          Every compliance screen belongs to one ship — its crew, its swings, its certificates. Pick
          the customer and then the ship in the rail, or straight from the list here.
        </p>
      </header>
      {list.length === 0 && (
        <p className="empty">
          No customers yet. <NavLink to="/company">Add one</NavLink>, then add its ships.
        </p>
      )}
      {list.map((customer) => {
        const ships = (partnerships.data ?? []).filter((p) => customer.partnershipIds.includes(p.id))
        return (
          <section key={customer.id} className="panel">
            <p className="panel__title">{customer.name}</p>
            {ships.length === 0 && (
              <p className="panel__detail">
                No ships yet — <NavLink to={`/company?customer=${customer.id}`}>add one</NavLink>.
              </p>
            )}
            {ships.length > 0 && (
              <ul className="list-plain list-plain--tight">
                {ships.map((ship) => (
                  <li key={ship.id}>
                    <NavLink to={`/admin?customer=${customer.id}&operation=${ship.id}`}>
                      {shipLabel(ship.id, ship.abbrev, ship.name, vessels.data ?? [])}
                    </NavLink>
                  </li>
                ))}
              </ul>
            )}
          </section>
        )
      })}
    </div>
  )
}

function NavGroup({
  label,
  items,
  later = false,
  suffix = '',
}: {
  label: string
  items: readonly NavItem[]
  later?: boolean
  /** `?customer=<id>` when a customer is in the box, so the links keep the scope. */
  suffix?: string
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
          to={`${item.to}${suffix}`}
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
