import { useState } from 'react'
import { useCreatePerson, usePositions } from '../api/queries'
import { ApiError, api, type Partnership, type Person, type Position } from '../api/client'
import { Modal } from './Modal'

/**
 * Loading a ship's crew: one at a time, or a whole crew list at once.
 *
 * A ship's roster is its own — the people on it belong to that operation — so both doors here
 * are fixed to the ship in view and only ask for what the roster needs: a name in the roster's
 * form, the employee id, the position. A crew list is pasted or dropped as text (a spreadsheet's
 * "copy" works: tab- or comma-separated, a header row or not), checked row by row against the
 * positions the system knows, and shown back before anything is created. Each person is created
 * through the same door the certificate intake uses, with the same refusal of a re-used id.
 */
export function AddCrewMember({ ship, onClose }: { ship: Partnership; onClose: () => void }): React.ReactNode {
  const positions = usePositions()
  const create = useCreatePerson()
  const [name, setName] = useState('')
  const [sam, setSam] = useState('')
  const [positionId, setPositionId] = useState<number | null>(null)

  return (
    <Modal title={`Add a crew member to ${ship.abbrev}`} note={ship.name} onClose={onClose}>
      <form
        className="editor"
        onSubmit={(event) => {
          event.preventDefault()
          if (positionId === null) return
          create.mutate(
            { name: rosterForm(name), sam, positionId, partnershipId: ship.id, email: null },
            { onSuccess: onClose },
          )
        }}
      >
        <label className="field field--inline field--grow">
          <span className="field__label">Name (SURNAME, Given)</span>
          <input className="input" value={name} onChange={(event) => setName(event.target.value)} placeholder="EVANS, Brenton" />
        </label>
        <label className="field field--inline">
          <span className="field__label">Sam #</span>
          <input className="input input--level" value={sam} onChange={(event) => setSam(event.target.value)} placeholder="e91754E" />
        </label>
        <label className="field field--inline">
          <span className="field__label">Position</span>
          <select className="input" value={positionId ?? ''} onChange={(event) => setPositionId(event.target.value === '' ? null : Number(event.target.value))}>
            <option value="">Choose…</option>
            {(positions.data ?? []).map((position) => (
              <option key={position.id} value={position.id}>
                {position.name}
              </option>
            ))}
          </select>
        </label>
        <div className="editor__actions">
          <button
            type="submit"
            className="button button--primary"
            disabled={create.isPending || name.trim() === '' || sam.trim() === '' || positionId === null}
          >
            {create.isPending ? 'Adding…' : 'Add crew member'}
          </button>
          <button type="button" className="button" onClick={onClose}>
            Cancel
          </button>
        </div>
        {create.error !== null && <p className="editor__error">{errorText(create.error)}</p>}
      </form>
    </Modal>
  )
}

interface CrewRow {
  readonly line: number
  readonly name: string
  readonly sam: string
  readonly positionName: string
  readonly position: Position | undefined
  readonly problem: string | null
}

type Outcome = { readonly line: number; readonly ok: true; readonly person: Person } | { readonly line: number; readonly ok: false; readonly error: string }

export function UploadCrewList({ ship, onClose, onDone }: { ship: Partnership; onClose: () => void; onDone: () => void }): React.ReactNode {
  const positions = usePositions()
  const [text, setText] = useState('')
  const [running, setRunning] = useState(false)
  const [outcomes, setOutcomes] = useState<Outcome[] | null>(null)

  const rows = parseCrewList(text, positions.data ?? [])
  const ready = rows.filter((row) => row.problem === null)

  async function run() {
    setRunning(true)
    const results: Outcome[] = []
    // One at a time, in the list's order: the server's answer to each row (a re-used id, say) is
    // worth reading in order, and forty parallel writes would land as forty audit events in no
    // order at all.
    for (const row of ready) {
      try {
        const person = await api.createPerson({
          name: row.name,
          sam: row.sam,
          positionId: row.position!.id,
          partnershipId: ship.id,
          email: null,
        })
        results.push({ line: row.line, ok: true, person })
      } catch (error) {
        results.push({ line: row.line, ok: false, error: errorText(error) })
      }
    }
    setOutcomes(results)
    setRunning(false)
    onDone()
  }

  return (
    <Modal title={`Upload a crew list for ${ship.abbrev}`} note={ship.name} wide onClose={onClose}>
      {outcomes === null && (
        <div className="editor">
          <p className="note">
            Paste the crew list — one person per line: <strong>name, Sam #, position</strong>. Copying
            the three columns from a spreadsheet works as it is; a header row is skipped. Names are
            written as SURNAME, Given (a "Brenton Evans" is turned round for you).
          </p>
          <label className="field field--grow">
            <span className="field__label">Crew list</span>
            <textarea
              className="input"
              rows={10}
              value={text}
              onChange={(event) => setText(event.target.value)}
              placeholder={'EVANS, Brenton\te91754E\tMaster\nFARMER, Evan\tf86373F\tMaster'}
            />
          </label>
          <label className="field field--inline">
            <span className="field__label">Or a file</span>
            <input
              className="input"
              type="file"
              accept=".csv,.tsv,.txt,text/plain,text/csv"
              onChange={(event) => {
                const file = event.target.files?.[0]
                if (file === undefined) return
                void file.text().then((content) => setText(content))
              }}
            />
          </label>

          {rows.length > 0 && (
            <div className="table-block table-block--plain">
              <table className="table">
                <thead>
                  <tr>
                    <th scope="col">Line</th>
                    <th scope="col">Name</th>
                    <th scope="col">Sam #</th>
                    <th scope="col">Position</th>
                    <th scope="col">Check</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <tr key={row.line}>
                      <td className="mono muted">{row.line}</td>
                      <td>{row.name}</td>
                      <td className="mono">{row.sam}</td>
                      <td>{row.position?.name ?? row.positionName}</td>
                      <td>
                        {row.problem === null ? (
                          <span className="chip chip--good chip--small">ready</span>
                        ) : (
                          <span className="chip chip--critical chip--small">{row.problem}</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="editor__actions">
            <button
              type="button"
              className="button button--primary"
              disabled={running || ready.length === 0}
              onClick={() => void run()}
            >
              {running ? 'Adding…' : `Add ${ready.length} ${ready.length === 1 ? 'crew member' : 'crew members'}`}
            </button>
            <button type="button" className="button" onClick={onClose}>
              Cancel
            </button>
            {rows.length > ready.length && (
              <span className="dim">{rows.length - ready.length} with a problem will be skipped.</span>
            )}
          </div>
        </div>
      )}

      {outcomes !== null && (
        <div className="editor">
          <p className="note">
            {outcomes.filter((o) => o.ok).length} added, {outcomes.filter((o) => !o.ok).length} refused.
          </p>
          <ul className="list-plain list-plain--tight">
            {outcomes.map((outcome) => (
              <li key={outcome.line} className="tag-row">
                <span className="mono muted">{outcome.line}</span>
                {outcome.ok ? (
                  <>
                    <span className="chip chip--good chip--small">added</span>
                    <span>
                      {outcome.person.name} <span className="mono muted">{outcome.person.sam}</span>
                    </span>
                  </>
                ) : (
                  <>
                    <span className="chip chip--critical chip--small">refused</span>
                    <span>{outcome.error}</span>
                  </>
                )}
              </li>
            ))}
          </ul>
          <div className="editor__actions">
            <button type="button" className="button button--primary" onClick={onClose}>
              Done
            </button>
          </div>
        </div>
      )}
    </Modal>
  )
}

/** Tab- or comma-separated lines of name, sam, position; a header row is dropped; blanks skipped. */
export function parseCrewList(text: string, positions: readonly Position[]): CrewRow[] {
  const byName = new Map(positions.map((position) => [position.name.toLowerCase(), position]))
  const seen = new Set<string>()
  return text
    .split(/\r?\n/)
    .map((raw, index) => ({ raw: raw.trim(), line: index + 1 }))
    .filter(({ raw }) => raw !== '')
    .filter(({ raw }, index) => !(index === 0 && /^(name|surname|crew)/i.test(raw) && /sam|position|rank/i.test(raw)))
    .map(({ raw, line }) => {
      const cells = raw.split(raw.includes('\t') ? '\t' : ',').map((cell) => cell.trim().replace(/^"|"$/g, ''))
      // A name written "EVANS, Brenton" splits on the comma; stitch it back when a fourth cell appears.
      const parts = !raw.includes('\t') && cells.length >= 4 ? [`${cells[0]}, ${cells[1]}`, ...cells.slice(2)] : cells
      const name = rosterForm(parts[0] ?? '')
      const sam = parts[1] ?? ''
      const positionName = parts[2] ?? ''
      const position = byName.get(positionName.toLowerCase())
      let problem: string | null = null
      if (name === '') problem = 'no name'
      else if (sam === '') problem = 'no Sam #'
      else if (positionName === '') problem = 'no position'
      else if (position === undefined) problem = `unknown position "${positionName}"`
      else if (seen.has(sam.toLowerCase())) problem = 'Sam # repeated in the list'
      seen.add(sam.toLowerCase())
      return { line, name, sam, positionName, position, problem }
    })
}

/** "Brenton Evans" → "EVANS, Brenton"; roster-form input passes through. */
export function rosterForm(name: string): string {
  const clean = name.trim()
  if (clean === '' || clean.includes(',')) return clean
  const parts = clean.split(/\s+/)
  const surname = parts.pop() ?? ''
  return `${surname.toUpperCase()}, ${parts.join(' ')}`.trim()
}

function errorText(error: unknown): string {
  return error instanceof ApiError ? error.message : String(error)
}
