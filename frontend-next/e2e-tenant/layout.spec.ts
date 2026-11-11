import { expect, test } from '@playwright/test'
import { tenantEntry } from '../e2e/tenant-entry'

for (const template of ['login1', 'login2', 'login3'] as const) {
  for (const theme of ['light', 'dark']) {
    test(`${template} ${theme}: responsive published brand and shared MFA`, async ({ page }, info) => {
      await page.addInitScript(({ theme }) => { localStorage.clear(); sessionStorage.clear(); localStorage.setItem('sma.locale', 'zh-CN'); localStorage.setItem('sma.theme', theme) }, { theme })
      const entry = tenantEntry('示例租户 / Sample Tenant', false, 'partner')
      entry.branding.revisionId = '20'
      entry.branding.login = { templateId: template, backgroundBlur: false, title: { zhCN: '欢迎回来', enUS: 'Welcome back' }, description: { zhCN: '在这里开启今天的工作。', enUS: 'Start your work here.' } }
      entry.branding.legal = { copyrightOwner: '示例公司', copyrightStartYear: 2020, icpRecord: '备案示例', icpUrl: 'https://beian.miit.gov.cn', policeRecord: '公安备案示例' }
      entry.branding.links = { privacy: 'https://example.com/privacy' }
      entry.branding.assets = { LOGO_LIGHT: '/api/tenant/brand-images/20/LOGO_LIGHT', LOGO_DARK: '/api/tenant/brand-images/20/LOGO_DARK', FAVICON: '/api/tenant/brand-images/20/FAVICON' }
      let entries = 0, verifications = 0
      await page.route(url => url.pathname.startsWith('/api/'), route => {
        const path = new URL(route.request().url()).pathname
        if (path === '/api/tenant/entry') { entries++; return route.fulfill({ json: { code: 0, data: entry } }) }
        if (path.startsWith('/api/tenant/brand-images/')) return route.fulfill({ contentType: 'image/svg+xml', body: '<svg xmlns="http://www.w3.org/2000/svg" width="80" height="40"><rect width="80" height="40" rx="8" fill="#6b51a3"/><circle cx="40" cy="20" r="10" fill="white"/></svg>' })
        if (path === '/api/auth/login') {
          expect(route.request().postDataJSON().portal).toBe('partner')
          return route.fulfill({ json: { code: 0, data: { status: 'MFA_REQUIRED', challengeToken: 'test-only-challenge', expiresAt: new Date(Date.now() + 120000).toISOString() } } })
        }
        if (path === '/api/auth/login-challenges/verify') {
          verifications++; expect(route.request().postDataJSON()).toEqual({ challengeToken: 'test-only-challenge', code: '123456' })
          return route.fulfill({ status: 400, json: { message: '测试拒绝 / Test rejection' } })
        }
        return route.fulfill({ status: 404 })
      })
      await page.setViewportSize({ width: 1440, height: 1000 })
      await page.goto('/login')
      const layout = page.locator('[data-login-template]')
      await expect(layout).toHaveAttribute('data-login-template', template)
      await expect(page.getByRole('heading', { name: '欢迎回来', exact: true })).toBeVisible()
      await expect(page.getByRole('img', { name: entry.branding.identity.systemName })).toHaveAttribute('src', '/api/tenant/brand-images/20/LOGO_' + theme.toUpperCase())
      await expect(page.locator('.branding-footer')).toContainText('2020')
      await expect(page.getByRole('link', { name: '隐私政策' })).toHaveAttribute('href', 'https://example.com/privacy')
      await page.locator('input[name="username"]').fill('tenant.operator')
      await page.locator('input[name="password"]').fill('Password1')
      await page.screenshot({ path: info.outputPath(`${template}-${theme}-desktop.png`), fullPage: true })
      if (info.project.name === 'next') {
        await page.getByRole('button', { name: '语言与时区', exact: true }).click()
        await page.getByRole('menuitem', { name: 'English', exact: true }).click()
      } else {
        await page.getByRole('combobox', { name: '语言', exact: true }).focus()
        await page.getByRole('combobox', { name: '语言', exact: true }).press('ArrowDown')
        await page.getByRole('option', { name: 'English', exact: true }).click()
      }
      await expect(page.getByRole('heading', { name: 'Welcome back', exact: true })).toBeVisible()
      await expect(page).toHaveTitle(entry.branding.identity.systemName)
      await expect(page.locator('input[name="password"]')).toHaveValue('Password1')
      await page.setViewportSize({ width: 390, height: 844 })
      await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
      await expect(page.locator('input[name="username"]')).toHaveValue('tenant.operator')
      await page.screenshot({ path: info.outputPath(`${template}-${theme}-mobile.png`), fullPage: true })
      await page.locator('button[type="submit"]').focus()
      await page.keyboard.press('Enter')
      await expect(page).toHaveURL(/\/login\/totp/)
      await expect(layout).toHaveAttribute('data-login-template', template)
      expect(entries).toBe(1)
      await page.locator('#totp-code').fill('123456')
      await page.locator('button[type="submit"]').click()
      await expect.poll(() => verifications).toBe(1)
      await expect(page.locator('#totp-code')).toHaveValue('')
      expect(await page.evaluate(() => Object.values(sessionStorage).concat(Object.values(localStorage)).some(value => value.includes('test-only-challenge')))).toBe(false)
    })
  }
}
