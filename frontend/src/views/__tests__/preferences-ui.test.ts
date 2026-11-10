import { expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus, { ElSelect } from 'element-plus'
import { i18n, loadLocaleModule, setLocale } from '@/locales'
import { usePreferences } from '@/stores/preferences'
import PreferencesView from '@/views/config/PreferencesView.vue'
const api = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn(), remove: vi.fn() }))
vi.mock('@/api/http', () => api)

it('saves the current header language and timezone while preserving an unsaved theme choice', async () => {
  const pinia = createPinia()
  setActivePinia(pinia)
  await loadLocaleModule('config')
  setLocale('zh-CN')
  const preferences = usePreferences()
  preferences.changeTimeZone('Asia/Shanghai')
  api.get.mockResolvedValue({
    locale: 'zh-CN',
    timeZone: 'Asia/Shanghai',
    theme: 'light',
    tabsEnabled: true,
    keepTabsAfterReload: false,
  })
  const wrapper = mount(PreferencesView, { global: { plugins: [pinia, ElementPlus, i18n] } })
  await flushPromises()
  wrapper.findAllComponents(ElSelect)[2]!.vm.$emit('update:modelValue', 'dark')
  preferences.changeLocale('en-US')
  preferences.changeTimeZone('UTC')
  await nextTick()
  expect(wrapper.findAllComponents(ElSelect).map((select) => select.props('modelValue'))).toEqual([
    'en-US',
    'UTC',
    'dark',
  ])
  await wrapper
    .findAll('button')
    .find((button) => button.text() === 'Save')!
    .trigger('click')
  await flushPromises()
  expect(api.put).toHaveBeenCalledWith(
    '/config/preferences',
    expect.objectContaining({ locale: 'en-US', timeZone: 'UTC', theme: 'dark' }),
  )
  wrapper.unmount()
  preferences.applyAppearance({ theme: 'light' })
})
