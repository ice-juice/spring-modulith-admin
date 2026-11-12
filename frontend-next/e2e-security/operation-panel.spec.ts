import { expect, test, type Page } from '@playwright/test'
import { login } from '../e2e/mock-api'
import { fullApi } from '../e2e/full-api'
const origin = 'http://127.0.0.1:5196'
test.use({ locale: 'zh-CN' })
const panel = (page: Page) => page.getByRole('dialog', { name: '操作状态', exact: true })

test('restores original LOCAL work after reload, queries once, supports keyboard and narrow screens', async ({ page }) => {
  await fullApi(page, origin)
  let writes = 0, reads = 0, key = '', terminal = false
  await page.route(url => url.pathname === '/api/config/parameters/refresh', route => { writes++; key = route.request().headers()['idempotency-key']!; return route.abort('failed') })
  await page.route(url => url.pathname === '/api/common/requests/lookup', route => {
    reads++; expect(route.request().postDataJSON()).toEqual({ key })
    return route.fulfill({ json: { code: 0, data: terminal ? { operationId: 'receipt-1', operation: 'parameters.refresh', state: 'SUCCEEDED', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:01Z' } : { state: 'NOT_FOUND' } } })
  })
  await login(page, '/config/parameters')
  await page.getByRole('button', { name: '刷新缓存', exact: true }).click()
  await expect(page.getByRole('button', { name: '查询原操作结果', exact: true })).toBeVisible()
  await page.reload()
  const trigger = page.getByRole('button', { name: '操作状态', exact: true })
  await trigger.focus(); await page.keyboard.press('Enter')
  await expect(panel(page).getByText('结果未知', { exact: true })).toBeVisible()
  await panel(page).getByText('操作编号与对象', { exact: true }).click()
  await expect(panel(page).getByText(key, { exact: true })).toBeVisible()
  await panel(page).getByRole('button', { name: '核对此操作', exact: true }).click()
  await expect(panel(page).getByRole('alert')).toBeVisible()
  await expect(panel(page).getByText('结果未知', { exact: true })).toBeVisible()
  terminal = true
  await page.setViewportSize({ width: 320, height: 720 })
  const bounds = await panel(page).boundingBox()
  expect(bounds!.x).toBeGreaterThanOrEqual(0); expect(bounds!.width).toBeLessThanOrEqual(320)
  await page.screenshot({ path: '.e2e-runtime/security-c4b1-panel-320.png' })
  await panel(page).getByRole('button', { name: '核对此操作', exact: true }).click()
  await expect(panel(page).getByRole('status')).toContainText('原操作已确认成功')
  await expect(panel(page).getByText('当前没有待核对的操作。', { exact: true })).toBeVisible()
  expect(writes).toBe(1); expect(reads).toBe(2)
  await page.keyboard.press('Escape'); await expect(panel(page)).toHaveCount(0)
  await expect(trigger).toBeFocused()
})

test('native unknown work survives reopening without calling the LOCAL receipt endpoint', async ({ page }) => {
  await fullApi(page, origin)
  let writes = 0, reads = 0
  await page.route(url => url.pathname === '/api/auth/sessions/other/revoke', route => { writes++; return route.abort('failed') })
  await page.route(url => url.pathname === '/api/common/requests/lookup', route => { reads++; return route.fallback() })
  await login(page, '/auth/sessions')
  await page.getByRole('button', { name: '强制下线', exact: true }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.getByRole('status').filter({ hasText: '有强制下线操作的结果尚未确认' })).toBeVisible()
  await page.getByRole('button', { name: '操作状态', exact: true }).click()
  await expect(panel(page).getByText('结果未知', { exact: true })).toBeVisible()
  await expect(panel(page).getByRole('button', { name: '核对此操作', exact: true })).toHaveCount(0)
  await expect(panel(page).getByText(/请在原业务页面核对原任务/)).toBeVisible()
  await page.keyboard.press('Escape'); await page.reload()
  await page.getByRole('button', { name: '操作状态', exact: true }).click()
  await expect(panel(page).getByText('结果未知', { exact: true })).toBeVisible()
  expect(writes).toBe(1); expect(reads).toBe(0)
})

test('English empty state is keyboard accessible', async ({ page }) => {
  await fullApi(page, origin); await login(page, '/')
  await page.evaluate(async () => { const path = '/src/locales/index.ts'; const { i18n } = await import(path); i18n.global.locale.value = 'en-US' })
  const trigger = page.getByRole('button', { name: 'Operation status', exact: true })
  await trigger.focus(); await page.keyboard.press('Enter')
  const dialog = page.getByRole('dialog', { name: 'Operation status', exact: true })
  await expect(dialog.getByText('No operations awaiting confirmation.', { exact: true })).toBeVisible()
  await expect(dialog.getByRole('button', { name: 'Check this operation', exact: true })).toHaveCount(0)
  await page.evaluate(async () => { const path = '/src/stores/preferences.ts'; const { usePreferences } = await import(path); usePreferences().theme = 'dark' })
  await expect(page.locator('html')).toHaveClass(/dark/)
  await dialog.evaluate(async element => { await Promise.all(element.getAnimations().map(animation => animation.finished)) })
  await page.screenshot({ path: '.e2e-runtime/security-c4b1-panel-dark-en.png' })
  await page.keyboard.press('Escape'); await expect(trigger).toBeFocused()
})

test('peer status synchronizes and a lock closes the panel before a late receipt arrives', async ({ page, context }) => {
  const peer = await context.newPage()
  for (const tab of [page, peer]) await fullApi(tab, origin)
  let key = '', received = false, release!: () => void
  const held = new Promise<void>(resolve => { release = resolve })
  await page.route(url => url.pathname === '/api/config/parameters/refresh', route => { key = route.request().headers()['idempotency-key']!; return route.abort('failed') })
  await peer.route(url => url.pathname === '/api/common/requests/lookup', async route => {
    expect(route.request().postDataJSON()).toEqual({ key }); received = true; await held
    await route.fulfill({ json: { code: 0, data: { operationId: 'receipt-1', operation: 'parameters.refresh', state: 'SUCCEEDED', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:01Z' } } })
  })
  await login(page, '/config/parameters'); await login(peer, '/')
  await peer.getByRole('button', { name: '操作状态', exact: true }).click()
  await expect(panel(peer).getByText('当前没有待核对的操作。', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '刷新缓存', exact: true }).click()
  await expect(panel(peer).getByText('结果未知', { exact: true })).toBeVisible()
  await panel(peer).getByRole('button', { name: '核对此操作', exact: true }).click()
  await expect.poll(() => received).toBe(true)
  await peer.evaluate(async () => { const path = '/src/stores/auth.ts'; const { useAuth } = await import(path); useAuth().user.locked = true; window.dispatchEvent(new Event('sma:locked')) })
  await expect(panel(peer)).toHaveCount(0)
  release()
  await expect.poll(() => peer.evaluate(() => navigator.locks.query().then(value => value.held?.length || 0))).toBe(0)
  expect(await peer.evaluate(key => !!localStorage.getItem('sma.operation.v1.' + key), key)).toBe(true)
  await expect(peer.getByText('原操作已确认成功。请返回业务页面重新读取最新数据。', { exact: true })).toHaveCount(0)
})
