import type { Block, BlockTrip, Route, Trip } from '~/api'
import { withRoute } from './routes'

/** Blocks with their trips; a block is identified by `serviceId|blockId`. Trips without a block are skipped. */
export function blocksWithTrips(blocks: Block[], trips: Trip[]): Array<Block & { trips: Trip[] }> {
  const byBlock = new Map<string, Trip[]>()
  for (const trip of trips) {
    if (!trip.blockId) continue
    const key = `${trip.serviceId}|${trip.blockId}`
    const list = byBlock.get(key) ?? []
    list.push(trip)
    byBlock.set(key, list)
  }
  return blocks.map((block) => ({ ...block, trips: byBlock.get(`${block.serviceId}|${block.blockId}`) ?? [] }))
}

/** A block's ordered trip references with their trip and its route; references to unknown trips are dropped. */
export function blockTripsWithTrips(
  blockTrips: BlockTrip[],
  trips: Trip[],
  routes: Map<string, Route>,
): Array<BlockTrip & { trip: Trip & { route: Route | null } }> {
  const tripById = new Map(trips.map((trip) => [trip.tripId, trip]))
  return blockTrips.flatMap((bt) => {
    const trip = tripById.get(bt.tripId)
    return trip ? [{ ...bt, trip: withRoute(trip, routes) }] : []
  })
}
