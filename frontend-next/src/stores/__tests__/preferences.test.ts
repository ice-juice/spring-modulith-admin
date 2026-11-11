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

it('keeps skin and theme independent, scopes its cache and restores a rejected change', async () => {
  setActivePinia(createPinia())
  const preferences = usePreferences()
  preferences.applyAppearance({ skinOwner: 'admin.201', skin: 'celadon', theme: 'dark' })
  await nextTick()
  expect(document.documentElement.dataset.skin).toBe('celadon')
  expect(document.documentElement.classList.contains('dark')).toBe(true)
  const previous = preferences.snapshot()
  preferences.applyAppearance({ skin: 'amber' })
  expect(preferences.theme).toBe('dark')
  preferences.applyAppearance(previous)
  expect(preferences.skin).toBe('celadon')
  preferences.applyAppearance({ theme: 'light' })
  expect(preferences.skin).toBe('celadon')
  preferences.applyAppearance({ skinOwner: 'admin.202', skin: 'iris' })
  expect(localStorage.getItem('sma.next.skin.admin.201')).toBe('celadon')
  expect(localStorage.getItem('sma.next.skin.admin.202')).toBe('iris')
  preferences.applyAppearance({ skin: 'unexpected' })
  expect(preferences.skin).toBe('iris')
})
it('scopes guest choices to the entry and applies published policy without overwriting stored choices', async () => {
  localStorage.clear(); setActivePinia(createPinia())
  const preferences = usePreferences()
  preferences.setBrandPolicy('21.admin', { defaultSkin: 'amber', allowPersonalSkin: true })
  expect(preferences.skin).toBe('amber')
  preferences.applyAppearance({ skin: 'celadon' })
  preferences.setBrandPolicy('21.admin', { defaultSkin: 'iris', allowPersonalSkin: false })
  expect(preferences.skin).toBe('iris')
  expect(preferences.personalSkinAllowed).toBe(false)
  preferences.applyAppearance({ skin: 'amber' })
  expect(localStorage.getItem('sma.next.skin.21.admin.guest')).toBe('celadon')
  preferences.setBrandPolicy('22.admin', { defaultSkin: 'amber', allowPersonalSkin: true })
  expect(preferences.skin).toBe('amber')
  preferences.setBrandPolicy('21.admin', { defaultSkin: 'iris', allowPersonalSkin: true })
  expect(preferences.skin).toBe('celadon')
  await nextTick()
  expect(document.documentElement.dataset.skin).toBe('celadon')
})
