import { expect, it } from 'vitest'
import { canonical, inheritanceAllowed, reconcileSave, unknownWrite, validBrandUpload, type BrandContent } from '../brands'
import { checkedPreviewMessage } from '../preview-protocol'
const content: BrandContent = {
  schemaVersion: 1, identity: { systemName: 'Tenant', shortName: '', englishName: '' },
  login: { templateId: 'login1', title: { zhCN: '', enUS: '' }, description: { zhCN: '', enUS: '' }, backgroundBlur: false },
  legal: { copyrightOwner: '', copyrightStartYear: null, copyrightUrl: '', icpRecord: '', icpUrl: '', policeRecord: '', policeUrl: '' },
  links: { website: '', help: '', support: '', privacy: '', agreement: '' },
  appearance: { primaryColor: '', defaultSkin: 'iris', allowPersonalSkin: true }, assets: {}, overrides: {},
}
it('reconciles a lost save response without rounding Long versions or treating an in-flight write as failure', () => {
  const version = '9007199254740993123'
  const draft = { content, version, publishedRevisionId: null, legacyBackgroundUrl: null }
  expect(reconcileSave(version, content, draft)).toBe('unobserved')
  expect(reconcileSave(version, content, { ...draft, version: '9007199254740993124' })).toBe('matched')
  expect(reconcileSave(version, content, { ...draft, version: '9007199254740993124', content: { ...content, identity: { ...content.identity, systemName: 'Other editor' } } })).toBe('conflict')
  expect(canonical({ a: 1, b: { c: 2, d: 3 } })).toBe(canonical({ b: { d: 3, c: 2 }, a: 1 }))
})
it('retains uncertainty for transport/server failures and rejects unsupported public brand uploads', () => {
  for (const code of [0, 202, 408, 500, 502, 503]) expect(unknownWrite({ code })).toBe(true)
  for (const code of [400, 401, 403, 404, 409, 413, 429]) expect(unknownWrite({ code })).toBe(false)
  expect(unknownWrite(new Error('Lost response'))).toBe(true)
  expect(validBrandUpload({ size: 2 * 1024 * 1024, type: 'image/png' })).toBe(true)
  expect(validBrandUpload({ size: 2 * 1024 * 1024 + 1, type: 'image/png' })).toBe(false)
  expect(validBrandUpload({ size: 10, type: 'image/svg+xml' })).toBe(false)
  expect(validBrandUpload({ size: 10, type: 'image/webp' })).toBe(false)
  for (const field of ['COPYRIGHT', 'ICP_RECORD', 'POLICE_RECORD'] as const) expect(inheritanceAllowed(field)).toBe(false)
  expect(inheritanceAllowed('WELCOME_TITLE')).toBe(true)
})
it('the frame accepts bounded visual data and rejects executable templates, private URLs and unsafe links', () => {
  const message = { type: 'sma:brand-preview', locale: 'zh-CN', dark: false, preview: { content, defaultsRevision: 'builtin-1', legacyBackgroundUrl: null, images: {} } }
  expect(checkedPreviewMessage(message)).toBe(message)
  expect(checkedPreviewMessage({ ...message, preview: { ...message.preview, images: { LOGO_LIGHT: '/api/file/files/42/content' } } })).toBeUndefined()
  expect(checkedPreviewMessage({ ...message, preview: { ...message.preview, images: { LOGO_LIGHT: 'data:image/svg+xml,<svg/>' } } })).toBeUndefined()
  expect(checkedPreviewMessage({ ...message, preview: { ...message.preview, content: { ...content, login: { ...content.login, templateId: 'https://example.com/script.js' } } } })).toBeUndefined()
  expect(checkedPreviewMessage({ ...message, preview: { ...message.preview, content: { ...content, links: { ...content.links, help: 'javascript:alert(1)' } } } })).toBeUndefined()
  expect(checkedPreviewMessage({ ...message, locale: 'invalid' })).toBeUndefined()
})
