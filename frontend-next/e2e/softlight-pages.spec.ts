import { expect, test } from '@playwright/test'
import { tenantEntry } from './tenant-entry'
import AxeBuilder from '@axe-core/playwright'
import { fullApi } from './full-api'
import { login } from './mock-api'

test('role workspace guards unsaved changes, retains failed writes and ignores late role responses', async ({ page }) => {
  const api = await fullApi(page)
  let failSave = true
  await page.route('**/api/iam/roles', route => route.fulfill({ json: { code: 0, data: [
    { id:'role-a', name:'角色甲', code:'A', enabled:true, dataScope:'SELF' },
    { id:'role-b', name:'角色乙', code:'B', enabled:true, dataScope:'SELF' },
  ] } }))
  await page.route('**/api/iam/roles/*/permissions', async route => {
    if (route.request().method() === 'PUT') return route.fulfill({ status: failSave ? 500 : 200, json: failSave ? { code:500, message:'模拟授权保存失败' } : { code:0, data:null } })
    const first = route.request().url().includes('role-a')
    if (first) await new Promise(resolve => setTimeout(resolve, 400))
    await route.fulfill({ json: { code:0, data: first ? ['read'] : ['write'] } })
  })
  await login(page, '/iam/roles')
  const panel = page.locator('[data-role-permissions]')
  await page.getByRole('button', { name:'角色乙', exact:true }).click()
  await panel.getByRole('button', { name:'全部展开', exact:true }).click()
  await expect(panel.getByRole('checkbox', { name:'编辑用户', exact:true })).toBeChecked()
  await panel.getByRole('checkbox', { name:'查看用户', exact:true }).check()
  await page.getByRole('button', { name:'角色甲', exact:true }).click()
  const confirmation = page.getByRole('alertdialog')
  await expect(confirmation).toBeVisible()
  await confirmation.getByRole('button', { name:'取消', exact:true }).click()
  await expect(panel.getByRole('heading', { name:'角色乙', exact:true })).toBeVisible()
  await panel.getByRole('button', { name:'保存', exact:true }).click()
  await expect(panel.getByRole('alert')).toContainText('模拟授权保存失败')
  await expect(panel.getByRole('checkbox', { name:'查看用户', exact:true })).toBeChecked()
  failSave = false
  await panel.getByRole('button', { name:'保存', exact:true }).click()
  await expect(panel.getByRole('status')).toHaveText('与上次读取或保存一致')
  expect(api.unmatched).toEqual([])
})

test('production login design keeps captcha, accessible forms, branding and both themes across widths', async ({ page }) => {
  test.setTimeout(90000)
  await fullApi(page)
  await page.route('**/api/tenant/entry', route => route.fulfill({ json: { code:0, data:tenantEntry('品牌工作空间', true) } }))
  await page.route('**/api/auth/captcha', route => route.fulfill({ json: { code:0, data:{ id:'sample-captcha', image:'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="100" height="36"%3E%3Crect width="100" height="36" fill="white"/%3E%3C/svg%3E' } } }))
  for (const locale of ['zh-CN','en-US']) for (const theme of ['light','dark']) {
    await page.addInitScript(({ locale, theme }) => { localStorage.setItem('sma.locale',locale); localStorage.setItem('sma.theme',theme) }, { locale, theme })
    await page.goto('/login')
    await expect(page.locator('#captcha')).toBeVisible()
    await expect(page.locator('#password')).toHaveAttribute('autocomplete','current-password')
    for (const width of [1440,768,390,320]) {
      await page.setViewportSize({ width, height:1000 })
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth+1)).toBe(true)
      if (width === 1440 || width === 390) await page.screenshot({ path:`../docs/frontend-next/images/softlight-login-${locale}-${theme}-${width}.png`, fullPage:true, animations:'disabled' })
    }
    const result = await new AxeBuilder({ page }).withTags(['wcag2a','wcag2aa','wcag21aa','wcag22aa']).analyze()
    expect(result.violations.map(v=>({id:v.id,nodes:v.nodes.map(n=>n.failureSummary)}))).toEqual([])
  }
})
