import { expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus, { ElMessageBox } from 'element-plus'
import { i18n, loadLocaleModule, setLocale } from '@/locales'
import CachesView from '@/views/monitor/CachesView.vue'
import PublicationsView from '@/views/monitor/PublicationsView.vue'
const api = vi.hoisted(() => ({ get: vi.fn(), remove: vi.fn(), post: vi.fn() }))
vi.mock('@/api/http', () => api)
it.each(['cache', 'event'])(
  'confirms and submits the exact %s identifier from the displayed row',
  async (kind) => {
    const pinia = createPinia()
    setActivePinia(pinia)
    await loadLocaleModule('monitor')
    setLocale('en-US')
    const id = kind === 'cache' ? 'config.parameters' : 'cc97b81d-0bf6-4eb9-8ce0-3b792ff4def1'
    api.get.mockResolvedValue(
      kind === 'cache'
        ? [{ id, name: 'Parameters', size: '2', hits: '1', misses: '1' }]
        : {
            items: [
              {
                id,
                eventType: 'LoginEvent',
                listener: 'audit',
                status: 'FAILED',
                attempts: 1,
                publishedAt: '2026-11-10T00:00:00Z',
              },
            ],
            total: '1',
          },
    )
    const confirm = vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm')
    const wrapper = mount(kind === 'cache' ? CachesView : PublicationsView, {
      global: { plugins: [pinia, ElementPlus, i18n], directives: { permission: { mounted() {} } } },
    })
    await flushPromises()
    await wrapper
      .find('.el-table__body-wrapper')
      .findAll('button')
      .find((button) => button.text() === (kind === 'cache' ? 'Clear' : 'Retry'))!
      .trigger('click')
    await flushPromises()
    expect(confirm).toHaveBeenCalledOnce()
    if (kind === 'cache') expect(api.remove).toHaveBeenCalledWith('/monitor/caches/' + id)
    else expect(api.post).toHaveBeenCalledWith('/monitor/publications/' + id + '/retry')
    wrapper.unmount()
    confirm.mockRestore()
    vi.clearAllMocks()
  },
)
