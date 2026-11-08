import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import { h } from 'vue'
import { UApp } from '#components'
import DefaultLayout from '../../app/layouts/default.vue'
import { json, page, stubFetch } from '../helpers/stubFetch'

const feed = { code: 'feed-a', name: 'Feed A', description: null, enabled: true, source: 'REMOTE' }
const revision = (status: string) => ({ id: 1, feedCode: 'feed-a', status })

/** Let in-flight requests resolve and Vue re-render, without waiting on real time. */
async function settle() {
  for (let i = 0; i < 5; i++) await vi.advanceTimersByTimeAsync(0)
}

describe('default layout feed readiness polling', () => {
  beforeEach(() => {
    useCookie('tt-demo-authenticated').value = 'true'
    vi.useFakeTimers({ shouldAdvanceTime: true })
  })
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  function stubBackend(revisionStatus: () => string) {
    return stubFetch(({ url }) => {
      const { pathname } = new URL(url)
      if (pathname.endsWith('/feeds/feed-a/revisions')) return page([revision(revisionStatus())])
      if (pathname.endsWith('/feeds/feed-a')) return json(feed)
      if (pathname.endsWith('/feeds')) return page([feed])
      return page([])
    })
  }
  const readinessCount = (calls: { url: string }[]) =>
    calls.filter((c) => new URL(c.url).pathname.endsWith('/feeds/feed-a/revisions')).length

  async function mountLayout() {
    return await mountSuspended({ render: () => h(UApp, null, () => h(DefaultLayout, null, () => h('div', 'page'))) }, { route: '/feed-a' })
  }

  it('keeps polling every 10 s while there is no ACTIVE revision', async () => {
    const { calls } = stubBackend(() => 'VALIDATING')
    const wrapper = await mountLayout()
    await settle()
    const initial = readinessCount(calls)
    expect(initial).toBeGreaterThanOrEqual(1)

    await vi.advanceTimersByTimeAsync(20_000)
    await settle()
    expect(readinessCount(calls)).toBe(initial + 2)
    wrapper.unmount()
  })

  it('stops polling once a response contains an ACTIVE revision', async () => {
    let status = 'VALIDATING'
    const { calls } = stubBackend(() => status)
    const wrapper = await mountLayout()
    await settle()

    const beforeActive = readinessCount(calls)
    status = 'ACTIVE'
    await vi.advanceTimersByTimeAsync(10_000)
    await settle()
    const afterActive = readinessCount(calls)
    expect(afterActive).toBeGreaterThan(beforeActive)

    await vi.advanceTimersByTimeAsync(20_000)
    await settle()
    expect(readinessCount(calls)).toBe(afterActive)
    wrapper.unmount()
  })
})
