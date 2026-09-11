export type RevisionStatus =
  | 'PENDING'
  | 'DOWNLOADING'
  | 'VALIDATING'
  | 'PARSING'
  | 'DERIVING'
  | 'READY'
  | 'ACTIVE'
  | 'SUPERSEDED'
  | 'FAILED'
  | 'UNCHANGED'
  | 'DRAFT'

export type BadgeColor = 'neutral' | 'info' | 'warning' | 'success' | 'error'

/** Ordered ingestion pipeline stages, for the processing stepper. */
export const PIPELINE_STEPS = [
  { key: 'DOWNLOADING', label: 'Download' },
  { key: 'VALIDATING', label: 'Validate' },
  { key: 'PARSING', label: 'Parse' },
  { key: 'DERIVING', label: 'Derive' },
  { key: 'READY', label: 'Ready' },
  { key: 'ACTIVE', label: 'Active' },
] as const

export interface RevisionStatusMeta {
  label: string
  color: BadgeColor
  /** index into PIPELINE_STEPS the revision has reached (-1 for failed/pending) */
  step: number
  terminal: boolean
}

const META: Record<RevisionStatus, RevisionStatusMeta> = {
  PENDING: { label: 'Queued', color: 'neutral', step: -1, terminal: false },
  DOWNLOADING: { label: 'Downloading', color: 'info', step: 0, terminal: false },
  VALIDATING: { label: 'Validating', color: 'info', step: 1, terminal: false },
  PARSING: { label: 'Parsing', color: 'info', step: 2, terminal: false },
  DERIVING: { label: 'Deriving', color: 'info', step: 3, terminal: false },
  READY: { label: 'Ready', color: 'success', step: 4, terminal: false },
  ACTIVE: { label: 'Active', color: 'success', step: 5, terminal: true },
  SUPERSEDED: { label: 'Superseded', color: 'neutral', step: 5, terminal: true },
  UNCHANGED: { label: 'Unchanged', color: 'neutral', step: 5, terminal: true },
  FAILED: { label: 'Failed', color: 'error', step: -1, terminal: true },
  DRAFT: { label: 'Draft', color: 'neutral', step: -1, terminal: true },
}

export function revisionStatusMeta(status: string | null | undefined): RevisionStatusMeta {
  return (
    META[status as RevisionStatus] ?? {
      label: status ?? '—',
      color: 'neutral',
      step: -1,
      terminal: false,
    }
  )
}

/** Human summary of a common Spring 6-field cron; falls back to the raw string. */
export function cronSummary(cron: string | null | undefined): string {
  if (!cron || !cron.trim()) return 'not scheduled'
  const p = cron.trim().split(/\s+/)
  // only summarise when day-of-month, month and day-of-week are unrestricted
  if (p.length === 6 && p[3] === '*' && p[4] === '*' && p[5] === '*') {
    const [, min, hour] = p
    const everyMin = /^\*\/(\d+)$/.exec(min)
    if (everyMin && hour === '*') return `every ${everyMin[1]} min`
    const everyHour = /^\*\/(\d+)$/.exec(hour)
    if (everyHour && /^\d+$/.test(min)) return `every ${everyHour[1]} h`
    if (/^\d+$/.test(min) && /^\d+$/.test(hour)) {
      return `daily at ${hour.padStart(2, '0')}:${min.padStart(2, '0')}`
    }
  }
  return cron
}

/** Total row count across GTFS entity types recorded on a revision. */
export function rowCount(
  rowCounts: Record<string, number> | null | undefined,
  entity: string,
): number | null {
  const v = rowCounts?.[`gtfs_${entity}`]
  return typeof v === 'number' ? v : null
}
