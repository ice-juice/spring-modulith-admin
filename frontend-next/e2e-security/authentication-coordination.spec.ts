import { expect, test, type Page } from '@playwright/test'
import { mockApi } from '../e2e/mock-api'
const origin = 'http://127.0.0.1:5196'
test.use({ locale: 'zh-CN' })
async function prepare(page: Page) {
  await mockApi(page, origin); await page.goto('/login?redirect=/iam/users')
  await page.getByLabel('用户名', { exact: true }).fill('e2e.admin'); await page.getByLabel('密码', { exact: true }).fill('Synthetic!Password2026')
}
function barrier() { let release!: () => void; const wait = new Promise<void>(resolve => { release = resolve }); return { wait, release: () => release() } }
test('two tabs do not concurrently authenticate or queue a blocked password request', async ({ page, context }) => {
  const peer = await context.newPage(); await prepare(page); await prepare(peer)
  const held = barrier(); let firstWrites = 0, peerWrites = 0
  await page.route(url => url.pathname === '/api/auth/login', async route => { firstWrites++; await held.wait; await route.fulfill({ json: { code: 0, data: { status: 'MFA_REQUIRED', challengeToken: 'first-private-challenge', expiresAt: new Date(Date.now() + 180000).toISOString() } } }) })
  await peer.route(url => url.pathname === '/api/auth/login', route => { peerWrites++; return route.fallback() })
  await page.getByRole('button', { name: '登录', exact: true }).click(); await expect.poll(() => firstWrites).toBe(1)
  await peer.getByRole('button', { name: '登录', exact: true }).click()
  await expect(peer.getByRole('alert')).toContainText('认证请求正在处理'); expect(peerWrites).toBe(0)
  held.release(); await expect(page).toHaveURL(/\/login\/totp/); expect(peerWrites).toBe(0)
  await peer.getByLabel('密码', { exact: true }).fill('Synthetic!Password2026'); await peer.getByRole('button', { name: '登录', exact: true }).click()
  await expect(peer).toHaveURL(/\/iam\/users$/); expect(peerWrites).toBe(1)
  // Waiting for OTP holds no global lock; a separate private login does not consume this challenge.
  await expect(page.getByLabel('动态验证码', { exact: true })).toBeEnabled()
})
test('a remembered login in another tab invalidates the older pending MFA without clearing the new shared session', async ({ page, context }) => {
  const peer = await context.newPage(); await prepare(page); await prepare(peer); let verifications = 0
  await page.route(url => url.pathname === '/api/auth/login', route => route.fulfill({ json: { code: 0, data: { status: 'MFA_REQUIRED', challengeToken: 'old-private-challenge', expiresAt: new Date(Date.now() + 180000).toISOString() } } }))
  await page.route(url => url.pathname === '/api/auth/login-challenges/verify', route => { verifications++; return route.abort('failed') })
  await page.getByRole('button', { name: '登录', exact: true }).click(); await expect(page).toHaveURL(/\/login\/totp/)
  await page.getByLabel('动态验证码', { exact: true }).fill('012345')
  await peer.getByRole('checkbox', { name: '保持登录', exact: true }).click(); await peer.getByRole('button', { name: '登录', exact: true }).click()
  await expect(peer).toHaveURL(/\/iam\/users$/)
  await expect(page.getByLabel('动态验证码', { exact: true })).toHaveValue(''); await expect(page.getByRole('button', { name: '验证并登录', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '返回登录', exact: true }).click(); await expect(page).toHaveURL(/\/login$/)
  expect(await peer.evaluate(() => localStorage.getItem('sma.access-token'))).toBe('isolated-e2e-token'); expect(verifications).toBe(0)
  expect(await page.evaluate(() => JSON.stringify([Object.entries(localStorage), Object.entries(sessionStorage)]))).not.toMatch(/old-private-challenge|012345|Synthetic/)
})
test('unsupported coordination rejects login before dispatch and explains the browser requirement', async ({ page }) => {
  await prepare(page); let writes = 0
  await page.route(url => url.pathname === '/api/auth/login', route => { writes++; return route.fallback() })
  await page.evaluate(() => Object.defineProperty(navigator, 'locks', { value: undefined, configurable: true }))
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('当前浏览器无法安全协调多标签操作'); expect(writes).toBe(0)
  await expect(page.getByLabel('密码', { exact: true })).toHaveValue('')
})
