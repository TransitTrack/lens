/**
 * Wall-clock time on a 24-hour clock (`06:25`, `18:40:09`), regardless of the browser's locale -
 * the dashboard never shows AM/PM. `timeZone` defaults to the browser's own.
 */
export function formatClock(
  value: string | number | Date | null | undefined,
  opts: { seconds?: boolean, timeZone?: string } = {},
): string {
  if (value === null || value === undefined) return '—'
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return typeof value === 'string' ? value : '—'
  return new Intl.DateTimeFormat('en-GB', {
    hour: '2-digit',
    minute: '2-digit',
    second: opts.seconds ? '2-digit' : undefined,
    hourCycle: 'h23',
    timeZone: opts.timeZone,
  }).format(date)
}
