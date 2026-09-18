import {afterAll, afterEach, beforeAll, describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import {server} from '../../mocks/server'
import RecommendationList from '../../app/components/optimize/RecommendationList.vue'

beforeAll(() => server.listen())
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('RecommendationList', () => {
  it('presents completed analysis results as a review-ready recommendation summary', async () => {
    const wrapper = await mountSuspended(RecommendationList, {props: {runId: 'run-1'}})
    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(wrapper.text()).toContain('2 recommendations ready for review')
    expect(wrapper.text()).toContain('Trip shift')
    expect(wrapper.text()).toContain('+1m 15s later')
    expect(wrapper.text()).toContain('28 samples')
    expect(wrapper.text()).toContain('3 stop times across 2 trips')

    const disclosure = wrapper.findAll('button').find((button) => button.text().includes('Show affected targets'))
    expect(disclosure).toBeDefined()
    await disclosure!.trigger('click')
    expect(wrapper.text()).toContain('Affected targets')
    expect(wrapper.text()).toContain('trip-c')
    expect(wrapper.text()).toContain('Stops 4–5')
  })
})
