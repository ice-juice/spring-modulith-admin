import { chromium } from '@playwright/test'
import { mkdir, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import assert from 'node:assert/strict'

// Dedicated visual prototype; never connects to the application's API.
const output = fileURLToPath(new URL('../../docs/frontend-next/design-review-softlight/images/', import.meta.url))
await mkdir(output, { recursive: true })
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const context = await browser.newContext({ viewport: { width: 1440, height: 1024 }, deviceScaleFactor: 1, reducedMotion: 'reduce' })
const page = await context.newPage()
const errors = [], requests = [], captures = []
page.on('pageerror', error => errors.push(error.message))
page.on('request', request => { if (request.url().includes('/api/')) requests.push(request.url()) })
try {
  for (const [screen, theme, width, lang] of [
    ['workspace','light',1440,'zh'], ['users','light',1440,'zh'], ['roles','light',1440,'zh'], ['login','light',1440,'zh'],
    ['workspace','dark',1440,'zh'], ['users','dark',1440,'en'], ['roles','dark',1440,'en'], ['login','dark',1440,'zh'],
    ['workspace','light',390,'zh'], ['users','light',390,'zh'], ['roles','dark',390,'en'], ['login','light',390,'zh'],
  ]) {
    await page.setViewportSize({ width, height: width < 500 ? 844 : 1024 })
    await page.goto(`http://127.0.0.1:5186/?page=${screen}&theme=${theme}&lang=${lang}`, { waitUntil: 'networkidle' })
    const dimensions = await page.evaluate(() => ({ body: document.documentElement.scrollWidth, viewport: innerWidth }))
    assert.ok(dimensions.body <= dimensions.viewport, `Horizontal page overflow: ${JSON.stringify({screen,theme,width,lang,...dimensions})}`)
    const filename = `${screen}-${theme}-${lang}-${width}.png`
    await page.screenshot({ path: `${output}/${filename}`, fullPage: true })
    captures.push(filename)
  }
  await page.setViewportSize({ width: 1440, height: 1024 })
  await page.goto('http://127.0.0.1:5186/?page=users', { waitUntil: 'networkidle' })
  await page.getByRole('textbox', { name:'搜索成员', exact:true }).fill('陈序')
  assert.equal(await page.locator('tbody tr').count(), 1)
  assert.ok((await page.locator('tbody').innerText()).includes('陈序'))
  await page.getByRole('textbox', { name:'搜索成员', exact:true }).fill('no-such-person')
  assert.ok((await page.locator('tbody').innerText()).includes('没有匹配'))
  await page.getByRole('textbox', { name:'搜索成员', exact:true }).fill('')
  await page.getByRole('checkbox', { name:'选择当前结果', exact:true }).check()
  assert.ok(await page.getByText('已选 6 人', { exact:true }).isVisible())
  await page.getByRole('button', { name:'添加成员', exact:true }).click()
  assert.ok(await page.locator('dialog').isVisible())
  await page.keyboard.press('Escape')
  assert.ok(!(await page.locator('dialog').isVisible()))
  await page.locator('.preview-pages').getByRole('button', { name:'角色与权限', exact:true }).click()
  const before = await page.locator('.permission-meta').innerText()
  await page.getByRole('checkbox', { name:'编辑用户', exact:true }).check()
  assert.notEqual(await page.locator('.permission-meta').innerText(), before)
  await page.getByRole('button', { name:'保存权限', exact:true }).click()
  assert.ok(await page.getByRole('status').isVisible())
  await page.getByRole('button', { name:'切换明暗主题', exact:true }).click()
  assert.equal(await page.locator('html').getAttribute('class'), 'dark')
  await page.getByRole('combobox', { name:'品牌配色', exact:true }).selectOption('jade')
  assert.equal(await page.locator('html').getAttribute('data-tone'), 'jade')
  await page.getByRole('combobox', { name:'品牌配色', exact:true }).selectOption('copper')
  assert.equal(await page.locator('html').getAttribute('data-tone'), 'copper')
  await page.setViewportSize({ width:390, height:844 })
  await page.getByRole('button', { name:'展开导航', exact:true }).click()
  assert.ok(await page.locator('.sidebar').evaluate(el => el.getBoundingClientRect().left >= 0))
  await page.getByRole('button', { name:'关闭导航', exact:true }).click()
  assert.deepEqual(errors, [])
  assert.deepEqual(requests, [])
  const report = { scope:'Standalone design prototype only; no backend integration', capturedAt: new Date().toISOString(), screenshots:captures, interactions:['member search and empty state','select visible members','native dialog open / Escape close','permission edit and demo save','light / dark','jade / copper palettes','mobile navigation'], horizontalOverflow:false, pageErrors:errors, apiRequests:requests }
  await writeFile(`${output}/../validation.json`, JSON.stringify(report, null, 2))
  console.log(JSON.stringify(report, null, 2))
} finally { await browser.close() }
