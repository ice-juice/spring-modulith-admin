import type { BrowserLocks } from '../browser-operation-coordinator'
/** Shared in-memory LockManager for unit tests; real multi-page semantics are tested in Playwright. */
export function browserLocksFixture() {
  const held = new Map<string, LockMode[]>(), waiting = new Map<string, (() => void)[]>()
  const locks: BrowserLocks = { async request(name, options, callback) {
    const conflict = () => (held.get(name) || []).some(mode => mode === 'exclusive' || options.mode === 'exclusive')
    while (conflict()) {
      if (options.ifAvailable) return callback(null)
      await new Promise<void>(resolve => { const queue = waiting.get(name) || []; queue.push(resolve); waiting.set(name, queue) })
    }
    const modes = held.get(name) || []; modes.push(options.mode); held.set(name, modes)
    try { return await callback({ name }) }
    finally { modes.splice(modes.indexOf(options.mode), 1); if (!modes.length) held.delete(name); const queue = waiting.get(name) || []; waiting.delete(name); queue.forEach(wake => wake()) }
  } }
  return { locks, held }
}
export function credentialMarker() { const key = Object.keys(localStorage).find(key => key.startsWith('sma.credential-intent.v2.')); return key ? localStorage.getItem(key) : null }
export function sessionLockMarker() { const key = Object.keys(localStorage).find(key => key.startsWith('sma.session-lock-intent.v2.')); return key ? localStorage.getItem(key) : null }
