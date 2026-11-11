/** Browser destination syntax only; server-side outbound requests need a separate SSRF policy. */
const unsafe = /[\s\u0000-\u001f\u007f\\]|%(?:0[0-9a-f]|1[0-9a-f]|5c|7f)/i
const base = 'https://local.invalid'
function usable(value: unknown): value is string {
  return typeof value === 'string' && value.length > 0 && value.length <= 2048 && !unsafe.test(value)
}
export function safeLocalPath(value: unknown): string | undefined {
  if (!usable(value) || !value.startsWith('/') || value.startsWith('//')) return
  try {
    const url = new URL(value, base)
    if (url.origin !== base || /(?:^|\/)(?:\.|%2e){1,2}(?:\/|$)/i.test(value.split(/[?#]/, 1)[0]!)) return
    return value
  } catch { return }
}
export function safeHttpLink(value: unknown): string | undefined {
  if (!usable(value) || !/^https?:\/\//i.test(value)) return
  try {
    const url = new URL(value)
    if (!url.hostname || url.username || url.password || (url.protocol !== 'https:' && url.protocol !== 'http:')) return
    return value
  } catch { return }
}
export function safeBrowserDestination(value: unknown) { return safeLocalPath(value) || safeHttpLink(value) }
