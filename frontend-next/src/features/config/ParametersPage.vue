<script setup lang="ts">
import PermissionButton from '@/components/business/PermissionButton.vue'

import TableWorkspace from '@/components/business/TableWorkspace.vue'
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { post } from '@/api/http'
import { notify, confirmAction } from '@/lib/feedback'
import { useSubmission } from '@/composables/useSubmission'
import OperationStatus from '@/components/business/OperationStatus.vue'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import PageHeader from '@/components/business/PageHeader.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import ResourcePage from '@/features/iam/ResourcePage.vue'
const { t } = useI18n(), tab = ref('parameters'), revision = ref(0), action = useSubmission(), busy = action.busy, error = action.error
const route = useRoute()
watch(() => route.query.focus, id => { if (typeof id === 'string') tab.value = 'parameters' }, { immediate: true })
async function refresh() { const result = await action.run({ operation: 'parameters.refresh', resource: 'config.parameters.maintenance', protocol: 'LOCAL' }, () => options => post('/config/parameters/refresh', undefined, options)); if (result.kind === 'success' || result.kind === 'completed') { revision.value++; notify(t('common.saved')) } }
async function reset() { const result = await action.run({ operation: 'parameters.reset', resource: 'config.parameters.maintenance', protocol: 'LOCAL' }, async () => { if (!await confirmAction(t('config.resetConfirm'), t('common.confirm'), true)) return; return options => post('/config/parameters/reset-builtins', undefined, options) }); if (result.kind === 'success' || result.kind === 'completed') { revision.value++; notify(t('common.saved')) } }
async function check() { if (await action.check() === 'SUCCEEDED') revision.value++ }
</script>
<template><TableWorkspace><template #tools><PageHeader :title="t('config.parameters')"><PermissionButton :permission="'config:parameter:update'" variant="outline" :disabled="busy" @click="refresh">{{ t('config.refreshCache') }}</PermissionButton><PermissionButton :permission="'config:parameter:update'" variant="outline" class="text-destructive" :disabled="busy" @click="reset">{{ t('config.resetDefaults') }}</PermissionButton></PageHeader><ErrorNotice :message="error" /><OperationStatus :slow="action.slow.value" :unresolved="action.unresolved.value" :querying="action.querying.value" queryable @check="check" /></template><Tabs v-model="tab"><TabsList class="mb-5"><TabsTrigger value="parameters">{{ t('config.parameters') }}</TabsTrigger><TabsTrigger value="groups">{{ t('config.parameterGroups') }}</TabsTrigger></TabsList><TabsContent value="parameters"><ResourcePage :key="revision" resource-id="parameters" embedded /></TabsContent><TabsContent value="groups"><ResourcePage resource-id="parameterGroups" embedded /></TabsContent></Tabs></TableWorkspace></template>
