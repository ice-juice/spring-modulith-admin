import { ApiError } from '@/api/http'

export interface OperationReceipt {
  operationId: string
  operation: string
  state: 'RUNNING' | 'UNKNOWN' | 'SUCCEEDED' | 'FAILED_FINAL'
  createdAt: string
  updatedAt: string
}
export function operationReceipt(value: unknown): OperationReceipt | undefined {
  if (!value || typeof value !== 'object') return
  const row = value as Partial<OperationReceipt>
  if (typeof row.operationId !== 'string' || !/^[A-Za-z0-9_.:-]{1,128}$/.test(row.operationId)
    || typeof row.operation !== 'string' || row.operation.length > 160
    || !['RUNNING', 'UNKNOWN', 'SUCCEEDED', 'FAILED_FINAL'].includes(row.state || '')
    || typeof row.createdAt !== 'string' || !Number.isFinite(Date.parse(row.createdAt))
    || typeof row.updatedAt !== 'string' || !Number.isFinite(Date.parse(row.updatedAt))) return
  return { operationId: row.operationId, operation: row.operation, state: row.state!, createdAt: row.createdAt, updatedAt: row.updatedAt }
}

export type LocalOutcome = 'completed' | 'failed' | 'rejected' | 'pending' | 'unknown'
/** LOCAL only. NATIVE adapters must interpret their own task/one-time protocol. */
export function localFailure(cause: unknown, previouslyUnknown = false): LocalOutcome {
  if (!(cause instanceof ApiError)) return 'unknown'
  const receipt = operationReceipt(cause.details)
  if (cause.key === 'common.request.completed' && receipt?.state === 'SUCCEEDED') return 'completed'
  if (cause.key === 'common.request.failed' && receipt?.state === 'FAILED_FINAL') return 'failed'
  if (cause.key === 'common.request.processing') return 'pending'
  if (previouslyUnknown || ['common.request.unknown', 'common.request.conflict', 'common.request.completed', 'common.request.failed'].includes(cause.key || '')) return 'unknown'
  if (cause.meta.status === 0 || cause.meta.status >= 500 || cause.meta.status === 429 || cause.meta.status === 202) return 'unknown'
  // A first, definitive LOCAL validation/permission/business rejection has no earlier ambiguous attempt.
  if (cause.meta.status >= 400 && cause.meta.status < 500 || cause.meta.status === 200 && cause.code !== 0) return 'rejected'
  return 'unknown'
}
