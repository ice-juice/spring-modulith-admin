<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
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
import DomainDetails from './DomainDetails.vue'
import { useSubmission } from '@/composables/useSubmission'
import OperationStatus from '@/components/business/OperationStatus.vue'
import { canonicalHostname, directory, type DomainRecord, type TenantOption, type VerificationSettings } from './directory'
import { useDirectorySession } from './useDirectorySession'

const { t } = useI18n(), auth = useAuth(), submission = useSubmission()
const options = ref<TenantOption[]>([]), owner = ref<TenantOption>(), optionPage = ref(1), optionTotal = ref('0'), keyword = ref(''), applied = ref('')
const rows = ref<DomainRecord[]>([]), selected = ref<DomainRecord>(), settings = ref<VerificationSettings>({ configured: false, ingressAddresses: [] })
const busy = ref(false), error = ref(''), message = ref(''), open = ref(false), hostname = ref(''), portal = ref('admin')
const pending = ref<{ tenantId: string; hostname: string; portal: string }>()
let readAttempt = 0
function clear() { readAttempt++; options.value = []; owner.value = undefined; optionPage.value = 1; optionTotal.value = '0'; keyword.value = ''; applied.value = ''; settings.value = { configured: false, ingressAddresses: [] }; rows.value = []; selected.value = undefined; open.value = false; hostname.value = ''; portal.value = 'admin'; pending.value = undefined; busy.value = false; error.value = ''; message.value = '' }
const session = useDirectorySession('tenant:domain:view', clear, () => { readAttempt++; busy.value = false })
const disabled = computed(() => busy.value || submission.busy.value || submission.querying.value || !session.accessible.value)
const available = computed(() => owner.value && !options.value.some(item => item.id === owner.value!.id) ? [owner.value, ...options.value] : options.value)
const columns = computed(() => ['hostname', 'portal', 'status', 'primary', 'ownership', 'tls', 'actions'].map(key => ({ key, title: t('tenant.directory.' + key), width: key === 'hostname' ? 240 : 130 })))
const hasNext = computed(() => BigInt(optionPage.value * 20) < BigInt(optionTotal.value))
async function loadOptions(target = 1) {
  if (disabled.value || open.value || selected.value) return
  const ticket = session.ticket(), attempt = ++readAttempt; busy.value = true; error.value = ''
  try {
    const [result, configuration] = await Promise.all([directory.options(target, applied.value), directory.settings()])
    if (!session.valid(ticket) || attempt !== readAttempt) return
    options.value = result.items; optionPage.value = target; optionTotal.value = result.total; settings.value = configuration
    if (owner.value) owner.value = result.items.find(item => item.id === owner.value!.id) ?? owner.value
    else owner.value = result.items[0]
  } catch (cause) { if (session.valid(ticket) && attempt === readAttempt) { error.value = (cause as Error).message; options.value = []; rows.value = [] } }
  finally { if (session.valid(ticket) && attempt === readAttempt) busy.value = false }
  if (session.valid(ticket) && attempt === readAttempt && owner.value) await loadRows()
}
async function loadRows() {
  if (disabled.value || !owner.value || open.value) return
  const ticket = session.ticket(), attempt = ++readAttempt, tenantId = owner.value.id; busy.value = true; error.value = ''
  try { const result = await directory.domains(tenantId); if (session.valid(ticket) && attempt === readAttempt && owner.value?.id === tenantId) rows.value = result }
  catch (cause) { if (session.valid(ticket) && attempt === readAttempt) { rows.value = []; error.value = (cause as Error).message } }
  finally { if (session.valid(ticket) && attempt === readAttempt) busy.value = false }
}
function choose(value: string) {
  if (disabled.value || open.value || selected.value) return
  owner.value = available.value.find(item => item.id === value); rows.value = []; selected.value = undefined; message.value = ''; void loadRows()
}
function search() { if (!disabled.value && !open.value && !selected.value) { applied.value = keyword.value; void loadOptions(1) } }
function create() { if (!disabled.value && owner.value && owner.value.status !== 'RETIRED' && auth.hasPermission('tenant:domain:create')) { submission.resetView(); submission.bind('tenant.domains.create.' + owner.value.id); hostname.value = ''; portal.value = 'admin'; error.value = ''; message.value = ''; open.value = true } }
async function save() {
  if (disabled.value || submission.unresolved.value || !owner.value || !open.value || !auth.hasPermission('tenant:domain:create')) return
  const input = { tenantId: owner.value.id, hostname: canonicalHostname(hostname.value), portal: portal.value.trim() }, ticket = session.ticket()
  const result = await submission.run({ operation: 'domains.create', resource: 'tenant.domains.create.' + input.tenantId, protocol: 'LOCAL' }, () => {
    if (!input.hostname || input.hostname.length > 253 || !/^[a-z][a-z0-9-]{0,31}$/.test(input.portal)) { error.value = t('tenant.directory.invalidDomain'); return }
    pending.value = input; error.value = ''
    return options => directory.createDomain(input, options)
  })
  if (!session.valid(ticket)) return
  if (result.kind === 'success' || result.kind === 'completed') {
    pending.value = undefined; open.value = false; message.value = t('tenant.directory.domainRegistered'); await loadRows()
    if (session.valid(ticket)) selected.value = rows.value.find(row => row.hostname === input.hostname && row.portal === input.portal)
  } else { if (submission.error.value) error.value = submission.error.value; if (!submission.unresolved.value) pending.value = undefined }
}
async function reconcile() {
  if (!submission.unresolved.value || disabled.value) return
  const ticket = session.ticket(), input = pending.value, state = await submission.check()
  if (!session.valid(ticket)) return
  if (state === 'SUCCEEDED') {
    pending.value = undefined; open.value = false; message.value = t('tenant.directory.stateMatched'); await loadRows()
    if (session.valid(ticket) && input) selected.value = rows.value.find(row => row.hostname === input.hostname && row.portal === input.portal)
  } else { if (state === 'FAILED_FINAL') pending.value = undefined; error.value = submission.error.value }
}
async function abandon() { const ticket = session.ticket(); if (!disabled.value && await confirmAction(t('tenant.directory.leaveUnknown'), t('common.confirm')) && session.valid(ticket)) { pending.value = undefined; open.value = false; submission.resetView(); await loadRows() } }
function update(row: DomainRecord) { rows.value = rows.value.map(item => item.id === row.id ? row : item) }
async function closeDetail() {
  const id = selected.value?.id, ticket = session.ticket(); selected.value = undefined
  await loadRows(); await nextTick()
  if (id && session.valid(ticket) && !selected.value) document.querySelector<HTMLButtonElement>('[data-domain-action="' + CSS.escape(id) + '"]')?.focus()
}
onMounted(() => loadOptions())
</script>
<template>
  <TableWorkspace>
    <template #tools>
      <PageHeader :title="t('tenant.directory.domains')" :description="t('tenant.directory.domainDescription')"><Button variant="outline" :disabled="disabled || open || !!selected" @click="loadOptions(optionPage)">{{ t('common.refresh') }}</Button><PermissionButton permission="tenant:domain:create" :disabled="disabled || !owner || owner.status === 'RETIRED'" @click="create">{{ t('tenant.directory.registerDomain') }}</PermissionButton></PageHeader>
      <div class="space-y-3"><form class="flex flex-wrap items-center gap-2" @submit.prevent="search"><Input v-model="keyword" class="max-w-xs" maxlength="128" :aria-label="t('tenant.directory.searchTenant')" :placeholder="t('tenant.directory.searchTenant')" :disabled="disabled || open || !!selected" /><Button variant="outline" :disabled="disabled || open || !!selected">{{ t('common.search') }}</Button><Button variant="outline" :disabled="disabled || open || !!selected || optionPage <= 1" type="button" :aria-label="t('common.previousPage')" @click="loadOptions(optionPage - 1)">{{ t('common.previousPage') }}</Button><span class="text-xs">{{ t('tenant.directory.optionPage', { page: optionPage, total: optionTotal }) }}</span><Button variant="outline" :disabled="disabled || open || !!selected || !hasNext" type="button" :aria-label="t('common.nextPage')" @click="loadOptions(optionPage + 1)">{{ t('common.nextPage') }}</Button></form>
        <div class="flex flex-wrap items-center gap-2"><Label for="domain-owner">{{ t('tenant.directory.owner') }}</Label><select id="domain-owner" class="max-w-full rounded-md border bg-background p-2 text-sm" :value="owner?.id ?? ''" :disabled="disabled || open || !!selected" @change="choose(($event.target as HTMLSelectElement).value)"><option v-if="!owner" value="">{{ t('tenant.directory.chooseTenant') }}</option><option v-for="item in available" :key="item.id" :value="item.id">{{ item.name }} {{ t('tenant.directory.separator') }} {{ item.code }} {{ t('tenant.directory.separator') }} {{ t('tenant.directory.states.' + item.status) }}</option></select></div>
        <p class="text-sm text-muted-foreground">{{ t('tenant.directory.registrationHint') }}</p>
      </div><ErrorNotice v-if="!open && !selected" :message="error" /><p v-if="message && !open && !selected" role="status" class="mt-2 text-sm">{{ message }}</p>
    </template>
    <DataTable :rows="auth.user?.locked ? [] : rows" :columns="columns" :loading="busy">
      <template #status="{ row }">{{ t('tenant.directory.domainStates.' + row.status) }}</template>
      <template #primary="{ row }">{{ row.primaryDomain ? t('tenant.directory.primary') : '—' }}</template>
      <template #ownership="{ row }">{{ t('tenant.directory.states.' + row.verificationStatus) }}</template>
      <template #tls="{ row }">{{ t('tenant.directory.states.' + row.tlsStatus) }}</template>
      <template #actions="{ row }"><Button variant="outline" size="sm" :data-domain-action="row.id" :disabled="disabled" @click="selected = { ...row }">{{ t('tenant.directory.details') }}</Button></template>
    </DataTable>
  </TableWorkspace>
  <EditorDialog v-if="!auth.user?.locked" v-model="open" :title="t('tenant.directory.registerDomain')" :description="t('tenant.directory.registrationHint')" :busy="busy || submission.busy.value" :submit-disabled="submission.unresolved.value" :dirty="!!hostname || portal !== 'admin' || !!pending" @save="save">
    <ErrorNotice :message="error" /><p v-if="message" role="status" class="mb-3 text-sm">{{ message }}</p><p class="mb-4 text-sm">{{ owner?.name }} {{ t('tenant.directory.separator') }} {{ owner?.code }}</p>
    <form @submit.prevent="save"><fieldset :disabled="disabled || submission.unresolved.value" class="space-y-4"><div class="space-y-2"><Label for="domain-hostname">{{ t('tenant.directory.hostname') }}</Label><Input id="domain-hostname" v-model="hostname" maxlength="253" autocomplete="off" :placeholder="t('tenant.directory.hostnameExample')" /></div><div class="space-y-2"><Label for="domain-portal">{{ t('tenant.directory.portal') }}</Label><Input id="domain-portal" v-model="portal" maxlength="32" autocomplete="off" /></div></fieldset></form>
    <OperationStatus :slow="submission.slow.value" /><div v-if="submission.unresolved.value" class="mt-4 space-y-3 rounded-lg border p-3" role="status"><p>{{ t('tenant.directory.unknown') }}</p><Button variant="outline" :disabled="disabled" @click="reconcile">{{ t('tenant.checkResult') }}</Button><Button variant="ghost" :disabled="disabled" @click="abandon">{{ t('tenant.directory.leavePending') }}</Button></div>
  </EditorDialog>
  <DomainDetails v-if="selected && owner" :key="selected.id" :initial="selected" :tenant="owner" :settings="settings" @changed="update" @close="closeDetail" />
</template>
