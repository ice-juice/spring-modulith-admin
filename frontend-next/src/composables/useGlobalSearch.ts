import { onScopeDispose, ref } from 'vue'
import { searchRegistry, type SearchContext, type SearchHit, type SearchProvider } from '@/lib/search'
export interface SearchGroup { provider: SearchProvider; items: SearchHit[]; page: number; hasMore: boolean; loading: boolean; error: boolean }
export function useGlobalSearch(registry = searchRegistry) {
  const groups = ref<SearchGroup[]>([])
  let generation = 0, controller: AbortController | undefined, query = '', context: SearchContext | undefined
  function clear() { generation++; controller?.abort(); controller = undefined; groups.value = []; context = undefined; query = '' }
  async function load(id: string, more = false) {
    const group = groups.value.find(item => item.provider.id === id)
    if (!group || group.loading || !context || !controller) return
    const current = generation, page = more ? group.page + 1 : group.page || 1
    group.loading = true; group.error = false
    try {
      const result = await group.provider.search({ query, page, size: 5, signal: controller.signal, context })
      if (current !== generation || controller?.signal.aborted) return
      group.items = more ? [...new Map([...group.items, ...result.items].map(item => [item.id, item])).values()] : result.items
      group.page = page; group.hasMore = result.hasMore
    } catch {
      if (current === generation && !controller?.signal.aborted) group.error = true
    } finally { if (current === generation) group.loading = false }
  }
  function search(value: string, actor: SearchContext) {
    clear(); query = value.trim().slice(0, 100); context = actor
    if (!query) return Promise.resolve([])
    controller = new AbortController()
    groups.value = registry.available(actor).map(provider => ({ provider, items: [], page: 0, hasMore: false, loading: false, error: false }))
    return Promise.allSettled(groups.value.map(group => load(group.provider.id)))
  }
  onScopeDispose(clear)
  return { groups, search, more: (id: string) => load(id, true), retry: (id: string) => load(id, !!groups.value.find(g => g.provider.id === id)?.page), clear }
}
