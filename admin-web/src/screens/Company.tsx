import { Link } from 'react-router-dom'
import { usePartnerships, usePeople, usePositions } from '../api/queries'
import { ErrorPanel } from '../components/ErrorPanel'
import { Spinner } from '../components/Spinner'
import { groupByRank } from '../domain/ranks'

/**
 * COM-1 — the company.
 *
 * The rail's first group, above the compliance loop: the operation itself rather than a day's
 * work on it. What lives here so far is what the system already knows about the company — the
 * partnerships it runs and the roster each one carries, by rank — laid out as a place to grow.
 * Everything on it is read from the same endpoints the compliance screens use; nothing is
 * invented for the page.
 */
export function Company(): React.ReactNode {
  const partnerships = usePartnerships()
  const people = usePeople()
  const positions = usePositions()

  if (partnerships.isPending || people.isPending || positions.isPending) {
    return <Spinner label="Loading the company" />
  }
  if (partnerships.error !== null) {
    return <ErrorPanel title="Could not load the partnerships" error={partnerships.error} />
  }
  if (people.error !== null) return <ErrorPanel title="Could not load the crew" error={people.error} />

  return (
    <div className="screen">
      <header className="screen__header">
        <h1 className="screen__title">Company</h1>
        <p className="screen__subtitle">
          The operation: its partnerships, and the crew each one carries. The compliance screens
          below the rail are the daily work on what is described here.
        </p>
      </header>

      <div className="counts counts--inline">
        <span className="counts__item">
          <span className="counts__value">{partnerships.data.length}</span>{' '}
          {partnerships.data.length === 1 ? 'partnership' : 'partnerships'}
        </span>
        <span className="counts__item">
          <span className="counts__value">{people.data.length}</span> crew
        </span>
        <span className="counts__item">
          <span className="counts__value">{positions.data?.length ?? 0}</span> positions
        </span>
      </div>

      {partnerships.data.map((partnership) => {
        const crew = people.data.filter((person) => person.partnershipId === partnership.id)
        const groups = groupByRank(crew)
        return (
          <section key={partnership.id} className="section">
            <div className="section__header">
              <div>
                <h2 className="section__title">
                  <span className="mono">{partnership.abbrev}</span> · {partnership.name}
                </h2>
                <p className="section__note">
                  {crew.length} crew
                  {partnership.vesselClass !== null && ` · ${partnership.vesselClass}`}
                </p>
              </div>
            </div>

            <div className="panel">
              {groups.length === 0 && <p className="section__note">No crew on this partnership.</p>}
              <div className="rule-cards">
                {groups.map((group) => (
                  <div key={group.label}>
                    <h3 className="section__title section__title--panel">
                      {group.label} · {group.people.length}
                    </h3>
                    <ul className="list-plain list-plain--tight">
                      {group.people.map((person) => (
                        <li key={person.id}>
                          <Link to={`/people/${person.id}`}>{person.name}</Link>{' '}
                          <span className="muted">· {person.positionName}</span>
                        </li>
                      ))}
                    </ul>
                  </div>
                ))}
              </div>
            </div>
          </section>
        )
      })}
    </div>
  )
}
