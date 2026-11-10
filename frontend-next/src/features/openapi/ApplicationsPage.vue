<script setup lang="ts">
import { useRequestGate } from '@/composables/useRequestGate'
const loadGate = useRequestGate()

import { computed } from 'vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Switch } from '@/components/ui/switch'
import PageHeader from '@/components/business/PageHeader.vue'
import FilterBar from '@/components/business/FilterBar.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import DataTable from '@/components/business/DataTable.vue'
import EditorDialog from '@/components/business/EditorDialog.vue'
import EntitySelect from '@/components/business/EntitySelect.vue'
import PasswordInput from '@/components/business/PasswordInput.vue'

import { reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { confirmOrCancel, notify } from '@/lib/feedback'
import { get, post, put, remove, type PageResult } from '@/api/http'
import { useAuth } from '@/stores/auth'
import { usePreferences } from '@/stores/preferences'
import { formatInstant } from '@/lib/time'
interface Application {
  id: string
  name: string
  type: number
  description: string | null
  appId: string
  enabled: boolean
  signatureRequired: boolean
  targetType: string | null
  targetId: string | null
  targetName: string | null
  createdAt: string
}
interface Credentials {
  id: string
  appId: string
  appSecret: string
}
const { t, locale } = useI18n(),
  auth = useAuth(),
  preferences = usePreferences(),
  route = useRoute(),
  rows = ref<Application[]>([]),
  types = ref<{ value: string; label: string }[]>([])
const page = ref(1),
  total = ref(0),
  keyword = ref(''),
  type = ref<number>(),
  enabled = ref<boolean>(),
  busy = ref(false),
  error = ref(''),
  dialog = ref(false),
  editingId = ref(''),
  saving = ref(false),
  credentials = ref<Credentials>(),
  detail = ref<Application>()
const form = reactive({
    name: '',
    type: 1,
    description: '',
    enabled: false,
    signatureRequired: true,
  }),
  target = reactive({ type: '', id: '', name: '' }),
  targetId = ref('')
function input(row: Application) {
  return {
    name: row.name,
    type: row.type,
    description: row.description || '',
    enabled: row.enabled,
    signatureRequired: row.signatureRequired,
  }
}
function typeName(value: number) {
  return types.value.find((type) => Number(type.value) === value)?.label || String(value)
}
async function load() {
  const isCurrent = loadGate()
  busy.value = true
  error.value = ''
  try {
    const result = await get<PageResult<Application>>('/openapi/apps', {
      page: page.value,
      size: 20,
      keyword: keyword.value,
      type: type.value,
      enabled: enabled.value,
    })
    if (!isCurrent()) return
    rows.value = result.items
    total.value = Number(result.total)
  } catch (e) {
    if (!isCurrent()) return
    error.value = (e as Error).message
  } finally {
    if (isCurrent()) { busy.value = false }
  }
}
function edit(row?: Application) {
  Object.assign(
    form,
    row
      ? input(row)
      : { name: '', type: 1, description: '', enabled: false, signatureRequired: true },
  )
  editingId.value = row?.id || ''
  error.value = ''
  dialog.value = true
}
async function save() {
  if (saving.value) return
  if (!form.name.trim()) {
    error.value = t('common.required')
    return
  }
  saving.value = true
  try {
    if (editingId.value) await put('/openapi/apps/' + editingId.value, form)
    else credentials.value = await post('/openapi/apps', form)
    dialog.value = false
    await load()
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    saving.value = false
  }
}
const writing = ref(new Set<string>())
function beginWrite(id: string) { if (writing.value.has(id)) return false; writing.value = new Set([...writing.value, id]); return true }
function endWrite(id: string) { const next = new Set(writing.value); next.delete(id); writing.value = next }
const targetOriginal = ref('')
watch(targetId, value => { if (value) targetOriginal.value = JSON.stringify(target) })
async function toggle(
  row: Application,
  key: 'enabled' | 'signatureRequired',
  value: boolean | string | number,
) {
  if (!beginWrite(row.id)) return
  try {
    await put('/openapi/apps/' + row.id, { ...input(row), [key]: Boolean(value) })
    await load()
  } catch (e) {
    error.value = (e as Error).message
  } finally { endWrite(row.id) }
}
async function rotate(row: Application) {
  if (!beginWrite(row.id)) return
  try {
    await confirmOrCancel(t('openapi.rotateConfirm'), t('common.confirm'), { type: 'warning' })
    credentials.value = await post('/openapi/apps/' + row.id + '/rotate')
  } catch (e) {
    if (e instanceof Error) error.value = e.message
  } finally { endWrite(row.id) }
}
async function erase(row: Application) {
  if (!beginWrite(row.id)) return
  try {
    await confirmOrCancel(t('common.deleteConfirm'), t('common.confirm'), { type: 'warning' })
    await remove('/openapi/apps/' + row.id)
    await load()
  } catch (e) {
    if (e instanceof Error) error.value = e.message
  } finally { endWrite(row.id) }
}
function link(row: Application) {
  Object.assign(target, {
    type: row.targetType || '',
    id: row.targetId || '',
    name: row.targetName || '',
  })
  targetId.value = row.id
}
async function saveTarget() {
  if (saving.value) return
  if (!target.type.trim() || !target.id.trim() || !target.name.trim()) {
    error.value = t('common.required')
    return
  }
  saving.value = true
  try {
    await put('/openapi/apps/' + targetId.value + '/target', target)
    targetId.value = ''
    await load()
  } catch (e) {
    error.value = (e as Error).message
  } finally { saving.value = false }
}
async function unlink(row: Application) {
  if (!beginWrite(row.id)) return
  try {
    await confirmOrCancel(t('openapi.unlinkConfirm'), t('common.confirm'))
    await remove('/openapi/apps/' + row.id + '/target')
    await load()
  } catch (e) {
    if (e instanceof Error) error.value = e.message
  } finally { endWrite(row.id) }
}
async function copySecret() {
  try {
    if (credentials.value) await navigator.clipboard.writeText(credentials.value.appSecret)
    notify(t('openapi.copied'))
  } catch {
    error.value = t('openapi.copyFailed')
  }
}
function search() {
  page.value = 1
  void load()
}
watch(
  locale,
  async () => {
    try {
      types.value = await get('/openapi/apps/types')
    } catch (e) {
      error.value = (e as Error).message
    }
    search()
  },
  { immediate: true },
)
watch(
  () => route.query.focus,
  async (id) => {
    if (typeof id === 'string')
      try {
        detail.value = await get('/openapi/apps/' + encodeURIComponent(id))
      } catch (e) {
        error.value = (e as Error).message
      }
  },
  { immediate: true },
)

const detailOpen = computed({ get:()=>!!detail.value,set:value=>{if(!value)detail.value=undefined} })
const credentialsOpen = computed({ get:()=>!!credentials.value,set:value=>{if(!value)credentials.value=undefined} })
const targetOpen = computed({ get:()=>!!targetId.value,set:value=>{if(!value)targetId.value=''} })
const original = ref('')
watch(dialog, value=>{if(value) original.value=JSON.stringify(form)})
const dirty = computed(()=>JSON.stringify(form)!==original.value)
const columns = computed(()=>[{key:'name',title:t('common.name'),width:180},{key:'type',title:t('openapi.type'),width:160},{key:'appId',title:t('openapi.appId'),width:280},{key:'enabled',title:t('common.enabled')},{key:'signatureRequired',title:t('openapi.signature')},{key:'targetName',title:t('openapi.target'),width:180},{key:'createdAt',title:t('common.createdAt'),width:190},{key:'actions',title:t('common.actions'),width:330}])
const enabledOptions = computed(()=>[{value:'true',label:t('common.enabled')},{value:'false',label:t('common.disabled')}])

</script>
<template>
  <PageHeader :title="t('openapi.apps')"><Button v-permission="'openapi:app:create'" @click="edit()">{{ t('openapi.create') }}</Button></PageHeader><FilterBar :busy="busy" @search="search"><Input v-model="keyword" :placeholder="t('common.search')" :aria-label="t('common.search')" /><EntitySelect :model-value="type == null ? null : String(type)" :options="types" :placeholder="t('openapi.type')" class="w-full sm:w-48" @update:model-value="type = $event == null ? undefined : Number($event)" /><EntitySelect :model-value="enabled == null ? null : String(enabled)" :options="enabledOptions" :placeholder="t('common.status')" class="w-full sm:w-40" @update:model-value="enabled = $event == null ? undefined : $event === 'true'" /></FilterBar><ErrorNotice :message="error" />
  <DataTable :rows="rows" :columns="columns" :loading="busy" :page="page" :total="total" @update:page="page = $event; load()"><template #name="{ row }"><button class="max-w-full truncate text-left font-medium text-primary hover:underline" @click="detail = row">{{ row.name }}</button></template><template #type="{ row }">{{ typeName(row.type) }}</template><template #appId="{ row }"><span class="block truncate font-mono text-xs" :title="row.appId">{{ row.appId }}</span></template><template #enabled="{ row }"><Switch :model-value="row.enabled" :disabled="!auth.hasPermission('openapi:app:update') || writing.has(row.id)" :aria-label="t('common.enabled') + ' · ' + row.name" @update:model-value="toggle(row, 'enabled', $event)" /></template><template #signatureRequired="{ row }"><Switch :model-value="row.signatureRequired" :disabled="!auth.hasPermission('openapi:app:update') || writing.has(row.id)" :aria-label="t('openapi.signature') + ' · ' + row.name" @update:model-value="toggle(row, 'signatureRequired', $event)" /></template><template #createdAt="{ row }">{{ formatInstant(row.createdAt, preferences.timeZone, locale) }}</template><template #actions="{ row }"><div class="flex flex-wrap gap-0.5"><Button v-permission="'openapi:app:update'" variant="ghost" size="sm" :disabled="writing.has(row.id)" @click="edit(row)">{{ t('common.edit') }}</Button><Button v-permission="'openapi:app:rotate'" variant="ghost" size="sm" :disabled="writing.has(row.id)" @click="rotate(row)">{{ t('openapi.rotate') }}</Button><Button v-permission="'openapi:app:link'" variant="ghost" size="sm" :disabled="writing.has(row.id)" @click="link(row)">{{ t('openapi.link') }}</Button><Button v-if="row.targetId" v-permission="'openapi:app:link'" variant="ghost" size="sm" :disabled="writing.has(row.id)" @click="unlink(row)">{{ t('openapi.unlink') }}</Button><Button v-permission="'openapi:app:delete'" variant="ghost" size="sm" class="text-destructive" @click="erase(row)">{{ t('common.remove') }}</Button></div></template></DataTable>
  <EditorDialog v-model="dialog" :title="t(editingId ? 'common.edit' : 'openapi.create')" :busy="saving" :dirty="dirty" @save="save"><ErrorNotice :message="error" /><form class="space-y-4" @submit.prevent="save"><div class="space-y-2"><Label for="app-name">{{ t('common.name') }}</Label><Input id="app-name" v-model="form.name" maxlength="128" required /></div><div class="space-y-2"><Label for="app-type">{{ t('openapi.type') }}</Label><EntitySelect id="app-type" :model-value="String(form.type)" :options="types" @update:model-value="form.type = Number($event || 1)" /></div><div class="space-y-2"><Label for="app-description">{{ t('common.description') }}</Label><Textarea id="app-description" v-model="form.description" maxlength="200" :rows="3" /></div><div class="flex items-center justify-between"><Label for="app-enabled">{{ t('common.enabled') }}</Label><Switch id="app-enabled" v-model="form.enabled" /></div><div class="flex items-center justify-between"><Label for="app-signature">{{ t('openapi.signature') }}</Label><Switch id="app-signature" v-model="form.signatureRequired" /></div><button type="submit" hidden tabindex="-1" aria-hidden="true">{{ t('common.save') }}</button></form></EditorDialog>
  <EditorDialog v-model="credentialsOpen" :title="t('openapi.credentials')"><div v-if="credentials" class="space-y-5"><p class="rounded-lg border border-primary/20 bg-accent p-3 text-sm text-accent-foreground">{{ t('openapi.secretOnce') }}</p><div class="space-y-2"><Label for="app-id">{{ t('openapi.appId') }}</Label><Input id="app-id" :model-value="credentials.appId" readonly class="font-mono text-xs" /></div><div class="space-y-2"><Label for="app-secret">{{ t('openapi.appSecret') }}</Label><PasswordInput id="app-secret" :model-value="credentials.appSecret" readonly autocomplete="off" /></div><Button variant="outline" @click="copySecret">{{ t('openapi.copySecret') }}</Button></div><template #footer><Button variant="outline" @click="credentialsOpen = false">{{ t('common.cancel') }}</Button></template></EditorDialog>
  <EditorDialog v-model="targetOpen" :title="t('openapi.target')" :busy="saving" :dirty="JSON.stringify(target) !== targetOriginal" @save="saveTarget"><ErrorNotice :message="error" /><div class="space-y-4"><div class="space-y-2"><Label for="target-type">{{ t('openapi.targetType') }}</Label><Input id="target-type" v-model="target.type" maxlength="64" /></div><div class="space-y-2"><Label for="target-id">{{ t('openapi.targetId') }}</Label><Input id="target-id" v-model="target.id" maxlength="128" /></div><div class="space-y-2"><Label for="target-name">{{ t('openapi.targetName') }}</Label><Input id="target-name" v-model="target.name" maxlength="128" /></div></div></EditorDialog>
  <EditorDialog v-model="detailOpen" :title="detail?.name || t('common.details')"><dl v-if="detail" class="space-y-5"><div v-for="entry in [{ label: t('openapi.appId'), value: detail.appId }, { label: t('openapi.type'), value: typeName(detail.type) }, { label: t('common.description'), value: detail.description }, { label: t('openapi.target'), value: detail.targetName }]" :key="entry.label"><dt class="text-xs text-muted-foreground">{{ entry.label }}</dt><dd class="mt-1 break-words text-sm">{{ entry.value || '—' }}</dd></div></dl><template #footer><Button variant="outline" @click="detailOpen = false">{{ t('common.cancel') }}</Button></template></EditorDialog>
</template>
