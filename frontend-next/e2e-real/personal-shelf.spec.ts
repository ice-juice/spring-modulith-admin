import { expect, test, type Page } from '@playwright/test'
import type { PersonalNote } from '../src/features/workspace/notes'
async function signIn(page: Page, username = 'admin', password = process.env.SMA_E2E_ADMIN_PASSWORD!) {
  await page.goto('/login')
  await page.getByRole('textbox', { name: '用户名', exact: true }).fill(username)
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL(url => url.pathname !== '/login')
}
test('real shelf persists across browser contexts, protects ownership and rejects stale versions', async ({ page, browser, baseURL }) => {
  test.setTimeout(120000)
  await signIn(page)
  await page.locator('.page-heading').getByRole('button', { name: '固定到我的标签', exact: true }).click()
  await page.getByRole('button', { name: '我的随手栏', exact: true }).click()
  const drawer = page.getByRole('dialog', { name: '我的随手栏', exact: true })
  await expect(drawer.locator('[data-shelf-bookmark]')).toHaveCount(1)
  await drawer.getByRole('button', { name: '个人便签', exact: true }).click()
  await drawer.getByRole('button', { name: '新建清单', exact: true }).click()
  await drawer.getByLabel('便签标题', { exact: true }).fill('真实持久化便签')
  await drawer.getByLabel('便签内容', { exact: true }).fill('仅属于当前用户的测试内容')
  await drawer.getByRole('button', { name: '添加清单项', exact: true }).click()
  await drawer.getByLabel('第 1 项内容', { exact: true }).fill('核对数据库保存')
  await drawer.getByLabel('完成第 1 项', { exact: true }).check()
  await expect(drawer.getByRole('status')).toHaveText('已保存')
  const token = await page.evaluate(() => sessionStorage.getItem('sma.access-token') || localStorage.getItem('sma.access-token'))
  const headers = { Authorization: 'Bearer ' + token, Origin: baseURL! }
  const list = async () => (await (await page.request.get('/api/workspace/shelf/notes', { headers })).json()).data as PersonalNote[]
  const record = (await list()).find(n => n.title === '真实持久化便签')!
  expect(record.items[0]!.done).toBe(true); expect(typeof record.id).toBe('string'); expect(typeof record.version).toBe('string')
  const newer = await page.request.put('/api/workspace/shelf/notes/' + record.id, { headers, data: { version: record.version, note: { ...record, content: '服务端新版本' } } })
  expect((await newer.json()).code).toBe(0)
  const stale = await page.request.put('/api/workspace/shelf/notes/' + record.id, { headers, data: { version: record.version, note: { ...record, content: '不能覆盖' } } })
  expect((await stale.json()).code).toBe(409)
  const otherPassword = 'S4a831b976'
  const created = await page.request.post('/api/iam/users', { headers, data: { user: { username: 'shelf.reader', name: 'Shelf reader', email: null, mobile: null, departmentId: null, gender: 'UNSPECIFIED', enabled: true, roleIds: [], postIds: [], locale: 'zh-CN', timeZone: 'UTC' }, password: otherPassword } })
  expect((await created.json()).code).toBe(0)
  const otherContext = await browser.newContext({ baseURL, locale: 'zh-CN' })
  try {
    const other = await otherContext.newPage(); await signIn(other, 'shelf.reader', otherPassword)
    const otherToken = await other.evaluate(() => sessionStorage.getItem('sma.access-token') || localStorage.getItem('sma.access-token'))
    const otherHeaders = { Authorization: 'Bearer ' + otherToken, Origin: baseURL! }
    const own = await other.request.get('/api/workspace/shelf/notes', { headers: otherHeaders })
    expect((await own.json()).data).toEqual([])
    const denied = await other.request.get('/api/workspace/shelf/notes/' + record.id, { headers: otherHeaders })
    expect((await denied.json()).code).not.toBe(0)
    const deniedWrite = await other.request.put('/api/workspace/shelf/notes/' + record.id, { headers: otherHeaders, data: { version: record.version, note: record } })
    expect((await deniedWrite.json()).code).not.toBe(0)
  } finally { await otherContext.close() }
  const freshContext = await browser.newContext({ baseURL, locale: 'zh-CN' })
  try {
    const fresh = await freshContext.newPage(); await signIn(fresh)
    await fresh.getByRole('button', { name: '我的随手栏', exact: true }).click()
    const freshDrawer = fresh.getByRole('dialog', { name: '我的随手栏', exact: true })
    await expect(freshDrawer.locator('[data-shelf-bookmark]')).toHaveCount(1)
    await freshDrawer.getByRole('button', { name: '个人便签', exact: true }).click()
    await freshDrawer.locator('[data-note-row]').filter({ hasText: '真实持久化便签' }).click()
    await expect(freshDrawer.getByLabel('便签内容', { exact: true })).toHaveValue('服务端新版本')
    await expect(freshDrawer.getByLabel('完成第 1 项', { exact: true })).toBeChecked()
    await freshDrawer.getByRole('button', { name: '悬浮显示', exact: true }).click()
    await expect(fresh.getByRole('region', { name: '真实持久化便签', exact: true })).toBeVisible()
    await fresh.reload()
    await expect(fresh.getByRole('region', { name: '真实持久化便签', exact: true })).toBeVisible()
  } finally { await freshContext.close() }
})
