import { expect, test, type Page } from '@playwright/test'
import { fullApi } from '../e2e/full-api'
import { login } from '../e2e/mock-api'
const origin = 'http://127.0.0.1:5196'
test.use({ locale: 'zh-CN' })
const screen = (page: Page) => page.getByRole('dialog', { name: '已锁屏', exact: true })
async function lock(page: Page) { await page.getByRole('button', { name: '账号', exact: true }).click(); await page.getByRole('menuitem', { name: '锁屏', exact: true }).click() }
async function unlock(page: Page, password = 'Correct!Historical') { await screen(page).getByLabel('密码', { exact: true }).fill(password); await screen(page).getByRole('button', { name: '解锁', exact: true }).click() }
async function fixtures(pages: Page[]) {
  const state = { locked: false, locks: 0, unlocks: 0, unknownUnlock: false, delayedPeerRead: false, readStarted: false, finishRead: () => {} }
  let release!: () => void; const pending = new Promise<void>(resolve => { release = resolve }); state.finishRead = release
  for (const [index, page] of pages.entries()) {
    const api = await fullApi(page, origin)
    await page.route(url => url.pathname === '/api/auth/me', async route => {
      const observed = state.locked
      if (index === 1 && state.delayedPeerRead && !observed) { state.readStarted = true; await pending }
      await route.fulfill({ json: { code: 0, data: { ...api.me, ...api.preferences, locked: observed } } })
    })
    await page.route(url => url.pathname === '/api/auth/lock', route => { state.locks++; state.locked = true; return route.fulfill({ json: { code: 0, data: null } }) })
    await page.route(url => url.pathname === '/api/auth/unlock', route => {
      state.unlocks++
      if (route.request().postDataJSON().password === 'Wrong') return route.fulfill({ json: { code: 400, message: '错误密码', data: null } })
      state.locked = false
      return state.unknownUnlock ? route.abort('failed') : route.fulfill({ json: { code: 0, data: null } })
    })
    await login(page, '/iam/users')
  }
  return state
}

test('locking shields both tabs; a wrong password preserves both shields and a confirmed unlock restores them', async ({ page, context }) => {
  const peer = await context.newPage(), state = await fixtures([page, peer])
  await lock(page); await expect(screen(page)).toBeVisible(); await expect(screen(peer)).toBeVisible()
  await expect(screen(peer).getByLabel('密码', { exact: true })).toBeEnabled()
  await unlock(peer, 'Wrong'); await expect(screen(peer).getByRole('alert')).toContainText('错误密码')
  await expect(screen(page)).toBeVisible(); await expect(screen(peer)).toBeVisible()
  await unlock(peer); await expect(screen(peer)).toHaveCount(0); await expect(screen(page)).toHaveCount(0)
  expect(state.locks).toBe(1); expect(state.unlocks).toBe(2)
  const raw = await page.evaluate(() => Object.entries(localStorage).find(([key]) => key.startsWith('sma.session-lock-intent.v2.'))?.[1])
  expect(JSON.parse(raw!).state).toBe('UNLOCKED'); expect(raw).not.toMatch(/Correct|Wrong|isolated-full-e2e-token/)
})

test('lost unlock acknowledgement stays shielded in both tabs after reload even when the server reports unlocked', async ({ page, context }) => {
  const peer = await context.newPage(), state = await fixtures([page, peer]); await lock(page)
  await expect(screen(peer)).toBeVisible(); state.unknownUnlock = true; await unlock(peer)
  await expect(screen(peer).getByRole('alert')).toContainText('锁屏状态尚未确认')
  await expect(screen(page).getByLabel('密码', { exact: true })).toBeDisabled()
  expect(state.locked).toBe(false); await peer.close(); await page.reload()
  await expect(screen(page)).toBeVisible(); await expect(screen(page).getByLabel('密码', { exact: true })).toBeDisabled()
  expect(state.unlocks).toBe(1)
  await screen(page).getByRole('button', { name: '退出登录', exact: true }).click(); await expect(page).toHaveURL(/\/login$/)
})

test('a delayed peer unlocked read cannot remove the shield from a newer lock', async ({ page, context }) => {
  const peer = await context.newPage(), state = await fixtures([page, peer]); await lock(page); await expect(screen(peer)).toBeVisible()
  state.delayedPeerRead = true; await unlock(page); await expect(screen(page)).toHaveCount(0)
  await expect.poll(() => state.readStarted).toBe(true)
  await lock(page); await expect.poll(() => state.locks).toBe(2)
  state.finishRead(); await expect(screen(peer)).toBeVisible()
  await expect.poll(async () => JSON.parse((await peer.evaluate(() => Object.entries(localStorage).find(([key]) => key.startsWith('sma.session-lock-intent.v2.'))?.[1]))!).state).toBe('LOCKED')
  await expect(screen(peer).getByLabel('密码', { exact: true })).toBeEnabled()
})

test('missing coordination capability exits locally without sending an unrecorded lock', async ({ page }) => {
  const state = await fixtures([page])
  await page.evaluate(() => Object.defineProperty(navigator, 'locks', { configurable: true, value: undefined }))
  await lock(page)
  await expect(page).toHaveURL(/\/login$/); expect(state.locks).toBe(0)
  expect(await page.evaluate(() => sessionStorage.getItem('sma.access-token'))).toBeNull()
})
