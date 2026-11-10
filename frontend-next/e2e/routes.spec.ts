import { expect, test } from '@playwright/test'
import { fullApi } from './full-api'
import { login } from './mock-api'
const routes = ['/', '/iam/users', '/iam/roles', '/iam/departments', '/iam/posts', '/iam/permissions', '/iam/navigation', '/profile', '/preferences', '/config/dictionaries', '/config/parameters', '/auth/sessions', '/audit/logins', '/audit/operations', '/file/files', '/message/notices', '/message/todos', '/message/inbox', '/message/inbox?tab=tasks', '/message/inbox?tab=private', '/monitor/server', '/monitor/sql', '/monitor/caches', '/monitor/events', '/monitor/database', '/openapi/apps', '/403', '/unknown-page']
test('all existing business routes render at desktop and mobile without page errors, missing translations or document overflow', async ({ page }) => {
  test.setTimeout(90000)
  const api = await fullApi(page), errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('console', message => { if (message.text().includes('[intlify]') || message.text().includes('[Vue warn]')) errors.push(message.text()) })
  await login(page, '/')
  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: 1000 })
    for (const route of routes) {
      await page.goto(route)
      await expect(page.locator('main').getByRole('heading', { level: 1 }).first(), route).toBeVisible()
      await expect(page.getByText('加载失败', { exact: true })).toHaveCount(0)
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1), width + ': ' + route).toBe(true)
      if (!['/403', '/unknown-page'].includes(route)) await expect(page.getByRole('heading', { name: '页面不存在' })).toHaveCount(0)
    }
  }
  expect(api.unmatched).toEqual([])
  expect(errors).toEqual([])
})
test('dictionary editing is independent of selecting its type and database filtering has an explicit empty state', async ({ page }) => {
  const api = await fullApi(page)
  await login(page, '/config/dictionaries')
  await page.getByRole('tab', { name: '字典类型', exact: true }).click()
  await page.getByRole('button', { name: '编辑', exact: true }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await expect(page.locator('[role=tab][data-state=active]')).toHaveText('字典类型')
  await page.getByRole('dialog').getByRole('button', { name: '取消', exact: true }).last().click()
  await page.goto('/monitor/database')
  await page.getByPlaceholder('搜索表名或说明').fill('does-not-exist')
  await expect(page.getByText('暂无数据', { exact: true })).toHaveCount(2)
  expect(api.writes.filter(write => write.path.startsWith('/config/dictionaries/'))).toEqual([])
})
test('SQL sorting sends the requested server ordering and document export produces a downloadable file', async ({ page }) => {
  await fullApi(page)
  await login(page, '/monitor/sql')
  const query = page.waitForRequest(request => { const url = new URL(request.url()); return url.pathname === '/api/monitor/sql' && url.searchParams.get('sort') === 'calls' })
  await page.getByRole('button', { name: '调用次数', exact: true }).click()
  expect(new URL((await query).url()).searchParams.get('direction')).toBe('asc')
  await page.goto('/monitor/database')
  const downloaded = page.waitForEvent('download')
  await page.getByRole('button', { name: '导出 Markdown 文档', exact: true }).click()
  expect((await downloaded).suggestedFilename()).toBe('database-schema.md')
})
