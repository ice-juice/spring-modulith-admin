import dayjs from 'dayjs'
import utc from 'dayjs/plugin/utc'
import timezone from 'dayjs/plugin/timezone'
import 'dayjs/locale/zh-cn'
import 'dayjs/locale/en'
dayjs.extend(utc)
dayjs.extend(timezone)
export function localDateTimeToInstant(value: string, zone: string): string {
  return dayjs
    .tz(value, validTimeZone(zone) ? zone : 'UTC')
    .utc()
    .toISOString()
}

export function validTimeZone(value?: string | null): value is string {
  if (!value) return false
  try {
    new Intl.DateTimeFormat('en-US', { timeZone: value })
    return true
  } catch {
    return false
  }
}
export function resolveTimeZone(
  user?: string | null,
  tenant?: string | null,
  browser = Intl.DateTimeFormat().resolvedOptions().timeZone,
): string {
  return [user, tenant, browser, 'UTC'].find(validTimeZone) || 'UTC'
}
/** Receives backend UTC Instants, never a timezone-less local date. */
export function formatInstant(
  value: string | null | undefined,
  zone: string,
  locale = 'zh-CN',
  pattern = 'YYYY-MM-DD HH:mm:ss',
): string {
  if (!value) return '—'
  const date = dayjs(value)
  if (!date.isValid()) return '—'
  return date
    .tz(validTimeZone(zone) ? zone : 'UTC')
    .locale(locale === 'zh-CN' ? 'zh-cn' : 'en')
    .format(pattern)
}
