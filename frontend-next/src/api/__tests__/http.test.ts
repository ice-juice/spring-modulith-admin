import { beforeEach, expect, it, vi } from 'vitest'
import axios, { AxiosError, AxiosHeaders, type AxiosResponse } from 'axios'
import { createPinia, setActivePinia } from 'pinia'
import { configureHttp, createApi, ApiError } from '../http'
import { saveToken, getToken } from '@/lib/token'
import { i18n } from '@/locales'
import { usePreferences } from '@/stores/preferences'
beforeEach(() => {
  setActivePinia(createPinia())
  localStorage.clear()
  sessionStorage.clear()
})
it('captures scoped node headers per API without mutating other requests or losing original request keys', async () => {
  saveToken('scoped-test-token', false)
  const sent: { node: unknown; process: unknown; key: unknown; token: unknown; signal: unknown }[] = []
  const client = configureHttp(axios.create({ adapter: async config => {
    sent.push({ node: config.headers['X-SMA-Cache-Node'], process: config.headers['X-SMA-Cache-Process'], key: config.headers['Idempotency-Key'], token: config.headers.Authorization, signal: config.signal })
    return { data: { code: 0, data: 'done' }, status: 200, statusText: 'OK', headers: { 'x-operation-id': 'receipt-1' }, config }
  } }))
  const headers = { 'X-SMA-Cache-Node': 'node-a', 'X-SMA-Cache-Process': 'process-a' }
  const scoped = createApi(client, headers), plain = createApi(client), received = vi.fn(), abort = new AbortController()
  headers['X-SMA-Cache-Node'] = 'node-b'
  await scoped.put('/monitor/caches/config.preferences/policy', {}, { idempotencyKey: 'original-request-key', signal: abort.signal, onResponse: received })
  await plain.get('/monitor/caches/tasks')
  expect(sent[0]).toEqual({ node: 'node-a', process: 'process-a', key: 'original-request-key', token: 'Bearer scoped-test-token', signal: abort.signal })
  expect(sent[1]).toMatchObject({ node: undefined, process: undefined, key: undefined })
  expect(received).toHaveBeenCalledOnce()
})
it('keeps localized errors in binary downloads and activates the lock screen on HTTP 423', async () => {
  const locked = vi.fn()
  window.addEventListener('sma:locked', locked, { once: true })
  const client = configureHttp(
    axios.create({
      adapter: async (config) => {
        const response: AxiosResponse = {
          data: new Blob(
            [JSON.stringify({ code: 423, error: 'auth.error.screen-locked', message: 'Locked' })],
            { type: 'application/json' },
          ),
          status: 423,
          statusText: 'Locked',
          headers: new AxiosHeaders(),
          config,
        }
        throw new AxiosError('Locked', 'ERR_BAD_REQUEST', config, undefined, response)
      },
    }),
  )
  await expect(client.get('/download', { responseType: 'blob' })).rejects.toMatchObject({
    code: 423,
    message: 'Locked',
  })
  expect(locked).toHaveBeenCalledOnce()
})
it('rejects business errors returned as JSON blobs but preserves downloaded JSON files', async () => {
  const body = new Blob(
    [JSON.stringify({ code: 400, error: 'file.error.in-use', message: 'In use' })],
    { type: 'application/json' },
  )
  let attachment = false
  const client = configureHttp(
    axios.create({
      adapter: async (config) => ({
        data: body,
        status: 200,
        statusText: 'OK',
        headers: attachment ? { 'content-disposition': 'attachment; filename="data.json"' } : {},
        config,
      }),
    }),
  )
  await expect(client.get('/download')).rejects.toMatchObject({ key: 'file.error.in-use' })
  attachment = true
  expect((await client.get('/download')).data).toBe(body)
})
it('sends identity, selected language and user zone with every request', async () => {
  saveToken('test-token', false)
  i18n.global.locale.value = 'en-US'
  usePreferences().changeTimeZone('America/New_York')
  const client = configureHttp(
    axios.create({
      adapter: async (config) => {
        expect(config.headers.Authorization).toBe('Bearer test-token')
        expect(config.headers['Accept-Language']).toBe('en-US')
        expect(config.headers['X-Time-Zone']).toBe('America/New_York')
        return {
          data: { code: 0, data: { id: '9007199254740993' } },
          status: 200,
          statusText: 'OK',
          headers: {},
          config,
        }
      },
    }),
  )
  expect((await client.get('/probe')).data.data.id).toBe('9007199254740993')
})
it('retains stable business keys and field details', async () => {
  const client = configureHttp(
    axios.create({
      adapter: async (config) => ({
        data: {
          code: 400,
          error: 'iam.user.duplicate',
          message: 'Already exists',
          data: ['username'],
        },
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      }),
    }),
  )
  await expect(client.get('/probe')).rejects.toMatchObject({
    code: 400,
    key: 'iam.user.duplicate',
    details: ['username'],
  })
})
it('clears expired tokens and notifies the router on HTTP 401', async () => {
  saveToken('expired', true)
  const listener = vi.fn()
  window.addEventListener('sma:unauthorized', listener, { once: true })
  const client = configureHttp(
    axios.create({
      adapter: async (config) => {
        const response: AxiosResponse = {
          data: { code: 401, error: 'common.error.unauthorized', message: 'Sign in' },
          status: 401,
          statusText: 'Unauthorized',
          headers: new AxiosHeaders(),
          config,
        }
        throw new AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, undefined, response)
      },
    }),
  )
  await expect(client.get('/probe')).rejects.toBeInstanceOf(ApiError)
  expect(getToken()).toBeNull()
  expect(listener).toHaveBeenCalledOnce()
})
it('does not clear a new login when an earlier identity receives a late unauthorized response', async () => {
  saveToken('old-token', false)
  const client = configureHttp(axios.create({ adapter: async config => {
    saveToken('new-token', false)
    const response: AxiosResponse = { data: { code: 401, message: 'Expired old session' }, status: 401, statusText: 'Unauthorized', headers: new AxiosHeaders(), config }
    throw new AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, undefined, response)
  } }))
  await expect(client.get('/probe')).rejects.toBeInstanceOf(ApiError)
  expect(getToken()).toBe('new-token')
})
it('rejects a queued authenticated or anonymous operation before dispatch if the identity changed', async () => {
  const adapter = vi.fn(async config => ({ data: { code: 0, data: null }, status: 200, statusText: 'OK', headers: {}, config }))
  const client = configureHttp(axios.create({ adapter })), api = createApi(client)
  saveToken('old-session', false)
  const logout = api.post('/auth/logout', undefined, { expectedIdentity: 'old-session' })
  saveToken('replacement-session', false)
  await expect(logout).rejects.toThrow(); expect(adapter).not.toHaveBeenCalled()
  const login = api.post('/auth/login', { username: 'synthetic', password: 'not-a-real-secret' }, { expectedIdentity: null })
  await expect(login).rejects.toThrow(); expect(adapter).not.toHaveBeenCalled(); expect(getToken()).toBe('replacement-session')
})
it('keeps a matching identity binding local and sends only the normal Authorization header', async () => {
  saveToken('matching-session', false)
  const client = configureHttp(axios.create({ adapter: async config => {
    expect(config.headers.Authorization).toBe('Bearer matching-session')
    expect(Object.keys(config.headers.toJSON()).some(name => /identity|smaExpected/i.test(name))).toBe(false)
    expect(config.params).toBeUndefined(); expect(config.data).toBeUndefined()
    return { data: { code: 0, data: null }, status: 200, statusText: 'OK', headers: {}, config }
  } }))
  await createApi(client).post('/auth/logout', undefined, { expectedIdentity: 'matching-session' })
})

it.each(['get', 'post', 'put', 'patch', 'remove'] as const)('captures the default identity before queued %s dispatch without caller opt-in', async method => {
  const adapter = vi.fn(async config => ({ data: { code: 0, data: null }, status: 200, statusText: 'OK', headers: {}, config }))
  const api = createApi(configureHttp(axios.create({ adapter })))
  saveToken('captured-original', false)
  const request = api[method]('/queued')
  saveToken('different-current', false)
  await expect(request).rejects.toThrow(); expect(adapter).not.toHaveBeenCalled()
})

it('captures an anonymous default without silently adopting a later login', async () => {
  const adapter = vi.fn(async config => ({ data: { code: 0, data: null }, status: 200, statusText: 'OK', headers: {}, config }))
  const api = createApi(configureHttp(axios.create({ adapter })))
  const request = api.post('/anonymous-intent')
  saveToken('newly-authenticated', false)
  await expect(request).rejects.toThrow(); expect(adapter).not.toHaveBeenCalled()
})
