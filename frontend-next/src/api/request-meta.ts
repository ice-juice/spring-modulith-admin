import type { AxiosResponse } from 'axios'

export interface ResponseMeta {
  status: number
  policy?: 'LOCAL' | 'QUERY' | 'NATIVE'
  operationId?: string
  uploadId?: string
  retryAfterSeconds?: number
}

/** Only bounded protocol metadata is retained, never headers containing credentials. */
export function responseMeta(response?: Pick<AxiosResponse, 'status' | 'headers'>): ResponseMeta {
  const headers = response?.headers || {}
  const id = (name: string) => {
    const value = headers[name]
    return typeof value === 'string' && /^[A-Za-z0-9_.:-]{1,128}$/.test(value) ? value : undefined
  }
  const policy = headers['x-request-policy']
  const retry = headers['retry-after']
  const seconds = typeof retry === 'string' && /^\d{1,10}$/.test(retry) ? Number(retry) : undefined
  return {
    status: response?.status || 0,
    policy: policy === 'LOCAL' || policy === 'QUERY' || policy === 'NATIVE' ? policy : undefined,
    operationId: id('x-operation-id'), uploadId: id('x-upload-id'),
    retryAfterSeconds: seconds,
  }
}
