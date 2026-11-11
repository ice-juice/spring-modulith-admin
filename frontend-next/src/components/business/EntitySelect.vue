<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Check, ChevronDown, X } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { useQueryDebounce } from '@/composables/useQueryDebounce'
import EmptyState from './EmptyState.vue'
export interface EntityOption { value: string; label: string; disabled?: boolean; children?: EntityOption[] }
const props = defineProps<{ options: EntityOption[]; multiple?: boolean; disabled?: boolean; placeholder?: string; id?: string; remote?: boolean; descriptionId?: string; invalid?: boolean; required?: boolean }>()
const emit = defineEmits<{ search: [query: string] }>()
const query = ref(''), open = ref(false), { t } = useI18n()
const search = useQueryDebounce(() => { if (!props.disabled) emit('search', query.value) })
watch(query, search.schedule)
watch(() => props.disabled, disabled => { if (disabled) { open.value = false; search.cancel() } }, { flush: 'sync' })
const value = defineModel<string | string[] | null>({ default: null })
function flatten(nodes: EntityOption[], level = 0): (EntityOption & { level: number })[] { return nodes.flatMap(node => [{ ...node, level }, ...flatten(node.children || [], level + 1)]) }
const all = computed(() => flatten(props.options)), selected = computed(() => Array.isArray(value.value) ? value.value : value.value ? [value.value] : [])
const shown = computed(() => props.remote ? all.value : all.value.filter(option => option.label.toLocaleLowerCase().includes(query.value.trim().toLocaleLowerCase())))
const label = computed(() => selected.value.map(id => all.value.find(option => option.value === id)?.label || id).join(', '))
function choose(option: EntityOption) { if (props.disabled || option.disabled) return; if (props.multiple) value.value = selected.value.includes(option.value) ? selected.value.filter(id => id !== option.value) : [...selected.value, option.value]; else { value.value = option.value; open.value = false } }
</script>
<template><div class="flex min-w-0 gap-1"><Popover v-model:open="open"><PopoverTrigger as-child><Button :id="id" type="button" variant="outline" class="min-w-0 flex-1 justify-between font-normal" :disabled="disabled" :aria-label="placeholder" :aria-describedby="descriptionId" :aria-invalid="invalid" :aria-required="required"><span class="truncate" :class="{ 'text-muted-foreground': !label }">{{ label || placeholder || t('common.all') }}</span><ChevronDown class="size-3.5 shrink-0 text-muted-foreground" /></Button></PopoverTrigger><PopoverContent update-position-strategy="always" class="w-[var(--reka-popover-trigger-width)] min-w-56 p-1" align="start"><div class="p-1"><Input v-model="query" :disabled="disabled" @compositionstart="search.compositionStart" @compositionend="search.compositionEnd" @keydown="search.keydown" :placeholder="t('common.search')" :aria-label="t('common.search')" /></div><div class="max-h-64 overflow-y-auto py-1"><button v-for="option in shown" :key="option.value" type="button" class="flex min-h-9 w-full items-center gap-2 rounded-sm py-2 pr-2 text-left text-sm hover:bg-accent disabled:opacity-40" :style="{ paddingLeft: 8 + option.level * 14 + 'px' }" :disabled="disabled || option.disabled" :aria-pressed="selected.includes(option.value)" @click="choose(option)"><span v-if="multiple" class="flex size-4 shrink-0 items-center justify-center rounded border" :class="selected.includes(option.value) ? 'border-primary bg-primary text-primary-foreground' : 'border-input'" aria-hidden="true"><Check v-if="selected.includes(option.value)" class="size-3" /></span><span class="min-w-0 flex-1 break-words">{{ option.label }}</span><Check v-if="!multiple && selected.includes(option.value)" class="size-4 text-primary" /></button><EmptyState v-if="!shown.length" :title="t('common.noMatch')" /></div></PopoverContent></Popover><Button v-if="selected.length && !disabled" type="button" variant="ghost" size="icon" :aria-label="t('common.reset')" @click="value = multiple ? [] : null"><X class="size-3.5" /></Button></div></template>
