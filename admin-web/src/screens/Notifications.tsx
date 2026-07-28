import { useState } from 'react'
import { Link } from 'react-router-dom'
import {
  useMarkAllNotificationsRead,
  useMarkNotificationRead,
  useNotifications,
} from '../api/queries'
import { ApiError, type Notification } from '../api/client'
import { ErrorPanel } from '../components/ErrorPanel'
import { Spinner } from '../components/Spinner'
import { notificationKind } from '../domain/enums'

/**
 * ADM-8 — the notifications centre (§9).
 *
 * ### Whose list is this?
 *
 * The caller's, and only the caller's. There is no "notifications for user X" and no way to ask for
 * one: a notification list is the record of what somebody has been told, and reading another
 * person's has no legitimate use here. The server answers for the authenticated actor and nothing
 * else, so this screen takes no identity parameter at all.
 *
 * ### A per-role event is several rows, not one shared row
 *
 * §9 routes back-office notifications per role and stores them per recipient, so "a register request
 * was raised" becomes one row for each Workflow Manager. That is what makes the read state below
 * genuinely per-user rather than the POC's global flag — and it is why the list shows who each row
 * was addressed to when it is not the reader.
 *
 * ### Deep links are internal or they are nothing
 *
 * A notification's `deepLink` comes from the server and is either an app path (`/register/UNI-CC24-1`)
 * or a mobile scheme (`crewcomp://certifications`). Only the first is followed here, and it is
 * checked rather than trusted: rendering an arbitrary server string as an href is how a stored value
 * becomes a redirect.
 */
export function Notifications(): React.ReactNode {
  const [state, setState] = useState<'all' | 'unread' | 'read'>('unread')
  const notifications = useNotifications(state)
  const markRead = useMarkNotificationRead()
  const markAllRead = useMarkAllNotificationsRead()

  if (notifications.isPending) return <Spinner label="Loading notifications" />
  if (notifications.error !== null) {
    return <ErrorPanel title="Could not load your notifications" error={notifications.error} />
  }

  const rows = notifications.data
  const unread = rows.filter((row) => !row.read).length

  return (
    <div className="screen screen--column">
      <header className="screen__header">
        <h1 className="screen__title">Notifications</h1>
        <p className="screen__subtitle">
          What you have been told, and why. Your own list only — addressed to you personally, or to a
          role you hold.
        </p>
      </header>

      <div className="selector">
        <div className="seg">
          {(['unread', 'read', 'all'] as const).map((value) => (
            <label key={value} className="seg__opt">
              <input
                type="radio"
                name="notification-state"
                checked={state === value}
                onChange={() => setState(value)}
              />
              {value === 'unread' ? 'Unread' : value === 'read' ? 'Read' : 'All'}
            </label>
          ))}
        </div>

        {unread > 0 && (
          <button
            type="button"
            className="button push"
            disabled={markAllRead.isPending}
            onClick={() => markAllRead.mutate(undefined)}
          >
            Mark all {unread} read
          </button>
        )}
      </div>

      {/* Why the same fact can appear several times over: §9 stores a notification per recipient, so
          a role-routed event becomes one row for each account holding that role. Anyone who can see
          more than one of those rows is seeing other people's copies as well as their own, and the
          recipient chip is what distinguishes them. */}
      {hasDuplicateTitles(rows) && (
        <p className="note">
          A back-office notification is addressed to <em>each</em> account holding the role (§9), which
          is what makes the read state below per-person. Where you see the same message more than
          once, the name on each row is who it went to.
        </p>
      )}

      {rows.length === 0 && (
        <p className="empty">
          {state === 'unread'
            ? 'Nothing unread.'
            : state === 'read'
              ? 'Nothing read yet.'
              : 'No notifications. Back-office notifications are addressed to the accounts holding ' +
                'each role — if none exist yet, there is nowhere for them to land (ADR 0003).'}
        </p>
      )}

      {rows.length > 0 && (
        <ul className="notifications">
          {rows.map((notification) => (
            <NotificationRow
              key={notification.id}
              notification={notification}
              onMarkRead={() => markRead.mutate(notification.id)}
              pending={markRead.isPending}
            />
          ))}
        </ul>
      )}

      {markRead.error !== null && <p className="editor__error">{errorText(markRead.error)}</p>}
      {markAllRead.error !== null && <p className="editor__error">{errorText(markAllRead.error)}</p>}
    </div>
  )
}

function NotificationRow({
  notification,
  onMarkRead,
  pending,
}: {
  notification: Notification
  onMarkRead: () => void
  pending: boolean
}): React.ReactNode {
  const kind = notificationKind(notification.kind)
  const internalPath = internalLink(notification.deepLink)

  return (
    <li className={notification.read ? 'notification notification--read' : 'notification'}>
      <div className="notification__mark" aria-hidden="true" />

      <div className="notification__body">
        <p className="notification__title">
          {notification.title}
          <span className={`chip chip--${kind.tone}`} title={kind.description}>
            {kind.label}
          </span>
        </p>
        {notification.body !== null && <p className="notification__detail">{notification.body}</p>}
        {/* The recipient is shown when the message went to a *role*, because §9 addresses one row to
            each account holding it — and the name is what tells two copies of the same fact apart. */}
        <p className="notification__meta">
          <time dateTime={notification.createdAt}>{formatMoment(notification.createdAt)}</time>
          {notification.audience === 'back_office' && ` · to ${notification.recipient}`}
          {notification.read && ' · read'}
        </p>
      </div>

      {/* A read row loses its Open link and its Mark-read button: there is nothing left to do to it,
          and a column of dead controls is what makes a list of forty rows unreadable. */}
      {!notification.read && (
        <div className="notification__actions">
          {internalPath !== null && (
            <Link className="notification__open" to={internalPath}>
              Open
            </Link>
          )}
          <button type="button" className="link-action" disabled={pending} onClick={onMarkRead}>
            Mark read
          </button>
        </div>
      )}
    </li>
  )
}

/** True when one fact has produced several rows — a per-role fan-out the reader can see all of. */
function hasDuplicateTitles(rows: readonly Notification[]): boolean {
  const seen = new Set<string>()
  return rows.some((row) => {
    const key = `${row.kind}|${row.title}|${row.body ?? ''}`
    if (seen.has(key)) return true
    seen.add(key)
    return false
  })
}

/**
 * The deep link, if it is one this app can follow.
 *
 * Server-provided and therefore checked: only a single-slash-prefixed relative path is accepted, so
 * `//evil.example` (a protocol-relative URL) and `crewcomp://certifications` (the mobile app's
 * scheme, meaningless here) both come back null and render as no link rather than as a redirect.
 */
export function internalLink(deepLink: string | null): string | null {
  if (deepLink === null) return null
  if (!deepLink.startsWith('/')) return null
  if (deepLink.startsWith('//')) return null
  return deepLink
}

/**
 * A notification's timestamp is a real instant, not a business date — it is when the system said
 * something, and `Intl` renders it in the reader's own zone, which is the right zone for "when did
 * I get this". Business *dates* still come from the session (NFR-5) and are never built here.
 */
function formatMoment(iso: string): string {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(
    new Date(iso),
  )
}

function errorText(error: unknown): string {
  return error instanceof ApiError ? error.message : String(error)
}
