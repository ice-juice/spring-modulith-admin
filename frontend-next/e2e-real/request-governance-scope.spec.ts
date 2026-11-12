import { expect, test, type APIRequestContext, type APIResponse, type Page } from '@playwright/test'
import { randomUUID } from 'node:crypto'

test.skip(process.env.SMA_E2E_SUITE !== 'security', 'Requires the isolated security runner')
const run = process.env.SMA_E2E_RUN_ID || ''
const origin = 'http://127.0.0.1:' + process.env.SMA_E2E_FRONTEND_PORT
const sessions = new Map<string, string>(), lastWrite = new Map<string, number>()
const grants = ['iam:post:view', 'iam:post:create', 'file:file:view', 'file:file:upload', 'file:file:delete']
const sample = { name: 'scope.txt', mimeType: 'text/plain', buffer: Buffer.from('S1d3 native upload verification\n') }
interface Receipt { uploadId: string; fileId: string; state: string; resumable: boolean }
interface LocalReceipt { operationId: string; state: string }
interface Member { username: string; token: string; roleId: string }

// Normal login only. Credentials/tokens remain in this worker and never become test artifacts.
async function login(request: APIRequestContext, username = 'admin', fresh = false) {
  if (!fresh && sessions.has(username)) return sessions.get(username)!
  const password = process.env.SMA_E2E_ADMIN_PASSWORD
  if (!password || !/^\d{13}$/.test(run)) throw new Error('Use run-real-e2e.mjs with the security suite')
  const response = await request.post('/api/auth/login', {
    headers: { Origin: origin }, data: { username, password, portal: 'admin', remember: false },
  })
  const body = await response.json()
  expect(response.status()).toBe(200); expect(body.code).toBe(0)
  if (typeof body.data?.token !== 'string' || !body.data.token) throw new Error('Expected a normally issued session')
  sessions.set(username, body.data.token)
  return body.data.token as string
}
async function pace(token: string) {
  // Respect the production 500ms write interval; no automatic retry or budget changes.
  const delay = 600 - (Date.now() - (lastWrite.get(token) || 0))
  if (delay > 0) await new Promise(resolve => setTimeout(resolve, delay))
  lastWrite.set(token, Date.now())
}
async function api(request: APIRequestContext, token: string, method: string, path: string, data?: unknown, key: string = randomUUID()) {
  if (method !== 'GET' && !path.endsWith('/lookup')) await pace(token)
  return request.fetch('/api' + path, { method, headers: { Authorization: 'Bearer ' + token, Origin: origin, 'Idempotency-Key': key }, data })
}
async function ok<T>(response: APIResponse): Promise<T> {
  const body = await response.json()
  expect(response.status()).toBe(200); expect(body.code, body.error || 'Success required').toBe(0)
  return body.data as T
}
async function denied(response: APIResponse, status: number, error: string) {
  expect(response.status()).toBe(status)
  const body = await response.json(); expect(body.error).toBe(error); expect(body.code).not.toBe(0)
}
async function member(request: APIRequestContext, admin: string, slot: string): Promise<Member> {
  const permissions = await ok<{ id: string; permissionKey: string }[]>(await api(request, admin, 'GET', '/iam/permissions'))
  const permissionIds = grants.map(key => {
    const permission = permissions.find(item => item.permissionKey === key)
    if (!permission) throw new Error('Missing test permission: ' + key)
    return permission.id
  })
  const username = 'scope_' + slot + '_' + run
  const role = await ok<{ id: string }>(await api(request, admin, 'POST', '/iam/roles', {
    name: username, code: username, parentId: null, sort: 0, enabled: true, dataScope: 'SELF', departmentIds: [], description: '',
  }))
  await ok(await api(request, admin, 'PUT', '/iam/roles/' + role.id + '/permissions', { permissionIds }))
  const user = await ok<{ superAdmin: boolean }>(await api(request, admin, 'POST', '/iam/users', {
    user: { username, name: username, departmentId: null, email: '', mobile: '', gender: 'UNSPECIFIED', enabled: true,
      roleIds: [role.id], postIds: [], locale: 'zh-CN', timeZone: 'Asia/Shanghai' }, password: process.env.SMA_E2E_ADMIN_PASSWORD,
  }))
  expect(user.superAdmin).toBe(false)
  return { username, token: await login(request, username), roleId: role.id }
}
async function local(request: APIRequestContext, token: string, slot: string, key: string = randomUUID()) {
  const row = await ok<{ id: string }>(await api(request, token, 'POST', '/iam/posts', {
    name: 'S1d3 ' + slot, code: 'S1D3_' + slot + '_' + run, sort: 0, enabled: true, description: '',
  }, key))
  const receipt = await ok<LocalReceipt>(await api(request, token, 'POST', '/common/requests/lookup', { key }))
  expect(receipt.state).toBe('SUCCEEDED')
  return { key, id: row.id, operationId: receipt.operationId }
}
async function upload(request: APIRequestContext, token: string, key: string = randomUUID(), file = sample) {
  await pace(token)
  return request.post('/api/file/files', {
    headers: { Authorization: 'Bearer ' + token, Origin: origin, 'Idempotency-Key': key }, multipart: { file },
  })
}
async function uploadReceipt(request: APIRequestContext, token: string, key: string) {
  const receipt = await ok<Receipt>(await api(request, token, 'POST', '/file/uploads/lookup', { key }))
  expect(receipt.state).toBe('SUCCEEDED'); expect(receipt.resumable).toBe(false)
  expect(Object.keys(receipt).sort()).toEqual(['createdAt', 'fileId', 'resumable', 'state', 'updatedAt', 'uploadId'])
  return receipt
}
async function openFiles(page: Page, token: string) {
  await page.addInitScript(({ token, origin }) => {
    if (window.top === window && location.origin === origin) {
      sessionStorage.setItem('sma.access-token', token); sessionStorage.setItem('sma.access-token.detached', '1')
    }
  }, { token, origin })
  await page.goto('/file/files')
  await expect(page.locator('input[type=file]')).toHaveCount(1)
}

test('LOCAL receipts remain actor-owned even for a super administrator; identical keys are independent per actor', async ({ request }) => {
  const admin = await login(request), owner = await member(request, admin, 'local'), other = await member(request, admin, 'peer')
  const mine = await local(request, owner.token, 'OWNER')
  for (const token of [admin, other.token]) {
    await denied(await api(request, token, 'POST', '/common/requests/lookup', { key: mine.key }), 404, 'common.error.not-found')
    await denied(await api(request, token, 'GET', '/common/requests/' + mine.operationId), 404, 'common.error.not-found')
  }
  const theirs = await local(request, other.token, 'OTHER', mine.key)
  expect(theirs.id).not.toBe(mine.id); expect(theirs.operationId).not.toBe(mine.operationId)
  expect(await ok<LocalReceipt>(await api(request, owner.token, 'GET', '/common/requests/' + mine.operationId)))
    .toMatchObject({ operationId: mine.operationId, state: 'SUCCEEDED' })
})

test('native upload receipts require their creator while file reads retain the independent current ACL', async ({ request }) => {
  const admin = await login(request), owner = await member(request, admin, 'upload'), other = await member(request, admin, 'reader')
  const key = randomUUID(), response = await upload(request, owner.token, key), file = await ok<{ id: string }>(response)
  expect(response.headers()['x-request-policy']).toBe('NATIVE'); expect(response.headers()['x-operation-id']).toBeUndefined()
  const receipt = await uploadReceipt(request, owner.token, key)
  expect(receipt.fileId).toBe(file.id); expect(response.headers()['x-upload-id']).toBe(receipt.uploadId)
  for (const token of [admin, other.token]) {
    await denied(await api(request, token, 'POST', '/file/uploads/lookup', { key }), 404, 'common.error.not-found')
    await denied(await api(request, token, 'GET', '/file/uploads/' + receipt.uploadId), 404, 'common.error.not-found')
  }
  await denied(await api(request, other.token, 'GET', '/file/files/' + file.id), 403, 'common.error.forbidden')
  await denied(await api(request, other.token, 'GET', '/file/files/' + file.id + '/content'), 403, 'common.error.forbidden')
  // Authorized administrator access to the business object does not grant access to another actor's receipt.
  expect(await ok<{ id: string }>(await api(request, admin, 'GET', '/file/files/' + file.id))).toMatchObject({ id: file.id })
  const content = await api(request, owner.token, 'GET', '/file/files/' + file.id + '/content')
  expect(content.status()).toBe(200); expect(await content.body()).toEqual(sample.buffer)
  await denied(await upload(request, owner.token, key, { ...sample, name: 'changed.txt' }), 409, 'common.request.conflict')
  expect((await uploadReceipt(request, owner.token, key)).fileId).toBe(file.id)
})

test('revoking grants prevents old LOCAL and upload receipts from bypassing current permissions after a new login', async ({ request }) => {
  const admin = await login(request), owner = await member(request, admin, 'revoked')
  const mine = await local(request, owner.token, 'REVOKED'), key = randomUUID()
  await ok(await upload(request, owner.token, key))
  const receipt = await uploadReceipt(request, owner.token, key)
  await ok(await api(request, admin, 'PUT', '/iam/roles/' + owner.roleId + '/permissions', { permissionIds: [] }))
  const fresh = await login(request, owner.username, true)
  const me = await ok<{ permissions: string[] }>(await api(request, fresh, 'GET', '/auth/me'))
  for (const permission of grants) expect(me.permissions).not.toContain(permission)
  await denied(await api(request, fresh, 'POST', '/common/requests/lookup', { key: mine.key }), 403, 'common.error.forbidden')
  await denied(await api(request, fresh, 'GET', '/common/requests/' + mine.operationId), 403, 'common.error.forbidden')
  await denied(await api(request, fresh, 'POST', '/file/uploads/lookup', { key }), 403, 'common.error.forbidden')
  await denied(await api(request, fresh, 'GET', '/file/uploads/' + receipt.uploadId), 403, 'common.error.forbidden')
})

test('committed upload response loss recovers by its native receipt across refresh; deleted files cannot be resurrected by replay', async ({ page, request }) => {
  const admin = await login(request)
  await openFiles(page, admin)
  let writes = 0, key = '', fileId = '', uploadId = ''
  await page.route(url => url.pathname === '/api/file/files', async route => {
    if (route.request().method() !== 'POST') return route.continue()
    writes++; key = route.request().headers()['idempotency-key']!
    const response = await route.fetch(), body = await response.json()
    expect(response.status()).toBe(200); expect(body.code).toBe(0)
    expect(response.headers()['x-request-policy']).toBe('NATIVE'); expect(response.headers()['x-operation-id']).toBeUndefined()
    fileId = body.data.id; uploadId = response.headers()['x-upload-id']!
    await route.abort('failed') // Real committed upload; only its response is discarded.
  })
  await page.locator('input[type=file]').setInputFiles(sample)
  await expect(page.getByRole('button', { name: '查询原操作结果', exact: true })).toBeVisible()
  expect(writes).toBe(1); expect(uploadId).toMatch(/^[a-f0-9-]{36}$/)
  await page.reload()
  await page.getByRole('button', { name: '操作状态', exact: true }).click()
  const panel = page.getByRole('dialog', { name: '操作状态', exact: true })
  await panel.getByRole('button', { name: '核对此操作', exact: true }).click()
  await expect(panel.getByRole('status')).toContainText('原操作已确认成功')
  expect(writes).toBe(1)
  expect(await uploadReceipt(request, admin, key)).toMatchObject({ uploadId, fileId })
  const before = await ok<{ items: { id: string }[] }>(await api(request, admin, 'GET', '/file/files?keyword=scope.txt&size=100'))
  expect(before.items.filter(row => row.id === fileId)).toHaveLength(1)
  await ok(await api(request, admin, 'DELETE', '/file/files/' + fileId))
  expect(await uploadReceipt(request, admin, key)).toMatchObject({ uploadId, fileId })
  await denied(await api(request, admin, 'GET', '/file/files/' + fileId), 200, 'common.error.data-not-found')
  await denied(await api(request, admin, 'GET', '/file/files/' + fileId + '/content'), 404, 'common.error.not-found')
  // Deliberate protocol replay with original bytes; the production UI never reconstructs them after refresh.
  await denied(await upload(request, admin, key), 200, 'common.error.data-not-found')
  const after = await ok<{ items: { id: string }[] }>(await api(request, admin, 'GET', '/file/files?keyword=scope.txt&size=100'))
  expect(after.items).toHaveLength(before.items.length - 1); expect(after.items.some(row => row.id === fileId)).toBe(false)
  await denied(await api(request, admin, 'GET', '/file/files/' + fileId + '/content'), 404, 'common.error.not-found')
})
