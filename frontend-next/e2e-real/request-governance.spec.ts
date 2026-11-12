import { expect, test, type Page } from '@playwright/test'
test.skip(process.env.SMA_E2E_SUITE !== 'security', 'Requires the dedicated security runner scope')

async function login(page: Page) {
  const password = process.env.SMA_E2E_ADMIN_PASSWORD
  if (!password) throw new Error('Use the isolated real E2E runner')
  await page.goto('/iam/posts')
  await page.getByRole('textbox', { name: '用户名', exact: true }).fill('admin')
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL(url => url.pathname === '/iam/posts')
}
async function createForm(page: Page, code: string, name: string) {
  await page.getByRole('button', { name: '新增', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('textbox', { name: '名称', exact: true }).fill(name)
  await dialog.getByRole('textbox', { name: '编码', exact: true }).fill(code)
  return dialog
}
async function readPosts(page: Page, keyword: string): Promise<{ id: string; code: string; name: string }[]> {
  return page.evaluate(async keyword => { const path = '/src/api/http.ts'; const { get } = await import(path); return (await get('/iam/posts', { keyword, size: 100 })).items }, keyword)
}

test('real LOCAL commit: twenty UI triggers remain one request while the actual response is delayed', async ({ page }) => {
  await login(page)
  const code = 'S1D_SLOW_' + Date.now(), name = 'S1d慢响应岗位'
  let writes = 0, key = '', committedId = '', release!: () => void
  const hold = new Promise<void>(resolve => { release = resolve })
  await page.route(url => url.pathname === '/api/iam/posts', async route => {
    if (route.request().method() !== 'POST') return route.continue()
    writes++; key = route.request().headers()['idempotency-key'] || ''
    const response = await route.fetch(), body = await response.json()
    expect(response.ok()).toBe(true); expect(body.code).toBe(0)
    committedId = body.data.id
    await hold
    await route.fulfill({ response })
  })
  const dialog = await createForm(page, code, name)
  try {
    await dialog.evaluate(element => {
      const form = element.querySelector('form')!, save = [...element.querySelectorAll('button')].find(button => button.textContent?.trim() === '保存' && !button.hidden)!
      for (let index = 0; index < 20; index++) { if (index % 2) form.requestSubmit(); else save.click() }
    })
    await expect.poll(() => committedId, { timeout: 30000 }).toMatch(/^\d+$/)
    await expect(dialog.getByRole('status')).toContainText('处理时间较长', { timeout: 6000 })
    expect(writes).toBe(1); expect(key).toMatch(/^[a-f0-9-]{36}$/)
    const rows = await readPosts(page, code)
    expect(rows).toEqual([expect.objectContaining({ id: committedId, code, name })])
  } finally { release() }
  await expect(dialog).toHaveCount(0)
  await expect(page.getByRole('cell', { name, exact: true })).toBeVisible()
  expect(writes).toBe(1)
})

test('real committed response loss survives refresh, resolves the original receipt and rejects an explicit duplicate', async ({ page }) => {
  await login(page)
  const code = 'S1D_LOST_' + Date.now(), name = 'S1d丢响应岗位'
  let writes = 0, key = '', input: unknown, committedId = ''
  const posts = (url: URL) => url.pathname === '/api/iam/posts'
  await page.route(posts, async route => {
    if (route.request().method() !== 'POST') return route.continue()
    writes++; key = route.request().headers()['idempotency-key'] || ''; input = route.request().postDataJSON()
    const response = await route.fetch(), body = await response.json()
    expect(response.ok()).toBe(true); expect(body.code).toBe(0); committedId = body.data.id
    await route.abort('failed') // Real server committed; only the browser acknowledgement is lost.
  })
  const dialog = await createForm(page, code, name)
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(dialog.getByRole('button', { name: '查询原操作结果', exact: true })).toBeVisible()
  expect(writes).toBe(1)
  await page.reload()
  await expect(page.locator('[data-operation-recovery]')).toBeVisible()
  await page.getByRole('button', { name: '操作状态', exact: true }).click()
  const panel = page.getByRole('dialog', { name: '操作状态', exact: true })
  await panel.getByRole('button', { name: '核对此操作', exact: true }).click()
  await expect(panel.getByRole('status')).toContainText('原操作已确认成功')
  expect(writes).toBe(1)
  expect(await readPosts(page, code)).toEqual([expect.objectContaining({ id: committedId, code, name })])
  await page.unroute(posts)
  // Deliberate server-side replay probe. This is not a UI retry or a reconstructed recovery action.
  const outcome = await page.evaluate(async ({ key, input }) => {
    const path = '/src/api/http.ts'; const { post } = await import(path)
    try { await post('/iam/posts', input, { idempotencyKey: key }); return 'unexpected-success' }
    catch (cause) { return (cause as { key: string }).key }
  }, { key, input })
  expect(outcome).toBe('common.request.completed')
  expect(await readPosts(page, code)).toHaveLength(1)
})

test('different real objects remain independently writable across tabs while one response is held', async ({ page, context }) => {
  await login(page)
  const suffix = Date.now().toString()
  const rows: { id: string; name: string; code: string }[] = await page.evaluate(async suffix => {
    const path = '/src/api/http.ts'; const { post } = await import(path), rows = []
    for (const slot of ['A', 'B']) rows.push(await post('/iam/posts', { name: 'S1d对象' + slot, code: 'S1D_' + slot + '_' + suffix, enabled: true, sort: 0 }))
    return rows
  }, suffix)
  const first = rows[0]!, second = rows[1]!, peer = await context.newPage()
  await login(peer); await page.reload()
  let committed = false, writes = 0, release!: () => void
  const hold = new Promise<void>(resolve => { release = resolve })
  await page.route(url => url.pathname === '/api/iam/posts/' + first.id, async route => {
    if (route.request().method() !== 'PUT') return route.continue()
    writes++; const response = await route.fetch(), body = await response.json()
    expect(body.code).toBe(0); committed = true; await hold; await route.fulfill({ response })
  })
  try {
    await page.getByRole('row').filter({ has: page.getByRole('cell', { name: first.name, exact: true }) }).getByRole('button', { name: '编辑', exact: true }).click()
    await page.getByRole('dialog').getByRole('textbox', { name: '名称', exact: true }).fill('S1d对象A已更新')
    await page.getByRole('dialog').getByRole('button', { name: '保存', exact: true }).click()
    await expect.poll(() => committed).toBe(true)
    await peer.getByRole('row').filter({ has: peer.getByRole('cell', { name: second.name, exact: true }) }).getByRole('button', { name: '编辑', exact: true }).click()
    await peer.getByRole('dialog').getByRole('textbox', { name: '名称', exact: true }).fill('S1d对象B已更新')
    await peer.getByRole('dialog').getByRole('button', { name: '保存', exact: true }).click()
    await expect(peer.getByRole('dialog')).toHaveCount(0)
    expect(await readPosts(peer, second.code)).toEqual([expect.objectContaining({ id: second.id, name: 'S1d对象B已更新' })])
    expect(writes).toBe(1)
  } finally { release() }
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(await readPosts(page, first.code)).toEqual([expect.objectContaining({ id: first.id, name: 'S1d对象A已更新' })])
  await peer.close()
})

test('a real client timeout and NOT_FOUND receipt cannot release an original request that may still arrive', async ({ page }) => {
  await login(page)
  const code = 'S1D_TIMEOUT_' + Date.now(), name = 'S1d超时后到达岗位'
  let writes = 0, committed = false, release!: () => void
  const hold = new Promise<void>(resolve => { release = resolve })
  await page.route(url => url.pathname === '/api/iam/posts', async route => {
    if (route.request().method() !== 'POST') return route.continue()
    writes++
    await hold // Hold delivery before dispatch, longer than the real client's 15s deadline.
    const response = await route.fetch(), body = await response.json()
    expect(response.ok()).toBe(true); expect(body.code).toBe(0); committed = true
    // The original browser transport has already timed out. No acknowledgement is fabricated.
    try { await route.abort('failed') } catch { /* The browser may already have disposed its aborted route. */ }
  })
  const dialog = await createForm(page, code, name)
  try {
    await dialog.getByRole('button', { name: '保存', exact: true }).click()
    await expect(dialog.getByRole('button', { name: '查询原操作结果', exact: true })).toBeVisible({ timeout: 25000 })
    const lookup = page.waitForResponse(response => new URL(response.url()).pathname === '/api/common/requests/lookup')
    await dialog.getByRole('button', { name: '查询原操作结果', exact: true }).click()
    expect((await lookup).status()).toBe(404)
    await expect(dialog.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
    expect(await readPosts(page, code)).toEqual([])
    expect(writes).toBe(1)
  } finally { release() }
  await expect.poll(() => committed, { timeout: 30000 }).toBe(true)
  await dialog.getByRole('button', { name: '查询原操作结果', exact: true }).click()
  await expect(dialog).toHaveCount(0)
  expect(await readPosts(page, code)).toEqual([expect.objectContaining({ code, name })])
  expect(writes).toBe(1)
})
