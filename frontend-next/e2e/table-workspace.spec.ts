import { expect, test } from '@playwright/test'
import { fullApi } from './full-api'
import { login } from './mock-api'
import { ruleFixture } from './rule-fixture'

test('rule list tables keep their custom pagination visible on desktop and mobile', async ({ page }) => {
  const api = await ruleFixture(page)
  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: 844 })
    for (const path of ['/rule/scenes', '/rule/templates', '/rule/instances']) {
      await page.goto(path)
      const table = page.locator('.data-table'), footer = page.locator('.table-workspace-footer')
      await expect(table).toBeVisible()
      await expect(footer).toBeInViewport()
      expect(await table.locator('[data-slot=scroll-area-viewport]').evaluate(el => el.clientHeight), path).toBeGreaterThan(200)
      const box = (await footer.boundingBox())!
      expect(box.y + box.height, path).toBeGreaterThan(804)
      expect(box.y + box.height, path).toBeLessThanOrEqual(845)
      expect(await page.evaluate(() => document.documentElement.scrollHeight <= innerHeight + 1), path).toBe(true)
    }
  }
  expect(api.unmatched).toEqual([])
})

test('200 rows scroll inside a full-height table while header, pagination and page remain stationary', async ({ page }, info) => {
  const api = await fullApi(page)
  let count = 400
  await page.route('**/api/iam/users?*', route => {
    const url = new URL(route.request().url()), size = Number(url.searchParams.get('size') || 20), index = Number(url.searchParams.get('page') || 1)
    return route.fulfill({ json: { code:0, data:{ items:Array.from({length:Math.min(size,count)},(_,i)=>({id:String((index-1)*size+i+1000),username:'member-'+((index-1)*size+i),name:'成员 '+i,enabled:true})),total:String(count),page:String(index),size:String(size) } } })
  })
  await page.setViewportSize({ width:1440, height:900 })
  await login(page, '/iam/users')
  await page.getByRole('combobox', { name:'每页条数', exact:true }).selectOption('200')
  const table=page.locator('.data-table'), viewport=table.locator('[data-slot=scroll-area-viewport]'), pagination=table.locator('.data-table-pagination')
  await expect(table.locator('tbody tr')).toHaveCount(200)
  const height=(await table.boundingBox())!.height, headerTop=(await table.locator('thead').boundingBox())!.y
  await expect(page.getByRole('cell', { name:'member-199', exact:true })).not.toBeInViewport()
  await viewport.hover()
  await page.mouse.wheel(0, 100000)
  await expect.poll(()=>viewport.evaluate(el=>el.scrollTop)).toBeGreaterThan(0)
  await expect(page.getByRole('cell', { name:'member-199', exact:true })).toBeInViewport()
  expect(Math.abs((await table.locator('thead').boundingBox())!.y-headerTop)).toBeLessThan(2)
  await expect(pagination).toBeInViewport()
  expect(await page.evaluate(()=>window.scrollY)).toBe(0)
  expect(await page.evaluate(()=>document.documentElement.scrollHeight<=innerHeight+1)).toBe(true)
  await page.screenshot({ path:'../docs/frontend-next/images/table-internal-200-desktop.png' })
  await page.getByRole('button', { name:'下一页', exact:true }).click()
  await expect(page.getByRole('cell', { name:'member-200', exact:true })).toBeVisible()
  await expect.poll(()=>viewport.evaluate(el=>el.scrollTop)).toBe(0)
  await viewport.focus(); await page.keyboard.press('PageDown')
  await expect.poll(()=>viewport.evaluate(el=>el.scrollTop)).toBeGreaterThan(0)
  count=1
  await page.getByRole('button', { name:'刷新', exact:true }).click()
  await expect(table.locator('tbody tr')).toHaveCount(1)
  expect(Math.abs((await table.boundingBox())!.height-height)).toBeLessThan(2)
  count=0
  await page.getByRole('button', { name:'刷新', exact:true }).click()
  await expect(table.getByText('暂无数据', { exact:true })).toBeVisible()
  expect(Math.abs((await table.boundingBox())!.height-height)).toBeLessThan(2)
  api.preferences.tabsEnabled=false
  await page.reload()
  await expect.poll(async()=>(await table.boundingBox())!.height).toBeGreaterThan(height+35)
  await info.attach('table-workspace-geometry', { body:JSON.stringify({tableHeight:height,rows:200,documentScroll:false,headerSticky:true,paginationVisible:true}),contentType:'application/json' })
})

test('mobile, short windows and CSS zoom keep filters reachable and the table bounded', async ({ page }) => {
  await fullApi(page)
  await login(page, '/iam/users')
  for (const [width,height] of [[390,844],[320,600],[1440,500]]) {
    await page.setViewportSize({width:width!,height:height!})
    const table=page.locator('.data-table'), viewport=table.locator('[data-slot=scroll-area-viewport]')
    await expect(table.locator('.data-table-pagination')).toBeInViewport()
    expect(await viewport.evaluate(el=>el.clientHeight)).toBeGreaterThan(35)
    expect(await page.evaluate(()=>document.documentElement.scrollHeight<=innerHeight+1)).toBe(true)
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBe(true)
    await page.getByRole('button',{name:'新增',exact:true}).click()
    await expect(page.getByRole('dialog').getByRole('button',{name:'保存',exact:true})).toBeInViewport()
    await page.keyboard.press('Escape')
  }
  await page.setViewportSize({width:1440,height:900})
  await page.evaluate(()=>{document.documentElement.style.zoom='2'})
  await expect(page.locator('.data-table-pagination')).toBeInViewport()
  expect(await page.evaluate(()=>document.documentElement.scrollHeight<=innerHeight+1)).toBe(true)
})

test('all list workspaces and nested tabs fill the middle without document scrolling', async ({ page }) => {
  test.setTimeout(120000)
  const api=await fullApi(page)
  await login(page, '/')
  const paths=['/iam/users','/iam/departments','/iam/posts','/iam/permissions','/iam/navigation','/config/dictionaries','/config/parameters','/auth/sessions','/audit/logins','/audit/operations','/file/files','/message/notices','/message/todos','/message/inbox','/message/inbox?tab=tasks','/message/inbox?tab=private','/monitor/sql','/monitor/caches','/monitor/events','/monitor/database','/openapi/apps']
  for(const width of [1440,390]) {
    api.preferences.theme=width===1440?'light':'dark'
    await page.setViewportSize({width,height:900})
    for(const path of paths) {
      await page.goto(path)
      const table=page.locator('.data-table:visible').first()
      await expect(table, path).toBeVisible()
      const box=(await table.boundingBox())!
      expect(box.y+box.height, path).toBeGreaterThan(860)
      expect(box.y+box.height, path).toBeLessThanOrEqual(901)
      expect(await table.locator('[data-slot=scroll-area-viewport]').evaluate(el=>el.clientHeight), path).toBeGreaterThan(35)
      expect(await page.evaluate(()=>document.documentElement.scrollHeight<=innerHeight+1), path).toBe(true)
    }
    await page.goto('/config/parameters')
    await page.getByRole('tab',{name:'参数分组',exact:true}).click()
    await expect(page.locator('.data-table:visible')).toBeInViewport()
    await page.goto('/monitor/database')
    await page.getByRole('tab',{name:'索引',exact:true}).click()
    await expect(page.getByRole('columnheader',{name:'字段列表',exact:true})).toBeVisible()
    await page.screenshot({path:`../docs/frontend-next/images/table-database-indexes-${width}.png`})
  }
  expect(api.unmatched).toEqual([])
})
