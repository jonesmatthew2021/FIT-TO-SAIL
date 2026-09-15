import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import {
  useAllPartnerships,
  useCreateFleet,
  useDeleteFleet,
  useFleets,
  useLinkAccountCompany,
  useSetFleetOverseers,
  useSetFleetShips,
  useUpdateFleet,
  useCreateInvoice,
  useCreateUserAccount,
  useCustomers,
  useInvoices,
  useSetCustomerBusiness,
  useSetInvoiceStatus,
  useSetUserAccountStatus,
  usePeople,
  useUpdateCustomer,
  useUpdateInvoice,
  useUserAccounts,
  useVessels,
} from '../api/queries'
import { ApiError, type Customer, type Fleet, type Invoice, type Partnership, type UserAccount, type Vessel } from '../api/client'
import { useIsOffice, useToday } from '../api/session'
import { Copy } from '../components/Copy'
import { ErrorPanel } from '../components/ErrorPanel'
import { shipLabel } from '../components/Layout'
import { Modal } from '../components/Modal'
import { Spinner } from '../components/Spinner'
import { downloadCsv, toCsv } from '../domain/csv'
import { epochDay, formatDate, formatDayMonth } from '../domain/dates'
import { CREW_ROLES, MANAGEMENT_ROLES, OVERSIGHT_ROLES, accessKind, accessLabel, roleLabel } from '../domain/enums'

const TABS = [
  { id: 'customers', label: 'Customers' },
  { id: 'billing', label: 'Billing' },
  { id: 'fleets', label: 'Fleets and oversight' },
  { id: 'access', label: 'Access' },
] as const

/**
 * Business (BUS-1) — the office's side of FIT TO SAIL: the customers as clients rather than as
 * fleets, what each is billed and what has come in, and who at each customer may sign in.
 *
 * Only the office sees it: a system administrator with the whole dataset. A customer's own
 * staff — an account scoped to their ships — never reach this screen and are never sent the
 * business fields (the server blanks them). The compliance engine reads nothing here.
 */
export function Business(): React.ReactNode {
  const office = useIsOffice()
  const [params, setParams] = useSearchParams()
  const tab = (TABS.find((t) => t.id === params.get('tab'))?.id ?? 'customers') as (typeof TABS)[number]['id']
  const customers = useCustomers()
  const partnerships = useAllPartnerships()
  const vessels = useVessels()

  if (!office) return <p className="empty">The business section is the office's — a system administrator's screen.</p>
  if (customers.isPending || partnerships.isPending) return <Spinner label="Loading the business" />
  if (customers.error !== null) return <ErrorPanel title="Could not load the customers" error={customers.error} />
  if (partnerships.error !== null) return <ErrorPanel title="Could not load the fleet" error={partnerships.error} />

  const shipsOf = (customer: Customer) => partnerships.data.filter((p) => customer.partnershipIds.includes(p.id))
  const runRate = customers.data
    .filter((c) => c.status === 'active')
    .reduce((sum, c) => sum + (c.ratePerShipMonth ?? 0) * shipsOf(c).length, 0)

  return (
    <div className="screen">
      <header className="screen__header">
        <h1 className="screen__title">Business</h1>
        <p className="screen__subtitle">
          <Copy k="business.subtitle">
            The office's side of FIT TO SAIL: the customers as clients, what each is billed, and who at each may sign in. Their crews and ships are under Company.
          </Copy>
        </p>
      </header>

      <div className="tiles">
        <Tile label="Customers" value={String(customers.data.filter((c) => c.status === 'active').length)} tone="accent" />
        <Tile label="Ships under management" value={String(customers.data.reduce((n, c) => n + shipsOf(c).length, 0))} tone="accent" />
        <Tile label="Monthly run rate" value={money(runRate)} tone="good" />
      </div>

      <nav className="tabs" aria-label="Business tabs">
        {TABS.map((t) => (
          <button
            key={t.id}
            type="button"
            className={t.id === tab ? 'tabs__tab tabs__tab--active' : 'tabs__tab'}
            aria-current={t.id === tab}
            onClick={() => {
              const next = new URLSearchParams(params)
              next.set('tab', t.id)
              setParams(next)
            }}
          >
            {t.label}
          </button>
        ))}
      </nav>

      {tab === 'customers' && <CustomersTab customers={customers.data} shipsOf={shipsOf} vessels={vessels.data ?? []} />}
      {tab === 'billing' && <BillingTab customers={customers.data} shipsOf={shipsOf} />}
      {tab === 'fleets' && <FleetsTab customers={customers.data} partnerships={partnerships.data} vessels={vessels.data ?? []} />}
      {tab === 'access' && <AccessTab customers={customers.data} shipsOf={shipsOf} vessels={vessels.data ?? []} />}
    </div>
  )
}

function money(amount: number): string {
  return new Intl.NumberFormat('en-AU', { style: 'currency', currency: 'AUD', maximumFractionDigits: 0 }).format(amount)
}

function money2(amount: number): string {
  return new Intl.NumberFormat('en-AU', { style: 'currency', currency: 'AUD' }).format(amount)
}

function Tile({ label, value, tone }: { label: string; value: string; tone: string }): React.ReactNode {
  return (
    <div className={`tile tile--${tone}`}>
      <span className="tile__label">{label}</span>
      <span className="tile__value">{value}</span>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Customers — the client, its contact and its billing terms
// ---------------------------------------------------------------------------

function CustomersTab({
  customers,
  shipsOf,
  vessels,
}: {
  customers: readonly Customer[]
  shipsOf: (customer: Customer) => Partnership[]
  vessels: readonly Vessel[]
}): React.ReactNode {
  const [editing, setEditing] = useState<{ customer: Customer; part: 'contact' | 'billing' } | null>(null)
  return (
    <>
      <div className="biz-cards">
        {customers.map((customer) => {
          const ships = shipsOf(customer)
          return (
            <div key={customer.id} className={customer.status === 'active' ? 'biz-card' : 'biz-card biz-card--former'}>
              <div className="biz-card__head">
                <div>
                  <span className="biz-card__name">{customer.name}</span>
                  {customer.shortName !== null && customer.shortName !== customer.name && <span className="muted"> · {customer.shortName}</span>}
                </div>
                <span className={`chip chip--${customer.status === 'active' ? 'good' : 'muted'} chip--small`}>{customer.status}</span>
              </div>

              <div className="biz-card__cols">
                <div>
                  <p className="nav__group swing-eyebrow">Contact</p>
                  <Fact label="Name" value={customer.contactName} />
                  <Fact label="Email" value={customer.contactEmail} />
                  <Fact label="Phone" value={customer.contactPhone} />
                  <Fact label="Notes" value={customer.notes} />
                  <button type="button" className="button button--quiet" onClick={() => setEditing({ customer, part: 'contact' })}>
                    Edit contact
                  </button>
                </div>
                <div>
                  <p className="nav__group swing-eyebrow">Billing</p>
                  <Fact label="Bills go to" value={customer.billingEmail} />
                  <Fact label="ABN" value={customer.abn} />
                  <Fact label="Address" value={customer.address} />
                  <Fact label="Plan" value={customer.plan} />
                  <Fact label="Rate" value={customer.ratePerShipMonth === null ? null : `${money2(customer.ratePerShipMonth)} per ship per month`} />
                  <Fact label="Billing notes" value={customer.billingNotes} />
                  <button type="button" className="button button--quiet" onClick={() => setEditing({ customer, part: 'billing' })}>
                    Edit billing
                  </button>
                </div>
                <div>
                  <p className="nav__group swing-eyebrow">Ships · {ships.length}</p>
                  {ships.length === 0 && <p className="dim">None yet — add them under Company.</p>}
                  <ul className="list-plain list-plain--tight">
                    {ships.map((ship) => (
                      <li key={ship.id}>
                        <Link to={`/admin?customer=${customer.id}&operation=${ship.id}`}>{shipLabel(ship.id, ship.abbrev, ship.name, vessels)}</Link>
                      </li>
                    ))}
                  </ul>
                  {customer.overseenPartnershipIds.length > 0 && (
                    <p className="muted">
                      Oversees {customer.overseenPartnershipIds.length} {customer.overseenPartnershipIds.length === 1 ? 'ship' : 'ships'} run by others — see Fleets and oversight.
                    </p>
                  )}
                  {customer.ratePerShipMonth !== null && ships.length > 0 && (
                    <p className="muted">{money2(customer.ratePerShipMonth * ships.length)} a month at the current rate.</p>
                  )}
                  <Link className="button button--quiet" to={`/company?customer=${customer.id}`}>
                    Open under Company
                  </Link>
                </div>
              </div>
            </div>
          )
        })}
      </div>
      {editing !== null && editing.part === 'contact' && <ContactForm customer={editing.customer} onClose={() => setEditing(null)} />}
      {editing !== null && editing.part === 'billing' && <BillingForm customer={editing.customer} onClose={() => setEditing(null)} />}
    </>
  )
}

function Fact({ label, value }: { label: string; value: string | null }): React.ReactNode {
  return (
    <div className="biz-fact">
      <span className="biz-fact__label">{label}</span>
      <span className={value === null || value === '' ? 'dim' : ''}>{value === null || value === '' ? '—' : value}</span>
    </div>
  )
}

function ContactForm({ customer, onClose }: { customer: Customer; onClose: () => void }): React.ReactNode {
  const update = useUpdateCustomer()
  const [name, setName] = useState(customer.name)
  const [shortName, setShortName] = useState(customer.shortName ?? '')
  const [contactName, setContactName] = useState(customer.contactName ?? '')
  const [contactEmail, setContactEmail] = useState(customer.contactEmail ?? '')
  const [contactPhone, setContactPhone] = useState(customer.contactPhone ?? '')
  const [notes, setNotes] = useState(customer.notes ?? '')
  const [status, setStatus] = useState(customer.status)
  return (
    <Modal title={`${customer.name} — contact`} onClose={onClose}>
      <form
        className="editor"
        onSubmit={(event) => {
          event.preventDefault()
          update.mutate(
            {
              customerId: customer.id,
              body: {
                name,
                shortName: shortName === '' ? null : shortName,
                contactName: contactName === '' ? null : contactName,
                contactEmail: contactEmail === '' ? null : contactEmail,
                contactPhone: contactPhone === '' ? null : contactPhone,
                notes: notes === '' ? null : notes,
                status,
              },
            },
            { onSuccess: onClose },
          )
        }}
      >
        <Field label="Company name" value={name} onChange={setName} />
        <Field label="Short name" value={shortName} onChange={setShortName} placeholder="How the office says it" />
        <Field label="Contact name" value={contactName} onChange={setContactName} />
        <Field label="Contact email" value={contactEmail} onChange={setContactEmail} type="email" />
        <Field label="Contact phone" value={contactPhone} onChange={setContactPhone} />
        <label className="field field--inline field--grow">
          <span className="field__label">Notes</span>
          <textarea className="input" rows={3} value={notes} onChange={(event) => setNotes(event.target.value)} />
        </label>
        <label className="field field--inline">
          <span className="field__label">Status</span>
          <select className="input" value={status} onChange={(event) => setStatus(event.target.value)}>
            <option value="active">Active</option>
            <option value="former">Former customer</option>
          </select>
        </label>
        <div className="editor__actions">
          <button type="submit" className="button button--primary" disabled={update.isPending || name.trim() === ''}>
            {update.isPending ? 'Saving…' : 'Save'}
          </button>
          <button type="button" className="button" onClick={onClose}>
            Cancel
          </button>
        </div>
        {update.error !== null && <p className="editor__error">{errorText(update.error)}</p>}
      </form>
    </Modal>
  )
}

function BillingForm({ customer, onClose }: { customer: Customer; onClose: () => void }): React.ReactNode {
  const save = useSetCustomerBusiness()
  const [billingEmail, setBillingEmail] = useState(customer.billingEmail ?? '')
  const [abn, setAbn] = useState(customer.abn ?? '')
  const [address, setAddress] = useState(customer.address ?? '')
  const [plan, setPlan] = useState(customer.plan ?? '')
  const [rate, setRate] = useState(customer.ratePerShipMonth === null ? '' : String(customer.ratePerShipMonth))
  const [billingNotes, setBillingNotes] = useState(customer.billingNotes ?? '')
  const rateNumber = rate.trim() === '' ? null : Number(rate)
  const badRate = rateNumber !== null && (Number.isNaN(rateNumber) || rateNumber < 0)
  return (
    <Modal title={`${customer.name} — billing`} note="What the bill says and where it goes. Nothing here reaches the customer's own screens." onClose={onClose}>
      <form
        className="editor"
        onSubmit={(event) => {
          event.preventDefault()
          if (badRate) return
          save.mutate(
            {
              customerId: customer.id,
              body: {
                billingEmail: billingEmail === '' ? null : billingEmail,
                abn: abn === '' ? null : abn,
                address: address === '' ? null : address,
                plan: plan === '' ? null : plan,
                ratePerShipMonth: rateNumber,
                billingNotes: billingNotes === '' ? null : billingNotes,
              },
            },
            { onSuccess: onClose },
          )
        }}
      >
        <Field label="Bills go to" value={billingEmail} onChange={setBillingEmail} type="email" placeholder="accounts@…" />
        <Field label="ABN" value={abn} onChange={setAbn} />
        <label className="field field--inline field--grow">
          <span className="field__label">Address</span>
          <textarea className="input" rows={2} value={address} onChange={(event) => setAddress(event.target.value)} />
        </label>
        <Field label="Plan" value={plan} onChange={setPlan} placeholder="Per ship, monthly" />
        <label className="field field--inline">
          <span className="field__label">Rate per ship per month (AUD)</span>
          <input className="input input--level" inputMode="decimal" value={rate} onChange={(event) => setRate(event.target.value)} placeholder="0.00" />
        </label>
        <label className="field field--inline field--grow">
          <span className="field__label">Billing notes</span>
          <textarea className="input" rows={2} value={billingNotes} onChange={(event) => setBillingNotes(event.target.value)} placeholder="Terms, PO numbers, who to chase" />
        </label>
        <div className="editor__actions">
          <button type="submit" className="button button--primary" disabled={save.isPending || badRate}>
            {save.isPending ? 'Saving…' : 'Save'}
          </button>
          <button type="button" className="button" onClick={onClose}>
            Cancel
          </button>
          {badRate && <span className="editor__error">The rate is a number of dollars, not below zero.</span>}
        </div>
        {save.error !== null && <p className="editor__error">{errorText(save.error)}</p>}
      </form>
    </Modal>
  )
}

function Field({
  label,
  value,
  onChange,
  type = 'text',
  placeholder,
}: {
  label: string
  value: string
  onChange: (value: string) => void
  type?: string
  placeholder?: string
}): React.ReactNode {
  return (
    <label className="field field--inline field--grow">
      <span className="field__label">{label}</span>
      <input className="input" type={type} value={value} onChange={(event) => onChange(event.target.value)} placeholder={placeholder} />
    </label>
  )
}

// ---------------------------------------------------------------------------
// Billing — invoices raised, sent, paid
// ---------------------------------------------------------------------------

function BillingTab({ customers, shipsOf }: { customers: readonly Customer[]; shipsOf: (customer: Customer) => Partnership[] }): React.ReactNode {
  const today = useToday()
  const invoices = useInvoices()
  const setStatus = useSetInvoiceStatus()
  const [raising, setRaising] = useState(false)
  const [editing, setEditing] = useState<Invoice | null>(null)
  const [filter, setFilter] = useState<'open' | 'all'>('open')

  if (invoices.isPending) return <Spinner label="Loading the invoices" />
  if (invoices.error !== null) return <ErrorPanel title="Could not load the invoices" error={invoices.error} />

  const all = invoices.data
  const outstanding = all.filter((i) => i.status === 'sent')
  const overdue = outstanding.filter((i) => i.dueOn !== null && epochDay(i.dueOn) < epochDay(today))
  const paidThisYear = all.filter((i) => i.status === 'paid' && i.paidOn !== null && i.paidOn.slice(0, 4) === today.slice(0, 4))
  const sum = (list: Invoice[]) => list.reduce((n, i) => n + i.amount, 0)
  const shown = filter === 'all' ? all : all.filter((i) => i.status === 'draft' || i.status === 'sent')

  const mark = (invoice: Invoice, status: string) => setStatus.mutate({ invoiceId: invoice.id, body: { status, on: null, dueOn: null } })

  return (
    <>
      <div className="tiles">
        <Tile label="Outstanding" value={money(sum(outstanding))} tone={outstanding.length > 0 ? 'warning' : 'good'} />
        <Tile label="Overdue" value={money(sum(overdue))} tone={overdue.length > 0 ? 'critical' : 'good'} />
        <Tile label={`Paid in ${today.slice(0, 4)}`} value={money(sum(paidThisYear))} tone="good" />
        <Tile label="Drafts" value={String(all.filter((i) => i.status === 'draft').length)} tone="accent" />
      </div>

      <div className="row-actions roster-toolbar">
        <button type="button" className="button button--primary" onClick={() => setRaising(true)}>
          Raise an invoice
        </button>
        <label className="check check--box">
          <input type="checkbox" checked={filter === 'all'} onChange={(event) => setFilter(event.target.checked ? 'all' : 'open')} />
          <span className="dot" />
          Show paid and void too
        </label>
        <button
          type="button"
          className="button"
          onClick={() =>
            downloadCsv(
              `invoices-${today}.csv`,
              toCsv(all, [
                { header: 'Reference', value: (i) => i.reference },
                { header: 'Customer', value: (i) => i.customerName },
                { header: 'Period start', value: (i) => i.periodStart },
                { header: 'Period end', value: (i) => i.periodEnd },
                { header: 'Amount', value: (i) => i.amount },
                { header: 'Status', value: (i) => i.status },
                { header: 'Issued', value: (i) => i.issuedOn },
                { header: 'Due', value: (i) => i.dueOn },
                { header: 'Paid', value: (i) => i.paidOn },
                { header: 'Note', value: (i) => i.note },
              ]),
            )
          }
        >
          Export CSV
        </button>
      </div>

      <div className="table-block table-block--plain">
        <table className="table">
          <thead>
            <tr>
              <th scope="col">Reference</th>
              <th scope="col">Customer</th>
              <th scope="col">Period</th>
              <th scope="col">Amount</th>
              <th scope="col">Status</th>
              <th scope="col">Issued</th>
              <th scope="col">Due</th>
              <th scope="col">Paid</th>
              <th scope="col" />
            </tr>
          </thead>
          <tbody>
            {shown.length === 0 && (
              <tr>
                <td colSpan={9} className="empty">
                  {all.length === 0 ? 'No invoices yet. Raise the first one above.' : 'Nothing open — everything is paid or void.'}
                </td>
              </tr>
            )}
            {shown.map((invoice) => {
              const late = invoice.status === 'sent' && invoice.dueOn !== null && epochDay(invoice.dueOn) < epochDay(today)
              return (
                <tr key={invoice.id}>
                  <td className="mono">{invoice.reference}</td>
                  <td>{invoice.customerName}</td>
                  <td>
                    {formatDayMonth(invoice.periodStart)} – {formatDate(invoice.periodEnd)}
                    {invoice.note !== null && <span className="meta"> · {invoice.note}</span>}
                  </td>
                  <td>{money2(invoice.amount)}</td>
                  <td>
                    <span className={`chip chip--${late ? 'critical' : invoice.status === 'paid' ? 'good' : invoice.status === 'sent' ? 'warning' : invoice.status === 'void' ? 'muted' : 'accent'} chip--small`}>
                      {late ? 'overdue' : invoice.status}
                    </span>
                  </td>
                  <td>{formatDate(invoice.issuedOn)}</td>
                  <td>{formatDate(invoice.dueOn)}</td>
                  <td>{formatDate(invoice.paidOn)}</td>
                  <td className="row-actions">
                    {invoice.status === 'draft' && (
                      <>
                        <button type="button" className="link-action" onClick={() => setEditing(invoice)}>
                          Edit
                        </button>
                        <button type="button" className="link-action" disabled={setStatus.isPending} onClick={() => mark(invoice, 'sent')}>
                          Mark sent
                        </button>
                      </>
                    )}
                    {invoice.status === 'sent' && (
                      <button type="button" className="link-action" disabled={setStatus.isPending} onClick={() => mark(invoice, 'paid')}>
                        Mark paid
                      </button>
                    )}
                    {(invoice.status === 'draft' || invoice.status === 'sent') && (
                      <button type="button" className="link-action" disabled={setStatus.isPending} onClick={() => mark(invoice, 'void')}>
                        Void
                      </button>
                    )}
                    {(invoice.status === 'void' || invoice.status === 'paid') && (
                      <button type="button" className="link-action" disabled={setStatus.isPending} onClick={() => mark(invoice, 'draft')}>
                        Back to draft
                      </button>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
      {setStatus.error !== null && <p className="editor__error">{errorText(setStatus.error)}</p>}

      {raising && <InvoiceForm customers={customers} shipsOf={shipsOf} onClose={() => setRaising(false)} />}
      {editing !== null && <InvoiceForm customers={customers} shipsOf={shipsOf} existing={editing} onClose={() => setEditing(null)} />}
    </>
  )
}

function monthBounds(today: string): { start: string; end: string } {
  const year = Number(today.slice(0, 4))
  const month = Number(today.slice(5, 7))
  const start = `${today.slice(0, 7)}-01`
  const last = new Date(Date.UTC(year, month, 0)).getUTCDate()
  return { start, end: `${today.slice(0, 7)}-${String(last).padStart(2, '0')}` }
}

function InvoiceForm({
  customers,
  shipsOf,
  existing,
  onClose,
}: {
  customers: readonly Customer[]
  shipsOf: (customer: Customer) => Partnership[]
  existing?: Invoice
  onClose: () => void
}): React.ReactNode {
  const today = useToday()
  const create = useCreateInvoice()
  const update = useUpdateInvoice()
  const bounds = monthBounds(today)
  const [customerId, setCustomerId] = useState<number | null>(existing?.customerId ?? customers.find((c) => c.status === 'active')?.id ?? null)
  const [periodStart, setPeriodStart] = useState(existing?.periodStart ?? bounds.start)
  const [periodEnd, setPeriodEnd] = useState(existing?.periodEnd ?? bounds.end)
  const [amount, setAmount] = useState(existing === undefined ? '' : String(existing.amount))
  const [reference, setReference] = useState('')
  const [note, setNote] = useState(existing?.note ?? '')
  const customer = customers.find((c) => c.id === customerId)
  const suggested = customer !== undefined && customer.ratePerShipMonth !== null ? customer.ratePerShipMonth * shipsOf(customer).length : null
  const amountNumber = Number(amount)
  const bad = amount.trim() === '' || Number.isNaN(amountNumber) || amountNumber < 0 || epochDay(periodEnd) < epochDay(periodStart)
  const pending = create.isPending || update.isPending
  const error = create.error ?? update.error

  return (
    <Modal title={existing === undefined ? 'Raise an invoice' : `Edit ${existing.reference}`} note="A draft until you mark it sent; the reference is made for you unless you give one." onClose={onClose}>
      <form
        className="editor"
        onSubmit={(event) => {
          event.preventDefault()
          if (bad || customerId === null) return
          if (existing === undefined) {
            create.mutate(
              { customerId, periodStart, periodEnd, amount: amountNumber, reference: reference.trim() === '' ? null : reference.trim(), note: note === '' ? null : note },
              { onSuccess: onClose },
            )
          } else {
            update.mutate({ invoiceId: existing.id, body: { periodStart, periodEnd, amount: amountNumber, note: note === '' ? null : note } }, { onSuccess: onClose })
          }
        }}
      >
        {existing === undefined && (
          <label className="field field--inline field--grow">
            <span className="field__label">Customer</span>
            <select className="input" value={customerId ?? ''} onChange={(event) => setCustomerId(event.target.value === '' ? null : Number(event.target.value))}>
              <option value="">Choose…</option>
              {customers.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </label>
        )}
        <label className="field field--inline">
          <span className="field__label">Period from</span>
          <input className="input" type="date" value={periodStart} onChange={(event) => setPeriodStart(event.target.value)} />
        </label>
        <label className="field field--inline">
          <span className="field__label">to</span>
          <input className="input" type="date" value={periodEnd} onChange={(event) => setPeriodEnd(event.target.value)} />
        </label>
        <label className="field field--inline">
          <span className="field__label">Amount (AUD)</span>
          <input className="input input--level" inputMode="decimal" value={amount} onChange={(event) => setAmount(event.target.value)} placeholder="0.00" />
        </label>
        {suggested !== null && existing === undefined && (
          <p className="muted">
            At {customer?.plan ?? 'the current rate'}: {money2(suggested)} for {shipsOf(customer as Customer).length} {shipsOf(customer as Customer).length === 1 ? 'ship' : 'ships'}.{' '}
            <button type="button" className="link-action" onClick={() => setAmount(suggested.toFixed(2))}>
              Use it
            </button>
          </p>
        )}
        {existing === undefined && <Field label="Reference" value={reference} onChange={setReference} placeholder="INV-2026-0001 (made for you if blank)" />}
        <Field label="Note" value={note} onChange={setNote} placeholder="What it covers, a PO number" />
        <div className="editor__actions">
          <button type="submit" className="button button--primary" disabled={pending || bad || customerId === null}>
            {pending ? 'Saving…' : existing === undefined ? 'Raise it as a draft' : 'Save'}
          </button>
          <button type="button" className="button" onClick={onClose}>
            Cancel
          </button>
        </div>
        {error !== null && <p className="editor__error">{errorText(error)}</p>}
      </form>
    </Modal>
  )
}

// ---------------------------------------------------------------------------
// Access — who at each customer may sign in, and to what
// ---------------------------------------------------------------------------

function AccessTab({
  customers,
  shipsOf,
  vessels,
}: {
  customers: readonly Customer[]
  shipsOf: (customer: Customer) => Partnership[]
  vessels: readonly Vessel[]
}): React.ReactNode {
  const accounts = useUserAccounts()
  const people = usePeople()
  const partnerships = useAllPartnerships()
  const setAccountStatus = useSetUserAccountStatus()
  const [inviting, setInviting] = useState<{ customer: Customer; kind: InviteKind } | null>(null)

  if (accounts.isPending) return <Spinner label="Loading the accounts" />
  if (accounts.error !== null) return <ErrorPanel title="Could not load the accounts" error={accounts.error} />

  // An account belongs to the company it is linked to. One made before companies were linked
  // belongs through the ships it is scoped to, or the crew member it is. What is neither is the office's.
  const shipOfPerson = new Map((people.data ?? []).map((p) => [p.id, p.partnershipId]))
  const belongs = (a: UserAccount, customer: Customer) =>
    a.customerId !== null
      ? a.customerId === customer.id
      : a.scopedPartnershipIds.some((id) => customer.partnershipIds.includes(id)) ||
        (a.scopedPartnershipIds.length === 0 && a.personId !== null && customer.partnershipIds.includes(shipOfPerson.get(a.personId) ?? -1))
  const of = (customer: Customer, kind: 'management' | 'oversight' | 'crew') =>
    accounts.data.filter((a) => belongs(a, customer) && accessKind(a.roles) === kind)
  const placed = new Set(customers.flatMap((c) => accounts.data.filter((a) => belongs(a, c)).map((a) => a.id)))
  const office = accounts.data.filter((a) => !placed.has(a.id))
  const onStatus = (a: UserAccount, status: string) => setAccountStatus.mutate({ userAccountId: a.id, status })
  const overseen = (customer: Customer) => (partnerships.data ?? []).filter((p) => customer.overseenPartnershipIds.includes(p.id))
  const label = (list: Partnership[]) => list.map((s) => shipLabel(s.id, s.abbrev, s.name, vessels)).join(', ')

  return (
    <>
      <p className="note">
        <Copy k="business.access-note">
          A customer's people sign in to the same program and see only their own company. Management runs the company's compliance across its ships; oversight sees, read only, the fleets a company monitors for others; crew see their own record through the crew app. An account with no company, ships or crew member against it sees everything, so those are the office's and are listed last.
        </Copy>
      </p>

      {customers.map((customer) => {
        const ships = shipsOf(customer)
        const watched = overseen(customer)
        const managers = of(customer, 'management')
        const overseers = of(customer, 'oversight')
        const crew = of(customer, 'crew')
        return (
          <div key={customer.id}>
            {(ships.length > 0 || managers.length > 0 || watched.length === 0) && (
              <section className="section biz-access">
                <div className="section__header">
                  <div>
                    <h2 className="section__title">{customer.name} management</h2>
                    <p className="section__note">
                      {managers.length === 0 ? 'Nobody yet' : `${managers.length} with access`} · {ships.length === 0 ? 'no ships of its own yet' : label(ships)}
                    </p>
                  </div>
                  <button type="button" className="button button--primary" disabled={ships.length === 0} title={ships.length === 0 ? "Add the customer's ships under Company first" : ''} onClick={() => setInviting({ customer, kind: 'management' })}>
                    Give management access
                  </button>
                </div>
                {managers.length > 0 && <AccountTable accounts={managers} onStatus={onStatus} pending={setAccountStatus.isPending} />}
              </section>
            )}

            {(watched.length > 0 || overseers.length > 0) && (
              <section className="section biz-access">
                <div className="section__header">
                  <div>
                    <h2 className="section__title">{customer.name} oversight</h2>
                    <p className="section__note">
                      {overseers.length === 0 ? 'Nobody yet' : `${overseers.length} with access`} · read only · {watched.length === 0 ? 'no fleet to oversee' : label(watched)}
                    </p>
                  </div>
                  <button type="button" className="button button--primary" disabled={watched.length === 0} title={watched.length === 0 ? 'Make this company the overseer of a fleet first' : ''} onClick={() => setInviting({ customer, kind: 'oversight' })}>
                    Give oversight access
                  </button>
                </div>
                {overseers.length > 0 && <AccountTable accounts={overseers} onStatus={onStatus} pending={setAccountStatus.isPending} />}
              </section>
            )}

            {(ships.length > 0 || crew.length > 0) && (
              <section className="section biz-access">
                <div className="section__header">
                  <div>
                    <h2 className="section__title">{customer.name} crew</h2>
                    <p className="section__note">{crew.length === 0 ? 'No crew member has an account yet' : `${crew.length} with access`} · their own record, through the crew app</p>
                  </div>
                  <button type="button" className="button" disabled={ships.length === 0} onClick={() => setInviting({ customer, kind: 'crew' })}>
                    Give crew access
                  </button>
                </div>
                {crew.length > 0 && <AccountTable accounts={crew} onStatus={onStatus} pending={setAccountStatus.isPending} />}
              </section>
            )}
          </div>
        )
      })}

      <section className="section biz-access">
        <div className="section__header">
          <div>
            <h2 className="section__title">The office</h2>
            <p className="section__note">Accounts with the whole dataset — FIT TO SAIL's own people. Managed in full under Administration.</p>
          </div>
          <Link className="button button--quiet" to="/administration">
            Administration
          </Link>
        </div>
        <AccountTable accounts={office} onStatus={onStatus} pending={setAccountStatus.isPending} />
      </section>

      {setAccountStatus.error !== null && <p className="editor__error">{errorText(setAccountStatus.error)}</p>}
      {inviting !== null && (
        <InviteForm
          customer={inviting.customer}
          ships={shipsOf(inviting.customer)}
          overseen={overseen(inviting.customer)}
          initialKind={inviting.kind}
          onClose={() => setInviting(null)}
        />
      )}
    </>
  )
}

function AccountTable({ accounts, onStatus, pending }: { accounts: readonly UserAccount[]; onStatus: (account: UserAccount, status: string) => void; pending: boolean }): React.ReactNode {
  return (
    <div className="table-block table-block--plain">
      <table className="table">
        <thead>
          <tr>
            <th scope="col">Name</th>
            <th scope="col">Email</th>
            <th scope="col">Access</th>
            <th scope="col">Status</th>
            <th scope="col" />
          </tr>
        </thead>
        <tbody>
          {accounts.length === 0 && (
            <tr>
              <td colSpan={5} className="empty">
                Nobody.
              </td>
            </tr>
          )}
          {accounts.map((account) => (
            <tr key={account.id}>
              <td>{account.displayName}</td>
              <td>{account.email ?? <span className="dim">—</span>}</td>
              <td title={account.roles.map(roleLabel).join(' · ')}>{accessLabel(account.roles)}</td>
              <td>
                <span className={`chip chip--${account.status === 'active' ? 'good' : 'muted'} chip--small`}>{account.status}</span>
              </td>
              <td>
                {account.status === 'active' ? (
                  <button type="button" className="link-action" disabled={pending} onClick={() => onStatus(account, 'disabled')}>
                    Take access away
                  </button>
                ) : (
                  <button type="button" className="link-action" disabled={pending} onClick={() => onStatus(account, 'active')}>
                    Give it back
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

type InviteKind = 'management' | 'oversight' | 'crew'

/**
 * Access for one of a customer's people, of one of three kinds, and the account linked to the
 * company so its ships follow the company's arrangement:
 *
 *  - Management: every back-office role, over the company's own ships (and any fleet it oversees).
 *  - Oversight: Vessel Master alone — read only — over the fleets the company oversees for others.
 *  - Crew: one crew member's own record, through the crew app; no ship scope.
 */
function InviteForm({
  customer,
  ships,
  overseen,
  initialKind,
  onClose,
}: {
  customer: Customer
  ships: Partnership[]
  overseen: Partnership[]
  initialKind: InviteKind
  onClose: () => void
}): React.ReactNode {
  const create = useCreateUserAccount()
  const link = useLinkAccountCompany()
  const people = usePeople()
  const [kind, setKind] = useState<InviteKind>(initialKind)
  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [personId, setPersonId] = useState<number | null>(null)
  const [error, setError] = useState<unknown>(null)
  const pending = create.isPending || link.isPending
  const crew = (people.data ?? []).filter((p) => customer.partnershipIds.includes(p.partnershipId) && p.status === 'active')
  const person = crew.find((p) => p.id === personId)
  const ready = kind === 'crew' ? personId !== null : displayName.trim() !== ''
  const kinds: { id: InviteKind; title: string; text: string; offered: boolean }[] = [
    { id: 'management', title: 'Management', text: `Runs the company's compliance: crew, certificates, swings, matrix — across ${ships.map((s) => s.abbrev).join(', ') || 'its ships'}.`, offered: ships.length > 0 },
    { id: 'oversight', title: 'Oversight', text: `Sees, read only, the fleets ${customer.shortName ?? customer.name} oversees — ${overseen.map((s) => s.abbrev).join(', ') || 'none yet'}. Changes nothing.`, offered: overseen.length > 0 },
    { id: 'crew', title: 'Crew', text: 'One crew member: their own certificates and swings, through the crew app. Nothing else.', offered: ships.length > 0 },
  ]

  return (
    <Modal title={`Access for ${customer.name}`} note="The account is tied to the company, so what it sees follows the company's ships and the fleets it oversees." onClose={onClose}>
      <form
        className="editor"
        onSubmit={(event) => {
          event.preventDefault()
          if (!ready) return
          setError(null)
          const roles = kind === 'management' ? [...MANAGEMENT_ROLES] : kind === 'oversight' ? [...OVERSIGHT_ROLES] : [...CREW_ROLES]
          const body =
            kind === 'crew'
              ? { displayName: person?.name ?? displayName, email: email === '' ? (person?.email ?? null) : email, roles, personId }
              : { displayName, email: email === '' ? null : email, roles }
          create.mutate(body, {
            onSuccess: (account) => link.mutate({ accountId: account.id, customerId: customer.id }, { onSuccess: onClose, onError: setError }),
            onError: setError,
          })
        }}
      >
        <div className="kind-choice kind-choice--three" role="radiogroup" aria-label="Kind of access">
          {kinds
            .filter((k) => k.offered)
            .map((k) => (
              <button key={k.id} type="button" className={kind === k.id ? 'kind-choice__option kind-choice__option--on' : 'kind-choice__option'} onClick={() => setKind(k.id)}>
                <strong>{k.title}</strong>
                <span>{k.text}</span>
              </button>
            ))}
        </div>

        {kind !== 'crew' ? (
          <>
            <Field label="Name" value={displayName} onChange={setDisplayName} />
            <Field label="Email" value={email} onChange={setEmail} type="email" />
          </>
        ) : (
          <>
            <label className="field field--inline field--grow">
              <span className="field__label">Crew member</span>
              <select className="input" value={personId ?? ''} onChange={(event) => setPersonId(event.target.value === '' ? null : Number(event.target.value))}>
                <option value="">Choose…</option>
                {crew.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name} · {p.positionName}
                  </option>
                ))}
              </select>
            </label>
            <Field label="Email" value={email} onChange={setEmail} type="email" placeholder={person?.email ?? 'the address they sign in with'} />
          </>
        )}

        <div className="editor__actions">
          <button type="submit" className="button button--primary" disabled={pending || !ready}>
            {pending ? 'Setting up…' : `Give ${kind} access`}
          </button>
          <button type="button" className="button" onClick={onClose}>
            Cancel
          </button>
        </div>
        {error !== null && <p className="editor__error">{errorText(error)}</p>}
      </form>
    </Modal>
  )
}

// ---------------------------------------------------------------------------
// Fleets and oversight (BUS-2)
// ---------------------------------------------------------------------------

/**
 * Fleets across operators and the companies overseeing them. A fleet groups ships from any
 * customers — "the MinRes barges", whoever runs each — and an overseer is a company given sight
 * of it: Portways monitors the MinRes barges without operating one. Every account tied to an
 * overseer follows the fleet: add a ship and they see it, remove the company and they stop.
 */
function FleetsTab({ customers, partnerships, vessels }: { customers: readonly Customer[]; partnerships: readonly Partnership[]; vessels: readonly Vessel[] }): React.ReactNode {
  const fleets = useFleets()
  const accounts = useUserAccounts()
  const remove = useDeleteFleet()
  const [editing, setEditing] = useState<Fleet | 'new' | null>(null)
  const [choosingShips, setChoosingShips] = useState<Fleet | null>(null)
  const [choosingOverseers, setChoosingOverseers] = useState<Fleet | null>(null)

  if (fleets.isPending) return <Spinner label="Loading the fleets" />
  if (fleets.error !== null) return <ErrorPanel title="Could not load the fleets" error={fleets.error} />

  const customerName = (id: number | null) => customers.find((c) => c.id === id)?.name ?? 'no customer'
  const linked = (customerId: number) => (accounts.data ?? []).filter((a) => a.customerId === customerId && a.status === 'active').length

  return (
    <div className="swing-page">
      <section className="section fold fold--accent">
        <div className="section__header">
          <div>
            <h2 className="section__title">
              {fleets.data.length === 0 ? 'No fleets yet.' : `${fleets.data.length} ${fleets.data.length === 1 ? 'fleet' : 'fleets'}`}
            </h2>
            <p className="section__note">
              <Copy k="business.fleets-note">
                When one company watches ships it does not run — Portways monitoring the MinRes barges, whoever operates each — group those ships as a fleet and make that company its overseer. Everyone at the overseer then sees the fleet's ships, read only, and keeps seeing them as ships join or leave.
              </Copy>
            </p>
          </div>
          <button type="button" className="button button--primary" onClick={() => setEditing('new')}>
            New fleet
          </button>
        </div>
      </section>

      {fleets.data.map((fleet) => {
        const ships = partnerships.filter((p) => fleet.partnershipIds.includes(p.id))
        const overseers = customers.filter((c) => fleet.overseerCustomerIds.includes(c.id))
        return (
          <div key={fleet.id} className="biz-card">
            <div className="biz-card__head">
              <div>
                <span className="biz-card__name">{fleet.name}</span>
                {fleet.note !== null && <div className="muted">{fleet.note}</div>}
              </div>
              <span className="row-actions">
                <button type="button" className="button button--quiet" onClick={() => setEditing(fleet)}>
                  Rename
                </button>
                <button type="button" className="button button--quiet" disabled={remove.isPending} onClick={() => remove.mutate(fleet.id)}>
                  Remove fleet
                </button>
              </span>
            </div>
            <div className="biz-card__cols biz-card__cols--two">
              <div>
                <p className="nav__group swing-eyebrow">Ships · {ships.length}</p>
                {ships.length === 0 && <p className="dim">No ships in this fleet yet.</p>}
                <ul className="list-plain list-plain--tight">
                  {ships.map((ship) => (
                    <li key={ship.id}>
                      <strong>{shipLabel(ship.id, ship.abbrev, ship.name, vessels)}</strong> <span className="muted">· run by {customerName(ship.customerId)}</span>
                    </li>
                  ))}
                </ul>
                <button type="button" className="button" onClick={() => setChoosingShips(fleet)}>
                  Choose ships
                </button>
              </div>
              <div>
                <p className="nav__group swing-eyebrow">Overseen by · {overseers.length}</p>
                {overseers.length === 0 && <p className="dim">No company oversees this fleet yet.</p>}
                <ul className="list-plain list-plain--tight">
                  {overseers.map((c) => (
                    <li key={c.id}>
                      <strong>{c.name}</strong>{' '}
                      <span className="muted">
                        · {linked(c.id)} {linked(c.id) === 1 ? 'login follows' : 'logins follow'} this fleet
                      </span>
                    </li>
                  ))}
                </ul>
                <button type="button" className="button" onClick={() => setChoosingOverseers(fleet)}>
                  Choose overseers
                </button>
              </div>
            </div>
          </div>
        )
      })}

      {remove.error !== null && <p className="editor__error">{errorText(remove.error)}</p>}
      {editing !== null && <FleetForm {...(editing === 'new' ? {} : { existing: editing })} onClose={() => setEditing(null)} />}
      {choosingShips !== null && (
        <ChooseMany
          title={`Ships in ${choosingShips.name}`}
          note="Any customer's ships. The overseers' logins follow whatever is ticked."
          groups={customers
            .map((c) => ({ label: c.name, items: partnerships.filter((p) => p.customerId === c.id).map((p) => ({ id: p.id, label: shipLabel(p.id, p.abbrev, p.name, vessels) })) }))
            .filter((g) => g.items.length > 0)}
          chosen={choosingShips.partnershipIds}
          kind="ships"
          fleetId={choosingShips.id}
          onClose={() => setChoosingShips(null)}
        />
      )}
      {choosingOverseers !== null && (
        <ChooseMany
          title={`Who oversees ${choosingOverseers.name}`}
          note="Each company ticked sees every ship in the fleet, read only, through its oversight logins."
          groups={[{ label: 'Companies', items: customers.map((c) => ({ id: c.id, label: c.name })) }]}
          chosen={choosingOverseers.overseerCustomerIds}
          kind="overseers"
          fleetId={choosingOverseers.id}
          onClose={() => setChoosingOverseers(null)}
        />
      )}
    </div>
  )
}

function FleetForm({ existing, onClose }: { existing?: Fleet; onClose: () => void }): React.ReactNode {
  const create = useCreateFleet()
  const update = useUpdateFleet()
  const [name, setName] = useState(existing?.name ?? '')
  const [note, setNote] = useState(existing?.note ?? '')
  const pending = create.isPending || update.isPending
  return (
    <Modal title={existing === undefined ? 'New fleet' : `Rename ${existing.name}`} onClose={onClose}>
      <form
        className="editor"
        onSubmit={(event) => {
          event.preventDefault()
          const cleanNote = note === '' ? null : note
          if (existing === undefined) create.mutate({ name, note: cleanNote }, { onSuccess: onClose })
          else update.mutate({ id: existing.id, name, note: cleanNote }, { onSuccess: onClose })
        }}
      >
        <Field label="Name" value={name} onChange={setName} placeholder="MinRes barges" />
        <Field label="Note" value={note} onChange={setNote} placeholder="What the fleet is and why it is watched" />
        <div className="editor__actions">
          <button type="submit" className="button button--primary" disabled={pending || name.trim() === ''}>
            {pending ? 'Saving…' : 'Save'}
          </button>
          <button type="button" className="button" onClick={onClose}>
            Cancel
          </button>
        </div>
        {(create.error ?? update.error) !== null && <p className="editor__error">{errorText(create.error ?? update.error)}</p>}
      </form>
    </Modal>
  )
}

function ChooseMany({
  title,
  note,
  groups,
  chosen,
  kind,
  fleetId,
  onClose,
}: {
  title: string
  note: string
  groups: { label: string; items: { id: number; label: string }[] }[]
  chosen: readonly number[]
  kind: 'ships' | 'overseers'
  fleetId: number
  onClose: () => void
}): React.ReactNode {
  const setShips = useSetFleetShips()
  const setOverseers = useSetFleetOverseers()
  const [picked, setPicked] = useState<number[]>([...chosen])
  const save = kind === 'ships' ? setShips : setOverseers
  return (
    <Modal title={title} note={note} onClose={onClose}>
      <div className="editor">
        {groups.map((group) => (
          <fieldset key={group.label} className="field field--inline field--grow">
            <span className="field__label">{group.label}</span>
            <div className="check-list">
              {group.items.map((item) => (
                <label key={item.id} className="check check--box">
                  <input type="checkbox" checked={picked.includes(item.id)} onChange={() => setPicked(picked.includes(item.id) ? picked.filter((x) => x !== item.id) : [...picked, item.id])} />
                  <span className="dot" />
                  {item.label}
                </label>
              ))}
            </div>
          </fieldset>
        ))}
        <div className="editor__actions">
          <button type="button" className="button button--primary" disabled={save.isPending} onClick={() => save.mutate({ id: fleetId, ids: picked }, { onSuccess: onClose })}>
            {save.isPending ? 'Saving…' : 'Save'}
          </button>
          <button type="button" className="button" onClick={onClose}>
            Cancel
          </button>
        </div>
        {save.error !== null && <p className="editor__error">{errorText(save.error)}</p>}
      </div>
    </Modal>
  )
}

function errorText(error: unknown): string {
  return error instanceof ApiError ? error.message : String(error)
}
