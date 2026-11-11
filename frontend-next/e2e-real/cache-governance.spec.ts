import { expect, test } from '@playwright/test'
import type { CacheTask, CacheRegion, CacheConfirmation, CachePolicyState } from '../src/features/monitor/cache'

test('real cache directory, prewarm, node confirmation and policy lifecycle share the existing menu', async ({ page, baseURL, browser }) => {
  test.setTimeout(180000)
  const password = process.env.SMA_E2E_ADMIN_PASSWORD
  if (!password) throw new Error('Requires isolated real E2E runner')
  await page.goto('/login')
  await page.getByRole('textbox', { name: '用户名', exact: true }).fill('admin')
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL(url => url.pathname !== '/login')
  await page.goto('/monitor/caches')
  const token = await page.evaluate(() => sessionStorage.getItem('sma.access-token') || localStorage.getItem('sma.access-token'))
  const headers = { Authorization: 'Bearer ' + token, Origin: baseURL! }
  const request = async <T>(path: string): Promise<T> => {
    const result = await page.request.get('/api/monitor/caches' + path, { headers })
    expect(result.ok()).toBe(true); const body = await result.json(); expect(body.code).toBe(0); return body.data
  }
  const regions = await request<CacheRegion[]>('/directory'), dictionary = regions.find(row => row.id === 'config.dictionary-options')!
  expect(dictionary.prewarming?.available).toBe(true)
  expect(typeof dictionary.observation!.control!.version).toBe('string')
  await expect(page.locator('tr[data-row-id="config.dictionary-options"]')).toBeVisible()
  await page.locator('tr[data-row-id="config.dictionary-options"]').getByRole('button', { name: '预热', exact: true }).click()
  const preview = page.getByRole('dialog')
  await preview.getByLabel('操作原因', { exact: true }).fill('隔离浏览器验证字典预热')
  await preview.getByRole('button', { name: '预览目标', exact: true }).click()
  await expect(preview.getByText(/每页最多/)).toBeVisible()
  await preview.getByRole('button', { name: '确认提交', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '缓存任务详情' })).toBeVisible()
  const warmTask = (await request<CacheTask[]>('/tasks')).find(row => row.action === 'PREWARM')!
  await expect.poll(async () => (await request<CacheTask>('/tasks/' + warmTask.id)).state, { timeout: 45000 }).toBe('SUCCEEDED')
  const warmResult = await request<CacheTask>('/tasks/' + warmTask.id)
  expect(BigInt(warmResult.items[0]!.warmup!.checkpointedReads)).toBeGreaterThan(0n)
  expect(warmResult.items[0]!.warmup!.processId).toBeTruthy()
  console.log('P08 real: prewarm completed with persisted read progress')
  await page.getByRole('dialog').getByRole('button', { name: '刷新', exact: true }).click()
  await expect(page.getByRole('dialog').getByText('逻辑完成', { exact: true })).toBeVisible()
  await page.keyboard.press('Escape')
  await page.getByRole('tab', { name: '缓存目录', exact: true }).click()
  await page.locator('tr[data-row-id="config.dictionary-options"]').getByRole('button', { name: '清理', exact: true }).click()
  await page.getByRole('dialog').getByLabel('操作原因', { exact: true }).fill('隔离浏览器验证失效确认')
  await page.getByRole('dialog').getByRole('button', { name: '预览目标', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '确认提交', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '缓存任务详情' })).toBeVisible()
  const clearTask = (await request<CacheTask[]>('/tasks')).find(row => row.action === 'CLEAR')!
  await expect.poll(async () => (await request<CacheConfirmation>('/tasks/' + clearTask.id + '/confirmation')).allTargetsConfirmed, { timeout: 45000 }).toBe(true)
  console.log('P08 real: clear applied and node confirmation persisted')
  await page.getByRole('dialog').getByRole('button', { name: '刷新', exact: true }).click()
  await expect(page.getByRole('dialog').getByText('全部目标已确认', { exact: true })).toBeVisible()
  await page.keyboard.press('Escape')
  await page.getByRole('tab', { name: '缓存策略', exact: true }).click()
  await page.getByLabel('缓存区域', { exact: true }).selectOption('config.dictionary-options')
  await expect(page.getByRole('button', { name: '编辑策略', exact: true })).toBeVisible({ timeout: 10000 })
  await page.getByRole('button', { name: '编辑策略', exact: true }).click()
  await page.getByRole('dialog').getByLabel('有效期（毫秒）', { exact: true }).fill('30000')
  await page.getByRole('dialog').getByLabel('操作原因', { exact: true }).fill('隔离浏览器验证运行策略')
  await page.getByRole('dialog').getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  const updated = await request<CachePolicyState>('/config.dictionary-options/policy')
  expect(updated.requestedPolicy).not.toBeNull()
  await page.getByRole('button', { name: '编辑策略', exact: true }).click()
  await page.getByRole('dialog').getByLabel('操作原因', { exact: true }).fill('恢复部署策略')
  await page.getByRole('dialog').getByRole('button', { name: '恢复部署策略', exact: true }).click()
  const resetResponse = page.waitForResponse(response => new URL(response.url()).pathname === '/api/monitor/caches/config.dictionary-options/policy/reset' && response.request().method() === 'POST')
  await page.getByRole('alertdialog').getByRole('button', { name: '确认', exact: true }).click()
  expect((await (await resetResponse).json()).code).toBe(0)
  await expect(page.getByRole('dialog', { includeHidden: true })).toHaveCount(0)
  expect((await request<CachePolicyState>('/config.dictionary-options/policy')).requestedPolicy).toBeNull()
  console.log('P08 real: policy saved and deployment policy restored')
  if (process.env.SMA_CACHE_TASKS_RESOURCES_ENABLED === 'true') {
    await page.getByRole('tab', { name: '缓存目录', exact: true }).click()
    const sql = page.locator('tr[data-row-id="common.sql-statistics"]')
    await sql.getByRole('checkbox').check()
    await page.getByRole('button', { name: '重置统计', exact: true }).click()
    await page.getByRole('dialog').getByLabel('操作原因', { exact: true }).fill('隔离浏览器验证绑定进程的原生统计重置')
    await page.getByRole('dialog').getByRole('button', { name: '预览目标', exact: true }).click()
    await expect(page.getByRole('dialog').getByText('可执行目标：1 个区域')).toBeVisible()
    await expect(page.getByRole('dialog').getByText(/仅在节点.*的进程.*执行/)).toBeVisible()
    await page.getByRole('dialog').getByRole('button', { name: '确认提交', exact: true }).click()
    await expect(page.getByRole('dialog', { name: '缓存任务详情' })).toBeVisible()
    const native = (await request<CacheTask[]>('/tasks')).find(row => row.action === 'RESET_STATS')!
    expect(native.targets[0]!.resource?.processId).toBeTruthy()
    await expect.poll(async () => (await request<CacheTask>('/tasks/' + native.id)).state, { timeout: 45000 }).toBe('SUCCEEDED')
    const result = await request<CacheTask>('/tasks/' + native.id)
    expect(result.items[0]!.resource?.processId).toBe(native.targets[0]!.resource?.processId)
    expect(result.items[0]!.resource?.completedAt).toBeTruthy()
    expect((await request<CacheConfirmation>('/tasks/' + native.id + '/confirmation')).coverage).toBe('NOT_APPLICABLE_RESOURCE')
    await page.getByRole('dialog').getByRole('button', { name: '刷新', exact: true }).click()
    await expect(page.getByRole('dialog').getByText('节点原生操作，以逐项结果为准，不作全节点失效确认')).toBeVisible()
    await page.keyboard.press('Escape')
    console.log('P08 real: native SQL reset completed on the frozen process without global confirmation claims')
  }
  // Anonymous and unrelated views do not gain metadata access through the menu refactor.
  const anonymous = await browser.newContext({ baseURL })
  try { expect((await anonymous.request.get('/api/monitor/caches/directory')).status()).toBe(401) } finally { await anonymous.close() }
  if (process.env.SMA_E2E_LEGACY === '1') {
    const legacy = await browser.newContext({ baseURL: 'http://127.0.0.1:' + (process.env.SMA_E2E_LEGACY_PORT || '5188') })
    try {
      const old = await legacy.newPage(); await old.goto('/login')
      await old.getByRole('textbox', { name: '用户名', exact: true }).fill('admin'); await old.getByLabel('密码', { exact: true }).fill(password)
      await old.getByRole('button', { name: '登录', exact: true }).click(); await old.waitForURL(url => url.pathname !== '/login')
      await old.goto('/monitor/caches'); await expect(old.getByText('字典选项缓存', { exact: true })).toBeVisible()
    } finally { await legacy.close() }
  }
})
