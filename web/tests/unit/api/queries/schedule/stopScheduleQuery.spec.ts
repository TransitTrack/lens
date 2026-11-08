import { afterEach, describe, expect, it, vi } from 'vitest'
import { stopScheduleQuery } from '~/composables/api/queries/schedule/stopScheduleQuery'
import { runQuery } from '../../../../helpers/colada'
import { json, stubFetch } from '../../../../helpers/stubFetch'

const params = { feedCode: 'f', revisionId: 7, stopId: 's1' }

describe('stopScheduleQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /stops/s1/schedule with the scope and returns the body', async () => {
    const { calls } = stubFetch(() => json({ stopId: 's1' }))
    const options = stopScheduleQuery(params)
    expect(await runQuery(options)).toEqual({ stopId: 's1' })
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/feeds/f/stops/s1/schedule')
    expect(url.searchParams.get('revisionId')).toBe('7')
    expect(options.key).toEqual(['feeds', 'f', 'rev', '7', 'stop-schedule', { stopId: 's1' }])
  })

  it('sends no revisionId for the active revision', async () => {
    const { calls } = stubFetch(() => json({}))
    await runQuery(stopScheduleQuery({ ...params, revisionId: undefined }))
    expect(new URL(calls[0]!.url).searchParams.has('revisionId')).toBe(false)
  })
})
