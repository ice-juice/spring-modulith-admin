import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { webcrypto } from 'node:crypto'
import { createPinia, disposePinia } from 'pinia'
import { useAuth, type CurrentUser } from '../auth'
import { ApiError, get, post } from '@/api/http'
import { getToken, getSharedToken, saveToken, clearToken } from '@/lib/token'
import { prepareSessionExit, hasSessionExitMarkers } from '@/lib/session-exit'
import { browserLocksFixture } from '@/lib/__tests__/browser-locks.fixture'
vi.mock('@/api/http', async original => ({ ...await original<typeof import('@/api/http')>(), get: vi.fn(), post: vi.fn() }))
vi.mock('../preferences', () => ({ usePreferences: () => ({ changeTimeZone: vi.fn(), changeLocale: vi.fn(), applyAppearance: vi.fn() }) }))
const actor: CurrentUser = { id: '1', username: 'tester', name: 'Tester', portal: 'admin', superAdmin: false, permissions: [], menus: [], navigation: [] }
const pins: ReturnType<typeof createPinia>[] = []
let manager: ReturnType<typeof browserLocksFixture>
beforeEach(() => { vi.resetAllMocks(); vi.stubGlobal('crypto', webcrypto); localStorage.clear(); sessionStorage.clear(); manager = browserLocksFixture(); Object.defineProperty(navigator, 'locks', { configurable: true, value: manager.locks }); vi.mocked(get).mockResolvedValue(actor); vi.mocked(post).mockResolvedValue(null) })
afterEach(() => { for (const pin of pins.splice(0)) disposePinia(pin); vi.unstubAllGlobals() })
function harness(token = 'original-private-token') { const pin = createPinia(); pins.push(pin); saveToken(token, false); const auth = useAuth(pin); auth.user = { ...actor }; return { auth, pin } }
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(yes => { resolve = yes }); return { promise, resolve } }
function notify() { const key = Object.keys(localStorage).find(key => key.startsWith('sma.session-exit.v1.'))!; window.dispatchEvent(new StorageEvent('storage', { key, newValue: localStorage.getItem(key) })) }
it('holds admission before unsaved confirmation, and cancellation neither retires nor signs out the session', async () => {
  const { auth } = harness(), answer = deferred<boolean>(), before = vi.fn(() => answer.promise), result = auth.logout(before)
  await vi.waitFor(() => expect(before).toHaveBeenCalledOnce()); expect(manager.held.size).toBeGreaterThan(0)
  expect(post).not.toHaveBeenCalled(); expect(hasSessionExitMarkers()).toBe(false)
  answer.resolve(false); expect(await result).toBe(false); expect(getToken()).toBe('original-private-token'); expect(manager.held.size).toBe(0)
})
it('unknown logout is not resent after reloading the old token, and load refuses to restore that session', async () => {
  const first = harness(); vi.mocked(post).mockRejectedValueOnce(new ApiError(0, null, 'Lost'))
  await expect(first.auth.logout()).rejects.toMatchObject({ code: 0 }); expect(getToken()).toBeNull()
  const restored = harness(); expect(await restored.auth.load()).toBe(false); expect(get).not.toHaveBeenCalled(); expect(restored.auth.user).toBeNull(); expect(getToken()).toBeNull()
  const again = harness(); expect(await again.auth.logout()).toBe(true); expect(post).toHaveBeenCalledOnce()
})
it('missing coordination still honors unsaved confirmation and performs local-only exit', async () => {
  const { auth } = harness(); localStorage.setItem('sma.access-token', 'unrelated-shared')
  Object.defineProperty(navigator, 'locks', { configurable: true, value: undefined })
  expect(await auth.logout(async () => false)).toBe(false); expect(getToken()).toBe('original-private-token')
  expect(await auth.logout()).toBe(true); expect(getToken()).toBeNull(); expect(getSharedToken()).toBe('unrelated-shared'); expect(post).not.toHaveBeenCalled()
})
it('a peer retirement also invalidates a tab waiting on a nonexclusive exit confirmation', async () => {
  const { auth } = harness(), peer = await prepareSessionExit('original-private-token', () => true), answer = deferred<boolean>(), before = vi.fn(() => answer.promise)
  const own = auth.logout(before); await vi.waitFor(() => expect(before).toHaveBeenCalledOnce())
  await peer.commit(); notify(); await vi.waitFor(() => expect(auth.user).toBeNull())
  expect(getToken()).toBeNull(); answer.resolve(false); expect(await own).toBe(false); expect(post).not.toHaveBeenCalled(); await peer.release()
})
it('a pending old retirement notification cannot clear a new token', async () => {
  const { auth } = harness(), peer = await prepareSessionExit('original-private-token', () => true)
  await peer.commit(); await peer.release(); notify()
  auth.reset(); saveToken('replacement-session', false); auth.user = { ...actor, id: '2' }
  await new Promise(resolve => setTimeout(resolve, 0)); expect(getToken()).toBe('replacement-session'); expect(auth.user.id).toBe('2')
})
it('invalidating an unconfirmed exit releases the browser lock before a stale dialog finishes', async () => {
  const { auth } = harness(), answer = deferred<boolean>(), before = vi.fn(() => answer.promise), pending = auth.logout(before)
  await vi.waitFor(() => expect(before).toHaveBeenCalledOnce()); auth.reset()
  await vi.waitFor(() => expect(manager.held.size).toBe(0)); answer.resolve(true)
  expect(await pending).toBe(false); expect(post).not.toHaveBeenCalled(); expect(hasSessionExitMarkers()).toBe(false)
})

it('a tab whose token already disappeared still honors cancellation and cannot clear a later login after confirmation', async () => {
  const { auth } = harness(); clearToken()
  expect(await auth.logout(async () => false)).toBe(false); expect(auth.user?.id).toBe('1')
  const answer = deferred<boolean>(), before = vi.fn(() => answer.promise), pending = auth.logout(before)
  await vi.waitFor(() => expect(before).toHaveBeenCalledOnce())
  saveToken('later-login', false); auth.user = { ...actor, id: '2' }
  answer.resolve(true); expect(await pending).toBe(false)
  expect(getToken()).toBe('later-login'); expect(auth.user.id).toBe('2'); expect(post).not.toHaveBeenCalled()
})
