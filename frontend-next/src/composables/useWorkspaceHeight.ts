import { onMounted, onUnmounted, ref } from 'vue'

/** Fixed workspaces follow the visible viewport, including keyboard and CSS zoom. */
export function useWorkspaceHeight() {
  const height = ref('100dvh')
  let observer: MutationObserver | undefined
  function measure() {
    const zoom = Number.parseFloat(getComputedStyle(document.documentElement).zoom) || 1
    height.value = (window.visualViewport?.height || window.innerHeight) / zoom + 'px'
  }
  onMounted(() => {
    measure()
    window.addEventListener('resize', measure)
    window.visualViewport?.addEventListener('resize', measure)
    observer = new MutationObserver(measure)
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['style'] })
  })
  onUnmounted(() => {
    window.removeEventListener('resize', measure)
    window.visualViewport?.removeEventListener('resize', measure)
    observer?.disconnect()
  })
  return height
}
