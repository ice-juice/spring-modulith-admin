import { expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { useAuth, type CurrentUser } from '@/stores/auth'
import PermissionButton from '../PermissionButton.vue'

it('uses only authorized icon metadata and keeps event/disabled semantics', async () => {
  const pinia = createPinia(); setActivePinia(pinia)
  const auth = useAuth()
  auth.user = { id: '1', permissions: ['openapi:app:create'], permissionIcons: { 'openapi:app:create': '3d:rocket' } } as unknown as CurrentUser
  let clicked = 0
  const wrapper = mount(PermissionButton, { props: { permission: 'openapi:app:create' }, attrs: { disabled: true, onClick: () => clicked++ }, slots: { default: '<svg data-original-icon></svg>Create' }, global: { plugins: [pinia] } })
  expect(wrapper.find('img').attributes('src')).toContain('/icons/3d/rocket.webp')
  await wrapper.find('button').trigger('click'); expect(clicked).toBe(0)
  auth.user.permissionIcons = { 'openapi:app:create': 'none' }; await nextTick()
  expect(wrapper.find('img').exists()).toBe(false)
  expect(wrapper.find('span').classes()).toContain('[&>svg]:hidden')
  auth.user.permissionIcons = {}; await nextTick()
  expect(wrapper.find('[data-original-icon]').exists()).toBe(true)
  expect(wrapper.find('span').classes()).not.toContain('[&>svg]:hidden')
  auth.user.permissions = []; await nextTick()
  expect(wrapper.find('button').exists()).toBe(false)
})
