import { beforeEach, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus, { ElTree } from 'element-plus'
import { nextTick } from 'vue'
import { i18n, loadLocaleModule, setLocale } from '@/locales'
import { useAuth, type CurrentUser } from '@/stores/auth'
import { permission } from '@/directives/permission'
import { get, put } from '@/api/http'
import ManagementView from '@/views/iam/ManagementView.vue'

vi.mock('vue-router', () => ({
  useRoute: () => ({ meta: { resource: 'roles' }, query: {} }),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}))
vi.mock('@/api/http', () => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), remove: vi.fn() }))

beforeEach(async () => {
  const pinia = createPinia()
  setActivePinia(pinia)
  await loadLocaleModule('iam')
  setLocale('en-US')
  useAuth().user = {
    id: 'admin',
    permissions: ['iam:role:view', 'iam:role:grant'],
    menus: [],
    navigation: [],
  } as unknown as CurrentUser
  vi.mocked(get).mockImplementation(async (url) => {
    if (url === '/iam/roles')
      return [
        {
          id: 'role1',
          parentId: '0',
          name: 'Operator',
          code: 'operator',
          sort: 0,
          enabled: true,
          dataScope: 'SELF',
        },
      ]
    if (url === '/iam/permissions/tree')
      return [
        {
          id: 'parent',
          name: 'Organization',
          children: [
            {
              id: 'menu',
              name: 'Departments',
              children: [
                { id: 'button1', name: 'Create', children: [] },
                { id: 'button2', name: 'Delete', children: [] },
              ],
            },
          ],
        },
      ]
    if (url === '/iam/roles/role1/permissions') return ['parent', 'menu', 'button1']
    return []
  })
  vi.mocked(put).mockResolvedValue(undefined)
})

it('keeps sibling permissions unchecked when assigned ancestors are loaded and saves that selection', async () => {
  const wrapper = mount(ManagementView, {
    attachTo: document.body,
    global: { plugins: [ElementPlus, i18n], directives: { permission } },
  })
  await flushPromises()
  const grant = wrapper.findAll('button').find((button) => button.text() === 'Assign permissions')
  expect(grant).toBeDefined()
  await grant!.trigger('click')
  await flushPromises()
  await nextTick()
  const tree = wrapper.findComponent(ElTree)
  expect(tree.vm.getCheckedKeys(false)).toEqual(
    expect.arrayContaining(['parent', 'menu', 'button1']),
  )
  expect(tree.vm.getCheckedKeys(false)).not.toContain('button2')
  const save = wrapper
    .findAllComponents({ name: 'ElButton' })
    .find((button) => button.text() === 'Save')
  expect(save).toBeDefined()
  await save!.trigger('click')
  await flushPromises()
  expect(put).toHaveBeenCalledWith('/iam/roles/role1/permissions', {
    permissionIds: expect.arrayContaining(['parent', 'menu', 'button1']),
  })
  const sent = vi.mocked(put).mock.calls[0]?.[1] as { permissionIds: string[] }
  expect(sent.permissionIds).not.toContain('button2')
  wrapper.unmount()
})
