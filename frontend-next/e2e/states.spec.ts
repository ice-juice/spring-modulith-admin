import { expect, test } from '@playwright/test'
import { fullApi } from './full-api'
import { login } from './mock-api'

test('business list routes display request errors and recover into explicit empty states', async ({ page }) => {
  test.setTimeout(120000)
  await fullApi(page)
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await login(page, '/')
  const array: unknown[] = [], paged = { items: [], page: '1', size: '20', total: '0' }
  const cases: [string, string, unknown][] = [
    ['/iam/users', '/iam/users', paged], ['/iam/roles', '/iam/roles', array],
    ['/iam/departments', '/iam/departments', array], ['/iam/posts', '/iam/posts', paged],
    ['/iam/permissions', '/iam/permissions', array], ['/iam/navigation', '/iam/navigation', array],
    ['/config/dictionaries', '/config/effective-dictionaries', paged], ['/config/parameters', '/config/effective-parameters', paged],
    ['/auth/sessions', '/auth/sessions', paged], ['/audit/logins', '/audit/logins', paged],
    ['/audit/operations', '/audit/operations', paged], ['/file/files', '/file/files', paged],
    ['/message/notices', '/message/notices', paged], ['/message/todos', '/message/todos/managed', paged],
    ['/message/inbox', '/message/notices/inbox', paged], ['/message/inbox?tab=tasks', '/message/todos', paged],
    ['/message/inbox?tab=private', '/message/private', paged],
    ['/monitor/sql', '/monitor/sql', paged], ['/monitor/caches', '/monitor/caches/directory', array],
    ['/monitor/events', '/monitor/publications', paged], ['/monitor/database', '/monitor/database', { database: 'empty', product: 'MySQL', version: '8', tables: [] }],
    ['/openapi/apps', '/openapi/apps', paged],
  ]
  for (const [path, endpoint, empty] of cases) {
    const matcher = (url: URL) => url.origin === (process.env.SMA_E2E_MOCK_ORIGIN || 'http://127.0.0.1:5174') && url.pathname === '/api' + endpoint
    await page.route(matcher, route => route.request().method() === 'GET' ? route.fulfill({ status: 503, json: { message: '验收接口暂不可用' } }) : route.fallback())
    await page.goto(path)
    await expect(page.locator('main').getByRole('alert').first(), path).toContainText('验收接口暂不可用')
    await page.unroute(matcher)
    await page.route(matcher, route => route.request().method() === 'GET' ? route.fulfill({ json: { code: 0, data: empty } }) : route.fallback())
    await page.reload()
    await expect(page.locator('main [aria-busy=true]'), path).toHaveCount(0)
    await expect(page.locator('main').getByText('暂无数据', { exact: true }).first(), path).toBeVisible()
    await expect(page.locator('main [role=alert]'), path).toHaveCount(0)
    await page.unroute(matcher)
  }
  expect(errors).toEqual([])
})

test('initial table load announces pending state and an unsuccessful refresh retains existing rows', async ({ page }) => {
  await fullApi(page)
  await login(page, '/iam/users')
  await expect(page.getByRole('cell', { name: 'test.member', exact: true })).toBeVisible()
  let finish: (() => void) | undefined
  await page.route((process.env.SMA_E2E_MOCK_ORIGIN || 'http://127.0.0.1:5174') + '/api/iam/users?*', async route => {
    await new Promise<void>(resolve => { finish = resolve })
    await route.fulfill({ status: 503, json: { message: '刷新失败，请重试' } })
  })
  await page.getByRole('button', { name: '刷新', exact: true }).click()
  await expect(page.locator('main .data-table[aria-busy=true]')).toBeVisible()
  await expect(page.getByRole('cell', { name: 'test.member', exact: true })).toBeVisible()
  await expect.poll(() => !!finish).toBe(true)
  finish!()
  await expect(page.getByRole('alert')).toContainText('刷新失败，请重试')
  await expect(page.getByRole('cell', { name: 'test.member', exact: true })).toBeVisible()
})
