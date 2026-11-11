import { expect, test } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { fullApi } from './full-api'
import { login } from './mock-api'

test('skin and theme persist independently, system follows the device, and failed writes roll back', async ({ page }) => {
  const api = await fullApi(page)
  await login(page, '/iam/departments')
  const header = page.locator('header')
  await header.getByRole('button', { name: '皮肤', exact: true }).click()
  await page.getByRole('menuitemradio', { name: '青瓷绿', exact: true }).click()
  await expect.poll(() => api.preferences.skin).toBe('celadon')
  await expect(page.locator('html')).toHaveAttribute('data-skin', 'celadon')
  await header.getByRole('button', { name: '主题', exact: true }).click()
  await page.getByRole('menuitem', { name: '深色', exact: true }).click()
  await expect(page.locator('html')).toHaveClass(/dark/)
  await expect(page.locator('html')).toHaveAttribute('data-skin', 'celadon')
  await page.reload()
  await expect(page.locator('html')).toHaveAttribute('data-skin', 'celadon')
  await expect(page.locator('html')).toHaveClass(/dark/)
  await header.getByRole('button', { name: '主题', exact: true }).click()
  await page.getByRole('menuitem', { name: '跟随系统', exact: true }).click()
  await page.emulateMedia({ colorScheme: 'light' })
  await expect(page.locator('html')).not.toHaveClass(/dark/)
  await page.emulateMedia({ colorScheme: 'dark' })
  await expect(page.locator('html')).toHaveClass(/dark/)
  await expect(page.locator('html')).toHaveAttribute('data-skin', 'celadon')
  await page.route('**/api/config/preferences', async route => {
    if (route.request().method() === 'PUT') return route.fulfill({ status: 500, json: { code: 500, message: 'Skin save rejected', data: null } })
    return route.fallback()
  })
  await header.getByRole('button', { name: '皮肤', exact: true }).click()
  await page.getByRole('menuitemradio', { name: '琥珀棕', exact: true }).click()
  await expect(page.getByText('Skin save rejected', { exact: true })).toBeVisible()
  await expect(page.locator('html')).toHaveAttribute('data-skin', 'celadon')
  expect(api.preferences.theme).toBe('system')
  expect(api.preferences.skin).toBe('celadon')
  expect(api.unmatched).toEqual([])
})

test('personal preferences edit and reset skin; a different account without skin returns to iris', async ({ page }) => {
  const api = await fullApi(page)
  await login(page, '/preferences')
  await page.locator('#preference-skin').click()
  await page.getByRole('button', { name: '琥珀棕', exact: true }).click()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => api.preferences.skin).toBe('amber')
  await page.reload()
  await expect(page.locator('#preference-skin')).toContainText('琥珀棕')
  await page.getByRole('button', { name: '恢复默认', exact: true }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.locator('html')).toHaveAttribute('data-skin', 'iris')
  api.preferences.skin = 'celadon'
  await page.reload()
  await expect(page.locator('html')).toHaveAttribute('data-skin', 'celadon')
  api.me.id = '202'
  // An older response has no skin: it must not inherit the previous account's choice.
  await page.route('**/api/auth/me', route => {
    return route.fulfill({ json: { code: 0, data: { ...api.me, ...api.preferences, skin: undefined } } })
  })
  await page.goto('/iam/departments')
  await expect(page.locator('html')).toHaveAttribute('data-skin', 'iris')
})

test('all skins remain readable across themes and viewport widths without tinting the light workspace', async ({ page }) => {
  test.setTimeout(120000)
  const api = await fullApi(page)
  await login(page, '/iam/departments')
  const geometry: Record<string, number> = {}
  for (const skin of ['iris', 'celadon', 'amber']) {
    for (const theme of ['light', 'dark']) {
      api.preferences.skin = skin
      api.preferences.theme = theme
      for (const width of [1440, 390, 320]) {
        await page.setViewportSize({ width, height: 900 })
        await page.goto('/iam/departments')
        await expect(page.locator('.data-table')).toBeVisible()
        await expect(page.locator('html')).toHaveAttribute('data-skin', skin)
        const height = (await page.locator('.data-table').boundingBox())!.height
        if (skin === 'iris') geometry[`${theme}-${width}`] = height
        else expect(Math.abs(height - geometry[`${theme}-${width}`]!)).toBeLessThan(2)
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1 && document.documentElement.scrollHeight <= innerHeight + 1)).toBe(true)
        if (theme === 'light') expect(await page.locator('.admin-body').evaluate(el => getComputedStyle(el).backgroundImage)).toBe('linear-gradient(rgb(246, 247, 250), rgb(246, 247, 250))')
        await page.locator('header').getByRole('button', { name: '皮肤', exact: true }).click()
        await expect(page.getByRole('menuitemradio', { checked: true })).toHaveCount(1)
        const accessibility = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()
        expect(accessibility.violations.map(v => ({ id: v.id, nodes: v.nodes.map(n => n.target) })), `${skin}/${theme}/${width}`).toEqual([])
        if (width !== 320) await page.screenshot({ path: `../docs/frontend-next/images/skin-${skin}-${theme}-${width}.png` })
        await page.keyboard.press('Escape')
      }
    }
  }
  expect(api.unmatched).toEqual([])
})
