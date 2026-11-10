export interface WorkspaceTab {
  path: string
  titleKey?: string
  fallback: string
}

/** Keep one tab per page, remembering its latest filters and focused record. */
export function trackPageTab(
  tabs: WorkspaceTab[],
  next: WorkspaceTab,
  pathname: (path: string) => string,
): WorkspaceTab[] {
  const index = tabs.findIndex((tab) => pathname(tab.path) === pathname(next.path))
  if (index < 0) return [...tabs, next]
  return tabs.map((tab, position) => (position === index ? next : tab))
}
