import { expect, it } from 'vitest'
import { clampRect } from '../geometry'
it('constrains corrupt or off-screen geometry after viewport changes', () => {
  const rect = clampRect({ x: Infinity, y: 9000, width: 9999, height: -200 }, 320, 700)
  expect(rect.x).toBeGreaterThanOrEqual(8); expect(rect.x + rect.width).toBeLessThanOrEqual(312)
  expect(rect.y + rect.height).toBeLessThanOrEqual(692); expect(rect.height).toBe(180)
  expect(clampRect({ collapsed: true, y: 9999 }, 1440, 900).y).toBe(844)
})
