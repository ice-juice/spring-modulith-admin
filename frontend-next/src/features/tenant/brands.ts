import { get, post, put, type PageResult, type RequestOptions } from '@/api/http'
import type { BrandAssetSlot } from '@/lib/tenant-branding'

export type LoginTemplateId = 'login1' | 'login2' | 'login3'
export type OverrideMode = 'OVERRIDE' | 'INHERIT' | 'HIDE'
export const assetSlots: BrandAssetSlot[] = ['LOGO_LIGHT', 'LOGO_DARK', 'LOGO_COMPACT', 'FAVICON', 'LOGIN_BACKGROUND']
export const overrideFields = ['SHORT_NAME', 'ENGLISH_NAME', 'WELCOME_TITLE', 'WELCOME_DESCRIPTION', 'COPYRIGHT', 'ICP_RECORD', 'POLICE_RECORD', 'WEBSITE', 'HELP', 'SUPPORT', 'PRIVACY', 'AGREEMENT', ...assetSlots, 'PRIMARY_COLOR'] as const
export type OverrideField = typeof overrideFields[number]
export interface BrandContent {
  schemaVersion: 1
  identity: { systemName: string; shortName: string | null; englishName: string | null }
  login: { templateId: LoginTemplateId; title: { zhCN: string | null; enUS: string | null }; description: { zhCN: string | null; enUS: string | null }; backgroundBlur: boolean }
  legal: { copyrightOwner: string | null; copyrightStartYear: number | null; copyrightUrl: string | null; icpRecord: string | null; icpUrl: string | null; policeRecord: string | null; policeUrl: string | null }
  links: { website: string | null; help: string | null; support: string | null; privacy: string | null; agreement: string | null }
  appearance: { primaryColor: string | null; defaultSkin: 'iris' | 'celadon' | 'amber'; allowPersonalSkin: boolean }
  assets: Partial<Record<BrandAssetSlot, string>>
  overrides: Partial<Record<OverrideField, OverrideMode>>
}
export interface BrandDraft { content: BrandContent; version: string; publishedRevisionId: string | null; legacyBackgroundUrl: string | null }
export interface BrandRevision { id: string; revision: string; sourceRevisionId: string | null; defaultsRevision: string; content: BrandContent; createdAt: string; legacyBackgroundUrl: string | null }
export interface BrandReceipt { revisionId: string; revision: string; sourceRevisionId: string | null; operationId: string }
export interface BrandPreview { content: BrandContent; defaultsRevision: string; legacyBackgroundUrl: string | null; images: Partial<Record<BrandAssetSlot, string>> }
export interface LoginTemplate { id: LoginTemplateId; version: number; nameKey: string; layout: string; enabled: boolean }
export type BrandOperation = { operationId: string; expectedPublishedRevisionId: string | null } & ({ kind: 'publish'; expectedDraftVersion: string } | { kind: 'rollback'; sourceRevisionId: string })
export const brands = {
  draft: () => get<BrandDraft>('/tenant/brands/draft'),
  templates: () => get<LoginTemplate[]>('/tenant/brands/templates'),
  save: (expectedVersion: string, content: BrandContent, options?: RequestOptions) => put<BrandDraft>('/tenant/brands/draft', { expectedVersion, content }, options),
  preview: (expectedVersion: string, content: BrandContent) => post<BrandPreview>('/tenant/brands/previews', { expectedVersion, content }),
  history: (page = 1) => get<PageResult<BrandRevision>>('/tenant/brands/revisions', { page, size: 10 }),
  revision: (id: string) => get<BrandRevision>('/tenant/brands/revisions/' + encodeURIComponent(id)),
  execute: (operation: BrandOperation, options?: RequestOptions) => {
    const { kind, ...input } = operation
    return post<BrandReceipt>('/tenant/brands/' + (kind === 'publish' ? 'publications' : 'rollbacks'), input, options)
  },
  operation: (id: string) => get<{ status: 'COMMITTED' | 'NOT_OBSERVED'; receipt: BrandReceipt | null }>('/tenant/brands/operations/' + encodeURIComponent(id)),
}
export function inheritanceAllowed(field: OverrideField) { return !(['COPYRIGHT', 'ICP_RECORD', 'POLICE_RECORD'] as OverrideField[]).includes(field) }
/** Map ordering in Java JSON must not create false dirty/conflict states. */
export function canonical(value: unknown): string {
  if (Array.isArray(value)) return '[' + value.map(canonical).join(',') + ']'
  if (value && typeof value === 'object') return '{' + Object.entries(value).sort(([a], [b]) => a.localeCompare(b)).map(([key, item]) => JSON.stringify(key) + ':' + canonical(item)).join(',') + '}'
  return JSON.stringify(value) ?? 'null'
}
export function reconcileSave(version: string, submitted: BrandContent, remote: BrandDraft): 'matched' | 'unobserved' | 'conflict' {
  if (remote.version === version) return 'unobserved'
  return BigInt(remote.version) > BigInt(version) && canonical(submitted) === canonical(remote.content) ? 'matched' : 'conflict'
}
export function unknownWrite(error: unknown): boolean {
  const code = (error as { code?: number } | null)?.code
  return code === undefined || code === 0 || code === 202 || code === 408 || code >= 500
}
export function validBrandUpload(file: Pick<File, 'size' | 'type'>) {
  return file.size > 0 && file.size <= 2 * 1024 * 1024 && ['image/png', 'image/jpeg', 'image/gif'].includes(file.type)
}
