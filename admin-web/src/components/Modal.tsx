import { useEffect, useRef } from 'react'

/**
 * A modal on the native `<dialog>` element.
 *
 * The browser supplies the parts a hand-rolled overlay always gets subtly wrong — focus is
 * trapped in the dialog, Escape closes it, and everything behind it is inert. This component
 * adds only what the element does not: backdrop-click-to-close and the app's frame around the
 * content.
 *
 * The parent owns the open state: render the modal to open it, and `onClose` fires exactly once
 * however it closes (Escape, the × button, or the backdrop) so the parent can unrender it.
 */
export function Modal({
  title,
  note,
  wide = false,
  onClose,
  children,
}: {
  title: string
  /** One line under the title — the modal's own subtitle. */
  note?: string
  /** For table-heavy content; the default width suits facts and short lists. */
  wide?: boolean
  onClose: () => void
  children: React.ReactNode
}): React.ReactNode {
  const ref = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialog = ref.current
    if (dialog === null || dialog.open) return
    // jsdom implements <dialog> without the top layer; the fallback keeps the component testable.
    if (typeof dialog.showModal === 'function') dialog.showModal()
    else dialog.setAttribute('open', '')
  }, [])

  return (
    <dialog
      ref={ref}
      className={wide ? 'modal modal--wide' : 'modal'}
      aria-label={title}
      onClose={onClose}
      onClick={(event) => {
        // A backdrop click targets the dialog element itself; the frame catches everything inside.
        if (event.target === ref.current) ref.current?.close()
      }}
    >
      <div className="modal__frame">
        <header className="modal__header">
          <div>
            <h2 className="modal__title">{title}</h2>
            {note !== undefined && <p className="modal__note">{note}</p>}
          </div>
          <button
            type="button"
            className="modal__close"
            aria-label="Close"
            onClick={() => ref.current?.close()}
          >
            ×
          </button>
        </header>
        <div className="modal__body">{children}</div>
      </div>
    </dialog>
  )
}
