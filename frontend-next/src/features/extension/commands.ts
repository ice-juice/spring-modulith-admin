export interface Field { key: string; type?: 'password' | 'number' | 'boolean' | 'list' | 'json'; value: unknown }
export const definitions: Record<string, Field[]> = {
  GUARD_CONFIGURE: [{key:'deploymentId',value:''},{key:'revision',type:'number',value:1},{key:'minimumCalls',type:'number',value:100},{key:'maximumErrorBasisPoints',type:'number',value:100},{key:'maximumSlowBasisPoints',type:'number',value:500},{key:'automaticRollback',type:'boolean',value:false},{key:'expiresAt',value:''}],
  GROUP_CREATE: [{ key: 'name', value: '' }, { key: 'enabled', type: 'boolean', value: true }],
  GROUP_UPDATE: [{ key: 'name', value: '' }, { key: 'enabled', type: 'boolean', value: true }],
  PLUGIN_CREATE: [{ key: 'name', value: '' }],
  GROUP_MOVE: [{ key: 'targetGroupId', value: '' }, { key: 'targetGroupRevision', type: 'number', value: 1 }],
  GROUP_GRANT: [{ key: 'userId', value: '' }, { key: 'actions', type: 'list', value: 'UPLOAD, VALIDATE' }, { key: 'read', type: 'boolean', value: true }],
  USER_CREATE: [{ key: 'username', value: '' }, { key: 'operationPassword', type: 'password', value: '' }, { key: 'securityAdmin', type: 'boolean', value: false }],
  IDENTITY_BIND: [{ key: 'machineId', value: 'admin-console' }, { key: 'tenantId', value: '1' }, { key: 'portal', value: 'admin' }, { key: 'hostUserId', value: '' }, { key: 'userId', value: '' }, { key: 'expectedRevision', type: 'number', value: 0 }, { key: 'enabled', type: 'boolean', value: true }],
  OPERATION_PASSWORD_UPDATE: [{ key: 'newOperationPassword', type: 'password', value: '' }],
  USER_UPDATE: [{ key: 'userId', value: '' }, { key: 'expectedCredentialVersion', type: 'number', value: 1 }, { key: 'enabled', type: 'boolean', value: true }, { key: 'securityAdmin', type: 'boolean', value: false }],
  FACTOR_RESET: [{ key: 'userId', value: '' }, { key: 'expectedCredentialVersion', type: 'number', value: 1 }, { key: 'newOperationPassword', type: 'password', value: '' }],
  MACHINE_CREATE: [{ key: 'id', value: '' }, { key: 'kind', value: 'NODE' }, { key: 'environments', type: 'list', value: 'production' }],
  MACHINE_UPDATE: [{ key: 'id', value: '' }, { key: 'expectedRevision', type: 'number', value: 1 }, { key: 'environments', type: 'list', value: 'production' }, { key: 'enabled', type: 'boolean', value: true }],
  MACHINE_ROTATE: [{ key: 'id', value: '' }, { key: 'expectedRevision', type: 'number', value: 1 }],
  MACHINE_ACTIVATE: [{ key: 'id', value: '' }, { key: 'expectedRevision', type: 'number', value: 1 }, { key: 'rotationId', value: '' }],
  DEPENDENCY_REGISTER: [{ key: 'coordinate', value: '' }, { key: 'sha256', value: '' }],
  TRUST_UPDATE: [{ key: 'operation', value: 'stage' }, { key: 'family', value: 'execution' }, { key: 'keyId', value: '' }],
  CREDENTIAL_UPDATE: [{ key: 'currentLoginPassword', type: 'password', value: '' }, { key: 'newLoginPassword', type: 'password', value: '' }, { key: 'newOperationPassword', type: 'password', value: '' }],
  POLICY_UPDATE: [{ key: 'document', type: 'json', value: '{}' }],
  EXCEPTION_CREATE: [{ key: 'document', type: 'json', value: '{}' }],
}
export function fieldBody(fields: Field[], values: Record<string, unknown>): unknown {
  if (fields.length === 1 && fields[0]?.type === 'json') return JSON.parse(String(values.document))
  return Object.fromEntries(fields.map(f => {
    const value = values[f.key]
    if (f.type === 'number') { const n = Number(value); if (!Number.isSafeInteger(n) || n < 0) throw new Error('invalid-number'); return [f.key, n] }
    if (f.type === 'list') return [f.key, [...new Set(String(value).split(',').map(s => s.trim()).filter(Boolean))].sort()]
    if (f.type === 'boolean') return [f.key, !!value]
    return [f.key, f.key.startsWith('new') && !value ? null : value]
  }))
}
