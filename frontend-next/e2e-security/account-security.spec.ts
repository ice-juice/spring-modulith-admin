import { expect, test } from '@playwright/test'
import { login, mockApi } from '../e2e/mock-api'
import { fullApi } from '../e2e/full-api'
const origin = 'http://127.0.0.1:5196'
const settings = { totpEnabled: false, totpPending: false, totpEnabledAt: null, securityPasswordSet: false, securityPasswordChangedAt: null, setupAvailable: true }
test.use({ locale: 'zh-CN' })
test('an uncertain credential mutation stays blocked after status reads and a full page reload without storing secrets', async ({ page }) => {
  await mockApi(page, origin); let writes = 0, reads = 0
  await page.route(url => url.pathname === '/api/auth/security-settings', route => { reads++; return route.fulfill({ json: { code: 0, data: settings } }) })
  await page.route(url => url.pathname === '/api/auth/security-passwords', route => {
    writes++; expect(route.request().postDataJSON().newPassword).toBe('a1b2c3')
    return route.abort('failed')
  })
  await login(page, '/auth/security')
  await page.getByRole('button', { name: '设置安全操作密码', exact: true }).click()
  await page.locator('#security-login-password').fill('Historical!Password2025')
  await page.locator('#security-new').fill('a1b2c3'); await page.locator('#security-confirm').fill('a1b2c3')
  await page.locator('.security-form').evaluate(form => { for (let i = 0; i < 20; i++) (form as HTMLFormElement).requestSubmit() })
  await expect(page.getByRole('alert')).toContainText('操作结果尚未确认')
  await expect(page.locator('#security-login-password')).toHaveValue('')
  await page.getByRole('button', { name: '重新查询状态', exact: true }).click()
  await expect(page.getByRole('button', { name: '设置安全操作密码', exact: true })).toBeDisabled()
  await expect(page.getByRole('alert')).toContainText('操作结果尚未确认')
  await page.reload()
  await expect(page.getByRole('button', { name: '设置安全操作密码', exact: true })).toBeDisabled()
  await expect(page.getByRole('alert')).toContainText('操作结果尚未确认')
  expect(writes).toBe(1); expect(reads).toBeGreaterThanOrEqual(3)
  const raw = await page.evaluate(() => Object.entries(localStorage).find(([key]) => key.startsWith('sma.credential-intent.v2.'))?.[1] || null)
  expect(Object.keys(JSON.parse(raw!)).sort()).toEqual(['key', 'schema', 'scope', 'state'])
  expect(raw).not.toMatch(/Historical|a1b2c3|isolated-e2e-token|password|secret|challengeToken/)
})
test('acknowledged enrollment shows a local QR and manual secret without persisting provisioning material', async ({ page }) => {
  await mockApi(page, origin)
  await page.route(url => url.pathname === '/api/auth/security-settings', route => route.fulfill({ json: { code: 0, data: settings } }))
  await page.route(url => url.pathname === '/api/auth/totp-enrollments', route => route.fulfill({ json: { code: 0, data: {
    challengeToken: 'fixture-confirmed-enrollment', secret: 'MANUALSECRET', uri: 'otpauth://totp/test?secret=MANUALSECRET', expiresAt: new Date(Date.now() + 180000).toISOString(), replacing: false,
  } } }))
  await login(page, '/auth/security')
  await page.getByRole('button', { name: '绑定验证器', exact: true }).click()
  await page.locator('#security-login-password').fill('Historical!Password2025')
  await page.getByRole('button', { name: '确认保存', exact: true }).click()
  await expect(page.locator('#manual-secret')).toHaveValue('MANUALSECRET')
  await expect(page.getByRole('img', { name: '验证器绑定二维码' })).toBeVisible()
  expect(await page.evaluate(() => Object.entries(localStorage).find(([key]) => key.startsWith('sma.credential-intent.v2.'))?.[1] || null)).toBeNull()
  const saved = await page.evaluate(() => JSON.stringify([Object.entries(localStorage), Object.entries(sessionStorage)]))
  expect(saved).not.toMatch(/MANUALSECRET|fixture-confirmed-enrollment|Historical!Password2025/)
})

test('an unknown lock remains shielded after reload and logout failure still removes the original local session', async ({ page }) => {
  await fullApi(page, origin); let locks = 0, unlocks = 0, logouts = 0
  await page.route(url => url.pathname === '/api/auth/lock', route => { locks++; return route.abort('failed') })
  await page.route(url => url.pathname === '/api/auth/unlock', route => { unlocks++; return route.fallback() })
  await page.route(url => url.pathname === '/api/auth/logout', route => { logouts++; return route.abort('failed') })
  await login(page, '/iam/users')
  await page.getByRole('button', { name: '账号', exact: true }).click()
  await page.getByRole('menuitem', { name: '锁屏', exact: true }).click()
  const screen = page.getByRole('dialog', { name: '已锁屏', exact: true })
  await expect(screen).toBeVisible(); await expect(screen.getByRole('alert')).toContainText('锁屏状态尚未确认')
  await page.reload(); await expect(screen).toBeVisible(); await expect(screen.getByRole('button', { name: '解锁', exact: true })).toBeDisabled()
  await page.keyboard.press('Escape'); await expect(screen).toBeVisible()
  await screen.getByRole('button', { name: '退出登录', exact: true }).click()
  await expect(page).toHaveURL(/\/login$/)
  expect(await page.evaluate(() => sessionStorage.getItem('sma.access-token'))).toBeNull()
  expect(locks).toBe(1); expect(unlocks).toBe(0); expect(logouts).toBe(1)
})
test('an unknown own-password change queries the original receipt after reload without retaining or resending passwords', async ({ page }) => {
  await fullApi(page, origin); let writes = 0, key = '', queries = 0
  await page.route(url => url.pathname === '/api/iam/users/me/password', route => {
    writes++; key = route.request().headers()['idempotency-key'] || ''; return route.abort('failed')
  })
  await page.route(url => url.pathname === '/api/common/requests/lookup', route => {
    queries++; expect(route.request().postDataJSON()).toEqual({ key })
    return queries === 1 ? route.fulfill({ status: 404, json: { code: 404, message: 'Not observed' } }) : route.fulfill({ json: { code: 0, data: {
      operationId: 'password-receipt', operation: 'iam.users.9721c28fc095e11b25656509', state: 'FAILED_FINAL', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:01Z',
    } } })
  })
  await login(page, '/iam/users')
  async function openPassword() { await page.getByRole('button', { name: '账号', exact: true }).click(); await page.getByRole('menuitem', { name: '修改密码', exact: true }).click() }
  await openPassword(); const dialog = page.getByRole('dialog', { name: '修改密码', exact: true })
  await dialog.getByLabel('原密码', { exact: true }).fill('Historical!Password2026')
  await dialog.getByLabel('新密码', { exact: true }).fill('Abcd1234'); await dialog.getByLabel('确认密码', { exact: true }).fill('Abcd1234')
  await dialog.locator('form').evaluate(form => { for (let i = 0; i < 20; i++) (form as HTMLFormElement).requestSubmit() })
  await expect(dialog.getByRole('alert')).toContainText('操作结果尚未确认')
  await expect(dialog.getByLabel('原密码', { exact: true })).toHaveValue('')
  await page.reload(); await openPassword()
  await expect(dialog.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  await dialog.getByRole('button', { name: '查询原操作结果', exact: true }).click()
  await expect.poll(() => queries).toBe(1); await expect(dialog.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  await dialog.getByRole('button', { name: '查询原操作结果', exact: true }).click()
  await expect(dialog.getByRole('button', { name: '保存', exact: true })).toBeEnabled()
  expect(writes).toBe(1); expect(queries).toBe(2)
  expect(await page.evaluate(() => Object.entries(localStorage).find(([key]) => key.startsWith('sma.credential-intent.v2.'))?.[1] || null)).toBeNull()
  const saved = await page.evaluate(() => JSON.stringify([Object.entries(localStorage), Object.entries(sessionStorage)]))
  expect(saved).not.toMatch(/Historical!Password2026|Abcd1234/)
})
