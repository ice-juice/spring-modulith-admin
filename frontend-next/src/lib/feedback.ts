import { ref } from 'vue'
export interface Notice { id: number; message: string; tone: 'success' | 'error' | 'info' }
export const notices = ref<Notice[]>([])
let sequence = 0
export function notify(message: string, tone: Notice['tone'] = 'success') {
  const id = ++sequence
  notices.value.push({ id, message, tone })
  if (notices.value.length > 4) notices.value.shift()
  setTimeout(() => dismissNotice(id), 6000)
  return id
}
export function dismissNotice(id: number) { notices.value = notices.value.filter(n => n.id !== id) }
interface Confirmation { title: string; description: string; destructive: boolean; resolve: (value: boolean) => void }
export const confirmation = ref<Confirmation>()
export function confirmAction(description: string, title: string, destructive = false) {
  confirmation.value?.resolve(false)
  return new Promise<boolean>(resolve => { confirmation.value = { title, description, destructive, resolve } })
}
export function settleConfirmation(value: boolean) { const active = confirmation.value; confirmation.value = undefined; active?.resolve(value) }
/** Cancellation is distinct from an Error so existing action flows do not report it as failure. */
export async function confirmOrCancel(description: string, title: string, options?: { type?: string }) {
  if (!await confirmAction(description, title, options?.type === 'warning')) throw Symbol('action-cancelled')
}
