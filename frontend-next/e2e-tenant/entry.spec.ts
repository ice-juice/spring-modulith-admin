import { expect, test } from '@playwright/test'
import { tenantEntry } from '../e2e/tenant-entry'

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => { localStorage.clear(); sessionStorage.clear(); localStorage.setItem('sma.locale', 'zh-CN') })
})

test('login uses the bound portal and makes no old public request', async ({ page }) => {
  const logins: Record<string, unknown>[] = []
  let oldRequests = 0, captchaRequests = 0
  await page.route(url => url.pathname.startsWith('/api/'), route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/tenant/entry') return route.fulfill({ json: { code: 0, data: tenantEntry('Tenant portal', false, 'partner') } })
    if (path === '/api/auth/login') { logins.push(route.request().postDataJSON()); return route.fulfill({ status: 400, json: { message: '登录验证失败' } }) }
    if (path === '/api/config/parameters/public') oldRequests++
    if (path === '/api/auth/captcha') captchaRequests++
    return route.fulfill({ status: 500 })
  })
  await page.goto('/login?portal=admin')
  await expect(page.locator('button[type="submit"]')).toBeEnabled()
  await page.locator('input[name="username"]').fill('test.operator')
  await page.locator('input[name="password"]').fill('Password1')
  await page.locator('button[type="submit"]').click()
  await expect.poll(() => logins.length).toBe(1)
  expect(logins[0]?.portal).toBe('partner')
  expect(captchaRequests).toBe(0)
  expect(oldRequests).toBe(0)
})

test('unavailable entry blocks forced submit and captcha, and explicit refresh recovers', async ({ page }) => {
  let available = false, logins = 0, captchas = 0
  await page.route(url => url.pathname.startsWith('/api/'), route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/tenant/entry') return available
      ? route.fulfill({ json: { code: 0, data: tenantEntry('Recovered tenant', true) } })
      : route.fulfill({ status: 404, json: { message: 'Not found' } })
    if (path === '/api/auth/captcha') { captchas++; return route.fulfill({ json: { code: 0, data: { id: 'captcha-1', image: 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7' } } }) }
    if (path === '/api/auth/login') logins++
    return route.fulfill({ status: 400, json: { message: 'Rejected' } })
  })
  await page.goto('/login')
  await expect(page.getByText('当前入口不可用，请检查访问地址或联系管理员。', { exact: true })).toBeVisible()
  await expect(page.locator('button[type="submit"]')).toBeDisabled()
  await expect(page.locator('input[name="username"]')).toBeDisabled()
  await page.locator('form').evaluate(form => form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })))
  expect(logins).toBe(0); expect(captchas).toBe(0)
  available = true
  await page.getByRole('button', { name: '刷新', exact: true }).click()
  await expect(page.locator('button[type="submit"]')).toBeEnabled()
  await expect.poll(() => captchas).toBe(1)
  await expect(page).toHaveTitle('Recovered tenant')
  expect(logins).toBe(0)
})

test('old map responses cannot silently enable fallback login', async ({ page }) => {
  let authCalls = 0
  await page.route(url => url.pathname.startsWith('/api/'), route => {
    if (new URL(route.request().url()).pathname === '/api/tenant/entry') return route.fulfill({ json: { code: 0, data: { 'system.name': 'Old map', 'login.captchaEnabled': 'false' } } })
    authCalls++; return route.fulfill({ status: 500 })
  })
  await page.goto('/login')
  await expect(page.locator('button[type="submit"]')).toBeDisabled()
  await expect(page.getByText('当前入口不可用，请检查访问地址或联系管理员。', { exact: true })).toBeVisible()
  expect(authCalls).toBe(0)
})

test('development HTTP and WebSocket proxies preserve external host and origin', async ({ page, request, baseURL }) => {
  const origin = baseURL!, host = new URL(origin).host
  const response = await request.get('/api/p10-proxy-probe', { headers: { Origin: origin } })
  expect(await response.json()).toEqual({ host, origin })
  await page.route('**/api/tenant/entry', route => route.fulfill({ json: { code: 0, data: tenantEntry() } }))
  await page.goto('/login')
  const observed = await page.evaluate(() => new Promise<{ host: string; origin: string }>((resolve, reject) => {
    const socket = new WebSocket('ws://' + location.host + '/api/p10-proxy-probe')
    const timer = setTimeout(() => { socket.close(); reject(new Error('WebSocket probe timed out')) }, 5000)
    socket.onmessage = event => { clearTimeout(timer); resolve(JSON.parse(event.data)); socket.close() }
    socket.onerror = () => { clearTimeout(timer); reject(new Error('WebSocket probe failed')) }
  }))
  expect(observed).toEqual({ host, origin })
})
