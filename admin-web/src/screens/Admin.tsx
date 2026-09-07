import { Link, useSearchParams } from 'react-router-dom'
import { useAllPartnerships, useCustomerScope } from '../api/queries'
import { ErrorPanel } from '../components/ErrorPanel'
import { ShipBar } from '../components/ShipBar'
import { Spinner } from '../components/Spinner'
import { SwingCompliance } from '../components/SwingCompliance'

/**
 * The portal's Admin tab set, per ship — the template every ship carries.
 *
 * The Coolibah portal's office lived on seven tabs: Today, Required documents for upload,
 * Certification checker, E-learning status, Swing compliance, OPMS checker, AI checker. They are
 * brought across here one at a time, each on the ship in scope; a tab not yet ported says so and
 * points at the screen in this app that does its job in the meantime, rather than rendering a
 * copy of the portal that nothing here backs.
 */
const TABS = [
  { id: 'today', label: 'Today' },
  { id: 'required-docs', label: 'Required documents for upload' },
  { id: 'checker', label: 'Certification checker' },
  { id: 'elearning', label: 'E-learning status' },
  { id: 'swing', label: 'Swing compliance' },
  { id: 'opms', label: 'OPMS checker' },
  { id: 'ai', label: 'AI checker' },
] as const

type TabId = (typeof TABS)[number]['id']

export function Admin(): React.ReactNode {
  const scope = useCustomerScope()
  const partnerships = useAllPartnerships()
  const [params, setParams] = useSearchParams()
  const tab = (TABS.find((t) => t.id === params.get('tab'))?.id ?? 'swing') as TabId

  if (partnerships.isPending) return <Spinner label="Loading the ship" />
  if (partnerships.error !== null) return <ErrorPanel title="Could not load the ship" error={partnerships.error} />
  const ship = partnerships.data.find((p) => p.id === scope.operationId)
  if (ship === undefined) return <p className="empty">Choose a ship in the rail.</p>

  return (
    <div className="screen">
      <header className="screen__header screen__header--bar">
        <div>
          <h1 className="screen__title">Admin</h1>
          <p className="screen__subtitle">{ship.name}</p>
        </div>
        <ShipBar />
      </header>

      <nav className="tabs" aria-label="Admin tabs">
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

      {tab === 'swing' && <SwingCompliance ship={ship} />}
      {tab === 'today' && (
        <Pending
          title="Today"
          what="The day's headline for this ship — what sails clean, what does not, what is due."
          where={{ to: `/${scope.suffix}`, label: 'the dashboard' }}
        />
      )}
      {tab === 'required-docs' && (
        <Pending
          title="Required documents for upload"
          what="The spreadsheets and sheets the checks read — skills matrix, validity matrix, training matrix, shift allocation, OPMS export — and which are on file."
          where={{ to: `/people${scope.suffix}`, label: 'People & holdings (upload certificates)' }}
        />
      )}
      {tab === 'checker' && (
        <Pending
          title="Certification checker"
          what="Every certificate on file against the matrix: what is held, what is missing, what disagrees."
          where={{ to: `/matrix${scope.suffix}`, label: 'the crew matrix' }}
        />
      )}
      {tab === 'elearning' && (
        <Pending
          title="E-learning status"
          what="The online modules each crew member has done, from the OPMS export."
          where={{ to: `/matrix${scope.suffix}`, label: 'the crew matrix (Project Induction)' }}
        />
      )}
      {tab === 'opms' && (
        <Pending
          title="OPMS checker"
          what="The latest OPMS export against the matrix — who is at fault where the two disagree."
          where={{ to: `/exceptions${scope.suffix}`, label: 'the exceptions worklist' }}
        />
      )}
      {tab === 'ai' && (
        <Pending
          title="AI checker"
          what="Ask a question of this ship's records, with files attached."
          where={{ to: `/${scope.suffix}`, label: 'Ask AI (top right of every screen)' }}
        />
      )}
    </div>
  )
}

function Pending({ title, what, where }: { title: string; what: string; where: { to: string; label: string } }): React.ReactNode {
  return (
    <div className="panel">
      <p className="panel__title">{title} — being brought across</p>
      <p className="panel__detail">{what}</p>
      <p className="panel__detail">
        Until it lands here, the nearest thing in this app is <Link to={where.to}>{where.label}</Link>.
      </p>
    </div>
  )
}
