import { expect, test, type Page } from '@playwright/test'
import { mockApi } from '../e2e/mock-api'
const origin = 'http://127.0.0.1:5197'
async function setup(page: Page) { await mockApi(page, origin); await page.goto('/login'); await expect(page.locator('#app')).not.toBeEmpty() }

test('independent legacy app supplies write keys, preserves explicit native keys and long IDs, and keeps GET read-only', async ({ page }) => {
  await setup(page)
  const seen: { method: string; key: string | undefined; body: unknown }[] = []
  await page.route(url => url.pathname === '/api/config/parameters/groups', route => {
    const request = route.request()
    seen.push({ method: request.method(), key: request.headers()['idempotency-key'], body: request.postData() ? request.postDataJSON() : null })
    return route.fulfill({ json: { code: 0, data: { id: '9007199254740993123' } } })
  })
  const ids = await page.evaluate(async () => {
    // This module is served by frontend/'s own Vite process, not frontend-next.
    const path = '/src/api/http.ts', { http, post } = await import(path)
    const first = await post('/config/parameters/groups', { id: '9007199254740993123', name: 'Legacy probe' })
    const second = await http.put('/config/parameters/groups', { id: '9007199254740993123', operationId: 'legacy-native-original', expectedVersion: '9007199254740993124' }, { headers: { 'idempotency-key': 'legacy-explicit-original' } })
    await http.patch('/config/parameters/groups', {}, { headers: { 'Idempotency-Key': 'legacy-patch-original' } })
    await http.delete('/config/parameters/groups', { headers: { 'Idempotency-Key': 'legacy-delete-original' } })
    await http.get('/config/parameters/groups')
    return [first.id, second.data.data.id]
  })
  expect(ids).toEqual(['9007199254740993123', '9007199254740993123'])
  expect(seen).toHaveLength(5)
  expect(seen[0]?.key).toMatch(/^[a-f0-9-]{36}$/)
  expect(seen[1]).toEqual({ method: 'PUT', key: 'legacy-explicit-original', body: { id: '9007199254740993123', operationId: 'legacy-native-original', expectedVersion: '9007199254740993124' } })
  expect(seen[2]?.key).toBe('legacy-patch-original'); expect(seen[3]?.key).toBe('legacy-delete-original')
  expect(seen[4]).toEqual({ method: 'GET', key: undefined, body: null })
  expect(await page.locator('body').innerHTML()).not.toContain('data-operation-panel')
})

test('legacy governance failures expose their original receipt and never automatically retry', async ({ page }) => {
  await setup(page)
  const cases = [
    { status: 202, key: 'common.request.processing', state: 'RUNNING' },
    { status: 202, key: 'common.request.unknown', state: 'UNKNOWN' },
    { status: 409, key: 'common.request.completed', state: 'SUCCEEDED' },
    { status: 409, key: 'common.request.failed', state: 'FAILED_FINAL' },
    { status: 429, key: 'common.request.rateLimited', state: 'UNKNOWN' },
    { status: 503, key: 'common.request.unknown', state: 'UNKNOWN' },
    { status: 200, key: 'test.businessRejected', state: 'UNKNOWN' },
  ]
  let writes = 0
  await page.route(url => url.pathname === '/api/config/parameters/groups', route => {
    const index = route.request().postDataJSON().index, item = cases[index]!
    expect(route.request().headers()['idempotency-key']).toBe('legacy-original-' + index)
    writes++
    return route.fulfill({ status: item.status, headers: { 'Retry-After': '10', 'X-Request-Policy': 'LOCAL' },
      json: { code: 999, error: item.key, message: 'Fixture response', data: { operationId: 'original-' + index, state: item.state } } })
  })
  const errors = await page.evaluate(async count => {
    const path = '/src/api/http.ts', { http } = await import(path), results = []
    for (let index = 0; index < count; index++) {
      try { await http.post('/config/parameters/groups', { index }, { headers: { 'Idempotency-Key': 'legacy-original-' + index } }); results.push({ unexpectedSuccess: true }) }
      catch (cause) { const error = cause as { key: string; details: unknown }; results.push({ key: error.key, details: error.details }) }
    }
    return results
  }, cases.length)
  expect(errors).toEqual(cases.map((item, index) => ({ key: item.key, details: { operationId: 'original-' + index, state: item.state } })))
  expect(writes).toBe(cases.length)
})

test('legacy network ambiguity does not replay a mutation and explicit original-key lookup sends no reconstructed body', async ({ page }) => {
  await setup(page)
  let writes = 0, reads = 0
  await page.route(url => url.pathname === '/api/config/parameters/groups', route => { writes++; return route.abort('failed') })
  await page.route(url => url.pathname === '/api/common/requests/lookup', route => {
    reads++; expect(route.request().postDataJSON()).toEqual({ key: 'legacy-lost-original' })
    return route.fulfill({ json: { code: 0, data: { state: 'UNKNOWN', operationId: 'old-receipt' } } })
  })
  const result = await page.evaluate(async () => {
    const path = '/src/api/http.ts', { http, post } = await import(path)
    let code = -1
    try { await http.post('/config/parameters/groups', { name: 'private form value' }, { headers: { 'Idempotency-Key': 'legacy-lost-original' } }) }
    catch (cause) { code = (cause as { code: number }).code }
    const receipt = await post('/common/requests/lookup', { key: 'legacy-lost-original' })
    return { code, receipt, stored: Object.values(localStorage).concat(Object.values(sessionStorage)).join(' ') }
  })
  expect(result.code).toBe(0); expect(result.receipt).toEqual({ state: 'UNKNOWN', operationId: 'old-receipt' })
  expect(result.stored).not.toContain('private form value')
  expect(writes).toBe(1); expect(reads).toBe(1)
})
