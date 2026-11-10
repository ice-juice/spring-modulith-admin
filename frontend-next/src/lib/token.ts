const key = 'sma.access-token'
export const getToken = () => sessionStorage.getItem(key) || localStorage.getItem(key)
export function saveToken(token: string, remember: boolean) {
  clearToken()
  ;(remember ? localStorage : sessionStorage).setItem(key, token)
}
export function clearToken() {
  localStorage.removeItem(key)
  sessionStorage.removeItem(key)
}
