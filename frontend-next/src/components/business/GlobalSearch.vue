<script setup lang="ts">
import { computed, nextTick, onScopeDispose, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { Search, LoaderCircle, ExternalLink } from '@lucide/vue'
import { Dialog, DialogContent, DialogTitle, DialogDescription } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useAuth } from '@/stores/auth'
import { searchMenus, safeSearchTarget, type SearchHit, type SearchContext } from '@/lib/search'
import { useGlobalSearch } from '@/composables/useGlobalSearch'
import AppIcon from './AppIcon.vue'
const open = defineModel<boolean>({ required: true })
const { t, locale } = useI18n(), auth = useAuth(), router = useRouter(), engine = useGlobalSearch()
const query = ref(''), mode = ref('menus'), menuLimit = ref(20), waiting = ref(false), input = ref<InstanceType<typeof Input>>()
const results = ref<HTMLElement>()
const identity = computed(() => `${auth.user?.portal}:${auth.user?.id}:${auth.user?.locked}:${auth.user?.permissions.join('|')}`)
const context = computed<SearchContext>(() => ({ identity: identity.value, permissions: auth.user?.permissions || [], menus: auth.user?.menus || [] }))
const menuHits = computed(() => searchMenus(context.value.menus, query.value))
const groups = engine.groups
const busy = computed(() => waiting.value || groups.value.some(group => group.loading))
let timer: ReturnType<typeof setTimeout> | undefined
function stop() { clearTimeout(timer); waiting.value = false; engine.clear() }
watch([query, mode, open, locale], () => {
  stop(); menuLimit.value = 20
  if (!open.value) { query.value = ''; return }
  if (mode.value !== 'global' || !query.value.trim()) return
  waiting.value = true
  timer = setTimeout(() => { waiting.value = false; void engine.search(query.value, context.value) }, 250)
})
watch(identity, () => { open.value = false; query.value = ''; stop() }, { flush: 'sync' })
onScopeDispose(stop)
async function focus(event: Event) { event.preventDefault(); await nextTick(); (input.value?.$el as HTMLInputElement | undefined)?.focus() }
async function navigate(hit: SearchHit) {
  if (!safeSearchTarget(hit.target) || auth.user?.locked) return
  if (hit.target.kind === 'external') { window.open(hit.target.url, '_blank', 'noopener,noreferrer'); open.value = false }
  else {
    if (hit.target.query?.focus && router.currentRoute.value.fullPath === router.resolve({ path: hit.target.path, query: hit.target.query }).fullPath) {
      const query = { ...hit.target.query }; delete query.focus
      if (await router.replace({ path: hit.target.path, query })) return
      await nextTick()
    }
    const failure = await router.push({ path: hit.target.path, query: hit.target.query })
    if (!failure || router.currentRoute.value.fullPath === router.resolve({ path: hit.target.path, query: hit.target.query }).fullPath) open.value = false
  }
}
function keys(event: KeyboardEvent) {
  const target = event.target as HTMLElement
  const fromInput = target.tagName === 'INPUT'
  if (!fromInput && !target.closest('[data-search-result]')) return
  const buttons = [...(results.value?.querySelectorAll<HTMLButtonElement>('[data-search-result]') || [])]
  if (!buttons.length) return
  if (event.key === 'Enter' && fromInput) { event.preventDefault(); buttons[0]?.click() }
  if (['ArrowDown', 'ArrowUp'].includes(event.key)) {
    event.preventDefault()
    const index = buttons.indexOf(target.closest('[data-search-result]') as HTMLButtonElement)
    const next = index < 0 ? event.key === 'ArrowDown' ? 0 : buttons.length - 1 : (index + (event.key === 'ArrowDown' ? 1 : -1) + buttons.length) % buttons.length
    buttons[next]?.focus(); buttons[next]?.scrollIntoView({ block: 'nearest' })
  }
}
</script>
<template>
  <Dialog v-model:open="open">
    <DialogContent class="flex max-h-[90dvh] flex-col gap-0 overflow-hidden p-0 sm:max-w-2xl" @open-auto-focus="focus" @keydown="keys">
      <div class="shrink-0 space-y-3 border-b p-4 pr-10 sm:p-5 sm:pr-12">
        <DialogTitle class="text-base">{{ t('common.unifiedSearch.title') }}</DialogTitle>
        <DialogDescription class="text-xs">{{ t('common.unifiedSearch.description') }}</DialogDescription>
        <div class="relative"><Search aria-hidden="true" class="absolute left-3 top-2.5 size-4 text-muted-foreground" /><Input ref="input" v-model="query" maxlength="100" class="pl-9" :placeholder="t('common.unifiedSearch.placeholder')" :aria-label="t('common.unifiedSearch.placeholder')" autocomplete="off" /></div>
        <div class="flex gap-2" role="group" :aria-label="t('common.unifiedSearch.mode')"><Button v-for="item in ['menus', 'global']" :key="item" size="sm" :variant="mode === item ? 'secondary' : 'ghost'" :aria-pressed="mode === item" @click="mode = item">{{ t('common.unifiedSearch.' + item) }}</Button></div>
      </div>
      <div ref="results" class="min-h-0 flex-1 space-y-4 overflow-y-auto overscroll-contain p-3 sm:p-4" :aria-busy="busy">
        <p v-if="mode === 'global' && !query.trim()" class="p-2 text-sm text-muted-foreground">{{ t('common.unifiedSearch.globalHint') }}</p>
        <section v-if="menuHits.length && (mode === 'menus' || query.trim())" :aria-label="t('common.unifiedSearch.menus')">
          <h2 class="mb-1 px-2 text-xs font-medium text-muted-foreground">{{ t('common.unifiedSearch.menus') + ' · ' + menuHits.length }}</h2>
          <ul><li v-for="hit in menuHits.slice(0, menuLimit)" :key="hit.id"><button type="button" data-search-result class="flex w-full items-center gap-3 rounded-lg p-3 text-left hover:bg-accent focus-visible:bg-accent" @click="navigate(hit)"><AppIcon :name="hit.icon" :size="22" /><span class="min-w-0 flex-1"><span class="block truncate text-sm font-medium">{{ hit.title }}</span><span class="block truncate text-xs text-muted-foreground">{{ hit.description }}</span></span><ExternalLink v-if="hit.target.kind === 'external'" class="size-4" :aria-label="t('common.unifiedSearch.external')" /></button></li></ul>
          <Button v-if="menuHits.length > menuLimit" variant="ghost" size="sm" @click="menuLimit += 20">{{ t('common.unifiedSearch.more') }}</Button>
        </section>
        <section v-for="group in groups" :key="group.provider.id" :aria-label="t(group.provider.labelKey)" class="space-y-1">
          <h2 class="px-2 text-xs font-medium text-muted-foreground">{{ t(group.provider.labelKey) }}</h2>
          <ul><li v-for="hit in group.items" :key="hit.id"><button type="button" data-search-result class="flex w-full items-center gap-3 rounded-lg p-3 text-left hover:bg-accent focus-visible:bg-accent" @click="navigate(hit)"><AppIcon :name="hit.icon" :size="20" /><span class="min-w-0"><span class="block break-words text-sm font-medium">{{ hit.title }}</span><span v-if="hit.description" class="block break-all text-xs text-muted-foreground">{{ hit.description }}</span></span></button></li></ul>
          <p v-if="group.loading" class="flex items-center gap-2 px-2 text-xs text-muted-foreground"><LoaderCircle class="size-3 animate-spin" />{{ t('common.loading') }}</p>
          <div v-else-if="group.error" class="flex items-center justify-between gap-2 px-2"><span class="text-xs text-destructive">{{ t('common.unifiedSearch.unavailable') }}</span><Button size="sm" variant="ghost" @click="engine.retry(group.provider.id)">{{ t('common.unifiedSearch.retry') }}</Button></div>
          <p v-else-if="!group.items.length" class="px-2 py-2 text-xs text-muted-foreground">{{ t('common.noMatch') }}</p>
          <Button v-else-if="group.hasMore" size="sm" variant="ghost" @click="engine.more(group.provider.id)">{{ t('common.unifiedSearch.more') }}</Button>
        </section>
        <p v-if="mode === 'menus' && !menuHits.length" class="p-4 text-center text-sm text-muted-foreground">{{ t('common.noMatch') }}</p>
        <p v-if="waiting" role="status" class="px-2 text-sm text-muted-foreground">{{ t('common.loading') }}</p>
      </div>
      <div class="shrink-0 border-t bg-muted/30 px-4 py-3 text-xs text-muted-foreground">{{ t('common.unifiedSearch.keys') }}</div>
    </DialogContent>
  </Dialog>
</template>
