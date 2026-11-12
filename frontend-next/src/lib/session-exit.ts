import { BrowserOperationCoordinator, type OperationLease } from './browser-operation-coordinator'

const prefix = 'sma.session-exit.v1.'
interface Marker { schema: 1; scope: string; key: string; state: 'LOCAL_EXIT' }
export interface SessionExit {
  exclusive: boolean
  /** Called only after local unsaved-work confirmation. Returns a key only for the sole sender. */
  commit(): Promise<string | null>
  release(): Promise<void>
}
const coordinator = new BrowserOperationCoordinator()
async function scope(token: string) {
  const bytes = new TextEncoder().encode(JSON.stringify([window.location.origin, token])); let digest: ArrayBuffer
  try { digest = await crypto.subtle.digest('SHA-256', bytes) } finally { bytes.fill(0) }
  return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('')
}
function read(hash: string): Marker | null {
  const raw = localStorage.getItem(prefix + hash)
  if (!raw) return null
  if (raw.length > 512) throw new Error('Invalid session exit metadata')
  const value = JSON.parse(raw) as Marker
  if (Object.keys(value).sort().join(',') !== 'key,schema,scope,state' || value.schema !== 1 || value.scope !== hash || value.state !== 'LOCAL_EXIT'
    || !/^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/.test(value.key)) throw new Error('Invalid session exit metadata')
  return value
}
export function hasSessionExitMarkers() {
  if (localStorage.length > 10000) throw new Error('Session exit storage limit')
  for (let i = 0; i < localStorage.length; i++) if (localStorage.key(i)?.startsWith(prefix)) return true
  return false
}
export const sessionExitEvent = (key: string | null) => key === null || key.startsWith(prefix)
export async function sessionHasExited(token: string) { return read(await scope(token)) !== null }

/** Persisted local retirement, not a server logout receipt. Local exit remains available on any failure. */
export async function prepareSessionExit(token: string, current: () => boolean): Promise<SessionExit> {
  const localOnly: SessionExit = { exclusive: false, commit: async () => null, release: async () => {} }
  let lease: OperationLease | undefined
  try {
    const hash = await scope(token)
    if (!current()) return localOnly
    lease = await coordinator.acquire({ owner: 'session-exit:' + hash, resource: 'session' })
    if (!current()) { await lease.release(); return localOnly }
    if (read(hash)) { await lease.release(); return localOnly }
    const acquired = lease; lease = undefined
    let released = false
    return {
      exclusive: true,
      release: async () => { if (!released) { released = true; await acquired.release() } },
      commit: async () => {
        if (released || !current()) return null
        let key: string | null = null
        try {
          await coordinator.transaction('session-exit-storage-v1', () => {
            if (released || !current() || read(hash)) return
            if (localStorage.length > 10000) throw new Error('Session exit storage limit')
            let count = 0
            for (let i = 0; i < localStorage.length; i++) if (localStorage.key(i)?.startsWith(prefix)) count++
            if (count >= 256) throw new Error('Session exit capacity')
            const id = crypto.randomUUID()
            localStorage.setItem(prefix + hash, JSON.stringify({ schema: 1, scope: hash, key: id, state: 'LOCAL_EXIT' } satisfies Marker))
            key = id
          })
        } catch { return null }
        return current() ? key : null
      },
    }
  } catch { await lease?.release(); return localOnly }
}
