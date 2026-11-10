import { expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import { i18n, loadLocaleModule } from '@/locales'
import ProfileView from '@/views/iam/ProfileView.vue'
const api = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn() }))
vi.mock('@/api/http', () => api)
vi.mock('@/components/AvatarImage.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/features/file/files', () => ({ upload: vi.fn() }))
it('removes the avatar with null and preserves snowflake IDs as strings', async () => {
  const pinia = createPinia()
  setActivePinia(pinia)
  await loadLocaleModule('iam')
  api.get.mockResolvedValue({
    id: '9007199254740993',
    username: 'tester',
    name: 'Tester',
    email: null,
    mobile: null,
    gender: null,
    avatarFileId: '9007199254740995',
  })
  const wrapper = mount(ProfileView, { global: { plugins: [pinia, i18n, ElementPlus] } })
  await flushPromises()
  const remove = wrapper
    .findAll('button')
    .find((button) => button.text() === i18n.global.t('iam.removeAvatar'))!
  await remove.trigger('click')
  await wrapper.find('form').trigger('submit')
  await flushPromises()
  expect(api.put).toHaveBeenCalledWith(
    '/iam/profile',
    expect.objectContaining({ avatarFileId: null, name: 'Tester', email: null }),
  )
  wrapper.unmount()
})
