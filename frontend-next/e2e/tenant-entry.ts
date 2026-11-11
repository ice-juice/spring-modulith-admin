import type { TenantEntryDto } from '../src/lib/tenant-branding'
export function tenantEntry(name = 'SMA 测试', captcha = false, portal = 'admin'): TenantEntryDto {
  return {
    entry: { tenantId: '1', domainId: '1', portal },
    branding: {
      revisionId: null, revision: '0', identity: { systemName: name },
      login: { templateId: 'login1', backgroundBlur: false },
      legal: {}, assets: {}, legacyBackgroundUrl: null,
    },
    login: { passwordEnabled: true, captchaEnabled: captcha },
  }
}
