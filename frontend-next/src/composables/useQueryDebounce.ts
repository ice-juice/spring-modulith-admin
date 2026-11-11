import { onScopeDispose } from 'vue'

/** Query input only: IME composition does not dispatch partial text, explicit search cancels the timer. */
export function useQueryDebounce(search: () => void, delay = 300) {
  let timer: ReturnType<typeof setTimeout> | undefined, composing = false, active = true
  function cancel() { if (timer !== undefined) clearTimeout(timer); timer = undefined }
  function trigger() { cancel(); if (active && !composing) search() }
  function schedule() { cancel(); if (active && !composing) timer = setTimeout(trigger, delay) }
  function compositionStart() { composing = true; cancel() }
  function compositionEnd() { composing = false; schedule() }
  function keydown(event: KeyboardEvent) { if (event.key === 'Enter' && (composing || event.isComposing || event.keyCode === 229)) event.preventDefault() }
  onScopeDispose(() => { active = false; cancel() })
  return { schedule, trigger, cancel, compositionStart, compositionEnd, keydown }
}
