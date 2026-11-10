import { onScopeDispose } from 'vue'
/** Independent ownership for each read flow, including post-await error/finally state. */
export function useRequestGate() {
  let generation = 0, active = true
  onScopeDispose(() => { active = false; generation++ })
  return () => { const current = ++generation; return () => active && generation === current }
}
