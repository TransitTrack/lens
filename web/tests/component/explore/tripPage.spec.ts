import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mountSuspended, mockNuxtImport } from '@nuxt/test-utils/runtime'
import { h, ref } from 'vue'
import { UApp } from '#components'
import TripPage from '../../../app/pages/[feedCode]/explore/trips/[tripId].vue'
import { withFeedContext } from '../../helpers/feedContext'
import { json, page, stubFetch } from '../../helpers/stubFetch'

const routeParams = vi.hoisted(() => ({ value: { tripId: 'T404' } as Record<string, string> }))
mockNuxtImport('useRoute', () => () => ({ params: routeParams.value, query: {} }))

async function settle() {
  for (let i = 0; i < 8; i++) await vi.advanceTimersByTimeAsync(0)
}

describe('explore trip page', () => {
  beforeEach(() => {
    useCookie('tt-demo-authenticated').value = 'true'
    vi.useFakeTimers({ shouldAdvanceTime: true })
  })
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('shows the not-found state for an unknown trip and never requests its dependents', async () => {
    const { calls } = stubFetch(({ url }) => {
      const { pathname } = new URL(url)
      if (pathname.endsWith('/trips/T404')) return json({ message: 'not found' }, 404)
      return page([])
    })

    const wrapper = await mountSuspended(
      { render: () => h(UApp, null, () => h(TripPage)) },
      withFeedContext({ selectedFeedCode: ref<string | null>('a') }),
    )
    await settle()

    expect(calls.some((c) => new URL(c.url).pathname.endsWith('/trips/T404'))).toBe(true)
    // No error alert, no endless skeleton: the page settles on the trip id with an empty body.
    expect(wrapper.text()).toContain('T404')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.find('.animate-pulse').exists()).toBe(false)
    const paths = calls.map((c) => new URL(c.url).pathname)
    expect(paths.some((p) => p.includes('/stop-times'))).toBe(false)
    expect(paths.some((p) => p.includes('/schedule-times'))).toBe(false)
    expect(paths.some((p) => /\/routes\//.test(p))).toBe(false)
    wrapper.unmount()
  })
})
