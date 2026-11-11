import { safeBrowserDestination, safeHttpLink } from '@/lib/web-addresses'
import type { BrandPreview } from './brands'

export interface PreviewMessage { type: 'sma:brand-preview'; preview: BrandPreview; locale: 'zh-CN' | 'en-US'; dark: boolean }
/** The preview frame never accepts arbitrary components, HTML, script or remotely supplied image URLs. */
export function checkedPreviewMessage(raw: unknown): PreviewMessage | undefined {
  if (!raw || typeof raw !== 'object') return
  const value = raw as Partial<PreviewMessage>, preview = value.preview
  if (value.type !== 'sma:brand-preview' || !['zh-CN', 'en-US'].includes(value.locale || '') || typeof value.dark !== 'boolean'
    || !preview || !preview.content || preview.content.schemaVersion !== 1 || !preview.images || Array.isArray(preview.images)) return
  const content = preview.content
  if (typeof content.identity?.systemName !== 'string' || content.identity.systemName.length > 128
    || !['login1', 'login2', 'login3'].includes(content.login?.templateId) || typeof content.login.backgroundBlur !== 'boolean'
    || !content.login.title || !content.login.description || !content.legal || !content.links || !content.appearance
    || !['iris', 'celadon', 'amber'].includes(content.appearance.defaultSkin)) return
  for (const text of [content.login.title.zhCN, content.login.title.enUS, content.login.description.zhCN, content.login.description.enUS]) {
    if (text !== null && (typeof text !== 'string' || text.length > 500)) return
  }
  for (const text of [content.identity.shortName, content.identity.englishName, content.legal.copyrightOwner, content.legal.icpRecord, content.legal.policeRecord]) {
    if (text !== null && (typeof text !== 'string' || text.length > 128)) return
  }
  for (const url of [content.legal.copyrightUrl, content.legal.icpUrl, content.legal.policeUrl, content.links.website, content.links.help, content.links.support, content.links.privacy, content.links.agreement]) {
    if (url !== null && url !== '' && (typeof url !== 'string' || url.length > 2048 || !safeHttpLink(url))) return
  }
  if (content.appearance.primaryColor !== null && content.appearance.primaryColor !== '' && !/^#[a-fA-F0-9]{6}$/.test(content.appearance.primaryColor)) return
  if (typeof content.appearance.allowPersonalSkin !== 'boolean') return
  if (content.legal.copyrightStartYear !== null && (!Number.isInteger(content.legal.copyrightStartYear) || content.legal.copyrightStartYear < 1900 || content.legal.copyrightStartYear > 9999)) return
  if (preview.legacyBackgroundUrl !== null && !safeBrowserDestination(preview.legacyBackgroundUrl)) return
  const images = Object.entries(preview.images)
  if (images.length > 5) return
  for (const [slot, url] of images) {
    if (!['LOGO_LIGHT', 'LOGO_DARK', 'LOGO_COMPACT', 'FAVICON', 'LOGIN_BACKGROUND'].includes(slot)
      || typeof url !== 'string' || url.length > 2_796_230 || !/^data:image\/png;base64,[a-zA-Z0-9+/]+={0,2}$/.test(url)) return
  }
  // Bound every other text field before rendering, including future unexpected payload additions.
  try { if (JSON.stringify(value).length > 14_100_000) return } catch { return }
  return value as PreviewMessage
}
