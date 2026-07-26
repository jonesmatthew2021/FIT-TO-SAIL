import { describe, expect, it } from 'vitest'
import { csvField, toCsv } from './csv'

interface Row {
  name: string
  days: number
  note: string | null
}

describe('CSV export', () => {
  it('quotes only what needs quoting', () => {
    expect(csvField('Smith')).toBe('Smith')
    expect(csvField('Smith, J')).toBe('"Smith, J"')
    expect(csvField('He said "no"')).toBe('"He said ""no"""')
    expect(csvField('line one\nline two')).toBe('"line one\nline two"')
  })

  it('renders empty for absent values rather than the string "null"', () => {
    expect(csvField(null)).toBe('')
    expect(csvField(undefined)).toBe('')
    expect(csvField(0)).toBe('0')
    expect(csvField(false)).toBe('false')
  })

  it('neutralises a field a spreadsheet would treat as a formula', () => {
    // Notes are free text typed by users. `=cmd|...` in a note must arrive as text.
    expect(csvField('=1+1')).toBe("'=1+1")
    expect(csvField('+61 400 000 000')).toBe("'+61 400 000 000")
    expect(csvField('-5')).toBe("'-5")
    expect(csvField('@handle')).toBe("'@handle")
    // A quoted field is still guarded inside the quotes.
    expect(csvField('=SUM(A1,A2)')).toBe('"\'=SUM(A1,A2)"')
  })

  it('writes a header row and CRLF line endings', () => {
    const rows: Row[] = [
      { name: 'Ann', days: 4, note: null },
      { name: 'Bo, R', days: -1, note: 'renewed' },
    ]
    const csv = toCsv(rows, [
      { header: 'Name', value: (row) => row.name },
      { header: 'Days', value: (row) => row.days },
      { header: 'Note', value: (row) => row.note },
    ])

    expect(csv).toBe('Name,Days,Note\r\nAnn,4,\r\n"Bo, R",\'-1,renewed\r\n')
  })

  it('writes only a header row for an empty list', () => {
    expect(toCsv([] as Row[], [{ header: 'Name', value: (row) => row.name }])).toBe('Name\r\n')
  })
})
