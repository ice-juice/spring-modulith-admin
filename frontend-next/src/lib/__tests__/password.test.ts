import { expect, it } from 'vitest'
import { validPassword } from '../password'
it('validates the backend character minimum and bcrypt UTF-8 byte maximum', () => {
  expect(validPassword('abcdefghijkl')).toBe(true)
  expect(validPassword('short')).toBe(false)
  expect(validPassword('字'.repeat(24))).toBe(true)
  expect(validPassword('字'.repeat(25))).toBe(false)
})
