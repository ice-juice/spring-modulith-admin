import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { webcrypto } from 'node:crypto'
import { prepareSessionExit, sessionHasExited, hasSessionExitMarkers } from '../session-exit'
import { browserLocksFixture } from './browser-locks.fixture'
let manager: ReturnType<typeof browserLocksFixture>
beforeEach(() => { vi.stubGlobal('crypto', webcrypto); localStorage.clear(); sessionStorage.clear(); manager = browserLocksFixture(); Object.defineProperty(navigator, 'locks', { configurable: true, value: manager.locks }) })
afterEach(() => vi.unstubAllGlobals())
it('reserves before confirmation, cancellation stores nothing, and only one sender commits for the same session', async () => {
  const cancelled = await prepareSessionExit('private-token', () => true)
  expect(hasSessionExitMarkers()).toBe(false); await cancelled.release()
  const first = await prepareSessionExit('private-token', () => true), peer = await prepareSessionExit('private-token', () => true)
  expect(await peer.commit()).toBeNull(); await peer.release()
  const key = await first.commit(); expect(key).toMatch(/^[a-f0-9-]{36}$/)
  expect(await first.commit()).toBeNull(); await first.release()
  expect(await sessionHasExited('private-token')).toBe(true)
  const restored = await prepareSessionExit('private-token', () => true); expect(await restored.commit()).toBeNull(); await restored.release()
  const raw = Object.entries(localStorage).find(([key]) => key.startsWith('sma.session-exit.v1.'))![1]
  expect(Object.keys(JSON.parse(raw)).sort()).toEqual(['key', 'schema', 'scope', 'state']); expect(raw).not.toContain('private-token'); expect(JSON.parse(raw).state).toBe('LOCAL_EXIT'); expect(manager.held.size).toBe(0)
})
it('distinct sessions remain independent and a new token is not retired by an old marker', async () => {
  const a = await prepareSessionExit('one', () => true), b = await prepareSessionExit('two', () => true)
  expect(await a.commit()).not.toBeNull(); expect(await b.commit()).not.toBeNull(); await a.release(); await b.release()
  expect(await sessionHasExited('fresh-session')).toBe(false)
})
it('missing browser locks and storage errors allow only local exit without a dispatch key', async () => {
  Object.defineProperty(navigator, 'locks', { configurable: true, value: undefined })
  const unavailable = await prepareSessionExit('one', () => true); expect(await unavailable.commit()).toBeNull(); await unavailable.release()
  Object.defineProperty(navigator, 'locks', { configurable: true, value: manager.locks })
  const broken = await prepareSessionExit('one', () => true), spy = vi.spyOn(localStorage, 'setItem').mockImplementation(() => { throw new Error('Full') })
  try { expect(await broken.commit()).toBeNull() } finally { spy.mockRestore(); await broken.release() }
  expect(hasSessionExitMarkers()).toBe(false); expect(manager.held.size).toBe(0)
})
it('capacity failure preserves all older retirement markers and refuses another network attempt', async () => {
  for (let i = 0; i < 256; i++) { const scope = i.toString(16).padStart(64, '0'); localStorage.setItem('sma.session-exit.v1.' + scope, JSON.stringify({ schema: 1, scope, key: crypto.randomUUID(), state: 'LOCAL_EXIT' })) }
  const full = await prepareSessionExit('new-private-token', () => true); expect(await full.commit()).toBeNull(); await full.release()
  expect(localStorage.length).toBe(256)
})
it('identity changes before commit leave no marker or leaked lock', async () => {
  let current = true
  const exit = await prepareSessionExit('one', () => current); current = false
  expect(await exit.commit()).toBeNull(); await exit.release(); expect(hasSessionExitMarkers()).toBe(false); expect(manager.held.size).toBe(0)
})
it('corrupt current-session metadata is never interpreted as a new send opportunity', async () => {
  const first = await prepareSessionExit('one', () => true); await first.commit(); await first.release()
  localStorage.setItem(Object.keys(localStorage)[0]!, '{invalid')
  await expect(sessionHasExited('one')).rejects.toThrow()
  const next = await prepareSessionExit('one', () => true); expect(await next.commit()).toBeNull(); await next.release()
  expect(localStorage.getItem(Object.keys(localStorage)[0]!)).toBe('{invalid')
})
