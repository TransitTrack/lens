import { intlLocale } from './intlLocale'

/**
 * Wall-clock time on a 24-hour clock (`06:25`, `18:40:09`), regardless of the browser's locale -
 * the dashboard never shows AM/PM. `timeZone` defaults to the browser's own; `locale` is the app locale.
 */
export function formatClock(
  value: string | number | Date | null | undefined,
  opts: { seconds?: boolean, timeZone?: string, locale?: string } = {},
): string {
  if (value === null || value === undefined) return '—'
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return typeof value === 'string' ? value : '—'
  return new Intl.DateTimeFormat(intlLocale(opts.locale), {
    hour: '2-digit',
    minute: '2-digit',
    second: opts.seconds ? '2-digit' : undefined,
    hourCycle: 'h23',
    timeZone: opts.timeZone,
  }).format(date)
}

/** `dd.mm.yyyy, hh:mm:ss` (24h) in every language - the dotted layout is fixed, only the digits come from Intl. */
export function formatDottedTimestamp(date: Date, appLocale: string | undefined, timeZone?: string): string {
  const parts = new Intl.DateTimeFormat(intlLocale(appLocale), {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
    timeZone,
  }).formatToParts(date)
  const value = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((part) => part.type === type)?.value ?? ''
  return `${value('day')}.${value('month')}.${value('year')}, ${value('hour')}:${value('minute')}:${value('second')}`
}

/** `Tue, 03.11.2026`: localised short weekday plus a fixed dd.mm.yyyy date. */
export function formatCalendarDate(date: Date, appLocale: string | undefined): string {
  const weekday = new Intl.DateTimeFormat(intlLocale(appLocale), { weekday: 'short' }).format(date)
  const twoDigits = (value: number) => String(value).padStart(2, '0')
  return `${weekday}, ${twoDigits(date.getDate())}.${twoDigits(date.getMonth() + 1)}.${date.getFullYear()}`
}
