import { createI18n } from 'vue-i18n'
import zhCommon from './zh-CN/common.json'
import enCommon from './en-US/common.json'
import type zhAuth from './zh-CN/auth.json'

export const supportedLocales = ['zh-CN', 'en-US'] as const
export type AppLocale = (typeof supportedLocales)[number]
export interface MessageSchema {
  common: typeof zhCommon
  auth: typeof zhAuth
}
declare module 'vue-i18n' {
  export interface DefineLocaleMessage {
    common: MessageSchema['common']
    auth: MessageSchema['auth']
  }
}

export function normalizeLocale(value?: string | null): AppLocale {
  return value?.toLowerCase().startsWith('en') ? 'en-US' : 'zh-CN'
}
export const i18n = createI18n({
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
