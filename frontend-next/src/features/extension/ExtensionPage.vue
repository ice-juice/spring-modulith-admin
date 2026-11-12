<script setup lang="ts">
import TableWorkspace from '@/components/business/TableWorkspace.vue'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Label } from '@/components/ui/label'
import PageHeader from '@/components/business/PageHeader.vue'
import FilterBar from '@/components/business/FilterBar.vue'
import DataTable from '@/components/business/DataTable.vue'
import EditorDialog from '@/components/business/EditorDialog.vue'
import { useSubmission } from '@/composables/useSubmission'
import OperationStatus from '@/components/business/OperationStatus.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import PasswordInput from '@/components/business/PasswordInput.vue'
import { getToken } from '@/lib/token'
import { useAuth } from '@/stores/auth'
import { usePreferences } from '@/stores/preferences'
import { formatInstant } from '@/lib/time'
import { useRequestGate } from '@/composables/useRequestGate'
import ReleaseFactors from './ReleaseFactors.vue'
import RuntimePanel from './RuntimePanel.vue'
import { definitions, fieldBody, type Field } from './commands'
import { allowed, CommandNotDispatched, commandEvidence, commandStatusEvidence, lookupCommandStatus, resumeReleaseUpload, releaseGuard, clearReleaseSession, connectRelease, executeCommand, inspectFiles, prepareCommand, releaseAccess, releaseIdentity, releaseRequest, sha256, type CommandStatus, type Artifact, type Bundle, type Deployment, type FileIntent, type Group, type Plugin, type Policy, type Prepared, type Receipt, type Release } from './client'

interface Item { id: string; [key: string]: unknown }
const { t } = useI18n(), auth = useAuth(), preferences = usePreferences(), gate = useRequestGate()
const commandAction = useSubmission(), deliveryAction = useSubmission()
deliveryAction.bind('extension.credential.*')
commandAction.bind('extension.console.*')
const tab = ref('artifacts'), environment = ref('production'), groupId = ref(''), pluginId = ref(''), groups = ref<Group[]>([]), plugins = ref<Plugin[]>([]), artifacts = ref<Artifact[]>([]), deployments = ref<Deployment[]>([])
const policy = ref<Policy>(), rows = ref<Item[]>([]), loading = ref(false), writing = ref(false), error = ref(''), page = ref(1), total = ref(0), progress = ref<number>(), receipt = ref<Receipt>()
const unknownKey = computed(() => commandAction.unresolved.value ? commandAction.record.value?.operationId || '' : '')
const connecting = ref(false), connectionError = ref(''), showFactors = ref(false)
let connectionEpoch = 0, heartbeat: ReturnType<typeof setInterval> | undefined
async function connect() {
  if (connecting.value || !auth.user || auth.user.locked) return
  const epoch = ++connectionEpoch; connecting.value = true; connectionError.value = ''
  try { await connectRelease() }
  catch (cause) { if (epoch === connectionEpoch) { clearReleaseSession(); connectionError.value = (cause as Error).message } }
  finally { if (epoch === connectionEpoch) connecting.value = false }
}
async function factorsCompleted() { showFactors.value = false; await connect() }
const group = computed(() => groups.value.find(g => g.id === groupId.value)), plugin = computed(() => plugins.value.find(p => p.id === pluginId.value))
const tabs = computed(() => ['artifacts', 'deployments', 'jobs', 'runtime', ...(releaseAccess.value?.securityAdmin ? ['accounts','bindings','machines','keys','audit','storage'] : [])])
const columns = computed(() => (tab.value === 'artifacts' ? ['name','version','state','bytes','sha256','createdAt','actions'] : tab.value === 'deployments' ? ['id','revision','state','stable','canary','actions'] : tab.value === 'jobs' ? ['id','artifactId','state','failure','createdAt'] : tab.value === 'accounts' ? ['username','enabled','enrolled','securityAdmin','credentialVersion','actions'] : tab.value === 'bindings' ? ['machineId','subject','userId','revision','enabled','actions'] : tab.value === 'machines' ? ['id','kind','environments','enabled','revision','actions'] : tab.value === 'keys' ? ['id','environment','purposes','notAfter','revoked'] : tab.value === 'storage' ? ['groupId','bytes','limit'] : ['sequence','actor','action','resource','outcome','occurredAt']).map(key => ({ key, title: t('extension.fields.' + key), width: key === 'sha256' ? 260 : key === 'actions' ? 300 : 130 })))
const can = (action: string) => allowed(action, groupId.value, environment.value)
function text(value: unknown) { if (value && typeof value === 'object' && !Array.isArray(value)) return JSON.stringify(value); if (typeof value === 'boolean') return t(value ? 'common.yes' : 'common.no'); if (Array.isArray(value)) return value.join(', '); return value == null ? '—' : String(value) }
function date(value: unknown) { return typeof value === 'string' ? formatInstant(value, preferences.timeZone) : '—' }
async function load() {
  if (!releaseAccess.value) return
  const current = gate(); loading.value = true; error.value = ''
  try {
    const [catalog, limits] = await Promise.all([releaseRequest<Group[]>('GET','/groups'),releaseRequest<Policy>('GET','/policy')])
    if (!current()) return
    groups.value = catalog; policy.value = limits
    if (!catalog.some(g => g.id === groupId.value)) groupId.value = catalog[0]?.id || ''
    const catalogPlugins = groupId.value ? await releaseRequest<Plugin[]>('GET',`/groups/${encodeURIComponent(groupId.value)}/plugins`) : []
    if (!current()) return
    plugins.value = catalogPlugins
    if (!catalogPlugins.some(p => p.id === pluginId.value)) pluginId.value = catalogPlugins[0]?.id || ''
    const query = `environment=${encodeURIComponent(environment.value)}`, offset = (page.value - 1) * 20
    let result: Item[] = [], count = 0
    if (tab.value === 'artifacts' && pluginId.value) {
      const data = await releaseRequest<{ items: Artifact[]; total: number }>('GET',`/console/artifacts?plugin=${encodeURIComponent(pluginId.value)}&${query}&offset=${offset}&limit=20`)
      if (!current()) return
      artifacts.value = data.items; count = data.total; result = data.items.map(a => ({ ...a, version: a.manifest?.version || '—' }))
    } else if (tab.value === 'deployments') {
      const data = await releaseRequest<Deployment[]>('GET',`/deployments?${query}&offset=${offset}&limit=20`)
      if (!current()) return
      deployments.value = data; count = offset + data.length + (data.length === 20 ? 1 : 0)
      result = data.map(d => ({ ...d, state: d.pending?.state || (d.active ? 'ACTIVE' : 'UNAVAILABLE'), stable: d.active?.stable.id, canary: d.active?.canary?.id }))
    } else if (tab.value === 'jobs' && pluginId.value) result = await releaseRequest<Item[]>('GET',`/console/jobs?plugin=${encodeURIComponent(pluginId.value)}&${query}`)
    else if (tab.value === 'accounts') result = await releaseRequest<Item[]>('GET',`/accounts?offset=${offset}&limit=20`)
    else if (tab.value === 'bindings') result = await releaseRequest<Item[]>('GET',`/console/bindings?offset=${offset}&limit=20`)
    else if (tab.value === 'machines') result = await releaseRequest<Item[]>('GET',`/security/machines?offset=${offset}&limit=20`)
    else if (tab.value === 'keys') result = await releaseRequest<Item[]>('GET','/security/public-keys')
    else if (tab.value === 'audit') {
      const data = await releaseRequest<{entry: Record<string, unknown>}[]>('GET',`/security/audit?after=${auditAfter.value}&limit=100`)
      result = data.map(e => ({ ...e.entry, id: String(e.entry.sequence) }))
    } else if (tab.value === 'storage') {
      const data = await releaseRequest<{groups: {groupId: string; bytes: number; limit: number}[]; repositoryBytes: number; repositoryLimit: number}>('GET','/console/storage')
      result = [{ id: 'repository', groupId: t('extension.repository'), bytes: data.repositoryBytes, limit: data.repositoryLimit }, ...data.groups.map(g => ({...g,id:g.groupId}))]
    }
    if (!current()) return
    rows.value = result; total.value = count || offset + result.length + (['accounts','bindings','machines'].includes(tab.value) && result.length === 20 ? 1 : 0)
  } catch (e) { if (current()) { rows.value = []; error.value = (e as Error).message } } finally { if (current()) loading.value = false }
}
const auditAfter = ref('0')
function scopeChanged() { page.value = 1; artifacts.value = []; deployments.value = []; rows.value = []; void load() }
function changeTab(value: string) { if (writing.value || dialog.value || prepared.value) return; tab.value = value; page.value = 1; rows.value = []; void load() }
function connected() { environment.value = releaseAccess.value?.environments[0] || 'production'; void load() }
watch(page, () => { void load() })
watch(() => JSON.stringify([getToken(), auth.user?.id, auth.user?.portal, auth.user?.locked, [...(auth.user?.permissions || [])].sort()]), () => {
  connectionEpoch++; connecting.value = false; showFactors.value = false; clearReleaseSession(); clearSecrets(); rows.value = []; groups.value = []; plugins.value = []
  if (auth.user && !auth.user.locked) void connect()
})
watch(releaseAccess, value => { if (value) connected(); else { gate(); rows.value = []; groups.value = []; plugins.value = []; prepared.value = undefined; clearSecrets(); dialog.value = false; secretResult.value = ''; receipt.value = undefined;resumeUpload.value='';detail.value='';detailOpen.value=false;reviewSummary.value='' } })
function releaseScopeChanged() { editorGeneration++; gate(); clearSecrets(); resumeUpload.value = ''; prepared.value = undefined; confirmOpen.value = false; dialog.value = false; receipt.value = undefined; secretResult.value = ''; detail.value = ''; detailOpen.value = false; reviewSummary.value = '' }
onMounted(() => { window.addEventListener('sma:release-access-changed', releaseScopeChanged); void connect(); heartbeat = setInterval(() => { if (releaseAccess.value) void connect() }, 30000) })
onBeforeUnmount(() => { window.removeEventListener('sma:release-access-changed', releaseScopeChanged); connectionEpoch++; clearInterval(heartbeat); clearReleaseSession(); clearSecrets() })

const dialog = ref(false), action = ref(''), fields = ref<Field[]>([]), values = reactive<Record<string, unknown>>({}), targetGroup = ref(''), targetPlugin = ref('')
const prepared = ref<Prepared>(), confirmOpen = ref(false), operationPassword = ref(''), otp = ref(''), files = ref<File[]>([]), fileIntents = ref<FileIntent[]>([]), manifest = ref('{}'), resumeUpload = ref('')
const reviewSummary=ref('')

const pendingBody = ref<unknown>(), pendingExpected = ref(0), detail = ref(''), detailOpen = ref(false), secretResult = ref('')
function clearSecrets() { operationPassword.value = ''; otp.value = ''; files.value = []; fileIntents.value = []; for (const key of Object.keys(values)) delete values[key] }
watch(confirmOpen, open => { if (!open) { operationPassword.value = ''; otp.value = ''; prepared.value = undefined } })
watch(dialog, open => { if (!open && !confirmOpen.value) clearSecrets() })
function edit(kind: string, initial: Record<string, unknown> = {}) {
  if (!releaseIdentity.value?.enrolled) { showFactors.value = true; return }
  if (writing.value || unknownKey.value || resumeUpload.value) { error.value = t('extension.resolveFirst'); return }
  action.value = kind; fields.value = definitions[kind] || []; clearSecrets(); pendingBody.value = undefined
  for (const f of fields.value) values[f.key] = initial[f.key] ?? f.value
  if (kind === 'POLICY_UPDATE') values.document = JSON.stringify(policy.value, null, 2)
  targetGroup.value = groupId.value || 'general'; targetPlugin.value = pluginId.value || 'console'; pendingExpected.value = plugin.value?.revision || 0
  if(kind==='GUARD_CONFIGURE'){values.expiresAt=new Date(Date.now()+900_000).toISOString();pendingExpected.value=Number(initial.revision)}
  error.value = ''; dialog.value = true
}
function artifactAction(kind: string, row: Item) { edit(kind); pendingBody.value = row.id }
function editBinding(row: Item) { const subject = row.subject as Record<string, unknown>; edit('IDENTITY_BIND', { machineId: row.machineId, tenantId: subject.tenantId, portal: subject.portal, hostUserId: subject.hostUserId, userId: row.userId, expectedRevision: row.revision, enabled: row.enabled }) }
async function chooseFiles(event: Event) {
  const selected = [...((event.target as HTMLInputElement).files || [])], owns = releaseGuard()
  files.value = selected; fileIntents.value = []
  if (!policy.value) return
  const current = files.value
  try { const intents = await inspectFiles(selected, policy.value); if (owns() && files.value === current) fileIntents.value = intents }
  catch (e) { if (owns() && files.value === current) { error.value = (e as Error).message; files.value = [] } }
}
const releaseForm = reactive({ id: 'business-extensions', stable: '', candidate: '', percent: 5, nodes: '', reason: '' })
const selectedDeployment = ref<Deployment>(), releaseArtifacts = ref<Artifact[]>([]), releaseHistory = ref<Release[]>([])
const companionChoices=ref<{plugin:Plugin;artifacts:Artifact[]}[]>([]),companionSelection=reactive<Record<string,string>>({})
let editorGeneration=0
watch(dialog,open=>{if(!open)editorGeneration++})
async function releaseEdit(kind: string, row?: Item) {
  if (writing.value || unknownKey.value || resumeUpload.value) return
  edit(kind);const generation=++editorGeneration; selectedDeployment.value = deployments.value.find(d => d.id === row?.id);releaseHistory.value=[];releaseArtifacts.value=[];companionChoices.value=[];for(const key of Object.keys(companionSelection))delete companionSelection[key]
  const existing = selectedDeployment.value?.active
  releaseForm.id = row?.id || 'business-extensions'; releaseForm.nodes = existing?.targetNodes.join(', ') || ''; releaseForm.reason = ''
  pendingExpected.value = selectedDeployment.value?.revision || 0
  const currentPlugin=pluginId.value,env=environment.value
  try { const artifacts=currentPlugin ? await releaseRequest<Artifact[]>('GET',`/plugins/${encodeURIComponent(currentPlugin)}/artifacts?environment=${encodeURIComponent(env)}`) : []
    if(generation!==editorGeneration)return;releaseArtifacts.value=artifacts
    if(row?.id){const history=await releaseRequest<Release[]>('GET',`/deployments/${encodeURIComponent(row.id)}/history?environment=${encodeURIComponent(env)}`);if(generation!==editorGeneration)return;releaseHistory.value=history}
    if(kind==='PUBLISH'){const choices=await Promise.all(plugins.value.filter(p=>p.id!==currentPlugin).map(async p=>({plugin:p,artifacts:await releaseRequest<Artifact[]>('GET',`/plugins/${encodeURIComponent(p.id)}/artifacts?environment=${encodeURIComponent(env)}`)})));if(generation===editorGeneration)companionChoices.value=choices}
  }
  catch (e) { error.value = (e as Error).message }
}
async function releaseBody(): Promise<unknown> {
  const d = selectedDeployment.value, active = d?.active
  if (action.value === 'CANCEL_RELEASE') return { deploymentId: d?.id, preparationId: d?.pending?.actionId, pendingRevision: d?.pending?.release.revision, reason: releaseForm.reason }
  const bundle = (id: string): Bundle => { const a = releaseArtifacts.value.find(a => a.id === id); if (!a) throw new Error(t('common.required')); return { id: 'bundle-' + crypto.randomUUID(), members: [{pluginId:a.pluginId,artifactId:a.id,sha256:a.sha256}] } }
  let stable: Bundle, canary: Bundle | null = null
  if (action.value === 'CANARY') { if (!active) throw new Error(t('common.required')); stable = active.stable; const candidate=bundle(releaseForm.candidate);canary={...candidate,members:stable.members.map(m=>candidate.members.find(c=>c.pluginId===m.pluginId)||m)};if(!stable.members.some(m=>m.pluginId===candidate.members[0]?.pluginId))throw new Error(t('common.required')) }
  else if (action.value === 'PROMOTE') { if (!active?.canary) throw new Error(t('common.required')); stable = active.canary }
  else if (action.value === 'ROLLBACK') { const historical=releaseHistory.value.find(h=>String(h.revision)===values.historicalBundle);if(!historical)throw new Error(t('common.required'));stable=historical.stable }
  else {
    stable = bundle(releaseForm.stable)
    if(action.value==='PUBLISH')for(const choice of companionChoices.value){const selected=companionSelection[choice.plugin.id];if(!selected)continue;const artifact=choice.artifacts.find(a=>a.id===selected);if(!artifact)throw new Error(t('common.required'));stable.members.push({pluginId:artifact.pluginId,artifactId:artifact.id,sha256:artifact.sha256})}
    stable.members.sort((a,b)=>a.pluginId<b.pluginId?-1:a.pluginId>b.pluginId?1:0)
  }
  // Rollback needs the historical bundle identity, not a new arbitrary vector.
  return { deploymentId: releaseForm.id, previousRevision: pendingExpected.value, revision: pendingExpected.value + 1, policyRevision: policy.value!.revision,
    stable, canary, canaryBasisPoints: canary ? Math.round(releaseForm.percent * 100) : 0, bucketSalt: active?.bucketSalt || crypto.randomUUID(), canarySubjects: [],
    targetNodes: active && ['CANARY','PROMOTE'].includes(action.value) ? active.targetNodes : [...new Set(releaseForm.nodes.split(',').map(s=>s.trim()).filter(Boolean))].sort(),
    configurationSha256: active?.configurationSha256 || await sha256(new TextEncoder().encode('{}').buffer) } satisfies Release
}
const releaseActions = ['PUBLISH','CANARY','PROMOTE','ROLLBACK','CANCEL_RELEASE']
async function review() {
  if (writing.value || !policy.value) return
  writing.value = true; error.value = ''
  const owns = releaseGuard(), editor = editorGeneration
  try {
    let body = pendingBody.value ?? fieldBody(fields.value,values)
    if (action.value === 'IDENTITY_BIND') { const binding = body as Record<string, unknown>; body = { machineId: binding.machineId, subject: { tenantId: binding.tenantId, portal: binding.portal, hostUserId: binding.hostUserId }, userId: binding.userId, expectedRevision: binding.expectedRevision, enabled: binding.enabled } }
    if (action.value === 'UPLOAD') { if (!files.value.length) throw new Error(t('common.required')); fileIntents.value = await inspectFiles(files.value,policy.value); body = files.value.some(f => f.name.endsWith('.java')) ? {manifest:JSON.parse(manifest.value)} : {} }
    if (releaseActions.includes(action.value)) body = await releaseBody()
    const own = ['CREDENTIAL_UPDATE', 'OPERATION_PASSWORD_UPDATE'].includes(action.value)
    const context = { action: action.value, environment: own ? 'security' : environment.value, groupId: own ? 'identity' : targetGroup.value, groupRevision: action.value === 'GROUP_CREATE' ? 0 : group.value?.revision || 0,
      pluginId: own ? 'user-' + releaseAccess.value!.userId : targetPlugin.value, expectedRevision: pendingExpected.value, policyRevision:policy.value.revision, files:action.value === 'UPLOAD' ? fileIntents.value : [] }
    reviewSummary.value=JSON.stringify(body,(key,value)=>/password|secret|token|grant/i.test(key)?t('extension.hiddenSecret'):value,2)
    const next = await prepareCommand(context,body); if (!owns() || editor !== editorGeneration) return
    prepared.value = next; confirmOpen.value = true; dialog.value = false
    for (const f of fields.value) if (f.type === 'password') values[f.key] = ''
  } catch(e) { if (owns()) error.value=(e as Error).message } finally {writing.value=false}
}
async function commit() {
  if (writing.value || commandAction.busy.value || !prepared.value) return
  writing.value = true; error.value = ''
  const current = prepared.value, stagedFiles = [...files.value], password = operationPassword.value, code = otp.value, owns = releaseGuard(), limits = policy.value!
  const upload = current.intent.action === 'UPLOAD'
  try {
    const result = await commandAction.run<CommandStatus>({ operation: 'extension.command.' + current.intent.action, resource: 'extension.console.' + current.intent.environment + '.' + current.intent.pluginId, protocol: 'NATIVE', nativeId: current.idempotencyKey },
      () => async () => {
        const receipt = await executeCommand(current, password, code)
        if (!owns()) throw new Error(t('extension.sessionChanged'))
        return { receipt, upload: upload && commandEvidence(receipt, current.idempotencyKey).state === 'SUCCEEDED'
          ? await resumeReleaseUpload(receipt.resourceId, stagedFiles, limits, p => { if (owns()) progress.value = p }) : undefined }
      },
      { success: value => commandOutcome(value, current.idempotencyKey, upload), failure: cause => cause instanceof CommandNotDispatched ? 'rejected' : 'unknown' })
    if (!owns()) return
    if (result.kind === 'success') {
      receipt.value = result.value.receipt
      if (owns()) { confirmOpen.value = false; clearSecrets(); await load() }
    } else error.value = commandAction.error.value
  } catch (cause) { if (owns()) error.value = (cause as Error).message }
  finally { writing.value = false; operationPassword.value = ''; otp.value = ''; prepared.value = undefined; confirmOpen.value = false; progress.value = undefined }
}
function commandOutcome(value: CommandStatus, key: string, upload: boolean) {
  const state = commandStatusEvidence(value, key, upload).state
  return state === 'SUCCEEDED' ? 'completed' : state === 'FAILED_FINAL' ? 'failed' : state === 'PENDING' ? 'pending' : 'unknown'
}
async function reconcile() {
  const record = commandAction.record.value
  if (!record?.operationId || commandAction.querying.value || writing.value) return
  const owns = releaseGuard(), key = record.operationId, upload = record.operation === 'extension.command.UPLOAD'
  resumeUpload.value = ''
  const result = await commandAction.checkNative(() => lookupCommandStatus(key, upload), value => commandStatusEvidence(value, key, upload))
  if (!owns()) return
  if (result?.state === 'PENDING' && result.value.upload?.resumable) resumeUpload.value = result.value.upload.id
  if (result?.state === 'SUCCEEDED' || result?.state === 'FAILED_FINAL') {
    receipt.value = result.value.receipt
    clearSecrets()
    await load()
  } else error.value = commandAction.error.value
}
async function resume() {
  const record = commandAction.record.value, limits = policy.value
  if (writing.value || !files.value.length || !limits || !record?.operationId || record.operation !== 'extension.command.UPLOAD') return
  const owns = releaseGuard(), key = record.operationId, stagedFiles = [...files.value]
  writing.value = true; error.value = ''
  try {
    const result = await commandAction.continueNative(async () => {
      const status = await lookupCommandStatus(key, true)
      if (commandStatusEvidence(status, key, true).state !== 'PENDING') return status
      if (!owns()) throw new Error(t('extension.sessionChanged'))
      return { receipt: status.receipt, upload: await resumeReleaseUpload(status.receipt.resourceId, stagedFiles, limits, p => { if (owns()) progress.value = p }) }
    }, { success: value => commandOutcome(value, key, true) })
    if (!owns()) return
    if (result?.kind === 'success') { receipt.value = result.value.receipt; resumeUpload.value = ''; clearSecrets(); await load() }
    else { resumeUpload.value = ''; error.value = commandAction.error.value }
  } finally { writing.value = false; progress.value = undefined }
}
function show(row: Item) { detail.value=JSON.stringify(row,null,2);detailOpen.value=true }
async function guardDetail(id:string){try{const value=await releaseRequest<Record<string,unknown>|null>('GET',`/deployments/${encodeURIComponent(id)}/guard?environment=${encodeURIComponent(environment.value)}`);detail.value=JSON.stringify(value,null,2);detailOpen.value=true}catch(e){error.value=(e as Error).message}}
async function deliver(kind: string, id: string) {
  if (writing.value || deliveryAction.busy.value) return
  const owns = releaseGuard()
  const result = await deliveryAction.run({ operation: 'extension.credential-' + kind, resource: 'extension.credential.' + kind + '.' + id, protocol: 'NATIVE' },
    () => () => releaseRequest('POST', kind === 'machine' ? '/security/machines/' + encodeURIComponent(id) + '/token' : '/accounts/' + encodeURIComponent(id) + '/enrollment-ticket'), { success: () => 'completed' })
  if (!owns()) return
  if (result.kind === 'success') secretResult.value = JSON.stringify(result.value, null, 2)
  else error.value = deliveryAction.error.value
}
</script>

<template>
  <TableWorkspace class="extension-workspace" :class="{ 'release-login-workspace': !releaseAccess }"><template #tools>
    <PageHeader :title="t('extension.title')" :description="t('extension.description')" />
    <div v-if="!releaseAccess" class="rounded-xl border bg-card p-5" role="status">
      <p class="mb-3 text-sm text-muted-foreground">{{ t(connecting ? 'extension.connecting' : 'extension.unifiedEntryHint') }}</p>
      <ErrorNotice :message="connectionError" />
      <Button v-if="!connecting" variant="outline" @click="connect">{{ t('extension.reconnect') }}</Button>
    </div>
    <template v-else>
      <div class="flex flex-wrap items-center justify-between gap-3 rounded-2xl border bg-accent/20 px-4 py-3"><div><span class="font-medium">{{ auth.user?.name || auth.user?.username }}</span><span class="ml-3 text-xs text-muted-foreground">{{ t(releaseAccess.securityAdmin ? 'extension.securityAdmin' : 'extension.scopedUser') }}</span></div><div class="flex flex-wrap items-center gap-2"><Label for="release-env">{{ t('extension.fields.environment') }}</Label><select id="release-env" v-model="environment" class="h-10 rounded-lg border bg-card px-3 text-sm" :disabled="writing || dialog || confirmOpen" @change="scopeChanged"><option v-for="env in releaseAccess.environments" :key="env">{{ env }}</option></select><Button v-if="releaseIdentity?.enrolled" variant="ghost" size="sm" @click="showFactors = !showFactors">{{ t('extension.recovery') }}</Button><Button variant="ghost" size="sm" @click="edit('OPERATION_PASSWORD_UPDATE')">{{ t('extension.actions.OPERATION_PASSWORD_UPDATE') }}</Button></div></div>
      <div v-if="!releaseIdentity?.enrolled" class="flex flex-wrap items-center gap-3 rounded-xl border bg-card px-4 py-3"><p class="flex-1 text-sm">{{ t('extension.enrollmentRequired') }}</p><Button size="sm" @click="showFactors = !showFactors">{{ t('extension.beginEnrollment') }}</Button></div>
      <ReleaseFactors v-if="showFactors" @completed="factorsCompleted" />
      <ErrorNotice :message="error" />
      <div v-if="receipt" role="status" class="flex flex-wrap items-center gap-3 rounded-xl border bg-card p-3 text-sm"><span>{{ t('extension.receipt') }}{{ t('extension.colon') }} {{ receipt.state }}</span><code class="break-all text-xs">{{ receipt.id }}</code><Button variant="ghost" size="sm" @click="show({ ...receipt })">{{ t('common.details') }}</Button></div>
      <OperationStatus :slow="commandAction.slow.value" /><OperationStatus :slow="deliveryAction.slow.value" /><p v-if="deliveryAction.unresolved.value" class="rounded-xl border p-3 text-sm" role="status">{{ t('extension.credentialUnknown') }}</p><div v-if="unknownKey" class="rounded-xl border p-3"><p class="mb-2 text-sm">{{ t('extension.unknownResult') }}</p><code class="break-all text-xs">{{ unknownKey }}</code><Button :disabled="writing || commandAction.querying.value" variant="outline" @click="reconcile">{{ t('extension.reconcile') }}</Button></div>
      <div v-if="resumeUpload" class="rounded-xl border p-3 text-sm"><p>{{ t('extension.resumeHint') }}</p><Label for="release-resume-files">{{ t('extension.originalFiles') }}</Label><Input id="release-resume-files" type="file" multiple accept=".jar,.java" :disabled="writing || commandAction.querying.value" @change="chooseFiles" /><Button :disabled="writing || commandAction.querying.value || !files.length" variant="outline" @click="resume">{{ t('extension.resume') }}</Button></div>
      <div v-if="progress !== undefined" role="status">{{ t('extension.uploadProgress', {progress}) }}<progress class="w-full" :value="progress" max="100" /></div>
      <nav class="hidden flex-wrap gap-1 sm:flex" :aria-label="t('extension.sections')"><Button v-for="item in tabs" :key="item" :variant="tab === item ? 'secondary' : 'ghost'" :aria-pressed="tab === item" :disabled="writing" @click="changeTab(item)">{{ t('extension.tabs.' + item) }}</Button></nav>
      <select :value="tab" class="h-10 w-full rounded-lg border bg-card px-3 sm:hidden" :aria-label="t('extension.sections')" :disabled="writing" @change="changeTab(($event.target as HTMLSelectElement).value)"><option v-for="item in tabs" :key="item" :value="item">{{ t('extension.tabs.'+item) }}</option></select>
        <details v-if="tab !== 'runtime'" class="rounded-xl border bg-card px-3 py-2"><summary class="cursor-pointer text-sm">{{ t('extension.scopeSettings') }} {{ t('common.detailSeparator') }} {{ group?.name || t('extension.select') }} {{ t('common.detailSeparator') }} {{ plugin?.name }}</summary><div class="release-scope-controls mt-3 flex flex-wrap items-end gap-3">
          <div class="min-w-36 flex-1 space-y-1"><Label for="release-group">{{ t('extension.group') }}</Label><select id="release-group" v-model="groupId" class="h-10 w-full rounded-lg border bg-card px-2" :disabled="writing || loading" @change="scopeChanged"><option v-for="g in groups" :key="g.id" :value="g.id">{{ g.name }}</option></select></div>
          <div class="min-w-36 flex-1 space-y-1"><Label for="release-plugin">{{ t('extension.plugin') }}</Label><select id="release-plugin" v-model="pluginId" class="h-10 w-full rounded-lg border bg-card px-2" :disabled="writing || loading" @change="scopeChanged"><option v-for="p in plugins" :key="p.id" :value="p.id">{{ p.name }}</option></select></div>
          <div class="flex flex-wrap gap-2"><Button v-if="releaseAccess.securityAdmin" size="sm" variant="outline" @click="edit('GROUP_CREATE')">{{ t('extension.actions.GROUP_CREATE') }}</Button><Button v-if="can('PLUGIN_CREATE')" size="sm" variant="outline" @click="edit('PLUGIN_CREATE')">{{ t('extension.actions.PLUGIN_CREATE') }}</Button><Button v-if="can('GROUP_UPDATE') && group" size="sm" variant="ghost" @click="edit('GROUP_UPDATE', {...group})">{{ t('extension.actions.GROUP_UPDATE') }}</Button><Button v-if="can('GROUP_MOVE') && plugin" size="sm" variant="ghost" @click="edit('GROUP_MOVE')">{{ t('extension.actions.GROUP_MOVE') }}</Button><Button v-if="releaseAccess.securityAdmin && group" size="sm" variant="ghost" @click="edit('GROUP_GRANT')">{{ t('extension.actions.GROUP_GRANT') }}</Button></div>
          <p class="w-full text-xs leading-5 text-muted-foreground">{{ t('extension.permissionHint') }}</p>
        </div></details>
    </template></template><template v-if="releaseAccess">
      <div v-if="tab === 'runtime'" class="min-h-0 flex-1 overflow-auto"><RuntimePanel /></div>
      <TableWorkspace v-else><template #tools>
          <FilterBar :busy="loading || writing" @search="load">
            <Input v-if="tab === 'audit'" v-model="auditAfter" :aria-label="t('extension.auditAfter')" :placeholder="t('extension.auditAfter')" />
            <span v-else class="text-sm text-muted-foreground">{{ t('extension.tabs.' + tab) }}</span>
            <template #actions>
              <Button v-if="tab === 'artifacts' && plugin && can('UPLOAD')" :disabled="writing" @click="edit('UPLOAD')">{{ t('extension.actions.UPLOAD') }}</Button>
              <Button v-if="tab === 'deployments' && can('PUBLISH')" @click="releaseEdit('PUBLISH')">{{ t('extension.actions.PUBLISH') }}</Button>
              <Button v-if="tab === 'bindings'" @click="edit('IDENTITY_BIND')">{{ t('extension.actions.IDENTITY_BIND') }}</Button><Button v-if="tab === 'accounts'" @click="edit('USER_CREATE')">{{ t('extension.actions.USER_CREATE') }}</Button>
              <Button v-if="tab === 'machines'" @click="edit('MACHINE_CREATE')">{{ t('extension.actions.MACHINE_CREATE') }}</Button>
              <Button v-if="tab === 'keys'" @click="edit('TRUST_UPDATE')">{{ t('extension.actions.TRUST_UPDATE') }}</Button>
              <Button v-if="tab === 'storage'" variant="outline" @click="edit('POLICY_UPDATE')">{{ t('extension.actions.POLICY_UPDATE') }}</Button>
            </template>
          </FilterBar>
          <div v-if="tab === 'artifacts' && releaseAccess.securityAdmin" class="mt-3 flex flex-wrap gap-2"><Button variant="outline" @click="edit('DEPENDENCY_REGISTER')">{{ t('extension.actions.DEPENDENCY_REGISTER') }}</Button><Button variant="outline" @click="edit('EXCEPTION_CREATE')">{{ t('extension.actions.EXCEPTION_CREATE') }}</Button></div></template><DataTable :rows="rows" :columns="columns" :loading="loading" :total="['artifacts','deployments','accounts','bindings','machines'].includes(tab) ? total : undefined" v-model:page="page" :storage-key="'extension.' + releaseAccess.userId + '.' + tab">
            <template #createdAt="{value}">{{ date(value) }}</template><template #occurredAt="{value}">{{ date(value) }}</template><template #notAfter="{value}">{{ date(value) }}</template>
            <template #enabled="{value}">{{ text(value) }}</template><template #enrolled="{value}">{{ text(value) }}</template><template #securityAdmin="{value}">{{ text(value) }}</template><template #revoked="{value}">{{ text(value) }}</template><template #environments="{value}">{{ text(value) }}</template><template #purposes="{value}">{{ text(value) }}</template>
            <template #sha256="{value}"><code class="block max-w-72 break-all text-xs">{{ value }}</code></template>
            <template #state="{value}"><span class="rounded-md bg-muted px-2 py-1 text-xs">{{ t('extension.states.' + String(value)) }}</span></template>
            <template #actions="{row}"><div class="flex flex-wrap gap-1">
              <Button variant="ghost" size="sm" @click="show(row)">{{ t('common.details') }}</Button>
              <template v-if="tab === 'artifacts'"><Button v-for="kind in ['VALIDATE','REVOKE','DELETE']" v-show="can(kind)" :key="kind" variant="ghost" size="sm" :disabled="writing" @click="artifactAction(kind,row)">{{ t('extension.actions.' + kind) }}</Button></template>
              <template v-if="tab === 'deployments'"><Button v-for="kind in ['CANARY','PROMOTE','ROLLBACK','CANCEL_RELEASE']" v-show="can(kind)" :key="kind" variant="ghost" size="sm" @click="releaseEdit(kind,row)">{{ t('extension.actions.' + kind) }}</Button><Button v-if="can('GUARD_CONFIGURE')" variant="ghost" size="sm" @click="edit('GUARD_CONFIGURE',{deploymentId:row.id,revision:row.revision})">{{ t('extension.actions.GUARD_CONFIGURE') }}</Button><Button variant="ghost" size="sm" @click="guardDetail(row.id)">{{ t('extension.guardStatus') }}</Button></template>
              <template v-if="tab === 'bindings'"><Button variant="ghost" size="sm" @click="editBinding(row)">{{ t('common.edit') }}</Button></template><template v-if="tab === 'accounts'"><Button variant="ghost" size="sm" @click="edit('USER_UPDATE',{...row,userId:row.id,expectedCredentialVersion:row.credentialVersion})">{{ t('common.edit') }}</Button><Button variant="ghost" size="sm" @click="edit('FACTOR_RESET',{userId:row.id,expectedCredentialVersion:row.credentialVersion})">{{ t('extension.actions.FACTOR_RESET') }}</Button><Button v-if="!row.enrolled" variant="ghost" size="sm" :disabled="writing" @click="deliver('account',row.id)">{{ t('extension.receiveSecret') }}</Button></template>
              <template v-if="tab === 'machines'"><Button variant="ghost" size="sm" @click="edit('MACHINE_UPDATE',{...row,expectedRevision:row.revision,environments:text(row.environments)})">{{ t('common.edit') }}</Button><Button variant="ghost" size="sm" @click="edit('MACHINE_ROTATE',{id:row.id,expectedRevision:row.revision})">{{ t('extension.actions.MACHINE_ROTATE') }}</Button><Button v-if="row.rotationId" variant="ghost" size="sm" @click="edit('MACHINE_ACTIVATE',{id:row.id,expectedRevision:row.revision,rotationId:row.rotationId})">{{ t('extension.actions.MACHINE_ACTIVATE') }}</Button><Button variant="ghost" size="sm" :disabled="writing" @click="deliver('machine',row.id)">{{ t('extension.receiveSecret') }}</Button></template>
            </div></template>
          </DataTable>
          
        </TableWorkspace>
      
    </template></TableWorkspace>
    <EditorDialog v-model="dialog" :title="action ? t('extension.actions.' + action) : t('extension.title')" :busy="writing" :dirty="true" :save-label="t('extension.review')" wide @save="review">
      <ErrorNotice :message="error" /><fieldset :disabled="writing" class="space-y-4">
        <div v-if="['GROUP_CREATE','PLUGIN_CREATE'].includes(action)" class="space-y-2"><Label for="new-identifier">{{ t('extension.identifier') }}</Label><Input v-if="action === 'GROUP_CREATE'" id="new-identifier" v-model="targetGroup" /><Input v-else id="new-identifier" v-model="targetPlugin" /></div>
        <div v-for="f in fields" :key="f.key" class="space-y-2"><Label :for="'field-' + f.key">{{ t('extension.fields.' + f.key) }}</Label><PasswordInput v-if="f.type === 'password'" :id="'field-' + f.key" :model-value="String(values[f.key] || '')" autocomplete="off" @update:model-value="values[f.key]=$event" /><Textarea v-else-if="f.type === 'json'" :id="'field-' + f.key" :model-value="String(values[f.key])" class="min-h-52 font-mono" @update:model-value="values[f.key]=$event" /><input v-else-if="f.type === 'boolean'" :id="'field-' + f.key" type="checkbox" :checked="!!values[f.key]" class="ml-3 size-5" @change="values[f.key]=($event.target as HTMLInputElement).checked" /><Input v-else :id="'field-' + f.key" :type="f.type === 'number' ? 'number' : 'text'" :model-value="String(values[f.key] ?? '')" @update:model-value="values[f.key]=$event" /></div>
        <template v-if="action === 'UPLOAD'"><Label for="release-files">{{ t('extension.files') }}</Label><Input id="release-files" type="file" accept=".jar,.java" multiple @change="chooseFiles" /><p class="text-xs text-muted-foreground">{{ t('extension.fileHint') }}</p><ul class="space-y-3"><li v-for="file in fileIntents" :key="file.name" class="rounded-lg border p-3 text-sm"><p>{{ file.name }} {{ t('common.detailSeparator') }} {{ file.bytes }} {{ t('extension.bytes') }}</p><code class="break-all text-xs">{{ file.sha256 }}</code></li></ul><template v-if="files.some(f=>f.name.endsWith('.java'))"><Label for="source-manifest">{{ t('extension.manifest') }}</Label><Textarea id="source-manifest" v-model="manifest" class="min-h-40 font-mono" /></template></template>
        <template v-if="releaseActions.includes(action)"><Label for="deployment-id">{{ t('extension.fields.deploymentId') }}</Label><Input id="deployment-id" v-model="releaseForm.id" :disabled="!!selectedDeployment" />
          <template v-if="['PUBLISH','CANARY'].includes(action)"><Label for="release-artifact">{{ t('extension.releaseArtifact') }}</Label><select id="release-artifact" :value="action === 'CANARY' ? releaseForm.candidate : releaseForm.stable" class="h-10 w-full rounded-lg border bg-card px-2" @change="action === 'CANARY' ? releaseForm.candidate=($event.target as HTMLSelectElement).value : releaseForm.stable=($event.target as HTMLSelectElement).value"><option value="">{{ t('extension.select') }}</option><option v-for="a in releaseArtifacts.filter(a=>['VERIFIED','BLOCKED'].includes(a.state))" :key="a.id" :value="a.id">{{ a.manifest?.version }} {{ t('common.detailSeparator') }} {{ a.sha256.slice(0,12) }}</option></select></template>
          <template v-if="action === 'PUBLISH'"><div v-for="choice in companionChoices" :key="choice.plugin.id" class="space-y-2"><Label :for="'companion-'+choice.plugin.id">{{ t('extension.companion') }} {{ choice.plugin.name }}</Label><select :id="'companion-'+choice.plugin.id" v-model="companionSelection[choice.plugin.id]" class="h-10 w-full rounded-lg border bg-card px-2"><option value="">{{ t('extension.notIncluded') }}</option><option v-for="a in choice.artifacts.filter(a=>['VERIFIED','BLOCKED'].includes(a.state))" :key="a.id" :value="a.id">{{ a.manifest?.version }} {{ t('common.detailSeparator') }} {{ a.sha256.slice(0,12) }}</option></select></div></template><template v-if="action === 'CANARY'"><Label for="canary-percent">{{ t('extension.canaryPercent') }}</Label><Input id="canary-percent" v-model.number="releaseForm.percent" type="number" min="0" max="100" step="0.01" /></template>
          <template v-if="action === 'ROLLBACK'"><Label for="historical-bundle">{{ t('extension.historicalBundle') }}</Label><select id="historical-bundle" v-model="values.historicalBundle" class="h-10 w-full rounded-lg border bg-card px-2"><option v-for="h in releaseHistory" :key="h.revision" :value="String(h.revision)">{{ h.revision }} {{ t('common.detailSeparator') }} {{ h.stable.id }}</option></select></template>
          <template v-if="action === 'CANCEL_RELEASE'"><Label for="cancel-reason">{{ t('extension.fields.reason') }}</Label><Input id="cancel-reason" v-model="releaseForm.reason" /></template>
          <template v-else><Label for="release-nodes">{{ t('extension.fields.targetNodes') }}</Label><Input id="release-nodes" v-model="releaseForm.nodes" :disabled="['CANARY','PROMOTE'].includes(action)" /><p class="text-xs text-muted-foreground">{{ t('extension.releaseHint') }}</p></template>
        </template>
        <p v-if="pendingBody" class="break-all rounded-lg border p-3 text-sm">{{ pendingBody }}</p>
      </fieldset>
    </EditorDialog>
    <EditorDialog v-model="confirmOpen" :title="t('extension.authorize')" :description="t('extension.authorizeHint')" :busy="writing" :save-label="t('extension.authorizeExecute')" @save="commit"><ErrorNotice :message="error" /><div v-if="prepared" class="space-y-4"><dl class="space-y-2 rounded-xl border bg-muted/40 p-3 text-sm"><div>{{ t('extension.fields.action') }}{{ t('extension.colon') }} {{ t('extension.actions.'+prepared.intent.action) }}</div><div>{{ t('extension.fields.environment') }}{{ t('extension.colon') }} {{ prepared.intent.environment }}</div><div>{{ t('extension.group') }}{{ t('extension.colon') }} {{ prepared.intent.groupId }}</div><div>{{ t('extension.plugin') }}{{ t('extension.colon') }} {{ prepared.intent.pluginId }}</div><code class="block break-all text-xs">{{ prepared.intent.parametersSha256 }}</code></dl><pre class="max-h-52 overflow-auto whitespace-pre-wrap break-all rounded-lg border p-3 text-xs">{{ reviewSummary }}</pre><ul class="space-y-2"><li v-for="file in prepared.intent.files" :key="file.name" class="text-xs"><span>{{ file.name }}</span><code class="block break-all">{{ file.sha256 }}</code></li></ul><Label for="operation-password">{{ t('extension.fields.operationPassword') }}</Label><PasswordInput id="operation-password" v-model="operationPassword" autocomplete="off" :disabled="writing" /><Label for="operation-otp">{{ t('extension.otp') }}</Label><Input id="operation-otp" v-model="otp" inputmode="numeric" maxlength="6" autocomplete="one-time-code" :disabled="writing" /><p class="text-xs text-muted-foreground">{{ t('extension.otpHint') }}</p></div></EditorDialog>
    <EditorDialog v-model="detailOpen" :title="t('common.details')"><pre class="max-h-96 overflow-auto whitespace-pre-wrap break-all rounded-lg bg-muted p-3 text-xs">{{ detail }}</pre><template #footer><Button variant="outline" @click="detailOpen=false">{{ t('extension.close') }}</Button></template></EditorDialog>
    <EditorDialog :model-value="!!secretResult" :title="t('extension.secretOnce')" @update:model-value="secretResult='' "><pre class="overflow-auto whitespace-pre-wrap break-all rounded-lg border p-3 text-xs">{{ secretResult }}</pre><template #footer><Button @click="secretResult=''">{{ t('extension.close') }}</Button></template></EditorDialog>
  
</template>
