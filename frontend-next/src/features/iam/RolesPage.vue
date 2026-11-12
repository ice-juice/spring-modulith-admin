<script setup lang="ts">
import PermissionButton from '@/components/business/PermissionButton.vue'

import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Plus, RefreshCw, ShieldCheck, ChevronRight, Pencil, Users, Trash2 } from '@lucide/vue'
import { get, remove } from '@/api/http'
import { useAuth } from '@/stores/auth'
import { useLatestRequest } from '@/composables/useLatestRequest'
import { useSubmission } from '@/composables/useSubmission'
import OperationStatus from '@/components/business/OperationStatus.vue'
import { confirmAction, notify } from '@/lib/feedback'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import PageHeader from '@/components/business/PageHeader.vue'
import EmptyState from '@/components/business/EmptyState.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import StatusBadge from '@/components/business/StatusBadge.vue'
import ResourceEditor from './ResourceEditor.vue'
import RolePermissionPanel from './RolePermissionPanel.vue'
import ResizablePanels from '@/components/business/ResizablePanels.vue'
import { resources, tableTree, type Row } from './resources'
const { t, locale } = useI18n(), router = useRouter(), auth = useAuth(), request = useLatestRequest()
const deletion = useSubmission(), rows = ref<Row[]>([]), selectedId = ref(''), query = ref(''), editorOpen = ref(false), editing = ref<Row>(), panel = ref<InstanceType<typeof RolePermissionPanel>>(), actionError = deletion.error, deleting = deletion.busy
const role = computed(() => rows.value.find(row => row.id === selectedId.value))
const shown = computed(() => {
  function flatten(items: Row[], depth = 0): Row[] { return items.flatMap(item => [{ ...item, __depth: depth }, ...flatten((item.children || []) as Row[], depth + 1)]) }
  return flatten(tableTree(rows.value)).filter(row => !query.value.trim() || `${row.name} ${row.code}`.toLocaleLowerCase().includes(query.value.trim().toLocaleLowerCase()))
})
async function load() { await request.run(signal => get<Row[]>('/iam/roles', undefined, { signal }), result => { rows.value = result; if (!result.some(row => row.id === selectedId.value)) selectedId.value = result[0]?.id || '' }, JSON.stringify(['/iam/roles', locale.value])) }
async function select(row: Row) { if (row.id === selectedId.value || deleting.value) return; if (panel.value && !await panel.value.canLeave()) return; selectedId.value = row.id }
async function refresh() { request.cancel(); if (panel.value && !await panel.value.reload()) return; await load() }
function edit(row?: Row) { editing.value = row; editorOpen.value = true }
async function erase() {
  if (!role.value || deleting.value) return
  const id = role.value.id
  const result = await deletion.run({ operation: 'roles.delete', resource: 'iam.roles.' + id, protocol: 'LOCAL' }, async () => {
    if (panel.value && !await panel.value.canLeave()) return
    if (!await confirmAction(t('common.deleteConfirm'), t('common.confirm'), true)) return
    return options => remove('/iam/roles/' + id, options)
  })
  if (result.kind === 'success' || result.kind === 'completed') { notify(t('common.deleted')); request.cancel(); await load() }
}
async function checkDeletion() { if (await deletion.check() === 'SUCCEEDED') { request.cancel(); await load() } }
watch(locale, load, { immediate: true })
</script>
<template>
  <PageHeader :title="t('iam.role')" :eyebrow="t('iam.roleEyebrow')" :description="t('iam.roleHint')"><Button variant="outline" :disabled="request.loading.value || deleting" @click="refresh"><RefreshCw class="size-4" />{{ t('common.refresh') }}</Button><PermissionButton :permission="'iam:role:create'" @click="edit()"><Plus class="size-4" />{{ t('common.create') }}</PermissionButton></PageHeader>
  <ErrorNotice :message="request.error.value || actionError" />
  <OperationStatus :slow="deletion.slow.value" :unresolved="deletion.unresolved.value" :querying="deletion.querying.value" queryable @check="checkDeletion" />
  <ResizablePanels :label="t('iam.roleList')">
    <template #panel>
    <section class="softlight-surface min-w-0 self-start p-4" :aria-busy="request.loading.value"><div class="mb-4 flex items-center justify-between px-1"><h2 class="text-sm font-semibold">{{ t('iam.roleList') }}</h2><span class="rounded-md bg-muted px-2 py-0.5 text-xs tabular-nums text-muted-foreground">{{ rows.length }}</span></div><Input v-model="query" class="mb-4" :placeholder="t('iam.roleSearch')" :aria-label="t('iam.roleSearch')" /><div class="max-h-[60dvh] space-y-2 overflow-y-auto"><p v-if="request.loading.value && !rows.length" class="p-6 text-sm text-muted-foreground">{{ t('common.loading') }}</p><EmptyState v-else-if="!shown.length" :title="t(query ? 'common.noMatch' : 'common.empty')" /><button v-for="row in shown" :key="row.id" data-role-row class="flex w-full items-center gap-3 rounded-xl p-3 text-left transition-colors" :class="selectedId === row.id ? 'bg-accent text-accent-foreground' : 'hover:bg-muted'" :aria-pressed="selectedId === row.id" :aria-label="String(row.name)" :style="{ paddingLeft: Math.min(Number(row.__depth || 0), 5) * 12 + 12 + 'px' }" @click="select(row)"><span class="grid size-9 shrink-0 place-items-center rounded-lg border bg-card"><ShieldCheck class="size-4" /></span><span class="min-w-0 flex-1"><span class="block break-words text-sm font-medium">{{ row.name }}</span><span class="mt-1 block truncate text-xs text-muted-foreground">{{ row.code }}</span></span><ChevronRight class="size-3.5 shrink-0" /></button></div></section>
    </template>
    <div v-if="role" class="min-w-0 space-y-4"><div class="flex flex-wrap items-center justify-between gap-3"><div class="flex flex-wrap items-center gap-2"><StatusBadge :label="t(role.enabled ? 'common.enabled' : 'common.disabled')" :tone="role.enabled ? 'success' : 'neutral'" /><span class="text-xs text-muted-foreground">{{ t('iam.fields.dataScope') }} {{ t('common.detailSeparator') }} {{ t('iam.choices.' + role.dataScope) }}</span></div><div class="flex flex-wrap gap-1"><PermissionButton :permission="'iam:role:update'" variant="outline" size="sm" @click="edit(role)"><Pencil class="size-3.5" />{{ t('common.edit') }}</PermissionButton><PermissionButton :permission="'iam:user:view'" variant="ghost" size="sm" @click="router.push({ path: '/iam/users', query: { roleId: role.id, roleName: String(role.name) } })"><Users class="size-3.5" />{{ t('iam.viewMembers') }}</PermissionButton><PermissionButton v-if="!role.builtin" :permission="'iam:role:delete'" variant="ghost" size="sm" class="text-destructive" :disabled="deleting" @click="erase"><Trash2 class="size-3.5" />{{ t('common.remove') }}</PermissionButton></div></div><RolePermissionPanel v-if="auth.hasPermission('iam:role:grant')" ref="panel" :role="role" /><section v-else class="softlight-surface p-7"><ShieldCheck class="mb-4 size-8 text-primary" /><h2 class="text-xl font-semibold">{{ role.name }}</h2><p class="mt-3 text-sm text-muted-foreground">{{ t('iam.roleReadOnly') }}</p></section></div>
    <EmptyState v-else-if="!request.loading.value" class="softlight-surface" />
  </ResizablePanels>
  <ResourceEditor v-model="editorOpen" :resource="resources.roles!" :row="editing" @saved="refresh" />
</template>
