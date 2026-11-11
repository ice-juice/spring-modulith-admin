import { ApiError, get, http, post, type RequestOptions } from '@/api/http'
import { i18n } from '@/locales'
export interface Asset {
  id: string
  name: string
  contentType: string
  kind: string
  sizeBytes: string
  checksum: string
  ownerId: string
  uploader: string
  createdAt: string
  previewable: boolean
  contentPath: string
}
export async function upload(file: File, imageOnly = false, options?: RequestOptions) {
  const data = new FormData()
  data.append('file', file)
  return post<Asset>('/file/files' + (imageOnly ? '/images' : ''), data, options)
}
export interface UploadReceipt { uploadId: string; state: 'ACCEPTED' | 'RUNNING' | 'UNKNOWN' | 'SUCCEEDED' | 'FAILED_FINAL'; fileId: string | null; resumable: boolean }
export function uploadEvidence(value: unknown): { id: string; state: 'SUCCEEDED' | 'FAILED_FINAL' | 'PENDING' | 'UNKNOWN' } {
  const row = value as Partial<UploadReceipt> | null
  if (!row || typeof row.uploadId !== 'string' || !/^[a-f0-9-]{36}$/.test(row.uploadId)
    || !['ACCEPTED', 'RUNNING', 'UNKNOWN', 'SUCCEEDED', 'FAILED_FINAL'].includes(row.state || '')
    || typeof row.resumable !== 'boolean' || row.state === 'SUCCEEDED' && (typeof row.fileId !== 'string' || !/^\d{1,20}$/.test(row.fileId))) throw new Error(i18n.global.t('common.operation.invalid'))
  return { id: row.uploadId, state: row.state === 'SUCCEEDED' ? 'SUCCEEDED' : row.state === 'FAILED_FINAL' ? 'FAILED_FINAL' : row.state === 'UNKNOWN' ? 'UNKNOWN' : 'PENDING' }
}
export const lookupUpload = (key: string) => post<UploadReceipt>('/file/uploads/lookup', { key })
export const uploadedAsset = (id: string) => get<Asset>('/file/files/' + encodeURIComponent(id))
export function uploadFailure(cause: unknown): 'failed' | 'pending' | 'unknown' {
  if (!(cause instanceof ApiError)) return 'unknown'
  try { const evidence = uploadEvidence(cause.details); if (cause.key === 'common.request.failed' && evidence.state === 'FAILED_FINAL') return 'failed' }
  catch { /* Missing task evidence does not prove rollback. */ }
  return cause.key === 'common.request.processing' ? 'pending' : 'unknown'
}
export async function content(id: string, inline = false) {
  return (
    await http.get<Blob>('/file/files/' + encodeURIComponent(id) + '/content', {
      responseType: 'blob',
      params: { inline },
    })
  ).data
}
export async function download(id: string, name: string) {
  saveBlob(await content(id), name)
}
export function saveBlob(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob),
    link = document.createElement('a')
  link.href = url
  link.download = name
  document.body.append(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
export function formatBytes(value: string | number) {
  const bytes = Number(value)
  if (!Number.isFinite(bytes) || bytes < 0) return '—'
  const units = ['B', 'KiB', 'MiB', 'GiB', 'TiB'],
    unit = Math.min(4, Math.floor(Math.log(Math.max(1, bytes)) / Math.log(1024)))
  return (
    (bytes / 1024 ** unit).toLocaleString(undefined, { maximumFractionDigits: 1 }) +
    ' ' +
    units[unit]
  )
}
