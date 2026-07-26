import { cellState, expiryImpact, holdingStatus } from '../domain/enums'

/**
 * A cell state, holding status or expiry impact rendered as a chip.
 *
 * Colour is never the only carrier: each chip shows its label as text, so the screen still reads
 * correctly in monochrome and to a screen reader. The dense matrix and planner grids are exactly
 * where a colour-only encoding would be tempting and wrong.
 */
export function StateChip({
  state,
  kind = 'cell',
  title,
}: {
  state: string
  kind?: 'cell' | 'holding' | 'impact'
  title?: string
}): React.ReactNode {
  const display =
    kind === 'holding' ? holdingStatus(state) : kind === 'impact' ? expiryImpact(state) : cellState(state)

  return (
    <span className={`chip chip--${display.tone}`} title={title ?? display.description}>
      {display.label}
    </span>
  )
}
