import { describe, expect, it } from 'vitest'
import { newerRevision, parameterProblem, parentChoices, sameRevision, type EffectiveDictionary } from '../layers'

const constraints = { nullable: false, allowEmpty: false, maxLength: 10, minimum: null, maximum: null, choices: [] as string[] }
describe('configuration layers', () => {
  it('compares long revisions without Number rounding', () => {
    const big = { platform: '9007199254740993', tenant: '9007199254740993' }
    expect(sameRevision(big, { platform: '9007199254740993', tenant: '9007199254740993' })).toBe(true)
    expect(sameRevision(big, { platform: '9007199254740992', tenant: '9007199254740993' })).toBe(false)
    expect(newerRevision({ platform: '2', tenant: '5' }, { platform: '2', tenant: '4' })).toBe(true)
    expect(newerRevision({ platform: '2', tenant: '4' }, { platform: '2', tenant: '4' })).toBe(false)
  })
  it('distinguishes explicit null, empty and typed values', () => {
    expect(parameterProblem({ valueType: 'STRING', constraints }, null)).toBe('null')
    expect(parameterProblem({ valueType: 'STRING', constraints: { ...constraints, nullable: true } }, null)).toBeNull()
    expect(parameterProblem({ valueType: 'STRING', constraints }, '')).toBe('empty')
    expect(parameterProblem({ valueType: 'STRING', constraints: { ...constraints, allowEmpty: true } }, '')).toBeNull()
    expect(parameterProblem({ valueType: 'BOOLEAN', constraints }, 'yes')).toBe('boolean')
    expect(parameterProblem({ valueType: 'INTEGER', constraints: { ...constraints, minimum: '60', maximum: '120' } }, '30')).toBe('minimum')
    expect(parameterProblem({ valueType: 'INTEGER', constraints }, '1.5')).toBe('integer')
    expect(parameterProblem({ valueType: 'JSON', constraints }, '{')).toBe('json')
    expect(parameterProblem({ valueType: 'STRING', constraints: { ...constraints, choices: ['a'] } }, 'b')).toBe('choice')
    expect(parameterProblem({ valueType: 'STRING', constraints }, 'x'.repeat(11))).toBe('length')
  })
  it('checks numeric bounds without rounding large integers or precise decimals', () => {
    const wide = { ...constraints, maxLength: 40, minimum: '9007199254740993', maximum: '9007199254740995' }
    expect(parameterProblem({ valueType: 'INTEGER', constraints: wide }, '9007199254740992')).toBe('minimum')
    expect(parameterProblem({ valueType: 'INTEGER', constraints: wide }, '9007199254740994')).toBeNull()
    expect(parameterProblem({ valueType: 'INTEGER', constraints: wide }, '9007199254740996')).toBe('maximum')
    const decimal = { ...constraints, maxLength: 40, minimum: '0.10000000000000001', maximum: '1.2' }
    expect(parameterProblem({ valueType: 'DECIMAL', constraints: decimal }, '0.1')).toBe('minimum')
    expect(parameterProblem({ valueType: 'DECIMAL', constraints: decimal }, '1.20')).toBeNull()
    expect(parameterProblem({ valueType: 'DECIMAL', constraints: decimal }, '1.201')).toBe('maximum')
  })
  it('offers private parents without cycles or unselectable shared items', () => {
    const entry = (code: string, parentCode: string | null, origin: 'PLATFORM' | 'TENANT_ONLY', selectable = true) => ({ code, parentCode, label: code, enabled: true, hidden: !selectable, selectable, sort: 0, origin })
    const view = { key: 'k', owner: 'config', mode: 'EXTENDABLE', tree: true, name: 'k', label: { text: 'k', translations: {} }, enabled: true, revision: { platform: '1', tenant: '0' },
      entries: [entry('root', null, 'PLATFORM'), entry('hidden', null, 'PLATFORM', false), entry('mine', 'root', 'TENANT_ONLY'), entry('child', 'mine', 'TENANT_ONLY')], layer: [] } satisfies EffectiveDictionary
    expect(parentChoices(view, null).map(item => item.code)).toEqual(['root', 'mine', 'child'])
    expect(parentChoices(view, 'mine').map(item => item.code)).toEqual(['root'])
    expect(parentChoices({ ...view, tree: false }, null)).toEqual([])
  })
})

