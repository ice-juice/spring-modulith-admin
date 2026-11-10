import { expect, it } from 'vitest'
import { accessDecision, safeRedirect } from '../access'
it('requires login and then page permission, while public routes remain accessible', () => {
  expect(accessDecision(false, false, undefined, () => false)).toBe('login')
  expect(accessDecision(false, true, 'iam:user:view', () => false)).toBe('forbidden')
  expect(accessDecision(false, true, 'iam:user:view', () => true)).toBe('allow')
  expect(accessDecision(true, false, undefined, () => false)).toBe('allow')
})
it('rejects external or recursive sign-in redirects', () => {
  expect(safeRedirect('https://example.com')).toBe('/')
  expect(safeRedirect('//example.com')).toBe('/')
  expect(safeRedirect('/login?redirect=/login')).toBe('/')
  expect(safeRedirect('/iam/users?page=2')).toBe('/iam/users?page=2')
})
