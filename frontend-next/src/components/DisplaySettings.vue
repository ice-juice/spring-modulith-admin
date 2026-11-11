<script setup lang="ts">
import { Sun, Moon, Monitor, Languages, Palette, RefreshCw } from '@lucide/vue'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuLabel, DropdownMenuRadioGroup, DropdownMenuRadioItem } from '@/components/ui/dropdown-menu'
import { usePreferences, skins } from '@/stores/preferences'
import { useAuth } from '@/stores/auth'
import { get, put } from '@/api/http'
import { useSubmission } from '@/composables/useSubmission'
import { notify } from '@/lib/feedback'
import EditorDialog from '@/components/business/EditorDialog.vue'
import OperationStatus from '@/components/business/OperationStatus.vue'
import EntitySelect from '@/components/business/EntitySelect.vue'
import type { AppLocale } from '@/locales'
const { t } = useI18n(), preferences = usePreferences()
const action = useSubmission(), syncing = ref(false), busy = computed(() => action.busy.value || action.unresolved.value || syncing.value), zoneOpen = ref(false), zone = ref(preferences.timeZone)
const zones = ['UTC', ...Intl.supportedValuesOf('timeZone')].map(value => ({ value, label: value }))
const themes = [{ id: 'light', icon: Sun }, { id: 'dark', icon: Moon }, { id: 'system', icon: Monitor }]
const activeTheme = computed(() => themes.find(theme => theme.id === preferences.theme) || themes[2]!)
const locales: { id: AppLocale; label: string }[] = [{ id: 'zh-CN', label: '简体中文' }, { id: 'en-US', label: 'English' }]
async function persist(previous: ReturnType<typeof preferences.snapshot>) {
  const auth = useAuth()
  if (!auth.user) return true
  const result = await action.run({ operation: 'preferences.save', resource: 'config.preferences.self', protocol: 'LOCAL' }, () => { const input = preferences.snapshot(); return options => put('/config/preferences', input, options) })
  if (result.kind === 'stale' || result.kind === 'cancelled') return false
  if (result.kind === 'success' || result.kind === 'completed') { await synchronize(); return true }
  // Revert the local preview only. UNKNOWN remains registered and may already have committed on the server.
  preferences.changeLocale(previous.locale); preferences.changeTimeZone(previous.timeZone); preferences.applyAppearance(previous)
  if (action.error.value) notify(action.error.value, 'error')
  return false
}
async function synchronize() {
  syncing.value = true
  try { const current = action.guard(), value = await get<ReturnType<typeof preferences.snapshot>>('/config/preferences'); if (!current()) return; preferences.changeLocale(value.locale || preferences.locale); preferences.changeTimeZone(value.timeZone || preferences.timeZone); preferences.applyAppearance(value); await useAuth().load(false) }
  catch (cause) { notify((cause as Error).message, 'error') }
  finally { syncing.value = false }
}
async function check() { if (await action.check() === 'SUCCEEDED') { await synchronize(); zoneOpen.value = false } else if (action.error.value) notify(action.error.value, 'error') }
async function language(value: AppLocale) { if (busy.value) return; const previous = preferences.snapshot(); preferences.changeLocale(value); await persist(previous) }
async function appearance(value: string) { if (busy.value) return; const previous = preferences.snapshot(); preferences.applyAppearance({ theme: value }); await persist(previous) }
async function skin(value: string) { if (busy.value || !preferences.personalSkinAllowed) return; const previous = preferences.snapshot(); preferences.applyAppearance({ skin: value }); await persist(previous) }
async function timeZone() { if (busy.value) return; const previous = preferences.snapshot(); preferences.changeTimeZone(zone.value); if (await persist(previous)) zoneOpen.value = false }
</script>
<template>
  <Button v-if="action.unresolved.value" variant="ghost" size="icon" :aria-label="t('common.operation.check')" :title="t('common.operation.check')" :disabled="action.querying.value" @click="check"><RefreshCw class="size-4" /></Button>
  <div v-else class="flex items-center gap-1">
    <DropdownMenu :modal="false">
      <DropdownMenuTrigger as-child><Button variant="ghost" size="icon" :aria-label="t('common.skin')" :title="t('common.skin') + ' · ' + t('common.skins.' + preferences.skin)" :disabled="busy || !preferences.personalSkinAllowed"><Palette class="size-4 text-primary" /></Button></DropdownMenuTrigger>
      <DropdownMenuContent align="end" class="w-44">
        <DropdownMenuLabel>{{ t('common.skin') }}</DropdownMenuLabel>
        <DropdownMenuRadioGroup :model-value="preferences.skin" @update:model-value="typeof $event === 'string' && skin($event)">
          <DropdownMenuRadioItem v-for="item in skins" :key="item" :value="item" :disabled="busy || !preferences.personalSkinAllowed"><span aria-hidden="true" class="skin-swatch size-3.5 shrink-0 rounded-full" :data-swatch="item" />{{ t('common.skins.' + item) }}</DropdownMenuRadioItem>
        </DropdownMenuRadioGroup>
      </DropdownMenuContent>
    </DropdownMenu>
    <DropdownMenu>
      <DropdownMenuTrigger as-child><Button variant="ghost" size="icon" :aria-label="t('common.theme')" :title="t('common.theme') + ' · ' + t('common.' + activeTheme.id)" :disabled="busy"><component :is="activeTheme.icon" class="size-4" /></Button></DropdownMenuTrigger>
      <DropdownMenuContent align="end" class="w-44">
        <DropdownMenuLabel>{{ t('common.theme') }}</DropdownMenuLabel>
        <DropdownMenuItem v-for="theme in themes" :key="theme.id" :class="{ 'bg-accent': preferences.theme === theme.id }" @select="appearance(theme.id)"><component :is="theme.icon" class="size-4" />{{ t('common.' + theme.id) }}</DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
    <DropdownMenu>
      <DropdownMenuTrigger as-child><Button variant="ghost" size="icon" :aria-label="t('common.languageAndTimeZone')" :title="t('common.languageAndTimeZone')" :disabled="busy"><Languages class="size-4" /></Button></DropdownMenuTrigger>
      <DropdownMenuContent align="end" class="w-44">
        <DropdownMenuLabel>{{ t('common.language') }}</DropdownMenuLabel>
        <DropdownMenuItem v-for="locale in locales" :key="locale.id" :class="{ 'bg-accent': preferences.locale === locale.id }" @select="language(locale.id)">{{ locale.label }}</DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem @select="zone = preferences.timeZone; zoneOpen = true">{{ t('common.timeZone') }}</DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  </div>
  <EditorDialog v-model="zoneOpen" :title="t('common.timeZone')" :busy="action.busy.value || syncing" :submit-disabled="action.unresolved.value" @save="timeZone"><OperationStatus :slow="action.slow.value" :unresolved="action.unresolved.value" :querying="action.querying.value" queryable @check="check" /><EntitySelect :model-value="zone" :options="zones" :disabled="busy" :placeholder="t('common.timeZone')" @update:model-value="zone = String($event || preferences.timeZone)" /></EditorDialog>
</template>
