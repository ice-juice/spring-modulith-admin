import { expect, test } from '@playwright/test'
import { fullApi } from './full-api'

// Synthetic enrollment only: never creates an authenticator on the running service.
for (const scenario of [
  { width: 1440, locale: 'zh-CN', theme: 'light' },
  { width: 1280, locale: 'zh-CN', theme: 'dark' },
  { width: 768, locale: 'zh-CN', theme: 'light' },
  { width: 390, locale: 'en-US', theme: 'dark' },
] as const) {
  test(`security layout ${scenario.width} ${scenario.locale} ${scenario.theme}`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width: scenario.width, height: 900 })
    const api = await fullApi(page)
    Object.assign(api.preferences, { locale: scenario.locale, theme: scenario.theme })
    const errors: string[] = [], securityWrites: string[] = []
    page.on('pageerror', error => errors.push(error.message))
    await page.addInitScript(() => sessionStorage.setItem('sma.access-token', 'isolated-layout-token'))
    await page.route('**/api/auth/security-settings', route => route.fulfill({ json: { code: 0, data: {
      totpEnabled: false, totpPending: false, securityPasswordSet: false, setupAvailable: true,
      totpEnabledAt: null, securityPasswordChangedAt: null,
    } } }))
    await page.route('**/api/auth/totp-enrollments**', route => {
      securityWrites.push(new URL(route.request().url()).pathname)
      return route.fulfill({ json: { code: 0, data: route.request().url().endsWith('/cancel') ? null : {
        challengeToken: 'synthetic-enrollment', secret: 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP',
        uri: 'otpauth://totp/LayoutTest?secret=JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP&issuer=LayoutTest',
        expiresAt: new Date(Date.now() + 600_000).toISOString(), replacing: false,
      } } })
    })
    await page.goto('/auth/security')
    const en = scenario.locale === 'en-US'
    const workspace = page.locator('.security-workspace'), summary = page.locator('.security-summary')
    await expect(workspace).toBeVisible()
    await page.screenshot({ path: testInfo.outputPath('overview.png'), fullPage: true })
    await page.getByRole('button', { name: en ? 'Set up authenticator' : '绑定验证器', exact: true }).click()
    await page.locator('#security-login-password').fill('SyntheticPassword9')
    await workspace.getByRole('button', { name: en ? 'Confirm and save' : '确认保存', exact: true }).click()
    await expect(page.getByRole('img', { name: en ? 'Authenticator setup QR code' : '验证器绑定二维码' })).toBeVisible()
    const left = await summary.boundingBox(), right = await workspace.boundingBox()
    expect(left).not.toBeNull(); expect(right).not.toBeNull()
    if (scenario.width >= 1280) {
      expect(right!.x).toBeGreaterThanOrEqual(left!.x + left!.width)
      expect(Math.abs(right!.y - left!.y)).toBeLessThan(2)
      const qrBox = await page.locator('.security-qr').boundingBox()
      const inputBox = await page.locator('#manual-secret').boundingBox()
      expect(inputBox!.x).toBeGreaterThan(qrBox!.x + qrBox!.width)
    } else {
      expect(right!.y).toBeLessThan(left!.y)
    }
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.screenshot({ path: testInfo.outputPath('enrollment.png'), fullPage: true })
    await page.locator('#new-authenticator-code').fill('123456')
    await page.locator('#new-authenticator-code').press('Tab')
    await expect(workspace.getByRole('button', { name: en ? 'Confirm enrollment' : '确认绑定', exact: true })).toBeFocused()
    expect(await page.evaluate(() => JSON.stringify([localStorage, sessionStorage]))).not.toContain('JBSWY3DPEHPK3PXP')
    await workspace.getByRole('button', { name: en ? 'Cancel enrollment' : '取消绑定', exact: true }).click()
    await expect(page.locator('#manual-secret')).toHaveCount(0)
    await page.getByRole('button', { name: en ? 'Set security password' : '设置安全操作密码', exact: true }).click()
    await expect(page.locator('#security-new')).toBeVisible()
    await expect(page.locator('#security-confirm')).toBeVisible()
    await page.screenshot({ path: testInfo.outputPath('security-password.png'), fullPage: true })
    expect(securityWrites).toEqual(['/api/auth/totp-enrollments', '/api/auth/totp-enrollments/cancel'])
    expect(api.unmatched).toEqual([])
    expect(errors).toEqual([])
  })
}
