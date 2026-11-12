import { expect, test } from '@playwright/test'
import { fullApi } from '../e2e/full-api'
import { login } from '../e2e/mock-api'
const origin = 'http://127.0.0.1:5196'
test.use({ locale: 'zh-CN' })
for (const config of [
  { path: '/monitor/sql', label: '重置', endpoint: '/api/monitor/sql', method: 'DELETE', resource: 'monitor.sql.statistics' },
  { path: '/monitor/events', label: '重试', endpoint: '/api/monitor/publications/event1/retry', method: 'POST', resource: 'monitor.publications.event1' },
]) {
  test(config.path + ' keeps an uncertain native write blocked after current-state reads and reload', async ({ page }) => {
    await fullApi(page, origin); let writes = 0
    await page.route(url => url.pathname === config.endpoint, route => {
      if (route.request().method() !== config.method) return route.fallback()
      writes++; return route.abort('failed')
    })
    await login(page, config.path)
    const button = page.getByRole('button', { name: config.label, exact: true })
    await button.evaluate(element => { for (let i = 0; i < 20; i++) (element as HTMLButtonElement).click() })
    await expect(page.getByRole('alertdialog')).toHaveCount(1)
    await page.getByRole('alertdialog').getByRole('button', { name: '确认', exact: true }).click()
    const pending = page.getByRole('status').filter({ hasText: '原操作结果尚未确认，同一目标暂不能再次提交' })
    await expect(pending).toBeVisible(); await expect(button).toBeDisabled()
    // An ordinary read (SQL search / publication refresh) must not reconcile the command.
    await page.getByRole('button', { name: config.path.endsWith('sql') ? '查询' : '刷新', exact: true }).click()
    await expect(pending).toBeVisible(); await expect(button).toBeDisabled()
    await page.reload(); await expect(pending).toBeVisible(); await expect(button).toBeDisabled(); expect(writes).toBe(1)
    const records = await page.evaluate(() => Object.entries(localStorage).filter(([key]) => key.startsWith('sma.operation.v1.')).map(([, value]) => JSON.parse(value)))
    expect(records).toHaveLength(1); expect(records[0].resource).toBe(config.resource)
    expect(JSON.stringify(records)).not.toMatch(/isolated-e2e-token|RoleChangedEvent|AuditListener|mapper.selectUsers/)
  })
}

test('unknown event resubmission does not block a different event, and current state never reconciles it', async ({ page }) => {
  await fullApi(page, origin); const writes: string[] = []
  let changed = false
  const item = (id: string, eventType: string) => ({ id, eventType, listener: 'AuditListener', status: changed ? 'COMPLETED' : 'FAILED', attempts: 1, publishedAt: '2026-11-10T08:00:00Z', lastResubmissionAt: null, completedAt: null })
  await page.route(url => url.pathname === '/api/monitor/publications', route => route.fulfill({ json: { code: 0, data: { items: [item('event1', 'FirstEvent'), item('event2', 'SecondEvent')], total: '2', page: '1', size: '20' } } }))
  await page.route(url => /\/api\/monitor\/publications\/[^/]+\/retry$/.test(url.pathname), route => { writes.push(new URL(route.request().url()).pathname); return writes.length === 1 ? route.abort('failed') : route.fulfill({ json: { code: 0, data: null } }) })
  await login(page, '/monitor/events')
  const first = page.getByRole('row').filter({ hasText: 'FirstEvent' }), second = page.getByRole('row').filter({ hasText: 'SecondEvent' })
  await first.getByRole('button', { name: '重试', exact: true }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect(first.getByRole('button', { name: '重试', exact: true })).toBeDisabled()
  await second.getByRole('button', { name: '重试', exact: true }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: '取消', exact: true }).click()
  expect(writes).toHaveLength(1)
  await second.getByRole('button', { name: '重试', exact: true }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect.poll(() => writes.length).toBe(2)
  changed = true; await page.getByRole('button', { name: '刷新', exact: true }).click()
  await expect(page.getByRole('button', { name: '重试', exact: true })).toHaveCount(0)
  await expect(page.getByRole('status').filter({ hasText: '原操作结果尚未确认' })).toBeVisible()
  changed = false; await page.reload()
  await expect(first.getByRole('button', { name: '重试', exact: true })).toBeDisabled()
  await expect(second.getByRole('button', { name: '重试', exact: true })).toBeEnabled()
})

test('leaving while SQL confirmation is open cannot send a late reset', async ({ page }) => {
  const api = await fullApi(page, origin); await login(page, '/monitor/sql')
  await page.getByRole('button', { name: '重置', exact: true }).click()
  // The global confirmation may outlive its route, but its original intent must not.
  await page.evaluate(() => { history.pushState({}, '', '/monitor/events'); window.dispatchEvent(new PopStateEvent('popstate')) })
  await expect(page).toHaveURL(/\/monitor\/events$/)
  await page.getByRole('alertdialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.getByRole('button', { name: '重试', exact: true })).toBeVisible()
  expect(api.writes.filter(write => write.path === '/monitor/sql' && write.method === 'DELETE')).toHaveLength(0)
})
