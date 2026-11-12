import { get, post, put, type PageResult, type RequestOptions } from '@/api/http'

/** Long revisions remain strings end to end; comparisons use BigInt so no JS Number rounding can change a version. */
export interface Revision { platform: string; tenant: string }
export type Origin = 'DEFAULT' | 'PLATFORM' | 'TENANT_OVERRIDE' | 'TENANT_ONLY'
export interface Label { text: string; messageKey?: string | null; translations: Partial<Record<'zh-CN' | 'en-US', string>> }
export interface Constraints { nullable: boolean; allowEmpty: boolean; maxLength: number; minimum: string | null; maximum: string | null; choices: string[] }
export interface EffectiveParameter {
  key: string; owner: string; groupKey: string; name: string; defaultName: string; nameKey: string | null; translations: Record<string, string>; sort: number
  scope: 'TENANT_ONLY' | 'PLATFORM_SHARED'; valueType: 'STRING' | 'BOOLEAN' | 'INTEGER' | 'DECIMAL' | 'JSON'; overridePolicy: 'LOCKED' | 'OVERRIDABLE'
  constraints: Constraints; securitySensitive: boolean; value: string | null; origin: Origin; revision: Revision; rowId: string | null; overridden: boolean
}
export interface DictionaryEntry { code: string; parentCode: string | null; label: string; enabled: boolean; hidden: boolean; selectable: boolean; sort: number; origin: Origin }
export interface LayerEntry { kind: 'OVERRIDE' | 'PRIVATE'; code: string; parentCode: string | null; label: Label | null; sort: number | null; hidden: boolean | null; enabled: boolean | null }
export type DictionaryMode = 'PLATFORM_LOCKED' | 'EXTENDABLE' | 'TENANT_ONLY'
export interface DictionarySummary { key: string; owner: string; mode: DictionaryMode; tree: boolean; name: string; enabled: boolean; revision: Revision; entries: number; selectable: number; overridden: number; privateItems: number }
export interface EffectiveDictionary { key: string; owner: string; mode: DictionaryMode; tree: boolean; name: string; label: Label; enabled: boolean; revision: Revision; entries: DictionaryEntry[]; layer: LayerEntry[] }
export interface SharedItem { code: string; parentCode: string | null; label: Label; enabled: boolean; sort: number }
export interface SharedSummary { id: string; kind: 'PARAMETER' | 'DICTIONARY'; key: string; owner: string; label: Label; draftVersion: string; publishedRevisionId: string | null; publishedRevision: string; codeDefaultsChanged: boolean }
export interface ParameterDefinition {
  owner: string; key: string; group: string; label: Label; scope: EffectiveParameter['scope']; valueType: EffectiveParameter['valueType']
  overridePolicy: EffectiveParameter['overridePolicy']; defaultValue: string | null; constraints: Constraints; securitySensitive: boolean; publicProjection: string
}
export interface SharedDetail extends SharedSummary {
  parameterDefinition: ParameterDefinition | null
  dictionaryDefinition: { owner: string; key: string; label: Label; mode: DictionaryMode; tree: boolean; items: SharedItem[] } | null
  parameterDraft: { schemaVersion: 1; label: Label; value: string | null } | null
  dictionaryDraft: { schemaVersion: 1; label: Label; items: SharedItem[] } | null
}
export interface SharedRevision { id: string; revision: string; contentBytes: number; createdAt: string; createdBy: string }

const versions = (revision: Revision) => ({ expectedPlatformRevision: revision.platform, expectedTenantRevision: revision.tenant })
const parameterPath = (key: string) => '/config/effective-parameters/' + encodeURIComponent(key)
const dictionaryPath = (key: string) => '/config/effective-dictionaries/' + encodeURIComponent(key)
const sharedPath = (id: string) => '/config/shared-configurations/' + encodeURIComponent(id)

export const layers = {
  parameters: (page = 1, keyword = '', group = '') => get<PageResult<EffectiveParameter>>('/config/effective-parameters', { page, size: 50, keyword, group }),
  parameter: (key: string) => get<EffectiveParameter>(parameterPath(key)),
  /** The value property is always sent; explicit null is distinct from inheritance. */
  override: (row: EffectiveParameter, value: string | null, options?: RequestOptions) => put<EffectiveParameter>(parameterPath(row.key) + '/override', { ...versions(row.revision), value }, options),
  inherit: (row: EffectiveParameter, options?: RequestOptions) => post<EffectiveParameter>(parameterPath(row.key) + '/inherit', versions(row.revision), options),
  dictionaries: (page = 1, keyword = '') => get<PageResult<DictionarySummary>>('/config/effective-dictionaries', { page, size: 50, keyword }),
  dictionary: (key: string) => get<EffectiveDictionary>(dictionaryPath(key)),
  overrideItem: (row: EffectiveDictionary, code: string, change: { label: Label | null; sort: number | null; hidden: boolean | null }, options?: RequestOptions) =>
    put<EffectiveDictionary>(dictionaryPath(row.key) + '/item-overrides', { versions: versions(row.revision), code, ...change }, options),
  restoreItem: (row: EffectiveDictionary, code: string, options?: RequestOptions) => post<EffectiveDictionary>(dictionaryPath(row.key) + '/item-overrides/restore', { versions: versions(row.revision), code }, options),
  savePrivate: (row: EffectiveDictionary, item: SharedItem, options?: RequestOptions) =>
    put<EffectiveDictionary>(dictionaryPath(row.key) + '/private-items', { versions: versions(row.revision), code: item.code, parentCode: item.parentCode, label: item.label, sort: item.sort, enabled: item.enabled }, options),
  enabled: (row: EffectiveDictionary, enabled: boolean, options?: RequestOptions) => put<EffectiveDictionary>(dictionaryPath(row.key) + '/enabled', { versions: versions(row.revision), enabled }, options),
}

export const shared = {
  page: (page = 1, kind: '' | 'PARAMETER' | 'DICTIONARY' = '', keyword = '') => get<PageResult<SharedSummary>>('/config/shared-configurations', { page, size: 50, kind: kind || undefined, keyword }),
  detail: (id: string) => get<SharedDetail>(sharedPath(id)),
  saveParameter: (row: SharedDetail, label: Label, value: string | null, options?: RequestOptions) =>
    put<SharedDetail>(sharedPath(row.id) + '/parameter-draft', { expectedVersion: row.draftVersion, content: { schemaVersion: 1, label, value } }, options),
  saveDictionary: (row: SharedDetail, label: Label, items: SharedItem[], options?: RequestOptions) =>
    put<SharedDetail>(sharedPath(row.id) + '/dictionary-draft', { expectedVersion: row.draftVersion, content: { schemaVersion: 1, label, items } }, options),
  /** Publishing binds both the reviewed draft and the publication it replaces. */
  publish: (row: SharedDetail, options?: RequestOptions) => post<{ revisionId: string; revision: string }>(sharedPath(row.id) + '/publications', { expectedDraftVersion: row.draftVersion, expectedPublishedRevisionId: row.publishedRevisionId }, options),
  history: (id: string, page = 1) => get<PageResult<SharedRevision>>(sharedPath(id) + '/revisions', { page, size: 20 }),
  revision: (id: string, revisionId: string) => get<{ id: string; revision: string; parameter: SharedDetail['parameterDraft']; dictionary: SharedDetail['dictionaryDraft']; createdAt: string }>(sharedPath(id) + '/revisions/' + encodeURIComponent(revisionId)),
}

export function sameRevision(left: Revision, right: Revision) { return BigInt(left.platform) === BigInt(right.platform) && BigInt(left.tenant) === BigInt(right.tenant) }
export function newerRevision(current: Revision, previous: Revision) {
  return BigInt(current.platform) >= BigInt(previous.platform) && BigInt(current.tenant) >= BigInt(previous.tenant) && !sameRevision(current, previous)
}
export function editable(row: EffectiveParameter) { return row.overridePolicy === 'OVERRIDABLE' }
/** Exact comparison for the bounded decimal inputs shown by the editor. Unusual exponents defer to server validation. */
function compareDecimal(left: string, right: string): number | null {
  function parts(input: string) {
    const [coefficient = '', exponent = '0'] = input.toLowerCase().split('e')
    const negative = coefficient.startsWith('-'), unsigned = coefficient.startsWith('-') || coefficient.startsWith('+') ? coefficient.slice(1) : coefficient
    if (!/^(?:\d+(?:\.\d*)?|\.\d+)$/.test(unsigned)) return null
    const dot = unsigned.indexOf('.'), fraction = dot < 0 ? 0 : unsigned.length - dot - 1
    const digits = unsigned.replace('.', '').replace(/^0+/, '') || '0'
    const power = Number(exponent) - fraction
    if (!Number.isSafeInteger(power) || Math.abs(power) > 10000) return null
    return { sign: digits === '0' ? 0 : negative ? -1 : 1, digits, power }
  }
  const a = parts(left), b = parts(right)
  if (!a || !b) return null
  if (a.sign !== b.sign) return Math.sign(a.sign - b.sign)
  if (a.sign === 0) return 0
  const order = a.digits.length + a.power - b.digits.length - b.power
  if (order !== 0) return Math.sign(order) * a.sign
  const width = Math.max(a.digits.length, b.digits.length)
  const ad = a.digits.padEnd(width, '0'), bd = b.digits.padEnd(width, '0')
  return (ad === bd ? 0 : ad < bd ? -1 : 1) * a.sign
}
/** Mirrors server constraints for immediate feedback only; the server remains authoritative. */
export function parameterProblem(row: Pick<EffectiveParameter, 'valueType' | 'constraints'>, value: string | null): string | null {
  const rules = row.constraints
  if (value === null) return rules.nullable ? null : 'null'
  if (value === '' && !rules.allowEmpty) return 'empty'
  if (value.length > rules.maxLength) return 'length'
  if (rules.choices.length && !rules.choices.includes(value)) return 'choice'
  if (row.valueType === 'BOOLEAN' && !['true', 'false'].includes(value)) return 'boolean'
  if (row.valueType === 'INTEGER' && !/^-?\d+$/.test(value)) return 'integer'
  if (row.valueType === 'DECIMAL' && !/^-?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][-+]?\d+)?$/.test(value)) return 'decimal'
  if (row.valueType === 'JSON') { try { JSON.parse(value) } catch { return 'json' } }
  if ((row.valueType === 'INTEGER' || row.valueType === 'DECIMAL') && value !== '') {
    if (rules.minimum !== null && compareDecimal(value, rules.minimum) === -1) return 'minimum'
    if (rules.maximum !== null && compareDecimal(value, rules.maximum) === 1) return 'maximum'
  }
  return null
}
export function layerEntry(view: EffectiveDictionary, code: string) { return view.layer.find(entry => entry.code === code) }
/** Parents offered for a private item: current selectable entries plus the tenant's own codes, never the item itself or its descendants. */
export function parentChoices(view: EffectiveDictionary, code: string | null) {
  if (!view.tree) return []
  const blocked = new Set<string>()
  if (code) {
    blocked.add(code)
    let changed = true
    while (changed) { changed = false; for (const entry of view.entries) if (entry.parentCode && blocked.has(entry.parentCode) && !blocked.has(entry.code)) { blocked.add(entry.code); changed = true } }
  }
  return view.entries.filter(entry => !blocked.has(entry.code) && (entry.selectable || entry.origin === 'TENANT_ONLY'))
}

