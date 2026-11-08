export interface VehicleRow {
  vehicleId: string
  label: string | null
  reportTs: string
  position: { lat: number, lon: number }
  bearing: number | null
  matched: boolean
  stale: boolean
  scheduleAdherenceSec: number | null
  stopPathIndex: number | null
  distanceAlongTripM: number | null
  speedMps: number | null
  pattern: { lengthM: number | null } | null
  trip: { tripId: string, routeId: string, tripHeadsign: string | null, route: {
    routeShortName: string | null, routeColor: string | null, routeTextColor: string | null
  } | null } | null
  block: { blockId: string, serviceId: string } | null
}
