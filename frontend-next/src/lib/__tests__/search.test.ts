import { expect, it, vi } from 'vitest'
import { effectScope } from 'vue'
import { createSearchRegistry, searchMenus, safeSearchTarget, type SearchContext, type SearchPage } from '../search'
import { useGlobalSearch } from '@/composables/useGlobalSearch'
const context: SearchContext = { identity: 'admin:1', permissions: ['file:file:view'], menus: [] }
it('searches all supplied authorized menus by name, ancestors and paths, with safe external/frame targets', () => {
  const menus = [{ id: '1', parentId: null, name: '系统', path: null, icon: null, permission: null, type: 'DIRECTORY', openMode: 'ROUTE', children: [
    { id: '2', parentId: '1', name: '用户', path: '/users', icon: null, permission: 'iam:user:view', type: 'MENU', openMode: 'ROUTE', children: [] },
    { id: '3', parentId: '1', name: '文档', path: 'https://example.test', icon: null, permission: null, type: 'MENU', openMode: 'IFRAME', children: [] },
  ] }]
  expect(searchMenus(menus, '系统 user')[0]?.target).toEqual({ kind: 'route', path: '/users' })
  expect(searchMenus(menus, '文档')[0]?.target).toEqual({ kind: 'route', path: '/frame/3' })
  expect(safeSearchTarget({ kind: 'route', path: '//evil.test' })).toBe(false)
  expect(safeSearchTarget({ kind: 'external', url: 'javascript:alert(1)' })).toBe(false)
})
it('isolates failures, checks permission, opts out semantic sources and ignores stale results after cancellation', async () => {
  const registry = createSearchRegistry(), denied = vi.fn(), ai = vi.fn()
  let finish: (page: SearchPage) => void = () => {}
  registry.register({ id: 'files', labelKey: '', capability: 'keyword', permission: 'file:file:view', search: req => req.query === 'old' ? new Promise(resolve => { finish = resolve }) : Promise.resolve({ items: [], hasMore: false }) })
  registry.register({ id: 'users', labelKey: '', capability: 'keyword', permission: 'iam:user:view', search: denied })
  registry.register({ id: 'ai', labelKey: '', capability: 'semantic', search: ai })
  registry.register({ id: 'error', labelKey: '', capability: 'keyword', search: () => Promise.reject(Error('unavailable')) })
  const scope = effectScope(), search = scope.run(() => useGlobalSearch(registry))!
  const old = search.search('old', context)
  await search.search('new', context)
  finish({ items: [{ id: 'old', source: 'files', title: 'stale', target: { kind: 'route', path: '/files' } }], hasMore: true })
  await old
  expect(search.groups.value.find(g => g.provider.id === 'files')?.items).toEqual([])
  expect(search.groups.value.find(g => g.provider.id === 'error')?.error).toBe(true)
  expect(denied).not.toHaveBeenCalled(); expect(ai).not.toHaveBeenCalled()
  search.clear(); expect(search.groups.value).toEqual([]); scope.stop()
})
it('appends deduplicated next pages and rejects duplicate source registrations', async () => {
  const registry = createSearchRegistry()
  registry.register({ id: 'items', labelKey: '', capability: 'keyword', search: async req => ({ items: [{ id: String(req.page), source: 'items', title: 'item', target: { kind: 'route', path: '/items' } }], hasMore: req.page < 2 }) })
  expect(() => registry.register({ id: 'items', labelKey: '', capability: 'keyword', search: vi.fn() })).toThrow()
  const scope = effectScope(), search = scope.run(() => useGlobalSearch(registry))!
  await search.search('keyword', context); await search.more('items')
  expect(search.groups.value[0]?.items.map(i => i.id)).toEqual(['1', '2'])
  expect(search.groups.value[0]?.hasMore).toBe(false); scope.stop()
})
