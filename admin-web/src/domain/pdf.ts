import { PDFDocument } from 'pdf-lib'

/** A4 in PDF points; a scan larger than the page is scaled down to fit, never up. */
const A4 = { width: 595.28, height: 841.89 }

/**
 * Whatever was picked, as a PDF.
 *
 * A PDF is returned as it is. A photo or scan in an image format the browser can decode — JPEG,
 * PNG, WebP, GIF, BMP — is drawn onto one PDF page, portrait or landscape to suit, and comes back
 * as a `.pdf` of the same name. Anything else (a Word file, an HEIC photo off an iPhone, a zip) is
 * refused with a sentence saying so, because there is nothing here that can read it: the office
 * saves it as a PDF and uploads again.
 *
 * Done in the browser so the server only ever stores PDFs under the office's naming rule.
 */
export async function asPdf(file: File): Promise<File> {
  const lower = file.name.toLowerCase()
  if (file.type === 'application/pdf' || (file.type === '' && lower.endsWith('.pdf'))) return file
  if (!file.type.startsWith('image/')) {
    throw new Error(`${file.name} is not a PDF or a picture, so it cannot be converted here — save it as a PDF and upload that.`)
  }

  let bitmap: ImageBitmap
  try {
    bitmap = await createImageBitmap(file)
  } catch {
    throw new Error(`${file.name} is a picture format this browser cannot open (an HEIC photo, say) — save it as JPEG or PDF and upload that.`)
  }

  const canvas = document.createElement('canvas')
  canvas.width = bitmap.width
  canvas.height = bitmap.height
  const context = canvas.getContext('2d')
  if (context === null) throw new Error('This browser could not draw the picture to convert it.')
  context.drawImage(bitmap, 0, 0)
  bitmap.close()
  const png = await new Promise<Blob>((resolve, reject) =>
    canvas.toBlob((blob) => (blob === null ? reject(new Error('The picture could not be encoded.')) : resolve(blob)), 'image/png'),
  )

  const pdf = await PDFDocument.create()
  const image = await pdf.embedPng(await png.arrayBuffer())
  const landscape = image.width > image.height
  const page = landscape ? { width: A4.height, height: A4.width } : A4
  const scale = Math.min(page.width / image.width, page.height / image.height, 1)
  const width = image.width * scale
  const height = image.height * scale
  pdf.addPage([page.width, page.height]).drawImage(image, {
    x: (page.width - width) / 2,
    y: (page.height - height) / 2,
    width,
    height,
  })
  const bytes = await pdf.save()
  const buffer = bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer
  return new File([buffer], `${file.name.replace(/\.[^.]+$/, '')}.pdf`, { type: 'application/pdf' })
}
