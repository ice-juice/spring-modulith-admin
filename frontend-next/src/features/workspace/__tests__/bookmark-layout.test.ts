import { effectScope, ref } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import { useBookmarkLayout } from '../bookmark-layout'
beforeEach(() => { localStorage.clear(); vi.restoreAllMocks() })
it('stores only layout and restores by server scope without leaking to another identity', () => {
  const owner = effectScope(), scope = ref('tenantA.admin.user1')
  const preference = owner.run(() => useBookmarkLayout(scope))!
  preference.select('cards')
  expect(localStorage.getItem('sma.shelf.bookmark-view.v1.tenantA.admin.user1')).toBe('cards')
  scope.value = 'tenantB.admin.user1'
  expect(preference.layout.value).toBe('list')
  scope.value = 'tenantA.admin.user1'
  expect(preference.layout.value).toBe('cards')
  scope.value = ''
  expect(preference.layout.value).toBe('list')
  preference.select('cards')
  expect(localStorage.length).toBe(1)
  owner.stop()
})
it('ignores malformed layout and remains usable with denied storage', () => {
  localStorage.setItem('sma.shelf.bookmark-view.v1.a', '{"content":"not a layout"}')
  const owner = effectScope(), scope = ref('a')
  const preference = owner.run(() => useBookmarkLayout(scope))!
  expect(preference.layout.value).toBe('list')
  vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('denied') })
  expect(() => preference.select('cards')).not.toThrow()
  expect(preference.layout.value).toBe('cards')
  vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('denied') })
  scope.value = 'b'
  expect(preference.layout.value).toBe('list')
  owner.stop()
})
