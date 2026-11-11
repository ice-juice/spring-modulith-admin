import { get, type PageResult } from '@/api/http'
import type { MenuItem } from '@/stores/auth'
import { menuRoute } from './menus'
import { safeLocalPath, safeHttpLink } from './web-addresses'

export type SearchTarget = { kind: 'route'; path: string; query?: Record<string, string> } | { kind: 'external'; url: string }
export interface SearchHit { id: string; source: string; title: string; description?: string; icon?: string | null; target: SearchTarget; citations?: { title: string; url: string }[] }
export interface SearchPage { items: SearchHit[]; hasMore: boolean }
export interface SearchContext { identity: string; permissions: readonly string[]; menus: MenuItem[] }
export interface SearchRequest { query: string; page: number; size: number; signal: AbortSignal; context: SearchContext }
/** Providers must use authorized APIs. Semantic providers remain opt-in until a matching UI is explicitly enabled. */
export interface SearchProvider { id: string; labelKey: string; capability: 'keyword' | 'semantic'; permission?: string; search: (request: SearchRequest) => Promise<SearchPage> }
export function createSearchRegistry() {
  const entries = new Map<string, SearchProvider>()
  return {
    register(provider: SearchProvider) {
      if (!/^[a-z][a-z0-9-]*$/.test(provider.id) || entries.has(provider.id)) throw new Error('Duplicate or invalid search provider: ' + provider.id)
      entries.set(provider.id, provider)
      return () => { if (entries.get(provider.id) === provider) entries.delete(provider.id) }
    },
    available(context: SearchContext, capability: SearchProvider['capability'] = 'keyword') {
      return [...entries.values()].filter(p => p.capability === capability && (!p.permission || context.permissions.includes(p.permission)))
    },
  }
}
export function safeSearchTarget(target: SearchTarget) {
  return target.kind === 'route'
    ? !!safeLocalPath(target.path)
    : !!safeHttpLink(target.url)
}
export function searchMenus(menus: MenuItem[], query: string): SearchHit[] {
  const words = query.trim().toLocaleLowerCase().split(/\s+/).filter(Boolean)
  const found: SearchHit[] = []
  function visit(rows: MenuItem[], parents: string[]) {
    for (const menu of rows) {
      const trail = [...parents, menu.name]
      const text = `${trail.join(' ')} ${menu.path || ''} ${menu.permission || ''}`.toLocaleLowerCase()
      if (menu.path && menu.type !== 'DIRECTORY' && words.every(w => text.includes(w))) {
        const target: SearchTarget = menu.openMode === 'EXTERNAL' ? { kind: 'external', url: menu.path } : { kind: 'route', path: menuRoute(menu) }
        if (safeSearchTarget(target)) found.push({ id: menu.id, source: 'menus', title: menu.name, description: parents.join(' / '), icon: menu.icon, target })
      }
      visit(menu.children || [], trail)
    }
  }
  visit(menus, [])
  return found.sort((a, b) => Number(b.title.toLocaleLowerCase() === query.toLocaleLowerCase()) - Number(a.title.toLocaleLowerCase() === query.toLocaleLowerCase()))
}
type SearchRow = { id: string; name?: string; title?: string; username?: string; paramKey?: string }
export const searchRegistry = createSearchRegistry()
for (const source of [
  { id: 'users', endpoint: '/iam/users', path: '/iam/users', permission: 'iam:user:view', icon: 'lucide:users' },
  { id: 'files', endpoint: '/file/files', path: '/file/files', permission: 'file:file:view', icon: 'lucide:files' },
  { id: 'notices', endpoint: '/message/notices', path: '/message/notices', permission: 'message:notice:view', icon: 'lucide:bell' },
  { id: 'tasks', endpoint: '/message/todos', path: '/message/inbox', permission: undefined, icon: 'lucide:list-checks' },
  { id: 'apps', endpoint: '/openapi/apps', path: '/openapi/apps', permission: 'openapi:app:view', icon: 'lucide:app-window' },
  { id: 'parameters', endpoint: '/config/parameters', path: '/config/parameters', permission: 'config:parameter:view', icon: 'lucide:sliders-horizontal' },
]) {
  searchRegistry.register({
    id: source.id, labelKey: 'common.unifiedSearch.sources.' + source.id, capability: 'keyword', permission: source.permission,
    async search(request) {
      const response = await get<PageResult<SearchRow> | SearchRow[]>(source.endpoint, { keyword: request.query, page: request.page, size: request.size }, { signal: request.signal, timeout: 8000 })
      const rows = Array.isArray(response) ? response.slice((request.page - 1) * request.size, request.page * request.size) : response.items
      return {
        hasMore: Array.isArray(response) ? response.length > request.page * request.size : Number(response.total) > request.page * request.size,
        items: rows.map(row => ({ id: String(row.id), source: source.id, title: row.name || row.title || row.username || String(row.id), description: row.paramKey || row.username, icon: source.icon,
          target: { kind: 'route' as const, path: source.path, query: { focus: String(row.id), ...(source.id === 'tasks' ? { tab: 'tasks' } : {}) } },
        })),
      }
    },
  })
}
