import { expect, test } from '@playwright/test'
import { fullApi } from './full-api'
import { login } from './mock-api'

test('navigation groups replace the top row, keep nested authorized menus, restore and fall back safely', async ({ page }) => {
  const api = await fullApi(page)
  Object.assign(api.me, { navigation: [
    { id: 'people', name: '组织与成员', menuIds: ['users', 'roles'] },
    { id: 'settings', name: '配置中心', menuIds: ['parameters'] },
    { id: 'empty', name: '空导航组', menuIds: ['not-authorized'] },
  ] })
  api.preferences.keepTabsAfterReload = true
  await page.setViewportSize({ width: 1440, height: 900 })
  await login(page, '/iam/users')
  const sidebar = page.locator('aside'), switcher = sidebar.getByRole('button', { name: '切换导航组', exact: true })
  await switcher.click()
  await page.getByRole('menuitemradio', { name: '组织与成员', exact: true }).click()
  await expect(switcher).toHaveText(/组织与成员/)
  await expect(sidebar.getByRole('link', { name: '用户管理', exact: true })).toBeVisible()
  await expect(sidebar.getByRole('link', { name: '系统参数', exact: true })).toHaveCount(0)
  await expect(page.locator('[data-scroll-region=business-navigation]')).toHaveCount(0)
  await sidebar.getByRole('link', { name: '角色管理', exact: true }).click()
  await expect(page.locator('[data-scroll-region=work-tabs]')).toContainText('用户管理')
  await expect(page.locator('[data-scroll-region=work-tabs]')).toContainText('角色管理')
  await page.reload()
  await expect(switcher).toHaveText(/组织与成员/)
  await expect(page.locator('[data-scroll-region=work-tabs]')).toContainText('用户管理')
  await page.getByRole('button', { name: '收起导航', exact:true }).click()
  await switcher.click()
  await page.getByRole('menuitemradio', { name:'配置中心', exact:true }).click()
  await expect(sidebar.getByRole('link', { name:'系统参数', exact:true })).toBeVisible()
  await page.keyboard.press('Control+k')
  await expect(page.getByRole('dialog', { name: '搜索', exact: true }).getByRole('textbox')).toBeFocused()
  await page.keyboard.press('Escape')
  await switcher.click()
  await page.getByRole('menuitemradio', { name:'空导航组', exact:true }).click()
  await expect(sidebar.getByRole('link', { name:'系统参数', exact:true })).toHaveCount(0)
  Object.assign(api.me, { navigation: [] })
  await page.reload()
  await expect(switcher).toHaveText(/全部功能/)
  await expect(sidebar.getByRole('link', { name:'系统参数', exact:true })).toBeVisible()
  expect(api.unmatched).toEqual([])
})

test('mobile navigation groups are reachable and selecting menus keeps navigation and work tabs independent', async ({ page }) => {
  const api = await fullApi(page)
  Object.assign(api.me, { navigation: [{ id:'people', name:'组织与成员', menuIds:['users','roles'] }] })
  await page.setViewportSize({ width:390, height:844 })
  await login(page, '/')
  await page.getByRole('button', { name:'展开导航', exact:true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('button', { name:'切换导航组', exact:true }).click()
  await page.getByRole('menuitemradio', { name:'组织与成员', exact:true }).click()
  await dialog.getByRole('link', { name:'用户管理', exact:true }).click()
  await expect(dialog).toHaveCount(0)
  await expect(page.locator('main h1')).toHaveText('用户管理')
  await expect(page.locator('[data-scroll-region=work-tabs]')).toContainText('用户管理')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  expect(api.unmatched).toEqual([])
})
