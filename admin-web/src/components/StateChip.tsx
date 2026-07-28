import { cellState, expiryImpact, holdingStatus, type Tone } from '../domain/enums'

/**
 * A cell state, holding status or expiry impact rendered as a chip.
 *
 * Colour is never the only carrier: each chip shows its label as text, so the screen still reads
 * correctly in monochrome and to a screen reader. The dense matrix and planner grids are exactly
 * where a colour-only encoding would be tempting and wrong.
 *
 * `tone` overrides the enumeration's own mapping, for the one case that is not a property of the
 * value alone: a held certificate warms to a warning when its expiry falls inside the lead window
 * (see `holdingTone`). Nothing else should pass it.
 */
export function StateChip({
  state,
  kind = 'cell',
  title,
  tone,
}: {
  state: string
  kind?: 'cell' | 'holding' | 'impact'
  title?: string
  tone?: Tone
}): React.ReactNode {
  const display =
    kind === 'holding' ? holdingStatus(state) : kind === 'impact' ? expiryImpact(state) : cellState(state)

  return (
    <span className={`chip chip--${tone ?? display.tone}`} title={title ?? display.description}>
      {display.label}
    </span>
  )
}
