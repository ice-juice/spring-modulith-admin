import { expect, test, type Page } from '@playwright/test'
import { login } from '../e2e/mock-api'
import { fullApi } from '../e2e/full-api'
const origin = 'http://127.0.0.1:5196'
const panel = (page: Page) => page.getByRole('dialog', { name: '操作状态', exact: true })
const notice = (page: Page) => page.locator('[data-operation-recovery]')
test.use({ locale: 'zh-CN' })

test('leaving and reloading retain a visible warning and an authorized static business recovery entry', async ({ page }) => {
  await fullApi(page, origin)
  let writes = 0, reads = 0, key = ''
  await page.route(url => url.pathname === '/api/config/parameters/refresh', route => { writes++; key = route.request().headers()['idempotency-key']!; return route.abort('failed') })
  await page.route(url => url.pathname === '/api/common/requests/lookup', route => { reads++; return route.fallback() })
  await login(page, '/config/parameters')
  await page.getByRole('button', { name: '刷新缓存', exact: true }).click()
  await expect(notice(page)).toContainText('有 1 项操作仍待确认')
  await expect(notice(page)).toContainText('离开或刷新会丢失未保存的输入和所选文件')
  await page.getByRole('link', { name: '操作台', exact: true }).click()
  await expect(page).toHaveURL(origin + '/')
  await expect(notice(page)).toBeVisible()
  await page.reload(); await expect(notice(page)).toBeVisible()
  await notice(page).getByRole('button', { name: '查看待核对操作', exact: true }).click()
  await panel(page).getByText('操作编号与对象', { exact: true }).click()
  await expect(panel(page).getByText(key, { exact: true })).toBeVisible()
  await panel(page).getByRole('button', { name: '前往业务页面', exact: true }).click()
  await expect(page).toHaveURL(origin + '/config/parameters')
  await expect(panel(page)).toHaveCount(0)
  await expect(notice(page)).toBeVisible()
  expect(writes).toBe(1); expect(reads).toBe(0)
  await page.setViewportSize({ width: 320, height: 720 })
  await expect(notice(page).getByRole('button')).toBeInViewport()
  const buttons = await page.locator('.admin-topbar button').evaluateAll(elements => elements.filter(element => element.getClientRects().length).map(element => { const box = element.getBoundingClientRect(); return { left: box.left, right: box.right } }))
  expect(buttons.every(box => box.left >= 0 && box.right <= 320)).toBe(true)
  await page.screenshot({ path: '.e2e-runtime/security-c4b2-recovery-320.png' })
})

test('permission removal hides business recovery and locking hides the entire pending notice', async ({ page }) => {
  await fullApi(page, origin)
  await page.route(url => url.pathname === '/api/auth/sessions/other/revoke', route => route.abort('failed'))
  await login(page, '/auth/sessions')
  await page.getByRole('button', { name: '强制下线', exact: true }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: '确认', exact: true }).click()
  await notice(page).getByRole('button').click()
  await expect(panel(page).getByRole('button', { name: '前往业务页面', exact: true })).toBeVisible()
  await page.evaluate(async () => { const path = '/src/stores/auth.ts'; const { useAuth } = await import(path); useAuth().user.superAdmin = false; useAuth().user.permissions = [] })
  await expect(panel(page).getByRole('button', { name: '前往业务页面', exact: true })).toHaveCount(0)
  await expect(panel(page).getByText('结果未知', { exact: true })).toBeVisible()
  await page.evaluate(async () => { const path = '/src/stores/auth.ts'; const { useAuth } = await import(path); useAuth().user.locked = true; window.dispatchEvent(new Event('sma:locked')) })
  await expect(notice(page)).toHaveCount(0); await expect(panel(page)).toHaveCount(0)
})

test('a reloaded upload checks only its original native receipt and never resumes retained metadata as a file', async ({ page }) => {
  await fullApi(page, origin)
  let writes = 0, reads = 0, key = '', complete = false
  await page.route(url => url.pathname === '/api/file/files', route => {
    if (route.request().method() !== 'POST') return route.fallback()
    writes++; key = route.request().headers()['idempotency-key']!; return route.abort('failed')
  })
  await page.route(url => url.pathname === '/api/file/uploads/lookup', route => {
    reads++; expect(route.request().postDataJSON()).toEqual({ key })
    return route.fulfill({ json: { code: 0, data: { uploadId: '11111111-1111-4111-8111-111111111111', state: complete ? 'SUCCEEDED' : 'ACCEPTED', fileId: complete ? '123' : null, resumable: !complete } } })
  })
  await login(page, '/file/files')
  await page.locator('input[type="file"]').setInputFiles({ name: 'private.txt', mimeType: 'text/plain', buffer: Buffer.from('private bytes') })
  await expect(notice(page)).toBeVisible(); await page.reload()
  await notice(page).getByRole('button').click()
  await panel(page).getByRole('button', { name: '核对此操作', exact: true }).click()
  await expect(panel(page).getByRole('status')).toContainText('仍在处理中')
  await expect(panel(page).getByText('等待结果', { exact: true })).toBeVisible()
  expect(writes).toBe(1); expect(reads).toBe(1)
  complete = true
  await panel(page).getByRole('button', { name: '核对此操作', exact: true }).click()
  await expect(panel(page).getByRole('status')).toContainText('已确认成功')
  await expect(panel(page).getByText('当前没有待核对的操作。', { exact: true })).toBeVisible()
  expect(writes).toBe(1); expect(reads).toBe(2)
  await page.keyboard.press('Escape'); await expect(notice(page)).toHaveCount(0)
})
