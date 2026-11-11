export type BrandAssetSlot = 'LOGO_LIGHT' | 'LOGO_DARK' | 'LOGO_COMPACT' | 'FAVICON' | 'LOGIN_BACKGROUND'
export function localizedBrandText(value: { zhCN: string | null; enUS: string | null } | undefined, locale: string): string {
  const preferred = locale.startsWith('en') ? value?.enUS : value?.zhCN
  const alternate = locale.startsWith('en') ? value?.zhCN : value?.enUS
  return preferred?.trim() ? preferred : alternate || ''
}
export function copyrightCaption(owner?: string | null, startYear?: number | null): string {
  if (!owner) return ''
  if (!startYear) return owner
  const year = new Date().getFullYear()
  return '© ' + (startYear < year ? startYear + '–' + year : String(startYear)) + ' ' + owner
}
export interface TenantEntryDto {
  entry: { tenantId: string; domainId: string; portal: string }
  branding: {
    revisionId: string | null; revision: string
    identity: { systemName: string; shortName?: string | null; englishName?: string | null }
    login: { templateId: 'login1' | 'login2' | 'login3'; title?: { zhCN: string | null; enUS: string | null }; description?: { zhCN: string | null; enUS: string | null }; backgroundBlur: boolean }
    legal: { copyrightOwner?: string | null; copyrightStartYear?: number | null; copyrightUrl?: string | null; icpRecord?: string | null; icpUrl?: string | null; policeRecord?: string | null; policeUrl?: string | null }
    links?: { website?: string | null; help?: string | null; support?: string | null; privacy?: string | null; agreement?: string | null }
    appearance?: { primaryColor?: string | null; defaultSkin: string; allowPersonalSkin: boolean }
    assets: Partial<Record<BrandAssetSlot, string>>
    legacyBackgroundUrl?: string | null
  }
  login: { passwordEnabled: boolean; captchaEnabled: boolean }
}
export function checkedEntry(raw: unknown): TenantEntryDto {
  const data = raw as TenantEntryDto | null
  if (!data || typeof data.entry?.tenantId !== 'string' || !/^[1-9]\d{0,18}$/.test(data.entry.tenantId)
    || typeof data.entry.domainId !== 'string' || !/^[1-9]\d{0,18}$/.test(data.entry.domainId)
    || typeof data.entry.portal !== 'string' || !/^[a-z][a-z0-9-]{0,31}$/.test(data.entry.portal)
    || typeof data.branding?.identity?.systemName !== 'string' || !data.branding.identity.systemName.trim()
    || !['login1', 'login2', 'login3'].includes(data.branding.login?.templateId)
    || typeof data.branding.login.backgroundBlur !== 'boolean'
    || !data.branding.legal || !data.branding.assets || Array.isArray(data.branding.assets)
    || typeof data.login?.passwordEnabled !== 'boolean' || typeof data.login.captchaEnabled !== 'boolean') {
    throw new Error('Invalid tenant entry')
  }
  for (const [slot, path] of Object.entries(data.branding.assets)) {
    if (typeof path !== 'string' || path !== '/api/tenant/brand-images/' + data.branding.revisionId + '/' + slot
      || !/^(LOGO_LIGHT|LOGO_DARK|LOGO_COMPACT|FAVICON|LOGIN_BACKGROUND)$/.test(slot)
      || typeof data.branding.revisionId !== 'string' || !/^[1-9]\d{0,18}$/.test(data.branding.revisionId)) {
      throw new Error('Invalid published image')
    }
  }
  return data
}
/** Legacy browser image references only; this never fetches a remote image on the server. */
export function legacyBrandImage(value: unknown): string | undefined {
  if (typeof value !== 'string' || !value || value.length > 2048 || /[\s\u0000-\u001f\u007f\\]|%(?:0[0-9a-f]|1[0-9a-f]|5c|7f)/i.test(value)) return
  if (!value.startsWith('/') && !/^https?:\/\//i.test(value)) return
  if (value.startsWith('//')) return
  try {
    const url = new URL(value, 'https://local.invalid')
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) return
    if (value.startsWith('/') && /(?:^|\/)(?:\.|%2e){1,2}(?:\/|$)/i.test(value.split(/[?#]/, 1)[0]!)) return
    return value
  } catch { return }
}
