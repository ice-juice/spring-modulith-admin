<script setup lang="ts">
import PermissionButton from '@/components/business/PermissionButton.vue'

import TableWorkspace from '@/components/business/TableWorkspace.vue'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { get, post } from '@/api/http'
import { notify } from '@/lib/feedback'
import { useSubmission } from '@/composables/useSubmission'
import OperationStatus from '@/components/business/OperationStatus.vue'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import PageHeader from '@/components/business/PageHeader.vue'
import EntitySelect from '@/components/business/EntitySelect.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import EmptyState from '@/components/business/EmptyState.vue'
import ResourcePage from '@/features/iam/ResourcePage.vue'
import EffectiveDictionariesPanel from './EffectiveDictionariesPanel.vue'
import type { Row } from '@/features/iam/resources'
const { t, locale } = useI18n(), route = useRoute(), router = useRouter(), types = ref<Row[]>([]), selected = ref<string | null>(null), tab = ref('effective'), action = useSubmission(), error = action.error, busy = action.busy
const current = computed(() => types.value.find(type => type.id === selected.value)), filters = computed(() => ({ typeId: selected.value })), options = computed(() => types.value.map(type => ({ value: type.id, label: String(type.name) })))
let generation = 0
action.bind('config.dictionaries.initialize')
async function load() { const active = ++generation; try { const result = await get<Row[]>('/config/dictionaries/types'); if (active !== generation) return; types.value = result; const requested = typeof route.query.typeId === 'string' ? route.query.typeId : selected.value; selected.value = result.some(type => type.id === requested) ? requested : result[0]?.id || null; if (typeof route.query.typeId === 'string' && selected.value) tab.value = 'items' } catch (cause) { if (active === generation) error.value = (cause as Error).message } }
async function initialize() { const result = await action.run({ operation: 'dictionaries.initialize', resource: 'config.dictionaries.initialize', protocol: 'LOCAL' }, () => options => post('/config/dictionaries/initialize', undefined, options)); if (result.kind === 'success' || result.kind === 'completed') { await load(); notify(t('common.saved')) } }
async function check() { if (await action.check() === 'SUCCEEDED') await load() }
function selectType(row: Row) { selected.value = row.id; tab.value = 'items'; void router.replace({ query: { ...route.query, typeId: row.id } }) }
watch([locale, () => route.query.typeId], load, { immediate: true })
</script>
<template><TableWorkspace><template #tools><PageHeader :title="t('config.dictionary')"><PermissionButton :permission="'config:dictionary:update'" variant="outline" :disabled="busy" @click="initialize">{{ t('config.initialize') }}</PermissionButton></PageHeader><ErrorNotice :message="error" /><OperationStatus :slow="action.slow.value" :unresolved="action.unresolved.value" :querying="action.querying.value" queryable @check="check" /><div v-if="tab !== 'effective'" class="mb-4 max-w-sm"><EntitySelect :model-value="selected" :options="options" :placeholder="t('config.selectType')" @update:model-value="selected = typeof $event === 'string' ? $event : null" /></div></template><Tabs v-model="tab"><TabsList class="mb-5"><TabsTrigger value="effective">{{ t('config.layers.sharedDictionaries') }}</TabsTrigger><TabsTrigger value="items">{{ t('config.layers.privateItems') }}</TabsTrigger><TabsTrigger value="types">{{ t('config.layers.privateTypes') }}</TabsTrigger></TabsList><TabsContent value="effective"><EffectiveDictionariesPanel /></TabsContent><TabsContent value="items"><ResourcePage v-if="selected" :key="selected" resource-id="dictionaryItems" :filters="filters" :preset="filters" :hierarchical="current?.tree === true" embedded /><EmptyState v-else :title="t('config.selectType')" /></TabsContent><TabsContent value="types"><ResourcePage resource-id="dictionaryTypes" embedded select-on-name @changed="load" @select="selectType" /></TabsContent></Tabs></TableWorkspace></template>
