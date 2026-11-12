import { expect, test, type Page } from '@playwright/test'
import { fullApi } from '../e2e/full-api'
import { login } from '../e2e/mock-api'
const origin = 'http://127.0.0.1:5196'
test.use({ locale: 'zh-CN' })
async function account(page: Page) { await page.getByRole('button', { name: '账号', exact: true }).click() }
const exit = (page: Page) => page.getByRole('menuitem', { name: '退出登录', exact: true }).click()
test('two tabs retire the same session with one network request and cannot restore its old token after an unknown result', async ({ page, context }) => {
  const peer = await context.newPage(); let writes = 0, reads = 0, release!: () => void
  const pending = new Promise<void>(resolve => { release = resolve })
  for (const tab of [page, peer]) {
    await fullApi(tab, origin)
    await tab.route(url => url.pathname === '/api/auth/logout', async route => {
      writes++; await pending; await route.abort('failed')
    })
    await tab.route(url => url.pathname === '/api/auth/me', route => { reads++; return route.fallback() })
    await login(tab, '/iam/users')
  }
  await account(page); await exit(page); await expect.poll(() => writes).toBe(1)
  // A matching peer retires automatically before the sender receives any network acknowledgement.
  await expect(peer).toHaveURL(/\/login(?:\?|$)/)
  release(); await expect(page).toHaveURL(/\/login$/)
  const raw = await page.evaluate(() => Object.entries(localStorage).find(([key]) => key.startsWith('sma.session-exit.v1.'))?.[1])
  expect(JSON.parse(raw!).state).toBe('LOCAL_EXIT'); expect(Object.keys(JSON.parse(raw!)).sort()).toEqual(['key', 'schema', 'scope', 'state'])
  expect(raw).not.toContain('isolated-full-e2e-token')
  const previousReads = reads
  await page.evaluate(() => sessionStorage.setItem('sma.access-token', 'isolated-full-e2e-token'))
  await page.goto('/iam/users'); await expect(page).toHaveURL(/\/login\?redirect=/)
  expect(writes).toBe(1); expect(reads).toBe(previousReads)
})

test('retiring a private session leaves a different remembered identity usable in its other tab', async ({ page, context }) => {
  const peer = await context.newPage(); await fullApi(page, origin); await fullApi(peer, origin)
  await login(page, '/iam/users')
  await peer.route(url => url.pathname === '/api/auth/login', route => route.fulfill({ json: { code: 0, data: { token: 'different-peer-session' } } }))
  await peer.goto('/login?redirect=/iam/users')
  await peer.getByLabel('用户名', { exact: true }).fill('e2e.admin'); await peer.getByLabel('密码', { exact: true }).fill('Synthetic!Password')
  await peer.getByRole('checkbox', { name: '保持登录', exact: true }).click(); await peer.getByRole('button', { name: '登录', exact: true }).click()
  await expect(peer).toHaveURL(/\/iam\/users$/)
  await account(page); await exit(page); await expect(page).toHaveURL(/\/login$/)
  await expect(peer.getByRole('button', { name: '账号', exact: true })).toBeVisible()
  expect(await peer.evaluate(() => localStorage.getItem('sma.access-token'))).toBe('different-peer-session')
  await peer.reload(); await expect(peer).toHaveURL(/\/iam\/users$/)
})

test('missing browser coordination keeps local exit available without an uncoordinated logout request', async ({ page }) => {
  await fullApi(page, origin); await login(page, '/iam/users'); let writes = 0
  await page.route(url => url.pathname === '/api/auth/logout', route => { writes++; return route.fallback() })
  await page.evaluate(() => Object.defineProperty(navigator, 'locks', { configurable: true, value: undefined }))
  await account(page); await exit(page); await expect(page).toHaveURL(/\/login$/)
  expect(writes).toBe(0); expect(await page.evaluate(() => sessionStorage.getItem('sma.access-token'))).toBeNull()
})
