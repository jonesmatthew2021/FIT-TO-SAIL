import { Link } from 'react-router-dom'
import { useCustomerScope, useManningCheck } from '../api/queries'
import type { CrewChange, Partnership } from '../api/client'
import { Spinner } from './Spinner'

/**
 * A swing against the ship's minimum safe manning table (OPS) — by position, the way the
 * shift rules are by certificate. The table itself is kept under Operations · Manning.
 */
export function ManningCheck({ ship, swing }: { ship: Partnership; swing: CrewChange }): React.ReactNode {
  const check = useManningCheck(ship.abbrev, swing.ccId)
  const suffix = useCustomerScope().suffix
  if (check.isPending) return <Spinner label="Checking manning" />
  if (check.error !== null || check.data === undefined) return null
  const lines = check.data.lines
  const tone = lines.length === 0 ? 'accent' : check.data.short > 0 ? 'critical' : 'good'
  return (
    <section className={`section fold fold--${tone}`}>
      <div className="section__header">
        <div>
          <h2 className="section__title">Minimum safe manning</h2>
          <p className="section__note">
            {lines.length === 0
              ? 'No manning table for this ship yet.'
              : check.data.short === 0
                ? 'Every position the manning document calls for is filled on this swing.'
                : `${check.data.short} ${check.data.short === 1 ? 'position is' : 'positions are'} short of the manning document.`}{' '}
            <Link to={`/operations${suffix}&tab=manning`}>The table</Link> is under Operations.
          </p>
        </div>
        {lines.length > 0 && (
          <span className={`chip chip--${check.data.short > 0 ? 'critical' : 'good'}`}>{check.data.short > 0 ? `${check.data.short} short` : 'all filled'}</span>
        )}
      </div>
      {lines.length > 0 && (
        <div className="table-block table-block--plain">
          <table className="table">
            <thead>
              <tr>
                <th scope="col">Position</th>
                <th scope="col">Where</th>
                <th scope="col">At least</th>
                <th scope="col">Standing</th>
                <th scope="col">Who</th>
              </tr>
            </thead>
            <tbody>
              {lines.map((l) => (
                <tr key={`${l.positionId}-${l.shift ?? ''}`}>
                  <td>{l.positionName}</td>
                  <td>{l.shift ?? 'Whole swing'}</td>
                  <td>{l.required}</td>
                  <td>
                    <span className={`chip chip--${l.satisfied ? 'good' : 'critical'} chip--small`}>{l.standing}</span>
                  </td>
                  <td className="table__wrap muted">{l.names.join(' · ') || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
