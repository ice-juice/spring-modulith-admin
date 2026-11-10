<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules,
  type ElTree,
} from 'element-plus'
import { get, post, put, remove, type PageResult } from '@/api/http'
import { useAuth } from '@/stores/auth'
import {
  editorFor,
  payloadFor,
  resources,
  tableTree,
  type Row,
  type Option,
} from '@/features/iam/resources'
const route = useRoute()
const router = useRouter()
function members(row: Row) {
  void router.push({ path: '/iam/users', query: { roleId: row.id, roleName: String(row.name) } })
}
function clearRole() {
  const query = { ...route.query }
  delete query.roleId
  delete query.roleName
  void router.replace({ query })
}
const props = withDefaults(
  defineProps<{
    resourceId?: string
    filters?: Record<string, unknown>
    preset?: Record<string, unknown>
    hierarchical?: boolean
  }>(),
  { hierarchical: true },
)
const emit = defineEmits<{ changed: []; select: [row: Row] }>()
const { t, locale } = useI18n()
const auth = useAuth()
const resource = computed(() => resources[props.resourceId || String(route.meta.resource)]!)
const rows = ref<Row[]>([])
const loading = ref(false)
const error = ref('')
const keyword = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const dialog = ref(false)
const saving = ref(false)
const current = ref<Row>()
const editor = ref(editorFor(resource.value))
const form = ref<FormInstance>()
const options = reactive<Record<string, Option[]>>({})
const grantDialog = ref(false)
const permissionTree = ref<Row[]>([])
const checkedTree = ref<InstanceType<typeof ElTree>>()
const roleForGrant = ref<Row>()
const resetDialog = ref(false)
const newPassword = ref('')
const userForReset = ref<Row>()
const focusMode = ref(true)
function rowClassName({ row }: { row: Row }) {
  return row.id === route.query?.focus ? 'focused-row' : ''
}
const tableData = computed(() => (resource.value.tree ? tableTree(rows.value) : rows.value))
const visibleFields = computed(() =>
  resource.value.fields.filter(
    (field) =>
      (!current.value || !field.createOnly) &&
      (field.key !== 'parentId' || props.hierarchical) &&
      !(
        resource.value.id === 'users' &&
        ((field.key === 'departmentId' && auth.user?.features?.departmentEnabled === false) ||
          (field.key === 'postIds' && auth.user?.features?.postEnabled === false) ||
          (['email', 'mobile', 'gender'].includes(field.key) &&
            auth.user?.features?.extendedProfileEnabled === false))
      ),
  ),
)
const rules = computed<FormRules>(() =>
  Object.fromEntries(
    visibleFields.value
      .filter((field) => field.required)
      .map((field) => [
        ['tree', 'select'].includes(field.kind) ? 'singles.' + field.key : 'text.' + field.key,
        [{ required: true, message: t('common.required'), trigger: 'blur' }],
      ]),
  ),
)
function label(key: string) {
  return t((resource.value.module || 'iam') + '.fields.' + key)
}
function choice(key: string) {
  return t((resource.value.module || 'iam') + '.choices.' + key)
}
function selectRow(row: Row) {
  emit('select', row)
}
const parameterBoolean = computed({
  get: () => editor.value.text.value === 'true',
  set: (value: boolean) => {
    editor.value.text.value = String(value)
  },
})
function search() {
  focusMode.value = false
  page.value = 1
  void load()
}
function openReset(row: Row) {
  userForReset.value = row
  newPassword.value = ''
  error.value = ''
  resetDialog.value = true
}
function canEdit(row: Row) {
  return resource.value.id !== 'users' || (!row.superAdmin && row.id !== auth.user?.id)
}
async function load() {
  loading.value = true
  error.value = ''
  try {
    const result =
      resource.value.id === 'users' && focusMode.value && typeof route.query?.focus === 'string'
        ? [await get<Row>(resource.value.endpoint + '/' + route.query.focus)]
        : await get<Row[] | PageResult<Row>>(resource.value.endpoint, {
            page: page.value,
            size: size.value,
            keyword: keyword.value || undefined,
            roleId:
              resource.value.id === 'users' && typeof route.query.roleId === 'string'
                ? route.query.roleId
                : undefined,
            ...props.filters,
          })
    rows.value = Array.isArray(result)
      ? result.filter(
          (row) =>
            !keyword.value.trim() ||
            ['name', 'code', 'permissionKey', 'typeKey', 'paramKey', 'value', 'groupKey'].some(
              (key) =>
                String(row[key] || '')
                  .toLocaleLowerCase()
                  .includes(keyword.value.trim().toLocaleLowerCase()),
            ),
        )
      : result.items
    total.value = Array.isArray(result) ? result.length : Number(result.total)
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}
interface TreeSource {
  id: string
  label?: string
  name?: string
  parentId?: string
  disabled?: boolean
  enabled?: boolean
  children?: TreeSource[]
  groupKey?: string
}
function optionTree(rows: TreeSource[]): Option[] {
  return rows.map((row) => ({
    value: row.id,
    label: row.label || row.name || '',
    disabled: row.disabled || row.enabled === false,
    children: row.children ? optionTree(row.children) : undefined,
  }))
}
async function loadOptions() {
  const sources = new Set(
    resource.value.fields.map((field) => field.source).filter((value): value is string => !!value),
  )
  const paths: Record<string, string> = {
    departments: '/iam/departments/tree',
    roleTree: '/iam/roles/tree',
    permissionTree: '/iam/permissions/tree',
    roles: '/iam/roles/options',
    posts: '/iam/posts/options',
    rootMenus: '/iam/permissions',
    parameterGroups: '/config/parameters/groups',
    dictionaryParents: '/config/dictionaries/items',
  }
  await Promise.all(
    [...sources].map(async (source) => {
      if (source === 'timeZones') {
        options[source] = ['UTC', ...Intl.supportedValuesOf('timeZone')].map((value) => ({
          value,
          label: value,
        }))
        return
      }
      try {
        const result = await get<TreeSource[] | Option[]>(paths[source]!, props.filters)
        if (source === 'roles' || source === 'posts') options[source] = result as Option[]
        else if (source === 'rootMenus')
          options[source] = optionTree(
            (result as TreeSource[]).filter((row) => String(row.parentId) === '0'),
          )
        else if (source === 'parameterGroups')
          options[source] = (result as TreeSource[]).map((row) => ({
            value: row.groupKey!,
            label: row.name || '',
          }))
        else if (source === 'dictionaryParents')
          options[source] = optionTree(tableTree(result as Row[]) as unknown as TreeSource[])
        else options[source] = optionTree(result as TreeSource[])
      } catch {
        options[source] = []
      }
    }),
  )
}
function fieldOptions(source?: string, key?: string): Option[] {
  const result = options[source || ''] || []
  return key === 'parentId' ? [{ value: '0', label: t('common.root') }, ...result] : result
}
async function openEditor(row?: Row) {
  current.value = row
  editor.value = editorFor(resource.value, row)
  dialog.value = true
  error.value = ''
  await loadOptions()
  await nextTick()
  form.value?.clearValidate()
}
async function save() {
  if (!(await form.value?.validate().catch(() => false))) return
  if (
    resource.value.id === 'users' &&
    !current.value &&
    (editor.value.text.password || '').length < 12
  ) {
    error.value = t('iam.passwordRule')
    return
  }
  saving.value = true
  error.value = ''
  try {
    const payload = {
      ...payloadFor(resource.value, editor.value, !!current.value),
      ...props.preset,
    }
    if (resource.value.id === 'parameters' && current.value?.secret && !editor.value.text.value)
      payload.value = null
    if (current.value) await put(resource.value.endpoint + '/' + current.value.id, payload)
    else await post(resource.value.endpoint, payload)
    dialog.value = false
    ElMessage.success(t('common.saved'))
    await load()
    emit('changed')
    if (resource.value.id === 'permissions' || resource.value.id === 'navigation')
      await auth.load(false)
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    saving.value = false
  }
}
async function deleteRow(row: Row) {
  try {
    await ElMessageBox.confirm(t('common.deleteConfirm'), t('common.confirm'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await remove(resource.value.endpoint + '/' + row.id)
    ElMessage.success(t('common.deleted'))
    await load()
    emit('changed')
  } catch (e) {
    error.value = (e as Error).message
  }
}
async function openGrant(row: Row) {
  roleForGrant.value = row
  grantDialog.value = true
  error.value = ''
  try {
    permissionTree.value = await get<Row[]>('/iam/permissions/tree')
    const selected = await get<string[]>('/iam/roles/' + row.id + '/permissions')
    await nextTick()
    checkedTree.value?.setCheckedKeys(selected)
  } catch (e) {
    error.value = (e as Error).message
  }
}
async function saveGrant() {
  saving.value = true
  try {
    await put('/iam/roles/' + roleForGrant.value?.id + '/permissions', {
      permissionIds: [
        ...new Set([
          ...(checkedTree.value?.getCheckedKeys(false) || []),
          ...(checkedTree.value?.getHalfCheckedKeys() || []),
        ]),
      ],
    })
    grantDialog.value = false
    ElMessage.success(t('common.saved'))
    await auth.load(false)
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    saving.value = false
  }
}
async function resetPassword() {
  if (newPassword.value.length < 12) {
    error.value = t('iam.passwordRule')
    return
  }
  try {
    await put('/iam/users/' + userForReset.value?.id + '/password', { password: newPassword.value })
    newPassword.value = ''
    resetDialog.value = false
    ElMessage.success(t('common.saved'))
  } catch (e) {
    error.value = (e as Error).message
  }
}
watch(
  [
    () => props.resourceId || route.meta.resource,
    locale,
    () => props.filters,
    () => route.query?.focus,
    () => route.query?.roleId,
  ],
  () => {
    page.value = 1
    keyword.value = ''
    focusMode.value = true
    void load()
  },
  { immediate: true, deep: true },
)
</script>
<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-input
        v-model="keyword"
        :placeholder="t('common.search')"
        clearable
        style="width: 260px"
        @keyup.enter="search"
      />
      <el-button @click="search">{{ t('common.search') }}</el-button>
      <el-button
        v-permission="resource.permission + ':create'"
        type="primary"
        @click="openEditor()"
        >{{ t('common.create') }}</el-button
      >
      <el-button @click="load">{{ t('common.refresh') }}</el-button>
      <el-tag v-if="resource.id === 'users' && route.query.roleId" closable @close="clearRole">{{
        t('iam.roleMembers', { name: String(route.query.roleName || route.query.roleId) })
      }}</el-tag>
    </div>
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    />
    <el-table
      v-loading="loading"
      :data="tableData"
      row-key="id"
      default-expand-all
      stripe
      :empty-text="t('common.empty')"
      @row-click="selectRow"
      :row-class-name="rowClassName"
    >
      <el-table-column
        v-for="column in resource.columns"
        :key="column.key"
        :prop="column.key"
        :label="label(column.key)"
        :min-width="column.key === 'name' ? 180 : 125"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <el-tag v-if="column.kind === 'status'" :type="row[column.key] ? 'success' : 'info'">{{
            t(row[column.key] ? 'common.enabled' : 'common.disabled')
          }}</el-tag>
          <span v-else-if="column.kind === 'boolean'">{{
            t(row[column.key] ? 'common.yes' : 'common.no')
          }}</span>
          <span v-else-if="column.kind === 'scope' || column.kind === 'type'">{{
            choice(String(row[column.key]))
          }}</span>
          <span v-else>{{
            resource.id === 'parameters' && column.key === 'value' && row.secret
              ? t('config.masked')
              : row[column.key]
          }}</span>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('common.actions')"
        :width="resource.id === 'roles' ? 270 : resource.id === 'users' ? 220 : 140"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            v-if="canEdit(row)"
            v-permission="resource.permission + ':update'"
            link
            type="primary"
            @click.stop="openEditor(row)"
            >{{ t('common.edit') }}</el-button
          >
          <el-button
            v-if="resource.id === 'roles'"
            v-permission="'iam:role:grant'"
            link
            type="primary"
            @click.stop="openGrant(row)"
            >{{ t('iam.grant') }}</el-button
          >
          <el-button
            v-if="resource.id === 'roles'"
            v-permission="'iam:user:view'"
            link
            @click.stop="members(row)"
            >{{ t('iam.viewMembers') }}</el-button
          >
          <el-button
            v-if="resource.id === 'users' && canEdit(row)"
            v-permission="'iam:user:reset-password'"
            link
            type="warning"
            @click.stop="openReset(row)"
            >{{ t('iam.resetPassword') }}</el-button
          >
          <el-button
            v-if="canEdit(row) && !row.builtin"
            v-permission="resource.permission + ':delete'"
            link
            type="danger"
            @click.stop="deleteRow(row)"
            >{{ t('common.remove') }}</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-if="resource.paged"
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next"
      style="margin-top: 20px"
      @current-change="load"
      @size-change="search"
    />
  </el-card>
  <el-dialog
    v-model="dialog"
    :title="t(current ? 'common.edit' : 'common.create') + ' · ' + t(resource.titleKey)"
    width="640px"
    destroy-on-close
  >
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
      style="margin-bottom: 16px"
    />
    <el-form ref="form" :model="editor" :rules="rules" label-width="140px">
      <template v-for="field in visibleFields" :key="field.key">
        <el-form-item
          v-if="field.key !== 'departmentIds' || editor.singles.dataScope === 'CUSTOM'"
          :label="label(field.key)"
          :prop="(['tree', 'select'].includes(field.kind) ? 'singles.' : 'text.') + field.key"
        >
          <el-switch
            v-if="
              resource.id === 'parameters' &&
              field.key === 'value' &&
              editor.singles.valueType === 'BOOLEAN'
            "
            v-model="parameterBoolean"
          />
          <el-input
            v-else-if="['text', 'textarea', 'password'].includes(field.kind)"
            v-model="editor.text[field.key]"
            :type="field.kind === 'text' ? 'text' : field.kind"
            :show-password="field.kind === 'password'"
            :disabled="
              !!current?.builtin &&
              (['permissionKey', 'typeKey', 'paramKey', 'groupKey'].includes(field.key) ||
                (field.key === 'value' && resource.id === 'dictionaryItems'))
            "
            :maxlength="
              resource.id === 'parameters' && field.key === 'value'
                ? 4000
                : field.key === 'description'
                  ? 512
                  : 128
            "
          />
          <el-input-number
            v-else-if="field.kind === 'number'"
            v-model="editor.numbers[field.key]"
            :min="0"
            :max="999999"
          />
          <el-switch
            v-else-if="field.kind === 'flag'"
            v-model="editor.flags[field.key]"
            :disabled="
              !!current?.builtin &&
              resource.id.startsWith('dictionary') &&
              ['enabled', 'tree'].includes(field.key)
            "
          />
          <el-tree-select
            v-else-if="field.kind === 'tree'"
            v-model="editor.singles[field.key]"
            :data="fieldOptions(field.source, field.key)"
            check-strictly
            filterable
            clearable
            node-key="value"
            style="width: 100%"
          />
          <el-tree-select
            v-else-if="field.kind === 'multiTree'"
            v-model="editor.multiples[field.key]"
            :data="fieldOptions(field.source)"
            multiple
            check-strictly
            filterable
            node-key="value"
            style="width: 100%"
          />
          <el-select
            v-else-if="field.kind === 'multi'"
            v-model="editor.multiples[field.key]"
            multiple
            filterable
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="option in fieldOptions(field.source)"
              :key="option.value"
              :label="option.label"
              :value="option.value"
              :disabled="option.disabled"
            />
          </el-select>
          <el-select
            v-else-if="field.kind === 'select'"
            v-model="editor.singles[field.key]"
            filterable
            :disabled="!!current?.builtin && ['type', 'valueType'].includes(field.key)"
            style="width: 100%"
          >
            <el-option
              v-for="value in field.choices || []"
              :key="value"
              :label="choice(value)"
              :value="value"
            />
            <el-option
              v-for="option in fieldOptions(field.source)"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
      </template>
      <template v-if="resource.translations">
        <el-divider>{{ t('common.translations') }}</el-divider>
        <el-form-item :label="t('common.nameZhCN')"
          ><el-input v-model="editor.text.nameZhCN" maxlength="128"
        /></el-form-item>
        <el-form-item :label="t('common.nameEnUS')"
          ><el-input v-model="editor.text.nameEnUS" maxlength="128"
        /></el-form-item>
      </template>
    </el-form>
    <template #footer
      ><el-button @click="dialog = false">{{ t('common.cancel') }}</el-button
      ><el-button type="primary" :loading="saving" @click="save">{{
        t('common.save')
      }}</el-button></template
    >
  </el-dialog>
  <el-dialog
    v-if="resource.id === 'roles'"
    v-model="grantDialog"
    :title="t('iam.grant') + ' · ' + roleForGrant?.name"
    width="600px"
    destroy-on-close
  >
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-tree
      ref="checkedTree"
      :data="permissionTree"
      node-key="id"
      :props="{ label: 'name', children: 'children' }"
      show-checkbox
      check-strictly
      default-expand-all
    />
    <template #footer
      ><el-button @click="grantDialog = false">{{ t('common.cancel') }}</el-button
      ><el-button type="primary" :loading="saving" @click="saveGrant">{{
        t('common.save')
      }}</el-button></template
    >
  </el-dialog>
  <el-dialog
    v-if="resource.id === 'users'"
    v-model="resetDialog"
    :title="t('iam.resetPassword')"
    width="450px"
  >
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-input
      v-model="newPassword"
      type="password"
      show-password
      :placeholder="t('iam.passwordRule')"
      autocomplete="new-password"
    />
    <template #footer
      ><el-button @click="resetDialog = false">{{ t('common.cancel') }}</el-button
      ><el-button type="primary" @click="resetPassword">{{ t('common.save') }}</el-button></template
    >
  </el-dialog>
</template>
