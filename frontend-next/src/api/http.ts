import axios, { type AxiosInstance, type AxiosResponse, type AxiosRequestConfig } from 'axios'
import { i18n } from '@/locales'
import { getToken, clearToken } from '@/lib/token'
import { usePreferences } from '@/stores/preferences'
import { responseMeta, type ResponseMeta } from './request-meta'
export type { ResponseMeta } from './request-meta'

export interface ApiResponse<T> {
  code: number
  error: string | null
  message: string
  data: T
}
export interface PageResult<T> {
  items: T[]
  total: string
  page: string
  size: string
}
export class ApiError extends Error {
  constructor(
    public code: number,
    public key: string | null,
    message: string,
    public details?: unknown,
    public meta: ResponseMeta = { status: code },
  ) {
    super(message)
  }
}
type IdentityBoundConfig = AxiosRequestConfig & { smaExpectedIdentity?: string | null }
/** Capture before Axios queues its interceptor chain. This property never becomes a transport header. */
export function bindRequestIdentity(config: AxiosRequestConfig, identity: string | null = getToken()): IdentityBoundConfig {
  return { ...config, smaExpectedIdentity: identity }
}
export function configureHttp(client: AxiosInstance) {
  client.interceptors.request.use((config) => {
    const token = getToken()
    const expected = (config as IdentityBoundConfig).smaExpectedIdentity
    if (expected !== undefined && expected !== token) {
      throw new Error(i18n.global.t('common.operation.identity'))
    }
    if (token) config.headers.Authorization = `Bearer ${token}`
    config.headers['Accept-Language'] = i18n.global.locale.value
    config.headers['X-Time-Zone'] = usePreferences().timeZone
    if (['post', 'put', 'patch', 'delete'].includes(config.method?.toLowerCase() || '') && !config.headers.has('Idempotency-Key')) {
      config.headers.set('Idempotency-Key', crypto.randomUUID())
    }
    return config
  })
  client.interceptors.response.use(
    async (response: AxiosResponse<ApiResponse<unknown>>) => {
      let body = response.data
      if (
        body instanceof Blob &&
        body.type.includes('json') &&
        !response.headers['content-disposition']
      ) {
        try {
          body = JSON.parse(await body.text())
        } catch {
          /* A binary response remains a binary response. */
        }
      }
      if (body && typeof body.code === 'number' && body.code !== 0) {
        throw new ApiError(body.code, body.error, body.message, body.data, responseMeta(response))
      }
      return response
    },
    async (error) => {
      if (axios.isAxiosError(error)) {
        const sentIdentity = error.config?.headers?.Authorization
        const currentIdentity = getToken()
        const ownsSession = currentIdentity ? sentIdentity === `Bearer ${currentIdentity}` : !sentIdentity
        if (error.response?.status === 401 && ownsSession) {
          clearToken()
          window.dispatchEvent(new Event('sma:unauthorized'))
        }
        if (error.response?.status === 423 && ownsSession) window.dispatchEvent(new Event('sma:locked'))
        let body = error.response?.data as ApiResponse<unknown> | undefined
        if (body instanceof Blob) {
          try {
            body = JSON.parse(await body.text())
          } catch {
            body = undefined
          }
        }
        return Promise.reject(
          new ApiError(
            error.response?.status || 0,
            body?.error || null,
            body?.message || i18n.global.t('common.networkError'),
            body?.data,
            responseMeta(error.response),
          ),
        )
      }
      return Promise.reject(error)
    },
  )
  return client
}
export const http = configureHttp(axios.create({ baseURL: '/api', timeout: 15000 }))
export interface RequestOptions extends Pick<AxiosRequestConfig, 'timeout' | 'signal'> {
  /** Local-only dispatch guard. Null explicitly binds an anonymous request; never persisted or sent as a header. */
  expectedIdentity?: string | null
  idempotencyKey?: string
  params?: unknown
  onResponse?: (meta: ResponseMeta) => void
}
/** Explicit original keys are supplied by the operation owner; this adapter never retries. */
export function createApi(client: AxiosInstance, scopedHeaders?: Readonly<Record<string, string>>) {
  const capturedHeaders = scopedHeaders ? { ...scopedHeaders } : undefined
  function config(options?: RequestOptions): IdentityBoundConfig {
    return bindRequestIdentity({ timeout: options?.timeout, signal: options?.signal, params: options?.params,
      headers: capturedHeaders || options?.idempotencyKey ? { ...capturedHeaders, ...(options?.idempotencyKey ? { 'Idempotency-Key': options.idempotencyKey } : {}) } : undefined }, options?.expectedIdentity)
  }
  async function unwrap<T>(request: Promise<AxiosResponse<ApiResponse<T>>>, options?: RequestOptions): Promise<T> {
    const response = await request
    options?.onResponse?.(responseMeta(response))
    return response.data.data
  }
  return {
    get: <T>(url: string, params?: unknown, options?: RequestOptions) => unwrap<T>(client.get(url, { ...config(options), params }), options),
    post: <T>(url: string, data?: unknown, options?: RequestOptions) => unwrap<T>(client.post(url, data, config(options)), options),
    put: <T>(url: string, data?: unknown, options?: RequestOptions) => unwrap<T>(client.put(url, data, config(options)), options),
    patch: <T>(url: string, data?: unknown, options?: RequestOptions) => unwrap<T>(client.patch(url, data, config(options)), options),
    remove: async (url: string, options?: RequestOptions): Promise<void> => { await unwrap(client.delete(url, config(options)), options) },
  }
}
export const { get, post, put, patch, remove } = createApi(http)
