import { beforeEach, expect, it } from 'vitest'
import axios from 'axios'
import { createPinia, setActivePinia } from 'pinia'
import { configureHttp } from '../http'

beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); sessionStorage.clear() })
it('adds a random operation key to writes, preserves explicit keys, and leaves reads alone', async () => {
  const seen: unknown[] = []
  const client = configureHttp(axios.create({ adapter: async config => {
    seen.push(config.headers.get('Idempotency-Key'))
    return { status: 200, statusText: 'OK', config, headers: {}, data: { code: 0, data: null } }
  } }))
  await client.post('/config/parameters', {})
  await client.put('/config/parameters/1', {})
  await client.post('/config/parameters', {}, { headers: { 'Idempotency-Key': 'same-original-operation' } })
  await client.get('/config/parameters')
  expect(seen[0]).toMatch(/^[0-9a-f-]{36}$/)
  expect(seen[1]).toMatch(/^[0-9a-f-]{36}$/)
  expect(seen[0]).not.toBe(seen[1])
  expect(seen[2]).toBe('same-original-operation')
  expect(seen[3]).toBeUndefined()
})
