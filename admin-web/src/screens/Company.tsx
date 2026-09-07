import { useState } from 'react'
import { Link } from 'react-router-dom'
import { OperationForm } from '../components/ShipBar'
import {
  useAddVessel,
  useAllPartnerships,
  useAttachPartnership,
  useCreateCustomer,
  useCustomerScope,
  useCustomers,
  useDeleteCustomer,
  useDetachPartnership,
  useRemoveVessel,
  useUnattachedPartnerships,
  useUpdateCustomer,
  useVessels,
} from '../api/queries'
import {
  ApiError,
  type Customer,
  type Partnership,
  type Person,
  type SaveCustomerRequest,
  type Vessel,
} from '../api/client'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import { keys } from '../api/queries'
import { useHasRole } from '../api/session'
import { ErrorPanel } from '../components/ErrorPanel'
import { Modal } from '../components/Modal'
import { Spinner } from '../components/Spinner'
import { groupByRank } from '../domain/ranks'

/** The roles the server accepts for a customer write — mirrored to hide the controls. */
const CUSTOMER_EDITORS = ['compliance_lead', 'system_administrator'] as const

/**
 * COM-1 — the customers: the businesses the operation takes on as clients, each with the
 * partnerships (operations) run for it and the crew those carry.
 *
 * The rail's first group. Choosing a customer there opens its own copy of the compliance menu,
 * and every compliance screen then shows that customer alone (`useCustomerScope`). This screen is
 * the directory behind that: who the customers are, how to reach them, and which operations are
 * theirs — the end state being one system running many businesses and their partnerships.
 */
export function Company(): React.ReactNode {
  const customers = useCustomers()
  const partnerships = useAllPartnerships()
  const vessels = useVessels()
  // Unscoped on purpose: this screen shows every customer's crew counts, whatever the rail has open.
  const people = useQuery({ queryKey: keys.people, queryFn: api.people })
  const scope = useCustomerScope()
  const canEdit = useHasRole(...CUSTOMER_EDITORS)
  const [adding, setAdding] = useState(false)

  if (customers.isPending || partnerships.isPending || people.isPending || vessels.isPending) {
    return <Spinner label="Loading the customers" />
  }
  if (vessels.error !== null) return <ErrorPanel title="Could not load the fleet" error={vessels.error} />
  if (customers.error !== null) return <ErrorPanel title="Could not load the customers" error={customers.error} />
  if (partnerships.error !== null) {
    return <ErrorPanel title="Could not load the partnerships" error={partnerships.error} />
  }
  if (people.error !== null) return <ErrorPanel title="Could not load the crew" error={people.error} />

  const shown = scope.customerId === null ? customers.data : customers.data.filter((c) => c.id === scope.customerId)
  const partnershipById = new Map(partnerships.data.map((partnership) => [partnership.id, partnership]))
  const unattached = partnerships.data.filter((partnership) => partnership.customerId === null)

  return (
    <div className="screen">
      <header className="screen__header">
        <h1 className="screen__title">{scope.customer === null ? 'Company' : scope.customer.name}</h1>
        <p className="screen__subtitle">
          {scope.customer === null
            ? 'The businesses we work for, each with the operations run for it and the crew they carry. Pick one in the rail to see every compliance screen for that customer alone.'
            : 'This customer alone. The compliance menu under its name in the rail shows every screen for its operations and crew.'}
        </p>
      </header>

      <div className="counts counts--inline">
        <span className="counts__item">
          <span className="counts__value">{customers.data.length}</span>{' '}
          {customers.data.length === 1 ? 'customer' : 'customers'}
        </span>
        <span className="counts__item">
          <span className="counts__value">{partnerships.data.length}</span>{' '}
          {partnerships.data.length === 1 ? 'partnership' : 'partnerships'}
        </span>
        <span className="counts__item">
          <span className="counts__value">{vessels.data.length}</span> {vessels.data.length === 1 ? 'vessel' : 'vessels'}
        </span>
        <span className="counts__item">
          <span className="counts__value">{people.data.length}</span> crew
        </span>
        {canEdit && scope.customerId === null && (
          <span className="push">
            <button type="button" className="button button--primary" onClick={() => setAdding(true)}>
              Add customer
            </button>
          </span>
        )}
      </div>

      {unattached.length > 0 && scope.customerId === null && (
        <p className="note">
          {unattached.length} {unattached.length === 1 ? 'partnership is' : 'partnerships are'} not attached to
          any customer yet: {unattached.map((p) => p.abbrev).join(', ')}. Attach them below.
        </p>
      )}

      {shown.map((customer) => (
        <CustomerCard
          key={customer.id}
          customer={customer}
          partnerships={customer.partnershipIds
            .map((id) => partnershipById.get(id))
            .filter((partnership): partnership is Partnership => partnership !== undefined)}
          vessels={vessels.data}
          people={people.data}
          canEdit={canEdit}
        />
      ))}

      {adding && <CustomerForm onClose={() => setAdding(false)} />}
    </div>
  )
}

function CustomerCard({
  customer,
  partnerships,
  vessels,
  people,
  canEdit,
}: {
  customer: Customer
  partnerships: readonly Partnership[]
  vessels: readonly Vessel[]
  people: readonly Person[]
  canEdit: boolean
}): React.ReactNode {
  const [editing, setEditing] = useState(false)
  const [removing, setRemoving] = useState(false)
  const [addingOperation, setAddingOperation] = useState(false)
  const [attaching, setAttaching] = useState<number | ''>('')
  const unattached = useUnattachedPartnerships()
  const attach = useAttachPartnership()
  const detach = useDetachPartnership()
  const remove = useDeleteCustomer()
  const partnershipIds = new Set(partnerships.map((partnership) => partnership.id))
  const crew = people.filter((person) => partnershipIds.has(person.partnershipId))
  const fleet = vessels.filter((vessel) => partnershipIds.has(vessel.partnershipId))

  return (
    <section className="section">
      <div className="section__header">
        <div>
          <h2 className="section__title">
            {customer.name}
            {customer.shortName !== null && customer.shortName !== customer.name && (
              <span className="muted"> · {customer.shortName}</span>
            )}{' '}
            <span className={`chip chip--${customer.status === 'active' ? 'good' : 'muted'} chip--small`}>
              {customer.status}
            </span>
          </h2>
          <p className="section__note">
            {partnerships.length} {partnerships.length === 1 ? 'operation' : 'operations'} · {fleet.length}{' '}
            {fleet.length === 1 ? 'vessel' : 'vessels'} · {crew.length} crew
            {customer.contactName !== null && ` · ${customer.contactName}`}
            {customer.contactEmail !== null && ` · ${customer.contactEmail}`}
            {customer.contactPhone !== null && ` · ${customer.contactPhone}`}
          </p>
        </div>
        <div className="row-actions">
          <Link className="button" to={`/company?customer=${customer.id}`}>
            Just this customer
          </Link>
          {canEdit && (
            <button type="button" className="button" onClick={() => setAddingOperation(true)}>
              Add ship
            </button>
          )}
          {canEdit && (
            <button type="button" className="button button--quiet" onClick={() => setEditing(true)}>
              Edit
            </button>
          )}
          {canEdit && !removing && (
            <button type="button" className="button button--quiet" onClick={() => setRemoving(true)}>
              Remove
            </button>
          )}
        </div>
      </div>

      {removing && (
        <div className="panel">
          <p className="panel__title">Remove {customer.name}?</p>
          <p className="panel__detail">
            {partnerships.length > 0
              ? `It still has ${partnerships.length} ${partnerships.length === 1 ? 'operation' : 'operations'} attached — detach them first, or the removal is refused.`
              : 'The customer row goes; the audit trail keeps what it said.'}
          </p>
          <div className="editor__actions">
            <button
              type="button"
              className="button button--primary"
              disabled={remove.isPending || partnerships.length > 0}
              onClick={() => remove.mutate(customer.id, { onSuccess: () => setRemoving(false) })}
            >
              {remove.isPending ? 'Removing…' : 'Remove customer'}
            </button>
            <button type="button" className="button" onClick={() => setRemoving(false)}>
              Keep
            </button>
          </div>
          {remove.error !== null && <p className="editor__error">{errorText(remove.error)}</p>}
        </div>
      )}

      {customer.notes !== null && <p className="note">{customer.notes}</p>}

      <div className="panel">
        {partnerships.length === 0 && (
          <p className="section__note">
            No operations yet — add one above (a vessel or vessel pairing with its own roster), or attach
            an existing one below.
          </p>
        )}
        {partnerships.map((partnership) => {
          const own = people.filter((person) => person.partnershipId === partnership.id)
          const ships = vessels.filter((vessel) => vessel.partnershipId === partnership.id)
          const groups = groupByRank(own)
          return (
            <div key={partnership.id} className="section">
              <div className="section__header">
                <div>
                  <h3 className="section__title section__title--panel">
                    <span className="mono">{partnership.abbrev}</span> · {partnership.name}
                  </h3>
                  <p className="section__note">
                    {ships.length} {ships.length === 1 ? 'vessel' : 'vessels'} · {own.length} crew
                    {partnership.vesselClass !== null && ` · ${partnership.vesselClass}`}
                  </p>
                </div>
                <div className="row-actions">
                  {/* Every ship carries the whole Admin template from the moment it exists. */}
                  <Link className="button" to={`/admin?customer=${customer.id}&operation=${partnership.id}`}>
                    Open this ship
                  </Link>
                  {canEdit && (
                    <button
                      type="button"
                      className="button button--quiet"
                      disabled={detach.isPending}
                      onClick={() => detach.mutate(partnership.id)}
                    >
                      Detach
                    </button>
                  )}
                </div>
              </div>
              <FleetList partnership={partnership} vessels={ships} canEdit={canEdit} />
              <div className="rule-cards">
                {groups.map((group) => (
                  <div key={group.label}>
                    <h4 className="section__title section__title--panel">
                      {group.label} · {group.people.length}
                    </h4>
                    <ul className="list-plain list-plain--tight">
                      {group.people.map((person) => (
                        <li key={person.id}>
                          <Link to={`/people/${person.id}?customer=${customer.id}`}>{person.name}</Link>{' '}
                          <span className="muted">· {person.positionName}</span>
                        </li>
                      ))}
                    </ul>
                  </div>
                ))}
              </div>
            </div>
          )
        })}

        {canEdit && (unattached.data?.length ?? 0) > 0 && (
          <form
            className="editor"
            onSubmit={(event) => {
              event.preventDefault()
              if (attaching === '') return
              attach.mutate({ customerId: customer.id, partnershipId: attaching }, { onSuccess: () => setAttaching('') })
            }}
          >
            <label className="field field--inline field--grow">
              <span className="field__label">Attach an operation</span>
              <select
                className="input"
                value={attaching}
                onChange={(event) => setAttaching(event.target.value === '' ? '' : Number(event.target.value))}
              >
                <option value="">Choose a partnership…</option>
                {(unattached.data ?? []).map((partnership) => (
                  <option key={partnership.id} value={partnership.id}>
                    {partnership.abbrev} — {partnership.name}
                  </option>
                ))}
              </select>
            </label>
            <div className="editor__actions">
              <button type="submit" className="button" disabled={attaching === '' || attach.isPending}>
                Attach
              </button>
            </div>
            {attach.error !== null && <p className="editor__error">{errorText(attach.error)}</p>}
          </form>
        )}
        {detach.error !== null && <p className="editor__error">{errorText(detach.error)}</p>}
      </div>

      {editing && <CustomerForm existing={customer} onClose={() => setEditing(false)} />}
      {addingOperation && <OperationForm customer={customer} customers={[]} onClose={() => setAddingOperation(false)} />}
    </section>
  )
}

/** The vessels on one operation, with add and remove. A customer's fleet is the sum of these. */
function FleetList({
  partnership,
  vessels,
  canEdit,
}: {
  partnership: Partnership
  vessels: readonly Vessel[]
  canEdit: boolean
}): React.ReactNode {
  const add = useAddVessel()
  const remove = useRemoveVessel()
  const [name, setName] = useState('')
  const [kind, setKind] = useState('tug')

  return (
    <div className="tint-card">
      {vessels.length === 0 && <p className="section__note">No vessels recorded on {partnership.abbrev} yet.</p>}
      {vessels.length > 0 && (
        <ul className="list-plain list-plain--tight">
          {vessels.map((vessel) => (
            <li key={vessel.id} className="tag-row">
              <span className="chip chip--muted chip--small">{vessel.kind}</span>
              <span>{vessel.name}</span>
              {canEdit && (
                <button
                  type="button"
                  className="button button--quiet"
                  disabled={remove.isPending}
                  onClick={() => remove.mutate(vessel.id)}
                >
                  Remove
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
      {canEdit && (
        <form
          className="editor"
          onSubmit={(event) => {
            event.preventDefault()
            add.mutate({ partnershipId: partnership.id, body: { name, kind } }, { onSuccess: () => setName('') })
          }}
        >
          <label className="field field--inline field--grow">
            <span className="field__label">Add a vessel</span>
            <input className="input" value={name} onChange={(event) => setName(event.target.value)} placeholder="Vessel name" />
          </label>
          <label className="field field--inline">
            <span className="field__label">Kind</span>
            <select className="input" value={kind} onChange={(event) => setKind(event.target.value)}>
              {VESSEL_KINDS.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </label>
          <div className="editor__actions">
            <button type="submit" className="button" disabled={add.isPending || name.trim() === ''}>
              Add vessel
            </button>
          </div>
          {add.error !== null && <p className="editor__error">{errorText(add.error)}</p>}
          {remove.error !== null && <p className="editor__error">{errorText(remove.error)}</p>}
        </form>
      )}
    </div>
  )
}

/** The kinds the office meets; free text on the wire, so a new kind is a new option here and nothing else. */
const VESSEL_KINDS = ['tug', 'barge', 'ship', 'ferry', 'workboat', 'other'] as const


/** Add or edit a customer — one form, the server's two doors. */
function CustomerForm({ existing, onClose }: { existing?: Customer; onClose: () => void }): React.ReactNode {
  const create = useCreateCustomer()
  const update = useUpdateCustomer()
  const [name, setName] = useState(existing?.name ?? '')
  const [shortName, setShortName] = useState(existing?.shortName ?? '')
  const [contactName, setContactName] = useState(existing?.contactName ?? '')
  const [contactEmail, setContactEmail] = useState(existing?.contactEmail ?? '')
  const [contactPhone, setContactPhone] = useState(existing?.contactPhone ?? '')
  const [notes, setNotes] = useState(existing?.notes ?? '')
  const [status, setStatus] = useState(existing?.status ?? 'active')
  const mutation = existing === undefined ? create : update

  return (
    <Modal title={existing === undefined ? 'Add a customer' : `Edit ${existing.name}`} onClose={onClose}>
      <form
        className="editor"
        onSubmit={(event) => {
          event.preventDefault()
          const body: SaveCustomerRequest = {
            name,
            shortName: shortName === '' ? null : shortName,
            contactName: contactName === '' ? null : contactName,
            contactEmail: contactEmail === '' ? null : contactEmail,
            contactPhone: contactPhone === '' ? null : contactPhone,
            notes: notes === '' ? null : notes,
            status,
          }
          if (existing === undefined) create.mutate(body, { onSuccess: onClose })
          else update.mutate({ customerId: existing.id, body }, { onSuccess: onClose })
        }}
      >
        <label className="field field--inline field--grow">
          <span className="field__label">Company name</span>
          <input className="input" value={name} onChange={(event) => setName(event.target.value)} placeholder="Mineral Resources" />
        </label>
        <label className="field field--inline">
          <span className="field__label">Short name</span>
          <input className="input" value={shortName} onChange={(event) => setShortName(event.target.value)} placeholder="MinRes" />
        </label>
        <label className="field field--inline field--grow">
          <span className="field__label">Contact</span>
          <input className="input" value={contactName} onChange={(event) => setContactName(event.target.value)} />
        </label>
        <label className="field field--inline">
          <span className="field__label">Email</span>
          <input className="input" type="email" value={contactEmail} onChange={(event) => setContactEmail(event.target.value)} />
        </label>
        <label className="field field--inline">
          <span className="field__label">Phone</span>
          <input className="input" value={contactPhone} onChange={(event) => setContactPhone(event.target.value)} />
        </label>
        <label className="field field--inline field--grow">
          <span className="field__label">Notes</span>
          <input className="input" value={notes} onChange={(event) => setNotes(event.target.value)} />
        </label>
        {existing !== undefined && (
          <label className="field field--inline">
            <span className="field__label">Status</span>
            <select className="input" value={status} onChange={(event) => setStatus(event.target.value)}>
              <option value="active">active</option>
              <option value="former">former</option>
            </select>
          </label>
        )}
        <div className="editor__actions">
          <button type="submit" className="button button--primary" disabled={mutation.isPending || name.trim() === ''}>
            {mutation.isPending ? 'Saving…' : existing === undefined ? 'Add customer' : 'Save'}
          </button>
          <button type="button" className="button" onClick={onClose}>
            Cancel
          </button>
        </div>
        {mutation.error !== null && <p className="editor__error">{errorText(mutation.error)}</p>}
      </form>
    </Modal>
  )
}

function errorText(error: unknown): string {
  return error instanceof ApiError ? error.message : String(error)
}
