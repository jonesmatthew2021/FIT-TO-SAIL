import { useState } from 'react'
import {
  flexRender,
  getCoreRowModel,
  getFilteredRowModel,
  getSortedRowModel,
  useReactTable,
  type ColumnDef,
  type SortingState,
} from '@tanstack/react-table'
import { downloadCsv, toCsv, type CsvColumn } from '../domain/csv'

/**
 * TanStack types a column by the type of the value it accesses. A heterogeneous table has no
 * single such type, and the library's own public signature is `ColumnDef<T, any>` for exactly
 * that reason. Aliased once here rather than spelled out at every call site.
 */
export type Column<T> = ColumnDef<T, any>

/**
 * The dense-table workhorse: the planner, gap report, people list and holdings grid are all this
 * component with different columns.
 *
 * Two behaviours are deliberate:
 *
 *  - **Unsorted by default.** Several of these lists arrive in a meaningful order — the gap
 *    report is the §5.4 worklist, ordered gap → expiring → unknown → review → pending → exempt.
 *    Applying a default sort here would quietly discard the engine's ranking, so the initial
 *    order is the server's and sorting is something the user asks for.
 *  - **CSV exports what the user is looking at**, filter and sort included (§6, "every list
 *    exports CSV"). Exporting the unfiltered set would silently hand over rows the screen was
 *    hiding — including, for a scoped role, rows they are not meant to have.
 */
export interface DataTableProps<T> {
  readonly rows: readonly T[]
  readonly columns: Column<T>[]
  readonly csv?: { readonly filename: string; readonly columns: readonly CsvColumn<T>[] }
  readonly filterPlaceholder?: string
  readonly empty?: string
  readonly onRowClick?: (row: T) => void
  readonly rowClassName?: (row: T) => string | undefined
}

export function DataTable<T>({
  rows,
  columns,
  csv,
  filterPlaceholder,
  empty = 'Nothing to show.',
  onRowClick,
  rowClassName,
}: DataTableProps<T>): React.ReactNode {
  const [sorting, setSorting] = useState<SortingState>([])
  const [filter, setFilter] = useState('')

  const table = useReactTable({
    data: rows as T[],
    columns,
    state: { sorting, globalFilter: filter },
    onSortingChange: setSorting,
    onGlobalFilterChange: setFilter,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
  })

  const visible = table.getRowModel().rows

  return (
    <div className="table-block">
      <div className="table-block__toolbar">
        {filterPlaceholder !== undefined && (
          <input
            className="input input--filter"
            type="search"
            value={filter}
            placeholder={filterPlaceholder}
            aria-label={filterPlaceholder}
            onChange={(event) => setFilter(event.target.value)}
          />
        )}
        <span className="table-block__count">
          {visible.length === rows.length
            ? `${rows.length} ${rows.length === 1 ? 'row' : 'rows'}`
            : `${visible.length} of ${rows.length} rows`}
        </span>
        {csv !== undefined && (
          <button
            type="button"
            className="button button--quiet"
            onClick={() =>
              downloadCsv(
                csv.filename,
                toCsv(
                  visible.map((row) => row.original),
                  csv.columns,
                ),
              )
            }
          >
            Export CSV
          </button>
        )}
      </div>

      <div className="table-scroll">
        <table className="table">
          <thead>
            {table.getHeaderGroups().map((group) => (
              <tr key={group.id}>
                {group.headers.map((header) => {
                  const sortable = header.column.getCanSort()
                  const direction = header.column.getIsSorted()
                  return (
                    <th
                      key={header.id}
                      scope="col"
                      aria-sort={
                        direction === 'asc'
                          ? 'ascending'
                          : direction === 'desc'
                            ? 'descending'
                            : undefined
                      }
                    >
                      {sortable ? (
                        <button
                          type="button"
                          className="table__sort"
                          onClick={header.column.getToggleSortingHandler()}
                        >
                          {flexRender(header.column.columnDef.header, header.getContext())}
                          <span aria-hidden="true" className="table__sort-marker">
                            {direction === 'asc' ? '▲' : direction === 'desc' ? '▼' : ''}
                          </span>
                        </button>
                      ) : (
                        flexRender(header.column.columnDef.header, header.getContext())
                      )}
                    </th>
                  )
                })}
              </tr>
            ))}
          </thead>
          <tbody>
            {visible.length === 0 && (
              <tr>
                <td className="table__empty" colSpan={table.getAllLeafColumns().length}>
                  {empty}
                </td>
              </tr>
            )}
            {visible.map((row) => (
              <tr
                key={row.id}
                className={[
                  onRowClick === undefined ? '' : 'table__row--clickable',
                  rowClassName?.(row.original) ?? '',
                ]
                  .filter(Boolean)
                  .join(' ')}
                onClick={
                  onRowClick === undefined
                    ? undefined
                    : (event) => {
                        // A click on the row's own controls — a person link, "Raise request" —
                        // belongs to them, not to the row.
                        const target = event.target as HTMLElement
                        if (target.closest('a, button, input, select, label') !== null) return
                        onRowClick(row.original)
                      }
                }
              >
                {row.getVisibleCells().map((cell) => (
                  <td key={cell.id}>{flexRender(cell.column.columnDef.cell, cell.getContext())}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
