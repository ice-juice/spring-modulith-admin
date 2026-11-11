<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { get, put } from '@/api/http'
import { useAuth } from '@/stores/auth'
import { notify } from '@/lib/feedback'
import { useSubmission } from '@/composables/useSubmission'
import OperationStatus from '@/components/business/OperationStatus.vue'
import EditorDialog from '@/components/business/EditorDialog.vue'
import PermissionTree from '@/components/business/PermissionTree.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import type { TreeNode } from '@/components/business/tree'
import type { Row } from './resources'
const props = defineProps<{ role?: Row }>(), open = defineModel<boolean>({ required: true }), { t } = useI18n(), auth = useAuth()
const action = useSubmission(), nodes = ref<TreeNode[]>([]), selected = ref<string[]>([]), original = ref(''), busy = ref(false), saving = action.busy, error = action.error, loaded = ref(false)
let generation = 0
watch(open, async value => { if (!value || !props.role) { generation++; return }; action.resetView(); action.bind('iam.roles.' + props.role.id); const current = ++generation; busy.value = true; loaded.value = false; error.value = ''; nodes.value = []; selected.value = []; try { const result = await Promise.all([get<TreeNode[]>('/iam/permissions/tree'), get<string[]>('/iam/roles/' + props.role.id + '/permissions')]); if (generation === current) { loaded.value = true; nodes.value = result[0]; selected.value = result[1]; original.value = JSON.stringify(result[1]) } } catch (cause) { if (generation === current) error.value = (cause as Error).message } finally { if (generation === current) busy.value = false } })
async function save() {
  if (busy.value || saving.value || !props.role || !loaded.value) return
  const id = props.role.id, current = generation, ids = [...new Set(selected.value)]
  const result = await action.run({ operation: 'roles.grant', resource: 'iam.roles.' + id, protocol: 'LOCAL' }, () => options => put('/iam/roles/' + id + '/permissions', { permissionIds: ids }, options))
  if (current !== generation) return
  if (result.kind === 'success' || result.kind === 'completed') { original.value = JSON.stringify(ids); open.value = false; notify(t('common.saved')); try { await auth.load(false) } catch (cause) { error.value = (cause as Error).message } }
}
async function check() { if (await action.check() === 'SUCCEEDED') { open.value = false; try { await auth.load(false) } catch (cause) { error.value = (cause as Error).message } } }
</script>
<template><EditorDialog v-model="open" :title="t('iam.grant') + ' · ' + (role?.name || '')" :busy="busy || saving" :submit-disabled="action.unresolved.value" :dirty="JSON.stringify(selected) !== original" wide @save="save"><ErrorNotice :message="error" /><OperationStatus :slow="action.slow.value" :unresolved="action.unresolved.value" :querying="action.querying.value" queryable @check="check" /><fieldset :disabled="saving || action.unresolved.value"><PermissionTree v-model="selected" :nodes="nodes" :loading="busy" /></fieldset></EditorDialog></template>
