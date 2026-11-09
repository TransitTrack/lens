import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import { defineComponent, h } from 'vue'
import { useQuery, useQueryCache } from '@pinia/colada'
import { UApp } from '#components'
import DefaultLayout from '../../app/layouts/default.vue'
import { revisionsQuery } from '../../app/composables/api/queries/gtfs/revisionsQuery'
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

  const feedCount = (calls: { url: string }[]) =>
    calls.filter((c) => new URL(c.url).pathname.endsWith('/feeds/feed-a')).length

  async function mountLayout(content: () => ReturnType<typeof h> = () => h('div', 'page')) {
    return await mountSuspended({ render: () => h(UApp, null, () => h(DefaultLayout, null, content)) }, { route: '/feed-a' })
  }

  /** A page reading the same revisions entry the layout's readiness reads. */
  function revisionsPage() {
    const handle: { refetch?: () => Promise<unknown>, queryCache?: ReturnType<typeof useQueryCache> } = {}
    const Page = defineComponent({
      setup() {
        handle.queryCache = useQueryCache()
        const { refetch } = useQuery(() => ({ ...revisionsQuery({ feedCode: 'feed-a' }), enabled: true }))
        handle.refetch = () => refetch()
        return () => h('div', 'revisions page')
      },
    })
    return { handle, content: () => h(Page) }
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

    const feedReads = feedCount(calls)
    await vi.advanceTimersByTimeAsync(20_000)
    await settle()
    expect(readinessCount(calls)).toBe(afterActive)
    expect(feedCount(calls)).toBe(feedReads)
    wrapper.unmount()
  })

  it('keeps the page mounted when a page read of the revisions fails after the feed settled', async () => {
    let failRevisions = false
    stubFetch(({ url }) => {
      const { pathname } = new URL(url)
      if (pathname.endsWith('/feeds/feed-a/revisions')) return failRevisions ? json({ title: 'Boom', status: 500 }, 500) : page([revision('ACTIVE')])
      if (pathname.endsWith('/feeds/feed-a')) return json(feed)
      if (pathname.endsWith('/feeds')) return page([feed])
      return page([])
    })
    const { handle, content } = revisionsPage()
    const wrapper = await mountLayout(content)
    await settle()
    expect(wrapper.text()).toContain('revisions page')

    failRevisions = true
    await handle.refetch!()
    await settle()
    expect(wrapper.text()).toContain('revisions page')
    expect(wrapper.text()).not.toContain('Preparing the first dataset')
    wrapper.unmount()
  })

  it('refetches a page read of the revisions on a feeds invalidation after the feed settled', async () => {
    const { calls } = stubBackend(() => 'ACTIVE')
    const { handle, content } = revisionsPage()
    // The reader is rendered before the layout, so the layout's options are the last applied to the shared entry
    // (Pinia Colada keeps one set of options per entry; the last consumer to ensure it wins).
    const wrapper = await mountSuspended(
      { render: () => h(UApp, null, () => [content(), h(DefaultLayout, null, () => h('div', 'page'))]) },
      { route: '/feed-a' },
    )
    await settle()
    expect(wrapper.text()).toContain('page')
    const settled = readinessCount(calls)

    await handle.queryCache!.invalidateQueries({ key: ['feeds'] })
    await settle()
    expect(readinessCount(calls)).toBe(settled + 1)
    wrapper.unmount()
  })

  it('does not report an unknown feed as preparing', async () => {
    stubFetch(({ url }) => {
      const { pathname } = new URL(url)
      if (pathname.endsWith('/feeds/feed-a/revisions')) return json({ title: 'Not Found', status: 404 }, 404)
      if (pathname.endsWith('/feeds/feed-a')) return json({ title: 'Not Found', status: 404 }, 404)
      if (pathname.endsWith('/feeds')) return page([])
      return page([])
    })
    const wrapper = await mountLayout()
    await settle()
    expect(wrapper.text()).not.toContain('Preparing the first dataset')
    expect(wrapper.text()).toContain('page')
    wrapper.unmount()
  })
})
