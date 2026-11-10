import { expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { usePreferences } from '../preferences'
it('applies server appearance settings and exposes a complete personal preference request', async () => {
  setActivePinia(createPinia())
  const preferences = usePreferences()
  preferences.changeTimeZone('America/New_York')
  preferences.applyAppearance({ theme: 'dark', tabsEnabled: false, keepTabsAfterReload: true })
  await nextTick()
  expect(document.documentElement.classList.contains('dark')).toBe(true)
  expect(preferences.snapshot()).toMatchObject({
    timeZone: 'America/New_York',
    theme: 'dark',
    tabsEnabled: false,
    keepTabsAfterReload: true,
  })
  preferences.applyAppearance({ theme: 'light' })
  await nextTick()
  expect(document.documentElement.classList.contains('dark')).toBe(false)
})
