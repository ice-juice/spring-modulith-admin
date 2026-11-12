import { ApiError } from '@/api/http'
export interface CredentialTicket { key: string; token: string; current: () => boolean }
export { useCredentialIntents } from './shared-credential-intents'
export { useSessionLockIntents } from './shared-session-lock-intents'
export function credentialOutcomeUnknown(cause: unknown) {
  return !(cause instanceof ApiError) || [0, 202, 408, 409].includes(cause.code) || cause.code >= 500
}