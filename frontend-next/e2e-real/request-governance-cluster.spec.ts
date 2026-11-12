import { expect, test, type Page, type Browser } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { readFileSync, writeFileSync, existsSync } from 'node:fs'
import path from 'node:path'

const peerUrl = process.env.SMA_E2E_PEER_BACKEND_URL || ''
const phase = process.env.SMA_E2E_RUN_PHASE, run = process.env.SMA_E2E_RUN_ID || ''
const work = process.env.SMA_E2E_RUN_WORK || ''
// Reuse normally issued sessions within this worker, never a storage-state file.
// The after-restart phase has a new process and authenticates again.
const sessions = new Map<boolean, string>()
test.skip(process.env.SMA_E2E_SUITE !== 'security' || !peerUrl, 'Requires the security runner with two isolated JVMs')
interface Evidence { kind: 'peer' | 'pair' | 'concurrent'; key: string; id: string; operationId: string }
const evidenceFile = path.join(work, 'request-evidence.json')
const input = (kind: Evidence['kind'], name = 'S1d2 ' + kind) => ({
  name, code: 'S1D2_' + kind.toUpperCase() + '_' + run, sort: 0, enabled: true, description: '',
})
function evidence(): Evidence[] {
  const raw = readFileSync(evidenceFile, 'utf8'); expect(raw.length).toBeLessThan(8192)
  const value = JSON.parse(raw); expect(value.schema).toBe(1); expect(value.run).toBe(run)
  expect(value.rows.length).toBeLessThanOrEqual(3)
  for (const row of value.rows) {
    expect(['peer', 'pair', 'concurrent']).toContain(row.kind); expect(row.id).toMatch(/^\d{1,20}$/)
    expect(row.key).toMatch(/^[a-f0-9-]{36}$/); expect(row.operationId).toMatch(/^[a-f0-9-]{36}$/)
    expect(Object.keys(row).sort()).toEqual(['id', 'key', 'kind', 'operationId'])
  }
  return value.rows
}
function remember(row: Evidence) {
  const rows = existsSync(evidenceFile) ? evidence() : []
  expect(rows.some(previous => previous.kind === row.kind)).toBe(false)
  writeFileSync(evidenceFile, JSON.stringify({ schema: 1, run, rows: [...rows, row] }) + '\n')
}
test.beforeAll(() => {
  expect(run).toMatch(/^\d{13}$/); expect(path.basename(work)).toBe('security-' + run)
  expect(new URL(peerUrl).hostname).toBe('127.0.0.1'); expect(new URL(peerUrl).protocol).toBe('http:')
  expect(['before-restart', 'after-restart']).toContain(phase)
})
async function usePeer(page: Page) {
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const original = new URL(route.request().url())
    const response = await route.fetch({ url: peerUrl + original.pathname + original.search })
    await route.fulfill({ response })
  })
}
async function login(page: Page, peer = false) {
  if (peer) await usePeer(page)
  const existing = sessions.get(peer)
  if (existing) {
    await page.addInitScript(({ token, origin }) => {
      if (window.top === window && location.origin === origin) {
        sessionStorage.setItem('sma.access-token', token)
        sessionStorage.setItem('sma.access-token.detached', '1')
      }
    }, { token: existing, origin: 'http://127.0.0.1:' + process.env.SMA_E2E_FRONTEND_PORT })
    await page.goto('/iam/posts')
    await expect(page.getByRole('heading', { name: '岗位管理', exact: true })).toBeVisible()
    return
  }
  const password = process.env.SMA_E2E_ADMIN_PASSWORD
  if (!password) throw new Error('Use the isolated security runner')
  await page.goto('/iam/posts')
  await page.getByRole('textbox', { name: '用户名', exact: true }).fill('admin')
  await page.getByLabel('密码', { exact: true }).fill(password)
  const loginResponse = page.waitForResponse(response => new URL(response.url()).pathname === '/api/auth/login' && response.request().method() === 'POST')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  const loginBody = await (await loginResponse).json()
  expect(loginBody.code, 'Real login must succeed before governance probes').toBe(0)
  await page.waitForURL(url => url.pathname === '/iam/posts')
  const token = await page.evaluate(async () => { const modulePath = '/src/lib/token.ts'; return (await import(modulePath)).getToken() })
  if (typeof token !== 'string' || !token) throw new Error('Expected a normally authenticated test session')
  sessions.set(peer, token)
}
async function independentPeer(browser: Browser, baseURL: string) {
  const context = await browser.newContext({ baseURL, locale: 'zh-CN' })
  const page = await context.newPage()
  try { await login(page, true) } catch (cause) { await context.close(); throw cause }
  return { context, page }
}
async function lookup(page: Page, key: string): Promise<{ operationId: string; state: string }> {
  return page.evaluate(async key => { const modulePath = '/src/api/http.ts'; const { post } = await import(modulePath); return post('/common/requests/lookup', { key }) }, key)
}
async function rows(page: Page, code: string): Promise<{ id: string; name: string; code: string }[]> {
  return page.evaluate(async code => { const modulePath = '/src/api/http.ts'; const { get } = await import(modulePath); return (await get('/iam/posts', { keyword: code, size: 100 })).items }, code)
}
async function write(page: Page, body: ReturnType<typeof input>, key: string, id?: string, count = 1) {
  return page.evaluate(async ({ body, key, id, count }) => {
    const modulePath = '/src/api/http.ts'; const { post, put } = await import(modulePath)
    return Promise.all(Array.from({ length: count }, async () => {
      try {
        const value = id ? await put('/iam/posts/' + id, body, { idempotencyKey: key }) : await post('/iam/posts', body, { idempotencyKey: key })
        return { state: 'success', id: value.id as string, status: 200, key: '', operationId: '', retryAfter: 0, policy: '' }
      } catch (cause) {
        const error = cause as { key: string; meta: { status: number; retryAfterSeconds?: number; policy?: string }; details?: { operationId?: string } }
        return { state: 'error', id: '', status: error.meta.status, key: error.key, operationId: error.details?.operationId || '', retryAfter: error.meta.retryAfterSeconds || 0, policy: error.meta.policy || '' }
      }
    }))
  }, { body, key, id, count })
}

if (phase !== 'after-restart') {
  test('a peer tab on the other JVM recovers the original committed write without resubmitting it', async ({ page, context }) => {
    await login(page)
    const original = input('peer'), [created] = await write(page, original, randomUUID())
    expect(created?.state).toBe('success'); const id = created!.id
    const peer = await context.newPage(); await login(peer, true); await page.reload()
    for (const tab of [page, peer]) {
      await tab.getByRole('row').filter({ has: tab.getByRole('cell', { name: original.name, exact: true }) }).getByRole('button', { name: '编辑', exact: true }).click()
      await tab.getByRole('dialog').getByRole('textbox', { name: '名称', exact: true }).fill('S1d2 peer updated')
    }
    let key = '', mutationsA = 0, mutationsB = 0
    peer.on('request', request => { if (request.method() === 'PUT' && new URL(request.url()).pathname === '/api/iam/posts/' + id) mutationsB++ })
    await page.route(url => url.pathname === '/api/iam/posts/' + id, async route => {
      mutationsA++; key = route.request().headers()['idempotency-key']!
      const response = await route.fetch(); expect((await response.json()).code).toBe(0)
      await route.abort('failed')
    })
    await page.getByRole('dialog').getByRole('button', { name: '保存', exact: true }).click()
    await expect(page.getByRole('dialog').getByRole('button', { name: '查询原操作结果', exact: true })).toBeVisible()
    await expect(peer.getByRole('dialog').getByRole('button', { name: '保存', exact: true })).toBeDisabled()
    await peer.getByRole('dialog').getByRole('button', { name: '查询原操作结果', exact: true }).click()
    await expect(peer.getByRole('dialog')).toHaveCount(0)
    const receipt = await lookup(peer, key); expect(receipt.state).toBe('SUCCEEDED')
    expect(await rows(peer, original.code)).toEqual([expect.objectContaining({ id, name: 'S1d2 peer updated' })])
    expect(mutationsA).toBe(1); expect(mutationsB).toBe(0)
    remember({ kind: 'peer', key, id, operationId: receipt.operationId })
    await peer.close()
  })

  test('one concurrent request per JVM produces one success and one original-key replay or processing receipt', async ({ page, browser }, testInfo) => {
    await login(page)
    const peer = await independentPeer(browser, String(testInfo.project.use.baseURL))
    try {
      const body = input('pair'), key = randomUUID()
      const results = (await Promise.all([write(page, body, key), write(peer.page, body, key)])).flat()
      const success = results.filter(result => result.state === 'success'); expect(success).toHaveLength(1)
      const rejected = results.find(result => result.state === 'error')!
      expect([202, 409]).toContain(rejected.status)
      expect(['common.request.processing', 'common.request.completed']).toContain(rejected.key)
      const a = await lookup(page, key), b = await lookup(peer.page, key)
      expect(a.state).toBe('SUCCEEDED'); expect(b).toEqual(a)
      expect(await rows(peer.page, body.code)).toEqual([expect.objectContaining({ id: success[0]!.id, name: body.name })])
      remember({ kind: 'pair', key, id: success[0]!.id, operationId: a.operationId })
    } finally { await peer.context.close() }
  })

  test('twenty writes preserve one original-key result even when bounded admission rejects excess work', async ({ page, browser }, testInfo) => {
    await login(page)
    const peer = await independentPeer(browser, String(testInfo.project.use.baseURL))
    try {
      const body = input('concurrent'), key = randomUUID()
      const [a, b] = await Promise.all([write(page, body, key, undefined, 10), write(peer.page, body, key, undefined, 10)])
      const results = [...a, ...b], success = results.filter(result => result.state === 'success')
      const wire = [a, b].flatMap((items, node) => items.map(({ status, key, retryAfter, policy }) => ({ node, status,
        key: /^[a-z][a-z0-9_.-]{0,159}$/.test(key) ? key : '', retryAfter, policy: ['LOCAL', 'NATIVE', 'QUERY'].includes(policy) ? policy : '' })))
      writeFileSync(path.join(work, 'concurrent-outcomes.json'), JSON.stringify(wire, null, 2) + '\n')
      await testInfo.attach('bounded-response-metadata', { body: Buffer.from(JSON.stringify(wire)), contentType: 'application/json' })
      expect(success).toHaveLength(1)
      for (const result of results.filter(result => result.state === 'error')) {
        if (result.status === 503) {
          // Only the declared, bounded unavailable response is permitted, never arbitrary 5xx.
          // It still requires the authoritative original-key checks below; no write is retried.
          expect(result.key).toBe('common.request.unavailable'); expect(result.retryAfter).toBe(1)
        } else {
          expect([202, 409]).toContain(result.status)
          expect(['common.request.processing', 'common.request.completed']).toContain(result.key)
        }
      }
      const receiptA = await lookup(page, key), receiptB = await lookup(peer.page, key)
      expect(receiptA.state).toBe('SUCCEEDED'); expect(receiptB).toEqual(receiptA)
      expect(await rows(page, body.code)).toEqual([expect.objectContaining({ id: success[0]!.id, code: body.code, name: body.name })])
      expect(await rows(peer.page, body.code)).toHaveLength(1)
      const [conflict] = await write(peer.page, { ...body, name: 'Conflicting input' }, key)
      expect(conflict).toMatchObject({ status: 409, key: 'common.request.conflict' })
      expect((await rows(page, body.code))[0]?.name).toBe(body.name)
      remember({ kind: 'concurrent', key, id: success[0]!.id, operationId: receiptA.operationId })
    } finally { await peer.context.close() }
  })
} else {
  test('both restarted JVMs return the same original durable receipts to new browser sessions', async ({ page, browser }, testInfo) => {
    await login(page)
    const peer = await independentPeer(browser, String(testInfo.project.use.baseURL))
    try {
      const saved = evidence(); expect(saved.map(row => row.kind).sort()).toEqual(['concurrent', 'pair', 'peer'])
      for (const row of saved) {
        for (const tab of [page, peer.page]) expect(await lookup(tab, row.key)).toMatchObject({ operationId: row.operationId, state: 'SUCCEEDED' })
      }
    } finally { await peer.context.close() }
  })
  test('restart cannot turn a completed key into a new creation or permit changed input', async ({ page, browser }, testInfo) => {
    await login(page)
    const peer = await independentPeer(browser, String(testInfo.project.use.baseURL))
    try {
      const saved = evidence().find(row => row.kind === 'concurrent')!, body = input('concurrent')
      for (const tab of [page, peer.page]) {
        const [replay] = await write(tab, body, saved.key)
        expect(replay).toMatchObject({ status: 409, key: 'common.request.completed', operationId: saved.operationId })
        const [conflict] = await write(tab, { ...body, sort: 7 }, saved.key)
        expect(conflict).toMatchObject({ status: 409, key: 'common.request.conflict' })
        expect(await rows(tab, body.code)).toEqual([expect.objectContaining({ id: saved.id, name: body.name })])
      }
    } finally { await peer.context.close() }
  })
  test('independent object updates remain distinct after both processes restart', async ({ page, browser }, testInfo) => {
    await login(page)
    const peer = await independentPeer(browser, String(testInfo.project.use.baseURL))
    try {
      const saved = evidence(), first = saved.find(row => row.kind === 'peer')!, second = saved.find(row => row.kind === 'concurrent')!
      const a = input('peer', 'S1d2 independent A'), b = input('concurrent', 'S1d2 independent B')
      const [left, right] = await Promise.all([write(page, a, randomUUID(), first.id), write(peer.page, b, randomUUID(), second.id)])
      expect(left[0]).toMatchObject({ state: 'success', id: first.id }); expect(right[0]).toMatchObject({ state: 'success', id: second.id })
      expect(await rows(page, a.code)).toEqual([expect.objectContaining({ id: first.id, name: a.name })])
      expect(await rows(peer.page, b.code)).toEqual([expect.objectContaining({ id: second.id, name: b.name })])
    } finally { await peer.context.close() }
  })
}
