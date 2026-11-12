const key = 'sma.access-token'
const detached = 'sma.access-token.detached'
/** A private tab must never silently fall back to another tab's remembered identity. */
export const getToken = () => sessionStorage.getItem(key) || (sessionStorage.getItem(detached) === '1' ? null : localStorage.getItem(key))
/** Memory-only comparison for auth admission; never include this value in coordination records. */
export const getSharedToken = () => localStorage.getItem(key)
export function detachToken() {
  sessionStorage.setItem(detached, '1')
  sessionStorage.removeItem(key)
}
export function saveToken(token: string, remember: boolean) {
  if (!token) throw new Error('Empty access token')
  if (remember) {
    localStorage.setItem(key, token)
    sessionStorage.removeItem(key)
    sessionStorage.removeItem(detached)
  } else {
    // Keep an unrelated shared identity for its owning tabs.
    sessionStorage.setItem(detached, '1')
    sessionStorage.setItem(key, token)
  }
}
export function clearToken(expected: string | null = getToken()) {
  if (getToken() !== expected) return false
  sessionStorage.setItem(detached, '1')
  if (expected !== null) {
    if (sessionStorage.getItem(key) === expected) sessionStorage.removeItem(key)
    if (localStorage.getItem(key) === expected) localStorage.removeItem(key)
  }
  return true
}
