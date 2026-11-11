import type { Page } from '@playwright/test'
import { tenantEntry } from './tenant-entry'
export const userId = '9007199254740993121', roleId = '9007199254740993221'
export async function mockApi(page: Page, origin = process.env.SMA_E2E_MOCK_ORIGIN || 'http://127.0.0.1:5174') {
  const writes: { path: string; method: string; body: Record<string, unknown> }[] = []
  const failures: string[] = []
  let failUserSave = false
  const user = { id: userId, username: 'test.user', name: '测试成员', email: 'member@example.test', enabled: true, roleIds: [roleId], postIds: [], departmentId: null, locale: 'zh-CN', timeZone: 'Asia/Shanghai' }
  const me = { id: '101', username: 'e2e.admin', name: '测试管理员', portal: 'admin', superAdmin: true, permissions: ['iam:user:view', 'iam:user:create', 'iam:user:update', 'iam:user:delete', 'iam:user:reset-password', 'iam:role:view', 'iam:role:create', 'iam:role:update', 'iam:role:delete', 'iam:role:grant'], menus: [{ id: 'org', parentId: null, name: '组织权限', type: 'DIRECTORY', path: null, icon: 'User', openMode: 'ROUTE', children: [{ id: 'users', parentId: 'org', name: '用户管理', type: 'MENU', path: '/iam/users', permission: 'iam:user:view', icon: 'User', openMode: 'ROUTE', children: [] }, { id: 'roles', parentId: 'org', name: '角色管理', type: 'MENU', path: '/iam/roles', permission: 'iam:role:view', icon: 'Lock', openMode: 'ROUTE', children: [] }] }], navigation: [], locale: 'zh-CN', timeZone: 'Asia/Shanghai', theme: 'light', tabsEnabled: true, keepTabsAfterReload: false }
  await page.route(origin + '/api/**', async route => {
    const request = route.request(), path = new URL(request.url()).pathname.replace(/^\/api/, ''), method = request.method()
    let body: Record<string, unknown> = {}; try { body = request.postDataJSON() || {} } catch { /* GET has no body. */ }
    const ok = (data: unknown) => route.fulfill({ json: { code: 0, error: null, message: 'ok', data } })
    if (method !== 'GET') writes.push({ path, method, body })
    if (path === '/tenant/entry') return ok(tenantEntry())
    if (path === '/auth/login') return ok({ token: 'isolated-e2e-token' })
    if (path === '/workspace/shelf') return ok({ scope: '1.admin.101', mode: 'drawer', version: '0', bookmarks: [], targets: [] })
    if (path === '/auth/me') return ok(me)
    if (path === '/message/summary') return ok({ unreadNotices: '0', pendingTodos: '0', unreadMessages: '0' })
    if (path === '/message/socket-ticket') return ok({ ticket: 'isolated-test-ticket' })
    if (path === '/workspace/dashboard') return ok({ metrics: [{ id: 'users', name: '用户管理', value: '1', path: '/iam/users' }], entries: [] })
    if (path === '/iam/users' && method === 'GET') return ok({ items: [user], page: '1', size: '20', total: '1' })
    if (path === '/iam/users/' + userId && method === 'GET') return ok(user)
    if (path === '/iam/users/' + userId && method === 'PUT') { if (failUserSave) { failUserSave = false; return route.fulfill({ json: { code: 1234, error: 'test.writeFailed', message: '模拟保存失败', data: null } }) }; Object.assign(user, body); return ok(user) }
    if (path === '/iam/users' && method === 'POST') return ok({ ...user, id: '9007199254740993122' })
    if (path === '/iam/roles') return ok([{ id: roleId, name: '测试角色', code: 'TEST', parentId: '0', enabled: true, sort: 0, dataScope: 'SELF', departmentIds: [] }])
    if (path === '/iam/roles/options') return ok([{ value: roleId, label: '测试角色' }])
    if (path === '/iam/posts/options' || path === '/iam/departments/tree' || path === '/iam/roles/tree') return ok([])
    if (path === '/iam/permissions/tree') return ok([{ id: 'menu', name: '组织管理', children: [{ id: 'read', name: '查看用户' }, { id: 'write', name: '编辑用户' }, { id: 'protected', name: '受保护节点', disabled: true }] }])
    if (path === '/iam/roles/' + roleId + '/permissions' && method === 'GET') return ok(['menu', 'read', 'write'])
    if (path === '/iam/roles/' + roleId + '/permissions' && method === 'PUT') return ok(null)
    if (path === '/auth/logout') return ok(null)
    failures.push(method + ' ' + path)
    return route.fulfill({ status: 501, json: { code: 501, message: 'Unimplemented test fixture', data: null } })
  })
  await page.route('**/actuator/health', route => route.fulfill({ json: { status: 'UP' } }))
  await page.routeWebSocket('**/api/message/ws?*', socket => { socket.onMessage(() => {}) })
  return { writes, failures, failNextUserSave: () => { failUserSave = true } }
}
export async function login(page: Page, target = '/iam/users') {
  await page.goto(target)
  await page.getByLabel('用户名', { exact: true }).fill('e2e.admin')
  await page.getByLabel('密码', { exact: true }).fill('IsolatedTestPassword!2026')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL(url => url.pathname !== '/login')
}
