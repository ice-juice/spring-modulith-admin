import { chromium } from '@playwright/test'
import { createServer } from 'vite'
import { mkdir, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import assert from 'node:assert/strict'
import { fullApi } from '../e2e/full-api.ts'
import { login } from '../e2e/mock-api.ts'

// Uses the existing isolated mock API, never an authenticated real environment.
const directory = fileURLToPath(new URL('../../docs/frontend-next/images/', import.meta.url))
await mkdir(directory, { recursive:true })
const server = await createServer({ server:{ host:'127.0.0.1', port:5174, strictPort:true } })
await server.listen()
const browser = await chromium.launch({ channel:'chrome', headless:true })
const page = await browser.newPage({ baseURL:'http://127.0.0.1:5174', viewport:{ width:1440, height:1000 }, reducedMotion:'reduce' })
const failures = [], files = []
page.on('pageerror', error => failures.push(error.message))
try {
  const api = await fullApi(page)
  await page.clock.setFixedTime(new Date('2026-11-10T08:00:00Z'))
  await login(page, '/')
  const routes = ['/iam/navigation','/config/dictionaries','/config/parameters','/profile','/preferences','/file/files','/message/inbox','/message/notices','/monitor/server','/monitor/database','/openapi/apps']
  for (const [theme,width] of [['light',1440],['dark',390]]) {
    api.preferences.theme = theme
    await page.setViewportSize({ width, height:1000 })
    for (const route of routes) {
      await page.goto(route, { waitUntil:'networkidle' })
      assert.ok(await page.locator('main h1').isVisible(), route)
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), route)
      const file = `softlight-review-${route.slice(1).replaceAll('/','-')}-${theme}-${width}.png`
      await page.screenshot({ path:directory+file, fullPage:true, animations:'disabled' })
      files.push({ route, theme, width, file })
    }
  }
  assert.deepEqual(failures, [])
  assert.deepEqual(api.unmatched, [])
  await writeFile(new URL('../../docs/frontend-next/softlight-visual-review.json',import.meta.url), JSON.stringify({ capturedAt:new Date().toISOString(), scope:'Actual application routes with isolated mock API; no live business data', files, pageErrors:failures, unmatchedApi:api.unmatched },null,2)+'\n')
  console.log(`Captured ${files.length} actual-route screenshots. No page errors, unmatched API or document overflow.`)
} finally { await browser.close(); await server.close() }
