import { describe, expect, it } from 'vitest'
import { fixtures } from '../../mocks/handlers'
import { emptyFilters, filterVehicles } from '../../app/utils/vehicleFilters'

describe('filterVehicles', () => {
  it('matches vehicle labels and identifiers without case sensitivity', () => {
    expect(filterVehicles(fixtures.vehicles, {...emptyFilters(), query: '101'})).toEqual([
      fixtures.vehicles[0],
    ])
    expect(filterVehicles(fixtures.vehicles, {...emptyFilters(), query: 'BUS-2'})).toEqual([
      fixtures.vehicles[1],
    ])
  })
})
