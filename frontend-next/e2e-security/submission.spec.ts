import { expect, test } from '@playwright/test'
import { login, mockApi, roleId, userId } from '../e2e/mock-api'
import { fullApi } from '../e2e/full-api'
const origin = 'http://127.0.0.1:5196'
test.use({ locale: 'zh-CN' })

test('accepted file upload resumes the original bytes and key only after a fresh lookup', async ({ page }) => {
  await fullApi(page, origin)
  const uploadId = '11111111-1111-4111-8111-111111111111', keys: string[] = []
  let writes = 0, lookups = 0
  await page.route(url => url.pathname === '/api/file/files', route => {
    if (route.request().method() !== 'POST') return route.fallback()
    writes++; keys.push(route.request().headers()['idempotency-key'] || '')
    expect(route.request().postDataBuffer()?.toString()).toContain('original-upload-content')
    return writes === 1 ? route.abort('failed') : route.fulfill({ json: { code: 0, data: { id: '42', name: 'original.txt' } } })
  })
  await page.route(url => url.pathname === '/api/file/uploads/lookup', route => {
    lookups++; expect(route.request().postDataJSON()).toEqual({ key: keys[0] })
    return route.fulfill({ json: { code: 0, data: { uploadId, state: 'ACCEPTED', fileId: null, resumable: true } } })
  })
  await login(page, '/file/files')
  await page.locator('input[type="file"]').setInputFiles({ name: 'original.txt', mimeType: 'text/plain', buffer: Buffer.from('original-upload-content') })
  await page.getByRole('button', { name: '查询原操作结果', exact: true }).click()
  await page.getByRole('button', { name: '继续上传原文件', exact: true }).evaluate(button => { for (let i = 0; i < 20; i++) (button as HTMLButtonElement).click() })
  await expect(page.getByRole('button', { name: '查询原操作结果', exact: true })).toHaveCount(0)
  await expect.poll(() => writes).toBe(2)
  expect(writes).toBe(2); expect(lookups).toBe(2); expect(keys[0]).toMatch(/^[a-f0-9-]{36}$/); expect(keys[1]).toBe(keys[0])
})

test('file-library recovery survives reload without attaching to a profile or recreating missing file input', async ({ page }) => {
  await fullApi(page, origin); let writes = 0, key = ''
  await page.route(url => url.pathname === '/api/file/files', route => {
    if (route.request().method() !== 'POST') return route.fallback()
    writes++; key = route.request().headers()['idempotency-key'] || ''; return route.abort('failed')
  })
  await page.route(url => url.pathname === '/api/file/uploads/lookup', route => {
    expect(route.request().postDataJSON()).toEqual({ key })
    return route.fulfill({ json: { code: 0, data: { uploadId: '11111111-1111-4111-8111-111111111111', state: 'ACCEPTED', fileId: null, resumable: true } } })
  })
  await login(page, '/file/files')
  await page.locator('input[type="file"]').setInputFiles({ name: 'original.txt', mimeType: 'text/plain', buffer: Buffer.from('private-upload-content') })
  await expect(page.getByRole('button', { name: '查询原操作结果', exact: true })).toBeVisible()
  await page.goto('/profile'); await expect(page.getByRole('button', { name: '上传头像', exact: true })).toBeEnabled()
  await expect(page.getByRole('button', { name: '查询原操作结果', exact: true })).toHaveCount(0)
  await page.goto('/file/files'); await page.getByRole('button', { name: '查询原操作结果', exact: true }).click()
  await expect(page.getByText('原文件已离开当前页面，不能直接续传。可继续查询原任务；完成后从文件库选择文件。', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '继续上传原文件', exact: true })).toHaveCount(0)
  const saved = await page.evaluate(() => Object.keys(localStorage).filter(key => key.startsWith('sma.operation.v1.')).map(key => localStorage.getItem(key)).join(''))
  expect(saved).toContain('file.library.upload'); expect(saved).not.toMatch(/original.txt|private-upload-content/); expect(writes).toBe(1)
})

test('a late attachment upload cannot attach itself to a newly opened notice draft', async ({ page }) => {
  await fullApi(page, origin); let release!: () => void, writes = 0
  const pending = new Promise<void>(resolve => { release = resolve })
  await page.route(url => url.pathname === '/api/file/files', async route => {
    if (route.request().method() !== 'POST') return route.fallback()
    writes++; await pending
    return route.fulfill({ json: { code: 0, data: { id: '42', name: 'old-draft.txt', contentType: 'text/plain', sizeBytes: '8' } } })
  })
  await login(page, '/message/notices'); await page.getByRole('button', { name: '发布通知', exact: true }).click()
  await page.getByRole('dialog').locator('#notice-title').fill('Original draft')
  await page.getByRole('dialog').locator('input[type="file"]').setInputFiles({ name: 'old-draft.txt', mimeType: 'text/plain', buffer: Buffer.from('original') })
  await expect.poll(() => writes).toBe(1)
  await page.getByRole('dialog').locator('[data-slot="dialog-footer"]').getByRole('button', { name: '取消', exact: true }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: '确认', exact: true }).click()
  await page.getByRole('button', { name: '发布通知', exact: true }).click(); release()
  await expect(page.getByRole('dialog').getByRole('button', { name: '上传文件', exact: true })).toBeEnabled()
  await expect(page.getByRole('dialog').getByText('old-draft.txt', { exact: true })).toHaveCount(0)
  expect(writes).toBe(1)
})

test('removing the previous avatar starts a distinct upload context without replaying an unresolved replacement', async ({ page }) => {
  await fullApi(page, origin); let writes = 0
  await page.route(url => url.pathname === '/api/iam/profile', route => route.fulfill({ json: { code: 0, data: { id: '101', username: 'fixture', name: 'Fixture', avatarFileId: '77' } } }))
  await page.route(url => url.pathname === '/api/file/files/images', route => { writes++; return route.abort('failed') })
  await login(page, '/profile')
  await page.locator('input[type="file"]').setInputFiles({ name: 'new-avatar.png', mimeType: 'image/png', buffer: Buffer.from('fixture-image') })
  await expect(page.getByRole('button', { name: '查询原操作结果', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '移除头像', exact: true }).click()
  await expect(page.getByRole('button', { name: '查询原操作结果', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '上传头像', exact: true })).toBeEnabled()
  const saved = await page.evaluate(() => Object.keys(localStorage).filter(key => key.startsWith('sma.operation.v1.')).map(key => localStorage.getItem(key)).join(''))
  expect(saved).toContain('file.avatar.self.77'); expect(writes).toBe(1)
})

test('unknown notice submission freezes rich content and attachments and checks the original intent', async ({ page }) => {
  await fullApi(page, origin); let writes = 0, key = ''
  await page.route(url => url.pathname === '/api/message/notices', route => {
    if (route.request().method() !== 'POST') return route.fallback()
    writes++; key = route.request().headers()['idempotency-key'] || ''
    return route.fulfill({ status: 503, json: { code: 503, message: 'Lost acknowledgement' } })
  })
  await page.route(url => url.pathname === '/api/common/requests/lookup', route => {
    expect(route.request().postDataJSON()).toEqual({ key })
    return route.fulfill({ json: { code: 0, data: { operationId: 'notice-receipt', operation: 'notices.save', state: 'SUCCEEDED', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:01Z' } } })
  })
  await login(page, '/message/notices'); await page.getByRole('button', { name: '发布通知', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.locator('#notice-title').fill('A private draft')
  await dialog.getByRole('textbox', { name: '内容', exact: true }).fill('Original unpublished body')
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(dialog.getByRole('button', { name: '查询原操作结果', exact: true })).toBeVisible()
  await expect(dialog.locator('#notice-title')).toBeDisabled()
  await expect(dialog.locator('[role="textbox"][contenteditable]')).toHaveAttribute('contenteditable', 'false')
  await expect(dialog.getByRole('button', { name: '选择已有文件', exact: true })).toBeDisabled()
  await expect(dialog.locator('#notice-audience')).toBeDisabled()
  expect(await page.evaluate(() => Object.keys(localStorage).filter(key => key.startsWith('sma.operation.v1.')).map(key => localStorage.getItem(key)).join(''))).not.toContain('Original unpublished body')
  await dialog.getByRole('button', { name: '查询原操作结果', exact: true }).click()
  await expect(dialog).toHaveCount(0); expect(writes).toBe(1)
})

test('duplicate unread notice clicks keep the first response and block overlapping read-all', async ({ page }) => {
  await fullApi(page, origin); let writes = 0, reads = 0, release!: () => void
  const gate = new Promise<void>(resolve => { release = resolve })
  await page.route(url => url.pathname === '/api/message/notices/inbox/notice1/read', async route => {
    writes++; await gate
    return route.fulfill({ status: 409, json: { code: 409, error: 'common.request.completed', message: 'Completed', data: { operationId: 'read-receipt', operation: 'notices.read', state: 'SUCCEEDED', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:01Z' } } })
  })
  await page.route(url => url.pathname === '/api/message/notices/inbox/notice1', route => { reads++; return route.fallback() })
  await login(page, '/message/inbox')
  await page.getByRole('button', { name: '系统维护通知', exact: true }).evaluate(button => { for (let i = 0; i < 20; i++) (button as HTMLButtonElement).click() })
  await expect.poll(() => writes).toBe(1)
  await expect(page.getByRole('button', { name: '全部标记已读', exact: true })).toBeDisabled()
  release()
  await expect(page.getByRole('dialog')).toContainText('请在维护前保存工作。')
  expect(writes).toBe(1); expect(reads).toBe(1)
})

test('task batch deletion captures selected IDs before confirmation and locks only overlapping rows', async ({ page }) => {
  const api = await fullApi(page, origin); let body: unknown, writes = 0, release!: () => void
  const tasks = ['a', 'b', 'c'].map(id => ({ ...api.task, id, title: 'task-' + id }))
  await page.route(url => url.pathname === '/api/message/todos', route => route.request().method() === 'GET'
    ? route.fulfill({ json: { code: 0, data: { items: tasks, total: '3', page: '1', size: '20' } } }) : route.fallback())
  const gate = new Promise<void>(resolve => { release = resolve })
  await page.route(url => url.pathname === '/api/message/todos/batch-delete', async route => { writes++; body = route.request().postDataJSON(); await gate; await route.fulfill({ json: { code: 0, data: null } }) })
  await login(page, '/message/inbox?tab=tasks')
  for (const id of ['a', 'b']) await page.locator('[data-row-id="' + id + '"]').getByRole('checkbox').click()
  await page.getByRole('button', { name: '删除选中', exact: true }).click()
  // Mutate the underlying selection while confirmation is open, as a programmatic/keyboard entry could.
  await page.locator('[data-row-id="c"]').getByRole('checkbox', { includeHidden: true }).evaluate(element => (element as HTMLElement).click())
  await page.getByRole('alertdialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect.poll(() => writes).toBe(1); expect(body).toEqual({ ids: ['a', 'b'] })
  await expect(page.locator('[data-row-id="a"]').getByRole('button', { name: '删除', exact: true })).toBeDisabled()
  await expect(page.locator('[data-row-id="c"]').getByRole('button', { name: '删除', exact: true })).toBeEnabled()
  release()
  await expect(page.locator('[data-row-id="a"]').getByRole('button', { name: '删除', exact: true })).toBeEnabled()
})

test('actual resource editor merges rapid button and form submissions before asynchronous validation', async ({ page }) => {
  const api = await mockApi(page, origin), errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  let writes = 0, key = '', release!: () => void
  const gate = new Promise<void>(resolve => { release = resolve })
  await page.route(url => url.pathname === '/api/iam/users/' + userId, async route => {
    if (route.request().method() !== 'PUT') return route.fallback()
    writes++; key = route.request().headers()['idempotency-key'] || ''; await gate; await route.fallback()
  })
  await login(page)
  await page.getByRole('button', { name: '编辑', exact: true }).click()
  const dialog = page.getByRole('dialog'), name = dialog.getByRole('textbox', { name: '名称', exact: true })
  await expect(name).toBeEnabled(); await name.fill('防重复编辑')
  await dialog.evaluate(element => {
    const form = element.querySelector('form')!, save = [...element.querySelectorAll('button')].find(button => button.textContent?.trim() === '保存' && !button.hidden)!
    for (let index = 0; index < 20; index++) { save.click(); form.requestSubmit() }
  })
  await expect.poll(() => writes).toBe(1)
  await expect(dialog.getByRole('status')).toContainText('处理时间较长', { timeout: 6000 })
  expect(key).toMatch(/^[a-f0-9-]{36}$/)
  release(); await expect(dialog).toHaveCount(0)
  await expect(page.getByRole('cell', { name: '防重复编辑', exact: true })).toBeVisible()
  expect(api.writes.filter(write => write.path === '/iam/users/' + userId)).toHaveLength(1)
  expect(errors).toEqual([])
})

test('actual filter waits for IME composition and explicit search cancels trailing debounce', async ({ page }) => {
  await mockApi(page, origin); await login(page)
  await expect(page.getByRole('cell', { name: 'test.user', exact: true })).toBeVisible()
  let queries = 0
  await page.route(url => url.pathname === '/api/iam/users', route => { queries++; return route.fallback() })
  const search = page.locator('.filter-bar input').first()
  await search.dispatchEvent('compositionstart'); await search.fill('测试'); await search.press('Enter')
  await page.waitForTimeout(400); expect(queries).toBe(0)
  await search.dispatchEvent('compositionend'); await expect.poll(() => queries).toBe(1)
  await expect(page.locator('.filter-bar button[type="submit"]')).toBeEnabled()
  // Dispatch the explicit search inside the debounce window; locator auto-wait can exceed 300ms under load.
  await search.evaluate(async input => {
    const element = input as HTMLInputElement
    element.value = '另一个关键词'; element.dispatchEvent(new Event('input', { bubbles: true }))
    await Promise.resolve()
    ;(element.form!.querySelector('button[type="submit"]') as HTMLButtonElement).click()
  })
  await expect.poll(() => queries).toBe(2); await page.waitForTimeout(400); expect(queries).toBe(2)
})

test('unknown upload queries its original file task without retransmitting and then rereads the authorized file', async ({ page }) => {
  await fullApi(page, origin)
  let uploads = 0, lookups = 0, key = '', completed = false
  const uploadId = '11111111-1111-4111-8111-111111111111', id = '9007199254740993123'
  await page.route(url => url.pathname === '/api/file/files', route => {
    if (route.request().method() !== 'POST') return route.fallback()
    uploads++; key = route.request().headers()['idempotency-key'] || ''; return route.abort('failed')
  })
  await page.route(url => url.pathname === '/api/file/uploads/lookup', route => {
    lookups++; expect(route.request().postDataJSON()).toEqual({ key })
    return route.fulfill({ json: { code: 0, data: { uploadId, state: completed ? 'SUCCEEDED' : 'RUNNING', fileId: completed ? id : null, resumable: false } } })
  })
  await page.route(url => url.pathname === '/api/file/files/' + id, route => route.fulfill({ json: { code: 0, data: { id, name: 'probe.txt', kind: 'DOCUMENT', sizeBytes: '4', previewable: false } } }))
  await login(page, '/file/files')
  await page.locator('input[type="file"]').setInputFiles({ name: 'probe.txt', mimeType: 'text/plain', buffer: Buffer.from('test') })
  const check = page.getByRole('button', { name: '查询原操作结果', exact: true })
  await expect(check).toBeVisible(); await expect(page.locator('input[type="file"]')).toBeDisabled()
  await check.click(); await expect.poll(() => lookups).toBe(1); expect(uploads).toBe(1)
  completed = true; await check.click(); await expect(check).toHaveCount(0)
  await expect(page.locator('input[type="file"]')).toBeEnabled(); expect(uploads).toBe(1); expect(lookups).toBe(2)
})

test('deletion owns the operation before confirmation and cancelling releases it without sending', async ({ page }) => {
  await mockApi(page, origin); let deletes = 0
  await page.route(url => url.pathname === '/api/iam/users/' + userId, route => {
    if (route.request().method() !== 'DELETE') return route.fallback()
    deletes++; return route.fulfill({ json: { code: 0, data: null } })
  })
  await login(page)
  const remove = page.getByRole('button', { name: '删除', exact: true })
  await remove.evaluate(button => { for (let index = 0; index < 20; index++) (button as HTMLButtonElement).click() })
  const confirmation = page.getByRole('alertdialog')
  await expect(confirmation).toHaveCount(1); await confirmation.getByRole('button', { name: '取消', exact: true }).click()
  await expect(confirmation).toHaveCount(0); expect(deletes).toBe(0)
  await remove.click(); await confirmation.getByRole('button', { name: '确认', exact: true }).click()
  await expect.poll(() => deletes).toBe(1)
})

test('an unknown LOCAL write keeps its draft and resolves by original key without another mutation', async ({ page }) => {
  await mockApi(page, origin); let writes = 0, key = ''
  await page.route(url => url.pathname === '/api/iam/users/' + userId, route => {
    if (route.request().method() !== 'PUT') return route.fallback()
    writes++; key = route.request().headers()['idempotency-key'] || ''
    return route.fulfill({ status: 503, json: { code: 503, error: 'common.request.unavailable', message: 'Unavailable' } })
  })
  await page.route(url => url.pathname === '/api/common/requests/lookup', route => {
    expect(route.request().postDataJSON()).toEqual({ key })
    return route.fulfill({ json: { code: 0, data: { operationId: 'receipt-1', operation: 'users.save', state: 'SUCCEEDED', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:01Z' } } })
  })
  await login(page); await page.getByRole('button', { name: '编辑', exact: true }).click()
  const dialog = page.getByRole('dialog'), name = dialog.getByRole('textbox', { name: '名称', exact: true })
  await expect(name).toBeEnabled(); await name.fill('保留原表单')
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(dialog.getByRole('button', { name: '查询原操作结果', exact: true })).toBeVisible()
  await expect(name).toHaveValue('保留原表单'); await expect(name).toBeDisabled(); await expect(dialog.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  await dialog.locator('form').evaluate(form => { for (let index = 0; index < 20; index++) (form as HTMLFormElement).requestSubmit() })
  expect(writes).toBe(1)
  await dialog.getByRole('button', { name: '查询原操作结果', exact: true }).click()
  await expect(dialog).toHaveCount(0); expect(writes).toBe(1)
})

test('configuration reset is reserved before confirmation and repeated triggers send once', async ({ page }) => {
  await fullApi(page, origin); let writes = 0
  await page.route(url => url.pathname === '/api/config/parameters/reset-builtins', route => {
    writes++; expect(route.request().headers()['idempotency-key']).toMatch(/^[a-f0-9-]{36}$/)
    return route.fulfill({ json: { code: 0, data: null } })
  })
  await login(page, '/config/parameters')
  await page.getByRole('button', { name: '恢复默认', exact: true }).evaluate(button => { for (let index = 0; index < 20; index++) (button as HTMLButtonElement).click() })
  const confirmation = page.getByRole('alertdialog'); await expect(confirmation).toHaveCount(1)
  expect(writes).toBe(0); await confirmation.getByRole('button', { name: '确认', exact: true }).click()
  await expect.poll(() => writes).toBe(1)
})

test('granting and deleting the same role cannot dispatch concurrently', async ({ page }) => {
  await mockApi(page, origin); let grants = 0, deletes = 0, release!: () => void
  const gate = new Promise<void>(resolve => { release = resolve })
  await page.route(url => url.pathname === '/api/iam/roles/' + roleId + '/permissions', async route => {
    if (route.request().method() !== 'PUT') return route.fallback()
    grants++; await gate; await route.fallback()
  })
  await page.route(url => url.pathname === '/api/iam/roles/' + roleId, route => {
    if (route.request().method() !== 'DELETE') return route.fallback()
    deletes++; return route.fulfill({ json: { code: 0, data: null } })
  })
  await login(page, '/iam/roles')
  const panel = page.locator('[data-role-permissions]')
  await panel.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => grants).toBe(1)
  await page.getByRole('button', { name: '删除', exact: true }).click()
  await expect(page.getByRole('alertdialog')).toHaveCount(0); expect(deletes).toBe(0)
  await expect(page.getByRole('alert')).toContainText('待核对')
  release(); await expect(panel.getByRole('button', { name: '保存', exact: true })).toBeEnabled()
})

test('password reset keeps the existing rule and never persists its secret on an unknown result', async ({ page }) => {
  await mockApi(page, origin); let writes = 0, key = ''
  await page.route(url => url.pathname === '/api/iam/users/' + userId + '/password', route => {
    writes++; key = route.request().headers()['idempotency-key'] || ''
    return route.fulfill({ status: 503, json: { code: 503, message: 'Unavailable' } })
  })
  await page.route(url => url.pathname === '/api/common/requests/lookup', route => {
    expect(route.request().postDataJSON()).toEqual({ key })
    return route.fulfill({ json: { code: 0, data: { operationId: 'password-receipt', operation: 'users.password', state: 'SUCCEEDED', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:01Z' } } })
  })
  await login(page); await page.getByRole('button', { name: '重置密码', exact: true }).click()
  const dialog = page.getByRole('dialog'), password = dialog.locator('#reset-password')
  await password.fill('short!'); await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(dialog.getByRole('alert')).toBeVisible(); expect(writes).toBe(0)
  await password.fill('Abcd1234'); await password.press('Enter')
  await expect(dialog.getByRole('button', { name: '查询原操作结果', exact: true })).toBeVisible()
  await expect(password).toBeDisabled(); expect(writes).toBe(1)
  expect(await page.evaluate(() => Object.keys(localStorage).filter(key => key.startsWith('sma.operation.v1.')).map(key => localStorage.getItem(key)).join(''))).not.toContain('Abcd1234')
  await dialog.getByRole('button', { name: '查询原操作结果', exact: true }).click(); await expect(dialog).toHaveCount(0); expect(writes).toBe(1)
})

test('unknown time-zone saving remains recoverable inside its modal', async ({ page }) => {
  await fullApi(page, origin); let writes = 0, key = ''
  await page.route(url => url.pathname === '/api/config/preferences', route => {
    if (route.request().method() !== 'PUT') return route.fallback()
    writes++; key = route.request().headers()['idempotency-key'] || ''
    return route.fulfill({ status: 503, json: { code: 503, message: 'Unavailable' } })
  })
  await page.route(url => url.pathname === '/api/common/requests/lookup', route => {
    expect(route.request().postDataJSON()).toEqual({ key })
    return route.fulfill({ json: { code: 0, data: { operationId: 'zone-receipt', operation: 'preferences.save', state: 'SUCCEEDED', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:01Z' } } })
  })
  await login(page)
  await page.getByRole('button', { name: '语言与时区', exact: true }).click()
  await page.getByRole('menuitem', { name: '时区', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '时区', exact: true })
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(dialog.getByRole('button', { name: '查询原操作结果', exact: true })).toBeEnabled()
  await expect(dialog.locator('[data-slot="dialog-footer"]').getByRole('button', { name: '取消', exact: true })).toBeEnabled()
  await dialog.getByRole('button', { name: '查询原操作结果', exact: true }).click()
  await expect(dialog).toHaveCount(0); expect(writes).toBe(1)
})

test('different file rows can be deleted concurrently while each row retains its original intent', async ({ page }) => {
  await fullApi(page, origin)
  const files = ['1', '2'].map(id => ({ id, name: 'probe-' + id, kind: 'DOCUMENT', sizeBytes: '4', previewable: false, createdAt: '2026-01-01T00:00:00Z' }))
  const releases = new Map<string, () => void>(), keys: string[] = []
  await page.route(url => url.pathname === '/api/file/files', route => route.fulfill({ json: { code: 0, data: { items: files, total: '2', page: '1', size: '20' } } }))
  await page.route(url => /^\/api\/file\/files\/[12]$/.test(url.pathname), async route => {
    if (route.request().method() !== 'DELETE') return route.fallback()
    const id = new URL(route.request().url()).pathname.split('/').at(-1)!
    keys.push(route.request().headers()['idempotency-key']!)
    await new Promise<void>(resolve => releases.set(id, resolve))
    await route.fulfill({ json: { code: 0, data: null } })
  })
  await login(page, '/file/files')
  const first = page.getByRole('row').filter({ hasText: 'probe-1' }).getByRole('button', { name: '删除', exact: true })
  const second = page.getByRole('row').filter({ hasText: 'probe-2' }).getByRole('button', { name: '删除', exact: true })
  await first.click(); await page.getByRole('alertdialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect.poll(() => releases.size).toBe(1); await expect(first).toBeDisabled(); await expect(second).toBeEnabled()
  await second.click(); await page.getByRole('alertdialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect.poll(() => releases.size).toBe(2); expect(new Set(keys).size).toBe(2)
  for (const release of releases.values()) release()
  await expect(first).toBeEnabled(); await expect(second).toBeEnabled()
})

test('an uncertain secret rotation is checked without returning a receipt as credentials or rotating again', async ({ page }) => {
  await fullApi(page, origin); let rotations = 0, key = ''
  await page.route(url => url.pathname === '/api/openapi/apps/app1/rotate', route => {
    rotations++; key = route.request().headers()['idempotency-key'] || ''
    return route.fulfill({ status: 503, json: { code: 503, message: 'Lost acknowledgement' } })
  })
  await page.route(url => url.pathname === '/api/common/requests/lookup', route => {
    expect(route.request().postDataJSON()).toEqual({ key })
    return route.fulfill({ json: { code: 0, data: { operationId: 'rotation-receipt', operation: 'openapi.rotate', state: 'SUCCEEDED', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:01Z' } } })
  })
  await login(page, '/openapi/apps')
  await page.getByRole('button', { name: '轮换密钥', exact: true }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.getByRole('button', { name: '查询原操作结果', exact: true })).toBeVisible()
  await expect(page.locator('#app-secret')).toHaveCount(0)
  await page.getByRole('button', { name: '查询原操作结果', exact: true }).click()
  await expect(page.getByText('原操作已完成，但首次返回的密钥无法再次读取。请核对应用状态，确需新密钥时再手动轮换。', { exact: true })).toBeVisible()
  await expect(page.locator('#app-secret')).toHaveCount(0); expect(rotations).toBe(1)
})

test('a completed application creation receipt is never displayed as the original credentials', async ({ page }) => {
  await fullApi(page, origin); let creates = 0
  await page.route(url => url.pathname === '/api/openapi/apps', route => {
    if (route.request().method() !== 'POST') return route.fallback()
    creates++
    return route.fulfill({ status: 409, json: { code: 409, error: 'common.request.completed', message: 'Completed', data: { operationId: 'creation-receipt', operation: 'openapi.create', state: 'SUCCEEDED', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:01Z' } } })
  })
  await login(page, '/openapi/apps'); await page.getByRole('button', { name: '创建应用', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('textbox', { name: '名称', exact: true }).fill('Completed creation')
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(dialog).toHaveCount(0); await expect(page.locator('#app-secret')).toHaveCount(0)
  await expect(page.getByText('原操作已完成，但首次返回的密钥无法再次读取。请核对应用状态，确需新密钥时再手动轮换。', { exact: true })).toBeVisible()
  expect(creates).toBe(1)
})


test('private-message sending preserves unknown content and only checks the original key', async ({ page }) => {
  await fullApi(page, origin); let writes = 0, key = ''
  await page.route(url => url.pathname === '/api/message/private', route => {
    if (route.request().method() !== 'POST') return route.fallback()
    writes++; key = route.request().headers()['idempotency-key'] || ''
    expect(route.request().postDataJSON()).toEqual({ recipientId: '102', content: 'Only send this once' })
    return route.fulfill({ status: 503, json: { code: 503, message: 'Lost acknowledgement' } })
  })
  await page.route(url => url.pathname === '/api/common/requests/lookup', route => {
    expect(route.request().postDataJSON()).toEqual({ key })
    return route.fulfill({ json: { code: 0, data: { operationId: 'private-receipt', operation: 'private.send', state: 'SUCCEEDED', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:01Z' } } })
  })
  await login(page, '/message/inbox?tab=private')
  await page.getByRole('button', { name: '写私信', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.locator('#private-recipient').click(); await page.getByRole('button', { name: '测试成员', exact: true }).click()
  await dialog.locator('#private-content').fill('Only send this once')
  await dialog.getByRole('button', { name: '发送', exact: true }).evaluate(button => { for (let i = 0; i < 20; i++) (button as HTMLButtonElement).click() })
  await expect(dialog.getByRole('button', { name: '查询原操作结果', exact: true })).toBeVisible()
  await expect(dialog.locator('#private-content')).toBeDisabled(); await expect(dialog.locator('#private-content')).toHaveValue('Only send this once')
  await dialog.getByRole('button', { name: '查询原操作结果', exact: true }).click()
  await expect(dialog).toHaveCount(0); expect(writes).toBe(1)
})
