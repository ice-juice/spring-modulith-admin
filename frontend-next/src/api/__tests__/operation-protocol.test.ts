import { beforeEach, expect, it, vi } from 'vitest'
import axios, { AxiosError } from 'axios'
import { createPinia, setActivePinia } from 'pinia'
import { ApiError, configureHttp, createApi } from '../http'
import { responseMeta } from '../request-meta'
import { localFailure } from '@/lib/operation-result'

beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); sessionStorage.clear() })
it('preserves an explicit intent through every mutation helper and exposes bounded success metadata', async () => {
  const seen: string[] = [], meta = vi.fn()
  const client = configureHttp(axios.create({ adapter: async config => {
    seen.push(String(config.headers.get('Idempotency-Key')))
    return { config, status: 200, statusText: 'OK', headers: { 'x-operation-id': 'receipt-id', 'x-request-policy': 'LOCAL' }, data: { code: 0, data: { id: '9007199254740993' } } }
  } }))
  const api = createApi(client), options = { idempotencyKey: 'original-intent-uuid', onResponse: meta }
  expect(await api.post('/create', {}, options)).toEqual({ id: '9007199254740993' })
  await api.put('/change', {}, options); await api.patch('/patch', {}, options); await api.remove('/delete', options)
  expect(seen).toEqual(Array(4).fill(options.idempotencyKey))
  expect(meta).toHaveBeenCalledWith(expect.objectContaining({ status: 200, policy: 'LOCAL', operationId: 'receipt-id' }))
})
it('keeps accepted/unknown bodies as errors with retry metadata, including HTTP 202', async () => {
  const client = configureHttp(axios.create({ adapter: async config => ({ config, status: 202, statusText: 'Accepted',
    headers: { 'retry-after': '3', 'x-upload-id': 'upload-id', 'x-request-policy': 'NATIVE' },
    data: { code: 202, error: 'common.request.unknown', message: 'Unknown', data: { state: 'UNKNOWN' } } }) }))
  await expect(createApi(client).post('/upload')).rejects.toMatchObject({ key: 'common.request.unknown', meta: { status: 202, retryAfterSeconds: 3, uploadId: 'upload-id', policy: 'NATIVE' } })
})
it('does not expose a completed receipt as the original DTO or automatically retry', async () => {
  let calls = 0
  const receipt = { operationId: 'receipt-id', operation: 'config.save', state: 'SUCCEEDED', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:01Z' }
  const client = configureHttp(axios.create({ adapter: async config => {
    calls++
    throw new AxiosError('Conflict', 'ERR_BAD_REQUEST', config, undefined, { config, status: 409, statusText: 'Conflict', headers: { 'retry-after': '2' },
      data: { code: 409, error: 'common.request.completed', message: 'Completed', data: receipt } })
  } }))
  const error = await createApi(client).post('/create').catch(value => value)
  if (!(error instanceof ApiError)) throw new Error('Expected a protocol error')
  expect(error.meta).toMatchObject({ status: 409, retryAfterSeconds: 2 })
  expect(localFailure(error)).toBe('completed'); expect(calls).toBe(1)
})
it('discards malformed or unrelated headers instead of persisting arbitrary content', () => {
  expect(responseMeta({ status: 429, headers: { 'retry-after': '-1', 'x-operation-id': '<script>', 'x-request-policy': 'BYPASS', authorization: 'secret' } }))
    .toEqual({ status: 429, policy: undefined, operationId: undefined, uploadId: undefined, retryAfterSeconds: undefined })
})
