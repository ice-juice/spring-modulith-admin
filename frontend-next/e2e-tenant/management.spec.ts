import { expect, test, type Page } from '@playwright/test'
import { fullApi } from '../e2e/full-api'
import { login } from '../e2e/mock-api'
import { tenantEntry } from '../e2e/tenant-entry'
import type { BrandContent, BrandDraft, BrandReceipt, BrandRevision } from '../src/features/tenant/brands'

function initial(): BrandContent {
  return { schemaVersion: 1, identity: { systemName: 'Tenant draft', shortName: '', englishName: '' }, login: { templateId: 'login1', title: { zhCN: '欢迎回来', enUS: 'Welcome back' }, description: { zhCN: '', enUS: '' }, backgroundBlur: false }, legal: { copyrightOwner: '', copyrightStartYear: null, copyrightUrl: '', icpRecord: '', icpUrl: '', policeRecord: '', policeUrl: '' }, links: { website: '', help: '', support: '', privacy: '', agreement: '' }, appearance: { primaryColor: '', defaultSkin: 'iris', allowPersonalSkin: true }, assets: {}, overrides: {} }
}
async function fixture(page: Page, origin: string) {
  const api = await fullApi(page, origin)
  api.me.permissions.push('tenant:brand:view', 'tenant:brand:edit', 'tenant:brand:publish', 'tenant:brand:rollback')
  let draft: BrandDraft = { content: initial(), version: '1', publishedRevisionId: null, legacyBackgroundUrl: null }
  const revisions: BrandRevision[] = [], receipts = new Map<string, BrandReceipt>(), localKeys = new Set<string>()
  await page.route('**/api/common/requests/lookup', route => {
    const key = route.request().postDataJSON().key; expect(localKeys.has(key)).toBe(true)
    return route.fulfill({ json: { code: 0, data: { operationId: 'local-' + key, operation: 'brands.save', state: 'SUCCEEDED', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:01Z' } } })
  })
  const state = { saveCalls: 0, publishCalls: 0, rollbackCalls: 0, previewCalls: 0, frameApiCalls: 0, loseSave: false, losePublish: false, delayPreview: false, releasePreview: undefined as (() => void) | undefined, hideReceipt: false }
  page.on('request', request => { if (new URL(request.url()).pathname.startsWith('/api/') && request.frame().parentFrame()) state.frameApiCalls++ })
  await page.route(url => url.pathname.startsWith('/api/tenant/'), async route => {
    const path = new URL(route.request().url()).pathname, method = route.request().method()
    const ok = (data: unknown) => route.fulfill({ json: { code: 0, data } })
    if (path === '/api/tenant/entry') {
      const entry = tenantEntry('Published tenant')
      if (revisions[0]) entry.branding = { ...entry.branding, ...revisions[0].content, assets: {}, revisionId: revisions[0].id, revision: revisions[0].revision }
      return ok(entry)
    }
    if (path === '/api/tenant/brands/templates') return ok(['login1', 'login2', 'login3'].map(id => ({ id, version: 1, nameKey: 'tenant.template.' + id, layout: id, enabled: true })))
    if (path === '/api/tenant/brands/draft') {
      if (method === 'GET') return ok(draft)
      state.saveCalls++
      const body = route.request().postDataJSON()
      if (body.expectedVersion !== draft.version) return route.fulfill({ json: { code: 400, error: 'tenant.error.stale', message: '远端版本已变化' } })
      draft = { ...draft, content: body.content, version: String(BigInt(draft.version) + 1n) }
      localKeys.add(route.request().headers()['idempotency-key']!)
      if (state.loseSave) { state.loseSave = false; return route.abort('failed') }
      return ok(draft)
    }
    if (path === '/api/tenant/brands/revisions') return ok({ items: revisions, total: String(revisions.length), page: '1', size: '10' })
    if (path === '/api/tenant/brands/previews') {
      state.previewCalls++
      const body = route.request().postDataJSON()
      if (state.delayPreview) await new Promise<void>(resolve => { state.releasePreview = resolve })
      return ok({ content: { ...body.content, overrides: {} }, defaultsRevision: 'builtin-1', legacyBackgroundUrl: null, images: {} })
    }
    if (path.startsWith('/api/tenant/brands/operations/')) {
      const receipt = state.hideReceipt ? undefined : receipts.get(path.split('/').at(-1)!)
      return ok({ status: receipt ? 'COMMITTED' : 'NOT_OBSERVED', receipt: receipt || null })
    }
    if (path === '/api/tenant/brands/publications' || path === '/api/tenant/brands/rollbacks') {
      const body = route.request().postDataJSON(), rollback = path.endsWith('/rollbacks')
      expect(route.request().headers()['idempotency-key']).toBe(body.operationId)
      if (rollback) state.rollbackCalls++; else state.publishCalls++
      if (body.expectedPublishedRevisionId !== draft.publishedRevisionId) return route.fulfill({ json: { code: 400, error: 'tenant.error.stale', message: '远端版本已变化' } })
      const source = rollback ? revisions.find(value => value.id === body.sourceRevisionId)!.content : draft.content
      const revision: BrandRevision = { id: String(9007199254740993100n + BigInt(revisions.length)), revision: String(revisions.length + 1), sourceRevisionId: body.sourceRevisionId || null, defaultsRevision: 'builtin-1', content: structuredClone(source), createdAt: '2026-11-11T10:00:00Z', legacyBackgroundUrl: null }
      revisions.unshift(revision); draft.publishedRevisionId = revision.id
      const receipt = { revisionId: revision.id, revision: revision.revision, sourceRevisionId: revision.sourceRevisionId, operationId: body.operationId }
      receipts.set(body.operationId, receipt)
      if (!rollback && state.losePublish) { state.losePublish = false; return route.abort('failed') }
      return ok(receipt)
    }
    return route.fallback()
  })
  return { api, state, remote: () => draft, revisions }
}

test('brand upload recovery belongs to the original slot and prevents saving before completion', async ({ page, baseURL }, info) => {
  test.skip(info.project.name !== 'next', 'Brand management belongs to frontend-next.')
  const { remote } = await fixture(page, baseURL!)
  let writes = 0, key = ''
  await page.route(url => url.pathname === '/api/file/files/images', route => {
    writes++
    const sent = route.request().headers()['idempotency-key']!
    if (writes === 1) { key = sent; return route.abort('failed') }
    expect(sent).toBe(key)
    return route.fulfill({ json: { code: 0, data: { id: '42', name: 'brand.png', contentType: 'image/png' } } })
  })
  await page.route(url => url.pathname === '/api/file/uploads/lookup', route => {
    expect(route.request().postDataJSON()).toEqual({ key })
    return route.fulfill({ json: { code: 0, data: { uploadId: '11111111-1111-4111-8111-111111111111', state: 'ACCEPTED', fileId: null, resumable: true } } })
  })
  await login(page, '/tenant/brand'); await page.getByRole('tab', { name: '品牌图片', exact: true }).click()
  await page.locator('input[type="file"][aria-label="浅色 Logo"]').setInputFiles({ name: 'brand.png', mimeType: 'image/png', buffer: Buffer.from('fixture-image') })
  await page.getByRole('button', { name: '查询原操作结果', exact: true }).click()
  await page.getByRole('button', { name: '继续上传原文件', exact: true }).click()
  await expect(page.getByRole('button', { name: '查询原操作结果', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '保存草稿', exact: true })).toBeEnabled()
  await page.getByRole('button', { name: '保存草稿', exact: true }).click()
  await expect.poll(() => remote().content.assets.LOGO_LIGHT).toBe('42')
  expect(remote().content.assets.LOGO_DARK).toBeUndefined(); expect(writes).toBe(2)
})

test('brand draft, isolated preview, publication and rollback preserve their distinct states', async ({ page, baseURL }, info) => {
  test.skip(info.project.name !== 'next', 'Brand management belongs to frontend-next.')
  const { state, remote, revisions } = await fixture(page, baseURL!)
  await login(page, '/tenant/brand')
  await expect(page.locator('#brand-name')).toHaveValue('Tenant draft')
  await page.locator('#brand-name').fill('First published brand')
  await page.getByRole('tab', { name: '登录页', exact: true }).click()
  await page.getByRole('button', { name: '居中卡片', exact: true }).click()
  const stored = await page.evaluate(() => ({ theme: localStorage.getItem('sma.theme'), locale: localStorage.getItem('sma.locale') }))
  await page.getByRole('button', { name: '预览登录页', exact: true }).click()
  const preview = page.frameLocator('iframe[title="预览登录页"]')
  await expect(preview.locator('[data-login-template]')).toHaveAttribute('data-login-template', 'login2')
  await expect(preview.getByRole('button', { name: '登录', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '浅色', exact: true }).click()
  await expect(preview.locator('html')).toHaveClass(/dark/)
  await expect(page.locator('html')).not.toHaveClass(/dark/)
  expect(await page.evaluate(() => ({ theme: localStorage.getItem('sma.theme'), locale: localStorage.getItem('sma.locale') }))).toEqual(stored)
  expect(state.frameApiCalls).toBe(0); expect(state.saveCalls).toBe(0)
  await page.getByRole('button', { name: '保存草稿', exact: true }).click()
  await expect(page.getByText('草稿已保存，尚未发布。', { exact: true })).toBeVisible()
  expect(revisions).toHaveLength(0)
  await page.getByRole('button', { name: '发布品牌', exact: true }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect.poll(() => revisions.length).toBe(1)
  await page.getByRole('tab', { name: '品牌名称', exact: true }).click()
  await page.locator('#brand-name').fill('Second published brand')
  await page.getByRole('button', { name: '保存草稿', exact: true }).click()
  await expect(page.getByText('草稿已保存，尚未发布。', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '发布品牌', exact: true }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect.poll(() => revisions.length).toBe(2)
  await page.getByRole('tab', { name: '发布历史', exact: true }).click()
  await page.locator('article').filter({ hasText: 'First published brand' }).getByRole('button', { name: '回退到此版本' }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect.poll(() => state.rollbackCalls).toBe(1)
  expect(revisions[0]?.content.identity.systemName).toBe('First published brand')
  expect(remote().content.identity.systemName).toBe('Second published brand')
  await page.screenshot({ path: info.outputPath('brand-history-desktop.png'), fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: info.outputPath('brand-history-mobile.png'), fullPage: true })
})

test('unknown writes are checked without automatic retry and late previews cannot replace edited fields', async ({ page, baseURL }, info) => {
  test.skip(info.project.name !== 'next', 'Brand management belongs to frontend-next.')
  const { state } = await fixture(page, baseURL!)
  await login(page, '/tenant/brand')
  await expect(page.locator('#brand-name')).toHaveValue('Tenant draft')
  state.delayPreview = true
  await page.getByRole('button', { name: '预览登录页', exact: true }).click()
  await expect.poll(() => !!state.releasePreview).toBe(true)
  await page.locator('#brand-name').fill('Edited while previewing')
  state.releasePreview!()
  await expect(page.getByText('编辑后点击“预览登录页”查看效果。', { exact: true })).toBeVisible()
  await expect(page.locator('iframe')).toHaveCount(0)
  state.loseSave = true
  await page.getByRole('button', { name: '保存草稿', exact: true }).click()
  await expect(page.getByText('上次写入结果尚未确认。请先核对结果，系统不会自动重试。', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '保存草稿', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '核对结果', exact: true }).click()
  await expect(page.getByText('原保存操作已完成，已读取当前草稿。', { exact: true })).toBeVisible()
  expect(state.saveCalls).toBe(1)
  state.losePublish = true; state.hideReceipt = true
  await page.getByRole('button', { name: '发布品牌', exact: true }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.getByText('上次写入结果尚未确认。请先核对结果，系统不会自动重试。', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '核对结果', exact: true }).click()
  await expect(page.getByText('尚未查到提交回执，原请求可能仍在执行。请稍后再次核对。', { exact: true })).toBeVisible()
  expect(state.publishCalls).toBe(1)
  state.hideReceipt = false
  await page.getByRole('button', { name: '核对结果', exact: true }).click()
  await expect(page.getByText('已核对该操作已提交。', { exact: true })).toBeVisible()
  expect(state.publishCalls).toBe(1)
})

test('view-only accounts cannot save or publish and stale versions preserve local edits', async ({ page, baseURL }, info) => {
  test.skip(info.project.name !== 'next', 'Brand management belongs to frontend-next.')
  const { api, state, remote } = await fixture(page, baseURL!)
  api.me.permissions = api.me.permissions.filter(permission => !['tenant:brand:edit', 'tenant:brand:publish', 'tenant:brand:rollback'].includes(permission))
  await login(page, '/tenant/brand')
  await expect(page.locator('#brand-name')).toBeDisabled()
  await expect(page.getByRole('button', { name: '保存草稿', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '发布品牌', exact: true })).toHaveCount(0)
  expect(state.saveCalls).toBe(0)
  api.me.permissions.push('tenant:brand:edit')
  await page.reload()
  await expect(page.locator('#brand-name')).toBeEnabled()
  await page.locator('#brand-name').fill('Local edit to retain')
  remote().version = '9'; remote().content.identity.systemName = 'Remote editor'
  await page.getByRole('button', { name: '保存草稿', exact: true }).click()
  await expect(page.getByText('远端版本已变化', { exact: true })).toBeVisible()
  await expect(page.locator('#brand-name')).toHaveValue('Local edit to retain')
  expect(remote().content.identity.systemName).toBe('Remote editor')
})


test('publication reserves before confirmation and a reload retains the original operation ID', async ({ page, baseURL }, info) => {
  test.skip(info.project.name !== 'next', 'Brand management belongs to frontend-next.')
  const { state } = await fixture(page, baseURL!)
  await login(page, '/tenant/brand'); await expect(page.locator('#brand-name')).toHaveValue('Tenant draft')
  state.losePublish = true; state.hideReceipt = true
  await page.getByRole('button', { name: '发布品牌', exact: true }).evaluate(button => { for (let i = 0; i < 20; i++) (button as HTMLButtonElement).click() })
  await expect(page.getByRole('alertdialog')).toHaveCount(1); expect(state.publishCalls).toBe(0)
  await page.getByRole('alertdialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.getByRole('button', { name: '核对结果', exact: true })).toBeVisible()
  const before = await page.evaluate(() => Object.keys(localStorage).filter(key => key.startsWith('sma.operation.v1.')))
  expect(before).toHaveLength(1)
  page.once('dialog', dialog => dialog.accept()); await page.reload()
  await expect(page.getByRole('button', { name: '核对结果', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '发布品牌', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '核对结果', exact: true }).click()
  await expect(page.getByText('尚未查到提交回执，原请求可能仍在执行。请稍后再次核对。', { exact: true })).toBeVisible()
  expect(await page.evaluate(() => Object.keys(localStorage).filter(key => key.startsWith('sma.operation.v1.')))).toEqual(before)
  state.hideReceipt = false; await page.getByRole('button', { name: '核对结果', exact: true }).click()
  await expect(page.getByText('已核对该操作已提交。', { exact: true })).toBeVisible(); expect(state.publishCalls).toBe(1)
})
