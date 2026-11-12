import { expect, test } from '@playwright/test'
import { fullApi } from '../e2e/full-api'
import { login } from '../e2e/mock-api'
const origin = 'http://127.0.0.1:5196'
test.use({ locale: 'zh-CN' })

test('unknown revocation survives absent list entries and reload, with no replay or stored session details', async ({ page }) => {
  await fullApi(page, origin)
  let writes = 0, missing = false
  await page.route(url => url.pathname === '/api/auth/sessions/other/revoke', route => { writes++; return route.abort('failed') })
  await page.route(url => url.pathname === '/api/auth/sessions', route => missing
    ? route.fulfill({ json: { code: 0, data: { items: [], total: '0', page: '1', size: '20' } } }) : route.fallback())
  await login(page, '/auth/sessions')
  const button = page.getByRole('button', { name: '强制下线', exact: true })
  await button.evaluate(element => { for (let i = 0; i < 20; i++) (element as HTMLButtonElement).click() })
  await expect(page.getByRole('alertdialog')).toHaveCount(1)
  await page.getByRole('alertdialog').getByRole('button', { name: '确认', exact: true }).click()
  const pending = page.getByRole('status').filter({ hasText: '有强制下线操作的结果尚未确认' })
  await expect(pending).toBeVisible(); await expect(button).toBeDisabled()
  missing = true; await page.getByRole('button', { name: '刷新', exact: true }).click()
  await expect(button).toHaveCount(0); await expect(pending).toBeVisible()
  await page.reload(); await expect(pending).toBeVisible()
  missing = false; await page.getByRole('button', { name: '刷新', exact: true }).click()
  await expect(button).toBeDisabled(); expect(writes).toBe(1)
  const records = await page.evaluate(() => Object.entries(localStorage).filter(([key]) => key.startsWith('sma.operation.v1.')).map(([, value]) => value))
  expect(records).toHaveLength(1)
  expect(JSON.parse(records[0]!).resource).toBe('auth.sessions.other')
  expect(records[0]).not.toMatch(/isolated-e2e-token|test.member|127\.0\.0\.2|Browser testing/)
})

test('cancellation sends no command and acknowledged revocation releases its target', async ({ page }) => {
  const api = await fullApi(page, origin)
  await login(page, '/auth/sessions')
  const button = page.getByRole('button', { name: '强制下线', exact: true })
  await button.click(); await page.getByRole('alertdialog').getByRole('button', { name: '取消', exact: true }).click()
  expect(api.writes.filter(write => write.path.endsWith('/revoke'))).toHaveLength(0)
  await button.click(); await page.getByRole('alertdialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect.poll(() => api.writes.filter(write => write.path.endsWith('/revoke')).length).toBe(1)
  await expect(button).toBeEnabled()
  await expect(page.getByText('当前会话', { exact: true })).toBeVisible()
  expect(api.unmatched).toEqual([])
})
