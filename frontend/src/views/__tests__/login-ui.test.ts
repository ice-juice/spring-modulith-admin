import { expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import { i18n, loadLocaleModule, setLocale } from '@/locales'
import { get } from '@/api/http'
import LoginView from '@/views/LoginView.vue'
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ replace: vi.fn() }),
}))
vi.mock('@/api/http', () => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }))
it('uses public branding and hides verification controls when the server disables them', async () => {
  const pinia = createPinia()
  setActivePinia(pinia)
  await loadLocaleModule('auth')
  setLocale('en-US')
  vi.mocked(get).mockResolvedValue({
    'system.name': 'Testing Console',
    'login.captchaEnabled': 'false',
  })
  const wrapper = mount(LoginView, { global: { plugins: [pinia, ElementPlus, i18n] } })
  await flushPromises()
  expect(wrapper.text()).toContain('Testing Console')
  expect(wrapper.text()).not.toContain('Verification code')
  expect(get).not.toHaveBeenCalledWith('/auth/captcha')
  wrapper.unmount()
})
