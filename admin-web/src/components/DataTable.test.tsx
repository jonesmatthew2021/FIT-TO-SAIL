import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { DataTable, type Column } from './DataTable'

interface Row {
  state: string
  name: string
}

/** The gap report arrives in the §5.4 worklist order; the table must not quietly re-rank it. */
const GAP_ORDER: Row[] = [
  { state: 'gap', name: 'Zara' },
  { state: 'expiring', name: 'Ann' },
  { state: 'unknown', name: 'Mo' },
]

const columns: Column<Row>[] = [
  { id: 'state', header: 'State', accessorFn: (row) => row.state },
  { id: 'name', header: 'Person', accessorFn: (row) => row.name },
]

function names(): string[] {
  return screen
    .getAllByRole('row')
    .slice(1) // drop the header row
    .map((row) => row.querySelectorAll('td')[1]?.textContent ?? '')
}

describe('DataTable', () => {
  it('renders rows in the order given, not sorted', () => {
    render(<DataTable rows={GAP_ORDER} columns={columns} />)
    expect(names()).toEqual(['Zara', 'Ann', 'Mo'])
  })

  it('sorts only when the user asks', async () => {
    const user = userEvent.setup()
    render(<DataTable rows={GAP_ORDER} columns={columns} />)

    await user.click(screen.getByRole('button', { name: /Person/ }))

    expect(names()).toEqual(['Ann', 'Mo', 'Zara'])
  })

  it('filters and reports how many rows are hidden', async () => {
    const user = userEvent.setup()
    render(<DataTable rows={GAP_ORDER} columns={columns} filterPlaceholder="Filter" />)

    expect(screen.getByText('3 rows')).toBeDefined()
    await user.type(screen.getByRole('searchbox', { name: 'Filter' }), 'Ann')

    expect(names()).toEqual(['Ann'])
    expect(screen.getByText('1 of 3 rows')).toBeDefined()
  })

  it('shows the empty message rather than an empty table body', () => {
    render(<DataTable rows={[] as Row[]} columns={columns} empty="No gaps." />)
    expect(screen.getByText('No gaps.')).toBeDefined()
  })
})
