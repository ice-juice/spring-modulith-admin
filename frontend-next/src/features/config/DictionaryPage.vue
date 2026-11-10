<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { get, post } from '@/api/http'
import { notify } from '@/lib/feedback'
import { Button } from '@/components/ui/button'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import PageHeader from '@/components/business/PageHeader.vue'
import EntitySelect from '@/components/business/EntitySelect.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import EmptyState from '@/components/business/EmptyState.vue'
import ResourcePage from '@/features/iam/ResourcePage.vue'
import type { Row } from '@/features/iam/resources'
const { t, locale } = useI18n(), route = useRoute(), router = useRouter(), types = ref<Row[]>([]), selected = ref<string | null>(null), tab = ref('items'), error = ref(''), busy = ref(false)
const current = computed(() => types.value.find(type => type.id === selected.value)), filters = computed(() => ({ typeId: selected.value })), options = computed(() => types.value.map(type => ({ value: type.id, label: String(type.name) })))
let generation = 0
async function load() { const active = ++generation; try { const result = await get<Row[]>('/config/dictionaries/types'); if (active !== generation) return; types.value = result; const requested = typeof route.query.typeId === 'string' ? route.query.typeId : selected.value; selected.value = result.some(type => type.id === requested) ? requested : result[0]?.id || null; if (!selected.value) tab.value = 'types' } catch (cause) { if (active === generation) error.value = (cause as Error).message } }
async function initialize() { if (busy.value) return; busy.value = true; error.value = ''; try { await post('/config/dictionaries/initialize'); await load(); notify(t('common.saved')) } catch (cause) { error.value = (cause as Error).message } finally { busy.value = false } }
function selectType(row: Row) { selected.value = row.id; tab.value = 'items'; void router.replace({ query: { ...route.query, typeId: row.id } }) }
watch([locale, () => route.query.typeId], load, { immediate: true })
</script>
<template><PageHeader :title="t('config.dictionary')"><Button v-permission="'config:dictionary:update'" variant="outline" :disabled="busy" @click="initialize">{{ t('config.initialize') }}</Button></PageHeader><ErrorNotice :message="error" /><div class="mb-4 max-w-sm"><EntitySelect :model-value="selected" :options="options" :placeholder="t('config.selectType')" @update:model-value="selected = typeof $event === 'string' ? $event : null" /></div><Tabs v-model="tab"><TabsList class="mb-5"><TabsTrigger value="items">{{ t('config.dictionaryItems') }}</TabsTrigger><TabsTrigger value="types">{{ t('config.dictionaryTypes') }}</TabsTrigger></TabsList><TabsContent value="items"><ResourcePage v-if="selected" :key="selected" resource-id="dictionaryItems" :filters="filters" :preset="filters" :hierarchical="current?.tree === true" embedded /><EmptyState v-else :title="t('config.selectType')" /></TabsContent><TabsContent value="types"><ResourcePage resource-id="dictionaryTypes" embedded select-on-name @changed="load" @select="selectType" /></TabsContent></Tabs></template>
