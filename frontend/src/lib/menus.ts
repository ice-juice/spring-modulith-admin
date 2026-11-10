import type { MenuItem } from '@/stores/auth'
export function findMenu(
  menus: MenuItem[],
  predicate: (menu: MenuItem) => boolean,
): MenuItem | undefined {
  for (const menu of menus) {
    if (predicate(menu)) return menu
    const child = findMenu(menu.children || [], predicate)
    if (child) return child
  }
}
export function filterMenus(menus: MenuItem[], query: string): MenuItem[] {
  const keyword = query.trim().toLocaleLowerCase()
  if (!keyword) return menus
  return menus.flatMap((menu) => {
    if (menu.name.toLocaleLowerCase().includes(keyword)) return [menu]
    const children = filterMenus(menu.children || [], query)
    return children.length ? [{ ...menu, children }] : []
  })
}
export function menuRoute(menu: MenuItem): string {
  return menu.openMode === 'IFRAME' ? '/frame/' + menu.id : menu.path || '/'
}
