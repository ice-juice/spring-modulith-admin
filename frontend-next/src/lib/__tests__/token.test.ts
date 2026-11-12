import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { clearToken, getToken, saveToken } from '../token'
function storage(): Storage {
  const values = new Map<string, string>()
  return { get length() { return values.size }, key: index => [...values.keys()][index] ?? null, getItem: key => values.get(key) ?? null,
    setItem: (key, value) => { values.set(key, value) }, removeItem: key => { values.delete(key) }, clear: () => values.clear() }
}
let shared: Storage, first: Storage, peer: Storage
beforeEach(() => { shared = storage(); first = storage(); peer = storage(); vi.stubGlobal('localStorage', shared); vi.stubGlobal('sessionStorage', first) })
afterEach(() => vi.unstubAllGlobals())
it('retains legacy remembered sessions for tabs without a private-session or signed-out marker', () => {
  shared.setItem('sma.access-token', 'remembered')
  expect(getToken()).toBe('remembered')
  vi.stubGlobal('sessionStorage', peer); expect(getToken()).toBe('remembered')
})
it('a private session does not delete a different remembered identity, and logout cannot fall back to it', () => {
  shared.setItem('sma.access-token', 'other-account')
  saveToken('private-account', false); expect(getToken()).toBe('private-account'); expect(shared.getItem('sma.access-token')).toBe('other-account')
  expect(clearToken('private-account')).toBe(true); expect(getToken()).toBeNull(); expect(shared.getItem('sma.access-token')).toBe('other-account')
  vi.stubGlobal('sessionStorage', peer); expect(getToken()).toBe('other-account')
  vi.stubGlobal('sessionStorage', first); expect(getToken()).toBeNull()
})
it('clearing a shared session only removes its exact token and a detached tab does not adopt a later login', () => {
  saveToken('original-shared', true); expect(clearToken('original-shared')).toBe(true); expect(shared.getItem('sma.access-token')).toBeNull()
  vi.stubGlobal('sessionStorage', peer); saveToken('later-shared', true)
  vi.stubGlobal('sessionStorage', first); expect(getToken()).toBeNull(); expect(clearToken()).toBe(true)
  expect(shared.getItem('sma.access-token')).toBe('later-shared')
  saveToken('explicit-next-session', false); expect(getToken()).toBe('explicit-next-session')
})
it('an old expected token cannot clear a newer effective session or detach its owning tab', () => {
  saveToken('old', true); shared.setItem('sma.access-token', 'new')
  expect(clearToken('old')).toBe(false); expect(getToken()).toBe('new'); expect(first.getItem('sma.access-token.detached')).toBeNull()
  saveToken('private-new', false); expect(clearToken('new')).toBe(false); expect(getToken()).toBe('private-new')
})
it('a remembered login explicitly reattaches its tab and removes only its private slot', () => {
  saveToken('private', false); clearToken(); saveToken('remembered', true)
  expect(first.getItem('sma.access-token')).toBeNull(); expect(first.getItem('sma.access-token.detached')).toBeNull(); expect(getToken()).toBe('remembered')
  expect(() => saveToken('', true)).toThrow(); expect(getToken()).toBe('remembered')
})
