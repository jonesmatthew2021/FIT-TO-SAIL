import { PDFDocument, StandardFonts, rgb, type PDFFont, type PDFPage } from 'pdf-lib'

/**
 * A written-down report as a PDF — the Coolibah portal's `saveReportPDF`, kept to what it did:
 * a title, a line under it, a paragraph of introduction, then groups of items, each item a code,
 * a title, a status word and a few lines of notes. Plain Helvetica, A4, page numbers.
 *
 * Built here rather than on the server because it is the page's own view written down — the
 * numbers on it are the engine's, already on screen — and the office wants it in their hand
 * before the meeting, not in a queue.
 */
export interface ReportItem {
  code: string
  title: string
  status: string
  notes: string[]
}

export interface ReportGroup {
  heading: string
  meta?: string
  blurb?: string
  items: ReportItem[]
}

export interface Report {
  title: string
  subtitle: string
  intro?: string
  empty?: string
  groups: ReportGroup[]
  filename: string
}

const PAGE = { width: 595.28, height: 841.89 }
const MARGIN = 44
const INK = rgb(0.1, 0.11, 0.14)
const MUTED = rgb(0.42, 0.44, 0.5)
const RULE = rgb(0.85, 0.86, 0.9)
const ACCENT = rgb(0.36, 0.33, 0.7)

export async function saveReportPdf(report: Report): Promise<void> {
  const pdf = await PDFDocument.create()
  const regular = await pdf.embedFont(StandardFonts.Helvetica)
  const bold = await pdf.embedFont(StandardFonts.HelveticaBold)
  const mono = await pdf.embedFont(StandardFonts.Courier)
  const writer = new Writer(pdf, regular, bold, mono)

  writer.text(report.title, bold, 18, INK)
  writer.gap(4)
  writer.text(report.subtitle, regular, 9.5, MUTED)
  writer.gap(10)
  if (report.intro !== undefined && report.intro !== '') {
    for (const paragraph of report.intro.split('\n\n')) {
      writer.text(paragraph, regular, 10.5, INK)
      writer.gap(6)
    }
    writer.gap(4)
  }

  const anything = report.groups.some((group) => group.items.length > 0)
  if (!anything && report.empty !== undefined) writer.text(report.empty, regular, 11, INK)

  for (const group of report.groups) {
    if (group.items.length === 0) continue
    writer.gap(8)
    writer.rule()
    writer.gap(8)
    writer.text(group.heading.toUpperCase() + (group.meta !== undefined && group.meta !== '' ? `   ${group.meta}` : ''), bold, 9.5, ACCENT, 0.6)
    if (group.blurb !== undefined && group.blurb !== '') {
      writer.gap(3)
      writer.text(group.blurb, regular, 9, MUTED)
    }
    writer.gap(6)
    for (const item of group.items) {
      writer.need(48)
      const head = item.code !== '' ? `${item.code}  ${item.title}` : item.title
      writer.textWithTag(head, bold, 10.5, INK, item.status, mono)
      for (const note of item.notes) {
        if (note.trim() === '') continue
        writer.text(note, regular, 9.5, MUTED, 0, 14)
      }
      writer.gap(7)
    }
  }

  writer.finish(report.title)
  const bytes = await pdf.save()
  const buffer = bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer
  const url = URL.createObjectURL(new Blob([buffer], { type: 'application/pdf' }))
  const link = document.createElement('a')
  link.href = url
  link.download = report.filename
  document.body.append(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
}

/** Lines onto pages: wraps to the margin, breaks pages, numbers them at the end. */
class Writer {
  private page: PDFPage
  private y = PAGE.height - MARGIN
  private readonly pages: PDFPage[] = []

  constructor(
    private readonly pdf: PDFDocument,
    private readonly regular: PDFFont,
    private readonly bold: PDFFont,
    private readonly mono: PDFFont,
  ) {
    this.page = this.newPage()
  }

  private newPage(): PDFPage {
    const page = this.pdf.addPage([PAGE.width, PAGE.height])
    this.pages.push(page)
    this.y = PAGE.height - MARGIN
    return page
  }

  need(height: number): void {
    if (this.y - height < MARGIN + 20) this.page = this.newPage()
  }

  gap(points: number): void {
    this.y -= points
  }

  rule(): void {
    this.need(4)
    this.page.drawLine({ start: { x: MARGIN, y: this.y }, end: { x: PAGE.width - MARGIN, y: this.y }, thickness: 0.6, color: RULE })
  }

  text(content: string, font: PDFFont, size: number, color: ReturnType<typeof rgb>, letterSpacing = 0, indent = 0): void {
    const width = PAGE.width - MARGIN * 2 - indent
    for (const line of wrap(content, font, size, width)) {
      this.need(size * 1.5)
      this.page.drawText(line, { x: MARGIN + indent, y: this.y - size, size, font, color, ...(letterSpacing > 0 ? { characterSpacing: letterSpacing } : {}) })
      this.y -= size * 1.45
    }
  }

  /** A heading line with a status word set at the right edge in the mono face. */
  textWithTag(content: string, font: PDFFont, size: number, color: ReturnType<typeof rgb>, tag: string, tagFont: PDFFont): void {
    const tagSize = 8.5
    const tagWidth = tag === '' ? 0 : tagFont.widthOfTextAtSize(tag, tagSize) + 12
    const width = PAGE.width - MARGIN * 2 - tagWidth
    const lines = wrap(content, font, size, width)
    lines.forEach((line, index) => {
      this.need(size * 1.5)
      this.page.drawText(line, { x: MARGIN, y: this.y - size, size, font, color })
      if (index === 0 && tag !== '') {
        this.page.drawText(tag, { x: PAGE.width - MARGIN - tagWidth + 12, y: this.y - size + 1, size: tagSize, font: tagFont, color: MUTED })
      }
      this.y -= size * 1.45
    })
    void this.regular
    void this.bold
    void this.mono
  }

  finish(title: string): void {
    this.pages.forEach((page, index) => {
      const label = `${title} · page ${index + 1} of ${this.pages.length}`
      page.drawText(label, { x: MARGIN, y: MARGIN - 18, size: 8, font: this.regular, color: MUTED })
    })
  }
}

function wrap(content: string, font: PDFFont, size: number, width: number): string[] {
  const lines: string[] = []
  for (const paragraph of content.replace(/\r/g, '').split('\n')) {
    const words = paragraph.split(/\s+/).filter((word) => word !== '')
    if (words.length === 0) {
      lines.push('')
      continue
    }
    let line = ''
    for (const word of words) {
      const candidate = line === '' ? word : `${line} ${word}`
      if (font.widthOfTextAtSize(candidate, size) <= width || line === '') line = candidate
      else {
        lines.push(line)
        line = word
      }
    }
    lines.push(line)
  }
  return lines
}
