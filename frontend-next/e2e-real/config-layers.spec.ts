import { expect, test } from '@playwright/test'

test('real tenant configuration UI overrides, inherits and opens the platform publication directory', async ({ page, browser }) => {
  test.setTimeout(180000)
  const password = process.env.SMA_E2E_ADMIN_PASSWORD
  if (!password || process.env.SMA_E2E_PLATFORM_OPERATOR !== '1' || process.env.SMA_E2E_LEGACY !== '1') throw new Error('Use the isolated runner with a task-owned platform operator and both frontends.')

  await page.goto('/config/parameters')
  await page.getByLabel('用户名', { exact: true }).fill('admin')
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL(url => url.pathname !== '/login')
  await page.goto('/config/parameters')

  const theme = page.locator('[data-row-id="ui.theme"]')
  await expect(theme).toBeVisible({ timeout: 30000 })
  await theme.getByRole('button', { name: '覆盖', exact: true }).click()
  await page.getByRole('dialog').getByRole('textbox', { name: '值', exact: true }).fill('dark')
  await page.getByRole('dialog').getByRole('button', { name: '保存覆盖', exact: true }).click()
  await expect(theme).toContainText('本租户覆盖')
  await expect(theme).toContainText('dark')
  await theme.getByRole('button', { name: '恢复继承', exact: true }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect(theme).toContainText('平台共享')
  await expect(theme).toContainText('light')

  await page.goto('/config/dictionaries')
  await expect(page.getByRole('tab', { name: '共享与内置字典', exact: true })).toHaveAttribute('data-state', 'active')
  await expect(page.getByRole('navigation', { name: '字典目录' })).toContainText('sex')
  const sharedDetailResponse = page.waitForResponse(response => /\/api\/config\/shared-configurations\/\d+$/.test(new URL(response.url()).pathname))
  await page.goto('/config/shared')
  const sharedDetail = (await (await sharedDetailResponse).json()).data
  if (sharedDetail.kind === 'PARAMETER') expect(sharedDetail.parameterDefinition.valueType).toMatch(/^(STRING|BOOLEAN|INTEGER|DECIMAL|JSON)$/)
  else expect(sharedDetail.dictionaryDefinition.mode).toMatch(/^(PLATFORM_LOCKED|EXTENDABLE|TENANT_ONLY)$/)
  await expect(page.getByRole('heading', { name: '平台共享配置' })).toBeVisible()
  await expect(page.getByRole('button', { name: '发布', exact: true })).toBeVisible()

  const legacy = await browser.newPage()
  const legacyParametersResponse = legacy.waitForResponse(response => new URL(response.url()).pathname === '/api/config/effective-parameters')
  await legacy.goto('http://127.0.0.1:' + (process.env.SMA_E2E_LEGACY_PORT || '5188') + '/config/parameters')
  await legacy.locator('input[name="username"]').fill('admin')
  await legacy.locator('input[name="password"]').fill(password)
  await legacy.locator('button[type="submit"]').click()
  await legacy.waitForURL(url => url.pathname !== '/login')
  await legacy.goto('http://127.0.0.1:' + (process.env.SMA_E2E_LEGACY_PORT || '5188') + '/config/parameters')
  const legacyParameters = (await (await legacyParametersResponse).json()).data.items
  expect(legacyParameters.every((row: { origin?: string }) => !!row.origin)).toBe(true)
  await expect(legacy.getByText('ui.theme', { exact: true })).toBeVisible({ timeout: 30000 })
  await expect(legacy.getByText('平台共享', { exact: true }).first()).toBeVisible()
  await legacy.close()
})
