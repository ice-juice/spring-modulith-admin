import { expect, test, type Request } from '@playwright/test'

test('the browser API client sends operation keys and preserves an explicit retry key', async ({ page }) => {
  const writes: Request[] = []
  page.on('request', request => { if (request.method() === 'POST' && request.url().endsWith('/config/parameters/groups')) writes.push(request) })
  await page.route(url => url.pathname.startsWith('/api/'), route => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 0, data: { 'login.captchaEnabled': 'false', id: '1' } }) }))
  await page.goto('/login')
  await page.evaluate(async () => {
    const modulePath = '/src/api/http.ts'
    const api = await import(modulePath)
    await api.post('/config/parameters/groups', { groupKey: 'browser-probe', name: 'Probe', sort: 0 })
    await api.http.post('/config/parameters/groups', { groupKey: 'browser-probe', name: 'Probe', sort: 0 }, { headers: { 'Idempotency-Key': 'explicit-browser-retry-key' } })
  })
  expect(writes).toHaveLength(2)
  expect(writes[0]?.headers()['idempotency-key']).toMatch(/^[0-9a-f-]{36}$/)
  expect(writes[1]?.headers()['idempotency-key']).toBe('explicit-browser-retry-key')
})
