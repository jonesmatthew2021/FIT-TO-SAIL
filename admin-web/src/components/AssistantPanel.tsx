import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import type { AssistantAsk, AssistantSource } from '../api/client'
import { NAV_ITEMS } from './Layout'

/**
 * The assistant panel — shell chrome, not a screen (design: ~/Temp/ai-handoff.md).
 *
 * One instance lives in `Layout`, above the router outlet, and stays mounted whether open or
 * closed so the conversation and scroll position survive navigation. Two display modes: **push**
 * (the shell's `padding-right` animates to the panel width; nothing is covered) and **overlay**
 * (the panel floats on `--shadow-lg` over a scrim). Overlay is forced below 1100px, where pushing
 * would squeeze the content columns past their `min-width` floors — the same breakpoint the
 * stylesheet already treats as "narrow".
 *
 * What the panel never does is the point: it never writes, it never computes a compliance answer,
 * and it never asserts a fact without a source chip — all three enforced server-side
 * (`AssistantService`), where they cannot be prompt-injected away. Until a §14.5 provider is
 * chosen the server answers 503, which renders here as an error turn carrying the server's
 * message verbatim — the same honesty ADM-9 shows for extraction.
 */

export type AssistantMode = 'push' | 'overlay'

interface AssistantPrefs {
  mode: AssistantMode
  width: number
}

const PREFS_KEY = 'crewcomp.assistant'
const MIN_WIDTH = 320
const MAX_WIDTH = 560
const DEFAULT_WIDTH = 400

/** Below this, push mode would squeeze content columns past their min-width floors. */
const FORCE_OVERLAY_QUERY = '(max-width: 1100px)'

export function clampWidth(width: number): number {
  return Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, Math.round(width)))
}

/** Mode and width persist per user (session-independent); the conversation does not. */
export function readAssistantPrefs(): AssistantPrefs {
  try {
    const raw = window.localStorage.getItem(PREFS_KEY)
    if (raw !== null) {
      const parsed = JSON.parse(raw) as Partial<AssistantPrefs>
      return {
        mode: parsed.mode === 'overlay' ? 'overlay' : 'push',
        width: clampWidth(typeof parsed.width === 'number' ? parsed.width : DEFAULT_WIDTH),
      }
    }
  } catch {
    // Corrupted preferences read as the defaults.
  }
  return { mode: 'push', width: DEFAULT_WIDTH }
}

export function writeAssistantPrefs(prefs: AssistantPrefs): void {
  try {
    window.localStorage.setItem(PREFS_KEY, JSON.stringify(prefs))
  } catch {
    // Storage full or denied — the panel still works, the preference just does not stick.
  }
}

// ---------------------------------------------------------------------------
// Route context
// ---------------------------------------------------------------------------

export interface AssistantContext {
  /** What the context chip shows: the screen ("ADM-4 Register") or an open record's id. */
  chip: string
  /** True when the chip is a business key, which Nocturne renders in monospace. */
  mono: boolean
  /** Always the screen, even when the chip names a record — the server wants both. */
  screen: string
  recordId?: string
}

/**
 * The context chip is what makes an answer scoped rather than generic. A screen resolves through
 * `NAV_ITEMS` by longest prefix; an open register record is named by its id instead, because
 * `recordId` is the business key in the URL. Evidence and person detail routes carry surrogate
 * keys (a UUID, a row id) that nobody would recognise, so they stay at screen level.
 */
export function contextForPath(pathname: string): AssistantContext {
  const register = /^\/register\/([^/]+)$/.exec(pathname)
  const registerId = register?.[1]
  if (registerId !== undefined && registerId !== 'new') {
    const id = decodeURIComponent(registerId)
    return { chip: id, mono: true, screen: 'ADM-4 Register', recordId: id }
  }

  const match = NAV_ITEMS
    .filter((item) => (item.to === '/' ? pathname === '/' : pathname === item.to || pathname.startsWith(`${item.to}/`)))
    .sort((a, b) => b.to.length - a.to.length)[0]
  const label = match === undefined ? 'Crewcomp Admin' : `${match.module} ${match.label}`
  return { chip: label, mono: false, screen: label }
}

/**
 * Suggested prompts, shown only at the conversation's opening state. Screen-specific per the
 * handoff; the dashboard's three are its copy verbatim, the rest are authored in the same voice
 * (declarative, quantifiable, no exclamation) and are fair game to reword.
 */
const DASHBOARD_PROMPTS: readonly string[] = [
  'What blocks the next cutoff?',
  'Who can I swap onto CC24?',
  'Summarise Unknown holdings',
]

const PROMPTS: readonly (readonly [string, readonly string[]])[] = [
  ['/planner', ['Who is eligible for the open slots?', 'Which quotas are short this swing?', 'Who expires mid-swing?']],
  ['/people', ['Whose medicals lapse this quarter?', 'Who holds Work at Heights?', 'Which holdings are unknown?']],
  ['/matrix', ['What changed in the published version?', 'Which cells differ by partnership?', 'What does footnote M7 require?']],
  ['/register', ['What is still open past its cutoff?', 'Which approvals lapse this swing?', 'Summarise this record’s history']],
  ['/requirements', ['Which requirements are unused?', 'Which codes were retired?', 'What has no issuing authority?']],
  ['/exceptions', ['What has waited longest?', 'Which items block a swing?', 'Summarise open items by area']],
  ['/crew-requests', ['What has waited longest?', 'Which asks affect the next swing?', 'Summarise open requests']],
  ['/notifications', ['What went unread this week?', 'Which scan raised the most?', 'Who was told about the last cutoff?']],
  ['/evidence', ['What has waited longest for review?', 'What arrived this week?', 'Which submissions name no requirement?']],
  ['/administration', ['Which jobs ran today?', 'Which settings are overridden?', 'Who holds System Administrator?']],
]

export function promptsForPath(pathname: string): readonly string[] {
  if (pathname === '/') return DASHBOARD_PROMPTS
  for (const [prefix, prompts] of PROMPTS) {
    if (pathname === prefix || pathname.startsWith(`${prefix}/`)) return prompts
  }
  return DASHBOARD_PROMPTS
}

// ---------------------------------------------------------------------------
// Panel state (owned by Layout, so the trigger and the panel share it)
// ---------------------------------------------------------------------------

export interface AssistantState {
  open: boolean
  mode: AssistantMode
  /** The mode actually in force — overlay wins below 1100px whatever the preference says. */
  effectiveMode: AssistantMode
  width: number
  /** What the shell's `--assistant-push` is set to: the width in push mode, else 0. */
  pushWidth: string
  toggle: () => void
  close: () => void
  setMode: (mode: AssistantMode) => void
  setWidth: (width: number) => void
  triggerRef: React.RefObject<HTMLButtonElement | null>
}

export function useAssistantPanel(): AssistantState {
  const initial = useMemo(readAssistantPrefs, [])
  const [open, setOpen] = useState(false)
  const [mode, setMode] = useState<AssistantMode>(initial.mode)
  const [width, setWidthState] = useState(initial.width)
  const [narrow, setNarrow] = useState(
    () => typeof window.matchMedia === 'function' && window.matchMedia(FORCE_OVERLAY_QUERY).matches,
  )
  const triggerRef = useRef<HTMLButtonElement | null>(null)

  useEffect(() => {
    if (typeof window.matchMedia !== 'function') return
    const query = window.matchMedia(FORCE_OVERLAY_QUERY)
    const onChange = (event: MediaQueryListEvent) => setNarrow(event.matches)
    query.addEventListener('change', onChange)
    return () => query.removeEventListener('change', onChange)
  }, [])

  useEffect(() => {
    writeAssistantPrefs({ mode, width })
  }, [mode, width])

  // ⌘K / Ctrl-K toggles, from anywhere. Suppressed while a native <dialog> is open: the dialog
  // owns the top layer, so a panel opened behind it would render underneath and read as broken.
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        if (document.querySelector('dialog[open]') !== null) return
        event.preventDefault()
        setOpen((wasOpen) => !wasOpen)
      }
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [])

  const effectiveMode: AssistantMode = narrow ? 'overlay' : mode
  return {
    open,
    mode,
    effectiveMode,
    width,
    pushWidth: open && effectiveMode === 'push' ? `${width}px` : '0px',
    toggle: useCallback(() => setOpen((wasOpen) => !wasOpen), []),
    close: useCallback(() => setOpen(false), []),
    setMode,
    setWidth: useCallback((next: number) => setWidthState(clampWidth(next)), []),
    triggerRef,
  }
}

// ---------------------------------------------------------------------------
// The trigger (rendered in the header, immediately before Switch role)
// ---------------------------------------------------------------------------

export function AssistantTrigger({ state }: { state: AssistantState }): React.ReactNode {
  const mac = typeof navigator !== 'undefined' && navigator.platform.toUpperCase().includes('MAC')
  return (
    <button
      ref={state.triggerRef}
      type="button"
      className="assistant-trigger"
      aria-label="Ask AI"
      aria-expanded={state.open}
      aria-controls="assistant-panel"
      onClick={state.toggle}
    >
      <span className="assistant__dot" aria-hidden="true" />
      <span className="assistant-trigger__label">Ask AI</span>
      <span className="assistant-trigger__hint">{mac ? '⌘K' : 'Ctrl K'}</span>
    </button>
  )
}

// ---------------------------------------------------------------------------
// The panel
// ---------------------------------------------------------------------------

interface Turn {
  role: 'user' | 'assistant'
  text: string
  sources?: readonly AssistantSource[]
  /** Set on an error turn, holding the question so "Try again" can resend it. */
  question?: string
  error?: boolean
}

/** ADM-8's open-redirect rule, applied to source-chip deep links: single-slash paths only. */
function internalLink(deepLink: string | null | undefined): string | null {
  if (deepLink === null || deepLink === undefined) return null
  return deepLink.startsWith('/') && !deepLink.startsWith('//') ? deepLink : null
}

export function AssistantPanel({ state }: { state: AssistantState }): React.ReactNode {
  const { open, effectiveMode } = state
  const location = useLocation()
  const context = useMemo(() => contextForPath(location.pathname), [location.pathname])

  const [turns, setTurns] = useState<readonly Turn[]>([])
  const [draft, setDraft] = useState('')
  const [pending, setPending] = useState(false)
  const [stickToBottom, setStickToBottom] = useState(true)

  const listRef = useRef<HTMLDivElement | null>(null)
  const composerRef = useRef<HTMLTextAreaElement | null>(null)
  const wasOpen = useRef(false)

  // Focus moves into the composer on open and back to the trigger on close (never on mount —
  // stealing focus from the app because the shell rendered would be worse than no management).
  useEffect(() => {
    if (open) {
      composerRef.current?.focus()
      setStickToBottom(true)
      if (listRef.current !== null) listRef.current.scrollTop = listRef.current.scrollHeight
    } else if (wasOpen.current) {
      state.triggerRef.current?.focus()
    }
    wasOpen.current = open
  }, [open, state.triggerRef])

  // Esc closes. Document-level so it works from anywhere in the panel; suppressed while a
  // native <dialog> is open, whose own Esc handling must win.
  useEffect(() => {
    if (!open) return
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && document.querySelector('dialog[open]') === null) state.close()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [open, state])

  // Pin to the bottom on every new turn and on the pending state appearing — scrollTop, not
  // scrollIntoView, which would also scroll the page behind the panel.
  useEffect(() => {
    if (stickToBottom && listRef.current !== null) {
      listRef.current.scrollTop = listRef.current.scrollHeight
    }
  }, [turns, pending, stickToBottom])

  const onListScroll = useCallback(() => {
    const list = listRef.current
    if (list === null) return
    setStickToBottom(list.scrollHeight - list.scrollTop - list.clientHeight < 32)
  }, [])

  const ask = useCallback(
    (question: string) => {
      setTurns((previous) => [...previous, { role: 'user', text: question }])
      setPending(true)
      const body: AssistantAsk = { question, screen: context.screen }
      if (context.recordId !== undefined) body.recordId = context.recordId
      void api
        .assistantAsk(body)
        .then((answer) => {
          setTurns((previous) => [...previous, { role: 'assistant', text: answer.text, sources: answer.sources }])
        })
        .catch((error: unknown) => {
          // The server's message verbatim (ApiError.message is ErrorDto.detail); never a paraphrase.
          const text = error instanceof ApiError ? error.message : 'The server could not be reached.'
          setTurns((previous) => [...previous, { role: 'assistant', text, question, error: true }])
        })
        .finally(() => setPending(false))
    },
    [context],
  )

  const send = useCallback(() => {
    const question = draft.trim()
    if (question === '' || pending) return
    setDraft('')
    ask(question)
  }, [draft, pending, ask])

  const onComposerKeyDown = useCallback(
    (event: React.KeyboardEvent<HTMLTextAreaElement>) => {
      if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault()
        send()
      }
    },
    [send],
  )

  // Drag the left edge to resize, 320–560px. Width is the pointer's distance from the right
  // viewport edge; the preference persists via the same effect that stores the mode.
  const onResizeStart = useCallback(
    (event: React.PointerEvent) => {
      event.preventDefault()
      const onMove = (move: PointerEvent) => state.setWidth(window.innerWidth - move.clientX)
      const onUp = () => {
        window.removeEventListener('pointermove', onMove)
        window.removeEventListener('pointerup', onUp)
      }
      window.addEventListener('pointermove', onMove)
      window.addEventListener('pointerup', onUp)
    },
    [state],
  )

  const prompts = promptsForPath(location.pathname)
  const atOpeningState = turns.length === 0 && !pending

  return (
    <>
      {open && effectiveMode === 'overlay' && (
        <div className="assistant-scrim" aria-hidden="true" onClick={state.close} />
      )}
      <aside
        id="assistant-panel"
        role="complementary"
        aria-label="Assistant"
        className={[
          'assistant',
          open ? 'assistant--open' : '',
          effectiveMode === 'overlay' ? 'assistant--overlay' : '',
        ]
          .filter(Boolean)
          .join(' ')}
        style={{ width: `${state.width}px` }}
      >
        <div className="assistant__resize" onPointerDown={onResizeStart} aria-hidden="true" />

        <header className="assistant__header">
          <span className="assistant__dot assistant__dot--bright" aria-hidden="true" />
          <h2 className="assistant__title">Assistant</h2>
          <div className="assistant__modes" role="group" aria-label="Display mode">
            <button
              type="button"
              className={state.mode === 'push' ? 'assistant__mode assistant__mode--on' : 'assistant__mode'}
              aria-pressed={state.mode === 'push'}
              onClick={() => state.setMode('push')}
            >
              Push
            </button>
            <button
              type="button"
              className={state.mode === 'overlay' ? 'assistant__mode assistant__mode--on' : 'assistant__mode'}
              aria-pressed={state.mode === 'overlay'}
              onClick={() => state.setMode('overlay')}
            >
              Overlay
            </button>
          </div>
          <button type="button" className="assistant__close" aria-label="Close assistant" onClick={state.close}>
            ✕
          </button>
        </header>

        <div className="assistant__context">
          <span className="assistant__overline">Context</span>
          <span className={context.mono ? 'chip chip--outline chip--small chip--mono' : 'chip chip--outline chip--small'}>
            {context.chip}
          </span>
          <span className="assistant__scope">Your role’s data only</span>
        </div>

        <div className="assistant__messages" ref={listRef} onScroll={onListScroll} aria-live="polite">
          <div className="assistant__turn">
            <span className="assistant__dot" aria-hidden="true" />
            <div className="assistant__prose">
              I can read the register, the matrix and the swing planner. Ask in plain words — I answer
              with the record I got it from.
            </div>
          </div>

          {turns.map((turn, index) =>
            turn.role === 'user' ? (
              <div key={index} className="assistant__user">
                {turn.text}
              </div>
            ) : turn.error === true ? (
              <div key={index} className="assistant__error" role="alert">
                <p>{turn.text}</p>
                {turn.question !== undefined && (
                  <button type="button" className="link-action" onClick={() => ask(turn.question as string)}>
                    Try again
                  </button>
                )}
              </div>
            ) : (
              <div key={index} className="assistant__turn">
                <span className="assistant__dot" aria-hidden="true" />
                <div className="assistant__prose">
                  {turn.text}
                  {turn.sources !== undefined && turn.sources.length > 0 && (
                    <span className="assistant__sources">
                      {turn.sources.map((source, sourceIndex) => {
                        const to = internalLink(source.deepLink)
                        return to === null ? (
                          <span key={sourceIndex} className="assistant__source">
                            {source.label}
                          </span>
                        ) : (
                          <Link key={sourceIndex} className="assistant__source" to={to}>
                            {source.label}
                          </Link>
                        )
                      })}
                    </span>
                  )}
                </div>
              </div>
            ),
          )}

          {pending && (
            <div className="assistant__pending">
              <span className="assistant__dot" aria-hidden="true" />
              Checking the register…
            </div>
          )}
        </div>

        {!stickToBottom && (
          <button
            type="button"
            className="assistant__jump"
            onClick={() => {
              setStickToBottom(true)
              if (listRef.current !== null) listRef.current.scrollTop = listRef.current.scrollHeight
            }}
          >
            Jump to latest
          </button>
        )}

        {atOpeningState && (
          <div className="assistant__prompts">
            {prompts.map((prompt) => (
              <button key={prompt} type="button" className="assistant__prompt" onClick={() => ask(prompt)}>
                {prompt}
              </button>
            ))}
          </div>
        )}

        <footer className="assistant__composer">
          <div className="assistant__composer-row">
            <textarea
              ref={composerRef}
              className="input assistant__input"
              rows={1}
              placeholder="Ask about a swing, a person or a requirement"
              aria-label="Ask the assistant"
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              onKeyDown={onComposerKeyDown}
            />
            <button
              type="button"
              className="button button--primary assistant__send"
              disabled={draft.trim() === '' || pending}
              onClick={send}
            >
              Send
            </button>
          </div>
          <p className="assistant__legal">
            Answers cite the record they came from. Nothing is changed without your approval.
          </p>
        </footer>
      </aside>
    </>
  )
}
