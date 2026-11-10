import { expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus, { ElSelect, ElDatePicker } from 'element-plus'
import { i18n, setLocale } from '@/locales'
import { usePreferences } from '@/stores/preferences'
import TasksView from '@/views/message/TasksView.vue'
const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), remove: vi.fn() }))
vi.mock('@/api/http', () => api)
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push: vi.fn() }),
}))

it('sends managed-task keyword, status, type and UTC range from the displayed filters', async () => {
  const pinia = createPinia()
  setActivePinia(pinia)
  setLocale('en-US')
  usePreferences().changeTimeZone('UTC')
  api.get.mockImplementation(async (url: string) =>
    url.startsWith('/config/') ? [] : { items: [], total: '0' },
  )
  const wrapper = mount(TasksView, {
    props: { managed: true },
    global: { plugins: [pinia, ElementPlus, i18n], directives: { permission: { mounted() {} } } },
  })
  await flushPromises()
  await wrapper.find('input[placeholder="Search"]').setValue('Review')
  const selects = wrapper.findAllComponents(ElSelect)
  selects[0]!.vm.$emit('update:modelValue', 1)
  selects[1]!.vm.$emit('update:modelValue', 2)
  wrapper
    .findComponent(ElDatePicker)
    .vm.$emit('update:modelValue', ['2026-11-10T00:00:00', '2026-11-11T00:00:00'])
  await wrapper
    .findAll('button')
    .find((button) => button.text() === 'Search')!
    .trigger('click')
  await flushPromises()
  expect(api.get).toHaveBeenLastCalledWith(
    '/message/todos/managed',
    expect.objectContaining({
      keyword: 'Review',
      state: 1,
      type: 2,
      dateField: 'dueAt',
      from: '2026-11-10T00:00:00.000Z',
      to: '2026-11-11T00:00:00.000Z',
    }),
  )
  wrapper.unmount()
})
