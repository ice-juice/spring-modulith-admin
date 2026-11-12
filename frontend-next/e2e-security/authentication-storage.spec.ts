import { expect, test, type Page } from '@playwright/test'
import { mockApi } from '../e2e/mock-api'
const origin = 'http://127.0.0.1:5196'
async function prepare(page: Page) { await mockApi(page, origin); await page.goto('/login') }
async function token(page: Page, action: 'get' | 'save' | 'clear', value?: string, remember = false) {
  return page.evaluate(async ({ action, value, remember }) => {
    const path = '/src/lib/token.ts', tokens = await import(path)
    if (action === 'save') tokens.saveToken(value, remember)
    if (action === 'clear') tokens.clearToken(value)
    return tokens.getToken() as string | null
  }, { action, value, remember })
}
test('private-tab logout preserves the other tab remembered identity and does not silently fall back after reload', async ({ page, context }) => {
  const peer = await context.newPage(); await prepare(page); await prepare(peer)
  expect(await token(peer, 'save', 'fixture-shared-identity', true)).toBe('fixture-shared-identity')
  expect(await token(page, 'save', 'fixture-private-identity')).toBe('fixture-private-identity')
  expect(await token(page, 'clear', 'fixture-private-identity')).toBeNull()
  expect(await token(peer, 'get')).toBe('fixture-shared-identity')
  await page.reload(); expect(await token(page, 'get')).toBeNull()
  expect(await token(peer, 'save', 'fixture-later-shared', true)).toBe('fixture-later-shared')
  expect(await token(page, 'clear')).toBeNull(); expect(await token(peer, 'get')).toBe('fixture-later-shared')
})
test('a private-tab 401 clears only its original session while a remembered peer remains signed in', async ({ page, context }) => {
  const peer = await context.newPage(); await prepare(page); await prepare(peer)
  await token(peer, 'save', 'fixture-shared', true); await token(page, 'save', 'fixture-private')
  await page.route(url => url.pathname === '/api/private-session-probe', route => {
    expect(route.request().headers().authorization).toBe('Bearer fixture-private')
    return route.fulfill({ status: 401, json: { code: 401, message: 'Expired' } })
  })
  await page.evaluate(async () => { const path = '/src/api/http.ts'; const { get } = await import(path); try { await get('/private-session-probe') } catch { /* Expected unauthorized. */ } })
  expect(await token(page, 'get')).toBeNull(); expect(await token(peer, 'get')).toBe('fixture-shared')
})
