import { formatInstant, localDateTimeToInstant } from '@/lib/time'
export interface Source {
  system: string | null
  messageId: string | null
  requestId: string | null
  url: string | null
  senderKey: string | null
  receiverKey: string | null
}
export interface Task {
  id: string
  userId: string
  userName: string
  title: string
  content: string | null
  url: string | null
  type: number
  priority: number
  state: number
  dueAt: string
  completedAt: string | null
  cancelledAt: string | null
  readAt: string | null
  source: Source
  createdAt: string
}
export function taskForm(row: Task | undefined, zone: string) {
  return {
    title: row?.title || '',
    content: row?.content || '',
    url: row?.url || '',
    type: row?.type || 1,
    priority: row?.priority || 1,
    dueAt: row ? formatInstant(row.dueAt, zone, 'en-US', 'YYYY-MM-DDTHH:mm:ss') : '',
    userId: row?.userId || null,
    source: {
      system: row?.source?.system || '',
      messageId: row?.source?.messageId || '',
      requestId: row?.source?.requestId || '',
      url: row?.source?.url || '',
      senderKey: row?.source?.senderKey || '',
      receiverKey: row?.source?.receiverKey || '',
    },
  }
}
export function taskInput(form: ReturnType<typeof taskForm>, zone: string) {
  return {
    ...form,
    dueAt: localDateTimeToInstant(form.dueAt, zone),
    content: [2, 4].includes(form.type) ? form.content : null,
    url: [3, 4].includes(form.type) ? form.url : null,
    source: Object.values(form.source).some(Boolean) ? form.source : null,
  }
}
export function activeTask(state: number) {
  return [1, 2, 5].includes(state)
}
