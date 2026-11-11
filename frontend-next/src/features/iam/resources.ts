import { configResources } from '@/features/config/resources'
export type FieldKind =
  'icon' | 'text' | 'textarea' | 'password' | 'number' | 'flag' | 'select' | 'tree' | 'multi' | 'multiTree'
export interface Option {
  value: string
  label: string
  disabled?: boolean
  children?: Option[]
}
export interface Field {
  key: string
  kind: FieldKind
  required?: boolean
  source?: string
  choices?: string[]
  initial?: string | number | boolean
  createOnly?: boolean
  keepEmpty?: boolean
}
export interface Column {
  key: string
  kind?: 'status' | 'scope' | 'type' | 'boolean'
}
export interface Resource {
  id: string
  module?: string
  titleKey: string
  endpoint: string
  permission: string
  paged?: boolean
  tree?: boolean
  fields: Field[]
  columns: Column[]
  translations?: boolean
}
export interface Row {
  id: string
  [key: string]: unknown
}
const name: Field = { key: 'name', kind: 'text', required: true }
const code: Field = { key: 'code', kind: 'text', required: true }
const sort: Field = { key: 'sort', kind: 'number', initial: 0 }
const enabled: Field = { key: 'enabled', kind: 'flag', initial: true }
const parent = (source: string): Field => ({ key: 'parentId', kind: 'tree', source, initial: '0' })
const columns: Column[] = [
  { key: 'name' },
  { key: 'code' },
  { key: 'sort' },
  { key: 'enabled', kind: 'status' },
]
export const resources: Record<string, Resource> = {
  ...configResources,
  departments: {
    id: 'departments',
    titleKey: 'iam.department',
    endpoint: '/iam/departments',
    permission: 'iam:department',
    tree: true,
    fields: [parent('departments'), name, code, sort, enabled],
    columns,
  },
  posts: {
    id: 'posts',
    titleKey: 'iam.post',
    endpoint: '/iam/posts',
    permission: 'iam:post',
    paged: true,
    fields: [name, code, sort, enabled, { key: 'description', kind: 'textarea' }],
    columns,
  },
  roles: {
    id: 'roles',
    titleKey: 'iam.role',
    endpoint: '/iam/roles',
    permission: 'iam:role',
    tree: true,
    fields: [
      parent('roleTree'),
      name,
      code,
      sort,
      enabled,
      {
        key: 'dataScope',
        kind: 'select',
        required: true,
        initial: 'SELF',
        choices: ['ALL', 'DEPARTMENT_TREE', 'DEPARTMENT', 'SELF', 'CUSTOM'],
      },
      { key: 'departmentIds', kind: 'multiTree', source: 'departments' },
      { key: 'description', kind: 'textarea' },
    ],
    columns: [...columns, { key: 'dataScope', kind: 'scope' }],
  },
  permissions: {
    id: 'permissions',
    titleKey: 'iam.permission',
    endpoint: '/iam/permissions',
    permission: 'iam:permission',
    tree: true,
    translations: true,
    fields: [
      parent('permissionTree'),
      name,
      { key: 'permissionKey', kind: 'text', required: true },
      { key: 'nameKey', kind: 'text' },
      {
        key: 'type',
        kind: 'select',
        choices: ['DIRECTORY', 'MENU', 'BUTTON', 'LOGIC'],
        required: true,
        initial: 'MENU',
      },
      { key: 'path', kind: 'text' },
      { key: 'icon', kind: 'icon' },
      {
        key: 'openMode',
        kind: 'select',
        choices: ['ROUTE', 'EXTERNAL', 'IFRAME'],
        initial: 'ROUTE',
        required: true,
      },
      sort,
      enabled,
      { key: 'hidden', kind: 'flag', initial: false },
      { key: 'superAdminDefault', kind: 'flag', initial: true },
    ],
    columns: [
      { key: 'name' },
      { key: 'permissionKey' },
      { key: 'type', kind: 'type' },
      { key: 'path' },
      { key: 'sort' },
      { key: 'enabled', kind: 'status' },
    ],
  },
  users: {
    id: 'users',
    titleKey: 'iam.user',
    endpoint: '/iam/users',
    permission: 'iam:user',
    paged: true,
    fields: [
      { key: 'username', kind: 'text', required: true },
      name,
      { key: 'password', kind: 'password', required: true, createOnly: true },
      { key: 'departmentId', kind: 'tree', source: 'departments' },
      { key: 'roleIds', kind: 'multi', source: 'roles' },
      { key: 'postIds', kind: 'multi', source: 'posts' },
      { key: 'email', kind: 'text' },
      { key: 'mobile', kind: 'text' },
      {
        key: 'gender',
        kind: 'select',
        choices: ['MALE', 'FEMALE', 'UNSPECIFIED'],
        initial: 'UNSPECIFIED',
      },
      enabled,
      { key: 'locale', kind: 'select', choices: ['zh-CN', 'en-US'], initial: 'zh-CN' },
      { key: 'timeZone', kind: 'select', source: 'timeZones', initial: 'UTC' },
    ],
    columns: [
      { key: 'username' },
      { key: 'name' },
      { key: 'email' },
      { key: 'mobile' },
      { key: 'enabled', kind: 'status' },
    ],
  },
  navigation: {
    id: 'navigation',
    titleKey: 'iam.navigation',
    endpoint: '/iam/navigation',
    permission: 'iam:navigation',
    translations: true,
    fields: [
      name,
      { key: 'icon', kind: 'icon' },
      sort,
      enabled,
      { key: 'menuIds', kind: 'multi', source: 'rootMenus' },
    ],
    columns: [
      { key: 'name' },
      { key: 'icon' },
      { key: 'sort' },
      { key: 'enabled', kind: 'status' },
    ],
  },
}
export interface EditorState {
  text: Record<string, string>
  numbers: Record<string, number>
  flags: Record<string, boolean>
  singles: Record<string, string | null>
  multiples: Record<string, string[]>
}
export function editorFor(resource: Resource, row?: Row): EditorState {
  const state: EditorState = { text: {}, numbers: {}, flags: {}, singles: {}, multiples: {} }
  for (const field of resource.fields) {
    const value =
      (field.key === 'name' && row?.defaultName ? row.defaultName : row?.[field.key]) ??
      field.initial
    if (field.kind === 'number') state.numbers[field.key] = typeof value === 'number' ? value : 0
    else if (field.kind === 'flag') state.flags[field.key] = value === true
    else if (['tree', 'select'].includes(field.kind))
      state.singles[field.key] = value == null ? null : String(value)
    else if (['multi', 'multiTree'].includes(field.kind))
      state.multiples[field.key] = Array.isArray(value) ? value.map(String) : []
    else state.text[field.key] = resource.id === 'parameters' && row?.secret && field.key === 'value' ? '' : value == null ? '' : String(value)
  }
  const translations = row?.translations as Record<string, string> | undefined
  state.text.nameZhCN = translations?.['zh-CN'] || ''
  state.text.nameEnUS = translations?.['en-US'] || ''
  return state
}
export function payloadFor(
  resource: Resource,
  state: EditorState,
  editing: boolean,
): Record<string, unknown> {
  const payload: Record<string, unknown> = {}
  for (const field of resource.fields) {
    if (editing && field.createOnly) continue
    if (field.kind === 'number') payload[field.key] = state.numbers[field.key]
    else if (field.kind === 'flag') payload[field.key] = state.flags[field.key]
    else if (['tree', 'select'].includes(field.kind))
      payload[field.key] = state.singles[field.key] ?? (field.key === 'parentId' ? '0' : null)
    else if (['multi', 'multiTree'].includes(field.kind))
      payload[field.key] = state.multiples[field.key] || []
    else
      payload[field.key] = state.text[field.key] || (field.required || field.keepEmpty ? '' : null)
  }
  if (resource.translations) {
    payload.translations = Object.fromEntries(
      [
        ['zh-CN', state.text.nameZhCN],
        ['en-US', state.text.nameEnUS],
      ].filter(([, value]) => value?.trim()),
    )
  }
  if (resource.id === 'users' && !editing) {
    const password = payload.password
    delete payload.password
    return { user: payload, password }
  }
  return payload
}
export function tableTree(rows: Row[]): Row[] {
  const indexed = new Map<string, Row & { children: Row[] }>(
    rows.map((row) => [row.id, { ...row, children: [] as Row[] }]),
  )
  const roots: Row[] = []
  for (const row of indexed.values()) {
    const parent = indexed.get(String(row.parentId))
    if (parent && parent !== row) parent.children.push(row)
    else roots.push(row)
  }
  return roots
}
