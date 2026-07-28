/**
 * A requirement, as "code first, title as annotation".
 *
 * The code is a business key and is monospaced for the same reason a Sam # is: it is the thing you
 * copy, search for and quote in a register record. The title beside it is grey because it is an
 * annotation on the code, not a second name for the same thing — and because in a column of thirty
 * rows the codes are what the eye scans down.
 */
export function RequirementLabel({
  code,
  title,
}: {
  code: string
  /** Optional because a code the cached catalogue has not seen has no title to annotate it with. */
  title?: string | null | undefined
}): React.ReactNode {
  return (
    <>
      <span className="mono">{code}</span>
      {title !== null && title !== undefined && title !== '' && (
        <>
          {' '}
          <span className="muted">{title}</span>
        </>
      )}
    </>
  )
}
