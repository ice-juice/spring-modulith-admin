import axios from 'axios'
import { ref } from 'vue'
import { ApiError, type ApiResponse } from '@/api/http'
import { i18n } from '@/locales'
import { getToken } from '@/lib/token'

export interface Scope { groupId: string; environment: string; actions: string[]; read: boolean }
export interface Access { userId: string; username: string; securityAdmin: boolean; scopes: Scope[]; environments: string[] }
export interface Group { id: string; name: string; revision: number; enabled: boolean }
export interface Plugin { id: string; name: string; revision: number; groupId: string }
export interface Artifact { id: string; name: string; pluginId: string; environment: string; sha256: string; bytes: number; state: string; manifest: { version: string } | null; createdAt: string }
export interface Policy { revision: number; jarBytes: number; sourceBytes: number; sourceBatchBytes: number; batchBytes: number; batchFiles: number; [key: string]: number }
export interface Intent { action: string; environment: string; groupId: string; groupRevision: number; pluginId: string; expectedRevision: number; policyRevision: number; files: FileIntent[]; parametersSha256: string }
export interface FileIntent { name: string; sha256: string; bytes: number }
export interface Receipt { id: string; state: string; resourceId: string; createdAt: string }
export interface Prepared { intent: Intent; parameters: string; idempotencyKey: string; challengeId: string; expiresAt: string }
export interface Bundle { id: string; members: { pluginId: string; artifactId: string; sha256: string }[] }
export interface Release { deploymentId: string; revision: number; previousRevision: number; policyRevision: number; stable: Bundle; canary: Bundle | null; canaryBasisPoints: number; bucketSalt: string; canarySubjects: string[]; targetNodes: string[]; configurationSha256: string }
export interface Deployment { id: string; revision: number; active: Release | null; pending: { actionId: string; state: string; expiresAt: string; readyNodes: string[]; release: Release } | null }
export const releaseAccess = ref<Access | null>(null)
export const releaseIdentity = ref<{ enrolled: boolean } | null>(null)
let generation = 0
// Host credentials go only to the host's authenticated, allowlisted proxy. Release tokens stay server-side.
export const releaseHttp = axios.create({ baseURL: '/api/extension/consoles', timeout: 55000, withCredentials: false })
function rejection(code?: string | null) {
  const messages: Record<string, string> = { 'extension.error.console-unconfigured': 'notConfigured', 'extension.error.console-unavailable': 'serviceUnavailable', 'console-identity-unbound': 'identityUnbound', 'console-identity-disabled': 'identityDisabled', 'console-enrollment-required': 'enrollmentRequired' }
  const message = code && (messages[code] || messages[code.replace(/^extension\./, '')])
  return message ? i18n.global.t('extension.' + message) : i18n.global.t('extension.requestRejected', { code: code?.replace(/^extension\./,'') || 'request-failed' })
}
export function releaseGuard() { const current = generation, token = getToken(); return () => current === generation && token === getToken() }
export function clearReleaseSession() { releaseAccess.value = null; releaseIdentity.value = null; generation++ }
for (const event of ['sma:unauthorized', 'sma:locked']) window.addEventListener(event, clearReleaseSession)
export async function releaseRequest<T>(method: 'GET' | 'POST' | 'PUT', path: string, data?: unknown): Promise<T> {
  const current = generation, sentToken = getToken()
  if (!sentToken) throw new ApiError(401, 'unauthenticated', i18n.global.t('extension.sessionChanged'))
  try {
    if (current !== generation || sentToken !== getToken()) throw new ApiError(409, 'stale-session', i18n.global.t('extension.sessionChanged'))
    const result = await releaseHttp.request<ApiResponse<T>>({ method: 'POST', url: '/requests', data, headers: {
      Authorization: `Bearer ${sentToken}`, 'Accept-Language': i18n.global.locale.value,
      'X-Release-Method': method, 'X-Release-Path': path, 'Content-Type': data instanceof Blob ? 'application/octet-stream' : 'application/json',
    } })
    if (current !== generation || sentToken !== getToken()) throw new ApiError(409, 'stale-session', i18n.global.t('extension.sessionChanged'))
    if (result.data.code !== 0) throw new ApiError(result.data.code, result.data.error, rejection(result.data.error))
    return result.data.data
  } catch (error) {
    if (axios.isAxiosError(error)) {
      if (error.response?.status === 401 && current === generation) clearReleaseSession()
      const body = error.response?.data as ApiResponse<unknown> | undefined
      throw new ApiError(error.response?.status || 0, body?.error || null, body?.error ? rejection(body.error) : i18n.global.t('common.networkError'))
    }
    throw error
  }
}
export async function connectRelease() {
  const current = generation
  const [access, identity] = await Promise.all([releaseRequest<Access>('GET', '/console/access'), releaseRequest<{ enrolled: boolean }>('GET', '/console/identity')])
  if (current !== generation) throw new ApiError(409, 'stale-session', i18n.global.t('extension.sessionChanged'))
  releaseIdentity.value = identity
  if (JSON.stringify(releaseAccess.value) !== JSON.stringify(access)) {
    if (releaseAccess.value) { generation++; window.dispatchEvent(new Event('sma:release-access-changed')) }
    releaseAccess.value = access
  }
}
export function allowed(action: string, group: string, environment: string) { return !!releaseIdentity.value?.enrolled && !!releaseAccess.value && (releaseAccess.value.securityAdmin || releaseAccess.value.scopes.some(s => s.groupId === group && s.environment === environment && s.actions.includes(action))) }
export async function sha256(bytes: ArrayBuffer) { return [...new Uint8Array(await crypto.subtle.digest('SHA-256', bytes))].map(v => v.toString(16).padStart(2, '0')).join('') }
export function base64(bytes: Uint8Array) { let text = ''; for (let i = 0; i < bytes.length; i += 8192) text += String.fromCharCode(...bytes.subarray(i, i + 8192)); return btoa(text) }
export async function prepareCommand(context: Omit<Intent, 'parametersSha256'>, body: unknown): Promise<Prepared> {
  const bytes = new TextEncoder().encode(JSON.stringify(body))
  const intent = { ...context, parametersSha256: await sha256(bytes.buffer) }
  const challenge = await releaseRequest<{ id: string; expiresAt: string }>('POST', '/challenges', intent)
  return { intent, parameters: base64(bytes), idempotencyKey: 'cmd-' + crypto.randomUUID(), challengeId: challenge.id, expiresAt: challenge.expiresAt }
}
export class UnknownCommand extends Error { constructor(public key: string) { super(i18n.global.t('extension.unknownResult')) } }
export class CommandNotDispatched extends Error {}
export async function executeCommand(prepared: Prepared, operationPassword: string, otp: string): Promise<Receipt> {
  let grant: { token: string }
  try { grant = await releaseRequest<{ token: string }>('POST', '/authorizations', { challengeId: prepared.challengeId, operationPassword, otp }) }
  catch (cause) { throw new CommandNotDispatched(cause instanceof Error ? cause.message : i18n.global.t('extension.unknownResult')) }
  try { return await releaseRequest<Receipt>('POST', '/commands', { idempotencyKey: prepared.idempotencyKey, grant: grant.token, intent: prepared.intent, parameters: prepared.parameters }) }
  catch (error) {
    if (error instanceof ApiError && (error.code === 0 || error.code >= 500)) {
      try { return await reconcileCommand(prepared.idempotencyKey) } catch { throw new UnknownCommand(prepared.idempotencyKey) }
    }
    throw error
  }
}
export function reconcileCommand(key: string) { return releaseRequest<Receipt>('GET', '/console/actions/' + encodeURIComponent(key)) }
export async function inspectFiles(files: File[], policy: Policy): Promise<FileIntent[]> {
  const sorted = [...files].sort((a, b) => a.name < b.name ? -1 : a.name > b.name ? 1 : 0)
  if (!sorted.length || sorted.length > policy.batchFiles || new Set(sorted.map(f => f.name)).size !== sorted.length) throw new Error(i18n.global.t('extension.fileLimit'))
  let total = 0, sources = 0
  for (const file of sorted) {
    const source = file.name.endsWith('.java')
    if (!/^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,159}$/.test(file.name) || !(source || file.name.endsWith('.jar')) || file.size <= 0 || file.size > (source ? policy.sourceBytes : policy.jarBytes)) throw new Error(i18n.global.t('extension.fileLimit'))
    total += file.size; if (source) sources += file.size
  }
  if (total > policy.batchBytes || sources > policy.sourceBatchBytes) throw new Error(i18n.global.t('extension.fileLimit'))
  const result: FileIntent[] = []
  for (const file of sorted) result.push({ name: file.name, bytes: file.size, sha256: await sha256(await file.arrayBuffer()) })
  return result
}
export async function uploadFiles(uploadId: string, files: File[], progress: (value: number) => void) {
  const owns = releaseGuard()
  const sorted = [...files].sort((a, b) => a.name < b.name ? -1 : a.name > b.name ? 1 : 0), total = sorted.reduce((n, f) => n + f.size, 0)
  let sent = 0
  for (const [index, file] of sorted.entries()) for (let start = 0, chunk = 0; start < file.size; start += 1048576, chunk++) {
    if (!owns()) throw new Error(i18n.global.t('extension.sessionChanged'))
    const bytes = file.slice(start, start + 1048576)
    await releaseRequest('PUT', `/uploads/${encodeURIComponent(uploadId)}/files/${index}/chunks/${chunk}`, bytes)
    sent += bytes.size; progress(Math.round(sent / total * 100))
  }
  if (!owns()) throw new Error(i18n.global.t('extension.sessionChanged'))
  return releaseRequest<Artifact[]>('POST', `/uploads/${encodeURIComponent(uploadId)}/complete`)
}

export interface UploadStatus { id: string; state: string; expiresAt: string; resumable: boolean; files: FileIntent[]; artifactIds: string[] }
export const lookupReleaseUpload = (id: string) => releaseRequest<UploadStatus>('GET', '/uploads/' + encodeURIComponent(id))
export function uploadStatusEvidence(value: UploadStatus, id: string) {
  if (!value || value.id !== id || !['RECEIVING', 'COMPLETE'].includes(value.state) || typeof value.resumable !== 'boolean'
    || !Array.isArray(value.files) || value.files.length < 1 || value.files.length > 100
    || !Array.isArray(value.artifactIds) || value.artifactIds.length > 100 || !Number.isFinite(Date.parse(value.expiresAt))) throw new Error(i18n.global.t('extension.unknownResult'))
  if (value.files.some(file => !file || typeof file.name !== 'string' || !/^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,159}$/.test(file.name)
    || typeof file.sha256 !== 'string' || !/^[a-f0-9]{64}$/.test(file.sha256) || !Number.isSafeInteger(file.bytes) || file.bytes <= 0)
    || new Set(value.files.map(file => file.name)).size !== value.files.length) throw new Error(i18n.global.t('extension.unknownResult'))
  if (value.state === 'COMPLETE' && (value.artifactIds.length !== value.files.length || new Set(value.artifactIds).size !== value.artifactIds.length
    || value.artifactIds.some(id => typeof id !== 'string' || !/^[A-Za-z0-9_.:-]{1,160}$/.test(id)))) throw new Error(i18n.global.t('extension.unknownResult'))
  return { id, state: value.state === 'COMPLETE' ? 'SUCCEEDED' as const : 'PENDING' as const }
}
/** The existing server protocol checks immutable chunk hashes. Never resume a new task or new input. */
export async function resumeReleaseUpload(id: string, originalFiles: File[], policy: Policy, progress: (value: number) => void) {
  const owns = releaseGuard(), status = await lookupReleaseUpload(id)
  if (uploadStatusEvidence(status, id).state === 'SUCCEEDED') return status
  if (!status.resumable || !originalFiles.length) throw new Error(i18n.global.t('extension.uploadNeedsOriginal'))
  const intents = await inspectFiles(originalFiles, policy)
  if (intents.length !== status.files.length || intents.some((file, index) => {
    const original = status.files[index]!
    return file.name !== original.name || file.bytes !== original.bytes || file.sha256 !== original.sha256
  })) throw new Error(i18n.global.t('extension.uploadNeedsOriginal'))
  if (!owns()) throw new Error(i18n.global.t('extension.sessionChanged'))
  await uploadFiles(id, originalFiles, progress)
  return lookupReleaseUpload(id)
}

export interface CommandStatus { receipt: Receipt; upload?: UploadStatus }
export async function lookupCommandStatus(key: string, upload: boolean): Promise<CommandStatus> {
  const receipt = await reconcileCommand(key)
  return { receipt, upload: upload && commandEvidence(receipt, key).state === 'SUCCEEDED' ? await lookupReleaseUpload(receipt.resourceId) : undefined }
}
export function commandStatusEvidence(value: CommandStatus, key: string, upload: boolean) {
  const result = commandEvidence(value.receipt, key)
  if (!upload || result.state !== 'SUCCEEDED') return result
  if (!value.upload) throw new Error(i18n.global.t('extension.unknownResult'))
  return { id: key, state: uploadStatusEvidence(value.upload, value.receipt.resourceId).state }
}


/** ACCEPTED confirms this command's receipt, not upload completion or deployment activation. */
export function commandEvidence(receipt: Receipt, key: string) {
  if (!receipt || typeof receipt.id !== 'string' || !/^[A-Za-z0-9_.:-]{1,160}$/.test(receipt.id) || typeof receipt.resourceId !== 'string' || !/^[A-Za-z0-9_.:-]{1,160}$/.test(receipt.resourceId)) throw new Error(i18n.global.t('extension.unknownResult'))
  return { id: key, state: ['ACCEPTED', 'ACTIVE'].includes(receipt.state) ? 'SUCCEEDED' as const : ['FAILED', 'CANCELLED'].includes(receipt.state) ? 'FAILED_FINAL' as const : 'UNKNOWN' as const }
}
