<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { get, put, remove } from '@/api/http'
import { useAuth } from '@/stores/auth'
import { usePreferences } from '@/stores/preferences'
import { normalizeLocale, type AppLocale } from '@/locales'
import { notify, confirmAction } from '@/lib/feedback'
import { useUnsavedChanges } from '@/composables/useUnsavedChanges'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Card, CardContent } from '@/components/ui/card'
import PageHeader from '@/components/business/PageHeader.vue'
import EntitySelect from '@/components/business/EntitySelect.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
const { t } = useI18n(), preferences = usePreferences(), auth = useAuth(), form = reactive(preferences.snapshot()), error = ref(''), busy = ref(false), ready = ref(false), original = ref(JSON.stringify(form))
const dirty = computed(() => ready.value && JSON.stringify(form) !== original.value)
useUnsavedChanges(dirty)
const zones = ['UTC', ...Intl.supportedValuesOf('timeZone')].map(value => ({ value, label: value })), locales = [{ value: 'zh-CN', label: '简体中文' }, { value: 'en-US', label: 'English' }]
const themes = computed(() => ['light', 'dark', 'system'].map(value => ({ value, label: t('config.themes.' + value) })))
watch(() => preferences.locale, value => { form.locale = value; const baseline = JSON.parse(original.value); baseline.locale = value; original.value = JSON.stringify(baseline) })
watch(() => preferences.timeZone, value => { form.timeZone = value; const baseline = JSON.parse(original.value); baseline.timeZone = value; original.value = JSON.stringify(baseline) })
watch(() => preferences.theme, value => { form.theme = value; const baseline = JSON.parse(original.value); baseline.theme = value; original.value = JSON.stringify(baseline) })
async function save() { if (busy.value || !ready.value) return; busy.value = true; error.value = ''; try { await put('/config/preferences', form); preferences.changeLocale(form.locale); preferences.changeTimeZone(form.timeZone); preferences.applyAppearance(form); await auth.load(false); original.value = JSON.stringify(form); notify(t('common.saved')) } catch (cause) { error.value = (cause as Error).message } finally { busy.value = false } }
async function reset() { if (busy.value || !await confirmAction(t('config.resetConfirm'), t('common.confirm'))) return; busy.value = true; try { await remove('/config/preferences'); preferences.changeLocale(normalizeLocale(navigator.language)); await auth.load(); Object.assign(form, preferences.snapshot()); original.value = JSON.stringify(form); notify(t('common.saved')) } catch (cause) { error.value = (cause as Error).message } finally { busy.value = false } }
onMounted(async () => { try { Object.assign(form, await get('/config/preferences')); if (!form.locale) form.locale = preferences.locale; if (!form.timeZone) form.timeZone = preferences.timeZone; original.value = JSON.stringify(form); ready.value = true } catch (cause) { error.value = (cause as Error).message } })
</script>
<template><PageHeader :title="t('common.preferences')" /><Card class="max-w-3xl shadow-none"><CardContent class="space-y-6"><ErrorNotice :message="error" /><form class="space-y-6" @submit.prevent="save"><div class="grid gap-5 sm:grid-cols-2"><div class="space-y-2"><Label for="preference-locale">{{ t('common.language') }}</Label><EntitySelect id="preference-locale" :model-value="form.locale" :options="locales" :disabled="!ready || busy" @update:model-value="form.locale = ($event || preferences.locale) as AppLocale" /></div><div class="space-y-2"><Label for="preference-zone">{{ t('common.timeZone') }}</Label><EntitySelect id="preference-zone" :model-value="form.timeZone" :options="zones" :disabled="!ready || busy" @update:model-value="form.timeZone = String($event || preferences.timeZone)" /></div><div class="space-y-2"><Label for="preference-theme">{{ t('config.theme') }}</Label><EntitySelect id="preference-theme" :model-value="form.theme" :options="themes" :disabled="!ready || busy" @update:model-value="form.theme = String($event || preferences.theme)" /></div></div><div class="space-y-5 border-y py-5"><div class="flex items-center justify-between gap-4"><Label for="preference-tabs">{{ t('config.tabsEnabled') }}</Label><Switch id="preference-tabs" v-model="form.tabsEnabled" :disabled="!ready || busy" /></div><div class="flex items-center justify-between gap-4"><Label for="preference-keep-tabs">{{ t('config.keepTabs') }}</Label><Switch id="preference-keep-tabs" v-model="form.keepTabsAfterReload" :disabled="!ready || busy || !form.tabsEnabled" /></div></div><div class="flex flex-wrap gap-2"><Button type="submit" :disabled="!ready || busy">{{ t(busy ? 'common.loading' : 'common.save') }}</Button><Button type="button" variant="outline" :disabled="!ready || busy" @click="reset">{{ t('config.resetDefaults') }}</Button></div></form></CardContent></Card></template>
