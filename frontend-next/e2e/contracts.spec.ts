import { expect, test } from '@playwright/test'
import { fullApi } from './full-api'
import { login } from './mock-api'

test('maximum page size, column and density preferences are persisted and isolated by account', async ({ page }, info) => {
  const api = await fullApi(page)
  await page.route('http://127.0.0.1:5174/api/iam/users?*', route => {
    const size = Number(new URL(route.request().url()).searchParams.get('size') || 20)
    return route.fulfill({ json: { code: 0, data: { items: Array.from({ length: size }, (_, i) => ({ id: '9007199254740993' + i, username: 'member-' + i, name: '用户 ' + i, enabled: true })), total: '200', page: '1', size: String(size) } } })
  })
  await login(page, '/iam/users')
  const start = Date.now()
  await page.getByRole('combobox', { name: '每页条数', exact: true }).selectOption('200')
  await expect(page.getByRole('cell', { name: 'member-199', exact: true })).toBeVisible()
  const table200Millis = Date.now() - start
  expect(table200Millis).toBeLessThan(5000)
  await info.attach('table-200-performance', { body: JSON.stringify({ table200Millis, budgetMillis: 5000 }), contentType: 'application/json' })
  await page.getByRole('button', { name: '紧凑密度', exact: true }).click()
  await page.getByRole('button', { name: '显示列', exact: true }).click()
  await page.getByRole('menuitemcheckbox', { name: '邮箱', exact: true }).click()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('columnheader', { name: '邮箱', exact: true })).toHaveCount(0)
  await page.reload()
  await expect(page.getByRole('button', { name: '标准密度', exact: true })).toBeVisible()
  await expect(page.getByRole('columnheader', { name: '邮箱', exact: true })).toHaveCount(0)
  api.me.id = '102'
  await page.reload()
  await expect(page.getByRole('button', { name: '紧凑密度', exact: true })).toBeVisible()
  await expect(page.getByRole('columnheader', { name: '邮箱', exact: true })).toBeVisible()
})

test('role data scopes, custom departments and large permission-tree search retain explicit selections', async ({ page }, info) => {
  test.setTimeout(60000)
  const api = await fullApi(page)
  await page.route('http://127.0.0.1:5174/api/iam/permissions/tree', route => route.fulfill({ json: { code: 0, data: [{ id: 'root', name: '权限根节点', children: Array.from({ length: 500 }, (_, i) => ({ id: 'node' + i, name: '业务权限 ' + i })) }] } }))
  await login(page, '/iam/roles')
  const dialog = page.getByRole('dialog')
  for (const [scope, label] of [['ALL', '全部数据'], ['SELF', '仅本人'], ['DEPARTMENT', '本部门'], ['DEPARTMENT_TREE', '本部门及下级'], ['CUSTOM', '自定义部门']]) {
    await page.getByRole('button', { name: '编辑', exact: true }).click()
    await dialog.locator('#field-dataScope').click()
    await page.getByRole('button', { name: label, exact: true }).click()
    if (scope === 'CUSTOM') {
      await dialog.locator('#field-departmentIds').click()
      await page.getByRole('button', { name: '研发部', exact: true }).click()
      await page.keyboard.press('Escape')
    }
    await dialog.getByRole('button', { name: '保存', exact: true }).click()
    await expect(dialog).toHaveCount(0)
    const body = api.writes.filter(write => write.path.startsWith('/iam/roles/') && write.method === 'PUT').at(-1)?.body
    expect(body?.dataScope).toBe(scope)
    if (scope === 'CUSTOM') expect(body?.departmentIds).toEqual(['dept1'])
  }
  const permissions = page.locator('[data-role-permissions]')
  const start = Date.now()
  await permissions.getByRole('textbox', { name: '搜索节点', exact: true }).fill('业务权限 499')
  await expect(permissions.getByRole('checkbox', { name: '业务权限 499', exact: true })).toBeVisible()
  const tree500SearchMillis = Date.now() - start
  expect(tree500SearchMillis).toBeLessThan(3000)
  await info.attach('tree-500-performance', { body: JSON.stringify({ tree500SearchMillis, budgetMillis: 3000 }), contentType: 'application/json' })
  await permissions.getByRole('checkbox', { name: '业务权限 499', exact: true }).click()
  await permissions.getByRole('button', { name: '保存', exact: true }).click()
  await expect(permissions.getByRole('status')).toHaveText('与上次读取或保存一致')
  expect(api.writes.at(-1)?.body.permissionIds).toEqual(['menu1', 'read', 'node499'])
})
