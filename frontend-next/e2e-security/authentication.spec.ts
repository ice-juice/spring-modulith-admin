import { expect, test, type Page } from '@playwright/test'
import { mockApi } from '../e2e/mock-api'
const origin = 'http://127.0.0.1:5196'
test.use({ locale: 'zh-CN' })
function barrier() {
  let release!: () => void
  return { wait: new Promise<void>(resolve => { release = resolve }), release: () => release() }
}
async function fillLogin(page: Page) {
  await page.goto('/login?redirect=/iam/users')
  await page.getByLabel('用户名', { exact: true }).fill('e2e.admin')
  await page.getByLabel('密码', { exact: true }).fill('SyntheticPassword!2026')
}
async function repeatSubmit(page: Page) {
  await page.locator('form').evaluate(form => { for (let i = 0; i < 20; i++) (form as HTMLFormElement).requestSubmit() })
}
async function leaveViaRouter(page: Page) {
  await page.evaluate(async () => {
    const path = '/src/router/index.ts'
    const { default: router } = await import(path)
    await router.push('/brand-preview')
  })
  await expect(page).toHaveURL(/\/brand-preview$/)
}
test('rapid login and OTP submissions send one request each and retain the MFA handoff', async ({ page }) => {
  await mockApi(page, origin)
  const login = barrier(), mfa = barrier(); let logins = 0, verifications = 0
  await page.route(url => url.pathname === '/api/auth/login', async route => {
    logins++; await login.wait
    await route.fulfill({ json: { code: 0, data: { status: 'MFA_REQUIRED', challengeToken: 'private-browser-challenge', expiresAt: new Date(Date.now() + 180000).toISOString() } } })
  })
  await page.route(url => url.pathname === '/api/auth/login-challenges/verify', async route => {
    verifications++; expect(route.request().postDataJSON()).toEqual({ challengeToken: 'private-browser-challenge', code: '012345' })
    await mfa.wait
    await route.fulfill({ json: { code: 0, data: { status: 'AUTHENTICATED', token: 'browser-mfa-session', rememberAllowed: false } } })
  })
  await fillLogin(page); await repeatSubmit(page); await expect.poll(() => logins).toBe(1)
  login.release(); await expect(page).toHaveURL(/\/login\/totp/)
  await page.getByLabel('动态验证码', { exact: true }).fill('012345'); await repeatSubmit(page)
  await expect.poll(() => verifications).toBe(1)
  mfa.release(); await expect(page).toHaveURL(/\/iam\/users$/)
  const saved = await page.evaluate(() => ({ local: localStorage.getItem('sma.access-token'), session: sessionStorage.getItem('sma.access-token'),
    records: JSON.stringify([Object.entries(localStorage), Object.entries(sessionStorage)]) }))
  expect(saved.local).toBeNull(); expect(saved.session).toBe('browser-mfa-session')
  expect(saved.records).not.toMatch(/private-browser-challenge|012345|SyntheticPassword/)
  expect(logins).toBe(1); expect(verifications).toBe(1)
})
test('unknown OTP outcome clears its challenge and code instead of enabling a replay', async ({ page }) => {
  await mockApi(page, origin); let verifications = 0
  await page.route(url => url.pathname === '/api/auth/login', route => route.fulfill({ json: { code: 0, data: {
    status: 'MFA_REQUIRED', challengeToken: 'uncertain-challenge', expiresAt: new Date(Date.now() + 180000).toISOString(),
  } } }))
  await page.route(url => url.pathname === '/api/auth/login-challenges/verify', route => { verifications++; return route.abort('failed') })
  await fillLogin(page); await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/login\/totp/)
  await page.getByLabel('动态验证码', { exact: true }).fill('012345')
  await page.getByRole('button', { name: '验证并登录', exact: true }).click()
  await expect(page.getByLabel('动态验证码', { exact: true })).toHaveValue('')
  await expect(page.getByRole('button', { name: '验证并登录', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: '返回登录', exact: true })).toBeEnabled()
  expect(verifications).toBe(1)
  await page.getByRole('button', { name: '返回登录', exact: true }).click()
  await expect(page).toHaveURL(/\/login$/)
  expect(await page.evaluate(() => sessionStorage.getItem('sma.access-token'))).toBeNull()
})
test('leaving the login page discards a late token and cannot navigate the replacement page', async ({ page }) => {
  await mockApi(page, origin)
  const pending = barrier(); let logins = 0, permissionReads = 0
  await page.route(url => url.pathname === '/api/auth/login', async route => { logins++; await pending.wait; await route.fulfill({ json: { code: 0, data: { status: 'AUTHENTICATED', token: 'obsolete-browser-token' } } }) })
  await page.route(url => url.pathname === '/api/auth/me', route => { permissionReads++; return route.fallback() })
  await fillLogin(page); await page.getByRole('button', { name: '登录', exact: true }).click(); await expect.poll(() => logins).toBe(1)
  await leaveViaRouter(page)
  const response = page.waitForResponse(url => url.url().endsWith('/api/auth/login'))
  pending.release(); await (await response).finished(); await page.waitForLoadState('networkidle')
  await expect.poll(() => page.evaluate(() => sessionStorage.getItem('sma.access-token'))).toBeNull()
  await expect(page).toHaveURL(/\/brand-preview$/); expect(permissionReads).toBe(0)
})
test('leaving during permission loading invalidates the accepted token and late navigation', async ({ page }) => {
  await mockApi(page, origin)
  const pending = barrier(); let permissionReads = 0
  await page.route(url => url.pathname === '/api/auth/me', async route => { permissionReads++; await pending.wait; await route.fallback() })
  await fillLogin(page); await page.getByRole('button', { name: '登录', exact: true }).click(); await expect.poll(() => permissionReads).toBe(1)
  await leaveViaRouter(page)
  const response = page.waitForResponse(url => url.url().endsWith('/api/auth/me'))
  pending.release(); await (await response).finished(); await page.waitForLoadState('networkidle')
  await expect.poll(() => page.evaluate(() => sessionStorage.getItem('sma.access-token'))).toBeNull()
  await expect(page).toHaveURL(/\/brand-preview$/)
})
