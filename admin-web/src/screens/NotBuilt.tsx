import { useLocation } from 'react-router-dom'
import { NAV_ITEMS } from '../components/Layout'

/**
 * The modules from §6 that have no backend behind them yet.
 *
 * They are in the navigation deliberately. An admin app whose menu shows only what works looks
 * finished; one that names the remaining modules keeps the scope visible to everyone who opens
 * it, including the person who has to build them.
 */
const BLOCKERS: Record<string, string> = {
  'ADM-3':
    'Needs the matrix versioning service: version list, draft creation, cell editing, the §5.5 ' +
    'diff and publication. The engine already consumes a MatrixSnapshot; nothing exposes or ' +
    'edits versions over the API yet.',
  'ADM-8':
    'Needs a back-office recipient to notify. NotificationService.raise works and the crew app ' +
    'renders its list, but notifications are addressed to a UserAccount and back-office users ' +
    'have none until the identity spike (ADR 0003) creates them. The §9 expiry scan and push ' +
    'fan-out are the other half.',
  'ADM-9':
    'Needs the §8 evidence pipeline: upload, LLM extraction, and the verification queue that a ' +
    'Data Steward accepts, corrects or rejects (LLM-1 — the model never writes the record).',
  'ADM-10':
    'Needs user/role administration, the configuration surface (lead days, suggestion weights, ' +
    'auto-accept threshold, schedules) and integration health.',
}

export function NotBuilt(): React.ReactNode {
  const location = useLocation()
  const item = NAV_ITEMS.find((candidate) => candidate.to === location.pathname)
  const module = item?.module ?? ''

  return (
    <div className="screen">
      <header className="screen__header">
        <h1 className="screen__title">{item?.label ?? 'Not found'}</h1>
        <p className="screen__subtitle">{module} — not built yet</p>
      </header>

      <div className="panel">
        <p>{BLOCKERS[module] ?? 'No such page.'}</p>
      </div>
    </div>
  )
}
