import axios, { type AxiosResponse } from 'axios'

/** Unified response body produced by the backend (com.sma.admin.common.ApiResponse). */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export const http = axios.create({ baseURL: '/api', timeout: 15000 })

http.interceptors.response.use((response: AxiosResponse<ApiResponse<unknown>>) => {
  const body = response.data
  if (body && typeof body.code === 'number' && body.code !== 0) {
    return Promise.reject(new Error(body.message))
  }
  return response
})
