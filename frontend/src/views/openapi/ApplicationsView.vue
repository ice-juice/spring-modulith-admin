<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
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
    rows.value = result.items
    total.value = Number(result.total)
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
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
async function toggle(
  row: Application,
  key: 'enabled' | 'signatureRequired',
  value: boolean | string | number,
) {
  try {
    await put('/openapi/apps/' + row.id, { ...input(row), [key]: Boolean(value) })
    await load()
  } catch (e) {
    error.value = (e as Error).message
  }
}
async function rotate(row: Application) {
  try {
    await ElMessageBox.confirm(t('openapi.rotateConfirm'), t('common.confirm'), { type: 'warning' })
    credentials.value = await post('/openapi/apps/' + row.id + '/rotate')
  } catch (e) {
    if (e instanceof Error) error.value = e.message
  }
}
async function erase(row: Application) {
  try {
    await ElMessageBox.confirm(t('common.deleteConfirm'), t('common.confirm'), { type: 'warning' })
    await remove('/openapi/apps/' + row.id)
    await load()
  } catch (e) {
    if (e instanceof Error) error.value = e.message
  }
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
  if (!target.type.trim() || !target.id.trim() || !target.name.trim()) {
    error.value = t('common.required')
    return
  }
  try {
    await put('/openapi/apps/' + targetId.value + '/target', target)
    targetId.value = ''
    await load()
  } catch (e) {
    error.value = (e as Error).message
  }
}
async function unlink(row: Application) {
  try {
    await ElMessageBox.confirm(t('openapi.unlinkConfirm'), t('common.confirm'))
    await remove('/openapi/apps/' + row.id + '/target')
    await load()
  } catch (e) {
    if (e instanceof Error) error.value = e.message
  }
}
async function copySecret() {
  try {
    if (credentials.value) await navigator.clipboard.writeText(credentials.value.appSecret)
    ElMessage.success(t('openapi.copied'))
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
</script>
<template>
  <el-card shadow="never"
    ><div class="toolbar">
      <el-input
        v-model="keyword"
        :placeholder="t('common.search')"
        clearable
        @keyup.enter="search"
      /><el-select v-model="type" clearable :placeholder="t('openapi.type')"
        ><el-option
          v-for="option in types"
          :key="option.value"
          :value="Number(option.value)"
          :label="option.label" /></el-select
      ><el-select v-model="enabled" clearable :placeholder="t('common.status')"
        ><el-option :value="true" :label="t('common.enabled')" /><el-option
          :value="false"
          :label="t('common.disabled')" /></el-select
      ><el-button @click="search">{{ t('common.search') }}</el-button
      ><el-button v-permission="'openapi:app:create'" type="primary" @click="edit()">{{
        t('openapi.create')
      }}</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" /><el-table
      v-loading="busy"
      :data="rows"
      row-key="id"
      ><el-table-column :label="t('common.name')" min-width="180"
        ><template #default="{ row }"
          ><el-button link @click="detail = row">{{ row.name }}</el-button></template
        ></el-table-column
      ><el-table-column :label="t('openapi.type')" min-width="150"
        ><template #default="{ row }">{{ typeName(row.type) }}</template></el-table-column
      ><el-table-column prop="appId" :label="t('openapi.appId')" min-width="260" /><el-table-column
        :label="t('common.enabled')"
        width="90"
        ><template #default="{ row }"
          ><el-switch
            :model-value="row.enabled"
            :disabled="!auth.hasPermission('openapi:app:update')"
            @change="toggle(row, 'enabled', $event)" /></template></el-table-column
      ><el-table-column :label="t('openapi.signature')" width="110"
        ><template #default="{ row }"
          ><el-switch
            :model-value="row.signatureRequired"
            :disabled="!auth.hasPermission('openapi:app:update')"
            @change="toggle(row, 'signatureRequired', $event)" /></template></el-table-column
      ><el-table-column
        prop="targetName"
        :label="t('openapi.target')"
        min-width="160"
      /><el-table-column :label="t('common.createdAt')" width="190"
        ><template #default="{ row }">{{
          formatInstant(row.createdAt, preferences.timeZone, locale)
        }}</template></el-table-column
      ><el-table-column :label="t('common.actions')" width="320"
        ><template #default="{ row }"
          ><el-button v-permission="'openapi:app:update'" link @click="edit(row)">{{
            t('common.edit')
          }}</el-button
          ><el-button v-permission="'openapi:app:rotate'" link @click="rotate(row)">{{
            t('openapi.rotate')
          }}</el-button
          ><el-button v-permission="'openapi:app:link'" link @click="link(row)">{{
            t('openapi.link')
          }}</el-button
          ><el-button
            v-if="row.targetId"
            v-permission="'openapi:app:link'"
            link
            @click="unlink(row)"
            >{{ t('openapi.unlink') }}</el-button
          ><el-button v-permission="'openapi:app:delete'" link type="danger" @click="erase(row)">{{
            t('common.remove')
          }}</el-button></template
        ></el-table-column
      ></el-table
    ><el-pagination
      v-model:current-page="page"
      :total="total"
      :page-size="20"
      layout="total, prev, pager, next"
      @current-change="load"
  /></el-card>
  <el-dialog
    v-model="dialog"
    :title="t(editingId ? 'common.edit' : 'openapi.create')"
    width="min(680px,95vw)"
    ><el-alert v-if="error" :title="error" type="error" :closable="false" /><el-form
      label-width="150px"
      ><el-form-item :label="t('common.name')" required
        ><el-input v-model="form.name" maxlength="128" /></el-form-item
      ><el-form-item :label="t('openapi.type')"
        ><el-select v-model="form.type"
          ><el-option
            v-for="option in types"
            :key="option.value"
            :value="Number(option.value)"
            :label="option.label" /></el-select></el-form-item
      ><el-form-item :label="t('common.description')"
        ><el-input
          v-model="form.description"
          type="textarea"
          maxlength="200"
          :rows="3" /></el-form-item
      ><el-form-item :label="t('common.enabled')"><el-switch v-model="form.enabled" /></el-form-item
      ><el-form-item :label="t('openapi.signature')"
        ><el-switch v-model="form.signatureRequired" /></el-form-item></el-form
    ><template #footer
      ><el-button @click="dialog = false">{{ t('common.cancel') }}</el-button
      ><el-button type="primary" :loading="saving" @click="save">{{
        t('common.save')
      }}</el-button></template
    ></el-dialog
  >
  <el-dialog
    :model-value="!!credentials"
    :title="t('openapi.credentials')"
    width="min(720px,95vw)"
    @close="credentials = undefined"
    ><template v-if="credentials"
      ><el-alert :title="t('openapi.secretOnce')" type="info" :closable="false" /><el-form
        label-width="130px"
        ><el-form-item :label="t('openapi.appId')"
          ><el-input :model-value="credentials.appId" readonly /></el-form-item
        ><el-form-item :label="t('openapi.appSecret')"
          ><el-input
            :model-value="credentials.appSecret"
            type="password"
            show-password
            readonly /></el-form-item></el-form
      ><el-button @click="copySecret">{{ t('openapi.copySecret') }}</el-button></template
    ></el-dialog
  >
  <el-dialog
    :model-value="!!targetId"
    :title="t('openapi.target')"
    width="min(640px,95vw)"
    @close="targetId = ''"
    ><el-alert v-if="error" :title="error" type="error" :closable="false" /><el-form
      label-width="140px"
      ><el-form-item :label="t('openapi.targetType')"
        ><el-input v-model="target.type" maxlength="64" /></el-form-item
      ><el-form-item :label="t('openapi.targetId')"
        ><el-input v-model="target.id" maxlength="128" /></el-form-item
      ><el-form-item :label="t('openapi.targetName')"
        ><el-input v-model="target.name" maxlength="128" /></el-form-item></el-form
    ><template #footer
      ><el-button type="primary" @click="saveTarget">{{ t('common.save') }}</el-button></template
    ></el-dialog
  >
  <el-dialog
    :model-value="!!detail"
    :title="detail?.name"
    width="min(640px,95vw)"
    @close="detail = undefined"
    ><el-descriptions v-if="detail" :column="1" border
      ><el-descriptions-item :label="t('openapi.appId')">{{ detail.appId }}</el-descriptions-item
      ><el-descriptions-item :label="t('openapi.type')">{{
        typeName(detail.type)
      }}</el-descriptions-item
      ><el-descriptions-item :label="t('common.description')">{{
        detail.description
      }}</el-descriptions-item
      ><el-descriptions-item :label="t('openapi.target')">{{
        detail.targetName
      }}</el-descriptions-item></el-descriptions
    ></el-dialog
  >
</template>
