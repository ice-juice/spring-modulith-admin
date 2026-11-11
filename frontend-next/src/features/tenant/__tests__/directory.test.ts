import { describe, expect, it } from 'vitest'
import { canonicalHostname, mayCheck, mayEnable, newerVersion, tenantTransitions, type DomainRecord } from '../directory'

const domain = (overrides: Partial<DomainRecord> = {}): DomainRecord => ({ id: '9007199254740993123', tenantId: '9007199254740993124', hostname: 'a.example.test', portal: 'admin', status: 'PENDING', primaryDomain: false, verificationStatus: 'PENDING', tlsStatus: 'PENDING', provenance: 'REQUESTED', version: '9007199254740993123', dnsStatus: 'PENDING', verifiedAt: null, tlsValidUntil: null, challengeRecord: null, challengeValue: null, challengeExpiresAt: null, lastCheckId: null, checkStatus: 'IDLE', checkFailure: 'NONE', checkLeaseUntil: null, checkCompletedAt: null, activationReady: false, ...overrides })
describe('tenant directory decisions', () => {
  it('compares exact Long versions beyond the JavaScript safe integer range', () => {
    expect(newerVersion('9007199254740993124', '9007199254740993123')).toBe(true)
    expect(newerVersion('9007199254740993123', '9007199254740993123')).toBe(false)
    expect(newerVersion('9e25', '1')).toBe(false)
  })
  it('requires both tenant state and valid evidence, never the successful check label alone', () => {
    const tenant = { id: '1', code: 'demo', name: 'Demo', status: 'ACTIVE' as const }
    expect(mayEnable(domain({ checkStatus: 'SUCCEEDED' }), tenant)).toBe(false)
    expect(mayEnable(domain({ activationReady: true }), { ...tenant, status: 'PROVISIONING' })).toBe(false)
    expect(mayEnable(domain({ activationReady: true }), tenant)).toBe(true)
    expect(mayEnable(domain({ status: 'ACTIVE', activationReady: true }), tenant)).toBe(false)
  })
  it('does not start a check with disabled deployment, expired proof or existing work', () => {
    const row = domain({ challengeValue: 'proof', challengeExpiresAt: '2026-11-12T00:00:01Z' })
    const now = Date.parse('2026-11-12T00:00:00Z')
    expect(mayCheck(row, true, now)).toBe(true)
    expect(mayCheck(row, false, now)).toBe(false)
    expect(mayCheck(row, true, now + 1000)).toBe(false)
    expect(mayCheck({ ...row, checkStatus: 'RUNNING' }, true, now)).toBe(false)
    expect(mayCheck({ ...row, tlsStatus: 'LOCAL' }, true, now)).toBe(false)
  })
  it('limits state transitions and accepts only hostnames, including IDNA normalization', () => {
    expect(tenantTransitions('RETIRED')).toEqual([])
    expect(tenantTransitions('ACTIVE')).not.toContain('RETIRED')
    expect(canonicalHostname('Example.COM.')).toBe('example.com')
    expect(canonicalHostname('例子.测试')).toBe('xn--fsqu00a.xn--0zwm56d')
    for (const invalid of ['https://example.com', 'example.com:443', 'a/b', 'user@example.com', 'a?b', 'a#b', 'a b']) expect(canonicalHostname(invalid)).toBe('')
  })
})
