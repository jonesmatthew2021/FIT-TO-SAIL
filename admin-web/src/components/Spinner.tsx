export function Spinner({ label }: { label: string }): React.ReactNode {
  return (
    <div className="spinner" role="status" aria-live="polite">
      <span className="spinner__dot" aria-hidden="true" />
      {label}…
    </div>
  )
}
