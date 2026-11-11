<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { get, put, remove } from '@/api/http'
import { useAuth } from '@/stores/auth'
import { usePreferences, skins, normalizeSkin } from '@/stores/preferences'
import { normalizeLocale, type AppLocale } from '@/locales'
import { notify, confirmAction } from '@/lib/feedback'
import { useUnsavedChanges } from '@/composables/useUnsavedChanges'
import { useSubmission } from '@/composables/useSubmission'
import OperationStatus from '@/components/business/OperationStatus.vue'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Card, CardContent } from '@/components/ui/card'
import PageHeader from '@/components/business/PageHeader.vue'
import EntitySelect from '@/components/business/EntitySelect.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
const { t } = useI18n(), preferences = usePreferences(), auth = useAuth(), form = reactive(preferences.snapshot()), action = useSubmission(), error = action.error, syncing = ref(false), busy = computed(() => action.busy.value || syncing.value), ready = ref(false), original = ref(JSON.stringify(form))
action.bind('config.preferences.self')
const dirty = computed(() => ready.value && JSON.stringify(form) !== original.value)
useUnsavedChanges(dirty)
const zones = ['UTC', ...Intl.supportedValuesOf('timeZone')].map(value => ({ value, label: value })), locales = [{ value: 'zh-CN', label: '简体中文' }, { value: 'en-US', label: 'English' }]
const themes = computed(() => ['light', 'dark', 'system'].map(value => ({ value, label: t('config.themes.' + value) })))
const skinOptions = computed(() => skins.map(value => ({ value, label: t('common.skins.' + value) })))
watch(() => preferences.locale, value => { form.locale = value; const baseline = JSON.parse(original.value); baseline.locale = value; original.value = JSON.stringify(baseline) })
watch(() => preferences.timeZone, value => { form.timeZone = value; const baseline = JSON.parse(original.value); baseline.timeZone = value; original.value = JSON.stringify(baseline) })
watch(() => preferences.theme, value => { form.theme = value; const baseline = JSON.parse(original.value); baseline.theme = value; original.value = JSON.stringify(baseline) })
watch(() => preferences.skin, value => { form.skin = value; const baseline = JSON.parse(original.value); baseline.skin = value; original.value = JSON.stringify(baseline) })
async function synchronize() {
  syncing.value = true
  try {
    const current = action.guard(), value = await get<ReturnType<typeof preferences.snapshot>>('/config/preferences')
    if (!current()) return
    preferences.changeLocale(value.locale || normalizeLocale(navigator.language)); preferences.changeTimeZone(value.timeZone || preferences.timeZone); preferences.applyAppearance(value)
    Object.assign(form, preferences.snapshot()); original.value = JSON.stringify(form); await auth.load(false)
    if (current()) notify(t('common.saved'))
  } catch (cause) { error.value = (cause as Error).message } finally { syncing.value = false }
}
async function save() {
  if (busy.value || !ready.value) return
  const result = await action.run({ operation: 'preferences.save', resource: 'config.preferences.self', protocol: 'LOCAL' }, () => { const input = { ...form }; return options => put('/config/preferences', input, options) })
  if (result.kind === 'success' || result.kind === 'completed') await synchronize()
}
async function reset() {
  if (busy.value || !ready.value) return
  const result = await action.run({ operation: 'preferences.reset', resource: 'config.preferences.self', protocol: 'LOCAL' }, async () => { if (!await confirmAction(t('config.resetConfirm'), t('common.confirm'))) return; return options => remove('/config/preferences', options) })
  if (result.kind === 'success' || result.kind === 'completed') await synchronize()
}
async function check() { if (await action.check() === 'SUCCEEDED') await synchronize() }
onMounted(async () => { try { Object.assign(form, await get('/config/preferences')); if (!form.locale) form.locale = preferences.locale; if (!form.timeZone) form.timeZone = preferences.timeZone; original.value = JSON.stringify(form); ready.value = true } catch (cause) { error.value = (cause as Error).message } })
</script>
<template><PageHeader :title="t('common.preferences')" /><Card class="max-w-4xl shadow-none"><CardContent class="space-y-6"><ErrorNotice :message="error" /><OperationStatus :slow="action.slow.value" :unresolved="action.unresolved.value" :querying="action.querying.value" queryable @check="check" /><form class="space-y-6" @submit.prevent="save"><fieldset class="contents" :disabled="busy || action.unresolved.value"><div class="grid gap-5 sm:grid-cols-2"><div class="space-y-2"><Label for="preference-locale">{{ t('common.language') }}</Label><EntitySelect id="preference-locale" :model-value="form.locale" :options="locales" :disabled="!ready || busy" @update:model-value="form.locale = ($event || preferences.locale) as AppLocale" /></div><div class="space-y-2"><Label for="preference-zone">{{ t('common.timeZone') }}</Label><EntitySelect id="preference-zone" :model-value="form.timeZone" :options="zones" :disabled="!ready || busy" @update:model-value="form.timeZone = String($event || preferences.timeZone)" /></div><div class="space-y-2"><Label for="preference-skin">{{ t('common.skin') }}</Label><EntitySelect id="preference-skin" :model-value="form.skin" :options="skinOptions" :disabled="!ready || busy || !preferences.personalSkinAllowed" @update:model-value="form.skin = normalizeSkin($event)" /></div><div class="space-y-2"><Label for="preference-theme">{{ t('config.theme') }}</Label><EntitySelect id="preference-theme" :model-value="form.theme" :options="themes" :disabled="!ready || busy" @update:model-value="form.theme = String($event || preferences.theme)" /></div></div><div class="space-y-5 rounded-xl border bg-muted/25 p-5"><div class="flex items-center justify-between gap-4"><Label for="preference-tabs">{{ t('config.tabsEnabled') }}</Label><Switch id="preference-tabs" v-model="form.tabsEnabled" :disabled="!ready || busy" /></div><div class="flex items-center justify-between gap-4"><Label for="preference-keep-tabs">{{ t('config.keepTabs') }}</Label><Switch id="preference-keep-tabs" v-model="form.keepTabsAfterReload" :disabled="!ready || busy || !form.tabsEnabled" /></div></div><div class="flex flex-wrap gap-2"><Button type="submit" :disabled="!ready || busy">{{ t(busy ? 'common.loading' : 'common.save') }}</Button><Button type="button" variant="outline" :disabled="!ready || busy" @click="reset">{{ t('config.resetDefaults') }}</Button></div></fieldset></form></CardContent></Card></template>
