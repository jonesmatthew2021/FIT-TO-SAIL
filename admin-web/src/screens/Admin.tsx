import { useSearchParams } from 'react-router-dom'
import { useAllPartnerships, useCustomerScope } from '../api/queries'
import { CertificationChecker } from '../components/CertificationChecker'
import { ELearningStatus } from '../components/ELearningStatus'
import { ErrorPanel } from '../components/ErrorPanel'
import { RequiredDocuments } from '../components/RequiredDocuments'
import { ShipBar } from '../components/ShipBar'
import { Spinner } from '../components/Spinner'
import { TodayScreen } from '../components/TodayScreen'

/**
 * The portal's Admin tab set, per ship — the template every ship carries.
 *
 * Four tabs, each on the ship in scope: Today, Required documents for upload, Certification
 * checker, E-learning status. Swing compliance lives on the Swing planner (ADM-2) beside the slot
 * planner; the portal's OPMS and AI checkers were left behind on purpose — Ask AI (top right of
 * every screen) is the question box here.
 */
const TABS = [
  { id: 'today', label: 'Today' },
  { id: 'required-docs', label: 'Required documents for upload' },
  { id: 'checker', label: 'Certification checker' },
  { id: 'elearning', label: 'E-learning status' },
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
    </div>
  )
}
