import { useState } from 'react'
import {
  useClearConfig,
  useConfig,
  useCreateIdentityProvider,
  useCreateUserAccount,
  useGrantRole,
  useIdentityProviders,
  useJobs,
  usePartnerships,
  useRevokeRole,
  useRunJob,
  useSetConfig,
  useSetIdentityProviderEnabled,
  useSetUserAccountScopes,
  useSetUserAccountStatus,
  useUserAccounts,
} from '../api/queries'
import {
  ApiError,
  type ConfigSetting,
  type IdentityProvider,
  type ScheduledJob,
  type UserAccount,
} from '../api/client'
import { useHasRole } from '../api/session'
import { ErrorPanel } from '../components/ErrorPanel'
import { Spinner } from '../components/Spinner'
import { ALL_ROLES, roleLabel } from '../domain/enums'

const ADMINS = ['system_administrator'] as const

/**
 * ADM-10 — administration: configuration, scheduled jobs, users and roles, and the SEC-1a identity
 * allow-list.
 *
 * ### Policy is editable here; cadence is not
 *
 * The settings section edits **policy** — lead days, thresholds, weights — which the server stores
 * and every read consults. The jobs section is deliberately read-only about *when* things run: the
 * scheduler resolves its cron at start-up from deployment configuration, so a cron editable on this
 * screen would be a setting that looks live and does nothing. What this screen offers instead is the
 * control an operator actually reaches for, which is "run it now".
 *
 * ### Account creation is transitional and says so
 *
 * ADR 0003 provisions corporate accounts at first sign-in — the `(issuer, subject)` pair that
 * identifies someone is not knowable before they present a token. So the accounts created here are
 * SEC-1b `local_test` ones, the server refuses to create them in production, and the screen labels
 * them for what they are. What is *not* transitional is everything else: roles, scopes, suspension
 * and the allow-list are the real administration surface and they work against real accounts.
 */
export function Administration(): React.ReactNode {
  const isAdmin = useHasRole(...ADMINS)

  return (
    <div className="screen screen--narrow">
      <header className="screen__header">
        <h1 className="screen__title">Administration</h1>
        <p className="screen__subtitle">
          Configuration, scheduled work, access, and which identity backends may authenticate anyone.
        </p>
      </header>

      <ConfigurationSection />
      <JobsSection isAdmin={isAdmin} />
      <UsersSection isAdmin={isAdmin} />
      <IdentityProvidersSection isAdmin={isAdmin} />
    </div>
  )
}

// ---------------------------------------------------------------------------
// Configuration
// ---------------------------------------------------------------------------

function ConfigurationSection(): React.ReactNode {
  const config = useConfig()
  const canEdit = useHasRole('system_administrator', 'compliance_lead')

  if (config.isPending) return <Spinner label="Loading configuration" />
  if (config.error !== null) {
    return <ErrorPanel title="Could not load the configuration" error={config.error} />
  }

  return (
    <section className="section">
      <div className="section__header">
        <div>
          <h2 className="section__title">Configuration</h2>
          <p className="section__note">
            A setting with no override uses the built-in default, so an untouched system is a working
            one. Every change is audited.
          </p>
        </div>
      </div>

      <div className="table-block table-block--plain">
        <table className="table">
          <thead>
            <tr>
              <th scope="col">Key</th>
              <th scope="col">Description</th>
              <th scope="col">Value</th>
              <th scope="col" />
              <th scope="col" />
            </tr>
          </thead>
          <tbody>
            {config.data.map((setting) => (
              <SettingRow key={setting.key} setting={setting} canEdit={canEdit} />
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}

function SettingRow({
  setting,
  canEdit,
}: {
  setting: ConfigSetting
  canEdit: boolean
}): React.ReactNode {
  const set = useSetConfig()
  const clear = useClearConfig()
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(() => renderValue(setting.value))

  if (canEdit && editing) {
    return (
      <tr>
        <td colSpan={5}>
          <form
            className="editor"
            onSubmit={(event) => {
              event.preventDefault()
              const parsed = parseValue(setting, draft)
              if (parsed === PARSE_FAILED) return
              set.mutate({ key: setting.key, value: parsed }, { onSuccess: () => setEditing(false) })
            }}
          >
            <label className="field field--inline field--grow">
              <span className="field__label">
                <span className="mono">{setting.key}</span> ·{' '}
                {setting.kind === 'weights'
                  ? 'named weights as JSON'
                  : setting.kind === 'threshold'
                    ? 'confidence between 0 and 1'
                    : 'whole number of days'}
              </span>
              <input
                className="input"
                value={draft}
                autoFocus
                onChange={(event) => setDraft(event.target.value)}
              />
            </label>
            {setting.kind === 'threshold' && (
              <p className="panel__hint">
                Leave it unset to mean "always review", which is the launch posture (LLM-2). A value
                of 0 is refused, because it would accept every extraction rather than enabling
                auto-acceptance.
              </p>
            )}
            {set.error !== null && <p className="editor__error">{errorText(set.error)}</p>}
            <div className="editor__actions">
              <button type="submit" className="button button--primary" disabled={set.isPending}>
                {set.isPending ? 'Saving…' : 'Save'}
              </button>
              <button type="button" className="button" onClick={() => setEditing(false)}>
                Cancel
              </button>
            </div>
          </form>
        </td>
      </tr>
    )
  }

  return (
    <tr>
      <td className="mono">{setting.key}</td>
      <td className="table__wrap">{setting.description}</td>
      <td>
        {/* An unset threshold is not an empty cell: "unset" *is* the policy, and it means every
            document goes to a human (LLM-2). Saying so is the whole point of the row. */}
        {renderValue(setting.value) === '' ? (
          <span className="muted">unset — always review</span>
        ) : (
          <span className={setting.kind === 'weights' ? 'mono mono--wrap' : undefined}>
            {renderValue(setting.value)}
          </span>
        )}
      </td>
      <td>
        {setting.overridden ? (
          <span className="chip chip--outline" title={`Set by ${setting.updatedBy ?? 'someone'}`}>
            overridden
          </span>
        ) : (
          <span className="chip chip--muted">default</span>
        )}
      </td>
      <td>
        {canEdit && (
          <div className="row-actions">
            <button
              type="button"
              className="link-action"
              onClick={() => {
                setDraft(renderValue(setting.value))
                setEditing(true)
              }}
            >
              Change
            </button>
            {setting.overridden && (
              <button
                type="button"
                className="link-action"
                disabled={clear.isPending}
                onClick={() => clear.mutate(setting.key)}
                title={`Restores the built-in default (${renderValue(setting.defaultValue) || 'unset'})`}
              >
                Reset to default
              </button>
            )}
          </div>
        )}
        {clear.error !== null && <p className="editor__error">{errorText(clear.error)}</p>}
      </td>
    </tr>
  )
}

export const PARSE_FAILED = Symbol('parse-failed')

/**
 * Turns the typed text into the JSON value the server expects, or gives up.
 *
 * Giving up rather than guessing: the server validates every setting and produces a message worth
 * reading, so the only job here is to send a *number* as a number and an *object* as an object.
 * Sending `"45"` where the server expects 45 would produce a type error about a value the user typed
 * correctly.
 */
export function parseValue(setting: ConfigSetting, text: string): unknown {
  const trimmed = text.trim()
  if (trimmed === '') return null
  if (setting.kind === 'weights') {
    try {
      return JSON.parse(trimmed)
    } catch {
      return PARSE_FAILED
    }
  }
  const asNumber = Number(trimmed)
  return Number.isFinite(asNumber) ? asNumber : PARSE_FAILED
}

export function renderValue(value: unknown): string {
  if (value === null || value === undefined) return ''
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

// ---------------------------------------------------------------------------
// Scheduled jobs
// ---------------------------------------------------------------------------

function JobsSection({ isAdmin }: { isAdmin: boolean }): React.ReactNode {
  const jobs = useJobs()
  const run = useRunJob()

  if (jobs.isPending) return <Spinner label="Loading scheduled jobs" />
  if (jobs.error !== null) {
    return <ErrorPanel title="Could not load the scheduled jobs" error={jobs.error} />
  }

  return (
    <section className="section">
      <div className="section__header">
        <div>
          <h2 className="section__title">Scheduled work</h2>
          <p className="section__note">
            Cadence is deployment configuration, not a setting — the scheduler reads it at start-up.
            Every job is an idempotent scan, so running one by hand raises no duplicates.
          </p>
        </div>
      </div>

      <div className="table-block table-block--plain">
        <table className="table">
          <thead>
            <tr>
              <th scope="col">Job</th>
              <th scope="col">Cron</th>
              <th scope="col">Last run</th>
              <th scope="col">Detail</th>
              <th scope="col" />
            </tr>
          </thead>
          <tbody>
            {jobs.data.map((job) => (
              <JobRow
                key={job.name}
                job={job}
                isAdmin={isAdmin}
                pending={run.isPending}
                onRun={() => run.mutate(job.name)}
              />
            ))}
          </tbody>
        </table>
      </div>

      {run.error !== null && <p className="editor__error">{errorText(run.error)}</p>}
      {run.data !== undefined && (
        <p className={run.data.outcome === 'succeeded' ? 'callout callout--quiet' : 'editor__error'}>
          {run.data.outcome}: {run.data.detail ?? 'no detail'}
        </p>
      )}

      <p className="panel__hint">
        Last-run state is held in memory, so it is empty after a restart and is per-instance. A
        durable job history belongs to the platform's monitoring (NFR-6), which the spike settles.
      </p>
    </section>
  )
}

function JobRow({
  job,
  isAdmin,
  pending,
  onRun,
}: {
  job: ScheduledJob
  isAdmin: boolean
  pending: boolean
  onRun: () => void
}): React.ReactNode {
  return (
    <tr>
      <td className="mono" title={job.description}>
        {job.name}
      </td>
      {/*
       * The cron is read-only, and that is not laziness: `quarkus-scheduler` resolves it at start-up
       * from deployment configuration, so a cron editable here would be a setting that looks live and
       * does nothing. "Run now" is the control an operator reaches for anyway.
       */}
      <td>
        <span className="chip chip--muted chip--mono">{job.schedule}</span>
      </td>
      <td>
        {job.lastRun === null ? (
          <span className="dim">not in this process</span>
        ) : (
          <span
            className={`chip chip--${job.lastRun.outcome === 'succeeded' ? 'good' : 'critical'}`}
            title={formatMoment(job.lastRun.startedAt)}
          >
            {job.lastRun.outcome}
          </span>
        )}
      </td>
      <td className="table__wrap">
        {job.lastRun === null ? (
          job.description
        ) : (
          <span
            style={
              job.lastRun.outcome === 'succeeded'
                ? undefined
                : { color: 'var(--tone-critical-mid)' }
            }
          >
            {job.lastRun.detail ?? 'no detail'}
          </span>
        )}
      </td>
      <td>
        {isAdmin && (
          <button type="button" className="link-action" disabled={pending} onClick={onRun}>
            Run now
          </button>
        )}
      </td>
    </tr>
  )
}

// ---------------------------------------------------------------------------
// Users and roles
// ---------------------------------------------------------------------------

function UsersSection({ isAdmin }: { isAdmin: boolean }): React.ReactNode {
  const accounts = useUserAccounts()
  const create = useCreateUserAccount()
  const [adding, setAdding] = useState(false)
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [roles, setRoles] = useState<readonly string[]>([])

  if (accounts.isPending) return <Spinner label="Loading accounts" />
  if (accounts.error !== null) {
    if (accounts.error instanceof ApiError && accounts.error.isForbidden) {
      return (
        <section className="section">
          <h2 className="section__title">Users and roles</h2>
          <p className="note">
            Only the System Administrator and the Compliance Lead may see who holds which role — a
            list of role assignments is a map of the system's authority.
          </p>
        </section>
      )
    }
    return <ErrorPanel title="Could not load the accounts" error={accounts.error} />
  }

  const transitional = accounts.data.filter((account) => account.kind === 'local_test')

  return (
    <section className="section">
      <div className="section__header">
        <div>
          <h2 className="section__title">Users and roles</h2>
          <p className="section__note">
            Role grants and revocations are audited with the actor who made them (AUTH-4). Accounts
            are suspended, never deleted: an account is the subject of audit events.
          </p>
        </div>
      </div>

      {transitional.length > 0 && (
        <p className="note">
          {transitional.length} of these {transitional.length === 1 ? 'is a' : 'are'} SEC-1b
          transitional <span className="mono">local_test</span>{' '}
          {transitional.length === 1 ? 'account' : 'accounts'}. They cannot authenticate in
          production and their removal is a P2 exit criterion; a real account is created when its
          holder first signs in with their corporate identity (ADR 0003).
        </p>
      )}

      <ul className="accounts">
        {accounts.data.map((account) => (
          <AccountRow key={account.id} account={account} isAdmin={isAdmin} />
        ))}
      </ul>

      {isAdmin && !adding && (
        <button type="button" className="button" onClick={() => setAdding(true)}>
          Add a transitional account
        </button>
      )}

      {isAdmin && adding && (
        <form
          className="editor"
          onSubmit={(event) => {
            event.preventDefault()
            create.mutate(
              { displayName: name, email: email === '' ? null : email, roles: [...roles], personId: null },
              {
                onSuccess: () => {
                  setAdding(false)
                  setName('')
                  setEmail('')
                  setRoles([])
                },
              },
            )
          }}
        >
          <label className="field field--inline field--grow">
            <span className="field__label">Display name</span>
            <input className="input" value={name} onChange={(event) => setName(event.target.value)} />
          </label>
          <label className="field field--inline">
            <span className="field__label">Email</span>
            <input
              className="input"
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
          </label>
          <div className="field field--grow">
            <span className="field__label">Roles</span>
            <span className="check-group">
              {ALL_ROLES.filter((role) => role !== 'crew_member').map((role) => (
                <label key={role} className="check check--box">
                  <input
                    type="checkbox"
                    checked={roles.includes(role)}
                    onChange={(event) =>
                      setRoles((current) =>
                        event.target.checked
                          ? [...current, role]
                          : current.filter((value) => value !== role),
                      )
                    }
                  />
                  <span className="dot" />
                  {roleLabel(role)}
                </label>
              ))}
            </span>
          </div>
          <p className="panel__hint">
            Crew Member is not offered: it is a role about a person's own data and needs a linked
            Person record, which the crew onboarding path creates (MOB-6).
          </p>
          {create.error !== null && <p className="editor__error">{errorText(create.error)}</p>}
          <div className="editor__actions">
            <button
              type="submit"
              className="button button--primary"
              disabled={create.isPending || name.trim() === '' || roles.length === 0}
            >
              {create.isPending ? 'Creating…' : 'Create'}
            </button>
            <button type="button" className="button" onClick={() => setAdding(false)}>
              Cancel
            </button>
          </div>
        </form>
      )}
    </section>
  )
}

function AccountRow({
  account,
  isAdmin,
}: {
  account: UserAccount
  isAdmin: boolean
}): React.ReactNode {
  const grant = useGrantRole()
  const revoke = useRevokeRole()
  const setStatus = useSetUserAccountStatus()
  const setScopes = useSetUserAccountScopes()
  const partnerships = usePartnerships()
  const [editingScopes, setEditingScopes] = useState(false)

  const error = grant.error ?? revoke.error ?? setStatus.error ?? setScopes.error
  const isVesselMaster = account.roles.includes('vessel_master')

  return (
    <li className="accounts__item">
      <div className="accounts__head">
        <span className="accounts__name">{account.displayName}</span>
        {account.email !== null && <span className="accounts__email">{account.email}</span>}
        <span className={`chip chip--${account.status === 'active' ? 'good' : 'warning'}`}>
          {account.status}
        </span>
        {account.kind === 'local_test' && (
          <span className="chip chip--muted" title="SEC-1b transitional account">
            local_test
          </span>
        )}

        <span className="accounts__aside">
          {isVesselMaster
            ? `Scoped to ${
                account.scopedPartnershipIds.length === 0
                  ? 'nothing — a Vessel Master with no scope sees nothing'
                  : account.scopedPartnershipIds
                      .map(
                        (id) =>
                          partnerships.data?.find((partnership) => partnership.id === id)?.abbrev ??
                          `#${id}`,
                      )
                      .join(', ')
              }`
            : account.identityLinked
              ? 'signed in before'
              : 'never signed in'}
        </span>

        {isAdmin && (
          <button
            type="button"
            className="link-action"
            disabled={setStatus.isPending}
            onClick={() =>
              setStatus.mutate({
                userAccountId: account.id,
                status: account.status === 'active' ? 'suspended' : 'active',
              })
            }
          >
            {account.status === 'active' ? 'Suspend' : 'Reactivate'}
          </button>
        )}
      </div>

      {/*
       * The seven roles as toggle chips, granted ones in the accent. Every role is shown rather than
       * only the held ones, because the question an administrator has is "what does this person have
       * *and not* have" — a list of only what is granted answers half of it.
       */}
      <div className="accounts__roles">
        {ALL_ROLES.map((role) => {
          const held = account.roles.includes(role)
          if (!isAdmin) {
            return held ? (
              <span key={role} className="chip chip--accent">
                {roleLabel(role)}
              </span>
            ) : null
          }
          return (
            <button
              key={role}
              type="button"
              className={held ? 'chip chip--accent chip--toggle' : 'chip chip--muted chip--toggle'}
              aria-pressed={held}
              title={held ? `Revoke ${roleLabel(role)}` : `Grant ${roleLabel(role)}`}
              disabled={grant.isPending || revoke.isPending}
              onClick={() =>
                held
                  ? revoke.mutate({ userAccountId: account.id, role })
                  : grant.mutate({ userAccountId: account.id, role })
              }
            >
              {roleLabel(role)}
            </button>
          )
        })}
      </div>

      {isVesselMaster && isAdmin && (
        <div className="accounts__scopes">
          <button
            type="button"
            className="link-action"
            onClick={() => setEditingScopes((current) => !current)}
          >
            {editingScopes ? 'Done with the scope' : 'Change the partnership scope'}
          </button>
          {editingScopes && (
            <span className="check-group">
              {(partnerships.data ?? []).map((partnership) => (
                <label key={partnership.id} className="check check--box">
                  <input
                    type="checkbox"
                    checked={account.scopedPartnershipIds.includes(partnership.id)}
                    onChange={(event) =>
                      setScopes.mutate({
                        userAccountId: account.id,
                        partnershipIds: event.target.checked
                          ? [...account.scopedPartnershipIds, partnership.id]
                          : account.scopedPartnershipIds.filter((id) => id !== partnership.id),
                      })
                    }
                  />
                  <span className="dot" />
                  {partnership.abbrev}
                </label>
              ))}
            </span>
          )}
        </div>
      )}

      {error !== null && error !== undefined && (
        <p className="editor__error">{errorText(error)}</p>
      )}
    </li>
  )
}

// ---------------------------------------------------------------------------
// SEC-1a — the identity allow-list
// ---------------------------------------------------------------------------

function IdentityProvidersSection({ isAdmin }: { isAdmin: boolean }): React.ReactNode {
  const providers = useIdentityProviders()
  const create = useCreateIdentityProvider()
  const [adding, setAdding] = useState(false)
  const [provider, setProvider] = useState('entra')
  const [issuer, setIssuer] = useState('')
  const [tenant, setTenant] = useState('')
  const [displayName, setDisplayName] = useState('')

  if (providers.isPending) return <Spinner label="Loading identity backends" />
  if (providers.error !== null) {
    if (providers.error instanceof ApiError && providers.error.isForbidden) return null
    return <ErrorPanel title="Could not load the identity backends" error={providers.error} />
  }

  return (
    <section className="section">
      <div className="section__header">
        <div>
          <h2 className="section__title">Identity backends</h2>
          <p className="section__note">
            This list <strong>is</strong> the SEC-1a allow-list: the server checks issuer and
            tenant/domain against it on every token, not just at first sign-in. A backend is onboarded
            disabled, and enabling it is a separate, separately-audited act.
          </p>
        </div>
      </div>

      {providers.data.length === 0 && (
        <p className="empty">
          No identity backends are onboarded, so nobody can sign in with a corporate identity yet.
        </p>
      )}

      {providers.data.length > 0 && (
        <div className="table-block table-block--plain">
          <div className="table-scroll">
            <table className="table" style={{ minWidth: 720 }}>
              <thead>
                <tr>
                  <th scope="col">Name</th>
                  <th scope="col">Provider</th>
                  <th scope="col">Issuer</th>
                  <th scope="col">Tenant / domain</th>
                  <th scope="col">State</th>
                  <th scope="col" />
                </tr>
              </thead>
              <tbody>
                {providers.data.map((entry) => (
                  <ProviderRow key={entry.id} entry={entry} isAdmin={isAdmin} />
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {isAdmin && !adding && (
        <button type="button" className="button self-start" onClick={() => setAdding(true)}>
          Onboard a backend
        </button>
      )}

      {isAdmin && adding && (
        <form
          className="editor"
          onSubmit={(event) => {
            event.preventDefault()
            create.mutate(
              { provider, issuer, tenantOrDomain: tenant, displayName },
              {
                onSuccess: () => {
                  setAdding(false)
                  setIssuer('')
                  setTenant('')
                  setDisplayName('')
                },
              },
            )
          }}
        >
          <label className="field field--inline">
            <span className="field__label">Provider</span>
            <select
              className="input"
              value={provider}
              onChange={(event) => setProvider(event.target.value)}
            >
              <option value="entra">Entra ID</option>
              <option value="google">Google Workspace</option>
              <option value="okta">Okta</option>
            </select>
          </label>
          <label className="field field--inline field--grow">
            <span className="field__label">Issuer</span>
            <input
              className="input"
              value={issuer}
              placeholder={ISSUER_PLACEHOLDERS[provider] ?? 'https://…'}
              onChange={(event) => setIssuer(event.target.value)}
            />
          </label>
          <label className="field field--inline">
            <span className="field__label">Tenant or domain</span>
            <input
              className="input"
              value={tenant}
              placeholder={TENANT_PLACEHOLDERS[provider] ?? ''}
              onChange={(event) => setTenant(event.target.value)}
            />
          </label>
          <label className="field field--inline">
            <span className="field__label">Name</span>
            <input
              className="input"
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
            />
          </label>
          <p className="panel__hint">{ISSUER_GUIDANCE[provider] ?? ''}</p>
          {create.error !== null && <p className="editor__error">{errorText(create.error)}</p>}
          <div className="editor__actions">
            <button
              type="submit"
              className="button button--primary"
              disabled={create.isPending || issuer.trim() === '' || tenant.trim() === ''}
            >
              {create.isPending ? 'Onboarding…' : 'Onboard, disabled'}
            </button>
            <button type="button" className="button" onClick={() => setAdding(false)}>
              Cancel
            </button>
          </div>
        </form>
      )}
    </section>
  )
}

function ProviderRow({
  entry,
  isAdmin,
}: {
  entry: IdentityProvider
  isAdmin: boolean
}): React.ReactNode {
  const setEnabled = useSetIdentityProviderEnabled()

  return (
    <tr>
      <td>{entry.displayName}</td>
      {/* Entra ID takes the accent because it is the tenant-scoped case ADR 0003 warns about most:
          a /common issuer allow-lists every tenant in the world. */}
      <td>
        <span className={entry.provider === 'entra' ? 'chip chip--accent' : 'chip chip--muted'}>
          {PROVIDER_LABELS[entry.provider] ?? entry.provider}
        </span>
      </td>
      <td className="mono meta">
        {entry.issuer}
      </td>
      <td className="text-sm">{entry.tenantOrDomain}</td>
      <td>
        <span className={`chip chip--${entry.enabled ? 'good' : 'muted'}`}>
          {entry.enabled ? 'may authenticate' : 'disabled'}
        </span>
      </td>
      <td>
        {isAdmin && (
          <button
            type="button"
            className="link-action"
            disabled={setEnabled.isPending}
            onClick={() =>
              setEnabled.mutate({ identityProviderId: entry.id, enabled: !entry.enabled })
            }
          >
            {entry.enabled ? 'Stop it authenticating' : 'Allow it to authenticate'}
          </button>
        )}
        {setEnabled.error !== null && <p className="editor__error">{errorText(setEnabled.error)}</p>}
      </td>
    </tr>
  )
}

const PROVIDER_LABELS: Record<string, string> = {
  entra: 'Entra ID',
  google: 'Google Workspace',
  okta: 'Okta',
}

/**
 * Per-provider guidance, taken from ADR 0003's specific traps rather than invented.
 *
 * These are the three ways this form gets filled in wrongly in a way that matters, and each one
 * would allow-list far more than intended. The server refuses the multi-tenant issuers outright;
 * the other two it cannot detect, so saying them here is the only defence.
 */
const ISSUER_PLACEHOLDERS: Record<string, string> = {
  entra: 'https://login.microsoftonline.com/{tenant-id}/v2.0',
  google: 'https://accounts.google.com',
  okta: 'https://{org}.okta.com',
}

const TENANT_PLACEHOLDERS: Record<string, string> = {
  entra: '{tenant-id}',
  google: 'acme.com  (the hd claim)',
  okta: '{org}.okta.com',
}

const ISSUER_GUIDANCE: Record<string, string> = {
  entra:
    'Per-tenant issuer only — never /common, /organizations or /consumers. Those endpoints issue ' +
    'tokens for every tenant in the world, so allow-listing one allow-lists everyone (ADR 0003). ' +
    'The tenant id is checked against the `tid` claim on every token.',
  google:
    "The tenant is the Workspace hosted domain, matched against the ID token's `hd` claim. A token " +
    'with no `hd` is a personal Gmail account and is refused: identity is keyed on `sub`, never on ' +
    'email, because an address can be reassigned inside a tenant.',
  okta:
    "Use the org authorization server, whose issuer is the org domain — so the issuer and the " +
    'allow-list key are the same string. A custom authorization server has a different issuer and ' +
    'will not match.',
}

function formatMoment(iso: string): string {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(
    new Date(iso),
  )
}

function errorText(error: unknown): string {
  return error instanceof ApiError ? error.message : String(error)
}
