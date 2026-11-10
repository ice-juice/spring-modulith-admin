import { createI18n } from 'vue-i18n'
import zhCommon from './zh-CN/common.json'
import enCommon from './en-US/common.json'
import type zhAuth from './zh-CN/auth.json'
import type zhIam from './zh-CN/iam.json'
import type zhConfig from './zh-CN/config.json'
import type zhAudit from './zh-CN/audit.json'
import type zhFile from './zh-CN/file.json'
import type zhMessage from './zh-CN/message.json'
import type zhMonitor from './zh-CN/monitor.json'
import type zhOpenapi from './zh-CN/openapi.json'
import type zhWorkspace from './zh-CN/workspace.json'

export const supportedLocales = ['zh-CN', 'en-US'] as const
export type AppLocale = (typeof supportedLocales)[number]
export interface MessageSchema {
  common: typeof zhCommon
  auth: typeof zhAuth
  iam: typeof zhIam
  config: typeof zhConfig
  audit: typeof zhAudit
  file: typeof zhFile
  message: typeof zhMessage
  monitor: typeof zhMonitor
  openapi: typeof zhOpenapi
  workspace: typeof zhWorkspace
}
declare module 'vue-i18n' {
  export interface DefineLocaleMessage {
    common: MessageSchema['common']
    auth: MessageSchema['auth']
    iam: MessageSchema['iam']
    config: MessageSchema['config']
    audit: MessageSchema['audit']
    file: MessageSchema['file']
    message: MessageSchema['message']
    monitor: MessageSchema['monitor']
    openapi: MessageSchema['openapi']
    workspace: MessageSchema['workspace']
  }
}

export function normalizeLocale(value?: string | null): AppLocale {
  return value?.toLowerCase().startsWith('en') ? 'en-US' : 'zh-CN'
}
export const i18n = createI18n<[Partial<MessageSchema>], AppLocale, false>({
  legacy: false,
  locale: normalizeLocale(localStorage.getItem('sma.locale') || navigator.language),
  fallbackLocale: 'zh-CN',
  messages: { 'zh-CN': { common: zhCommon }, 'en-US': { common: enCommon } },
})
const loaders = import.meta.glob(['./*/*.json', '!./*/common.json'])
const loaded = new Set(['zh-CN/common', 'en-US/common'])
export async function loadLocaleModule(module: string) {
  for (const locale of supportedLocales) {
    const key = `${locale}/${module}`
    const loader = loaders[`./${key}.json`]
    if (loaded.has(key) || !loader) continue
    const bundle = (await loader()) as { default: Record<string, unknown> }
    i18n.global.mergeLocaleMessage(locale, { [module]: bundle.default })
    loaded.add(key)
  }
}
export function setLocale(locale: AppLocale) {
  i18n.global.locale.value = locale
  localStorage.setItem('sma.locale', locale)
  document.documentElement.lang = locale
  document.title = i18n.global.t('common.brand')
}
