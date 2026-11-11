import { afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, nextTick, reactive } from 'vue'
import { mount, type VueWrapper } from '@vue/test-utils'
import { useDirectorySession } from '../useDirectorySession'

const auth = reactive({ user: { id: '1', portal: 'admin', locked: false }, allowed: true, hasPermission: () => auth.allowed })
const branding = reactive<{ entry: { entry: { tenantId: string } } | null }>({ entry: null })
vi.mock('@/stores/auth', () => ({ useAuth: () => auth }))
vi.mock('@/stores/branding', () => ({ useBranding: () => branding }))
let wrapper: VueWrapper | undefined
afterEach(() => { wrapper?.unmount(); wrapper = undefined })
describe('directory session boundaries', () => {
  it('preserves work through transient branding refresh but invalidates tenant, lock, actor and permission changes', async () => {
    const clear = vi.fn(), interrupt = vi.fn()
    let session!: ReturnType<typeof useDirectorySession>
    wrapper = mount(defineComponent({ setup() { session = useDirectorySession('tenant:domain:view', clear, interrupt); return {} }, template: '<div />' }))
    const first = session.ticket()
    branding.entry = { entry: { tenantId: '10' } }; await nextTick()
    branding.entry = null; await nextTick()
    branding.entry = { entry: { tenantId: '10' } }; await nextTick()
    expect(clear).not.toHaveBeenCalled(); expect(session.valid(first)).toBe(true)
    branding.entry = { entry: { tenantId: '11' } }; await nextTick()
    expect(clear).toHaveBeenCalledTimes(1); expect(session.valid(first)).toBe(false)
    const second = session.ticket()
    auth.user.locked = true; await nextTick()
    expect(interrupt).toHaveBeenCalledTimes(1); expect(session.accessible.value).toBe(false)
    auth.user.locked = false; await nextTick()
    expect(session.valid(second)).toBe(false)
    auth.user.id = '2'; await nextTick(); expect(clear).toHaveBeenCalledTimes(2)
    auth.allowed = false; await nextTick(); expect(clear).toHaveBeenCalledTimes(3)
    const last = session.ticket(); wrapper.unmount(); wrapper = undefined
    expect(session.valid(last)).toBe(false)
  })
})
