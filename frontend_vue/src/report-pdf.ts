/**
 * Builds the PDF of a report in the browser, from the page as displayed in its
 * "paper" (light) style: every block marked with `data-pdf-block` becomes an
 * image placed on A4 pages, never cut unless taller than a page. A block marked
 * `data-pdf-keep-with-next` (group titles) moves to the next page with the
 * block that follows it.
 */
const PAGE_W = 210
const PAGE_H = 297
const MARGIN = 12
const FOOTER = 7
const CONTENT_W = PAGE_W - 2 * MARGIN
const PAGE_BOTTOM = PAGE_H - MARGIN - FOOTER
/** Largest gap kept between two blocks, in mm. */
const MAX_GAP = 6

export interface PdfProgress {
  (done: number, total: number): void
}

export async function downloadReportPdf(root: HTMLElement, fileName: string, onProgress?: PdfProgress) {
  const [{ jsPDF }, { toCanvas }] = await Promise.all([import('jspdf'), import('html-to-image')])
  const blocks = Array.from(root.querySelectorAll<HTMLElement>('[data-pdf-block]'))
  if (!blocks.length) throw new Error('Nothing to export.')

  const pxToMm = CONTENT_W / root.getBoundingClientRect().width
  const pdf = new jsPDF({ unit: 'mm', format: 'a4', orientation: 'portrait', compress: true })

  const rects = blocks.map((b) => b.getBoundingClientRect())
  const heights = rects.map((r) => r.height * pxToMm)
  let y = MARGIN

  for (let i = 0; i < blocks.length; i++) {
    const height = heights[i]
    const gap = i === 0 ? 0 : Math.min(MAX_GAP, Math.max(0, (rects[i].top - rects[i - 1].bottom) * pxToMm))
    // A group title needs room for itself and the first card under it.
    const needed = blocks[i].hasAttribute('data-pdf-keep-with-next') && i + 1 < blocks.length
      ? height + MAX_GAP + Math.min(heights[i + 1], PAGE_BOTTOM - MARGIN)
      : height
    let top = y + gap
    if (y > MARGIN && top + needed > PAGE_BOTTOM) {
      pdf.addPage()
      top = MARGIN
    }

    const canvas = await toCanvas(blocks[i], { pixelRatio: 2, backgroundColor: '#ffffff' })
    const image = canvas.toDataURL('image/jpeg', 0.9)
    const pageRoom = PAGE_BOTTOM - top
    if (height <= pageRoom) {
      pdf.addImage(image, 'JPEG', MARGIN, top, CONTENT_W, height, undefined, 'FAST')
      y = top + height
    } else {
      // Taller than what is left of a page: slice it over as many pages as needed.
      let offset = 0
      while (offset < height) {
        const room = PAGE_BOTTOM - top
        pdf.addImage(image, 'JPEG', MARGIN, top - offset, CONTENT_W, height, undefined, 'FAST')
        // Hide what spills into the margins of this page.
        pdf.setFillColor(255, 255, 255)
        if (offset > 0) pdf.rect(0, 0, PAGE_W, top, 'F')
        pdf.rect(0, PAGE_BOTTOM, PAGE_W, PAGE_H - PAGE_BOTTOM, 'F')
        offset += room
        if (offset < height) {
          pdf.addPage()
          top = MARGIN
        } else {
          y = PAGE_BOTTOM - (offset - height)
        }
      }
    }
    onProgress?.(i + 1, blocks.length)
  }

  const pages = pdf.getNumberOfPages()
  pdf.setFontSize(8)
  pdf.setTextColor(130)
  for (let p = 1; p <= pages; p++) {
    pdf.setPage(p)
    pdf.text(`${p} / ${pages}`, PAGE_W - MARGIN, PAGE_H - MARGIN + 1, { align: 'right' })
  }
  pdf.save(fileName)
}
