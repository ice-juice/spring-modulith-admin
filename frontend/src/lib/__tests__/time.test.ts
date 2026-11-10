import { describe, expect, it } from 'vitest'
import { formatInstant, resolveTimeZone, localDateTimeToInstant } from '../time'
describe('user time zones', () => {
  it('interprets picker values in the selected user zone independently of the browser', () => {
    expect(localDateTimeToInstant('2026-11-10 09:00:00', 'America/New_York')).toBe(
      '2026-11-10T14:00:00.000Z',
    )
    expect(localDateTimeToInstant('2026-11-10 09:00:00', 'Asia/Shanghai')).toBe(
      '2026-11-10T01:00:00.000Z',
    )
  })
  it('resolves personal > tenant > browser > UTC and ignores invalid zones', () => {
    expect(resolveTimeZone('America/New_York', 'Asia/Shanghai', 'Europe/Paris')).toBe(
      'America/New_York',
    )
    expect(resolveTimeZone('invalid', 'Asia/Shanghai', 'Europe/Paris')).toBe('Asia/Shanghai')
    expect(resolveTimeZone(null, null, 'invalid')).toBe('UTC')
  })
  it('renders both occurrences of the repeated daylight-saving hour', () => {
    expect(formatInstant('2026-11-01T05:30:00Z', 'America/New_York', 'en-US', 'HH:mm Z')).toBe(
      '01:30 -04:00',
    )
    expect(formatInstant('2026-11-01T06:30:00Z', 'America/New_York', 'en-US', 'HH:mm Z')).toBe(
      '01:30 -05:00',
    )
    expect(formatInstant(null, 'UTC')).toBe('—')
  })
})
