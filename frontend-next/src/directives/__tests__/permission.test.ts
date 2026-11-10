import { expect, it } from 'vitest'
import { nextTick } from 'vue'
import { defineComponent } from 'vue'
import { createPinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { permission } from '../permission'
import { useAuth, type CurrentUser } from '@/stores/auth'

it('updates visible actions when permissions change and defaults to hidden', async () => {
  const wrapper = mount(
    defineComponent({ template: '<button v-permission="\'iam:user:create\'">Create</button>' }),
    {
      global: { plugins: [createPinia()], directives: { permission } },
    },
  )
  expect(wrapper.element.style.display).toBe('none')
  const auth = useAuth()
  auth.user = { permissions: ['iam:user:create'] } as CurrentUser
  await nextTick()
  expect(wrapper.element.style.display).toBe('')
  auth.user = { permissions: [] } as unknown as CurrentUser
  await nextTick()
  expect(wrapper.element.style.display).toBe('none')
  wrapper.unmount()
})
