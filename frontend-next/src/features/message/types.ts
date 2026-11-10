export interface Option {
  value: string
  label: string
  disabled?: boolean
}
export interface Notice {
  id: string
  title: string
  content: string
  type: string
  priority: number
  audienceType: string
  audienceIds: string[]
  fileIds: string[]
  recipientCount: string
  readCount: string
  createdBy: string
  createdAt: string
  updatedAt: string
  readAt: string | null
}
export interface NoticeInput {
  title: string
  content: string
  type: string
  priority: number
  audienceType: string
  audienceIds: string[]
  fileIds: string[]
}
export function noticeInput(row?: Notice): NoticeInput {
  return {
    title: row?.title || '',
    content: row?.content || '',
    type: row?.type || '1',
    priority: row?.priority || 1,
    audienceType: row?.audienceType || 'ALL',
    audienceIds: [...(row?.audienceIds || [])],
    fileIds: [...(row?.fileIds || [])],
  }
}
