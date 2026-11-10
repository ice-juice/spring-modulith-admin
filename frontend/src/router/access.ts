export function safeRedirect(value: unknown): string {
  return typeof value === 'string' &&
    value.startsWith('/') &&
    !value.startsWith('//') &&
    !value.startsWith('/login')
    ? value
    : '/'
}
export function accessDecision(
  publicRoute: boolean,
  signedIn: boolean,
  permission: string | undefined,
  allowed: (key: string) => boolean,
): 'allow' | 'login' | 'forbidden' {
  if (publicRoute) return 'allow'
  if (!signedIn) return 'login'
  if (permission && !allowed(permission)) return 'forbidden'
  return 'allow'
}
