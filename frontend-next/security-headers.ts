/** Report-only rollout: enforce only after each deployed template/resource is verified. */
export const securityHeaders = {
  'X-Content-Type-Options': 'nosniff',
  'X-Frame-Options': 'SAMEORIGIN',
  'Referrer-Policy': 'no-referrer',
  'Permissions-Policy': 'camera=(), microphone=(), geolocation=()',
  'Content-Security-Policy-Report-Only': "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: https:; font-src 'self' data:; connect-src 'self'; worker-src 'self' blob:; frame-src 'self' https:; object-src 'none'; base-uri 'none'; frame-ancestors 'self'",
}
