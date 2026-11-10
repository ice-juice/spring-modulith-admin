import axios, { type AxiosInstance, type AxiosResponse } from 'axios'
import { i18n } from '@/locales'
import { getToken, clearToken } from '@/lib/token'
import { usePreferences } from '@/stores/preferences'

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
  ) {
    super(message)
  }
}
export function configureHttp(client: AxiosInstance) {
  client.interceptors.request.use((config) => {
    const token = getToken()
    if (token) config.headers.Authorization = `Bearer ${token}`
    config.headers['Accept-Language'] = i18n.global.locale.value
    config.headers['X-Time-Zone'] = usePreferences().timeZone
    return config
  })
  client.interceptors.response.use(
    (response: AxiosResponse<ApiResponse<unknown>>) => {
      const body = response.data
      if (body && typeof body.code === 'number' && body.code !== 0) {
        throw new ApiError(body.code, body.error, body.message, body.data)
      }
      return response
    },
    (error) => {
      if (axios.isAxiosError(error)) {
        if (error.response?.status === 401) {
          clearToken()
          window.dispatchEvent(new Event('sma:unauthorized'))
        }
        const body = error.response?.data as ApiResponse<unknown> | undefined
        return Promise.reject(
          new ApiError(
            error.response?.status || 0,
            body?.error || null,
            body?.message || i18n.global.t('common.networkError'),
            body?.data,
          ),
        )
      }
      return Promise.reject(error)
    },
  )
  return client
}
export const http = configureHttp(axios.create({ baseURL: '/api', timeout: 15000 }))
export async function get<T>(url: string, params?: unknown): Promise<T> {
  return (await http.get<ApiResponse<T>>(url, { params })).data.data
}
export async function post<T>(url: string, data?: unknown): Promise<T> {
  return (await http.post<ApiResponse<T>>(url, data)).data.data
}
export async function put<T>(url: string, data?: unknown): Promise<T> {
  return (await http.put<ApiResponse<T>>(url, data)).data.data
}
export async function remove(url: string): Promise<void> {
  await http.delete(url)
}
