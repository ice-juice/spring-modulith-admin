import { expect, test } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { fullApi } from './full-api'
import { login } from './mock-api'

test('representative pages satisfy automated WCAG AA checks in both themes and languages', async ({ page }) => {
  test.setTimeout(120000)
  const api = await fullApi(page)
  await login(page, '/')
  for (const locale of ['zh-CN', 'en-US']) {
    api.preferences.locale = locale
    for (const theme of ['light', 'dark']) {
      api.preferences.theme = theme
      for (const path of ['/', '/iam/users', '/iam/roles']) {
        await page.goto(path)
        await expect(page.locator('main h1')).toBeVisible()
        await expect(page.locator('main [role=alert]')).toHaveCount(0)
        await expect(page.locator('main [aria-busy=true]')).toHaveCount(0)
        await page.waitForFunction(() => document.getAnimations().every(animation => animation.playState !== 'running'))
        const result = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa']).analyze()
        expect(result.violations.map(v => ({ id: v.id, nodes: v.nodes.map(n => ({ target: n.target, reason: n.failureSummary })) })), `${locale} ${theme} ${path}`).toEqual([])
      }
    }
  }
})

test('mobile row-action menu can open and close an editor, and message tabs keep their URL', async ({ page }) => {
  await fullApi(page)
  await page.setViewportSize({ width: 390, height: 844 })
  await login(page, '/iam/users')
  await page.getByRole('button', { name: '操作', exact: true }).click()
  await page.getByRole('button', { name: '编辑', exact: true }).click()
  const editor = page.getByRole('dialog', { name: '编辑 · 用户管理', exact: true })
  await expect(editor).toBeVisible()
  await expect(editor.getByRole('button', { name: '保存', exact: true })).toBeInViewport()
  await page.keyboard.press('Escape')
  await expect(editor).toHaveCount(0)
  await page.goto('/message/inbox')
  await page.getByRole('tab', { name: '我的待办', exact: true }).click()
  await expect(page).toHaveURL(/tab=tasks/)
  await page.reload()
  await expect(page.getByRole('button', { name: '新建待办', exact: true })).toBeVisible()
})

test('viewport widths, 200 percent layout and keyboard modal focus remain usable', async ({ page }) => {
  test.setTimeout(90000)
  await fullApi(page)
  await login(page, '/iam/users')
  for (const width of [1920, 1440, 1024, 768, 390, 320]) {
    await page.setViewportSize({ width, height: 900 })
    await expect(page.locator('main h1')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
    await page.getByRole('button', { name: '新增', exact: true }).click()
    const dialog = page.getByRole('dialog')
    await expect(dialog.getByRole('button', { name: '保存', exact: true })).toBeInViewport()
    await page.keyboard.press('Escape')
    await expect(dialog).toHaveCount(0)
    await expect(page.getByRole('button', { name: '新增', exact: true })).toBeFocused()
  }
  await page.setViewportSize({ width: 1440, height: 900 })
  // CSS zoom exercises double-sized content, in addition to the half-width viewport checks above.
  await page.evaluate(() => { document.documentElement.style.zoom = '2' })
  await expect(page.getByRole('button', { name: '新增', exact: true })).toBeInViewport()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
  await page.getByRole('button', { name: '新增', exact: true }).click()
  const dialog = page.getByRole('dialog')
  for (let i = 0; i < 30; i++) {
    await page.keyboard.press('Tab')
    expect(await dialog.evaluate(el => el.contains(document.activeElement))).toBe(true)
  }
  await expect(dialog.getByRole('button', { name: '保存', exact: true })).toBeInViewport()
})
