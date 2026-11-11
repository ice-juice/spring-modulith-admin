import { beforeEach, expect, it } from 'vitest'
import axios from 'axios'
import { createPinia, setActivePinia } from 'pinia'
import { configureHttp } from '../http'

beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); sessionStorage.clear() })
it('keeps the legacy client compatible with guarded writes without replacing an explicit operation key', async () => {
  const seen: unknown[] = []
  const client = configureHttp(axios.create({ adapter: async config => {
    seen.push(config.headers.get('Idempotency-Key'))
    return { status: 200, statusText: 'OK', config, headers: {}, data: { code: 0, data: null } }
  } }))
  await client.post('/config/parameters', {})
  await client.post('/config/parameters', {}, { headers: { 'Idempotency-Key': 'original-operation-key' } })
  await client.get('/config/parameters')
  expect(seen[0]).toMatch(/^[0-9a-f-]{36}$/)
  expect(seen[1]).toBe('original-operation-key')
  expect(seen[2]).toBeUndefined()
})
