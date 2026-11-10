import { expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import { i18n, loadLocaleModule, setLocale } from '@/locales'
import NoticesView from '@/views/message/NoticesView.vue'
import { noticeInput } from '../types'
import { useAuth } from '@/stores/auth'
const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), remove: vi.fn() }))
vi.mock('@/api/http', () => api)
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }) }))
vi.mock('@/components/RichEditor.vue', () => ({
  default: {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template:
      '<textarea :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
}))
vi.mock('@/components/AttachmentList.vue', () => ({ default: { template: '<div />' } }))
it('copies notification IDs without numeric coercion and without mutating loaded arrays', () => {
  const selected = ['9007199254740993'],
    files = ['9007199254740995']
  const input = noticeInput({ audienceIds: selected, fileIds: files } as Parameters<
    typeof noticeInput
  >[0])
  input.audienceIds.push('9007199254740997')
  input.fileIds.splice(0, 1)
  expect(selected).toEqual(['9007199254740993'])
  expect(files).toEqual(['9007199254740995'])
})
it('renders the actual notice list and opens the editor with server-provided audience IDs intact', async () => {
  const pinia = createPinia()
  setActivePinia(pinia)
  await loadLocaleModule('message')
  setLocale('en-US')
  const id = '9007199254740993',
    receiver = '9007199254740995'
  api.get.mockImplementation(async (path: string) =>
    path.includes('/config/')
      ? [{ value: '1', label: 'Standard' }]
      : path.endsWith('/audience')
        ? [{ value: receiver, label: 'Reader' }]
        : {
            items: [
              {
                id,
                title: 'Release',
                content: '<p>Safe</p>',
                type: '1',
                priority: 1,
                audienceType: 'USER',
                audienceIds: [receiver],
                fileIds: [],
                recipientCount: '1',
                readCount: '0',
              },
            ],
            total: '1',
          },
  )
  useAuth().user = {
    id: '1',
    username: 'admin',
    name: 'Admin',
    portal: 'admin',
    superAdmin: true,
    permissions: ['message:notice:update'],
    menus: [],
    navigation: [],
  }
  const wrapper = mount(NoticesView, {
    global: { plugins: [pinia, ElementPlus, i18n], directives: { permission: { mounted() {} } } },
  })
  await flushPromises()
  expect(wrapper.text()).toContain('Release')
  await wrapper
    .find('.el-table__body-wrapper')
    .findAll('button')
    .find((button) => button.text() === 'Edit')!
    .trigger('click')
  await flushPromises()
  expect(wrapper.find('input[maxlength="255"]').element).toHaveProperty('value', 'Release')
  expect(wrapper.find('textarea').element).toHaveProperty('value', '<p>Safe</p>')
  await wrapper
    .findAll('button')
    .find((button) => button.text() === 'Save')!
    .trigger('click')
  await flushPromises()
  expect(api.put).toHaveBeenCalledWith(
    '/message/notices/' + id,
    expect.objectContaining({ audienceIds: [receiver], fileIds: [] }),
  )
  wrapper.unmount()
})
