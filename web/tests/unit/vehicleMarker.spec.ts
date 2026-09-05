import { describe, expect, it } from 'vitest'
import { colorForVehicle } from '../../app/utils/vehicleMarker'

describe('colorForVehicle', () => {
  it('matched and not stale -> green', () => {
    expect(colorForVehicle(true, false)).toBe('#22c55e')
  })
  it('stale (regardless of matched) -> amber', () => {
    expect(colorForVehicle(true, true)).toBe('#f59e0b')
    expect(colorForVehicle(false, true)).toBe('#f59e0b')
  })
  it('neither matched nor stale -> gray', () => {
    expect(colorForVehicle(false, false)).toBe('#9ca3af')
  })
})
