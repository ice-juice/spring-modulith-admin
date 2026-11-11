import { test, expect } from '@playwright/test'
import { fullApi } from './full-api'
import { login } from './mock-api'
import type { ShelfBoard } from '../src/features/workspace/shelf'
import type { PersonalNote } from '../src/features/workspace/notes'
import AxeBuilder from '@axe-core/playwright'
test('personal bookmarks add, rename, reorder, remove, undo and survive closing work tabs', async ({ page }) => {
  const api = await fullApi(page, 'http://127.0.0.1:5198')
  const targets = [{ id: '0', title: '', path: '/', icon: null }, { id: 'users', title: '用户管理', path: '/iam/users', icon: null }, { id: 'roles', title: '角色管理', path: '/iam/roles', icon: null }]
  const board: ShelfBoard = { scope: '1.admin.101', version: '0', mode: 'drawer', bookmarks: [], targets }
  let sequence = 0, rejectRename = false
  await page.route('**/api/workspace/shelf**', route => {
    const req = route.request(), path = new URL(req.url()).pathname, method = req.method()
    const body = method === 'GET' || method === 'DELETE' ? {} : req.postDataJSON()
    if (method === 'POST') {
      if (!board.bookmarks.some(b => b.menuId === body.menuId)) board.bookmarks.push({ id: String(++sequence), menuId: body.menuId, alias: body.alias, position: sequence, target: targets.find(t => t.id === body.menuId)! })
    } else if (method === 'DELETE') board.bookmarks = board.bookmarks.filter(b => b.id !== path.split('/').at(-1))
    else if (method === 'PUT' && path.endsWith('/order')) board.bookmarks.sort((a, b) => body.ids.indexOf(a.id) - body.ids.indexOf(b.id))
    else if (method === 'PUT') {
      if (rejectRename) { rejectRename = false; return route.fulfill({ json: { code: 409, message: '其他窗口已修改，请保留编辑后重试', data: null } }) }
      board.bookmarks.find(b => b.id === path.split('/').at(-1))!.alias = body.alias
    }
    if (method !== 'GET') board.version = String(Number(board.version) + 1)
    return route.fulfill({ json: { code: 0, data: board } })
  })
  await login(page, '/iam/users?keyword=preserved')
  await page.locator('.page-heading').getByRole('button', { name: '固定到我的标签' }).click()
  await page.getByRole('button', { name: '我的随手栏', exact: true }).click()
  const drawer = page.getByRole('dialog', { name: '我的随手栏', exact: true })
  await expect(drawer.locator('[data-shelf-bookmark]')).toHaveCount(1)
  await drawer.getByRole('button', { name: '添加', exact: true }).click()
  await drawer.getByRole('button', { name: '角色管理', exact: true }).click()
  await expect(drawer.locator('[data-shelf-bookmark]')).toHaveCount(2)
  await drawer.getByRole('button', { name: '用户管理', exact: true }).click()
  await expect(page).toHaveURL(/\/iam\/users\?keyword=preserved$/)
  await page.getByRole('button', { name: '我的随手栏', exact: true }).click()
  await drawer.locator('[data-shelf-bookmark]').nth(1).getByRole('button', { name: '上移', exact: true }).click()
  await expect(drawer.locator('[data-shelf-bookmark]').first()).toContainText('角色管理')
  const first = drawer.locator('[data-shelf-bookmark]').first()
  await first.getByRole('button', { name: '修改个人名称' }).click()
  await first.getByRole('textbox', { name: '个人显示名称' }).fill('常用角色')
  rejectRename = true
  await first.getByRole('button', { name: '保存', exact: true }).click()
  await expect(first.getByRole('textbox')).toHaveValue('常用角色')
  await expect(drawer).toContainText('其他窗口已修改')
  await first.getByRole('button', { name: '保存', exact: true }).click()
  await expect(first).toContainText('常用角色')
  await first.getByRole('button', { name: '取消固定', exact: true }).click()
  await drawer.getByRole('button', { name: '撤销', exact: true }).click()
  await expect(drawer.locator('[data-shelf-bookmark]')).toHaveCount(2)
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: '关闭 用户管理', exact: true }).click()
  expect(board.bookmarks).toHaveLength(2)
  await page.reload()
  await page.getByRole('button', { name: '我的随手栏', exact: true }).click()
  await expect(drawer).toContainText('常用角色')
  await page.setViewportSize({ width: 320, height: 700 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
  board.bookmarks[0]!.target = null
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: '我的随手栏', exact: true }).click()
  await expect(drawer.locator('[data-shelf-bookmark]').first()).toContainText('入口已失效')
  await expect(drawer.locator('[data-shelf-bookmark]').first().getByRole('button', { name: '入口已失效或无权访问', exact: true })).toBeDisabled()
  expect(api.unmatched).toEqual([])
})

test('floating and docked layouts support pointer keyboard resize, three notes, restore and narrow screens', async ({ page }) => {
  test.setTimeout(90000)
  const api = await fullApi(page, 'http://127.0.0.1:5198')
  const board: ShelfBoard = { scope: '1.admin.101', mode: 'drawer', version: '0', bookmarks: [], targets: [{ id: '0', title: '', path: '/', icon: null }] }
  const records: PersonalNote[] = Array.from({ length: 4 }, (_, i) => ({ id: String(i + 1), requestKey: '00000000-0000-4000-8000-00000000000' + (i + 1), title: '便签 ' + (i + 1), kind: 'text', content: '测试私有内容 ' + (i + 1), items: [], color: 'purple', pinned: false, relatedMenuId: null, relatedTarget: null, status: 'active', trashedAt: null, version: '1', updatedAt: '2026-11-11T00:00:00Z' }))
  await page.route('**/api/workspace/shelf**', route => {
    const request = route.request(), path = new URL(request.url()).pathname
    if (path.endsWith('/mode')) { board.mode = request.postDataJSON().mode; board.version = String(Number(board.version) + 1) }
    return route.fulfill({ json: { code: 0, data: path.includes('/notes') ? records : board } })
  })
  await page.setViewportSize({ width: 1440, height: 900 }); await login(page, '/')
  await page.getByRole('button', { name: '我的随手栏', exact: true }).click()
  await page.getByLabel('显示方式', { exact: true }).selectOption('floating')
  let panel = page.getByRole('region', { name: '我的随手栏', exact: true })
  await expect(panel).toBeVisible()
  const handle = panel.getByRole('button', { name: '移动窗口（方向键可调整）', exact: true })
  await handle.click({ trial: true })
  const handleBox = (await handle.boundingBox())!
  await page.mouse.move(handleBox.x + 10, handleBox.y + 10); await page.mouse.down(); await page.mouse.move(36, 200, { steps: 8 }); await page.mouse.up()
  expect((await panel.boundingBox())!.x).toBe(8)
  const previousWidth = (await panel.boundingBox())!.width
  await panel.getByRole('button', { name: '调整大小（方向键可调整）' }).focus()
  await page.keyboard.press('ArrowRight')
  expect((await panel.boundingBox())!.width).toBe(previousWidth + 16)
  await panel.getByRole('button', { name: '折叠为标题条' }).click()
  expect((await panel.boundingBox())!.height).toBe(48)
  await panel.getByRole('button', { name: '展开窗口' }).click()
  await page.screenshot({ path: 'test-results-shelf/floating-light.png' })
  await panel.getByLabel('显示方式', { exact: true }).selectOption('docked')
  await expect(page.locator('[data-shelf-docked]')).toBeVisible()
  expect(await page.locator('.admin-shell').evaluate(el => getComputedStyle(el).paddingRight)).toBe('380px')
  await page.evaluate(() => document.documentElement.classList.add('dark'))
  await page.screenshot({ path: 'test-results-shelf/docked-dark.png' })
  await page.setViewportSize({ width: 320, height: 700 })
  await expect(page.getByRole('dialog', { name: '我的随手栏', exact: true })).toBeVisible()
  await page.getByRole('dialog', { name: '我的随手栏', exact: true }).getByLabel('显示方式', { exact: true }).click({ trial: true })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
  await page.screenshot({ path: 'test-results-shelf/narrow-dark.png' })
  await page.setViewportSize({ width: 1440, height: 900 })
  panel = page.getByRole('region', { name: '我的随手栏', exact: true })
  await panel.getByLabel('显示方式', { exact: true }).selectOption('drawer')
  let drawer = page.getByRole('dialog', { name: '我的随手栏', exact: true })
  await drawer.getByRole('button', { name: '个人便签', exact: true }).click()
  for (let i = 1; i <= 3; i++) {
    if (i > 1) { await page.getByRole('button', { name: '我的随手栏', exact: true }).click(); await drawer.getByRole('button', { name: '返回便签列表' }).click() }
    await drawer.locator('[data-note-row]').filter({ hasText: '便签 ' + i }).click()
    await drawer.getByRole('button', { name: '悬浮显示', exact: true }).click()
    await expect(page.locator('[data-shelf-floating]')).toHaveCount(i)
  }
  await page.getByRole('button', { name: '我的随手栏', exact: true }).click()
  await drawer.getByRole('button', { name: '返回便签列表' }).click()
  await drawer.locator('[data-note-row]').filter({ hasText: '便签 4' }).click()
  await drawer.getByRole('button', { name: '悬浮显示', exact: true }).click()
  await expect(page.getByText('最多同时悬浮 3 张便签，请先收起一张。', { exact: true })).toBeVisible()
  await expect(page.locator('[data-shelf-floating]')).toHaveCount(3)
  await page.keyboard.press('Escape'); await page.reload()
  await expect(page.locator('[data-shelf-floating]')).toHaveCount(3)
  expect(await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage }))).not.toContain('测试私有内容')
  await page.screenshot({ path: 'test-results-shelf/three-notes.png' })
  Object.assign(api.preferences, { locale: 'en-US', theme: 'dark' }); await page.reload()
  await page.getByRole('button', { name: 'My shelf', exact: true }).click()
  drawer = page.getByRole('dialog', { name: 'My shelf', exact: true })
  await expect(drawer.getByLabel('Display mode', { exact: true })).toBeVisible()
  const accessibility = await new AxeBuilder({ page }).include('[data-slot="sheet-content"]').withTags(['wcag2a', 'wcag2aa']).analyze()
  expect(accessibility.violations).toEqual([])
  await page.screenshot({ path: 'test-results-shelf/drawer-english-dark.png' })
  await page.keyboard.press('Escape')
  await page.locator('.admin-shell header').getByRole('button', { name: 'Account', exact: true }).click()
  await page.getByRole('menuitem', { name: 'Change password', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: 'Change password', exact: true })
  await expect(dialog).toBeVisible()
  const modalLayer = await dialog.evaluate(el => Number(getComputedStyle(el).zIndex))
  const noteLayer = await page.locator('[data-shelf-floating]').first().evaluate(el => Number(getComputedStyle(el).zIndex))
  expect(modalLayer).toBeGreaterThan(noteLayer)
  await page.keyboard.press('Escape')
  await page.locator('.admin-shell header').getByRole('button', { name: 'Account', exact: true }).click()
  await page.getByRole('menuitem', { name: 'Lock screen', exact: true }).click()
  await expect(page.getByRole('dialog', { name: 'Screen locked', exact: true })).toBeVisible()
  await expect(page.locator('[data-shelf-floating]')).toHaveCount(0)
  await expect(page.getByText('测试私有内容 1', { exact: true })).toHaveCount(0)
})

test('notes autosave text and checklist, preserve failures, resolve conflicts, archive and restore', async ({ page }) => {
  await fullApi(page, 'http://127.0.0.1:5198')
  const records: import('../src/features/workspace/notes').PersonalNote[] = []
  let fail = false, conflict = false, writes = 0, failedKey = ''
  await page.route('**/api/common/requests/lookup', route => {
    expect(route.request().postDataJSON()).toEqual({ key: failedKey })
    return route.fulfill({ json: { code: 0, data: { operationId: 'note-receipt', operation: 'shelf.note-update', state: fail ? 'PENDING' : 'FAILED_FINAL', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:01Z' } } })
  })
  const targets = [{ id: '0', title: '', path: '/', icon: null }]
  await page.route('**/api/workspace/shelf**', route => {
    const request = route.request(), path = new URL(request.url()).pathname, method = request.method(), body = method === 'GET' ? {} : request.postDataJSON()
    const ok = (data: unknown) => route.fulfill({ json: { code: 0, data } })
    if (path === '/api/workspace/shelf') return ok({ scope: '1.admin.101', mode: 'drawer', version: '0', bookmarks: [], targets })
    if (method === 'GET') return ok(path.endsWith('/notes') ? records : records.find(r => r.id === path.split('/').at(-1)))
    writes++
    if (fail) { failedKey = request.headers()['idempotency-key']!; return route.fulfill({ status: 500, json: { code: 500, message: '模拟网络失败' } }) }
    if (method === 'POST') {
      const previous = records.find(r => r.requestKey === body.requestKey)
      if (previous) return ok(previous)
      const n = { ...body.note, id: String(records.length + 1), requestKey: body.requestKey, version: '1', status: 'active', trashedAt: null, updatedAt: new Date().toISOString(), relatedTarget: null }
      records.push(n); return ok(n)
    }
    const row = records.find(r => r.id === path.split('/')[path.endsWith('/status') ? 5 : 5])!
    if (conflict) { conflict = false; row.version = String(Number(row.version) + 1); row.content = '远端内容'; return route.fulfill({ json: { code: 409, message: '版本冲突' } }) }
    Object.assign(row, path.endsWith('/status') ? { status: body.status } : body.note, { version: String(Number(row.version) + 1) })
    return ok(row)
  })
  await login(page, '/')
  await page.getByRole('button', { name: '我的随手栏', exact: true }).click()
  const drawer = page.getByRole('dialog', { name: '我的随手栏', exact: true })
  await drawer.getByRole('button', { name: '个人便签', exact: true }).click()
  await drawer.getByRole('button', { name: '新建文字便签' }).click()
  await drawer.getByLabel('便签标题', { exact: true }).fill('核对清单')
  await drawer.getByLabel('便签内容', { exact: true }).fill('保留我的编辑')
  await expect(drawer.getByRole('status')).toHaveText('已保存')
  expect(records[0]!.content).toBe('保留我的编辑')
  await drawer.getByLabel('便签类型', { exact: true }).selectOption('checklist')
  await drawer.getByRole('button', { name: '添加清单项' }).click()
  await expect(drawer).toContainText('请填写标题和每个清单项，并检查内容长度。')
  await drawer.getByLabel('第 1 项内容', { exact: true }).fill('检查权限')
  await drawer.getByLabel('完成第 1 项', { exact: true }).check()
  await expect(drawer.getByRole('status')).toHaveText('已保存')
  expect(records[0]!.items[0]!.done).toBe(true)
  fail = true
  await drawer.getByLabel('便签内容', { exact: true }).fill('保存失败也要保留')
  await expect(drawer.getByRole('button', { name: '查询原操作结果', exact: true })).toBeVisible()
  const failuresAt = writes
  await page.waitForTimeout(1500)
  expect(writes).toBe(failuresAt)
  await page.keyboard.press('Escape')
  await expect(drawer).toBeVisible()
  await expect(drawer.getByLabel('便签内容', { exact: true })).toHaveValue('保存失败也要保留')
  fail = false
  await drawer.getByRole('button', { name: '查询原操作结果', exact: true }).click()
  expect(writes).toBe(failuresAt)
  await drawer.getByRole('button', { name: '重试', exact: true }).click()
  await expect(drawer.getByRole('status')).toHaveText('已保存')
  conflict = true
  await drawer.getByLabel('便签内容', { exact: true }).fill('我的冲突副本')
  await expect(drawer.getByRole('button', { name: '另存为新便签' })).toBeVisible()
  await drawer.getByRole('button', { name: '另存为新便签' }).click()
  await expect(drawer.getByLabel('便签内容', { exact: true })).toHaveValue('我的冲突副本')
  await expect(drawer.getByRole('status')).toHaveText('已保存')
  expect(records).toHaveLength(2); expect(records[0]!.content).toBe('远端内容')
  await drawer.getByRole('button', { name: '归档', exact: true }).click()
  await drawer.getByRole('button', { name: '已归档', exact: true }).click()
  await drawer.locator('[data-note-row]').click()
  await drawer.getByRole('button', { name: '恢复', exact: true }).click()
  await drawer.getByRole('button', { name: '使用中', exact: true }).click()
  await drawer.locator('[data-note-row]').filter({ hasText: '我的冲突副本' }).click()
  await drawer.getByRole('button', { name: '移入回收站', exact: true }).click()
  await drawer.getByRole('button', { name: '回收站', exact: true }).click()
  await drawer.locator('[data-note-row]').click()
  await drawer.getByRole('button', { name: '恢复', exact: true }).click()
  expect(await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage }))).not.toContain('我的冲突副本')
})

test('locked sign out asks before discarding unsaved notes and cancellation keeps the session', async ({ page }) => {
  const api = await fullApi(page, 'http://127.0.0.1:5198')
  await page.route('**/api/workspace/shelf**', route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/shelf')) return route.fulfill({ json: { code: 0, data: { scope: '1.admin.101', mode: 'drawer', version: '0', bookmarks: [], targets: [] } } })
    if (route.request().method() === 'GET') return route.fulfill({ json: { code: 0, data: [] } })
    return route.fulfill({ status: 500, json: { code: 500, message: '保存失败，草稿保留' } })
  })
  await login(page, '/')
  await page.getByRole('button', { name: '我的随手栏', exact: true }).click()
  const shelf = page.getByRole('dialog', { name: '我的随手栏', exact: true })
  await shelf.getByRole('button', { name: '个人便签', exact: true }).click()
  await shelf.getByRole('button', { name: '新建文字便签' }).click()
  await shelf.getByLabel('便签内容', { exact: true }).fill('尚未同步的草稿')
  await expect(shelf.getByRole('button', { name: '查询原操作结果', exact: true })).toBeVisible()
  await page.evaluate(() => window.dispatchEvent(new Event('sma:locked')))
  const locked = page.getByRole('dialog').filter({ has: page.getByRole('button', { name: '退出登录', exact: true }) })
  await locked.getByRole('button', { name: '退出登录', exact: true }).click()
  const confirmation = page.getByRole('alertdialog')
  await expect(confirmation).toContainText('继续退出将丢弃本次修改')
  expect(api.writes.filter(w => w.path === '/auth/logout')).toHaveLength(0)
  await confirmation.getByRole('button', { name: '取消', exact: true }).click()
  await expect(locked).toBeVisible()
  await locked.getByRole('button', { name: '退出登录', exact: true }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect(page).toHaveURL(/\/login/)
  expect(api.writes.filter(w => w.path === '/auth/logout')).toHaveLength(1)
})
