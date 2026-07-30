import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import {
  AssistantPanel,
  AssistantTrigger,
  clampWidth,
  contextForPath,
  promptsForPath,
  readAssistantPrefs,
  useAssistantPanel,
  writeAssistantPrefs,
} from './AssistantPanel'

/**
 * The panel's behaviour rules from the handoff, pinned: the context chip follows the route and
 * names an open record; the composer refuses an empty send; an error turn carries the server's
 * message verbatim with a Try again; mode and width persist and clamp.
 */

describe('route context', () => {
  it('names the screen by module code and label', () => {
    expect(contextForPath('/')).toEqual({ chip: 'ADM-1 Dashboard', mono: false, screen: 'ADM-1 Dashboard' })
    expect(contextForPath('/planner').chip).toBe('ADM-2 Swing planner')
    expect(contextForPath('/people/12').chip).toBe('ADM-5 People & holdings')
    expect(contextForPath('/evidence/3f6a').chip).toBe('ADM-9 Evidence queue')
  })

  it('names an open register record by its business key, in monospace', () => {
    const context = contextForPath('/register/UNICC24-4')
    expect(context.chip).toBe('UNICC24-4')
    expect(context.mono).toBe(true)
    expect(context.screen).toBe('ADM-4 Register')
    expect(context.recordId).toBe('UNICC24-4')
  })

  it('treats the new-record form as the screen, not a record', () => {
    expect(contextForPath('/register/new')).toEqual({ chip: 'ADM-4 Register', mono: false, screen: 'ADM-4 Register' })
  })
})

describe('suggested prompts', () => {
  it("the dashboard's three are the handoff's copy verbatim", () => {
    expect(promptsForPath('/')).toEqual([
      'What blocks the next cutoff?',
      'Who can I swap onto CC24?',
      'Summarise Unknown holdings',
    ])
  })

  it('a detail route takes its screen’s set', () => {
    expect(promptsForPath('/register/UNICC24-4')).toEqual(promptsForPath('/register'))
  })
})

describe('preferences', () => {
  beforeEach(() => window.localStorage.clear())

  it('round-trips, clamping the width to 320–560', () => {
    writeAssistantPrefs({ mode: 'overlay', width: 9999 })
    // Width is clamped on read, so an out-of-range stored value cannot wedge the panel off-screen.
    expect(readAssistantPrefs()).toEqual({ mode: 'overlay', width: 560 })
    expect(clampWidth(10)).toBe(320)
  })

  it('corrupted storage reads as the defaults', () => {
    window.localStorage.setItem('crewcomp.assistant', '{not json')
    expect(readAssistantPrefs()).toEqual({ mode: 'push', width: 400 })
  })
})

function Harness(): React.ReactNode {
  const state = useAssistantPanel()
  return (
    <>
      <AssistantTrigger state={state} />
      <AssistantPanel state={state} />
    </>
  )
}

function renderPanel(path = '/') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Harness />
    </MemoryRouter>,
  )
}

function stubAsk(status: number, body: unknown): ReturnType<typeof vi.fn> {
  const mock = vi.fn(
    async () =>
      new Response(JSON.stringify(body), {
        status,
        headers: { 'Content-Type': 'application/json' },
      }),
  )
  vi.stubGlobal('fetch', mock)
  return mock
}

describe('the panel', () => {
  beforeEach(() => {
    window.localStorage.clear()
    vi.unstubAllGlobals()
  })

  it('opens from the trigger, closes on Escape, and returns focus to the trigger', async () => {
    const user = userEvent.setup()
    renderPanel()
    const trigger = screen.getByRole('button', { name: /Ask AI/ })
    expect(trigger.getAttribute('aria-expanded')).toBe('false')

    await user.click(trigger)
    expect(trigger.getAttribute('aria-expanded')).toBe('true')
    const panel = screen.getByRole('complementary', { name: 'Assistant' })
    expect(panel.className).toContain('assistant--open')
    expect(document.activeElement).toBe(screen.getByRole('textbox', { name: 'Ask the assistant' }))

    fireEvent.keyDown(document, { key: 'Escape' })
    expect(trigger.getAttribute('aria-expanded')).toBe('false')
    expect(document.activeElement).toBe(trigger)
  })

  it('toggles from ⌘K', () => {
    renderPanel()
    fireEvent.keyDown(document, { key: 'k', metaKey: true })
    expect(screen.getByRole('button', { name: /Ask AI/ }).getAttribute('aria-expanded')).toBe('true')
    fireEvent.keyDown(document, { key: 'k', ctrlKey: true })
    expect(screen.getByRole('button', { name: /Ask AI/ }).getAttribute('aria-expanded')).toBe('false')
  })

  it('refuses an empty send, and Enter sends while Shift-Enter does not', async () => {
    const mock = stubAsk(503, { error: 'assistant_unconfigured', detail: 'No assistant model is configured (§14.5).' })
    const user = userEvent.setup()
    renderPanel()
    await user.click(screen.getByRole('button', { name: /Ask AI/ }))

    const send = screen.getByRole('button', { name: 'Send' })
    expect(send.hasAttribute('disabled')).toBe(true)

    const composer = screen.getByRole('textbox', { name: 'Ask the assistant' })
    await user.type(composer, 'line one{Shift>}{Enter}{/Shift}line two')
    expect(mock).not.toHaveBeenCalled()
    expect(send.hasAttribute('disabled')).toBe(false)

    await user.keyboard('{Enter}')
    expect(mock).toHaveBeenCalledOnce()
    expect((composer as HTMLTextAreaElement).value).toBe('')
  })

  it("renders the server's refusal verbatim as an error turn with Try again", async () => {
    stubAsk(503, { error: 'assistant_unconfigured', detail: 'No assistant model is configured (§14.5).' })
    const user = userEvent.setup()
    renderPanel()
    await user.click(screen.getByRole('button', { name: /Ask AI/ }))
    await user.type(screen.getByRole('textbox', { name: 'Ask the assistant' }), 'What blocks the next cutoff?')
    await user.click(screen.getByRole('button', { name: 'Send' }))

    expect(await screen.findByRole('alert')).toBeDefined()
    expect(screen.getByText('No assistant model is configured (§14.5).')).toBeDefined()
    expect(screen.getByRole('button', { name: 'Try again' })).toBeDefined()
  })

  it('renders an answer’s source chips, linking only single-slash paths', async () => {
    stubAsk(200, {
      text: 'UNI CC24 is one GPH short of the M7 quota on Shift 1.',
      sources: [
        { label: 'ADM-2 Swing planner', deepLink: '/planner' },
        { label: 'evil', deepLink: '//evil.example' },
      ],
    })
    const user = userEvent.setup()
    renderPanel()
    await user.click(screen.getByRole('button', { name: /Ask AI/ }))
    await user.click(screen.getByRole('button', { name: 'What blocks the next cutoff?' }))

    expect(await screen.findByText(/one GPH short/)).toBeDefined()
    const chip = screen.getByRole('link', { name: 'ADM-2 Swing planner' })
    expect(chip.getAttribute('href')).toBe('/planner')
    // The protocol-relative "deep link" renders as text, never as an href (open-redirect rule).
    expect(screen.getByText('evil').tagName).toBe('SPAN')
  })

  it('shows suggested prompts only at the opening state', async () => {
    stubAsk(503, { error: 'assistant_unconfigured', detail: 'No assistant model is configured (§14.5).' })
    const user = userEvent.setup()
    renderPanel()
    await user.click(screen.getByRole('button', { name: /Ask AI/ }))
    expect(screen.getByRole('button', { name: 'Summarise Unknown holdings' })).toBeDefined()

    await user.click(screen.getByRole('button', { name: 'What blocks the next cutoff?' }))
    await screen.findByRole('alert')
    expect(screen.queryByRole('button', { name: 'Summarise Unknown holdings' })).toBeNull()
  })

  it('persists the display mode per user', async () => {
    const user = userEvent.setup()
    renderPanel()
    await user.click(screen.getByRole('button', { name: /Ask AI/ }))

    const overlay = screen.getByRole('button', { name: 'Overlay' })
    expect(overlay.getAttribute('aria-pressed')).toBe('false')
    await user.click(overlay)
    expect(overlay.getAttribute('aria-pressed')).toBe('true')
    expect(readAssistantPrefs().mode).toBe('overlay')
    expect(screen.getByRole('complementary', { name: 'Assistant' }).className).toContain('assistant--overlay')
  })
})
