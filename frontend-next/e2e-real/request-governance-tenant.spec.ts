import { expect, test, type APIRequestContext, type APIResponse } from '@playwright/test'
import { randomBytes, randomUUID } from 'node:crypto'

test.skip(process.env.SMA_E2E_SUITE !== 'security' || process.env.SMA_E2E_SECURITY_TENANTS !== '1', 'Requires explicit isolated security tenant fixtures')
test.setTimeout(180000)
const backend = process.env.SMA_NEXT_BACKEND_URL || ''
const origin = 'http://127.0.0.1:' + process.env.SMA_E2E_FRONTEND_PORT
type Side = 'a' | 'b'
interface Actor { side: Side; token: string; host: string }
interface View { id: string; code: string; name: string; version: string; [key: string]: unknown }
const actors = new Map<Side, Actor>(), lastWrite = new Map<Side, number>()
const required = ['iam:post:view', 'iam:post:create', 'iam:post:update', 'file:file:upload', 'identity:realm:view', 'identity:realm:manage',
  'identity:client:view', 'identity:client:manage', 'mail:profile:view', 'mail:profile:manage', 'file:storage:view', 'file:storage:manage']

async function success<T>(response: APIResponse): Promise<T> {
  const body = await response.json()
  expect(response.status()).toBe(200); expect(body.code, body.error || 'Success required').toBe(0)
  return body.data as T
}
async function rejected(response: APIResponse, status: number, error: string) {
  expect(response.status()).toBe(status)
  const body = await response.json(); expect(body.code).not.toBe(0); expect(body.error).toBe(error)
}
async function pace(side: Side) {
  const delay = 600 - (Date.now() - (lastWrite.get(side) || 0))
  if (delay > 0) await new Promise(resolve => setTimeout(resolve, delay))
  lastWrite.set(side, Date.now())
}
async function call(request: APIRequestContext, actor: Actor, method: string, path: string, data?: unknown, key: string = randomUUID()) {
  if (method !== 'GET' && !path.endsWith('/lookup')) await pace(actor.side)
  return request.fetch(backend + '/api' + path, { method, headers: {
    Host: actor.host, Origin: origin, Authorization: 'Bearer ' + actor.token, 'Idempotency-Key': key,
  }, data })
}
async function actor(request: APIRequestContext, side: Side): Promise<Actor> {
  if (actors.has(side)) return actors.get(side)!
  if (!process.env.SMA_E2E_ADMIN_PASSWORD || new URL(backend).hostname !== '127.0.0.1'
    || process.env.SMA_E2E_TENANT_HOST !== 'tenant-security.test') throw new Error('Invalid isolated test environment')
  const host = side === 'a' ? new URL(backend).host : process.env.SMA_E2E_TENANT_HOST
  const entry = await success<{ entry: { tenantId: string; portal: string } }>(await request.get(backend + '/api/tenant/entry', { headers: { Host: host } }))
  expect(entry.entry).toMatchObject({ tenantId: side === 'a' ? '1' : '2', portal: 'admin' })
  const response = await request.post(backend + '/api/auth/login', { headers: { Host: host, Origin: origin }, data: {
    username: side === 'a' ? 'admin' : process.env.SMA_E2E_TENANT_USERNAME,
    password: process.env.SMA_E2E_ADMIN_PASSWORD, portal: 'admin', remember: false,
  } })
  const body = await success<{ token: string }>(response)
  if (!body.token || typeof body.token !== 'string') throw new Error('Expected normally issued session')
  const value = { side, host, token: body.token }
  const me = await success<{ superAdmin: boolean; permissions: string[] }>(await call(request, value, 'GET', '/auth/me'))
  expect(me.superAdmin).toBe(true)
  for (const permission of required) expect(me.permissions).toContain(permission)
  actors.set(side, value); return value
}
async function tenants(request: APIRequestContext): Promise<[Actor, Actor]> { return [await actor(request, 'a'), await actor(request, 'b')] }
async function upload(request: APIRequestContext, owner: Actor, key: string) {
  await pace(owner.side)
  return request.post(backend + '/api/file/files', { headers: {
    Host: owner.host, Origin: origin, Authorization: 'Bearer ' + owner.token, 'Idempotency-Key': key,
  }, multipart: { file: { name: 'tenant.txt', mimeType: 'text/plain', buffer: Buffer.from('tenant scope fixture') } } })
}

test('real Host binds tenants and rejects foreign Bearers; same business keys and native receipts remain isolated', async ({ request }) => {
  const [a, b] = await tenants(request)
  for (const [owner, foreign] of [[a, b], [b, a]] as const) {
    await rejected(await call(request, { ...owner, host: foreign.host }, 'GET', '/auth/me'), 401, 'common.error.unauthorized')
  }
  const key = randomUUID(), body = { name: 'Tenant scoped post', code: 'S1D3_TENANT', sort: 0, enabled: true, description: '' }
  const first = await success<View>(await call(request, a, 'POST', '/iam/posts', body, key))
  const receipt = await success<{ operationId: string; state: string }>(await call(request, a, 'POST', '/common/requests/lookup', { key }))
  expect(receipt.state).toBe('SUCCEEDED')
  await rejected(await call(request, b, 'POST', '/common/requests/lookup', { key }), 404, 'common.error.not-found')
  await rejected(await call(request, b, 'GET', '/common/requests/' + receipt.operationId), 404, 'common.error.not-found')
  await rejected(await call(request, b, 'PUT', '/iam/posts/' + first.id, body), 200, 'common.error.data-not-found')
  const second = await success<View>(await call(request, b, 'POST', '/iam/posts', body, key))
  expect(second.id).not.toBe(first.id)
  for (const [owner, expected] of [[a, first], [b, second]] as const) {
    const rows = await success<{ items: View[] }>(await call(request, owner, 'GET', '/iam/posts?keyword=S1D3_TENANT'))
    expect(rows.items.map(row => row.id)).toEqual([expected.id])
  }
  const uploadKey = randomUUID(), file = await success<View>(await upload(request, a, uploadKey))
  const native = await success<{ uploadId: string; fileId: string; state: string }>(await call(request, a, 'POST', '/file/uploads/lookup', { key: uploadKey }))
  expect(native).toMatchObject({ state: 'SUCCEEDED', fileId: file.id })
  await rejected(await call(request, b, 'POST', '/file/uploads/lookup', { key: uploadKey }), 404, 'common.error.not-found')
  await rejected(await call(request, b, 'GET', '/file/uploads/' + native.uploadId), 404, 'common.error.not-found')
  await rejected(await call(request, b, 'GET', '/file/files/' + file.id), 200, 'common.error.data-not-found')
  await rejected(await call(request, b, 'GET', '/file/files/' + file.id + '/content'), 404, 'common.error.not-found')
  const ownFile = await success<View>(await upload(request, b, uploadKey)); expect(ownFile.id).not.toBe(file.id)
  const ownReceipt = await success<{ uploadId: string }>(await call(request, b, 'POST', '/file/uploads/lookup', { key: uploadKey }))
  expect(ownReceipt.uploadId).not.toBe(native.uploadId)
  expect(await success<{ fileId: string }>(await call(request, a, 'POST', '/file/uploads/lookup', { key: uploadKey }))).toMatchObject({ fileId: file.id })
})

// No provider requests: all profiles are disabled. Distinct random dummy credentials are kept in memory only.
async function configurationPair(request: APIRequestContext, endpoint: string, notFound: string,
  input: (owner: Actor) => Promise<Record<string, unknown>> | Record<string, unknown>, configured: string | undefined,
  secretFields: string[], uniqueField?: string) {
  const [a, b] = await tenants(request), rows: View[] = [], inputs: Record<string, unknown>[] = []
  for (const owner of [a, b]) {
    const configuration = await input(owner); inputs.push(configuration)
    const row = await success<View>(await call(request, owner, 'POST', endpoint, configuration)); rows.push(row)
    if (configured) expect(row[configured]).toBe(true)
    for (const key of secretFields) {
      expect(Object.hasOwn(row, key)).toBe(false)
      if (typeof configuration[key] === 'string') expect(JSON.stringify(row).includes(configuration[key] as string)).toBe(false)
    }
  }
  const first = rows[0]!, second = rows[1]!
  expect(first.id).not.toBe(second.id); expect(first.code).toBe(second.code)
  if (uniqueField) expect(first[uniqueField]).not.toBe(second[uniqueField])
  for (const [owner, foreign, own, ownInput] of [[a, second, first, inputs[0]!], [b, first, second, inputs[1]!]] as const) {
    const page = await success<{ items: View[] }>(await call(request, owner, 'GET', endpoint + '?size=100'))
    expect(page.items.some(row => row.id === own.id)).toBe(true); expect(page.items.some(row => row.id === foreign.id)).toBe(false)
    await rejected(await call(request, owner, 'GET', endpoint + '/' + foreign.id), 200, notFound)
    // Valid own configuration eliminates malformed-input rejection as a false positive.
    await rejected(await call(request, owner, 'PUT', endpoint + '/' + foreign.id, { expectedVersion: foreign.version, configuration: ownInput }), 200, notFound)
    expect(await success<View>(await call(request, owner, 'GET', endpoint + '/' + own.id))).toEqual(own)
  }
  const edit: Record<string, unknown> = { ...inputs[0]!, name: 'Tenant A changed only' }
  for (const field of secretFields) edit[field] = null // Blank secrets retain the tenant's encrypted revision.
  const updated = await success<View>(await call(request, a, 'PUT', endpoint + '/' + first.id, { expectedVersion: first.version, configuration: edit }))
  expect(updated.name).toBe('Tenant A changed only'); expect(updated.version).not.toBe(first.version)
  if (configured) expect(updated[configured]).toBe(true)
  expect(await success<View>(await call(request, b, 'GET', endpoint + '/' + second.id))).toEqual(second)
}

test('Google client IDs, secrets and realm references are isolated between equally authorized tenants', async ({ request }) => {
  const [a, b] = await tenants(request), realms = new Map<Side, View>()
  for (const owner of [a, b]) realms.set(owner.side, await success<View>(await call(request, owner, 'POST', '/identity/realms', {
    code: 'scope-realm', name: 'Scope realm ' + owner.side, enabled: true, registrationEnabled: false, passwordEnabled: false,
  })))
  const input = (owner: Actor) => ({ realmId: realms.get(owner.side)!.id, code: 'scope-web', name: 'Google ' + owner.side,
    application: 'scope', platform: 'WEB', enabled: false, googleEnabled: true,
    googleClientId: 'scope-' + owner.side + '.apps.googleusercontent.com', googleSecret: randomBytes(24).toString('hex'),
    callbackUris: ['https://' + owner.side + '.example.test/oauth/callback'], allowedOrigins: ['https://' + owner.side + '.example.test'],
    accessTokenSeconds: 900, refreshTokenSeconds: 2592000 })
  await configurationPair(request, '/identity/clients', 'identity.error.configuration-not-found', input, 'secretConfigured', ['googleSecret'], 'googleClientId')
  await rejected(await call(request, a, 'POST', '/identity/clients', { ...input(a), code: 'foreign-realm', realmId: realms.get('b')!.id }), 200, 'identity.error.configuration-not-found')
})

test('mail connections and write-only passwords remain tenant-owned with no SMTP delivery', async ({ request }) => {
  await configurationPair(request, '/mail/profiles', 'mail.error.profile-not-found', owner => ({
    code: 'scope-mail', name: 'Mail ' + owner.side, host: 'smtp.' + owner.side + '.example.test', port: 465, tlsMode: 'TLS',
    username: 'mail-' + owner.side, password: randomBytes(24).toString('hex'), fromAddress: 'sender@' + owner.side + '.example.test',
    fromName: 'Scope', applications: ['scope'], dailyQuota: 100, perMinuteLimit: 10, enabled: false,
  }), 'passwordConfigured', ['password'], 'host')
})

for (const provider of ['LOCAL', 'OSS', 'S3', 'R2'] as const) {
  test(provider + ' storage configuration, immutable revisions and credentials remain tenant-owned without provider I/O', async ({ request }) => {
    await configurationPair(request, '/file/storage-profiles', 'file.error.storage-profile-not-found', owner => ({
      code: 'scope-' + provider.toLowerCase(), name: provider + ' ' + owner.side, provider,
      localRoot: provider === 'LOCAL' ? 'default' : null,
      region: provider === 'S3' ? (owner.side === 'a' ? 'us-east-1' : 'us-west-2') : provider === 'OSS' ? 'cn-hangzhou' : provider === 'R2' ? 'auto' : null,
      endpoint: provider === 'R2' ? 'https://' + owner.side.repeat(32) + '.r2.cloudflarestorage.com' : null,
      bucket: provider === 'LOCAL' ? null : 'scope-' + owner.side + '-bucket', prefix: 'scope/' + owner.side,
      accessKeyId: provider === 'LOCAL' ? null : randomBytes(12).toString('hex'),
      secretAccessKey: provider === 'LOCAL' ? null : randomBytes(24).toString('hex'),
      applications: ['scope'], uploadsEnabled: false, readsEnabled: false,
    }), provider === 'LOCAL' ? undefined : 'credentialsConfigured', ['accessKeyId', 'secretAccessKey', 'sessionToken'], 'prefix')
  })
}
