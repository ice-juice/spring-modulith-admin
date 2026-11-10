import { describe, expect, it } from 'vitest'
import { pageQuery, positiveInteger } from '../table'
describe('server pagination contract', () => {
  it('maps zero based UI indexes to one based server pages and caps page size', () => {
    expect(pageQuery(0, 20)).toEqual({ page: 1, size: 20 })
    expect(pageQuery(4, 999)).toEqual({ page: 5, size: 200 })
    expect(pageQuery(-5, NaN)).toEqual({ page: 1, size: 20 })
  })
  it('rejects imprecise numeric values rather than treating IDs as page counts', () => {
    expect(positiveInteger('9007199254740993123', 1)).toBe(1)
    expect(positiveInteger('20', 1)).toBe(20)
  })
})
