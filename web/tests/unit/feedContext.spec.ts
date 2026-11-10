import { describe, expect, it } from 'vitest'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import { defineComponent, h, ref } from 'vue'
import { useFeedContext } from '~/composables/feedContext'
import { feedContextStub, withFeedContext } from '../helpers/feedContext'

const Probe = defineComponent({
  setup() {
    const ctx = useFeedContext()
    return () => h('span', ctx.feedPath('/x'))
  },
})

describe('feed context', () => {
  it('throws without a provider', async () => {
    await expect(mountSuspended(Probe)).rejects.toThrow('FeedContext not provided')
  })

  it('returns the provided context', async () => {
    const wrapper = await mountSuspended(Probe, withFeedContext({ selectedFeedCode: ref('f1') }))
    expect(wrapper.text()).toBe('/f1/x')
    expect(feedContextStub().feeds.value).toEqual([])
  })
})
