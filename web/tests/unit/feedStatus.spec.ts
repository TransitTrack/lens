import { describe, expect, it } from 'vitest'
import { isInProgressRevisionStatus } from '../../app/utils/feedStatus'

describe('isInProgressRevisionStatus', () => {
  it.each(['PENDING', 'DOWNLOADING', 'VALIDATING', 'PARSING', 'DERIVING'])(
    'recognises %s as an in-progress ingest stage',
    (status) => {
      expect(isInProgressRevisionStatus(status)).toBe(true)
    },
  )

  it.each(['READY', 'ACTIVE', 'SUPERSEDED', 'FAILED', 'UNCHANGED', 'DRAFT', null])(
    'does not treat %s as an in-progress ingest stage',
    (status) => {
      expect(isInProgressRevisionStatus(status)).toBe(false)
    },
  )
})
