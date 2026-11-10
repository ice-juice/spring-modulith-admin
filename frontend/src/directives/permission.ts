import { watchEffect, type Directive } from 'vue'
import { useAuth } from '@/stores/auth'

const stops = new WeakMap<HTMLElement, () => void>()
export const permission: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    const auth = useAuth()
    const display = el.style.display
    stops.set(
      el,
      watchEffect(() => {
        el.style.display = auth.hasPermission(binding.value) ? display : 'none'
      }),
    )
  },
  updated(el, binding) {
    stops.get(el)?.()
    const auth = useAuth()
    stops.set(
      el,
      watchEffect(() => {
        el.style.display = auth.hasPermission(binding.value) ? '' : 'none'
      }),
    )
  },
  unmounted(el) {
    stops.get(el)?.()
    stops.delete(el)
  },
}
