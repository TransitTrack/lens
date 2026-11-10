import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import { h, ref } from 'vue'
import { UApp } from '#components'
import RoutesPage from '../../../app/pages/[feedCode]/explore/routes/index.vue'
import { withFeedContext } from '../../helpers/feedContext'
import { page, stubFetch } from '../../helpers/stubFetch'

const route = (feed: string) => ({ routeId: `${feed}-R1`, agencyId: null, routeShortName: `${feed.toUpperCase()}1`, routeLongName: `Line of ${feed}`, routeType: 3 })

async function settle() {
  for (let i = 0; i < 8; i++) await vi.advanceTimersByTimeAsync(0)
}

describe('explore routes page', () => {
  beforeEach(() => {
    useCookie('tt-demo-authenticated').value = 'true'
    vi.useFakeTimers({ shouldAdvanceTime: true })
  })
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('keeps the previous feed rows until the new feed responds, then shows only the new ones', async () => {
    let release!: () => void
    const held = new Promise<void>((resolve) => { release = resolve })
    stubFetch(async ({ url }) => {
      const { pathname } = new URL(url)
      if (pathname.endsWith('/feeds/b/routes')) {
        await held
        return page([route('b')])
      }
      if (pathname.endsWith('/feeds/a/routes')) return page([route('a')])
      return page([])
    })

    const selectedFeedCode = ref<string | null>('a')
    const wrapper = await mountSuspended(
      { render: () => h(UApp, null, () => h(RoutesPage)) },
      withFeedContext({ selectedFeedCode }),
    )
    await settle()
    expect(wrapper.text()).toContain('Line of a')

    selectedFeedCode.value = 'b'
    await settle()
    expect(wrapper.text()).toContain('Line of a')
    expect(wrapper.text()).not.toContain('Line of b')

    release()
    await settle()
    expect(wrapper.text()).toContain('Line of b')
    expect(wrapper.text()).not.toContain('Line of a')
    wrapper.unmount()
  })
})
