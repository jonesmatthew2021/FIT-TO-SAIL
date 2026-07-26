/**
 * CSV export — spec §6, "every list exports CSV".
 *
 * Escaping follows RFC 4180: quote a field whenever it contains a delimiter, a quote or a
 * newline, and double any embedded quote. One extra rule beyond the RFC: a field whose first
 * character is `=`, `+`, `-` or `@` is prefixed with a single quote. Spreadsheets treat those as
 * formulas, and this data includes free-text notes typed by users — a note beginning `=` should
 * arrive as text, not execute.
 */

export interface CsvColumn<T> {
  readonly header: string
  readonly value: (row: T) => string | number | boolean | null | undefined
}

const NEEDS_QUOTING = /[",\r\n]/
const FORMULA_PREFIX = /^[=+\-@\t\r]/

export function csvField(value: string | number | boolean | null | undefined): string {
  if (value === null || value === undefined) return ''
  const text = String(value)
  const guarded = FORMULA_PREFIX.test(text) ? `'${text}` : text
  if (!NEEDS_QUOTING.test(guarded)) return guarded
  return `"${guarded.replaceAll('"', '""')}"`
}

export function toCsv<T>(rows: readonly T[], columns: readonly CsvColumn<T>[]): string {
  const lines = [columns.map((column) => csvField(column.header)).join(',')]
  for (const row of rows) {
    lines.push(columns.map((column) => csvField(column.value(row))).join(','))
  }
  // CRLF per RFC 4180, and a trailing newline so `wc -l` and Excel agree on the row count.
  return lines.join('\r\n') + '\r\n'
}

/** Triggers a browser download. Kept separate from [toCsv] so the formatting stays testable. */
export function downloadCsv(filename: string, content: string): void {
  // Leading BOM: without it Excel on Windows reads UTF-8 as the local codepage and mangles any
  // non-ASCII vessel or person name.
  const blob = new Blob(['\uFEFF', content], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
