import { describe, expect, it, beforeAll, afterAll, afterEach } from 'vitest'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import { server } from '../../mocks/server'
import FeedPicker from '../../app/components/FeedPicker.vue'
import { useDashboardSelection } from '../../app/composables/useDashboardSelection'

beforeAll(() => server.listen())
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('FeedPicker', () => {
  it('auto-selects the first feed when none is selected', async () => {
    const { selectFeed } = useDashboardSelection()
    selectFeed(null)
    const wrapper = await mountSuspended(FeedPicker)
    await new Promise((r) => setTimeout(r, 0)) // let the query resolve
    const { selectedFeedCode } = useDashboardSelection()
    expect(selectedFeedCode.value).toBe('feed-a')
    expect(wrapper.text()).toContain('Feed A')
  })
})
