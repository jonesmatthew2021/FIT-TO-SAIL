import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { keys, useAllPartnerships, useCustomerScope, usePeople } from '../api/queries'
import type { Person } from '../api/client'
import { useHasRole } from '../api/session'
import { UploadCertificates } from '../components/CertificatesOnFile'
import { AddCrewMember, UploadCrewList } from '../components/CrewUpload'
import { DataTable, type Column } from '../components/DataTable'
import { ErrorPanel } from '../components/ErrorPanel'
import { Spinner } from '../components/Spinner'

/** The roles that may file a certificate — the same two that decide on ADM-9. */
const FILERS = ['data_steward', 'system_administrator'] as const

/** The roles the server accepts for creating a person — mirrored to hide the controls. */
const CREW_EDITORS = ['crew_coordinator', 'data_steward', 'system_administrator'] as const

/**
 * ADM-5 — the crew directory.
 *
 * The list is row-scoped by the server (AUTH-2): a Vessel Master sees their partnerships and a
 * crew member sees one row, without this screen passing a filter. That is the point of scoping
 * centrally — there is no parameter here to get wrong.
 *
 * §6 asks for a compliance roll-up column. It is not here, because a roll-up is only defined
 * against a swing (§5.2 evaluates a person *for a crew change*), and there is no swing-free
 * evaluation endpoint. Showing a number computed against some arbitrarily chosen swing would be
 * worse than showing none. The roll-up appears on the person detail page, against a swing you
 * pick.
 */
export function People(): React.ReactNode {
  const people = usePeople()
  const navigate = useNavigate()
  const canFile = useHasRole(...FILERS)
  const canEditCrew = useHasRole(...CREW_EDITORS)
  const scope = useCustomerScope()
  const partnerships = useAllPartnerships()
  const client = useQueryClient()
  const [addingOne, setAddingOne] = useState(false)
  const [uploadingList, setUploadingList] = useState(false)
  const ship = scope.operationId === null ? undefined : partnerships.data?.find((p) => p.id === scope.operationId)

  if (people.isPending) return <Spinner label="Loading people" />
  if (people.error !== null) return <ErrorPanel title="Could not load people" error={people.error} />

  const columns: Column<Person>[] = [
    {
      id: 'sam',
      header: 'Sam #',
      accessorFn: (row) => row.sam,
      cell: ({ row }) => <span className="mono">{row.original.sam}</span>,
    },
    { id: 'name', header: 'Name', accessorFn: (row) => row.name },
    { id: 'position', header: 'Position', accessorFn: (row) => row.positionName },
    {
      id: 'tier',
      header: 'Tier',
      accessorFn: (row) => row.tier ?? '',
      cell: ({ row }) => row.original.tier ?? <span className="dim">—</span>,
    },
    { id: 'partnership', header: 'Partnership', accessorFn: (row) => row.partnershipAbbrev },
    {
      id: 'status',
      header: 'Status',
      accessorFn: (row) => row.status,
      cell: ({ row }) => (
        <span className={`chip chip--${row.original.status === 'active' ? 'good' : 'muted'}`}>
          {row.original.status}
        </span>
      ),
    },
    {
      id: 'email',
      header: 'Email',
      accessorFn: (row) => row.email ?? '',
      cell: ({ row }) => row.original.email ?? <span className="dim">—</span>,
    },
  ]

  return (
    <div className="screen">
      <header className="screen__header">
        <h1 className="screen__title">People &amp; holdings</h1>
        <p className="screen__subtitle">
          {people.data.length} crew visible to your roles. The directory is the system of record for
          qualification holdings; each person's page carries the scans behind them.
        </p>
      </header>

      {/* A batch of certificates from the office: the model reads each one and suggests whose it
          is; the uploader confirms, or adds the crew member the roster does not have yet. */}
      {/* A ship's crew is its own: the two doors for loading it are fixed to the ship in view. */}
      <div className="row-actions">
        {canEditCrew && ship !== undefined && (
          <>
            <button type="button" className="button button--primary" onClick={() => setAddingOne(true)}>
              Add crew member
            </button>
            <button type="button" className="button" onClick={() => setUploadingList(true)}>
              Upload crew list
            </button>
          </>
        )}
        {canFile && <UploadCertificates />}
      </div>

      {addingOne && ship !== undefined && <AddCrewMember ship={ship} onClose={() => setAddingOne(false)} />}
      {uploadingList && ship !== undefined && (
        <UploadCrewList
          ship={ship}
          onClose={() => setUploadingList(false)}
          onDone={() => void client.invalidateQueries({ queryKey: keys.people })}
        />
      )}

      <DataTable
        rows={people.data}
        columns={columns}
        filterPlaceholder="Filter by name, Sam #, position…"
        onRowClick={(person) => void navigate(`/people/${person.id}${scope.suffix}`)}
        csv={{
          filename: 'people.csv',
          columns: [
            { header: 'Sam #', value: (row) => row.sam },
            { header: 'Name', value: (row) => row.name },
            { header: 'Position', value: (row) => row.positionName },
            { header: 'Tier', value: (row) => row.tier },
            { header: 'Partnership', value: (row) => row.partnershipAbbrev },
            { header: 'Status', value: (row) => row.status },
            { header: 'Email', value: (row) => row.email },
          ],
        }}
      />
    </div>
  )
}
