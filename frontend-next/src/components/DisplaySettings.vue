<script setup lang="ts">
import { Sun, Moon, Monitor, Languages } from '@lucide/vue'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuLabel } from '@/components/ui/dropdown-menu'
import { usePreferences } from '@/stores/preferences'
import { useAuth } from '@/stores/auth'
import { put } from '@/api/http'
import { notify } from '@/lib/feedback'
import EditorDialog from '@/components/business/EditorDialog.vue'
import EntitySelect from '@/components/business/EntitySelect.vue'
import type { AppLocale } from '@/locales'
const { t } = useI18n(), preferences = usePreferences()
const busy = ref(false), zoneOpen = ref(false), zone = ref(preferences.timeZone)
const zones = ['UTC', ...Intl.supportedValuesOf('timeZone')].map(value => ({ value, label: value }))
const themes = [{ id: 'light', icon: Sun }, { id: 'dark', icon: Moon }, { id: 'system', icon: Monitor }]
const locales: { id: AppLocale; label: string }[] = [{ id: 'zh-CN', label: '简体中文' }, { id: 'en-US', label: 'English' }]
async function persist() { const auth = useAuth(); if (!auth.user) return; busy.value = true; const id = auth.user.id; try { await put('/config/preferences', preferences.snapshot()); if (auth.user?.id === id) await auth.load(false) } catch (cause) { notify((cause as Error).message, 'error') } finally { busy.value = false } }
async function language(value: AppLocale) { preferences.changeLocale(value); await persist() }
async function appearance(value: string) { preferences.applyAppearance({ theme: value }); await persist() }
async function timeZone() { preferences.changeTimeZone(zone.value); await persist(); zoneOpen.value = false }
</script>
<template><DropdownMenu><DropdownMenuTrigger as-child><Button variant="ghost" size="icon" :aria-label="t('common.settings')" :disabled="busy"><Languages class="size-4" /></Button></DropdownMenuTrigger><DropdownMenuContent align="end" class="w-44"><DropdownMenuLabel>{{ t('common.theme') }}</DropdownMenuLabel><DropdownMenuItem v-for="theme in themes" :key="theme.id" :class="{ 'bg-accent': preferences.theme === theme.id }" @select="appearance(theme.id)"><component :is="theme.icon" class="size-4" />{{ t('common.' + theme.id) }}</DropdownMenuItem><DropdownMenuSeparator /><DropdownMenuLabel>{{ t('common.language') }}</DropdownMenuLabel><DropdownMenuItem v-for="locale in locales" :key="locale.id" :class="{ 'bg-accent': preferences.locale === locale.id }" @select="language(locale.id)">{{ locale.label }}</DropdownMenuItem><DropdownMenuSeparator /><DropdownMenuItem @select="zone = preferences.timeZone; zoneOpen = true">{{ t('common.timeZone') }}</DropdownMenuItem></DropdownMenuContent></DropdownMenu><EditorDialog v-model="zoneOpen" :title="t('common.timeZone')" :busy="busy" @save="timeZone"><EntitySelect :model-value="zone" :options="zones" :placeholder="t('common.timeZone')" @update:model-value="zone = String($event || preferences.timeZone)" /></EditorDialog></template>
