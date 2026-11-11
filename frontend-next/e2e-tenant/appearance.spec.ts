import { expect, test } from '@playwright/test'
import { tenantEntry } from '../e2e/tenant-entry'

test('guest skin choices follow tenant policy, stay isolated and survive a temporary policy restriction', async ({ page }, info) => {
  test.skip(info.project.name !== 'next', 'Personal skins are a frontend-next feature.')
  const entry = tenantEntry()
  entry.branding.appearance = { primaryColor: '', defaultSkin: 'amber', allowPersonalSkin: true }
  await page.addInitScript(() => { localStorage.setItem('sma.locale', 'zh-CN') })
  await page.route('**/api/tenant/entry', route => route.fulfill({ json: { code: 0, data: entry } }))
  await page.goto('/login')
  await expect(page.locator('html')).toHaveAttribute('data-skin', 'amber')
  await page.getByRole('button', { name: '皮肤', exact: true }).click()
  await page.getByRole('menuitemradio', { name: '青瓷绿', exact: true }).click()
  await expect(page.locator('html')).toHaveAttribute('data-skin', 'celadon')
  entry.branding.appearance.allowPersonalSkin = false
  await page.reload()
  await expect(page.getByRole('button', { name: '皮肤', exact: true })).toBeDisabled()
  await expect(page.locator('html')).toHaveAttribute('data-skin', 'amber')
  await page.getByRole('button', { name: '主题', exact: true }).click()
  await page.getByRole('menuitem', { name: '深色', exact: true }).click()
  await expect(page.locator('html')).toHaveClass(/dark/)
  entry.branding.appearance.allowPersonalSkin = true
  await page.reload()
  await expect(page.locator('html')).toHaveAttribute('data-skin', 'celadon')
  entry.entry.tenantId = '2'; entry.branding.appearance.defaultSkin = 'iris'
  await page.reload()
  await expect(page.locator('html')).toHaveAttribute('data-skin', 'iris')
  await expect(page.locator('html')).toHaveClass(/dark/)
})
