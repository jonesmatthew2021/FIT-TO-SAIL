import { ApiError } from '../api/client'

/**
 * The one place an error reaches the screen.
 *
 * A 403 is reported with the server's own explanation. The backend answers authorisation
 * failures with the reason rather than an opaque "forbidden" (see `AccessDeniedMapper`), on the
 * argument that at ~60 known users an unexplained refusal costs support time and reveals nothing
 * the actor could not already infer — so passing it through is the point, not a leak.
 */
export function ErrorPanel({ title, error }: { title: string; error: unknown }): React.ReactNode {
  const detail =
    error instanceof ApiError
      ? error.message
      : error instanceof Error
        ? error.message
        : String(error)

  return (
    <div className="panel panel--error" role="alert">
      <h2 className="panel__title">{title}</h2>
      <p className="panel__detail">{detail}</p>
      {error instanceof ApiError && error.isForbidden && (
        <p className="panel__hint">
          Your roles do not permit this. If that is wrong, a System Administrator can change them.
        </p>
      )}
    </div>
  )
}
