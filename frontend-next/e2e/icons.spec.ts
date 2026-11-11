import { expect, test } from '@playwright/test'
import { fullApi } from './full-api'
import { login } from './mock-api'
import AxeBuilder from '@axe-core/playwright'

test('expanded icon library and 3D navigation work in both themes, skins and narrow screens', async ({ page }) => {
  test.setTimeout(120000)
  const api = await fullApi(page)
  api.me.menus[0]!.children[0]!.icon = '3d:folder'
  Object.assign(api.me, { navigation: [{ id: 'demo', name: '图标导航', icon: '3d:rocket', menuIds: ['org'] }] })
  const row = { id: '903', parentId: '0', name: '选择器验收', permissionKey: 'iam:icon:view', type: 'MENU', path: '/iam/departments', icon: '3d:folder', openMode: 'ROUTE', sort: 0, enabled: true, hidden: false, superAdminDefault: true, builtin: false }
  await page.route('**/api/iam/permissions**', route => route.fulfill({ json: { code: 0, data: [row] } }))
  await login(page, '/iam/permissions')
  const requests: string[] = []
  page.on('request', request => { if (request.resourceType() === 'image') requests.push(request.url()) })
  for (const skin of ['iris', 'celadon', 'amber']) for (const theme of ['light', 'dark']) {
    api.preferences.skin = skin; api.preferences.theme = theme
    const width = theme === 'light' ? 1440 : 390
    await page.setViewportSize({ width, height: 900 })
    await page.goto('/iam/permissions')
    if (width === 390) {
      await page.locator('.data-table [data-slot=scroll-area-viewport]').evaluate(el => { el.scrollLeft = el.scrollWidth })
      await page.getByRole('button', { name: '操作', exact: true }).click()
    }
    await page.getByRole('button', { name: '编辑', exact: true }).click()
    const editor = page.getByRole('dialog', { name: '编辑 · 权限资源', exact: true })
    await editor.locator('#field-icon').click()
    const picker = page.getByRole('dialog', { name: '选择图标', exact: true })
    await picker.getByRole('combobox', { name: '图库', exact: true }).selectOption('3d')
    await expect(picker.getByRole('status')).toHaveText('找到 122 个图标')
    const accessibility = await new AxeBuilder({ page }).include('[data-slot=dialog-content]').withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()
    expect(accessibility.violations.map(v => ({ id: v.id, nodes: v.nodes.map(n => n.target) }))).toEqual([])
    await expect(picker.getByRole('button', { name: '使用此图标', exact: true })).toBeInViewport()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
    await page.screenshot({ path: `../docs/frontend-next/images/icons-picker-${skin}-${theme}-${width}.png` })
    await page.keyboard.press('Escape'); await expect(picker).toHaveCount(0)
    await page.keyboard.press('Escape'); await expect(editor).toHaveCount(0)
  }
  expect(requests.every(url => new URL(url).origin === 'http://127.0.0.1:5174')).toBe(true)
  expect(api.unmatched).toEqual([])
})

test('permission buttons consume current-user icon metadata and never grant access', async ({ page }) => {
  const api = await fullApi(page)
  let permissionIcons = { 'openapi:app:create': '3d:rocket', 'iam:user:create': 'lucide:plus' }
  await page.route('**/api/auth/me', route => route.fulfill({ json: { code: 0, data: { ...api.me, ...api.preferences, permissionIcons } } }))
  await login(page, '/openapi/apps')
  const create = page.getByRole('button', { name: '创建应用', exact: true })
  await expect(create.locator('img')).toHaveAttribute('src', /icons\/3d\/rocket.webp$/)
  await create.click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await page.keyboard.press('Escape')
  permissionIcons = { ...permissionIcons, 'openapi:app:create': 'none' }
  await page.reload()
  await expect(create).toBeVisible()
  await expect(create.locator('img, svg')).toHaveCount(0)
  api.me.permissions = api.me.permissions.filter(key => key !== 'openapi:app:create')
  await page.reload()
  await expect(create).toHaveCount(0)
  expect(api.unmatched).toEqual([])
})

test('icon picker searches full catalogs, preserves cancel/unknown values, saves and clears menu icons', async ({ page }) => {
  const api = await fullApi(page)
  let icon: string | null = 'LegacyUnlistedIcon'
  const row = { id: '901', parentId: '0', name: '图标验收菜单', defaultName: '图标验收菜单', permissionKey: 'iam:icon-demo:view', type: 'MENU', path: '/iam/departments', openMode: 'ROUTE', sort: 0, enabled: true, hidden: false, superAdminDefault: true, builtin: false }
  const writes: Record<string, unknown>[] = []
  await page.route('**/api/iam/permissions**', route => {
    if (route.request().method() === 'PUT') { const body = route.request().postDataJSON(); writes.push(body); icon = body.icon }
    return route.fulfill({ json: { code: 0, data: route.request().method() === 'PUT' ? { ...row, icon } : [{ ...row, icon }] } })
  })
  api.me.menus[0]!.children.push({ ...api.me.menus[0]!.children[0]!, id: 'icon-demo', name: row.name, path: row.path, icon: '3d:folder' })
  await login(page, '/iam/permissions')
  await page.getByRole('button', { name: '编辑', exact: true }).click()
  const editor = page.getByRole('dialog', { name: '编辑 · 权限资源', exact: true })
  await expect(editor.locator('#field-icon')).toContainText('LegacyUnlistedIcon')
  await expect(editor.getByText(/此图标暂未收录/)).toBeVisible()
  await editor.locator('#field-icon').click()
  const picker = page.getByRole('dialog', { name: '选择图标', exact: true })
  await picker.getByRole('textbox').fill('rocket')
  await picker.getByRole('button', { name: '火箭 · 3d:rocket', exact: true }).click()
  await picker.getByRole('button', { name: '取消', exact: true }).first().click()
  await expect(editor.locator('#field-icon')).toContainText('LegacyUnlistedIcon')
  await editor.locator('#field-icon').click()
  await picker.getByRole('combobox', { name: '图库', exact: true }).selectOption('3d')
  await expect(picker.getByRole('status')).toHaveText('找到 122 个图标')
  await picker.getByRole('button', { name: '下一页', exact: true }).click()
  await picker.getByRole('textbox').fill('文件夹')
  await picker.getByRole('button', { name: '文件夹 · 3d:folder', exact: true }).click()
  await picker.getByRole('button', { name: '使用此图标', exact: true }).click()
  expect(writes).toHaveLength(0)
  await editor.getByRole('button', { name: '保存', exact: true }).click()
  await expect(editor).toHaveCount(0)
  expect(writes[0]?.icon).toBe('3d:folder')
  await page.getByRole('button', { name: '编辑', exact: true }).click()
  await expect(editor.locator('#field-icon img')).toHaveAttribute('src', /icons\/3d\/folder.webp$/)
  await editor.getByRole('button', { name: '清除', exact: true }).click()
  await editor.getByRole('button', { name: '保存', exact: true }).click()
  await expect(editor).toHaveCount(0)
  expect(writes[1]?.icon).toBe('none')
  await page.reload()
  await page.getByRole('button', { name: '编辑', exact: true }).click()
  await expect(editor.locator('#field-icon')).toHaveText('不设置图标')
  await editor.getByRole('button', { name: '保存', exact: true }).click()
  expect(api.unmatched).toEqual([])
})

test('3D menus and picker remain usable on mobile and a failed resource save retains the choice', async ({ page }) => {
  const api = await fullApi(page)
  api.preferences.theme = 'dark'
  const row = { id: '902', parentId: '0', name: '图标菜单', permissionKey: 'iam:icon:view', type: 'MENU', path: '/iam/departments', icon: '3d:folder', openMode: 'ROUTE', sort: 0, enabled: true, hidden: false, superAdminDefault: true, builtin: false }
  await page.route('**/api/iam/permissions**', route => route.request().method() === 'PUT'
    ? route.fulfill({ status: 500, json: { code: 500, message: 'Icon save rejected' } })
    : route.fulfill({ json: { code: 0, data: [row] } }))
  await page.setViewportSize({ width: 390, height: 844 })
  await login(page, '/iam/permissions')
  const viewport = page.locator('.data-table [data-slot=scroll-area-viewport]')
  await viewport.evaluate(el => { el.scrollLeft = el.scrollWidth })
  await page.getByRole('button', { name: '操作', exact: true }).click()
  await page.getByRole('button', { name: '编辑', exact: true }).click()
  const editor = page.getByRole('dialog', { name: '编辑 · 权限资源', exact: true })
  await editor.locator('#field-icon').click()
  const picker = page.getByRole('dialog', { name: '选择图标', exact: true })
  await picker.getByRole('textbox').fill('用户')
  await picker.getByRole('button', { name: '用户 · lucide:users', exact: true }).focus()
  await page.keyboard.press('Enter')
  await picker.getByRole('button', { name: '使用此图标', exact: true }).click()
  await editor.getByRole('button', { name: '保存', exact: true }).click()
  await expect(editor.getByText('Icon save rejected', { exact: true })).toBeVisible()
  await expect(editor.locator('#field-icon')).toHaveText('用户')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
})
