/** Stateful mock of the P10 effective/shared configuration contract; versions advance like the server and stale writes fail. */
export function createConfigLayers() {
  const label = (text: string, en?: string) => ({ text, messageKey: null, translations: en ? { 'en-US': en } : {} })
  const constraints = { nullable: true, allowEmpty: true, maxLength: 4000, minimum: null, maximum: null, choices: [] as string[] }
  const parameters = [
    { key: 'ui.theme', owner: 'config', groupKey: 'appearance', name: '默认主题', defaultName: '默认主题', nameKey: null, translations: {}, sort: 0, scope: 'PLATFORM_SHARED', valueType: 'STRING', overridePolicy: 'OVERRIDABLE', constraints, securitySensitive: false, value: 'light', origin: 'PLATFORM', revision: { platform: '1', tenant: '0' }, rowId: null as string | null, overridden: false },
    { key: 'auth.sessionTtlSeconds', owner: 'config', groupKey: 'security', name: '普通登录会话时长（秒）', defaultName: '普通登录会话时长（秒）', nameKey: null, translations: {}, sort: 0, scope: 'PLATFORM_SHARED', valueType: 'INTEGER', overridePolicy: 'LOCKED', constraints: { ...constraints, nullable: false, allowEmpty: false, minimum: '60', maximum: '31536000' }, securitySensitive: true, value: '28800', origin: 'PLATFORM', revision: { platform: '1', tenant: '0' }, rowId: null as string | null, overridden: false },
  ]
  let tenant = 0n
  const shared = { platform: '1', theme: 'light' }
  const entries = () => [
    { code: '1', parentCode: null, label: dictionary.override ? '先生' : '男', enabled: true, hidden: false, selectable: true, sort: 0, origin: dictionary.override ? 'TENANT_OVERRIDE' : 'PLATFORM' },
    { code: '2', parentCode: null, label: '女', enabled: true, hidden: false, selectable: true, sort: 1, origin: 'PLATFORM' },
    ...dictionary.privates.map(item => ({ code: item.code, parentCode: item.parentCode, label: item.label.text, enabled: item.enabled, hidden: false, selectable: item.enabled, sort: item.sort, origin: 'TENANT_ONLY' })),
  ]
  const dictionary = { override: false, privates: [] as { code: string; parentCode: string | null; label: { text: string }; enabled: boolean; sort: number }[], enabled: true }
  const revision = () => ({ platform: shared.platform, tenant: String(tenant) })
  const view = () => ({ key: 'sex', owner: 'config', mode: 'EXTENDABLE', tree: false, name: '性别', label: label('性别'), enabled: dictionary.enabled, revision: revision(), entries: entries(),
    layer: [...(dictionary.override ? [{ kind: 'OVERRIDE', code: '1', parentCode: null, label: label('先生'), sort: null, hidden: null, enabled: null }] : []), ...dictionary.privates.map(item => ({ kind: 'PRIVATE', ...item, hidden: null }))] })
  const head = { id: '9007199254740993001', kind: 'PARAMETER', key: 'ui.theme', owner: 'config', label: label('默认主题', 'Default theme'), draftVersion: '1', publishedRevisionId: '9007199254740993101', publishedRevision: '1', codeDefaultsChanged: false }
  let draft = 'light'
  const revisions = [{ id: '9007199254740993101', revision: '1', contentBytes: 64, createdAt: '2026-11-10T08:00:00Z', createdBy: '0' }]
  const versions = (body: Record<string, unknown>) => (body.versions as Record<string, unknown>) || body
  const current = (body: Record<string, unknown>) => { const value = versions(body); return value.expectedPlatformRevision === shared.platform && value.expectedTenantRevision === String(tenant) }
  const sync = () => { for (const row of parameters) { row.revision = revision(); if (row.key === 'ui.theme' && !row.overridden) row.value = shared.theme } }
  function handle(path: string, method: string, body: Record<string, unknown>, url: URL): unknown {
    sync()
    if (path === '/config/effective-parameters') { const keyword = (url.searchParams.get('keyword') || '').toLowerCase(); const items = parameters.filter(row => row.key.toLowerCase().includes(keyword)); return { items, page: '1', size: '50', total: String(items.length) } }
    const parameter = /^\/config\/effective-parameters\/([^/]+)(?:\/(override|inherit))?$/.exec(path)
    if (parameter) {
      const row = parameters.find(item => item.key === decodeURIComponent(parameter[1] || ''))
      if (!row) return undefined
      if (method === 'GET') return row
      if (!current(body)) return null
      tenant++
      if (parameter[2] === 'override') Object.assign(row, { value: body.value as string | null, origin: 'TENANT_OVERRIDE', overridden: true, rowId: '9007199254740993201' })
      else Object.assign(row, { value: shared.theme, origin: 'PLATFORM', overridden: false })
      sync(); return row
    }
    if (path === '/config/effective-dictionaries') return { items: [{ key: 'sex', owner: 'config', mode: 'EXTENDABLE', tree: false, name: '性别', enabled: dictionary.enabled, revision: revision(), entries: entries().length, selectable: entries().filter(item => item.selectable).length, overridden: dictionary.override ? 1 : 0, privateItems: dictionary.privates.length }], page: '1', size: '50', total: '1' }
    if (path === '/config/effective-dictionaries/sex') return view()
    const write = /^\/config\/effective-dictionaries\/sex\/(item-overrides(?:\/restore)?|private-items|enabled)$/.exec(path)
    if (write) {
      if (!current(body)) return null
      tenant++
      if (write[1] === 'item-overrides') dictionary.override = true
      else if (write[1] === 'item-overrides/restore') dictionary.override = false
      else if (write[1] === 'enabled') dictionary.enabled = body.enabled === true
      else { const item = { code: String(body.code), parentCode: (body.parentCode as string | null) ?? null, label: body.label as { text: string }, enabled: body.enabled === true, sort: Number(body.sort) }; dictionary.privates = [...dictionary.privates.filter(row => row.code !== item.code), item] }
      return view()
    }
    if (path === '/config/shared-configurations') return { items: [head], page: '1', size: '50', total: '1' }
    if (path === '/config/shared-configurations/' + head.id) return { ...head, parameterDefinition: { owner: 'config', key: 'ui.theme', group: 'appearance', label: head.label, scope: 'PLATFORM_SHARED', valueType: 'STRING', overridePolicy: 'OVERRIDABLE', defaultValue: 'light', constraints, securitySensitive: false, publicProjection: 'NONE' }, dictionaryDefinition: null, parameterDraft: { schemaVersion: 1, label: head.label, value: draft }, dictionaryDraft: null }
    if (path === '/config/shared-configurations/' + head.id + '/revisions') return { items: [...revisions].reverse(), page: '1', size: '20', total: String(revisions.length) }
    if (path.startsWith('/config/shared-configurations/' + head.id + '/revisions/')) return { id: path.split('/').pop(), revision: '1', parameter: { schemaVersion: 1, label: head.label, value: 'light' }, dictionary: null, createdAt: '2026-11-10T08:00:00Z' }
    if (path === '/config/shared-configurations/' + head.id + '/parameter-draft') {
      if (body.expectedVersion !== head.draftVersion) return null
      draft = String((body.content as Record<string, unknown>).value); head.draftVersion = String(BigInt(head.draftVersion) + 1n); return handle('/config/shared-configurations/' + head.id, 'GET', {}, url)
    }
    if (path === '/config/shared-configurations/' + head.id + '/publications') {
      if (body.expectedDraftVersion !== head.draftVersion || body.expectedPublishedRevisionId !== head.publishedRevisionId) return null
      const next = { id: String(BigInt(head.publishedRevisionId) + 1n), revision: String(BigInt(head.publishedRevision) + 1n), contentBytes: 64, createdAt: '2026-11-10T09:00:00Z', createdBy: '101' }
      revisions.push(next); head.publishedRevisionId = next.id; head.publishedRevision = next.revision; shared.platform = next.revision; shared.theme = draft
      return { revisionId: next.id, revision: next.revision }
    }
    return undefined
  }
  return { handle, parameters, dictionary, head }
}

