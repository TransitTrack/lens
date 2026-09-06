import { hexColor } from './gtfs'

export { secToHm } from './stopSchedule'

interface BlockLike {
  startTimeSec: number
  endTimeSec: number
}

export function blockDurationSec(b: BlockLike): number {
  return Math.max(0, b.endTimeSec - b.startTimeSec)
}

/** Max number of blocks running simultaneously (peak vehicle demand). */
export function peakConcurrency(blocks: BlockLike[]): number {
  const events: [number, number][] = []
  for (const b of blocks) {
    events.push([b.startTimeSec, 1])
    events.push([b.endTimeSec, -1])
  }
  events.sort((a, b) => a[0] - b[0] || a[1] - b[1])
  let cur = 0
  let peak = 0
  for (const [, delta] of events) {
    cur += delta
    peak = Math.max(peak, cur)
  }
  return peak
}

export interface BlockTripLike {
  listIndex: number
  layoverAfterSec: number | null
  deadheadAfter: boolean | null
  trip: {
    tripId: string
    tripHeadsign: string | null
    routeId: string
    startTimeSec: number | null
    endTimeSec: number | null
    route?: { routeShortName: string | null, routeColor: string | null } | null
  }
}

export interface TimeSplit {
  revenueSec: number
  layoverSec: number
  deadheadLegs: number
}

export function timeSplit(trips: BlockTripLike[]): TimeSplit {
  let revenueSec = 0
  let layoverSec = 0
  let deadheadLegs = 0
  for (const bt of trips) {
    const s = bt.trip.startTimeSec
    const e = bt.trip.endTimeSec
    if (s != null && e != null) revenueSec += Math.max(0, e - s)
    layoverSec += bt.layoverAfterSec ?? 0
    if (bt.deadheadAfter) deadheadLegs++
  }
  return { revenueSec, layoverSec, deadheadLegs }
}

export interface TimelineSegment {
  key: string
  kind: 'trip' | 'layover'
  x: number // 0..1
  w: number // 0..1
  color: string
  routeLabel: string
  headsign: string | null
  startSec: number
  endSec: number
  deadheadAfter: boolean
}

/** Lay out a block's trips (and the layover gaps between them) on a 0..1 axis. */
export function timelineSegments(
  trips: BlockTripLike[],
  spanStart: number,
  spanEnd: number,
): TimelineSegment[] {
  const span = Math.max(1, spanEnd - spanStart)
  const at = (sec: number) => (sec - spanStart) / span
  const ordered = [...trips].sort((a, b) => a.listIndex - b.listIndex)
  const segs: TimelineSegment[] = []

  for (const bt of ordered) {
    const s = bt.trip.startTimeSec
    const e = bt.trip.endTimeSec
    if (s == null || e == null) continue
    segs.push({
      key: `t-${bt.listIndex}`,
      kind: 'trip',
      x: at(s),
      w: Math.max(0.002, at(e) - at(s)),
      color: hexColor(bt.trip.route?.routeColor, '#3b82f6'),
      routeLabel: bt.trip.route?.routeShortName ?? bt.trip.routeId,
      headsign: bt.trip.tripHeadsign,
      startSec: s,
      endSec: e,
      deadheadAfter: !!bt.deadheadAfter,
    })
    const lay = bt.layoverAfterSec ?? 0
    if (lay > 0) {
      segs.push({
        key: `l-${bt.listIndex}`,
        kind: 'layover',
        x: at(e),
        w: Math.max(0.001, at(e + lay) - at(e)),
        color: '#64748b',
        routeLabel: '',
        headsign: null,
        startSec: e,
        endSec: e + lay,
        deadheadAfter: false,
      })
    }
  }
  return segs
}
