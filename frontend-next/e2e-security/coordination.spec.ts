import { expect, test, type Page } from '@playwright/test'
import type { OperationCoordinator, OperationLease } from '../src/lib/browser-operation-coordinator'
import { login, mockApi } from '../e2e/mock-api'
import { fullApi } from '../e2e/full-api'
type Harness = { coordinator: OperationCoordinator; leases: Map<string, OperationLease> }
const origin = 'http://127.0.0.1:5196'
test.use({ locale: 'zh-CN' })
async function setup(page: Page) {
  await mockApi(page, origin); await page.goto('/login')
  await page.evaluate(async () => {
    const modulePath = '/src/lib/browser-operation-coordinator.ts'
    const { BrowserOperationCoordinator } = await import(modulePath)
    const state = window as unknown as Harness
    state.coordinator = new BrowserOperationCoordinator(); state.leases = new Map()
  })
}
async function acquire(page: Page, id: string, resource: string, resources?: string[], owner = '["site/api","1","admin","2"]') {
  return page.evaluate(async ({ id, resource, resources, owner }) => {
    const state = window as unknown as Harness
    try { state.leases.set(id, await state.coordinator.acquire({ owner, resource, resources })); return 'acquired' }
    catch (cause) { return (cause as { reason: string }).reason }
  }, { id, resource, resources, owner })
}
async function release(page: Page, id: string) { await page.evaluate(async id => { const state = window as unknown as Harness; await state.leases.get(id)?.release(); state.leases.delete(id) }, id) }

test('real browser tabs exclude overlapping targets, allow independent work, and release locks when a tab closes', async ({ page, context }) => {
  const peer = await context.newPage(); await setup(page); await setup(peer)
  expect(await acquire(page, 'first', 'tasks.1', ['tasks.2'])).toBe('acquired')
  expect(await acquire(peer, 'overlap', 'tasks.2')).toBe('busy')
  expect(await acquire(peer, 'all', 'tasks.*')).toBe('busy')
  expect(await acquire(peer, 'independent', 'tasks.3')).toBe('acquired')
  expect(await acquire(peer, 'tenant', 'tasks.1', undefined, '["site/api","another-tenant","admin","2"]')).toBe('acquired')
  await release(peer, 'independent'); await release(peer, 'tenant')
  await page.close()
  // Page closure and the browser lock manager's release are separate observable events.
  // Observe release without retrying an acquisition or weakening the fail-fast contract.
  await expect.poll(() => peer.evaluate(async () => (await navigator.locks.query()).held?.length || 0)).toBe(0)
  expect(await acquire(peer, 'after-close', 'tasks.*')).toBe('acquired')
  await release(peer, 'after-close')
  expect((await peer.evaluate(() => navigator.locks.query())).held).toEqual([])
})

test('a declined batch leaves no partial locks and does not automatically retry when its conflict disappears', async ({ page, context }) => {
  const peer = await context.newPage(); await setup(page); await setup(peer)
  expect(await acquire(page, 'holder', 'items.z')).toBe('acquired')
  expect(await acquire(peer, 'declined', 'items.a', ['items.z'])).toBe('busy')
  expect(await acquire(page, 'unleaked', 'items.a')).toBe('acquired')
  await release(page, 'holder'); await release(page, 'unleaked')
  const snapshot = await peer.evaluate(() => navigator.locks.query())
  expect(snapshot.held).toEqual([]); expect(snapshot.pending).toEqual([])
  expect(await peer.evaluate(() => (window as unknown as Harness).leases.has('declined'))).toBe(false)
})

test('production session commands coordinate before confirmation and retain a peer unknown after the sender closes', async ({ page, context }) => {
  const peer = await context.newPage()
  await fullApi(page, origin); await fullApi(peer, origin)
  let writes = 0
  for (const tab of [page, peer]) await tab.route(url => url.pathname === '/api/auth/sessions/other/revoke', route => { writes++; return route.abort('failed') })
  await login(page, '/auth/sessions'); await login(peer, '/auth/sessions')
  const first = page.getByRole('button', { name: '强制下线', exact: true }), second = peer.getByRole('button', { name: '强制下线', exact: true })
  await first.click(); await expect(page.getByRole('alertdialog')).toBeVisible()
  await second.click(); await expect(peer.getByRole('alert')).toBeVisible(); await expect(peer.getByRole('alertdialog')).toHaveCount(0)
  expect(writes).toBe(0)
  await page.getByRole('alertdialog').getByRole('button', { name: '取消', exact: true }).click()
  await second.click(); await peer.getByRole('alertdialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect(peer.getByRole('status').filter({ hasText: '有强制下线操作的结果尚未确认' })).toBeVisible()
  await expect(page.getByRole('status').filter({ hasText: '有强制下线操作的结果尚未确认' })).toBeVisible()
  await peer.close(); await page.reload()
  await expect(first).toBeDisabled(); expect(writes).toBe(1)
})

test('unsupported browser coordination rejects before confirmation and does not silently fall back to storage locks', async ({ page }) => {
  await fullApi(page, origin); let writes = 0
  await page.route(url => url.pathname === '/api/auth/sessions/other/revoke', route => { writes++; return route.fallback() })
  await login(page, '/auth/sessions')
  await page.evaluate(() => Object.defineProperty(navigator, 'locks', { value: undefined, configurable: true }))
  await page.getByRole('button', { name: '强制下线', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('当前浏览器无法安全协调多标签操作')
  await expect(page.getByRole('alertdialog')).toHaveCount(0); expect(writes).toBe(0)
})

test('a peer can reconcile the original LOCAL key and both tabs release it without replaying the mutation', async ({ page, context }) => {
  const peer = await context.newPage(); let writes = 0, key = ''
  for (const tab of [page, peer]) {
    await fullApi(tab, origin)
    await tab.route(url => url.pathname === '/api/config/parameters/refresh', route => { writes++; key = route.request().headers()['idempotency-key']!; return route.abort('failed') })
    await tab.route(url => url.pathname === '/api/common/requests/lookup', route => {
      expect(route.request().postDataJSON()).toEqual({ key })
      return route.fulfill({ json: { code: 0, data: { operationId: 'receipt-shared', operation: 'parameters.refresh', state: 'SUCCEEDED', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:01Z' } } })
    })
    await login(tab, '/config/parameters')
  }
  await page.getByRole('button', { name: '刷新缓存', exact: true }).click()
  await expect(page.getByRole('button', { name: '查询原操作结果', exact: true })).toBeVisible()
  // B was loaded before A submitted; storage notifications must update its existing registry.
  await expect(peer.getByRole('button', { name: '查询原操作结果', exact: true })).toBeVisible()
  await peer.getByRole('button', { name: '查询原操作结果', exact: true }).click()
  await expect(peer.getByRole('button', { name: '查询原操作结果', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '查询原操作结果', exact: true })).toHaveCount(0)
  expect(writes).toBe(1)
})
