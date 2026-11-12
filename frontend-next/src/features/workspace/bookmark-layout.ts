import { onScopeDispose, ref, watch, type Ref } from 'vue'

export type BookmarkLayout = 'list' | 'cards'
/** Persist only a layout enum, keyed by the server-issued user/tenant/portal scope. */
export function useBookmarkLayout(scope: Readonly<Ref<string>>) {
  const layout = ref<BookmarkLayout>('list')
  const key = () => 'sma.shelf.bookmark-view.v1.' + scope.value
  watch(scope, value => {
    layout.value = 'list'
    if (!value) return
    try { if (localStorage.getItem(key()) === 'cards') layout.value = 'cards' } catch { /* Optional local layout. */ }
  }, { immediate: true, flush: 'sync' })
  function select(value: BookmarkLayout) {
    layout.value = value
    if (scope.value) { try { localStorage.setItem(key(), value) } catch { /* Keep the usable in-memory layout. */ } }
  }
  return { layout, select }
}

/** FLIP moves persistent Vue-owned elements; no demo engine, copied SVG or runtime dependency. */
export function useCollectionMotion(container: Ref<HTMLElement | undefined>) {
  const animations = new Set<Animation>()
  function cancel() { for (const animation of animations) animation.cancel(); animations.clear() }
  function capture() {
    cancel()
    return new Map(Array.from(container.value?.querySelectorAll<HTMLElement>('[data-shelf-bookmark]') || [], el => [el, el.getBoundingClientRect()]))
  }
  function play(before: ReturnType<typeof capture>) {
    if (matchMedia('(prefers-reduced-motion: reduce)').matches) return
    for (const [element, old] of before) {
      if (!element.isConnected || !element.animate) continue
      const next = element.getBoundingClientRect()
      if (!old.width || !old.height || !next.width || !next.height) continue
      const animation = element.animate([
        { transform: `translate(${old.left - next.left}px,${old.top - next.top}px) scale(${old.width / next.width},${old.height / next.height})`, transformOrigin: 'top left' },
        { transform: 'none', transformOrigin: 'top left' },
      ], { duration: 220, easing: 'cubic-bezier(.2,.8,.2,1)' })
      animations.add(animation)
      animation.onfinish = () => { animations.delete(animation) }
    }
  }
  onScopeDispose(cancel)
  return { capture, play, cancel }
}
