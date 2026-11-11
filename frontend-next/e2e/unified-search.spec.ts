import { expect, test } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { fullApi } from './full-api'
import { login } from './mock-api'

test('file hits open authorized metadata, including selecting the same hit again after closing', async ({ page }) => {
  await fullApi(page)
  let reads = 0
  const asset = { id: 'search-file', name: '季度报告.txt', kind: 'DOCUMENT', contentType: 'text/plain', sizeBytes: '42', checksum: 'fixture', previewable: false, createdAt: '2026-11-11T00:00:00Z' }
  await page.route('**/api/file/files?*', route => route.fulfill({ json: { code: 0, data: { items: [asset], total: '1', page: '1', size: '5' } } }))
  await page.route('**/api/file/files/search-file', route => { reads++; return route.fulfill({ json: { code: 0, data: asset } }) })
  await login(page, '/')
  for (let i = 0; i < 2; i++) {
    await page.locator('header').getByRole('button', { name: '搜索', exact: true }).click()
    const search = page.getByRole('dialog', { name: '搜索', exact: true })
    await search.getByRole('button', { name: '全局', exact: true }).click()
    await search.getByRole('textbox').fill('季度')
    await search.getByRole('region', { name: '文件', exact: true }).getByRole('button', { name: '季度报告.txt', exact: true }).click()
    await expect(page.getByRole('dialog', { name: '季度报告.txt', exact: true })).toBeVisible()
    await page.keyboard.press('Escape')
  }
  expect(reads).toBe(2)
})

test('one top search replaces both sidebar inputs, finds menus across groups and supports keyboard navigation', async ({ page }) => {
  const api = await fullApi(page)
  Object.assign(api.me, { navigation: [{ id: 'people', name: '组织', menuIds: ['users'] }] })
  await login(page, '/')
  const sidebar = page.locator('aside')
  await sidebar.getByRole('button', { name: '切换导航组' }).click()
  await page.getByRole('menuitemradio', { name: '组织', exact: true }).click()
  await expect(sidebar.getByRole('textbox')).toHaveCount(0)
  await expect(sidebar.getByRole('link', { name: '应用中心' })).toHaveCount(0)
  await page.keyboard.press('Control+k')
  const dialog = page.getByRole('dialog', { name: '搜索', exact: true })
  await expect(dialog.getByRole('textbox')).toBeFocused()
  await dialog.getByRole('textbox').fill('应用')
  await page.keyboard.press('ArrowDown'); await page.keyboard.press('Enter')
  await expect(page).toHaveURL(/\/openapi\/apps$/)
  await expect(dialog).toHaveCount(0)
  await page.setViewportSize({ width: 320, height: 700 })
  await page.locator('header').getByRole('button', { name: '搜索', exact: true }).click()
  await expect(dialog.getByRole('textbox')).toHaveValue('')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: '展开导航', exact: true }).click()
  await expect(page.getByRole('dialog').getByRole('textbox')).toHaveCount(0)
  expect(api.unmatched).toEqual([])
})

test('global providers use server keyword paging, isolate failures and navigate by ID without leaking query or secret values', async ({ page }) => {
  const api = await fullApi(page)
  let failFiles = true
  const calls: { keyword: string | null; page: string | null; size: string | null }[] = []
  await page.route('**/api/iam/users?*', route => {
    const params = new URL(route.request().url()).searchParams
    calls.push({ keyword: params.get('keyword'), page: params.get('page'), size: params.get('size') })
    const pageNumber = Number(params.get('page') || 1)
    return route.fulfill({ json: { code: 0, data: { items: Array.from({ length: pageNumber === 1 ? 5 : 1 }, (_, i) => ({ id: String(pageNumber * 10 + i), name: `命中用户 ${pageNumber * 10 + i}`, username: 'matching' })), total: '6', page: String(pageNumber), size: '5' } } })
  })
  await page.route('**/api/file/files?*', route => route.fulfill(failFiles ? { status: 500, json: { code: 500 } } : { json: { code: 0, data: { items: [], total: '0' } } }))
  await page.route('**/api/config/parameters?*', route => route.fulfill({ json: { code: 0, data: [{ id: '77', name: '命中参数', paramKey: 'public.name', value: 'DO_NOT_RENDER_SECRET', secret: true }] } }))
  await login(page, '/')
  await page.locator('header').getByRole('button', { name: '搜索', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '搜索', exact: true })
  await dialog.getByRole('button', { name: '全局', exact: true }).click()
  await dialog.getByRole('textbox').fill('命中')
  const users = dialog.getByRole('region', { name: '用户', exact: true })
  await expect(users.getByRole('button', { name: /命中用户/ })).toHaveCount(5)
  const files = dialog.getByRole('region', { name: '文件', exact: true })
  await expect(files.getByText(/此来源暂时无法查询/)).toBeVisible()
  await users.getByRole('button', { name: '加载更多', exact: true }).click()
  await expect(users.getByRole('button', { name: /命中用户/ })).toHaveCount(6)
  expect(calls).toEqual([{ keyword: '命中', page: '1', size: '5' }, { keyword: '命中', page: '2', size: '5' }])
  failFiles = false; await files.getByRole('button', { name: '重试', exact: true }).click()
  await expect(files.getByText('没有匹配结果', { exact: true })).toBeVisible()
  await expect(dialog).not.toContainText('DO_NOT_RENDER_SECRET')
  await dialog.getByRole('button', { name: /命中参数/ }).click()
  await expect(page).toHaveURL(/\/config\/parameters\?focus=77$/)
  expect(await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage }))).not.toContain('命中')
  expect(api.unmatched).toEqual([])
})

test('no-permission providers are not requested, closing cancels pending results and skins remain accessible', async ({ page }) => {
  test.setTimeout(90000)
  const api = await fullApi(page)
  api.me.permissions = []
  const requests: string[] = []
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url())
    if (url.searchParams.has('keyword')) requests.push(url.pathname)
    await route.fallback()
  })
  await login(page, '/')
  for (const [skin, theme, width] of [['iris', 'light', 1440], ['celadon', 'dark', 390], ['amber', 'light', 320]] as const) {
    api.preferences.skin = skin; api.preferences.theme = theme
    await page.setViewportSize({ width, height: 850 }); await page.reload()
    await page.locator('header').getByRole('button', { name: '搜索', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: '搜索', exact: true })
    await dialog.getByRole('button', { name: '全局', exact: true }).click()
    await dialog.getByRole('textbox').fill('我的任务')
    await expect(dialog.getByRole('region', { name: '我的待办', exact: true })).toContainText(api.task.title)
    expect(requests.every(path => path === '/api/message/todos')).toBe(true)
    const accessibility = await new AxeBuilder({ page }).include('[data-slot=dialog-content]').withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()
    expect(accessibility.violations.map(v => ({ id: v.id, nodes: v.nodes.map(n => n.target) }))).toEqual([])
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
    await page.screenshot({ path: `../docs/frontend-next/images/unified-search-${skin}-${theme}-${width}.png` })
    await page.keyboard.press('Escape')
    await expect(dialog).toHaveCount(0)
  }
})
