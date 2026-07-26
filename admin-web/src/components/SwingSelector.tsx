import { useCrewChanges, usePartnerships } from '../api/queries'
import type { CrewChange } from '../api/client'
import { formatDateRange } from '../domain/dates'
import { epochDay } from '../domain/dates'

/**
 * Partnership × crew-change selector (ADM-2).
 *
 * The crew-change list is scoped to the selected partnership's own calendar rather than offering
 * every CC id in the system — `CC24` is a different fortnight in each partnership, so a global
 * list would invite selecting a pair that does not exist.
 */
export function SwingSelector({
  partnership,
  cc,
  onChange,
}: {
  partnership: string | null
  cc: string | null
  onChange: (partnership: string | null, cc: string | null) => void
}): React.ReactNode {
  const partnerships = usePartnerships()
  const crewChanges = useCrewChanges(partnership)

  return (
    <div className="selector">
      <label className="field field--inline">
        <span className="field__label">Partnership</span>
        <select
          className="input"
          value={partnership ?? ''}
          onChange={(event) => onChange(event.target.value === '' ? null : event.target.value, null)}
        >
          <option value="">Select…</option>
          {(partnerships.data ?? []).map((option) => (
            <option key={option.id} value={option.abbrev}>
              {option.abbrev} — {option.name}
            </option>
          ))}
        </select>
      </label>

      <label className="field field--inline">
        <span className="field__label">Crew change</span>
        <select
          className="input"
          value={cc ?? ''}
          disabled={partnership === null}
          onChange={(event) => onChange(partnership, event.target.value === '' ? null : event.target.value)}
        >
          <option value="">Select…</option>
          {(crewChanges.data ?? []).map((option) => (
            <option key={option.id} value={option.ccId}>
              {option.ccId} — {formatDateRange(option.from, option.to)}
            </option>
          ))}
        </select>
      </label>

      {partnerships.data?.length === 0 && (
        <p className="selector__note">
          No partnerships are visible to your roles.
        </p>
      )}
    </div>
  )
}

/**
 * The swing to open by default: the one containing the business date, else the next one to
 * start, else the most recent.
 *
 * This is calendar selection, not compliance evaluation — it picks which swing to *show*, and
 * every number on the screen still comes from the server's evaluation of it.
 */
export function defaultCrewChange(crewChanges: readonly CrewChange[], today: string): CrewChange | null {
  if (crewChanges.length === 0) return null
  const now = epochDay(today)

  const current = crewChanges.find((cc) => epochDay(cc.from) <= now && epochDay(cc.to) >= now)
  if (current !== undefined) return current

  const upcoming = crewChanges
    .filter((cc) => epochDay(cc.from) > now)
    .sort((a, b) => epochDay(a.from) - epochDay(b.from))
  if (upcoming[0] !== undefined) return upcoming[0]

  const past = [...crewChanges].sort((a, b) => epochDay(b.to) - epochDay(a.to))
  return past[0] ?? null
}
