import { expect, test } from '@playwright/test'

test('serves HTML security headers and renders hostile rich content without execution or tracking', async ({ page }) => {
  const leaks: string[] = []
  page.on('request', request => { if (request.url().includes('attacker.invalid')) leaks.push(request.url()) })
  await page.route(url => url.pathname.startsWith('/api/'), route => route.fulfill({ status: 401, contentType: 'application/json', body: '{"code":401,"message":"Unauthorized"}' }))
  const response = await page.goto('/login')
  expect(response?.headers()['content-security-policy-report-only']).toContain("object-src 'none'")
  expect(response?.headers()['x-content-type-options']).toBe('nosniff')
  await page.evaluate(async () => {
    const modulePath = '/src/lib/safe-html.ts'
    const { sanitizeRichHtml } = await import(modulePath)
    const probe = document.createElement('div'); probe.id = 'security-probe'
    probe.innerHTML = sanitizeRichHtml('<p>正常<strong>内容</strong></p><script>document.documentElement.dataset.exploited=1</script><img src="https://attacker.invalid/pixel" onerror="document.documentElement.dataset.exploited=1"><svg onload="document.documentElement.dataset.exploited=1"></svg><form><input></form><a href="javascript:document.documentElement.dataset.exploited=1">bad</a><a href="//attacker.invalid">relative</a><a href="https://example.test" target="_blank">good</a><iframe srcdoc="<script>parent.document.documentElement.dataset.exploited=1</script>"></iframe>')
    document.body.append(probe)
  })
  await expect(page.locator('#security-probe strong')).toHaveText('内容')
  await expect(page.locator('#security-probe img, #security-probe iframe, #security-probe svg, #security-probe script, #security-probe form, #security-probe input')).toHaveCount(0)
  await expect(page.locator('#security-probe a').nth(0)).not.toHaveAttribute('href')
  await expect(page.locator('#security-probe a').nth(1)).not.toHaveAttribute('href')
  await expect(page.locator('#security-probe a').nth(2)).toHaveAttribute('href', 'https://example.test')
  await expect(page.locator('#security-probe a').nth(2)).toHaveAttribute('rel', 'noopener noreferrer')
  await expect(page.locator('html')).not.toHaveAttribute('data-exploited')
  expect(leaks).toEqual([])
})

test('rejects unsafe navigation and keeps valid local and external links', async ({ page }) => {
  await page.route(url => url.pathname.startsWith('/api/'), route => route.fulfill({ status: 401, contentType: 'application/json', body: '{"code":401,"message":"Unauthorized"}' }))
  await page.goto('/login')
  const values = await page.evaluate(async () => {
    const modulePath = '/src/lib/web-addresses.ts'
    const { safeBrowserDestination } = await import(modulePath)
    return ['//attacker.invalid', '/\\attacker.invalid', 'javascript:alert(1)', 'https://trusted@attacker.invalid', '/iam/users?page=2', 'https://example.test/help'].map(value => safeBrowserDestination(value) ?? null)
  })
  expect(values).toEqual([null, null, null, null, '/iam/users?page=2', 'https://example.test/help'])
})
