<script setup lang="ts">
import { validPassword } from '@/lib/password'
import { computed, ref, watch } from 'vue'
import { Field, useForm } from 'vee-validate'
import { formSchema } from '@/lib/form-schema'
import { z } from 'zod'
import { useI18n } from 'vue-i18n'
import { useAuth } from '@/stores/auth'
import { ApiError, get, post, put } from '@/api/http'
import { notify } from '@/lib/feedback'
import { useSubmission } from '@/composables/useSubmission'
import type { OperationSend } from '@/lib/operation-registry'
import OperationStatus from '@/components/business/OperationStatus.vue'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Button } from '@/components/ui/button'
import EditorDialog from '@/components/business/EditorDialog.vue'
import EntitySelect from '@/components/business/EntitySelect.vue'
import IconPicker from '@/components/business/IconPicker.vue'
import PasswordInput from '@/components/business/PasswordInput.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import { editorFor, payloadFor, tableTree, type Resource, type Row, type Option, type EditorState, type Field as ResourceField } from './resources'
const requiredMarker = '*'
function selection(value: unknown): string | string[] | null { return Array.isArray(value) ? value.map(String) : value == null ? null : String(value) }
const props = defineProps<{ resource: Resource; row?: Row; filters?: Record<string, unknown>; preset?: Record<string, unknown>; hierarchical?: boolean }>()
const open = defineModel<boolean>({ required: true }), emit = defineEmits<{ saved: [] }>()
const { t } = useI18n(), auth = useAuth(), submission = useSubmission(), saving = submission.busy, options = ref<Record<string, Option[]>>({}), error = ref(''), optionsBusy = ref(false)
const optionsFailed = ref(false)
let generation = 0
const fields = computed(() => props.resource.fields.filter(field => (!props.row || !field.createOnly) && (field.key !== 'parentId' || props.hierarchical !== false) && !(props.resource.id === 'users' && ((field.key === 'departmentId' && auth.user?.features?.departmentEnabled === false) || (field.key === 'postIds' && auth.user?.features?.postEnabled === false) || (['email', 'mobile', 'gender'].includes(field.key) && auth.user?.features?.extendedProfileEnabled === false)))))
const schema = computed(() => {
  const text: Record<string, z.ZodType> = {}, singles: Record<string, z.ZodTypeAny> = {}
  for (const field of fields.value) {
    if (['text', 'textarea', 'password', 'icon'].includes(field.kind)) {
      const patterns: Record<string, RegExp> = { paramKey: /^[a-z][a-zA-Z0-9.-]{0,127}$/, typeKey: /^[a-z][a-z0-9_]{0,63}$/ }
      if (props.resource.id === 'parameterGroups') patterns.groupKey = /^[a-z][a-z0-9_-]{0,63}$/
      if (field.kind === 'icon') { text[field.key] = z.string().max(64).optional(); continue }
      const pattern = patterns[field.key]
      text[field.key] = pattern ? z.string().trim().regex(pattern, t('common.invalidFormat')) : field.required ? z.string().trim().min(1, t('common.required')) : z.string().optional()
    }
    if (['tree', 'select'].includes(field.kind)) singles[field.key] = field.required ? z.string({ error: t('common.required') }).min(1, t('common.required')) : z.string().nullable().optional()
  }
  return formSchema<EditorState>(z.object({ text: z.object(text).passthrough(), singles: z.object(singles).passthrough(), numbers: z.record(z.string(), z.number()), flags: z.record(z.string(), z.boolean()), multiples: z.record(z.string(), z.array(z.string())) }))
})
const { values, resetForm, handleSubmit, meta, setFieldValue, setFieldError } = useForm<EditorState>({ initialValues: editorFor(props.resource, props.row), validationSchema: schema })
function normalizeBoolean() {
  if (props.resource.id === 'parameters' && values.singles?.valueType === 'BOOLEAN' && !props.row?.secret && !['true', 'false'].includes(values.text?.value || '')) setFieldValue('text.value', 'false')
}
watch(() => [open.value, values.singles?.valueType], ([isOpen]) => { if (isOpen) normalizeBoolean() }, { flush: 'post' })
function path(field: ResourceField) { return (field.kind === 'number' ? 'numbers.' : field.kind === 'flag' ? 'flags.' : ['multi', 'multiTree'].includes(field.kind) ? 'multiples.' : ['tree', 'select'].includes(field.kind) ? 'singles.' : 'text.') + field.key }
function label(field: ResourceField) { return t((props.resource.module || 'iam') + '.fields.' + field.key) }
function disabled(field: ResourceField) { return !!props.row?.builtin && (['permissionKey', 'typeKey', 'paramKey', 'groupKey', 'type', 'valueType'].includes(field.key) || (field.key === 'value' && props.resource.id === 'dictionaryItems') || (props.resource.id.startsWith('dictionary') && ['enabled', 'tree'].includes(field.key))) }
function choices(field: ResourceField) { const list = field.choices?.map(value => ({ value, label: t((props.resource.module || 'iam') + '.choices.' + value) })) || options.value[field.source || ''] || []; return field.key === 'parentId' ? [{ value: '0', label: t('common.root') }, ...list] : list }
function asOptions(rows: unknown[], source: string): Option[] { return rows.map(value => { const row = value as Record<string, unknown>; return { value: String(source === 'parameterGroups' ? row.groupKey : row.value ?? row.id), label: String(row.label ?? row.name ?? ''), disabled: row.disabled === true || row.enabled === false, children: Array.isArray(row.children) ? asOptions(row.children, source) : undefined } }) }
async function loadOptions() {
  const current = ++generation; optionsBusy.value = true; optionsFailed.value = false; error.value = ''
  const paths: Record<string, string> = { departments: '/iam/departments/tree', roleTree: '/iam/roles/tree', permissionTree: '/iam/permissions/tree', roles: '/iam/roles/options', posts: '/iam/posts/options', rootMenus: '/iam/permissions', parameterGroups: '/config/parameters/groups', dictionaryParents: '/config/dictionaries/items' }
  try { const result = await Promise.all([...new Set(fields.value.map(field => field.source).filter((source): source is string => !!source))].map(async source => {
    if (source === 'timeZones') return [source, ['UTC', ...Intl.supportedValuesOf('timeZone')].map(value => ({ value, label: value }))] as const
    let rows = await get<Record<string, unknown>[]>(paths[source]!, props.filters)
    if (source === 'rootMenus') rows = rows.filter(row => String(row.parentId) === '0')
    if (source === 'dictionaryParents') rows = tableTree(rows as Row[])
    return [source, asOptions(rows, source)] as const
  })); if (current === generation) options.value = Object.fromEntries(result) } catch (cause) { if (current === generation) { optionsFailed.value = true; error.value = (cause as Error).message } } finally { if (current === generation) optionsBusy.value = false }
}
async function save() {
  if (saving.value || optionsBusy.value || optionsFailed.value) return
  error.value = ''
  const endpoint = props.resource.endpoint, rowId = props.row?.id, resourceId = props.resource.id, editorGeneration = generation
  const result = await submission.run({ operation: resourceId + '.save', resource: endpoint.slice(1).replaceAll('/', '.') + '.' + (rowId || 'new'), protocol: 'LOCAL' }, async () => {
    let send: ((options: OperationSend) => Promise<unknown>) | undefined
    await handleSubmit(validated => {
      if (resourceId === 'users' && !rowId && !validPassword(validated.text.password || '')) { error.value = t('iam.passwordRule'); return }
      const payload = JSON.parse(JSON.stringify({ ...payloadFor(props.resource, validated, !!rowId), ...props.preset }))
      if (resourceId === 'parameters' && props.row?.secret && !validated.text.value) payload.value = null
      send = options => rowId ? put(endpoint + '/' + rowId, payload, options) : post(endpoint, payload, options)
    })()
    return open.value && generation === editorGeneration && props.row?.id === rowId ? send : undefined
  })
  if (!open.value || generation !== editorGeneration) return
  if (result.kind === 'success' || result.kind === 'completed') { await saved(result.kind === 'completed'); return }
  if ('error' in result) {
    const cause = result.error
    if (cause instanceof ApiError && Array.isArray(cause.details)) {
      for (const violation of cause.details) {
        const field = fields.value.find(field => field.key === String(violation.field).replace(/^user\./, ''))
        if (field && typeof violation.message === 'string') setFieldError(path(field) as 'text.name', violation.message)
      }
    }
  }
  if (submission.error.value) error.value = submission.error.value
}
async function saved(recovered = false) {
  resetForm(); open.value = false; notify(t(recovered ? 'common.operation.completed' : 'common.saved')); emit('saved')
  if (['permissions', 'navigation'].includes(props.resource.id)) { try { await auth.load(false) } catch (cause) { error.value = (cause as Error).message } }
}
async function checkResult() { const state = await submission.check(); error.value = submission.error.value; if (state === 'SUCCEEDED') await saved(true) }
watch(open, value => { if (value) { submission.resetView(); submission.bind(props.resource.endpoint.slice(1).replaceAll('/', '.') + '.' + (props.row?.id || 'new')); error.value = ''; options.value = {}; resetForm({ values: editorFor(props.resource, props.row) }); normalizeBoolean(); void loadOptions() } else generation++ })
</script>
<template><EditorDialog v-model="open" :title="t(row ? 'common.edit' : 'common.create') + ' · ' + t(resource.titleKey)" :busy="saving || optionsBusy" :submit-disabled="submission.unresolved.value" :dirty="meta.dirty" @save="save"><ErrorNotice :message="error" /><OperationStatus :slow="submission.slow.value" :unresolved="submission.unresolved.value" :querying="submission.querying.value" queryable @check="checkResult" /><Button v-if="optionsFailed" variant="outline" class="mb-4" @click="loadOptions">{{ t('common.refresh') }}</Button><form class="grid gap-x-5 gap-y-4 sm:grid-cols-2" @submit.prevent="save"><fieldset class="contents" :disabled="saving || submission.unresolved.value"><template v-for="field in fields" :key="field.key"><Field v-if="field.key !== 'departmentIds' || values.singles.dataScope === 'CUSTOM'" v-slot="{ value, handleChange, errorMessage }" :name="path(field)"><div class="min-w-0 space-y-2" :class="{ 'sm:col-span-2': ['textarea', 'multiTree'].includes(field.kind) }"><Label :for="'field-' + field.key">{{ label(field) }}<span v-if="field.required" class="text-destructive" aria-hidden="true">{{ requiredMarker }}</span></Label><Switch v-if="resource.id === 'parameters' && field.key === 'value' && values.singles.valueType === 'BOOLEAN'" :id="'field-' + field.key" :model-value="values.text.value === 'true'" @update:model-value="setFieldValue('text.value', String($event))" /><IconPicker v-else-if="field.kind === 'icon'" :id="'field-' + field.key" :model-value="String(value || '')" :disabled="disabled(field)" :invalid="!!errorMessage" :description-id="errorMessage ? 'error-' + field.key : undefined" @update:model-value="handleChange" /><PasswordInput v-else-if="field.kind === 'password'" :id="'field-' + field.key" :model-value="String(value || '')" autocomplete="new-password" :aria-invalid="!!errorMessage" :aria-describedby="errorMessage ? 'error-' + field.key : undefined" :aria-required="!!field.required" @update:model-value="handleChange" /><Textarea v-else-if="field.kind === 'textarea'" :id="'field-' + field.key" :model-value="String(value || '')" :disabled="disabled(field)" :maxlength="resource.id === 'parameters' ? 4000 : 512" :aria-invalid="!!errorMessage" :aria-describedby="errorMessage ? 'error-' + field.key : undefined" :aria-required="!!field.required" @update:model-value="handleChange" /><Input v-else-if="field.kind === 'text'" :id="'field-' + field.key" :model-value="String(value || '')" :disabled="disabled(field)" maxlength="128" :aria-invalid="!!errorMessage" :aria-describedby="errorMessage ? 'error-' + field.key : undefined" :aria-required="!!field.required" @update:model-value="handleChange" /><Input v-else-if="field.kind === 'number'" :id="'field-' + field.key" type="number" :model-value="Number(value)" :min="0" :max="999999" :disabled="disabled(field)" @update:model-value="handleChange(Number($event))" /><Switch v-else-if="field.kind === 'flag'" :id="'field-' + field.key" :model-value="!!value" :disabled="disabled(field)" @update:model-value="handleChange" /><EntitySelect v-else :id="'field-' + field.key" :description-id="errorMessage ? 'error-' + field.key : undefined" :invalid="!!errorMessage" :required="!!field.required" :model-value="selection(value)" :options="choices(field)" :multiple="['multi', 'multiTree'].includes(field.kind)" :disabled="disabled(field) || optionsBusy" :placeholder="label(field)" @update:model-value="handleChange" /><p v-if="errorMessage" :id="'error-' + field.key" class="text-xs text-destructive" role="alert">{{ errorMessage }}</p></div></Field></template><template v-if="resource.translations"><div class="mt-2 border-t pt-4 text-sm font-medium sm:col-span-2">{{ t('common.translations') }}</div><div v-for="(key, index) in ['nameZhCN', 'nameEnUS']" :key="key" class="space-y-2"><Label :for="key">{{ t(index ? 'common.nameEnUS' : 'common.nameZhCN') }}</Label><Input :id="key" :model-value="values.text[key] || ''" maxlength="128" @update:model-value="setFieldValue(('text.' + key) as 'text.nameZhCN', String($event))" /></div></template><button type="submit" hidden tabindex="-1" aria-hidden="true">{{ t('common.save') }}</button></fieldset></form></EditorDialog></template>
