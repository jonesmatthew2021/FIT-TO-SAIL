import { Link, useSearchParams } from 'react-router-dom'
import { useAllPartnerships, useCustomerScope } from '../api/queries'
import { CertificationChecker } from '../components/CertificationChecker'
import { ELearningStatus } from '../components/ELearningStatus'
import { ErrorPanel } from '../components/ErrorPanel'
import { RequiredDocuments } from '../components/RequiredDocuments'
import { ShipBar } from '../components/ShipBar'
import { Spinner } from '../components/Spinner'
import { SwingCompliance } from '../components/SwingCompliance'
import { TodayScreen } from '../components/TodayScreen'

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
  // Today first, as the portal opened: the day's worklist before anything else.
  const tab = (TABS.find((t) => t.id === params.get('tab'))?.id ?? 'today') as TabId

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
        <TodayScreen
          ship={ship}
          go={(target) => {
            const next = new URLSearchParams(params)
            next.set('tab', target)
            setParams(next)
          }}
        />
      )}
      {tab === 'required-docs' && <RequiredDocuments ship={ship} />}
      {tab === 'checker' && <CertificationChecker />}
      {tab === 'elearning' && <ELearningStatus />}
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
