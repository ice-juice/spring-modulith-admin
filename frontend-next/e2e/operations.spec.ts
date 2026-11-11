import { expect, test } from '@playwright/test'
import { fullApi } from './full-api'
import { login } from './mock-api'

test('five notice audiences, existing attachment selection, readers and recycle actions use explicit IDs', async ({ page }) => {
  test.setTimeout(90000)
  const api = await fullApi(page), errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await login(page, '/message/notices')
  const dialog = page.getByRole('dialog')
  for (const [kind, label] of [['ALL', '全部可见用户'], ['ROLE', '角色'], ['DEPARTMENT', '部门'], ['POST', '岗位'], ['USER', '指定用户']]) {
    await page.getByRole('button', { name: '发布通知', exact: true }).click()
    await dialog.getByRole('textbox', { name: '标题', exact: true }).fill('通知 ' + kind)
    await dialog.getByRole('textbox', { name: '内容', exact: true }).fill('验收内容')
    await dialog.locator('#notice-audience').click()
    await page.getByRole('button', { name: label, exact: true }).click()
    if (kind !== 'ALL') {
      await dialog.locator('#notice-recipients').click()
      await page.getByRole('button', { name: '测试成员', exact: true }).click()
      await page.keyboard.press('Escape')
    }
    if (kind === 'ALL') {
      await dialog.getByRole('button', { name: '选择已有文件', exact: true }).click()
      const picker = page.getByRole('dialog', { name: '选择已有文件', exact: true })
      await picker.getByRole('checkbox').click()
      await picker.getByRole('button', { name: '保存', exact: true }).click()
      await expect(picker).toHaveCount(0)
      await expect(dialog.getByRole('button', { name: 'sample.png', exact: true })).toBeVisible()
    }
    await dialog.getByRole('button', { name: '保存', exact: true }).click()
    await expect(dialog).toHaveCount(0)
    const body = api.writes.filter(write => write.path === '/message/notices' && write.method === 'POST').at(-1)?.body
    expect(body?.audienceType).toBe(kind)
    expect(body?.audienceIds).toEqual(kind === 'ALL' ? [] : ['102'])
    expect(body?.fileIds).toEqual(kind === 'ALL' ? ['file1'] : [])
  }
  await page.getByRole('button', { name: '0 / 2', exact: true }).click()
  await expect(dialog.getByRole('cell', { name: '测试管理员', exact: true })).toBeVisible()
  await dialog.getByRole('button', { name: '取消', exact: true }).last().click()
  await page.getByRole('button', { name: '回收', exact: true }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect.poll(() => api.writes.some(write => write.path === '/message/notices/notice1' && write.method === 'DELETE')).toBe(true)
  await page.getByRole('switch', { name: '回收站', exact: true }).click()
  await page.getByRole('button', { name: '恢复', exact: true }).click()
  await expect.poll(() => api.writes.some(write => write.path === '/message/notices/notice1/restore')).toBe(true)
  expect(api.unmatched).toEqual([])
  expect(errors).toEqual([])
})

test('task form types, UTC conversion, state transitions and managed assignment retain their contracts', async ({ page }) => {
  test.setTimeout(90000)
  const api = await fullApi(page)
  await login(page, '/message/inbox?tab=tasks')
  const dialog = page.getByRole('dialog')
  for (const [kind, label] of [[1, '普通'], [2, '内容'], [3, '链接'], [4, '内容与链接']]) {
    await page.getByRole('button', { name: '新建待办', exact: true }).click()
    await dialog.getByRole('textbox', { name: '标题', exact: true }).fill('待办 ' + kind)
    await dialog.locator('#task-type').click()
    await page.getByRole('button', { name: String(label), exact: true }).click()
    await dialog.locator('#task-due').fill('2030-11-10T15:30')
    if ([2, 4].includes(Number(kind))) await dialog.locator('#task-content').fill('任务内容')
    if ([3, 4].includes(Number(kind))) await dialog.locator('#task-url').fill('/iam/users')
    await dialog.getByRole('button', { name: '保存', exact: true }).click()
    await expect(dialog).toHaveCount(0)
    const body = api.writes.filter(write => write.path === '/message/todos' && write.method === 'POST').at(-1)?.body
    expect(body?.type).toBe(kind)
    expect(body?.dueAt).toBe('2030-11-10T07:30:00.000Z')
    expect(body?.originalDueAt).toBeUndefined()
  }
  await page.getByRole('button', { name: '变更状态', exact: true }).click()
  await page.getByRole('menuitem', { name: '已完成', exact: true }).click()
  await expect.poll(() => api.task.state).toBe(3)
  await expect(page.getByRole('button', { name: '编辑', exact: true })).toHaveCount(0)
  await page.goto('/message/todos')
  await page.getByRole('button', { name: '分配待办', exact: true }).click()
  await dialog.locator('#task-user').click()
  await page.getByRole('button', { name: '测试成员', exact: true }).click()
  await dialog.locator('#task-title').fill('分配验收')
  await dialog.locator('#task-due').fill('2030-11-10T15:30')
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(dialog).toHaveCount(0)
  expect(api.writes.find(write => write.path === '/message/todos/assign')?.body.userId).toBe('102')
  expect(api.unmatched).toEqual([])
})

test('profile drafts survive header preference changes; saved theme, language and tabs survive reload', async ({ page }) => {
  const api = await fullApi(page)
  await login(page, '/profile')
  await page.locator('#profile-name').fill('尚未保存的姓名')
  await page.getByRole('button', { name: '主题', exact: true }).click()
  await page.getByRole('menuitem', { name: '深色', exact: true }).click()
  await expect(page.locator('html')).toHaveClass(/dark/)
  await expect(page.locator('#profile-name')).toHaveValue('尚未保存的姓名')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => api.writes.some(write => write.path === '/iam/profile' && write.body.name === '尚未保存的姓名')).toBe(true)
  await page.goto('/preferences')
  await page.locator('#preference-keep-tabs').click()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => api.preferences.keepTabsAfterReload).toBe(true)
  await page.getByRole('button', { name: '语言与时区', exact: true }).click()
  await page.getByRole('menuitem', { name: 'English', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Preferences', exact: true })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Preferences', exact: true })).toBeVisible()
  await expect(page.locator('html')).toHaveClass(/dark/)
  expect(api.unmatched).toEqual([])
})

test('cache clear uses preview and a durable task instead of a direct delete', async ({ page }) => {
  const api = await fullApi(page)
  await login(page, '/monitor/caches')
  await page.locator('main tbody').getByRole('button', { name: '清理', exact: true }).first().click()
  await page.getByRole('dialog').getByLabel('操作原因', { exact: true }).fill('刷新缓存')
  await page.getByRole('dialog').getByRole('button', { name: '预览目标', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '确认提交', exact: true }).click()
  await expect.poll(() => api.writes.some(write => write.path.endsWith('/submit') && write.method === 'POST')).toBe(true)
  await expect(page.getByRole('dialog', { name: '缓存任务详情' })).toBeVisible()
  expect(api.writes.some(write => write.path.startsWith('/monitor/caches') && write.method === 'DELETE')).toBe(false)
})

test('SQL reset and event retry are confirmed writes; referenced files retain visible errors', async ({ page }) => {
  const api = await fullApi(page)
  await login(page, '/monitor/sql')
  for (const [path, label, endpoint, method] of [
    ['/monitor/sql', '重置', '/monitor/sql', 'DELETE'],
    ['/monitor/events', '重试', '/monitor/publications/event1/retry', 'POST'],
  ]) {
    await page.goto(path!)
    await page.getByRole('button', { name: label!, exact: true }).click()
    await page.getByRole('alertdialog').getByRole('button', { name: '确认', exact: true }).click()
    await expect.poll(() => api.writes.some(write => write.path === endpoint && write.method === method)).toBe(true)
  }
  await page.goto('/file/files')
  await page.route((process.env.SMA_E2E_MOCK_ORIGIN || 'http://127.0.0.1:5174') + '/api/file/files/file1', route => route.request().method() === 'DELETE' ? route.fulfill({ status: 409, json: { message: '文件已被引用，不能删除' } }) : route.fallback())
  await page.getByRole('button', { name: '删除', exact: true }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveText(/文件已被引用/)
  await expect(page.getByRole('button', { name: 'sample.png', exact: true })).toBeVisible()
  expect(api.unmatched).toEqual([])
})

test('file thumbnails release authenticated object URLs when their page is left', async ({ page }) => {
  await fullApi(page)
  await page.addInitScript(() => {
    const created: string[] = [], revoked: string[] = []
    const create = URL.createObjectURL.bind(URL), revoke = URL.revokeObjectURL.bind(URL)
    URL.createObjectURL = blob => { const value = create(blob); created.push(value); return value }
    URL.revokeObjectURL = value => { revoked.push(value); revoke(value) }
    Object.assign(window, { fileUrlProbe: { created, revoked } })
  })
  await login(page, '/file/files')
  const probe = () => page.evaluate(() => (window as unknown as { fileUrlProbe: { created: string[]; revoked: string[] } }).fileUrlProbe)
  await expect.poll(async () => (await probe()).created.length).toBeGreaterThan(0)
  const urls = (await probe()).created
  await page.getByRole('link', { name: '用户管理', exact: true }).click()
  await expect(page.locator('main h1')).toHaveText('用户管理')
  await expect.poll(async () => { const revoked = (await probe()).revoked; return urls.every(value => revoked.includes(value)) }).toBe(true)
})
