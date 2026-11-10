import dayjs from 'dayjs'
import utc from 'dayjs/plugin/utc'
import timezone from 'dayjs/plugin/timezone'
import 'dayjs/locale/zh-cn'
import 'dayjs/locale/en'
import { i18n } from '@/locales'
dayjs.extend(utc)
dayjs.extend(timezone)
export function localDateTimeToInstant(value: string, zone: string): string {
  const selectedZone = validTimeZone(zone) ? zone : 'UTC'
  const match = value.trim().match(/^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2})(?::(\d{2}))?$/)
  if (!match) throw new Error(i18n.global.t('common.invalidLocalTime'))
  const normalized = `${match[1]}T${match[2]}:${match[3] || '00'}`
  const parsed = dayjs.tz(normalized, selectedZone)
  if (!parsed.isValid() || parsed.format('YYYY-MM-DDTHH:mm:ss') !== normalized) throw new Error(i18n.global.t('common.nonexistentLocalTime'))
  const offsets = new Set([-24, -12, 0, 12, 24].map(hours => dayjs.utc(parsed.valueOf() + hours * 3600000).tz(selectedZone).utcOffset()))
  const candidates = [...offsets].map(offset => Date.parse(normalized + 'Z') - offset * 60000).filter(instant => dayjs.utc(instant).tz(selectedZone).format('YYYY-MM-DDTHH:mm:ss') === normalized)
  if (new Set(candidates).size > 1) throw new Error(i18n.global.t('common.ambiguousLocalTime'))
  return parsed.utc().toISOString()
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
