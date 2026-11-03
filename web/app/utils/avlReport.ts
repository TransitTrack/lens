import { intlLocale } from './intlLocale'

export interface ReportAssignment {
  descTripId?: string | null
  match?: {
    trip?: {
      tripId?: string | null
    } | null
  } | null
}

export function isValidReportRange(date: string, startTime: string, endTime: string): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(date) && /^\d{2}:\d{2}$/.test(startTime) && startTime < endTime
}

export function assignmentLabel(
  report: ReportAssignment,
  t: (key: string, params?: Record<string, unknown>) => string,
): string {
  return report.match?.trip?.tripId ?? report.descTripId ?? t('reports.notAvailable')
}

export function formatReportTimestamp(iso: string, timeZone: string, appLocale?: string): string {
  return new Intl.DateTimeFormat(intlLocale(appLocale), {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
    timeZone,
  }).format(new Date(iso))
}

export function formatReportSource(source: string): string {
  return source.replaceAll('_', '-')
}
