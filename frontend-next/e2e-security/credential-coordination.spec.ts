import { expect, test, type Page } from '@playwright/test'
import { login, mockApi } from '../e2e/mock-api'
import { fullApi } from '../e2e/full-api'
const origin = 'http://127.0.0.1:5196'
const settings = { totpEnabled: false, totpPending: false, totpEnabledAt: null, securityPasswordSet: false, securityPasswordChangedAt: null, setupAvailable: true }
test.use({ locale: 'zh-CN' })
async function prepare(page: Page) {
  await mockApi(page, origin)
  await page.route(url => url.pathname === '/api/auth/security-settings', route => route.fulfill({ json: { code: 0, data: settings } }))
  await login(page, '/auth/security')
}
async function fill(page: Page) {
  await page.getByRole('button', { name: '设置安全操作密码', exact: true }).click()
  await page.locator('#security-login-password').fill('Historical!Password2025')
  await page.locator('#security-new').fill('a1b2c3'); await page.locator('#security-confirm').fill('a1b2c3')
}
test('credential UNKNOWN is shared before the response, survives closing the sender and never stores secrets', async ({ page, context }) => {
  const peer = await context.newPage(); await prepare(page); await prepare(peer)
  await fill(page); await fill(peer)
  let writes = 0, finish!: () => void
  const pending = new Promise<void>(resolve => { finish = resolve })
  await page.route(url => url.pathname === '/api/auth/security-passwords', async route => { writes++; await pending; await route.abort('failed') })
  await peer.route(url => url.pathname === '/api/auth/security-passwords', route => { writes++; return route.abort('failed') })
  await page.getByRole('button', { name: '确认保存', exact: true }).click()
  await expect.poll(() => writes).toBe(1)
  await expect(peer.getByRole('alert')).toContainText('操作结果尚未确认')
  await expect(peer.locator('#security-login-password')).toHaveCount(0)
  await expect(peer.getByRole('button', { name: '设置安全操作密码', exact: true })).toBeDisabled()
  finish(); await expect(page.getByRole('alert')).toContainText('操作结果尚未确认')
  await page.close(); await peer.reload()
  await peer.getByRole('button', { name: '重新查询状态', exact: true }).click()
  await expect(peer.getByRole('button', { name: '设置安全操作密码', exact: true })).toBeDisabled(); expect(writes).toBe(1)
  const raw = await peer.evaluate(() => Object.entries(localStorage).filter(([key]) => key.startsWith('sma.credential-intent.v2.')).map(([, value]) => value))
  expect(raw).toHaveLength(1); expect(Object.keys(JSON.parse(raw[0]!)).sort()).toEqual(['key', 'schema', 'scope', 'state'])
  expect(raw[0]).not.toMatch(/Historical|a1b2c3|isolated-e2e-token|password|secret|challengeToken/)
})

test('a peer queries the original password key and only the matching terminal receipt releases both tabs', async ({ page, context }) => {
  const peer = await context.newPage(); let writes = 0, key = '', reads = 0
  for (const tab of [page, peer]) {
    await fullApi(tab, origin)
    await tab.route(url => url.pathname === '/api/iam/users/me/password', route => { writes++; key = route.request().headers()['idempotency-key']!; return route.abort('failed') })
    await tab.route(url => url.pathname === '/api/common/requests/lookup', route => {
      reads++; expect(route.request().postDataJSON()).toEqual({ key })
      return route.fulfill({ json: { code: 0, data: { operationId: 'password-receipt', operation: reads === 1 ? 'wrong-operation' : 'iam.users.9721c28fc095e11b25656509', state: 'FAILED_FINAL', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:01Z' } } })
    })
    await login(tab, '/iam/users')
    await tab.getByRole('button', { name: '账号', exact: true }).click(); await tab.getByRole('menuitem', { name: '修改密码', exact: true }).click()
  }
  const first = page.getByRole('dialog', { name: '修改密码', exact: true }), second = peer.getByRole('dialog', { name: '修改密码', exact: true })
  await first.getByLabel('原密码', { exact: true }).fill('Historical!Password2025')
  await first.getByLabel('新密码', { exact: true }).fill('Abcd1234'); await first.getByLabel('确认密码', { exact: true }).fill('Abcd1234')
  await first.getByRole('button', { name: '保存', exact: true }).click()
  await expect(second.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  await second.getByRole('button', { name: '查询原操作结果', exact: true }).click()
  await expect.poll(() => reads).toBe(1); await expect(second.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  await second.getByRole('button', { name: '查询原操作结果', exact: true }).click()
  await expect(first.getByRole('button', { name: '保存', exact: true })).toBeEnabled(); await expect(second.getByRole('button', { name: '保存', exact: true })).toBeEnabled()
  expect(writes).toBe(1); expect(reads).toBe(2)
})

test('missing Web Locks refuses credential dispatch with an actionable message', async ({ page }) => {
  await prepare(page); let writes = 0
  await page.evaluate(() => Object.defineProperty(navigator, 'locks', { configurable: true, value: undefined }))
  await page.route(url => url.pathname === '/api/auth/security-passwords', route => { writes++; return route.abort('failed') })
  await fill(page); await page.getByRole('button', { name: '确认保存', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('当前浏览器无法安全协调多标签操作')
  expect(writes).toBe(0)
})
