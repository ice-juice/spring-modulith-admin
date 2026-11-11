import type { Component } from 'vue'
import { lineIcons } from './line-catalog'
import { threeIcons } from './three-catalog'
export interface IconEntry { id: string; category: string; zh: string; en: string; component?: Component; file?: string }
export const categories = ['people', 'security', 'files', 'communication', 'data', 'commerce', 'system', 'actions'] as const
export const iconCatalog: IconEntry[] = [...lineIcons, ...threeIcons]
const byId = new Map(iconCatalog.map(entry => [entry.id, entry]))
const aliases: Record<string, string> = {
  User: 'users', UserFilled: 'users', Avatar: 'user-round', Setting: 'settings', Tools: 'settings', Lock: 'shield',
  Key: 'key-round', Files: 'files', Folder: 'folder', Bell: 'bell', Message: 'bell', Monitor: 'activity',
  DataAnalysis: 'activity', HomeFilled: 'layout-dashboard', Menu: 'list', Grid: 'layout-dashboard', Connection: 'network', Coin: 'database',
}
export function resolveIcon(value?: string | null): IconEntry | undefined {
  if (!value) return undefined
  const direct = byId.get(value)
  if (direct) return direct
  const name = aliases[value] || value.replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase()
  return byId.get('lucide:' + name)
}
export function iconLabel(entry: IconEntry, locale: string) { return locale.startsWith('zh') ? entry.zh : entry.en }
export function searchIcons(query: string, kind = 'all', category = 'all') {
  const words = query.trim().toLowerCase().split(/\s+/).filter(Boolean)
  return iconCatalog.filter(entry => (kind === 'all' || entry.id.startsWith(kind + ':')) && (category === 'all' || entry.category === category) && words.every(word => `${entry.id} ${entry.zh} ${entry.en}`.toLowerCase().includes(word)))
}
