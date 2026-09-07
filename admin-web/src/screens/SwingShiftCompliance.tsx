import { useAllPartnerships, useCustomerScope } from '../api/queries'
import { ErrorPanel } from '../components/ErrorPanel'
import { Spinner } from '../components/Spinner'
import { SwingCompliance } from '../components/SwingCompliance'

/**
 * Swing and shift compliance (POR-2) — the Coolibah portal's Swing Compliance page on the ship in
 * scope: the swing on now and the five coming, and under the one pressed, who is clear, who is
 * not, what is expiring onboard, and whether the swing carries the certificates each shift needs.
 * Moving crew and setting watches is the screen before it, Crew and shift distribution.
 */
export function SwingShiftCompliance(): React.ReactNode {
  const scope = useCustomerScope()
  const partnerships = useAllPartnerships()

  if (partnerships.isPending) return <Spinner label="Loading the ship" />
  if (partnerships.error !== null) return <ErrorPanel title="Could not load the ship" error={partnerships.error} />
  const ship = partnerships.data.find((p) => p.id === scope.operationId)
  if (ship === undefined) return <p className="empty">Choose a ship in the rail.</p>

  return (
    <div className="screen">
      <header className="screen__header">
        <div className="screen__headline">
          <h1 className="screen__title">Swing and shift compliance</h1>
        </div>
        <p className="screen__subtitle">{ship.name}</p>
      </header>
      <SwingCompliance ship={ship} show="compliance" />
    </div>
  )
}
