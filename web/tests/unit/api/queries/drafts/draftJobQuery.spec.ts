import { afterEach, describe, expect, it, vi } from 'vitest'
import { draftJobQuery } from '~/composables/api/queries/drafts'
import { runQuery } from '../../../../helpers/colada'
import { json, stubFetch } from '../../../../helpers/stubFetch'

describe('draftJobQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /draft-jobs/j1 and returns the job', async () => {
    const { calls } = stubFetch(() => json({ state: 'RUNNING' }))
    const options = draftJobQuery({ jobId: 'j1' })
    expect(await runQuery(options)).toEqual({ state: 'RUNNING' })
    expect(new URL(calls[0]!.url).pathname).toBe('/api/v1/draft-jobs/j1')
    expect(options.key).toEqual(['draft-jobs', 'j1'])
    expect(options.gcTime).toBe(0)
    expect(options.staleTime).toBe(0)
  })

  it('returns null on 404', async () => {
    stubFetch(() => json({ title: 'Not Found', status: 404 }, 404))
    expect(await runQuery(draftJobQuery({ jobId: 'j1' }))).toBeNull()
  })
})
