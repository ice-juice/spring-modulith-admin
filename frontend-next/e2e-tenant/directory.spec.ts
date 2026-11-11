import { expect, test, type Page } from '@playwright/test'
import { fullApi } from '../e2e/full-api'
import { login } from '../e2e/mock-api'
import type { DomainCheck, DomainRecord, TenantRecord } from '../src/features/tenant/directory'

const epoch = '9007199254740993123'
const fresh = () => new Date(Date.now() + 86400000).toISOString()
async function fixture(page: Page, origin: string, readonly = false) {
  const api = await fullApi(page, origin)
  api.me.permissions.push('tenant:tenant:view', 'tenant:domain:view', ...(readonly ? [] : ['tenant:tenant:create', 'tenant:tenant:update', 'tenant:domain:create', 'tenant:domain:update', 'tenant:domain:verify']))
  const tenants: TenantRecord[] = [{ id: epoch, code: 'alpha', name: '甲租户', status: 'ACTIVE', securityGeneration: epoch }, { id: '9007199254740993124', code: 'beta', name: '乙租户', status: 'PROVISIONING', securityGeneration: '1' }]
  const initial: DomainRecord = { id: '9007199254740993130', tenantId: epoch, hostname: 'alpha.example.test', portal: 'admin', status: 'PENDING', primaryDomain: false, verificationStatus: 'PENDING', tlsStatus: 'PENDING', provenance: 'REQUESTED', version: epoch, dnsStatus: 'PENDING', verifiedAt: null, tlsValidUntil: null, challengeRecord: null, challengeValue: null, challengeExpiresAt: null, lastCheckId: null, checkStatus: 'IDLE', checkFailure: 'NONE', checkLeaseUntil: null, checkCompletedAt: null, activationReady: false }
  const domains: DomainRecord[] = [initial]
  const receipts = new Map<string, DomainCheck>(), localKeys = new Set<string>()
  await page.route('**/api/common/requests/lookup', route => {
    const key = route.request().postDataJSON().key; expect(localKeys.has(key)).toBe(true)
    return route.fulfill({ json: { code: 0, data: { operationId: 'local-' + key, operation: 'directory.write', state: 'SUCCEEDED', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:01Z' } } })
  })
  const state = { creates: 0, checks: 0, enables: 0, challenges: 0, loseCreate: false, loseCheck: false, loseChallenge: false, conflict: false, seenVersions: [] as string[], held: false, release: undefined as (() => void) | undefined }
  await page.route(url => /^\/api\/tenant\/(tenants|domains)(\/|$)/.test(url.pathname), async route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname, method = request.method()
    const ok = (data: unknown) => route.fulfill({ json: { code: 0, data } })
    const list = () => tenants.filter(row => !url.searchParams.get('keyword') || (row.code + row.name).includes(url.searchParams.get('keyword')!))
    if (path.endsWith('/tenant-options') || path === '/api/tenant/tenants' && method === 'GET') return ok({ items: list(), total: String(list().length), page: '1', size: '20' })
    if (path.endsWith('/verification-settings')) return ok({ configured: true, ingressAddresses: ['203.0.113.10'] })
    if (path === '/api/tenant/tenants' && method === 'POST') {
      state.creates++; const input = request.postDataJSON()
      tenants.push({ ...input, id: '9007199254740993129', status: 'PROVISIONING', securityGeneration: '1' })
      localKeys.add(request.headers()['idempotency-key']!)
      if (state.loseCreate) { state.loseCreate = false; return route.abort('failed') }
      return ok(tenants.at(-1))
    }
    if (path.endsWith('/status')) {
      const input = request.postDataJSON(), row = tenants.find(item => item.id === path.split('/').at(-2))!
      state.seenVersions.push(input.expectedGeneration)
      if (state.conflict) return route.fulfill({ json: { code: 400, error: 'tenant.error.stale', message: '远端版本已变化' } })
      row.status = input.status; row.securityGeneration = String(BigInt(row.securityGeneration) + 1n); return ok(row)
    }
    if (path === '/api/tenant/domains' && method === 'GET') return ok(domains.filter(item => item.tenantId === url.searchParams.get('tenantId')))
    if (path === '/api/tenant/domains' && method === 'POST') { const input = request.postDataJSON(); const row = { ...initial, ...input, id: '9007199254740993140' }; domains.push(row); return ok(row) }
    const row = domains.find(item => item.id === path.split('/')[4])!
    if (path.includes('/checks/') && method === 'GET') return ok(receipts.get(path.split('/').at(-1)!))
    if (method === 'GET') return ok(row)
    const input = request.postDataJSON(); state.seenVersions.push(input.expectedVersion)
    if (input.expectedVersion !== row.version) return route.fulfill({ json: { code: 400, error: 'tenant.error.stale', message: '远端版本已变化' } })
    row.version = String(BigInt(row.version) + 1n)
    if (path.endsWith('/challenges')) {
      state.challenges++; row.challengeRecord = '_sma-tenant.' + row.hostname; row.challengeValue = 'sma-tenant-v1=' + 'a'.repeat(43); row.challengeExpiresAt = fresh()
      localKeys.add(request.headers()['idempotency-key']!)
      if (state.loseChallenge) { state.loseChallenge = false; return route.abort('failed') }
      return ok(row)
    }
    if (path.endsWith('/checks')) {
      expect(request.headers()['idempotency-key']).toBe(input.operationId)
      state.checks++; row.checkStatus = 'SUCCEEDED'; row.verificationStatus = 'VERIFIED'; row.dnsStatus = 'READY'; row.tlsStatus = 'READY'; row.activationReady = true; row.tlsValidUntil = fresh(); row.verifiedAt = new Date().toISOString()
      const result: DomainCheck = { id: '9007199254740993150', domainId: row.id, operationId: input.operationId, status: 'SUCCEEDED', failureCode: 'NONE', ownershipVerified: true, dnsReady: true, tlsReady: true, tlsValidUntil: row.tlsValidUntil, addresses: ['203.0.113.10'], fingerprints: ['a'.repeat(64)], createdAt: row.verifiedAt, leaseUntil: fresh(), completedAt: row.verifiedAt }
      receipts.set(input.operationId, result)
      if (state.held) await new Promise<void>(resolve => { state.release = resolve })
      if (state.loseCheck) { state.loseCheck = false; return route.abort('failed') }
      return ok(result)
    }
    if (path.endsWith('/enabled')) { state.enables++; row.status = input.enabled ? 'ACTIVE' : 'DISABLED'; return ok(row) }
    if (path.endsWith('/primary')) { domains.forEach(item => { item.primaryDomain = item.id === row.id }); return ok(row) }
    return route.fulfill({ status: 500 })
  })
  return { state, api, tenants, domains }
}
async function confirm(page: Page) { await page.getByRole('alertdialog').getByRole('button', { name: '确认', exact: true }).click() }
test.beforeEach(({}, info) => { test.skip(info.project.name !== 'next', 'Tenant directory management belongs to frontend-next.') })

test('tenant registration reconciles a lost response without duplicating and preserves a conflicted state form', async ({ page, baseURL }) => {
  const { state } = await fixture(page, baseURL!); state.loseCreate = true
  await login(page, '/tenant/tenants')
  await expect(page.getByText('甲租户', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '登记租户', exact: true }).click()
  await page.getByLabel('租户编码', { exact: true }).fill('new-tenant')
  await page.getByLabel('租户名称', { exact: true }).fill('新租户')
  await page.getByRole('dialog').getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByText(/操作结果尚未确认/)).toBeVisible()
  await page.getByRole('button', { name: '核对结果', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(state.creates).toBe(1)
  await expect(page.getByText('新租户', { exact: true })).toBeVisible()
  await page.locator('[data-row-id="' + epoch + '"]').getByRole('button', { name: '修改状态' }).click()
  await page.getByLabel('状态', { exact: true }).selectOption('MAINTENANCE')
  state.conflict = true
  await page.getByRole('dialog').getByRole('button', { name: '保存', exact: true }).click(); await confirm(page)
  await expect(page.getByText('远端版本已变化', { exact: true })).toBeVisible()
  await expect(page.getByLabel('状态', { exact: true })).toHaveValue('MAINTENANCE')
  expect(state.seenVersions).toEqual([epoch])
})

test('domain verification reconciles its original receipt and requires explicit activation', async ({ page, baseURL }, info) => {
  const { state } = await fixture(page, baseURL!); state.loseCheck = true; state.loseChallenge = true
  await login(page, '/tenant/domains')
  await page.getByRole('button', { name: '管理域名', exact: true }).click()
  await expect(page.getByRole('button', { name: '启用域名', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '生成挑战', exact: true }).click(); await confirm(page)
  await expect(page.getByText(/操作结果尚未确认/)).toBeVisible()
  await page.getByRole('button', { name: '核对结果', exact: true }).click()
  await expect(page.getByText('_sma-tenant.alpha.example.test', { exact: true })).toBeVisible()
  expect(state.challenges).toBe(1)
  await page.getByRole('button', { name: '运行域名检查', exact: true }).click(); await confirm(page)
  await expect(page.getByText(/操作结果尚未确认/)).toBeVisible()
  await expect(page.getByRole('button', { name: '运行域名检查', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '核对结果', exact: true }).click()
  await expect(page.getByRole('button', { name: '启用域名', exact: true })).toBeEnabled()
  expect(state.checks).toBe(1); expect(state.enables).toBe(0)
  await page.getByRole('button', { name: '启用域名', exact: true }).click(); await confirm(page)
  await expect(page.getByRole('button', { name: '设为主域名', exact: true })).toBeEnabled()
  await page.getByRole('button', { name: '设为主域名', exact: true }).click(); await confirm(page)
  await expect(page.getByRole('button', { name: '设为主域名', exact: true })).toBeDisabled()
  expect(state.enables).toBe(1)
  expect(state.seenVersions.every(value => typeof value === 'string' && BigInt(value) >= BigInt(epoch))).toBe(true)
  await page.screenshot({ path: info.outputPath('domain-management.png') })
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.getByRole('dialog')).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: info.outputPath('domain-management-mobile.png') })
})

test('view-only directory does not offer mutations or run verification', async ({ page, baseURL }) => {
  const { state } = await fixture(page, baseURL!, true)
  await login(page, '/tenant/domains')
  await expect(page.getByRole('button', { name: '登记域名', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '管理域名', exact: true }).click()
  for (const name of ['生成挑战', '运行域名检查', '启用域名', '停用域名', '设为主域名']) await expect(page.getByRole('button', { name, exact: true })).toHaveCount(0)
  expect(state.checks + state.challenges + state.enables).toBe(0)
})

test('a check response arriving after logout cannot restore protected domain data', async ({ page, baseURL }) => {
  const { state } = await fixture(page, baseURL!); state.held = true
  await login(page, '/tenant/domains')
  await page.getByRole('button', { name: '管理域名', exact: true }).click()
  await page.getByRole('button', { name: '生成挑战', exact: true }).click(); await confirm(page)
  await expect(page.getByRole('button', { name: '运行域名检查', exact: true })).toBeEnabled()
  await page.getByRole('button', { name: '运行域名检查', exact: true }).click(); await confirm(page)
  await expect.poll(() => state.release !== undefined).toBe(true)
  await page.evaluate(() => window.dispatchEvent(new Event('sma:unauthorized')))
  state.release!()
  await page.waitForURL(url => url.pathname === '/login')
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeEnabled()
  await expect(page.getByText('alpha.example.test', { exact: true })).toHaveCount(0)
  expect(state.checks).toBe(1); expect(state.enables).toBe(0)
})

test('locking hides an in-flight check and unlocking preserves its original pending receipt', async ({ page, baseURL }) => {
  const { state } = await fixture(page, baseURL!); state.held = true
  await login(page, '/tenant/domains')
  await page.getByRole('button', { name: '管理域名', exact: true }).click()
  await page.getByRole('button', { name: '生成挑战', exact: true }).click(); await confirm(page)
  await expect(page.getByRole('button', { name: '运行域名检查', exact: true })).toBeEnabled()
  await page.getByRole('button', { name: '运行域名检查', exact: true }).click(); await confirm(page)
  await expect.poll(() => state.release !== undefined).toBe(true)
  await page.evaluate(() => window.dispatchEvent(new Event('sma:locked')))
  await expect(page.getByText('_sma-tenant.alpha.example.test', { exact: true })).not.toBeVisible()
  state.release!()
  await page.locator('#unlock-password').fill('IsolatedTestPassword!2026')
  await page.getByRole('button', { name: '解锁', exact: true }).click()
  await expect(page.getByText(/操作结果尚未确认/)).toBeVisible()
  await page.getByRole('button', { name: '核对结果', exact: true }).click()
  await expect(page.getByRole('button', { name: '启用域名', exact: true })).toBeEnabled()
  expect(state.checks).toBe(1)
})

test('directory appearance supports English, dark mode and keyboard access', async ({ page, baseURL }, info) => {
  await fixture(page, baseURL!, true)
  await login(page, '/tenant/domains')
  await expect(page.getByRole('button', { name: '管理域名', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '主题', exact: true }).click()
  await page.getByRole('menuitem', { name: '深色', exact: true }).click()
  await page.getByRole('button', { name: '语言与时区', exact: true }).click()
  await page.getByRole('menuitem', { name: 'English', exact: true }).click()
  const manage = page.getByRole('button', { name: 'Manage domain', exact: true })
  await manage.focus(); await page.keyboard.press('Enter')
  await expect(page.getByRole('dialog').getByText('DNS TXT challenge', { exact: true })).toBeVisible()
  await expect(page.locator('html')).toHaveClass(/dark/)
  await page.screenshot({ path: info.outputPath('domain-dark-english.png') })
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(manage).toBeFocused()
})
