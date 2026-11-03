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

type Translate = (key: string, params?: Record<string, unknown>, plural?: number) => string

export type BadgeColor = 'neutral' | 'info' | 'warning' | 'success' | 'error'

const IN_PROGRESS_REVISION_STATUSES = new Set<RevisionStatus>([
  'PENDING',
  'DOWNLOADING',
  'VALIDATING',
  'PARSING',
  'DERIVING',
])

/** Whether an ingest revision is still moving through the import pipeline. */
export function isInProgressRevisionStatus(status: string | null | undefined): boolean {
  return IN_PROGRESS_REVISION_STATUSES.has(status as RevisionStatus)
}

/** Ordered ingestion pipeline stages, for the processing stepper. */
// i18n-keys: feed.step.*
export const PIPELINE_STEPS = [
  { key: 'DOWNLOADING', labelKey: 'feed.step.download' },
  { key: 'VALIDATING', labelKey: 'feed.step.validate' },
  { key: 'PARSING', labelKey: 'feed.step.parse' },
  { key: 'DERIVING', labelKey: 'feed.step.derive' },
  { key: 'READY', labelKey: 'feed.step.ready' },
  { key: 'ACTIVE', labelKey: 'feed.step.active' },
] as const

export interface RevisionStatusMeta {
  label: string
  color: BadgeColor
  /** index into PIPELINE_STEPS the revision has reached (-1 for failed/pending) */
  step: number
  terminal: boolean
}

type StatusMetaEntry = Omit<RevisionStatusMeta, 'label'> & { labelKey: string }

// i18n-keys: feed.status.*
const META: Record<RevisionStatus, StatusMetaEntry> = {
  PENDING: { labelKey: 'feed.status.PENDING', color: 'neutral', step: -1, terminal: false },
  DOWNLOADING: { labelKey: 'feed.status.DOWNLOADING', color: 'info', step: 0, terminal: false },
  VALIDATING: { labelKey: 'feed.status.VALIDATING', color: 'info', step: 1, terminal: false },
  PARSING: { labelKey: 'feed.status.PARSING', color: 'info', step: 2, terminal: false },
  DERIVING: { labelKey: 'feed.status.DERIVING', color: 'info', step: 3, terminal: false },
  READY: { labelKey: 'feed.status.READY', color: 'success', step: 4, terminal: false },
  ACTIVE: { labelKey: 'feed.status.ACTIVE', color: 'success', step: 5, terminal: true },
  SUPERSEDED: { labelKey: 'feed.status.SUPERSEDED', color: 'neutral', step: 5, terminal: true },
  UNCHANGED: { labelKey: 'feed.status.UNCHANGED', color: 'neutral', step: 5, terminal: true },
  FAILED: { labelKey: 'feed.status.FAILED', color: 'error', step: -1, terminal: true },
  DRAFT: { labelKey: 'feed.status.DRAFT', color: 'neutral', step: -1, terminal: true },
}

export function revisionStatusMeta(
  status: string | null | undefined,
  t: Translate,
): RevisionStatusMeta {
  const entry = META[status as RevisionStatus]
  if (!entry) return { label: status ?? '—', color: 'neutral', step: -1, terminal: false }
  const { labelKey, ...rest } = entry
  return { label: t(labelKey), ...rest }
}

/** Human summary of a common Spring 6-field cron; falls back to the raw string. */
export function cronSummary(cron: string | null | undefined, t: Translate): string {
  if (!cron || !cron.trim()) return t('feed.cron.notScheduled')
  const p = cron.trim().split(/\s+/)
  // only summarise when day-of-month, month and day-of-week are unrestricted
  if (p.length === 6 && p[3] === '*' && p[4] === '*' && p[5] === '*') {
    const [, min, hour] = p
    const everyMin = /^\*\/(\d+)$/.exec(min)
    if (everyMin && hour === '*') return t('feed.cron.everyMin', { n: everyMin[1] })
    const everyHour = /^\*\/(\d+)$/.exec(hour)
    if (everyHour && /^\d+$/.test(min)) return t('feed.cron.everyHour', { n: everyHour[1] })
    if (/^\d+$/.test(min) && /^\d+$/.test(hour)) {
      return t('feed.cron.daily', { time: `${hour.padStart(2, '0')}:${min.padStart(2, '0')}` })
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
