<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuth } from '@/stores/auth'
import { confirmAction } from '@/lib/feedback'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import PageHeader from '@/components/business/PageHeader.vue'
import TableWorkspace from '@/components/business/TableWorkspace.vue'
import DataTable from '@/components/business/DataTable.vue'
import PermissionButton from '@/components/business/PermissionButton.vue'
import EditorDialog from '@/components/business/EditorDialog.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import { useSubmission } from '@/composables/useSubmission'
import OperationStatus from '@/components/business/OperationStatus.vue'
import { directory, tenantTransitions, type TenantRecord, type TenantStatus } from './directory'
import { useDirectorySession } from './useDirectorySession'

const { t } = useI18n(), auth = useAuth(), submission = useSubmission()
const rows = ref<TenantRecord[]>([]), page = ref(1), total = ref('0'), keyword = ref(''), applied = ref('')
const busy = ref(false), error = ref(''), message = ref(''), open = ref(false), selected = ref<TenantRecord>()
const code = ref(''), name = ref(''), status = ref<TenantStatus>('ACTIVE')
type Pending = { kind: 'create'; code: string; name: string } | { kind: 'status'; row: TenantRecord; status: TenantStatus }
const pending = ref<Pending>()
let readAttempt = 0
function clear() { readAttempt++; rows.value = []; total.value = '0'; page.value = 1; keyword.value = ''; applied.value = ''; open.value = false; selected.value = undefined; code.value = ''; name.value = ''; status.value = 'ACTIVE'; pending.value = undefined; busy.value = false; error.value = ''; message.value = '' }
const session = useDirectorySession('tenant:tenant:view', clear, () => { readAttempt++; busy.value = false })
const columns = computed(() => ['code', 'name', 'status', 'securityGeneration', 'actions'].map(key => ({ key, title: t('tenant.directory.' + key), width: key === 'name' ? 220 : 150 })))
const dirty = computed(() => !!pending.value || (selected.value ? status.value !== selected.value.status : !!code.value || !!name.value))
const disabled = computed(() => busy.value || submission.busy.value || submission.querying.value || !session.accessible.value)
async function load(target = page.value) {
  if (disabled.value || open.value) return
  const ticket = session.ticket(), attempt = ++readAttempt; busy.value = true; error.value = ''
  try { const result = await directory.tenants(target, applied.value); if (session.valid(ticket) && attempt === readAttempt) { rows.value = result.items; total.value = result.total; page.value = target } }
  catch (cause) { if (session.valid(ticket) && attempt === readAttempt) { rows.value = []; error.value = (cause as Error).message } }
  finally { if (session.valid(ticket) && attempt === readAttempt) busy.value = false }
}
function search() { if (!disabled.value && !open.value) { applied.value = keyword.value; void load(1) } }
function edit(row?: TenantRecord) {
  if (disabled.value || !auth.hasPermission(row ? 'tenant:tenant:update' : 'tenant:tenant:create')) return
  submission.resetView(); submission.bind('tenant.tenants.' + (row?.id || 'new'))
  selected.value = row ? { ...row } : undefined; status.value = row ? tenantTransitions(row.status)[0] ?? row.status : 'ACTIVE'
  code.value = ''; name.value = ''; pending.value = undefined; error.value = ''; message.value = ''; open.value = true
}
async function save() {
  if (disabled.value || submission.unresolved.value || !open.value || !auth.hasPermission(selected.value ? 'tenant:tenant:update' : 'tenant:tenant:create')) return
  const ticket = session.ticket()
  const request: Pending = selected.value ? { kind: 'status', row: { ...selected.value }, status: status.value } : { kind: 'create', code: code.value.trim(), name: name.value.trim() }
  const result = await submission.run({ operation: 'tenants.' + request.kind, resource: 'tenant.tenants.' + (request.kind === 'status' ? request.row.id : 'new'), protocol: 'LOCAL' }, async () => {
    if (request.kind === 'create' && (!/^[a-z][a-z0-9-]{0,63}$/.test(request.code) || !request.name || request.name.length > 128)) { error.value = t('tenant.directory.invalidTenant'); return }
    if (request.kind === 'status' && !await confirmAction(t('tenant.directory.statusConfirm', { name: request.row.name, status: t('tenant.directory.states.' + request.status) }), t('common.confirm'))) return
    if (!session.valid(ticket)) return
    pending.value = request; error.value = ''
    return options => request.kind === 'create' ? directory.createTenant(request, options) : directory.status(request.row, request.status, options)
  })
  if (!session.valid(ticket)) return
  if (result.kind === 'success' || result.kind === 'completed') { pending.value = undefined; open.value = false; message.value = t(request.kind === 'create' ? 'tenant.directory.registered' : 'tenant.directory.updated'); await load() }
  else { if (submission.error.value) error.value = submission.error.value; if (!submission.unresolved.value) pending.value = undefined }
}
async function reconcile() {
  if (!submission.unresolved.value || disabled.value) return
  const ticket = session.ticket(), state = await submission.check()
  if (!session.valid(ticket)) return
  if (state === 'SUCCEEDED') { pending.value = undefined; open.value = false; message.value = t('tenant.directory.stateMatched'); await load() }
  else { if (state === 'FAILED_FINAL') pending.value = undefined; error.value = submission.error.value }
}
async function abandon() {
  const ticket = session.ticket()
  if (!disabled.value && await confirmAction(t('tenant.directory.leaveUnknown'), t('common.confirm')) && session.valid(ticket)) { pending.value = undefined; open.value = false; submission.resetView(); await load() }
}
onMounted(() => load(1))
</script>
<template>
  <TableWorkspace>
    <template #tools>
      <PageHeader :title="t('tenant.directory.tenants')" :description="t('tenant.directory.tenantDescription')"><Button variant="outline" :disabled="disabled || open" @click="load()">{{ t('common.refresh') }}</Button><PermissionButton permission="tenant:tenant:create" :disabled="disabled" @click="edit()">{{ t('tenant.directory.registerTenant') }}</PermissionButton></PageHeader>
      <p class="mb-3 text-sm text-muted-foreground">{{ t('tenant.directory.provisioningHint') }}</p>
      <form class="flex flex-wrap gap-2" @submit.prevent="search"><Input v-model="keyword" :aria-label="t('tenant.directory.searchTenant')" :placeholder="t('tenant.directory.searchTenant')" maxlength="128" class="max-w-sm" :disabled="disabled || open" /><Button variant="outline" :disabled="disabled || open">{{ t('common.search') }}</Button></form>
      <ErrorNotice v-if="!open" :message="error" /><p v-if="message && !open" role="status" class="mt-2 text-sm">{{ message }}</p>
    </template>
    <DataTable :rows="auth.user?.locked ? [] : rows" :columns="columns" :loading="busy" :total="Number(total)" :page="page" @update:page="load">
      <template #status="{ row }">{{ t('tenant.directory.states.' + row.status) }}</template>
      <template #actions="{ row }"><PermissionButton permission="tenant:tenant:update" variant="outline" size="sm" :disabled="disabled || !tenantTransitions(row.status).length" @click="edit(row)">{{ t('tenant.directory.changeStatus') }}</PermissionButton></template>
    </DataTable>
  </TableWorkspace>
  <EditorDialog v-if="!auth.user?.locked" v-model="open" :title="t(selected ? 'tenant.directory.changeStatus' : 'tenant.directory.registerTenant')" :description="t('tenant.directory.provisioningHint')" :busy="busy || submission.busy.value" :submit-disabled="submission.unresolved.value" :dirty="dirty" @save="save">
    <ErrorNotice :message="error" /><p v-if="message" role="status" class="mb-3 text-sm">{{ message }}</p>
    <form class="space-y-4" @submit.prevent="save"><fieldset :disabled="disabled || submission.unresolved.value" class="space-y-4">
      <template v-if="!selected"><div class="space-y-2"><Label for="tenant-code">{{ t('tenant.directory.code') }}</Label><Input id="tenant-code" v-model="code" maxlength="64" autocomplete="off" /></div><div class="space-y-2"><Label for="tenant-name">{{ t('tenant.directory.name') }}</Label><Input id="tenant-name" v-model="name" maxlength="128" /></div></template>
      <template v-else><p>{{ selected.name }} {{ t('tenant.directory.separator') }} {{ selected.code }}</p><div class="space-y-2"><Label for="tenant-status">{{ t('tenant.directory.status') }}</Label><select id="tenant-status" v-model="status" class="w-full rounded-md border bg-background p-2"><option v-for="item in tenantTransitions(selected.status)" :key="item" :value="item">{{ t('tenant.directory.states.' + item) }}</option></select></div></template>
    </fieldset></form>
    <OperationStatus :slow="submission.slow.value" /><div v-if="submission.unresolved.value" class="mt-4 space-y-3 rounded-lg border p-3" role="status"><p>{{ t('tenant.directory.unknown') }}</p><Button variant="outline" :disabled="disabled" @click="reconcile">{{ t('tenant.checkResult') }}</Button><Button variant="ghost" :disabled="disabled" @click="abandon">{{ t('tenant.directory.leavePending') }}</Button></div>
  </EditorDialog>
</template>
