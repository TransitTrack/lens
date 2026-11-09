import { afterEach, describe, expect, it, vi } from 'vitest'
import { scheduleTimesQuery } from '~/composables/api/queries/schedule'
import { runQuery } from '../../../../helpers/colada'
import { page, stubFetch } from '../../../../helpers/stubFetch'

const params = { feedCode: 'f', revisionId: 7, tripId: 't1', patternKey: 'pk' }

describe('scheduleTimesQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests schedule-times with the scope and returns the rows', async () => {
    const { calls } = stubFetch(() => page([{ id: 'x' }]))
    const options = scheduleTimesQuery(params)
    expect(await runQuery(options)).toEqual([{ id: 'x' }])
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/feeds/f/schedule-times')
    expect(url.searchParams.get('revisionId')).toBe('7')
    expect(url.searchParams.get('tripId')).toBe('t1'); expect(url.searchParams.get('patternKey')).toBe('pk')
    expect(options.key).toEqual(['feeds', 'f', 'rev', '7', 'schedule-times', { tripId: 't1', patternKey: 'pk' }])
    expect(options.staleTime).toBe(Infinity)
  })

  it('reads every page', async () => {
    const { calls } = stubFetch(({ url }) => new Response(
      JSON.stringify(new URL(url).searchParams.get('offset') === '0' ? [{ id: 'a' }, { id: 'b' }] : [{ id: 'c' }]),
      { headers: { 'Content-Type': 'application/json', 'X-Total-Count': '3' } },
    ))
    expect(await runQuery(scheduleTimesQuery(params))).toHaveLength(3)
    expect(calls).toHaveLength(2)
  })

  it('sends no revisionId for the active revision', async () => {
    const { calls } = stubFetch(() => page([]))
    await runQuery(scheduleTimesQuery({ ...params, revisionId: undefined }))
    expect(new URL(calls[0]!.url).searchParams.has('revisionId')).toBe(false)
  })
})
