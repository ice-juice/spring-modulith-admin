import { expect, test } from '@playwright/test'

test('production login escapes branding and rejects untrusted background/link values', async ({ page }) => {
  const name = '<img src=x onerror="document.documentElement.dataset.exploited=1">'
  const requests: string[] = []
  page.on('request', request => { if (request.url().includes('attacker.invalid')) requests.push(request.url()) })
  await page.route(url => url.pathname.startsWith('/api/'), route => {
    if (route.request().url().endsWith('/config/parameters/public')) return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 0, data: {
      'system.name': name, 'login.captchaEnabled': 'false', 'login.backgroundUrl': '//attacker.invalid/pixel',
      'system.copyrightCompany': 'Safe company', 'system.copyrightUrl': 'javascript:alert(1)',
    } }) })
    return route.fulfill({ status: 401, contentType: 'application/json', body: '{"code":401,"message":"Unauthorized"}' })
  })
  const response = await page.goto('/login')
  expect(response?.headers()['content-security-policy-report-only']).toContain("frame-ancestors 'self'")
  expect(response?.headers()['x-frame-options']).toBe('SAMEORIGIN')
  await expect(page).toHaveTitle(name)
  await expect(page.getByText(name, { exact: true }).first()).toBeVisible()
  await expect(page.locator('img[onerror]')).toHaveCount(0)
  await expect(page.locator('a[href^="javascript:"]')).toHaveCount(0)
  await expect(page.locator('html')).not.toHaveAttribute('data-exploited')
  expect(requests).toEqual([])
})
