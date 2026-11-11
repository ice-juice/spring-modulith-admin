import { defineStore } from 'pinia'
import { computed, ref, watchEffect, onScopeDispose } from 'vue'
import { i18n, setLocale, type AppLocale } from '@/locales'
import { resolveTimeZone } from '@/lib/time'

export const skins = ['iris', 'celadon', 'amber'] as const
export type Skin = typeof skins[number]
export function normalizeSkin(value: unknown): Skin { return skins.includes(value as Skin) ? value as Skin : 'iris' }

export const usePreferences = defineStore('preferences', () => {
  const userZone = ref<string | null>(localStorage.getItem('sma.time-zone'))
  const tenantZone = ref<string | null>(null)
  const theme = ref(localStorage.getItem('sma.theme') || 'light')
  let skinOwner = 'guest'
  const policy = ref<{ scope: string; defaultSkin: Skin; allowPersonalSkin: boolean }>()
  const personalSkin = ref<Skin | undefined>(readSkin('sma.next.skin.guest'))
  const personalSkinAllowed = computed(() => policy.value?.allowPersonalSkin !== false)
  const skin = computed(() => !personalSkinAllowed.value ? policy.value!.defaultSkin : personalSkin.value || policy.value?.defaultSkin || 'iris')
  function readSkin(key: string): Skin | undefined {
    try { const value = localStorage.getItem(key); return skins.includes(value as Skin) ? value as Skin : undefined } catch { return undefined }
  }
  function skinKey() { return 'sma.next.skin.' + (policy.value ? policy.value.scope + '.' : '') + skinOwner }
  function setBrandPolicy(scope?: string, value?: { defaultSkin: string; allowPersonalSkin: boolean }) {
    const changed = scope !== policy.value?.scope
    policy.value = scope && value ? { scope, defaultSkin: normalizeSkin(value.defaultSkin), allowPersonalSkin: value.allowPersonalSkin } : undefined
    if (changed && skinOwner === 'guest') personalSkin.value = readSkin(skinKey())
  }
  const tabsEnabled = ref(true)
  const keepTabsAfterReload = ref(false)
  const media = window.matchMedia('(prefers-color-scheme: dark)')
  const systemDark = ref(media.matches)
  const updateMedia = (event: MediaQueryListEvent) => {
    systemDark.value = event.matches
  }
  media.addEventListener('change', updateMedia)
  onScopeDispose(() => media.removeEventListener('change', updateMedia))
  const timeZone = computed(() => resolveTimeZone(userZone.value, tenantZone.value))
  const locale = computed(() => i18n.global.locale.value)
  watchEffect(() => {
    document.documentElement.dataset.skin = skin.value
    document.documentElement.classList.toggle(
      'dark',
      theme.value === 'dark' || (theme.value === 'system' && systemDark.value),
    )
  })
  function changeLocale(value: AppLocale) {
    setLocale(value)
  }
  function changeTimeZone(value: string) {
    userZone.value = resolveTimeZone(value)
    localStorage.setItem('sma.time-zone', userZone.value)
  }
  function applyAppearance(value: {
    theme?: string
    skin?: string
    skinOwner?: string
    tabsEnabled?: boolean
    keepTabsAfterReload?: boolean
  }) {
    if (value.skinOwner && value.skinOwner !== skinOwner) { skinOwner = value.skinOwner; personalSkin.value = readSkin(skinKey()) }
    if (value.skin !== undefined && (personalSkinAllowed.value || value.skinOwner)) {
      personalSkin.value = normalizeSkin(value.skin)
      try { localStorage.setItem(skinKey(), personalSkin.value) } catch { /* Appearance remains usable without storage. */ }
    }
    if (value.theme && ['light', 'dark', 'system'].includes(value.theme)) {
      theme.value = value.theme
      localStorage.setItem('sma.theme', theme.value)
    }
    if (typeof value.tabsEnabled === 'boolean') tabsEnabled.value = value.tabsEnabled
    if (typeof value.keepTabsAfterReload === 'boolean')
      keepTabsAfterReload.value = value.keepTabsAfterReload
  }
  function snapshot() {
    return {
      locale: locale.value,
      timeZone: timeZone.value,
      theme: theme.value,
      skin: skin.value,
      tabsEnabled: tabsEnabled.value,
      keepTabsAfterReload: keepTabsAfterReload.value,
    }
  }
  return {
    userZone,
    tenantZone,
    timeZone,
    locale,
    theme,
    skin,
    personalSkinAllowed,
    setBrandPolicy,
    tabsEnabled,
    keepTabsAfterReload,
    changeLocale,
    changeTimeZone,
    applyAppearance,
    snapshot,
  }
})
