import { describe, expect, it } from 'vitest'
import type { VehiclesQuery } from '../../generated/graphql'

describe('generated graphql types', () => {
  it('shapes a Vehicles query result', () => {
    const sample: VehiclesQuery = {
      vehicles: [
        {
          vehicleId: 'v1',
          label: null,
          reportTs: '2026-09-06T08:00:00Z',
          position: { lat: 45.75, lon: 21.22 },
          matched: true,
          stale: false,
          scheduleAdherenceSec: 30,
          stopPathIndex: 2,
          speedMps: 8.5,
        },
      ],
    }
    expect(sample.vehicles[0]?.vehicleId).toBe('v1')
  })
})
