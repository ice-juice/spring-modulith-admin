import { safeLocalPath } from '@/lib/web-addresses'
export function safeRedirect(value: unknown): string {
  const path = safeLocalPath(value)
  return path && !path.startsWith('/login') ? path : '/'
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
