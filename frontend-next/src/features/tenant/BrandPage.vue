<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { getToken } from '@/lib/token'
import { useAuth } from '@/stores/auth'
import { useBranding } from '@/stores/branding'
import { usePreferences, skins } from '@/stores/preferences'
import { useSubmission } from '@/composables/useSubmission'
import OperationStatus from '@/components/business/OperationStatus.vue'
import { useUnsavedChanges } from '@/composables/useUnsavedChanges'
import { confirmAction } from '@/lib/feedback'
import { formatInstant } from '@/lib/time'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Input } from '@/components/ui/input'
import { Switch } from '@/components/ui/switch'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import PageHeader from '@/components/business/PageHeader.vue'
import PermissionButton from '@/components/business/PermissionButton.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import BrandField from './BrandField.vue'
import BrandAssetInput from './BrandAssetInput.vue'
import BrandPreviewPane from './BrandPreviewPane.vue'
import { assetSlots, brands, canonical, unknownWrite, type BrandContent, type BrandDraft, type BrandOperation, type BrandPreview, type BrandRevision, type LoginTemplate, type OverrideField, type OverrideMode } from './brands'

const auth = useAuth(), branding = useBranding(), preferences = usePreferences(), { t } = useI18n()
const submission = useSubmission()
submission.bind('tenant.brands.self')
const draft = ref<BrandDraft>(), form = ref<BrandContent>(), baseline = ref(''), templates = ref<LoginTemplate[]>([])
const history = ref<BrandRevision[]>([]), total = ref('0'), page = ref(1), tab = ref('identity')
const busy = ref(false), uploading = ref(false), previewBusy = ref(false), error = ref(''), message = ref(''), preview = ref<BrandPreview>()
const pending = ref<{ kind: 'save'; version: string; content: BrandContent } | { kind: 'operation'; operation: BrandOperation }>()
let generation = 0, previewAttempt = 0
const identity = computed(() => auth.user ? JSON.stringify([getToken(), auth.user.portal, auth.user.id, [...auth.user.permissions].sort()]) : '')
let tenantScope = branding.entry?.entry.tenantId
const dirty = computed(() => !!form.value && canonical(form.value) !== baseline.value)
const waiting = computed(() => busy.value || uploading.value || submission.busy.value || submission.querying.value)
const disabled = computed(() => waiting.value || !!pending.value || submission.unresolved.value || !auth.hasPermission('tenant:brand:edit') || !!auth.user?.locked)
const canRollback = computed(() => !!draft.value?.publishedRevisionId && !pending.value && !submission.unresolved.value && !waiting.value && !auth.user?.locked)
const canPublish = computed(() => !!draft.value && draft.value.version !== '0' && !dirty.value && !pending.value && !submission.unresolved.value && !waiting.value && !auth.user?.locked)
useUnsavedChanges(computed(() => dirty.value || !!pending.value), waiting)
const publishedVersion = computed(() => history.value.find(value => value.id === draft.value?.publishedRevisionId)?.revision)
const sections = ['identity', 'login', 'legal', 'links', 'appearance', 'assets', 'history'] as const
const linkFields = ['website', 'help', 'support', 'privacy', 'agreement'] as const
function mode(field: OverrideField) { return form.value?.overrides[field] || 'OVERRIDE' }
function setMode(field: OverrideField, value: OverrideMode) { if (form.value && !disabled.value) form.value.overrides[field] = value }
function accept(value: BrandDraft) { draft.value = value; form.value = structuredClone(value.content); baseline.value = canonical(value.content) }
function valid(ticket: number) { return ticket === generation && !!auth.user && !auth.user.locked && auth.hasPermission('tenant:brand:view') }
function clear() { generation++; previewAttempt++; draft.value = undefined; form.value = undefined; baseline.value = ''; history.value = []; templates.value = []; pending.value = undefined; preview.value = undefined; busy.value = false; uploading.value = false; previewBusy.value = false; error.value = ''; message.value = '' }
watch(identity, clear, { flush: 'sync' })
watch(() => branding.entry?.entry.tenantId, value => { if (value) { if (tenantScope && tenantScope !== value) clear(); tenantScope = value } }, { flush: 'sync' })
watch(() => auth.hasPermission('tenant:brand:view'), allowed => { if (!allowed) clear() })
watch(() => auth.user?.locked, locked => { if (locked) { generation++; previewAttempt++; preview.value = undefined; busy.value = false; uploading.value = false; previewBusy.value = false } })
watch(() => form.value && canonical(form.value), () => { previewAttempt++; preview.value = undefined; previewBusy.value = false })
onUnmounted(clear)
async function load() {
  if (waiting.value || auth.user?.locked || !auth.hasPermission('tenant:brand:view')) return
  if (dirty.value && !await confirmAction(t('tenant.loadRemoteConfirm'), t('common.confirm'))) return
  const ticket = ++generation; busy.value = true; error.value = ''; message.value = ''
  try {
    const [remote, catalog, revisions] = await Promise.all([brands.draft(), brands.templates(), brands.history(1)])
    if (!valid(ticket)) return
    accept(remote); templates.value = catalog; history.value = revisions.items; total.value = revisions.total; page.value = 1
  } catch (cause) { if (valid(ticket)) error.value = (cause as Error).message }
  finally { if (ticket === generation) busy.value = false }
}
async function save() {
  if (disabled.value || !form.value || !draft.value || !dirty.value) return
  const input = JSON.parse(JSON.stringify(form.value)) as BrandContent, version = draft.value.version, ticket = generation
  error.value = ''; message.value = ''
  const result = await submission.run({ operation: 'brands.save', resource: 'tenant.brands.self', protocol: 'LOCAL' }, () => {
    pending.value = { kind: 'save', version, content: input }
    return options => brands.save(version, input, options)
  })
  if (!valid(ticket)) return
  if (result.kind === 'success' || result.kind === 'completed') {
    pending.value = undefined
    busy.value = true
    try { const remote = await brands.draft(); if (valid(ticket)) { accept(remote); message.value = t('tenant.saved') } }
    catch (cause) { if (valid(ticket)) error.value = (cause as Error).message }
    finally { if (ticket === generation) busy.value = false }
  } else { error.value = submission.error.value; if (!submission.unresolved.value) pending.value = undefined }
}
async function renderPreview() {
  if (!form.value || !draft.value || waiting.value || pending.value || auth.user?.locked) return
  const ticket = generation, attempt = ++previewAttempt, input = JSON.parse(JSON.stringify(form.value)) as BrandContent
  previewBusy.value = true; error.value = ''
  try { const result = await brands.preview(draft.value.version, input); if (valid(ticket) && attempt === previewAttempt) preview.value = result }
  catch (cause) { if (valid(ticket) && attempt === previewAttempt) error.value = (cause as Error).message }
  finally { if (ticket === generation && attempt === previewAttempt) previewBusy.value = false }
}
async function refreshPublication(ticket: number) {
  const [remote, revisions] = await Promise.all([brands.draft(), brands.history(1)])
  if (!valid(ticket)) return
  draft.value = draft.value ? { ...draft.value, publishedRevisionId: remote.publishedRevisionId } : remote
  history.value = revisions.items; total.value = revisions.total; page.value = 1
  void branding.load().then(ok => { if (ok && valid(ticket)) return auth.load(false) }).catch(cause => { if (valid(ticket)) error.value = (cause as Error).message })
}
async function execute(operation: BrandOperation) {
  const ticket = generation; error.value = ''; message.value = ''
  const result = await submission.run({ operation: 'brands.' + operation.kind, resource: 'tenant.brands.self', protocol: 'NATIVE' }, async () => {
    if (!await confirmAction(t(operation.kind === 'publish' ? 'tenant.publishConfirm' : 'tenant.rollbackConfirm'), t('common.confirm'))) return
    if (!valid(ticket) || !auth.hasPermission('tenant:brand:' + operation.kind)) return
    return async options => {
      const intent = { ...operation, operationId: options.idempotencyKey }
      pending.value = { kind: 'operation', operation: intent }
      const receipt = await brands.execute(intent, options)
      if (receipt.operationId !== intent.operationId) throw new Error(t('tenant.notObserved'))
      return receipt
    }
  }, { success: () => 'completed', failure: cause => unknownWrite(cause) ? 'unknown' : 'rejected' })
  if (!valid(ticket)) return
  if (result.kind === 'success') {
    pending.value = undefined; message.value = t(operation.kind === 'publish' ? 'tenant.publishedSuccess' : 'tenant.rolledBack')
    busy.value = true
    try { await refreshPublication(ticket) } catch (cause) { if (valid(ticket)) error.value = (cause as Error).message }
    finally { if (ticket === generation) busy.value = false }
  } else { error.value = submission.error.value; if (!submission.unresolved.value) pending.value = undefined }
}
async function publish() {
  if (!canPublish.value || !auth.hasPermission('tenant:brand:publish')) return
  await execute({ kind: 'publish', operationId: '', expectedDraftVersion: draft.value!.version, expectedPublishedRevisionId: draft.value!.publishedRevisionId })
}
async function rollback(revision: BrandRevision) {
  if (!canRollback.value || !draft.value?.publishedRevisionId || !auth.hasPermission('tenant:brand:rollback')) return
  await execute({ kind: 'rollback', operationId: '', sourceRevisionId: revision.id, expectedPublishedRevisionId: draft.value.publishedRevisionId })
}
async function checkResult() {
  if (!submission.unresolved.value || waiting.value || auth.user?.locked) return
  const ticket = generation; error.value = ''; message.value = ''
  try {
    if (submission.record.value?.protocol === 'LOCAL') {
      const state = await submission.check()
      if (!valid(ticket)) return
      if (state === 'SUCCEEDED') {
        pending.value = undefined; busy.value = true
        const remote = await brands.draft(); if (valid(ticket)) { accept(remote); message.value = t('tenant.savedMatched') }
      } else if (state === 'FAILED_FINAL') { pending.value = undefined; error.value = submission.error.value }
      else message.value = t('tenant.notObserved')
    } else {
      const key = submission.record.value!.key
      const result = await submission.checkNative(row => brands.operation(row.key), value => {
        if (value.status === 'COMMITTED' && value.receipt?.operationId === key) return { id: key, state: 'SUCCEEDED' }
        return { id: key, state: 'UNKNOWN' }
      })
      if (!valid(ticket)) return
      if (result?.state === 'SUCCEEDED') { pending.value = undefined; message.value = t('tenant.operationConfirmed'); busy.value = true; await refreshPublication(ticket) }
      else { message.value = t('tenant.notObserved'); error.value = submission.error.value }
    }
  } catch (cause) { if (valid(ticket)) error.value = (cause as Error).message }
  finally { if (ticket === generation) busy.value = false }
}
async function historyPage(target: number) {
  if (waiting.value || target < 1 || auth.user?.locked) return
  const ticket = generation; busy.value = true
  try { const result = await brands.history(target); if (valid(ticket)) { history.value = result.items; total.value = result.total; page.value = target } }
  catch (cause) { if (valid(ticket)) error.value = (cause as Error).message }
  finally { if (ticket === generation) busy.value = false }
}
onMounted(load)
</script>
<template>
  <div class="space-y-5">
    <PageHeader :title="t('tenant.title')" :description="t('tenant.description')"><Button variant="outline" :disabled="waiting || !!pending || !!auth.user?.locked" @click="load">{{ t('tenant.loadRemote') }}</Button><Button variant="outline" :disabled="!form || waiting || previewBusy || !!pending || !!auth.user?.locked" @click="renderPreview">{{ t(previewBusy ? 'common.loading' : 'tenant.preview') }}</Button><PermissionButton permission="tenant:brand:edit" :disabled="disabled || !dirty" @click="save">{{ t('tenant.saveDraft') }}</PermissionButton><PermissionButton permission="tenant:brand:publish" :disabled="!canPublish" @click="publish">{{ t('tenant.publish') }}</PermissionButton></PageHeader>
    <p v-if="auth.user?.locked" role="status">{{ t('tenant.locked') }}</p>
    <template v-else>
      <ErrorNotice :message="error" /><p v-if="message" role="status" class="text-sm text-muted-foreground">{{ message }}</p>
      <OperationStatus :slow="submission.slow.value" /><div v-if="submission.unresolved.value" class="space-y-2 rounded-xl border border-amber-500/50 bg-card p-4" role="status"><p>{{ t('tenant.unknown') }}</p><p v-if="submission.record.value?.protocol === 'NATIVE'" class="break-all text-xs">{{ t('tenant.operationLabel', { id: submission.record.value?.key }) }}</p><Button variant="outline" :disabled="waiting" @click="checkResult">{{ t('tenant.checkResult') }}</Button></div>
      <div v-if="draft" class="flex flex-wrap gap-4 text-xs text-muted-foreground"><span>{{ t('tenant.draftVersion', { version: draft.version }) }}</span><span>{{ publishedVersion ? t('tenant.published', { version: publishedVersion }) : t(draft.publishedRevisionId ? 'tenant.publishedAvailable' : 'tenant.unpublished') }}</span><span v-if="dirty">{{ t('tenant.dirty') }}</span></div>
      <Tabs v-if="form" v-model="tab" class="min-w-0"><TabsList class="flex h-auto flex-wrap justify-start"><TabsTrigger v-for="section in sections" :key="section" :value="section">{{ t('tenant.' + section) }}</TabsTrigger></TabsList>
        <form v-show="tab !== 'history'" @submit.prevent="save"><fieldset :disabled="disabled" class="mt-4 min-w-0 rounded-xl border bg-card p-5">
          <TabsContent value="identity" class="grid gap-5 sm:grid-cols-2"><BrandField id="brand-name" v-model="form.identity.systemName" :label="t('tenant.systemName')" :maxlength="128" required :disabled="disabled" /><BrandField id="brand-short" v-model="form.identity.shortName" :label="t('tenant.shortName')" field="SHORT_NAME" :mode="mode('SHORT_NAME')" :maxlength="40" :disabled="disabled" @mode="setMode('SHORT_NAME', $event)" /><BrandField id="brand-english" v-model="form.identity.englishName" :label="t('tenant.englishName')" field="ENGLISH_NAME" :mode="mode('ENGLISH_NAME')" :maxlength="128" :disabled="disabled" @mode="setMode('ENGLISH_NAME', $event)" /></TabsContent>
          <TabsContent value="login" class="space-y-6"><div class="grid gap-3 sm:grid-cols-3"><button v-for="item in templates" :key="item.id" type="button" class="space-y-3 rounded-xl border p-4 text-left disabled:opacity-50" :class="form.login.templateId === item.id ? 'border-primary ring-1 ring-primary' : ''" :disabled="disabled || !item.enabled" :aria-pressed="form.login.templateId === item.id" @click="form.login.templateId = item.id"><span class="template-mini" :class="item.id" aria-hidden="true"><i /><b /></span><span class="block text-sm font-medium">{{ t(item.nameKey) }}</span></button></div><div class="grid gap-5 sm:grid-cols-2"><BrandField id="brand-title-zh" v-model="form.login.title.zhCN" :label="t('tenant.welcomeTitle') + ' · ' + t('tenant.zhCN')" field="WELCOME_TITLE" :mode="mode('WELCOME_TITLE')" :maxlength="120" :disabled="disabled" @mode="setMode('WELCOME_TITLE', $event)" /><BrandField id="brand-title-en" v-model="form.login.title.enUS" :label="t('tenant.welcomeTitle') + ' · English'" :disabled="disabled || mode('WELCOME_TITLE') !== 'OVERRIDE'" :maxlength="120" /><BrandField id="brand-description-zh" v-model="form.login.description.zhCN" :label="t('tenant.welcomeDescription') + ' · ' + t('tenant.zhCN')" field="WELCOME_DESCRIPTION" :mode="mode('WELCOME_DESCRIPTION')" :maxlength="500" :disabled="disabled" @mode="setMode('WELCOME_DESCRIPTION', $event)" /><BrandField id="brand-description-en" v-model="form.login.description.enUS" :label="t('tenant.welcomeDescription') + ' · English'" :disabled="disabled || mode('WELCOME_DESCRIPTION') !== 'OVERRIDE'" :maxlength="500" /></div><div class="flex items-center gap-3"><Switch id="brand-blur" v-model="form.login.backgroundBlur" :disabled="disabled" /><Label for="brand-blur">{{ t('tenant.backgroundBlur') }}</Label></div></TabsContent>
          <TabsContent value="legal" class="space-y-5"><p class="text-sm text-muted-foreground">{{ t('tenant.legalHint') }}</p><div class="grid gap-5 sm:grid-cols-2"><BrandField id="brand-copyright" v-model="form.legal.copyrightOwner" :label="t('tenant.copyrightOwner')" field="COPYRIGHT" :mode="mode('COPYRIGHT')" :maxlength="128" :disabled="disabled" @mode="setMode('COPYRIGHT', $event)" /><div class="space-y-2"><Label for="brand-year">{{ t('tenant.copyrightStartYear') }}</Label><Input id="brand-year" type="number" min="1900" max="9999" :model-value="form.legal.copyrightStartYear ?? ''" :disabled="disabled || mode('COPYRIGHT') !== 'OVERRIDE'" @update:model-value="form.legal.copyrightStartYear = $event === '' ? null : Number($event)" /></div><BrandField id="brand-copyright-url" v-model="form.legal.copyrightUrl" :label="t('tenant.copyrightUrl')" :maxlength="2048" :disabled="disabled || mode('COPYRIGHT') !== 'OVERRIDE'" /><BrandField id="brand-icp" v-model="form.legal.icpRecord" :label="t('tenant.icpRecord')" field="ICP_RECORD" :mode="mode('ICP_RECORD')" :maxlength="100" :disabled="disabled" @mode="setMode('ICP_RECORD', $event)" /><BrandField id="brand-icp-url" v-model="form.legal.icpUrl" :label="t('tenant.icpUrl')" :maxlength="2048" :disabled="disabled || mode('ICP_RECORD') !== 'OVERRIDE'" /><BrandField id="brand-police" v-model="form.legal.policeRecord" :label="t('tenant.policeRecord')" field="POLICE_RECORD" :mode="mode('POLICE_RECORD')" :maxlength="100" :disabled="disabled" @mode="setMode('POLICE_RECORD', $event)" /><BrandField id="brand-police-url" v-model="form.legal.policeUrl" :label="t('tenant.policeUrl')" :maxlength="2048" :disabled="disabled || mode('POLICE_RECORD') !== 'OVERRIDE'" /></div></TabsContent>
          <TabsContent value="links" class="grid gap-5 sm:grid-cols-2"><BrandField v-for="field in linkFields" :id="'brand-link-' + field" :key="field" v-model="form.links[field]" :label="t('tenant.' + field)" :field="field.toUpperCase() as OverrideField" :mode="mode(field.toUpperCase() as OverrideField)" :maxlength="2048" :disabled="disabled" @mode="setMode(field.toUpperCase() as OverrideField, $event)" /></TabsContent>
          <TabsContent value="appearance" class="space-y-5"><BrandField id="brand-color" v-model="form.appearance.primaryColor" :label="t('tenant.primaryColor')" field="PRIMARY_COLOR" :mode="mode('PRIMARY_COLOR')" :maxlength="7" :disabled="disabled" @mode="setMode('PRIMARY_COLOR', $event)" /><p class="text-xs text-muted-foreground">{{ t('tenant.primaryColorHint') }}</p><div class="space-y-2"><Label for="brand-skin">{{ t('tenant.defaultSkin') }}</Label><select id="brand-skin" v-model="form.appearance.defaultSkin" class="block w-full rounded-md border bg-background p-2" :disabled="disabled"><option v-for="skin in skins" :key="skin" :value="skin">{{ t('common.skins.' + skin) }}</option></select></div><div class="flex items-center gap-3"><Switch id="brand-personal-skin" v-model="form.appearance.allowPersonalSkin" :disabled="disabled" /><Label for="brand-personal-skin">{{ t('tenant.allowPersonalSkin') }}</Label></div></TabsContent>
          <TabsContent value="assets" class="space-y-5"><p class="text-sm text-muted-foreground">{{ t('tenant.uploadHint') }}</p><p v-if="draft?.legacyBackgroundUrl" class="text-sm text-muted-foreground">{{ t('tenant.legacyBackground') }}</p><div v-for="slot in assetSlots" :key="slot" class="flex flex-wrap items-center gap-3 rounded-lg border p-4"><div class="min-w-0 flex-1"><Label :for="'brand-asset-mode-' + slot">{{ t('tenant.asset.' + slot) }}</Label><p class="mt-2 break-all text-xs text-muted-foreground">{{ form.assets[slot] ? t('tenant.assetSelected') : t('tenant.noAsset') }}</p></div><select :id="'brand-asset-mode-' + slot" :value="mode(slot)" class="rounded-md border bg-background p-2 text-sm" :disabled="disabled" @change="setMode(slot, ($event.target as HTMLSelectElement).value as OverrideMode)"><option v-for="choice in ['OVERRIDE', 'INHERIT', 'HIDE']" :key="choice" :value="choice">{{ t('tenant.mode.' + choice) }}</option></select><BrandAssetInput :label="t('tenant.asset.' + slot)" :disabled="disabled || mode(slot) !== 'OVERRIDE'" @uploaded="form!.assets[slot] = $event" @busy="uploading = $event" @error="error = $event" /><Button type="button" variant="ghost" :disabled="disabled || !form.assets[slot]" @click="delete form.assets[slot]">{{ t('tenant.removeAsset') }}</Button></div></TabsContent>
        </fieldset></form>
        <TabsContent value="history" class="space-y-3"><p v-if="!history.length" class="p-5 text-sm text-muted-foreground">{{ t('tenant.historyEmpty') }}</p><article v-for="revision in history" :key="revision.id" class="flex flex-wrap items-center justify-between gap-4 rounded-xl border bg-card p-4"><div><p class="font-medium">{{ t('tenant.revisionCaption', { version: revision.revision, name: revision.content.identity.systemName }) }}</p><p class="mt-1 text-xs text-muted-foreground">{{ formatInstant(revision.createdAt, preferences.timeZone, preferences.locale) }}</p><p v-if="revision.id === draft?.publishedRevisionId" class="mt-1 text-xs text-primary">{{ t('tenant.viewPublished') }}</p></div><PermissionButton permission="tenant:brand:rollback" variant="outline" :disabled="!canRollback || revision.id === draft?.publishedRevisionId" @click="rollback(revision)">{{ t('tenant.rollback') }}</PermissionButton></article><div class="flex items-center justify-end gap-3"><Button variant="outline" :disabled="waiting || page <= 1" @click="historyPage(page - 1)">{{ t('common.previousPage') }}</Button><span class="text-sm">{{ page }}</span><Button variant="outline" :disabled="waiting || BigInt(page * 10) >= BigInt(total)" @click="historyPage(page + 1)">{{ t('common.nextPage') }}</Button></div></TabsContent>
      </Tabs>
      <BrandPreviewPane :preview="preview" />
    </template>
  </div>
</template>
<style scoped>
.template-mini{height:5rem;display:flex;align-items:center;justify-content:space-around;gap:.5rem;border-radius:.5rem;background:var(--muted);padding:.75rem}.template-mini i{display:block;width:40%;height:70%;border-radius:.3rem;background:color-mix(in srgb,var(--primary) 20%,var(--card))}.template-mini b{display:block;width:40%;height:75%;border:1px solid var(--border);border-radius:.3rem;background:var(--card)}.template-mini.login2{justify-content:center}.template-mini.login2 i{display:none}.template-mini.login3{background:#203749}
</style>
