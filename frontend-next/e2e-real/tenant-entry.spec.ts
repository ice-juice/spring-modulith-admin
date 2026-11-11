import { expect, test, type APIResponse } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import type { TenantEntryDto } from '../src/lib/tenant-branding'

async function data<T>(response: APIResponse): Promise<T> {
  expect(response.status()).toBe(200)
  const body = await response.json() as { code: number; data: T }
  expect(body.code).toBe(0)
  return body.data
}

test('real published branding is consumed by both frontends and the old public route is absent', async ({ page, browser, baseURL }) => {
  test.setTimeout(120000)
  const password = process.env.SMA_E2E_ADMIN_PASSWORD
  if (!password || process.env.SMA_E2E_LEGACY !== '1') throw new Error('Use the isolated E2E runner with both frontend servers.')
  const oldCalls: string[] = []
  page.on('request', request => { if (new URL(request.url()).pathname === '/api/config/parameters/public') oldCalls.push(request.url()) })
  const initial = await data<TenantEntryDto>(await page.request.get('/api/tenant/entry'))
  expect(initial.entry.portal).toBe('admin')
  expect(initial.login.captchaEnabled).toBe(false)
  expect((await page.request.get(process.env.SMA_NEXT_BACKEND_URL + '/api/tenant/entry', { headers: { Host: 'unregistered.example.test' } })).status()).toBe(404)
  expect((await page.request.get('/api/config/parameters/public')).status()).toBe(401)
  await page.goto('/login?portal=partner')
  await expect(page.locator('button[type="submit"]')).toBeEnabled()
  await page.locator('input[name="username"]').fill('admin')
  await page.locator('input[name="password"]').fill(password)
  await page.locator('button[type="submit"]').click()
  await page.waitForURL(url => url.pathname !== '/login')
  const token = await page.evaluate(() => sessionStorage.getItem('sma.access-token') || localStorage.getItem('sma.access-token'))
  expect(token).toBeTruthy()
  const headers = { Authorization: 'Bearer ' + token, Origin: baseURL! }
  expect((await page.request.get('/api/config/parameters/public', { headers })).status()).toBe(405)
  const png = await page.evaluate(() => {
    const canvas = document.createElement('canvas'); canvas.width = 24; canvas.height = 16
    const context = canvas.getContext('2d')!; context.fillStyle = '#123456'; context.fillRect(0, 0, 24, 16)
    return canvas.toDataURL('image/png').split(',')[1]!
  })
  const file = await data<{ id: string }>(await page.request.post('/api/file/files/images', {
    headers: { ...headers, 'Idempotency-Key': randomUUID() }, multipart: { file: { name: 'tenant-background.png', mimeType: 'image/png', buffer: Buffer.from(png, 'base64') } },
  }))
  const draft = await data<{ content: Record<string, unknown>; version: string; publishedRevisionId: string | null }>(await page.request.get('/api/tenant/brands/draft', { headers }))
  const content = {
    ...draft.content,
    identity: { systemName: 'P10 实际租户品牌', shortName: 'P10', englishName: 'Tenant branding' },
    legal: { copyrightOwner: 'P10 测试主体', copyrightStartYear: 2026, copyrightUrl: '', icpRecord: 'P10 测试备案', icpUrl: '', policeRecord: '', policeUrl: '' },
    assets: { LOGIN_BACKGROUND: file.id },
  }
  const saved = await data<{ version: string }>(await page.request.put('/api/tenant/brands/draft', { headers: { ...headers, 'Idempotency-Key': randomUUID() }, data: { expectedVersion: draft.version, content } }))
  expect((await data<TenantEntryDto>(await page.request.get('/api/tenant/entry'))).branding.identity.systemName).toBe(initial.branding.identity.systemName)
  await data(await page.request.post('/api/tenant/brands/publications', {
    headers, data: { operationId: randomUUID(), expectedDraftVersion: saved.version, expectedPublishedRevisionId: draft.publishedRevisionId },
  }))
  const published = await data<TenantEntryDto>(await page.request.get('/api/tenant/entry'))
  expect(published.branding.identity.systemName).toBe('P10 实际租户品牌')
  expect((await page.request.get('/api/file/files/' + file.id + '/content')).status()).toBe(401)
  const origins = [baseURL!, 'http://127.0.0.1:' + (process.env.SMA_E2E_LEGACY_PORT || '5188')]
  for (const origin of origins) {
    const context = await browser.newContext({ locale: 'zh-CN' })
    const visitor = await context.newPage()
    visitor.on('request', request => { if (new URL(request.url()).pathname === '/api/config/parameters/public') oldCalls.push(request.url()) })
    const image = visitor.waitForResponse(response => new URL(response.url()).pathname === published.branding.assets.LOGIN_BACKGROUND)
    await visitor.goto(origin + '/login?portal=partner')
    await expect(visitor).toHaveTitle('P10 实际租户品牌')
    await expect(visitor.locator('.branding-footer')).toContainText('P10 测试主体')
    await expect(visitor.locator('.branding-footer')).toContainText('2026')
    await expect(visitor.getByText('P10 测试备案', { exact: true })).toBeVisible()
    expect((await image).status()).toBe(200)
    await expect(visitor.locator('[style*="brand-images"]').first()).toBeVisible()
    await visitor.locator('input[name="username"]').fill('admin')
    await visitor.locator('input[name="password"]').fill(password)
    await visitor.locator('button[type="submit"]').click()
    await visitor.waitForURL(url => url.pathname !== '/login')
    await context.close()
  }
  expect(oldCalls).toEqual([])
})
