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

export function assignmentLabel(report: ReportAssignment): string {
  return report.match?.trip?.tripId ?? report.descTripId ?? 'n/a'
}

export function formatReportTimestamp(iso: string, timeZone: string): string {
  return new Intl.DateTimeFormat('en-GB', {
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
