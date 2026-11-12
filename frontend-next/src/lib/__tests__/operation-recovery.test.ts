import { expect, it } from 'vitest'
import { operationRecoveryPath, permittedRecoveryPath } from '../operation-recovery'
const row = (resource: string, operation = 'operation') => ({ resource, operation })

it('returns only code-owned paths without copying opaque keys or URLs', () => {
  for (const [resource, expected] of [
    ['config.parameters.maintenance', '/config/parameters'], ['auth.sessions.other', '/auth/sessions'],
    ['tenant.domains.9007199254740993123', '/tenant/domains'], ['monitor.caches.region-a', '/monitor/caches'],
    ['file.avatar.self.none', '/profile'], ['file.brand.logo.12', '/tenant/brand'], ['file.notice.new.uuid', '/message/notices'],
    ['monitor.publications.event-id', '/monitor/events'], ['extension.console.test.plugin', '/extension/manage'],
  ]) expect(operationRecoveryPath(row(resource!))).toBe(expected)
  expect(operationRecoveryPath(row('https://evil.example/path'))).toBeUndefined()
  expect(operationRecoveryPath(row('config.parameters-evil.x'))).toBeUndefined()
  expect(operationRecoveryPath(row('unregistered.business.1'))).toBeUndefined()
  expect(operationRecoveryPath(row('rule.assets.1', 'rule.testcase-save'))).toBeUndefined()
  expect(operationRecoveryPath(row('rule.assets.1', 'rule.template-publish'))).toBe('/rule/templates')
  expect(operationRecoveryPath(row('rule.batch.1', 'rule.batch'))).toBe('/rule/operations')
})

it('requires a registered protected destination and current route permission, including existing alternatives', () => {
  const record = row('monitor.caches.region-a')
  const resolve = () => ({ matched: [{}], meta: { permission: 'monitor:cache:view', anyPermission: ['monitor:cache:manage'] } })
  expect(permittedRecoveryPath(record, resolve, () => false)).toBeUndefined()
  expect(permittedRecoveryPath(record, resolve, key => key === 'monitor:cache:view')).toBe('/monitor/caches')
  expect(permittedRecoveryPath(record, resolve, key => key === 'monitor:cache:manage')).toBe('/monitor/caches')
  expect(permittedRecoveryPath(record, () => ({ matched: [], meta: {} }), () => true)).toBeUndefined()
  expect(permittedRecoveryPath(record, () => ({ matched: [{}], meta: { public: true } }), () => true)).toBeUndefined()
  expect(permittedRecoveryPath(row('config.preferences.self'), () => ({ matched: [{}], meta: {} }), () => false)).toBe('/preferences')
})
