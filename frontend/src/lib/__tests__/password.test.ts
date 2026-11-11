import { expect, it } from 'vitest'
import { validPassword, validSecurityPassword } from '../password'
it('uses distinct new-password length rules without requiring mixed case or symbols', () => {
  for (const value of ['m7K2v9R4', 'm7K2v9R4nP3q', 'm7k2v9r4', 'M7K2V9R4']) expect(validPassword(value)).toBe(true)
  for (const value of ['123456', 'aaa', '12345678', 'abcdefgh', 'm7K2v9R', 'm7K2v9R4Np3qX', 'm7K2v9R4\n', '密码ab1234', 'm7K2v9R!']) expect(validPassword(value)).toBe(false)
  for (const value of ['a1b2c3', 'a1b2c3d', 'n8L3w0S5']) expect(validSecurityPassword(value)).toBe(true)
  for (const value of ['123456', 'abcdef', 'a1b2c', 'a1b2c3d4e', 'a1b2c!']) expect(validSecurityPassword(value)).toBe(false)
})
