import { expect, test } from '@playwright/test'
import { fullApi } from './full-api'
import { login } from './mock-api'

test('actual workspace, users and roles: both languages, themes and six widths with reviewable screenshots', async ({ page }) => {
  test.setTimeout(180000)
  const api = await fullApi(page), errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await page.clock.setFixedTime(new Date('2026-11-10T08:00:00Z'))
  await login(page, '/')
  for (const locale of ['zh-CN', 'en-US']) {
    api.preferences.locale = locale
    for (const theme of ['light', 'dark']) {
      api.preferences.theme = theme
      for (const [slug, path] of [['workspace', '/'], ['users', '/iam/users'], ['roles', '/iam/roles']]) {
        await page.goto(path!)
        await expect(page.locator('main h1')).toBeVisible()
        for (const width of [1920, 1440, 1024, 768, 390, 320]) {
          await page.setViewportSize({ width, height: 1000 })
          expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), `${path} ${locale} ${theme} ${width}`).toBe(true)
          if (width === 1440 || width === 390) await page.screenshot({ path: `../docs/frontend-next/images/actual-${slug}-${locale}-${theme}-${width}.png`, fullPage: true, animations: 'disabled' })
        }
      }
    }
  }
  expect(errors).toEqual([])
})
