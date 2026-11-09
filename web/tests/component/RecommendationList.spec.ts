import {describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import type {OptimizationRecommendation} from '~/api'
import RecommendationList from '../../app/components/optimize/RecommendationList.vue'

const recommendations = [
  {
    id: 'rec-1', kind: 'TRIP_SHIFT', status: 'PENDING', sampleCount: 28, deltaSec: 75,
    currentValue: {targets: [{tripId: 'trip-a', startTimeSec: 21600}]},
    proposedValue: {targets: [{tripId: 'trip-a', startTimeSec: 21675}]},
    evidence: {}, reason: 'Trips run late',
  },
  {
    id: 'rec-2', kind: 'STOP_TIME', status: 'PENDING', sampleCount: 40, deltaSec: -30,
    currentValue: {targets: [{tripId: 'trip-b', stopSequence: 2, arrivalSec: 22000}]},
    proposedValue: {targets: [
      {tripId: 'trip-b', stopSequence: 2, arrivalSec: 21970},
      {tripId: 'trip-c', stopSequence: 4, arrivalSec: 23000},
      {tripId: 'trip-c', stopSequence: 5, arrivalSec: 23100},
    ]},
    evidence: {}, reason: 'Stops run early',
  },
] as unknown as OptimizationRecommendation[]

describe('RecommendationList', () => {
  it('presents completed analysis results as a review-ready recommendation summary', async () => {
    const wrapper = await mountSuspended(RecommendationList, {props: {recommendations}})

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
