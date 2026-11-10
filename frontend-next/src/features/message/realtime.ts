import { onMounted, onUnmounted } from 'vue'
export function useMessageRefresh(load: () => unknown, types: string[]) {
  const listener = (event: Event) => {
    if (types.includes((event as CustomEvent<{ type: string }>).detail?.type)) void load()
  }
  onMounted(() => window.addEventListener('sma:message', listener))
  onUnmounted(() => window.removeEventListener('sma:message', listener))
}
