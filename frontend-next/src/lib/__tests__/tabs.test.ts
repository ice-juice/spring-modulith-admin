import { expect, it } from 'vitest'
import { trackPageTab, type WorkspaceTab } from '../tabs'

it('reuses the users tab when opening role members or clearing their filter, and remembers the latest location', () => {
  const pathname = (path: string) => new URL(path, 'https://example.test').pathname
  let tabs: WorkspaceTab[] = [{ path: '/', fallback: 'Home' }]
  for (const path of [
    '/iam/roles',
    '/iam/users?roleId=123',
    '/iam/users',
    '/iam/users?focus=456',
  ]) {
    tabs = trackPageTab(tabs, { path, fallback: path }, pathname)
  }
  expect(tabs.map((tab) => tab.path)).toEqual(['/', '/iam/roles', '/iam/users?focus=456'])
  const restored = tabs.reduce<WorkspaceTab[]>(
    (result, tab) => trackPageTab(result, tab, pathname),
    [],
  )
  expect(restored).toEqual(tabs)
})
