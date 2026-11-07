type RevisionStatus =
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
