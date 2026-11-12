import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, disposePinia, getActivePinia, setActivePinia } from 'pinia'
import { http } from '../http'
import { saveToken } from '@/lib/token'
import { ruleWrite } from '@/features/rule/api'
import { parseExact, printExact } from '@/features/rule/exact'
import { cacheApi } from '@/features/monitor/cache'
const originalAdapter = http.defaults.adapter
beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); sessionStorage.clear(); saveToken('original-adapter-session', false) })
afterEach(() => { http.defaults.adapter = originalAdapter; disposePinia(getActivePinia()!) })

it.each(['rule', 'cache'])('rejects queued %s writes before a replacement session reaches the network', async kind => {
  const adapter = vi.fn(async config => ({ data: { code: 0, data: null }, status: 200, statusText: 'OK', headers: {}, config }))
  http.defaults.adapter = adapter
  const request = kind === 'rule' ? ruleWrite('/scenes', { name: 'captured' }) : cacheApi.submit('captured-task', 'original-intent-key')
  saveToken('new-adapter-session', false)
  await expect(request).rejects.toThrow(); expect(adapter).not.toHaveBeenCalled()
})
it('preserves exact rule numbers, original key and response metadata with a local identity binding', async () => {
  const received = vi.fn()
  http.defaults.adapter = async config => {
    expect(config.headers.Authorization).toBe('Bearer original-adapter-session')
    expect(config.headers['Idempotency-Key']).toBe('original-rule-key')
    expect(config.data).toBe('{"amount":12345678901234567890.123456789}')
    expect(Object.keys(config.headers.toJSON()).some(key => /identity/i.test(key))).toBe(false)
    return { data: '{"code":0,"data":{"value":12345678901234567890.123456789}}', status: 200, statusText: 'OK', headers: { 'x-operation-id': 'rule-receipt' }, config }
  }
  const result = await ruleWrite('/scenes', parseExact('{"amount":12345678901234567890.123456789}'), 'POST', { idempotencyKey: 'original-rule-key', onResponse: received })
  expect(printExact(result)).toBe('{"value":12345678901234567890.123456789}')
  expect(received).toHaveBeenCalledWith(expect.objectContaining({ operationId: 'rule-receipt' }))
})
it('preserves cache task key and path without adding current-node routing to a persisted submit', async () => {
  http.defaults.adapter = async config => {
    expect(config.url).toBe('/monitor/caches/tasks/task%3A1/submit')
    expect(config.headers['Idempotency-Key']).toBe('original-cache-key')
    expect(config.headers.Authorization).toBe('Bearer original-adapter-session')
    expect(config.headers['X-SMA-Cache-Node']).toBeUndefined(); expect(config.headers['X-SMA-Cache-Process']).toBeUndefined()
    return { data: { code: 0, data: { id: 'task:1', state: 'QUEUED' } }, status: 200, statusText: 'OK', headers: {}, config }
  }
  expect(await cacheApi.submit('task:1', 'original-cache-key')).toMatchObject({ id: 'task:1', state: 'QUEUED' })
})
