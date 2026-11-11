<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { categories, resolveIcon, searchIcons, iconLabel } from '@/lib/icons'
import AppIcon from './AppIcon.vue'
import EditorDialog from './EditorDialog.vue'
defineProps<{ id?: string; disabled?: boolean; invalid?: boolean; descriptionId?: string }>()
const value = defineModel<string>({ default: '' })
const { t, locale } = useI18n()
const open = ref(false), draft = ref(''), query = ref(''), kind = ref('all'), category = ref('all'), page = ref(1)
const current = computed(() => resolveIcon(value.value)), selected = computed(() => resolveIcon(draft.value))
const matches = computed(() => searchIcons(query.value, kind.value, category.value))
const pages = computed(() => Math.max(1, Math.ceil(matches.value.length / 48)))
const shown = computed(() => matches.value.slice((page.value - 1) * 48, page.value * 48))
watch([query, kind, category], () => { page.value = 1 })
function choose() { draft.value = value.value; query.value = ''; kind.value = 'all'; category.value = 'all'; page.value = 1; open.value = true }
function apply() { value.value = draft.value; open.value = false }
</script>
<template>
  <div class="min-w-0 space-y-2">
    <div class="flex min-w-0 items-center gap-2">
      <Button :id="id" type="button" variant="outline" class="min-w-0 flex-1 justify-start" :disabled="disabled" :aria-invalid="invalid" :aria-describedby="descriptionId" @click="choose">
        <AppIcon v-if="value" :name="value" :size="24" />
        <span class="truncate">{{ current ? iconLabel(current, locale) : value === 'none' ? t('common.icons.none') : value || t('common.icons.choose') }}</span>
      </Button>
      <Button v-if="value && value !== 'none'" type="button" variant="ghost" :disabled="disabled" @click="value = 'none'">{{ t('common.icons.clear') }}</Button>
    </div>
    <p v-if="value && value !== 'none' && !current" class="text-xs text-muted-foreground">{{ t('common.icons.unknown') }}</p>
  </div>
  <EditorDialog v-model="open" wide :title="t('common.icons.choose')" :description="t('common.icons.hint')" :save-label="t('common.icons.use')" @save="apply">
    <div class="space-y-4">
      <Input v-model="query" :placeholder="t('common.icons.search')" :aria-label="t('common.icons.search')" />
      <div class="grid grid-cols-2 gap-3">
        <label class="grid gap-1 text-xs">{{ t('common.icons.library') }}<select v-model="kind" class="h-9 min-w-0 rounded-md border border-input bg-background px-2 text-sm"><option value="all">{{ t('common.icons.all') }}</option><option value="lucide">{{ t('common.icons.line') }}</option><option value="3d">{{ t('common.icons.three') }}</option></select></label>
        <label class="grid gap-1 text-xs">{{ t('common.icons.category') }}<select v-model="category" class="h-9 min-w-0 rounded-md border border-input bg-background px-2 text-sm"><option value="all">{{ t('common.icons.all') }}</option><option v-for="item in categories" :key="item" :value="item">{{ t('common.icons.categories.' + item) }}</option></select></label>
      </div>
      <p role="status" class="text-xs text-muted-foreground">{{ t('common.icons.count', { count: matches.length }) }}</p>
      <div class="grid max-h-64 grid-cols-3 gap-2 overflow-y-auto p-1 sm:grid-cols-6" role="group" :aria-label="t('common.icons.results')">
        <button v-for="entry in shown" :key="entry.id" type="button" class="flex min-h-20 min-w-0 flex-col items-center justify-center gap-1 rounded-lg border p-2 hover:bg-accent" :class="draft === entry.id ? 'border-primary bg-accent text-accent-foreground' : 'border-border bg-card'" :aria-pressed="draft === entry.id" :aria-label="iconLabel(entry, locale) + ' · ' + entry.id" :title="entry.id" @click="draft = entry.id">
          <AppIcon :name="entry.id" :size="entry.file ? 40 : 28" /><span class="w-full truncate text-center text-xs">{{ iconLabel(entry, locale) }}</span>
        </button>
      </div>
      <p v-if="!matches.length" class="py-4 text-center text-sm text-muted-foreground">{{ t('common.noMatch') }}</p>
      <div class="flex items-center justify-between gap-2"><Button type="button" variant="outline" size="sm" :disabled="page <= 1" @click="page--">{{ t('common.previousPage') }}</Button><span class="text-xs">{{ t('common.pageOf', { page, pages }) }}</span><Button type="button" variant="outline" size="sm" :disabled="page >= pages" @click="page++">{{ t('common.nextPage') }}</Button></div>
      <div class="rounded-lg border p-3">
        <p class="mb-2 break-all text-xs text-muted-foreground">{{ t('common.icons.preview') + ' · ' + (selected ? iconLabel(selected, locale) : draft && draft !== 'none' ? draft : t('common.icons.none')) }}</p>
        <div class="grid grid-cols-2 gap-2">
          <div v-for="dark in [false, true]" :key="String(dark)" class="space-y-2 rounded-lg border p-3" :class="dark ? 'bg-[#1d1e28] text-[#eeeef4]' : 'bg-white text-[#252638]'">
            <span class="block text-xs">{{ t(dark ? 'common.dark' : 'common.light') }}</span>
            <div class="flex items-center gap-2 text-xs"><AppIcon v-if="draft" :name="draft" :size="24" /><span>{{ t('common.icons.menuExample') }}</span></div>
            <div class="inline-flex items-center gap-2 rounded border border-current/30 px-2 py-1 text-xs"><AppIcon v-if="draft" :name="draft" :size="18" /><span>{{ t('common.icons.buttonExample') }}</span></div>
          </div>
        </div>
      </div>
    </div>
    <template #footer><Button type="button" variant="ghost" @click="draft = 'none'">{{ t('common.icons.none') }}</Button><Button type="button" variant="outline" @click="open = false">{{ t('common.cancel') }}</Button><Button type="button" @click="apply">{{ t('common.icons.use') }}</Button></template>
  </EditorDialog>
</template>
