import { beforeEach, expect, it, vi } from 'vitest'
import axios, { AxiosError, AxiosHeaders, type AxiosResponse } from 'axios'
import { createPinia, setActivePinia } from 'pinia'
import { configureHttp, ApiError } from '../http'
import { saveToken, getToken } from '@/lib/token'
import { i18n } from '@/locales'
import { usePreferences } from '@/stores/preferences'
beforeEach(() => {
  setActivePinia(createPinia())
  localStorage.clear()
  sessionStorage.clear()
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
