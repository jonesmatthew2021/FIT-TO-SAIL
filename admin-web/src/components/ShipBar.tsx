import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import {
  useAllPartnerships,
  useCreatePartnership,
  useCustomerScope,
  useCustomers,
  useDeletePartnership,
  useVessels,
} from '../api/queries'
import { ApiError, type Customer, type Partnership } from '../api/client'
import { useHasRole } from '../api/session'
import { Modal } from './Modal'
import { shipLabel } from './Layout'

/** The roles the server accepts for a fleet write — mirrored to hide the controls. */
const FLEET_EDITORS = ['compliance_lead', 'system_administrator'] as const

/**
 * The ship bar — a screen's own copy of the rail's ship box, with add and remove beside it.
 *
 * Choosing a ship puts `?customer=…&operation=…` on the page, the same mechanism as the rail
 * (`useCustomerScope`), so the screen narrows to that ship. With no customer in scope the box
 * lists every ship, and picking one also picks its customer. Add opens the operation form;
 * Remove is refused by the server while anything hangs off the ship — crew, swings, records —
 * so it is only ever a way to undo a ship added by mistake.
 */
export function ShipBar(): React.ReactNode {
  const scope = useCustomerScope()
  const customers = useCustomers()
  const partnerships = useAllPartnerships()
  const vessels = useVessels()
  const canEdit = useHasRole(...FLEET_EDITORS)
  const location = useLocation()
  const navigate = useNavigate()
  const remove = useDeletePartnership()
  const [adding, setAdding] = useState(false)
  const [removing, setRemoving] = useState(false)

  const all = partnerships.data ?? []
  const ships = scope.customer === null ? all : all.filter((p) => scope.customer?.partnershipIds.includes(p.id))
  const selected = scope.operationId === null ? undefined : all.find((p) => p.id === scope.operationId)

  return (
    <div className="ship-bar">
      <label className="field field--inline">
        <span className="field__label">Ship</span>
        <select
          className="input"
          aria-label="Ship"
          value={scope.operationId ?? ''}
          onChange={(event) => {
            const value = event.target.value
            if (value === '') {
              void navigate(scope.customerId === null ? location.pathname : `${location.pathname}?customer=${scope.customerId}`)
              return
            }
            const ship = all.find((p) => p.id === Number(value))
            const customerId = ship?.customerId ?? scope.customerId
            void navigate(
              customerId === null
                ? `${location.pathname}?operation=${value}`
                : `${location.pathname}?customer=${customerId}&operation=${value}`,
            )
          }}
        >
          <option value="">{scope.customer === null ? 'All ships' : `All ${scope.customer.shortName ?? scope.customer.name} ships`}</option>
          {ships.map((partnership) => (
            <option key={partnership.id} value={partnership.id}>
              {shipLabel(partnership.id, partnership.abbrev, partnership.name, vessels.data ?? [])}
            </option>
          ))}
        </select>
      </label>
      {canEdit && (
        <button type="button" className="button" onClick={() => setAdding(true)}>
          Add ship
        </button>
      )}
      {canEdit && selected !== undefined && !removing && (
        <button type="button" className="button button--quiet" onClick={() => setRemoving(true)}>
          Remove ship
        </button>
      )}

      {removing && selected !== undefined && (
        <Modal title={`Remove ${shipLabel(selected.id, selected.abbrev, selected.name, vessels.data ?? [])}?`} onClose={() => setRemoving(false)}>
          <p className="panel__detail">
            Only a ship with nothing on it can be removed — no crew, swings or records. One with history
            stays; the server refuses and says what is on it.
          </p>
          <div className="editor__actions">
            <button
              type="button"
              className="button button--primary"
              disabled={remove.isPending}
              onClick={() =>
                remove.mutate(selected.id, {
                  onSuccess: () => {
                    setRemoving(false)
                    void navigate(scope.customerId === null ? location.pathname : `${location.pathname}?customer=${scope.customerId}`)
                  },
                })
              }
            >
              {remove.isPending ? 'Removing…' : 'Remove ship'}
            </button>
            <button type="button" className="button" onClick={() => setRemoving(false)}>
              Keep
            </button>
          </div>
          {remove.error !== null && <p className="editor__error">{errorText(remove.error)}</p>}
        </Modal>
      )}

      {adding && (
        <OperationForm
          {...(scope.customer === null ? {} : { customer: scope.customer })}
          customers={customers.data ?? []}
          onClose={() => setAdding(false)}
          onCreated={(partnership) => {
            setAdding(false)
            void navigate(`${location.pathname}?customer=${partnership.customerId ?? ''}&operation=${partnership.id}`)
          }}
        />
      )}
    </div>
  )
}

/**
 * A new operation — a ship, or a vessel pairing with one roster — under a customer. With the
 * customer given it is fixed; without, the form asks, because a ship belongs to somebody.
 */
export function OperationForm({
  customer,
  customers,
  onClose,
  onCreated,
}: {
  customer?: Customer
  customers: readonly Customer[]
  onClose: () => void
  onCreated?: (partnership: Partnership) => void
}): React.ReactNode {
  const create = useCreatePartnership()
  const [customerId, setCustomerId] = useState<number | null>(customer?.id ?? null)
  const [abbrev, setAbbrev] = useState('')
  const [name, setName] = useState('')
  const [vesselClass, setVesselClass] = useState('')
  const chosen = customer ?? customers.find((c) => c.id === customerId)

  return (
    <Modal
      title={chosen === undefined ? 'Add a ship' : `Add a ship for ${chosen.name}`}
      note="A vessel, or a vessel pairing with one roster and swing calendar. Its compliance screens exist the moment it does."
      onClose={onClose}
    >
      <form
        className="editor"
        onSubmit={(event) => {
          event.preventDefault()
          if (customerId === null) return
          create.mutate(
            { customerId, body: { abbrev, name, vesselClass: vesselClass === '' ? null : vesselClass } },
            { onSuccess: (partnership) => (onCreated === undefined ? onClose() : onCreated(partnership)) },
          )
        }}
      >
        {customer === undefined && (
          <label className="field field--inline field--grow">
            <span className="field__label">Customer</span>
            <select className="input" value={customerId ?? ''} onChange={(event) => setCustomerId(event.target.value === '' ? null : Number(event.target.value))}>
              <option value="">Choose…</option>
              {customers.map((option) => (
                <option key={option.id} value={option.id}>
                  {option.name}
                </option>
              ))}
            </select>
          </label>
        )}
        <label className="field field--inline">
          <span className="field__label">Code</span>
          <input
            className="input input--level"
            value={abbrev}
            onChange={(event) => setAbbrev(event.target.value.toUpperCase())}
            placeholder="COO"
            maxLength={6}
          />
        </label>
        <label className="field field--inline field--grow">
          <span className="field__label">Name</span>
          <input className="input" value={name} onChange={(event) => setName(event.target.value)} placeholder="TSV Coolibah — MinRes Onslow" />
        </label>
        <label className="field field--inline">
          <span className="field__label">Vessel class</span>
          <input className="input" value={vesselClass} onChange={(event) => setVesselClass(event.target.value)} placeholder="optional" />
        </label>
        <div className="editor__actions">
          <button
            type="submit"
            className="button button--primary"
            disabled={create.isPending || customerId === null || abbrev.trim() === '' || name.trim() === ''}
          >
            {create.isPending ? 'Adding…' : 'Add ship'}
          </button>
          <button type="button" className="button" onClick={onClose}>
            Cancel
          </button>
        </div>
        {create.error !== null && <p className="editor__error">{errorText(create.error)}</p>}
      </form>
    </Modal>
  )
}

function errorText(error: unknown): string {
  return error instanceof ApiError ? error.message : String(error)
}
