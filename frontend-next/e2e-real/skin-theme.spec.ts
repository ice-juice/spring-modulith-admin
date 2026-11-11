import { expect, test } from '@playwright/test'

test('skin is saved by the real preference API and returned after a fresh login', async ({ page, browser, baseURL }) => {
  await page.goto('/login')
  await page.getByRole('textbox', { name: '用户名', exact: true }).fill('admin')
  await page.getByLabel('密码', { exact: true }).fill(process.env.SMA_E2E_ADMIN_PASSWORD!)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL(url => url.pathname !== '/login')
  await page.locator('header').getByRole('button', { name: '皮肤', exact: true }).click()
  await page.getByRole('menuitemradio', { name: '青瓷绿', exact: true }).click()
  await expect(page.locator('header').getByRole('button', { name: '皮肤', exact: true })).toBeEnabled()
  const token = await page.evaluate(() => sessionStorage.getItem('sma.access-token') || localStorage.getItem('sma.access-token'))
  const headers = { Authorization: 'Bearer ' + token }
  const read = async () => (await (await page.request.get(baseURL + '/api/config/preferences', { headers })).json()).data
  await expect.poll(async () => (await read()).skin).toBe('celadon')
  const saved = await read()
  const legacy = { locale: saved.locale, timeZone: saved.timeZone, theme: 'dark', tabsEnabled: saved.tabsEnabled, keepTabsAfterReload: saved.keepTabsAfterReload }
  const oldWrite = await page.request.put(baseURL + '/api/config/preferences', { headers, data: legacy })
  expect((await oldWrite.json()).data.skin).toBe('celadon')
  const invalid = await page.request.put(baseURL + '/api/config/preferences', { headers, data: { ...legacy, skin: 'invalid' } })
  expect(invalid.status()).toBe(400)
  expect((await read()).skin).toBe('celadon')
  const context = await browser.newContext({ baseURL, locale: 'zh-CN' })
  try {
    const fresh = await context.newPage()
    await fresh.goto('/login')
    await fresh.getByRole('textbox', { name: '用户名', exact: true }).fill('admin')
    await fresh.getByLabel('密码', { exact: true }).fill(process.env.SMA_E2E_ADMIN_PASSWORD!)
    await fresh.getByRole('button', { name: '登录', exact: true }).click()
    await fresh.waitForURL(url => url.pathname !== '/login')
    await expect(fresh.locator('html')).toHaveAttribute('data-skin', 'celadon')
    await expect(fresh.locator('html')).toHaveClass(/dark/)
    await fresh.locator('header').getByRole('button', { name: '皮肤', exact: true }).click()
    await fresh.getByRole('menuitemradio', { name: '琥珀棕', exact: true }).click()
    await expect(fresh.locator('header').getByRole('button', { name: '皮肤', exact: true })).toBeEnabled()
    await fresh.reload()
    await expect(fresh.locator('html')).toHaveAttribute('data-skin', 'amber')
    await expect(fresh.locator('html')).toHaveClass(/dark/)
  } finally { await context.close() }
})
