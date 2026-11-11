<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuth } from '@/stores/auth'
import { usePreferences } from '@/stores/preferences'
import { confirmAction } from '@/lib/feedback'
import { formatInstant } from '@/lib/time'
import { Button } from '@/components/ui/button'
import EditorDialog from '@/components/business/EditorDialog.vue'
import PermissionButton from '@/components/business/PermissionButton.vue'
import { useSubmission } from '@/composables/useSubmission'
import OperationStatus from '@/components/business/OperationStatus.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import { unknownWrite } from './brands'
import { directory, mayCheck, mayEnable, type DomainRecord, type DomainCheck, type TenantOption, type VerificationSettings } from './directory'
import { useDirectorySession } from './useDirectorySession'

const props = defineProps<{ initial: DomainRecord; tenant: TenantOption; settings: VerificationSettings }>()
const emit = defineEmits<{ close: []; changed: [row: DomainRecord] }>()
const { t, locale } = useI18n(), auth = useAuth(), preferences = usePreferences()
const submission = useSubmission()
submission.bind('tenant.domains.' + props.initial.id)
const row = ref<DomainRecord>({ ...props.initial }), receipt = ref<DomainCheck>(), busy = ref(false), fresh = ref(true), error = ref(''), message = ref('')
type Action = 'verify' | 'challenge' | 'primary' | 'enable' | 'disable'
const session = useDirectorySession('tenant:domain:view', () => { receipt.value = undefined; busy.value = false }, () => { busy.value = false; fresh.value = false })
const disabled = computed(() => busy.value || submission.busy.value || submission.querying.value || submission.unresolved.value || !fresh.value || !session.accessible.value || props.tenant.status === 'RETIRED')
const open = computed({ get: () => !auth.user?.locked, set: value => { if (!value && !auth.user?.locked) emit('close') } })
const time = (value: string | null) => formatInstant(value, preferences.timeZone, locale.value)
function accept(value: DomainRecord) { row.value = value; fresh.value = true; emit('changed', value) }
async function refresh() {
  if (busy.value || submission.busy.value || submission.querying.value || !session.accessible.value) return
  const ticket = session.ticket(); busy.value = true; error.value = ''
  try { const result = await directory.domain(row.value.id); if (session.valid(ticket)) accept(result) }
  catch (cause) { if (session.valid(ticket)) { fresh.value = false; error.value = (cause as Error).message } }
  finally { if (session.valid(ticket)) busy.value = false }
}
function evidence(value: DomainCheck, key: string) {
  if (!value || value.operationId !== key || value.domainId !== row.value.id || !['RUNNING', 'SUCCEEDED', 'EXPIRED', 'FAILED', 'SUPERSEDED'].includes(value.status)) throw new Error(t('tenant.directory.notConfirmed'))
  return { id: key, state: value.status === 'RUNNING' ? 'PENDING' as const : value.status === 'SUCCEEDED' ? 'SUCCEEDED' as const : 'FAILED_FINAL' as const }
}
async function act(kind: Action) {
  if (disabled.value || !auth.hasPermission(kind === 'verify' || kind === 'challenge' ? 'tenant:domain:verify' : 'tenant:domain:update')) return
  if (kind === 'verify' && !mayCheck(row.value, props.settings.configured)) return
  if (kind === 'enable' && !mayEnable(row.value, props.tenant)) return
  const ticket = session.ticket(), snapshot = { ...row.value }; error.value = ''; message.value = ''; receipt.value = undefined
  const result = await submission.run<DomainCheck | DomainRecord>({ operation: 'domains.' + kind, resource: 'tenant.domains.' + snapshot.id, protocol: kind === 'verify' ? 'NATIVE' : 'LOCAL' }, async () => {
    if (!await confirmAction(t('tenant.directory.confirm.' + kind, { hostname: snapshot.hostname }), t('common.confirm')) || !session.valid(ticket) || snapshot.version !== row.value.version) return
    return async options => {
      if (kind === 'verify') {
        const value = await directory.verify(snapshot, options.idempotencyKey, options)
        evidence(value, options.idempotencyKey)
        if (session.valid(ticket)) receipt.value = value
        return value
      }
      return kind === 'challenge' ? directory.challenge(snapshot, options) : kind === 'primary' ? directory.primary(snapshot, options) : directory.enabled(snapshot, kind === 'enable', options)
    }
  }, { success: value => { const state = evidence(value as DomainCheck, submission.key.value).state; return state === 'SUCCEEDED' ? 'completed' : state === 'PENDING' ? 'pending' : 'failed' }, failure: cause => unknownWrite(cause) ? 'unknown' : 'rejected' })
  if (!session.valid(ticket)) return
  if (result.kind === 'cancelled' || result.kind === 'blocked') { if (submission.error.value) error.value = submission.error.value; return }
  fresh.value = false
  if (result.kind === 'success' || result.kind === 'completed') message.value = t('tenant.directory.updated')
  else error.value = submission.error.value
  if (!submission.unresolved.value) await refresh()
}
async function reconcile() {
  if (!submission.unresolved.value || busy.value || submission.querying.value || !session.accessible.value) return
  const ticket = session.ticket(); error.value = ''; message.value = ''
  if (submission.record.value?.protocol === 'NATIVE') {
    const key = submission.record.value.key, id = row.value.id
    const result = await submission.checkNative(() => directory.check(id, key), value => evidence(value, key))
    if (!session.valid(ticket)) return
    if (result) receipt.value = result.value
    if (result?.state === 'SUCCEEDED' || result?.state === 'FAILED_FINAL') { message.value = t('tenant.directory.stateMatched'); await refresh() }
    else error.value = submission.error.value
  } else {
    const state = await submission.check()
    if (!session.valid(ticket)) return
    if (state === 'SUCCEEDED' || state === 'FAILED_FINAL') { message.value = t('tenant.directory.stateMatched'); await refresh() }
    else error.value = submission.error.value
  }
}
async function abandon() {
  const ticket = session.ticket()
  if (!busy.value && !submission.busy.value && !submission.querying.value && await confirmAction(t('tenant.directory.leaveUnknown'), t('common.confirm')) && session.valid(ticket)) emit('close')
}
async function copy(value: string) {
  const ticket = session.ticket()
  try { await navigator.clipboard.writeText(value); if (session.valid(ticket)) message.value = t('tenant.directory.copied') }
  catch { if (session.valid(ticket)) error.value = t('tenant.directory.copyFailed') }
}
</script>
<template>
  <EditorDialog v-model="open" :title="row.hostname" :description="t('tenant.directory.domainDetailHint')" :busy="busy || submission.busy.value" :dirty="submission.unresolved.value" wide>
    <ErrorNotice :message="error" /><p v-if="message" role="status" class="mb-3 text-sm">{{ message }}</p>
    <dl class="grid grid-cols-2 gap-3 text-sm sm:grid-cols-3">
      <div><dt class="text-muted-foreground">{{ t('tenant.directory.owner') }}</dt><dd class="break-all">{{ tenant.name }}</dd></div>
      <div><dt class="text-muted-foreground">{{ t('tenant.directory.portal') }}</dt><dd>{{ row.portal }}</dd></div>
      <div><dt class="text-muted-foreground">{{ t('tenant.directory.version') }}</dt><dd>{{ row.version }}</dd></div>
      <div><dt class="text-muted-foreground">{{ t('tenant.directory.status') }}</dt><dd>{{ t('tenant.directory.domainStates.' + row.status) }}<span v-if="row.primaryDomain"> {{ t('tenant.directory.separator') }} {{ t('tenant.directory.primary') }}</span></dd></div>
      <div><dt class="text-muted-foreground">{{ t('tenant.directory.ownership') }}</dt><dd>{{ t('tenant.directory.states.' + row.verificationStatus) }}</dd></div>
      <div><dt class="text-muted-foreground">{{ t('tenant.directory.dns') }}</dt><dd>{{ t('tenant.directory.states.' + row.dnsStatus) }}</dd></div>
      <div><dt class="text-muted-foreground">{{ t('tenant.directory.tls') }}</dt><dd>{{ t('tenant.directory.states.' + row.tlsStatus) }}</dd></div>
      <div><dt class="text-muted-foreground">{{ t('tenant.directory.verifiedAt') }}</dt><dd>{{ time(row.verifiedAt) }}</dd></div>
      <div><dt class="text-muted-foreground">{{ t('tenant.directory.tlsExpiry') }}</dt><dd>{{ time(row.tlsValidUntil) }}</dd></div>
    </dl>
    <div class="mt-5 space-y-3 rounded-xl border p-4">
      <p class="text-sm font-medium">{{ t('tenant.directory.dnsChallenge') }}</p>
      <p class="text-sm text-muted-foreground">{{ t('tenant.directory.dnsHint') }}</p>
      <template v-if="row.challengeValue && row.challengeRecord"><div v-for="field in ['challengeRecord', 'challengeValue'] as const" :key="field" class="space-y-1"><p class="text-xs text-muted-foreground">{{ t('tenant.directory.' + field) }}</p><div class="flex items-start gap-2"><code class="min-w-0 flex-1 select-all break-all rounded bg-muted p-2 text-xs">{{ row[field] }}</code><Button variant="outline" size="sm" :aria-label="t('tenant.directory.copy.' + field)" @click="copy(row[field]!)">{{ t('tenant.directory.copyLabel') }}</Button></div></div><p class="text-xs">{{ t('tenant.directory.challengeExpiry') }} {{ t('tenant.directory.separator') }} {{ time(row.challengeExpiresAt) }}</p></template>
      <PermissionButton permission="tenant:domain:verify" variant="outline" :disabled="disabled || row.tlsStatus === 'LOCAL'" @click="act('challenge')">{{ t(row.challengeValue ? 'tenant.directory.rotateChallenge' : 'tenant.directory.createChallenge') }}</PermissionButton>
    </div>
    <div class="mt-5 space-y-3 rounded-xl border p-4">
      <p class="text-sm">{{ t(settings.configured ? 'tenant.directory.ingress' : 'tenant.directory.verificationDisabled') }}</p><p v-if="settings.configured" class="break-all font-mono text-xs">{{ settings.ingressAddresses.join(', ') }}</p>
      <p class="text-sm">{{ t('tenant.directory.lastCheck') }} {{ t('tenant.directory.separator') }} {{ t('tenant.directory.states.' + row.checkStatus) }} {{ t('tenant.directory.separator') }} {{ time(row.checkCompletedAt) }}</p><p v-if="row.checkFailure !== 'NONE'" class="text-sm">{{ t('tenant.directory.failures.' + row.checkFailure) }}</p>
      <PermissionButton permission="tenant:domain:verify" variant="outline" :disabled="disabled || !mayCheck(row, settings.configured)" @click="act('verify')">{{ t('tenant.directory.runCheck') }}</PermissionButton>
      <div v-if="receipt" class="space-y-1 text-sm" role="status"><p>{{ t('tenant.directory.checkResult') }} {{ t('tenant.directory.separator') }} {{ t('tenant.directory.states.' + receipt.status) }}</p><p v-if="receipt.failureCode !== 'NONE'">{{ t('tenant.directory.failures.' + receipt.failureCode) }}</p><p class="break-all text-xs">{{ t('tenant.operationLabel', { id: receipt.operationId }) }}</p><p v-if="receipt.addresses.length" class="break-all text-xs">{{ receipt.addresses.join(', ') }}</p><details v-if="receipt.fingerprints.length"><summary>{{ t('tenant.directory.fingerprints') }}</summary><p v-for="fingerprint in receipt.fingerprints" :key="fingerprint" class="break-all font-mono text-xs">{{ fingerprint }}</p></details></div>
    </div>
    <OperationStatus :slow="submission.slow.value" /><div v-if="submission.unresolved.value" class="mt-5 space-y-3 rounded-lg border p-4" role="status"><p>{{ t('tenant.directory.unknown') }}</p><p v-if="submission.record.value?.protocol === 'NATIVE'" class="break-all text-xs">{{ t('tenant.operationLabel', { id: submission.record.value?.key }) }}</p><div class="flex flex-wrap gap-2"><Button variant="outline" :disabled="busy || submission.querying.value" @click="reconcile">{{ t('tenant.checkResult') }}</Button><Button variant="ghost" :disabled="busy || submission.querying.value" @click="abandon">{{ t('tenant.directory.leavePending') }}</Button></div></div>
    <div class="mt-5 space-y-3"><p class="text-sm text-muted-foreground">{{ t('tenant.directory.activationHint') }}</p><div class="flex flex-wrap gap-2"><PermissionButton v-if="row.status !== 'ACTIVE'" permission="tenant:domain:update" :disabled="disabled || !mayEnable(row, tenant)" @click="act('enable')">{{ t('tenant.directory.enable') }}</PermissionButton><PermissionButton v-if="row.status !== 'DISABLED'" permission="tenant:domain:update" variant="outline" :disabled="disabled" @click="act('disable')">{{ t('tenant.directory.disable') }}</PermissionButton><PermissionButton permission="tenant:domain:update" variant="outline" :disabled="disabled || row.primaryDomain || row.status !== 'ACTIVE' || !['READY', 'LOCAL'].includes(row.tlsStatus)" @click="act('primary')">{{ t('tenant.directory.setPrimary') }}</PermissionButton></div></div>
    <template #footer><Button variant="outline" :disabled="busy || submission.busy.value || submission.querying.value" @click="refresh">{{ t('common.refresh') }}</Button><Button variant="outline" :disabled="busy || submission.busy.value || submission.querying.value" @click="emit('close')">{{ t('tenant.directory.close') }}</Button></template>
  </EditorDialog>
</template>
