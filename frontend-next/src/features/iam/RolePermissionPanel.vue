<script setup lang="ts">
import PermissionButton from '@/components/business/PermissionButton.vue'
import { computed, onScopeDispose, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Check, ShieldCheck, RefreshCw } from '@lucide/vue'
import { get, put } from '@/api/http'
import { useAuth } from '@/stores/auth'
import { confirmAction, notify } from '@/lib/feedback'
import { useUnsavedChanges } from '@/composables/useUnsavedChanges'
import { useSubmission } from '@/composables/useSubmission'
import OperationStatus from '@/components/business/OperationStatus.vue'
import { Button } from '@/components/ui/button'
import PermissionTree from '@/components/business/PermissionTree.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import type { TreeNode } from '@/components/business/tree'
import type { Row } from './resources'
const props = defineProps<{ role: Row }>(), { t } = useI18n(), auth = useAuth()
const action = useSubmission(), nodes = ref<TreeNode[]>([]), selected = ref<string[]>([]), original = ref('[]'), busy = ref(false), saving = action.busy, loaded = ref(false), error = action.error
const signature = (ids: string[]) => JSON.stringify([...new Set(ids)].sort())
const dirty = computed(() => loaded.value && signature(selected.value) !== original.value)
useUnsavedChanges(dirty, saving)
let generation = 0
onScopeDispose(() => generation++)
async function load() {
  action.resetView()
  action.bind('iam.roles.' + props.role.id)
  const current = ++generation, id = props.role.id
  busy.value = true; loaded.value = false; error.value = ''; nodes.value = []; selected.value = []; original.value = '[]'
  try { const [tree, ids] = await Promise.all([get<TreeNode[]>('/iam/permissions/tree'), get<string[]>('/iam/roles/' + id + '/permissions')]); if (current !== generation) return; nodes.value = tree; selected.value = ids; original.value = signature(ids); loaded.value = true }
  catch (cause) { if (current === generation) error.value = (cause as Error).message }
  finally { if (current === generation) busy.value = false }
}
async function canLeave() { if (saving.value) { notify(t('common.operationInProgress'), 'info'); return false }; return !dirty.value || await confirmAction(t('common.unsavedConfirm'), t('common.confirm')) }
async function reload() { if (!await canLeave()) return false; await load(); return true }
async function save() {
  if (!loaded.value || busy.value || saving.value || !auth.hasPermission('iam:role:grant')) return
  const id = props.role.id, current = generation, ids = [...new Set(selected.value)]
  const result = await action.run({ operation: 'roles.grant', resource: 'iam.roles.' + id, protocol: 'LOCAL' }, () => options => put('/iam/roles/' + id + '/permissions', { permissionIds: ids }, options))
  if (current !== generation) return
  if (result.kind === 'success' || result.kind === 'completed') { try { await load(); notify(t('common.saved')); await auth.load(false) } catch (cause) { error.value = (cause as Error).message } }
}
async function check() { if (await action.check() === 'SUCCEEDED') { await load(); try { await auth.load(false) } catch (cause) { error.value = (cause as Error).message } } }
watch(() => props.role.id, load, { immediate: true })
defineExpose({ canLeave, reload })
</script>
<template>
  <section class="softlight-surface overflow-hidden" data-role-permissions :aria-label="t('iam.grant')" :aria-busy="busy">
    <div class="role-permission-heading flex items-start justify-between gap-5 border-b p-5 sm:p-7"><div class="min-w-0"><p class="softlight-eyebrow">{{ t('iam.grant') }}</p><h2 class="mt-3 text-2xl font-semibold break-words tracking-tight">{{ role.name }}</h2><p class="mt-2 text-sm text-muted-foreground">{{ t('iam.permissionsHint') }}</p></div><span class="permission-emblem"><ShieldCheck class="size-7" /></span></div>
    <div class="p-4 sm:p-6"><ErrorNotice :message="error" /><OperationStatus :slow="action.slow.value" :unresolved="action.unresolved.value" :querying="action.querying.value" queryable @check="check" /><fieldset :disabled="saving || action.unresolved.value"><PermissionTree v-model="selected" :nodes="nodes" :loading="busy" expand-roots /></fieldset><p v-if="!loaded && !busy" class="mt-3 text-sm text-muted-foreground">{{ t('common.loadingFailed') }}</p></div>
    <div class="flex flex-wrap items-center justify-between gap-3 border-t bg-muted/25 px-5 py-4 sm:px-7"><span class="text-xs text-muted-foreground" role="status">{{ t(dirty ? 'iam.permissionDraft' : 'iam.permissionSynced') }}</span><div class="flex gap-2"><Button variant="outline" :disabled="busy || saving" @click="reload"><RefreshCw class="size-4" />{{ t('common.refresh') }}</Button><PermissionButton :permission="'iam:role:grant'" :disabled="busy || saving || !loaded" @click="save"><Check class="size-4" />{{ t(saving ? 'common.loading' : 'common.save') }}</PermissionButton></div></div>
  </section>
</template>
