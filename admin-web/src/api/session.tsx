import { createContext, useContext, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api, ApiError, type Session } from './client'
import { SignIn } from '../screens/SignIn'
import { Spinner } from '../components/Spinner'
import { ErrorPanel } from '../components/ErrorPanel'

/**
 * The signed-in session.
 *
 * Two things come from here and from nowhere else:
 *
 *  - **The actor's roles**, used to hide controls the server would refuse. That gating is
 *    defence in depth only (AUTH-1) — the server re-checks every call, and a hidden button is a
 *    courtesy, not a control.
 *  - **The business date**, in the operating timezone (NFR-5). No screen calls `new Date()`.
 *
 * Where the login endpoint lives is the identity spike's business (ADR 0003): the BFF runs the
 * OIDC code flow server-side and hands the browser an opaque `HttpOnly` cookie. This app's whole
 * part in it is to treat a 401 as "not signed in" and send the user to [BFF_LOGIN_PATH].
 */

/** Implemented by the BFF in the identity spike; a full-page navigation, not a fetch. */
export const BFF_LOGIN_PATH = '/api/v1/auth/login'

const SessionContext = createContext<Session | null>(null)

export function useSession(): Session {
  const session = useContext(SessionContext)
  if (session === null) {
    throw new Error('useSession outside a SessionProvider')
  }
  return session
}

/** Defence-in-depth UI gating. The server is the authority; this only tidies the screen. */
export function useHasRole(...roles: readonly string[]): boolean {
  const session = useSession()
  return roles.some((role) => session.roles.includes(role))
}

/** The business date, in the operating timezone. */
export function useToday(): string {
  return useSession().today
}

/**
 * A customer's own staff: an account scoped to some ships and not the office's system
 * administrator (BUS-1). The server limits what they read; this only shapes the screen to match —
 * one company in the rail, no business section, no adding or removing ships.
 */
export function useIsCustomerStaff(): boolean {
  const session = useSession()
  return session.partnershipIds.length > 0 && !session.roles.includes('system_administrator')
}

/** The office: a system administrator with the whole dataset — the one who runs the business. */
export function useIsOffice(): boolean {
  const session = useSession()
  return session.roles.includes('system_administrator') && session.partnershipIds.length === 0
}

export function SessionProvider({ children }: { children: ReactNode }): ReactNode {
  const query = useQuery({
    queryKey: ['session'],
    queryFn: api.session,
    // A 401 is an answer, not a failure to retry: retrying just delays the sign-in screen.
    retry: (failureCount, error) =>
      !(error instanceof ApiError && error.isUnauthenticated) && failureCount < 2,
    staleTime: 5 * 60 * 1000,
  })

  if (query.isPending) return <Spinner label="Signing in" />

  if (query.error instanceof ApiError && query.error.isUnauthenticated) {
    return <SignIn />
  }
  if (query.error !== null) {
    return <ErrorPanel title="Could not reach the server" error={query.error} />
  }

  return <SessionContext.Provider value={query.data}>{children}</SessionContext.Provider>
}
