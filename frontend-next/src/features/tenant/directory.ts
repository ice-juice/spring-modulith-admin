import { get, post, put, type PageResult, type RequestOptions } from '@/api/http'

export type TenantStatus = 'PROVISIONING' | 'ACTIVE' | 'SUSPENDED' | 'MAINTENANCE' | 'RETIRED'
export interface TenantOption { id: string; code: string; name: string; status: TenantStatus }
export interface TenantRecord extends TenantOption { securityGeneration: string }
export interface DomainRecord {
  id: string; tenantId: string; hostname: string; portal: string; status: 'PENDING' | 'ACTIVE' | 'DISABLED'; primaryDomain: boolean
  verificationStatus: string; tlsStatus: string; provenance: string; version: string; dnsStatus: string
  verifiedAt: string | null; tlsValidUntil: string | null; challengeRecord: string | null; challengeValue: string | null
  challengeExpiresAt: string | null; lastCheckId: string | null; checkStatus: string; checkFailure: string
  checkLeaseUntil: string | null; checkCompletedAt: string | null; activationReady: boolean
}
export interface DomainCheck {
  id: string; domainId: string; operationId: string; status: 'RUNNING' | 'EXPIRED' | 'SUCCEEDED' | 'FAILED' | 'SUPERSEDED'
  failureCode: string; ownershipVerified: boolean; dnsReady: boolean; tlsReady: boolean; tlsValidUntil: string | null
  addresses: string[]; fingerprints: string[]; createdAt: string; leaseUntil: string; completedAt: string | null
}
export interface VerificationSettings { configured: boolean; ingressAddresses: string[] }
const domainPath = (id: string) => '/tenant/domains/' + encodeURIComponent(id)
export const directory = {
  tenants: (page = 1, keyword = '') => get<PageResult<TenantRecord>>('/tenant/tenants', { page, size: 20, keyword }),
  createTenant: (input: { code: string; name: string }, options?: RequestOptions) => post<TenantRecord>('/tenant/tenants', input, options),
  status: (row: TenantRecord, status: TenantStatus, options?: RequestOptions) => put<TenantRecord>('/tenant/tenants/' + encodeURIComponent(row.id) + '/status', { expectedGeneration: row.securityGeneration, status }, options),
  options: (page = 1, keyword = '') => get<PageResult<TenantOption>>('/tenant/domains/tenant-options', { page, size: 20, keyword }),
  domains: (tenantId: string) => get<DomainRecord[]>('/tenant/domains', { tenantId }),
  domain: (id: string) => get<DomainRecord>(domainPath(id)),
  settings: () => get<VerificationSettings>('/tenant/domains/verification-settings'),
  createDomain: (input: { tenantId: string; hostname: string; portal: string }, options?: RequestOptions) => post<DomainRecord>('/tenant/domains', input, options),
  challenge: (row: DomainRecord, options?: RequestOptions) => post<DomainRecord>(domainPath(row.id) + '/challenges', { expectedVersion: row.version }, options),
  verify: (row: DomainRecord, operationId: string, options?: RequestOptions) => post<DomainCheck>(domainPath(row.id) + '/checks', { expectedVersion: row.version, operationId }, options),
  check: (id: string, operationId: string) => get<DomainCheck>(domainPath(id) + '/checks/' + encodeURIComponent(operationId)),
  enabled: (row: DomainRecord, enabled: boolean, options?: RequestOptions) => put<DomainRecord>(domainPath(row.id) + '/enabled', { expectedVersion: row.version, enabled }, options),
  primary: (row: DomainRecord, options?: RequestOptions) => put<DomainRecord>(domainPath(row.id) + '/primary', { expectedVersion: row.version }, options),
}
export function tenantTransitions(status: TenantStatus): TenantStatus[] {
  return ({ PROVISIONING: ['ACTIVE', 'RETIRED'], ACTIVE: ['SUSPENDED', 'MAINTENANCE'], MAINTENANCE: ['ACTIVE', 'SUSPENDED'], SUSPENDED: ['ACTIVE', 'RETIRED'], RETIRED: [] } satisfies Record<TenantStatus, TenantStatus[]>)[status] ?? []
}
export function newerVersion(current: string, previous: string) {
  return /^\d+$/.test(current) && /^\d+$/.test(previous) && BigInt(current) > BigInt(previous)
}
export function mayEnable(row: DomainRecord, tenant: TenantOption | undefined) { return row.status !== 'ACTIVE' && row.activationReady && tenant?.status === 'ACTIVE' }
export function terminalCheck(check: DomainCheck) { return check.status !== 'RUNNING' }
export function mayCheck(row: DomainRecord, configured: boolean, now = Date.now()) {
  return configured && row.tlsStatus !== 'LOCAL' && row.checkStatus !== 'RUNNING' && !!row.challengeValue && !!row.challengeExpiresAt && Date.parse(row.challengeExpiresAt) > now
}
export function canonicalHostname(value: string) {
  const host = value.trim().replace(/\.$/, '').toLowerCase()
  if (!host || /[\s/\\:@?#]/.test(host)) return ''
  try { const url = new URL('https://' + host); return url.hostname } catch { return '' }
}
