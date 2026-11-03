import { describe, expect, it } from 'vitest'
import { createI18n } from 'vue-i18n'
import en from '../../i18n/locales/en.json'
import {
  revisionStatusMeta,
  isInProgressRevisionStatus,
  cronSummary,
  rowCount,
  PIPELINE_STEPS,
} from '../../app/utils/feedStatus'

const t = createI18n({ legacy: false, locale: 'en', messages: { en } }).global.t

describe('revisionStatusMeta', () => {
  it('maps pipeline stages to an ordered step', () => {
    expect(revisionStatusMeta('VALIDATING', t)).toMatchObject({
      label: 'Validating',
      step: 1,
      terminal: false,
    })
    expect(revisionStatusMeta('ACTIVE', t)).toMatchObject({
      label: 'Active',
      color: 'success',
      terminal: true,
    })
    expect(revisionStatusMeta('ACTIVE', t).step).toBe(PIPELINE_STEPS.length - 1)
  })

  it('failed is error, step -1, terminal', () => {
    expect(revisionStatusMeta('FAILED', t)).toEqual({
      label: 'Failed',
      color: 'error',
      step: -1,
      terminal: true,
    })
  })

  it('unknown / null falls back to neutral', () => {
    expect(revisionStatusMeta(null, t).color).toBe('neutral')
    expect(revisionStatusMeta('WHAT', t).label).toBe('WHAT')
  })
})

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

describe('cronSummary', () => {
  it('recognises common Spring 6-field patterns', () => {
    expect(cronSummary('0 */15 * * * *', t)).toBe('every 15 min')
    expect(cronSummary('0 0 */6 * * *', t)).toBe('every 6 h')
    expect(cronSummary('0 30 3 * * *', t)).toBe('daily at 03:30')
  })

  it('falls back to the raw string / a note', () => {
    expect(cronSummary('0 0 12 * * MON-FRI', t)).toBe('0 0 12 * * MON-FRI')
    expect(cronSummary(null, t)).toBe('not scheduled')
    expect(cronSummary('', t)).toBe('not scheduled')
  })
})

describe('rowCount', () => {
  it('reads a gtfs_<entity> key', () => {
    expect(rowCount({ gtfs_route: 59, gtfs_stop: 896 }, 'route')).toBe(59)
    expect(rowCount({ gtfs_route: 59 }, 'trip')).toBeNull()
    expect(rowCount(null, 'route')).toBeNull()
  })
})
