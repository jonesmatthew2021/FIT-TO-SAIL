import { createContext, useContext, useState } from 'react'
import { useClearCopy, usePageCopy, useSetCopy } from '../api/queries'
import { ApiError } from '../api/client'
import { useHasRole } from '../api/session'

/** The roles that may change what a page says — the two that own how the office reads. */
const COPY_EDITORS = ['compliance_lead', 'system_administrator'] as const

interface CopyState {
  editing: boolean
  setEditing: (editing: boolean) => void
  canEdit: boolean
  overrides: ReadonlyMap<string, string>
}

const CopyContext = createContext<CopyState>({
  editing: false,
  setEditing: () => undefined,
  canEdit: false,
  overrides: new Map(),
})

/**
 * Editable page text.
 *
 * Any sentence wrapped in [Copy] can be rewritten by the office from the page itself: switch on
 * "Edit text" in the header, press the sentence, type, save. The new wording is stored on the
 * server under the sentence's key and shown to everyone; "Back to the original" forgets it. The
 * code keeps the original as the default, so a key nobody has touched reads as written here.
 */
export function CopyProvider({ children }: { children: React.ReactNode }): React.ReactNode {
  const copy = usePageCopy()
  const canEdit = useHasRole(...COPY_EDITORS)
  const [editing, setEditing] = useState(false)
  const overrides = new Map((copy.data ?? []).map((entry) => [entry.key, entry.text]))
  return <CopyContext.Provider value={{ editing: editing && canEdit, setEditing, canEdit, overrides }}>{children}</CopyContext.Provider>
}

export function useCopyEditing(): Pick<CopyState, 'editing' | 'setEditing' | 'canEdit'> {
  const { editing, setEditing, canEdit } = useContext(CopyContext)
  return { editing, setEditing, canEdit }
}

/** The header's switch. */
export function EditTextToggle(): React.ReactNode {
  const { editing, setEditing, canEdit } = useCopyEditing()
  if (!canEdit) return null
  return (
    <button
      type="button"
      className={editing ? 'button button--primary' : 'button'}
      aria-pressed={editing}
      title="Rewrite any sentence on the page that shows a pencil"
      onClick={() => setEditing(!editing)}
    >
      {editing ? 'Done editing text' : 'Edit text'}
    </button>
  )
}

/**
 * One piece of page text. `k` is its key — stable, dotted, lower case (`people.upload-note`);
 * the child is the original wording. Plain text only: an edit is a sentence, not markup, and
 * line breaks are kept.
 */
export function Copy({ k, children }: { k: string; children: string }): React.ReactNode {
  const { editing, overrides } = useContext(CopyContext)
  const text = overrides.get(k) ?? children
  if (!editing) return <span className="copy">{text}</span>
  return <CopyEditor k={k} original={children} current={text} changed={overrides.has(k)} />
}

function CopyEditor({ k, original, current, changed }: { k: string; original: string; current: string; changed: boolean }): React.ReactNode {
  const save = useSetCopy()
  const clear = useClearCopy()
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState(current)

  if (!open) {
    return (
      <button
        type="button"
        className={changed ? 'copy copy--editable copy--changed' : 'copy copy--editable'}
        title={changed ? 'Rewritten — press to change or restore' : 'Press to rewrite'}
        onClick={() => {
          setDraft(current)
          setOpen(true)
        }}
      >
        {current}
        <span className="copy__pencil" aria-hidden="true">
          ✎
        </span>
      </button>
    )
  }

  return (
    <span className="copy copy--open">
      <textarea
        className="input copy__textarea"
        value={draft}
        rows={Math.min(8, Math.max(2, draft.split('\n').length + 1))}
        autoFocus
        onChange={(event) => setDraft(event.target.value)}
      />
      <span className="row-actions copy__actions">
        <button
          type="button"
          className="button button--primary button--quiet"
          disabled={save.isPending || draft.trim() === ''}
          onClick={() => save.mutate({ key: k, text: draft.trim() }, { onSuccess: () => setOpen(false) })}
        >
          {save.isPending ? 'Saving…' : 'Save'}
        </button>
        {changed && (
          <button
            type="button"
            className="button button--quiet"
            disabled={clear.isPending}
            title={`Original: ${original}`}
            onClick={() => clear.mutate(k, { onSuccess: () => setOpen(false) })}
          >
            Back to the original
          </button>
        )}
        <button type="button" className="button button--quiet" onClick={() => setOpen(false)}>
          Cancel
        </button>
        {(save.error !== null || clear.error !== null) && (
          <span className="editor__error">{errorText(save.error ?? clear.error)}</span>
        )}
      </span>
    </span>
  )
}

function errorText(error: unknown): string {
  return error instanceof ApiError ? error.message : String(error)
}
