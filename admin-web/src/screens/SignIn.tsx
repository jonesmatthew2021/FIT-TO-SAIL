import { useState } from 'react'
import { readDevIdentity, writeDevIdentity } from '../api/client'
import { BFF_LOGIN_PATH } from '../api/session'
import { ALL_ROLES, roleLabel } from '../domain/enums'

/**
 * Shown when the session call answers 401.
 *
 * In production this is a single button: corporate SSO is the only authentication path (SEC-1),
 * so there is nothing to type. The click is a full-page navigation to the BFF, which runs the
 * OIDC code flow server-side and returns with the session cookie set (ADR 0003).
 *
 * In development it is a role picker driving the backend's dev-auth shim. The whole block below
 * sits behind `import.meta.env.DEV`, a compile-time constant, so a production bundle does not
 * contain it — the same belt-and-braces as the backend, where the shim is removed from the
 * artefact at build time.
 */
export function SignIn(): React.ReactNode {
  return (
    <main className="signin">
      <h1 className="signin__title">CREWCOMP</h1>
      <p className="signin__lede">Crew compliance administration</p>

      <a className="button button--primary" href={BFF_LOGIN_PATH}>
        Sign in with your corporate account
      </a>

      {import.meta.env.DEV && <DevSignIn />}
    </main>
  )
}

function DevSignIn(): React.ReactNode {
  const existing = readDevIdentity()
  const [user, setUser] = useState(existing?.user ?? 'Dev User')
  const [roles, setRoles] = useState<string[]>(
    existing?.roles ?? ['crew_coordinator', 'data_steward'],
  )
  const [personId, setPersonId] = useState(existing?.personId?.toString() ?? '')
  const [partnerships, setPartnerships] = useState(existing?.partnershipIds?.join(',') ?? '')

  function toggle(role: string): void {
    setRoles((current) =>
      current.includes(role) ? current.filter((r) => r !== role) : [...current, role],
    )
  }

  function apply(): void {
    const parsedPersonId = Number.parseInt(personId, 10)
    const parsedPartnerships = partnerships
      .split(',')
      .map((value) => Number.parseInt(value.trim(), 10))
      .filter((value) => !Number.isNaN(value))

    writeDevIdentity({
      user,
      roles,
      ...(Number.isNaN(parsedPersonId) ? {} : { personId: parsedPersonId }),
      ...(parsedPartnerships.length === 0 ? {} : { partnershipIds: parsedPartnerships }),
    })
    // A reload is the honest way to restart every query under the new identity: role changes
    // alter what half these endpoints return, so keeping any cached answer would be wrong.
    window.location.reload()
  }

  return (
    <section className="signin__dev">
      <h2 className="signin__dev-title">Development sign-in</h2>
      <p className="signin__dev-warning">
        Local only. This sends <code>X-Dev-Roles</code> to the backend&rsquo;s development shim,
        which is not present in a production build.
      </p>

      <label className="field">
        <span className="field__label">Name</span>
        <input className="input" value={user} onChange={(event) => setUser(event.target.value)} />
      </label>

      <fieldset className="field">
        <legend className="field__label">Roles</legend>
        <span className="check-group">
          {ALL_ROLES.map((role) => (
            <label key={role} className="check check--box">
              <input type="checkbox" checked={roles.includes(role)} onChange={() => toggle(role)} />
              <span className="dot" />
              {roleLabel(role)}
            </label>
          ))}
        </span>
      </fieldset>

      <label className="field">
        <span className="field__label">Person id (crew member scoping)</span>
        <input
          className="input"
          value={personId}
          inputMode="numeric"
          placeholder="blank for back-office"
          onChange={(event) => setPersonId(event.target.value)}
        />
      </label>

      <label className="field">
        <span className="field__label">Partnership ids (Vessel Master scoping)</span>
        <input
          className="input"
          value={partnerships}
          placeholder="e.g. 1,2"
          onChange={(event) => setPartnerships(event.target.value)}
        />
      </label>

      <button type="button" className="button button--primary" onClick={apply}>
        Continue as {roles.length === 0 ? 'nobody' : roles.map(roleLabel).join(', ')}
      </button>
    </section>
  )
}
