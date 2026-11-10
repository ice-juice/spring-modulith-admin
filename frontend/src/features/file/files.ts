import { http, post } from '@/api/http'
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
export async function upload(file: File, imageOnly = false) {
  const data = new FormData()
  data.append('file', file)
  return post<Asset>('/file/files' + (imageOnly ? '/images' : ''), data)
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
